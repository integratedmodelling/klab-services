package org.integratedmodelling.klab.services.actors.messages;

import java.io.Serializable;

import org.integratedmodelling.klab.utils.Parameters;
import org.integratedmodelling.klab.api.authentication.scope.Scope.Status;

public class AgentResponse implements Serializable {

    private static final long serialVersionUID = 7014141315769313725L;

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
