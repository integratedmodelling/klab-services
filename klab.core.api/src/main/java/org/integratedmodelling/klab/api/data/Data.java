package org.integratedmodelling.klab.api.data;

import java.util.List;
import java.util.Map;
import java.util.PrimitiveIterator;
import org.integratedmodelling.klab.api.exceptions.KlabIllegalStateException;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.knowledge.Concept;
import org.integratedmodelling.klab.api.knowledge.DescriptionType;
import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.api.services.resources.adapters.Adapter;
import org.integratedmodelling.klab.api.services.runtime.Actuator;
import org.integratedmodelling.klab.api.services.runtime.Notification;

/**
 * The <code>Data</code> object encapsulates the network-transmissible data package specified
 * through the Avro schema and understood by all k.LAB services. If the {@link Adapter} used is
 * available locally, no network transmission will happen. A Data object must be created with a
 * name, an Observable and a Geometry.
 *
 * <p>A Data object that wraps a quality observation will be unmarshalled with an appropriate
 * subclass implementing one of the {@link PrimitiveIterator} interfaces, so that the numbers can be
 * extracted as needed without boxing as long as the primitive iterator methods are used. In the
 * case of a category quality, the object will implement {@link PrimitiveIterator.OfInt} and the
 * dataKey will be instantiated as well so that the (Integer) numbers can be translated to the
 * needed objects, normally {@link Concept} instances.
 */
public interface Data {

  /**
   * This collects the information related to how the data should be either stored in memory or
   * remapped for access by specific contextualizers or adapters, enabling the creation of
   * independent buffers for parallelization based on the type of contextualization. It is created
   * by the resolver after collecting the info from the computation (adapter or contextualizer) and
   * compounding it with any overrides from model/observable annotations and/or runtime
   * configuration. The finalized distribution strategy is included in the {@link
   * Actuator}s produced by the resolver for any
   * quality observations.
   *
   * <p>TODO we should also enable a split based on a collective concept used as spatial/temporal
   * context for individual computations. This would trigger resolution at the resolver side. It
   * would be specified in a model using an annotation like <code>
   * @split({{each hydrology:RiverBasin}})</code> on a model.
   *
   * - curve the fill curve providing meaning for the sequence of data in storage
   * - suggestedSplits the number of splits suggested for data parallelization, with -1 for
   *     arbitrary and 1 for no splits.
   * - minSplitSize minimum geometry size for a buffer when splits are requested
   * - maxBufferSize maximum size for the overall data operation to apply. If the geometry is
   *     larger than this, the adapter or contextualizer will be rejected by the resolver.
   * - dataType the requested or native data type for the operation.
   */
  class ShardingStrategy {

    private FillCurve curve = FillCurve.UNSPECIFIED;
    private int suggestedSplits = -1;
    private long minSplitSize = 0;
    private long maxBufferSize = 0;
    private Storage.Type dataType;

    public ShardingStrategy() {}

    public ShardingStrategy(
        FillCurve curve,
        int suggestedSplits,
        long minSplitSize,
        long maxBufferSize,
        Storage.Type dataType) {
      this.curve = curve;
      this.suggestedSplits = suggestedSplits;
      this.minSplitSize = minSplitSize;
      this.maxBufferSize = maxBufferSize;
      this.dataType = dataType;
    }

    /**
     * Neutral sharding strategy that won't override anything when merged with another.
     *
     * @return
     */
    public static ShardingStrategy neutral() {
      return new ShardingStrategy();
    }

    /**
     * Return a trivial, linear-filling, non-parallelizing, all-accepting sharding strategy for the
     * passed storage type. For testing purposes.
     *
     * @param dataType
     * @return
     */
    public static ShardingStrategy trivial(Storage.Type dataType) {
      var ret = new ShardingStrategy();
      ret.curve = FillCurve.D1_LINEAR;
      ret.suggestedSplits = 1;
      ret.minSplitSize = 0;
      ret.maxBufferSize = 0;
      ret.dataType = dataType;
      return ret;
    }

    public FillCurve getCurve() {
      return curve;
    }

    public void setCurve(FillCurve curve) {
      this.curve = curve;
    }

    public int getSuggestedSplits() {
      return suggestedSplits;
    }

