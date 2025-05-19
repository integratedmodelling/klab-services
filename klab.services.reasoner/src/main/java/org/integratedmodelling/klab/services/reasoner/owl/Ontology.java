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

import org.integratedmodelling.common.knowledge.ConceptImpl;
import org.integratedmodelling.common.lang.Axiom;
import org.integratedmodelling.klab.api.exceptions.KlabIOException;
import org.integratedmodelling.klab.api.exceptions.KlabInternalErrorException;
import org.integratedmodelling.klab.api.exceptions.KlabValidationException;
import org.integratedmodelling.klab.api.knowledge.Concept;
import org.integratedmodelling.klab.api.knowledge.SemanticType;
import org.integratedmodelling.klab.api.utils.Utils;
import org.integratedmodelling.klab.services.reasoner.internal.CoreOntology;
import org.integratedmodelling.klab.services.reasoner.internal.CoreOntology.NS;
import org.semanticweb.HermiT.model.Individual;
import org.semanticweb.owlapi.io.OWLXMLOntologyFormat;
import org.semanticweb.owlapi.model.*;

import java.io.File;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A proxy for an ontology. Holds a list of concepts and a list of axioms. Can be turned into a list
 * and marshalled to a server for actual knowledge creation. Contains no instances, properties or
 * restrictions directly, just concepts for indexing and axioms for the actual stuff.
 *
 * <p>TODO use the basic ConceptImpl from the API package; record a hash of Long->OWLClass to match
 * with each.
 *
 * @author Ferd
 */
public class Ontology {

  String id;
  private Set<String> imported = new HashSet<>();
  private Map<String, Concept> delegates = new HashMap<>();
  OWLOntology ontology;
  private String prefix;
  private Map<String, Concept> conceptIDs = new HashMap<>();
  Map<String, Individual> individuals = new LinkedHashMap<>();
  OWL owl;

  /*
   * all properties
   */
  Set<String> propertyIDs = new HashSet<>();

  /*
   * property IDs by class - no other way to return the OWL objects quickly. what a pain
   */
  Set<String> opropertyIDs = new HashSet<>();
  Set<String> dpropertyIDs = new HashSet<>();
  Set<String> apropertyIDs = new HashSet<>();
  Map<String, String> definitionIds = Collections.synchronizedMap(new HashMap<>());

  private String resourceUrl;
  private boolean isInternal = false;
  private AtomicInteger idCounter = new AtomicInteger(0);

  List<Axiom> axiomCache = new ArrayList<>();

  Ontology(OWL owl, OWLOntology ontology, String id) {

    this.id = id;
    this.owl = owl;
    this.ontology = ontology;
    this.prefix = ontology.getOntologyID().getOntologyIRI().toString();

    scan();
  }

  public String getPrefix() {
    return this.prefix;
  }

  /*
   * build a catalog of names, as there seems to be no way to quickly assess if an ontology
   * contains a named entity or not. This needs to be kept in sync with any changes, which is a
   * pain. FIXME reintegrate the conditionals when define() works properly.
   */
  private void scan() {

    for (OWLClass c : this.ontology.getClassesInSignature(false)) {
      if (c.getIRI().toString().contains(this.prefix)
          && !this.conceptIDs.containsKey(c.getIRI().getFragment())) {
        this.conceptIDs.put(
            c.getIRI().getFragment(),
            owl.makeConcept(c, c.getIRI().getFragment(), this.getName(), OWL.emptyType));
      }
    }
    for (OWLProperty<?, ?> p : this.ontology.getDataPropertiesInSignature(false)) {
      if (p.getIRI().toString().contains(this.prefix)) {
        this.dpropertyIDs.add(p.getIRI().getFragment());
        this.propertyIDs.add(p.getIRI().getFragment());
      }
    }
    for (OWLProperty<?, ?> p : this.ontology.getObjectPropertiesInSignature(false)) {
      if (p.getIRI().toString().contains(this.prefix)) {
        this.opropertyIDs.add(p.getIRI().getFragment());
        this.propertyIDs.add(p.getIRI().getFragment());
      }
    }
    for (OWLAnnotationProperty p : this.ontology.getAnnotationPropertiesInSignature()) {
      if (p.getIRI().toString().contains(this.prefix)) {
        this.apropertyIDs.add(p.getIRI().getFragment());
        this.propertyIDs.add(p.getIRI().getFragment());
      }
    }
  }

  public void addDelegateConcept(String id, String namespace, Concept concept) {
    this.delegates.put(id, concept);
    owl.getOntology(concept.getNamespace())
        .define(
            Collections.singleton(
                Axiom.AnnotationAssertion(
                    concept.getName(), NS.LOCAL_ALIAS_PROPERTY, namespace + ":" + id)));
  }

