/*******************************************************************************
 *  Copyright (C) 2007, 2015:
 *  
 *    - Ferdinando Villa <ferdinando.villa@bc3research.org>
 *    - integratedmodelling.org
 *    - any other authors listed in @author annotations
 *
 *    All rights reserved. This file is part of the k.LAB software suite,
 *    meant to enable modular, collaborative, integrated 
 *    development of interoperable data and model components. For
 *    details, see http://integratedmodelling.org.
 *    
 *    This program is free software; you can redistribute it and/or
 *    modify it under the terms of the Affero General Public License 
 *    Version 3 or any later version.
 *
 *    This program is distributed in the hope that it will be useful,
 *    but without any warranty; without even the implied warranty of
 *    merchantability or fitness for a particular purpose.  See the
 *    Affero General Public License for more details.
 *  
 *     You should have received a copy of the Affero General Public License
 *     along with this program; if not, write to the Free Software
 *     Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 *     The license is also available at: https://www.gnu.org/licenses/agpl.html
 *******************************************************************************/
package org.integratedmodelling.klab.services.reasoner.owl;

import java.util.Arrays;
import java.util.Iterator;
import java.util.Set;

import org.integratedmodelling.klab.api.knowledge.SemanticType;

/**
 * Just a holder for axiom information. Because axioms are basically just syntax, we provide an implementation
 * here so you don't need to.
 * 
 * @author Ferd
 * 
 */
public class Axiom implements Iterable<Object> {

    /*
     * class axioms
     */
    public static final String EQUIVALENT_CLASSES                 = "EquivalentClasses";
    
    /** The Constant SUBCLASS_OF. */
    public static final String SUBCLASS_OF                        = "SubClassOf";
    
    /** The Constant DISJOINT_CLASSES. */
    public static final String DISJOINT_CLASSES                   = "DisjointClasses";
    
    /** The Constant DISJOINT_UNION. */
    public static final String DISJOINT_UNION                     = "DisjointUnion";

    /** The Constant CLASS_ASSERTION. */
    /*
     * individual axioms
     */
    public static final String CLASS_ASSERTION                    = "ClassAssertion";
    
    /** The Constant SAME_INDIVIDUAL. */
    public static final String SAME_INDIVIDUAL                    = "SameIndividual";
    
    /** The Constant DIFFERENT_INDIVIDUALS. */
    public static final String DIFFERENT_INDIVIDUALS              = "DifferentIndividuals";
    
    /** The Constant OBJECT_PROPERTY_ASSERTION. */
    public static final String OBJECT_PROPERTY_ASSERTION          = "ObjectPropertyAssertion";
    
    /** The Constant NEGATIVE_OBJECT_PROPERTY_ASSERTION. */
    public static final String NEGATIVE_OBJECT_PROPERTY_ASSERTION = "NegativeObjectPropertyAssertion";
    
    /** The Constant DATA_PROPERTY_ASSERTION. */
    public static final String DATA_PROPERTY_ASSERTION            = "DataPropertyAssertion";
    
    /** The Constant NEGATIVE_DATA_PROPERTY_ASSERTION. */
    public static final String NEGATIVE_DATA_PROPERTY_ASSERTION   = "NegativeDataPropertyAssertion";

    /** The Constant EQUIVALENT_OBJECT_PROPERTIES. */
    /*
     * object property axioms
     */
    public static final String EQUIVALENT_OBJECT_PROPERTIES       = "EquivalentObjectProperties";
    
    /** The Constant SUB_OBJECT_PROPERTY. */
    public static final String SUB_OBJECT_PROPERTY                = "SubObjectPropertyOf";
    
    /** The Constant INVERSE_OBJECT_PROPERTIES. */
    public static final String INVERSE_OBJECT_PROPERTIES          = "InverseObjectProperties";
    
    /** The Constant FUNCTIONAL_OBJECT_PROPERTY. */
    public static final String FUNCTIONAL_OBJECT_PROPERTY         = "FunctionalObjectProperty";
    