    public void setSuggestedSplits(int suggestedSplits) {
      this.suggestedSplits = suggestedSplits;
    }

    public long getMinSplitSize() {
      return minSplitSize;
    }

    public void setMinSplitSize(long minSplitSize) {
      this.minSplitSize = minSplitSize;
    }

    public long getMaxBufferSize() {
      return maxBufferSize;
    }

    public void setMaxBufferSize(long maxBufferSize) {
      this.maxBufferSize = maxBufferSize;
    }

    public Storage.Type getDataType() {
      return dataType;
    }

    public void setDataType(Storage.Type dataType) {
      this.dataType = dataType;
    }

    private ShardingStrategy copy() {
      return new ShardingStrategy(
          this.curve, this.suggestedSplits, this.minSplitSize, this.maxBufferSize, this.dataType);
    }

    /**
     * Adjust the strategy to reflect the passed others, which may override the current values. The
     * passed others are in order of precedence: the one after the first overrides the previous.
     * Overriding may only happen if compatible (e.g. the data type).
     *
     * @param others
     * @return
     */
    public ShardingStrategy override(ShardingStrategy... others) {
      var ret = this.copy();
      if (others != null) {
        for (var other : others) {
          if (other != null) {
            if (ret.dataType == null) {
              ret.dataType = other.dataType;
            } else if (other.dataType != null && ret.dataType != other.dataType) {
              if (ret.dataType.isNumber() && other.dataType.isNumber()) {
                ret.dataType = other.dataType;
              } else {
                throw new IllegalArgumentException(
                    "Incompatible data types: " + ret.dataType + " vs. " + other.dataType);
              }
            }
            if (ret.curve == FillCurve.UNSPECIFIED || other.curve != FillCurve.UNSPECIFIED) {
              ret.curve = other.curve;
            }
            if (ret.suggestedSplits == -1 || other.suggestedSplits != -1) {
              ret.suggestedSplits = other.suggestedSplits;
            }
            if (ret.minSplitSize == 0 || other.suggestedSplits != 0) {
              ret.minSplitSize = other.minSplitSize;
            }
            if (ret.maxBufferSize == 0 || other.maxBufferSize != 0) {
              ret.maxBufferSize = other.maxBufferSize;
            }
          }
        }
      }
      return ret;
    }

    public boolean equals(ShardingStrategy other) {
      if (other == null) return false;
      return suggestedSplits == other.suggestedSplits
          && minSplitSize == other.minSplitSize
          && maxBufferSize == other.maxBufferSize
          && curve == other.curve
          && dataType == other.dataType;
    }

    /**
     * Return the base interface for the storage scanner adopting this strategy
     *
     * @return
     */
    public Class<? extends Storage.Scanner> getScannerClass() {
      if (dataType == null) {
        return null;
      }
      return switch (dataType) {
        case DOUBLE -> Storage.DoubleScanner.class;
        case FLOAT -> Storage.FloatScanner.class;
        case INTEGER -> Storage.IntScanner.class;
        case LONG -> Storage.LongScanner.class;
        case KEYED -> Storage.KeyScanner.class;
        case BOOLEAN -> Storage.BooleanScanner.class;
      };
    }
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
  enum FillCurve {

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

    FillCurve(int dimensions) {
      this.dimensions = dimensions;
    }

