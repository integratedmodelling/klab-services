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
import org.integratedmodelling.klab.api.data.Metadata;
import org.integratedmodelling.klab.api.exceptions.KIOException;
import org.integratedmodelling.klab.api.exceptions.KInternalErrorException;
import org.integratedmodelling.klab.api.knowledge.Artifact;
import org.integratedmodelling.klab.api.knowledge.Concept;
import org.integratedmodelling.klab.api.knowledge.SemanticType;
import org.integratedmodelling.klab.api.knowledge.Semantics;
import org.integratedmodelling.klab.api.lang.LogicalConnector;
import org.integratedmodelling.klab.api.lang.kim.KimNamespace;
import org.integratedmodelling.klab.api.services.runtime.Channel;
import org.integratedmodelling.klab.api.utils.Utils;
import org.integratedmodelling.klab.api.utils.Utils.CamelCase;
import org.integratedmodelling.klab.configuration.Services;
import org.integratedmodelling.klab.exceptions.KlabException;
import org.integratedmodelling.klab.exceptions.KlabIOException;
import org.integratedmodelling.klab.exceptions.KlabInternalErrorException;
import org.integratedmodelling.klab.exceptions.KlabValidationException;
import org.integratedmodelling.klab.knowledge.ConceptImpl;
import org.integratedmodelling.klab.services.reasoner.internal.CoreOntology;
import org.integratedmodelling.klab.services.reasoner.internal.CoreOntology.NS;
import org.integratedmodelling.klab.services.reasoner.internal.SemanticTranslator;
import org.integratedmodelling.klab.utils.Pair;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.IRI;
import org.semanticweb.owlapi.model.OWLClass;
import org.semanticweb.owlapi.model.OWLClassExpression;
import org.semanticweb.owlapi.model.OWLDataFactory;
import org.semanticweb.owlapi.model.OWLObjectIntersectionOf;
import org.semanticweb.owlapi.model.OWLObjectUnionOf;
import org.semanticweb.owlapi.model.OWLOntology;
import org.semanticweb.owlapi.model.OWLOntologyAlreadyExistsException;
import org.semanticweb.owlapi.model.OWLOntologyCreationException;
import org.semanticweb.owlapi.model.OWLOntologyManager;
import org.semanticweb.owlapi.model.OWLProperty;
import org.semanticweb.owlapi.model.OWLQuantifiedRestriction;
import org.semanticweb.owlapi.util.AutoIRIMapper;

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
	private HashMap<String, KimNamespace> resourceIndex = new HashMap<>();
	private HashMap<String, Ontology> ontologies = new HashMap<>();
	private HashMap<String, String> iri2ns = new HashMap<>();
	private HashMap<String, String> c2ont = new HashMap<>();
	private HashMap<String, OWLClass> systemConcepts = new HashMap<>();
	private HashMap<String, ConceptImpl> xsdMappings = new HashMap<>();
	private HashMap<Long, OWLClass> owlClasses = new HashMap<>();
	private AtomicLong classId = new AtomicLong(1l);

	static EnumSet<SemanticType> emptyType = EnumSet.noneOf(SemanticType.class);

	Ontology nonSemanticConcepts;

	/*
	 * package-visible, never null.
	 */
	OWLOntologyManager manager = null;
