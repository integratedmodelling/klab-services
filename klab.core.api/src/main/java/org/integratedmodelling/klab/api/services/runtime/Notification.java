package org.integratedmodelling.klab.api.services.runtime;

import org.integratedmodelling.klab.api.services.runtime.impl.NotificationImpl;

public interface Notification {

	/**
	 * Additional classification info. Can be used for display or other purposes.
	 * Will be filled as things progress.
	 * 
	 * @author ferdinando.villa
	 *
	 */
	public enum Type {
		None, Success, Failure
	}

	public enum Mode {
		Silent, Normal, Verbose
	}
	
	public enum Level {
	    Debug, Info, Warning, Error, SystemError
	}

	/**
	 * The notifying identity
	 * 
	 * @return
	 */
	String getIdentity();

	/**
	 * This will be the string representation of the silly Java level, which was
	 * born before enums existed.
	 * 
	 * @return
	 */
	Level getLevel();

	/**
	 * System time of notification
	 * 
	 * @return
	 */
	long getTimestamp();

	String getMessage();

	Type getType();
	
	public static Notification of(String message, Level level) {
	    return new NotificationImpl(message, level);
	}
}