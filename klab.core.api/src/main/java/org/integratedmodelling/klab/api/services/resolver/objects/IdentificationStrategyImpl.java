package org.integratedmodelling.klab.api.services.resolver.objects;

import org.integratedmodelling.klab.api.data.Metadata;
import org.integratedmodelling.klab.api.exceptions.KlabUnimplementedException;
import org.integratedmodelling.klab.api.knowledge.IdentificationStrategy;
import org.integratedmodelling.klab.api.knowledge.ObservationStrategy;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.knowledge.observation.impl.ObservationBuilderImpl;

public class IdentificationStrategyImpl extends ObservationStrategyImpl implements IdentificationStrategy {

    public IdentificationStrategyImpl(ObservationStrategy strategy) {
        throw new KlabUnimplementedException("Identification strategies are not yet implemented");
    }

    @Override
    public int compare(Observation o1, Observation o2) {
        throw new KlabUnimplementedException("Identification strategies are not yet implemented");
    }
}
