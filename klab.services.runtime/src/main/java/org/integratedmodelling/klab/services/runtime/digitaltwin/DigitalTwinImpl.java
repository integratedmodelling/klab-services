package org.integratedmodelling.klab.services.runtime.digitaltwin;

import org.integratedmodelling.klab.api.data.KnowledgeGraph;
import org.integratedmodelling.klab.api.digitaltwin.DigitalTwin;
import org.integratedmodelling.klab.api.digitaltwin.StateStorage;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.provenance.Provenance;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.services.RuntimeService;
import org.integratedmodelling.klab.api.services.runtime.Dataflow;
import org.integratedmodelling.klab.runtime.knowledge.DataflowGraph;
import org.integratedmodelling.klab.runtime.knowledge.ProvenanceGraph;
import org.integratedmodelling.klab.runtime.storage.StateStorageImpl;

public class DigitalTwinImpl implements DigitalTwin {

    KnowledgeGraph knowledgeGraph;
    StateStorage stateStorage;
    ContextScope rootScope;

    public DigitalTwinImpl(RuntimeService service, ContextScope scope, KnowledgeGraph database) {
        this.rootScope = scope;
        this.knowledgeGraph = database.contextualize(scope);
        this.stateStorage = new StateStorageImpl(service, scope);
    }

    @Override
    public KnowledgeGraph knowledgeGraph() {
        return this.knowledgeGraph;
    }

    @Override
    public StateStorage stateStorage() {
        return this.stateStorage;
    }

    @Override
    public Provenance getProvenanceGraph(ContextScope context) {
        return new ProvenanceGraph(this.knowledgeGraph, this.rootScope);
    }

    @Override
    public Dataflow<Observation> getDataflowGraph(ContextScope context) {
        return new DataflowGraph(this.knowledgeGraph, this.rootScope);
    }

    @Override
    public void dispose() {
        // TODO. Persistence depends on the database passed at initialization.
    }

}
