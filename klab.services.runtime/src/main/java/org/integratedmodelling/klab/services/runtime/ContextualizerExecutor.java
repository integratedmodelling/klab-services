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

  private String inputBinding(java.lang.reflect.Parameter parameter) {
    var inputs = callInfo.serviceInfo().serviceInfo.listInputs();
    if (inputs.stream().noneMatch(input -> input.getName().equals(parameter.getName()))) return null;
    if (dependencies.containsKey(parameter.getName())) return parameter.getName();
    var names = dependencies.keySet().stream().filter(name -> !Dataflow.SELF_ID.equals(name)).toList();
    return names.size() == 1 ? names.getFirst() : null;
  }

  @Override
  protected Class<? extends Storage.Scanner> inputScannerClass(String name) {
    var method = componentRegistry.implementation(callInfo.serviceInfo()).method;
    if (method != null) for (var parameter : method.getParameters())
      if (name.equals(inputBinding(parameter)) && Storage.Scanner.class.isAssignableFrom(parameter.getType()))
        return parameter.getType().asSubclass(Storage.Scanner.class);
    return Storage.Scanner.class;
  }

  @Override
  protected void validateInputBindings(Map<String, java.util.List<Storage.Scanner>> scanners) {
    var method = componentRegistry.implementation(callInfo.serviceInfo()).method;
    if (method == null) return;
    for (var parameter : method.getParameters()) {
      var binding = inputBinding(parameter);
      if (binding != null && scanners.containsKey(binding))
        for (var scanner : scanners.get(binding))
          adaptObservationArgument(parameter, dependencies.get(binding), scanner);
      else if (callInfo.serviceInfo().serviceInfo.listOutputs().stream()
          .anyMatch(output -> output.getName().equals(parameter.getName()))
          && Storage.Scanner.class.isAssignableFrom(parameter.getType())) {
        var nativeType = observation.getContextualizationData().getNativeShardingStrategy().getDataType();
        org.integratedmodelling.klab.api.data.StorageScan.operation(nativeType,
            org.integratedmodelling.klab.api.data.StorageScan.type(
                parameter.getType().asSubclass(Storage.Scanner.class), nativeType),
            org.integratedmodelling.klab.api.data.StorageScan.Precision.ALLOW_FLOAT_NARROWING);
      }
    }
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
    geometry = TemporalGeometry.localize(geometry, event);

    if (componentRegistry.implementation(callInfo.serviceInfo()).method != null) {

      var implementation = componentRegistry.implementation(callInfo.serviceInfo());

      Data.Builder builder = null;
      boolean needsBuilder =
          Arrays.stream(implementation.method.getParameterTypes())
              .anyMatch(Data.Builder.class::isAssignableFrom);

      if (needsBuilder) {
        builder = new org.integratedmodelling.klab.data.DirectDataBuilder(
            observation.getName(), null, observation, scope, null);
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

          if (Boolean.FALSE.equals(context)) return false;

        } catch (Exception e) {
          cause = e instanceof java.lang.reflect.InvocationTargetException invocation
              && invocation.getCause() != null ? invocation.getCause() : e;
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
          if (Boolean.FALSE.equals(context)) return false;
        } catch (Exception e) {
          cause = e instanceof java.lang.reflect.InvocationTargetException invocation
              && invocation.getCause() != null ? invocation.getCause() : e;
          return false;
        }
      }
      if (builder != null) {
        observation.getNotifications().addAll(builder.getNotifications());
        if (org.integratedmodelling.klab.api.utils.Utils.Notifications.hasErrors(builder.getNotifications()))
          return false;
        var outcomes = builder.getObjects().stream().map(Data.Builder::getObservation).toList();
        contextualizationScope.getOutcomes().addAll(outcomes);
        if (observation instanceof org.integratedmodelling.klab.api.knowledge.observation.impl.ObservationImpl impl)
          impl.setChildrenCount(outcomes.size());
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