    /** The Constant INVERSE_FUNCTIONAL_OBJECT_PROPERTY. */
    public static final String INVERSE_FUNCTIONAL_OBJECT_PROPERTY = "InverseFunctionalObjectProperty";
    
    /** The Constant SYMMETRIC_OBJECT_PROPERTY. */
    public static final String SYMMETRIC_OBJECT_PROPERTY          = "SymmetricObjectProperty";
    
    /** The Constant ASYMMETRIC_OBJECT_PROPERTY. */
    public static final String ASYMMETRIC_OBJECT_PROPERTY         = "AsymmetricObjectProperty";
    
    /** The Constant TRANSITIVE_OBJECT_PROPERTY. */
    public static final String TRANSITIVE_OBJECT_PROPERTY         = "TransitiveObjectProperty";
    
    /** The Constant REFLEXIVE_OBJECT_PROPERTY. */
    public static final String REFLEXIVE_OBJECT_PROPERTY          = "ReflexiveObjectProperty";
    
    /** The Constant IRREFLEXIVE_OBJECT_PROPERTY. */
    public static final String IRREFLEXIVE_OBJECT_PROPERTY        = "IrrefexiveObjectProperty";
    
    /** The Constant OBJECT_PROPERTY_DOMAIN. */
    public static final String OBJECT_PROPERTY_DOMAIN             = "ObjectPropertyDomain";
    
    /** The Constant OBJECT_PROPERTY_RANGE. */
    public static final String OBJECT_PROPERTY_RANGE              = "ObjectPropertyRange";
    
    /** The Constant DISJOINT_OBJECT_PROPERTIES. */
    public static final String DISJOINT_OBJECT_PROPERTIES         = "DisjointObjectProperties";
    
    /** The Constant SUB_PROPERTY_CHAIN_OF. */
    public static final String SUB_PROPERTY_CHAIN_OF              = "SubPropertyChainOf";

    /** The Constant EQUIVALENT_DATA_PROPERTIES. */
    /*
     * data property axioms
     */
    public static final String EQUIVALENT_DATA_PROPERTIES         = "EquivalentDataProperties";
    
    /** The Constant SUB_DATA_PROPERTY. */
    public static final String SUB_DATA_PROPERTY                  = "SubDataPropertyOf";
    
    /** The Constant SUB_ANNOTATION_PROPERTY. */
    public static final String SUB_ANNOTATION_PROPERTY            = "SubAnnotationPropertyOf";
    
    /** The Constant FUNCTIONAL_DATA_PROPERTY. */
    public static final String FUNCTIONAL_DATA_PROPERTY           = "FunctionalDataProperty";
    
    /** The Constant DATA_PROPERTY_DOMAIN. */
    public static final String DATA_PROPERTY_DOMAIN               = "DataPropertyDomain";
    
    /** The Constant DATA_PROPERTY_RANGE. */
    public static final String DATA_PROPERTY_RANGE                = "DataPropertyRange";
    
    /** The Constant DISJOINT_DATA_PROPERTIES. */
    public static final String DISJOINT_DATA_PROPERTIES           = "DisjointDataProperties";
    
    /** The Constant HAS_KEY. */
    public static final String HAS_KEY                            = "HasKey";
    
    /** The Constant SWRL_RULE. */
    public static final String SWRL_RULE                          = "Rule";

    /** The Constant NO_VALUES_FROM_RESTRICTION. */
    /*
     * restriction "axioms". These are not atomic in DL and actually are more like
     * ontology "actions", but we represent them as single axioms for ease of use - the
     * ontology implementation is able to translate them into the non-atomic actual OWL
     * axioms.
     */
    public static final String NO_VALUES_FROM_RESTRICTION         = "NoValuesFrom";
    
    /** The Constant ALL_VALUES_FROM_RESTRICTION. */
    public static final String ALL_VALUES_FROM_RESTRICTION        = "AllValuesFrom";
    
    /** The Constant SOME_VALUES_FROM_RESTRICTION. */
    public static final String SOME_VALUES_FROM_RESTRICTION       = "SomeValuesFrom";
    
    /** The Constant EXACTLY_N_VALUES_FROM_RESTRICTION. */
    public static final String EXACTLY_N_VALUES_FROM_RESTRICTION  = "ExactlyNValuesFrom";
    
