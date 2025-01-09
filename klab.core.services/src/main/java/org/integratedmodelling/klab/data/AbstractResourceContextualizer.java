package org.integratedmodelling.klab.data;

import org.integratedmodelling.klab.api.data.Data;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.knowledge.Resource;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.common.data.Instance;

public abstract class AbstractResourceContextualizer {

    public boolean contextualize(Observation observation, ContextScope scope) {
        return false;
    }

    /**
     * Retrieve all the input data the resource wants.
     *
     * @param resource
     * @param scope
     * @return
     */
    protected Data getInputData(Resource resource, ContextScope scope) {
        return null;
    }

    protected abstract Data getData(Resource resource, Geometry geometry, ContextScope scope);
}
