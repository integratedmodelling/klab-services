package org.integratedmodelling.klab.api.data;

import java.io.Serializable;
import java.util.List;
import java.util.Map;
import java.util.PrimitiveIterator;

import org.integratedmodelling.klab.api.data.mediation.classification.DataKey;
import org.integratedmodelling.klab.api.digitaltwin.Scheduler;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.knowledge.Artifact;
import org.integratedmodelling.klab.api.scope.ServiceScope;

/**
 * Base storage providing only general methods. There is one Storage object per observation, managed
 * by one {@link org.integratedmodelling.klab.api.digitaltwin.StorageManager} per {@link
 * org.integratedmodelling.klab.api.digitaltwin.DigitalTwin}
 *
 * <p>The interface is implemented by classes specialized for a particular type of data, enabling
 * faster, non-boxing native operation. For this reason there are no set/get methods in the base
 * {@link Shard} interface used for I/O. The runtime makes the choice based on the API of the
 * contextualizers or annotations added to models or concepts.
 *
 * @author Ferd
 */
public interface Storage {

  //        if (array instanceof BufferArray.Float32) return 4;
  //    if (array instanceof BufferArray.Float64) return 8;
  //    if (array instanceof BufferArray.Int32) return 4;
  //    if (array instanceof BufferArray.Int64) return 8;
  //    if (array instanceof BufferArray.Int16) return 2;
  //    if (array instanceof BufferArray.Int8) return 1;
  //    throw new IllegalArgumentException("Unsupported buffer type: " + array.getClass());
  //
  enum Type {
    DOUBLE(true, 8),
    FLOAT(true, 4),
    INTEGER(true, 4),
    LONG(true, 8),
    KEYED(false, 4),
    BOOLEAN(false, 1);

    private boolean number;
    private int size;

    Type(boolean number, int size) {
      this.number = number;
      this.size = size;
    }

    public boolean isNumber() {
      return number;
    }

    public int size() {
      return size;
    }

    public static Type defaultFor(Artifact.Type artifactType) {
      if (artifactType == null) {
        return null;
      }
      return switch (artifactType) {
        case NUMBER -> DOUBLE;
        case BOOLEAN -> BOOLEAN;
        case CONCEPT -> KEYED;
        default -> null;
      };
    }
  }

  /**
   * The filler is used to fill the buffer with values. The core interface extends a primitive long
   * iterator (of which only hasNext() may be used) and does not expose the three most important
   * functions: get() for read scanners, add() for write scanners, and peek() to check the current
   * value. It must be used sequentially as an iterator, and only the peek() function can be called
   * without advancing the iteration.
   *
   * <p>TODO revise if eventually Java supports primitive var types and this can be done right
   *
   * <p>Iteration proceeds along the geometry and the fill curve of the originating Buffer, which
   * can be retrieved in a read-only wrapper if needed.
   */
  interface Scanner extends PrimitiveIterator.OfLong {

    /**
     * Read-only view of the shard, used if the geometry or histogram needs to be accessed. These
     * can also be bound to contextualizer arguments for the observation being contextualized (but
     * not necessarily for any input observations).
     *
     * @return
     */
    Shard shard();

    /**
     * Total size of the buffer we represent.
     *
     * @return
     */
    long size();
  }

  interface DoubleScanner extends Scanner {
    double get();

    double peek();

    void add(double value);
  }

  interface IntScanner extends Scanner {
    int get();

    int peek();

    void add(int value);
  }

  interface LongScanner extends Scanner {
    long get();

    long peek();

    void add(long value);
  }

  interface FloatScanner extends Scanner {
    float get();

    float peek();

    void add(float value);
  }

  interface BooleanScanner extends Scanner {
    boolean get();

    boolean peek();

    void add(boolean value);
  }

  interface KeyScanner<T extends Serializable> extends Scanner {
    T get();

    T peek();

    void add(T value);
  }

  /**
   * Shards are just storage and may be implemented in different ways by the adopted {@link
   * org.integratedmodelling.klab.api.digitaltwin.StorageManager}. They are not normally used
   * directly at the API level; interaction happens through scanners retrieved from Storage#scan(),
   * which can adapt the shards to any compatible choice of parallelism and fill curve. The scanners
   * are bound to contextualizer parameters to enable data access.
   */
  interface Shard extends RuntimeAsset {

