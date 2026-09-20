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
    var extent =
        GeometryRepository.INSTANCE
            .scale(
                Geometry.create(
                    "T0(1){ttype=PHYSICAL,tstart="
                        + time.getStart().getMilliseconds()
                        + ",tend="
                        + time.getEnd().getMilliseconds()
                        + "}"))
            .getTime();
    return GeometryRepository.INSTANCE.scale(geometry).with(extent);
  }

  @Override
  public synchronized <T extends Storage.Scanner> List<T> scan(
      Observation observation, Data.ShardingStrategy layout, Class<T> type, Access access) {
    if (prepared) throw new IllegalStateException("Temporal write set is sealed");
    var view = views.computeIfAbsent(observation.getId(), ignored -> new View(observation));
    if (!view.storage.getNativeShardingStrategy().equals(layout))
      throw new UnsupportedOperationException("Temporal scanner layout mediation is not available");
    var ret = new ArrayList<T>();
    for (int n = 0; n < view.layout.size(); n++)
      ret.add(ScannerAdapters.adaptType(view.scanner(n, access), type));
    return ret;
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
            default ->
                throw new UnsupportedOperationException(
                    "Temporal keyed writers require persistent keys");
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
                  case "peek" -> value(partition, cursor[0], access == Access.PRIOR);
                  case "get" -> value(partition, cursor[0]++, access == Access.PRIOR);
                  case "add" -> {
                    if (access != Access.WRITE || prepared)
                      throw new IllegalStateException("Scanner is not writable");
                    long index = cursor[0]++;
                    Object next = args[0];
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
