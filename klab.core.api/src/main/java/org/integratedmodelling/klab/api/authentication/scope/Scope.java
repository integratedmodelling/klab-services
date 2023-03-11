package org.integratedmodelling.klab.api.authentication.scope;

import java.io.Serializable;

import org.integratedmodelling.klab.api.collections.Parameters;
import org.integratedmodelling.klab.api.services.runtime.Channel;

public abstract interface Scope extends Channel, Serializable {

    enum Type {
        SERVICE, // service-level scope
        USER, // root-level scope
        SCRIPT, // session-level scope
        API, // session for the REST API through a client
        APPLICATION, // session for an application, including the Explorer
        SESSION, // raw session for direct use within Java code
        CONTEXT // context, on which observe() can be called
    }

    /**
     * Each scope can carry arbitrary data linked to it.
     * 
     * @return
     */
    Parameters<String> getData();

}
