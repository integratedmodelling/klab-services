package org.integratedmodelling.klab.api.knowledge;

import java.util.Collection;
import org.integratedmodelling.klab.api.exceptions.KlabUnimplementedException;
import org.integratedmodelling.klab.api.lang.kim.KimConcept;

/**
 * A classification of the primary observation activity (odo:Description) that can produce an
 * observation of this observable. Encodes the same classification in ODO-IM. The descriptions
 * capture the higher-level "countable" taxonomy through boolean inspection methods.
 *
 * <p>Collective observations (those with "instantiation == true") always trigger the corresponding
 * singular observation. So INSTANTIATION triggers ACKNOWLEDGEMENT and CLASSIFICATION triggers
 * CHARACTERIZATION. This behavior must be hard-coded in the implementation and not achieved through
 * observation strategies..
 *
 * @author ferdinando.villa
 */
public enum DescriptionType {

  /**
   * The observation activity that produces nothing. Classifies the description type of any
   * non-functional, abstract or inconsistent observable.
   */
  VOID(false, "void", Artifact.Type.VOID, "nothing"),
  /**
   * The observation activity that produces a countable substantial. Example: <code>
   * each earth:Terrestrial earth:Region</code>. Triggers the corresponding ACKNOWLEDGEMENT.
   */
  INSTANTIATION(true, "object", Artifact.Type.OBJECT, "instantiator"),
  /**
   * The observation activity that explains a detected configuration (aka EMERGENCE).
   *
   * <p>Example: <code>infrastructure:RoadNetwork</code>
   */
  DETECTION(false, "configuration", Artifact.Type.CONFIGURATION, "detector"),
  /** The observation activity that produces a dynamic account of a process */
  SIMULATION(false, "process", Artifact.Type.PROCESS, "simulator"),
  /** The observation activity that produces a measurable quality with units */
  MEASURE(false, "number", Artifact.Type.QUANTITY, "quantifier"),
  /** The observation activity that produces a numeric quality */
  QUANTIFICATION(false, "number", Artifact.Type.QUANTITY, "quantifier"),
  /** The observation activity that produces a numeric quality that quantifies value */
  VALUATION(false, "number", Artifact.Type.QUANTITY, "valuator"),
  /**
   * The observation activity that produces a categorical quality (observes a conceptual category)
   * over a context.
   */
  CATEGORIZATION(false, "concept", Artifact.Type.CONCEPT, "categorizer"),
  /** The observation activity that produces a boolean quality (presence/absence) */
  VERIFICATION(false, "boolean", Artifact.Type.BOOLEAN, "verifier"),
  /**
   * The observation activity that scans a group of observation to attribute a concrete trait or
   * role to each of them (if it is a quality, it will produce a transforming state for successive
   * subsetting of another observation). Equivalent to INSTANTIATION of a concrete t/a given the
   * abstract form and an inherent observable. This is specified as <code>
   * ABSTRACT_TRAIT of each SUBSTANTIAL</code>. Triggers CHARACTERIZATION after each successful
   * resolution.
   */
  CLASSIFICATION(true, "resolve", Artifact.Type.VOID, "classifier"),
  /**
   * The resolution activity of a concrete trait or role after it has been attributed to an
   * observation through {@link #CLASSIFICATION}. Explains the trait within the observation. This is
   * specified as <code>TRAIT of SUBSTANTIAL</code>.
   */
  CHARACTERIZATION(false, "resolve", Artifact.Type.CONCEPT, "characterizer"),
  /**
   * The resolution activity of a concrete trait or role that has been attributed to a quality
   * observation. Transforms the quality so that it expresses the trait. This is specified as <code>
   * TRAIT of QUALITY</code>.
   */
  TRANSFORMATION(false, "resolve", Artifact.Type.NUMBER, "transformer"),

