package org.integratedmodelling.klab.runtime.storage;

import java.lang.reflect.Proxy;
import java.util.*;
import org.integratedmodelling.common.knowledge.GeometryRepository;
import org.integratedmodelling.klab.api.data.*;
import org.integratedmodelling.klab.api.digitaltwin.*;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.knowledge.observation.impl.ObservationImpl;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.common.data.impl.ShardImpl;
import org.integratedmodelling.klab.runtime.language.ScannerAdapters;

/** Lazy sparse overlays. No committed buffer is writable, including during failed attempts. */
public final class LocalTemporalWriteSet implements TemporalWriteSet {
  private final Scheduler.Event event;
  private final ContextScope scope;
  private final DigitalTwin.Transaction transaction;
  private final Map<Long, View> views = new LinkedHashMap<>();
  private volatile boolean prepared;

  public LocalTemporalWriteSet(Scheduler.Event event, ContextScope scope) {
    this.event = event;
    this.scope = scope;
    this.transaction = scope.getCurrentTransaction();
    if (transaction == null || event.getType() == Scheduler.Event.Type.INITIALIZATION)
      throw new IllegalArgumentException("Temporal writes require a non-INIT transaction");
    transaction.setTemporalWrites(this);
  }

  public static Geometry localize(Geometry geometry, Scheduler.Event event) {
    var time = event.getTime();
    var space = geometry.dimension(Geometry.Dimension.Type.SPACE);
    // Preserve the established scale encoding for whole observations (also used in delta evidence).
    // Bare shard bounds must remain geometry: they may inherit a CRS and cannot build a full tile.
    if (space != null && space.getParameters().containsKey("shape")) {
      var extent = GeometryRepository.INSTANCE.scale(Geometry.create("T0(1){ttype=PHYSICAL,tstart="
          + time.getStart().getMilliseconds() + ",tend=" + time.getEnd().getMilliseconds() + "}")).getTime();
      return GeometryRepository.INSTANCE.scale(geometry).with(extent);
    }
    var encoded = new StringBuilder("T0(1){ttype=PHYSICAL,tstart=")
        .append(time.getStart().getMilliseconds()).append(",tend=")
        .append(time.getEnd().getMilliseconds()).append("}");
    // Shard bounds may inherit their owner's CRS; localization changes time only.
    for (var dimension : geometry.getDimensions())
      if (dimension.getType() != Geometry.Dimension.Type.TIME) encoded.append(dimension.encode());
    return StorageScan.parseGeometry(encoded.toString());
  }

  @Override
  public synchronized <T extends Storage.Scanner> List<T> scan(
      Observation observation, Data.ShardingStrategy layout, Class<T> type, Access access) {
    if (prepared) throw new IllegalStateException("Temporal write set is sealed");
    var view = views.computeIfAbsent(observation.getId(), ignored -> new View(observation));
    view.storage.validateKeyWorldview();
    if (!view.storage.getNativeShardingStrategy().equals(layout))
      throw new UnsupportedOperationException("Temporal scanner layout mediation is not available");
    var ret = new ArrayList<T>();
    for (int n = 0; n < view.layout.size(); n++)
      ret.add(ScannerAdapters.adaptType(view.scanner(n, access), type));
    return ret;
  }

  @Override
  public synchronized List<StorageScan.Partition> writeLayout(Observation observation) {
    if (prepared) throw new IllegalStateException("Temporal write set is sealed");
    var view = views.computeIfAbsent(observation.getId(), ignored -> new View(observation));
    view.storage.validateKeyWorldview();
    var result = new ArrayList<StorageScan.Partition>();
    for (int i = 0; i < view.layout.size(); i++) {
      var geometry = view.layout.get(i);
      result.add(new StorageScan.Partition("task-" + i, geometry.encode(), geometry.size()));
    }
    return List.copyOf(result);
  }

