package org.integratedmodelling.klab.services.runtime;

import org.integratedmodelling.klab.api.data.Storage;
import org.integratedmodelling.klab.api.digitaltwin.Scheduler;
import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.api.knowledge.Resource;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.services.resources.adapters.Adapter;
import org.integratedmodelling.klab.data.ServiceResourceContextualizer;

import java.util.Map;

public class LocalAdapterExecutor extends AbstractExecutor
    implements CompiledDataflow.ContextualExecutor {

  private final Adapter adapter;
  private Resource resource;

  public LocalAdapterExecutor(
      CompiledDataflow.CallDescriptors callInfo,
      Observation observation,
      Map<String, Observable> localNames,
      ContextScope scope) {
    super(callInfo, observation, scope, localNames);
    this.adapter = callInfo.embeddedAdapter();
    this.resource = callInfo.resource();
  }

  @Override
  protected boolean run(Scheduler.Event event, Storage.Scanner scanner) {

    if (adapter.hasContextualizer()) {
      resource = adapter.contextualize(resource, scanner.shard().getGeometry(), scope);
    }

    // enqueue data extraction from adapter method TODO needs the scanner
    final var contextualizer =
        new ServiceResourceContextualizer(adapter, resource, observation, scope.getDigitalTwin());

    // TODO this cannot be the simple executor, needs the scanner to be passed after
    return contextualizer.contextualize(
        // pass the operation for provenance recording
        // TODO must get the event only
        observation, event, scope);
  }
}
