/*******************************************************************************
 * Copyright (C) 2007, 2015:
 *
 * - Ferdinando Villa <ferdinando.villa@bc3research.org> - integratedmodelling.org - any other
 * authors listed in @author annotations
 *
 * All rights reserved. This file is part of the k.LAB software suite, meant to enable modular,
 * collaborative, integrated development of interoperable data and model components. For details,
 * see http://integratedmodelling.org.
 *
 * This program is free software; you can redistribute it and/or modify it under the terms of the
 * Affero General Public License Version 3 or any later version.
 *
 * This program is distributed in the hope that it will be useful, but without any warranty; without
 * even the implied warranty of merchantability or fitness for a particular purpose. See the Affero
 * General Public License for more details.
 *
 * You should have received a copy of the Affero General Public License along with this program; if
 * not, write to the Free Software Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA
 * 02111-1307, USA. The license is also available at: https://www.gnu.org/licenses/agpl.html
 *******************************************************************************/
package org.integratedmodelling.klab.services.reasoner.owl;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.common.collect.Sets;
import org.integratedmodelling.common.knowledge.ConceptImpl;
import org.integratedmodelling.common.lang.Axiom;
import org.integratedmodelling.common.logging.Logging;
import org.integratedmodelling.klab.api.collections.Pair;
import org.integratedmodelling.klab.api.data.Metadata;
import org.integratedmodelling.klab.api.exceptions.KlabIOException;
import org.integratedmodelling.klab.api.exceptions.KlabInternalErrorException;
import org.integratedmodelling.klab.api.exceptions.KlabUnimplementedException;
import org.integratedmodelling.klab.api.exceptions.KlabValidationException;
import org.integratedmodelling.klab.api.knowledge.Artifact;
import org.integratedmodelling.klab.api.knowledge.Concept;
import org.integratedmodelling.klab.api.knowledge.SemanticType;
import org.integratedmodelling.klab.api.knowledge.Semantics;
import org.integratedmodelling.klab.api.lang.LogicalConnector;
import org.integratedmodelling.klab.api.lang.UnarySemanticOperator;
import org.integratedmodelling.klab.api.lang.kim.KimNamespace;
import org.integratedmodelling.klab.api.lang.kim.KimOntology;
import org.integratedmodelling.klab.api.scope.Scope;
import org.integratedmodelling.klab.api.services.Authority;
import org.integratedmodelling.klab.api.services.Authority.Identity;
import org.integratedmodelling.klab.api.services.ResourcesService;
import org.integratedmodelling.klab.api.services.runtime.Channel;
import org.integratedmodelling.klab.api.utils.Utils;
import org.integratedmodelling.klab.api.utils.Utils.CamelCase;
import org.integratedmodelling.klab.configuration.ServiceConfiguration;
import org.integratedmodelling.klab.services.reasoner.internal.CoreOntology;
import org.integratedmodelling.klab.services.reasoner.internal.CoreOntology.NS;
import org.semanticweb.HermiT.Reasoner;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.*;
import org.semanticweb.owlapi.reasoner.*;
import org.semanticweb.owlapi.util.AutoIRIMapper;
import org.semanticweb.owlapi.util.Version;
import org.springframework.util.StringUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Import concepts and properties from OWL ontologies.
 *
 * @author Ferd
 */
public class OWL {

  public static final String DEFAULT_ONTOLOGY_PREFIX = "http://integratedmodelling.org/ks";
  public static final String INTERNAL_ONTOLOGY_PREFIX =
      "http://integratedmodelling.org/ks/internal";

  private HashMap<String, KimNamespace> namespaces = new HashMap<>();
  private HashMap<String, Ontology> ontologies = new HashMap<>();
  private HashMap<String, String> iri2ns = new HashMap<>();
  private HashMap<String, OWLClass> systemConcepts = new HashMap<>();
  private HashMap<String, ConceptImpl> xsdMappings = new HashMap<>();
  private BiMap<Long, OWLClass> owlClasses = HashBiMap.create();
  private BiMap<Long, ConceptImpl> conceptsById = HashBiMap.create();
  private AtomicLong classId = new AtomicLong(1L);
  private Scope scope;

  private boolean reasonerActive;
  private boolean reasonerSynchronizing = false;
  private Ontology mergedReasonerOntology;
  private OWLReasoner reasoner;

  // this is the ontology that imports all concepts including the worldview, on which the OWL
  // reasoner operates
  public static String INTERNAL_REASONER_ONTOLOGY_ID = "k.reasoner";
  // concepts that start without a namespace end up here, which eventually will import the entire
  // worldview
  public static String INTERNAL_ONTOLOGY_ID = "k.derived";

  static EnumSet<SemanticType> emptyType = EnumSet.noneOf(SemanticType.class);

  Ontology nonSemanticConcepts;
  OWLOntologyManager manager = null;

  private Concept thing;
  private Concept nothing;

  private CoreOntology coreOntology;

  public OWL(Scope scope) {
    this.scope = scope;
  }

  public Concept nothing(String urn, Throwable... exceptions) {
    // TODO return a special nothing with the passed URN and the exceptions in metadata
    return nothing;
  }

  /**
   * Source of truth for identifier-friendly reference names
   *
   * @param namespace
   * @param name
   * @return
   */
  public static String getCleanFullId(String namespace, String name) {
    return namespace.replaceAll("\\.", "_") + "__" + CamelCase.toLowerCase(name, '_');
  }

  private long registerOwlClass(OWLClass cls) {
    long ret = classId.getAndIncrement();
    owlClasses.put(ret, cls);
    return ret;
  }

  /**
   * Given a collection of namespace-specified knowledge and/or collections thereof, return the
   * ontology of a namespace that sits on top of all others in the asserted dependency chain and
   * imports all concepts. If that is not possible, return the "fallback" ontology which must
   * already import all the concepts (typically the one where the targets are used in a
   * declaration). Used to establish where to put derived concepts and restrictions so as to avoid
   * circular dependencies in the underlying OWL model while minimizing redundant concepts.
   *
   * @param targets
   * @return
   */
  public Ontology getTargetOntology(Ontology fallback, Object... targets) {

    Set<String> graph = new HashSet<>();
    if (targets != null) {
      for (Object o : targets) {
        if (o instanceof Semantics) {
          String nsid = ((Semantics) o).getNamespace();
          if (getNamespace(nsid) != null) {
            graph.add(nsid);
          }
        } else if (o instanceof Iterable) {
          for (Object u : (Iterable<?>) o) {
            if (u instanceof Semantics) {
              String nsid = ((Semantics) u).getNamespace();
              if (getNamespace(nsid) != null) {
                graph.add(nsid);
              }
            }
          }
        }
      }
    }

    String namespace = null;
    Set<String> os = new HashSet<>(graph);
    for (String a : graph) {
      if (namespace == null) {
        namespace = a;
      }
      for (String b : os) {
        KimNamespace ns = getNamespace(b);
        if (!b.equals(a) && ns.getImports().containsKey(a)) {
          namespace = b;
        }
      }
    }

    /*
     * candidate namespace must already import all the others. If not, choose the
     * fallback ontology which must be guaranteed to contain all the imports
     * already.
     */
    boolean transitive = true;
    KimNamespace ns = getNamespace(namespace);
    for (String s : graph) {
      if (!s.equals(ns.getUrn()) && !ns.getImports().containsKey(s)) {
        transitive = false;
        break;
      }
    }
    return (Ontology) (transitive && ns != null ? getOntology(ns.getUrn()) : fallback);
  }

  public Ontology requireOntology(String id) {
    return requireOntology(id == null ? INTERNAL_ONTOLOGY_ID : id, DEFAULT_ONTOLOGY_PREFIX);
  }

  public Ontology requireOntology(String id, String prefix) {

    if (ontologies.get(id) != null) {
      return ontologies.get(id);
    }

    Ontology ret = null;
    try {
      OWLOntology o = manager.createOntology(IRI.create(prefix + "/" + id));
      ret = new Ontology(this, o, id);
      ontologies.put(id, ret);
      iri2ns.put(((Ontology) ret).getPrefix(), id);
    } catch (OWLOntologyCreationException e) {
      throw new KlabInternalErrorException(e);
    }

    return ret;
  }

  /**
   * Get the IProperty corresponding to the OWL class passed. Throws an unchecked exception if not
   * found.
   *
   * @param owl
   * @return the property for the class
   */
  public Property getPropertyFor(OWLProperty<?, ?> owl) {

    Property ret = null;
    String sch = owl.getIRI().getNamespace();
    if (sch.endsWith("#")) {
      sch = sch.substring(0, sch.length() - 1);
    }
    if (sch.endsWith("/")) {
      sch = sch.substring(0, sch.length() - 1);
    }
    Ontology ontology = getOntology(iri2ns.get(sch));
    if (ontology != null) {
      ret = ontology.getProperty(owl.getIRI().getFragment());
    }

    if (ret == null) {
      throw new KlabInternalErrorException(
          "internal: OWL entity " + owl + " does not correspond to a known ontology");
    }

    return ret;
  }

  /** Create a manager and load every OWL file under the load path. */
  //    @Deprecated
  //    public void initialize() {
  //
  //        manager = OWLManager.createOWLOntologyManager();
  //        // this.loadPath = loadPath;
  //        coreOntology = new CoreOntology(Configuration.INSTANCE.getDataPath("knowledge"), this);
  //        // coreOntology.load(monitor);
  //        load(coreOntology.getRoot());
  //
  //        /*
  //         * TODO insert basic datatypes as well
  //         */
  //        this.systemConcepts.put("owl:Thing", manager.getOWLDataFactory().getOWLThing());
  //        this.systemConcepts.put("owl:Nothing", manager.getOWLDataFactory().getOWLNothing());
  //
  //        // if (this.loadPath == null) {
  //        // throw new KIOException("owl resources cannot be found: knowledge load
  //        // directory does not
  //        // exist");
  //        // }
  //
  //        // load();
  //
  //        this.mergedReasonerOntology = (Ontology) requireOntology(INTERNAL_REASONER_ONTOLOGY_ID,
  //                OWL.INTERNAL_ONTOLOGY_PREFIX);
  //        this.mergedReasonerOntology.setInternal(true);
  //
  //        /*
  //         * all namespaces so far are internal, and just these.
  //         */
  //        for (KimNamespace ns : this.namespaces.values()) {
  //            // ((Namespace) ns).setInternal(true);
  //            getOntology(ns.getUrn()).setInternal(true);
  //        }
  //
  //        this.nonSemanticConcepts = requireOntology("nonsemantic", INTERNAL_ONTOLOGY_PREFIX);
  //
  //        /*
  //         * create an independent ontology for the non-semantic types we encounter.
  //         */
  //        // if (Namespaces.INSTANCE.getNamespace(ONTOLOGY_ID) == null) {
  //        // Namespaces.INSTANCE.registerNamespace(new Namespace(ONTOLOGY_ID, null,
  //        // overall),
  //        // monitor);
  //        // }
  //        if (Configuration.INSTANCE.useReasoner()) {
  //            this.reasoner =
  //                    new Reasoner.ReasonerFactory().createReasoner(mergedReasonerOntology
  //                    .getOWLOntology());
  //            reasonerActive = true;
  //        }
  //
  //        for (KimNamespace ns : this.namespaces.values()) {
  //            registerWithReasoner(getOntology(ns.getUrn()));
  //        }
  //
  //    }
  public void initialize(KimOntology rootDomain) {

    manager = OWLManager.createOWLOntologyManager();
    // this.loadPath = loadPath;
    coreOntology = new CoreOntology(ServiceConfiguration.INSTANCE.getDataPath("knowledge"), this);
    // coreOntology.load(monitor);
    load(coreOntology.getRoot());

    /*
     * FIXME manual mapping until I understand what's going on with BFO, whose
     * concepts have a IRI that does not contain the namespace.
     */
    iri2ns.put("http://purl.obolibrary.org/obo", "bfo");

    /*
     * TODO insert basic datatypes as well
     */
    this.systemConcepts.put("owl:Thing", manager.getOWLDataFactory().getOWLThing());
    this.systemConcepts.put("owl:Nothing", manager.getOWLDataFactory().getOWLNothing());

    coreOntology.validateRootDomain(rootDomain, scope);

    this.mergedReasonerOntology =
        (Ontology) requireOntology(INTERNAL_REASONER_ONTOLOGY_ID, OWL.INTERNAL_ONTOLOGY_PREFIX);
    this.mergedReasonerOntology.setInternal(true);

    /*
     * all namespaces so far are internal, and just these.
     */
    for (KimNamespace ns : this.namespaces.values()) {
      // ((Namespace) ns).setInternal(true);
      getOntology(ns.getUrn()).setInternal(true);
    }

    this.nonSemanticConcepts = requireOntology("nonsemantic", INTERNAL_ONTOLOGY_PREFIX);

    /*
     * create an independent ontology for the non-semantic types we encounter.
     */
    // if (Namespaces.INSTANCE.getNamespace(ONTOLOGY_ID) == null) {
    // Namespaces.INSTANCE.registerNamespace(new Namespace(ONTOLOGY_ID, null,
    // overall),
    // monitor);
    // }
    if (ServiceConfiguration.INSTANCE.useReasoner()) {
      this.reasoner =
          new Reasoner.ReasonerFactory().createReasoner(mergedReasonerOntology.getOWLOntology());
      reasonerActive = true;
    }

    for (KimNamespace ns : this.namespaces.values()) {
      registerWithReasoner(getOntology(ns.getUrn()));
    }
  }

  public CoreOntology getCoreOntology() {
    return this.coreOntology;
  }

