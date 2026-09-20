package org.integratedmodelling.klab.runtime.storage;

import java.util.*;
import org.integratedmodelling.klab.api.data.*;
import org.integratedmodelling.klab.api.digitaltwin.Scheduler;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.scope.ContextScope;

/** Bounded integration assertions for k.Actors. Pure codec correctness has independent JUnit oracles. */
public final class StorageReadInspector {
  private StorageReadInspector() {}
  /** User supplies an independent expected affine mapping; sampled views must agree with it. */
  public static boolean unitCheck(ContextScope scope, Observation observation, String unit, double factor, double offset) {
    if (!(observation.getObservable() instanceof org.integratedmodelling.common.knowledge.ObservableImpl original))
      throw new IllegalArgumentException("Unsupported observable implementation");
    var requested = new org.integratedmodelling.common.knowledge.ObservableImpl(original);
    requested.setUnit(new org.integratedmodelling.klab.data.mediation.UnitServiceImpl().getUnit(unit));
    var view = StorageReads.binding(observation, requested);
    check(scope, view, Data.FillCurve.D2_YX, 3, 8);
    try (var nativeRead = StorageReads.open(observation, scope, null, Data.FillCurve.D2_YX, Storage.DoubleScanner.class);
         var converted = StorageReads.open(view, scope, null, Data.FillCurve.D2_YX, Storage.DoubleScanner.class)) {
      var from = nativeRead.scanners().getFirst(); var to = converted.scanners().getFirst();
      int count = (int)Math.min(64, from.size());
      for (int i = 0; i < count; i++) {
        long index = count == 1 ? 0 : (from.size()-1)/(count-1)*i;
        from.seek(index); to.seek(index);
        if (from.isValid() != to.isValid()) throw new IllegalStateException("Mediation changed missingness");
        if (!from.isValid()) continue;
        double expected = from.peek()*factor + offset;
        if (Math.abs(to.peek()-expected) > Math.max(1e-10, Math.abs(expected)*1e-12))
          throw new IllegalStateException("Incorrect unit conversion at " + index);
      }
    }
    return true;
  }

  public static boolean check(ContextScope scope, Observation observation, Data.FillCurve curve, int splits, int samples) {
    if (splits < 1 || splits > 256 || samples < 1 || samples > 64)
      throw new IllegalArgumentException("Inspector limits: 1..256 partitions and 1..64 samples per partition");
    var event = StorageReads.event(observation, null);
    var source = StorageReads.source(observation, scope);
    var storage = scope.getDigitalTwin().getStorageManager().getStorage(source);
    var original = StorageScan.Layout.of(storage.getNativeShardingStrategy());
    var semantics = StorageScan.semantics(source.getObservable());
    var layout = new Data.ShardingStrategy(curve, splits, 0, 0, null);
    var plan = storage.plan(StorageReads.request(observation, event, layout, List.of(), Storage.Scanner.class));
    try (var single = StorageReads.open(observation, scope, event, curve, Storage.Scanner.class);
         var partitioned = storage.open(plan)) {
      var whole = single.scanners().getFirst();
      var wholeView = whole.view();
      var virtual = new StorageScan.SourceShard("inspector:whole", wholeView.partition().geometry(), whole.size(), 0, 0,
          new StorageScan.Layout(curve, 1, 0, 0, wholeView.valueType()));
      var mapping = ConformantScan.compile(List.of(virtual), StorageReads.request(observation, event, layout,
          plan.description().partitions(), Storage.Scanner.class), observation.getGeometry().encode());
      long[] cell = new long[mapping.sources[0].shape.length];
      for (int p = 0; p < partitioned.scanners().size(); p++) {
        var scanner = partitioned.scanners().get(p);
        int count = (int)Math.min(samples, scanner.size());
        for (int i = 0; i < count; i++) {
          long offset = count == 1 ? 0 : (scanner.size() - 1) / (count - 1) * i;
          scanner.seek(offset);
          mapping.targets[p].decode(offset, curve, cell);
          long global = mapping.sources[0].encode(cell, curve);
          whole.seek(global);
          var expected = StorageScan.textValue(whole);
          if (!expected.equals(StorageScan.textValue(scanner)) || scanner.position() != offset
              || !expected.equals(StorageReads.text(observation, scope, event, curve, global)))
            throw new IllegalStateException("Storage view mismatch at partition " + p + ", offset " + offset);
          if (scanner.nextLong() != offset || scanner.position() != offset + 1)
            throw new IllegalStateException("Storage cursor advanced incorrectly");
        }
      }
    }
    if (!original.equals(StorageScan.Layout.of(storage.getNativeShardingStrategy()))
        || !semantics.equals(StorageScan.semantics(source.getObservable())))
      throw new IllegalStateException("Read mediation changed the producer contract");
    return true;
  }
}
