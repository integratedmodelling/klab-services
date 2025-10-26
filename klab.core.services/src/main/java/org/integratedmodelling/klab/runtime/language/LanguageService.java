package org.integratedmodelling.klab.runtime.language;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.integratedmodelling.common.utils.Utils;
import org.integratedmodelling.klab.api.collections.Parameters;
import org.integratedmodelling.klab.api.exceptions.KlabIllegalStateException;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.lang.Annotation;
import org.integratedmodelling.klab.api.lang.ServiceCall;
import org.integratedmodelling.klab.api.lang.kactors.KActorsStatement.Call;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.scope.Scope;
import org.integratedmodelling.klab.api.scope.SessionScope;
import org.integratedmodelling.klab.api.services.Language;
import org.integratedmodelling.klab.api.services.ResourcesService;
import org.integratedmodelling.klab.api.services.resources.ResourceSet;
import org.integratedmodelling.klab.api.services.runtime.Notification;
import org.integratedmodelling.klab.api.services.runtime.extension.Extensions;
import org.integratedmodelling.klab.components.ComponentRegistry;
import org.integratedmodelling.klab.runtime.computation.GroovyProcessor;
import org.integratedmodelling.klab.services.scopes.ServiceUserScope;

public class LanguageService implements Language {

  private ComponentRegistry componentRegistry;
  private Map<String, LanguageProcessor> languageProcessors = new HashMap<>();

  public LanguageService() {
    this.languageProcessors.put(DEFAULT_EXPRESSION_LANGUAGE, new GroovyProcessor());
  }

  @Override
  public LanguageProcessor getLanguageProcessor(String language) {
    return languageProcessors.get(language);
  }

