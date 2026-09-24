package org.integratedmodelling.klab.api.data;

import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import org.integratedmodelling.klab.api.digitaltwin.Scheduler;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.knowledge.observation.scale.time.Time;
import org.integratedmodelling.klab.api.knowledge.observation.scale.time.TimePeriod;

/** Immutable read-view contracts. Plans are provider-bound; descriptions are portable data only. */
public final class StorageScan {
  private StorageScan() {}

  public enum Precision { LOSSLESS, ALLOW_FLOAT_NARROWING }
  public enum Access { READ_ONLY, WRITE }
  public enum Coverage { EXACT, MISSING_OUTSIDE }
  /** CONSERVATIVE averages densities over overlap; CONSERVATIVE_TOTAL apportions source cell totals.
   * Neither policy is category aggregation. INTERPOLATE and conservative policies require floats. */
  public enum Sampling { EXACT, NEAREST, INTERPOLATE, CONSERVATIVE, CONSERVATIVE_TOTAL, MAJORITY }
  public enum Operation { IDENTITY, INDEX_REMAP, FLOAT_TO_DOUBLE, DOUBLE_TO_FLOAT, VALUE_CONVERSION, SPATIAL_RESAMPLE }
  public enum Capability { NATIVE_READ, FLOAT_ADAPTATION, INDEXED_READ, BLOCK_READ, VALIDITY, PINNED_SESSION, CONFORMANT_READ, VALUE_MEDIATION, SPATIAL_READ, KEYED_READ }
  public enum HistogramPolicy { UNAVAILABLE }

  /** Mutable strategy beans must be snapshotted before they become plan/cache metadata. */
  public record Layout(Data.FillCurve curve, int splits, long minSize, long maxSize, Storage.Type type)
      implements Serializable {
    public Layout {
      new Data.ShardingStrategy(curve, splits, minSize, maxSize, type).validate();
    }
    public static Layout of(Data.ShardingStrategy strategy) {
      Objects.requireNonNull(strategy).validate();
      return new Layout(strategy.getCurve(), strategy.getSuggestedSplits(), strategy.getMinSplitSize(),
          strategy.getMaxBufferSize(), strategy.getDataType());
    }
    public Data.ShardingStrategy strategy() { return new Data.ShardingStrategy(curve, splits, minSize, maxSize, type); }
  }

  /** The event is snapshotted; no live event/observation/time object is retained by a request. */
  public record Slice(Scheduler.Event.Type type, String key, long start, long end) implements Serializable {
    public Slice {
      Objects.requireNonNull(type); text(key, "event key");
      if (type != Scheduler.Event.Type.INITIALIZATION && end <= start)
        throw new IllegalArgumentException("A scan interval must have positive duration");
    }
    public static Slice of(Scheduler.Event event) {
      Objects.requireNonNull(event);
      boolean init = event.getType() == Scheduler.Event.Type.INITIALIZATION;
      return new Slice(event.getType(), event.toKey(), init ? 0 : event.getTime().getStart().getMilliseconds(),
          init ? 0 : event.getTime().getEnd().getMilliseconds());
    }
    public Scheduler.Event event() {
      if (type == Scheduler.Event.Type.INITIALIZATION) return Scheduler.Event.initialization();
      return new Scheduler.Event() {
        public Type getType() { return type; }
        public String toKey() { return key; }
        public Time getTime() { return TimePeriod.create(start, end); }
        public Observation getEvent() { return null; }
      };
    }
  }

  public record Budget(int maxPartitions, int blockValues) implements Serializable {
    public Budget {
      if (maxPartitions < 1 || blockValues < 1) throw new IllegalArgumentException("Scan budgets must be positive");
    }
    public static Budget defaults() { return new Budget(65536, 65536); }
  }

