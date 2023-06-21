package org.integratedmodelling.klab.services.actors;

import org.integratedmodelling.klab.api.authentication.scope.Scope;
import org.integratedmodelling.klab.api.lang.kactors.KActorsBehavior;
import org.integratedmodelling.klab.runtime.kactors.messages.core.ViewAction;
import org.integratedmodelling.klab.runtime.kactors.messages.core.ViewLayout;

import io.reacted.core.reactors.ReActions;
import io.reacted.core.reactorsystem.ReActorContext;

/**
 * An application holds the view state as well as the application environment.
 * The agent receives a ViewLayout message from the actor VM, then keeps it
 * updated as ViewAction messages are sent by the controller.
 * 
 * @author Ferd
 *
 */
public class ApplicationAgent extends SessionAgent {

	public ApplicationAgent(String name) {
		super(name);
	}

	public ApplicationAgent(KActorsBehavior application, Scope scope) {
		super(application.getName());
		run(application, scope);
	}

	protected ReActions.Builder setBehavior() {
		return super.setBehavior().reAct(ViewLayout.class, this::handleViewLayout).reAct(ViewAction.class,
				this::handleViewAction);
	}

	private void handleViewLayout(ReActorContext ctx, ViewLayout message) {
	}

	private void handleViewAction(ReActorContext ctx, ViewAction message) {
	}

}
