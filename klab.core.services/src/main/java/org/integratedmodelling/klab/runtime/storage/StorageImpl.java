package org.integratedmodelling.klab.runtime.storage;

import com.dynatrace.dynahist.layout.Layout;
import com.dynatrace.dynahist.layout.OpenTelemetryExponentialBucketsLayout;
import java.io.Serial;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListMap;
import org.integratedmodelling.common.knowledge.GeometryRepository;
import org.integratedmodelling.klab.api.data.Data;
import org.integratedmodelling.klab.api.data.Geometries;
import org.integratedmodelling.klab.api.data.Histogram;
import org.integratedmodelling.klab.api.data.Storage;
import org.integratedmodelling.klab.api.data.mediation.classification.DataKey;
import org.integratedmodelling.klab.api.digitaltwin.GraphModel;
import org.integratedmodelling.klab.api.digitaltwin.Scheduler;
import org.integratedmodelling.klab.api.exceptions.KlabIllegalStateException;
import org.integratedmodelling.klab.api.exceptions.KlabUnimplementedException;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.knowledge.observation.scale.Scale;
import org.integratedmodelling.klab.api.knowledge.observation.scale.time.Time;
import org.integratedmodelling.klab.api.knowledge.observation.scale.time.TimeInstant;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.scope.ServiceScope;
import org.integratedmodelling.klab.common.data.impl.ShardImpl;
import org.integratedmodelling.klab.runtime.language.ScannerAdapters;
import org.integratedmodelling.klab.utilities.Utils;
import org.ojalgo.array.BufferArray;

public class StorageImpl implements Storage {

  private final Observation observation;
  private final ContextScope scope;
  private Data.ShardingStrategy nativeShardingStrategy;
  private final StorageManagerImpl storageManager;
  private DataKey dataKey;

  /**
   * Used as a key for the geometry-aware shard cache. In all current implementations, the key will
   * be a single long for time, but this future-proofs the implementation for more dimensions in the
   * geometry.
   */
  private static class ComparableLongList extends ArrayList<Long>
      implements Comparable<ComparableLongList> {
    @Serial private static final long serialVersionUID = 1L;

    public ComparableLongList() {
      super();
    }

    public ComparableLongList(List<Long> key) {
      super(key);
    }

    @Override
    public int compareTo(ComparableLongList other) {
      int size = Math.min(this.size(), other.size());
      for (int i = 0; i < size; i++) {
        int comparison = Long.compare(this.get(i), other.get(i));
        if (comparison != 0) {
          return comparison;
        }
      }
      return Integer.compare(this.size(), other.size());
    }
  }

  class ShardStorage {
    private final Shard shard;
    private final StorageManagerImpl storage;
    private com.dynatrace.dynahist.Histogram histogram;
    private final BufferArray data;

    ShardStorage(Shard shard, StorageManagerImpl storage) {
      this.shard = shard;
      this.storage = storage;
      resetHistogram();
      this.data =
          switch (shard.getShardingStrategy().getDataType()) {
            case DOUBLE -> storage.getDoubleBuffer(shard.getGeometry().size());
            case FLOAT -> storage.getFloatBuffer(shard.getGeometry().size());
            case INTEGER, KEYED -> storage.getIntBuffer(shard.getGeometry().size());
            case BOOLEAN -> storage.getBooleanBuffer(shard.getGeometry().size());
            case LONG -> storage.getLongBuffer(shard.getGeometry().size());
          };
    }

    void resetHistogram() {
      this.histogram =
          storage.isRecordHistogram()
              ? com.dynatrace.dynahist.Histogram.createDynamic(
                  histogramLayout(observation.getObservable()))
              : null;
      if (shard instanceof ShardImpl shardImpl) {
        shardImpl.setHistogram(null);
      }
    }

    void rebuildHistogram() {
      resetHistogram();
      if (histogram == null) {
        return;
      }
      for (long i = 0; i < data.count(); i++) {
        switch (shard.getNativeType()) {
          case DOUBLE -> addToHistogram(histogram, data.doubleValue(i));
          case FLOAT -> addToHistogram(histogram, data.floatValue(i));
          case INTEGER, KEYED -> addToHistogram(histogram, data.intValue(i));
          case LONG -> addToHistogram(histogram, data.longValue(i));
          case BOOLEAN -> addToHistogram(histogram, data.byteValue(i) == 0 ? 0 : 1);
        }
      }
    }

    public void close() {
      this.data.close();
    }
  }

