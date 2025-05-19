package org.integratedmodelling.klab.services.reasoner.internal;

import com.google.common.collect.Sets;
import org.integratedmodelling.klab.utilities.Utils;
import org.integratedmodelling.klab.api.collections.Pair;
import org.integratedmodelling.klab.api.knowledge.Concept;
import org.integratedmodelling.klab.api.knowledge.SemanticType;
import org.integratedmodelling.klab.api.lang.kim.KimOntology;
import org.integratedmodelling.klab.api.scope.Scope;
import org.integratedmodelling.klab.api.services.runtime.Channel;
import org.integratedmodelling.klab.configuration.ServiceConfiguration;
import org.integratedmodelling.klab.services.reasoner.owl.OWL;

import java.io.File;
import java.util.*;

/**
 * The core workspace only contains OWL ontologies and is currently read from the classpath.
 *
 * <p>TODO switch to loading the core ontology from the worldview; this requires ALL fundamental
 * types to be specified, including those created by unary operators (currently impossible) and the
 * annotation properties moved to the klab: namespace, which could be read from OWL or even created
 * here to keep one source of truth (as we need to reference the properties from Java). The ODO
 * properties remain a bit unclear: they may be moved to klab: unless the core semantics depends on
 * them for validation.
 *
 * <p>TODO this should become a map of fundamental type -> Concept and binary operator -> property
 *
 * @author ferdinando.villa
 */
public class CoreOntology {

  private File root;
  private Map<SemanticType, Concept> worldviewCoreConcepts =
      Collections.synchronizedMap(new HashMap<>());
  private OWL owl;
  private static Map<SemanticType, String> coreConceptIds =
      Collections.synchronizedMap(new HashMap<>());

  // TODO this should be read from the core namespace; having them all defined through the worldview
  // must be
  //  validated, including the results of unary operators
  public static final String CORE_ONTOLOGY_NAME = "odo";

