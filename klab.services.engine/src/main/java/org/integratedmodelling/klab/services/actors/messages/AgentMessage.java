package org.integratedmodelling.klab.services.actors.messages;

import java.io.Serializable;

import org.integratedmodelling.klab.api.authentication.scope.Scope.Status;

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

    public AgentResponse response(Status status, Object... data) {
        AgentResponse ret = new AgentResponse();
        ret.setStatus(status);
        if (data != null) {
            for (int i = 0; i < data.length; i++) {
                ret.getData().put(data[i].toString(), data[++i]);
            }
        }
        return ret;
    }
}
