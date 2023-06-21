package org.integratedmodelling.klab.services.actors;

import org.integratedmodelling.klab.api.authentication.scope.Scope;
import org.integratedmodelling.klab.api.lang.kactors.KActorsBehavior;
import org.integratedmodelling.klab.runtime.kactors.messages.core.ScriptEvent;

import io.reacted.core.messages.reactors.ReActorInit;
import io.reacted.core.reactors.ReActions;
import io.reacted.core.reactorsystem.ReActorContext;

public class TestCaseAgent extends SessionAgent {

	private KActorsBehavior application;
	private Scope scope;
	
	public TestCaseAgent(String name) {
		super(name);
	}

	public TestCaseAgent(KActorsBehavior application, Scope scope) {
		super(application.getName());
		this.application = application;
		this.scope = scope;
	}

	protected ReActions.Builder setBehavior() {
		return super.setBehavior().reAct(ScriptEvent.class, this::handleScriptEvent);
	}

	private void handleScriptEvent(ReActorContext rctx, ScriptEvent message) {
		System.out.println("IPERSPERMA " + message);
	}

	@Override
	protected void initialize(ReActorContext rctx, ReActorInit message) {
		super.initialize(rctx, message);
		run(application, scope);
	}
	
	
}
