package org.integratedmodelling.klab.runtime.language;

import org.integratedmodelling.klab.api.collections.Parameters;
import org.integratedmodelling.klab.api.exceptions.KlabIllegalStateException;
import org.integratedmodelling.klab.api.knowledge.Expression;
import org.integratedmodelling.klab.api.knowledge.Expression.CompilerOption;
import org.integratedmodelling.klab.api.knowledge.Expression.Descriptor;
import org.integratedmodelling.klab.api.knowledge.observation.DirectObservation;
import org.integratedmodelling.klab.api.lang.Annotation;
import org.integratedmodelling.klab.api.lang.ServiceCall;
import org.integratedmodelling.klab.api.lang.kactors.KActorsStatement.Call;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.scope.Scope;
import org.integratedmodelling.klab.api.services.Language;
import org.integratedmodelling.klab.api.services.ResourcesService;
import org.integratedmodelling.klab.api.services.resources.ResourceSet;
import org.integratedmodelling.klab.api.services.runtime.Notification;
import org.integratedmodelling.klab.components.ComponentRegistry;

import java.lang.reflect.InvocationTargetException;
import java.util.List;

public class LanguageService implements Language {

    private ComponentRegistry componentRegistry;

    //    class FunctionDescriptor {
    //        ServiceInfo serviceInfo;
    //        Class<?> implementation;
    //        Object mainClassInstance;
    //        Object wrappingClassInstance;
    //        Method method;
    //        Constructor<?> constructor;
    //        // check call style: 1 = call, scope, prototype; 2 = call, scope; 3 = custom, matched at
    //        // each call
    //        int methodCall;
    //        boolean staticMethod;
    //        boolean staticClass;
    //        public boolean error;
    //
    //    }
    //
    //    private Map<String, FunctionDescriptor> functions = new HashMap<>();
    //    private Map<String, FunctionDescriptor> annotations = new HashMap<>();
    //    private Map<String, FunctionDescriptor> verbs = new HashMap<>();
    //    private Map<Class<?>, Object> globalInstances = new HashMap<>();

    @Override
    public Descriptor describe(String expression, String language, Scope scope, CompilerOption... options) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Expression compile(String expression, String language, CompilerOption... options) {
        // TODO Auto-generated method stub
        return null;
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
        ComponentRegistry.FunctionDescriptor descriptor = this.componentRegistry.getFunctionDescriptor(call);
        if (descriptor == null) {
            /*
            check the resource service in the scope to see if we can find a component that supports this call
             */
            ResourceSet resourceSet =
                    scope.getService(ResourcesService.class).resolveServiceCall(call.getUrn(),
                            call.getRequiredVersion(), scope);
            if (!resourceSet.isEmpty()) {
                componentRegistry.loadComponents(resourceSet, scope);
                descriptor = this.componentRegistry.getFunctionDescriptor(call);
            }
        }
        if (descriptor != null && !descriptor.error) {
            if (descriptor.method != null) {
                // can't be null
                try {
                    return (T) descriptor.method.invoke(descriptor.staticMethod ? null :
                                                        descriptor.mainClassInstance,
                            getParameters(descriptor, call, scope, false));
                } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
                    scope.error("runtime error when invoking function " + call.getUrn());
                    return null;
                }
            } else if (descriptor.constructor != null) {
                Object[] args = getParameters(descriptor, call, scope, true);
                try {
                    return (T) descriptor.constructor.newInstance(args);
                } catch (Throwable e) {
                    throw new KlabIllegalStateException(e);
                }
            }
        }
        return null;
    }

    //    @Override
    //    public void loadComponent(ResourceSet resourceSet, Scope scope) {
    //        // TODO implement
    //    }

    private Object[] getParameters(ComponentRegistry.FunctionDescriptor descriptor, ServiceCall call,
                                   Scope scope,
                                   boolean isConstructor) {
        switch (descriptor.serviceInfo.getFunctionType()) {
            case ANNOTATION:
                break;
            case FUNCTION:
                if (isConstructor) {
                    return matchParameters(descriptor.constructor.getParameterTypes(), call, scope);
                } else {
                    if (descriptor.methodCall == 1) {
                        return new Object[]{call, scope, descriptor.serviceInfo};
                    } else if (descriptor.methodCall == 2) {
                        return new Object[]{call, scope};
                    } else {
                        return matchParameters(descriptor.method.getParameterTypes(), call, scope);
                    }
                }
            case VERB:
                break;
        }
        return null;
    }

    private Object[] matchParameters(Class<?>[] parameterTypes, ServiceCall call, Scope scope) {

        Object[] ret = new Object[parameterTypes.length];

        /*
        first check if we have passed unnamed parameters in the right order.
         */
        if (call.getParameters().getUnnamedArguments().size() == parameterTypes.length) {
            boolean ok = true;
            for (int i = 0; i < parameterTypes.length; i++) {
                if (!(call.getParameters().getUnnamedArguments().get(i) == null || parameterTypes[i].isAssignableFrom(call.getParameters().getUnnamedArguments().get(0).getClass()))) {
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
            if (Scope.class.isAssignableFrom(cls)) {
                ret[i] = scope;
            } else if (ServiceCall.class.isAssignableFrom(cls)) {
                ret[i] = call;
            } /*else if (Geometry.class.isAssignableFrom(cls)) {
                ret[i] = scope instanceof SessionScope ? ((SessionScope) scope).getScale() : null;
            } else if (Scale.class.isAssignableFrom(cls)) {
                ret[i] = scope instanceof SessionScope ? ((SessionScope) scope).getScale() : null;
            }*/ else if (Parameters.class.isAssignableFrom(cls)) {
                ret[i] = call.getParameters();
            } else if (DirectObservation.class.isAssignableFrom(cls)) {
                ret[i] = scope instanceof ContextScope ? ((ContextScope) scope).getContextObservation() :
                         null;
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
    //                ret.method = ret.implementation.getDeclaredMethod(serviceInfo.getExecutorMethod(),
    //                        getParameterClasses(serviceInfo, 1, false));
    //                ret.methodCall = 1;
    //            } catch (NoSuchMethodException | SecurityException e) {
    //            }
    //            if (ret.method == null) {
    //                try {
    //                    ret.method = ret.implementation.getDeclaredMethod(serviceInfo.getExecutorMethod(),
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
    //                                        (ServiceConfiguration.INSTANCE.getMainService().getClass())
    //                                        .newInstance(mainService);
    //                            } catch (Throwable t) {
    //                            }
    //                            if (ret.mainClassInstance == null) {
    //                                try {
    //                                    ret.mainClassInstance =
    //                                            ret.implementation.getDeclaredConstructor(KlabService
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
    //                        Logging.INSTANCE.error("Cannot instantiate main class for function library "
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
    //     * Return the default parameterization for functions and constructors according to function type and
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
    //                        throw new KlabIllegalStateException("no declared executor class for service "
    //                        + serviceInfo.getName() + ": constructor can't be extracted");
    //                    }
    //                    Class[] ret = null;
    //                    for (Constructor<?> constructor : cls.getConstructors()) {
    //                        if (ret == null || ret.length == 0) {
    //                            ret = constructor.getParameterTypes();
    //                        }
    //                    }
    //                    if (ret == null) {
    //                        throw new KlabIllegalStateException("no usable constructor for service " +
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
    //                // look for a constructor we know what to do with. If we are a service, we can first try
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
