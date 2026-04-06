package org.integratedmodelling.klab.runtime.storage;

import com.dynatrace.dynahist.layout.Layout;
import com.dynatrace.dynahist.layout.OpenTelemetryExponentialBucketsLayout;
import java.io.Serial;
import java.util.*;
import java.util.concurrent.ConcurrentSkipListMap;
import org.integratedmodelling.common.knowledge.GeometryRepository;
import org.integratedmodelling.klab.api.data.Data;
import org.integratedmodelling.klab.api.data.Geometries;
import org.integratedmodelling.klab.api.data.Histogram;
import org.integratedmodelling.klab.api.data.Storage;
import org.integratedmodelling.klab.api.data.impl.HistogramImpl;
import org.integratedmodelling.klab.api.data.mediation.classification.DataKey;
import org.integratedmodelling.klab.api.digitaltwin.GraphModel;
import org.integratedmodelling.klab.api.digitaltwin.Scheduler;
import org.integratedmodelling.klab.api.exceptions.KlabIllegalStateException;
import org.integratedmodelling.klab.api.exceptions.KlabUnimplementedException;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.knowledge.observation.impl.ObservationImpl;
import org.integratedmodelling.klab.api.knowledge.observation.scale.Scale;
import org.integratedmodelling.klab.api.knowledge.observation.scale.time.Time;
import org.integratedmodelling.klab.api.knowledge.observation.scale.time.TimeInstant;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.scope.ServiceScope;
import org.integratedmodelling.klab.common.data.impl.ShardImpl;
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
    private final com.dynatrace.dynahist.Histogram histogram;
    private final BufferArray data;

    ShardStorage(Shard shard, StorageManagerImpl storage) {
      this.shard = shard;
      this.storage = storage;
      this.histogram =
          storage.isRecordHistogram()
              ? com.dynatrace.dynahist.Histogram.createDynamic(
                  histogramLayout(observation.getObservable()))
              : null;
      this.data =
          switch (shard.getShardingStrategy().getDataType()) {
            case DOUBLE -> storage.getDoubleBuffer(shard.getGeometry().size());
            case FLOAT -> storage.getFloatBuffer(shard.getGeometry().size());
            // TODO use size/int32.size for booleans and adapt the scanners
            case INTEGER, KEYED, BOOLEAN -> storage.getIntBuffer(shard.getGeometry().size());
            case LONG -> storage.getLongBuffer(shard.getGeometry().size());
          };
    }

    public void close() {
      this.data.close();
    }
  }

  private Map<String, ShardStorage> shardStorage = new HashMap<>();

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

    if (observation.getContextualizationData()
        instanceof ObservationImpl.ContextualizationDataImpl data) {
      data.setNativeShardingStrategy(shardingStrategy);
    }

    /**
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
      var dsplits = original.size() / minSize;
      while (original.size() / dsplits > maxSize) {
        dsplits *= 2;
      }
      splits = (int) dsplits;
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

    if (scale.size() == 1) {
      return List.of(ShardImpl.trivial(nativeShardingStrategy.getDataType()));
    }

    var shards = new ArrayList<Shard>();
    int index = 0; // TODO do we need this to change with the time slice?
    var geometries =
        getGeometries(
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
    if (this.nativeShardingStrategy.getCurve() == Data.FillCurve.UNSPECIFIED
        && shardingStrategy.getCurve() != Data.FillCurve.UNSPECIFIED) {
      this.nativeShardingStrategy = shardingStrategy;
    }

    if (this.nativeShardingStrategy.equals(shardingStrategy)) {
      return getNativeShards(locator).stream().map(shard -> (T) getNativeScanner(shard)).toList();
    }
    return remapScanners(
        getNativeShards(locator).stream().map(shard -> getNativeScanner(shard)).toList(),
        shardingStrategy,
        scannerClass);
  }

  @Override
  public Storage.Scanner getNativeScanner(Shard shard) {

    var st = shardStorage.get(shard.getUrn());

    if (st == null) {
      shardStorage.put(shard.getUrn(), st = restore(shard));
    }

    if (st == null) {
      throw new KlabIllegalStateException(
          "Cannot restore shard " + shard.getUrn() + " from storage");
    }

    /**
     * TODO if there is a need for mediation, we should create a MediatingScanner with the
     * appropriate type. All mediation should be in the scanner and nowhere else.
     */
    return switch (shard.getShardingStrategy().getDataType()) {
      case DOUBLE -> new LocalDoubleScanner((ShardImpl) shard, st.data, st.histogram);
      case FLOAT -> new LocalFloatScanner((ShardImpl) shard, st.data, st.histogram);
      case INTEGER, KEYED, BOOLEAN ->
          new LocalIntScanner(
              (ShardImpl) shard, st.data, st.histogram); // TODO needs to implement KEYED
      case LONG -> new LocalLongScanner((ShardImpl) shard, st.data, st.histogram);
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
    if (!storageManager.hasExistingData()) {
      throw new KlabIllegalStateException("Cannot restore shard without pre-existing storage data");
    }
    var ret = new StorageImpl.ShardStorage(shard, storageManager);
    var file = storageManager.getStorageFile(shard);
    if (!storageManager.loadBufferArray(ret.data, file, shard.getNativeType())) {
      scope.error("Cannot read shard data from local storage: " + file);
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

    // TODO! two steps: 1) splits & curve; 2) data type casting
    return (List<T>) nativeScanners;
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

    var allBuffers = allShards();
    if (allBuffers.size() == 1) {
      return shardStorage.get((allBuffers.getFirst()).getUrn()).histogram;
    } else if (allBuffers.size() > 1) {
      com.dynatrace.dynahist.Histogram ret = null;
      var first = shardStorage.get((allBuffers.getFirst()).getUrn()).histogram;
      if (first != null) {
        ret = com.dynatrace.dynahist.Histogram.createDynamic(first.getLayout());
        for (var buffer : allBuffers) {
          if (shardStorage.get(buffer.getUrn()).histogram != null) {
            ret.addHistogram(shardStorage.get(buffer.getUrn()).histogram);
          }
        }
      }
    }
    return null;
  }

  @Override
  public Histogram getHistogram() {
    return Utils.Data.adaptHistogram(histogram());
  }

  @Override
  public DataKey getKey() {
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
  public void close(ServiceScope serviceScope) {
    // TODO remove all buffers
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
    protected final BufferArray data;
    protected final com.dynatrace.dynahist.Histogram histogram;
    protected long index = 0L;

    public BaseScanner(
        ShardImpl shard, BufferArray data, com.dynatrace.dynahist.Histogram histogram) {
      this.shard = shard;
      this.size = shard.getGeometry().size();
      this.data = data;
      this.histogram = histogram;
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
  }

  /* TODO handle the histogram */
  class LocalDoubleScanner extends BaseScanner implements Storage.DoubleScanner {

    public LocalDoubleScanner(
        ShardImpl shard, BufferArray data, com.dynatrace.dynahist.Histogram histogram) {
      super(shard, data, histogram);
    }

    @Override
    public double get() {
      return data.get(index++);
    }

    @Override
    public double peek() {
      return data.get(index);
    }

    @Override
    public void add(double value) {
      if (histogram != null) {
        histogram.addValue(value);
      }
      data.set(index++, value);
    }
  }

  class LocalFloatScanner extends BaseScanner implements Storage.FloatScanner {

    public LocalFloatScanner(
        ShardImpl shard, BufferArray data, com.dynatrace.dynahist.Histogram histogram) {
      super(shard, data, histogram);
    }

    @Override
    public float get() {
      return data.get(index++).floatValue();
    }

    @Override
    public float peek() {
      return data.get(index).floatValue();
    }

    @Override
    public void add(float value) {
      if (histogram != null) {
        histogram.addValue(value);
      }
      data.set(index++, value);
    }
  }

  class LocalIntScanner extends BaseScanner implements Storage.IntScanner {

    public LocalIntScanner(
        ShardImpl shard, BufferArray data, com.dynatrace.dynahist.Histogram histogram) {
      super(shard, data, histogram);
    }

    @Override
    public int get() {
      return data.get(index++).intValue();
    }

    @Override
    public int peek() {
      return data.get(index).intValue();
    }

    @Override
    public void add(int value) {
      if (histogram != null) {
        histogram.addValue(value);
      }
      data.set(index++, value);
    }
  }

  class LocalLongScanner extends BaseScanner implements Storage.LongScanner {

    public LocalLongScanner(
        ShardImpl shard, BufferArray data, com.dynatrace.dynahist.Histogram histogram) {
      super(shard, data, histogram);
    }

    @Override
    public long get() {
      return data.get(index++).longValue();
    }

    @Override
    public long peek() {
      return data.get(index).longValue();
    }

    @Override
    public void add(long value) {
      if (histogram != null) {
        histogram.addValue(value);
      }
      data.set(index++, value);
    }
  }
}