  @Override
  public synchronized <T extends Storage.Scanner> StorageScan.Session<T> read(
      Observation observation, StorageScan.Request<T> request, Access access) {
    if (prepared || access == Access.WRITE || request.access() != StorageScan.Access.READ_ONLY)
      throw new IllegalArgumentException("Temporal reads require an unsealed read-only snapshot");
    if (!request.slice().equals(StorageScan.Slice.of(event)))
      throw new UnsupportedOperationException("Temporal read requires this transaction's exact support");
    var view = views.computeIfAbsent(observation.getId(), ignored -> new View(observation));
    view.storage.validateKeyWorldview();
    var semantics = StorageScan.semantics(view.observation.getObservable());
    var targetSemantics = ValueMediation.target(semantics, request.semantics());
    var conversion = ValueMediation.compile(semantics, targetSemantics, request.rate());
    ValueMediation.validateType(conversion, view.storage.getNativeType());
    var nativeLayout = StorageScan.Layout.of(view.storage.getNativeShardingStrategy());
    var valueType = StorageScan.type(request.scannerClass(), view.storage.getNativeType());
    var operation = StorageScan.operation(nativeLayout.type(), valueType, request.precision());
    if (request.layout().type() != null && request.layout().type() != nativeLayout.type()
        && request.layout().type() != valueType) throw new IllegalArgumentException("Conflicting temporal value type");
    if (view.layout.size() > request.budget().maxPartitions()) throw new IllegalArgumentException("Source budget exceeded");
    var sources = new ArrayList<StorageScan.SourceShard>();
    var shards = new ArrayList<Storage.Shard>();
    for (int i = 0; i < view.layout.size(); i++) {
      var geometry = view.layout.get(i);
      sources.add(new StorageScan.SourceShard("transaction:" + observation.getId() + ":" + i,
          geometry.encode(), geometry.size(), i, event.getTime().getEnd().getMilliseconds(), nativeLayout));
      // A transaction overlay is not a persisted physical shard.
      shards.add(null);
    }
    var partitions = request.partitions();
    boolean aligned = partitions.size() == sources.size() && request.layout().curve() == nativeLayout.curve();
    for (int i = 0; aligned && i < sources.size(); i++)
      aligned = partitions.get(i).geometry().equals(sources.get(i).geometry());
    var nativeSpace = StorageReads.spatialSupport(view.observation);
    aligned &= request.geometry() == null
        || request.geometry().equals(StorageScan.parseGeometry(view.support.encode()).encode())
        || nativeSpace != null && request.geometry().equals(nativeSpace.encode());
    ScanMapping mapping = aligned ? null : SpatialScan.plan(sources, request, view.support.encode(), StorageReads.acceptsLossy(scope));
    if (mapping != null) partitions = mapping.partitions();
    SpatialScan.validateConversion(mapping, conversion);
    boolean spatial = mapping instanceof SpatialScan;
    var description = new StorageScan.Description(view.storage.getNativeType() == Storage.Type.KEYED ? 5 : spatial ? 4 : conversion == null ? 2 : 3, "transaction:" + UUID.randomUUID(),
        observation.getId(), Objects.toString(observation.getUrn(), ""), request.slice(), semantics, targetSemantics,
        nativeLayout, request.layout(), sources, partitions, valueType, request.precision(), spatial ? request.coverage() : StorageScan.Coverage.EXACT,
        spatial ? request.sampling() : StorageScan.Sampling.EXACT, request.budget(), spatial ? SpatialScan.operations(conversion, operation) : conversion == null ? List.of(StorageScan.Operation.INDEX_REMAP, operation)
            : List.of(StorageScan.Operation.INDEX_REMAP, StorageScan.Operation.VALUE_CONVERSION, operation), StorageScan.HistogramPolicy.UNAVAILABLE, conversion, SpatialScan.metadata(mapping), view.storage.getNativeType() == Storage.Type.KEYED ? view.storage.key().snapshot() : null);
    StorageScan.Plan<T> plan = new StorageScan.Plan<>() {
      public StorageScan.Description description() { return description; }
      public Class<T> scannerClass() { return request.scannerClass(); }
    };
    var release = view.storage.pinReads();
    var readers = new ArrayList<IndexedStorageReader>();
    try {
      for (int i = 0; i < view.layout.size(); i++) {
        var baseline = view.baseline.isEmpty() ? null : view.storage.openReader(view.baseline.get(i), request.budget().blockValues());
        var changes = access == Access.PRIOR ? Map.<Long, Object>of() : Map.copyOf(view.changes.get(i));
        if (baseline == null && changes.size() != view.layout.get(i).size())
          throw new IllegalStateException("Created quality has no complete requested state");
        readers.add(new TemporalIndexedReader(baseline, changes, view.storage.getNativeType(),
            view.layout.get(i).size(), request.budget().blockValues(), view.storage.getNativeType() == Storage.Type.KEYED ? view.storage.key().readOnly() : null));
      }
      var session = new LocalScanSession<>(plan, shards, readers, mapping, release);
      transaction.afterCommit(session::close);
      transaction.afterRollback(session::close);
      return session;
    } catch (RuntimeException | Error e) {
      for (var reader : readers) try { reader.close(); } catch (RuntimeException failure) { e.addSuppressed(failure); }
      release.run(); throw e;
    }
  }

  @Override
  public synchronized boolean changed(Observation observation) {
    return views.containsKey(observation.getId()) && views.get(observation.getId()).changed();
  }

  @Override
  public synchronized Set<Observation> changedObservations() {
    var ret = Collections.newSetFromMap(new IdentityHashMap<Observation, Boolean>());
    views
        .values()
        .forEach(
            view -> {
              if (view.changed()) ret.add(view.observation);
            });
    return Collections.unmodifiableSet(ret);
  }

  @Override
  public synchronized void prepare() {
    if (prepared) throw new IllegalStateException("Temporal write set already prepared");
    prepared = true;
    for (var view : views.values()) {
      if (!view.changed()) continue;
      view.validateComplete();
      view.storage.stageTemporal(
          event,
          view.support,
          view.layout,
          (partition, index) -> view.value(partition, index, false),
          transaction);
      if (view.observation instanceof ObservationImpl concrete) {
        var timestamps = new ArrayList<>(concrete.getEventTimestamps());
        var before = new ArrayList<>(timestamps);
        timestamps.add(event.getTime().getEnd().getMilliseconds());
        concrete.setEventTimestamps(timestamps);
        transaction.afterRollback(() -> concrete.setEventTimestamps(before));
      }
    }
  }

