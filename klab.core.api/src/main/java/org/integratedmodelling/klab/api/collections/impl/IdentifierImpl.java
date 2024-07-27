package org.integratedmodelling.klab.api.collections.impl;

import org.integratedmodelling.klab.api.collections.Identifier;

public class IdentifierImpl implements Identifier {

    String value;

    @Override
    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

}
