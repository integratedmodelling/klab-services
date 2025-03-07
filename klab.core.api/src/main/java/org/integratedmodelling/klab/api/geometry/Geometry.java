package org.integratedmodelling.klab.api.geometry;

import org.integratedmodelling.klab.api.collections.Parameters;
import org.integratedmodelling.klab.api.geometry.impl.GeometryBuilder;
import org.integratedmodelling.klab.api.geometry.impl.GeometryImpl;
import org.integratedmodelling.klab.api.knowledge.observation.scale.ExtentDimension;
import org.integratedmodelling.klab.api.knowledge.observation.scale.Scale;
import org.integratedmodelling.klab.api.lang.ServiceInfo;

import java.io.Serializable;
import java.util.List;

/**
 * TODO obsolete - revise these javadocs!
 *
 * <p>A Geometry is the declaration of the topological dimensions for the artifacts specified by (or
 * allowed for) a resource or computation (for example a resource identified by a URN) or by a
 * {@link ServiceInfo#getGeometry() service prototype} declared in k.DL or through annotations).
 * When used in such a declarative fashion it may have a granularity > 1, meaning that it describes
 * a multiplicity of resources, and one child geometry describing the geometry of each resource.
 *
 * <p>Geometries apply to artifacts, so they do not contain any semantic information. In k.LAB,
 * observations (the semantic peer of artifact) expose into a worldview-aware {@link Scale scale}
 * instead, whose API extends {@link Geometry}. The scale also precisely locates the geometry and
 * guarantees valid values of the corresponding <strong>shape</strong>, consisting of long sizes and
 * extents, which the geometry can describe using {@link #size()} and its dimensions but is allowed
 * to leave them unspecified. When used as a {@link Scale} the geometry is currently always
 * associated to an observation, not to a computation or resource, so it never has granularity != 1.
 * Currently a non-API observation group (corresponding to the object artifact) exposes the overall
 * S/T scale but does not specify granularity.
 *
 * <p><strong>Declaring geometries</strong>
 *
 * <p>A geometry can be declared through a simple string (which is also its string value). Each
 * dimension can use S/s for space, T/t for time, and any other uppercase/lowercase letters except S
 * and T for other extents (the latter are currently unused and will need a "legend" of sort if/when
 * so). The correspondent Greek letters (sigma and tau) can be used to specify "generic" dimensions,
 * which are stated to exist and may have a resolution but are aspecific w.r.t. their actual
 * location and topology.
 *
 * <p>Each letter can be used alone if the shape consists of one extent without subdivisions, or it
 * can be followed by the number of dimensions for each extent if subdivided: so T will mean located
 * in time but not distributed, while T1 will mean located and distributed in time along a
 * 1-dimensional topology (with an arbitrary 'shape', i.e. number of subdivisions). If the uppercase
 * letter is used, the topology is regular and the only information needed to characterize it is the
 * number of subdivisions. If the letter is lowercase, the topology is irregular. Use the uppercase
 * letter if there is no subdivision.
 *
 * <p>The trivial case is a scalar geometry, describing scalars with no structure and specified by
 * an asterisk (<code>*</code>). Scalar geometries cannot be assigned parameters, nor, obviously, a
 * shape. An empty geometry (which is useless except to report errors) is specified by an uppercase
 * <code>X</code>. Scalar geometries are useful: for example, a resolver for any countable will have
 * scalar geometry and void type.
 *
 * <p>If the shape is only letters with optional numbers, the topology applies to a single object
 * ({@link #getGranularity()} == {@link Granularity#SINGLE}). Otherwise, it can be prefixed by
 * <code>#</code>, which distributes the geometry across multiple objects ({@link #getGranularity()}
 * == {@link Granularity#MULTIPLE}). Objects that are children of another or others are defined
 * after a comma. So for example
 *
 * <p><code>
 * TS3,#S2
 * </code>
 *
 * <p>denotes a single 3-dimensional regular spatial geometry (voxel cube), referenced but not
 * distributed in time and containing multiple sub-objects with just a 2-dimensional spatial
 * geometry; while
 *
 * <p><code>
 * #s0
 * </code>
 *
 * <p>denotes a non-temporal set of irregular points in space. This notation assumes that all
 * dimensions of a topology are either regular or irregular.
 *
 * @author fvilla
 */
public interface Geometry extends Serializable, Locator {

    public enum Granularity {
    /** */
    SINGLE,
    /** */
    MULTIPLE
  }

  /**
   * Encoders can be passed to {@link #encode(Encoder...)} to modify the way that specific
   * parameters are encoded in the geometry string.
   */
  interface Encoder {

    /**
     * The dimension on which this encoder is applied
     *
     * @return
     */
    Dimension.Type dimension();