    /**
     * Map a number of steps along this space-filling curve to the linear offset in a row-major
     * linear array representing an n-dimensional matrix with the provided sizes.
     *
     * <p>Sizes length must match this curve's dimensionality, except for UNSPECIFIED which is
     * treated as a generic row-major traversal of any dimensionality (offset == normalized steps).
     *
     * @param steps number of steps along the curve (can be negative or exceed total cells)
     * @param sizes sizes of each dimension; length must equal dimensions (unless UNSPECIFIED)
     * @return the linear offset in row-major order corresponding to walking {@code steps} along the
     *     specified curve starting from 0
     */
    public long offset(long steps, long[] sizes) {
      if (sizes == null || sizes.length == 0) {
        throw new IllegalArgumentException("sizes must be a non-empty array");
      }
      if (this != UNSPECIFIED && sizes.length != this.dimensions) {
        throw new IllegalArgumentException(
            "sizes length ("
                + sizes.length
                + ") must equal curve dimensions ("
                + this.dimensions
                + ")");
      }
      for (long s : sizes) {
        if (s <= 0) {
          throw new IllegalArgumentException("all sizes must be > 0");
        }
      }

      long total = product(sizes);
      if (total == 0) {
        return 0L;
      }
      long nsteps = positiveMod(steps, total);

      switch (this) {
        case UNSPECIFIED:
        case D1_LINEAR:
        case D2_XY:
        case D3_XYZ:
          // Row-major traversal: last index fastest; offset equals normalized steps
          return nsteps;

        case D2_YX:
          {
            // Scan order: Y slowest, X fastest
            int[] order = new int[] {1, 0};
            long[] coords = coordsFromStepByOrder(nsteps, sizes, order);
            return flattenRowMajor(coords, sizes);
          }
        case D2_XInvY:
          {
            // Row-major scanning but Y inverted within each X block
            long[] coords = coordsFromStepByOrder(nsteps, sizes, new int[] {0, 1});
            coords[1] = sizes[1] - 1 - coords[1];
            return flattenRowMajor(coords, sizes);
          }
        case D3_ZYX:
          {
            // For offset(), 3D variants fall back to row-major: return normalized steps
            return nsteps;
          }
        case D2_HILBERT:
        case D3_HILBERT:
          throw new UnsupportedOperationException(
              "Hilbert curve offset mapping not implemented in this method");
        default:
          // Should not happen
          return nsteps;
      }
    }

    /**
     * Complement to {@link #offset(long, long[])}: returns the per-dimension coordinates reached
     * after walking the given number of steps along this curve. Coordinates are in the same order
     * as sizes: index 0 refers to the first dimension, etc. Steps are normalized to [0, total).
     *
     * <p>For UNSPECIFIED, behaves as a generic row-major traversal for the provided dimensionality.
     *
     * @param steps number of steps along the curve (can be negative or exceed total cells)
     * @param sizes sizes of each dimension; length must equal dimensions (unless UNSPECIFIED)
     * @return array of length sizes.length containing coordinates in each dimension
     */
    public long[] offsets(long steps, long[] sizes) {
      if (sizes == null || sizes.length == 0) {
        throw new IllegalArgumentException("sizes must be a non-empty array");
      }
      if (this != UNSPECIFIED && sizes.length != this.dimensions) {
        throw new IllegalArgumentException(
            "sizes length ("
                + sizes.length
                + ") must equal curve dimensions ("
                + this.dimensions
                + ")");
      }
      for (long s : sizes) {
        if (s <= 0) {
          throw new IllegalArgumentException("all sizes must be > 0");
        }
      }

      long total = product(sizes);
      if (total == 0) {
        return new long[sizes.length];
      }
      long nsteps = positiveMod(steps, total);

      switch (this) {
        case UNSPECIFIED:
        case D1_LINEAR:
        case D2_XY:
        case D3_XYZ:
          {
            int n = sizes.length;
            int[] order = new int[n];
            for (int i = 0; i < n; i++) order[i] = i; // row-major (0 slowest -> last fastest)
            return coordsFromStepByOrder(nsteps, sizes, order);
          }
        case D2_YX:
          {
            return coordsFromStepByOrder(nsteps, sizes, new int[] {1, 0});
          }
        case D2_XInvY:
          {
            long[] coords = coordsFromStepByOrder(nsteps, sizes, new int[] {0, 1});
            coords[1] = sizes[1] - 1 - coords[1];
            return coords;
          }
        case D3_ZYX:
          {
            // For offsets(), 3D variants fall back to row-major coordinates as well
            int n = sizes.length;
            int[] order = new int[n];
            for (int i = 0; i < n; i++) order[i] = i;
            return coordsFromStepByOrder(nsteps, sizes, order);
          }
        case D2_HILBERT:
        case D3_HILBERT:
          throw new UnsupportedOperationException("Hilbert curve offsets not implemented");
        default:
          {
            int n = sizes.length;
            int[] order = new int[n];
            for (int i = 0; i < n; i++) order[i] = i;
            return coordsFromStepByOrder(nsteps, sizes, order);
          }
      }
    }

    private static long positiveMod(long a, long m) {
      long r = a % m;
      return r < 0 ? r + m : r;
    }