  static {
    coreConceptIds.put(SemanticType.PROCESS, NS.CORE_PROCESS);
    coreConceptIds.put(SemanticType.SUBJECT, NS.CORE_SUBJECT);
    coreConceptIds.put(SemanticType.EVENT, NS.CORE_EVENT);
    coreConceptIds.put(SemanticType.FUNCTIONAL, NS.CORE_FUNCTIONAL_RELATIONSHIP);
    coreConceptIds.put(SemanticType.STRUCTURAL, NS.CORE_STRUCTURAL_RELATIONSHIP);
    coreConceptIds.put(SemanticType.RELATIONSHIP, NS.CORE_RELATIONSHIP);
    coreConceptIds.put(SemanticType.EXTENSIVE, NS.CORE_EXTENSIVE_PHYSICAL_PROPERTY);
    coreConceptIds.put(SemanticType.INTENSIVE, NS.CORE_INTENSIVE_PHYSICAL_PROPERTY);
    coreConceptIds.put(SemanticType.IDENTITY, NS.CORE_IDENTITY);
    coreConceptIds.put(SemanticType.ATTRIBUTE, NS.CORE_ATTRIBUTE);
    coreConceptIds.put(SemanticType.REALM, NS.CORE_REALM);
    coreConceptIds.put(SemanticType.ORDERING, NS.CORE_ORDERING);
    coreConceptIds.put(SemanticType.ROLE, NS.CORE_ROLE);
    coreConceptIds.put(SemanticType.CONFIGURATION, NS.CORE_CONFIGURATION);
    coreConceptIds.put(SemanticType.CLASS, NS.CORE_TYPE);
    coreConceptIds.put(SemanticType.QUANTITY, NS.CORE_QUANTITY);
    coreConceptIds.put(SemanticType.DOMAIN, NS.CORE_DOMAIN);
    coreConceptIds.put(SemanticType.ENERGY, NS.CORE_ENERGY);
    coreConceptIds.put(SemanticType.ENTROPY, NS.CORE_ENTROPY);
    coreConceptIds.put(SemanticType.LENGTH, NS.CORE_LENGTH);
    coreConceptIds.put(SemanticType.MASS, NS.CORE_MASS);
    coreConceptIds.put(SemanticType.VOLUME, NS.CORE_VOLUME);
    coreConceptIds.put(SemanticType.WEIGHT, NS.CORE_WEIGHT);
    coreConceptIds.put(SemanticType.MONEY, NS.CORE_MONETARY_VALUE);
    coreConceptIds.put(SemanticType.DURATION, NS.CORE_DURATION);
    coreConceptIds.put(SemanticType.AREA, NS.CORE_AREA);
    coreConceptIds.put(SemanticType.ACCELERATION, NS.CORE_ACCELERATION);
    coreConceptIds.put(SemanticType.PRIORITY, NS.CORE_PRIORITY);
    coreConceptIds.put(SemanticType.ELECTRIC_POTENTIAL, NS.CORE_ELECTRIC_POTENTIAL);
    coreConceptIds.put(SemanticType.CHARGE, NS.CORE_CHARGE);
    coreConceptIds.put(SemanticType.RESISTANCE, NS.CORE_RESISTANCE);
    coreConceptIds.put(SemanticType.RESISTIVITY, NS.CORE_RESISTIVITY);
    coreConceptIds.put(SemanticType.PRESSURE, NS.CORE_PRESSURE);
    coreConceptIds.put(SemanticType.ANGLE, NS.CORE_ANGLE);
    coreConceptIds.put(SemanticType.VELOCITY, NS.CORE_SPEED);
    coreConceptIds.put(SemanticType.TEMPERATURE, NS.CORE_TEMPERATURE);
    coreConceptIds.put(SemanticType.VISCOSITY, NS.CORE_VISCOSITY);
    coreConceptIds.put(SemanticType.AGENT, NS.CORE_AGENT);
    coreConceptIds.put(SemanticType.UNCERTAINTY, NS.CORE_UNCERTAINTY);
    coreConceptIds.put(SemanticType.PROBABILITY, NS.CORE_PROBABILITY);
    coreConceptIds.put(SemanticType.PROPORTION, NS.CORE_PROPORTION);
    coreConceptIds.put(SemanticType.NUMEROSITY, NS.CORE_COUNT);
    coreConceptIds.put(SemanticType.DISTANCE, NS.CORE_DISTANCE);
    coreConceptIds.put(SemanticType.RATIO, NS.CORE_RATIO);
    coreConceptIds.put(SemanticType.VALUE, NS.CORE_VALUE);
    coreConceptIds.put(SemanticType.CHANGE, NS.CORE_CHANGE);
    coreConceptIds.put(SemanticType.OCCURRENCE, NS.CORE_OCCURRENCE);
    coreConceptIds.put(SemanticType.PRESENCE, NS.CORE_PRESENCE);
    coreConceptIds.put(SemanticType.EXTENT, NS.CORE_EXTENT);
  }

  public static boolean isCore(Concept t) {
    return CORE_ONTOLOGY_NAME.equals(t.getNamespace());
  }

  public static String getBaseTraitProperty(Concept base) {
    if (base.is(SemanticType.ATTRIBUTE)) {
      return NS.HAS_ATTRIBUTE_PROPERTY;
    } else if (base.is(SemanticType.REALM)) {
      return NS.HAS_REALM_PROPERTY;
    } else if (base.is(SemanticType.IDENTITY)) {
      return NS.HAS_IDENTITY_PROPERTY;
    } else if (base.is(SemanticType.ROLE)) {
      return NS.HAS_ROLE_PROPERTY;
    }
    return NS.HAS_TRAIT_PROPERTY;
  }

