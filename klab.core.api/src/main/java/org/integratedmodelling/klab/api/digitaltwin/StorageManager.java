package org.integratedmodelling.klab.api.digitaltwin;

import org.integratedmodelling.klab.api.data.Data;
import org.integratedmodelling.klab.api.data.Storage;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;

/**
 * The state storage is a persistent store of state storage data for the digital twin. Each digital
 * twin creates a StateStorage instance, which should be referring to a common storage backend
 * within the runtime. According to the scope that owns the DT, the storage may persist itself on
 * close or delete its data. Specific storages may be marked as temporary (normally only
 * intermediate values kept for debugging) so that they can be deleted on context close even when
 * the storage is persistent.
 */
public interface StorageManager {

  /**
   * Get the storage for an existing observation. If the storage is not yet created, this must throw
   * a KlabIllegalStateException.
   *
   * @param observation
   * @throws org.integratedmodelling.klab.api.exceptions.KlabIllegalStateException if the storage
   *     was not created before the call
   * @return the storage for the passed observation
   */
  Storage getStorage(Observation observation);

  /**
   * Create storage for the passed observation, using the observation's scale and ID and honoring
   * the passed sharding strategy.
   *
   * @param observation
   * @param shardingStrategy
   * @return the newly created storage
   */
  Storage createStorage(Observation observation, Data.ShardingStrategy shardingStrategy);

  /**
   * Get a temporary scanner for a transparently managed chunk of storage of the passed size and
   * scanner class. A mock-up {@link org.integratedmodelling.klab.api.data.Storage.Shard} is
   * connected to it so that the API remains consistent and the geometry is accessible. The storage
   * linked to the scanner is deleted after the scanner goes out of scope or the service shuts down,
   * so this should be used as a heap variable, within the scope of a single method.
   *
   * @param geometry
   * @param scannerClass
   * @return
   * @param <T>
   */
  <T extends Storage.Scanner> T getTemporaryScanner(Geometry geometry, Class<T> scannerClass);

  /**
   * Safely delete everything that has been stored in the scope we're running. Nothing should be
   * done in the scope after this is called.
   */
  void clear();
}