  /** Acknowledgement is the resolution of a substantial. Triggered by INSTANTIATION. */
  ACKNOWLEDGEMENT(false, "void", Artifact.Type.VOID, "explainer"),
  /**
   * Instantiation of relationships, requiring the "connected" countables to be observed as well.
   * Triggers ACKNOWLEDGEMENT for each observed relationship.
   */
  CONNECTION(true, "object", Artifact.Type.RELATIONSHIP, "connector");

  private final boolean instantiation;
  private final String kdlType;
  private Artifact.Type observationType;
  private String verbalForm;

  /**
   * Return whether this description activity is an instantiation, i.e. is resolved by creating zero
   * or more of its target observations. The observation is not completed until the resulting
   * observations are also resolved. Descriptions can instantiate countables (through {@link
   * #INSTANTIATION}, {@link #CONNECTION} or predicates (through {@link #CLASSIFICATION}).
   *
   * @return
   */
  public boolean isInstantiation() {
    return instantiation;
  }

  /**
   * The type of k.DL actuator declaration corresponding to this description. The k.DL actuator
   * creates the observation corresponding to the description.
   *
   * @return
   */
  public String getKdlType() {
    return kdlType;
  }

  public Artifact.Type getObservationType() {
    return observationType;
  }

  public String getVerbalForm() {
    return verbalForm;
  }

  /**
   * Return whether this description activity is a resolution, i.e. is resolved by "explaining" an
   * existing observation so that it corresponds to its stated semantics.
   *
   * @return
   */
  public boolean isResolution() {
    return !instantiation;
  }

  DescriptionType(
      boolean mode, String kdlKeyword, Artifact.Type observationType, String verbalForm) {
    this.instantiation = mode;
    this.kdlType = kdlKeyword;
    this.observationType = observationType;
    this.verbalForm = verbalForm;
  }

  public static DescriptionType forSemantics(KimConcept observable) {

    // predicates are particular and cannot be classified based on type alone
    if (observable.is(SemanticType.PREDICATE)) {
      // depends on the inherency
      var inherent = observable.getInherent();
      if (inherent == null) {
        // not observable as such
        return VOID;
      }
      if (inherent.is(SemanticType.QUALITY)) {
        return TRANSFORMATION;
      }

      return inherent.isCollective() ? CLASSIFICATION : CHARACTERIZATION;
    }
    return forSemantics(observable.getType(), observable.isCollective());
  }

  /**
   * Return the description type that corresponds to the specified semantics, according to the
   * context of resolution.
   *
   * @param type the semantic types for the observable
   * @param distributed if true, the description type refers to instantiation (of either
   *     observations or their traits); otherwise it refers to "explanation" of an existing
   *     observation or characteristic. It's only relevant for countables and traits.
   * @return the description type
   * @deprecated use the other
   */
  private static DescriptionType forSemantics(Collection<SemanticType> type, boolean distributed) {
    if (type.contains(SemanticType.CLASS)) {
      return CATEGORIZATION;
    } else if (type.contains(SemanticType.PRESENCE)) {
      return VERIFICATION;
    } else if (type.contains(SemanticType.EXTENSIVE) || type.contains(SemanticType.INTENSIVE)) {
      return MEASURE;
    } else if (type.contains(SemanticType.VALUE) || type.contains(SemanticType.MONETARY_VALUE)) {
      return VALUATION;
    } else if (type.contains(SemanticType.QUALITY)) {
      return QUANTIFICATION;
    } else if (type.contains(SemanticType.RELATIONSHIP)) {
      return distributed ? CONNECTION : ACKNOWLEDGEMENT;
    } else if (type.contains(SemanticType.CONFIGURATION)) {
      return DETECTION;
    } else if (type.contains(SemanticType.PROCESS)) {
      return SIMULATION;
    } else if (type.contains(SemanticType.COUNTABLE)) {
      return distributed ? INSTANTIATION : ACKNOWLEDGEMENT;
    } else if (type.contains(SemanticType.NOTHING)) {
      return VOID;
    }
    throw new KlabUnimplementedException(
        "DescriptionType::forSemantics - unexpected semantic typeset " + type);
    //        return COMPILATION;
  }
}
