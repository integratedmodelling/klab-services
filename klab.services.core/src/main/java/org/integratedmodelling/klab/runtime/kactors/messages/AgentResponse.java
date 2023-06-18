package org.integratedmodelling.klab.runtime.kactors.messages;

import java.io.Serializable;

import org.integratedmodelling.klab.api.authentication.scope.Scope.Status;
import org.integratedmodelling.klab.api.services.runtime.kactors.VM;
import org.integratedmodelling.klab.utils.Parameters;

public class AgentResponse implements Serializable, VM.Message {

    private static final long serialVersionUID = 7014141315769313725L;
    
    // constants for often-used data FIXME/CHECK
    public static final String RESULT = "result";
    public static final String ERROR = "error";
    
    private long id;
    private Parameters<String> data = Parameters.create();
    private Status status;

    public long getId() {
        return id;
    }

    public void setId(long id) {
        this.id = id;
    }

    public Parameters<String> getData() {
        return data;
    }

    public void setData(Parameters<String> data) {
        this.data = data;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

}
