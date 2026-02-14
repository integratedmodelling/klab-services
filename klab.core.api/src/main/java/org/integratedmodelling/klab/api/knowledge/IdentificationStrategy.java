package org.integratedmodelling.klab.api.knowledge;

import org.integratedmodelling.klab.api.knowledge.observation.Observation;

import java.util.Comparator;

/**
 * An identification strategy is a functor compiled from a {@link
 * org.integratedmodelling.klab.api.lang.kim.KimObservationStrategy} tagged as an identification
 * strategy. Its job is to compare two {@link Observation}s of a singular substantial and assess if
 * they represent the same individual.
 *
 * The default IdentificationStrategy is predefined in the runtime and compares the observation URNs.
 */
public interface IdentificationStrategy extends Knowledge, Resolvable, Comparator<Observation> {}