  public Collection<Concept> getConcepts() {
    return new ArrayList<>(conceptIDs.values());
  }

  // @Override
  public Collection<Property> getProperties() {
    ArrayList<Property> ret = new ArrayList<>();
    for (OWLProperty<?, ?> p : this.ontology.getDataPropertiesInSignature(false)) {
      ret.add(new Property(p, this.id));
    }
    for (OWLProperty<?, ?> p : this.ontology.getObjectPropertiesInSignature(false)) {
      ret.add(new Property(p, this.id));
    }
    for (OWLAnnotationProperty p : this.ontology.getAnnotationPropertiesInSignature()) {
      ret.add(new Property(p, this.id));
    }

    return ret;
  }

  // @Override
  public Concept getConcept(String ID) {
    Concept ret = this.conceptIDs.get(ID);
    if (ret != null) {
      return ret;
    }
    return delegates.get(ID);
  }

  // @Override
  public Property getProperty(String ID) {
    if (this.opropertyIDs.contains(ID)) {
      return new Property(
          this.ontology
              .getOWLOntologyManager()
              .getOWLDataFactory()
              .getOWLObjectProperty(IRI.create(this.prefix + "#" + ID)),
          this.id);
    }
    if (this.dpropertyIDs.contains(ID)) {
      return new Property(
          this.ontology
              .getOWLOntologyManager()
              .getOWLDataFactory()
              .getOWLDataProperty(IRI.create(this.prefix + "#" + ID)),
          this.id);
    }
    if (this.apropertyIDs.contains(ID)) {
      return new Property(
          this.ontology
              .getOWLOntologyManager()
              .getOWLDataFactory()
              .getOWLAnnotationProperty(IRI.create(this.prefix + "#" + ID)),
          this.id);
    }
    return null;
  }

  // @Override
  public String getURI() {
    return this.ontology.getOWLOntologyManager().getOntologyDocumentIRI(this.ontology).toString();
  }

  // @Override
  public boolean write(File file, boolean writeImported) {

    if (writeImported) {

      Set<Ontology> authorities = new HashSet<>(getDelegateOntologies());

      File path = Utils.Files.getPath(file.toString());
      String myns = this.ontology.getOntologyID().getOntologyIRI().getNamespace();
      for (OWLOntology o : this.ontology.getImportsClosure()) {
        String iri = o.getOntologyID().getOntologyIRI().toString();
        if (iri.startsWith(myns) && !o.equals(this.ontology)) {
          String fr = o.getOntologyID().getOntologyIRI().getFragment();
          Ontology other = owl.getOntology(fr);
          if (other != null) {
            if (!fr.endsWith(".owl")) {
              fr += ".owl";
            }
            File efile =
                new File((path.toString().equals(".") ? "" : (path + File.separator)) + fr);
            other.write(efile, false);
            // authorities.addAll(((Ontology)
            // other.getOntology()).getDelegateOntologies());
          }
        }
      }

      for (Ontology o : authorities) {
        File efile =
            new File(
                (path.toString().equals(".") ? "" : (path + File.separator))
                    + o.getName()
                    + ".owl");
        o.write(efile, false);
      }
    }

    OWLOntologyFormat format =
        this.ontology.getOWLOntologyManager().getOntologyFormat(this.ontology);
    OWLXMLOntologyFormat owlxmlFormat = new OWLXMLOntologyFormat();

    if (format.isPrefixOWLOntologyFormat()) {
      owlxmlFormat.copyPrefixesFrom(format.asPrefixOWLOntologyFormat());
    }
    try {
      this.ontology
          .getOWLOntologyManager()
          .saveOntology(this.ontology, owlxmlFormat, IRI.create(file.toURI()));
    } catch (OWLOntologyStorageException e) {
      throw new KlabIOException(e);
    }

    return true;
  }

  /**
   * Return the ontologies that host all authority concepts we delegate to.
   *
   * @return the delegate ontologies
   */
  public Collection<Ontology> getDelegateOntologies() {
    Set<Ontology> ret = new HashSet<>();
    for (Concept c : delegates.values()) {
      ret.add(owl.getOntology(c.getNamespace()));
    }
    return ret;
  }

  /**
   * Incrementally add an axiom. It is just stored until define() is called.
   *
   * @param axiom
   */
  public void add(Axiom axiom) {
    this.axiomCache.add(axiom);
  }

