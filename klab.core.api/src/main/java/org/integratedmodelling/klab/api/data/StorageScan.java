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
  public enum Sampling { EXACT, NEAREST, INTERPOLATE, CONSERVATIVE }
  public enum Operation { IDENTITY, FLOAT_TO_DOUBLE, DOUBLE_TO_FLOAT }
  public enum Capability { NATIVE_READ, FLOAT_ADAPTATION, INDEXED_READ, BLOCK_READ, VALIDITY, PINNED_SESSION }
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
  public record Semantics(String observable, String unit, String range, String currency, String contextualDimensions)
      implements Serializable {
    public Semantics {
      Objects.requireNonNull(observable); Objects.requireNonNull(unit); Objects.requireNonNull(range);
      Objects.requireNonNull(currency); Objects.requireNonNull(contextualDimensions);
    }
  }

  public record Partition(String id, String geometry, long size) implements Serializable {
    public Partition {
      text(id, "partition id"); text(geometry, "partition geometry");
      var parsed = Geometry.create(geometry);
      if (size < 0 || parsed.size() != size)
        throw new IllegalArgumentException("Partition size must match its geometry");
      geometry = parsed.encode();
    }
  }

  /** Null geometry/semantics and an empty partition list select the native values at planning time. */
  public record Request<T extends Storage.Scanner>(Slice slice, Layout layout, String geometry,
      List<Partition> partitions, Semantics semantics, Class<T> scannerClass, Access access,
      Precision precision, Coverage coverage, Sampling sampling, Budget budget) {
    public Request {
      Objects.requireNonNull(slice); Objects.requireNonNull(layout); Objects.requireNonNull(scannerClass);
      Objects.requireNonNull(access); Objects.requireNonNull(precision); Objects.requireNonNull(coverage);
      Objects.requireNonNull(sampling); Objects.requireNonNull(budget);
      partitions = List.copyOf(partitions);
      if (geometry != null) geometry = Geometry.create(geometry).encode();
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

  public record SourceShard(String urn, String geometry, long size, int index, long timestamp, Layout layout)
      implements Serializable {
    public SourceShard {
      text(urn, "source shard URN"); Objects.requireNonNull(layout);
      geometry = new Partition(urn, geometry, size).geometry();
      if (index < 0) throw new IllegalArgumentException("Negative shard index");
    }
  }

  /** Version 1 describes identity traversal with optional primitive floating-point adaptation. */
  public record Description(int version, String sourceRevision, long observationId, String observationUrn, Slice slice,
      Semantics sourceSemantics, Semantics targetSemantics, Layout nativeLayout, Layout requestedLayout,
      List<SourceShard> sources, List<Partition> partitions, Storage.Type valueType,
      Precision precision, Coverage coverage, Sampling sampling, Budget budget,
      List<Operation> operations, HistogramPolicy histogram) implements Serializable {
    public Description {
      if (version != 1) throw new IllegalArgumentException("Unsupported scan description version: " + version);
      text(sourceRevision, "source revision");
      Objects.requireNonNull(observationUrn); Objects.requireNonNull(slice);
      Objects.requireNonNull(sourceSemantics); Objects.requireNonNull(targetSemantics);
      Objects.requireNonNull(nativeLayout); Objects.requireNonNull(requestedLayout);
      Objects.requireNonNull(valueType); Objects.requireNonNull(precision); Objects.requireNonNull(coverage);
      Objects.requireNonNull(sampling); Objects.requireNonNull(budget); Objects.requireNonNull(histogram);
      sources = List.copyOf(sources); partitions = List.copyOf(partitions); operations = List.copyOf(operations);
      if (sources.isEmpty() || sources.size() != partitions.size() || sources.size() > budget.maxPartitions())
        throw new IllegalArgumentException("Invalid native source/partition count");
      if (sources.stream().map(SourceShard::urn).distinct().count() != sources.size()
          || partitions.stream().map(Partition::id).distinct().count() != partitions.size())
        throw new IllegalArgumentException("Duplicate scan source or partition");
      if (coverage != Coverage.EXACT || sampling != Sampling.EXACT || !sourceSemantics.equals(targetSemantics)
          || !nativeLayout.equals(requestedLayout))
        throw new IllegalArgumentException("Version 1 only supports native layout and semantics");
      Operation operation = operation(nativeLayout.type(), valueType, precision);
      if (!operations.equals(List.of(operation))) throw new IllegalArgumentException("Invalid operation pipeline");
      for (int i = 0; i < sources.size(); i++) {
        var source = sources.get(i); var target = partitions.get(i);
        if (source.index() != i || !source.layout().equals(nativeLayout)
            || source.size() != target.size() || !source.geometry().equals(target.geometry()))
          throw new IllegalArgumentException("Version 1 requires ordered, aligned native partitions");
      }
    }

    /** SHA-256 of versioned, length-prefixed UTF-8 fields, in record/list order. */
    public String fingerprint() {
      try {
        var digest = MessageDigest.getInstance("SHA-256");
        fingerprintFields(digest, this);
        return HexFormat.of().formatHex(digest.digest());
      } catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
  }

  /** Plan handles are deliberately not serializable; reopening a description requires planning. */
  public interface Plan<T extends Storage.Scanner> {
    Description description();
    Class<T> scannerClass();
  }

  /** Consumer metadata is separate from physical shards and is never a HAS_DATA descriptor. */
  public record View(Partition partition, Data.FillCurve curve, Storage.Type valueType,
      Semantics semantics, Slice slice, List<SourceShard> sources, HistogramPolicy histogram) {
    public View { sources = List.copyOf(sources); }
  }

  /** Location of the NEXT value; requesting it never advances the cursor. */
  public record Location(Partition partition, Data.FillCurve curve, Slice slice, long offset) {}

  public interface Session<T extends Storage.Scanner> extends AutoCloseable {
    List<T> scanners();
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
    if (source == Storage.Type.KEYED || target == Storage.Type.KEYED)
      throw new UnsupportedOperationException("KEYED scanning requires a durable dictionary");
    if (source == target) return Operation.IDENTITY;
    if (source == Storage.Type.FLOAT && target == Storage.Type.DOUBLE) return Operation.FLOAT_TO_DOUBLE;
    if (source == Storage.Type.DOUBLE && target == Storage.Type.FLOAT && precision == Precision.ALLOW_FLOAT_NARROWING)
      return Operation.DOUBLE_TO_FLOAT;
    throw new IllegalArgumentException("Unsupported precision/type conversion: " + source + " -> " + target);
  }

  private static void text(String value, String name) {
    if (value == null || value.isBlank()) throw new IllegalArgumentException("Missing " + name);
  }

  private static void fingerprintFields(MessageDigest digest, Object value) {
    if (value instanceof List<?> list) {
      fingerprintFields(digest, list.size());
      list.forEach(item -> fingerprintFields(digest, item));
    } else if (value.getClass().isRecord()) {
      for (var component : value.getClass().getRecordComponents()) {
        try { fingerprintFields(digest, component.getAccessor().invoke(value)); }
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
