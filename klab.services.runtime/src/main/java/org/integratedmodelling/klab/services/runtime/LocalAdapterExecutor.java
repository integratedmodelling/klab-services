package org.integratedmodelling.klab.services.runtime;

import org.integratedmodelling.klab.api.data.Storage;
import org.integratedmodelling.klab.api.digitaltwin.DigitalTwin;
import org.integratedmodelling.klab.api.digitaltwin.GraphModel;
import org.integratedmodelling.klab.api.digitaltwin.Scheduler;
import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.api.knowledge.Resource;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.services.resources.adapters.Adapter;
import org.integratedmodelling.klab.api.services.resources.adapters.ResourceAdapter;
import org.integratedmodelling.klab.api.services.runtime.Dataflow;
import org.integratedmodelling.klab.data.LocalResourceContextualizer;
import org.integratedmodelling.klab.services.scopes.ServiceContextScope;

import java.util.Map;

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
      Scheduler.Event event, Map<String, Storage.Scanner> scanners, ContextScope scope) {

    var res = resource;
    if (adapter.hasContextualizer()) {
      res =
          adapter.contextualize(
              resource, scanners.get(Dataflow.SELF_ID).shard().getGeometry(), scope);
    }

    // TODO do something with the deps

    // enqueue data extraction from adapter method TODO needs the scanner
    final var contextualizer =
        new LocalResourceContextualizer(adapter, res, observation, dependencies);

    // TODO this cannot be the simple executor, needs the scanner to be passed after
    return contextualizer.contextualize(scanners.get(Dataflow.SELF_ID), event, scope);
  }
}
