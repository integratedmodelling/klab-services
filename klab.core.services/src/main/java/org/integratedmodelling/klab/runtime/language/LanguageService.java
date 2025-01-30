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
  public <T> T execute(ServiceCall call, Scope scope, Class<T> resultClass) {
    Extensions.FunctionDescriptor descriptor = this.componentRegistry.getFunctionDescriptor(call);
    if (descriptor == null) {
      /*
      check the resource service in the scope to see if we can find a component that supports this call
       */
      ResourceSet resourceSet =
          scope
              .getService(ResourcesService.class)
              .resolveServiceCall(call.getUrn(), call.getRequiredVersion(), scope);
      if (!resourceSet.isEmpty()) {
        componentRegistry.loadComponents(resourceSet, scope);
        descriptor = this.componentRegistry.getFunctionDescriptor(call);
      }
    }
    if (descriptor != null && !descriptor.error) {
      if (componentRegistry.implementation(descriptor).method != null) {
        // can't be null
        try {
          return (T)
              componentRegistry
                  .implementation(descriptor)
                  .method
                  .invoke(
                      descriptor.staticMethod
                          ? null
                          : componentRegistry.implementation(descriptor).mainClassInstance,
                      getParameters(descriptor, call, scope, false));
        } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
          scope.error("runtime error when invoking function " + call.getUrn());
          return null;
        }
      } else if (componentRegistry.implementation(descriptor).constructor != null) {
        Object[] args = getParameters(descriptor, call, scope, true);
        try {
          return (T) componentRegistry.implementation(descriptor).constructor.newInstance(args);
        } catch (Throwable e) {
          throw new KlabIllegalStateException(e);
        }
      }
    }
    return null;
  }

  private Object[] getParameters(
      Extensions.FunctionDescriptor descriptor,
      ServiceCall call,
      Scope scope,
      boolean isConstructor) {
    switch (descriptor.serviceInfo.getFunctionType()) {
      case ANNOTATION:
        // TODO
        break;
      case FUNCTION:
        if (isConstructor) {
          return matchParameters(
              componentRegistry.implementation(descriptor).constructor.getParameterTypes(),
              call,
              scope);
        } else {
          if (descriptor.methodCall == 1) {
            return new Object[] {call, scope, descriptor.serviceInfo};
          } else if (descriptor.methodCall == 2) {
            return new Object[] {call, scope};
          } else {
            return matchParameters(
                componentRegistry.implementation(descriptor).method.getParameterTypes(),
                call,
                scope);
          }
        }
      case FREEFORM:
        return matchParametersFreeform(
            componentRegistry.implementation(descriptor).method.getParameterTypes(), call, scope);
      case VERB:
        // TODO
    }
    return null;
  }

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
  private Object[] matchParametersFreeform(
      Class<?>[] parameterTypes, ServiceCall call, Scope scope) {
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

  private Object[] matchParameters(Class<?>[] parameterTypes, ServiceCall call, Scope scope) {

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

  //    public void declare(ServiceInfo serviceInfo) {
  //        FunctionDescriptor descriptor = createFunctionDescriptor(serviceInfo);
  //        switch (serviceInfo.getFunctionType()) {
  //            case ANNOTATION:
  //                this.annotations.put(serviceInfo.getName(), descriptor);
  //                break;
  //            case FUNCTION:
  //                this.functions.put(serviceInfo.getName(), descriptor);
  //                break;
  //            case VERB:
  //                this.verbs.put(serviceInfo.getName(), descriptor);
  //                break;
  //        }
  //    }
  //
  //    private FunctionDescriptor createFunctionDescriptor(ServiceInfo serviceInfo) {
  //
  //        FunctionDescriptor ret = new FunctionDescriptor();
  //
  //        ret.serviceInfo = serviceInfo;
  //        ret.implementation = serviceInfo.executorClass();
  //
  //        if (serviceInfo.getExecutorMethod() != null) {
  //            try {
  //                ret.method =
  // ret.implementation.getDeclaredMethod(serviceInfo.getExecutorMethod(),
  //                        getParameterClasses(serviceInfo, 1, false));
  //                ret.methodCall = 1;
  //            } catch (NoSuchMethodException | SecurityException e) {
  //            }
  //            if (ret.method == null) {
  //                try {
  //                    ret.method =
  // ret.implementation.getDeclaredMethod(serviceInfo.getExecutorMethod(),
  //                            getParameterClasses(serviceInfo, 2, false));
  //                    ret.methodCall = 2;
  //                } catch (NoSuchMethodException | SecurityException e) {
  //                }
  //            }
  //            if (ret.method == null) {
  //                for (Method m : ret.implementation.getMethods()) {
  //                    if (serviceInfo.getExecutorMethod().equals(m.getName())) {
  //                        ret.method = m;
  //                    }
  //                }
  //                ret.methodCall = 3;
  //            }
  //
  //            if (ret.method != null) {
  //                if (Modifier.isStatic(ret.method.getModifiers()) || serviceInfo.isReentrant()) {
  //                    // use a global class instance
  //                    ret.mainClassInstance = createGlobalClassInstance(ret);
  //                    ret.staticMethod = Modifier.isStatic(ret.method.getModifiers());
  //                } else if (!serviceInfo.isReentrant()) {
  //                    // create the instance just for this prototype
  //                    try {
  //                        if (ServiceConfiguration.INSTANCE.getMainService() != null) {
  //
  //                            var mainService = ServiceConfiguration.INSTANCE.getMainService();
  //                    /*
  //                    try first with the actual service class
  //                     */
  //                            try {
  //                                ret.mainClassInstance =
  //                                        ret.implementation.getDeclaredConstructor
  //
  // (ServiceConfiguration.INSTANCE.getMainService().getClass())
  //                                        .newInstance(mainService);
  //                            } catch (Throwable t) {
  //                            }
  //                            if (ret.mainClassInstance == null) {
  //                                try {
  //                                    ret.mainClassInstance =
  //
  // ret.implementation.getDeclaredConstructor(KlabService
  //                                            .class).newInstance(mainService);
  //                                } catch (Throwable t) {
  //                                }
  //                            }
  //                        }
  //                        if (ret.mainClassInstance == null) {
  //                            ret.mainClassInstance = ret.implementation.getDeclaredConstructor()
  //                            .newInstance();
  //                        }
  //                    } catch (Exception e) {
  //                        Logging.INSTANCE.error("Cannot instantiate main class for function
  // library "
  //                        + ret.implementation.getCanonicalName() + ": " + e.getMessage());
  //                        ret.error = true;
  //                    }
  //                }
  //            } else {
  //                ret.error = true;
  //            }
  //        } else {
  //            // analyze constructor
  //            if (serviceInfo.isReentrant()) {
  //                // create the instance just for this prototype
  //                try {
  //                    ret.mainClassInstance = createGlobalClassInstance(ret);
  //                } catch (Exception e) {
  //                    ret.error = true;
  //                }
  //            } else {
  //                try {
  //                    ret.constructor =
  //                            ret.implementation.getDeclaredConstructor(getParameterClasses
  //                            (serviceInfo, 1,
  //                                    true));
  //                    ret.methodCall = 1;
  //                } catch (NoSuchMethodException | SecurityException e) {
  //                }
  //                if (ret.constructor == null) {
  //                    try {
  //                        ret.constructor =
  //                                ret.implementation.getDeclaredConstructor(getParameterClasses
  //                                (serviceInfo, 2,
  //                                        true));
  //                        ret.methodCall = 2;
  //                    } catch (NoSuchMethodException | SecurityException e) {
  //                    }
  //                }
  //                if (ret.constructor == null) {
  //                    ret.methodCall = 3;
  //                }
  //            }
  //
  //        }
  //
  //        return ret;
  //    }

  //    /**
  //     * Return the default parameterization for functions and constructors according to function
  // type and
  //     * allowed "style".
  //     *
  //     * @param serviceInfo
  //     * @param callMethod
  //     * @param isConstructor
  //     * @return
  //     */
  //    private Class<?>[] getParameterClasses(ServiceInfo serviceInfo, int callMethod, boolean
  //    isConstructor) {
  //        switch (serviceInfo.getFunctionType()) {
  //            case ANNOTATION:
  //                break;
  //            case FUNCTION:
  //                if (isConstructor) {
  //                    // TODO check: using the last constructor with parameters, or the empty
  //                     constructor if
  //                    //  found.
  //                    Class<?> cls = serviceInfo.executorClass();
  //                    if (cls == null) {
  //                        throw new KlabIllegalStateException("no declared executor class for
  // service "
  //                        + serviceInfo.getName() + ": constructor can't be extracted");
  //                    }
  //                    Class[] ret = null;
  //                    for (Constructor<?> constructor : cls.getConstructors()) {
  //                        if (ret == null || ret.length == 0) {
  //                            ret = constructor.getParameterTypes();
  //                        }
  //                    }
  //                    if (ret == null) {
  //                        throw new KlabIllegalStateException("no usable constructor for service "
  // +
  //                        serviceInfo.getName() + " served by class " + cls.getCanonicalName());
  //                    }
  //                    return ret;
  //
  //                } else {
  //                    if (callMethod == 1) {
  //                        return new Class[]{ServiceCall.class, Scope.class, ServiceInfo.class};
  //                    } else if (callMethod == 2) {
  //                        return new Class[]{ServiceCall.class, Scope.class};
  //                    }
  //                }
  //
  //                break;
  //            case VERB:
  //                break;
  //        }
  //        throw new KlabIllegalArgumentException("can't assess parameter types for " + serviceInfo
  //        .getName());
  //    }

  //    private Object createGlobalClassInstance(FunctionDescriptor ret) {
  //        try {
  //            Object instance = this.globalInstances.get(ret.implementation);
  //            if (instance == null) {
  //                // look for a constructor we know what to do with. If we are a service, we can
  // first try
  //                // with a constructor that takes it.
  //                if (ServiceConfiguration.INSTANCE.getMainService() != null) {
  //
  //                    var mainService = ServiceConfiguration.INSTANCE.getMainService();
  //                    /*
  //                    try first with the actual service class
  //                     */
  //                    try {
  //                        instance =
  //                                ret.implementation.getDeclaredConstructor(ServiceConfiguration
  //                                .INSTANCE.getMainService().getClass()).newInstance(mainService);
  //                    } catch (Throwable t) {
  //                    }
  //                    if (instance == null) {
  //                        try {
  //                            instance =
  //                                    ret.implementation.getDeclaredConstructor(KlabService.class)
  //                                    .newInstance(mainService);
  //                        } catch (Throwable t) {
  //                        }
  //                    }
  //                }
  //                if (instance == null) {
  //                    instance = ret.implementation.getDeclaredConstructor().newInstance();
  //                }
  //                this.globalInstances.put(ret.implementation, instance);
  //            }
  //            return instance;
  //        } catch (InstantiationException | IllegalAccessException | IllegalArgumentException |
  //                 InvocationTargetException | NoSuchMethodException | SecurityException e) {
  //            ret.error = true;
  //            Logging.INSTANCE.error("Cannot instantiate main class for function library " + ret
  //            .implementation.getCanonicalName() + ": " + e.getMessage());
  //        }
  //        return null;
  //    }

}