  String importOntology(
      OWLOntology ontology, String resource, String namespace, boolean imported, Channel monitor) {

    if (!ontologies.containsKey(namespace)) {
      ontologies.put(namespace, new Ontology(this, ontology, namespace));
    }

    /*
     * seen already?
     */
    if (this.namespaces.containsKey(namespace)) {
      return namespace;
    }

    // FIXME needs a local namespace implementation for ontologies
    // Namespace ns = new Namespace(namespace, new File(resource),
    // ontologies.get(namespace));
    //
    // this.namespaces.put(namespace, ns);

    return namespace;
  }

  /*
   * the three knowledge manager methods we implement, so we can serve as delegate
   * to a KM for these.
   */
  public Concept getConcept(String concept) {

    Concept result = null;

    if (QualifiedName.validate(concept)) {

      QualifiedName st = new QualifiedName(concept);

      if (Character.isUpperCase(st.getNamespace().charAt(0))) {

        Authority authority = ServiceConfiguration.INSTANCE.getAuthorities().get(st.getNamespace());
        if (authority != null) {
          Identity identity = authority.resolveIdentity(removeTicks(st.getName()));
          if (identity != null) {
            result = getAuthorityConcept(identity);
          }
        }

      } else {

        Ontology o = ontologies.get(st.getNamespace());
        if (o == null) {
          OWLClass systemConcept = this.systemConcepts.get(st.toString());
          if (systemConcept != null) {
            result = makeConcept(systemConcept, st.getName(), st.getNamespace(), emptyType);
          }
        } else {
          result = o.getConcept(st.getName());
        }
      }
    }

    return result;
  }

  // careful: not reentrant on purpose. Can't already have the class in the caches or an exception
  // will be thrown. Must release the ontology in advance. Used in Ontology but should be private.
  Concept makeConcept(
      OWLClass owlClass, String id, String ontologyName, Collection<SemanticType> type) {

    ConceptImpl ret = new ConceptImpl();

    if (CoreOntology.CORE_ONTOLOGY_NAME.equals(ontologyName)) {
      ret.setAbstract(true);
      ret.setReferenceName(CoreOntology.CORE_ONTOLOGY_NAME + "_" + id.toLowerCase());
    }

    /*
    This will throw an exception if the class is already there. Count on the releaseOntology() having
    been called and having worked before this happens.
     */
    ret.setId(registerOwlClass(owlClass));
    ret.setName(id);
    ret.setNamespace(ontologyName);
    ret.setUrn(ontologyName + ":" + id);
    ret.getType().addAll(type);
    registerConcept(ret);
    return ret;
  }

  private String removeTicks(String id) {
    if (id.startsWith("'") || id.startsWith("\"")) {
      id = id.substring(1);
    }
    if (id.endsWith("'") || id.endsWith("\"")) {
      id = id.substring(0, id.length() - 1);
    }
    return id;
  }

  public Property getProperty(String concept) {

    Property result = null;

    if (QualifiedName.validate(concept)) {
      String[] conceptSpaceAndLocalName = QualifiedName.splitIdentifier(concept);
      Ontology o = ontologies.get(conceptSpaceAndLocalName[0]);
      if (o != null) {
        result = o.getProperty(conceptSpaceAndLocalName[1]);
        if (result == null && !conceptSpaceAndLocalName[1].startsWith("p")) {
          result = o.getProperty("p" + conceptSpaceAndLocalName[1]);
        }
      }
    }
    return result;
  }

  // public KConcept getLeastGeneralCommonConcept(Collection<KConcept> cc) {
  // KConcept ret = null;
  // KConcept tmpConcept;
  //
  // for (KConcept concept : cc) {
  // if (ret == null) {
  // ret = concept;
  // } else {
  // tmpConcept = ((Concept) ret).getLeastGeneralCommonConcept(concept);
  // if (tmpConcept != null) {
  // ret = tmpConcept;
  // }
  // }
  // }
  //
  // return ret;
  // }

  /**
   * Return the ontology for the given namespace ID (short name).
   *
   * @param ns
   * @return the ontology
   */
  public Ontology getOntology(String ns) {
    return (Ontology) ontologies.get(ns);
  }

  public Concept getRootConcept() {
    if (this.thing == null) {
      this.thing = new ConceptImpl();
      ((ConceptImpl) this.thing).setId(registerOwlClass(manager.getOWLDataFactory().getOWLThing()));
      ((ConceptImpl) this.thing).setUrn("owl:Thing");
      ((ConceptImpl) this.thing).setNamespace("owl");
      registerConcept((ConceptImpl) this.thing);
    }
    return this.thing;
  }

  public void registerConcept(ConceptImpl concept) {
    this.conceptsById.put(concept.getId(), concept);
  }

  public String getConceptSpace(IRI iri) {

    if (iri2ns.containsKey(iri.toString())) {
      return iri2ns.get(iri.toString());
    }

    String oIri = removeEntity(iri);
    String ret = iri2ns.get(oIri);

    if (ret == null) {
      /*
       * happens, whenever we depend on a concept from a server ontology not loaded
       * yet. Must find a way to deal with this.
       */
      ret = Utils.URLs.getNameFromURL(oIri);
    }

    return ret;
  }

  private String removeEntity(IRI iri) {
    String eiri = iri.toString();
    if (eiri.contains("#")) {
      eiri = eiri.substring(0, eiri.lastIndexOf('#'));
    } else {
      eiri = eiri.substring(0, eiri.lastIndexOf('/'));
    }
    return eiri;
  }

  /**
   * Load OWL files from given directory and in its subdirectories, using a prefix mapper to resolve
   * URLs internally and deriving ontology names from the relative paths. This uses the resolver
   * passed at initialization only to create the namespace. It's only meant for core knowledge not
   * seen by users.
   *
   * @param kdir
   */
  public void load(File kdir) {

    AutoIRIMapper imap = new AutoIRIMapper(kdir, true);
    manager.addIRIMapper(imap);

    File[] files = kdir.listFiles();
    // null in error
    if (files != null) {
      for (File fl : files) {
        loadInternal(fl, "", false, null);
      }
    } else {
      throw new KlabIOException("Errors reading core ontologies: system will be nonfunctional.");
    }
  }

  /**
   * @param f
   * @param path
   * @param forcePath disregard directory structure and use passed path as prefix for ontology
   * @param monitor
   */
  private void loadInternal(File f, String path, boolean forcePath, Channel monitor) {

    String pth =
        path == null
            ? ""
            : (path
                + (path.isEmpty() ? "" : ".")
                + Utils.CamelCase.toLowerCase(Utils.Files.getFileBaseName(f.toString()), '-'));

    if (forcePath) {
      pth = path;
    }

    if (f.isDirectory()) {
      if (!Utils.Files.getFileBaseName(f.toString()).startsWith(".")) {
        for (File fl : Objects.requireNonNull(f.listFiles())) {
          loadInternal(fl, pth, false, monitor);
        }
      }
    } else if (Utils.Files.getFileExtension(f.toString()).equals("owl")) {

      try (InputStream input = new FileInputStream(f)) {

        OWLOntology ontology = manager.loadOntologyFromOntologyDocument(input);
        input.close();
        Ontology ont = new Ontology(this, ontology, pth);
        ont.setResourceUrl(f.toURI().toURL().toString());
        ontologies.put(pth, ont);
        iri2ns.put(ont.getPrefix(), pth);

      } catch (OWLOntologyAlreadyExistsException e) {

        /*
         * already imported- wrap it and use it as is.
         */
        OWLOntology ont = manager.getOntology(e.getOntologyID().getOntologyIRI());
        if (ont != null && ontologies.get(pth) == null) {
          Ontology o = new Ontology(this, ont, pth);
          try {
            o.setResourceUrl(f.toURI().toURL().toString());
          } catch (MalformedURLException e1) {
          }
          ontologies.put(pth, o);
          iri2ns.put(ont.getOntologyID().getOntologyIRI().toString(), pth);
        }

      } catch (Exception e) {

        /*
         * everything else is probably an error
         */
        if (monitor != null) {
          monitor.error(new KlabIOException("reading " + f + ": " + e.getMessage()));
        } else {
          Logging.INSTANCE.error(e);
        }
      }

      Ontology o = ontologies.get(pth);
      if (o != null) {
        /*
         * FIXME create namespace
         */
        // Namespace ns = new Namespace(pth, f, o);
        // // ns.setId(pth);
        // // ns.setResourceUrl(f.toString());
        // // ns.setOntology(o);
        // this.namespaces.put(pth, ns);
      }
    }
  }

  public Concept getAuthorityConcept(Authority.Identity identity) {

    if (identity == null) {
      return null;
    }

    if (Utils.Notifications.hasErrors(identity.getNotifications())) {
      return null;
    }

    String oid = "auth_" + Utils.Paths.getFirst(identity.getAuthorityName(), ".").toLowerCase();
    boolean isNew = getOntology(oid) == null;
    Ontology ontology = requireOntology(oid, OWL.INTERNAL_ONTOLOGY_PREFIX);

    if (isNew) {
      ontology.setInternal(true);
      registerWithReasoner(ontology);
      // authorityOntologyIds.add(oid);
    }

    Concept ret = ontology.getConcept(identity.getConceptName());
    if (ret == null) {

      List<Axiom> axioms = new ArrayList<>();
      Set<SemanticType> type = UnarySemanticOperator.getType("identity", null);
      type.add(SemanticType.AUTHORITY_IDENTITY);

      // lookup parent if any; otherwise ensure we have a suitable parent
      String baseIdentity = identity.getBaseIdentity();
      if (baseIdentity == null) {
        baseIdentity = StringUtils.capitalize(oid.toLowerCase()) + "Identity";
      } else {
        // TODO recursively resolve the base identity
        throw new KlabUnimplementedException(
            "explicit base identities for authority concepts are still unimplemented");
      }

      String pName = "is" + baseIdentity;
      Concept base = ontology.getConcept(baseIdentity);
      if (base == null) {
        axioms.add(Axiom.ClassAssertion(baseIdentity, type));
        // TODO check - there should be a base identity per leaf vocabulary
        axioms.add(Axiom.AnnotationAssertion(baseIdentity, NS.BASE_DECLARATION, "true"));
        axioms.add(
            Axiom.AnnotationAssertion(
                baseIdentity, NS.AUTHORITY_ID_PROPERTY, identity.getAuthorityName()));
        axioms.add(
            Axiom.AnnotationAssertion(
                baseIdentity,
                NS.REFERENCE_NAME_PROPERTY,
                "auth_"
                    + identity.getAuthorityName().toLowerCase()
                    + "_"
                    + baseIdentity.toLowerCase()));
        axioms.add(Axiom.ObjectPropertyAssertion(pName));
        axioms.add(Axiom.ObjectPropertyRange(pName, baseIdentity));
        axioms.add(Axiom.SubObjectProperty(NS.HAS_IDENTITY_PROPERTY, pName));
        axioms.add(
            Axiom.AnnotationAssertion(
                baseIdentity, NS.TRAIT_RESTRICTING_PROPERTY, oid + ":" + pName));
      }

      // if we created the parent, add the restricting property and prepare to
      // give it to the new identity

      axioms.add(Axiom.ClassAssertion(identity.getConceptName(), type));
      axioms.add(Axiom.SubClass(baseIdentity, identity.getConceptName()));
      axioms.add(
          Axiom.AnnotationAssertion(
              identity.getConceptName(),
              NS.REFERENCE_NAME_PROPERTY,
              "auth_"
                  + identity.getAuthorityName().toLowerCase()
                  + "_"
                  + identity.getConceptName().toLowerCase()));
      axioms.add(
          Axiom.AnnotationAssertion(
              identity.getConceptName(), Metadata.DC_LABEL, identity.getLabel()));
      axioms.add(
          Axiom.AnnotationAssertion(
              identity.getConceptName(), NS.DISPLAY_LABEL_PROPERTY, identity.getLabel()));
      axioms.add(
          Axiom.AnnotationAssertion(
              identity.getConceptName(), NS.AUTHORITY_ID_PROPERTY, identity.getAuthorityName()));
      axioms.add(
          Axiom.AnnotationAssertion(
              identity.getConceptName(), Metadata.DC_COMMENT, identity.getDescription()));
      axioms.add(
          Axiom.AnnotationAssertion(
              identity.getConceptName(), NS.CONCEPT_DEFINITION_PROPERTY, identity.getLocator()));

      ontology.define(axioms);

      ret = ontology.getConcept(identity.getConceptName());
    }

    return ret;
  }

  public Ontology refreshOntology(URL url, String id) {

    InputStream input;
    Ontology ret = null;

    try {
      input = url.openStream();
      OWLOntology ontology = manager.loadOntologyFromOntologyDocument(input);
      input.close();
      ret = new Ontology(this, ontology, id);
      ret.setResourceUrl(url.toString());
      ontologies.put(id, ret);
      iri2ns.put(ret.getPrefix(), id);

    } catch (OWLOntologyAlreadyExistsException e) {

      /*
       * already imported- wrap it and use it as is.
       */
      OWLOntology ont = manager.getOntology(e.getOntologyID().getOntologyIRI());
      if (ont != null && ontologies.get(id) == null) {
        Ontology ontology = new Ontology(this, ont, id);
        ontologies.put(id, ontology);
        iri2ns.put(ontology.getPrefix(), id);
      }

    } catch (Exception e) {

      /*
       * everything else is probably an error
       */
      throw new KlabIOException(e);
    }

    Ontology o = ontologies.get(id);
    if (o != null) {
      /*
       * FIXME create namespace
       */
      // Namespace ns = new Namespace(id, new File(url.getFile()), o);
      // // INamespaceDefinition ns = (INamespaceDefinition) _resolver
      // // .newLanguageObject(INamespace.class, null);
      // // ns.setId(id);
      // // ns.setResourceUrl(url.toString());
      // // ns.setOntology(o);
      // this.namespaces.put(id, ns);
    }

    return ret;
  }