    /** The Constant AT_LEAST_N_VALUES_FROM_RESTRICTION. */
    public static final String AT_LEAST_N_VALUES_FROM_RESTRICTION = "AtLeastNaluesFrom";
    
    /** The Constant AT_MOST_N_VALUES_FROM_RESTRICTION. */
    public static final String AT_MOST_N_VALUES_FROM_RESTRICTION  = "AtMostNValuesFrom";
    
    /** The Constant HAS_VALUE_RESTRICTION. */
    public static final String HAS_VALUE_RESTRICTION              = "HasValue";
    
    /** The Constant HAS_MAX_CARDINALITY_RESTRICTION. */
    public static final String HAS_MAX_CARDINALITY_RESTRICTION    = "HasMaxCardinality";
    
    /** The Constant HAS_MIN_CARDINALITY_RESTRICTION. */
    public static final String HAS_MIN_CARDINALITY_RESTRICTION    = "HasMinCardinality";
    
    /** The Constant HAS_EXACT_CARDINALITY_RESTRICTION. */
    public static final String HAS_EXACT_CARDINALITY_RESTRICTION  = "HasExactCardinality";

    /** The Constant ANNOTATION_ASSERTION. */
    /*
     * annotation property axioms
     */
    public static final String ANNOTATION_ASSERTION               = "AnnotationAssertion";
    
    /** The Constant SUB_ANNOTATION_PROPERTY_OF. */
    public static final String SUB_ANNOTATION_PROPERTY_OF         = "SubAnnotationPropertyOf";
    
    /** The Constant ANNOTATION_PROPERTY_ASSERTION. */
    public static final String ANNOTATION_PROPERTY_ASSERTION      = "AnnotationPropertyAssertion";
    
    /** The Constant ANNOTATION_PROPERTY_RANGE. */
    public static final String ANNOTATION_PROPERTY_RANGE          = "AnnotationPropertyRangeOf";
    
    /** The Constant ANNOTATION_PROPERTY_DOMAIN. */
    public static final String ANNOTATION_PROPERTY_DOMAIN         = "AnnotationPropertyDomain";
    
    /** The Constant DATATYPE_DEFINITION. */
    public static final String DATATYPE_DEFINITION                = "DatatypeDefinition";
    
    private String   _type;
    private Object[] _args;
    Set<SemanticType> conceptType;
    
    /**
     * Create a class assertion. The option integer can be used to store additional flags for the
     * concept with the ontology, which will be quicker to retrieve than annotation properties, for
     * internal use (e.g. distinguish "inferred" observation concepts from asserted ones).
     * 
     * @param conceptId
     * @param type 
     * @return class assertion
     */
    static public Axiom ClassAssertion(String conceptId, Set<SemanticType> type) {
        Axiom ret = new Axiom(CLASS_ASSERTION, conceptId);
        ret.conceptType = type;
        return ret;
    }

    static public Axiom AnnotationAssertion(String targetConcept, String annotationProperty, Object value) {
        return new Axiom(ANNOTATION_ASSERTION, targetConcept, annotationProperty, value);
    }

    static public Axiom SubClass(String parentClass, String subclass) {
        return new Axiom(SUBCLASS_OF, parentClass, subclass);
    }

    static public Axiom SubObjectProperty(String parentProperty, String subProperty) {
        return new Axiom(SUB_OBJECT_PROPERTY, parentProperty, subProperty);
    }

    static public Axiom SubAnnotationProperty(String parentProperty, String subProperty) {
        return new Axiom(SUB_ANNOTATION_PROPERTY, parentProperty, subProperty);
    }
    
    static public Axiom SubDataProperty(String parentProperty, String subProperty) {
        return new Axiom(SUB_DATA_PROPERTY, parentProperty, subProperty);
    }

    public static Axiom ObjectPropertyAssertion(String string) {
        return new Axiom(OBJECT_PROPERTY_ASSERTION, string);
    }

    public static Axiom DataPropertyAssertion(String string) {
        return new Axiom(DATA_PROPERTY_ASSERTION, string);
    }