  /**
   * Check that all fundamental concepts are mapped to the root ontology. For now just give a
   * warning and do not check that they're actually available in the core ontologies mentioned,
   * which we must do eventually.
   *
   * <p>TODO implement the logic in the class javadocs
   *
   * @param rootDomain
   * @param scope
   */
  public void validateRootDomain(KimOntology rootDomain, Scope scope) {
    Map<SemanticType, String> coreConcepts = new HashMap<>();
    for (var concept : rootDomain.getStatements()) {
      if (concept.getUpperConceptDefined() != null) {
        var implemented = Sets.intersection(concept.getType(), SemanticType.DECLARABLE_TYPES);
        if (implemented.size() > 1) {
          scope.warn(
              "Inconsistent worldview: the fundamental type for core concept "
                  + concept.getNamespace()
                  + ":"
                  + concept.getUrn()
                  + " is ambiguous: "
                  + implemented);
        } else if (implemented.isEmpty()) {
          scope.warn(
              "Inconsistent worldview: can't establish the fundamental type for core concept "
                  + concept.getNamespace()
                  + ":"
                  + concept.getUrn()
                  + " ("
                  + concept.getUpperConceptDefined()
                  + ")");
        } else {
          var type = implemented.iterator().next();
          if (coreConcepts.containsKey(type)) {
            // should be an error
            scope.warn(
                "Inconsistent worldview: fundamental type "
                    + type
                    + ", assigned to core concept "
                    + coreConcepts.get(type)
                    + ", is being reassigned to "
                    + concept.getNamespace()
                    + ":"
                    + concept.getUrn());
          } else {
            coreConcepts.put(type, concept.getNamespace() + ":" + concept.getUrn());
          }
        }
      }
    }

    for (SemanticType type : SemanticType.DECLARABLE_TYPES) {
      if (!coreConcepts.containsKey(type)) {
        scope.warn(
            "Inconsistent worldview: fundamental type "
                + type
                + " is not assigned to a worldview concept");
      }
    }
  }

  public static interface NS {

    // core properties. TODO: these could be created in the root worldview namespace
    public static final String IS_ABSTRACT = "odo:isAbstract";
    public static final String BASE_DECLARATION = "klab:baseDeclaration";
    public static final String ORDER_PROPERTY = "klab:orderingRank";
    public static final String HAS_ATTRIBUTE_PROPERTY = "odo:hasAttribute";
    public static final String HAS_REALM_PROPERTY = "odo:hasRealm";
    public static final String HAS_IDENTITY_PROPERTY = "odo:hasIdentity";
    public static final String HAS_TRAIT_PROPERTY = "odo:hasTrait";
    public static final String HAS_COMPRESENT_PROPERTY = "odo:hasCompresent";
    public static final String HAS_CAUSANT_PROPERTY = "odo:hasCausant";
    public static final String HAS_CAUSED_PROPERTY = "odo:hasCaused";
    public static final String HAS_PURPOSE_PROPERTY = "odo:hasPurpose";
    public static final String OCCURS_DURING_PROPERTY = "odo:observedDuring";
    public static final String IS_ADJACENT_TO_PROPERTY = "odo:isAdjacentTo";
    public static final String HAS_SUBJECTIVE_TRAIT_PROPERTY = "odo:hasSubjectiveTrait";
    public static final String IS_SUBJECTIVE = "odo:isSubjectiveTrait";
    public static final String IS_INHERENT_TO_PROPERTY = "odo:isInherentTo";
    // TODO decide which ones go in klab and which in ODO
    public static final String DESCRIBES_OBSERVABLE_PROPERTY = "klab:describesObservable";
    public static final String IS_COMPARED_TO_PROPERTY = "klab:isComparedTo";
    public static final String HAS_ROLE_PROPERTY = "odo:hasRole";
    public static final String DENIABILITY_PROPERTY = "odo:isDeniable";
    public static final String APPLIES_TO_PROPERTY = "odo:appliesTo";
    public static final String IMPLIES_SOURCE_PROPERTY = "odo:impliesSource";
    public static final String IMPLIES_DESTINATION_PROPERTY = "odo:impliesDestination";
    public static final String REQUIRES_IDENTITY_PROPERTY = "odo:requiresIdentity";
    public static final String IS_TYPE_DELEGATE = "odo:isTypeDelegate";
    public static final String IS_NEGATION_OF = "odo:isNegationOf";

    // annotation property that specifies the base SI unit for a physical property
    public static final String SI_UNIT_PROPERTY = "odo:unit";

    /*
     * Annotations affecting the ranking system. Used as keys in maps, so they don't depend on
     * the ontology being in the system.
     */
    public static final String LEXICAL_SCOPE = "im:lexical-scope";
    public static final String TRAIT_CONCORDANCE = "im:trait-concordance";
    public static final String SEMANTIC_DISTANCE = "im:semantic-concordance";

    public static final String INHERENCY = "im:inherency";
    public static final String EVIDENCE = "im:evidence";
    public static final String NETWORK_REMOTENESS = "im:network-remoteness";
    public static final String SUBJECTIVE_CONCORDANCE = "im:subjective-concordance";