  /**
   * Incorporate all the axioms introduced with {@link #add(Axiom)}.
   *
   * @return
   */
  public Collection<String> define() {
    Collection<String> ret = define(axiomCache);
    axiomCache.clear();
    return ret;
  }

  public Collection<String> define(Collection<Axiom> axioms) {

    ArrayList<String> errors = new ArrayList<>();

    /*
     * ACHTUNG remember to add IDs to appropriate catalogs as classes and property assertions
     * are encountered. This can be called incrementally, so better not to call scan() every
     * time.
     */
    OWLDataFactory factory = this.ontology.getOWLOntologyManager().getOWLDataFactory();

    for (Axiom axiom : axioms) {

      // System.out.println(" [" + id + "] => " + axiom);

      try {

        if (axiom.is(Axiom.CLASS_ASSERTION)) {

          OWLClass newcl =
              factory.getOWLClass(IRI.create(this.prefix + "#" + axiom.getArgument(0)));
          this.ontology
              .getOWLOntologyManager()
              .addAxiom(this.ontology, factory.getOWLDeclarationAxiom(newcl));
          this.conceptIDs.put(
              axiom.getArgument(0).toString(),
              owl.makeConcept(newcl, axiom.getArgument(0).toString(), id, axiom.getConceptType()));

        } else if (axiom.is(Axiom.SUBCLASS_OF)) {

          OWLClass subclass = findClass(axiom.getArgument(1).toString(), errors);
          OWLClass superclass = findClass(axiom.getArgument(0).toString(), errors);

          if (subclass != null && superclass != null) {
            owl.manager.addAxiom(
                this.ontology, factory.getOWLSubClassOfAxiom(subclass, superclass));
          }

        } else if (axiom.is(Axiom.ANNOTATION_PROPERTY_ASSERTION)) {

          OWLAnnotationProperty p =
              factory.getOWLAnnotationProperty(
                  IRI.create(this.prefix + "#" + axiom.getArgument(0)));
          // this.ontology.getOWLOntologyManager().addAxiom(this.ontology,
          // factory.getOWLDeclarationAxiom(p));
          this.propertyIDs.add(axiom.getArgument(0).toString());
          this.apropertyIDs.add(axiom.getArgument(0).toString());
          OWLMetadata.metadataVocabulary.put(
              p.getIRI().toString(), getName() + ":" + axiom.getArgument(0));

        } else if (axiom.is(Axiom.DATA_PROPERTY_ASSERTION)) {

          OWLDataProperty p =
              factory.getOWLDataProperty(IRI.create(this.prefix + "#" + axiom.getArgument(0)));
          this.ontology
              .getOWLOntologyManager()
              .addAxiom(this.ontology, factory.getOWLDeclarationAxiom(p));
          this.propertyIDs.add(axiom.getArgument(0).toString());
          this.dpropertyIDs.add(axiom.getArgument(0).toString());

        } else if (axiom.is(Axiom.DATA_PROPERTY_DOMAIN)) {

          OWLEntity property = findProperty(axiom.getArgument(0).toString(), true, errors);
          OWLClass classExp = findClass(axiom.getArgument(1).toString(), errors);

          if (property != null && classExp != null) {
            owl.manager.addAxiom(
                this.ontology,
                factory.getOWLDataPropertyDomainAxiom(property.asOWLDataProperty(), classExp));
          }

        } else if (axiom.is(Axiom.DATA_PROPERTY_RANGE)) {

          OWLEntity property = findProperty(axiom.getArgument(0).toString(), true, errors);
          /*
           * TODO XSD stuff
           */

          // _manager.manager.addAxiom(
          // _ontology,
          // factory.getOWLDataPropertyRangeAxiom(property.asOWLDataProperty(),
          // classExp));

        } else if (axiom.is(Axiom.OBJECT_PROPERTY_ASSERTION)) {

          OWLObjectProperty p =
              factory.getOWLObjectProperty(IRI.create(this.prefix + "#" + axiom.getArgument(0)));
          this.ontology
              .getOWLOntologyManager()
              .addAxiom(this.ontology, factory.getOWLDeclarationAxiom(p));
          this.propertyIDs.add(axiom.getArgument(0).toString());
          this.opropertyIDs.add(axiom.getArgument(0).toString());

        } else if (axiom.is(Axiom.OBJECT_PROPERTY_DOMAIN)) {

          OWLEntity property = findProperty(axiom.getArgument(0).toString(), false, errors);
          OWLClass classExp = findClass(axiom.getArgument(1).toString(), errors);

          if (property != null && classExp != null) {
            owl.manager.addAxiom(
                this.ontology,
                factory.getOWLObjectPropertyDomainAxiom(property.asOWLObjectProperty(), classExp));
          }

        } else if (axiom.is(Axiom.OBJECT_PROPERTY_RANGE)) {

          OWLEntity property = findProperty(axiom.getArgument(0).toString(), false, errors);
          OWLClass classExp = findClass(axiom.getArgument(1).toString(), errors);

          if (property != null && classExp != null) {
            owl.manager.addAxiom(
                this.ontology,
                factory.getOWLObjectPropertyRangeAxiom(property.asOWLObjectProperty(), classExp));
          }

        } else if (axiom.is(Axiom.ALL_VALUES_FROM_RESTRICTION)) {

          OWLEntity property = findProperty(axiom.getArgument(1).toString(), false, errors);
          OWLClass target = findClass(axiom.getArgument(0).toString(), errors);
          OWLClass filler = findClass(axiom.getArgument(2).toString(), errors);
          OWLClassExpression restr =
              factory.getOWLObjectAllValuesFrom(property.asOWLObjectProperty(), filler);

          if (property != null && filler != null && target != null && restr != null) {
            owl.manager.addAxiom(this.ontology, factory.getOWLSubClassOfAxiom(target, restr));
          }

        } else if (axiom.is(Axiom.AT_LEAST_N_VALUES_FROM_RESTRICTION)) {

          int n = ((Number) axiom.getArgument(3)).intValue();

          OWLEntity property = findProperty(axiom.getArgument(1).toString(), false, errors);
          OWLClass target = findClass(axiom.getArgument(0).toString(), errors);
          OWLClass filler = findClass(axiom.getArgument(2).toString(), errors);

          if (property != null && filler != null && target != null) {
            OWLClassExpression restr =
                factory.getOWLObjectMinCardinality(n, property.asOWLObjectProperty(), filler);
            if (restr != null) {
              owl.manager.addAxiom(this.ontology, factory.getOWLSubClassOfAxiom(target, restr));
            }
          }

        } else if (axiom.is(Axiom.AT_MOST_N_VALUES_FROM_RESTRICTION)) {

          int n = ((Number) axiom.getArgument(3)).intValue();

          OWLEntity property = findProperty(axiom.getArgument(1).toString(), false, errors);
          OWLClass target = findClass(axiom.getArgument(0).toString(), errors);
          OWLClass filler = findClass(axiom.getArgument(2).toString(), errors);

          if (property != null && filler != null && target != null) {
            OWLClassExpression restr =
                factory.getOWLObjectMaxCardinality(n, property.asOWLObjectProperty(), filler);
            if (restr != null) {
              owl.manager.addAxiom(this.ontology, factory.getOWLSubClassOfAxiom(target, restr));
            }
          }

        } else if (axiom.is(Axiom.EXACTLY_N_VALUES_FROM_RESTRICTION)) {

          int n = ((Number) axiom.getArgument(3)).intValue();

          OWLEntity property = findProperty(axiom.getArgument(1).toString(), false, errors);
          OWLClass target = findClass(axiom.getArgument(0).toString(), errors);
          OWLClass filler = findClass(axiom.getArgument(2).toString(), errors);

          if (property != null && filler != null && target != null) {

            OWLClassExpression restr =
                factory.getOWLObjectExactCardinality(n, property.asOWLObjectProperty(), filler);
            if (restr != null) {
              owl.manager.addAxiom(this.ontology, factory.getOWLSubClassOfAxiom(target, restr));
            }
          }

        } else if (axiom.is(Axiom.SOME_VALUES_FROM_RESTRICTION)) {

          OWLEntity property = findProperty(axiom.getArgument(1).toString(), false, errors);
          OWLClass target = findClass(axiom.getArgument(0).toString(), errors);
          OWLClass filler = findClass(axiom.getArgument(2).toString(), errors);

          if (property != null && filler != null && target != null) {
            OWLClassExpression restr =
                factory.getOWLObjectSomeValuesFrom(property.asOWLObjectProperty(), filler);
            if (restr != null) {
              owl.manager.addAxiom(this.ontology, factory.getOWLSubClassOfAxiom(target, restr));
            }
          }
        } else if (axiom.is(Axiom.DATATYPE_DEFINITION)) {

        } else if (axiom.is(Axiom.DISJOINT_CLASSES)) {

          Set<OWLClassExpression> classExpressions = new HashSet<>();
          for (Object arg : axiom) {
            OWLClass p = factory.getOWLClass(IRI.create(this.prefix + "#" + arg));
            classExpressions.add(p);
          }
          owl.manager.addAxiom(this.ontology, factory.getOWLDisjointClassesAxiom(classExpressions));

        } else if (axiom.is(Axiom.ASYMMETRIC_OBJECT_PROPERTY)) {

        } else if (axiom.is(Axiom.DIFFERENT_INDIVIDUALS)) {

        } else if (axiom.is(Axiom.DISJOINT_OBJECT_PROPERTIES)) {

        } else if (axiom.is(Axiom.DISJOINT_DATA_PROPERTIES)) {

        } else if (axiom.is(Axiom.DISJOINT_UNION)) {

        } else if (axiom.is(Axiom.EQUIVALENT_CLASSES)) {

          Set<OWLClassExpression> classExpressions = new HashSet<>();
          for (Object arg : axiom) {
            OWLClass classExp = findClass(arg.toString(), errors);
            classExpressions.add(classExp);
          }
          owl.manager.addAxiom(
              this.ontology, factory.getOWLEquivalentClassesAxiom(classExpressions));

        } else if (axiom.is(Axiom.EQUIVALENT_DATA_PROPERTIES)) {

        } else if (axiom.is(Axiom.EQUIVALENT_OBJECT_PROPERTIES)) {

        } else if (axiom.is(Axiom.FUNCTIONAL_DATA_PROPERTY)) {

          OWLDataProperty prop =
              factory.getOWLDataProperty(IRI.create(this.prefix + "#" + axiom.getArgument(0)));
          owl.manager.addAxiom(this.ontology, factory.getOWLFunctionalDataPropertyAxiom(prop));

        } else if (axiom.is(Axiom.FUNCTIONAL_OBJECT_PROPERTY)) {

          OWLObjectProperty prop =
              factory.getOWLObjectProperty(IRI.create(this.prefix + "#" + axiom.getArgument(0)));
          owl.manager.addAxiom(this.ontology, factory.getOWLFunctionalObjectPropertyAxiom(prop));

        } else if (axiom.is(Axiom.INVERSE_FUNCTIONAL_OBJECT_PROPERTY)) {

        } else if (axiom.is(Axiom.INVERSE_OBJECT_PROPERTIES)) {

        } else if (axiom.is(Axiom.IRREFLEXIVE_OBJECT_PROPERTY)) {

        } else if (axiom.is(Axiom.NEGATIVE_DATA_PROPERTY_ASSERTION)) {

        } else if (axiom.is(Axiom.NEGATIVE_OBJECT_PROPERTY_ASSERTION)) {

        } else if (axiom.is(Axiom.REFLEXIVE_OBJECT_PROPERTY)) {

        } else if (axiom.is(Axiom.SUB_ANNOTATION_PROPERTY_OF)) {

        } else if (axiom.is(Axiom.SUB_DATA_PROPERTY)) {

          OWLDataProperty subdprop =
              (OWLDataProperty) findProperty(axiom.getArgument(1).toString(), true, errors);
          OWLDataProperty superdprop =
              (OWLDataProperty) findProperty(axiom.getArgument(0).toString(), true, errors);

          if (subdprop != null && superdprop != null) {
            owl.manager.addAxiom(
                this.ontology, factory.getOWLSubDataPropertyOfAxiom(subdprop, superdprop));
          }

        } else if (axiom.is(Axiom.SUB_ANNOTATION_PROPERTY)) {

          OWLAnnotationProperty suboprop =
              (OWLAnnotationProperty) findProperty(axiom.getArgument(1).toString(), false, errors);
          OWLAnnotationProperty superoprop =
              (OWLAnnotationProperty) findProperty(axiom.getArgument(0).toString(), false, errors);

          if (suboprop != null && superoprop != null) {
            owl.manager.addAxiom(
                this.ontology, factory.getOWLSubAnnotationPropertyOfAxiom(suboprop, superoprop));
          }

        } else if (axiom.is(Axiom.SUB_OBJECT_PROPERTY)) {

          OWLObjectProperty suboprop =
              (OWLObjectProperty) findProperty(axiom.getArgument(1).toString(), false, errors);
          OWLObjectProperty superoprop =
              (OWLObjectProperty) findProperty(axiom.getArgument(0).toString(), false, errors);

          if (suboprop != null && superoprop != null) {
            owl.manager.addAxiom(
                this.ontology, factory.getOWLSubObjectPropertyOfAxiom(suboprop, superoprop));
          }

        } else if (axiom.is(Axiom.SUB_PROPERTY_CHAIN_OF)) {

        } else if (axiom.is(Axiom.SYMMETRIC_OBJECT_PROPERTY)) {

        } else if (axiom.is(Axiom.TRANSITIVE_OBJECT_PROPERTY)) {

        } else if (axiom.is(Axiom.SWRL_RULE)) {

        } else if (axiom.is(Axiom.HAS_KEY)) {

        } else if (axiom.is(Axiom.ANNOTATION_ASSERTION)) {

          OWLAnnotationProperty property =
              findAnnotationProperty(axiom.getArgument(1).toString(), errors);
          Object value = axiom.getArgument(2);
          OWLLiteral literal = null;
          OWLEntity target = findKnowledge(axiom.getArgument(0).toString(), errors);

          if (target != null) {

            if (value instanceof String)
              literal = factory.getOWLLiteral(Utils.Strings.pack((String) value));
            else if (value instanceof Integer) literal = factory.getOWLLiteral((Integer) value);
            else if (value instanceof Long) literal = factory.getOWLLiteral((Long) value);
            else if (value instanceof Float) literal = factory.getOWLLiteral((Float) value);
            else if (value instanceof Double) literal = factory.getOWLLiteral((Double) value);
            else if (value instanceof Boolean) literal = factory.getOWLLiteral((Boolean) value);

            /*
             * TODO determine literal from type of value and property
             */
            if (property != null && literal != null) {
              OWLAnnotation annotation = factory.getOWLAnnotation(property, literal);
              owl.manager.addAxiom(
                  this.ontology,
                  factory.getOWLAnnotationAssertionAxiom(target.getIRI(), annotation));
              addMetadata(axiom.getArgument(0).toString(), property.getIRI().toString(), value);
            }
          }

        } else if (axiom.is(Axiom.ANNOTATION_PROPERTY_DOMAIN)) {

        } else if (axiom.is(Axiom.ANNOTATION_PROPERTY_RANGE)) {

        }

      } catch (OWLOntologyChangeException e) {
        errors.add(e.getMessage());
      }
    }

    scan();

    return errors;
  }

