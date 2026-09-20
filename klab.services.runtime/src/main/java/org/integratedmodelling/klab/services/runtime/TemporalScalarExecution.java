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
    var output =
        writes.scan(target, strategy, strategy.getScannerClass(), TemporalWriteSet.Access.WRITE);
    var readers = new LinkedHashMap<String, List<? extends Storage.Scanner>>();
    var names = computation.inputNames() == null ? inputs.keySet() : computation.inputNames();
    for (var name : names) {
      var input = name.equals("self") ? target : inputs.get(name);
      if (input == null) throw new IllegalArgumentException("Unknown scalar input " + name);
      var sourceStrategy = input.getContextualizationData().getNativeShardingStrategy();
      var scans =
          writes.scan(
              input,
              sourceStrategy,
              sourceStrategy.getScannerClass(),
              priorInputs || input.getId() == target.getId()
                  ? TemporalWriteSet.Access.PRIOR
                  : TemporalWriteSet.Access.CURRENT);
      if (scans.size() != output.size())
        throw new UnsupportedOperationException("Scalar input/output splits require mediation");
      for (int n = 0; n < scans.size(); n++) {
        if (!scans
            .get(n)
            .shard()
            .getGeometry()
            .encode()
            .equals(output.get(n).shard().getGeometry().encode()))
          throw new UnsupportedOperationException(
              "Scalar input/output locations require geometry mediation");
      }
      readers.put(name.equals("self") ? "__prior_self" : name, scans);
    }
    for (int n = 0; n < output.size(); n++) {
      var scanners = new HashMap<String, Storage.Scanner>();
      scanners.put("self", output.get(n));
      for (var entry : readers.entrySet()) scanners.put(entry.getKey(), entry.getValue().get(n));
      if (!computation.execute(scanners, event, scope)) return false;
    }
    return true;
  }
}
