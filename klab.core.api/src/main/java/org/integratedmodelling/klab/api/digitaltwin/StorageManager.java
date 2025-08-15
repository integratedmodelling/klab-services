package org.integratedmodelling.klab.api.digitaltwin;

import org.integratedmodelling.klab.api.data.Storage;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;

/**
 * The state storage is a persistent store of state storage data for the digital twin. Each digital
 * twin creates a StateStorage instance, which should be referring to a common storage backend
 * within the runtime. According to the scope that owns the DT, the storage may persist itself on
 * close or delete its data. Specific storages may be marked as temporary (normally only
 * intermediate values kept for debugging) so that they can be deleted on context close even when
 * the storage is persistent.
 *
 * <p>TODO rename to something more idiomatic (e.g. manager) - StateStorage vs. Storage is confusing
 */
public interface StorageManager {

  /**
   * Create or retrieve storage for the passed observation, using the observation's scale and ID. If
   * creating, honor any constraints and configuration from the runtime config, lexical scope,
   * adapters and/or contextualizers re: fill curve, splits and type, held in {@link
   * Observation#getSharding()}
   *
   * <p>If the storage has already been created at the time of the call, the annotation will be
   * ignored; the resulting shards will be remapped when they are accessed.
   *
   * @param observation
   * @return the storage in the specified class.
   */
  Storage getStorage(Observation observation);

  /**
   * Safely delete everything that has been stored in the scope we're running. Nothing should be
   * done in the scope after this is called.
   */
  void clear();
}