  private void addMetadata(String conceptId, String property, Object literal) {
    ConceptImpl concept = (ConceptImpl) this.conceptIDs.get(conceptId);
    property = OWLMetadata.translate(property);
    concept.getMetadata().put(property, literal);
    if (CoreOntology.NS.IS_ABSTRACT.equals(property)) {
      concept.setAbstract(Boolean.parseBoolean(literal.toString()));
    } else if (CoreOntology.NS.CONCEPT_DEFINITION_PROPERTY.equals(property)) {
      concept.setUrn(removeOuterParentheses(literal.toString()));
    } else if (CoreOntology.NS.REFERENCE_NAME_PROPERTY.equals(property)) {
      concept.setReferenceName(literal.toString());
    }
  }

  private String removeOuterParentheses(String string) {
    string = string.trim();
    if (string.startsWith("(") && string.endsWith(")")) {
      string = string.substring(1, string.length() - 1);
    }
    return string;
  }

  /* must exist, can be property or class */
  private OWLEntity findKnowledge(String string, ArrayList<String> errors) {

    if (this.conceptIDs.containsKey(string)) {
      return findClass(string, errors);
    } else if (this.propertyIDs.contains(string)) {
      if (this.apropertyIDs.contains(string)) {
        return findAnnotationProperty(string, errors);
      }
      return findProperty(string, this.dpropertyIDs.contains(string), errors);
    }

    return null;
  }

