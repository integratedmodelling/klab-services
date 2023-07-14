package org.integratedmodelling.klab.services.actors.messages.kactor;

import java.net.URL;

import org.integratedmodelling.klab.runtime.kactors.messages.AgentMessage;

/**
 * Just a message to run a behavior, normally one that has been already
 * configured into the agent along with the scope, although the VM is threadsafe
 * so this can be sent for different behaviors as well.
 * 
 * @author Ferd
 *
 */
public class RunBehavior extends AgentMessage {

	private static final long serialVersionUID = 5539640073416217055L;

	private String behavior;
	private URL behaviorUrl;
	
	public RunBehavior() {
	}

	public RunBehavior(String behavior) {
		this.behavior = behavior;
	}

	public String getBehavior() {
		return behavior;
	}

	public void setBehavior(String behavior) {
		this.behavior = behavior;
	}

	public URL getBehaviorUrl() {
		return behaviorUrl;
	}

	public void setBehaviorUrl(URL behaviorUrl) {
		this.behaviorUrl = behaviorUrl;
	}

}
