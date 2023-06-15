package org.integratedmodelling.klab.runtime.kactors;

public class Semaphore {

	public enum Type {
		/**
		 * if a LOAD semaphore is in a Load message, wait for the semaphore to go green
		 * before moving on.
		 */
		LOAD,

		/**
		 * if a FIRE semaphore is in a KActorsCall message, wait for the action to fire
		 * before moving on to the next instruction.
		 */
		FIRE
	}

	long id;
	Type type;
	boolean warned;

	public Semaphore(Type type, long id) {
		this.type = type;
		this.id = id;
	}

	public Type getType() {
		return type;
	}

	public long getId() {
		return id;
	}

	public String toString() {
		return type + "-" + id;
	}

	public void setWarned() {
		this.warned = true;
	}

	public boolean isWarned() {
		return warned;
	}

	public static boolean expired(Semaphore semaphore) {
		// TODO Auto-generated method stub
		return false;
	}

	public static Semaphore create(Type fire) {
		// TODO Auto-generated method stub
		return null;
	}

}