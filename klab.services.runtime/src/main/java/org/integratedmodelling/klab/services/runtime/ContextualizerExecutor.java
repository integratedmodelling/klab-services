package org.integratedmodelling.klab.services.runtime;

import java.util.Arrays;
import java.util.Map;
import org.integratedmodelling.klab.api.data.Data;
import org.integratedmodelling.klab.api.data.Storage;
import org.integratedmodelling.klab.api.digitaltwin.Scheduler;
import org.integratedmodelling.klab.api.exceptions.KlabUnimplementedException;
import org.integratedmodelling.klab.api.knowledge.Urn;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.lang.ServiceCall;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.services.runtime.Dataflow;
import org.integratedmodelling.klab.components.ComponentRegistry;

public class ContextualizerExecutor extends AbstractExecutor
    implements CompiledDataflow.ContextualExecutor {

  private final ComponentRegistry componentRegistry;
  private final ServiceCall call;

  public ContextualizerExecutor(
      ComponentRegistry componentRegistry,
      CompiledDataflow.CallDescriptors callInfo,
      Observation observation,
      Map<String, Observation> dependencies,
      ServiceCall call,
      ContextScope scope) {
    super(callInfo, observation, scope, dependencies);
    this.componentRegistry = componentRegistry;
    this.call = call;
  }

  @Override
  protected boolean run(
      Scheduler.Event event,
      Map<String, Storage.Scanner> scanners,
      ContextScope scope,
      RuntimeService.ContextualizationScope contextualizationScope) {

    var geometry =
        scanners.get(Dataflow.SELF_ID) == null
            ? observation.getGeometry()
            : scanners.get(Dataflow.SELF_ID).shard().getGeometry();

    if (componentRegistry.implementation(callInfo.serviceInfo()).method != null) {

      var implementation = componentRegistry.implementation(callInfo.serviceInfo());

      Data.Builder builder = null;
      boolean needsBuilder =
          Arrays.stream(implementation.method.getParameterTypes())
              .anyMatch(cls -> cls.isAssignableFrom(Data.Builder.class));

      if (needsBuilder) {
        // TODO! MUST PROVIDE A BUILDER IF REQUESTED - for instantiators it's the only way
        throw new KlabUnimplementedException("HOSTIA MAKE A BUILDER FOR THE INSTANTIATOR");
      }

      var arguments =
          matchArguments(
              callInfo.serviceInfo().serviceInfo,
              implementation.method,
              callInfo.resource(),
              geometry,
              builder,
              scanners,
              observation,
              observation.getObservable(),
              // TODO can be smarter if a resource or a resource URN is in the parameters
              callInfo.resource() == null ? null : Urn.of(callInfo.resource().getUrn()),
              call.getParameters(),
              call,
              // TODO can be smarter if an expression is in the parameters
              null, // expression,
              // TODO can be smarter if a lookup table is in the parameters
              null, // lookupTable,
              null, // TODO INPUTS add
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

          // TODO PROCESS RESULT - BOOLEAN, NOTIFICATION ETC.

          // TODO INGEST DATA IF BUILDER WAS PASSED

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

  @Override
  public boolean validate() {
    // TODO validate service call, return types etc.
    return true;
  }
}
