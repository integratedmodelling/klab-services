package org.integratedmodelling.klab.services.runtime;

import org.integratedmodelling.klab.api.data.Storage;
import org.integratedmodelling.klab.api.digitaltwin.Scheduler;
import org.integratedmodelling.klab.api.digitaltwin.StorageManager;
import org.integratedmodelling.klab.api.exceptions.KlabIllegalStateException;
import org.integratedmodelling.klab.api.exceptions.KlabInternalErrorException;
import org.integratedmodelling.klab.api.exceptions.KlabResourceAccessException;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.knowledge.Urn;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.lang.ServiceCall;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.utils.Utils;
import org.integratedmodelling.klab.components.ComponentRegistry;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class ContextualizerExecutor extends AbstractExecutor
    implements CompiledDataflow.ContextualExecutor {

  private final ComponentRegistry componentRegistry;
  private final ServiceCall call;

  public ContextualizerExecutor(
      ComponentRegistry componentRegistry,
      CompiledDataflow.CallDescriptors callInfo,
      Observation observation,
      ServiceCall call,
      ContextScope scope) {
    super(callInfo, observation, scope);
    this.componentRegistry = componentRegistry;
    this.call = call;
  }

  protected boolean run(Scheduler.Event event, Storage.Scanner scanner) {

    var geometry = scanner.shard().getGeometry();

    if (componentRegistry.implementation(callInfo.serviceInfo()).method != null) {

      var implementation = componentRegistry.implementation(callInfo.serviceInfo());
      var arguments =
          ComponentRegistry.matchArguments(
              implementation.method,
              callInfo.resource(),
              geometry,
              null,
              observation,
              observation.getObservable(),
              callInfo.resource() == null ? null : Urn.of(callInfo.resource().getUrn()),
              call.getParameters(),
              call,
              storage,
              null, // expression,
              null, // lookupTable,
              null,
              event,
              scope);

      if (arguments == null) {
        return false;
      }

      if (callInfo.serviceInfo().staticMethod) {
        try {
          var context =
              componentRegistry
                  .implementation(callInfo.serviceInfo())
                  .method
                  .invoke(null, arguments.toArray());

        } catch (Exception e) {
          cause = e;
          scope.error(e /* TODO tracing parameters */);
          return false;
        }

      } else if (componentRegistry.implementation(callInfo.serviceInfo()).mainClassInstance
          != null) {
        try {
          var context =
              componentRegistry
                  .implementation(callInfo.serviceInfo())
                  .method
                  .invoke(
                      componentRegistry.implementation(callInfo.serviceInfo()).mainClassInstance,
                      arguments.toArray());
          return true;
        } catch (Exception e) {
          cause = e;
          scope.error(e /* TODO tracing parameters */);
          return false;
        }
      }
    }
    return true;
  }
}
