package org.integratedmodelling.klab.api.collections;

import org.integratedmodelling.klab.api.collections.impl.IdentifierImpl;

import java.io.Serializable;

/**
 * A string dressed in a class that ensures it's recognized as an identifier in the originating language.
 */
public interface Identifier extends Serializable {

    static Identifier create(String id) {
        var ret = new IdentifierImpl();
        ret.setValue(id);
        return ret;
    }

    /**
     * The string value of the identifier,
     *
     * @return
     */
    String getValue();
}