    private static long product(long[] sizes) {
      long p = 1L;
      for (long s : sizes) {
        p *= s;
      }
      return p;
    }

    /** Given a step count and a scan order (slowest to fastest), compute coordinates. */
    private static long[] coordsFromStepByOrder(long step, long[] sizes, int[] orderSlowToFast) {
      int n = sizes.length;
      if (orderSlowToFast.length != n) {
        throw new IllegalArgumentException("order length must match sizes length");
      }
      long[] coords = new long[n];
      long t = step;
      for (int i = orderSlowToFast.length - 1; i >= 0; i--) {
        int d = orderSlowToFast[i];
        long size = sizes[d];
        coords[d] = t % size;
        t /= size;
      }
      return coords;
    }

    /** Flatten coordinates to a row-major linear offset (last dimension fastest). */
    private static long flattenRowMajor(long[] coords, long[] sizes) {
      long offset = 0L;
      long stride = 1L;
      for (int i = sizes.length - 1; i >= 0; i--) {
        offset += coords[i] * stride;
        stride *= sizes[i];
      }
      return offset;
    }

    /** Compute the step index along a scan order (slowest to fastest) given coordinates. */
    private static long stepFromCoordsByOrder(long[] coords, long[] sizes, int[] orderSlowToFast) {
      if (coords.length != sizes.length || orderSlowToFast.length != sizes.length) {
        throw new IllegalArgumentException("dimensions mismatch in stepFromCoordsByOrder");
      }
      long t = 0L;
      for (int i = 0; i < orderSlowToFast.length; i++) {
        int d = orderSlowToFast[i];
        long size = sizes[d];
        long c = coords[d];
        if (c < 0 || c >= size) {
          throw new IllegalArgumentException("coordinate out of bounds for dimension " + d);
        }
        t = t * size + c;
      }
      return t;
    }

    /**
     * Remap an offset (step index) taken along this curve into the corresponding step index along
     * the destination curve, preserving the same n-dimensional coordinates.
     */
    public long map(int offset, long[] originalSizes, FillCurve destination) {
      if (originalSizes == null || originalSizes.length == 0) {
        throw new IllegalArgumentException("sizes must be a non-empty array");
      }
      if (this != UNSPECIFIED && originalSizes.length != this.dimensions) {
        throw new IllegalArgumentException(
            "sizes length ("
                + originalSizes.length
                + ") must equal source curve dimensions ("
                + this.dimensions
                + ")");
      }
      if (destination == null) {
        throw new IllegalArgumentException("destination curve must not be null");
      }
      if (destination != UNSPECIFIED && originalSizes.length != destination.dimensions) {
        throw new IllegalArgumentException(
            "sizes length ("
                + originalSizes.length
                + ") must equal destination curve dimensions ("
                + destination.dimensions
                + ")");
      }
      for (long s : originalSizes) {
        if (s <= 0) {
          throw new IllegalArgumentException("all sizes must be > 0");
        }
      }

      long total = product(originalSizes);
      if (total == 0) {
        return 0L;
      }
      long nsteps = positiveMod(offset, total);

      // Compute coordinates from this curve
      long[] coords;
      switch (this) {
        case UNSPECIFIED:
        case D1_LINEAR:
        case D2_XY:
        case D3_XYZ:
          {
            int n = originalSizes.length;
            int[] order = new int[n];
            for (int i = 0; i < n; i++) order[i] = i; // row-major order
            coords = coordsFromStepByOrder(nsteps, originalSizes, order);
            break;
          }
        case D2_YX:
          {
            coords = coordsFromStepByOrder(nsteps, originalSizes, new int[] {1, 0});
            break;
          }
        case D2_XInvY:
          {
            coords = coordsFromStepByOrder(nsteps, originalSizes, new int[] {0, 1});
            coords[1] = originalSizes[1] - 1 - coords[1];
            break;
          }
        case D3_ZYX:
          {
            int n = originalSizes.length;
            int[] order = new int[n];
            for (int i = 0; i < n; i++) order[i] = i; // treat as row-major for mapping as well
            coords = coordsFromStepByOrder(nsteps, originalSizes, order);
            break;
          }
        case D2_HILBERT:
        case D3_HILBERT:
          throw new UnsupportedOperationException("Hilbert curve mapping not implemented");
        default:
          int n = originalSizes.length;
          int[] order = new int[n];
          for (int i = 0; i < n; i++) order[i] = i;
          coords = coordsFromStepByOrder(nsteps, originalSizes, order);
      }

      // Compute destination step from coordinates
      long destStep;
      switch (destination) {
        case UNSPECIFIED:
        case D1_LINEAR:
        case D2_XY:
        case D3_XYZ:
          {
            int n = originalSizes.length;
            int[] order = new int[n];
            for (int i = 0; i < n; i++) order[i] = i; // row-major
            destStep = stepFromCoordsByOrder(coords, originalSizes, order);
            break;
          }
        case D2_YX:
          {
            destStep = stepFromCoordsByOrder(coords, originalSizes, new int[] {1, 0});
            break;
          }
        case D2_XInvY:
          {
            long[] c2 = coords.clone();
            c2[1] = originalSizes[1] - 1 - c2[1];
            destStep = stepFromCoordsByOrder(c2, originalSizes, new int[] {0, 1});
            break;
          }
        case D3_ZYX:
          {
            int n = originalSizes.length;
            int[] order = new int[n];
            for (int i = 0; i < n; i++) order[i] = i; // row-major destination for ZYX variant
            destStep = stepFromCoordsByOrder(coords, originalSizes, order);
            break;
          }
        case D2_HILBERT:
        case D3_HILBERT:
          throw new UnsupportedOperationException("Hilbert curve mapping not implemented");
        default:
          {
            int n = originalSizes.length;
            int[] order = new int[n];
            for (int i = 0; i < n; i++) order[i] = i;
            destStep = stepFromCoordsByOrder(coords, originalSizes, order);
          }
      }

      // Normalize to total just in case and return
      return positiveMod(destStep, total);
    }

