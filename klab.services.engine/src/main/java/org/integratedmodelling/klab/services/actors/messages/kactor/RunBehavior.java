package org.integratedmodelling.klab.services.actors.messages.kactor;

import org.integratedmodelling.klab.api.authentication.scope.Scope;
import org.integratedmodelling.klab.runtime.kactors.messages.AgentMessage;

public class RunBehavior extends AgentMessage {

	private static final long serialVersionUID = 5539640073416217055L;

	private String behavior;
	private Scope scope;

	public String getBehavior() {
		return behavior;
	}

	public void setBehavior(String behavior) {
		this.behavior = behavior;
	}

	public Scope getScope() {
		return scope;
	}

	public void setScope(Scope scope) {
		this.scope = scope;
	}

}
