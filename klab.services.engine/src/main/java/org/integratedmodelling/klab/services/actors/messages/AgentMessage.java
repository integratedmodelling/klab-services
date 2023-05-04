package org.integratedmodelling.klab.services.actors.messages;

import java.io.Serializable;

import org.integratedmodelling.klab.api.authentication.scope.SessionScope.Status;

/**
 * Superclass for messages to/from actor. The general pattern is that whenever an action has
 * finished with a status, the actor will send the same message that has triggered it after
 * completing it with a status and any other needed information so that the scope can be updated at
 * the receiving end.
 * 
 * @author Ferd
 *
 */
public abstract class AgentMessage implements Serializable {

    private static final long serialVersionUID = 721530303478254820L;

    private Status status = Status.STARTED;

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }
    
}