    public static FillCurve defaultCurve(Geometry geometry) {
      var space =
          geometry.getDimensions().stream()
              .filter(d -> d.getType() == Geometry.Dimension.Type.SPACE)
              .findFirst();

      return space
          .map(
              dimension ->
                  switch (dimension.getDimensionality()) {
                    case 2 -> FillCurve.D2_XY;
                    case 3 -> FillCurve.D3_XYZ;
                    default -> FillCurve.D1_LINEAR;
                  })
          .orElse(FillCurve.D1_LINEAR);
    }
  }

  /**
   * A data builder encodes data content, either in raw binary form or by configuring an adapter.
   */
  interface Builder {

    /**
     * Add the passed notification. Returns self.
     *
     * @param notification the notification to add
     * @return this builder instance for method chaining
     */
    Builder notification(Notification notification);

    /**
     * Pass the ID of an adapter that will interpret the contents. If not passed, raw binary content
     * is assumed. If passed, the data object built may contain only references to the configuration
     * of the identified adapter.
     *
     * @param adapterId
     * @return
     */
    Builder adapter(String adapterId);

    /**
     * Add the passed non-semantic metadata. Can be used for properties when no data content is
     * added but an adapter is used. Returns self.
     *
     * @param key the metadata key
     * @param value the metadata value
     * @return this builder instance for method chaining
     */
    Builder metadata(String key, Object value);

    /**
     * Returns a builder specialized for a secondary observation identified by a <em>known</em>
     * identifier. Using this is only necessary if anything must be set for the observation besides
     * the values, such as metadata. Otherwise the scanner can simply be retrieved directly using
     * {@link #scanner(String, Class)}.
     *
     * @return a new builder for the state
     */
    Builder state(String outputId);

    /**
     * Returns a new builder for an object, on which build() must be called to confirm the
     * transaction. The API ensures that the object is sound after this call, but the builder can be
     * used to add metadata, states or child objects.
     *
     * @param name the name of the object
     * @param observable the observable for the object FIXME should not be necessary?
     * @param geometry the geometry for the object
     * @return a new builder for the object
     */
    Builder object(String name, Observable observable, Geometry geometry);