  private OWLClass findClass(String c, ArrayList<String> errors) {

    OWLClass ret = null;

    if (c.equals("owl:Nothing")) {
      return owl.getOWLClass(owl.getNothing());
    } else if (c.equals("owl:Thing")) {
      return owl.getOWLClass(owl.getRootConcept());
    }

    if (c.contains(":")) {

      Concept cc = owl.getConcept(c);
      if (cc == null) {
        errors.add("concept " + cc + " not found");
        return null;
      }

      /*
       * ensure ontology is imported
       */
      if (!cc.getNamespace().equals(this.id) && !this.imported.contains(cc.getNamespace())) {

        this.imported.add(cc.getNamespace());
        IRI importIRI =
            owl.getOntology(cc.getNamespace()).ontology.getOntologyID().getOntologyIRI();
        OWLImportsDeclaration importDeclaraton =
            this.ontology
                .getOWLOntologyManager()
                .getOWLDataFactory()
                .getOWLImportsDeclaration(importIRI);
        owl.manager.applyChange(new AddImport(this.ontology, importDeclaraton));
      }

      ret = owl.getOWLClass(cc);

    } else {

      Concept cc = conceptIDs.get(c);

      if (cc != null) {
        ret = owl.getOWLClass(cc);
      } else {

        ret =
            this.ontology
                .getOWLOntologyManager()
                .getOWLDataFactory()
                .getOWLClass(IRI.create(this.prefix + "#" + c));

        this.conceptIDs.put(c, owl.makeConcept(ret, c, getName(), OWL.emptyType));
      }
    }

    return ret;
  }

