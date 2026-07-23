package org.integratedmodelling.klab.api.collections;

import java.io.Serializable;

import org.integratedmodelling.klab.api.collections.impl.ConstantImpl;
import org.integratedmodelling.klab.api.collections.impl.IdentifierImpl;

/**
 * A string dressed in a class that ensures it's recognized as an identifier in the originating language.
 */
public interface Constant extends Serializable {

    static Constant create(String id) {
        var ret = new ConstantImpl();
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