//    private File loadPath;

	Concept thing;
	Concept nothing;

	private CoreOntology coreOntology;

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
	 * Given a collection of namespace-specified knowledge and/or collections
	 * thereof, return the ontology of a namespace that sits on top of all others in
	 * the asserted dependency chain and imports all concepts. If that is not
	 * possible, return the "fallback" ontology which must already import all the
	 * concepts (typically the one where the targets are used in a declaration).
	 * Used to establish where to put derived concepts and restrictions so as to
	 * avoid circular dependencies in the underlying OWL model while minimizing
	 * redundant concepts.
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

	/**
	 * Get the KConcept corresponding to the OWL class passed. Throws an unchecked
	 * exception if not found.
	 * 
	 * @param owl
	 * @param complainIfNotFound
	 * @return the concept for the class
	 */
	public Concept getConceptFor(OWLClass owl, boolean complainIfNotFound) {
		Concept ret = null;
		String sch = owl.getIRI().getNamespace();
		if (sch.endsWith("#")) {
			sch = sch.substring(0, sch.length() - 1);
		}
		if (sch.endsWith("/")) {
			sch = sch.substring(0, sch.length() - 1);
		}
		Ontology ontology = getOntology(iri2ns.get(sch));
		if (ontology != null) {
			ret = ontology.getConcept(owl.getIRI().getFragment());
		}

		if (ret == null && complainIfNotFound) {
			throw new KInternalErrorException("internal: OWL entity " + owl + " not corresponding to a known ontology");
		}

		return ret;
	}

	public Concept getConceptFor(IRI iri) {
		Concept ret = null;
		String sch = iri.getNamespace();
		if (sch.endsWith("#")) {
			sch = sch.substring(0, sch.length() - 1);
		}
		Ontology ontology = getOntology(iri2ns.get(sch));
		if (ontology != null) {
			ret = ontology.getConcept(iri.getFragment());
		}

		if (ret == null) {
			throw new KInternalErrorException("internal: OWL IRI " + iri + " not corresponding to a known ontology");
		}

		return ret;
	}

	/**
	 * Get the IProperty corresponding to the OWL class passed. Throws an unchecked
	 * exception if not found.
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
			throw new KInternalErrorException(
					"internal: OWL entity " + owl + " does not correspond to a known ontology");
		}

		return ret;
	}

	public Property getPropertyFor(IRI iri) {
		Property ret = null;
		String sch = iri.getNamespace();
		if (sch.endsWith("#")) {
			sch = sch.substring(0, sch.length() - 1);
		}
		Ontology ontology = getOntology(iri2ns.get(sch));
		if (ontology != null) {
			ret = ontology.getProperty(iri.getFragment());
		}

		if (ret == null) {
			throw new KInternalErrorException("internal: OWL IRI " + iri + " not corresponding to a known ontology");
		}

		return ret;
	}

	public static String getFileName(String s) {

		String ret = s;

		int sl = ret.lastIndexOf(File.separator);
		if (sl < 0) {
			sl = ret.lastIndexOf('/');
		}
		if (sl > 0) {
			ret = ret.substring(sl + 1);
		}

		return ret;
	}

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
//        this.loadPath = loadPath;
		coreOntology = new CoreOntology(Configuration.INSTANCE.getDataPath("knowledge"));
//        coreOntology.load(monitor);
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

//        if (this.loadPath == null) {
//            throw new KIOException("owl resources cannot be found: knowledge load directory does not exist");
//        }

//        load();

		/*
		 * all namespaces so far are internal, and just these.
		 */
		for (KimNamespace ns : this.namespaces.values()) {
			// ((Namespace) ns).setInternal(true);
			// ((Ontology) (ns.getOntology())).setInternal(true);
		}

		// createReasoner(monitor);

		/*
		 * create an independent ontology for the non-semantic types we encounter.
		 */
		this.nonSemanticConcepts = requireOntology("nonsemantic", INTERNAL_ONTOLOGY_PREFIX);

	}

	public CoreOntology getCoreOntology() {
		return this.coreOntology;
	}

	// public void createReasoner(IMonitor monitor) {
	//
	// /*
	// * Create the reasoner.
	// */
	// Reasoner.INSTANCE.setReasoner(new KlabReasoner(this, monitor));
	//
	// /*
	// * all namespaces so far are internal, and just these.
	// */
	// for (INamespace ns : this.namespaces.values()) {
	// Reasoner.INSTANCE.addOntology((Ontology) ns.getOntology());
	// }
	// }

