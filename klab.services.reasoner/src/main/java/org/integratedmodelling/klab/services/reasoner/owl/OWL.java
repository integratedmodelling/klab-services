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

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;

import org.integratedmodelling.klab.Configuration;
import org.integratedmodelling.klab.api.authentication.scope.ServiceScope;
import org.integratedmodelling.klab.api.data.Metadata;
import org.integratedmodelling.klab.api.exceptions.KIOException;
import org.integratedmodelling.klab.api.exceptions.KInternalErrorException;
import org.integratedmodelling.klab.api.exceptions.KValidationException;
import org.integratedmodelling.klab.api.knowledge.Artifact;
import org.integratedmodelling.klab.api.knowledge.Concept;
import org.integratedmodelling.klab.api.knowledge.IKnowledge;
import org.integratedmodelling.klab.api.knowledge.ISemantic;
import org.integratedmodelling.klab.api.knowledge.SemanticType;
import org.integratedmodelling.klab.api.knowledge.Semantics;
import org.integratedmodelling.klab.api.lang.LogicalConnector;
import org.integratedmodelling.klab.api.lang.UnarySemanticOperator;
import org.integratedmodelling.klab.api.lang.kim.KimNamespace;
import org.integratedmodelling.klab.api.services.runtime.Channel;
import org.integratedmodelling.klab.api.utils.Utils;
import org.integratedmodelling.klab.api.utils.Utils.CamelCase;
import org.integratedmodelling.klab.configuration.Services;
import org.integratedmodelling.klab.exceptions.KlabException;
import org.integratedmodelling.klab.exceptions.KlabIOException;
import org.integratedmodelling.klab.exceptions.KlabValidationException;
import org.integratedmodelling.klab.knowledge.ConceptImpl;
import org.integratedmodelling.klab.services.reasoner.api.IAxiom;
import org.integratedmodelling.klab.services.reasoner.internal.CoreOntology;
import org.integratedmodelling.klab.services.reasoner.internal.CoreOntology.NS;
import org.integratedmodelling.klab.utils.Pair;
import org.semanticweb.HermiT.Reasoner;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.AxiomType;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLAxiom;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLClassExpression;
import org.semanticweb.owlapi.model.OWLDataFactory;
import org.semanticweb.owlapi.model.OWLDataProperty;
import org.semanticweb.owlapi.model.OWLDataPropertyExpression;
import org.semanticweb.owlapi.model.OWLLiteral;
import org.semanticweb.owlapi.model.OWLNamedIndividual;
import org.semanticweb.owlapi.model.OWLNaryBooleanClassExpression;
import org.semanticweb.owlapi.model.OWLObjectIntersectionOf;
import org.semanticweb.owlapi.model.OWLObjectPropertyExpression;
import org.semanticweb.owlapi.model.OWLObjectUnionOf;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyAlreadyExistsException;
import org.semanticweb.owlapi.model.OWLOntologyCreationException;
import org.semanticweb.owlapi.model.OWLOntologyManager;
import org.semanticweb.owlapi.model.OWLProperty;
import org.semanticweb.owlapi.model.OWLQuantifiedRestriction;
import org.semanticweb.owlapi.reasoner.AxiomNotInProfileException;
import org.semanticweb.owlapi.reasoner.ClassExpressionNotInProfileException;
import org.semanticweb.owlapi.reasoner.FreshEntitiesException;
import org.semanticweb.owlapi.reasoner.InconsistentOntologyException;
import org.semanticweb.owlapi.reasoner.InferenceType;
import org.semanticweb.owlapi.reasoner.Node;
import org.semanticweb.owlapi.reasoner.NodeSet;
import org.semanticweb.owlapi.reasoner.OWLReasoner;
import org.semanticweb.owlapi.reasoner.ReasonerInterruptedException;
import org.semanticweb.owlapi.reasoner.TimeOutException;
import org.semanticweb.owlapi.reasoner.UnsupportedEntailmentTypeException;
import org.semanticweb.owlapi.util.AutoIRIMapper;
import org.semanticweb.owlapi.util.Version;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.common.collect.Sets;

/**
 * Import concepts and properties from OWL ontologies.
 *
 * @author Ferd
 */
public enum OWL {

    INSTANCE;

    public static final String DEFAULT_ONTOLOGY_PREFIX = "http://integratedmodelling.org/ks";
    public static final String INTERNAL_ONTOLOGY_PREFIX = "http://integratedmodelling.org/ks/internal";

    private HashMap<String, KimNamespace> namespaces = new HashMap<>();
    private HashMap<String, Ontology> ontologies = new HashMap<>();
    private HashMap<String, String> iri2ns = new HashMap<>();
    private HashMap<String, OWLClass> systemConcepts = new HashMap<>();
    private HashMap<String, ConceptImpl> xsdMappings = new HashMap<>();
    private BiMap<Long, OWLClass> owlClasses = HashBiMap.create();
    private BiMap<Long, ConceptImpl> conceptsById = HashBiMap.create();
    private AtomicLong classId = new AtomicLong(1l);

    private boolean reasonerActive;
    private boolean reasonerSynchronizing = false;
    private Ontology mergedReasonerOntology;
    private OWLReasoner reasoner;

    private static String INTERNAL_REASONER_ONTOLOGY_ID = "k";

    static EnumSet<SemanticType> emptyType = EnumSet.noneOf(SemanticType.class);

    Ontology nonSemanticConcepts;
    OWLOntologyManager manager = null;

    Concept thing;
    Concept nothing;

    private CoreOntology coreOntology;
    private ServiceScope scope;

    /**
     * Source of truth for identifier-friendly reference names
     * 
     * @param main
     * @return
     */
    public static String getCleanFullId(String namespace, String name) {
        return namespace.replaceAll("\\.", "_") + "__" + CamelCase.toLowerCase(name, '_');
    }

    long registerOwlClass(OWLClass cls) {
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
         * candidate namespace must already import all the others. If not, choose the fallback
         * ontology which must be guaranteed to contain all the imports already.
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
        return requireOntology(id, DEFAULT_ONTOLOGY_PREFIX);
    }

    public Ontology requireOntology(String id, String prefix) {

        if (ontologies.get(id) != null) {
            return ontologies.get(id);
        }

        Ontology ret = null;
        try {
            OWLOntology o = manager.createOntology(IRI.create(prefix + "/" + id));
            ret = new Ontology(o, id);
            ontologies.put(id, ret);
            iri2ns.put(((Ontology) ret).getPrefix(), id);
        } catch (OWLOntologyCreationException e) {
            throw new KInternalErrorException(e);
        }

        return ret;
    }

