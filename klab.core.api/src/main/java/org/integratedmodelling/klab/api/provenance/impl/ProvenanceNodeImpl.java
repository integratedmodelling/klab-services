package org.integratedmodelling.klab.api.provenance.impl;

import org.integratedmodelling.klab.api.provenance.Provenance;

public class ProvenanceNodeImpl implements Provenance.Node {

    private String name;
    private boolean empty;

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
}