  @Override
  public List<Notification> validate(ServiceCall call) {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public List<Notification> validate(Annotation annotation) {
    // TODO Auto-generated method stub
    return null;
  }

  @Override
  public List<Notification> validate(Call message) {
    // TODO Auto-generated method stub
    return null;
  }

  @SuppressWarnings("unchecked")
  @Override
  public <T> T execute(ServiceCall call, Scope scope, Class<T> resultClass, Object... furtherArgs) {
    var descriptors = this.componentRegistry.getFunctionDescriptor(call);
    if (descriptors == null) {
      /*
      check the resource service in the scope to see if we can find a component that supports this call
       */
      ResourceSet resourceSet =
          scope
              .getService(ResourcesService.class)
              .resolveServiceCall(call.getUrn(), call.getRequiredVersion(), scope);
      if (!resourceSet.isEmpty()) {
        componentRegistry.loadComponents(resourceSet, scope);
        descriptors = this.componentRegistry.getFunctionDescriptor(call);
      }
    }
    if (descriptors != null) {
      for (var descriptor : descriptors) {
        if (!descriptor.error) {
          if (componentRegistry.implementation(descriptor).method != null) {
            // adapt the parameters to the function call
            var parameters = getParameters(descriptor, call, scope, false, furtherArgs);
            if (parameters == null) {
              continue;
            }
            try {
              return (T)
                  componentRegistry
                      .implementation(descriptor)
                      .method
                      .invoke(
                          descriptor.staticMethod
                              ? null
                              : componentRegistry.implementation(descriptor).mainClassInstance,
                          parameters);
            } catch (IllegalAccessException
                | IllegalArgumentException
                | InvocationTargetException e) {
              scope.error("runtime error when invoking function " + call.getUrn(), e);
              return null;
            }
          } else if (componentRegistry.implementation(descriptor).constructor != null) {
            Object[] args = getParameters(descriptor, call, scope, true, furtherArgs);
            if (args == null) {
              continue;
            }
            try {
              return (T) componentRegistry.implementation(descriptor).constructor.newInstance(args);
            } catch (Throwable e) {
              throw new KlabIllegalStateException(e);
            }
          }
        }
      }
    }

    scope.error("no suitable function implementation found for " + call.getUrn());

    return null;
  }

  /**
   * Match all parameters to a method and return the arguments, or null if the method cannot be
   * matched.
   *
   * <p>FIXME check if we can remove this and use ArgumentMatcher instead.
   *
   * <p>TODO manage scanners intelligently: if there is a scanner in the args, use that; otherwise
   * if there is a quality observation, create a scanner that merges whatever splits are in the
   * actual storage. There must be an event as we don't support multi-time slice geometries at this
   * point.
   *
   * @param descriptor
   * @param call
   * @param scope
   * @param isConstructor
   * @param furtherArgs
   * @return
   */
  public Object[] getParameters(
      Extensions.FunctionDescriptor descriptor,
      ServiceCall call,
      Scope scope,
      boolean isConstructor,
      Object... furtherArgs) {
    switch (descriptor.serviceInfo.getFunctionType()) {
      case ANNOTATION:
        // TODO
        break;
      case FUNCTION:
        if (isConstructor) {
          return matchParameters(
              componentRegistry.implementation(descriptor).constructor.getParameterTypes(),
              call,
              scope,
              furtherArgs);
        } else {
          if (descriptor.methodCall == 1) {
            return new Object[] {call, scope, descriptor.serviceInfo};
          } else if (descriptor.methodCall == 2) {
            return new Object[] {call, scope};
          } else {
            return matchParameters(
                componentRegistry.implementation(descriptor).method.getParameterTypes(),
                call,
                scope,
                furtherArgs);
          }
        }
      case FREEFORM:
        return ArgumentMatcher.matchParametersFreeform(
            componentRegistry.implementation(descriptor).method.getParameterTypes(),
            call,
            scope,
            furtherArgs);
      case VERB:
        // TODO
    }
    return null;
  }

  /**
   * FIXME check ArgumentMatcher and either remove this completely or revise the former to
   * accommodate.
   *
   * @param parameterTypes
   * @param call
   * @param scope
   * @param furtherArgs
   * @return
   */
  private Object[] matchParameters(
      Class<?>[] parameterTypes, ServiceCall call, Scope scope, Object... furtherArgs) {

    Object[] ret = new Object[parameterTypes.length];

    /*
    first check if we have passed unnamed parameters in the right order.
     */
    if (call.getParameters().getUnnamedArguments().size() == parameterTypes.length) {
      boolean ok = true;
      for (int i = 0; i < parameterTypes.length; i++) {
        if (!(call.getParameters().getUnnamedArguments().get(i) == null
            || parameterTypes[i].isAssignableFrom(
                call.getParameters().getUnnamedArguments().getFirst().getClass()))) {
          ok = false;
          break;
        }
      }

      if (ok) {
        return call.getParameters().getUnnamedArguments().toArray();
      }
    }

    int i = 0;
    for (Class<?> cls : parameterTypes) {
      if (ContextScope.class.isAssignableFrom(cls) && scope instanceof ContextScope) {
        ret[i] = scope;
      } else if (SessionScope.class.isAssignableFrom(cls) && scope instanceof SessionScope) {
        ret[i] = scope;
      } else if (Scope.class.isAssignableFrom(cls)) {
        ret[i] = scope;
      } else if (ServiceCall.class.isAssignableFrom(cls)) {
        ret[i] = call;
      } /*else if (Geometry.class.isAssignableFrom(cls)) {
            ret[i] = scope instanceof SessionScope ? ((SessionScope) scope).getScale() : null;
        } else if (Scale.class.isAssignableFrom(cls)) {
            ret[i] = scope instanceof SessionScope ? ((SessionScope) scope).getScale() : null;
        }*/ else if (Parameters.class.isAssignableFrom(cls)) {
        ret[i] = call.getParameters();
      } else if (Observation.class.isAssignableFrom(cls)) {
        ret[i] =
            scope instanceof ContextScope ? ((ContextScope) scope).getContextObservation() : null;
      } /* TODO more type inference: definitely Model */ else {
        ret[i] = null;
      }
      i++;
    }
    return ret;
  }

  public void setComponentRegistry(ComponentRegistry componentRegistry) {
    this.componentRegistry = componentRegistry;
  }

  @Override
  public String serviceName() {
    return "k.LAB Language Service";
  }
}