  private final Map<String, ShardStorage> shardStorage = new ConcurrentHashMap<>();

  /*
   * Buffer storage along slowest-varying dimensions. All dimensions except one (space) must have
   * linear indexing and come from the scheduler event that serves as an index.
   */
  private NavigableMap<ComparableLongList, List<Shard>> shards = new ConcurrentSkipListMap<>();

  /**
   * Create the storage container for the observation according to the observation's own sharding
   * strategy, which determines the specific native type for numeric observations, and all options
   * related to storage and distribution. Shards are created upon first access and reinterpreted
   * according to the requesting sharding strategy.
   *
   * <p>TODO this must have a persistence strategy
   *
   * @param observation
   * @param contextScope
   */
  public StorageImpl(
      Observation observation,
      Data.ShardingStrategy shardingStrategy,
      ContextScope contextScope,
      StorageManagerImpl storageManager) {
    this.observation = observation;
    this.scope = contextScope;
    this.storageManager = storageManager;
    this.nativeShardingStrategy = shardingStrategy;

    /*
     * If the observation comes from the KG, we load any pre-existing shards into lazy containers.
     */
    if (observation.getId() > 0) {
      for (var shard :
          contextScope
              .getDigitalTwin()
              .getKnowledgeGraph()
              .query(Shard.class, contextScope)
              .source(observation)
              .along(GraphModel.Relationship.HAS_DATA)
              .run(contextScope)) {
        if (shard.getGeometry() == null && shard instanceof ShardImpl shardImpl) {
          shardImpl.setGeometry(GeometryRepository.INSTANCE.geometry(observation.getGeometry()));
        }
        var time = TimeInstant.create(shard.getTimestamp());
        var scale = GeometryRepository.INSTANCE.scale(observation.getGeometry()).at(time);
        var key =
            scale.getExtents().stream()
                // TODO generalize: remove the moving dimension in the geometry - no scenarios so
                // far
                //  under which it's not space.
                .filter(e -> e.getType() != Geometry.Dimension.Type.SPACE)
                .map(
                    e ->
                        e.getType() == Geometry.Dimension.Type.TIME
                            ? shard.getTimestamp()
                            : /* TODO use the index for any further dimension, unused for now */ 0L)
                .toList();
        shards.computeIfAbsent(new ComparableLongList(key), k -> new ArrayList<>()).add(shard);
      }
    }

    // TODO prepare shard descriptors from any existing shards in the knowledge graph! If there is a
    // different sharding strategy, adopt that as native and create mediators.
  }

  // used only for testing, won't work for anything else
  private StorageImpl() {
    observation = null;
    scope = null;
    nativeShardingStrategy = Data.ShardingStrategy.trivial(Type.DOUBLE);
    storageManager = null;
  }

  @Override
  public Type getNativeType() {
    return nativeShardingStrategy.getDataType();
  }

  /**
   * Generate the best-case scenario for an overall geometry according to preferences set in the
   * adapter or contextualizer.
   *
   * @param original
   * @param desiredSplits
   * @param minSize
   * @param maxSize
   * @return
   */
  private List<Geometry> getGeometries(
      Geometry original, int desiredSplits, long minSize, long maxSize) {

    if (desiredSplits == 1) {
      return List.of(original);
    }
    var splits = desiredSplits;
    if (splits <= 0) {
      long effectiveMinSize = Math.max(1, minSize);
      var dsplits = Math.max(1, original.size() / effectiveMinSize);
      while (maxSize > 0 && original.size() / dsplits > maxSize) {
        dsplits *= 2;
      }
      splits = (int) Math.min(Integer.MAX_VALUE, dsplits);
    }

    return original.split(splits);
  }

  @Override
  public List<Shard> getNativeShards(Scheduler.Event event) {

    var time = event.getTime();
    if (time.size() != 1) {
      throw new KlabUnimplementedException(
          "Multiple time steps for a buffer request during contextualization");
    }

    var scale = GeometryRepository.INSTANCE.scale(observation.getGeometry()).at(time);
    long timeStart = time.is(Time.Type.INITIALIZATION) ? 0 : time.getStart().getMilliseconds();
    var key =
        scale.getExtents().stream()
            // TODO generalize: remove the moving dimension in the geometry - no scenarios so far
            //  under which it's not space.
            .filter(e -> e.getType() != Geometry.Dimension.Type.SPACE)
            .map(
                e ->
                    e.getType() == Geometry.Dimension.Type.TIME
                        ? timeStart
                        : /* TODO use the index for any further dimension, unused for now */ 0L)
            .toList();

    return shards.computeIfAbsent(new ComparableLongList(key), k -> createShards(scale, timeStart));
  }

