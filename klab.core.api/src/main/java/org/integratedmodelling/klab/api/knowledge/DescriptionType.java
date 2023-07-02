package org.integratedmodelling.klab.api.knowledge;

import java.util.Collection;

/**
 * A classification of the primary observation activity (odo:Description) that
 * can produce an observation of this observable. Encodes the same
 * classification in ODO-IM. The descriptions specialize
 * {@link IResolutionScope#Mode}, which is captured by exposing its
 * correspondent value.
 * 
 * @author ferdinando.villa
 *
 */
public enum DescriptionType {

	/**
	 * The observation activity that produces a countable object. Acknowledgement is
	 * a special case of instantiation, limited to a subject and performed on a fiat
	 * basis (in k.IM through an <code>observe</code> statement).
	 */
	INSTANTIATION(true),
	/**
	 * The observation activity that produces a configuration (aka EMERGENCE) - the
	 * instantiation of a configuration.
	 */
	DETECTION(true),
	/**
	 * The observation activity that produces a dynamic account of a process
	 */
	SIMULATION(false),
	/**
	 * The observation activity that produces a numeric quality
	 */
	QUANTIFICATION(false),
	/**
	 * The observation activity that produces a categorical quality (observes a
	 * conceptual category) over a context.
	 */
	CATEGORIZATION(false),
	/**
	 * The observation activity that produces a boolean quality (presence/absence)
	 */
	VERIFICATION(false),
	/**
	 * The observation activity that attributes a trait or role to another
	 * observation (if it is a quality, it may transform its values). Equivalent to
	 * INSTANTIATION of a concrete t/a given the abstract form and an inherent
	 * observable.
	 */
	CLASSIFICATION(true),
	/**
	 * The resolution activity of a concrete trait or role that has been previously
	 * attributed to an observation through {@link #CLASSIFICATION}.
	 */
	CHARACTERIZATION(false),
	/**
	 * Compilation is the observation of a void observable, producing only side
	 * effects. Creates non-semantic artifacts such as tables, charts, reports etc.
	 */
	COMPILATION(false),
	/**
	 * Acknowledgement is the "void" of observation activity: an object exists and
	 * we just take notice of it. The resolution of an instantiated object is an
	 * acknowledgement and descriptions can be written of it (appearing as void
	 * actuators in the dataflow, internal to the instantiator's actuator).
	 * Acknowledgements can also be explicitly programmed in k.IM through the
	 * <code>observe</code> statement.
	 */
	ACKNOWLEDGEMENT(false);

	boolean instantiation;

	/**
	 * Return whether this description activity is an instantiation, i.e. is
	 * resolved by creating zero or more of its target observations. The observation
	 * is not completed until the resulting observations are also resolved.
	 * Descriptions can instantiate countables (through {@link #INSTANTIATION} or
	 * predicates (through {@link #CLASSIFICATION}).
	 * 
	 * @return
	 */
	public boolean isInstantiation() {
		return instantiation;
	}

	/**
	 * Return whether this description activity is a resolution, i.e. is resolved by
	 * "explaining" an existing observation so that it corresponds to its stated
	 * semantics.
	 * 
	 * @return
	 */
	public boolean isResolution() {
		return !instantiation;
	}

	DescriptionType(boolean mode) {
		this.instantiation = mode;
	}

	public static DescriptionType forSemantics(Collection<SemanticType> type, boolean distributed) {
		if (type.contains(SemanticType.CLASS)) {
			return CATEGORIZATION;
		} else if (type.contains(SemanticType.PRESENCE)) {
			return VERIFICATION;
		} else if (type.contains(SemanticType.QUANTIFIABLE)) {
			return QUANTIFICATION;
		} else if (type.contains(SemanticType.CONFIGURATION)) {
			return DETECTION;
		} else if (type.contains(SemanticType.PROCESS)) {
			return SIMULATION;
		} else if (type.contains(SemanticType.TRAIT)) {
			return CLASSIFICATION;
		} else if (type.contains(SemanticType.DIRECT_OBSERVABLE)) {
			return distributed ? INSTANTIATION : ACKNOWLEDGEMENT;
		}
		return COMPILATION;
	}
}