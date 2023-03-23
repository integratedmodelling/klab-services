package org.integratedmodelling.klab.services.community.model;

import org.integratedmodelling.klab.api.community.Activity;
import org.integratedmodelling.klab.api.services.runtime.Message;

public class ActivityImpl implements Activity {

    private Message message;

    public Message getMessage() {
        return message;
    }

    public void setMessage(Message message) {
        this.message = message;
    }
    
}