  private OWLEntity findProperty(String c, boolean isData, ArrayList<String> errors) {

    OWLEntity ret = null;

    if (c.contains(":")) {

      Property cc = owl.getProperty(c);
      if (cc == null) {
        errors.add("property " + cc + " not found");
        return null;
      }

      /*
       * ensure ontology is imported
       */
      if (!cc.getNamespace().equals(this.id) && !this.imported.contains(cc.getNamespace())) {

        this.imported.add(cc.getNamespace());
        IRI importIRI = ((Ontology) cc.getOntology(owl)).ontology.getOntologyID().getOntologyIRI();
        OWLImportsDeclaration importDeclaraton =
            this.ontology
                .getOWLOntologyManager()
                .getOWLDataFactory()
                .getOWLImportsDeclaration(importIRI);
        owl.manager.applyChange(new AddImport(this.ontology, importDeclaraton));
      }

      ret = ((Property) cc)._owl;

      if (isData && ret instanceof OWLObjectProperty) {
        throw new KlabValidationException(cc + " is an object property: data expected");
      }
      if (!isData && ret instanceof OWLDataProperty) {
        throw new KlabValidationException(cc + " is a data property: object expected");
      }

    } else {
      ret =
          isData
              ? this.ontology
                  .getOWLOntologyManager()
                  .getOWLDataFactory()
                  .getOWLDataProperty(IRI.create(this.prefix + "#" + c))
              : this.ontology
                  .getOWLOntologyManager()
                  .getOWLDataFactory()
                  .getOWLObjectProperty(IRI.create(this.prefix + "#" + c));

      if (isData) {
        this.dpropertyIDs.add(c);
      } else {
        this.opropertyIDs.add(c);
      }
      this.propertyIDs.add(c);
    }

    return ret;
  }

