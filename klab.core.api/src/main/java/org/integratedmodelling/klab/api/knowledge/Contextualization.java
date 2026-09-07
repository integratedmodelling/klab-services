package org.integratedmodelling.klab.api.knowledge;

import java.util.Collection;
import org.integratedmodelling.klab.api.exceptions.KlabUnimplementedException;
import org.integratedmodelling.klab.api.lang.kim.KimConcept;

/**
 * A classification of the primary observation activity (odo:Description) that can produce an
 * observation of this observable. Encodes the same classification in ODO-IM. The descriptions
 * capture the higher-level "countable" taxonomy through boolean inspection methods.
 *
 * <p>Collective contextualizations (those with "instantiation == true") always trigger the
 * corresponding singular observation. So INSTANTIATION/CONNECTION trigger ACKNOWLEDGEMENT and
 * CLASSIFICATION triggers CHARACTERIZATION. This behavior must be hard-coded in the implementation
 * and not achieved through observation strategies..
 *
 * @author ferdinando.villa
 */
public enum Contextualization {

  /**
   * The activity that produces nothing. Classifies the description type of any non-functional,
   * abstract or inconsistent observable.
   */
  VOID(false, "void", Artifact.Type.VOID, "nothing"),
  /**
   * The activity that contextualizes a collective observation of countable substantials. Example:
   * <code>
   * each earth:Terrestrial earth:Region</code>. Triggers the corresponding ACKNOWLEDGEMENT.
   */
  INSTANTIATION(true, "object", Artifact.Type.OBJECT, "instantiator"),
  /**
   * The activity that contextualizes a detected configuration (aka EMERGENCE) that has been
   * submitted to the DigitalTwin by the semantic engine detecting its existence. A Configuration
   * built from detected qualities will be local to the Observation holding the qualities; one built
   * from Relationships will be global to the DigitalTwin and may change as new observations are
   * made under the same observer.
   *
   * <p>Example: <code>infrastructure:RoadNetwork</code>
   */
  DETECTION(false, "configuration", Artifact.Type.CONFIGURATION, "detector"),
  /**
   * The activity that contextualizes a process. The Process is an occurrent, so it is resolved but
   * not initialized before computation starts; its schedule will be merged with the Digital Twin's,
   * and contextualization will be scheduled as time progresses and when the generated time
   * transitions affect it.
   */
  SIMULATION(false, "process", Artifact.Type.PROCESS, "simulator"),
  /**
   * The activity that contextualizes a measurable physical property with units providing the scale.
   */
  MEASURE(false, "number", Artifact.Type.QUANTITY, "quantifier"),
  /**
   * The observation activity that contextualizes a numeric quality that is not a measurement, so
   * does not have a Unit (although it may have a range, or be a magnitude or other proxy that
   * describes a measured quality that does)
   */
  QUANTIFICATION(false, "number", Artifact.Type.QUANTITY, "quantifier"),
  /**
   * The activity that contextualizes a numeric quality that quantifies value, monetary or not. The
   * value semantics can be absolute or relative to another concept. Values always have a Currency,
   * which may be a bounded rank or a monetary unit.
   */
  VALUATION(false, "number", Artifact.Type.QUANTITY, "valuator"),
  /**
   * The activity that contextualizes a categorical quality (a category linked to a concept, i.e., a
   * <code>type of Concept</code>). The resulting observation reifies a predicate in context, and
   * will always have a concept consistent with the definition as its value.
   */
  CATEGORIZATION(false, "concept", Artifact.Type.CONCEPT, "categorizer"),
  /**
   * The activity that contextualizes a boolean quality, which in semantic terms corresponds to
   * presence or absence of a substantial. Presence of a process is accepted as a semantic shortcut
   * to "any event that subsumes the process".
   */
  VERIFICATION(false, "boolean", Artifact.Type.BOOLEAN, "verifier"),
  /**
   * CLASSIFICATION of an ABSTRACT PREDICATE (either directly abstract or qualified with <code>any
   * </code>) is the contextualization that scans one or more substantials to attribute a concrete
   * trait or role to each of them. Equivalent to INSTANTIATION of a concrete t/a given the abstract
   * form and an inherent observable. This is specified as <code>
   * ABSTRACT_PREDICATE of [each] SUBSTANTIAL</code>. Using a collective inherent forces k.LAB to
   * resolve the collective substantials before the contextualization is triggered; not using <code>
   * each</code> will only classify the substantials in the context of the observation. The
   * substantials acquire the concrete predicate in their semantics, but do not switch cohorts; if
   * the predicate is an <code>individual identity</code>, new cohorts may be built to collect the
   * observables (e.g. Countries collecting all Regions that adopt Country). Triggers
   * CHARACTERIZATION after each successful resolution.
   */
  CLASSIFICATION(true, "resolve", Artifact.Type.VOID, "classifier"),
  /**
   * The contextualization of a concrete trait or role after it has been attributed to an
   * observation through {@link #CLASSIFICATION}. Explains the trait within the observation. This is
   * specified as <code>PREDICATE of SUBSTANTIAL</code>.
   */
  CHARACTERIZATION(false, "resolve", Artifact.Type.CONCEPT, "characterizer"),
  /**
   * The contextualization of a concrete trait or role that has been attributed to a quality
   * observation. Transforms the quality so that it expresses the trait. This is specified as <code>
   * PREDICATE of QUALITY</code>.
   */
  TRANSFORMATION(false, "resolve", Artifact.Type.NUMBER, "transformer"),
  /**
   * Acknowledgement is the contextualization (explanation) of an individual substantial. Triggered
   * by INSTANTIATION.
   */
  ACKNOWLEDGEMENT(false, "void", Artifact.Type.VOID, "explainer"),
  /**
   * Instantiation of relationships and bonds, requiring the "connected" countables to be observed
   * as well. Uses the same rules as CLASSIFICATION w.r.t. the collective/individual nature of the
   * substantials, triggering instantiation vs. using the substantials existing in the context of
   * the observation at the time of contextualization. Relationships are substantials, so
   * ACKNOWLEDGEMENT is triggered at for each observed relationship.
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
  public boolean isCollective() {
    return instantiation;
  }

  /**
   * The type of declaration corresponding to this description. The k.DL actuator creates the
   * observation corresponding to the description.
   *
   * @return
   */
  public String getLanguageForm() {
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

  Contextualization(
      boolean mode, String kdlKeyword, Artifact.Type observationType, String verbalForm) {
    this.instantiation = mode;
    this.kdlType = kdlKeyword;
    this.observationType = observationType;
    this.verbalForm = verbalForm;
  }

  public static Contextualization forSemantics(KimConcept observable) {

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
   */
  private static Contextualization forSemantics(
      Collection<SemanticType> type, boolean distributed) {
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
        "Contextualization::forSemantics - unexpected semantic typeset " + type);
    //        return COMPILATION;
  }
}