  public ConceptImpl getDatatypeMapping(String string) {
    return this.xsdMappings.get(string);
  }

  public ConceptImpl registerDatatypeMapping(String xsd, ConceptImpl concept) {
    return this.xsdMappings.put(xsd, concept);
  }

  public void releaseOntology(Ontology ontology) {

    this.namespaces.remove(ontology.getName());
    var onto = ontologies.remove(ontology.getName());
    for (var concept : onto.getConcepts()) {
      if (concept instanceof ConceptImpl conceptImpl) {
        conceptsById.remove(conceptImpl.getId());
        owlClasses.remove(conceptImpl.getId());
      }
    }
    iri2ns.remove(((Ontology) ontology).getPrefix());
    manager.removeOntology(((Ontology) ontology).ontology);
  }

  public void clear() {
    Collection<String> keys = new HashSet<>(ontologies.keySet());
    for (String o : keys) {
      releaseOntology(getOntology(o));
    }
  }

  public Collection<Ontology> getOntologies(boolean includeInternal) {
    ArrayList<Ontology> ret = new ArrayList<>();
    for (Ontology o : ontologies.values()) {
      if (((Ontology) o).isInternal() && !includeInternal) continue;
      ret.add(o);
    }
    return ret;
  }

  public KimNamespace getNamespace(String ns) {
    return this.namespaces.get(ns);
  }

  public Collection<KimNamespace> getNamespaces() {
    return this.namespaces.values();
  }

  public Collection<Concept> listConcepts(boolean includeInternal) {

    ArrayList<Concept> ret = new ArrayList<>();

    for (Ontology o : ontologies.values()) {
      if (((Ontology) o).isInternal() && !includeInternal) continue;
      ret.addAll(o.getConcepts());
    }
    return ret;
  }

  public Concept getNothing() {
    if (this.nothing == null) {
      this.nothing = new ConceptImpl();
      this.nothing.getType().add(SemanticType.NOTHING);
      ((ConceptImpl) this.nothing)
          .setId(registerOwlClass(manager.getOWLDataFactory().getOWLNothing()));
      ((ConceptImpl) this.nothing).setUrn("owl:Nothing");
      ((ConceptImpl) this.nothing).setNamespace("owl");
    }
    return this.nothing;
  }

  public Collection<Concept> unwrap(OWLClassExpression cls) {

    Set<Concept> ret = new HashSet<>();
    if (cls instanceof OWLObjectIntersectionOf) {
      for (OWLClassExpression o : ((OWLObjectIntersectionOf) cls).getOperands()) {
        ret.addAll(unwrap(o));
      }
    } else if (cls instanceof OWLObjectUnionOf) {
      for (OWLClassExpression o : ((OWLObjectUnionOf) cls).getOperands()) {
        ret.addAll(unwrap(o));
      }
    } else if (cls instanceof OWLClass) {
      ret.add(getExistingOrCreate(cls.asOWLClass()));
    }
    return ret;
  }

  /**
   * Get the restricted classes only if the target concept of the restriction is the one passed.
   * This simply returns one class - TODO improve API.
   *
   * @param target
   * @param restricted
   * @return
   */
  public Concept getDirectRestrictedClass(Concept target, Property restricted) {
    OWLClass owl = getOWLClass(target);
    synchronized (owl) {
      for (OWLClassExpression s : owl.getSuperClasses(manager.getOntologies())) {
        if (s instanceof OWLQuantifiedRestriction) {
          if (getPropertyFor(
                      (OWLProperty<?, ?>) ((OWLQuantifiedRestriction<?, ?, ?>) s).getProperty())
                  .is(restricted, this)
              && ((OWLQuantifiedRestriction<?, ?, ?>) s).getFiller()
                  instanceof OWLClassExpression) {
            Collection<Concept> concepts =
                unwrap((OWLClassExpression) ((OWLQuantifiedRestriction<?, ?, ?>) s).getFiller());
            if (concepts != null) {
              return concepts.iterator().next();
            }
          }
        }
      }
    }
    return null;
  }

  /**
   * Return all the concepts directly restricted by this property.
   *
   * @param target
   * @param restricted
   * @return
   */
  public Collection<Concept> getDirectRestrictedClasses(Concept target, Property restricted) {
    Set<Concept> ret = new HashSet<>();
    OWLClass owl = getOWLClass(target);
    synchronized (owl) {
      for (OWLClassExpression s : owl.getSuperClasses(manager.getOntologies())) {
        if (s instanceof OWLQuantifiedRestriction) {
          if (getPropertyFor(
                      (OWLProperty<?, ?>) ((OWLQuantifiedRestriction<?, ?, ?>) s).getProperty())
                  .is(restricted, this)
              && ((OWLQuantifiedRestriction<?, ?, ?>) s).getFiller()
                  instanceof OWLClassExpression) {
            ret.addAll(
                unwrap((OWLClassExpression) ((OWLQuantifiedRestriction<?, ?, ?>) s).getFiller()));
          }
        }
      }
    }
    return ret;
  }

  /**
   * Return the concept or concepts (if a union) restricted by the passed object property in the
   * restriction closest to the passed concept in its asserted parent hierarchy.
   *
   * @param target
   * @param restricted
   * @return the concepts restricted in the target by the property
   */
  public Collection<Concept> getRestrictedClasses(Concept target, Property restricted) {
    return new SpecializingRestrictionVisitor(target, restricted, true, this).getResult();
  }

  public Collection<Concept> getRestrictedClasses(
      Concept target, Property restricted, boolean useSuperproperties) {
    return new SpecializingRestrictionVisitor(target, restricted, useSuperproperties, this)
        .getResult();
  }

  public void restrictSome(Concept target, Property property, Concept filler, Ontology ontology) {
    getTargetOntology(ontology, target, property, filler)
        .define(
            Collections.singleton(
                Axiom.SomeValuesFrom(
                    target.getNamespace() + ":" + target.getName(),
                    property.toString(),
                    filler.getNamespace() + ":" + filler.getName())));
  }

  public void restrictAll(
      Concept target,
      Property property,
      LogicalConnector how,
      Collection<Concept> fillers,
      Ontology ontology) {

    if (fillers.size() == 1) {
      restrictAll(target, property, fillers.iterator().next(), ontology);
      return;
    }

    if (!(how.equals(LogicalConnector.INTERSECTION) || how.equals(LogicalConnector.UNION))) {
      throw new IllegalArgumentException("connectors can only be union or intersection");
    }

    Set<OWLClassExpression> classes = new HashSet<>();
    for (Concept c : fillers) {
      classes.add(getOWLClass(c));
    }
    OWLDataFactory factory = manager.getOWLDataFactory();
    OWLClassExpression union =
        how.equals(LogicalConnector.UNION)
            ? factory.getOWLObjectUnionOf(classes)
            : factory.getOWLObjectIntersectionOf(classes);
    OWLClassExpression restriction =
        factory.getOWLObjectAllValuesFrom(property._owl.asOWLObjectProperty(), union);
    manager.addAxiom(
        (getTargetOntology(ontology, target, property, fillers)).ontology,
        factory.getOWLSubClassOfAxiom(getOWLClass(target), restriction));
  }

  public void restrictSome(
      Concept target,
      Property property,
      LogicalConnector how,
      Collection<Concept> fillers,
      Ontology ontology) {

    if (fillers.size() == 1) {
      restrictSome(target, property, fillers.iterator().next(), ontology);
      return;
    }

    if (!(how.equals(LogicalConnector.INTERSECTION) || how.equals(LogicalConnector.UNION))) {
      throw new IllegalArgumentException("connectors can only be union or intersection");
    }

    Set<OWLClassExpression> classes = new HashSet<>();
    for (Concept c : fillers) {
      classes.add(getOWLClass(c));
    }
    OWLDataFactory factory = manager.getOWLDataFactory();
    OWLClassExpression union =
        how.equals(LogicalConnector.UNION)
            ? factory.getOWLObjectUnionOf(classes)
            : factory.getOWLObjectIntersectionOf(classes);
    OWLClassExpression restriction =
        factory.getOWLObjectSomeValuesFrom(property._owl.asOWLObjectProperty(), union);
    manager.addAxiom(
        ((Ontology) property.getOntology(this)).ontology,
        factory.getOWLSubClassOfAxiom(getOWLClass(target), restriction));
  }

  public void restrictAll(Concept target, Property property, Concept filler, Ontology ontology) {
    getTargetOntology(ontology, target, property, filler)
        .define(
            Collections.singleton(
                Axiom.AllValuesFrom(
                    target.getNamespace() + ":" + target.getName(),
                    property.toString(),
                    filler.getNamespace() + ":" + filler.getName())));
  }

  public void restrictAtLeast(
      Concept target,
      Property property,
      LogicalConnector how,
      Collection<Concept> fillers,
      int min,
      Ontology ontology) {

    if (fillers.size() == 1) {
      restrictAtLeast(target, property, fillers.iterator().next(), min, ontology);
      return;
    }

    if (!(how.equals(LogicalConnector.INTERSECTION) || how.equals(LogicalConnector.UNION))) {
      throw new IllegalArgumentException("connectors can only be union or intersection");
    }

    Set<OWLClassExpression> classes = new HashSet<>();
    for (Concept c : fillers) {
      classes.add(getOWLClass(c));
    }
    OWLDataFactory factory = manager.getOWLDataFactory();
    OWLClassExpression union =
        how.equals(LogicalConnector.UNION)
            ? factory.getOWLObjectUnionOf(classes)
            : factory.getOWLObjectIntersectionOf(classes);
    OWLClassExpression restriction =
        factory.getOWLObjectMinCardinality(min, property._owl.asOWLObjectProperty(), union);
    manager.addAxiom(
        (getTargetOntology(ontology, target, property, fillers)).ontology,
        factory.getOWLSubClassOfAxiom(getOWLClass(target), restriction));
  }

  public void restrictAtLeast(
      Concept target, Property property, Concept filler, int min, Ontology ontology) {
    getTargetOntology(ontology, target, property, filler)
        .define(
            Collections.singleton(
                Axiom.AtLeastNValuesFrom(
                    target.getNamespace() + ":" + target.getName(),
                    property.toString(),
                    filler.getNamespace() + ":" + filler.getName(),
                    min)));
  }

  public void restrictAtMost(
      Concept target,
      Property property,
      LogicalConnector how,
      Collection<Concept> fillers,
      int max,
      Ontology ontology) {

    if (fillers.size() == 1) {
      restrictAtMost(target, property, fillers.iterator().next(), max, ontology);
      return;
    }
    if (!(how.equals(LogicalConnector.INTERSECTION) || how.equals(LogicalConnector.UNION))) {
      throw new IllegalArgumentException("connectors can only be union or intersection");
    }
    Set<OWLClassExpression> classes = new HashSet<>();
    for (Concept c : fillers) {
      classes.add(getOWLClass(c));
    }
    OWLDataFactory factory = manager.getOWLDataFactory();
    OWLClassExpression union =
        how.equals(LogicalConnector.UNION)
            ? factory.getOWLObjectUnionOf(classes)
            : factory.getOWLObjectIntersectionOf(classes);
    OWLClassExpression restriction =
        factory.getOWLObjectMaxCardinality(
            max, ((Property) property)._owl.asOWLObjectProperty(), union);
    manager.addAxiom(
        (getTargetOntology(ontology, target, property, fillers)).ontology,
        factory.getOWLSubClassOfAxiom(getOWLClass(target), restriction));
  }

  public void restrictAtMost(
      Concept target, Property property, Concept filler, int max, Ontology ontology) {
    getTargetOntology(ontology, target, property, filler)
        .define(
            Collections.singleton(
                Axiom.AtMostNValuesFrom(
                    target.getNamespace() + ":" + target.getName(),
                    property.toString(),
                    filler.getNamespace() + ":" + filler.getName(),
                    max)));
  }

  public void restrictExactly(
      Concept target,
      Property property,
      LogicalConnector how,
      Collection<Concept> fillers,
      int howmany,
      Ontology ontology) {

    if (fillers.size() == 1) {
      restrictExactly(target, property, fillers.iterator().next(), howmany, ontology);
      return;
    }
    if (!(how.equals(LogicalConnector.INTERSECTION) || how.equals(LogicalConnector.UNION))) {
      throw new IllegalArgumentException("connectors can only be union or intersection");
    }
    Set<OWLClassExpression> classes = new HashSet<>();
    for (Concept c : fillers) {
      classes.add(getOWLClass(c));
    }
    OWLDataFactory factory = manager.getOWLDataFactory();
    OWLClassExpression union =
        how.equals(LogicalConnector.UNION)
            ? factory.getOWLObjectUnionOf(classes)
            : factory.getOWLObjectIntersectionOf(classes);
    OWLClassExpression restriction =
        factory.getOWLObjectExactCardinality(howmany, property._owl.asOWLObjectProperty(), union);
    manager.addAxiom(
        (getTargetOntology(ontology, target, property, fillers)).ontology,
        factory.getOWLSubClassOfAxiom(getOWLClass(target), restriction));
  }

  public void restrictExactly(
      Concept target, Property property, Concept filler, int howMany, Ontology ontology) {
    getTargetOntology(ontology, target, property, filler)
        .define(
            Collections.singleton(
                Axiom.ExactlyNValuesFrom(
                    target.getNamespace() + ":" + target.getName(),
                    property.toString(),
                    filler.getNamespace() + ":" + filler.getName(),
                    howMany)));
  }

