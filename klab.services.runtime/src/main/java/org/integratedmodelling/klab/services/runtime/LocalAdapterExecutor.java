package org.integratedmodelling.klab.services.runtime;

import java.util.Map;
import org.integratedmodelling.klab.api.data.Storage;
import org.integratedmodelling.klab.api.digitaltwin.Scheduler;
import org.integratedmodelling.klab.api.knowledge.Resource;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.services.resources.adapters.Adapter;
import org.integratedmodelling.klab.api.services.resources.adapters.ResourceAdapter;
import org.integratedmodelling.klab.api.services.runtime.Dataflow;
import org.integratedmodelling.klab.api.services.runtime.Notification;
import org.integratedmodelling.klab.data.LocalResourceContextualizer;

public class LocalAdapterExecutor extends AbstractExecutor
    implements CompiledDataflow.ContextualExecutor {

  private final Adapter adapter;
  private Resource resource;

  public LocalAdapterExecutor(
      CompiledDataflow.CallDescriptors callInfo,
      Observation observation,
      Map<String, Observation> dependencies,
      ContextScope scope) {
    super(callInfo, observation, scope, dependencies);
    this.adapter = callInfo.embeddedAdapter();
    this.resource = callInfo.resource();
  }

  @Override
  public boolean validate() {
    return adapter.validate(
        resource, scope, ResourceAdapter.Validator.LifecyclePhase.PreContextualization);
  }

  @Override
  protected boolean run(
      Scheduler.Event event,
      Map<String, Storage.Scanner> scanners,
      ContextScope scope,
      RuntimeService.ContextualizationScope contextualizationScope) {

    var res = resource;
    if (adapter.hasContextualizer()) {
      try {
        res =
            adapter.contextualize(
                resource,
                scanners == null ? null : scanners.get(Dataflow.SELF_ID).shard().getGeometry(),
                scope);
      } catch (Throwable e) {
        observation.getNotifications().add(Notification.error(e));
        return false;
      }
    }

    // TODO do something with the deps

    // enqueue data extraction from adapter method TODO needs the scanner
    final var contextualizer =
        new LocalResourceContextualizer(adapter, res, observation, dependencies);

    try {
      // TODO this cannot be the simple executor, needs the scanner to be passed after
      return contextualizer.contextualize(
          scanners == null ? null : scanners.get(Dataflow.SELF_ID),
          event,
          scope,
          contextualizationScope);
    } catch (Throwable e) {
      observation.getNotifications().add(Notification.error(e));
      return false;
    }
  }
}
