package org.integratedmodelling.klab.api.digitaltwin;

import org.integratedmodelling.klab.api.data.Storage;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.lang.Annotation;

import java.util.Collection;

/**
 * The state storage is a persistent store of state storage data for the digital twin. Each digital
 * twin creates a StateStorage instance, which should be referring to a common storage backend
 * within the runtime. According to the scope that owns the DT, the storage may persist itself on
 * close or delete its data. Specific storages may be marked as temporary (normally only
 * intermediate values kept for debugging) so that they can be deleted on context close even when
 * the storage is persistent.
 */
public interface StateStorage {

  /**
   * Get any existing storage for the passed observation as generic storage type. The storage must
   * have been created before and type adaptation is on the caller.
   *
   * @param observation
   * @return
   */
  Storage<?> getStorage(Observation observation);

  /**
   * Get any existing storage for the passed observation, return null if not existing.
   *
   * @param observation
   * @param storageClass
   * @param <T>
   * @return
   */
  <T extends Storage> T getExistingStorage(Observation observation, Class<T> storageClass);

  /**
   * Create or retrieve storage for the passed observation, using the observation's scale and ID.
   *
   * @param observation
   * @param storageClass
   * @param <T>
   * @return the storage in the specified class.
   */
  <T extends Storage> T getOrCreateStorage(Observation observation, Class<T> storageClass);

  /**
   * Promote the passing storage to the type desired. This can be very expensive, so it should be
   * only called in selected circumstances. The storage implementation should be lazy so that any
   * call to this method can be inexpensive if this is called before the first write, which should
   * be the acceptable way to call this.
   *
   * @param observation
   * @param existingStorage may be null, in which case this behaves like {@link
   *     #getOrCreateStorage(Observation, Class)}, or the same class requested, in which case it
   *     will return the same storage
   * @param storageClass
   * @param <T>
   * @return
   * @throws org.integratedmodelling.klab.api.exceptions.KlabIllegalArgumentException if the
   *     existing state is not compatible with the requested
   */
  <T extends Storage> T promoteStorage(
      Observation observation, Storage existingStorage, Class<T> storageClass);

  /** Safely delete everything in the scope we're running. */
  void clear();
}