  /**
   * Return whether the restriction on type involving concept is optional. If there is no such
   * restriction, return false.
   *
   * @param type
   * @param concept
   * @return true if restriction exists and is optional
   */
  public boolean isRestrictionOptional(Concept type, Concept concept) {
    return new ConceptRestrictionVisitor(type, concept, this).isOptional();
  }

  /**
   * Return whether the restriction on type involving concept is a negation. If there is no such
   * restriction, return false.
   *
   * @param type
   * @param concept
   * @return true if restriction exists and is a negation
   */
  public boolean isRestrictionDenied(Concept type, Concept concept) {
    return new ConceptRestrictionVisitor(type, concept, this).isDenied();
  }

  public org.integratedmodelling.klab.api.services.Reasoner reasoner() {
    return scope.getService(org.integratedmodelling.klab.api.services.Reasoner.class);
  }

  public ResourcesService resources() {
    return scope.getService(ResourcesService.class);
  }

  public Property getRestrictingProperty(Concept type, Concept concept) {
    ConceptRestrictionVisitor visitor = new ConceptRestrictionVisitor(type, concept, this);
    if (visitor.getRestriction() != null) {
      return getPropertyFor((OWLProperty<?, ?>) visitor.getRestriction().getProperty());
    }
    return null;
  }

  public Concept getExistingOrCreate(OWLClass owl) {

    if (owlClasses.containsValue(owl)) {
      return conceptsById.get(owlClasses.inverse().get(owl));
    }

    String conceptId = owl.getIRI().getFragment();
    String namespace = getConceptSpace(owl.getIRI());

    Concept ret = null;
    Ontology ontology = ontologies.get(namespace);
    if (ontology == null) {
      throw new IllegalArgumentException("getExistingOrCreate: ontology not found: " + namespace);
    }

    ret = ontology.getConcept(conceptId);
    if (ret == null) {
      ret = ((Ontology) ontology).createConcept(conceptId, emptyType);
    }

    return ret;
  }

  public Concept getIntersection(
      Collection<Concept> concepts, Ontology destination, Collection<SemanticType> stype) {

    EnumSet<SemanticType> type = EnumSet.copyOf(stype);
    type.add(SemanticType.INTERSECTION);

    List<String> ids = new ArrayList<>();
    Set<OWLClassExpression> classes = new HashSet<>();
    for (Concept c : concepts) {
      classes.add(getOWLClass(c));
      ids.add(c.toString().replace(':', '_'));
    }

    Collections.sort(ids);
    StringBuilder id = new StringBuilder();
    for (String iid : ids) {
      id.append((id.isEmpty()) ? "" : "__and__").append(iid);
    }

    Concept ret = ((Ontology) destination).getConcept(id.toString());
    if (ret != null) {
      return ret;
    }

    OWLDataFactory factory = manager.getOWLDataFactory();
    OWLClassExpression union = factory.getOWLObjectIntersectionOf(classes);
    ret = (ConceptImpl) ((Ontology) destination).createConcept(id.toString(), type);
    manager.addAxiom(
        ((Ontology) destination).ontology, factory.getOWLSubClassOfAxiom(getOWLClass(ret), union));

    return ret;
  }

  public Concept getUnion(
      Collection<Concept> concepts, Ontology destination, Collection<SemanticType> stype) {

    EnumSet<SemanticType> type = EnumSet.copyOf(stype);
    type.add(SemanticType.UNION);

    List<String> ids = new ArrayList<>();
    Set<OWLClassExpression> classes = new HashSet<>();
    for (Concept c : concepts) {
      classes.add(getOWLClass(c));
      ids.add(c.toString().replace(':', '_'));
    }

    Collections.sort(ids);
    StringBuilder id = new StringBuilder();
    for (String iid : ids) {
      id.append((id.isEmpty()) ? "" : "__or__").append(iid);
    }

    Concept ret = ((Ontology) destination).getConcept(id.toString());
    if (ret != null) {
      return ret;
    }

    OWLDataFactory factory = manager.getOWLDataFactory();
    OWLClassExpression union = factory.getOWLObjectUnionOf(classes);
    ret = (ConceptImpl) ((Ontology) destination).createConcept(id.toString(), type);
    manager.addAxiom(
        ((Ontology) destination).ontology, factory.getOWLSubClassOfAxiom(getOWLClass(ret), union));

    return ret;
  }

  public ConceptImpl getConsequentialityEvent(Collection<Concept> concepts, Ontology destination) {
    // TODO
    return null;
  }

  public String importExternal(String url, String prefix, Channel monitor) {

    // TODO must handle the situation when the prefix is already there better than
    // this.

    if (iri2ns.containsKey(url)) {
      return iri2ns.get(url);
    }

    File out = new File(/*
         * Configuration.INSTANCE.getDataPath("knowledge/.imports") + File.separator +
         */ prefix + ".owl");
    try {
      Utils.URLs.copyChanneled(new URI(url).toURL(), out);
      loadInternal(out, prefix, true, monitor);
    } catch (Exception e) {
      monitor.error(e);
      return null;
    }

    return prefix;
  }

  public Concept getNonsemanticPeer(String name, Artifact.Type type) {

    String conceptId =
        Utils.Strings.capitalize(type.name().toLowerCase()) + CamelCase.toUpperCamelCase(name, '.');
    SemanticType qualityType =
        switch (type) {
          case TEXT -> SemanticType.CATEGORY;
          case NUMBER -> SemanticType.QUANTITY;
          case CONCEPT -> SemanticType.CLASS;
          case BOOLEAN -> SemanticType.PRESENCE;
          case OBJECT -> SemanticType.SUBJECT;
          case EVENT -> SemanticType.EVENT;
          default ->
              throw new IllegalArgumentException(
                  "wrong type passed for non-semantic peer generation:" + " " + type);
        };
    EnumSet<SemanticType> identity =
        type.isCountable()
            ? EnumSet.of(
                SemanticType.SUBJECT,
                SemanticType.OBSERVABLE,
                SemanticType.DIRECT_OBSERVABLE,
                SemanticType.COUNTABLE)
            : EnumSet.of(SemanticType.QUALITY, SemanticType.OBSERVABLE, qualityType);

    Concept ret = nonSemanticConcepts.getConcept(conceptId);
    if (ret != null) {
      if (!ret.is(qualityType)) {
        throw new KlabInternalErrorException(
            "non-semantic peer concept for "
                + name
                + " was declared previously with a "
                + "different type");
      }
      return ret;
    }

    nonSemanticConcepts.define(
        Collections.singletonList(Axiom.ClassAssertion(conceptId, identity)));

    return nonSemanticConcepts.getConcept(conceptId);
  }

  /**
   * True if the passed object has true semantics, i.e. is not a non-semantic object.
   *
   * @param observable
   * @return
   */
  public boolean isSemantic(Semantics observable) {
    return !observable.getNamespace().equals(nonSemanticConcepts.getName());
  }

  public Ontology readOntology(String string) {
    try {
      return new Ontology(
          this, manager.loadOntology(IRI.create(string)), Utils.URLs.getURLBaseName(string));
    } catch (OWLOntologyCreationException e) {
      throw new KlabIOException(e);
    }
  }

  public void restrict(
      Concept target,
      Property property,
      LogicalConnector how,
      Collection<Concept> fillers,
      Ontology ontology)
      throws KlabValidationException {

    /*
     * divide up in bins according to base trait; take property from annotation;
     * restrict each group.
     */
    Map<Concept, List<Concept>> pairs = new HashMap<>();
    for (Concept t : fillers) {
      Concept base =
          scope
              .getService(org.integratedmodelling.klab.api.services.Reasoner.class)
              .baseParentTrait(t);
      if (base == null) {
        if (CoreOntology.isCore(t)) {
          base = t;
          pairs.put(base, new ArrayList<>());
        } else {
          System.err.println("HOSTIA no  base trait for " + t);
          continue;
        }
      } else if (!pairs.containsKey(base)) {
        pairs.put(base, new ArrayList<>());
      }
      pairs.get(base).add(t);
    }

    for (Concept base : pairs.keySet()) {

      String prop =
          base.getMetadata().get(CoreOntology.NS.TRAIT_RESTRICTING_PROPERTY, String.class);
      if (prop == null || getProperty(prop) == null) {
        if (CoreOntology.isCore(base)) {
          // use the property itself for an abstract core property
          prop = CoreOntology.getBaseTraitProperty(base);
        } else if (base.is(SemanticType.SUBJECTIVE)) {
          // we can assign any subjective traits to anything
          prop = CoreOntology.NS.HAS_SUBJECTIVE_TRAIT_PROPERTY;
        } else {
          throw new KlabValidationException("cannot find a property to restrict for trait " + base);
        }
      }
      restrictSome(target, getProperty(prop), how, pairs.get(base), (Ontology) ontology);
    }
  }

  /**
   * Set the "applied to" clause for a trait or observable. Should also validate.
   *
   * @param type
   * @param applicables
   */
  public void setApplicableObservables(Concept type, List<Concept> applicables, Ontology ontology) {
    // TODO validate
    restrictSome(
        type,
        getProperty(CoreOntology.NS.APPLIES_TO_PROPERTY),
        LogicalConnector.UNION,
        applicables,
        ontology);
  }

  public void defineRelationship(
      Concept relationship, Concept source, Concept target, Ontology ontology) {
    Property hasSource = getProperty(CoreOntology.NS.IMPLIES_SOURCE_PROPERTY);
    Property hasTarget = getProperty(CoreOntology.NS.IMPLIES_DESTINATION_PROPERTY);
    restrictSome(
        relationship, hasSource, LogicalConnector.UNION, Collections.singleton(source), ontology);
    restrictSome(
        relationship, hasTarget, LogicalConnector.UNION, Collections.singleton(target), ontology);
  }

  /**
   * Analyze an observable concept and return the main observable with all the original identities
   * and realms but no attributes; separately, return the list of the attributes that were removed.
   *
   * @param observable
   * @return attribute profile
   */
  public Pair<Concept, Collection<Concept>> separateAttributes(Concept observable) {

    var reas = scope.getService(org.integratedmodelling.klab.api.services.Reasoner.class);
    Concept obs = reas.coreObservable(observable);
    ArrayList<Concept> tret = new ArrayList<>();
    ArrayList<Concept> keep = new ArrayList<>();

    for (Concept zt : reas.traits(observable)) {
      if (reas.is(zt, getConcept(CoreOntology.NS.CORE_IDENTITY))
          || reas.is(zt, getConcept(CoreOntology.NS.CORE_REALM))) {
        keep.add(zt);
      } else {
        tret.add(zt);
      }
    }

    Concept root = obs;
    // Services.INSTANCE.getReasoner().declareObservable((obs == null ? observable :
    // obs), keep,
    // Services.INSTANCE.getReasoner().context(observable),
    // Services.INSTANCE.getReasoner().inherent(observable));

    return Pair.of(root, tret);
  }

  public void addTrait(Concept main, Concept trait, Ontology ontology) {
    Property property = null;
    if (trait.is(SemanticType.IDENTITY)) {
      property = getProperty(CoreOntology.NS.HAS_IDENTITY_PROPERTY);
    } else if (trait.is(SemanticType.REALM)) {
      property = getProperty(CoreOntology.NS.HAS_REALM_PROPERTY);
    } else if (trait.is(SemanticType.ATTRIBUTE)) {
      property = getProperty(CoreOntology.NS.HAS_ATTRIBUTE_PROPERTY);
    }
    if (property != null) {
      restrict(
          main,
          property,
          LogicalConnector.UNION,
          Collections.singleton(trait),
          (Ontology) ontology);
    }
  }

  public Concept makeNegation(Concept attribute) {

    var reas = scope.getService(org.integratedmodelling.klab.api.services.Reasoner.class);

    String orig = attribute.getMetadata().get(NS.IS_NEGATION_OF, String.class);
    if (orig != null) {
      return getConcept(orig);
    }

    Ontology aontology = getOntology(attribute.getNamespace());
    String prop =
        attribute.getMetadata().get(CoreOntology.NS.TRAIT_RESTRICTING_PROPERTY, String.class);
    String conceptId = "Not" + getCleanId(attribute);
    Concept ret = aontology.getConcept(conceptId);
    Concept parent = reas.parent(attribute);

    if (ret == null) {

      EnumSet<SemanticType> newType = EnumSet.copyOf(attribute.getType());

      aontology.add(Axiom.ClassAssertion(conceptId, newType));
      aontology.add(Axiom.SubClass(parent.getNamespace() + ":" + parent.getName(), conceptId));
      aontology.add(Axiom.AnnotationAssertion(conceptId, CoreOntology.NS.BASE_DECLARATION, "true"));
      aontology.add(
          Axiom.AnnotationAssertion(
              conceptId,
              CoreOntology.NS.REFERENCE_NAME_PROPERTY,
              "not_" + attribute.getReferenceName()));
      aontology.add(
          Axiom.AnnotationAssertion(conceptId, "rdfs:label", "Not" + getCleanId(attribute)));
      aontology.add(
          Axiom.AnnotationAssertion(
              conceptId, NS.CONCEPT_DEFINITION_PROPERTY, "not  " + attribute.getUrn()));

      if (prop != null) {
        aontology.add(Axiom.AnnotationAssertion(conceptId, NS.TRAIT_RESTRICTING_PROPERTY, prop));
      }

      aontology.define();

      ret = aontology.getConcept(conceptId);

      restrictSome(
          ret, getProperty(CoreOntology.NS.IS_NEGATION_OF), attribute, (Ontology) aontology);
    }

    return ret;
  }

  /**
   * Return all atomic components of a concept, recursing any logical combination irrespective of
   * the operator.
   *
   * @param trigger
   * @return
   */
  public Collection<Concept> flattenOperands(Concept trigger) {
    Set<Concept> ret = new HashSet<>();
    _flattenOperands(trigger, ret);
    return ret;
  }

