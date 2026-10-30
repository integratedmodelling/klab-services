package org.integratedmodelling.klab.services.runtime;

import java.util.*;
import org.integratedmodelling.common.lang.ServiceCallImpl;
import org.integratedmodelling.klab.api.data.Metadata;
import org.integratedmodelling.klab.api.digitaltwin.Scheduler;
import org.integratedmodelling.klab.api.knowledge.CollectiveReductionBinding;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.services.Reasoner;
import org.integratedmodelling.klab.api.services.runtime.Actuator;
import org.integratedmodelling.klab.services.scopes.ServiceContextScope;

/** Certified collective query followed by a real scalar-storage computation. */
final class CollectiveReductionExecutor implements CompiledDataflow.ContextualExecutor {
  private final RuntimeService runtime;
  private final Observation output;
  private final Actuator actuator;
  private final CollectiveReductionBinding binding;
  private Throwable cause;

  CollectiveReductionExecutor(RuntimeService runtime, Actuator actuator, CollectiveReductionBinding binding) {
    this.runtime = runtime; this.actuator = actuator; this.output = actuator.getObservation(); this.binding = binding;
  }

  @Override public boolean validate() { return output != null && binding != null; }
  @Override public Throwable getCause() { return cause; }

  @Override public boolean execute(Scheduler.Event event, ServiceContextScope scope,
      RuntimeService.ContextualizationScope results) {
    try {
      if (event == null || event.getType() != Scheduler.Event.Type.INITIALIZATION)
        throw new IllegalStateException("Collective reduction supports initialization only");
      binding.require(scope, actuator.getOperationObservable(), output.getGeometry());
      var collective = scope.getService(Reasoner.class).resolveObservable(binding.operand());
      var builderQuery = new Observation.NaiveBuilder(collective, scope);
      builderQuery.geometry(output.getGeometry()).query();
      var query = builderQuery.make();
      var completed = runtime.queryCollective(query, scope, binding);
      if (completed == null || completed.isEmpty())
        throw new IllegalStateException("Collective discovery is unresolved; no zero/absence can be produced");
      var coverage = completed.getMetadata().get(Metadata.IM_QUERY_COVERAGE, Number.class);
      if (coverage == null || !Double.isFinite(coverage.doubleValue()) || coverage.doubleValue() < 1.0 - 1e-9)
        throw new IllegalStateException("Collective discovery does not cover the whole requested support");
      // getMembers clips actual cohort members to the proven support. Producer childrenCount is not evidence.
      var ids = new HashSet<String>();
      for (var member : runtime.getMembers(completed, scope)) {
        if (member == null || member.isEmpty() || member.getId() == Observation.UNASSIGNED_ID || member.getId() == Observation.QUERY_ID || member.getTransientId() == 0)
          throw new IllegalStateException("Unregistered or invalid collective member");
        runtime.requireReductionMemberObserver(member, scope, binding);
        ids.add(member.getId() > 0 ? "id:"+member.getId() : "transient:"+member.getTransientId());
      }
      Object value = binding.mode() == CollectiveReductionBinding.Mode.COUNT ? (Object)(long)ids.size() : !ids.isEmpty();
      binding.require(scope, actuator.getOperationObservable(), output.getGeometry());
      var builder = runtime.getComputationBuilder(output, scope, actuator, Map.of());
      if (!builder.add(new ServiceCallImpl("klab.core.constant.resolver", "value", value)))
        throw new IllegalStateException("Cannot compile collective scalar result");
      var writer = new ScalarOperationExecutor(builder, output, Map.of(), scope, runtime.shardExecution());
      if (!writer.validate() || !writer.execute(event, scope, results)) {
        cause = writer.getCause();
        if (cause == null) cause = new IllegalStateException("Collective scalar storage failed");
        return false;
      }
      binding.require(scope, actuator.getOperationObservable(), output.getGeometry());
      return true;
    } catch (RuntimeException failure) { cause = failure; return false; }
  }
}
