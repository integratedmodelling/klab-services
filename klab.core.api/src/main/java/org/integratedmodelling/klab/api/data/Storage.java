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
 * {@link Buffer} interface used for I/O. The runtime makes the choice based on the API of the
 * contextualizers or annotations added to models or concepts.
 *
 * @author Ferd
 */
public interface Storage {

  enum Type {
    //    @Deprecated
    //    BOXING,
    DOUBLE,
    FLOAT,
    INTEGER,
    LONG,
    KEYED,
    BOOLEAN
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
     * Read-only view of the buffer, used if the geometry or the fill curve need to be accessed.
     *
     * @return
     */
    Buffer buffer();

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

  /** New buffer API to substitute Storage.Buffer. */
  interface Buffer extends RuntimeAsset {

    Geometry getGeometry();

    /**
     * The original fill curve for the stored data, or a remapped one that reinterprets it.
     *
     * @return
     */
    Data.FillCurve getFillCurve();

    /**
     * Return a scanner of the requested type for this buffer. The scanner must be used sequentially
     * in agreement with the buffer geometry and will automatically take care of remapping if it's
     * from a buffer using different type, fill curve, or buffer geometry from the native buffers.
     *
     * @param fillerClass
     * @return
     * @param <T>
     */
    <T extends Scanner> T getScanner(Class<T> fillerClass);
  }

  /**
   * Return the native buffers for the observation we represent correspondent to the scheduler event
   * that determined their computation. This is called by the scheduler to update the knowledge
   * graph within the transaction that executed a successful contextualization.
   *
   * @param event
   * @return a list of buffers, possibly empty.
   */
  List<Buffer> getNativeBuffers(Scheduler.Event event);

  /**
   * Create or retrieve buffers for the observation we represent, honoring any requests in terms of
   * splits and fill curve. If the buffers do not exist natively for the observation, they will be
   * created and stored in the knowledge graph transaction as the "native" buffers for the locating
   * geometry. If already existing and not honoring the geometry parameters, the buffers returned
   * will be remapping the original ones to the passed geometry, fill curve and splits, to
   * accommodate the configuration expected by the requesting contextualizer or adapter.
   *
   * <p>The overall constraint is the original observation geometry, which all buffers must
   * ultimately cover exactly, and its dimensionality, which must be preserved at all times.
   *
   * @param locator the event that sets the boundaries for the buffer computation within the
   *     observation geometry. It must result in at most one varying dimension, normally space.
   * @param request the specifications for the buffer geometry, fill curve and split logic.`
   * @return a list of new or existing buffers, possibly wrapped in mediating buffers.
   */
  List<Buffer> getOrCreateBuffers(Scheduler.Event locator, Data.Access request);

  /**
   * This will be known after the first buffer is created.
   *
   * @return
   */
  Type getNativeType();