  void _flattenOperands(Concept trigger, Set<Concept> ret) {
    var reas = scope.getService(org.integratedmodelling.klab.api.services.Reasoner.class);
    if (trigger.is(SemanticType.INTERSECTION) || trigger.is(SemanticType.UNION)) {
      for (Concept tr : reas.operands(trigger)) {
        _flattenOperands(tr, ret);
      }
    } else {
      ret.add(trigger);
    }
  }

  public static String getCleanId(Concept main) {
    String id = main.getMetadata().get(Metadata.DC_LABEL, String.class);
    if (id == null) {
      id = main.getName();
    }
    return id;
  }

  OWLClass getOWLClass(Concept concept) {
    return owlClasses.get(((ConceptImpl) concept).getId());
  }

  /**
   * Create a subclass of this concept in the internal ontology with the same metadata and type as
   * the original. This is called when constructing a new concept that will be restricted with new
   * restrictions not appropriate for the original concept. The URN will be set later and can be
   * left blank in the returned concept but the final URN is passed for the class annotation.
   *
   * <p>The semantic type of the concept must be finalized when this is called.
   *
   * <p>The concept obtained must be passed back to {@link #finalizeConcept(ConceptImpl)} once the
   * definition is complete to be registered with the reasoner and for its full metadata to be
   * completed.
   *
   * @param ret
   * @return
   */
  public synchronized Concept makeSubclass(Concept ret, String urn) {

    var ontology = getOntology(INTERNAL_ONTOLOGY_ID);
    var name = ontology.createIdForDefinition(urn);
    List<Axiom> ax = new ArrayList<>();
    ax.add(Axiom.ClassAssertion(name, ret.getType()));
    ax.add(Axiom.SubClass(ret.getNamespace() + ":" + ret.getName(), name));
    ax.add(Axiom.AnnotationAssertion(name, NS.CONCEPT_DEFINITION_PROPERTY, urn));
    ontology.define(ax);
    return ontology.getConcept(name);
  }

  public synchronized void finalizeConcept(ConceptImpl concept) {
    if (reasoner != null) {
      if (!reasoner.isSatisfiable(getOWLClass(concept))) {
          concept.error("concept definition is semantically inconsistent");
      }
    }
  }

  /**
   * Turn a concept into its change if it's not already one, implementing the corresponding semantic
   * operator.
   *
   * @param concept the untransformed concept
   * @return the transformed concept
   */
  public synchronized Concept makeChange(Concept concept) {

    if (concept.is(SemanticType.CHANGE)) {
      return concept;
    }

    String cName = getCleanId(concept) + "Change";

    if (!concept.is(SemanticType.QUALITY)) {
      return null;
    }

    // this.hasUnaryOp = true;
    var reas = scope.getService(org.integratedmodelling.klab.api.services.Reasoner.class);

    String definition = UnarySemanticOperator.CHANGE.declaration[0] + " " + concept.getUrn();
    Ontology ontology = getOntology(concept.getNamespace());
    String conceptId = ontology.getIdForDefinition(definition);
    Concept context = reas.inherent(concept);

    if (conceptId == null) {

      conceptId = ontology.createIdForDefinition(definition);

      String reference =
          UnarySemanticOperator.CHANGE.getReferenceName(concept.getReferenceName(), null);

      Set<SemanticType> newType = UnarySemanticOperator.CHANGE.apply(concept.getType());

      ArrayList<Axiom> ax = new ArrayList<>();
      ax.add(Axiom.ClassAssertion(conceptId, newType));
      ax.add(Axiom.SubClass(NS.CORE_CHANGE, conceptId));
      ax.add(Axiom.AnnotationAssertion(conceptId, NS.REFERENCE_NAME_PROPERTY, reference));
      ax.add(Axiom.AnnotationAssertion(conceptId, NS.BASE_DECLARATION, "true"));
      ax.add(Axiom.AnnotationAssertion(conceptId, "rdfs:label", cName));
      ax.add(Axiom.AnnotationAssertion(conceptId, "rdfs:label", cName));
      ax.add(Axiom.AnnotationAssertion(conceptId, NS.CONCEPT_DEFINITION_PROPERTY, definition));
      ontology.define(ax);

      Concept ret = ontology.getConcept(conceptId);

      restrictSome(
          ret, getProperty(CoreOntology.NS.DESCRIBES_OBSERVABLE_PROPERTY), concept, ontology);
      restrictSome(ret, getProperty(CoreOntology.NS.CHANGES_PROPERTY), concept, ontology);

      /*
       * inherency of the change is the context of the quality it describes, as the change is in the
       * independent and the quality change is a consequence of it.
       */
      if (context != null) {
        restrictSome(ret, getProperty(NS.IS_INHERENT_TO_PROPERTY), context, ontology);
      }
    }

    return ontology.getConcept(conceptId);
  }

  /**
   * Turn a concept into its change if it's not already one, implementing the corresponding semantic
   * operator.
   *
   * @param concept the untransformed concept
   * @return the transformed concept
   */
  public synchronized Concept makeRate(Concept concept) {

    if (concept.is(SemanticType.RATE)) {
      return concept;
    }

    String cName = getCleanId(concept) + "ChangeRate";

    if (!concept.is(SemanticType.QUALITY)) {
      return null;
    }

    // this.hasUnaryOp = true;
    var reas = scope.getService(org.integratedmodelling.klab.api.services.Reasoner.class);

    String definition = UnarySemanticOperator.RATE.declaration[0] + " " + concept.getUrn();
    Ontology ontology = getOntology(concept.getNamespace());
    String conceptId = ontology.getIdForDefinition(definition);
    Concept context = reas.inherent(concept);

    if (conceptId == null) {

      conceptId = ontology.createIdForDefinition(definition);

      String reference =
          UnarySemanticOperator.RATE.getReferenceName(concept.getReferenceName(), null);

      Set<SemanticType> newType = UnarySemanticOperator.RATE.apply(concept.getType());

      ArrayList<Axiom> ax = new ArrayList<>();
      ax.add(Axiom.ClassAssertion(conceptId, newType));
      ax.add(Axiom.SubClass(NS.CORE_CHANGE_RATE, conceptId));
      ax.add(Axiom.AnnotationAssertion(conceptId, NS.REFERENCE_NAME_PROPERTY, reference));
      ax.add(Axiom.AnnotationAssertion(conceptId, NS.BASE_DECLARATION, "true"));
      ax.add(Axiom.AnnotationAssertion(conceptId, "rdfs:label", cName));
      ax.add(Axiom.AnnotationAssertion(conceptId, "rdfs:label", cName));
      ax.add(Axiom.AnnotationAssertion(conceptId, NS.CONCEPT_DEFINITION_PROPERTY, definition));
      ontology.define(ax);

      Concept ret = ontology.getConcept(conceptId);

      restrictSome(
          ret, getProperty(CoreOntology.NS.DESCRIBES_OBSERVABLE_PROPERTY), concept, ontology);

      /*
       * context of the change is the same context as the quality it describes - FIXME
       * this shouldn't be needed as the inherency is an alternative place to look for
       * context.
       */
      if (context != null) {
        restrictSome(ret, getProperty(NS.IS_INHERENT_TO_PROPERTY), context, ontology);
      }
    }

    return ontology.getConcept(conceptId);
  }

  /**
   * Turn a concept into its change if it's not already one, implementing the corresponding semantic
   * operator.
   *
   * @param concept the untransformed concept
   * @return the transformed concept
   */
  public synchronized Concept makeChanged(Concept concept) {

    if (concept.is(SemanticType.CHANGED)) {
      return concept;
    }

    String cName = "Changed" + getCleanId(concept);

    if (!concept.is(SemanticType.QUALITY)) {
      return null;
    }

    // this.hasUnaryOp = true;
    var reas = scope.getService(org.integratedmodelling.klab.api.services.Reasoner.class);

    String definition = UnarySemanticOperator.CHANGED.declaration[0] + " " + concept.getUrn();
    Ontology ontology = getOntology(concept.getNamespace());
    String conceptId = ontology.getIdForDefinition(definition);
    Concept context = reas.inherent(concept);

    if (conceptId == null) {

      conceptId = ontology.createIdForDefinition(definition);

      String reference =
          UnarySemanticOperator.CHANGED.getReferenceName(concept.getReferenceName(), null);

      Set<SemanticType> newType = UnarySemanticOperator.CHANGED.apply(concept.getType());

      ArrayList<Axiom> ax = new ArrayList<>();
      ax.add(Axiom.ClassAssertion(conceptId, newType));
      ax.add(Axiom.SubClass(NS.CORE_EVENT, conceptId));
      ax.add(Axiom.AnnotationAssertion(conceptId, NS.REFERENCE_NAME_PROPERTY, reference));
      ax.add(Axiom.AnnotationAssertion(conceptId, NS.BASE_DECLARATION, "true"));
      ax.add(Axiom.AnnotationAssertion(conceptId, "rdfs:label", cName));
      ax.add(Axiom.AnnotationAssertion(conceptId, "rdfs:label", cName));
      ax.add(Axiom.AnnotationAssertion(conceptId, NS.CONCEPT_DEFINITION_PROPERTY, definition));
      ontology.define(ax);

      Concept ret = ontology.getConcept(conceptId);

      restrictSome(
          ret, getProperty(CoreOntology.NS.DESCRIBES_OBSERVABLE_PROPERTY), concept, ontology);
      restrictSome(ret, getProperty(CoreOntology.NS.CHANGED_PROPERTY), concept, ontology);

      /*
       * context of the change event is the same context as the quality it describes
       */
      if (context != null) {
        restrictSome(ret, getProperty(NS.IS_INHERENT_TO_PROPERTY), context, ontology);
      }
    }

    return ontology.getConcept(conceptId);
  }

  /**
   * Turn a concept into its count/numerosity if it's not already one, implementing the
   * corresponding semantic operator. Also makes the original concept the numerosity's inherent.
   *
   * @param concept the untransformed concept
   * @return the transformed concept
   */
  public synchronized Concept makeCount(Concept concept) {

    if (concept.is(SemanticType.NUMEROSITY)) {
      return concept;
    }

    /*
     * first, ensure we're counting countable things.
     */
    if (!concept.is(SemanticType.COUNTABLE)) {
      throw new KlabValidationException("cannot count a non-countable observable");
    }

    // this.hasUnaryOp = true;

    String cName = getCleanId(concept) + "Count";

    /*
     * make a ConceptCount if not there, and ensure it's a continuously quantifiable
     * quality. Must be in same ontology as the original concept.
     */
    String definition = UnarySemanticOperator.COUNT.declaration[0] + " " + concept.getUrn();
    String reference =
        UnarySemanticOperator.COUNT.getReferenceName(concept.getReferenceName(), null);
    Ontology ontology = getOntology(concept.getNamespace());
    String conceptId = ontology.getIdForDefinition(definition);

    if (conceptId == null) {

      conceptId = ontology.createIdForDefinition(definition);

      Set<SemanticType> newType = UnarySemanticOperator.COUNT.apply(concept.getType());
      ArrayList<Axiom> ax = new ArrayList<>();
      ax.add(Axiom.ClassAssertion(conceptId, newType));
      ax.add(Axiom.SubClass(NS.CORE_COUNT, conceptId));
      ax.add(Axiom.AnnotationAssertion(conceptId, NS.REFERENCE_NAME_PROPERTY, reference));
      ax.add(Axiom.AnnotationAssertion(conceptId, NS.BASE_DECLARATION, "true"));
      ax.add(Axiom.AnnotationAssertion(conceptId, "rdfs:label", cName));
      ax.add(Axiom.AnnotationAssertion(conceptId, NS.CONCEPT_DEFINITION_PROPERTY, definition));
      ontology.define(ax);
      Concept ret = ontology.getConcept(conceptId);

      /*
       * numerosity is inherent to the thing that's counted.
       */
      restrictSome(ret, getProperty(NS.DESCRIBES_OBSERVABLE_PROPERTY), concept, ontology);
    }

    return ontology.getConcept(conceptId);
  }

  /**
   * Turn a concept into the distance from it if it's not already one, implementing the
   * corresponding semantic operator.
   *
   * @param concept the untransformed concept
   * @return the transformed concept
   */
  public synchronized Concept makeDistance(Concept concept) {

    if (concept.is(SemanticType.DISTANCE)) {
      return concept;
    }

    if (!concept.is(SemanticType.COUNTABLE)) {
      throw new KlabValidationException(
          "cannot compute the distance to a non-countable observable");
    }

    // this.hasUnaryOp = true;

    String cName = "DistanceTo" + getCleanId(concept);
    String definition = UnarySemanticOperator.DISTANCE.declaration[0] + " " + concept.getUrn();
    Ontology ontology = getOntology(concept.getNamespace());
    String conceptId = ontology.getIdForDefinition(definition);

    if (conceptId == null) {

      conceptId = ontology.createIdForDefinition(definition);
      String reference =
          UnarySemanticOperator.DISTANCE.getReferenceName(concept.getReferenceName(), null);

      Set<SemanticType> newType = UnarySemanticOperator.DISTANCE.apply(concept.getType());

      ArrayList<Axiom> ax = new ArrayList<>();
      ax.add(Axiom.ClassAssertion(conceptId, newType));
      ax.add(Axiom.SubClass(NS.CORE_DISTANCE, conceptId));
      ax.add(Axiom.AnnotationAssertion(conceptId, NS.REFERENCE_NAME_PROPERTY, reference));
      ax.add(Axiom.AnnotationAssertion(conceptId, NS.BASE_DECLARATION, "true"));
      ax.add(Axiom.AnnotationAssertion(conceptId, "rdfs:label", cName));
      ax.add(Axiom.AnnotationAssertion(conceptId, NS.CONCEPT_DEFINITION_PROPERTY, definition));
      ontology.define(ax);

      Concept ret = ontology.getConcept(conceptId);

      /*
       * distance is inherent to the thing that's present.
       */
      restrictSome(ret, getProperty(NS.DESCRIBES_OBSERVABLE_PROPERTY), concept, ontology);
    }

    return ontology.getConcept(conceptId);
  }

