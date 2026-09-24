package org.integratedmodelling.klab.runtime.storage;

import java.util.List;
import org.integratedmodelling.klab.api.data.*;
import org.integratedmodelling.klab.api.digitaltwin.Scheduler;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.scope.ContextScope;

/** Shared consumer binding. Requests are ephemeral; source ownership never follows a query ID. */
public final class StorageReads {
  private StorageReads() {}
  /** Read the current runtime policy at planning AND opening, including cached plans. */
  static boolean acceptsLossy(ContextScope scope) {
    if (scope == null) return true;
    org.integratedmodelling.klab.api.services.KlabService runtime = scope.getService(org.integratedmodelling.klab.api.services.RuntimeService.class);
    if (runtime == null || runtime.settings() == null) return true;
    // Resolver-side clients cache settings after the first read. Refresh their snapshot so a
    // runtime policy change cannot be bypassed by a previously connected resolver.
    if (runtime instanceof org.integratedmodelling.common.services.client.BaseServiceClient)
      runtime.settings().asMap();
    return !Boolean.FALSE.equals(runtime.settings().get(
        org.integratedmodelling.klab.api.configuration.Setting.ACCEPT_LOSSY_MEDIATIONS, Boolean.class));
  }

  public static final String RATE = "im:storage-currency-rate";
  public static final String BINDING_SOURCE = "im:storage-binding-source";

  /** Resolution-time geometry validation, before a reused quality is accepted as a dependency.
   * Synthetic descriptors contain metadata only; event/revision validity is checked by real reads. */
  public static void validateSpatialReuse(Observation source, Geometry requested, ContextScope scope) {
    if (source == null || source.getGeometry() == null || requested == null
        || !source.getObservable().is(org.integratedmodelling.klab.api.knowledge.SemanticType.QUALITY)) return;
    var from = source.getGeometry().dimension(Geometry.Dimension.Type.SPACE);
    var to = requested.dimension(Geometry.Dimension.Type.SPACE);
    if (from == null && to == null || from != null && to != null && from.encode().equals(to.encode())) return;
    if (from == null || to == null) {
      if (!acceptsLossy(scope)) throw SpatialScan.disabled("one quality has no spatial extent");
      throw new UnsupportedOperationException("Spatial mediation requires both source and target extents");
    }
    boolean keyed = source.getObservable().is(org.integratedmodelling.klab.api.knowledge.SemanticType.CLASS);
    var layout = new StorageScan.Layout(from.getDimensionality() == 2 ? Data.FillCurve.D2_XY : Data.FillCurve.D1_LINEAR,
        1, 0, 0, keyed ? Storage.Type.KEYED : Storage.Type.DOUBLE);
    var descriptor = new StorageScan.SourceShard("resolution-source", from.encode(), from.size(), 0, 0, layout);
    var request = new StorageScan.Request<>(StorageScan.Slice.of(Scheduler.Event.initialization()), layout,
        to.encode(), List.of(), null, Storage.Scanner.class, StorageScan.Access.READ_ONLY,
        StorageScan.Precision.LOSSLESS, StorageScan.Coverage.MISSING_OUTSIDE,
        keyed ? StorageScan.Sampling.MAJORITY : StorageScan.Sampling.NEAREST, StorageScan.Budget.defaults());
    SpatialScan.plan(List.of(descriptor), request, from.encode(), acceptsLossy(scope));
  }

  public static org.integratedmodelling.klab.api.services.CurrencyService.Rate rate(Observation observation) {
    Object value = observation.getMetadata().get(RATE);
    return value == null ? null : org.integratedmodelling.klab.utilities.Utils.Json.parseObject(value.toString(),
        org.integratedmodelling.klab.api.services.CurrencyService.Rate.class);
  }