    // Scale criteria are an aggregation of time + space (and potentially others)
    public static final String SCALE_COVERAGE = "im:scale-coverage";
    public static final String SCALE_SPECIFICITY = "im:scale-specificity";
    public static final String SCALE_COHERENCY = "im:scale-coherency";

    /*
     * using space and time explicitly should be alternative to using scale criteria. All are
     * computed anyway and can be used together if wished.
     */
    public static final String SPACE_COVERAGE = "im:space-coverage";
    public static final String SPACE_SPECIFICITY = "im:space-specificity";
    public static final String SPACE_COHERENCY = "im:space-coherency";
    public static final String TIME_COVERAGE = "im:time-coverage";
    public static final String TIME_SPECIFICITY = "im:time-specificity";
    public static final String TIME_COHERENCY = "im:time-coherency";

    // only annotation used for subjective ranking in the default behavior
    public static final String RELIABILITY = "im:reliability";

    /*
     * annotation properties supporting k.LAB functions
     * TODO these (the whole klab: ontology) should be created, not loaded
     */
    public static final String CORE_OBSERVABLE_PROPERTY = "klab:coreObservable";
    public static final String CONCEPT_DEFINITION_PROPERTY = "klab:conceptDefinition";
    public static final String LOCAL_ALIAS_PROPERTY = "klab:localAlias";
    public static final String DISPLAY_LABEL_PROPERTY = "klab:displayLabel";
    public static final String REFERENCE_NAME_PROPERTY = "klab:referenceName";
    public static final String AUTHORITY_ID_PROPERTY = "klab:authorityId";
    public static final String UNTRANSFORMED_CONCEPT_PROPERTY = "klab:untransformedConceptId";
    public static final String ORIGINAL_TRAIT = "klab:originalTrait";

    /**
     * Annotation contains the ID of the property (in same ontology) that will be used to create
     * restrictions to adopt the trait carrying the annotation.
     */
    public static final String TRAIT_RESTRICTING_PROPERTY = "klab:restrictingProperty";

    /*
     * the core properties we use internally to establish observation semantics
     */
    public static final String AFFECTS_PROPERTY = "odo:affects";
    public static final String CREATES_PROPERTY = "odo:creates";
    public static final String CHANGES_PROPERTY = "odo:changes";
    public static final String CHANGED_PROPERTY = "odo:changed";

    /**
     * Core observables. TODO the root domain of the worldview should define ALL the core types or
     * cause an error, so that we can just use the root ontology in the worldview and not have to
     * load the foundational ontology.
     *
     * <p>TODO this should become a map of fundamental type -> Concept and binary operator ->
     * property
     */
    public static final String CORE_DOMAIN = "odo:Domain";