  /**
   * Turn a concept into its presence if it's not already one, implementing the corresponding
   * semantic operator. Also makes the original concept the numerosity's inherent.
   *
   * @param concept the untransformed concept
   * @return the transformed concept
   */
  public synchronized Concept makePresence(Concept concept) {

    if (concept.is(SemanticType.PRESENCE)) {
      return concept;
    }

    if (concept.is(SemanticType.QUALITY)
        || concept.is(SemanticType.CONFIGURATION)
        || concept.is(SemanticType.TRAIT)
        || concept.is(SemanticType.ROLE)) {
      throw new KlabValidationException(
          "presence can be observed only for subjects, events, processes and relationships");
    }

    // this.hasUnaryOp = true;

    String cName = getCleanId(concept) + "Presence";
    String definition = UnarySemanticOperator.PRESENCE.declaration[0] + " " + concept.getUrn();
    Ontology ontology = getOntology(concept.getNamespace());
    String conceptId = ontology.getIdForDefinition(definition);

    if (conceptId == null) {

      conceptId = ontology.createIdForDefinition(definition);

      String reference =
          UnarySemanticOperator.PRESENCE.getReferenceName(concept.getReferenceName(), null);

      Set<SemanticType> newType = UnarySemanticOperator.PRESENCE.apply(concept.getType());
      ArrayList<Axiom> ax = new ArrayList<>();
      ax.add(Axiom.ClassAssertion(conceptId, newType));
      ax.add(Axiom.SubClass(NS.CORE_PRESENCE, conceptId));
      ax.add(Axiom.AnnotationAssertion(conceptId, NS.BASE_DECLARATION, "true"));
      ax.add(Axiom.AnnotationAssertion(conceptId, NS.REFERENCE_NAME_PROPERTY, reference));
      ax.add(Axiom.AnnotationAssertion(conceptId, "rdfs:label", cName));
      ontology.define(ax);
      Concept ret = ontology.getConcept(conceptId);

      /*
       * presence is inherent to the thing that's present.
       */
      restrictSome(ret, getProperty(NS.DESCRIBES_OBSERVABLE_PROPERTY), concept, ontology);
    }

    return ontology.getConcept(conceptId);
  }

  /**
   * Turn a concept into its occurrence (probability of presence) if it's not already one,
   * implementing the corresponding semantic operator. Also makes the original concept the
   * numerosity's inherent.
   *
   * @param concept the untransformed concept. Must be a direct observable.
   * @return the transformed concept
   */
  public synchronized Concept makeOccurrence(Concept concept) {

    if (concept.is(SemanticType.OCCURRENCE)) {
      return concept;
    }

    if (!concept.is(SemanticType.DIRECT_OBSERVABLE)) {
      throw new KlabValidationException(
          "occurrences (probability of presence) can be observed only for subjects, events, "
              + "processes and "
              + "relationships");
    }

    // this.hasUnaryOp = true;

    String cName = getCleanId(concept) + "Occurrence";
    String definition = UnarySemanticOperator.OCCURRENCE.declaration[0] + " " + concept.getUrn();
    Ontology ontology = getOntology(concept.getNamespace());
    String conceptId = ontology.getIdForDefinition(definition);

    if (conceptId == null) {

      String reference =
          UnarySemanticOperator.OCCURRENCE.getReferenceName(concept.getReferenceName(), null);

      conceptId = ontology.createIdForDefinition(definition);
      Set<SemanticType> newType = UnarySemanticOperator.OCCURRENCE.apply(concept.getType());

      ArrayList<Axiom> ax = new ArrayList<>();
      ax.add(Axiom.ClassAssertion(conceptId, newType));
      ax.add(Axiom.SubClass(NS.CORE_OCCURRENCE, conceptId));
      ax.add(Axiom.AnnotationAssertion(conceptId, NS.REFERENCE_NAME_PROPERTY, reference));
      ax.add(Axiom.AnnotationAssertion(conceptId, NS.BASE_DECLARATION, "true"));
      ax.add(Axiom.AnnotationAssertion(conceptId, "rdfs:label", cName));
      ax.add(Axiom.AnnotationAssertion(conceptId, NS.CONCEPT_DEFINITION_PROPERTY, definition));
      ontology.define(ax);
      Concept ret = ontology.getConcept(conceptId);

      /*
       * occurrence is inherent to the event that's possible.
       */
      restrictSome(ret, getProperty(NS.DESCRIBES_OBSERVABLE_PROPERTY), concept, ontology);
    }

    return ontology.getConcept(conceptId);
  }

  /**
   * Turn a concept into its probability if it's not already one, implementing the corresponding
   * semantic operator. Also makes the original concept the numerosity's inherent.
   *
   * @param concept the untransformed concept. Must be an event.
   * @return the transformed concept
   */
  public synchronized Concept makeMagnitude(Concept concept) {

    if (concept.is(SemanticType.MAGNITUDE)) {
      return concept;
    }

    if (Sets.intersection(concept.getType(), SemanticType.CONTINUOUS_QUALITY_TYPES).size() == 0) {
      throw new KlabValidationException(
          "magnitudes can only be observed only for quantifiable " + "qualities");
    }

    // this.hasUnaryOp = true;

    String cName = getCleanId(concept) + "Magnitude";
    String definition = UnarySemanticOperator.MAGNITUDE.declaration[0] + " " + concept.getUrn();
    Ontology ontology = getOntology(concept.getNamespace());
    String conceptId = ontology.getIdForDefinition(definition);

    if (conceptId == null) {

      conceptId = ontology.createIdForDefinition(definition);

      String reference =
          UnarySemanticOperator.MAGNITUDE.getReferenceName(concept.getReferenceName(), null);

      Set<SemanticType> newType = UnarySemanticOperator.MAGNITUDE.apply(concept.getType());

      ArrayList<Axiom> ax = new ArrayList<>();
      ax.add(Axiom.ClassAssertion(conceptId, newType));
      ax.add(Axiom.SubClass(NS.CORE_MAGNITUDE, conceptId));
      ax.add(Axiom.AnnotationAssertion(conceptId, NS.REFERENCE_NAME_PROPERTY, reference));
      ax.add(Axiom.AnnotationAssertion(conceptId, NS.BASE_DECLARATION, "true"));
      ax.add(Axiom.AnnotationAssertion(conceptId, "rdfs:label", cName));
      ax.add(Axiom.AnnotationAssertion(conceptId, NS.CONCEPT_DEFINITION_PROPERTY, definition));
      ontology.define(ax);
      Concept ret = ontology.getConcept(conceptId);

      /*
       * probability is inherent to the event that's possible.
       */
      restrictSome(ret, getProperty(NS.DESCRIBES_OBSERVABLE_PROPERTY), concept, ontology);
    }

    return ontology.getConcept(conceptId);
  }

  /**
   * Turn a concept into its probability if it's not already one, implementing the corresponding
   * semantic operator. Also makes the original concept the numerosity's inherent.
   *
   * @param concept the untransformed concept. Must be an event.
   * @return the transformed concept
   */
  public synchronized Concept makeLevel(Concept concept) {

    if (concept.is(SemanticType.ORDERING)) {
      return concept;
    }

    if (Sets.intersection(concept.getType(), SemanticType.CONTINUOUS_QUALITY_TYPES).size() == 0) {
      throw new KlabValidationException(
          "magnitudes can only be observed only for quantifiable " + "qualities");
    }

    // this.hasUnaryOp = true;

    String cName = getCleanId(concept) + "Level";
    String definition = UnarySemanticOperator.LEVEL.declaration[0] + " " + concept.getUrn();
    Ontology ontology = getOntology(concept.getNamespace());
    String conceptId = ontology.getIdForDefinition(definition);

    if (conceptId == null) {

      conceptId = ontology.createIdForDefinition(definition);

      Set<SemanticType> newType = UnarySemanticOperator.LEVEL.apply(concept.getType());

      String reference =
          UnarySemanticOperator.LEVEL.getReferenceName(concept.getReferenceName(), null);

      ArrayList<Axiom> ax = new ArrayList<>();
      ax.add(Axiom.ClassAssertion(conceptId, newType));
      ax.add(Axiom.SubClass(NS.CORE_LEVEL, conceptId));
      ax.add(Axiom.AnnotationAssertion(conceptId, NS.REFERENCE_NAME_PROPERTY, reference));
      ax.add(Axiom.AnnotationAssertion(conceptId, NS.BASE_DECLARATION, "true"));
      ax.add(Axiom.AnnotationAssertion(conceptId, "rdfs:label", cName));
      ax.add(Axiom.AnnotationAssertion(conceptId, NS.CONCEPT_DEFINITION_PROPERTY, definition));
      ontology.define(ax);
      Concept ret = ontology.getConcept(conceptId);

      /*
       * probability is inherent to the event that's possible.
       */
      restrictSome(ret, getProperty(NS.DESCRIBES_OBSERVABLE_PROPERTY), concept, ontology);
    }

    return ontology.getConcept(conceptId);
  }

  /**
   * Turn a concept into its probability if it's not already one, implementing the corresponding
   * semantic operator. Also makes the original concept the numerosity's inherent.
   *
   * @param concept the untransformed concept. Must be an event.
   * @return the transformed concept
   */
  public synchronized Concept makeProbability(Concept concept) {

    if (concept.is(SemanticType.PROBABILITY)) {
      return concept;
    }

    if (!concept.is(SemanticType.EVENT)) {
      throw new KlabValidationException("probabilities can only be observed only for events");
    }

    // this.hasUnaryOp = true;

    String cName = getCleanId(concept) + "Probability";
    String definition = UnarySemanticOperator.PROBABILITY.declaration[0] + " " + concept.getUrn();
    Ontology ontology = getOntology(concept.getNamespace());
    String conceptId = ontology.getIdForDefinition(definition);

    if (conceptId == null) {

      conceptId = ontology.createIdForDefinition(definition);

      Set<SemanticType> newType = UnarySemanticOperator.PROBABILITY.apply(concept.getType());

      String reference =
          UnarySemanticOperator.PROBABILITY.getReferenceName(concept.getReferenceName(), null);

      ArrayList<Axiom> ax = new ArrayList<>();
      ax.add(Axiom.ClassAssertion(conceptId, newType));
      ax.add(Axiom.SubClass(NS.CORE_PROBABILITY, conceptId));
      ax.add(Axiom.AnnotationAssertion(conceptId, NS.REFERENCE_NAME_PROPERTY, reference));
      ax.add(Axiom.AnnotationAssertion(conceptId, NS.BASE_DECLARATION, "true"));
      ax.add(Axiom.AnnotationAssertion(conceptId, "rdfs:label", cName));
      ax.add(Axiom.AnnotationAssertion(conceptId, NS.CONCEPT_DEFINITION_PROPERTY, definition));
      ontology.define(ax);
      Concept ret = ontology.getConcept(conceptId);

      /*
       * probability is inherent to the event that's possible.
       */
      restrictSome(ret, getProperty(NS.DESCRIBES_OBSERVABLE_PROPERTY), concept, ontology);
    }

    return ontology.getConcept(conceptId);
  }

  /**
   * Turn a concept into its uncertainty if it's not already one, implementing the corresponding
   * semantic operator. Also makes the original concept the numerosity's inherent.
   *
   * @param concept the untransformed concept.
   * @return the transformed concept
   */
  public synchronized Concept makeUncertainty(Concept concept) {

    if (concept.is(SemanticType.UNCERTAINTY)) {
      return concept;
    }

    String cName = "UncertaintyOf" + getCleanId(concept);
    String definition = UnarySemanticOperator.UNCERTAINTY.declaration[0] + " " + concept.getUrn();
    Ontology ontology = getOntology(concept.getNamespace());
    String conceptId = ontology.getIdForDefinition(definition);

    // this.hasUnaryOp = true;

    if (conceptId == null) {

      String reference =
          UnarySemanticOperator.UNCERTAINTY.getReferenceName(concept.getReferenceName(), null);

      conceptId = ontology.createIdForDefinition(definition);
      Set<SemanticType> newType = UnarySemanticOperator.UNCERTAINTY.apply(concept.getType());

      ArrayList<Axiom> ax = new ArrayList<>();
      ax.add(Axiom.ClassAssertion(conceptId, newType));
      ax.add(Axiom.SubClass(NS.CORE_UNCERTAINTY, conceptId));
      ax.add(Axiom.AnnotationAssertion(conceptId, NS.REFERENCE_NAME_PROPERTY, reference));
      ax.add(Axiom.AnnotationAssertion(conceptId, NS.BASE_DECLARATION, "true"));
      ax.add(Axiom.AnnotationAssertion(conceptId, "rdfs:label", cName));
      ax.add(Axiom.AnnotationAssertion(conceptId, NS.CONCEPT_DEFINITION_PROPERTY, definition));
      ontology.define(ax);
      Concept ret = ontology.getConcept(conceptId);
      /*
       * uncertainty is inherent to the thing that's present.
       */
      restrictSome(ret, getProperty(NS.DESCRIBES_OBSERVABLE_PROPERTY), concept, ontology);
    }

    return ontology.getConcept(conceptId);
  }