  //
  //  /**
  //   * Tag interface for a buffer that can be filled according to a geometry and a filling curve.
  //   * Offset and size may cover the full storage geometry or a sub-geometry for parallel,
  // distributed
  //   * implementations. Temporal events will produce modified buffers that share the same geometry
  //   * except for the temporal extension. The Buffer subclass obtained with Buffer is a value
  // iterator
  //   * of the necessary type, with preference for non-boxing iterators.
  //   *
  //   * <p>Buffers are {@link RuntimeAsset}s because they end up in the {@link KnowledgeGraph}
  // exposed
  //   * by the {@link org.integratedmodelling.klab.api.digitaltwin.DigitalTwin}.
  //   *
  //   * <p>Specific buffer types should also implement a mapping function for map/reduce
  // operations.
  //   * @deprecated see new implementation in StorageHelper
  //   */
  //  interface Buffer extends Data.Cursor, RuntimeAsset {
  //
  //    default RuntimeAsset.Type classify() {
  //      return Type.DATA;
  //    }
  //
  //    /**
  //     * Size of the buffer. In a simple situation this will normally be equal to the size of the
  //     * fastest-changing extent of the geometry.
  //     *
  //     * @return
  //     */
  //    long size();
  //
  //    /**
  //     * Locators of the buffer relative to the storage's fill curve and the sub-geometry of the
  //     * overall storage geometry represented in it. Must agree with the number of buffers and the
  //     * size. These are linear offsets, one per dimension.
  //     *
  //     * @return
  //     */
  //    long offset();
  //
  //    String getUrn();
  //
  //    long getTimestamp();
  //  }
  //
  //  interface DoubleBuffer extends Buffer {
  //
  //    interface DoubleScanner extends PrimitiveIterator.OfLong {
  //
  //      /**
  //       * Return the value at the current offset in the iterator and advance the iteration.
  //       *
  //       * @return
  //       */
  //      double get();
  //
  //      /**
  //       * Return the value at the current offset in the iterator without advancing the iteration.
  //       *
  //       * @return
  //       */
  //      double peek();
  //
  //      /**
  //       * Set the value at the current iterable offset and advance the iteration. Do not use
  // after
  //       * get() - use peek() if the value must be known.
  //       *
  //       * @param value
  //       */
  //      void add(double value);
  //    }
  //
  //    @Override
  //    DoubleScanner scan();
  //
  //    /**
  //     * Random access value. The offset is according to the overall fill curve and
  // buffer-specific
  //     * offsets.
  //     *
  //     * @param offset
  //     */
  //    double get(long offset);
  //
  //    /**
  //     * Random value set. May be inefficient. Offset as in {@link #get(long)}.
  //     *
  //     * @param value
  //     * @param offset
  //     */
  //    void set(double value, long offset);
  //
  //    /**
  //     * Supposed to be more efficient than a loop, based on the implementation.
  //     *
  //     * @param value
  //     */
  //    void fill(double value);
  //  }
  //
  //  interface LongBuffer extends Buffer {
  //
  //    /**
  //     * Return the value at the current offset in the iterator and advance the iteration.
  //     *
  //     * @return
  //     */
  //    long get();
  //
  //    /**
  //     * Return the value at the current offset in the iterator without advancing the iteration.
  //     *
  //     * @return
  //     */
  //    long peek();
  //
  //    /**
  //     * Set the value at the current iterable offset and advance the iteration. Do not use after
  //     * get()!
  //     *
  //     * @param value
  //     */
  //    void add(long value);
  //
  //    /**
  //     * Random access value. The offset is according to the overall fill curve and
  // buffer-specific
  //     * offsets.
  //     *
  //     * @param offset
  //     */
  //    long get(long offset);
  //
  //    /**
  //     * Random value set. May be inefficient. Offset as in {@link #get(long)}.
  //     *
  //     * @param value
  //     * @param offset
  //     */
  //    void set(long value, long offset);
  //
  //    /**
  //     * Supposed to be more efficient than a loop, based on the implementation.
  //     *
  //     * @param value
  //     */
  //    void fill(long value);
  //  }
  //
  //  default RuntimeAsset.Type classify() {
  //    return RuntimeAsset.Type.ARTIFACT;
  //  }

  //  Type getType();

  //  /**
  //   * The {@link Data.FillCurve} for the spatial arrangement in the buffers. The fill curve is
  //   * established based on the geometry unless a <code>@fillcurve
  //   * </code> annotation is present on the model. The fill curve is irrelevant if there is only
  // one
  //   * spatial state or no spatial extent at all. In such cases it's best to avoid initializing a
  // moot
  //   * Hilbert curve which has more overhead than the others.
  //   *
  //   * <p>The storage may not have a fill curve until the first buffers are created.
  //   *
  //   * @return the spatial fill curve for the spatial extent.
  //   */
  //  Data.FillCurve spaceFillCurve();
  //
  //  /**
  //   * Return the buffers that cover the passed geometry at the passed time. The time in the
  // geometry
  //   * is considered only if the specific time transition is null. Implementations may constrain
  // this
  //   * to only work with geometries that are "in phase" with the existing buffers, throwing an
  //   * exception if not. Buffers should be created as required. The type, amount and filling curve
  // of
  //   * the buffers will reflect the defaults from service configuration, possibly overridden
  // through
  //   * the annotations passed at the moment of creating the storage. The service MUST ensure that
  // the
  //   * buffer splits are identical across all the qualities within the same subject.
  //   *
  //   * @param geometry
  //   * @param transition the time from the event being contextualized.
  //   * @throws org.integratedmodelling.klab.api.exceptions.KlabIllegalArgumentException if the
  //   *     parameters cause non-resolvable geometry conflicts with the underlying implementation.
  //   * @return
  //   */
  //  List<? extends Storage.Buffer> buffers(Geometry geometry, Time transition);