  private synchronized List<Shard> createShards(Scale scale, long timeStart) {

    var shards = new ArrayList<Shard>();
    int index = 0; // TODO do we need this to change with the time slice?
    var geometries =
        scale.size() == 1
            ? List.<Geometry>of(scale)
            : getGeometries(
                scale,
                nativeShardingStrategy.getSuggestedSplits(),
                nativeShardingStrategy.getMinSplitSize(),
                nativeShardingStrategy.getMaxBufferSize());
    for (var geometry : geometries) {
      var shard =
          new ShardImpl(
              GeometryRepository.INSTANCE.geometry(geometry),
              observation,
              nativeShardingStrategy,
              index++,
              geometries.size(),
              timeStart,
              scope.getConfiguration().getPersistence(),
              getNativeType());

      shardStorage.put(shard.getUrn(), new ShardStorage(shard, storageManager));

      shards.add(shard);
    }

    return shards;
  }

  @Override
  public <T extends Scanner> List<T> scan(
      Scheduler.Event locator,
      Data.ShardingStrategy shardingStrategy,
      Class<T> scannerClass,
      boolean readOnly) {
    Objects.requireNonNull(scannerClass, "scannerClass");
    if (this.nativeShardingStrategy.getCurve() == Data.FillCurve.UNSPECIFIED
        && shardingStrategy.getCurve() != Data.FillCurve.UNSPECIFIED) {
      this.nativeShardingStrategy = shardingStrategy;
    }

    if (this.nativeShardingStrategy.equals(shardingStrategy)) {
      var ret = new ArrayList<T>();
      for (var shard : getNativeShards(locator)) {
        var scanner = getNativeScanner(shard, readOnly, !readOnly);
        ret.add(ScannerAdapters.adaptType(scanner, scannerClass));
      }
      return ret;
    }
    // Reject before opening scanners: constructing write scanners resets their histograms, so an
    // unsupported remapping request must not have observable side effects.
    return remapScanners(List.of(), shardingStrategy, scannerClass);
  }

  @Override
  public Storage.Scanner getNativeScanner(Shard shard) {
    return getNativeScanner(shard, false, false);
  }

  private Storage.Scanner getNativeScanner(Shard shard, boolean readOnly, boolean resetForWrite) {

    var st = shardStorage.get(shard.getUrn());

    if (st == null) {
      st = shardStorage.computeIfAbsent(shard.getUrn(), urn -> restore(shard));
    }

    if (resetForWrite) {
      // Do not let an asynchronous persistence task observe a buffer while a new run mutates it.
      storageManager.flushPendingPersistence();
      st.resetHistogram();
    }

    /**
     * TODO if there is a need for mediation, we should create a MediatingScanner with the
     * appropriate type. All mediation should be in the scanner and nowhere else.
     */
    return switch (shard.getShardingStrategy().getDataType()) {
      case DOUBLE -> new LocalDoubleScanner((ShardImpl) shard, st.data, st.histogram, readOnly);
      case FLOAT -> new LocalFloatScanner((ShardImpl) shard, st.data, st.histogram, readOnly);
      case INTEGER -> new LocalIntScanner((ShardImpl) shard, st.data, st.histogram, readOnly);
      case LONG -> new LocalLongScanner((ShardImpl) shard, st.data, st.histogram, readOnly);
      case BOOLEAN -> new LocalBooleanScanner((ShardImpl) shard, st.data, st.histogram, readOnly);
      case KEYED ->
          throw new KlabUnimplementedException(
              "KEYED storage requires a persistent DataKey and a dedicated KeyScanner");
    };
  }

  /**
   * TODO next obvious step is to keep these cached and offload buffers dynamically when things get
   * big. Could use a Buffer proxy with file associated and set it into a cache linked to overall
   * size.
   *
   * @param shard
   * @return
   */
  private ShardStorage restore(Shard shard) {
    var file = storageManager.getStorageFile(shard);
    if (!file.isFile()) {
      throw new KlabIllegalStateException("Cannot restore missing shard storage " + file);
    }
    var ret = new StorageImpl.ShardStorage(shard, storageManager);
    if (!storageManager.loadBufferArray(ret.data, file, shard.getNativeType())) {
      ret.close();
      throw new KlabIllegalStateException("Cannot read shard data from local storage: " + file);
    }
    ret.rebuildHistogram();
    if (shard instanceof ShardImpl shardImpl) {
      shardImpl.setHistogram(Utils.Data.adaptHistogram(ret.histogram));
    }
    return ret;
  }

