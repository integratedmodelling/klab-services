package org.integratedmodelling.klab.api.data;

import java.util.Collection;
import java.util.List;
import java.util.PrimitiveIterator;

import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.knowledge.observation.scale.time.Time;
import org.integratedmodelling.klab.api.lang.Annotation;
import org.integratedmodelling.klab.api.scope.Persistence;

/**
 * Base storage providing only general methods. Children enable either boxed I/O or faster native
 * operation (recommended). The runtime makes the choice based on the API of the contextualizers.
 *
 * @author Ferd
 */
public interface Storage extends RuntimeAsset {

  enum Type {
    BOXING,
    DOUBLE,
    FLOAT,
    INTEGER,
    LONG,
    KEYED,
    BOOLEAN
  }

  /**
   * Tag interface for a buffer that can produce a filler using a particular filling curve for a
   * geometry. The latter can be the full storage geometry or a sub-geometry for parallel,
   * distributed implementations. Temporal events may produce modified buffers that share the same
   * geometry except for the temporal location. The Buffer subclass obtained with Buffer is a value
   * iterator using a specified fill curve and geometry.
   *
   * <p>Buffers have a unique ID and a geometry, plus a persistence status so that the {@link
   * org.integratedmodelling.klab.api.digitaltwin.DigitalTwin} can set up copies, backups or other
   * operations to be done to guarantee persistence across invocations.
   *
   * <p>Buffers are {@link RuntimeAsset}s because they end up in the {@link KnowledgeGraph} exposed
   * by the {@link org.integratedmodelling.klab.api.digitaltwin.DigitalTwin}.
   *
   * <p>Specific buffer types should also implement a mapping function for map/reduce operations.
   */
  interface Buffer extends Data.Cursor, RuntimeAsset {

    default RuntimeAsset.Type classify() {
      return Type.DATA;
    }

    /**
     * Size of the buffer. In a simple situation this will normally be equal to the size of the
     * fastest-changing extent of the geometry.
     *
     * @return
     */
    long size();

    /**
     * Locators of the buffer relative to the storage's fill curve and the sub-geometry of the
     * overall storage geometry represented in it. Must agree with the number of buffers and the
     * size. These are linear offsets, one per dimension.
     *
     * @return
     */
    long offset();

    String getUrn();

    long getTimestamp();
  }

  interface DoubleBuffer extends Buffer {

    interface DoubleScanner extends PrimitiveIterator.OfLong {

      /**
       * Return the value at the current offset in the iterator and advance the iteration.
       *
       * @return
       */
      double get();

      /**
       * Return the value at the current offset in the iterator without advancing the iteration.
       *
       * @return
       */
      double peek();

      /**
       * Set the value at the current iterable offset and advance the iteration. Do not use after
       * get() - use peek() if the value must be known.
       *
       * @param value
       */
      void add(double value);
    }

    @Override
    DoubleScanner scan();

    /**
     * Random access value. The offset is according to the overall fill curve and buffer-specific
     * offsets.
     *
     * @param offset
     */
    double get(long offset);

    /**
     * Random value set. May be inefficient. Offset as in {@link #get(long)}.
     *
     * @param value
     * @param offset
     */
    void set(double value, long offset);

    /**
     * Supposed to be more efficient than a loop, based on the implementation.
     *
     * @param value
     */
    void fill(double value);
  }

  interface LongBuffer extends Buffer {

    /**
     * Return the value at the current offset in the iterator and advance the iteration.
     *
     * @return
     */
    long get();

    /**
     * Return the value at the current offset in the iterator without advancing the iteration.
     *
     * @return
     */
    long peek();

    /**
     * Set the value at the current iterable offset and advance the iteration. Do not use after
     * get()!
     *
     * @param value
     */
    void add(long value);

    /**
     * Random access value. The offset is according to the overall fill curve and buffer-specific
     * offsets.
     *
     * @param offset
     */
    long get(long offset);

    /**
     * Random value set. May be inefficient. Offset as in {@link #get(long)}.
     *
     * @param value
     * @param offset
     */
    void set(long value, long offset);

    /**
     * Supposed to be more efficient than a loop, based on the implementation.
     *
     * @param value
     */
    void fill(long value);
  }

  default RuntimeAsset.Type classify() {
    return RuntimeAsset.Type.ARTIFACT;
  }

  Type getType();

