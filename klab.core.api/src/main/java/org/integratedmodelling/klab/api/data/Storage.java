package org.integratedmodelling.klab.api.data;

import java.io.Serializable;
import java.util.List;
import java.util.PrimitiveIterator;
import org.integratedmodelling.klab.api.digitaltwin.Scheduler;
import org.integratedmodelling.klab.api.geometry.Geometry;

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

  enum Type {
    DOUBLE(true),
    FLOAT(true),
    INTEGER(true),
    LONG(true),
    KEYED(false),
    BOOLEAN(false);

    private boolean number;

    Type(boolean number) {
      this.number = number;
    }

    public boolean isNumber() {
      return number;
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
   * New shard API to substitute Storage.Buffer. Shards are just storage and may be implemented in
   * different ways by the adopted {@link
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
     * Each shard should have a histogram built upon filling or upon demand, whichever is faster.
     * Histogram implementation must allow merging so that the contextualizers can access aggregated
     * observation information quickly by binding the histogram to the contextualizing functions.
     *
     * @return
     */
    Histogram getHistogram();

    Scanner getNativeScanner();
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
   *     ultimately affect the native shards according to the original geometry.
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
}
