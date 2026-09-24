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
import org.integratedmodelling.klab.api.data.StorageScan;
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
  private final Data.ShardingStrategy nativeShardingStrategy;
  private final Object scanLock = new Object();
  private final String scanInstance = UUID.randomUUID().toString();
  private long scanGeneration;
  private int scanLeases;
  private boolean closed;
  private final Set<String> readableShards = new HashSet<>();
  private final StorageManagerImpl storageManager;
  private ConceptDataKey dataKey;

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
      this(shard, storage, false);
    }

    ShardStorage(Shard shard, StorageManagerImpl storage, boolean provisional) {
      this.shard = shard;
      this.storage = storage;
      resetHistogram();
      this.data =
          provisional
              ? (BufferArray)
                  StorageManagerImpl.bufferFactory(shard.getNativeType())
                      .make(shard.getGeometry().size())
              : switch (shard.getShardingStrategy().getDataType()) {
                case DOUBLE -> storage.getDoubleBuffer(shard.getGeometry().size());
                case FLOAT -> storage.getFloatBuffer(shard.getGeometry().size());
                case INTEGER, KEYED -> storage.getIntBuffer(shard.getGeometry().size());
                case BOOLEAN -> storage.getBooleanBuffer(shard.getGeometry().size());
                case LONG -> storage.getLongBuffer(shard.getGeometry().size());
              };
    }

    void resetHistogram() {
      this.histogram =
          storage.isRecordHistogram() && shard.getNativeType() != Type.KEYED
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
  private final Map<String, List<Shard>> temporalShards = new LinkedHashMap<>();
  private TemporalHistory history = new TemporalHistory(1, List.of());

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
    this.nativeShardingStrategy = shardingStrategy.copy().validate();

    /*
     * If the observation comes from the KG, we load any pre-existing shards into lazy containers.
     */
    if (observation.getId() > 0) {
      var encodedHistory = observation.getMetadata().get(TemporalHistory.KEY);
      if (encodedHistory != null)
        history = Utils.Json.parseObject(encodedHistory.toString(), TemporalHistory.class);
      var versionByUrn = new HashMap<String, String>();
      for (var revision : history.revisions()) {
        temporalShards.put(revision.event(), new ArrayList<>());
        for (var urn : revision.shards()) versionByUrn.put(urn, revision.event());
      }
      for (var shard :
          contextScope
              .getDigitalTwin()
              .getKnowledgeGraph()
              .query(Shard.class, contextScope)
              .source(observation)
              .along(GraphModel.Relationship.HAS_DATA)
              .run(contextScope)) {
        if (shard.getGeometry() == null) {
          throw new KlabIllegalStateException(
              "Cannot reconstruct storage without shard geometry: " + shard.getUrn());
        }
        readableShards.add(shard.getUrn());
        if (versionByUrn.containsKey(shard.getUrn())) {
          temporalShards.get(versionByUrn.get(shard.getUrn())).add(shard);
          continue;
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
      for (var group : shards.values()) {
        validateRestoredShards(group, nativeShardingStrategy.getDataType());
      }
      for (var revision : history.revisions()) {
        var group = temporalShards.get(revision.event());
        if (group.size() != revision.shards().size() || group.isEmpty())
          throw new KlabIllegalStateException(
              "Missing committed temporal data for " + revision.event());
        validateRestoredShards(group, nativeShardingStrategy.getDataType());
      }
    }
    if (getNativeType() == Type.KEYED && !allShards().isEmpty()) {
      var restored=new HashSet<String>();
      for (var shard : allShards()) if(restored.add(shard.getKeyDictionaryHash())) key().restore(storageManager.readDictionary(shard.getKeyDictionaryHash()));
    }
  }

  /** Database traversal order is unspecified; scanner order must follow the native partition. */
  static void validateRestoredShards(List<Shard> group, Type nativeType) {
    group.sort(Comparator.comparingInt(Shard::getShardIndex));
    for (int index = 0; index < group.size(); index++) {
      var shard = group.get(index);
      if (shard.getShardCount() != group.size()
          || shard.getShardIndex() != index
          || shard.getNativeType() != nativeType
          || shard.getShardingStrategy() == null
          || shard.getShardingStrategy().getDataType() != nativeType) {
        throw new KlabIllegalStateException(
            "Incomplete or inconsistent persisted shard group at " + shard.getUrn());
      }
    }
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

    // Shard descriptors need plain geometry, not reconstructed service-local scales.
    return Geometry.forTransport(original).split(splits);
  }

  @Override
  public List<Shard> getNativeShards(Scheduler.Event event) {
    return getNativeShards(event, event.getType() == Scheduler.Event.Type.INITIALIZATION);
  }

  private List<Shard> getNativeShards(Scheduler.Event event, boolean create) {

    if (event.getType() != Scheduler.Event.Type.INITIALIZATION) {
      if (create)
        throw new KlabIllegalStateException(
            "Temporal writes require a transaction-owned write set");
      var exact = temporalShards.get(event.toKey());
      if (exact != null) return List.copyOf(exact);
      TemporalHistory.Revision covering = null;
      for (var revision : history.revisions()) {
        if (revision.start() <= event.getTime().getStart().getMilliseconds()
            && revision.end() >= event.getTime().getEnd().getMilliseconds()) covering = revision;
      }
      if (covering != null) return List.copyOf(temporalShards.get(covering.event()));
      var baseline =
          temporalBaseline(
              event, Boolean.TRUE.equals(observation.getMetadata().get(TemporalHistory.EPHEMERAL)));
      if (baseline.isEmpty())
        throw new KlabIllegalStateException("No committed state at requested temporal support");
      return baseline;
    }

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

    var storageKey = new ComparableLongList(key);
    if (!create) {
      var existing = shards.get(storageKey);
      if (existing == null) {
        throw new KlabIllegalStateException(
            "No available storage slice for observation "
                + observation.getId()
                + " at "
                + timeStart);
      }
      return existing;
    }
    return shards.computeIfAbsent(storageKey, k -> createShards(scale, timeStart));
  }

  @Override
  public List<StorageScan.Partition> writeLayout(Scheduler.Event event) {
    if (event.getType() != Scheduler.Event.Type.INITIALIZATION)
      throw new UnsupportedOperationException("Temporal outputs require a write set");
    var scale = GeometryRepository.INSTANCE.scale(observation.getGeometry()).at(event.getTime());
    var geometries = scale.size() == 1 ? List.<Geometry>of(scale) : temporalLayout(scale);
    var result = new ArrayList<StorageScan.Partition>();
    for (int i = 0; i < geometries.size(); i++) {
      var geometry = geometries.get(i);
      result.add(new StorageScan.Partition("task-" + i, geometry.encode(), geometry.size()));
    }
    return List.copyOf(result);
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
    shardingStrategy.validate();
    StorageScan.operation(getNativeType(), StorageScan.type(scannerClass, getNativeType()),
        StorageScan.Precision.ALLOW_FLOAT_NARROWING);

    synchronized (scanLock) {
      assertOpen();
      if (!readOnly) assertUnleased();
      if (this.nativeShardingStrategy.equals(shardingStrategy)) {
        var ret = new ArrayList<T>();
        for (var shard : getNativeShards(locator, !readOnly)) {
          var scanner = getNativeScanner(shard, readOnly, !readOnly);
          ret.add(ScannerAdapters.adaptType(scanner, scannerClass));
        }
        return ret;
      }
      // Reject before opening scanners: constructing write scanners resets their histograms, so an
      // unsupported remapping request must not have observable side effects.
      return remapScanners(List.of(), shardingStrategy, scannerClass);
    }
  }

  @Override
  public Storage.Scanner getNativeScanner(Shard shard) {
    boolean immutable =
        history.revisions().stream().anyMatch(r -> r.shards().contains(shard.getUrn()));
    return getNativeScanner(shard, immutable, false);
  }

  private Storage.Scanner getNativeScanner(Shard shard, boolean readOnly, boolean resetForWrite) {

    synchronized (scanLock) {
      assertOpen();
      if (resetForWrite) assertUnleased();
      if (getNativeType() == Type.KEYED) storageManager.currentWorldview();

      var st = shardStorage.get(shard.getUrn());

      if (st == null) {
        st = shardStorage.computeIfAbsent(shard.getUrn(), urn -> restore(shard));
      }

      if (resetForWrite) {
        // Do not let an asynchronous persistence task observe a buffer while a new run mutates it.
        storageManager.flushPendingPersistence();
        st.resetHistogram();
        scanGeneration++;
        readableShards.remove(shard.getUrn());
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
        case KEYED -> new LocalKeyScanner((ShardImpl) shard, st.data, readOnly);
      };
    }
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
    if (shard.getNativeType() == Type.KEYED) {
      var dictionary = storageManager.readDictionary(shard.getKeyDictionaryHash());
      for (long i=0;i<ret.data.count();i++) if (ret.data.intValue(i) < 0 || ret.data.intValue(i) > dictionary.entries().size()) {
        ret.close(); throw new IllegalStateException("Unknown code in keyed shard " + shard.getUrn());
      }
      var expected = shard.getCategoryHistogram();
      if (expected == null || !expected.equals(categoryHistogram(ret.data, dictionary.fingerprint()))) {
        ret.close(); throw new IllegalStateException("Keyed histogram/dictionary mismatch " + shard.getUrn());
      }
    }
    ret.rebuildHistogram();
    if (shard instanceof ShardImpl shardImpl) {
      shardImpl.setHistogram(Utils.Data.adaptHistogram(ret.histogram, ret.data.count()));
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
    return nativeShardingStrategy.copy();
  }

  @Override
  public Set<StorageScan.Capability> scanCapabilities() {
    return Set.of(StorageScan.Capability.NATIVE_READ, StorageScan.Capability.FLOAT_ADAPTATION,
        StorageScan.Capability.INDEXED_READ, StorageScan.Capability.BLOCK_READ,
        StorageScan.Capability.VALIDITY, StorageScan.Capability.PINNED_SESSION, StorageScan.Capability.CONFORMANT_READ, StorageScan.Capability.VALUE_MEDIATION, StorageScan.Capability.SPATIAL_READ, StorageScan.Capability.KEYED_READ);
  }

  private record NativePlan<T extends Scanner>(StorageImpl owner, long generation,
      StorageScan.Description description, Class<T> scannerClass, List<Shard> shards, ScanMapping mapping, String observationGeometry)
      implements StorageScan.Plan<T> {
    private NativePlan { shards = List.copyOf(shards); }
  }

  private record PlanKey(long generation, long observationId, String observationGeometry,
      StorageScan.Semantics semantics, List<StorageScan.SourceShard> sources, StorageScan.Request<?> request, boolean acceptsLossy) {}

  // Cache metadata only. Large plans bypass the cache, so its footprint is bounded independently
  // of request budgets and dataset size. Entries contain no readers or payload buffers.
  private final Map<PlanKey, NativePlan<?>> scanPlans = new LinkedHashMap<>(16, 0.75f, true);

  @Override
  @SuppressWarnings("unchecked") // scannerClass is part of the immutable request/cache key
  public <T extends Scanner> StorageScan.Plan<T> plan(StorageScan.Request<T> request) {
    Objects.requireNonNull(request);
    synchronized (scanLock) {
      assertOpen();
      if (request.access() != StorageScan.Access.READ_ONLY)
        throw new UnsupportedOperationException("Planned writes require a future native ownership protocol");
      if (getNativeType() == Type.KEYED) storageManager.currentWorldview();
      var layout = StorageScan.Layout.of(nativeShardingStrategy);
      if (layout.curve() == Data.FillCurve.UNSPECIFIED || layout.curve() == Data.FillCurve.D3_ZYX
          || layout.curve() == Data.FillCurve.D2_HILBERT || layout.curve() == Data.FillCurve.D3_HILBERT)
        throw new UnsupportedOperationException("Unsupported native traversal: " + layout.curve());
      var semantics = scanSemantics();
      var targetSemantics = ValueMediation.target(semantics, request.semantics());
      var conversion = ValueMediation.compile(semantics, targetSemantics, request.rate());
      ValueMediation.validateType(conversion, getNativeType());
      var valueType = StorageScan.type(request.scannerClass(), getNativeType());
      if (request.layout().type() != null && request.layout().type() != getNativeType()
          && request.layout().type() != valueType)
        throw new IllegalArgumentException("Requested layout type contradicts the scanner type");
      var operation = StorageScan.operation(getNativeType(), valueType, request.precision());
      var nativeShards = List.copyOf(getNativeShards(request.slice().event(), false));
      if (nativeShards.isEmpty()) throw new KlabIllegalStateException("No initialized source data for scan");
      if (nativeShards.stream().anyMatch(shard -> !readableShards.contains(shard.getUrn())))
        throw new IllegalStateException("Source shards have not been finalized");
      if (nativeShards.size() > request.budget().maxPartitions())
        throw new IllegalArgumentException("Source partition budget exceeded");
      var sources = nativeShards.stream().map(this::scanSource).toList();
      String observationGeometry = observation.getGeometry().encode();
      boolean acceptsLossy = StorageReads.acceptsLossy(scope);
      var key = new PlanKey(scanGeneration, observation.getId(), observationGeometry, semantics, sources, request, acceptsLossy);
      var cached = scanPlans.get(key);
      if (cached != null) return (StorageScan.Plan<T>) cached;
      var partitions = request.partitions();
      boolean aligned = layout.curve() == request.layout().curve()
          && (request.geometry() == null || request.geometry().equals(StorageScan.parseGeometry(observationGeometry).encode()));
      if (!partitions.isEmpty()) {
        aligned &= partitions.size() == sources.size();
        for (int i = 0; aligned && i < partitions.size(); i++)
          aligned = partitions.get(i).geometry().equals(sources.get(i).geometry())
              && partitions.get(i).size() == sources.get(i).size();
      } else {
        aligned &= layout.splits() == request.layout().splits() && layout.minSize() == request.layout().minSize()
            && layout.maxSize() == request.layout().maxSize();
      }
      ScanMapping mapping = null;
      if (aligned) {
        if (partitions.isEmpty()) partitions = sources.stream()
            .map(source -> new StorageScan.Partition("native-" + source.index(), source.geometry(), source.size())).toList();
        for (var partition : partitions)
          if (request.layout().maxSize() > 0 && partition.size() > request.layout().maxSize()) aligned = false;
      }
      if (!aligned) {
        mapping = SpatialScan.plan(sources, request, observationGeometry, acceptsLossy);
        partitions = mapping.partitions();
      }
      boolean identity = aligned && layout.equals(request.layout());
      SpatialScan.validateConversion(mapping, conversion);
      boolean spatial = mapping instanceof SpatialScan;
      var description = new StorageScan.Description(getNativeType() == Type.KEYED ? 5 : spatial ? 4 : conversion != null ? 3 : identity ? 1 : 2, scanInstance + ":" + scanGeneration,
          observation.getId(), Objects.toString(observation.getUrn(), ""), request.slice(), semantics,
          targetSemantics, layout, request.layout(), sources, partitions, valueType, request.precision(),
          spatial ? request.coverage() : StorageScan.Coverage.EXACT, spatial ? request.sampling() : StorageScan.Sampling.EXACT, request.budget(), spatial ? SpatialScan.operations(conversion, operation) : conversion != null ? List.of(StorageScan.Operation.INDEX_REMAP, StorageScan.Operation.VALUE_CONVERSION, operation) : identity && getNativeType() != Type.KEYED ? List.of(operation)
              : List.of(StorageScan.Operation.INDEX_REMAP, operation), StorageScan.HistogramPolicy.UNAVAILABLE, conversion, SpatialScan.metadata(mapping), getNativeType() == Type.KEYED ? key().snapshot() : null);
      var plan = new NativePlan<>(this, scanGeneration, description, request.scannerClass(), nativeShards, mapping, observationGeometry);
      long links = mapping == null ? sources.size() : Arrays.stream(mapping.dependencies()).mapToLong(array -> array.length).sum();
      if (sources.size() + partitions.size() + links <= 1024) {
        scanPlans.put(key, plan);
        if (scanPlans.size() > 16) scanPlans.remove(scanPlans.keySet().iterator().next());
      }
      return plan;
    }
  }

  private StorageScan.SourceShard scanSource(Shard shard) {
    return new StorageScan.SourceShard(shard.getUrn(), shard.getGeometry().encode(), shard.getGeometry().size(),
        shard.getShardIndex(), shard.getTimestamp(), StorageScan.Layout.of(shard.getShardingStrategy()));
  }

  private StorageScan.Semantics scanSemantics() {
    return StorageScan.semantics(observation.getObservable());
  }

  @Override
  public <T extends Scanner> StorageScan.Session<T> open(StorageScan.Plan<T> plan) {
    synchronized (scanLock) {
      assertOpen();
      if (!(plan instanceof NativePlan<T> nativePlan) || nativePlan.owner() != this)
        throw new IllegalArgumentException("Scan plan was not issued by this storage instance");
      if (nativePlan.generation() != scanGeneration || plan.description().observationId() != observation.getId()
          || !nativePlan.observationGeometry().equals(observation.getGeometry().encode())
          || !plan.description().sourceSemantics().equals(scanSemantics())
          || !nativePlan.shards().stream().map(this::scanSource).toList().equals(plan.description().sources()))
        throw new IllegalStateException("Source changed since scan planning; replan explicitly");
      if (nativePlan.mapping() instanceof SpatialScan && !StorageReads.acceptsLossy(scope))
        throw SpatialScan.disabled("spatial extents differ; previously issued resampling plan");
      if (getNativeType() == Type.KEYED) storageManager.currentWorldview();
      scanLeases++;
      var readers = new ArrayList<IndexedStorageReader>();
      var previouslyLoaded = Set.copyOf(shardStorage.keySet());
      try {
        for (var shard : nativePlan.shards()) readers.add(openReader(shard, plan.description().budget().blockValues()));
        return new LocalScanSession<>(plan, nativePlan.shards(), readers, nativePlan.mapping(), this::releaseScan);
      } catch (RuntimeException | Error failure) {
        for (var reader : readers) {
          try { reader.close(); } catch (RuntimeException cleanup) { failure.addSuppressed(cleanup); }
        }
        for (var shard : nativePlan.shards()) {
          if (!previouslyLoaded.contains(shard.getUrn())) {
            var state = shardStorage.remove(shard.getUrn());
            if (state != null) {
              try { state.close(); } catch (RuntimeException cleanup) { failure.addSuppressed(cleanup); }
            }
          }
        }
        releaseScan();
        throw failure;
      }
    }
  }

  IndexedStorageReader openReader(Shard shard, int blockValues) {
    var state = shardStorage.computeIfAbsent(shard.getUrn(), ignored -> restore(shard));
    return new LocalIndexedReader(state.data, shard.getNativeType(), blockValues, getNativeType() == Type.KEYED ? key().readOnly() : null);
  }

  Runnable pinReads() {
    synchronized (scanLock) { assertOpen(); scanLeases++; }
    return this::releaseScan;
  }

  private void releaseScan() { synchronized (scanLock) { scanLeases--; } }
  private void assertOpen() { if (closed) throw new IllegalStateException("Storage is closed"); }
  private void assertUnleased() {
    assertOpen();
    if (scanLeases != 0) throw new IllegalStateException("Storage is pinned by an open read session");
  }

  public List<Shard> allShards() {
    var ret = new ArrayList<Shard>();
    shards.values().forEach(ret::addAll);
    temporalShards.values().forEach(ret::addAll);
    return ret;
  }

  /** Canonical owner of the cached storage and its committed metadata. */
  Observation temporalOwner() {
    return observation;
  }

  /** Select a stable causal baseline; equal-time revisions remain independently addressable. */
  synchronized List<Shard> temporalBaseline(Scheduler.Event event, boolean ephemeral) {
    TemporalHistory.Revision selected = null;
    long start = event.getTime().getStart().getMilliseconds();
    long end = event.getTime().getEnd().getMilliseconds();
    for (var revision : history.revisions()) {
      boolean eligible =
          ephemeral && event.getBoundary() == Scheduler.Event.Boundary.NONE
              ? revision.start() <= start && revision.end() >= end : revision.end() <= start;
      if (eligible && (selected == null || revision.end() >= selected.end())) selected = revision;
    }
    if (selected != null) return List.copyOf(temporalShards.get(selected.event()));
    if (ephemeral) return List.of();
    var initial = shards.get(new ComparableLongList(List.of(0L)));
    if (initial == null
        && shards.size() == 1
        && shards.firstEntry().getValue().stream().allMatch(s -> s.getTimestamp() == 0))
      initial = shards.firstEntry().getValue();
    return initial == null ? List.of() : List.copyOf(initial);
  }

  Object nativeValue(Shard shard, long index) {
    var data = shardStorage.computeIfAbsent(shard.getUrn(), urn -> restore(shard)).data;
    return switch (getNativeType()) {
      case DOUBLE -> data.doubleValue(index);
      case FLOAT -> data.floatValue(index);
      case INTEGER, KEYED -> data.intValue(index);
      case LONG -> data.longValue(index);
      case BOOLEAN -> data.byteValue(index) != 0;

    };
  }

  List<Geometry> temporalLayout(Geometry geometry) {
    return getGeometries(
        geometry,
        nativeShardingStrategy.getSuggestedSplits(),
        nativeShardingStrategy.getMinSplitSize(),
        nativeShardingStrategy.getMaxBufferSize());
  }

  /** Flush detached versions before graph commit; expose them in this storage only afterwards. */
  void stageTemporal(
      Scheduler.Event event,
      Geometry support,
      List<Geometry> layout,
      java.util.function.BiFunction<Integer, Long, Object> value,
      org.integratedmodelling.klab.api.digitaltwin.DigitalTwin.Transaction transaction) {
    if (history.revisions().stream().anyMatch(r -> r.event().equals(event.toKey())))
      throw new KlabIllegalStateException("Temporal state already committed: " + event.toKey());
    var pending = new ArrayList<Shard>();
    var buffers = new ArrayList<ShardStorage>();
    transaction.afterRollback(
        () -> {
          for (var buffer : buffers) {
            buffer.close();
            try {
              java.nio.file.Files.deleteIfExists(
                  storageManager.getStorageFile(buffer.shard).toPath());
            } catch (java.io.IOException e) {
              scope.error(e);
            }
          }
        });
    for (int partition = 0; partition < layout.size(); partition++) {
      var shard =
          new ShardImpl(
              Geometry.forTransport(layout.get(partition)),
              observation,
              nativeShardingStrategy,
              partition,
              layout.size(),
              event.getTime().getEnd().getMilliseconds(),
              scope.getConfiguration().getPersistence(),
              getNativeType());
      var buffer = new ShardStorage(shard, storageManager, true);
      buffers.add(buffer);
      pending.add(shard);
      for (long index = 0; index < buffer.data.count(); index++) {
        var nativeValue = value.apply(partition, index);
        if (nativeValue instanceof Boolean b) buffer.data.set(index, b ? 1 : 0);
        else if (nativeValue instanceof Long l) buffer.data.set(index, l.longValue());
        else if (nativeValue instanceof Integer i) buffer.data.set(index, i.intValue());
        else buffer.data.set(index, ((Number) nativeValue).doubleValue());
      }
      buffer.rebuildHistogram();
      shard.setHistogram(Utils.Data.adaptHistogram(buffer.histogram, buffer.data.count()));
      if (getNativeType() == Type.KEYED) finalizeKeyed(shard, buffer.data);
      storageManager.persistTemporalShard(shard, buffer.data);
      transaction.link(
          observation,
          shard,
          GraphModel.Relationship.HAS_DATA,
          "eventId",
          event.toKey(),
          "start",
          event.getTime().getStart().getMilliseconds(),
          "end",
          event.getTime().getEnd().getMilliseconds());
    }
    var revisions = new ArrayList<>(history.revisions());
    revisions.add(
        new TemporalHistory.Revision(
            event.toKey(),
            event.getTime().getStart().getMilliseconds(),
            event.getTime().getEnd().getMilliseconds(),
            Geometry.forTransport(support).encode(),
            pending.stream().map(Shard::getUrn).toList()));
    var next = new TemporalHistory(1, revisions);
    var previous = observation.getMetadata().get(TemporalHistory.KEY);
    observation.getMetadata().put(TemporalHistory.KEY, Utils.Json.asString(next));
    if (observation
        instanceof
        org.integratedmodelling.klab.api.knowledge.observation.impl.ObservationImpl concrete) {
      var oldHistograms = concrete.getHistograms();
      var updatedHistograms = new TreeMap<Long, Histogram>(oldHistograms);
      com.dynatrace.dynahist.Histogram aggregate = null;
      long count = 0;
      for (var buffer : buffers) {
        count += buffer.data.count();
        if (buffer.histogram != null) {
          if (aggregate == null)
            aggregate =
                com.dynatrace.dynahist.Histogram.createDynamic(buffer.histogram.getLayout());
          aggregate.addHistogram(buffer.histogram);
        }
      }
      if (aggregate != null)
        updatedHistograms.put(
            event.getTime().getEnd().getMilliseconds(),
            Utils.Data.adaptHistogram(aggregate, count));
      concrete.setHistograms(updatedHistograms);
      transaction.afterRollback(() -> concrete.setHistograms(oldHistograms));
    }
    transaction.update(observation);
    transaction.afterRollback(
        () -> {
          if (previous == null) observation.getMetadata().remove(TemporalHistory.KEY);
          else observation.getMetadata().put(TemporalHistory.KEY, previous);
        });
    transaction.afterCommit(
        () -> {
          synchronized (scanLock) {
            history = next;
            temporalShards.put(event.toKey(), List.copyOf(pending));
            for (var buffer : buffers) {
              shardStorage.put(buffer.shard.getUrn(), buffer);
              readableShards.add(buffer.shard.getUrn());
            }
          }
        });
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

  /** Timestamp summaries use the last committed revision; history retains every event identity. */
  private List<Shard> histogramShards() {
    var selected = new TreeMap<Long, List<Shard>>();
    for (var shard : allShards()) {
      selected.computeIfAbsent(shard.getTimestamp(), ignored -> new ArrayList<>()).add(shard);
    }
    for (var revision : history.revisions()) {
      selected.put(revision.end(), temporalShards.get(revision.event()));
    }
    return selected.values().stream().flatMap(List::stream).toList();
  }

  private NavigableMap<Long, com.dynatrace.dynahist.Histogram> temporalHistograms() {
    var ret = new TreeMap<Long, com.dynatrace.dynahist.Histogram>();
    for (var shard : histogramShards()) {
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
  public Map<Long, org.integratedmodelling.klab.api.data.mediation.classification.KeyedData.Summary> getCategoryHistograms() {
    synchronized(scanLock) {
      if(getNativeType()!=Type.KEYED)return Map.of();
      storageManager.currentWorldview();
      var groups=new TreeMap<Long,List<Shard>>();
      for(var shard:histogramShards())groups.computeIfAbsent(shard.getTimestamp(),k->new ArrayList<>()).add(shard);
      var result=new TreeMap<Long,org.integratedmodelling.klab.api.data.mediation.classification.KeyedData.Summary>();
      for(var group:groups.entrySet()) {
        var dictionaries=new HashMap<String,org.integratedmodelling.klab.api.data.mediation.classification.KeyedData.Dictionary>();
        for(var shard:group.getValue()) dictionaries.computeIfAbsent(shard.getKeyDictionaryHash(),storageManager::readDictionary);
        var target=dictionaries.values().stream().max(Comparator.comparingInt(d->d.entries().size())).orElseThrow();
        var translations=new HashMap<String,int[]>();
        var counts=new TreeMap<Integer,Long>();long missing=0;
        for(var shard:group.getValue()) {
          var histogram=shard.getCategoryHistogram();
          if(histogram==null || !histogram.dictionary().equals(shard.getKeyDictionaryHash()))throw new IllegalStateException("Missing/mismatched categorical histogram");
          var translate=translations.computeIfAbsent(histogram.dictionary(),id->dictionaries.get(id).translationTo(target));
          for(var count:histogram.counts().entrySet()) {
            if(count.getKey()>=translate.length)throw new IllegalStateException("Unknown histogram code");
            counts.merge(translate[count.getKey()],count.getValue(),Math::addExact);
          }
          missing=Math.addExact(missing,histogram.missing());
        }
        result.put(group.getKey(),new org.integratedmodelling.klab.api.data.mediation.classification.KeyedData.Summary(target,
            new org.integratedmodelling.klab.api.data.mediation.classification.KeyedData.CategoryHistogram(target.fingerprint(),counts,missing)));
      }
      return Collections.unmodifiableMap(result);
    }
  }

  @Override
  public Map<Long, Histogram> getHistograms() {
    var ret = new TreeMap<Long, Histogram>();
    temporalHistograms()
        .forEach(
            (timestamp, histogram) ->
                ret.put(
                    timestamp,
                    Utils.Data.adaptHistogram(
                        histogram,
                        histogramShards().stream()
                            .filter(s -> s.getTimestamp() == timestamp)
                            .mapToLong(s -> shardStorage.get(s.getUrn()).data.count())
                            .sum())));
    return Collections.unmodifiableMap(ret);
  }

  @Override
  public Histogram getHistogram() {
    var histogram = histogram();
    return Utils.Data.adaptHistogram(
        histogram,
        histogramShards().stream().mapToLong(s -> shardStorage.get(s.getUrn()).data.count()).sum());
  }

  @Override
  public DataKey getKey() {
    if(getNativeType()!=Type.KEYED)return null;
    storageManager.currentWorldview();
    return key().readOnly();
  }

  void validateKeyWorldview() { if(getNativeType()==Type.KEYED)storageManager.currentWorldview(); }

  ConceptDataKey key() {
    synchronized (scanLock) {
      if (dataKey == null) {
        var reasoner = scope.getService(org.integratedmodelling.klab.api.services.Reasoner.class);
        var worldview = storageManager.currentWorldview();
        if (!observation.getObservable().is(org.integratedmodelling.klab.api.knowledge.SemanticType.CLASS))
          throw new IllegalArgumentException("KEYED requires a type-of observable");
        var target = reasoner.describedType(observation.getObservable());
        dataKey = new ConceptDataKey(reasoner, target, worldview, () -> storageManager.bindWorldview(worldview), () -> {
          if(!worldview.equals(storageManager.currentWorldview()))throw new IllegalStateException("Keyed semantic environment changed");
        });
      }
      return dataKey;
    }
  }

  private org.integratedmodelling.klab.api.data.mediation.classification.KeyedData.CategoryHistogram categoryHistogram(BufferArray data, String hash) {
    var counts = new TreeMap<Integer,Long>(); long missing=0;
    for(long i=0;i<data.count();i++) { int code=data.intValue(i); if(code==0)missing++; else {
      if(code<0 || code>=key().size())throw new IllegalStateException("Unknown keyed code " + code);
      counts.merge(code,1L,Math::addExact);
    } }
    return new org.integratedmodelling.klab.api.data.mediation.classification.KeyedData.CategoryHistogram(hash,counts,missing);
  }

  private void finalizeKeyed(ShardImpl shard, BufferArray data) {
    var dictionary = key().snapshot();
    storageManager.persistDictionary(dictionary);
    shard.setKeyDictionaryHash(dictionary.fingerprint());
    shard.setCategoryHistogram(categoryHistogram(data,dictionary.fingerprint()));
  }

  @Override
  public void finalizeRun(Scanner scanner) {
    if (!(scanner instanceof BaseScanner))
      throw new IllegalArgumentException("Finalization requires a native output scanner");
    synchronized (scanLock) {
      assertOpen();
      if (scanner instanceof BaseScanner baseScanner) {
        Histogram histogram = null;
        var storage = shardStorage.get(scanner.shard().getUrn());
        if (storage.histogram != null) {
          var dynaHistogram = storage.histogram;
          histogram = Utils.Data.adaptHistogram(dynaHistogram, storage.data.count());
        }
        baseScanner.shard.setHistogram(histogram);
        if (getNativeType() == Type.KEYED) finalizeKeyed(baseScanner.shard, storage.data);
      }
      if (scope.getConfiguration().getPersistence().survivesShutdown) {
        storageManager.persistShard(scanner);
      }
      readableShards.add(scanner.shard().getUrn());
    }
  }

  @Override
  public void flush() {
    storageManager.flushPendingPersistence();
  }

  @Override
  public void close(ServiceScope serviceScope) {
    synchronized (scanLock) {
      if (closed) return;
      assertUnleased();
      flush();
      shardStorage.values().forEach(shardStorage1 -> shardStorage1.close());
      shardStorage.clear();
      scanPlans.clear();
      closed = true;
    }
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

  class LocalKeyScanner extends BaseScanner implements Storage.KeyScanner<org.integratedmodelling.klab.api.knowledge.Concept> {
    private final ConceptDataKey key;
    LocalKeyScanner(ShardImpl shard, BufferArray data, boolean readOnly) {
      super(shard,data,null,readOnly); key = StorageImpl.this.key();
    }
    public DataKey key() { return key.readOnly(); }
    public boolean isValid() { return peek() != null; }
    public org.integratedmodelling.klab.api.knowledge.Concept peek() { if(!hasNext())throw new NoSuchElementException(); return key.lookup(data.intValue(index)); }
    public org.integratedmodelling.klab.api.knowledge.Concept get() { var value=peek();index++;return value; }
    public void add(org.integratedmodelling.klab.api.knowledge.Concept value) {
      synchronized(scanLock) {
        assertUnleased();assertWritable();if(!hasNext())throw new NoSuchElementException();
        int code=key.code(value);data.set(index++,code);scanGeneration++;readableShards.remove(shard.getUrn());
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
      synchronized (scanLock) {
        assertUnleased();
        assertWritable();
        addToHistogram(histogram, value);
        data.set(index++, value);
        scanGeneration++;
        readableShards.remove(shard.getUrn());
      }
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
      synchronized (scanLock) {
        assertUnleased();
        assertWritable();
        addToHistogram(histogram, value);
        data.set(index++, value);
        scanGeneration++;
        readableShards.remove(shard.getUrn());
      }
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
      synchronized (scanLock) {
        assertUnleased();
        assertWritable();
        addToHistogram(histogram, value);
        data.set(index++, value);
        scanGeneration++;
        readableShards.remove(shard.getUrn());
      }
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
      synchronized (scanLock) {
        assertUnleased();
        assertWritable();
        addToHistogram(histogram, value);
        data.set(index++, value);
        scanGeneration++;
        readableShards.remove(shard.getUrn());
      }
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
      synchronized (scanLock) {
        assertUnleased();
        assertWritable();
        addToHistogram(histogram, value ? 1 : 0);
        data.set(index++, (byte) (value ? 1 : 0));
        scanGeneration++;
        readableShards.remove(shard.getUrn());
      }
    }
  }

  private static void addToHistogram(com.dynatrace.dynahist.Histogram histogram, double value) {
    if (histogram != null && !Double.isNaN(value)) {
      histogram.addValue(value);
    }
    // TODO HistogramImpl supports missing counts but DynaHist does not. Track missing/no-data
    // values alongside the dynamic histogram once the runtime has one canonical no-data policy.
  }
}
