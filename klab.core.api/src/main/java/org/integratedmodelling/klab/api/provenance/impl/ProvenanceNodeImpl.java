package org.integratedmodelling.klab.api.provenance.impl;

import org.integratedmodelling.klab.api.Klab;
import org.integratedmodelling.klab.api.data.Metadata;
import org.integratedmodelling.klab.api.provenance.Provenance;

public abstract class ProvenanceNodeImpl implements Provenance.Node {

    private long id;
    private String name;
    private boolean empty;
    private Metadata metadata = Metadata.create();
    private long transientId = Klab.getNextId();

    @Override
    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    @Override
    public boolean isEmpty() {
        return empty;
    }

    public void setEmpty(boolean empty) {
        this.empty = empty;
    }

    @Override
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    @Override
    public long getTransientId() {
        return transientId;
    }

    public void setTransientId(long transientId) {
        this.transientId = transientId;
    }

    @Override
    public Metadata getMetadata() {
        return metadata;
    }

    public void setMetadata(Metadata metadata) {
        this.metadata = metadata;
    }
}