  /**
   * Remap a list of scanners to those representing the requested sharding strategy. This may entail
   * creating temporary geometries and merging or splitting scanners to reflect them. Data types may
   * need to be cast to remap to a different compatible type.
   *
   * @param nativeScanners
   * @param shardingStrategy
   * @param scannerClass
   * @param <T>
   * @return
   */
  public static <T extends Scanner> List<T> remapScanners(
      List<Scanner> nativeScanners, Data.ShardingStrategy shardingStrategy, Class<T> scannerClass) {

    // Returning native scanners here used to silently violate the requested curve, split count and
    // scanner type. Keep the limitation explicit until geometry-aware split/merge and type
    // mediation can be implemented as scanner decorators.
    throw new KlabUnimplementedException(
        "Scanner remapping across sharding strategies is not implemented");
  }

  @Override
  public Data.ShardingStrategy getNativeShardingStrategy() {
    return nativeShardingStrategy;
  }

  public List<Shard> allShards() {
    var ret = new ArrayList<Shard>();
    shards.values().forEach(ret::addAll);
    return ret;
  }

  private Layout histogramLayout(Observable observable) {
    // TODO use sensible types and values for the observable
    return OpenTelemetryExponentialBucketsLayout.create(1);
  }

  public com.dynatrace.dynahist.Histogram histogram() {
    com.dynatrace.dynahist.Histogram ret = null;
    for (var histogram : temporalHistograms().values()) {
      if (ret == null) {
        ret = com.dynatrace.dynahist.Histogram.createDynamic(histogram.getLayout());
      }
      ret.addHistogram(histogram);
    }
    return ret;
  }

  private NavigableMap<Long, com.dynatrace.dynahist.Histogram> temporalHistograms() {
    var ret = new TreeMap<Long, com.dynatrace.dynahist.Histogram>();
    for (var shard : allShards()) {
      var shardData = shardStorage.computeIfAbsent(shard.getUrn(), urn -> restore(shard));
      if (shardData.histogram != null) {
        var histogram = ret.get(shard.getTimestamp());
        if (histogram == null) {
          histogram =
              com.dynatrace.dynahist.Histogram.createDynamic(shardData.histogram.getLayout());
          ret.put(shard.getTimestamp(), histogram);
        }
        histogram.addHistogram(shardData.histogram);
      }
    }
    return ret;
  }

  @Override
  public Map<Long, Histogram> getHistograms() {
    var ret = new TreeMap<Long, Histogram>();
    temporalHistograms()
        .forEach(
            (timestamp, histogram) ->
                ret.put(timestamp, Utils.Data.adaptHistogram(histogram)));
    return Collections.unmodifiableMap(ret);
  }

  @Override
  public Histogram getHistogram() {
    return Utils.Data.adaptHistogram(histogram());
  }

  @Override
  public DataKey getKey() {
    // TODO KEYED storage needs a durable, mergeable key descriptor in ShardImpl and Observation.
    return dataKey;
  }

  @Override
  public void finalizeRun(Scanner scanner) {
    if (scanner instanceof BaseScanner baseScanner) {
      Histogram histogram = null;
      var storage = shardStorage.get(scanner.shard().getUrn());
      if (storage.histogram != null) {
        var dynaHistogram = storage.histogram;
        histogram = Utils.Data.adaptHistogram(dynaHistogram);
      }
      baseScanner.shard.setHistogram(histogram);
    }
    if (scope.getConfiguration().getPersistence().survivesShutdown) {
      storageManager.persistShard(scanner);
    }
  }

  @Override
  public void flush() {
    storageManager.flushPendingPersistence();
  }

  @Override
  public void close(ServiceScope serviceScope) {
    flush();
    shardStorage.values().forEach(shardStorage1 -> shardStorage1.close());
    shardStorage.clear();
  }

  public static void main(String[] args) {

    var s = new StorageImpl();

    var original = Geometry.create(Geometries.CENTRAL_COLOMBIA);
    for (var g : s.getGeometries(original, -1, 65600, Long.MAX_VALUE)) {
      System.out.println(g);
    }
  }

