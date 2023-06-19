package org.integratedmodelling.klab.api.services.runtime.kactors;

import java.io.Serializable;

import org.integratedmodelling.klab.api.authentication.scope.Scope;
import org.integratedmodelling.klab.api.collections.Parameters;
import org.integratedmodelling.klab.api.lang.kactors.KActorsBehavior;
import org.integratedmodelling.klab.api.services.runtime.Channel;

/**
 * The k.Actors virtual machine. For now just a simple interface as a place to
 * put a runtime scope, needed by extensions.
 * 
 * @author mario
 *
 */
public interface VM {

	/**
	 * The scope for a behavior is a specialized Channel whose
	 * {@link #post(java.util.function.Consumer, Object...)} and
	 * {@link #send(Object...)} methods will direct any message that implements
	 * {@link AgentMessage} to the underlying actor, if the scope of execution
	 * implies one.
	 * 
	 * @author Ferd
	 *
	 */
	interface BehaviorScope extends Channel {

	}

	/**
	 * Generic agent message is just a serializable tag interface. Used to recognize
	 * and dispatch messages to agents when the scope's send() and post() are called
	 * with these as parameters.
	 * 
	 * @author Ferd
	 *
	 */
	interface AgentMessage extends Serializable {

	}

	/**
	 * Run the passed behavior with the passed parameters in the passed scope.
	 * 
	 * @param behavior
	 * @param arguments
	 * @param scope
	 */
	void run(KActorsBehavior behavior, Parameters<String> arguments, Scope scope);

}
