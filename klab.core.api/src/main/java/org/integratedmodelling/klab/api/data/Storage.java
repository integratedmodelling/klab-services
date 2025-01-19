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
  interface Buffer extends RuntimeAsset {

    default RuntimeAsset.Type classify() {
      return Type.DATA;
    }

    Storage.Type dataType();

    long size();

    long[] offsets();

    /**
     * The portable histogram. Should never be null.
     *
     * @return
     */
    Histogram histogram();

    /**
     * The persistence tells us whether we need to periodically offload the buffer to disk in order
     * to keep the digital twin consistent across server boots.
     *
     * @return
     */
    Persistence persistence();

    /**
     * Obtain a Data.Filler whose <code>add(value)</code> method will use the same fill curve that
     * the buffer implements. When the last add() is called on the filler, the buffer is expected to
     * be finalized and immutable. Storage managers may decide to queue in operations such as
     * building statistics, images or the like on a low-priority queue.
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
   * @param size the size of the buffer - either from the full geometry or for a part using the
   *     passed fill curve and offsets.
   * @param fillCurve the fill curve along which the buffer is iterable. Only the subclasses
   *     implement primitive or boxing iterators. The filler returned by the buffer implements the
   *     same fill curve.
   * @param offsets the offset for the storage within the geometry, so that the data can be
   *     navigated according to the fill curve.
   * @return a suitable buffer
   */
  B buffer(long size, Data.FillCurve fillCurve, long[] offsets);

  Type getType();

  /**
   * After the contextualization is finished, the storage will contain one or more buffers with the
   * data content, geometry and data fill curve. The set of buffers will cover the geometry of the
   * observation.
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