  public static Observation binding(Observation source, Observation requested) {
    if (requested == null) return source;
    var bound = binding(source, requested.getObservable());
    if (requested.getMetadata().containsKey(RATE)) {
      if (!(bound instanceof org.integratedmodelling.klab.api.knowledge.observation.impl.ObservationImpl concrete))
        throw new IllegalArgumentException("Cannot detach currency binding");
      var copy = concrete.copyForReadBinding(bound.getObservable());
      copy.getMetadata().put(RATE, requested.getMetadata().get(RATE));
      bound = copy;
    }
    return bound;
  }

  /** A binding changes the consumer semantics, never the storage owner's observable. */
  public static Observation binding(Observation source, org.integratedmodelling.klab.api.knowledge.Observable requested) {
    if (requested == null || !source.getObservable().is(org.integratedmodelling.klab.api.knowledge.SemanticType.QUALITY)
        || StorageScan.semantics(source.getObservable()).equals(StorageScan.semantics(requested))) return source;
    if (!(source instanceof org.integratedmodelling.klab.api.knowledge.observation.impl.ObservationImpl concrete))
      throw new IllegalArgumentException("Cannot create detached binding from this observation implementation");
    var copy = concrete.copyForReadBinding(requested);
    if (source.getId() > 0) copy.getMetadata().put(BINDING_SOURCE, source.getId());
    return copy;
  }

  public record Evidence(int version, String binding, StorageScan.Description plan) {
    public Evidence {
      if (version != 1 || binding == null || binding.isBlank() || plan == null)
        throw new IllegalArgumentException("Invalid storage execution evidence");
    }
  }

  /** Activity publication shares the consumer transaction; old activities are never rewritten. */
  public static void record(ContextScope scope, String binding, StorageScan.Description plan) {
    var transaction = scope.getCurrentTransaction();
    if (transaction == null || transaction.getActivity() == null || plan == null) return;
    var metadata = transaction.getActivity().getMetadata();
    if (metadata == null) return;
    String key = "im:storage-read:" + java.util.UUID.randomUUID();
    synchronized (metadata) {
      metadata.put(key, org.integratedmodelling.klab.utilities.Utils.Json.asString(new Evidence(1, binding, plan)));
    }
    transaction.afterRollback(() -> { synchronized (metadata) { metadata.remove(key); } });
  }

  public static Observation source(Observation observation, ContextScope scope) {
    if (observation instanceof org.integratedmodelling.klab.api.knowledge.observation.impl.ObservationImpl bound
        && bound.storageSource() != null) return source(bound.storageSource(), scope);
    if (observation.getId() != Observation.QUERY_ID) {
      Object binding = observation.getMetadata().get(BINDING_SOURCE);
      if (binding == null) return observation;
      if (!(binding instanceof Number id) || id.longValue() <= 0) throw new IllegalArgumentException("Invalid binding source");
      var resolved = scope.getObservation(id.longValue());
      if (resolved == null || resolved.getId() != id.longValue()) throw new IllegalArgumentException("Missing binding source");
      return resolved;
    }
    Object ids = observation.getMetadata().get(Metadata.IM_QUERY_SOURCE_IDS);
    if (!(ids instanceof List<?> list) || list.size() != 1 || !(list.getFirst() instanceof Number id)
        || id.longValue() <= 0)
      throw new IllegalArgumentException("A quality query requires exactly one durable source ID");
    var source = scope.getObservation(id.longValue());
    if (source == null || source.getId() != id.longValue())
      throw new IllegalArgumentException("Query source is not available in this context: " + id);
    return source;
  }

  public static Geometry spatialSupport(Observation observation) {
    var space = observation.getGeometry().dimension(Geometry.Dimension.Type.SPACE);
    return space == null ? null : StorageScan.parseGeometry(space.encode());
  }

  public static Scheduler.Event event(Observation observation, Scheduler.Event event) {
    if (event != null) return event;
    var time = observation.getGeometry().dimension(Geometry.Dimension.Type.TIME);
    if (time != null && time.size() > 1)
      throw new IllegalArgumentException("An explicit temporal slice is required for this observation");
    return Scheduler.Event.initialization();
  }

