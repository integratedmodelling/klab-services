package org.integratedmodelling.klab.api.data;

import org.integratedmodelling.klab.api.Klab;
import org.integratedmodelling.klab.api.exceptions.KlabIllegalStateException;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.api.scope.Scope;
import org.integratedmodelling.klab.api.services.runtime.Notification;

import java.util.List;
import java.util.Map;

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
   * Any of the space-filling curves can be used in the data encoding. Each state with multiple
   * values must define the curve it uses. Normally these are used for 2D space but there may be 3D
   * and others in the future, so extend as needed.
   */
  enum FillCurve {
    S1_LINEAR,
    S2_XY,
    S2_YX,
    S2_SIERPINSKI_3,
    S2_HILBERT
    // ... TODO more as needed. Sierpinski can have different orders; the arrowhead can be extended
    // to 3D
  }

  interface Filler {}

  @FunctionalInterface
  interface IntFiller extends Filler {
    void add(int value);
  }

  @FunctionalInterface
  interface FloatFiller extends Filler {
    void add(float value);
  }

  @FunctionalInterface
  interface BooleanFiller extends Filler {
    void add(boolean value);
  }

  @FunctionalInterface
  interface DoubleFiller extends Filler {
    void add(double value);
  }

  @FunctionalInterface
  interface KeyedFiller extends Filler {
    void add(Object value);
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
     * Return a filler for the quality data object being built. Will throw an exception if the
     * observable is not a quality and the class is not compatible with the observable's {@link
     * org.integratedmodelling.klab.api.knowledge.DescriptionType}.
     *
     * @param fillerClass
     * @return
     * @param <T>
     */
    <T extends Filler> T filler(Class<T> fillerClass);

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
   * States in a data object whose observable is a quality. There may be states also in the result
   * of contextualization of a process or a non-collective observation. If the contextualization is
   * for a quality, the first state should normally be the observable requested, and other ancillary
   * observations may have been produced if requested through an observation constraint.
   *
   * <p>Each returned object will implement one of the {@link java.util.PrimitiveIterator} classes.
   * A class switch should be used along with the {@link #fillCurve()} to transfer the data to the
   * storage, filtering through the {@link #dataKey()} if appropriate.
   *
   * @return
   */
  List<Data> states();

  /**
   * The objects returned from a data object whose observable is countable and collective.
   *
   * @return
   */
  List<Data> objects();

  /**
   * Mandatory in data objects that represent states, null otherwise. Return the desired fill curve.
   *
   * @return
   */
  FillCurve fillCurve();

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
   * The number of objects returned by {@link #objects()} in object data, or the number of values in
   * states. The observable should be the guide in asking the right questions about the data.
   *
   * @return
   */
  long size();

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
      public List<Data> states() {
        return List.of();
      }

      @Override
      public List<Data> objects() {
        return List.of();
      }

      @Override
      public FillCurve fillCurve() {
        return null;
      }

      @Override
      public Map<Integer, String> dataKey() {
        return Map.of();
      }

      @Override
      public long size() {
        return 0;
      }
    };
  }
}
