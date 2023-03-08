package org.integratedmodelling.klab.api.lang.kim;

import java.util.List;

import org.integratedmodelling.klab.api.lang.Contextualizable;
import org.integratedmodelling.klab.api.lang.ActionTrigger;

public interface KimAction extends KimStatement {

	enum Type {
		SET, INTEGRATE, DO, MOVE, DESTROY
	};

	/**
	 * The type of action.
	 * 
	 * @return action type
	 */
	Type getType();

	/**
	 * The trigger for the action.
	 * 
	 * @return the trigger
	 */
	ActionTrigger getTrigger();

	/**
	 * If the action has a target state, return its formal name in the model.
	 * 
	 * @return state targeted
	 */
	String getTargetStateId();

	/**
	 * The types of events that trigger the action, or an empty collection if
	 * trigger is not Trigger.EVENT.
	 * 
	 * @return any triggering event concept
	 */
	List<KimConcept> getTriggeringEvents();

	/**
	 * The actual computables for this action.
	 * 
	 * @return the computables
	 */
	List<Contextualizable> getComputation();

}
