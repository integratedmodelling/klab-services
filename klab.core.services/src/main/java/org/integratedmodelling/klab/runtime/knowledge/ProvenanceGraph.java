package org.integratedmodelling.klab.runtime.knowledge;

import org.integratedmodelling.klab.api.data.KnowledgeGraph;
import org.integratedmodelling.klab.api.knowledge.Artifact;
import org.integratedmodelling.klab.api.provenance.Activity;
import org.integratedmodelling.klab.api.provenance.Agent;
import org.integratedmodelling.klab.api.provenance.Provenance;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Iterator;
import java.util.List;

public class ProvenanceGraph implements Provenance {

    private final KnowledgeGraph database;
    private final ContextScope scope;

    public ProvenanceGraph(KnowledgeGraph database, ContextScope contextScope) {
        this.database = database;
        this.scope = contextScope;
    }


    @Override
    public boolean isEmpty() {
        return false;
    }

    @Override
    public List<Activity> getPrimaryActions() {
        return List.of();
    }

    @Override
    public Collection<Artifact> getArtifacts() {
        return List.of();
    }

    @Override
    public Activity getCause(Node node) {
        return null;
    }

    @Override
    public Agent getAgent(Node node) {
        return null;
    }

    @Override
    public <T> Collection<T> collect(Class<? extends T> cls) {
        return List.of();
    }

    @Override
    public Iterator<Activity> iterator() {
        return null;
    }

    @Override
    public long getId() {
        return 0;
    }
}