    /**
     * The key for the parameter handled by this encoder
     *
     * @return
     */
    String key();

    /**
     * The value to be encoded. Return the encoded form for the passed value.
     *
     * @param value
     * @return
     */
    String encode(Object value);
  }

  /**
   * Constant for non-dimensional (referenced but not distributed) return value of {@link
   * Dimension#getDimensionality()}.
   */
  public static final int NONDIMENSIONAL = -1;

  /** Constant for undefined dimension size. */
  public static final long UNDEFINED = -1L;

  /** Infinite size, only admitted for the time dimension. */
  public static final long INFINITE_SIZE = Long.MAX_VALUE;

  /**
   * @author Ferd
   */
  public interface Dimension extends Serializable {

    /**
     * Types are ordered in the sequence of scanning in scales. Changing the order will destroy the
     * entire runtime!
     *
     * @author Ferd
     */
    enum Type {

      /** */
      NUMEROSITY,
      /** */
      TIME,
      /** */
      SPACE;
    }

    /**
     * Return the [start, end] offsets corresponding to the span of the passed dimension in the
     * offset space of this dimension. Used for subsetting of geometries in {@link
     * Geometry#at(Locator)}.
     *
     * @param dimension
     * @return
     */
    long[] locate(Dimension dimension);

    /**
     * Generic means 'not completely defined', i.e. not usable for contextualization. This
     * corresponds to the Greek lowercase letter for the dimension in the string encoding.
     *
     * @return
     */
    boolean isGeneric();

    /**
     * Match against constants {@link Type#SPACE} and {@link Type#TIME}. If none of these, any other
     * user-defined dimension is possible - conventions must be established in worldview for those.
     *
     * @return the dimension type
     */
    Type getType();

    /**
     * Whether any subdivisions in this dimension are regular or irregular. Regularity also implies
     * distribution, i.e. the behavior of values over this geometry will depend on their extensive
     * nature independent of size. If not regular, they will only depend if size > 1 and the effect
     * will be different for each subdivision. Size may be 1 even in regular dimensions.
     *
     * @return regularity
     */
    boolean isRegular();

    /**
     * Can be {@link Geometry#NONDIMENSIONAL} or a positive (0+) integer. Non-dimensional means
     * referenced but not distributed.
     *
     * @return dimensionality of this dimension
     */
    int getDimensionality();

    /**
     * Return a linear offset for the array of offsets matching the dimensionality in size. Will be
     * needed only if getDimensionality() > 1.
     *
     * @param offsets
     * @return
     */
    long offset(long... offsets);

    /**
     * Return the size of this dimension. In a geometry that has been declared but not defined (such
     * as the result of parsing a dimension string) this will return {@link Geometry#UNDEFINED},
     * which is a negative value.
     *
     * @return the size of the dimension or
     */
    long size();

    /**
     * Return the topological numerosity and shape correspondent to the passed dimension of the
     * underlying geometry. For example, {@code locator.getShape(Type.SPACE)} called on a scale
     * locator where space is a 10x20 grid will return [10, 20]. This is normally called in
     * contextualizers when numeric offsets need to be exposed. Because contextualizers are declared
     * with their geometry, there should be no need for error checking, and asking for a dimension
     * that is not part of the locator will throw an exception.
     *
     * <p>In a geometry whose size is undefined, this will return an array of {@link
     * Geometry#UNDEFINED} values of size corresponding to the dimensionality.
     *
     * @return the shape corresponding to the dimension. Never null.
     * @throws IllegalArgumentException if the locator does not have the requested dimension.
     */
    List<Long> getShape();

    /**
     * Additional parameters may be given to further specify a dimension. These are intended to be
     * linked to small POD data or arrays, and to enable transferring fully specified geometries
     * across services. These are given after each dimension specification within braces and are
     * named with field names and text values, e.g.:
     *
     * <pre>
     * S2[10,10]{srid=EPSG:3040,bounds=[23.3,221.0,25.2,444.4]}
     * </pre>
     *
     * <p>Geometry implementations should expose an API to specify and read these parameters in
     * idiomatic ways and not rely on users providing identifiers.
     *
     * <p>
     *
     * @return the parameters
     */
    Parameters<String> getParameters();

    /**
     * Return the extent dimension this dimension describes.
     *
     * @return
     */
    ExtentDimension extentDimension();

    /**
     * Return the parseable string encoding of the dimension.
     *
     * @return
     */
    String encode();

    /**
     * Returns whether size > 1, dimension is regular, or any other parameter have been defined that
     * make this dimension, generic or not, distributed in its extent.
     *
     * @return
     */
    boolean distributed();
  }

  public static Geometry EMPTY = create("X");

