package org.integratedmodelling.klab.runtime.storage;

import org.integratedmodelling.klab.api.data.Storage;
import org.integratedmodelling.klab.api.knowledge.observation.State;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.ojalgo.array.BufferArray;
import org.ojalgo.concurrent.Parallelism;

/**
 * Storage service. In k.LAB 12+ this is invisible at the API level; storage underlies the states but it's retrieved
 * through adapters, and can be used in lieu of the generic boxing API in {@link State}. Choice of storage back-end is,
 * by default, made intelligently according to scale size, available RAM, disk space, disk transfer rates, processors
 * and CPU load.
 *
 * TODO this should be context-wide and manage its storage with the lifetime of the scope
 *
 * @author Ferd
 */
public enum StorageManager {

    INSTANCE;

    private static final String STORAGE_SCOPE_KEY = "klab.storage.scope";

    private StorageScope getStorageScope(ContextScope scope) {
        var ret = scope.getData().get(STORAGE_SCOPE_KEY, StorageScope.class);
        if (ret == null) {
            ret = new StorageScope(scope);
            scope.getData().put(STORAGE_SCOPE_KEY, ret);
        }
        return ret;
    }

    /**
     * Return a boxing storage for the requested geometry, semantics, expected parallelism, and JVM environment. Use
     * only if there is no way to predict the type of the stored data.
     * <p>
     * TODO check if file-mapped storage works in parallel and if so, parallelism
     * isn't necessary.
     *
     * @param scope       provides scale and runtime environment
     * @param parallelism only advisory
     * @return
     */
    public Storage getStorage(ContextScope scope, Parallelism parallelism) {
        return null;
    }

    /**
     * Return a native double storage for the requested geometry, semantics, expected parallelism, and JVM environment.
     * While the most expensive in terms of space, this is likely to be the fastest storage possible due to emphasis on
     * doubles in the underlying libraries.
     * <p>
     * TODO check if file-mapped storage works in parallel and if so, parallelism
     * isn't necessary.
     *
     * @param scope       provides scale and runtime environment
     * @param parallelism only advisory
     * @return
     */
    public DoubleStorage getDoubleStorage(ContextScope scope, Parallelism parallelism) {
        return new DoubleStorage(scope.getScale(), getStorageScope(scope));
    }

    /**
     * Return a native integer storage for the requested geometry, semantics, expected parallelism, and JVM
     * environment.
     * <p>
     * TODO check if file-mapped storage works in parallel and if so, parallelism
     * isn't necessary.
     *
     * @param scope       provides scale and runtime environment
     * @param parallelism only advisory
     * @return
     */
    public IntStorage getIntStorage(ContextScope scope, Parallelism parallelism) {
        return new IntStorage(scope.getScale(), getStorageScope(scope));
    }

    /**
     * Return a key/value storage based on native integers and a fast lookup table.
     * <p>
     * TODO check if file-mapped storage works in parallel and if so, parallelism
     * isn't necessary.
     *
     * @param scope       provides scale and runtime environment
     * @param parallelism only advisory
     * @return
     */
    public KeyedStorage getKeyedStorage(ContextScope scope, Parallelism parallelism) {
        return new KeyedStorage(scope.getScale(), getStorageScope(scope));
    }

    public BooleanStorage getBooleanStorage(ContextScope scope, Parallelism parallelism) {
        return new BooleanStorage(scope.getScale(), getStorageScope(scope));
    }

    /**
     * Return a native float storage for the requested geometry, semantics, expected parallelism, and JVM environment.
     * <p>
     * TODO check if file-mapped storage works in parallel and if so, parallelism
     * isn't necessary.
     *
     * @param scope       provides scale and runtime environment
     * @param parallelism only advisory
     * @return
     */
    public FloatStorage getFloatStorage(ContextScope scope, Parallelism parallelism) {
        return null;
    }

    public void freeStorage(ContextScope scope) {
        var storageScope = scope.getData().get(STORAGE_SCOPE_KEY, StorageScope.class);
        if (storageScope != null) {
            storageScope.close();
        }
    }
}
