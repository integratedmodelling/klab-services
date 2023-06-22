package org.integratedmodelling.klab.services.actors;

import org.integratedmodelling.klab.api.authentication.scope.Scope;
import org.integratedmodelling.klab.api.lang.kactors.KActorsBehavior;

import io.reacted.core.messages.reactors.ReActorInit;
import io.reacted.core.reactorsystem.ReActorContext;

public class ScriptAgent extends SessionAgent {

	private KActorsBehavior application;
	private Scope scope;

	public ScriptAgent(String name) {
		super(name);
	}

	public ScriptAgent(KActorsBehavior application, Scope scope) {
		super(application.getName());
		this.application = application;
		this.scope = scope;
	}

	@Override
	protected void initialize(ReActorContext rctx, ReActorInit message) {
		super.initialize(rctx, message);
		run(application, scope);
	}

}