    // /**
    // * Get the Concept corresponding to the OWL class passed. Throws an unchecked exception if not
    // * found.
    // *
    // * @param owl
    // * @param complainIfNotFound
    // * @return the concept for the class
    // */
    // public Concept getConceptFor(OWLClass owl, boolean complainIfNotFound) {
    // Concept ret = null;
    // String sch = owl.getIRI().getNamespace();
    // if (sch.endsWith("#")) {
    // sch = sch.substring(0, sch.length() - 1);
    // }
    // if (sch.endsWith("/")) {
    // sch = sch.substring(0, sch.length() - 1);
    // }
    // Ontology ontology = getOntology(iri2ns.get(sch));
    // if (ontology != null) {
    // ret = ontology.getConcept(owl.getIRI().getFragment());
    // }
    //
    // if (ret == null && complainIfNotFound) {
    // throw new KInternalErrorException("internal: OWL entity " + owl + " does not correspond to a
    // known ontology");
    // }
    //
    // return ret;
    // }
    //
    // public Concept getConceptFor(IRI iri) {
    // Concept ret = null;
    // String sch = iri.getNamespace();
    // if (sch.endsWith("#")) {
    // sch = sch.substring(0, sch.length() - 1);
    // }
    // Ontology ontology = getOntology(iri2ns.get(sch));
    // if (ontology != null) {
    // ret = ontology.getConcept(iri.getFragment());
    // }
    //
    // if (ret == null) {
    // throw new KInternalErrorException("internal: OWL IRI " + iri + " does not correspond to a
    // known ontology");
    // }
    //
    // return ret;
    // }

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
            throw new KInternalErrorException("internal: OWL entity " + owl + " does not correspond to a known ontology");
        }

        return ret;
    }

    // public Property getPropertyFor(IRI iri) {
    // Property ret = null;
    // String sch = iri.getNamespace();
    // if (sch.endsWith("#")) {
    // sch = sch.substring(0, sch.length() - 1);
    // }
    // Ontology ontology = getOntology(iri2ns.get(sch));
    // if (ontology != null) {
    // ret = ontology.getProperty(iri.getFragment());
    // }
    //
    // if (ret == null) {
    // throw new KInternalErrorException("internal: OWL IRI " + iri + " not corresponding to a known
    // ontology");
    // }
    //
    // return ret;
    // }

    // public static String getFileName(String s) {
    //
    // String ret = s;
    //
    // int sl = ret.lastIndexOf(File.separator);
    // if (sl < 0) {
    // sl = ret.lastIndexOf('/');
    // }
    // if (sl > 0) {
    // ret = ret.substring(sl + 1);
    // }
    //
    // return ret;
    // }

    /**
     * Create a manager and load every OWL file under the load path.
     * 
     * @param loadPath
     * @param monitor
     * 
     * @throws KlabException
     */
    public void initialize(Channel monitor) {

        manager = OWLManager.createOWLOntologyManager();
        // this.loadPath = loadPath;
        coreOntology = new CoreOntology(Configuration.INSTANCE.getDataPath("knowledge"));
        // coreOntology.load(monitor);
        load(coreOntology.getRoot());

        /*
         * FIXME manual mapping until I understand what's going on with BFO, whose concepts have a
         * IRI that does not contain the namespace.
         */
        iri2ns.put("http://purl.obolibrary.org/obo", "bfo");

        /*
         * TODO insert basic datatypes as well
         */
        this.systemConcepts.put("owl:Thing", manager.getOWLDataFactory().getOWLThing());
        this.systemConcepts.put("owl:Nothing", manager.getOWLDataFactory().getOWLNothing());

        // if (this.loadPath == null) {
        // throw new KIOException("owl resources cannot be found: knowledge load directory does not
        // exist");
        // }

        // load();

        this.mergedReasonerOntology = (Ontology) requireOntology(INTERNAL_REASONER_ONTOLOGY_ID, OWL.INTERNAL_ONTOLOGY_PREFIX);
        this.mergedReasonerOntology.setInternal(true);

        /*
         * all namespaces so far are internal, and just these.
         */
        for (KimNamespace ns : this.namespaces.values()) {
            // ((Namespace) ns).setInternal(true);
            getOntology(ns.getNamespace()).setInternal(true);
        }

        this.nonSemanticConcepts = requireOntology("nonsemantic", INTERNAL_ONTOLOGY_PREFIX);

        /*
         * create an independent ontology for the non-semantic types we encounter.
         */
        // if (Namespaces.INSTANCE.getNamespace(ONTOLOGY_ID) == null) {
        // Namespaces.INSTANCE.registerNamespace(new Namespace(ONTOLOGY_ID, null, overall),
        // monitor);
        // }
        if (Configuration.INSTANCE.useReasoner()) {
            this.reasoner = new Reasoner.ReasonerFactory().createReasoner(mergedReasonerOntology.getOWLOntology());
            reasonerActive = true;
        }

        for (KimNamespace ns : this.namespaces.values()) {
            registerWithReasoner(getOntology(ns.getNamespace()));
        }

    }

    public CoreOntology getCoreOntology() {
        return this.coreOntology;
    }

    String importOntology(OWLOntology ontology, String resource, String namespace, boolean imported, Channel monitor) {

        if (!ontologies.containsKey(namespace)) {
            ontologies.put(namespace, new Ontology(ontology, namespace));
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
     * the three knowledge manager methods we implement, so we can serve as delegate to a KM for
     * these.
     */
    public Concept getConcept(String concept) {

        Concept result = null;

        if (QualifiedName.validate(concept)) {

            QualifiedName st = new QualifiedName(concept);

            if (Character.isUpperCase(st.getNamespace().charAt(0))) {

                /*
                 * FIXME authority concept
                 */
                // result = Concepts.INSTANCE
                // .getAuthorityConcept(Authorities.INSTANCE.getIdentity(st.getNamespace(),
                // removeTicks(st.getName())));

            } else {

                Ontology o = ontologies.get(st.getNamespace());
                if (o == null) {
                    OWLClass systemConcept = this.systemConcepts.get(st);
                    if (systemConcept != null) {
                        result = o.makeConcept(systemConcept, st.getName(), st.getNamespace(), emptyType);
                    }
                } else {
                    result = o.getConcept(st.getName());
                }
            }
        }

        return result;
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

    public void registerConcept(ConceptImpl thing) {
        this.conceptsById.put(thing.getId(), thing);
    }

    public String getConceptSpace(IRI iri) {

        if (iri2ns.containsKey(iri.toString())) {
            return iri2ns.get(iri.toString());
        }

        String oIri = removeEntity(iri);
        String ret = iri2ns.get(oIri);

        if (ret == null) {
            /*
             * happens, whenever we depend on a concept from a server ontology not loaded yet. Must
             * find a way to deal with this.
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
     * Load OWL files from given directory and in its subdirectories, using a prefix mapper to
     * resolve URLs internally and deriving ontology names from the relative paths. This uses the
     * resolver passed at initialization only to create the namespace. It's only meant for core
     * knowledge not seen by users.
     *
     * @param kdir
     * @throws KlabException
     * @throws KlabIOException
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
            throw new KIOException("Errors reading core ontologies: system will be nonfunctional.");
        }
    }

    /**
     * 
     * @param f
     * @param path
     * @param forcePath disregard directory structure and use passed path as prefix for ontology
     * @param monitor
     * @throws KlabException
     */
    private void loadInternal(File f, String path, boolean forcePath, Channel monitor) {

        String pth = path == null
                ? ""
                : (path + (path.isEmpty() ? "" : ".")
                        + Utils.CamelCase.toLowerCase(Utils.Files.getFileBaseName(f.toString()), '-'));

        if (forcePath) {
            pth = path;
        }

        if (f.isDirectory()) {
            if (!Utils.Files.getFileBaseName(f.toString()).startsWith(".")) {
                for (File fl : f.listFiles()) {
                    loadInternal(fl, pth, false, monitor);
                }
            }
        } else if (Utils.Files.getFileExtension(f.toString()).equals("owl")) {

            InputStream input;

            try {
                input = new FileInputStream(f);
                OWLOntology ontology = manager.loadOntologyFromOntologyDocument(input);
                input.close();
                Ontology ont = new Ontology(ontology, pth);
                ont.setResourceUrl(f.toURI().toURL().toString());
                ontologies.put(pth, ont);
                iri2ns.put(ont.getPrefix(), pth);

            } catch (OWLOntologyAlreadyExistsException e) {

                /*
                 * already imported- wrap it and use it as is.
                 */
                OWLOntology ont = manager.getOntology(e.getOntologyID().getOntologyIRI());
                if (ont != null && ontologies.get(pth) == null) {
                    Ontology o = new Ontology(ont, pth);
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
                monitor.error(new KIOException("reading " + f + ": " + e.getMessage()));
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

    public Ontology refreshOntology(URL url, String id) {

        InputStream input;
        Ontology ret = null;

        try {
            input = url.openStream();
            OWLOntology ontology = manager.loadOntologyFromOntologyDocument(input);
            input.close();
            ret = new Ontology(ontology, id);
            ret.setResourceUrl(url.toString());
            ontologies.put(id, ret);
            iri2ns.put(ret.getPrefix(), id);

        } catch (OWLOntologyAlreadyExistsException e) {

            /*
             * already imported- wrap it and use it as is.
             */
            OWLOntology ont = manager.getOntology(e.getOntologyID().getOntologyIRI());
            if (ont != null && ontologies.get(id) == null) {
                Ontology ontology = new Ontology(ont, id);
                ontologies.put(id, ontology);
                iri2ns.put(ontology.getPrefix(), id);
            }

        } catch (Exception e) {

            /*
             * everything else is probably an error
             */
            throw new KIOException(e);
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

        // FIXME do we need the file catalog? // remove from _csIndex - should be
        // harmless to leave
        // for now
        KimNamespace ns = this.namespaces.get(ontology.getName());
        if (ns != null) {
            // this.resourceIndex.remove(ns.getLocalFile().toString());
        }
        this.namespaces.remove(ontology.getName());
        ontologies.remove(ontology.getName());
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
            if (((Ontology) o).isInternal() && !includeInternal)
                continue;
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
            if (((Ontology) o).isInternal() && !includeInternal)
                continue;
            ret.addAll(o.getConcepts());
        }
        return ret;
    }

    public Concept getNothing() {
        if (this.nothing == null) {
            this.nothing = new ConceptImpl();
            this.nothing.getType().add(SemanticType.NOTHING);
            ((ConceptImpl) this.nothing).setId(registerOwlClass(manager.getOWLDataFactory().getOWLNothing()));
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
            for (OWLClassExpression s : owl.getSuperClasses(OWL.INSTANCE.manager.getOntologies())) {
                if (s instanceof OWLQuantifiedRestriction) {
                    if (getPropertyFor((OWLProperty<?, ?>) ((OWLQuantifiedRestriction<?, ?, ?>) s).getProperty()).is(restricted)
                            && ((OWLQuantifiedRestriction<?, ?, ?>) s).getFiller() instanceof OWLClassExpression) {
                        Collection<Concept> concepts = unwrap(
                                (OWLClassExpression) ((OWLQuantifiedRestriction<?, ?, ?>) s).getFiller());
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
            for (OWLClassExpression s : owl.getSuperClasses(OWL.INSTANCE.manager.getOntologies())) {
                if (s instanceof OWLQuantifiedRestriction) {
                    if (getPropertyFor((OWLProperty<?, ?>) ((OWLQuantifiedRestriction<?, ?, ?>) s).getProperty()).is(restricted)
                            && ((OWLQuantifiedRestriction<?, ?, ?>) s).getFiller() instanceof OWLClassExpression) {
                        ret.addAll(unwrap((OWLClassExpression) ((OWLQuantifiedRestriction<?, ?, ?>) s).getFiller()));
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
        return new SpecializingRestrictionVisitor(target, restricted, true).getResult();
    }

    public Collection<Concept> getRestrictedClasses(Concept target, Property restricted, boolean useSuperproperties) {
        return new SpecializingRestrictionVisitor(target, restricted, useSuperproperties).getResult();
    }

    public void restrictSome(Concept target, Property property, Concept filler, Ontology ontology) {
        getTargetOntology(ontology, target, property, filler)
                .define(Collections.singleton(Axiom.SomeValuesFrom(target.toString(), property.toString(), filler.toString())));
    }

    public void restrictAll(Concept target, Property property, LogicalConnector how, Collection<Concept> fillers,
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
        OWLClassExpression union = how.equals(LogicalConnector.UNION)
                ? factory.getOWLObjectUnionOf(classes)
                : factory.getOWLObjectIntersectionOf(classes);
        OWLClassExpression restriction = factory.getOWLObjectAllValuesFrom(property._owl.asOWLObjectProperty(), union);
        manager.addAxiom((getTargetOntology(ontology, target, property, fillers)).ontology,
                factory.getOWLSubClassOfAxiom(getOWLClass(target), restriction));
    }

    public void restrictSome(Concept target, Property property, LogicalConnector how, Collection<Concept> fillers,
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
        OWLClassExpression union = how.equals(LogicalConnector.UNION)
                ? factory.getOWLObjectUnionOf(classes)
                : factory.getOWLObjectIntersectionOf(classes);
        OWLClassExpression restriction = factory.getOWLObjectSomeValuesFrom(property._owl.asOWLObjectProperty(), union);
        manager.addAxiom(((Ontology) property.getOntology()).ontology,
                factory.getOWLSubClassOfAxiom(getOWLClass(target), restriction));
    }

    public void restrictAll(Concept target, Property property, Concept filler, Ontology ontology) {
        getTargetOntology(ontology, target, property, filler)
                .define(Collections.singleton(Axiom.AllValuesFrom(target.toString(), property.toString(), filler.toString())));
    }

    public void restrictAtLeast(Concept target, Property property, LogicalConnector how, Collection<Concept> fillers, int min,
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
        OWLClassExpression union = how.equals(LogicalConnector.UNION)
                ? factory.getOWLObjectUnionOf(classes)
                : factory.getOWLObjectIntersectionOf(classes);
        OWLClassExpression restriction = factory.getOWLObjectMinCardinality(min, property._owl.asOWLObjectProperty(), union);
        manager.addAxiom((getTargetOntology(ontology, target, property, fillers)).ontology,
                factory.getOWLSubClassOfAxiom(getOWLClass(target), restriction));
    }

    public void restrictAtLeast(Concept target, Property property, Concept filler, int min, Ontology ontology) {
        getTargetOntology(ontology, target, property, filler).define(
                Collections.singleton(Axiom.AtLeastNValuesFrom(target.toString(), property.toString(), filler.toString(), min)));
    }

    public void restrictAtMost(Concept target, Property property, LogicalConnector how, Collection<Concept> fillers, int max,
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
        OWLClassExpression union = how.equals(LogicalConnector.UNION)
                ? factory.getOWLObjectUnionOf(classes)
                : factory.getOWLObjectIntersectionOf(classes);
        OWLClassExpression restriction = factory.getOWLObjectMaxCardinality(max, ((Property) property)._owl.asOWLObjectProperty(),
                union);
        manager.addAxiom((getTargetOntology(ontology, target, property, fillers)).ontology,
                factory.getOWLSubClassOfAxiom(getOWLClass(target), restriction));
    }

    public void restrictAtMost(Concept target, Property property, Concept filler, int max, Ontology ontology) {
        getTargetOntology(ontology, target, property, filler).define(
                Collections.singleton(Axiom.AtMostNValuesFrom(target.toString(), property.toString(), filler.toString(), max)));
    }

    public void restrictExactly(Concept target, Property property, LogicalConnector how, Collection<Concept> fillers, int howmany,
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
        OWLClassExpression union = how.equals(LogicalConnector.UNION)
                ? factory.getOWLObjectUnionOf(classes)
                : factory.getOWLObjectIntersectionOf(classes);
        OWLClassExpression restriction = factory.getOWLObjectExactCardinality(howmany, property._owl.asOWLObjectProperty(),
                union);
        manager.addAxiom((getTargetOntology(ontology, target, property, fillers)).ontology,
                factory.getOWLSubClassOfAxiom(getOWLClass(target), restriction));
    }

    public void restrictExactly(Concept target, Property property, Concept filler, int howMany, Ontology ontology) {
        getTargetOntology(ontology, target, property, filler).define(Collections
                .singleton(Axiom.ExactlyNValuesFrom(target.toString(), property.toString(), filler.toString(), howMany)));
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
        return new ConceptRestrictionVisitor(type, concept).isOptional();
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
        return new ConceptRestrictionVisitor(type, concept).isDenied();
    }

    public Property getRestrictingProperty(Concept type, Concept concept) {
        ConceptRestrictionVisitor visitor = new ConceptRestrictionVisitor(type, concept);
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

    public Concept getIntersection(Collection<Concept> concepts, Ontology destination, Collection<SemanticType> stype) {

        EnumSet<SemanticType> type = EnumSet.copyOf(stype);
        type.add(SemanticType.INTERSECTION);

        List<String> ids = new ArrayList<>();
        Set<OWLClassExpression> classes = new HashSet<>();
        for (Concept c : concepts) {
            classes.add(getOWLClass(c));
            ids.add(c.toString().replace(':', '_'));
        }

        Collections.sort(ids);
        String id = "";
        for (String iid : ids) {
            id += (id.isEmpty() ? "" : "__and__") + iid;
        }

        Concept ret = ((Ontology) destination).getConcept(id);
        if (ret != null) {
            return ret;
        }

        OWLDataFactory factory = manager.getOWLDataFactory();
        OWLClassExpression union = factory.getOWLObjectIntersectionOf(classes);
        ret = (ConceptImpl) ((Ontology) destination).createConcept(id, type);
        manager.addAxiom(((Ontology) destination).ontology, factory.getOWLSubClassOfAxiom(getOWLClass(ret), union));

        return ret;
    }

    public Concept getUnion(Collection<Concept> concepts, Ontology destination, Collection<SemanticType> stype) {

        EnumSet<SemanticType> type = EnumSet.copyOf(stype);
        type.add(SemanticType.UNION);

        List<String> ids = new ArrayList<>();
        Set<OWLClassExpression> classes = new HashSet<>();
        for (Concept c : concepts) {
            classes.add(getOWLClass(c));
            ids.add(c.toString().replace(':', '_'));
        }

        Collections.sort(ids);
        String id = "";
        for (String iid : ids) {
            id += (id.isEmpty() ? "" : "__or__") + iid;
        }

        Concept ret = ((Ontology) destination).getConcept(id);
        if (ret != null) {
            return ret;
        }

        OWLDataFactory factory = manager.getOWLDataFactory();
        OWLClassExpression union = factory.getOWLObjectUnionOf(classes);
        ret = (ConceptImpl) ((Ontology) destination).createConcept(id, type);
        manager.addAxiom(((Ontology) destination).ontology, factory.getOWLSubClassOfAxiom(getOWLClass(ret), union));

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

        File out = new File(
                /*
                 * Configuration.INSTANCE.getDataPath("knowledge/.imports") + File.separator +
                 */ prefix + ".owl");
        try {
            Utils.URLs.copyChanneled(new URL(url), out);
            loadInternal(out, prefix, true, monitor);
        } catch (MalformedURLException e) {
            monitor.error(e);
            return null;
        }

        return prefix;
    }

    public Concept getNonsemanticPeer(String name, Artifact.Type type) {

        String conceptId = Utils.Strings.capitalize(type.name().toLowerCase()) + CamelCase.toUpperCamelCase(name, '.');
        SemanticType qualityType = null;
        switch(type) {
        case TEXT:
            qualityType = SemanticType.CATEGORY;
            break;
        case NUMBER:
            qualityType = SemanticType.QUANTITY;
            break;
        case CONCEPT:
            qualityType = SemanticType.CLASS;
            break;
        case BOOLEAN:
            qualityType = SemanticType.PRESENCE;
            break;
        case OBJECT:
            qualityType = SemanticType.SUBJECT;
            break;
        case EVENT:
            qualityType = SemanticType.EVENT;
            break;
        default:
            throw new IllegalArgumentException("wrong type passed for non-semantic peer generation: " + type);
        }
        EnumSet<SemanticType> identity = type.isCountable()
                ? EnumSet.of(SemanticType.SUBJECT, SemanticType.OBSERVABLE, SemanticType.DIRECT_OBSERVABLE,
                        SemanticType.COUNTABLE)
                : EnumSet.of(SemanticType.QUALITY, SemanticType.OBSERVABLE, qualityType);

        Concept ret = nonSemanticConcepts.getConcept(conceptId);
        if (ret != null) {
            if (!ret.is(qualityType)) {
                throw new KInternalErrorException(
                        "non-semantic peer concept for " + name + " was declared previously with a different type");
            }
            return ret;
        }

        nonSemanticConcepts.define(Collections.singletonList(Axiom.ClassAssertion(conceptId, identity)));

        return nonSemanticConcepts.getConcept(conceptId);
    }

    /**
     * True if the passed object has true semantics, i.e. is not a non-semantic object.
     * 
     * @param observable
     * @return
     */
    public boolean isSemantic(Semantics observable) {
        return !observable.semantics().getNamespace().equals(nonSemanticConcepts.getName());
    }

    public Ontology readOntology(String string) {
        try {
            return new Ontology(manager.loadOntology(IRI.create(string)), Utils.URLs.getURLBaseName(string));
        } catch (OWLOntologyCreationException e) {
            throw new KIOException(e);
        }
    }

    public void restrict(Concept target, Property property, LogicalConnector how, Collection<Concept> fillers, Ontology ontology)
            throws KValidationException {

        /*
         * divide up in bins according to base trait; take property from annotation; restrict each
         * group.
         */
        Map<Concept, List<Concept>> pairs = new HashMap<>();
        for (Concept t : fillers) {
            Concept base = Services.INSTANCE.getReasoner().baseParentTrait(t);
            if (base == null) {

            } else if (!pairs.containsKey(base)) {
                pairs.put(base, new ArrayList<>());
            }
            pairs.get(base).add(t);
        }

        for (Concept base : pairs.keySet()) {

            String prop = base.getMetadata().get(CoreOntology.NS.TRAIT_RESTRICTING_PROPERTY, String.class);
            if (prop == null || getProperty(prop) == null) {
                if (base.is(SemanticType.SUBJECTIVE)) {
                    /*
                     * we can assign any subjective traits to anything
                     */
                    prop = CoreOntology.NS.HAS_SUBJECTIVE_TRAIT_PROPERTY;
                } else {
                    throw new KValidationException("cannot find a property to restrict for trait " + base);
                }
            }
            OWL.INSTANCE.restrictSome(target, getProperty(prop), how, pairs.get(base), (Ontology) ontology);
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
        restrictSome(type, getProperty(CoreOntology.NS.APPLIES_TO_PROPERTY), LogicalConnector.UNION, applicables, ontology);
    }

    public void defineRelationship(Concept relationship, Concept source, Concept target, Ontology ontology) {
        Property hasSource = getProperty(CoreOntology.NS.IMPLIES_SOURCE_PROPERTY);
        Property hasTarget = getProperty(CoreOntology.NS.IMPLIES_DESTINATION_PROPERTY);
        restrictSome(relationship, hasSource, LogicalConnector.UNION, Collections.singleton(source), ontology);
        restrictSome(relationship, hasTarget, LogicalConnector.UNION, Collections.singleton(target), ontology);
    }

    /**
     * Analyze an observable concept and return the main observable with all the original identities
     * and realms but no attributes; separately, return the list of the attributes that were
     * removed.
     * 
     * @param observable
     * @return attribute profile
     * @throws KlabValidationException
     */
    public Pair<Concept, Collection<Concept>> separateAttributes(Concept observable) {

        Concept obs = Services.INSTANCE.getReasoner().coreObservable(observable);
        ArrayList<Concept> tret = new ArrayList<>();
        ArrayList<Concept> keep = new ArrayList<>();

        for (Concept zt : Services.INSTANCE.getReasoner().traits(observable)) {
            if (zt.is(OWL.INSTANCE.getConcept(CoreOntology.NS.CORE_IDENTITY)) || zt.is(getConcept(CoreOntology.NS.CORE_REALM))) {
                keep.add(zt);
            } else {
                tret.add(zt);
            }
        }

        Concept root = obs; 
//            Services.INSTANCE.getReasoner().declareObservable((obs == null ? observable : obs), keep,
//            Services.INSTANCE.getReasoner().context(observable), Services.INSTANCE.getReasoner().inherent(observable));

        return new Pair<>(root, tret);
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
            restrict(main, property, LogicalConnector.UNION, Collections.singleton(trait), (Ontology) ontology);
        }
    }

    public Concept makeNegation(Concept attribute, Ontology ontology) {

        Concept negation = Services.INSTANCE.getReasoner().negated(attribute);
        if (negation != null) {
            return negation;
        }

        Ontology aontology = getOntology(attribute.getNamespace());
        String prop = attribute.getMetadata().get(CoreOntology.NS.TRAIT_RESTRICTING_PROPERTY, String.class);
        String conceptId = "Not" + getCleanId(attribute);
        Concept ret = ontology.getConcept(conceptId);
        Concept parent = attribute.parent();

        if (ret == null) {

            EnumSet<SemanticType> newType = EnumSet.copyOf(attribute.getType());

            aontology.add(Axiom.ClassAssertion(conceptId, newType));
            aontology.add(Axiom.SubClass(parent.toString(), conceptId));
            aontology.add(Axiom.AnnotationAssertion(conceptId, CoreOntology.NS.BASE_DECLARATION, "true"));
            aontology.add(Axiom.AnnotationAssertion(conceptId, CoreOntology.NS.REFERENCE_NAME_PROPERTY,
                    "not_" + attribute.getReferenceName()));
            aontology.add(Axiom.AnnotationAssertion(conceptId, "rdfs:label", "Not" + getCleanId(attribute)));
            aontology.add(Axiom.AnnotationAssertion(conceptId, NS.CONCEPT_DEFINITION_PROPERTY, "not  " + attribute.getUrn()));

            if (prop != null) {
                aontology.add(Axiom.AnnotationAssertion(conceptId, NS.TRAIT_RESTRICTING_PROPERTY, prop));
            }

            aontology.define();

            ret = aontology.getConcept(conceptId);

            OWL.INSTANCE.restrictSome(ret, getProperty(CoreOntology.NS.IS_NEGATION_OF), attribute, (Ontology) ontology);
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
        if (trigger.is(SemanticType.INTERSECTION) || trigger.is(SemanticType.UNION)) {
            for (Concept tr : trigger.operands()) {
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
     * Turn a concept into its change if it's not already one, implementing the corresponding
     * semantic operator.
     * 
     * @param concept the untransformed concept
     * @param addDefinition add the {@link NS#CONCEPT_DEFINITION_PROPERTY} annotation; pass true if
     *        used from outside the builder
     * @return the transformed concept
     */
    public Concept makeChange(Concept concept, boolean addDefinition) {

        String cName = getCleanId(concept) + "Change";

        if (!concept.is(SemanticType.QUALITY)) {
            return null;
        }

        // this.hasUnaryOp = true;

        String definition = UnarySemanticOperator.CHANGE.declaration[0] + " " + concept.getUrn();
        Ontology ontology = getOntology(concept.getNamespace());
        String conceptId = ontology.getIdForDefinition(definition);
        Concept context = Services.INSTANCE.getReasoner().context(concept);

        if (conceptId == null) {

            conceptId = ontology.createIdForDefinition(definition);

            String reference = UnarySemanticOperator.CHANGE.getReferenceName(concept.getReferenceName(), null);

            Set<SemanticType> newType = UnarySemanticOperator.CHANGE.apply(concept.getType());

            ArrayList<IAxiom> ax = new ArrayList<>();
            ax.add(Axiom.ClassAssertion(conceptId, newType));
            ax.add(Axiom.SubClass(NS.CORE_CHANGE, conceptId));
            ax.add(Axiom.AnnotationAssertion(conceptId, NS.REFERENCE_NAME_PROPERTY, reference));
            ax.add(Axiom.AnnotationAssertion(conceptId, NS.BASE_DECLARATION, "true"));
            ax.add(Axiom.AnnotationAssertion(conceptId, "rdfs:label", cName));
            ax.add(Axiom.AnnotationAssertion(conceptId, "rdfs:label", cName));

            if (addDefinition) {
                ax.add(Axiom.AnnotationAssertion(conceptId, NS.CONCEPT_DEFINITION_PROPERTY, definition));
            }
            ontology.define(ax);

            Concept ret = ontology.getConcept(conceptId);

            OWL.INSTANCE.restrictSome(ret, getProperty(CoreOntology.NS.DESCRIBES_OBSERVABLE_PROPERTY), concept, ontology);
            OWL.INSTANCE.restrictSome(ret, getProperty(CoreOntology.NS.CHANGES_PROPERTY), concept, ontology);

            /*
             * context of the change is the same context as the quality it describes - FIXME this
             * shouldn't be needed as the inherency is an alternative place to look for context.
             */
            if (context != null) {
                OWL.INSTANCE.restrictSome(ret, getProperty(NS.HAS_CONTEXT_PROPERTY), context, ontology);
            }

        }

        return ontology.getConcept(conceptId);
    }

    /**
     * Turn a concept into its change if it's not already one, implementing the corresponding
     * semantic operator.
     * 
     * @param concept the untransformed concept
     * @param addDefinition add the {@link NS#CONCEPT_DEFINITION_PROPERTY} annotation; pass true if
     *        used from outside the builder
     * @return the transformed concept
     */
    public Concept makeRate(Concept concept, boolean addDefinition) {

        String cName = getCleanId(concept) + "ChangeRate";

        if (!concept.is(SemanticType.QUALITY)) {
            return null;
        }

        // this.hasUnaryOp = true;

        String definition = UnarySemanticOperator.RATE.declaration[0] + " " + concept.getUrn();
        Ontology ontology = getOntology(concept.getNamespace());
        String conceptId = ontology.getIdForDefinition(definition);
        Concept context = Services.INSTANCE.getReasoner().context(concept);

        if (conceptId == null) {

            conceptId = ontology.createIdForDefinition(definition);

            String reference = UnarySemanticOperator.RATE.getReferenceName(concept.getReferenceName(), null);

            Set<SemanticType> newType = UnarySemanticOperator.RATE.apply(concept.getType());

            ArrayList<IAxiom> ax = new ArrayList<>();
            ax.add(Axiom.ClassAssertion(conceptId, newType));
            ax.add(Axiom.SubClass(NS.CORE_CHANGE_RATE, conceptId));
            ax.add(Axiom.AnnotationAssertion(conceptId, NS.REFERENCE_NAME_PROPERTY, reference));
            ax.add(Axiom.AnnotationAssertion(conceptId, NS.BASE_DECLARATION, "true"));
            ax.add(Axiom.AnnotationAssertion(conceptId, "rdfs:label", cName));
            ax.add(Axiom.AnnotationAssertion(conceptId, "rdfs:label", cName));

            if (addDefinition) {
                ax.add(Axiom.AnnotationAssertion(conceptId, NS.CONCEPT_DEFINITION_PROPERTY, definition));
            }
            ontology.define(ax);

            Concept ret = ontology.getConcept(conceptId);

            OWL.INSTANCE.restrictSome(ret, getProperty(CoreOntology.NS.DESCRIBES_OBSERVABLE_PROPERTY), concept, ontology);

            /*
             * context of the change is the same context as the quality it describes - FIXME this
             * shouldn't be needed as the inherency is an alternative place to look for context.
             */
            if (context != null) {
                OWL.INSTANCE.restrictSome(ret, getProperty(NS.HAS_CONTEXT_PROPERTY), context, ontology);
            }

        }

        return ontology.getConcept(conceptId);
    }

    /**
     * Turn a concept into its change if it's not already one, implementing the corresponding
     * semantic operator.
     * 
     * @param concept the untransformed concept
     * @param addDefinition add the {@link NS#CONCEPT_DEFINITION_PROPERTY} annotation; pass true if
     *        used from outside the builder
     * @return the transformed concept
     */
    public Concept makeChanged(Concept concept, boolean addDefinition) {

        String cName = "Changed" + getCleanId(concept);

        if (!concept.is(SemanticType.QUALITY)) {
            return null;
        }

        // this.hasUnaryOp = true;

        String definition = UnarySemanticOperator.CHANGED.declaration[0] + " " + concept.getUrn();
        Ontology ontology = getOntology(concept.getNamespace());
        String conceptId = ontology.getIdForDefinition(definition);
        Concept context = Services.INSTANCE.getReasoner().context(concept);

        if (conceptId == null) {

            conceptId = ontology.createIdForDefinition(definition);

            String reference = UnarySemanticOperator.CHANGED.getReferenceName(concept.getReferenceName(), null);

            Set<SemanticType> newType = UnarySemanticOperator.CHANGED.apply(concept.getType());

            ArrayList<IAxiom> ax = new ArrayList<>();
            ax.add(Axiom.ClassAssertion(conceptId, newType));
            ax.add(Axiom.SubClass(NS.CORE_EVENT, conceptId));
            ax.add(Axiom.AnnotationAssertion(conceptId, NS.REFERENCE_NAME_PROPERTY, reference));
            ax.add(Axiom.AnnotationAssertion(conceptId, NS.BASE_DECLARATION, "true"));
            ax.add(Axiom.AnnotationAssertion(conceptId, "rdfs:label", cName));
            ax.add(Axiom.AnnotationAssertion(conceptId, "rdfs:label", cName));

            if (addDefinition) {
                ax.add(Axiom.AnnotationAssertion(conceptId, NS.CONCEPT_DEFINITION_PROPERTY, definition));
            }
            ontology.define(ax);

            Concept ret = ontology.getConcept(conceptId);

            restrictSome(ret, getProperty(CoreOntology.NS.DESCRIBES_OBSERVABLE_PROPERTY), concept, ontology);
            restrictSome(ret, getProperty(CoreOntology.NS.CHANGED_PROPERTY), concept, ontology);

            /*
             * context of the change event is the same context as the quality it describes - FIXME
             * this shouldn't be needed as the inherency is an alternative place to look for
             * context.
             */
            if (context != null) {
                restrictSome(ret, getProperty(NS.HAS_CONTEXT_PROPERTY), context, ontology);
            }

        }

        return ontology.getConcept(conceptId);
    }

    /**
     * Turn a concept into its count/numerosity if it's not already one, implementing the
     * corresponding semantic operator. Also makes the original concept the numerosity's inherent.
     * 
     * @param concept the untransformed concept
     * @param addDefinition add the {@link NS#CONCEPT_DEFINITION_PROPERTY} annotation; pass true if
     *        used from outside the builder
     * @return the transformed concept
     */
    public Concept makeCount(Concept concept, boolean addDefinition) {

        /*
         * first, ensure we're counting countable things.
         */
        if (!concept.is(SemanticType.COUNTABLE)) {
            throw new KValidationException("cannot count a non-countable observable");
        }

        // this.hasUnaryOp = true;

        String cName = getCleanId(concept) + "Count";

        /*
         * make a ConceptCount if not there, and ensure it's a continuously quantifiable quality.
         * Must be in same ontology as the original concept.
         */
        String definition = UnarySemanticOperator.COUNT.declaration[0] + " " + concept.getUrn();
        String reference = UnarySemanticOperator.COUNT.getReferenceName(concept.getReferenceName(), null);
        Ontology ontology = getOntology(concept.getNamespace());
        String conceptId = ontology.getIdForDefinition(definition);

        if (conceptId == null) {

            conceptId = ontology.createIdForDefinition(definition);

            Set<SemanticType> newType = UnarySemanticOperator.COUNT.apply(concept.getType());
            ArrayList<IAxiom> ax = new ArrayList<>();
            ax.add(Axiom.ClassAssertion(conceptId, newType));
            ax.add(Axiom.SubClass(NS.CORE_COUNT, conceptId));
            ax.add(Axiom.AnnotationAssertion(conceptId, NS.REFERENCE_NAME_PROPERTY, reference));
            ax.add(Axiom.AnnotationAssertion(conceptId, NS.BASE_DECLARATION, "true"));
            ax.add(Axiom.AnnotationAssertion(conceptId, "rdfs:label", cName));
            if (addDefinition) {
                ax.add(Axiom.AnnotationAssertion(conceptId, NS.CONCEPT_DEFINITION_PROPERTY, definition));
            }
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
     * @param addDefinition add the {@link NS#CONCEPT_DEFINITION_PROPERTY} annotation; pass true if
     *        used from outside the builder
     * @return the transformed concept
     */
    public Concept makeDistance(Concept concept, boolean addDefinition) {

        if (!concept.is(SemanticType.COUNTABLE)) {
            throw new KValidationException("cannot compute the distance to a non-countable observable");
        }

        // this.hasUnaryOp = true;

        String cName = "DistanceTo" + getCleanId(concept);
        String definition = UnarySemanticOperator.DISTANCE.declaration[0] + " " + concept.getUrn();
        Ontology ontology = getOntology(concept.getNamespace());
        String conceptId = ontology.getIdForDefinition(definition);

        if (conceptId == null) {

            conceptId = ontology.createIdForDefinition(definition);
            String reference = UnarySemanticOperator.DISTANCE.getReferenceName(concept.getReferenceName(), null);

            Set<SemanticType> newType = UnarySemanticOperator.DISTANCE.apply(concept.getType());

            ArrayList<IAxiom> ax = new ArrayList<>();
            ax.add(Axiom.ClassAssertion(conceptId, newType));
            ax.add(Axiom.SubClass(NS.CORE_DISTANCE, conceptId));
            ax.add(Axiom.AnnotationAssertion(conceptId, NS.REFERENCE_NAME_PROPERTY, reference));
            ax.add(Axiom.AnnotationAssertion(conceptId, NS.BASE_DECLARATION, "true"));
            ax.add(Axiom.AnnotationAssertion(conceptId, "rdfs:label", cName));
            if (addDefinition) {
                ax.add(Axiom.AnnotationAssertion(conceptId, NS.CONCEPT_DEFINITION_PROPERTY, definition));
            }
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
     * @param addDefinition add the {@link NS#CONCEPT_DEFINITION_PROPERTY} annotation; pass true if
     *        used from outside the builder
     * @return the transformed concept
     */
    public Concept makePresence(Concept concept, boolean addDefinition) {

        if (concept.is(SemanticType.QUALITY) || concept.is(SemanticType.CONFIGURATION) || concept.is(SemanticType.TRAIT)
                || concept.is(SemanticType.ROLE)) {
            throw new KValidationException("presence can be observed only for subjects, events, processes and relationships");
        }

        // this.hasUnaryOp = true;

        String cName = getCleanId(concept) + "Presence";
        String definition = UnarySemanticOperator.PRESENCE.declaration[0] + " " + concept.getUrn();
        Ontology ontology = getOntology(concept.getNamespace());
        String conceptId = ontology.getIdForDefinition(definition);

        if (conceptId == null) {

            conceptId = ontology.createIdForDefinition(definition);

            String reference = UnarySemanticOperator.PRESENCE.getReferenceName(concept.getReferenceName(), null);

            Set<SemanticType> newType = UnarySemanticOperator.PRESENCE.apply(concept.getType());
            ArrayList<IAxiom> ax = new ArrayList<>();
            ax.add(Axiom.ClassAssertion(conceptId, newType));
            ax.add(Axiom.SubClass(NS.CORE_PRESENCE, conceptId));
            ax.add(Axiom.AnnotationAssertion(conceptId, NS.BASE_DECLARATION, "true"));
            ax.add(Axiom.AnnotationAssertion(conceptId, NS.REFERENCE_NAME_PROPERTY, reference));
            ax.add(Axiom.AnnotationAssertion(conceptId, "rdfs:label", cName));
            if (addDefinition) {
                ax.add(Axiom.AnnotationAssertion(conceptId, NS.CONCEPT_DEFINITION_PROPERTY, definition));
            }
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
     * @param addDefinition add the {@link NS#CONCEPT_DEFINITION_PROPERTY} annotation; pass true if
     *        used from outside the builder
     * @return the transformed concept
     */
    public Concept makeOccurrence(Concept concept, boolean addDefinition) {

        if (!concept.is(SemanticType.DIRECT_OBSERVABLE)) {
            throw new KValidationException(
                    "occurrences (probability of presence) can be observed only for subjects, events, processes and relationships");
        }

        // this.hasUnaryOp = true;

        String cName = getCleanId(concept) + "Occurrence";
        String definition = UnarySemanticOperator.OCCURRENCE.declaration[0] + " " + concept.getUrn();
        Ontology ontology = getOntology(concept.getNamespace());
        String conceptId = ontology.getIdForDefinition(definition);

        if (conceptId == null) {

            String reference = UnarySemanticOperator.OCCURRENCE.getReferenceName(concept.getReferenceName(), null);

            conceptId = ontology.createIdForDefinition(definition);
            Set<SemanticType> newType = UnarySemanticOperator.OCCURRENCE.apply(concept.getType());

            ArrayList<IAxiom> ax = new ArrayList<>();
            ax.add(Axiom.ClassAssertion(conceptId, newType));
            ax.add(Axiom.SubClass(NS.CORE_OCCURRENCE, conceptId));
            ax.add(Axiom.AnnotationAssertion(conceptId, NS.REFERENCE_NAME_PROPERTY, reference));
            ax.add(Axiom.AnnotationAssertion(conceptId, NS.BASE_DECLARATION, "true"));
            ax.add(Axiom.AnnotationAssertion(conceptId, "rdfs:label", cName));

            if (addDefinition) {
                ax.add(Axiom.AnnotationAssertion(conceptId, NS.CONCEPT_DEFINITION_PROPERTY, definition));
            }
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
     * @param addDefinition add the {@link NS#CONCEPT_DEFINITION_PROPERTY} annotation; pass true if
     *        used from outside the builder
     * @return the transformed concept
     */
    public Concept makeMagnitude(Concept concept, boolean addDefinition) {

        if (Sets.intersection(concept.getType(), SemanticType.CONTINUOUS_QUALITY_TYPES).size() == 0) {
            throw new KValidationException("magnitudes can only be observed only for quantifiable qualities");
        }

        // this.hasUnaryOp = true;

        String cName = getCleanId(concept) + "Magnitude";
        String definition = UnarySemanticOperator.MAGNITUDE.declaration[0] + " " + concept.getUrn();
        Ontology ontology = getOntology(concept.getNamespace());
        String conceptId = ontology.getIdForDefinition(definition);

        if (conceptId == null) {

            conceptId = ontology.createIdForDefinition(definition);

            String reference = UnarySemanticOperator.MAGNITUDE.getReferenceName(concept.getReferenceName(), null);

            Set<SemanticType> newType = UnarySemanticOperator.MAGNITUDE.apply(concept.getType());

            ArrayList<IAxiom> ax = new ArrayList<>();
            ax.add(Axiom.ClassAssertion(conceptId, newType));
            ax.add(Axiom.SubClass(NS.CORE_MAGNITUDE, conceptId));
            ax.add(Axiom.AnnotationAssertion(conceptId, NS.REFERENCE_NAME_PROPERTY, reference));
            ax.add(Axiom.AnnotationAssertion(conceptId, NS.BASE_DECLARATION, "true"));
            ax.add(Axiom.AnnotationAssertion(conceptId, "rdfs:label", cName));
            if (addDefinition) {
                ax.add(Axiom.AnnotationAssertion(conceptId, NS.CONCEPT_DEFINITION_PROPERTY, definition));
            }
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
     * @param addDefinition add the {@link NS#CONCEPT_DEFINITION_PROPERTY} annotation; pass true if
     *        used from outside the builder
     * @return the transformed concept
     */
    public Concept makeLevel(Concept concept, boolean addDefinition) {

        if (Sets.intersection(concept.getType(), SemanticType.CONTINUOUS_QUALITY_TYPES).size() == 0) {
            throw new KValidationException("magnitudes can only be observed only for quantifiable qualities");
        }

        // this.hasUnaryOp = true;

        String cName = getCleanId(concept) + "Level";
        String definition = UnarySemanticOperator.LEVEL.declaration[0] + " " + concept.getUrn();
        Ontology ontology = getOntology(concept.getNamespace());
        String conceptId = ontology.getIdForDefinition(definition);

        if (conceptId == null) {

            conceptId = ontology.createIdForDefinition(definition);

            Set<SemanticType> newType = UnarySemanticOperator.LEVEL.apply(concept.getType());

            String reference = UnarySemanticOperator.LEVEL.getReferenceName(concept.getReferenceName(), null);

            ArrayList<IAxiom> ax = new ArrayList<>();
            ax.add(Axiom.ClassAssertion(conceptId, newType));
            ax.add(Axiom.SubClass(NS.CORE_LEVEL, conceptId));
            ax.add(Axiom.AnnotationAssertion(conceptId, NS.REFERENCE_NAME_PROPERTY, reference));
            ax.add(Axiom.AnnotationAssertion(conceptId, NS.BASE_DECLARATION, "true"));
            ax.add(Axiom.AnnotationAssertion(conceptId, "rdfs:label", cName));
            if (addDefinition) {
                ax.add(Axiom.AnnotationAssertion(conceptId, NS.CONCEPT_DEFINITION_PROPERTY, definition));
            }
            ontology.define(ax);
            Concept ret = ontology.getConcept(conceptId);

            /*
             * probability is inherent to the event that's possible.
             */
            OWL.INSTANCE.restrictSome(ret, getProperty(NS.DESCRIBES_OBSERVABLE_PROPERTY), concept, ontology);
        }

        return ontology.getConcept(conceptId);
    }

    /**
     * Turn a concept into its probability if it's not already one, implementing the corresponding
     * semantic operator. Also makes the original concept the numerosity's inherent.
     * 
     * @param concept the untransformed concept. Must be an event.
     * @param addDefinition add the {@link NS#CONCEPT_DEFINITION_PROPERTY} annotation; pass true if
     *        used from outside the builder
     * @return the transformed concept
     */
    public Concept makeProbability(Concept concept, boolean addDefinition) {

        if (!concept.is(SemanticType.EVENT)) {
            throw new KValidationException("probabilities can only be observed only for events");
        }

        // this.hasUnaryOp = true;

        String cName = getCleanId(concept) + "Probability";
        String definition = UnarySemanticOperator.PROBABILITY.declaration[0] + " " + concept.getUrn();
        Ontology ontology = getOntology(concept.getNamespace());
        String conceptId = ontology.getIdForDefinition(definition);

        if (conceptId == null) {

            conceptId = ontology.createIdForDefinition(definition);

            Set<SemanticType> newType = UnarySemanticOperator.PROBABILITY.apply(concept.getType());

            String reference = UnarySemanticOperator.PROBABILITY.getReferenceName(concept.getReferenceName(), null);

            ArrayList<IAxiom> ax = new ArrayList<>();
            ax.add(Axiom.ClassAssertion(conceptId, newType));
            ax.add(Axiom.SubClass(NS.CORE_PROBABILITY, conceptId));
            ax.add(Axiom.AnnotationAssertion(conceptId, NS.REFERENCE_NAME_PROPERTY, reference));
            ax.add(Axiom.AnnotationAssertion(conceptId, NS.BASE_DECLARATION, "true"));
            ax.add(Axiom.AnnotationAssertion(conceptId, "rdfs:label", cName));
            if (addDefinition) {
                ax.add(Axiom.AnnotationAssertion(conceptId, NS.CONCEPT_DEFINITION_PROPERTY, definition));
            }
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
     * @param addDefinition add the {@link NS#CONCEPT_DEFINITION_PROPERTY} annotation; pass true if
     *        used from outside the builder
     * @return the transformed concept
     */
    public Concept makeUncertainty(Concept concept, boolean addDefinition) {

        String cName = "UncertaintyOf" + getCleanId(concept);
        String definition = UnarySemanticOperator.UNCERTAINTY.declaration[0] + " " + concept.getUrn();
        Ontology ontology = getOntology(concept.getNamespace());
        String conceptId = ontology.getIdForDefinition(definition);

        // this.hasUnaryOp = true;

        if (conceptId == null) {

            String reference = UnarySemanticOperator.UNCERTAINTY.getReferenceName(concept.getReferenceName(), null);

            conceptId = ontology.createIdForDefinition(definition);
            Set<SemanticType> newType = UnarySemanticOperator.UNCERTAINTY.apply(concept.getType());

            ArrayList<IAxiom> ax = new ArrayList<>();
            ax.add(Axiom.ClassAssertion(conceptId, newType));
            ax.add(Axiom.SubClass(NS.CORE_UNCERTAINTY, conceptId));
            ax.add(Axiom.AnnotationAssertion(conceptId, NS.REFERENCE_NAME_PROPERTY, reference));
            ax.add(Axiom.AnnotationAssertion(conceptId, NS.BASE_DECLARATION, "true"));
            ax.add(Axiom.AnnotationAssertion(conceptId, "rdfs:label", cName));
            if (addDefinition) {
                ax.add(Axiom.AnnotationAssertion(conceptId, NS.CONCEPT_DEFINITION_PROPERTY, definition));
            }
            ontology.define(ax);
            Concept ret = ontology.getConcept(conceptId);
            /*
             * uncertainty is inherent to the thing that's present.
             */
            restrictSome(ret, getProperty(NS.DESCRIBES_OBSERVABLE_PROPERTY), concept, ontology);
        }

        return ontology.getConcept(conceptId);
    }

    public Concept makeProportion(Concept concept, Concept comparison, boolean addDefinition, boolean isPercentage) {

        if (!(concept.is(SemanticType.QUALITY) || concept.is(SemanticType.TRAIT))
                && (comparison != null && !comparison.is(SemanticType.QUALITY))) {
            throw new KValidationException("proportion must be of qualities or traits to qualities");
        }

        String cName = getCleanId(concept) + (isPercentage ? "Percentage" : "Proportion")
                + (comparison == null ? "" : getCleanId(comparison));

        // this.hasUnaryOp = true;

        String definition = (isPercentage
                ? UnarySemanticOperator.PERCENTAGE.declaration[0]
                : UnarySemanticOperator.PROPORTION.declaration[0])
                + " (" + concept.getUrn() + ")"
                + (comparison == null
                        ? ""
                        : (" " + (isPercentage
                                ? UnarySemanticOperator.PERCENTAGE.declaration[1]
                                : UnarySemanticOperator.PROPORTION.declaration[1]) + " (" + comparison.getUrn() + ")"));

        Ontology ontology = getOntology(concept.getNamespace());
        String conceptId = ontology.getIdForDefinition(definition);

        if (conceptId == null) {

            conceptId = ontology.createIdForDefinition(definition);

            String reference = isPercentage
                    ? UnarySemanticOperator.PERCENTAGE.getReferenceName(concept.getReferenceName(),
                            comparison == null ? null : comparison.getReferenceName())
                    : UnarySemanticOperator.PROPORTION.getReferenceName(concept.getReferenceName(),
                            comparison == null ? null : comparison.getReferenceName());

            Set<SemanticType> newType = isPercentage
                    ? UnarySemanticOperator.PERCENTAGE.apply(concept.getType())
                    : UnarySemanticOperator.PROPORTION.apply(concept.getType());

            ArrayList<IAxiom> ax = new ArrayList<>();
            ax.add(Axiom.ClassAssertion(conceptId, newType));
            ax.add(Axiom.SubClass(NS.CORE_PROPORTION, conceptId));
            ax.add(Axiom.AnnotationAssertion(conceptId, NS.REFERENCE_NAME_PROPERTY, reference));
            ax.add(Axiom.AnnotationAssertion(conceptId, NS.BASE_DECLARATION, "true"));
            ax.add(Axiom.AnnotationAssertion(conceptId, "rdfs:label", cName));
            if (addDefinition) {
                ax.add(Axiom.AnnotationAssertion(conceptId, NS.CONCEPT_DEFINITION_PROPERTY, definition));
            }
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

    public Concept makeRatio(Concept concept, Concept comparison, boolean addDefinition) {

        /*
         * accept only two qualities of the same physical nature (TODO)
         */
        if (!(concept.is(SemanticType.QUALITY) || concept.is(SemanticType.TRAIT)) || !comparison.is(SemanticType.QUALITY)) {
            throw new KValidationException("ratios must be between qualities of the same nature or traits to qualities");
        }

        // this.hasUnaryOp = true;

        String cName = getCleanId(concept) + "To" + getCleanId(comparison) + "Ratio";

        String definition = UnarySemanticOperator.RATIO.declaration[0] + " (" + concept.getUrn() + ")"
                + (comparison == null
                        ? ""
                        : " " + (UnarySemanticOperator.RATIO.declaration[1] + " (" + comparison.getUrn() + ")"));

        Ontology ontology = getOntology(concept.getNamespace());
        String conceptId = ontology.getIdForDefinition(definition);

        if (conceptId == null) {

            conceptId = ontology.createIdForDefinition(definition);

            String reference = UnarySemanticOperator.RATIO.getReferenceName(concept.getReferenceName(),
                    comparison == null ? null : comparison.getReferenceName());

            Set<SemanticType> newType = UnarySemanticOperator.RATIO.apply(concept.getType());

            ArrayList<IAxiom> ax = new ArrayList<>();
            ax.add(Axiom.ClassAssertion(conceptId, newType));
            ax.add(Axiom.SubClass(NS.CORE_RATIO, conceptId));
            ax.add(Axiom.AnnotationAssertion(conceptId, NS.REFERENCE_NAME_PROPERTY, reference));
            ax.add(Axiom.AnnotationAssertion(conceptId, NS.BASE_DECLARATION, "true"));
            ax.add(Axiom.AnnotationAssertion(conceptId, "rdfs:label", cName));
            if (addDefinition) {
                ax.add(Axiom.AnnotationAssertion(conceptId, NS.CONCEPT_DEFINITION_PROPERTY, definition));
            }

            // unit for ratios of physical properties
            if ((concept.is(SemanticType.EXTENSIVE_PROPERTY) || concept.is(SemanticType.INTENSIVE_PROPERTY))
                    && (comparison.is(SemanticType.EXTENSIVE_PROPERTY) || comparison.is(SemanticType.INTENSIVE_PROPERTY))) {
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

    public Concept makeValue(Concept concept, Concept comparison, boolean addDefinition, boolean monetary) {

        String cName = (monetary ? "MonetaryValueOf" : "ValueOf") + getCleanId(concept)
                + (comparison == null ? "" : ("Vs" + getCleanId(comparison)));

        String definition = (monetary
                ? UnarySemanticOperator.MONETARY_VALUE.declaration[0]
                : UnarySemanticOperator.VALUE.declaration[0])
                + " (" + concept.getUrn() + ")"
                + (comparison == null
                        ? ""
                        : " " + (UnarySemanticOperator.VALUE.declaration[1] + " (" + comparison.getUrn() + ")"));

        Ontology ontology = getOntology(concept.getNamespace());
        String conceptId = ontology.getIdForDefinition(definition);

        // this.hasUnaryOp = true;

        if (conceptId == null) {

            conceptId = ontology.createIdForDefinition(definition);

            String reference = monetary
                    ? UnarySemanticOperator.MONETARY_VALUE.getReferenceName(concept.getReferenceName(),
                            comparison == null ? null : comparison.getReferenceName())
                    : UnarySemanticOperator.VALUE.getReferenceName(concept.getReferenceName(),
                            comparison == null ? null : comparison.getReferenceName());

            Set<SemanticType> newType = monetary
                    ? UnarySemanticOperator.MONETARY_VALUE.apply(concept.getType())
                    : UnarySemanticOperator.VALUE.apply(concept.getType());

            ArrayList<IAxiom> ax = new ArrayList<>();
            ax.add(Axiom.ClassAssertion(conceptId, newType));
            ax.add(Axiom.SubClass(monetary ? NS.CORE_MONETARY_VALUE : NS.CORE_VALUE, conceptId));
            ax.add(Axiom.AnnotationAssertion(conceptId, NS.REFERENCE_NAME_PROPERTY, reference));
            ax.add(Axiom.AnnotationAssertion(conceptId, NS.BASE_DECLARATION, "true"));
            ax.add(Axiom.AnnotationAssertion(conceptId, "rdfs:label", cName));
            if (addDefinition) {
                ax.add(Axiom.AnnotationAssertion(conceptId, NS.CONCEPT_DEFINITION_PROPERTY, definition));
            }
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
     * @param addDefinition
     * @return
     */
    public Concept makeType(Concept classified, boolean addDefinition) {

        String traitID = getCleanId(classified) + "Type";
        String definition = UnarySemanticOperator.TYPE.declaration[0] + " " + classified.getUrn();
        Ontology ontology = getOntology(classified.getNamespace());
        String conceptId = ontology.getIdForDefinition(definition);

        // this.hasUnaryOp = true;

        if (conceptId == null) {

            conceptId = ontology.createIdForDefinition(definition);

            Set<SemanticType> newType = UnarySemanticOperator.TYPE.apply(classified.getType());

            String reference = UnarySemanticOperator.TYPE.getReferenceName(classified.getReferenceName(), null);

            List<IAxiom> axioms = new ArrayList<>();
            axioms.add(Axiom.ClassAssertion(conceptId, newType));
            axioms.add(Axiom.SubClass(NS.CORE_TYPE, conceptId));
            axioms.add(Axiom.AnnotationAssertion(conceptId, NS.BASE_DECLARATION, "true"));
            axioms.add(Axiom.AnnotationAssertion(conceptId, NS.REFERENCE_NAME_PROPERTY, reference));
            axioms.add(Axiom.AnnotationAssertion(conceptId, NS.IS_TYPE_DELEGATE, "true"));
            axioms.add(Axiom.AnnotationAssertion(conceptId, "rdfs:label", traitID));
            if (addDefinition) {
                axioms.add(Axiom.AnnotationAssertion(conceptId, NS.CONCEPT_DEFINITION_PROPERTY, definition));
            }
            ontology.define(axioms);
            Concept ret = ontology.getConcept(conceptId);

            restrictSome(ret, getProperty(NS.DESCRIBES_OBSERVABLE_PROPERTY), classified, ontology);

            /*
             * types inherit the context from their trait
             */
            Concept context = Services.INSTANCE.getReasoner().context(classified);
            if (context != null) {
                restrictSome(ret, getProperty(NS.HAS_CONTEXT_PROPERTY), context, ontology);
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
     * {@link IKnowledge#is(ISemantic)} will only check for direct subsumption. This one defaults to
     * that when the reasoner is not active.
     * 
     * Annotation properties only use asserted methods.
     * 
     * TODO handle exceptions and/or default to is() when the ontology is inconsistent and we're not
     * running in strict mode.
     * 
     * @param c1
     * @param c2
     * @return true
     */
    public boolean is(Semantics c1, Semantics c2) {

        if (c1 instanceof Concept && c2 instanceof Concept) {

            if (reasoner == null) {
                return c1.is(c2);
            }
            return getSubClasses(OWL.INSTANCE.getOWLClass(c2.asConcept()), false)
                    .containsEntity(OWL.INSTANCE.getOWLClass(c1.asConcept()));

        } else if (c1 instanceof Property && c2 instanceof Property) {

            if (reasoner == null || (((Property) c1).isAnnotation() && ((Property) c2).isAnnotation())) {
                return ((Property) c1).is(c2);
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
        return reasoner == null ? true : isSatisfiable(OWL.INSTANCE.getOWLClass(c.asConcept()));
    }

    /**
     * Synonym of getAllParents(), in Concept; defaults to asserted parents if reasoner is off. Will
     * not return owl:Thing (which should never appear anyway) or owl:Nothing, even if the concept
     * is inconsistent.
     * 
     * @param main
     * @return the parent closure of the concept
     */
    public Set<Concept> getParentClosure(Concept main) {
        Set<Concept> ret = new HashSet<>();
        if (reasoner != null) {
            for (OWLClass cls : getSuperClasses(OWL.INSTANCE.getOWLClass(main.asConcept()), false).getFlattened()) {
                if (cls.isBottomEntity() || cls.isTopEntity()) {
                    continue;
                }
                Concept cc = OWL.INSTANCE.getConceptFor(cls);
                if (cc != null) {
                    ret.add(cc);
                }
            }
        } else {
            ret.addAll(Services.INSTANCE.getReasoner().allParents(main));
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
            for (OWLClass cls : getSubClasses(OWL.INSTANCE.getOWLClass(main.asConcept()), false).getFlattened()) {
                if (cls.isBottomEntity() || cls.isTopEntity()) {
                    continue;
                }
                Concept cc = OWL.INSTANCE.getConceptFor(cls);
                if (cc != null) {
                    ret.add(cc);
                }
            }
            return ret;
        }
        return Services.INSTANCE.getReasoner().allChildren(main);
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
            throws InconsistentOntologyException, FreshEntitiesException, ReasonerInterruptedException, TimeOutException {
        return reasoner.getDataPropertyDomains(arg0, arg1);
    }

    public Set<OWLLiteral> getDataPropertyValues(OWLNamedIndividual arg0, OWLDataProperty arg1)
            throws InconsistentOntologyException, FreshEntitiesException, ReasonerInterruptedException, TimeOutException {
        return reasoner.getDataPropertyValues(arg0, arg1);
    }

    public NodeSet<OWLNamedIndividual> getDifferentIndividuals(OWLNamedIndividual arg0)
            throws InconsistentOntologyException, FreshEntitiesException, ReasonerInterruptedException, TimeOutException {
        return reasoner.getDifferentIndividuals(arg0);
    }

    public NodeSet<OWLClass> getDisjointClasses(OWLClassExpression arg0)
            throws ReasonerInterruptedException, TimeOutException, FreshEntitiesException, InconsistentOntologyException {
        return reasoner.getDisjointClasses(arg0);
    }

    public NodeSet<OWLDataProperty> getDisjointDataProperties(OWLDataPropertyExpression arg0)
            throws InconsistentOntologyException, FreshEntitiesException, ReasonerInterruptedException, TimeOutException {
        return reasoner.getDisjointDataProperties(arg0);
    }

    public NodeSet<OWLObjectPropertyExpression> getDisjointObjectProperties(OWLObjectPropertyExpression arg0)
            throws InconsistentOntologyException, FreshEntitiesException, ReasonerInterruptedException, TimeOutException {
        return reasoner.getDisjointObjectProperties(arg0);
    }

    public Node<OWLClass> getEquivalentClasses(OWLClassExpression arg0) throws InconsistentOntologyException,
            ClassExpressionNotInProfileException, FreshEntitiesException, ReasonerInterruptedException, TimeOutException {
        return reasoner.getEquivalentClasses(arg0);
    }

    public Node<OWLDataProperty> getEquivalentDataProperties(OWLDataProperty arg0)
            throws InconsistentOntologyException, FreshEntitiesException, ReasonerInterruptedException, TimeOutException {
        return reasoner.getEquivalentDataProperties(arg0);
    }

    public Node<OWLObjectPropertyExpression> getEquivalentObjectProperties(OWLObjectPropertyExpression arg0)
            throws InconsistentOntologyException, FreshEntitiesException, ReasonerInterruptedException, TimeOutException {
        return reasoner.getEquivalentObjectProperties(arg0);
    }

    public NodeSet<OWLNamedIndividual> getInstances(OWLClassExpression arg0, boolean arg1) throws InconsistentOntologyException,
            ClassExpressionNotInProfileException, FreshEntitiesException, ReasonerInterruptedException, TimeOutException {
        return reasoner.getInstances(arg0, arg1);
    }

    public Node<OWLObjectPropertyExpression> getInverseObjectProperties(OWLObjectPropertyExpression arg0)
            throws InconsistentOntologyException, FreshEntitiesException, ReasonerInterruptedException, TimeOutException {
        return reasoner.getInverseObjectProperties(arg0);
    }

    public NodeSet<OWLClass> getObjectPropertyDomains(OWLObjectPropertyExpression arg0, boolean arg1)
            throws InconsistentOntologyException, FreshEntitiesException, ReasonerInterruptedException, TimeOutException {
        return reasoner.getObjectPropertyDomains(arg0, arg1);
    }

    public NodeSet<OWLClass> getObjectPropertyRanges(OWLObjectPropertyExpression arg0, boolean arg1)
            throws InconsistentOntologyException, FreshEntitiesException, ReasonerInterruptedException, TimeOutException {
        return reasoner.getObjectPropertyRanges(arg0, arg1);
    }

    public NodeSet<OWLNamedIndividual> getObjectPropertyValues(OWLNamedIndividual arg0, OWLObjectPropertyExpression arg1)
            throws InconsistentOntologyException, FreshEntitiesException, ReasonerInterruptedException, TimeOutException {
        return reasoner.getObjectPropertyValues(arg0, arg1);
    }

    public String getReasonerName() {
        return reasoner.getReasonerName();
    }

    public Version getReasonerVersion() {
        return reasoner.getReasonerVersion();
    }

    public Node<OWLNamedIndividual> getSameIndividuals(OWLNamedIndividual arg0)
            throws InconsistentOntologyException, FreshEntitiesException, ReasonerInterruptedException, TimeOutException {
        return reasoner.getSameIndividuals(arg0);
    }

    public NodeSet<OWLClass> getSubClasses(OWLClassExpression arg0, boolean arg1) throws ReasonerInterruptedException,
            TimeOutException, FreshEntitiesException, InconsistentOntologyException, ClassExpressionNotInProfileException {
        return reasoner.getSubClasses(arg0, arg1);
    }

    public NodeSet<OWLDataProperty> getSubDataProperties(OWLDataProperty arg0, boolean arg1)
            throws InconsistentOntologyException, FreshEntitiesException, ReasonerInterruptedException, TimeOutException {
        return reasoner.getSubDataProperties(arg0, arg1);
    }

    public NodeSet<OWLObjectPropertyExpression> getSubObjectProperties(OWLObjectPropertyExpression arg0, boolean arg1)
            throws InconsistentOntologyException, FreshEntitiesException, ReasonerInterruptedException, TimeOutException {
        return reasoner.getSubObjectProperties(arg0, arg1);
    }

    public NodeSet<OWLClass> getSuperClasses(OWLClassExpression arg0, boolean arg1) throws InconsistentOntologyException,
            ClassExpressionNotInProfileException, FreshEntitiesException, ReasonerInterruptedException, TimeOutException {
        return reasoner.getSuperClasses(arg0, arg1);
    }

    public NodeSet<OWLDataProperty> getSuperDataProperties(OWLDataProperty arg0, boolean arg1)
            throws InconsistentOntologyException, FreshEntitiesException, ReasonerInterruptedException, TimeOutException {
        return reasoner.getSuperDataProperties(arg0, arg1);
    }

    public NodeSet<OWLObjectPropertyExpression> getSuperObjectProperties(OWLObjectPropertyExpression arg0, boolean arg1)
            throws InconsistentOntologyException, FreshEntitiesException, ReasonerInterruptedException, TimeOutException {
        return reasoner.getSuperObjectProperties(arg0, arg1);
    }

    public long getTimeOut() {
        return reasoner.getTimeOut();
    }

    public NodeSet<OWLClass> getTypes(OWLNamedIndividual arg0, boolean arg1)
            throws InconsistentOntologyException, FreshEntitiesException, ReasonerInterruptedException, TimeOutException {
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

    public boolean isEntailed(OWLAxiom arg0) throws ReasonerInterruptedException, UnsupportedEntailmentTypeException,
            TimeOutException, AxiomNotInProfileException, FreshEntitiesException, InconsistentOntologyException {
        return reasoner.isEntailed(arg0);
    }

    public boolean isEntailed(Set<? extends OWLAxiom> arg0)
            throws ReasonerInterruptedException, UnsupportedEntailmentTypeException, TimeOutException, AxiomNotInProfileException,
            FreshEntitiesException, InconsistentOntologyException {
        return reasoner.isEntailed(arg0);
    }

    public boolean isEntailmentCheckingSupported(AxiomType<?> arg0) {
        return reasoner.isEntailmentCheckingSupported(arg0);
    }

    public boolean isPrecomputed(InferenceType arg0) {
        return reasoner.isPrecomputed(arg0);
    }

    public boolean isSatisfiable(OWLClassExpression arg0) throws ReasonerInterruptedException, TimeOutException,
            ClassExpressionNotInProfileException, FreshEntitiesException, InconsistentOntologyException {
        return reasoner.isSatisfiable(arg0);
    }

    public void registerWithReasoner(KimNamespace parsed) {
        Ontology ontology = getOntology(parsed.getNamespace());
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
    //
    //
    //
    // /**
    // * Given a collection of namespace-specified knowledge and/or collections
    // * thereof, return the ontology of a namespace that sits on top of all others in
    // * the asserted dependency chain and imports all concepts. If that is not
    // * possible, return the "fallback" ontology which must already import all the
    // * concepts (typically the one where the targets are used in a declaration).
    // * Used to establish where to put derived concepts and restrictions so as to
    // * avoid circular dependencies in the underlying OWL model while minimizing
    // * redundant concepts.
    // *
    // * @param targets
    // * @return
    // */
    // public Ontology getTargetOntology(Ontology fallback, Object... targets) {
    //
    // Set<String> graph = new HashSet<>();
    // if (targets != null) {
    // for (Object o : targets) {
    // if (o instanceof KlabAsset || o instanceof KimStatement) {
    // String nsid = getNamespace(o);
    // if (Services.INSTANCE.getResources().resolveNamespace(nsid, scope) != null) {
    // graph.add(nsid);
    // }
    // } else if (o instanceof Iterable) {
    // for (Object u : (Iterable<?>) o) {
    // if (u instanceof IKnowledge) {
    // String nsid = getNamespace(o);
    // if (Services.INSTANCE.getResources().resolveNamespace(nsid, scope) != null) {
    // graph.add(nsid);
    // }
    // }
    // }
    // }
    // }
    // }
    //
    // String namespace = null;
    // Set<String> os = new HashSet<>(graph);
    // for (String a : graph) {
    // if (namespace == null) {
    // namespace = a;
    // }
    // for (String b : os) {
    // KimNamespace ns = Services.INSTANCE.getResources().resolveNamespace(b, scope);
    // if (!b.equals(a) && ns.getImports().containsKey(a)) {
    // namespace = b;
    // }
    // }
    // }
    //
    // /*
    // * candidate namespace must already import all the others. If not, choose the
    // * fallback ontology which must be guaranteed to contain all the imports already.
    // */
    // boolean transitive = true;
    // KimNamespace ns = Services.INSTANCE.getResources().resolveNamespace(namespace, scope);
    // for (String s : graph) {
    // if (!s.equals(ns.getUrn()) && !ns.getImports().containsKey(s)) {
    // transitive = false;
    // break;
    // }
    // }
    // return (Ontology) (transitive && ns != null ? getOntology(ns.getNamespace()) : fallback);
    // }
    //
    // String getNamespace(Object o) {
    // if (o instanceof KimStatement) {
    // return ((KimStatement)o).getNamespace();
    // } else if (o instanceof Model) {
    // return ((Model)o).getNamespace();
    // }
    // // FIXME throw some shit
    // return null;
    // }

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
}