  /** Definitions, not executable mediators or reasoner-local IDs. Empty fields mean absent. */
  public record Semantics(String observable, String unit, String range, String currency, String contextualDimensions, String meaning)
      implements Serializable {
    public Semantics(String observable, String unit, String range, String currency, String contextualDimensions) {
      this(observable, unit, range, currency, contextualDimensions, observable);
    }
    public Semantics {
      Objects.requireNonNull(observable); Objects.requireNonNull(unit); Objects.requireNonNull(range);
      Objects.requireNonNull(currency); Objects.requireNonNull(contextualDimensions);
      if (meaning == null) meaning = observable;
    }
  }

  /** Portable semantic snapshot shared by storage providers and consumer bindings. */
  public static Semantics semantics(org.integratedmodelling.klab.api.knowledge.Observable observable) {
    String unit = "", currency = "", range = "", dimensions = "";
    if (observable.getUnit() != null) {
      if (!(observable.getUnit() instanceof org.integratedmodelling.klab.api.data.mediation.impl.UnitImpl impl))
        throw new UnsupportedOperationException("Unit definition is not portable");
      unit = Objects.requireNonNull(impl.getDefinition());
      dimensions = (impl.isContextual() ? "contextual:" : "") + new TreeMap<>(impl.getAggregatedDimensions()).toString();
    }
    if (observable.getCurrency() != null) {
      if (!(observable.getCurrency() instanceof org.integratedmodelling.klab.api.data.mediation.impl.CurrencyImpl impl))
        throw new UnsupportedOperationException("Currency definition is not portable");
      currency = Objects.requireNonNull(impl.getDefinition());
    }
    if (observable.getRange() != null) {
      var r = observable.getRange();
      range = r.getLowerBound() + ":" + r.isLowerExclusive() + ":" + r.getUpperBound() + ":" + r.isUpperExclusive();
    }
    return new Semantics(Objects.toString(observable.getUrn(), ""), unit, range, currency, dimensions,
        observable.getSemantics() == null ? Objects.toString(observable.getUrn(), "") :
        observable.getSemantics().getUrn() + "|" + observable.getContextualization() + "|"
            + (observable.getObserverSemantics() == null ? "" : observable.getObserverSemantics().getUrn()));
  }

  /** Exact, locale-independent text for the next value, without advancing. Missing is "null".
   * Boxing is confined to callers that explicitly request object values, never this path. */
  public static String textValue(Storage.Scanner scanner) {
    if (!scanner.isValid()) return "null";
    return switch (scanner) {
      case Storage.DoubleScanner s -> Double.toString(s.peek());
      case Storage.FloatScanner s -> Float.toString(s.peek());
      case Storage.LongScanner s -> Long.toString(s.peek());
      case Storage.IntScanner s -> Integer.toString(s.peek());
      case Storage.BooleanScanner s -> Boolean.toString(s.peek());
      case Storage.KeyScanner<?> s -> ((org.integratedmodelling.klab.api.knowledge.Concept)s.peek()).getUrn();
      default -> throw new UnsupportedOperationException("No text representation for this scanner");
    };
  }

  public record Partition(String id, String geometry, long size) implements Serializable {
    public Partition {
      text(id, "partition id"); text(geometry, "partition geometry");
      var parsed = parseGeometry(geometry);
      if (size < 0 || parsed.size() != size)
        throw new IllegalArgumentException("Partition size must match its geometry");
      geometry = parsed.encode();
    }
  }