    /**
     * Some "tile"of the observation geometry. No shard can ever overlap another's geometry, and the
     * shard union is the observation's geometry. Shape constraints are not passed down to grid
     * shards.
     *
     * @return
     */
    Geometry getGeometry();

    /**
     * @return
     */
    Data.ShardingStrategy getShardingStrategy();

    /**
     * @return
     */
    int getShardIndex();

    /**
     * Total number of shards in the storage.
     *
     * @return
     */
    int getShardCount();

    /**
     * Each shard should have a histogram built upon filling or upon demand, whichever is faster.
     * Histogram implementation must allow merging so that the contextualizers can access aggregated
     * observation information quickly by binding the histogram to the contextualizing functions.
     *
     * @return
     */
    Histogram getHistogram();

    /**
     * Native type of the storage associated to the shard.
     *
     * @return
     */
    Storage.Type getNativeType();

    /**
     * The timestamp is the primary key for the (list of) shards in the storage.
     *
     * @return
     */
    long getTimestamp();

    /**
     * The URN is unique of the shard and must identify its storage on disk or in a database.
     *
     * @return
     */
    String getUrn();
  }

  /**
   * Return the native buffers for the observation we represent correspondent to the sharding
   * configuration set in the observation and the scheduler event that determined their computation.
   * This is called by the scheduler to update the knowledge graph within the transaction that
   * executed a successful contextualization.
   *
   * @param event
   * @return a list of buffers, possibly empty.
   */
  List<Shard> getNativeShards(Scheduler.Event event);

  Scanner getNativeScanner(Shard shard);

  /**
   * Create or retrieve scanners for the observation we represent, honoring any requests in terms of
   * splits and fill curve. If the buffers do not exist natively for the observation, they will be
   * created (with the current logic they should always exist, as the scheduler builds them before
   * contextualization). If the sharding strategy is different from the native, the scanners will be
   * mappers for the original ones. The overall constraint is the original observation geometry,
   * which all buffers must ultimately cover exactly, and its dimensionality, which must be
   * preserved at all times.
   *
   * @param locator the event that sets the boundaries for the buffer computation within the
   *     observation geometry. It must result in at most one varying dimension, normally space.
   * @param request the specifications for the buffer geometry, fill curve and split logic.`
   * @param scannerClass the scanner class to instantiate for the returned buffers.
   * @param readOnly if true, the returned scanners will be read-only and throw an exception on
   *     add().
   * @return a list of new or existing scanners, possibly wrapped in mediating scanners that
   *     ultimately affect the native shards according to the original geometry. Compatible
   *     floating-point type mediation must use primitive, write-through scanners: implementations
   *     must not box individual values or allocate a converted copy of the buffer.
   */
  <T extends Scanner> List<T> scan(
      Scheduler.Event locator,
      Data.ShardingStrategy request,
      Class<T> scannerClass,
      boolean readOnly);

  /**
   * The native sharding strategy for the observation we represent.
   *
   * @return
   */
  Data.ShardingStrategy getNativeShardingStrategy();

  /**
   * This will be known after the first buffer is created.
   *
   * @return
   */
  Type getNativeType();

  /**
   * The merged histogram built on demand by merging that of all existing shards.
   *
   * @return
   */
  Histogram getHistogram();

  /**
   * Return independently merged histogram snapshots for every temporal slice. Multiple spatial
   * shards with the same timestamp are merged into one entry.
   *
   * @return timestamp-keyed fixed histograms, or an empty map
   */
  default Map<Long, Histogram> getHistograms() {
    return Map.of();
  }

  /**
   * If there is a data key, return it. This must collect all the mappings from all shards and be
   * kept up to date.
   *
   * @return the data key, or null.
   */
  DataKey getKey();

  /**
   * Called after each shard's successful run to update internal indices and, if required, enqueue
   * low-priority threads to persist a shard's storage.
   *
   * @param scanner
   */
  void finalizeRun(Scanner scanner);

  /**
   * Wait for any pending maintenance required to make the current shard state durable. Runtime
   * implementations should call this before committing shard descriptors to the knowledge graph.
   */
  default void flush() {}

  /**
   * Admin-only: destroy any trace of storage and leave
   *
   * @param serviceScope
   */
  void close(ServiceScope serviceScope);
}
