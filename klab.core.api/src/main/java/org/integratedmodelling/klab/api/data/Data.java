package org.integratedmodelling.klab.api.data;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.PrimitiveIterator;
import org.integratedmodelling.klab.api.Klab;
import org.integratedmodelling.klab.api.collections.Pair;
import org.integratedmodelling.klab.api.exceptions.KlabIllegalStateException;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.api.lang.Annotation;
import org.integratedmodelling.klab.api.services.runtime.Notification;

/**
 * The <code>Data</code> object encapsulates the network-transmissible data package specified
 * through the Avro schema and understood by all k.LAB services. If the {@link
 * org.integratedmodelling.klab.api.services.resources.adapters.Adapter} used is available locally,
 * no network transmission will happen. A Data object must be created with a name, an Observable and
 * a Geometry.
 *
 * <p>A Data object that wraps a quality observation will be unmarshalled with an appropriate
 * subclass implementing one of the {@link java.util.PrimitiveIterator} interfaces, so that the
 * numbers can be extracted as needed without boxing as long as the primitive iterator methods are
 * used. In the case of a category quality, the object will implement {@link
 * java.util.PrimitiveIterator.OfInt} and the dataKey will be instantiated as well so that the
 * (Integer) numbers can be translated to the needed objects, normally {@link
 * org.integratedmodelling.klab.api.knowledge.Concept} instances.
 */
public interface Data {

  /**
   * TODO merge Cursor and Filler (call it Cursor). Enable 6 unboxing methods for data access in
   * each subclass:
   *
   * <p>add(value) -> standard add using buffer's curve add(value, FillCurve) -> add translating
   * offsets from another curve set to the same geometry value get() -> retrieve using buffer value
   * get(FillCurve) -> retrieve at current position translating curve value get(long) -> random
   * access get() set(value, long) -> random access set()
   *
   * <p>TODO OR: avoid the ones with FillCurve and just ask for a specific curve when creating the
   * cursor. So just add(value), get(), set(value, long) and get(long). Class without checking: a
   * different class for the two cases. The method should be available as FC translation from
   * (maybe) the geometry?
   */

  /**
   * A Cursor iterates one or more geometry dimensions using a long offset. If the geometry it
   * refers to results from splitting an original larger geometry, it can also locate the current
   * offset in it.
   */
  interface Cursor extends PrimitiveIterator.OfLong {

    /**
     * The linear offset in the geometry corresponding to the dimension offsets passed relative to
     * the space filling curve implemented, possibly along with offsets in any other varying
     * dimensions. The passed offsets will be matched to the varying dimensions, ignoring the
     * geometry extents that do not vary and assuming the dimensionality of the filling curve to
     * establish the leval number of parameters. This should be used in situations when the geometry
     * has been filtered so that only the varying dimensions remain.
     *
     * @param dimensionOffsets
     * @return the offset or -1L if no mapping is possible.
     */
    long offset(Cursor other, long... dimensionOffsets);
  }

  /** Non-boxing mapper for extent offsets to n-dimensional coordinates. */
  @FunctionalInterface
  public interface LongToLongArrayFunction {

    /**
     * Applies this function to the given argument.
     *
     * @param value the function argument
     * @return the function result
     */
    long[] apply(long value);
  }

  /** Non-boxing mapper for extent n-dimensional coordinates to linear offsets. */
  @FunctionalInterface
  public interface LongArrayToLongFunction {

    /**
     * Applies this function to the given argument.
     *
     * @param value the function argument
     * @return the function result
     */
    long apply(long[] value);
  }

  /**
   * Any of the space-filling curves are used in the data encoding. The {@link Data} object contains
   * a filling curve, which must be applied to the observation {@link Storage} for proper
   * arrangement of spatial dimensions. Each state with distributed space must define the curve it
   * uses.
   *
   * <p>Extents other than space can be assumed to always use D1_LINEAR whenever they are
   * distributed. At some point we may generalize further.
   */
  enum SpaceFillingCurve {