  public static <T extends Storage.Scanner> StorageScan.Request<T> request(
      Observation consumer, Scheduler.Event event, Data.ShardingStrategy layout,
      List<StorageScan.Partition> partitions, Class<T> type) {
    return request(consumer, event, layout, partitions, type, partitions.isEmpty() ? consumer.getGeometry() : null);
  }

  /** Target support supplies the owning output CRS when native output partitions omit it. */
  public static <T extends Storage.Scanner> StorageScan.Request<T> request(
      Observation consumer, Scheduler.Event event, Data.ShardingStrategy layout,
      List<StorageScan.Partition> partitions, Class<T> type, Geometry targetSupport) {
    return new StorageScan.Request<>(StorageScan.Slice.of(event(consumer, event)),
        StorageScan.Layout.of(layout), targetSupport == null ? null : targetSupport.encode(), partitions,
        StorageScan.semantics(consumer.getObservable()), type, StorageScan.Access.READ_ONLY,
        type == Storage.FloatScanner.class ? StorageScan.Precision.ALLOW_FLOAT_NARROWING : StorageScan.Precision.LOSSLESS,
        StorageScan.Coverage.MISSING_OUTSIDE, consumer.getObservable().is(org.integratedmodelling.klab.api.knowledge.SemanticType.CLASS) ? StorageScan.Sampling.MAJORITY : StorageScan.Sampling.NEAREST, StorageScan.Budget.defaults(), rate(consumer));
  }

  /** One consumer traversal, irrespective of native shard count. The caller owns the session. */
  public static <T extends Storage.Scanner> StorageScan.Session<T> open(
      Observation observation, ContextScope scope, Scheduler.Event event, Data.FillCurve curve, Class<T> type) {
    var source = source(observation, scope);
    var storage = scope.getDigitalTwin().getStorageManager().getStorage(source);
    var nativeLayout = storage.getNativeShardingStrategy();
    var layout = new Data.ShardingStrategy(
        curve == null || curve == Data.FillCurve.UNSPECIFIED ? nativeLayout.getCurve() : curve,
        1, 0, 0, null);
    var plan = storage.plan(request(observation, event, layout, List.of(), type));
    if (plan.description().partitions().size() != 1)
      throw new IllegalArgumentException("Single traversal request did not produce one partition");
    return storage.open(plan);
  }

  public static String text(StorageScan.Point point, ContextScope scope) {
    var source = scope.getObservation(point.sourceId());
    if (source == null) throw new IllegalArgumentException("Unknown observation " + point.sourceId());
    var storage = scope.getDigitalTwin().getStorageManager().getStorage(source);
    var curve = point.curve() == Data.FillCurve.UNSPECIFIED ? storage.getNativeShardingStrategy().getCurve() : point.curve();
    var request = new StorageScan.Request<>(point.slice(), new StorageScan.Layout(curve, 1, 0, 0, null),
        point.geometry(), List.of(), point.semantics(), Storage.Scanner.class, StorageScan.Access.READ_ONLY,
        StorageScan.Precision.LOSSLESS, StorageScan.Coverage.MISSING_OUTSIDE, storage.getNativeType() == Storage.Type.KEYED ? StorageScan.Sampling.MAJORITY : StorageScan.Sampling.NEAREST, StorageScan.Budget.defaults(), point.rate());
    var plan = storage.plan(request);
    if (plan.description().partitions().size() != 1 || point.offset() >= plan.description().partitions().getFirst().size())
      throw new IndexOutOfBoundsException("Cell offset " + point.offset());
    try (var session = storage.open(plan)) {
      var scanner = session.scanners().getFirst(); scanner.seek(point.offset());
      return StorageScan.textValue(scanner);
    }
  }

  /** Bounded indexed cell access through precisely the same view as an exporter. */
  public static String text(Observation observation, ContextScope scope, Scheduler.Event event,
      Data.FillCurve curve, long offset) {
    try (var session = open(observation, scope, event, curve, Storage.Scanner.class)) {
      var scanner = session.scanners().getFirst();
      scanner.seek(offset);
      return StorageScan.textValue(scanner);
    }
  }
}