  /** Null geometry/semantics select native coverage/semantics. Empty partitions let the planner
   * derive partitions from the requested layout; an exact native request retains native partitions. */
  public record Request<T extends Storage.Scanner>(Slice slice, Layout layout, String geometry,
      List<Partition> partitions, Semantics semantics, Class<T> scannerClass, Access access,
      Precision precision, Coverage coverage, Sampling sampling, Budget budget, org.integratedmodelling.klab.api.services.CurrencyService.Rate rate) {
    public Request(Slice slice, Layout layout, String geometry, List<Partition> partitions, Semantics semantics,
        Class<T> scannerClass, Access access, Precision precision, Coverage coverage, Sampling sampling, Budget budget) {
      this(slice, layout, geometry, partitions, semantics, scannerClass, access, precision, coverage, sampling, budget, null);
    }
    public Request {
      Objects.requireNonNull(slice); Objects.requireNonNull(layout); Objects.requireNonNull(scannerClass);
      Objects.requireNonNull(access); Objects.requireNonNull(precision); Objects.requireNonNull(coverage);
      Objects.requireNonNull(sampling); Objects.requireNonNull(budget);
      partitions = List.copyOf(partitions);
      if (geometry != null) geometry = parseGeometry(geometry).encode();
      if (partitions.size() > budget.maxPartitions()) throw new IllegalArgumentException("Partition budget exceeded");
      if (partitions.stream().map(Partition::id).distinct().count() != partitions.size())
        throw new IllegalArgumentException("Duplicate consumer partition identity");
    }
    public static <T extends Storage.Scanner> Request<T> nativeRead(Scheduler.Event event,
        Data.ShardingStrategy strategy, Class<T> scannerClass) {
      return new Request<>(Slice.of(event), Layout.of(strategy), null, List.of(), null, scannerClass,
          Access.READ_ONLY, Precision.LOSSLESS, Coverage.EXACT, Sampling.EXACT, Budget.defaults());
    }
  }

  /** A cell in one full-coverage consumer traversal. Positive source identity is authorized in
   * the calling context. Null geometry/semantics select the durable source definitions. */
  public record Point(long sourceId, Slice slice, Data.FillCurve curve, String geometry,
      Semantics semantics, long offset, org.integratedmodelling.klab.api.services.CurrencyService.Rate rate) implements Serializable {
    public Point(long sourceId, Slice slice, Data.FillCurve curve, String geometry, Semantics semantics, long offset) {
      this(sourceId, slice, curve, geometry, semantics, offset, null);
    }
    public Point {
      if (sourceId <= 0 || offset < 0) throw new IllegalArgumentException("Invalid source ID or cell offset");
      Objects.requireNonNull(slice); Objects.requireNonNull(curve);
      if (geometry != null) geometry = parseGeometry(geometry).encode();
    }
  }

  public record SourceShard(String urn, String geometry, long size, int index, long timestamp, Layout layout)
      implements Serializable {
    public SourceShard {
      text(urn, "source shard URN"); Objects.requireNonNull(layout);
      geometry = new Partition(urn, geometry, size).geometry();
      if (index < 0) throw new IllegalArgumentException("Negative shard index");
    }
  }

  /** Portable, precompiled ordinary conversion. No mediator or per-cell object is retained. */
  public record Conversion(String kind, double factor, double offset, String sourceRange,
      String targetRange, org.integratedmodelling.klab.api.services.CurrencyService.Rate rate) implements Serializable {
    public Conversion {
      if (!Set.of("UNIT", "RANGE", "CURRENCY").contains(kind) || !Double.isFinite(factor)
          || factor <= 0 || !Double.isFinite(offset)) throw new IllegalArgumentException("Invalid value conversion");
      Objects.requireNonNull(sourceRange); Objects.requireNonNull(targetRange);
      if (kind.equals("CURRENCY") != (rate != null)) throw new IllegalArgumentException("Currency rate required exclusively for currency conversion");
      if (rate != null && (factor != rate.factor() || offset != 0)) throw new IllegalArgumentException("Rate/kernel mismatch");
    }
  }

  /** Effective CRS identities, including inherited definitions omitted from physical shard geometry.
   * Description version 4 fixes XY axis order, strict transforms and binary64 overlap policy. */
  public record Spatial(String sourceCrs, String targetCrs) implements Serializable {
    public Spatial { text(sourceCrs, "source CRS"); text(targetCrs, "target CRS"); }
  }

