package org.integratedmodelling.klab.runtime.knowledge;

import org.integratedmodelling.common.runtime.DataflowImpl;
import org.integratedmodelling.klab.api.collections.Parameters;
import org.integratedmodelling.klab.api.data.KnowledgeGraph;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.services.resources.ResourceSet;
import org.integratedmodelling.klab.api.services.runtime.Actuator;
import org.integratedmodelling.klab.api.services.runtime.Dataflow;

import java.util.List;

/**
 * A server side implementation of a dataflow that uses the knowledge graph and can be adapted to a
 * serializable form for clients.
 */
public class DataflowGraph implements Dataflow {

    private final KnowledgeGraph database;
    private final ContextScope scope;

    public DataflowGraph(KnowledgeGraph database, ContextScope contextScope) {
        this.database = database;
        this.scope = contextScope;
    }

    @Override
    public boolean isEmpty() {
        return false;
    }

    @Override
    public ResourceSet getRequirements() {
        return null;
    }

    @Override
    public Geometry getCoverage() {
        return null;
    }

    @Override
    public List<Actuator> getComputation() {
        return List.of();
    }

    @Override
    public Observation getTarget() {
        return null;
    }

    public DataflowImpl adapt() {
        return null;
    }

    @Override
    public long getId() {
        return 0;
    }
}