    public static Axiom ObjectPropertyRange(String property, String concept) {
        return new Axiom(OBJECT_PROPERTY_RANGE, property, concept);
    }

    public static Axiom DataPropertyRange(String property, String concept) {
        return new Axiom(DATA_PROPERTY_RANGE, property, concept);
    }

    public static Axiom ObjectPropertyDomain(String property, String concept) {
        return new Axiom(OBJECT_PROPERTY_DOMAIN, property, concept);
    }

    public static Axiom DataPropertyDomain(String property, String concept) {
        return new Axiom(DATA_PROPERTY_DOMAIN, property, concept);
    }

    public static Axiom FunctionalDataProperty(String id) {
        return new Axiom(FUNCTIONAL_DATA_PROPERTY, id);
    }

    public static Axiom FunctionalObjectProperty(String id) {
        return new Axiom(FUNCTIONAL_OBJECT_PROPERTY, id);
    }

    public static Axiom AnnotationPropertyAssertion(String id) {
        return new Axiom(ANNOTATION_PROPERTY_ASSERTION, id);
    }

    public static Axiom DisjointClasses(String[] concepts) {
        return new Axiom(DISJOINT_CLASSES, (Object[]) concepts);
    }

    public static Axiom SomeValuesFrom(String restrictedConcept, String restrictedProperty, String restrictionFiller) {
        return new Axiom(SOME_VALUES_FROM_RESTRICTION, restrictedConcept, restrictedProperty, restrictionFiller);
    }

    public static Axiom AllValuesFrom(String restrictedConcept, String restrictedProperty, String restrictionFiller) {
        return new Axiom(ALL_VALUES_FROM_RESTRICTION, restrictedConcept, restrictedProperty, restrictionFiller);
    }

    public static Axiom NoValuesFrom(String restrictedConcept, String restrictedProperty, String restrictionFiller) {
        return new Axiom(NO_VALUES_FROM_RESTRICTION, restrictedConcept, restrictedProperty, restrictionFiller);
    }

    public static Axiom AtLeastNValuesFrom(String restrictedConcept, String restrictedProperty, String restrictionFiller, int n) {
        return new Axiom(AT_LEAST_N_VALUES_FROM_RESTRICTION, restrictedConcept, restrictedProperty, restrictionFiller, n);
    }

    public static Axiom AtMostNValuesFrom(String restrictedConcept, String restrictedProperty, String restrictionFiller, int n) {
        return new Axiom(AT_MOST_N_VALUES_FROM_RESTRICTION, restrictedConcept, restrictedProperty, restrictionFiller, n);
    }

    public static Axiom ExactlyNValuesFrom(String restrictedConcept, String restrictedProperty, String restrictionFiller, int n) {
        return new Axiom(EXACTLY_N_VALUES_FROM_RESTRICTION, restrictedConcept, restrictedProperty, restrictionFiller, n);
    }

    public static Axiom HasValue(String concept, String dataProperty, Object value) {
        return new Axiom(HAS_VALUE_RESTRICTION, concept, dataProperty, value);
    }

    public static Axiom EquivalentClasses(String class1, String class2) {
        return new Axiom(EQUIVALENT_CLASSES, class1, class2);
    }

    public Axiom(String type, Object... args) {
        _type = type;
        _args = args;
    }

    @Override
    public boolean equals(Object arg0) {
        if (arg0 instanceof Axiom) {
            return toString().equals(arg0.toString());
        }
        return false;
    }

    @Override
    public int hashCode() {
        return toString().hashCode();
    }

    @Override
    public String toString() {
        String ret = "<" + _type;
        for (Object o : _args) {
            ret += "," + o.toString();
        }
        return ret + ">";
    }

//    @Override
    public boolean is(String classAssertion) {
        return _type.equals(classAssertion);
    }

//    @Override
    public Object getArgument(int index) {
        return _args[index];
    }

    @Override
    public Iterator<Object> iterator() {
        return Arrays.asList(_args).iterator();
    }

    public int size() {
        return _args == null ? 0 : _args.length;
    }

}
