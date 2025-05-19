package org.integratedmodelling.klab.api.knowledge;

import org.integratedmodelling.klab.api.exceptions.KlabUnimplementedException;

import java.util.Collection;

/**
 * A classification of the primary observation activity (odo:Description) that can produce an observation of
 * this observable. Encodes the same classification in ODO-IM. The descriptions capture the higher-level
 * "countable" taxonomy through boolean inspection methods.
 *
 * @author ferdinando.villa
 */
public enum DescriptionType {

    VOID(false, "void", Artifact.Type.VOID, "nothing"),
    /**
     * The observation activity that produces a countable object. Acknowledgement is a special case of
     * instantiation, limited to a subject and performed on a fiat basis (in k.IM through an
     * <code>observe</code> statement). The instantiation of relationships ({@link #CONNECTION}) is handled
     * separately because of the non-independence from its targets.
     */
    INSTANTIATION(true, "object", Artifact.Type.OBJECT, "instantiate"),
    /**
     * The observation activity that produces a configuration (aka EMERGENCE) - the instantiation of a
     * configuration.
     */
    DETECTION(true, "configuration", Artifact.Type.CONFIGURATION, "detect"),
    /**
     * The observation activity that produces a dynamic account of a process
     */
    SIMULATION(false, "process", Artifact.Type.PROCESS, "simulate"),
    /**
     * The observation activity that produces a numeric quality
     */
    QUANTIFICATION(false, "number", Artifact.Type.QUANTITY, "quantify"),
    /**
     * The observation activity that produces a categorical quality (observes a conceptual category) over a
     * context.
     */
    CATEGORIZATION(false, "concept", Artifact.Type.CONCEPT, "categorize"),
    /**
     * The observation activity that produces a boolean quality (presence/absence)
     */
    VERIFICATION(false, "boolean", Artifact.Type.BOOLEAN, "verify"),
    /**
     * The observation activity that scans a group of observation to attribute a concrete trait or role to
     * each of them (if it is a quality, it will produce a transforming state for successive subsetting of
     * another observation). Equivalent to INSTANTIATION of a concrete t/a given the abstract form and an
     * inherent observable. This is specified as <code>TRAIT of OBSERVABLE</code>.
     */
    CLASSIFICATION(true, "resolve", Artifact.Type.VOID, "classify"),
    /**
     * The resolution activity of a concrete trait or role that has been previously attributed to an
     * observation through {@link #CLASSIFICATION}. Explains the trait within the observation.  This is
     * specified as <code>TRAIT within OBSERVABLE</code>.
     */
    CHARACTERIZATION(false, "resolve", Artifact.Type.CONCEPT, "characterize"),
//    /**
//     * Compilation is the observation of a void observable, producing only side effects. Creates
//     non-semantic
//     * artifacts such as tables, charts, reports etc.
//     */
//    COMPILATION(false, "void"),
    /**
     * Acknowledgement is the "void" of observation activity: an object exists and we take notice of its
     * existence. It does not <em>produce</em> an observation but simply explains it through a description.
     * The resolution of an instantiated object is an acknowledgement and descriptions can be written of it
     * (appearing as void actuators in the dataflow, internal to the instantiator's actuator).
     * Acknowledgements can also be explicitly programmed in k.IM through the
     * <code>observe</code> statement.
     */
    ACKNOWLEDGEMENT(false, "void", Artifact.Type.VOID, "explain"),
    /**
     * Instantiation of relationships, requiring the "connected" countables to be observed as well.
     */
    CONNECTION(true, "object", Artifact.Type.RELATIONSHIP, "connect");

    private final boolean instantiation;
    private final String kdlType;
    private Artifact.Type observationType;
    private String verbalForm;


    /**
     * Return whether this description activity is an instantiation, i.e. is resolved by creating zero or more
     * of its target observations. The observation is not completed until the resulting observations are also
     * resolved. Descriptions can instantiate countables (through {@link #INSTANTIATION}, {@link #CONNECTION}
     * or predicates (through {@link #CLASSIFICATION}).
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

    public Artifact.Type getObservationType() {
        return observationType;
    }

    public String getVerbalForm() {
        return verbalForm;
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

    DescriptionType(boolean mode, String kdlKeyword, Artifact.Type observationType, String verbalForm) {
        this.instantiation = mode;
        this.kdlType = kdlKeyword;
        this.observationType = observationType;
        this.verbalForm = verbalForm;
    }

    /**
     * Return the description type that corresponds to the specified semantics, according to the context of
     * resolution.
     *
     * @param type        the semantic types for the observable
     * @param distributed if true, the description type refers to instantiation (of either observations or
     *                    their traits); otherwise it refers to "explanation" of an existing observation or
     *                    characteristic. It's only relevant for countables and traits.
     * @return the description type
     */
    public static DescriptionType forSemantics(Collection<SemanticType> type, boolean distributed) {
        if (type.contains(SemanticType.CLASS)) {
            return CATEGORIZATION;
        } else if (type.contains(SemanticType.PRESENCE)) {
            return VERIFICATION;
        } else if (type.contains(SemanticType.RELATIONSHIP)) {
            return distributed ? CONNECTION : ACKNOWLEDGEMENT;
        } else if (type.contains(SemanticType.QUANTIFIABLE)) {
            return QUANTIFICATION;
        } else if (type.contains(SemanticType.CONFIGURATION)) {
            return DETECTION;
        } else if (type.contains(SemanticType.PROCESS)) {
            return SIMULATION;
        } else if (type.contains(SemanticType.TRAIT)) {
            return distributed ? CLASSIFICATION : CHARACTERIZATION;
        } else if (type.contains(SemanticType.DIRECT_OBSERVABLE)) {
            return distributed ? INSTANTIATION : ACKNOWLEDGEMENT;
        } else if (type.contains(SemanticType.NOTHING)) {
            return VOID;
        }
        throw new KlabUnimplementedException("DescriptionType::forSemantics - unexpected semantic typeset " + type);
//        return COMPILATION;
    }
}