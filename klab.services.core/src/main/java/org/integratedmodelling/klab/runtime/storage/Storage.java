package org.integratedmodelling.klab.runtime.storage;

/**
 * Base storage providing the general methods. Children enable either boxed I/O
 * or faster native operation (recommended). The runtime makes the choice.
 * 
 * @author Ferd
 *
 */
public abstract class Storage {

	public enum Type {
		BOXING,
		DOUBLE,
		FLOAT,
		INTEGER,
		KEYED
	}
	
	public abstract Type getType();
	
}
