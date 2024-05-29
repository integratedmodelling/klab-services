package org.integratedmodelling.klab.runtime.kactors.messages;

import org.integratedmodelling.klab.api.lang.kactors.KActorsBehavior;
import org.integratedmodelling.klab.api.scope.Scope;
import org.integratedmodelling.klab.api.services.runtime.kactors.AgentMessage;

public class CreateApplication extends AgentMessage {

	private static final long serialVersionUID = -6105256439472164152L;

	private Scope scope;
	private KActorsBehavior.Type behaviorType;
	private String behaviorName;

	public KActorsBehavior.Type getBehaviorType() {
		return behaviorType;
	}

	public void setBehaviorType(KActorsBehavior.Type behaviorType) {
		this.behaviorType = behaviorType;
	}

	public CreateApplication() {
	}

	public CreateApplication(Scope scope, String behaviorName, KActorsBehavior.Type behaviorType) {
		this.scope = scope;
		this.behaviorType = behaviorType;
		this.behaviorName = behaviorName;
	}
	
	public Scope getScope() {
		return scope;
	}

	public void setScope(Scope scope) {
		this.scope = scope;
	}

	public String getBehaviorName() {
		return behaviorName;
	}

	public void setBehaviorName(String behaviorName) {
		this.behaviorName = behaviorName;
	}

}