  private final class View {
    final Observation observation;
    final StorageImpl storage;
    final Geometry support;
    final List<Storage.Shard> baseline;
    final List<Geometry> layout;
    final List<Map<Long, Object>> changes = new ArrayList<>();
    final boolean created;

    View(Observation observation) {
      var candidate = scope.getDigitalTwin().getStorageManager().createStorage(observation);
      if (!(candidate instanceof StorageImpl local))
        throw new UnsupportedOperationException(
            "Storage backend does not implement temporal writes");
      storage = local;
      storage.validateKeyWorldview();
      // Graph traversals may return another object for the same ID. Data, timestamps and deltas
      // must all update the owner held by the storage cache.
      observation = local.temporalOwner();
      this.observation = observation;
      support = localize(observation.getGeometry(), event);
      boolean ephemeral =
          Boolean.TRUE.equals(observation.getMetadata().get(TemporalHistory.EPHEMERAL));
      baseline = storage.temporalBaseline(event, ephemeral);
      created = ephemeral;
      if (baseline.isEmpty() && !created)
        throw new IllegalStateException(
            "Affected quality has no committed baseline: " + observation.getUrn());
      layout =
          baseline.isEmpty()
              ? storage.temporalLayout(support)
              : baseline.stream().map(shard -> localize(shard.getGeometry(), event)).toList();
      for (int i = 0; i < layout.size(); i++) changes.add(new HashMap<>());
    }

    boolean changed() {
      return changes.stream().anyMatch(map -> !map.isEmpty());
    }

    void validateComplete() {
      if (created)
        for (int n = 0; n < layout.size(); n++)
          if (changes.get(n).size() != layout.get(n).size())
            throw new IllegalStateException(
                "Created output must cover every declared location explicitly");
    }

    Object value(int partition, long index, boolean prior) {
      if (!prior && changes.get(partition).containsKey(index))
        return changes.get(partition).get(index);
      if (baseline.isEmpty()) throw new IllegalStateException("Created quality has no prior value");
      return storage.nativeValue(baseline.get(partition), index);
    }

    Storage.Scanner scanner(int partition, Access access) {
      var type = storage.getNativeType();
      Class<? extends Storage.Scanner> api =
          switch (type) {
            case DOUBLE -> Storage.DoubleScanner.class;
            case FLOAT -> Storage.FloatScanner.class;
            case INTEGER -> Storage.IntScanner.class;
            case LONG -> Storage.LongScanner.class;
            case BOOLEAN -> Storage.BooleanScanner.class;
            case KEYED -> Storage.KeyScanner.class;
          };
      var descriptor =
          new ShardImpl(
              Geometry.forTransport(layout.get(partition)),
              observation,
              storage.getNativeShardingStrategy(),
              partition,
              layout.size(),
              event.getTime().getEnd().getMilliseconds(),
              scope.getConfiguration().getPersistence(),
              type);
      long size = layout.get(partition).size();
      long[] cursor = {0};
      return (Storage.Scanner)
          Proxy.newProxyInstance(
              Storage.class.getClassLoader(),
              new Class<?>[] {api},
              (proxy, method, args) -> {
                switch (method.getName()) {
                  case "key": return storage.key().readOnly();
                  case "isValid": return type == Storage.Type.KEYED ? ((Integer)value(partition,cursor[0],access==Access.PRIOR)) != 0 : true;
                  case "shard":
                    return descriptor;
                  case "size":
                    return size;
                  case "hasNext":
                    return cursor[0] < size;
                  case "toString":
                    return "TemporalScanner(" + observation.getId() + "," + access + ")";
                  case "hashCode":
                    return System.identityHashCode(proxy);
                  case "equals":
                    return proxy == args[0];
                }
                if (cursor[0] >= size) throw new NoSuchElementException("Scanner exhausted");
                return switch (method.getName()) {
                  case "nextLong", "next" -> cursor[0]++;
                  case "peek" -> type == Storage.Type.KEYED ? storage.key().lookup((Integer)value(partition,cursor[0],access==Access.PRIOR)) : value(partition, cursor[0], access == Access.PRIOR);
                  case "get" -> type == Storage.Type.KEYED ? storage.key().lookup((Integer)value(partition,cursor[0]++,access==Access.PRIOR)) : value(partition, cursor[0]++, access == Access.PRIOR);
                  case "add" -> {
                    if (access != Access.WRITE || prepared)
                      throw new IllegalStateException("Scanner is not writable");
                    Object next = type == Storage.Type.KEYED ? storage.key().code(args[0]) : args[0];
                    long index = cursor[0]++;
                    if (!created
                        && Objects.equals(
                            next, storage.nativeValue(baseline.get(partition), index)))
                      changes.get(partition).remove(index);
                    else changes.get(partition).put(index, next);
                    yield null;
                  }
                  default ->
                      throw new UnsupportedOperationException(
                          "Unsupported temporal scanner operation: " + method.getName());
                };
              });
    }
  }
}
