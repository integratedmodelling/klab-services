package org.integratedmodelling.klab.api.data;

import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.knowledge.KlabAsset;
import org.integratedmodelling.klab.api.scope.Persistence;

import java.util.List;

/**
 * Base storage providing only general methods. Children enable either boxed I/O or faster native
 * operation (recommended). The runtime makes the choice based on the API of the contextualizers.
 *
 * @author Ferd
 */
public interface Storage<B extends Storage.Buffer> extends RuntimeAsset {

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
  interface Buffer extends RuntimeAsset /* TODO must extend Cursor too */ {

    default RuntimeAsset.Type classify() {
      return Type.DATA;
    }

    /*
    FIXME remove - top level says everything
     */
    Storage.Type dataType();

    long size();

    // FIXME this is a single long according to the overall fill curve
    @Deprecated
    long[] offsets();

    /**
     * The portable histogram. Should never be null.
     * FIXME remove
     * @deprecated should be internal
     * @return
     */
    Histogram histogram();

    /**
     * The persistence tells us whether we need to periodically offload the buffer to disk in order
     * to keep the digital twin consistent across server boots.
     * FIXME move to top level
     * @return
     */
    Persistence persistence();

    /**
     * Obtain a Data.Filler whose <code>add(value)</code> method will use the same fill curve that
     * the buffer implements. When the last add() is called on the filler, the buffer is expected to
     * be finalized and immutable. Storage managers may decide to queue in operations such as
     * building statistics, images or the like on a low-priority queue.
     * FIXME remove, merge with cursor
     * @param fillerClass
     * @return
     * @param <T>
     */
    <T extends Data.Filler> T filler(Class<T> fillerClass);

    /**
     * The {@link org.integratedmodelling.klab.api.data.Data.FillCurve} for this buffer. Applies to
     * both filling and iteration through subclasses.
     *
     * FIXME move to top level
     *
     * @return
     */
    Data.FillCurve fillCurve();
  }

  default RuntimeAsset.Type classify() {
    return RuntimeAsset.Type.ARTIFACT;
  }

  /**
   * Obtain a buffer to access and/or fill the storage or a part of it.
   *
   * @param size the size of the buffer - either from the full geometry or for a part using the
   *     passed fill curve and offsets. If the buffer already exists, the passed fill curve should be
   *             honored by enabling the buffer's cursors to translate the
   * @param fillCurve the fill curve along which the buffer is iterable. Only the subclasses
   *     implement primitive or boxing iterators. The filler returned by the buffer implements the
   *     same fill curve.
   * @param offset the offset for the storage within the overall geometry according to the fill
   *     curve. This should be 0 unless the buffer is a partial coverage.
   *
   * @return a suitable buffer
   * @deprecated this should come from a cursor and not be asked directly, The method could be internal
   * and not in the API.
   */
  B buffer(long size, Data.FillCurve fillCurve, long[] offset);

//  /**
//   * Obtain a cursor over the data with the specified class type. The Cursor subclasses provide the
//   * add, set and get methods for both sequential and random r/w access.
//   *
//   * The resulting {@link Data.Cursor} will use the default fill curve for the geometry of the
//   * underlying data buffer.
//   *
//   * @param cursorClass
//   * @return
//   * @param <T>
//   */
//  <T extends Data.Cursor> T cursor(Class<T> cursorClass);
//  <T extends Data.Cursor> T cursor(Geometry geometry, Class<T> cursorClass);
//  <T extends Data.Cursor> T cursor(Geometry geometry, Data.FillCurve fillCurve, Class<T> cursorClass);
//  <T extends Data.Cursor> T cursor(Geometry geometry, Data.FillCurve fillCurve, long start, long end, Class<T> cursorClass);

  Type getType();

  /**
   * After the contextualization is finished, the storage will contain one or more buffers with the
   * data content, geometry and data fill curve. The set of buffers will cover the geometry of the
   * observation.
   *
   * TODO the buffers should also be cursors and iterate longs. Buffers are predefined (possibly honoring
   *  @split and @fillcurve annotations, otherwise using geometry-dependent defaults) when this is accessed.
   *  A buffer(FillCurve) should also be provided that will return a single buffer honoring the fill curve,
   *  applying any offsets if multiple (or throw an exception) and remap the iteration to the "actual" fill
   *  curve unless it's the same.
   *
   * TODO The fill curve must be available at the Storage level, not the buffer level, as it cannot be different
   *  in different buffers.
   *
   * @return
   */
  List<Storage.Buffer> buffers();

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
}
