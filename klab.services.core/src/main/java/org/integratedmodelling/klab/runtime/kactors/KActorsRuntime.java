package org.integratedmodelling.klab.runtime.kactors;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.integratedmodelling.klab.api.collections.Pair;
import org.integratedmodelling.klab.api.collections.Parameters;
import org.integratedmodelling.klab.api.services.runtime.kactors.ActionExecutor;

/**
 * The runtime singleton providing configuration and access to the global
 * k.Actors runtime assets.
 * 
 * @author Ferd
 *
 */
public enum KActorsRuntime {

	INSTANCE;

	private Map<String, Pair<String, Class<? extends ActionExecutor>>> actionClasses = Collections
			.synchronizedMap(new HashMap<>());

	public Class<? extends ActionExecutor> getActionClass(String id) {
		Pair<String, Class<? extends ActionExecutor>> ret = actionClasses.get(id);
		return ret == null ? null : ret.getSecond();
	}

	/**
	 * Create and return an action from one of the system behaviors. If the action
	 * holds state, this should be created ex novo and configured with the passed
	 * data.
	 * 
	 * @param behavior
	 * @param id
	 * @param sender
	 * @param arguments
	 * @param messageId
	 * @param scope
	 * @return
	 */
	public ActionExecutor getSystemAction(String id, Parameters<String> arguments, KActorsScope scope) {

		Pair<String, Class<? extends ActionExecutor>> cls = actionClasses.get(id);
		if (cls != null) {
			try {
//				Constructor<? extends ActionExecutor> constructor = cls.getSecond().getConstructor(
//						IActorIdentity.class, IParameters.class, IKActorsBehavior.Scope.class, ActorRef.class,
//						String.class);
//				ActionExecutor ret = constructor.newInstance(identity, arguments, scope, sender, callId);
//				ret.notifyDefinition(this.actionDefinitions.get(cls.getSecond()));
//				return ret;
			} catch (Throwable e) {
				scope.error("Error while creating action " + id + ": " + e.getMessage());
			}
		}
		return null;
	}
}