  public static class BaseScanner implements Storage.Scanner {

    protected final ShardImpl shard;
    protected final long size;
    // BufferArray is shared by the typed scanners, which exclusively use its primitive accessors.
    protected final BufferArray data;
    protected final com.dynatrace.dynahist.Histogram histogram;
    protected final boolean readOnly;
    protected long index = 0L;

    public BaseScanner(
        ShardImpl shard,
        BufferArray data,
        com.dynatrace.dynahist.Histogram histogram,
        boolean readOnly) {
      this.shard = shard;
      this.size = shard.getGeometry().size();
      this.data = data;
      this.histogram = histogram;
      this.readOnly = readOnly;
    }

    @Override
    public ShardImpl shard() {
      return shard;
    }

    @Override
    public long size() {
      return size;
    }

    @Override
    public long nextLong() {
      return index++;
    }

    @Override
    public boolean hasNext() {
      return index < size;
    }

    protected void assertWritable() {
      if (readOnly) {
        throw new KlabIllegalStateException("Cannot write through a read-only storage scanner");
      }
    }
  }

  class LocalDoubleScanner extends BaseScanner implements Storage.DoubleScanner {

    public LocalDoubleScanner(
        ShardImpl shard,
        BufferArray data,
        com.dynatrace.dynahist.Histogram histogram,
        boolean readOnly) {
      super(shard, data, histogram, readOnly);
    }

    @Override
    public double get() {
      return data.doubleValue(index++);
    }

    @Override
    public double peek() {
      return data.doubleValue(index);
    }

    @Override
    public void add(double value) {
      assertWritable();
      addToHistogram(histogram, value);
      data.set(index++, value);
    }
  }

  class LocalFloatScanner extends BaseScanner implements Storage.FloatScanner {

    public LocalFloatScanner(
        ShardImpl shard,
        BufferArray data,
        com.dynatrace.dynahist.Histogram histogram,
        boolean readOnly) {
      super(shard, data, histogram, readOnly);
    }

    @Override
    public float get() {
      return data.floatValue(index++);
    }

    @Override
    public float peek() {
      return data.floatValue(index);
    }

    @Override
    public void add(float value) {
      assertWritable();
      addToHistogram(histogram, value);
      data.set(index++, value);
    }
  }

  class LocalIntScanner extends BaseScanner implements Storage.IntScanner {

    public LocalIntScanner(
        ShardImpl shard,
        BufferArray data,
        com.dynatrace.dynahist.Histogram histogram,
        boolean readOnly) {
      super(shard, data, histogram, readOnly);
    }

    @Override
    public int get() {
      return data.intValue(index++);
    }

    @Override
    public int peek() {
      return data.intValue(index);
    }

    @Override
    public void add(int value) {
      assertWritable();
      addToHistogram(histogram, value);
      data.set(index++, value);
    }
  }

  class LocalLongScanner extends BaseScanner implements Storage.LongScanner {

    public LocalLongScanner(
        ShardImpl shard,
        BufferArray data,
        com.dynatrace.dynahist.Histogram histogram,
        boolean readOnly) {
      super(shard, data, histogram, readOnly);
    }

    @Override
    public long get() {
      return data.longValue(index++);
    }

    @Override
    public long peek() {
      return data.longValue(index);
    }

    @Override
    public void add(long value) {
      assertWritable();
      addToHistogram(histogram, value);
      data.set(index++, value);
    }
  }

  class LocalBooleanScanner extends BaseScanner implements Storage.BooleanScanner {

    LocalBooleanScanner(
        ShardImpl shard,
        BufferArray data,
        com.dynatrace.dynahist.Histogram histogram,
        boolean readOnly) {
      super(shard, data, histogram, readOnly);
    }

    @Override
    public boolean get() {
      return data.byteValue(index++) != 0;
    }

    @Override
    public boolean peek() {
      return data.byteValue(index) != 0;
    }

    @Override
    public void add(boolean value) {
      assertWritable();
      addToHistogram(histogram, value ? 1 : 0);
      data.set(index++, (byte) (value ? 1 : 0));
    }
  }

  private static void addToHistogram(
      com.dynatrace.dynahist.Histogram histogram, double value) {
    if (histogram != null && !Double.isNaN(value)) {
      histogram.addValue(value);
    }
    // TODO HistogramImpl supports missing counts but DynaHist does not. Track missing/no-data
    // values alongside the dynamic histogram once the runtime has one canonical no-data policy.
  }
}