//    private void initialize(Channel monitor) {
//
//    }

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
	 * the three knowledge manager methods we implement, so we can serve as delegate
	 * to a KM for these.
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
		}
		return this.thing;
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
	 * Load OWL files from given directory and in its subdirectories, using a prefix
	 * mapper to resolve URLs internally and deriving ontology names from the
	 * relative paths. This uses the resolver passed at initialization only to
	 * create the namespace. It's only meant for core knowledge not seen by users.
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
	 * @param forcePath disregard directory structure and use passed path as prefix
	 *                  for ontology
	 * @param monitor
	 * @throws KlabException
	 */
	private void loadInternal(File f, String path, boolean forcePath, Channel monitor) {

		String pth = path == null ? ""
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
	 * Get the restricted classes only if the target concept of the restriction is
	 * the one passed. This simply returns one class - TODO improve API.
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
					if (getPropertyFor((OWLProperty<?, ?>) ((OWLQuantifiedRestriction<?, ?, ?>) s).getProperty())
							.is(restricted)
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
					if (getPropertyFor((OWLProperty<?, ?>) ((OWLQuantifiedRestriction<?, ?, ?>) s).getProperty())
							.is(restricted)
							&& ((OWLQuantifiedRestriction<?, ?, ?>) s).getFiller() instanceof OWLClassExpression) {
						ret.addAll(unwrap((OWLClassExpression) ((OWLQuantifiedRestriction<?, ?, ?>) s).getFiller()));
					}
				}
			}
		}
		return ret;
	}

	/**
	 * Return the concept or concepts (if a union) restricted by the passed object
	 * property in the restriction closest to the passed concept in its asserted
	 * parent hierarchy.
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
		getTargetOntology(ontology, target, property, filler).define(
				Collections.singleton(Axiom.SomeValuesFrom(target.toString(), property.toString(), filler.toString())));
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
		OWLClassExpression union = how.equals(LogicalConnector.UNION) ? factory.getOWLObjectUnionOf(classes)
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
		OWLClassExpression union = how.equals(LogicalConnector.UNION) ? factory.getOWLObjectUnionOf(classes)
				: factory.getOWLObjectIntersectionOf(classes);
		OWLClassExpression restriction = factory.getOWLObjectSomeValuesFrom(property._owl.asOWLObjectProperty(), union);
		manager.addAxiom(((Ontology) property.getOntology()).ontology,
				factory.getOWLSubClassOfAxiom(getOWLClass(target), restriction));
	}

	public void restrictAll(Concept target, Property property, Concept filler, Ontology ontology) {
		getTargetOntology(ontology, target, property, filler).define(
				Collections.singleton(Axiom.AllValuesFrom(target.toString(), property.toString(), filler.toString())));
	}

	public void restrictAtLeast(Concept target, Property property, LogicalConnector how, Collection<Concept> fillers,
			int min, Ontology ontology) {

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
		OWLClassExpression union = how.equals(LogicalConnector.UNION) ? factory.getOWLObjectUnionOf(classes)
				: factory.getOWLObjectIntersectionOf(classes);
		OWLClassExpression restriction = factory.getOWLObjectMinCardinality(min, property._owl.asOWLObjectProperty(),
				union);
		manager.addAxiom((getTargetOntology(ontology, target, property, fillers)).ontology,
				factory.getOWLSubClassOfAxiom(getOWLClass(target), restriction));
	}

	public void restrictAtLeast(Concept target, Property property, Concept filler, int min, Ontology ontology) {
		getTargetOntology(ontology, target, property, filler).define(Collections
				.singleton(Axiom.AtLeastNValuesFrom(target.toString(), property.toString(), filler.toString(), min)));
	}

	public void restrictAtMost(Concept target, Property property, LogicalConnector how, Collection<Concept> fillers,
			int max, Ontology ontology) {

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
		OWLClassExpression union = how.equals(LogicalConnector.UNION) ? factory.getOWLObjectUnionOf(classes)
				: factory.getOWLObjectIntersectionOf(classes);
		OWLClassExpression restriction = factory.getOWLObjectMaxCardinality(max,
				((Property) property)._owl.asOWLObjectProperty(), union);
		manager.addAxiom((getTargetOntology(ontology, target, property, fillers)).ontology,
				factory.getOWLSubClassOfAxiom(getOWLClass(target), restriction));
	}

	public void restrictAtMost(Concept target, Property property, Concept filler, int max, Ontology ontology) {
		getTargetOntology(ontology, target, property, filler).define(Collections
				.singleton(Axiom.AtMostNValuesFrom(target.toString(), property.toString(), filler.toString(), max)));
	}

	public void restrictExactly(Concept target, Property property, LogicalConnector how, Collection<Concept> fillers,
			int howmany, Ontology ontology) {

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
		OWLClassExpression union = how.equals(LogicalConnector.UNION) ? factory.getOWLObjectUnionOf(classes)
				: factory.getOWLObjectIntersectionOf(classes);
		OWLClassExpression restriction = factory.getOWLObjectExactCardinality(howmany,
				property._owl.asOWLObjectProperty(), union);
		manager.addAxiom((getTargetOntology(ontology, target, property, fillers)).ontology,
				factory.getOWLSubClassOfAxiom(getOWLClass(target), restriction));
	}

	public void restrictExactly(Concept target, Property property, Concept filler, int howMany, Ontology ontology) {
		getTargetOntology(ontology, target, property, filler).define(Collections.singleton(
				Axiom.ExactlyNValuesFrom(target.toString(), property.toString(), filler.toString(), howMany)));
	}

	/**
	 * Return whether the restriction on type involving concept is optional. If
	 * there is no such restriction, return false.
	 * 
	 * @param type
	 * @param concept
	 * @return true if restriction exists and is optional
	 */
	public boolean isRestrictionOptional(Concept type, Concept concept) {
		return new ConceptRestrictionVisitor(type, concept).isOptional();
	}

	/**
	 * Return whether the restriction on type involving concept is a negation. If
	 * there is no such restriction, return false.
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

		File out = new File(/*
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
		switch (type) {
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
				throw new KlabInternalErrorException(
						"non-semantic peer concept for " + name + " was declared previously with a different type");
			}
			return ret;
		}

		nonSemanticConcepts.define(Collections.singletonList(Axiom.ClassAssertion(conceptId, identity)));

		return nonSemanticConcepts.getConcept(conceptId);
	}

	/**
	 * True if the passed object has true semantics, i.e. is not a non-semantic
	 * object.
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

	public void restrict(Concept target, Property property, LogicalConnector how, Collection<Concept> fillers,
			Ontology ontology) throws KlabValidationException {

		/*
		 * divide up in bins according to base trait; take property from annotation;
		 * restrict each group.
		 */
		Map<Concept, List<Concept>> pairs = new HashMap<>();
		for (Concept t : fillers) {
			Concept base = Services.INSTANCE.getReasoner().baseParentTrait(t);
			if (!pairs.containsKey(base)) {
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
					throw new KlabValidationException("cannot find property to restrict for trait " + base);
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
		restrictSome(type, getProperty(CoreOntology.NS.APPLIES_TO_PROPERTY), LogicalConnector.UNION, applicables,
				ontology);
	}

	public void defineRelationship(Concept relationship, Concept source, Concept target, Ontology ontology) {
		Property hasSource = getProperty(CoreOntology.NS.IMPLIES_SOURCE_PROPERTY);
		Property hasTarget = getProperty(CoreOntology.NS.IMPLIES_DESTINATION_PROPERTY);
		restrictSome(relationship, hasSource, LogicalConnector.UNION, Collections.singleton(source), ontology);
		restrictSome(relationship, hasTarget, LogicalConnector.UNION, Collections.singleton(target), ontology);
	}

	/**
	 * Analyze an observable concept and return the main observable with all the
	 * original identities and realms but no attributes; separately, return the list
	 * of the attributes that were removed.
	 * 
	 * @param observable
	 * @return attribute profile
	 * @throws KlabValidationException
	 */
	public Pair<Concept, Collection<Concept>> separateAttributes(Concept observable) throws KlabValidationException {

		Concept obs = Services.INSTANCE.getReasoner().coreObservable(observable);
		ArrayList<Concept> tret = new ArrayList<>();
		ArrayList<Concept> keep = new ArrayList<>();

		for (Concept zt : Services.INSTANCE.getReasoner().traits(observable)) {
			if (zt.is(OWL.INSTANCE.getConcept(CoreOntology.NS.CORE_IDENTITY))
					|| zt.is(getConcept(CoreOntology.NS.CORE_REALM))) {
				keep.add(zt);
			} else {
				tret.add(zt);
			}
		}

		Concept root = null; // Observables.declareObservable((IConcept) (obs == null ? observable
		// : obs), keep, Observables.getContextType(observable), Observables
		// .getInherentType(observable));

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
			aontology.add(Axiom.AnnotationAssertion(conceptId, "rdfs:label",
					"Not" + SemanticTranslator.getCleanId(attribute)));
			aontology.add(
					Axiom.AnnotationAssertion(conceptId, NS.CONCEPT_DEFINITION_PROPERTY, "not  " + attribute.getUrn()));

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
	 * Return all atomic components of a concept, recursing any logical combination
	 * irrespective of the operator.
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

}
