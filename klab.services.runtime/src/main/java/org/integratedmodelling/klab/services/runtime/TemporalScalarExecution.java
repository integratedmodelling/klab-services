package org.integratedmodelling.klab.services.runtime;

import java.util.*;
import org.integratedmodelling.klab.api.data.*;
import org.integratedmodelling.klab.api.digitaltwin.Scheduler;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.services.runtime.ScalarComputation;

/** Scanner binding shared by inline process outputs and downstream scalar quality actuators. */
final class TemporalScalarExecution {
  static boolean run(
      ScalarComputation computation,
      Observation target,
      Map<String, Observation> inputs,
      Scheduler.Event event,
      ContextScope scope,
      boolean priorInputs) {
    var writes = scope.getCurrentTransaction().getTemporalWrites();
    if (writes == null) throw new IllegalStateException("Temporal computation has no write set");
    var strategy = target.getContextualizationData().getNativeShardingStrategy();
    try (var resources = new org.integratedmodelling.klab.runtime.language.ScanResources()) {
      var eventSupport = event.getBoundary() == Scheduler.Event.Boundary.NONE || event.getEvent() == null
          ? null : org.integratedmodelling.klab.runtime.storage.StorageReads.spatialSupport(event.getEvent());
      StorageScan.Session<? extends Storage.Scanner> eventOutput = null;
      if (eventSupport != null && !Boolean.TRUE.equals(target.getMetadata().get(
          org.integratedmodelling.klab.runtime.storage.TemporalHistory.EPHEMERAL))) {
        var layout = new Data.ShardingStrategy(strategy.getCurve(), 1, 0, 0, null);
        eventOutput = resources.add(writes.write(target,
            org.integratedmodelling.klab.runtime.storage.StorageReads.request(target, event, layout,
                List.of(), Storage.Scanner.class, eventSupport)));
      }
      var partitions = eventOutput == null ? writes.writeLayout(target) : eventOutput.description().partitions();
      var readers = new LinkedHashMap<String, List<? extends Storage.Scanner>>();
      var names = computation.inputNames() == null ? inputs.keySet() : computation.inputNames();
      for (var name : names) {
        var input = name.equals("self") ? target : inputs.get(name);
        if (input == null) throw new IllegalArgumentException("Unknown scalar input " + name);
        var source = org.integratedmodelling.klab.runtime.storage.StorageReads.source(input, scope);
        var layout = new Data.ShardingStrategy(strategy.getCurve(), partitions.size(), 0, 0, null);
        var targetSupport = eventSupport == null
            ? org.integratedmodelling.klab.runtime.storage.StorageReads.spatialSupport(target) : eventSupport;
        var request = new StorageScan.Request<>(StorageScan.Slice.of(event), StorageScan.Layout.of(layout),
            targetSupport == null ? null : targetSupport.encode(), partitions, StorageScan.semantics(input.getObservable()), Storage.Scanner.class,
            StorageScan.Access.READ_ONLY, StorageScan.Precision.LOSSLESS, StorageScan.Coverage.MISSING_OUTSIDE,
            input.getObservable().is(org.integratedmodelling.klab.api.knowledge.SemanticType.CLASS) ? StorageScan.Sampling.MAJORITY : StorageScan.Sampling.NEAREST, StorageScan.Budget.defaults(), org.integratedmodelling.klab.runtime.storage.StorageReads.rate(input));
        var session = resources.add(writes.read(source, request,
            priorInputs || source.getId() == target.getId() ? TemporalWriteSet.Access.PRIOR : TemporalWriteSet.Access.CURRENT));
        org.integratedmodelling.klab.runtime.storage.StorageReads.record(scope, target.getId() + ":" + name, session.description());
        var scans = session.scanners();
        if (!scans.stream().map(scanner -> scanner.view().partition()).toList().equals(partitions))
          throw new IllegalStateException("Temporal planner changed output partitions");
        readers.put(name.equals("self") ? "__prior_self" : name, scans);
      }
      var output = eventOutput == null
          ? writes.scan(target, strategy, strategy.getScannerClass(), TemporalWriteSet.Access.WRITE)
          : eventOutput.scanners();
      if (output.size() != partitions.size()) throw new IllegalStateException("Temporal output layout changed");
      for (int n = 0; n < output.size(); n++) {
        var scanners = new HashMap<String, Storage.Scanner>();
        scanners.put("self", output.get(n));
        for (var entry : readers.entrySet()) scanners.put(entry.getKey(), entry.getValue().get(n));
        if (!computation.execute(scanners, event, scope)) return false;
      }
      return true;
    }
  }
}
