package org.integratedmodelling.klab.api.data;

import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.scope.Persistence;

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
   * geometry that can be the full storage geometry or a sub-geometry for parallel, distributed
   * implementations. The Buffer subclass obtained with Buffer is a value iterator using a specified
   * fill curve and geometry.
   *
   * <p>Buffers have a unique ID and a geometry, plus a persistence status so that the {@link
   * org.integratedmodelling.klab.api.digitaltwin.DigitalTwin} can set up copies, backups or other
   * operations to be done to guarantee persistence across invocations.
   *
   * <p>Specific buffer types should also implement a mapping function for map/reduce operations.
   */
  interface Buffer {

    String id();

    Persistence persistence();

    /**
     * Obtain a Data.Filler whose add method will use the same fill curve that the buffer
     * implements. When the last add() is called on the filler, the buffer is expected to be
     * finalized and immutable. Storage managers may decide to queue in operations such as building
     * statistics, images or the like on a low-priority queue.
     *
     * @param fillerClass
     * @return
     * @param <T>
     */
    <T extends Data.Filler> T filler(Class<T> fillerClass);

    /**
     * The {@link org.integratedmodelling.klab.api.data.Data.FillCurve} for this buffer. Applies to
     * both filling and iteration through subclasses.
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
   * @param geometry the geometry for the buffer. Must be in phase and contained within the storage
   *     overall geometry returned by {@link #getGeometry()}. Implementations may impose
   *     restrictions.
   * @param fillCurve the fill curve along which the buffer is iterable. Only the subclasses
   *     implement primitive or boxing iterators. The filler returned by the buffer implements the
   *     same fill curve.
   * @return a suitable buffer
   */
  B buffer(Geometry geometry, Data.FillCurve fillCurve);

  Type getType();

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