  public synchronized Concept makeProportion(
      Concept concept, Concept comparison, boolean isPercentage) {

    if (concept.is(SemanticType.PROPORTION) || concept.is(SemanticType.PERCENTAGE)) {
      return concept;
    }

    if (!(concept.is(SemanticType.QUALITY) || concept.is(SemanticType.TRAIT))
        && (comparison != null && !comparison.is(SemanticType.QUALITY))) {
      throw new KlabValidationException("proportion must be of qualities or traits to qualities");
    }

    String cName =
        getCleanId(concept)
            + (isPercentage ? "Percentage" : "Proportion")
            + (comparison == null ? "" : getCleanId(comparison));

    // this.hasUnaryOp = true;

    String definition =
        (isPercentage
                ? UnarySemanticOperator.PERCENTAGE.declaration[0]
                : UnarySemanticOperator.PROPORTION.declaration[0])
            + " ("
            + concept.getUrn()
            + ")"
            + (comparison == null
                ? ""
                : (" "
                    + (isPercentage
                        ? UnarySemanticOperator.PERCENTAGE.declaration[1]
                        : UnarySemanticOperator.PROPORTION.declaration[1])
                    + " ("
                    + comparison.getUrn()
                    + ")"));

    Ontology ontology = getOntology(concept.getNamespace());
    String conceptId = ontology.getIdForDefinition(definition);

    if (conceptId == null) {

      conceptId = ontology.createIdForDefinition(definition);

      String reference =
          isPercentage
              ? UnarySemanticOperator.PERCENTAGE.getReferenceName(
                  concept.getReferenceName(),
                  comparison == null ? null : comparison.getReferenceName())
              : UnarySemanticOperator.PROPORTION.getReferenceName(
                  concept.getReferenceName(),
                  comparison == null ? null : comparison.getReferenceName());

      Set<SemanticType> newType =
          isPercentage
              ? UnarySemanticOperator.PERCENTAGE.apply(concept.getType())
              : UnarySemanticOperator.PROPORTION.apply(concept.getType());

      ArrayList<Axiom> ax = new ArrayList<>();
      ax.add(Axiom.ClassAssertion(conceptId, newType));
      ax.add(Axiom.SubClass(NS.CORE_PROPORTION, conceptId));
      ax.add(Axiom.AnnotationAssertion(conceptId, NS.REFERENCE_NAME_PROPERTY, reference));
      ax.add(Axiom.AnnotationAssertion(conceptId, NS.BASE_DECLARATION, "true"));
      ax.add(Axiom.AnnotationAssertion(conceptId, "rdfs:label", cName));
      ax.add(Axiom.AnnotationAssertion(conceptId, NS.CONCEPT_DEFINITION_PROPERTY, definition));
      ontology.define(ax);

      Concept ret = ontology.getConcept(conceptId);
      /*
       * proportion is inherent to the thing that's present.
       */
      restrictSome(ret, getProperty(NS.DESCRIBES_OBSERVABLE_PROPERTY), concept, ontology);
      if (comparison != null) {
        restrictSome(ret, getProperty(NS.IS_COMPARED_TO_PROPERTY), comparison, ontology);
      }
    }

    return ontology.getConcept(conceptId);
  }

  public synchronized Concept makeRatio(Concept concept, Concept comparison) {

    if (concept.is(SemanticType.RATIO)) {
      return concept;
    }

    /*
     * accept only two qualities of the same physical nature (TODO)
     */
    if (!(concept.is(SemanticType.QUALITY) || concept.is(SemanticType.TRAIT))
        || !comparison.is(SemanticType.QUALITY)) {
      throw new KlabValidationException(
          "ratios must be between qualities of the same nature or traits to qualities");
    }

    // this.hasUnaryOp = true;

    String cName = getCleanId(concept) + "To" + getCleanId(comparison) + "Ratio";

    String definition =
        UnarySemanticOperator.RATIO.declaration[0]
            + " ("
            + concept.getUrn()
            + ")"
            + (comparison == null
                ? ""
                : " "
                    + (UnarySemanticOperator.RATIO.declaration[1]
                        + " ("
                        + comparison.getUrn()
                        + ")"));

    Ontology ontology = getOntology(concept.getNamespace());
    String conceptId = ontology.getIdForDefinition(definition);

    if (conceptId == null) {

      conceptId = ontology.createIdForDefinition(definition);

      String reference =
          UnarySemanticOperator.RATIO.getReferenceName(
              concept.getReferenceName(),
              comparison == null ? null : comparison.getReferenceName());

      Set<SemanticType> newType = UnarySemanticOperator.RATIO.apply(concept.getType());

      ArrayList<Axiom> ax = new ArrayList<>();
      ax.add(Axiom.ClassAssertion(conceptId, newType));
      ax.add(Axiom.SubClass(NS.CORE_RATIO, conceptId));
      ax.add(Axiom.AnnotationAssertion(conceptId, NS.REFERENCE_NAME_PROPERTY, reference));
      ax.add(Axiom.AnnotationAssertion(conceptId, NS.BASE_DECLARATION, "true"));
      ax.add(Axiom.AnnotationAssertion(conceptId, "rdfs:label", cName));
      ax.add(Axiom.AnnotationAssertion(conceptId, NS.CONCEPT_DEFINITION_PROPERTY, definition));

      // unit for ratios of physical properties
      if ((concept.is(SemanticType.EXTENSIVE) || concept.is(SemanticType.INTENSIVE))
          && (comparison.is(SemanticType.EXTENSIVE) || comparison.is(SemanticType.INTENSIVE))) {
        Object unit1 = concept.getMetadata().get(NS.SI_UNIT_PROPERTY);
        Object unit2 = comparison.getMetadata().get(NS.SI_UNIT_PROPERTY);
        if (unit1 != null && unit2 != null) {
          String unit = unit1 + "/" + unit2;
          ax.add(Axiom.AnnotationAssertion(conceptId, NS.SI_UNIT_PROPERTY, unit));
        }
      }

      ontology.define(ax);

      Concept ret = ontology.getConcept(conceptId);

      /*
       * ratio is inherent to the thing that's present.
       */
      restrictSome(ret, getProperty(NS.DESCRIBES_OBSERVABLE_PROPERTY), concept, ontology);
      restrictSome(ret, getProperty(NS.IS_COMPARED_TO_PROPERTY), comparison, ontology);
    }

    return ontology.getConcept(conceptId);
  }

  public synchronized Concept makeValue(Concept concept, Concept comparison, boolean monetary) {

    if (concept.is(SemanticType.VALUE) || concept.is(SemanticType.MONETARY_VALUE)) {
      return concept;
    }

    String cName =
        (monetary ? "MonetaryValueOf" : "ValueOf")
            + getCleanId(concept)
            + (comparison == null ? "" : ("Vs" + getCleanId(comparison)));

    String definition =
        (monetary
                ? UnarySemanticOperator.MONETARY_VALUE.declaration[0]
                : UnarySemanticOperator.VALUE.declaration[0])
            + " ("
            + concept.getUrn()
            + ")"
            + (comparison == null
                ? ""
                : " "
                    + (UnarySemanticOperator.VALUE.declaration[1]
                        + " ("
                        + comparison.getUrn()
                        + ")"));

    Ontology ontology = getOntology(concept.getNamespace());
    String conceptId = ontology.getIdForDefinition(definition);

    // this.hasUnaryOp = true;

    if (conceptId == null) {

      conceptId = ontology.createIdForDefinition(definition);

      String reference =
          monetary
              ? UnarySemanticOperator.MONETARY_VALUE.getReferenceName(
                  concept.getReferenceName(),
                  comparison == null ? null : comparison.getReferenceName())
              : UnarySemanticOperator.VALUE.getReferenceName(
                  concept.getReferenceName(),
                  comparison == null ? null : comparison.getReferenceName());

      Set<SemanticType> newType =
          monetary
              ? UnarySemanticOperator.MONETARY_VALUE.apply(concept.getType())
              : UnarySemanticOperator.VALUE.apply(concept.getType());

      ArrayList<Axiom> ax = new ArrayList<>();
      ax.add(Axiom.ClassAssertion(conceptId, newType));
      ax.add(Axiom.SubClass(monetary ? NS.CORE_MONETARY_VALUE : NS.CORE_VALUE, conceptId));
      ax.add(Axiom.AnnotationAssertion(conceptId, NS.REFERENCE_NAME_PROPERTY, reference));
      ax.add(Axiom.AnnotationAssertion(conceptId, NS.BASE_DECLARATION, "true"));
      ax.add(Axiom.AnnotationAssertion(conceptId, "rdfs:label", cName));
      ax.add(Axiom.AnnotationAssertion(conceptId, NS.CONCEPT_DEFINITION_PROPERTY, definition));
      ontology.define(ax);

      Concept ret = ontology.getConcept(conceptId);

      /*
       * value is inherent to the thing that's present.
       */
      restrictSome(ret, getProperty(NS.DESCRIBES_OBSERVABLE_PROPERTY), concept, ontology);
      if (comparison != null) {
        restrictSome(ret, getProperty(NS.IS_COMPARED_TO_PROPERTY), comparison, ontology);
      }
    }

    return ontology.getConcept(conceptId);
  }

  /**
   * type of <trait> makes a CLASS that incarnates the trait as a quality and whose values are the
   * concrete children of the trait.
   *
   * @param classified
   * @return
   */
  public synchronized Concept makeType(Concept classified) {

    if (classified.is(SemanticType.CLASS)) {
      return classified;
    }

    String traitID = getCleanId(classified) + "Type";
    String definition = UnarySemanticOperator.TYPE.declaration[0] + " " + classified.getUrn();
    Ontology ontology = getOntology(classified.getNamespace());
    String conceptId = ontology.getIdForDefinition(definition);
    var reas = scope.getService(org.integratedmodelling.klab.api.services.Reasoner.class);

    // this.hasUnaryOp = true;

    if (conceptId == null) {

      conceptId = ontology.createIdForDefinition(definition);

      Set<SemanticType> newType = UnarySemanticOperator.TYPE.apply(classified.getType());

      String reference =
          UnarySemanticOperator.TYPE.getReferenceName(classified.getReferenceName(), null);

      List<Axiom> axioms = new ArrayList<>();
      axioms.add(Axiom.ClassAssertion(conceptId, newType));
      axioms.add(Axiom.SubClass(NS.CORE_TYPE, conceptId));
      axioms.add(Axiom.AnnotationAssertion(conceptId, NS.BASE_DECLARATION, "true"));
      axioms.add(Axiom.AnnotationAssertion(conceptId, NS.REFERENCE_NAME_PROPERTY, reference));
      axioms.add(Axiom.AnnotationAssertion(conceptId, NS.IS_TYPE_DELEGATE, "true"));
      axioms.add(Axiom.AnnotationAssertion(conceptId, "rdfs:label", traitID));
      axioms.add(Axiom.AnnotationAssertion(conceptId, NS.CONCEPT_DEFINITION_PROPERTY, definition));
      ontology.define(axioms);
      Concept ret = ontology.getConcept(conceptId);

      restrictSome(ret, getProperty(NS.DESCRIBES_OBSERVABLE_PROPERTY), classified, ontology);

      /*
       * types inherit the context from their trait
       */
      Concept context = reas.inherent(classified);
      if (context != null) {
        restrictSome(ret, getProperty(NS.IS_INHERENT_TO_PROPERTY), context, ontology);
      }
    }

    return ontology.getConcept(conceptId);
  }

  Concept getConceptFor(OWLClass cls) {
    Long id = owlClasses.inverse().get(cls);
    return conceptsById.get(id);
  }

  synchronized void registerWithReasoner(Ontology o) {
    if (mergedReasonerOntology != null) {
      mergedReasonerOntology.addImport(o);
      if (reasoner != null && reasonerSynchronizing) {
        reasoner.flush();
      }
    }
  }

  public void flushReasoner() {
    if (reasoner != null && !reasonerSynchronizing) {
      reasoner.flush();
    }
  }

  public boolean isOn() {
    return reasonerActive;
  }

  public Ontology getOntology() {
    return mergedReasonerOntology;
  }

  /**
   * Only check for direct subsumption. Default inference when the reasoner is not active.
   *
   * <p>Annotation properties only use asserted methods.
   *
   * <p>TODO handle exceptions and/or default to is() when the ontology is inconsistent and we're
   * not running in strict mode.
   *
   * @param c1
   * @param c2
   * @return true
   */
  public boolean is(Semantics c1, Semantics c2) {

    var reas = scope.getService(org.integratedmodelling.klab.api.services.Reasoner.class);

    if (c1 instanceof Concept && c2 instanceof Concept) {

      if (reasoner == null) {
        return reas.is(c1, c2);
      }
      return getSubClasses(getOWLClass(c2.asConcept()), false)
          .containsEntity(getOWLClass(c1.asConcept()));

    } else if (c1 instanceof Property && c2 instanceof Property) {

      if (reasoner == null || (((Property) c1).isAnnotation() && ((Property) c2).isAnnotation())) {
        return ((Property) c1).is(c2, this);
      }

      if (((Property) c1).isObjectProperty() && ((Property) c2).isObjectProperty()) {
        return getSubObjectProperties(((Property) c2).getOWLEntity().asOWLObjectProperty(), false)
            .containsEntity(((Property) c1).getOWLEntity().asOWLObjectProperty());
      } else if (((Property) c1).isLiteralProperty() && ((Property) c2).isLiteralProperty()) {
        return getSubDataProperties(((Property) c2).getOWLEntity().asOWLDataProperty(), false)
            .containsEntity(((Property) c1).getOWLEntity().asOWLDataProperty());
      }
    }

    return false;
  }

  /**
   * Return if concept is consistent. If reasoner is off, assume true.
   *
   * @param c
   * @return true if concept is consistent.
   */
  public boolean isSatisfiable(Semantics c) {
    return reasoner == null || isSatisfiable(getOWLClass(c.asConcept()));
  }

