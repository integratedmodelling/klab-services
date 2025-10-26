package org.integratedmodelling.klab.runtime.language;

import org.integratedmodelling.common.knowledge.GeometryRepository;
import org.integratedmodelling.common.utils.Utils;
import org.integratedmodelling.klab.api.collections.Parameters;
import org.integratedmodelling.klab.api.data.Data;
import org.integratedmodelling.klab.api.data.Storage;
import org.integratedmodelling.klab.api.data.mediation.classification.LookupTable;
import org.integratedmodelling.klab.api.digitaltwin.DigitalTwin;
import org.integratedmodelling.klab.api.digitaltwin.Scheduler;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.knowledge.Expression;
import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.api.knowledge.Resource;
import org.integratedmodelling.klab.api.knowledge.Urn;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.knowledge.observation.scale.Scale;
import org.integratedmodelling.klab.api.knowledge.observation.scale.space.Space;
import org.integratedmodelling.klab.api.knowledge.observation.scale.time.Time;
import org.integratedmodelling.klab.api.lang.ServiceCall;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.scope.Scope;
import org.integratedmodelling.klab.services.scopes.ServiceUserScope;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/** k.LAB-aware argument matching functions. */
public class ArgumentMatcher {

  /**
   * Freeform matching between unnamed parameters and the method's arguments. Any Parameters matches
   * the call's own. The passed args may not be PODs and this method should only be used within the
   * same VM.
   *
   * @param parameterTypes
   * @param call
   * @param scope
   * @return
   */
  public static Object[] matchParametersFreeform(
      Class<?>[] parameterTypes, ServiceCall call, Scope scope, Object... furtherArgs) {
    List<Object> payload = new ArrayList<>(call.getParameters().getUnnamedArguments());
    payload.add(call);
    payload.add(scope);
    if (scope instanceof ServiceUserScope serviceUserScope) {
      // add the service and the user identity
      payload.add(serviceUserScope.getService());
      payload.add(serviceUserScope.getUser());
    }
    if (!call.getParameters().isEmpty()) {
      payload.add(call.getParameters());
    }
    var args = Utils.Collections.matchArguments(parameterTypes, payload.toArray());
    if (args == null && !payload.isEmpty()) {
      return null;
    }
    return args;
  }

  /**
   * Painful argument matcher for method using or inferring all possible arguments. Scanners come
   * through the data builder.
   *
   * <p>TODO this should be a back-end for matchParametersFreeform.
   *
   * @param method
   * @param resource
   * @param geometry
   * @param builder
   * @param observation
   * @param observable
   * @param urn
   * @param urnParameters
   * @param serviceCall // * @param storage
   * @param expression
   * @param lookupTable
   * @param schedulerEvent
   * @param scope
   * @return
   */
  public static List<Object> matchArguments(
      Method method,
      Resource resource,
      Geometry geometry,
      Data.Builder builder,
      Observation observation,
      Observable observable,
      Urn urn,
      Parameters<String> urnParameters,
      ServiceCall serviceCall,
      Storage.Scanner scanner,
      Expression expression,
      LookupTable lookupTable,
      Data inputData,
      Scheduler.Event schedulerEvent,
      Scope scope) {
    List<Object> runArguments = new ArrayList<>();
    DigitalTwin digitalTwin = null;
    if (scope instanceof ContextScope contextScope) {
      digitalTwin = contextScope.getDigitalTwin();
    }
    Scale scale = geometry instanceof Scale scale1 ? scale1 : null;

    // TODO HERE match inputs to scanners through the data builder

    if (method != null) {
      for (var argument : method.getParameterTypes()) {
        if (ContextScope.class.isAssignableFrom(argument)) {
          // TODO consider wrapping into read-only delegating wrappers
          runArguments.add(scope);
        } else if (Scope.class.isAssignableFrom(argument)) {
          runArguments.add(scope);
        } else if (Observation.class.isAssignableFrom(argument)) {
          runArguments.add(observation);
        } else if (Data.Builder.class.isAssignableFrom(argument)) {
          runArguments.add(builder);
        } else if (Data.class.isAssignableFrom(argument)) {
          runArguments.add(inputData);
        } else if (ServiceCall.class.isAssignableFrom(argument)) {
          runArguments.add(serviceCall);
        } else if (Parameters.class.isAssignableFrom(argument)) {
          runArguments.add(urnParameters);
        } else if (Storage.Shard.class.isAssignableFrom(argument)) {
          runArguments.add(scanner == null ? null : scanner.shard());
        } else if (Storage.Scanner.class.isAssignableFrom(argument)) {
          runArguments.add(scanner);
        } else if (Scale.class.isAssignableFrom(argument)) {
          if (scale == null && geometry != null) {
            scale = GeometryRepository.INSTANCE.scale(geometry);
          }
          runArguments.add(scale);
        } else if (Geometry.class.isAssignableFrom(argument)) {
          runArguments.add(geometry);
        } else if (Observable.class.isAssignableFrom(argument)) {
          runArguments.add(observable);
        } else if (Space.class.isAssignableFrom(argument)) {
          if (scale == null && geometry != null) {
            scale = GeometryRepository.INSTANCE.scale(geometry);
          }
          runArguments.add(scale == null ? null : scale.getSpace());
        } else if (Time.class.isAssignableFrom(argument)) {
          if (schedulerEvent != null) {
            runArguments.add(schedulerEvent.getTime());
          } else if (scale == null && geometry != null) {
            scale = GeometryRepository.INSTANCE.scale(geometry);
          }
          runArguments.add(scale == null ? null : scale.getTime());
        } else if (Scheduler.Event.class.isAssignableFrom(argument)) {
          runArguments.add(schedulerEvent);
        } else if (Resource.class.isAssignableFrom(argument) && resource != null) {
          runArguments.add(resource);
        } else if (Expression.class.isAssignableFrom(argument) && expression != null) {
          runArguments.add(expression);
        } else if (Urn.class.isAssignableFrom(argument) && urn != null) {
          runArguments.add(urn);
        } else if (LookupTable.class.isAssignableFrom(argument) && lookupTable != null) {
          runArguments.add(lookupTable);
        } else {
          scope.error(
              "Cannot map argument of type "
                  + argument.getCanonicalName()
                  + " to known objects in call to "
                  + method);
          runArguments.add(null);
        }
      }
      return runArguments;
    }

    return null;
  }
}
