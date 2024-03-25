package org.integratedmodelling.klab.api.services.runtime.kactors;

import org.integratedmodelling.klab.api.collections.Parameters;
import org.integratedmodelling.klab.api.scope.Scope.Status;
import org.integratedmodelling.klab.api.services.runtime.kactors.VM;

import java.io.Serializable;

/**
 * Message sent back from an agent to the calling scope to communicate action status. For messages that can be
 * repeated, call {@link #setRemoveHandler(boolean)} before sending back the response, otherwise the calling
 * code will remove the response handler.
 */
public class AgentResponse implements Serializable, VM.AgentMessage {

    private static final long serialVersionUID = 7014141315769313725L;

    // constants for often-used data FIXME/CHECK
    public static final String RESULT = "result";
    public static final String ERROR = "error";

    private long id;
    private Parameters<String> data = Parameters.create();
    private Status status;

    public boolean isRemoveHandler() {
        return removeHandler;
    }

    public void setRemoveHandler(boolean removeHandler) {
        this.removeHandler = removeHandler;
    }

    /*
    default behavior for responses is to remove the response handler after the first call.
     */
    private boolean removeHandler = true;

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
