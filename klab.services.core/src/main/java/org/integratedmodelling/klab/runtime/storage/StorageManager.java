package org.integratedmodelling.klab.runtime.storage;

import org.integratedmodelling.klab.api.authentication.scope.ContextScope;
import org.integratedmodelling.klab.api.knowledge.observation.State;
import org.ojalgo.concurrent.Parallelism;

/**
 * Storage service. In k.LAB 12+ this is invisible at the API level; storage
 * underlies the states but it's retrieved through adapters, and can be used in
 * lieu of the generic boxing API in {@link State}. Choice of storage back-end
 * is, by default, made intelligently according to scale size, available RAM,
 * disk space, disk transfer rates, processors and CPU load.
 * 
 * @author Ferd
 *
 */
public enum StorageManager {

	INSTANCE;

	/**
	 * Return a boxing storage for the requested geometry, semantics, expected
	 * parallelism, and JVM environment. Use only if there is no way to predict the
	 * type of the stored data.
	 * 
	 * TODO check if file-mapped storage works in parallel and if so, parallelism
	 * isn't necessary.
	 * 
	 * @param state
	 * @param scope       the scale we must go over. This may have the effect of
	 * @param parallelism only advisory
	 * @return
	 */
	public Storage getStorage(State state, ContextScope scope, Parallelism parallelism) {
		return null;
	}

	/**
	 * Return a boxing storage for the requested geometry, semantics, expected
	 * parallelism, and JVM environment. Use only if there is no way to predict the
	 * type of the stored data.
	 * 
	 * TODO check if file-mapped storage works in parallel and if so, parallelism
	 * isn't necessary.
	 * 
	 * @param state
	 * @param scope       the scale we must go over. This may have the effect of
	 * @param parallelism only advisory
	 * @return
	 */
	public DoubleStorage getDoubleStorage(State state, ContextScope scope, Parallelism parallelism) {
		return null;
	}
	
	/**
	 * Return a boxing storage for the requested geometry, semantics, expected
	 * parallelism, and JVM environment. Use only if there is no way to predict the
	 * type of the stored data.
	 * 
	 * TODO check if file-mapped storage works in parallel and if so, parallelism
	 * isn't necessary.
	 * 
	 * @param state
	 * @param scope       the scale we must go over. This may have the effect of
	 * @param parallelism only advisory
	 * @return
	 */
	public IntStorage getIntStorage(State state, ContextScope scope, Parallelism parallelism) {
		return null;
	}
	
	/**
	 * Return a boxing storage for the requested geometry, semantics, expected
	 * parallelism, and JVM environment. Use only if there is no way to predict the
	 * type of the stored data.
	 * 
	 * TODO check if file-mapped storage works in parallel and if so, parallelism
	 * isn't necessary.
	 * 
	 * @param state
	 * @param scope       the scale we must go over. This may have the effect of
	 * @param parallelism only advisory
	 * @return
	 */
	public KeyedStorage getKeyedStorage(State state, ContextScope scope, Parallelism parallelism) {
		return null;
	}
	
	/**
	 * Return a boxing storage for the requested geometry, semantics, expected
	 * parallelism, and JVM environment. Use only if there is no way to predict the
	 * type of the stored data.
	 * 
	 * TODO check if file-mapped storage works in parallel and if so, parallelism
	 * isn't necessary.
	 * 
	 * @param state
	 * @param scope       the scale we must go over. This may have the effect of
	 * @param parallelism only advisory
	 * @return
	 */
	public FloatStorage getFloatStorage(State state, ContextScope scope, Parallelism parallelism) {
		return null;
	}
}
