package org.integratedmodelling.klab.services.community.model;

import org.integratedmodelling.klab.api.community.Activity;
import org.integratedmodelling.klab.api.services.runtime.Message;

public class ActivityImpl implements Activity {

	private Message message;
	private Type type;

	public void setType(Type type) {
		this.type = type;
	}

	public Message getMessage() {
		return message;
	}

	public void setMessage(Message message) {
		this.message = message;
	}

	@Override
	public Type getType() {
		return this.type;
	}

}