  public static Geometry create(String geometry) {
    return GeometryImpl.makeGeometry(geometry, 0);
  }

  /**
   * Encode the geometry to a string literal from which it can be reconstructed and analyzed. The
   * literal should be normalized so that an equality contract holds between both two geometries and
   * their encoded forms.
   *
   * @return the encoded geometry
   */
  String encode(Encoder... encoders);

  /**
   * Generic means 'not completely defined', i.e. not usable for contextualization. This corresponds
   * to the Greek lowercase letter for the dimension in the string encoding.
   *
   * @return
   */
  boolean isGeneric();

  //    /**
  //     * A geometry may imply another for component objects. E.g. spatial data may have geometry
  // and
  //     * define objects within it, with different geometry constrained by this.
  //     *
  //     * Was using Optional but it does not serialize well - do not do that.
  //     *
  //     * @return the optional child geometry, or null.
  //     */
  //    Geometry getChild();

  /**
   * Return all the dimensions this geometry. Dimensions are reinterpreted through the worldview and
   * turned into the worldview's topological interpretation before a scale can be built.
   *
   * @return all dimensions
   */
  List<Dimension> getDimensions();

  /**
   * Return the dimension of the passed type, or null.
   *
   * @param type
   * @return the dimension or null
   */
  Dimension dimension(Dimension.Type type);

  /**
   * A geometry may specify one or multiple objects.
   *
   * @return the granularity
   */
  Granularity getGranularity();

  /**
   * An empty geometry applies to any resource that does not produce raw information but processes
   * data instead. A geometry {@link #at(Locator)} with an empty geometry as argument must return
   * the same geometry.
   *
   * @return true for a geometry that was not specified.
   */
  boolean isEmpty();

  /**
   * A trivial geometry describes scalar values with no structure.
   *
   * @return true if scalar
   */
  boolean isScalar();

  /**
   * The product of the size of all dimensions. If one or more of the dimensions has size ==
   * UNDEFINED, return UNDEFINED.
   *
   * @return the size of the geometry
   */
  long size();

  /**
   * TODO javadocs obsolete!
   *
   * <p>Return a locator to select specific dimensions and states within them. Use in
   * contextualizers to preserve semantics when addressing dependent states and numeric offsets are
   * required to interface to other APIs.
   *
   * <p>Locators can be converted to the desired form using {@link Locator#as(Class)}. So if numeric
   * offsets are needed to point to a state or scan a dimension, calls can be combined as in
   *
   * <pre>
   * Offset offset = geometry.at(geometry.getTime(), ISpace.class, x, y).as(Offset.class);
   *
   * ... use offset.pos[0], offset.pos[1] to point to a grid cell in the current time
   * </pre>
   *
   * <p>The resulting locator is an {@link Iterable} and will produce either itself (if scalar) or
   * all the locators for the scanned dimension when iterated.
   *
   * <p>The call format is quite flexible. Object that can be passed are:
   *
   * <ul>
   *   <li>specific extents from a scale;
   *   <li>the class of an extent;
   *   <li>the {@link Dimension.Type} specifying an extent;
   *   <li>long or long[] offsets; if following an extent specifier, they will be applied to the
   *       preceding extent to pinpoint a specific state, otherwise to the entire geometry for the
   *       same purposes. If one offset is passed and the dimensionality of the target is multiple,
   *       it will be treated as a linear offset and converted.
   * </ul>
   *
   * Passing an extent locator that is not present in the scale or the empty geometry returns the
   * locator unmodified.
   *
   * @param dimension a locator to subset the geometry. Further locations can be done by chaining
   *     at() calls.
   * @return a valid located geometry
   */
  Geometry at(Locator dimension);

  /**
   * If the geometry results from a split() operation, return the linear offset in each dimension
   * w.r.t. the original geometry that was splitted. In non-split geometries, returns a vector of
   * 0s.
   *
   * @return
   */
  long[] getExtentOffsets();

  /**
   * TODO unimplemented. Must define the constraint API and the offset retrieval operation.
   *
   * <p>Split a geometry in a list of geometries whose extent and/or resolution match the specified
   * constraints (TO BE DEFINED). The resulting geometries will have their {@link
   * #getExtentOffsets()} return the offset per each dimension within the original geometry so that
   * the corresponding original offsets can be reconstructed.
   *
   * <p>This can be used to parallelize a large geometry so that it can be processed in parallel
   * chunks.
   *
   * @return
   */
  List<Geometry> split();

  /**
   * If this is true, the reported size will not include the time, and the time dimension will have
   * {@link #INFINITE_SIZE} size.
   *
   * @return
   */
  boolean infiniteTime();

  static GeometryBuilder builder() {
    return new GeometryBuilder();
  }
}
