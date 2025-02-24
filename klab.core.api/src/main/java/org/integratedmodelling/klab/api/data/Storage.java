package org.integratedmodelling.klab.api.data;

import java.util.Collection;
import java.util.List;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.lang.Annotation;
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
    long[] offsets();
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
   * @return the spatial fill curve for the spatial extent.
   */
  Data.SpaceFillingCurve spaceFillCurve();

  /**
   * Retrieve all buffers that cover the passed geometry, which must be in phase with the overall
   * geometry. Implementations may provide support for partial geometries within a single extent but
   * this is not expected in general. There may be multiple buffers even with a single time extent,
   * and they should be usable in parallel as needed. This one is called by contextualizers to
   * obtain, and according to implementation possibly create, the needed buffer(s) for reading and
   * writing according to the contextualization stage.
   *
   * <p>The buffers are allocated using the default fill curve and an implementation-dependent
   * strategy unless a <code>@split</code> annotation is present on the model to define the split
   * strategy.
   *
   * @param geometry the (sub)-geometry that covers the buffers. According to implementation, the
   *     geometry's coverage of the overall geometry may be more or less constrained.
   * @param bufferClass the class of the buffer, which is needed to access the non-boxing add, set
   *     and get methods exposed by the different {@link Buffer} subclasses. If a class is asked for
   *     that does not match the existing buffers, a mediating buffer should be produced. The native
   *     buffer class should always be understandable based on the storage type.
   * @param annotations may contain specifications for splits (<code>@split</code>); also <code>
   *     fillcurve</code> can specify the fill curve to use to address the spatial arrangement of
   *     the geometry. In some situations (e.g. single spatial buffer) it should be possible to use
   *     a different fill curve (e.g. a S2HILBERT for a SXY when the buffer is orthonormal), which
   *     the implementation should automatically remap. If that is not possible (e.g. there are
   *     multiple buffers with a different FC) a {@link
   *     org.integratedmodelling.klab.api.exceptions.KlabIllegalArgumentException} should be thrown.
   * @return the list of buffers covering the geometry and addressable through the passed curve.
   * @throws org.integratedmodelling.klab.api.exceptions.KlabIllegalArgumentException if the
   *     parameters cause non-resolvable geometry conflicts with the underlying implementation.
   */
  <T extends Storage.Buffer> List<T> buffers(
      Geometry geometry, Class<T> bufferClass, Collection<Annotation> annotations);

  /**
   * After the contextualization is finished, the storage will contain one or more buffers with the
   * data content, geometry and data fill curve. The set of buffers will cover the geometry of the
   * observation. This one returns all the existing buffers; they are expected to be fully defined
   * and read-only at this stage.
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

  /**
   * The persistence tells us whether we need to periodically offload the buffers to disk in order
   * to keep the digital twin consistent across server boots.
   *
   * @return
   */
  Persistence persistence();
}