  /** Version 1 is native identity; version 2 adds conformant remapping; version 3 adds value conversion.
   * Version 4 adds spatial resampling with the versioned XY/binary64 policy documented in STORAGE.md.
   * Providers validate conformance and compile conversion before issuing executable handles. */
  public record Description(int version, String sourceRevision, long observationId, String observationUrn, Slice slice,
      Semantics sourceSemantics, Semantics targetSemantics, Layout nativeLayout, Layout requestedLayout,
      List<SourceShard> sources, List<Partition> partitions, Storage.Type valueType,
      Precision precision, Coverage coverage, Sampling sampling, Budget budget,
      List<Operation> operations, HistogramPolicy histogram, Conversion conversion, Spatial spatial,
      org.integratedmodelling.klab.api.data.mediation.classification.KeyedData.Dictionary dictionary) implements Serializable {
    public Description(int version, String sourceRevision, long observationId, String observationUrn, Slice slice,
        Semantics sourceSemantics, Semantics targetSemantics, Layout nativeLayout, Layout requestedLayout,
        List<SourceShard> sources, List<Partition> partitions, Storage.Type valueType, Precision precision,
        Coverage coverage, Sampling sampling, Budget budget, List<Operation> operations, HistogramPolicy histogram,
        Conversion conversion, Spatial spatial) {
      this(version,sourceRevision,observationId,observationUrn,slice,sourceSemantics,targetSemantics,nativeLayout,requestedLayout,
          sources,partitions,valueType,precision,coverage,sampling,budget,operations,histogram,conversion,spatial,null);
    }
    public Description(int version, String sourceRevision, long observationId, String observationUrn, Slice slice,
        Semantics sourceSemantics, Semantics targetSemantics, Layout nativeLayout, Layout requestedLayout,
        List<SourceShard> sources, List<Partition> partitions, Storage.Type valueType, Precision precision,
        Coverage coverage, Sampling sampling, Budget budget, List<Operation> operations, HistogramPolicy histogram,
        Conversion conversion) {
      this(version, sourceRevision, observationId, observationUrn, slice, sourceSemantics, targetSemantics,
          nativeLayout, requestedLayout, sources, partitions, valueType, precision, coverage, sampling, budget,
          operations, histogram, conversion, null);
    }
    public Description(int version, String sourceRevision, long observationId, String observationUrn, Slice slice,
        Semantics sourceSemantics, Semantics targetSemantics, Layout nativeLayout, Layout requestedLayout,
        List<SourceShard> sources, List<Partition> partitions, Storage.Type valueType, Precision precision,
        Coverage coverage, Sampling sampling, Budget budget, List<Operation> operations, HistogramPolicy histogram) {
      this(version, sourceRevision, observationId, observationUrn, slice, sourceSemantics, targetSemantics,
          nativeLayout, requestedLayout, sources, partitions, valueType, precision, coverage, sampling, budget,
          operations, histogram, null);
    }
    public Description {
      if (version != 1 && version != 2 && version != 3 && version != 4 && version != 5) throw new IllegalArgumentException("Unsupported scan description version: " + version);
      text(sourceRevision, "source revision");
      Objects.requireNonNull(observationUrn); Objects.requireNonNull(slice);
      Objects.requireNonNull(sourceSemantics); Objects.requireNonNull(targetSemantics);
      Objects.requireNonNull(nativeLayout); Objects.requireNonNull(requestedLayout);
      Objects.requireNonNull(valueType); Objects.requireNonNull(precision); Objects.requireNonNull(coverage);
      Objects.requireNonNull(sampling); Objects.requireNonNull(budget); Objects.requireNonNull(histogram);
      sources = List.copyOf(sources); partitions = List.copyOf(partitions); operations = List.copyOf(operations);
      if (sources.isEmpty() || partitions.isEmpty() || sources.size() > budget.maxPartitions()
          || partitions.size() > budget.maxPartitions() || version == 1 && sources.size() != partitions.size())
        throw new IllegalArgumentException("Invalid native source/partition count");
      if (sources.stream().map(SourceShard::urn).distinct().count() != sources.size()
          || partitions.stream().map(Partition::id).distinct().count() != partitions.size())
        throw new IllegalArgumentException("Duplicate scan source or partition");
      if (version < 4 && (coverage != Coverage.EXACT || sampling != Sampling.EXACT) || version < 3 && !sourceSemantics.equals(targetSemantics)
          || version == 1 && !nativeLayout.equals(requestedLayout))
        throw new IllegalArgumentException("Exact coverage is required; versions 1/2 require native semantics and version 1 requires native layout");
      if ((version == 5) != (dictionary != null) || valueType == Storage.Type.KEYED && dictionary == null)
        throw new IllegalArgumentException("Keyed plans require version 5 dictionary evidence");
      if (dictionary != null && (valueType != Storage.Type.KEYED || conversion != null
          || spatial == null && (sampling != Sampling.EXACT || coverage != Coverage.EXACT)))
        throw new IllegalArgumentException("Invalid keyed plan policy");
      Operation operation = operation(nativeLayout.type(), valueType, precision);
      if (version < 4 && ((version == 3) != (conversion != null))) throw new IllegalArgumentException("Conversion requires version 3");
      if (version < 5 && (version == 4) != (spatial != null)) throw new IllegalArgumentException("Spatial CRS metadata requires version 4");
      if (spatial != null && sampling == Sampling.EXACT) throw new IllegalArgumentException("Spatial description requires a sampling policy");
      if (!operations.equals(spatial != null ? (conversion == null
          ? List.of(Operation.SPATIAL_RESAMPLE, operation)
          : List.of(Operation.SPATIAL_RESAMPLE, Operation.VALUE_CONVERSION, operation)) : version == 1 ? List.of(operation) : version == 2 || version == 5 ? List.of(Operation.INDEX_REMAP, operation)
          : List.of(Operation.INDEX_REMAP, Operation.VALUE_CONVERSION, operation))) throw new IllegalArgumentException("Invalid operation pipeline");
      for (int i = 0; i < sources.size(); i++) {
        var source = sources.get(i);
        if (source.index() != i || !source.layout().equals(nativeLayout))
          throw new IllegalArgumentException("Invalid source layout or order");
        if (version == 1 && (source.size() != partitions.get(i).size()
            || !source.geometry().equals(partitions.get(i).geometry())))
          throw new IllegalArgumentException("Version 1 requires ordered, aligned native partitions");
      }
    }

    /** SHA-256 of versioned, length-prefixed UTF-8 fields, in record/list order. */
    public String fingerprint() {
      try {
        var digest = MessageDigest.getInstance("SHA-256");
        fingerprintFields(digest, this, version < 3);
        return HexFormat.of().formatHex(digest.digest());
      } catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
  }

  /** One stable named dependency. Parallel AFFECTS relationships retain separate bindings. */
  public record Binding(int version, String id, Semantics source, Semantics target, Conversion conversion)
      implements Serializable {
    public static final String PROPERTY = "storageMediation";
    public Binding {
      if (version != 1 || id == null || id.isBlank()) throw new IllegalArgumentException("Invalid mediation binding schema");
      Objects.requireNonNull(source); Objects.requireNonNull(target);
      if (!source.equals(target) && conversion == null) throw new IllegalArgumentException("Missing binding conversion");
    }
  }

  /** Plan handles are deliberately not serializable; reopening a description requires planning. */
  public interface Plan<T extends Storage.Scanner> {
    Description description();
    Class<T> scannerClass();
  }

  /** Consumer metadata is separate from physical shards and is never a HAS_DATA descriptor. */
  public record View(Partition partition, Data.FillCurve curve, Storage.Type valueType,
      Semantics semantics, Slice slice, List<SourceShard> sources, HistogramPolicy histogram, Spatial spatial) {
    public View(Partition partition, Data.FillCurve curve, Storage.Type valueType, Semantics semantics,
        Slice slice, List<SourceShard> sources, HistogramPolicy histogram) {
      this(partition, curve, valueType, semantics, slice, sources, histogram, null);
    }
    public View { sources = List.copyOf(sources); }
  }

  /** Location of the NEXT value; requesting it never advances the cursor. */
  public record Location(Partition partition, Data.FillCurve curve, Slice slice, long offset) {}

  public interface Session<T extends Storage.Scanner> extends AutoCloseable {
    List<T> scanners();
    /** Immutable execution evidence, when supplied by the provider. */
    default Description description() { return null; }
    boolean isClosed();
    /** Cancellation has the same release/invalidation semantics as close. */
    default void cancel() { close(); }
    @Override void close();
  }

  public static Storage.Type type(Class<? extends Storage.Scanner> scannerClass, Storage.Type nativeType) {
    if (scannerClass == Storage.Scanner.class) return nativeType;
    for (var type : Storage.Type.values())
      if (Data.ShardingStrategy.trivial(type).getScannerClass() == scannerClass) return type;
    throw new IllegalArgumentException("Unsupported scanner class: " + scannerClass.getName());
  }

  public static Operation operation(Storage.Type source, Storage.Type target, Precision precision) {
    Objects.requireNonNull(source); Objects.requireNonNull(target); Objects.requireNonNull(precision);
    if (source != target && (source == Storage.Type.KEYED || target == Storage.Type.KEYED))
      throw new UnsupportedOperationException("KEYED scanning requires a durable dictionary");
    if (source == target) return Operation.IDENTITY;
    if (source == Storage.Type.FLOAT && target == Storage.Type.DOUBLE) return Operation.FLOAT_TO_DOUBLE;
    if (source == Storage.Type.DOUBLE && target == Storage.Type.FLOAT && precision == Precision.ALLOW_FLOAT_NARROWING)
      return Operation.DOUBLE_TO_FLOAT;
    throw new IllegalArgumentException("Unsupported precision/type conversion: " + source + " -> " + target);
  }

  /** Decode persisted geometry text, including unescaped commas inside WKT parameter values. */
  public static Geometry parseGeometry(String encoding) {
    var escaped = new StringBuilder(encoding.length());
    int braces = 0, parentheses = 0;
    for (int i = 0; i < encoding.length(); i++) {
      char c = encoding.charAt(i);
      if (c == '{') { braces++; parentheses = 0; }
      if (braces > 0 && c == '(') parentheses++;
      if (braces > 0 && c == ')') parentheses--;
      if (c == ',' && braces > 0 && parentheses > 0) escaped.append("&comma;");
      else escaped.append(c);
      if (c == '}') { braces--; parentheses = 0; }
    }
    return Geometry.create(escaped.toString());
  }

  private static void text(String value, String name) {
    if (value == null || value.isBlank()) throw new IllegalArgumentException("Missing " + name);
  }

  private static void fingerprintFields(MessageDigest digest, Object value, boolean legacy) {
    if (value == null) {
      digest.update(new byte[] {-1, -1, -1, -1});
    } else if (value instanceof List<?> list) {
      fingerprintFields(digest, list.size(), legacy);
      list.forEach(item -> fingerprintFields(digest, item, legacy));
    } else if (value.getClass().isRecord()) {
      for (var component : value.getClass().getRecordComponents()) {
        if (value instanceof Description d && d.version() < 5 && component.getName().equals("dictionary")) continue;
        if (value instanceof Description description && description.version() < 4 && component.getName().equals("spatial")) continue;
        if (legacy && (value instanceof Description && component.getName().equals("conversion")
            || value instanceof Semantics && component.getName().equals("meaning"))) continue;
        try { fingerprintFields(digest, component.getAccessor().invoke(value), legacy); }
        catch (ReflectiveOperationException e) { throw new IllegalStateException(e); }
      }
    } else {
      byte[] bytes = value.toString().getBytes(StandardCharsets.UTF_8);
      int length = bytes.length;
      digest.update(new byte[] {(byte)(length >>> 24), (byte)(length >>> 16), (byte)(length >>> 8), (byte)length});
      digest.update(bytes);
    }
  }
}
