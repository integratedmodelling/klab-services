package org.integratedmodelling.klab.api.knowledge;

import java.util.Collection;

import org.integratedmodelling.klab.api.knowledge.observation.Observation;

/**
 * A classification of the primary observation activity (odo:Description) that can produce an observation of this
 * observable. Encodes the same classification in ODO-IM. The descriptions capture the higher-level "countable" taxonomy
 * through boolean inspection methods.
 *
 * @author ferdinando.villa
 */
public enum DescriptionType {

    /**
     * The observation activity that produces a countable object. Acknowledgement is a special case of instantiation,
     * limited to a subject and performed on a fiat basis (in k.IM through an <code>observe</code> statement). The
     * instantiation of relationships ({@link #CONNECTION}) is handled separately because of the non-independence from
     * its targets.
     */
    INSTANTIATION(true, "object"),
    /**
     * The observation activity that produces a configuration (aka EMERGENCE) - the instantiation of a configuration.
     */
    DETECTION(true, "configuration"),
    /**
     * The observation activity that produces a dynamic account of a process
     */
    SIMULATION(false, "process"),
    /**
     * The observation activity that produces a numeric quality
     */
    QUANTIFICATION(false, "number"),
    /**
     * The observation activity that produces a categorical quality (observes a conceptual category) over a context.
     */
    CATEGORIZATION(false, "concept"),
    /**
     * The observation activity that produces a boolean quality (presence/absence)
     */
    VERIFICATION(false, "boolean"),
    /**
     * The observation activity that attributes a trait or role to another observation (if it is a quality, it may
     * transform its values). Equivalent to INSTANTIATION of a concrete t/a given the abstract form and an inherent
     * observable.
     */
    CLASSIFICATION(true, "resolve"),
    /**
     * The resolution activity of a concrete trait or role that has been previously attributed to an observation through
     * {@link #CLASSIFICATION}. Produces a FILTER observation strategy.
     */
    CHARACTERIZATION(false, "resolve"),
    /**
     * Compilation is the observation of a void observable, producing only side effects. Creates non-semantic artifacts
     * such as tables, charts, reports etc.
     */
    COMPILATION(false, "void"),
    /**
     * Acknowledgement is the "void" of observation activity: an object exists and we just take notice of it. The
     * resolution of an instantiated object is an acknowledgement and descriptions can be written of it (appearing as
     * void actuators in the dataflow, internal to the instantiator's actuator). Acknowledgements can also be explicitly
     * programmed in k.IM through the
     * <code>observe</code> statement.
     */
    ACKNOWLEDGEMENT(false, "resolve"),
    /**
     * Instantiation of relationships, requiring the "connected" countables to be observed as well.
     */
    CONNECTION(true, "object");

    boolean instantiation;
    String kdlType;
    Artifact.Type observationType;

    /**
     * Return whether this description activity is an instantiation, i.e. is resolved by creating zero or more of its
     * target observations. The observation is not completed until the resulting observations are also resolved.
     * Descriptions can instantiate countables (through {@link #INSTANTIATION}, {@link #CONNECTION} or predicates
     * (through {@link #CLASSIFICATION}).
     *
     * @return
     */
    public boolean isInstantiation() {
        return instantiation;
    }

    /**
     * The type of k.DL actuator declaration corresponding to this description. The k.DL actuator creates the
     * observation corresponding to the description.
     *
     * @return
     */
    public String getKdlType() {
        return kdlType;
    }

    /**
     * Return whether this description activity is a resolution, i.e. is resolved by "explaining" an existing
     * observation so that it corresponds to its stated semantics.
     *
     * @return
     */
    public boolean isResolution() {
        return !instantiation;
    }

    DescriptionType(boolean mode, String kdlKeyword) {
        this.instantiation = mode;
        this.kdlType = kdlKeyword;
    }

    public static DescriptionType forSemantics(Collection<SemanticType> type, boolean distributed) {
        if (type.contains(SemanticType.CLASS)) {
            return CATEGORIZATION;
        } else if (type.contains(SemanticType.PRESENCE)) {
            return VERIFICATION;
        } else if (type.contains(SemanticType.RELATIONSHIP)) {
            return CONNECTION;
        } else if (type.contains(SemanticType.QUANTIFIABLE)) {
            return QUANTIFICATION;
        } else if (type.contains(SemanticType.CONFIGURATION)) {
            return DETECTION;
        } else if (type.contains(SemanticType.PROCESS)) {
            return SIMULATION;
        } else if (type.contains(SemanticType.TRAIT)) {
            return distributed ? CHARACTERIZATION : CLASSIFICATION;
        } else if (type.contains(SemanticType.RELATIONSHIP)) {
            return CONNECTION;
        } else if (type.contains(SemanticType.DIRECT_OBSERVABLE)) {
            return distributed ? INSTANTIATION : ACKNOWLEDGEMENT;
        }
        return COMPILATION;
    }
}