    public static final String CORE_PROCESS = "odo:Process";
    public static final String CORE_EVENT = "odo:Event";
    public static final String CORE_IDENTITY = "odo:Identity";
    public static final String CORE_QUANTITY = "odo:ContinuousNumericallyQuantifiableQuality";
    public static final String CORE_SUBJECT = "odo:Subject";
    public static final String CORE_EXTENSIVE_PHYSICAL_PROPERTY = "odo:ExtensivePhysicalProperty";
    public static final String CORE_INTENSIVE_PHYSICAL_PROPERTY = "odo:IntensivePhysicalProperty";
    public static final String CORE_ENERGY = "odo:Energy";
    public static final String CORE_ENTROPY = "odo:Entropy";
    public static final String CORE_LENGTH = "odo:Length";
    public static final String CORE_MASS = "odo:Mass";
    public static final String CORE_PROBABILITY = "odo:Probability";
    public static final String CORE_MAGNITUDE = "odo:Magnitude";
    public static final String CORE_LEVEL = "odo:Level";
    public static final String CORE_VOLUME = "odo:Volume";
    public static final String CORE_WEIGHT = "odo:Weight";
    public static final String CORE_DURATION = "odo:Duration";
    public static final String CORE_MONETARY_VALUE = "odo:MonetaryValue";
    @Deprecated // change rate of velocity
    public static final String CORE_ACCELERATION = "odo:Acceleration";
    public static final String CORE_AREA = "odo:Area";
    public static final String CORE_ELECTRIC_POTENTIAL = "odo:ElectricPotential";
    public static final String CORE_CHARGE = "odo:Charge";
    public static final String CORE_RESISTANCE = "odo:Resistance";
    public static final String CORE_RESISTIVITY = "odo:Resistivity";
    public static final String CORE_PRESSURE = "odo:Pressure";
    public static final String CORE_ANGLE = "odo:Angle";
    public static final String CORE_CHANGE = "odo:Change";
    public static final String CORE_CHANGE_RATE = "odo:ChangeRate";
    public static final String CORE_SPEED = "odo:Speed";
    public static final String CORE_TEMPERATURE = "odo:Temperature";
    public static final String CORE_VISCOSITY = "odo:Viscosity";
    public static final String CORE_AGENT = "odo:Agent";
    public static final String CORE_CONFIGURATION = "odo:Configuration";
    public static final String CORE_RELATIONSHIP = "odo:Relationship";
    public static final String CORE_FUNCTIONAL_RELATIONSHIP = "odo:FunctionalRelationship";
    public static final String CORE_STRUCTURAL_RELATIONSHIP = "odo:StructuralRelationship";
    public static final String CORE_TYPE = "odo:EnumerableQuality";
    public static final String CORE_ORDERING = "odo:Ordering";
    public static final String CORE_REALM = "odo:Realm";
    public static final String CORE_ATTRIBUTE = "odo:Attribute";
    public static final String CORE_ROLE = "odo:Role";
    public static final String CORE_PRIORITY = "odo:Priority";
    public static final String CORE_COUNT = "odo:Numerosity";
    public static final String CORE_PROPORTION = "odo:Proportion";
    public static final String CORE_RATIO = "odo:Ratio";
    public static final String CORE_PRESENCE = "odo:Presence";
    public static final String CORE_OCCURRENCE = "odo:Occurrence";
    public static final String CORE_VALUE = "odo:Value";
    public static final String CORE_DISTANCE = "odo:Distance";
    public static final String CORE_UNCERTAINTY = "odo:Uncertainty";
    public static final String CORE_EXTENT = "odo:Extent";
  }

  public CoreOntology(File directory, OWL owl) {
    this.root = directory;
    this.owl = owl;
    Utils.Classpath.extractKnowledgeFromClasspath(this.root);
  }

  public CoreOntology(List<Pair<String, String>> ontologies, OWL owl) {
    this.root = ServiceConfiguration.INSTANCE.getDataPath("knowledge");
    this.owl = owl;
    for (var ontology : ontologies) {
      // TODO?
    }
  }

  public File getRoot() {
    return this.root;
  }

  public void registerCoreConcept(String coreConcept, Concept worldviewPeer) {
    /*
     * TODO must handle the specialized concepts so that they inherit from the redefined ones,
     * too. E.g. when the AGENT handler is received, it should create and install all the agent
     * types in the same ontology.
     */
    System.out.println("PIROGA");
  }

  public Concept getCoreType(Set<SemanticType> type) {

    if (type.contains(SemanticType.NOTHING)) {
      return owl.getNothing();
    }

    SemanticType coreType = getRepresentativeCoreSemanticType(type);
    if (coreType == null) {
      return null;
    }
    Concept ret = worldviewCoreConcepts.get(coreType);
    if (ret == null) {
      String id = coreConceptIds.get(coreType);
      if (id != null) {
        ret = owl.getConcept(id);
      }
    }

    return ret;
  }