  //  /**
  //   * Return the buffers that cover the passed geometry at the passed time. The time in the
  // geometry
  //   * * is considered only if the specific time transition is null. Like {@link
  // #buffers(Geometry,
  //   * Time)} but enables some degree of recontextualization so that contextualizers can establish
  // the
  //   * fill curve they expect to use. The returned buffers must be capable of adapting to the
  //   * requested parameters, which would normally come as <code>@storage</code> annotations built
  // from
  //   * the contextualizer's declaration.
  //   *
  //   * @param geometry
  //   * @param transition the time from the event being contextualized.
  //   * @param storageAnnotation
  //   * @throws org.integratedmodelling.klab.api.exceptions.KlabIllegalArgumentException if the
  //   *     parameters cause non-resolvable conflicts with the underlying implementation.
  //   * @return
  //   */
  //  List<? extends Storage.Buffer> buffers(
  //      Geometry geometry, Time transition, Annotation storageAnnotation);

  //  /**
  //   * Retrieve all buffers that cover the passed geometry at the passed time. The time in the
  //   * geometry is considered only if the specific time transition is null, The geometry must be
  // in
  //   * phase with the overall geometry. Implementations may provide support for partial geometries
  //   * within a single extent but this is not expected in general. There may be multiple buffers
  // even
  //   * with a single time extent, and they should be usable in parallel as needed. This one is
  // called
  //   * by contextualizers to obtain, and according to implementation possibly create, the needed
  //   * buffer(s) for reading and writing according to the contextualization stage.
  //   *
  //   * <p>The buffers are allocated using the default fill curve and an implementation-dependent
  //   * strategy unless a <code>@split</code> annotation is present on the model to define the
  // split
  //   * strategy.
  //   *
  //   * @param geometry the (sub)-geometry that covers the buffers. According to implementation,
  // the
  //   *     geometry's coverage of the overall geometry may be more or less constrained.
  //   * @param transition the time from the event being contextualized.
  //   * @param bufferClass the class of the buffer, which is needed to access the non-boxing add,
  // set
  //   *     and get methods exposed by the different {@link Buffer} subclasses. If a class is asked
  // for
  //   *     that does not match the existing buffers, a mediating buffer should be produced. The
  // native
  //   *     buffer class should always be understandable based on the storage type.
  //   * @throws org.integratedmodelling.klab.api.exceptions.KlabIllegalArgumentException if the
  //   *     parameters cause non-resolvable type or geometry conflicts with the underlying
  //   *     implementation.
  //   */
  //  <T extends Storage.Buffer> List<T> buffers(
  //      Geometry geometry, Time transition, Class<T> bufferClass);
  //
  //  /**
  //   * After the contextualization is finished, the storage will contain one or more buffers with
  // the
  //   * data content, geometry and data fill curve. The set of buffers will cover the geometry of
  // the
  //   * observation. This one returns all the existing buffers; they are expected to be fully
  // defined
  //   * and read-only at this stage.
  //   */
  //  List<Storage.Buffer> allBuffers();

  //  /**
  //   * The overall geometry of the storage. Will change during contextualization to reflect
  // dynamic
  //   * dimensions extended by events.
  //   *
  //   * @return
  //   */
  //  Geometry getGeometry();

  /**
   * The merged histogram built on demand by merging that of all existing buffers.
   *
   * @return
   */
  Histogram getHistogram();
}
