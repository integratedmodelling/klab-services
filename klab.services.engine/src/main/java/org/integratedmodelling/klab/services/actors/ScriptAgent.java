package org.integratedmodelling.klab.services.actors;

import org.integratedmodelling.klab.api.authentication.scope.Scope;
import org.integratedmodelling.klab.utils.NameGenerator;

import io.reacted.core.messages.reactors.ReActorInit;
import io.reacted.core.reactorsystem.ReActorContext;

public class ScriptAgent extends SessionAgent {

	public ScriptAgent(String name, Scope scope) {
		super(name + "." + NameGenerator.shortUUID(), scope);
	}

	@Override
	protected void initialize(ReActorContext rctx, ReActorInit message) {
		super.initialize(rctx, message);
	}

}