  public SemanticType getRepresentativeCoreSemanticType(Collection<SemanticType> type) {

    SemanticType ret = null;

    /*
     * FIXME can be made faster using a mask and a switch, although the specialized concepts
     * still require a bit of extra logic.
     */

    if (type.contains(SemanticType.PROCESS)) {
      ret = SemanticType.PROCESS;
    } else if (type.contains(SemanticType.SUBJECT)) {
      ret = SemanticType.SUBJECT;
    } else if (type.contains(SemanticType.EVENT)) {
      ret = SemanticType.EVENT;
    } else if (type.contains(SemanticType.RELATIONSHIP)) {
      ret = SemanticType.RELATIONSHIP;
    } else /* if (SemanticType.contains(SemanticType.TRAIT)) { */ if (type.contains(
        SemanticType.IDENTITY)) {
      ret = SemanticType.IDENTITY;
    } else if (type.contains(SemanticType.ATTRIBUTE)) {
      ret = SemanticType.ATTRIBUTE;
    } else if (type.contains(SemanticType.REALM)) {
      ret = SemanticType.REALM;
    } else if (type.contains(SemanticType.ORDERING)) {
      ret = SemanticType.ORDERING;
    } else if (type.contains(SemanticType.ROLE)) {
      ret = SemanticType.ROLE;
    } else if (type.contains(SemanticType.CONFIGURATION)) {
      ret = SemanticType.CONFIGURATION;
    } else if (type.contains(SemanticType.CLASS)) {
      ret = SemanticType.CLASS;
    } else if (type.contains(SemanticType.QUANTITY)) {
      ret = SemanticType.QUANTITY;
    } else if (type.contains(SemanticType.DOMAIN)) {
      ret = SemanticType.DOMAIN;
    } else if (type.contains(SemanticType.ENERGY)) {
      ret = SemanticType.ENERGY;
    } else if (type.contains(SemanticType.ENTROPY)) {
      ret = SemanticType.ENTROPY;
    } else if (type.contains(SemanticType.LENGTH)) {
      ret = SemanticType.LENGTH;
    } else if (type.contains(SemanticType.MASS)) {
      ret = SemanticType.LENGTH;
    } else if (type.contains(SemanticType.VOLUME)) {
      ret = SemanticType.VOLUME;
    } else if (type.contains(SemanticType.WEIGHT)) {
      ret = SemanticType.WEIGHT;
    } else if (type.contains(SemanticType.MONEY)) {
      ret = SemanticType.MONEY;
    } else if (type.contains(SemanticType.DURATION)) {
      ret = SemanticType.DURATION;
    } else if (type.contains(SemanticType.AREA)) {
      ret = SemanticType.AREA;
    } else if (type.contains(SemanticType.ACCELERATION)) {
      ret = SemanticType.ACCELERATION;
    } else if (type.contains(SemanticType.PRIORITY)) {
      ret = SemanticType.PRIORITY;
    } else if (type.contains(SemanticType.ELECTRIC_POTENTIAL)) {
      ret = SemanticType.ELECTRIC_POTENTIAL;
    } else if (type.contains(SemanticType.CHARGE)) {
      ret = SemanticType.CHARGE;
    } else if (type.contains(SemanticType.RESISTANCE)) {
      ret = SemanticType.RESISTANCE;
    } else if (type.contains(SemanticType.RESISTIVITY)) {
      ret = SemanticType.RESISTIVITY;
    } else if (type.contains(SemanticType.PRESSURE)) {
      ret = SemanticType.PRESSURE;
    } else if (type.contains(SemanticType.ANGLE)) {
      ret = SemanticType.ANGLE;
    } else if (type.contains(SemanticType.VELOCITY)) {
      ret = SemanticType.VELOCITY;
    } else if (type.contains(SemanticType.TEMPERATURE)) {
      ret = SemanticType.TEMPERATURE;
    } else if (type.contains(SemanticType.VISCOSITY)) {
      ret = SemanticType.VISCOSITY;
    } else if (type.contains(SemanticType.AGENT)) {
      ret = SemanticType.AGENT;
    } else if (type.contains(SemanticType.UNCERTAINTY)) {
      ret = SemanticType.UNCERTAINTY;
    } else if (type.contains(SemanticType.PROBABILITY)) {
      ret = SemanticType.PROBABILITY;
    } else if (type.contains(SemanticType.PROPORTION)) {
      ret = SemanticType.PROPORTION;
    } else if (type.contains(SemanticType.NUMEROSITY)) {
      ret = SemanticType.NUMEROSITY;
    } else if (type.contains(SemanticType.DISTANCE)) {
      ret = SemanticType.DISTANCE;
    } else if (type.contains(SemanticType.RATIO)) {
      ret = SemanticType.RATIO;
    } else if (type.contains(SemanticType.VALUE)) {
      ret = SemanticType.VALUE;
    } else if (type.contains(SemanticType.MONETARY_VALUE)) {
      ret = SemanticType.MONETARY_VALUE;
    } else if (type.contains(SemanticType.OCCURRENCE)) {
      ret = SemanticType.OCCURRENCE;
    } else if (type.contains(SemanticType.PRESENCE)) {
      ret = SemanticType.PRESENCE;
    } else if (type.contains(SemanticType.EXTENT)) {
      ret = SemanticType.EXTENT;
    }
    // THESE COME AFTER ALL THE POSSIBLE SUBCLASSES
    else if (type.contains(SemanticType.EXTENSIVE)) {
      ret = SemanticType.EXTENSIVE;
    } else if (type.contains(SemanticType.INTENSIVE)) {
      ret = SemanticType.INTENSIVE;
    } /*
       * else if (type.contains(Type.ASSESSMENT)) { ret = Type.ASSESSMENT; }
       */

    return ret;
  }