  private OWLAnnotationProperty findAnnotationProperty(String c, ArrayList<String> errors) {

    OWLAnnotationProperty ret = null;

    if (c.equals("rdfs:label")) {
      return this.ontology.getOWLOntologyManager().getOWLDataFactory().getRDFSLabel();
    } else if (c.equals("rdfs:comment")) {
      return this.ontology.getOWLOntologyManager().getOWLDataFactory().getRDFSComment();
    } else if (c.equals("rdfs:seealso")) {
      return this.ontology.getOWLOntologyManager().getOWLDataFactory().getRDFSSeeAlso();
    } else if (c.equals("rdfs:isdefinedby")) {
      return this.ontology.getOWLOntologyManager().getOWLDataFactory().getRDFSIsDefinedBy();
    }

    if (c.contains(":")) {

      Property cc = owl.getProperty(c);
      if (cc != null) {
        OWLEntity e = ((Property) cc)._owl;
        if (e instanceof OWLAnnotationProperty) return (OWLAnnotationProperty) e;
      } else {
        QualifiedName ct = new QualifiedName(c);
        Ontology ontology = owl.getOntology(ct.getNamespace());
        // FIXME check
        // if (ontology == null) {
        // KKimNamespace ns = Namespaces.INSTANCE.getNamespace(ct.getName());
        // if (ns != null)
        // ontology = OWL.INSTANCE.getOntology(ct.getName());
        // }
        if (ontology != null) {
          ret =
              ((Ontology) ontology)
                  .ontology
                  .getOWLOntologyManager()
                  .getOWLDataFactory()
                  .getOWLAnnotationProperty(
                      IRI.create(((Ontology) ontology).prefix + "#" + ct.getName()));
        }
      }
    } else {
      ret =
          this.ontology
              .getOWLOntologyManager()
              .getOWLDataFactory()
              .getOWLAnnotationProperty(IRI.create(this.prefix + "#" + c));
      this.apropertyIDs.add(c);
      this.propertyIDs.add(c);
    }

    return ret;
  }

  //// @Override
  // public IMetadata getMetadata() {
  // // TODO Auto-generated method stub
  // return null;
  // }

  // @Override
  public String getName() {
    return this.id;
  }

  // @Override
  public int getConceptCount() {
    return this.conceptIDs.size();
  }

  // @Override
  public int getPropertyCount() {
    return this.propertyIDs.size();
  }

  public void setResourceUrl(String string) {
    this.resourceUrl = string;
  }

  /**
   * Return the URL of the resource this was read from, or null if it was created by the API.
   *
   * @return URL of source
   */
  public String getResourceUrl() {
    return this.resourceUrl;
  }

