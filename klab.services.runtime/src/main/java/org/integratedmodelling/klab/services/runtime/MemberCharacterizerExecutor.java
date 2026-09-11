package org.integratedmodelling.klab.services.runtime;

import java.lang.reflect.*;
import java.util.*;
import org.integratedmodelling.klab.api.digitaltwin.Scheduler;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.lang.ServiceCall;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.scope.Scope;
import org.integratedmodelling.klab.api.services.runtime.Actuator;
import org.integratedmodelling.klab.components.ComponentRegistry;

/** Local characterization effects return success, never a new observation or attribution. */
final class MemberCharacterizerExecutor {
  record Invocation(Method method, Object receiver, ServiceCall call) {}
  private final Actuator actuator;
  private final List<Invocation> invocations;

  static MemberCharacterizerExecutor compile(Actuator actuator, ComponentRegistry registry) {
    if (actuator.getOperationObservable() == null
        || actuator.getOperationObservable().getContextualization()
            != org.integratedmodelling.klab.api.knowledge.Contextualization.CHARACTERIZATION
        || actuator.getTargetBindings().size() != 1
        || actuator.getTargetBindings().getFirst().getKind() != Actuator.TargetBinding.Kind.OBSERVATION
        || actuator.getTargetBindings().getFirst().getTarget() == null)
      throw new IllegalArgumentException("Characterization requires an individual member binding");
    var invocations = new ArrayList<Invocation>();
    for (var call : actuator.getComputation()) {
      var candidates = new ArrayList<Invocation>();
      for (var descriptor : registry.getFunctionDescriptor(call)) {
        var implementation = registry.implementation(descriptor);
        if (implementation != null && supports(implementation.method))
          candidates.add(new Invocation(implementation.method, implementation.mainClassInstance, call));
      }
      if (candidates.size() != 1)
        throw new IllegalArgumentException("Expected one typed characterizer for " + call.getUrn());
      invocations.add(candidates.getFirst());
    }
    return new MemberCharacterizerExecutor(actuator, invocations);
  }

  MemberCharacterizerExecutor(Actuator actuator, List<Invocation> invocations) {
    this.actuator = actuator;
    this.invocations = List.copyOf(invocations);
  }

  static boolean supports(Method method) {
    if (method == null || !Modifier.isPublic(method.getModifiers())
        || !(method.getReturnType() == void.class || method.getReturnType() == boolean.class)) return false;
    var seen = new HashSet<Class<?>>();
    int scopes = 0;
    for (var type : method.getParameterTypes()) {
      if (!seen.add(type)) return false;
      if (type == Scope.class || type == ContextScope.class) scopes++;
      else if (type != Observable.class && type != Observation.class && type != ServiceCall.class
          && type != Geometry.class && type != Scheduler.Event.class) return false;
    }
    return scopes == 1 && seen.contains(Observable.class);
  }

  void execute(Observation member, Geometry geometry, Scheduler.Event event, ContextScope scope) throws Exception {
    for (var invocation : invocations) {
      var types = invocation.method().getParameterTypes();
      var arguments = new Object[types.length];
      for (int i = 0; i < types.length; i++) {
        var type = types[i];
        arguments[i] = type == Observable.class ? actuator.getOperationObservable()
            : type == Observation.class ? member : type == Geometry.class ? geometry
            : type == Scheduler.Event.class ? event : type == ServiceCall.class ? invocation.call() : scope;
      }
      var result = invocation.method().invoke(invocation.receiver(), arguments);
      if (Boolean.FALSE.equals(result)) throw new IllegalStateException("Characterizer returned false");
    }
  }
}