    /** Unfortunately needed because of Java not accepting null in defaults for annotations */
    UNSPECIFIED(0),

    /** Expects a single dimension changing, such as along a line. */
    D1_LINEAR(1),

    /** Iterates along one two-dimensional extent with the first index varying slower (row-first) */
    D2_XY(2),
    /**
     * Iterates along one two-dimensional extent with the first index varying faster (column-first)
     */
    D2_YX(2),

    /**
     * Iterates along one 2-dimensional extent with the first index varying slower (row-first) going
     * last to first on the Y index
     */
    D2_XInvY(2),

    D3_XYZ(3),

    D3_ZYX(3),

    // TODO also hilbert n-dim
    D2_HILBERT(2),

    D3_HILBERT(3);

    public final int dimensions;

    public Pair<LongToLongArrayFunction, LongArrayToLongFunction> offsetMappers(Geometry geometry) {
      Klab.Configuration configuration = Klab.INSTANCE.getConfiguration();
      if (configuration == null) {
        throw new KlabIllegalStateException("k.LAB environment not configured");
      }
      return configuration.getSpatialOffsetMapping(geometry, this);
    }

    SpaceFillingCurve(int dimensions) {
      this.dimensions = dimensions;
    }
  }

  interface Builder {

    /**
     * Add the passed notification. Returns self.
     *
     * @param notification
     * @return
     */
    Builder notification(Notification notification);

    /**
     * Add the passed non-semantic metadata. Returns self.
     *
     * @param key
     * @param value
     * @return
     */
    Builder metadata(String key, Object value);

    /**
     * Returns a new builder on which build() must be called to confirm the transaction. The
     * geometry is mandatorily that of the builder and the name is the URN of the observable. On the
     * builder, one of the fillers must be called to set the numbers.
     *
     * @param observable
     * @return
     */
    Builder state(Observable observable);

    /**
     * Returns a new builder for an object, on which build() must be called to confirm the
     * transaction. The API ensures that the object is sound after this call, but the builder can be
     * used to add metadata, states or child objects.
     *
     * @param name
     * @param observable
     * @param geometry
     * @return
     */
    Builder object(String name, Observable observable, Geometry geometry);

    /**
     * Shorthand for buffers(.., storage.getFillingCurve()).getFirst() when the fill curve
     * doesnt'matter and there is only one buffer because it's been forced to.
     *
     * @param fillerClass
     * @return
     * @param <T>
     */
    <T extends Storage.Buffer> T buffer(Class<T> fillerClass);

    /**
     * Shorthand for buffers(..).getFirst() when we know that there will be only one buffer due to
     * contextualizer configuration (split=1).
     *
     * @param fillerClass
     * @param spaceFillingCurve
     * @return
     * @param <T>
     */
    <T extends Storage.Buffer> T buffer(Class<T> fillerClass, SpaceFillingCurve spaceFillingCurve);

    /**
     * Shorthand for getting a set of parallel buffers with the filling curve mandated by the
     * modeler, configuration or implementation.
     *
     * @param fillerClass
     * @return
     * @param <T>
     */
    <T extends Storage.Buffer> List<T> buffers(Class<T> fillerClass);

    /**
     * Return buffers for the quality data object being built, in number depending on settings on
     * models, observables or contextualizer. Will throw an exception if the observable is not a
     * quality and the class is not compatible with the observable's {@link
     * org.integratedmodelling.klab.api.knowledge.DescriptionType}.
     *
     * @param fillerClass
     * @param spaceFillingCurve
     * @return
     * @param <T>
     */
    <T extends Storage.Buffer> List<T> buffers(
        Class<T> fillerClass, SpaceFillingCurve spaceFillingCurve);

    /**
     * Must be called on any secondary builders. Should NOT be called on the root builder, passed to
     * encoders. Nothing needs to be done with the output which is automatically added if this comes
     * from a {@link #state(Observable)} or {@link #object(String, Observable, Geometry)} call.
     *
     * @return
     */
    Data build();
  }