  /**
   * The {@link Data.SpaceFillingCurve} for the spatial arrangement in the buffers. The fill curve
   * is established based on the geometry unless a <code>@fillcurve
   * </code> annotation is present on the model. The fill curve is irrelevant if there is only one
   * spatial state or no spatial extent at all. In such cases it's best to avoid initializing a moot
   * Hilbert curve which has more overhead than the others.
   *
   * <p>The storage may not have a fill curve until the first buffers are created.
   *
   * @return the spatial fill curve for the spatial extent.
   */
  Data.SpaceFillingCurve spaceFillCurve();

  /**
   * Return the buffers that cover the passed geometry at the passed time. The time in the geometry
   * is considered only if the specific time transition is null. Implementations may constrain this
   * to only work with geometries that are "in phase" with the existing buffers, throwing an
   * exception if not. Buffers should be created as required. The type, amount and filling curve of
   * the buffers will reflect the defaults from service configuration, possibly overridden through
   * the annotations passed at the moment of creating the storage. The service MUST ensure that the
   * buffer splits are identical across all the qualities within the same subject.
   *
   * @param geometry
   * @param transition the time from the event being contextualized.
   * @throws org.integratedmodelling.klab.api.exceptions.KlabIllegalArgumentException if the
   *     parameters cause non-resolvable geometry conflicts with the underlying implementation.
   * @return
   */
  List<? extends Storage.Buffer> buffers(Geometry geometry, Time transition);

  /**
   * Return the buffers that cover the passed geometry at the passed time. The time in the geometry
   * * is considered only if the specific time transition is null. Like {@link #buffers(Geometry,
   * Time)} but enables some degree of recontextualization so that contextualizers can establish the
   * fill curve they expect to use. The returned buffers must be capable of adapting to the
   * requested parameters, which would normally come as <code>@storage</code> annotations built from
   * the contextualizer's declaration.
   *
   * @param geometry
   * @param transition the time from the event being contextualized.
   * @param storageAnnotation
   * @throws org.integratedmodelling.klab.api.exceptions.KlabIllegalArgumentException if the
   *     parameters cause non-resolvable conflicts with the underlying implementation.
   * @return
   */
  List<? extends Storage.Buffer> buffers(
      Geometry geometry, Time transition, Annotation storageAnnotation);

  /**
   * Retrieve all buffers that cover the passed geometry at the passed time. The time in the
   * geometry is considered only if the specific time transition is null, The geometry must be in
   * phase with the overall geometry. Implementations may provide support for partial geometries
   * within a single extent but this is not expected in general. There may be multiple buffers even
   * with a single time extent, and they should be usable in parallel as needed. This one is called
   * by contextualizers to obtain, and according to implementation possibly create, the needed
   * buffer(s) for reading and writing according to the contextualization stage.
   *
   * <p>The buffers are allocated using the default fill curve and an implementation-dependent
   * strategy unless a <code>@split</code> annotation is present on the model to define the split
   * strategy.
   *
   * @param geometry the (sub)-geometry that covers the buffers. According to implementation, the
   *     geometry's coverage of the overall geometry may be more or less constrained.
   * @param transition the time from the event being contextualized.
   * @param bufferClass the class of the buffer, which is needed to access the non-boxing add, set
   *     and get methods exposed by the different {@link Buffer} subclasses. If a class is asked for
   *     that does not match the existing buffers, a mediating buffer should be produced. The native
   *     buffer class should always be understandable based on the storage type.
   * @throws org.integratedmodelling.klab.api.exceptions.KlabIllegalArgumentException if the
   *     parameters cause non-resolvable type or geometry conflicts with the underlying
   *     implementation.
   */
  <T extends Storage.Buffer> List<T> buffers(
      Geometry geometry, Time transition, Class<T> bufferClass);

  /**
   * After the contextualization is finished, the storage will contain one or more buffers with the
   * data content, geometry and data fill curve. The set of buffers will cover the geometry of the
   * observation. This one returns all the existing buffers; they are expected to be fully defined
   * and read-only at this stage.
   */
  List<Storage.Buffer> allBuffers();

  /**
   * The overall geometry of the storage. Will change during contextualization to reflect dynamic
   * dimensions extended by events.
   *
   * @return
   */
  Geometry getGeometry();

  /**
   * The merged histogram build on demand by merging that of all buffers created.
   *
   * @return
   */
  Histogram getHistogram();

  /**
   * The persistence tells us whether we need to periodically offload the buffers to disk in order
   * to keep the digital twin consistent across server boots.
   *
   * @return
   */
  Persistence persistence();
}