    /**
     * Create a scanner of the specified type using the filling curve and geometry declared for the
     * adapter or method. The scanner will be matched to the geometry being contextualized and will
     * refer to the main output.
     *
     * @param scannerClass the class of scanner to create
     * @return a single scanner of the specified type using the specified space filling curve
     * @param <T> the type of scanner desired
     * @throws KlabIllegalStateException if the artifact type is incompatible with the requested
     *     scanner class
     */
    <T extends Storage.Shard> T scanner(Class<T> scannerClass);

    /**
     * Return a scanner of the specified type for an additional output or for an input, using the
     * filling curve and geometry declared for the adapter or method. The identifier will be matched
     * to the inputs and additional outputs declared for the contextualizer being used, and an
     * exception will be thrown if the correspondent observation is not understood. If the scanner
     * refers to an input observation, any set operations called on it will throw an exception.
     *
     * <p>Scanners can be retrieved by an instance returned by state() if anything besides the
     * values must be set in the observation.
     *
     * @param identifier
     * @param scannerClass
     * @return
     * @param <T>
     */
    <T extends Storage.Shard> T scanner(String identifier, Class<T> scannerClass);

    /**
     * Must be called on any secondary builders. Should NOT be called on the root builder, passed to
     * encoders. Nothing needs to be done with the output which is automatically added if this comes
     * from a {@link #state(Observable)} or {@link #object(String, Observable, Geometry)} call.
     *
     * @return the built Data object
     */
    Data build();
  }

  /**
   * The name. Never null; in quality observations, it will be the URN of the observable or the
   * stated name if there is one.
   *
   * @return the name of this data object
   */
  String name();

  /**
   * The observable URN. Never null.
   *
   * @return the URN of the observable associated with this data
   */
  String semantics();

  /**
   * The geometry. Never null.
   *
   * @return the geometry of this data object
   */
  Geometry geometry();

  /**
   * Metadata. Possibly empty, never null.
   *
   * @return the metadata associated with this data object
   */
  Metadata metadata();

  /**
   * If empty, the data cannot be used. Normally there will be notifications explaining why.
   *
   * @return true if this data object is empty and cannot be used, false otherwise
   */
  boolean empty();

  /**
   * Any notifications added. If any notification is ERROR level, empty() will be true.
   *
   * @return a list of notifications associated with this data object
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
   * <p>Each returned object will implement one of the {@link PrimitiveIterator} classes. A class
   * switch should be used along with the fill curve to transfer the data to the storage, filtering
   * through the {@link #dataKey()} if appropriate.
   *
   * @return a list of child data objects
   */
  List<Data> children();

  //  /**
  //   * Annotations are important because they contain indications re: fill curve, splits and any
  //   * runtime configuration. The key annotations for qualities are <code>fillcurve</code> and
  // <code>
  //   * split</code>.
  //   *
  //   * <p>TODO expose annotation names and methods so they are recognized and validated at the API
  //   * level
  //   *
  //   * @return a collection of annotations associated with this data object
  //   */
  //  Collection<Annotation> annotations();

  /**
   * This is not null only when the observable is a categorical quality, i.e its {@link
   * DescriptionType} is {@link DescriptionType#CATEGORIZATION}. In this case the data object will
   * implement {@link PrimitiveIterator.OfInt} and can be iterated to extract the categories.
   *
   * @return a map of integer keys to category string values
   */
  Map<Integer, String> dataKey();

  /**
   * The number of objects returned by {@link #children()} in object data, or the number of values
   * in states, or both. If size() == 0 the data specify no child observations. The observable
   * should be the guide in asking the right questions about the data.
   *
   * @return the number of objects or values in this data object
   */
  long size();

  /**
   * True if the instance contains state values. In that case it will need to be cast to the
   * appropriate primivite iterator to obtain the data.
   *
   * @return true if this data object contains state values, false otherwise
   */
  boolean hasStates();

  //  static Data.Builder builder(String name, Observable observable, Geometry geometry) {
  //    Klab.Configuration configuration = Klab.INSTANCE.getConfiguration();
  //    if (configuration == null) {
  //      throw new KlabIllegalStateException(
  //          "k.LAB environment not configured to create a data builder");
  //    }
  //    return configuration.getDataBuilder(name, observable, geometry);
  //  }

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
        return Geometry.UNIVERSAL;
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

      //      @Override
      //      public Collection<Annotation> annotations() {
      //        return List.of();
      //      }

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