  public String importOntology(String url, String prefix, Channel monitor) {
    return owl.importExternal(url, prefix, monitor);
  }

  public void setAsCoreType(Concept concept) {
    worldviewCoreConcepts.put(getRepresentativeCoreSemanticType(concept.getType()), concept);
  }

  public Concept alignCoreInheritance(Concept concept) {
    // if (concept.is(IKimConcept.Type.RELATIONSHIP)) {
    // // parent of core relationship depends on functional/structural nature
    // if (concept.is(IKimConcept.Type.FUNCTIONAL) ||
    // concept.is(IKimConcept.Type.STRUCTURAL)) {
    // concept = getCoreType(EnumSet.of(IKimConcept.Type.RELATIONSHIP));
    // }
    // } else if (concept.is(IKimConcept.Type.AGENT)) {
    // // parent of agent depends on agent typology
    // if (concept.is(IKimConcept.Type.DELIBERATIVE) ||
    // concept.is(IKimConcept.Type.INTERACTIVE)
    // || concept.is(IKimConcept.Type.REACTIVE)) {
    // concept = getCoreType(EnumSet.of(IKimConcept.Type.AGENT));
    // }
    // }
    return concept;
  }

  // /**
  // * Return the spatial nature, if any, of the passed concept, which should be a countable, or
  // * null.
  // *
  // * @param concept
  // * @return
  // */
  // public ExtentDimension getSpatialNature(KConcept concept) {
  // for (KConcept identity : reasoner.identities(concept)) {
  // if (identity.is(OWL.INSTANCE.getConcept(NS.SPATIAL_IDENTITY))) {
  // if (identity.is(OWL.INSTANCE.getConcept(NS.AREAL_IDENTITY))) {
  // return ExtentDimension.AREAL;
  // } else if (identity.is(OWL.INSTANCE.getConcept(NS.PUNTAL_IDENTITY))) {
  // return ExtentDimension.PUNTAL;
  // }
  // if (identity.is(OWL.INSTANCE.getConcept(NS.LINEAL_IDENTITY))) {
  // return ExtentDimension.LINEAL;
  // }
  // if (identity.is(OWL.INSTANCE.getConcept(NS.VOLUMETRIC_IDENTITY))) {
  // return ExtentDimension.VOLUMETRIC;
  // }
  // }
  // }
  // return null;
  // }

  // /**
  // * Return the temporal resolution implied in the passed concept, which should be an event, or
  // * null.
  // *
  // * TODO add the multiplier from (TBI) data properties associated with the identity.
  // *
  // * @param concept
  // * @return
  // */
  // public KTime.Resolution getTemporalNature(KConcept concept) {
  // for (KConcept identity : reasoner.identities(concept)) {
  // if (identity.is(OWL.INSTANCE.getConcept(NS.TEMPORAL_IDENTITY))) {
  // if (identity.is(OWL.INSTANCE.getConcept(NS.YEARLY_IDENTITY))) {
  // return Time.resolution(1, KTime.Resolution.Type.YEAR);
  // } else if (identity.is(OWL.INSTANCE.getConcept(NS.HOURLY_IDENTITY))) {
  // return Time.resolution(1, KTime.Resolution.Type.HOUR);
  // } else if (identity.is(OWL.INSTANCE.getConcept(NS.WEEKLY_IDENTITY))) {
  // return Time.resolution(1, KTime.Resolution.Type.WEEK);
  // } else if (identity.is(OWL.INSTANCE.getConcept(NS.MONTHLY_IDENTITY))) {
  // return Time.resolution(1, KTime.Resolution.Type.MONTH);
  // } else if (identity.is(OWL.INSTANCE.getConcept(NS.DAILY_IDENTITY))) {
  // return Time.resolution(1, KTime.Resolution.Type.DAY);
  // }
  // }
  // }
  // return null;
  // }

}
