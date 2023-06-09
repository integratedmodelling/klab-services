package org.integratedmodelling.klab.api.community;

import org.integratedmodelling.klab.api.services.runtime.Message;

/**
 * Activities compose workflows and the global activity graph. According to the
 * activity type, history and sequence, workflows are automatically
 * instantiated, maintained and archived.
 * 
 * @author Ferd
 *
 */
public interface Activity {

	enum Type {

	}

	/**
	 * Each activity has a type. The ActivityPub established vocabulary is the
	 * reference point, with extensions.
	 * 
	 * @return
	 */
	Type getType();

	/**
	 * Payload in this message is always going to be {@link ActivityPayload}.
	 * Activities that start off as naked messages are always wrapped into the
	 * activity corresponding to the payload implementation.
	 * 
	 * @return
	 */
	Message getMessage();

}