  /**
   * The name. Never null; in quality observations, it will be the URN of the observable or the
   * stated name if there is one.
   *
   * @return
   */
  String name();

  /**
   * The observable URN. Never null.
   *
   * @return
   */
  String semantics();

  /**
   * The geometry. Never null.
   *
   * @return
   */
  Geometry geometry();

  /**
   * Metadata. Possibly empty, never null.
   *
   * @return
   */
  Metadata metadata();

  /**
   * If empty, the data cannot be used. Normally there will be notifications explaining why.
   *
   * @return
   */
  boolean empty();

  /**
   * Any notifications added. If any notification is ERROR level, empty() will be true.
   *
   * @return
   */
  List<Notification> notifications();

  /**
   * The objects returned from a data object whose observable is countable and collective. This
   * returns any object instances AND any state instances. The only situation that cannot happen is
   * that the data resulting from contextualizing a quality contain objects as children.
   *
   * <p>States result from a data object whose observable is a quality. There may be states also in
   * the result of contextualization of a process or a non-collective observation; a collective
   * observation may also produce states along with objects, which should be linked to the context
   * observation. If the contextualization is for a quality, the first state should normally be the
   * observable requested, and other ancillary observations may have been produced if requested
   * through an observation constraint.
   *
   * <p>Each returned object will implement one of the {@link java.util.PrimitiveIterator} classes.
   * A class switch should be used along with the {@link #fillCurve()} to transfer the data to the
   * storage, filtering through the {@link #dataKey()} if appropriate.
   *
   * @return
   */
  List<Data> children();

  /**
   * Annotations are important because they contain indications re: fill curve, splits and any
   * runtime configuration. The key annotations for qualities are <code>fillcurve</code> and <code>
   * split</code>.
   *
   * <p>TODO expose annotation names and methods so they are recognized and validated at the API
   * level
   *
   * @return
   */
  Collection<Annotation> annotations();

  /**
   * This is not null only when the observable is a categorical quality, i.e its {@link
   * org.integratedmodelling.klab.api.knowledge.DescriptionType} is {@link
   * org.integratedmodelling.klab.api.knowledge.DescriptionType#CATEGORIZATION}. In this case the
   * data object will implement {@link java.util.PrimitiveIterator.OfInt} and can be iterated to
   * extract the categories.
   *
   * @return
   */
  Map<Integer, String> dataKey();

  /**
   * The number of objects returned by {@link #children()} in object data, or the number of values
   * in states, or both. If size() == 0 the data specify no child observations. The observable
   * should be the guide in asking the right questions about the data.
   *
   * @return
   */
  long size();

  /**
   * True if the instance contains state values. In that case it will need to be cast to the
   * appropriate primivite iterator to obtain the data.
   *
   * @return
   */
  boolean hasStates();

  static Data.Builder builder(String name, Observable observable, Geometry geometry) {
    Klab.Configuration configuration = Klab.INSTANCE.getConfiguration();
    if (configuration == null) {
      throw new KlabIllegalStateException(
          "k.LAB environment not configured to create a data builder");
    }
    return configuration.getDataBuilder(name, observable, geometry);
  }

  static Data empty(Notification notification) {

    return new Data() {
      @Override
      public String name() {
        return "unknown";
      }

      @Override
      public String semantics() {
        return "owl:Nothing";
      }

      @Override
      public Geometry geometry() {
        return Geometry.EMPTY;
      }

      @Override
      public Metadata metadata() {
        return Metadata.create();
      }

      @Override
      public boolean empty() {
        return true;
      }

      @Override
      public List<Notification> notifications() {
        return List.of(notification);
      }

      @Override
      public List<Data> children() {
        return List.of();
      }

      @Override
      public Collection<Annotation> annotations() {
        return List.of();
      }

      @Override
      public Map<Integer, String> dataKey() {
        return Map.of();
      }

      @Override
      public long size() {
        return 0;
      }

      @Override
      public boolean hasStates() {
        return false;
      }
    };
  }
}