  public Concept createConcept(String newName, Set<SemanticType> type) {

    Concept ret = getConcept(newName);
    if (ret == null) {
      ArrayList<Axiom> ax = new ArrayList<>();
      ax.add(Axiom.ClassAssertion(newName, type));
      define(ax);
      ret = getConcept(newName);
    }
    return ret;
  }

  public Property createProperty(String newName, boolean isData) {

    Property ret = getProperty(newName);
    if (ret == null) {
      ArrayList<Axiom> ax = new ArrayList<>();
      ax.add(
          isData ? Axiom.DataPropertyAssertion(newName) : Axiom.ObjectPropertyAssertion(newName));
      define(ax);
      ret = getProperty(newName);
    }
    return ret;
  }

  public boolean isInternal() {
    return this.isInternal;
  }

  public void setInternal(boolean b) {
    this.isInternal = b;
  }

  public void createReasoner() {

    try {
      this.ontology
          .getOWLOntologyManager()
          .loadOntology(IRI.create(this.ontology.getOntologyID().getOntologyIRI().toString()));
    } catch (OWLOntologyCreationException e) {
      throw new KlabInternalErrorException(e);
    }
  }

  public void addImport(Ontology ontology) {

    if (!this.ontology.getImports().contains(((Ontology) ontology).ontology)) {
      OWLDataFactory factory = this.ontology.getOWLOntologyManager().getOWLDataFactory();
      OWLImportsDeclaration imp =
          factory.getOWLImportsDeclaration(
              IRI.create(
                  ((Ontology) ontology).ontology.getOntologyID().getOntologyIRI().toString()));
      this.ontology.getOWLOntologyManager().applyChange(new AddImport(this.ontology, imp));
      this.imported.add(ontology.getName());
    }
  }

  @Override
  public String toString() {
    return "<O " + id + " (" + ontology.getOntologyID().getOntologyIRI() + ")>";
  }

  public OWLOntology getOWLOntology() {
    return this.ontology;
  }

  /**
   * Get the unique ID for a concept with this definition, if any has been created.
   *
   * @param definition
   * @return the ID or null
   */
  public String getIdForDefinition(String definition) {
    return this.definitionIds.get(definition);
  }

  /**
   * Create a new ID for this definition and store it.
   *
   * @param definition
   * @return the new ID
   */
  public String createIdForDefinition(String definition) {
    String id =
        getName().replaceAll("\\.", "_").toUpperCase()
            + "_"
            + String.format("%09d", idCounter.incrementAndGet());
    this.definitionIds.put(definition, id);
    return id;
  }

  // @Override
  public Set<Ontology> getImports(boolean recursive) {
    return getImports(this, recursive, new HashSet<>());
  }

  private Set<Ontology> getImports(Ontology ontology, boolean recursive, Set<Ontology> ret) {
    for (String i : ontology.imported) {
      Ontology o = owl.getOntology(i);
      if (o != null) {
        boolean isnew = ret.add(o);
        if (recursive && isnew) {
          getImports((Ontology) o, recursive, ret);
        }
      }
    }
    return ret;
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + ((ontology == null) ? 0 : ontology.getOntologyID().hashCode());
    return result;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (obj == null) return false;
    if (getClass() != obj.getClass()) return false;
    Ontology other = (Ontology) obj;
    if (ontology == null) {
      if (other.ontology != null) return false;
    } else if (!ontology.getOntologyID().equals(other.ontology.getOntologyID())) return false;
    return true;
  }

  // // @Override
  // public KConcept getIdentity(String authority, String authorityIdentity) {
  // // KAuthority.Identity identity = Authorities.INSTANCE.getIdentity(authority,
  // // authorityIdentity);
  // return null;
  // }

  // public IIndividual getSingletonIndividual(IConcept concept) {
  // String iName = "the" + concept.getLocalName();
  // if (individuals.containsKey(iName)) {
  // return individuals.get(iName);
  // }
  // IIndividual ret = new Individual();
  // ((Individual) ret).define(concept, iName, this);
  // return ret;
  // }
  //
  // public void linkIndividuals(IIndividual source, IIndividual destination,
  // IProperty link) {
  //
  // OWLObjectPropertyAssertionAxiom axiom = manager.manager.getOWLDataFactory()
  // .getOWLObjectPropertyAssertionAxiom(((Property) link)._owl
  // .asOWLObjectProperty(), ((Individual) source).individual, ((Individual)
  // destination).individual);
  //
  // AddAxiom addAxiom = new AddAxiom(ontology, axiom);
  // manager.manager.applyChange(addAxiom);
  // }
}