  /**
   * Synonym of getAllParents(), in Concept; defaults to asserted parents if reasoner is off. Will
   * not return owl:Thing (which should never appear anyway) or owl:Nothing, even if the concept is
   * inconsistent.
   *
   * @param main
   * @return the parent closure of the concept
   */
  public Set<Concept> getParentClosure(Concept main) {

    var reas = scope.getService(org.integratedmodelling.klab.api.services.Reasoner.class);

    Set<Concept> ret = new HashSet<>();
    if (reasoner != null) {
      for (OWLClass cls : getSuperClasses(getOWLClass(main.asConcept()), false).getFlattened()) {
        if (cls.isBottomEntity() || cls.isTopEntity()) {
          continue;
        }
        Concept cc = getConceptFor(cls);
        if (cc != null) {
          ret.add(cc);
        }
      }
    } else {
      ret.addAll(reas.allParents(main));
    }
    return ret;
  }

  /**
   * Synonym of getSubclasses, in Concepts; defaults to asserted children if reasoner is off. Will
   * not return owl:Thing (which should never appear anyway) or owl:Nothing, which is a child of
   * everything.
   *
   * @param main
   * @return the semantic closure of the concept
   */
  public Collection<Concept> getSemanticClosure(Concept main) {

    if (reasoner != null) {
      Set<Concept> ret = new HashSet<>();
      for (OWLClass cls : getSubClasses(getOWLClass(main.asConcept()), false).getFlattened()) {
        if (cls.isBottomEntity() || cls.isTopEntity()) {
          continue;
        }
        Concept cc = getConceptFor(cls);
        if (cc != null) {
          ret.add(cc);
        }
      }
      return ret;
    }
    var reas = scope.getService(org.integratedmodelling.klab.api.services.Reasoner.class);
    return reas.allChildren(main);
  }

  /*
   * Delegate methods. TODO align with k.LAB API instead of OWLAPI.
   */

  public void flush() {
    if (reasoner != null) {
      reasoner.flush();
    }
  }

  public NodeSet<OWLClass> getDataPropertyDomains(OWLDataProperty arg0, boolean arg1)
      throws InconsistentOntologyException,
          FreshEntitiesException,
          ReasonerInterruptedException,
          TimeOutException {
    return reasoner.getDataPropertyDomains(arg0, arg1);
  }

  public Set<OWLLiteral> getDataPropertyValues(OWLNamedIndividual arg0, OWLDataProperty arg1)
      throws InconsistentOntologyException,
          FreshEntitiesException,
          ReasonerInterruptedException,
          TimeOutException {
    return reasoner.getDataPropertyValues(arg0, arg1);
  }

  public NodeSet<OWLNamedIndividual> getDifferentIndividuals(OWLNamedIndividual arg0)
      throws InconsistentOntologyException,
          FreshEntitiesException,
          ReasonerInterruptedException,
          TimeOutException {
    return reasoner.getDifferentIndividuals(arg0);
  }

  public NodeSet<OWLClass> getDisjointClasses(OWLClassExpression arg0)
      throws ReasonerInterruptedException,
          TimeOutException,
          FreshEntitiesException,
          InconsistentOntologyException {
    return reasoner.getDisjointClasses(arg0);
  }

  public NodeSet<OWLDataProperty> getDisjointDataProperties(OWLDataPropertyExpression arg0)
      throws InconsistentOntologyException,
          FreshEntitiesException,
          ReasonerInterruptedException,
          TimeOutException {
    return reasoner.getDisjointDataProperties(arg0);
  }

  public NodeSet<OWLObjectPropertyExpression> getDisjointObjectProperties(
      OWLObjectPropertyExpression arg0)
      throws InconsistentOntologyException,
          FreshEntitiesException,
          ReasonerInterruptedException,
          TimeOutException {
    return reasoner.getDisjointObjectProperties(arg0);
  }

  public Node<OWLClass> getEquivalentClasses(OWLClassExpression arg0)
      throws InconsistentOntologyException,
          ClassExpressionNotInProfileException,
          FreshEntitiesException,
          ReasonerInterruptedException,
          TimeOutException {
    return reasoner.getEquivalentClasses(arg0);
  }

  public Node<OWLDataProperty> getEquivalentDataProperties(OWLDataProperty arg0)
      throws InconsistentOntologyException,
          FreshEntitiesException,
          ReasonerInterruptedException,
          TimeOutException {
    return reasoner.getEquivalentDataProperties(arg0);
  }

  public Node<OWLObjectPropertyExpression> getEquivalentObjectProperties(
      OWLObjectPropertyExpression arg0)
      throws InconsistentOntologyException,
          FreshEntitiesException,
          ReasonerInterruptedException,
          TimeOutException {
    return reasoner.getEquivalentObjectProperties(arg0);
  }

  public NodeSet<OWLNamedIndividual> getInstances(OWLClassExpression arg0, boolean arg1)
      throws InconsistentOntologyException,
          ClassExpressionNotInProfileException,
          FreshEntitiesException,
          ReasonerInterruptedException,
          TimeOutException {
    return reasoner.getInstances(arg0, arg1);
  }

  public Node<OWLObjectPropertyExpression> getInverseObjectProperties(
      OWLObjectPropertyExpression arg0)
      throws InconsistentOntologyException,
          FreshEntitiesException,
          ReasonerInterruptedException,
          TimeOutException {
    return reasoner.getInverseObjectProperties(arg0);
  }

  public NodeSet<OWLClass> getObjectPropertyDomains(OWLObjectPropertyExpression arg0, boolean arg1)
      throws InconsistentOntologyException,
          FreshEntitiesException,
          ReasonerInterruptedException,
          TimeOutException {
    return reasoner.getObjectPropertyDomains(arg0, arg1);
  }

  public NodeSet<OWLClass> getObjectPropertyRanges(OWLObjectPropertyExpression arg0, boolean arg1)
      throws InconsistentOntologyException,
          FreshEntitiesException,
          ReasonerInterruptedException,
          TimeOutException {
    return reasoner.getObjectPropertyRanges(arg0, arg1);
  }

  public NodeSet<OWLNamedIndividual> getObjectPropertyValues(
      OWLNamedIndividual arg0, OWLObjectPropertyExpression arg1)
      throws InconsistentOntologyException,
          FreshEntitiesException,
          ReasonerInterruptedException,
          TimeOutException {
    return reasoner.getObjectPropertyValues(arg0, arg1);
  }

  public String getReasonerName() {
    return reasoner.getReasonerName();
  }

  public Version getReasonerVersion() {
    return reasoner.getReasonerVersion();
  }

  public Node<OWLNamedIndividual> getSameIndividuals(OWLNamedIndividual arg0)
      throws InconsistentOntologyException,
          FreshEntitiesException,
          ReasonerInterruptedException,
          TimeOutException {
    return reasoner.getSameIndividuals(arg0);
  }

  public NodeSet<OWLClass> getSubClasses(OWLClassExpression arg0, boolean arg1)
      throws ReasonerInterruptedException,
          TimeOutException,
          FreshEntitiesException,
          InconsistentOntologyException,
          ClassExpressionNotInProfileException {
    return reasoner.getSubClasses(arg0, arg1);
  }

  public NodeSet<OWLDataProperty> getSubDataProperties(OWLDataProperty arg0, boolean arg1)
      throws InconsistentOntologyException,
          FreshEntitiesException,
          ReasonerInterruptedException,
          TimeOutException {
    return reasoner.getSubDataProperties(arg0, arg1);
  }

  public NodeSet<OWLObjectPropertyExpression> getSubObjectProperties(
      OWLObjectPropertyExpression arg0, boolean arg1)
      throws InconsistentOntologyException,
          FreshEntitiesException,
          ReasonerInterruptedException,
          TimeOutException {
    return reasoner.getSubObjectProperties(arg0, arg1);
  }

  public NodeSet<OWLClass> getSuperClasses(OWLClassExpression arg0, boolean arg1)
      throws InconsistentOntologyException,
          ClassExpressionNotInProfileException,
          FreshEntitiesException,
          ReasonerInterruptedException,
          TimeOutException {
    return reasoner.getSuperClasses(arg0, arg1);
  }

  public NodeSet<OWLDataProperty> getSuperDataProperties(OWLDataProperty arg0, boolean arg1)
      throws InconsistentOntologyException,
          FreshEntitiesException,
          ReasonerInterruptedException,
          TimeOutException {
    return reasoner.getSuperDataProperties(arg0, arg1);
  }

  public NodeSet<OWLObjectPropertyExpression> getSuperObjectProperties(
      OWLObjectPropertyExpression arg0, boolean arg1)
      throws InconsistentOntologyException,
          FreshEntitiesException,
          ReasonerInterruptedException,
          TimeOutException {
    return reasoner.getSuperObjectProperties(arg0, arg1);
  }

  public long getTimeOut() {
    return reasoner.getTimeOut();
  }

  public NodeSet<OWLClass> getTypes(OWLNamedIndividual arg0, boolean arg1)
      throws InconsistentOntologyException,
          FreshEntitiesException,
          ReasonerInterruptedException,
          TimeOutException {
    return reasoner.getTypes(arg0, arg1);
  }

  public Node<OWLClass> getUnsatisfiableClasses()
      throws ReasonerInterruptedException, TimeOutException, InconsistentOntologyException {
    return reasoner.getUnsatisfiableClasses();
  }

  public void interrupt() {
    reasoner.interrupt();
  }

  public boolean isConsistent() throws ReasonerInterruptedException, TimeOutException {
    return reasoner.isConsistent();
  }

  public boolean isEntailed(OWLAxiom arg0)
      throws ReasonerInterruptedException,
          UnsupportedEntailmentTypeException,
          TimeOutException,
          AxiomNotInProfileException,
          FreshEntitiesException,
          InconsistentOntologyException {
    return reasoner.isEntailed(arg0);
  }

  public boolean isEntailed(Set<? extends OWLAxiom> arg0)
      throws ReasonerInterruptedException,
          UnsupportedEntailmentTypeException,
          TimeOutException,
          AxiomNotInProfileException,
          FreshEntitiesException,
          InconsistentOntologyException {
    return reasoner.isEntailed(arg0);
  }

  public boolean isEntailmentCheckingSupported(AxiomType<?> arg0) {
    return reasoner.isEntailmentCheckingSupported(arg0);
  }

  public boolean isPrecomputed(InferenceType arg0) {
    return reasoner.isPrecomputed(arg0);
  }

  public boolean isSatisfiable(OWLClassExpression arg0)
      throws ReasonerInterruptedException,
          TimeOutException,
          ClassExpressionNotInProfileException,
          FreshEntitiesException,
          InconsistentOntologyException {
    return reasoner.isSatisfiable(arg0);
  }

  public void registerWithReasoner(KimOntology parsed) {
    Ontology ontology = getOntology(parsed.getUrn());
    if (ontology != null) {
      registerWithReasoner(ontology);
    }
  }

  public Collection<Concept> getParents(Concept concept) {
    Set<Concept> concepts = new HashSet<>();
    OWLClass owl = getOWLClass(concept);
    synchronized (owl) {
      Set<OWLClassExpression> set = owl.getSuperClasses(manager.getOntologies());
      for (OWLClassExpression s : set) {
        if (!s.equals(owl) && !(s.isAnonymous() || s.asOWLClass().isBuiltIn()))
          concepts.add(getExistingOrCreate(s.asOWLClass()));
      }
    }
    return concepts;
  }

  public Collection<Concept> getChildren(Concept concept) {
    Set<Concept> concepts = new HashSet<>();
    OWLClass owl = getOWLClass(concept);
    synchronized (owl) {
      Set<OWLClassExpression> set = owl.getSubClasses(manager.getOntologies());
      for (OWLClassExpression s : set) {
        if (!(s.isAnonymous() || s.isOWLNothing() || s.isOWLThing()))
          concepts.add(getExistingOrCreate(s.asOWLClass()));
      }
      if (set.isEmpty() && owl.isOWLThing()) {
        for (Ontology onto : ontologies.values()) {
          concepts.addAll(onto.getConcepts());
        }
      }
    }
    return concepts;
  }

  public Concept getLeastGeneralCommonConcept(Concept reference, Concept otherConcept) {

    var reas = scope.getService(org.integratedmodelling.klab.api.services.Reasoner.class);

    Concept ret = null;
    if (otherConcept == null) {
      ret = reference;
    }
    if (reas.is(reference, otherConcept)) {
      ret = otherConcept;
    } else if (reas.is(otherConcept, reference)) {
      ret = reference;
    } else {
      for (Concept pp : getParents(reference)) {
        Concept c1 = getLeastGeneralCommonConcept(pp, otherConcept);
        if (c1 != null) {
          ret = c1;
          break;
        }
      }
    }
    return ret;
  }

  public Set<Concept> getOperands(Concept asConcept) {
    Set<Concept> ret = new HashSet<>();
    OWLClass _owl = getOWLClass(asConcept);
    Set<OWLClassExpression> set = _owl.getSuperClasses(manager.getOntologies());
    for (OWLClassExpression s : set) {
      if (s instanceof OWLNaryBooleanClassExpression) {
        for (OWLClassExpression cls : ((OWLNaryBooleanClassExpression) s).getOperandsAsList()) {
          if (cls instanceof OWLClass) {
            ret.add(getExistingOrCreate(cls.asOWLClass()));
          }
        }
      }
    }
    return ret;
  }

  public boolean exportOntology(String namespace, File directory) {
    Ontology ret = getOntology(namespace);
    if (ret != null) {
      directory.mkdirs();
      return ret.write(new File(directory + File.separator + namespace + ".owl"), true);
    }
    return false;
  }
}
