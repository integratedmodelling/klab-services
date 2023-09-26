package org.integratedmodelling.klab.runtime.language;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.integratedmodelling.klab.api.exceptions.KIllegalArgumentException;
import org.integratedmodelling.klab.api.knowledge.Expression;
import org.integratedmodelling.klab.api.knowledge.Expression.CompilerOption;
import org.integratedmodelling.klab.api.knowledge.Expression.Descriptor;
import org.integratedmodelling.klab.api.lang.Annotation;
import org.integratedmodelling.klab.api.lang.Prototype;
import org.integratedmodelling.klab.api.lang.ServiceCall;
import org.integratedmodelling.klab.api.lang.kactors.KActorsStatement.Call;
import org.integratedmodelling.klab.api.scope.Scope;
import org.integratedmodelling.klab.api.services.Language;
import org.integratedmodelling.klab.api.services.ResourcesService;
import org.integratedmodelling.klab.api.services.resources.ResourceSet;
import org.integratedmodelling.klab.api.services.runtime.Notification;

public class LanguageService implements Language {

    class FunctionDescriptor {
        Prototype prototype;
        Class<?> implementation;
        Object mainClassInstance;
        Object wrappingClassInstance;
        Method method;
        Constructor<?> constructor;
        // check call style: 1 = call, scope, prototype; 2 = call, scope; 3 = custom, matched at
        // each call
        int methodCall;
        boolean staticMethod;
        boolean staticClass;
        public boolean error;

    }

    private Map<String, FunctionDescriptor> functions = new HashMap<>();
    private Map<String, FunctionDescriptor> annotations = new HashMap<>();
    private Map<String, FunctionDescriptor> verbs = new HashMap<>();
    private Map<Class<?>, Object> globalInstances = new HashMap<>();

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
        FunctionDescriptor descriptor = this.functions.get(call.getName());
        if (descriptor == null) {
            /*
            check the resource service in the scope to see if we can find a component that supports this call
             */
            ResourceSet resourceSet = scope.getService(ResourcesService.class).resolveServiceCall(call.getName(), scope);
            if (!resourceSet.isEmpty()) {
                loadComponent(resourceSet, scope);
                descriptor = this.functions.get(call.getName());
            }
        }
        if (descriptor != null && !descriptor.error) {
            if (descriptor.method != null) {
                // can't be null
                try {
                    return (T) descriptor.method.invoke(descriptor.staticMethod ? null : descriptor.mainClassInstance,
                            getParameters(descriptor, call, scope, false));
                } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
                    scope.error("runtime error when invoking function " + call.getName());
                    return null;
                }
            }
        } else if (descriptor.constructor != null) {
            // TODO
            System.out.println("ZORBA IL GRECO");
        }
        return null;
    }

    @Override
    public void loadComponent(ResourceSet resourceSet, Scope scope) {
        // TODO implement
    }

    private Object[] getParameters(FunctionDescriptor descriptor, ServiceCall call, Scope scope, boolean isConstructor) {
        switch(descriptor.prototype.getFunctionType()) {
        case ANNOTATION:
            break;
        case FUNCTION:
            if (isConstructor) {
                // TODO
            } else {
                if (descriptor.methodCall == 1) {
                    return new Object[]{call, scope, descriptor.prototype};
                } else if (descriptor.methodCall == 2) {
                    return new Object[]{call, scope};
                } else {
                    // TODO match parameters with the method's class signature
                }
            }
            break;
        case VERB:
            break;
        }
        return null;
    }

    public void declare(Prototype prototype) {
        FunctionDescriptor descriptor = createFunctionDescriptor(prototype);
        switch(prototype.getFunctionType()) {
        case ANNOTATION:
            this.annotations.put(prototype.getName(), descriptor);
            break;
        case FUNCTION:
            this.functions.put(prototype.getName(), descriptor);
            break;
        case VERB:
            this.verbs.put(prototype.getName(), descriptor);
            break;
        }
    }

    private FunctionDescriptor createFunctionDescriptor(Prototype prototype) {

        FunctionDescriptor ret = new FunctionDescriptor();

        ret.prototype = prototype;
        ret.implementation = prototype.getExecutorClass();

        if (prototype.getExecutorMethod() != null) {
            try {
                ret.method = ret.implementation.getDeclaredMethod(prototype.getExecutorMethod(),
                        getParameterClasses(prototype, 1, false));
                ret.methodCall = 1;
            } catch (NoSuchMethodException | SecurityException e) {
            }
            if (ret.method == null) {
                try {
                    ret.method = ret.implementation.getDeclaredMethod(prototype.getExecutorMethod(),
                            getParameterClasses(prototype, 2, false));
                    ret.methodCall = 2;
                } catch (NoSuchMethodException | SecurityException e) {
                }
            }
            if (ret.method == null) {
                for (Method m : ret.implementation.getMethods()) {
                    if (prototype.getExecutorMethod().equals(m.getName())) {
                        ret.method = m;
                    }
                }
                ret.methodCall = 3;
            }

            if (ret.method != null) {
                if (Modifier.isStatic(ret.method.getModifiers()) || prototype.isReentrant()) {
                    // use a global class instance
                    ret.mainClassInstance = createGlobalClassInstance(ret);
                    ret.staticMethod = Modifier.isStatic(ret.method.getModifiers());
                } else if (!prototype.isReentrant()) {
                    // create the instance just for this prototype
                    try {
                        ret.mainClassInstance = ret.implementation.getDeclaredConstructor().newInstance();
                    } catch (Exception e) {
                        ret.error = true;
                    }
                }
            } else {
                ret.error = true;
            }
        } else {
            // analyze constructor
            if (prototype.isReentrant()) {
                // create the instance just for this prototype
                try {
                    ret.mainClassInstance = createGlobalClassInstance(ret);
                } catch (Exception e) {
                    ret.error = true;
                }
            } else {
                try {
                    ret.constructor = ret.implementation.getDeclaredConstructor(getParameterClasses(prototype, 1, true));
                    ret.methodCall = 1;
                } catch (NoSuchMethodException | SecurityException e) {
                }
                if (ret.constructor == null) {
                    try {
                        ret.constructor = ret.implementation.getDeclaredConstructor(getParameterClasses(prototype, 2, true));
                        ret.methodCall = 2;
                    } catch (NoSuchMethodException | SecurityException e) {
                    }
                }
                if (ret.constructor == null) {
                    ret.methodCall = 3;
                }
            }

        }

        return ret;
    }

    /**
     * Return the default parameterization for functions and constructors according to function type
     * and allowed "style".
     * 
     * @param prototype
     * @param callMethod
     * @param isConstructor
     * @return
     */
    private Class<?>[] getParameterClasses(Prototype prototype, int callMethod, boolean isConstructor) {
        switch(prototype.getFunctionType()) {
        case ANNOTATION:
            break;
        case FUNCTION:
            if (isConstructor) {
                // TODO
            } else {
                if (callMethod == 1) {
                    return new Class[]{ServiceCall.class, Scope.class, Prototype.class};
                } else if (callMethod == 2) {
                    return new Class[]{ServiceCall.class, Scope.class};
                }
            }

            break;
        case VERB:
            break;
        }
        throw new KIllegalArgumentException("can't assess parameter types for " + prototype.getName());
    }

    private Object createGlobalClassInstance(FunctionDescriptor ret) {
        try {
            Object instance = this.globalInstances.get(ret.implementation);
            if (instance == null) {
                instance = ret.implementation.getDeclaredConstructor().newInstance();
                this.globalInstances.put(ret.implementation, instance);
            }
            return instance;
        } catch (InstantiationException | IllegalAccessException | IllegalArgumentException | InvocationTargetException
                | NoSuchMethodException | SecurityException e) {
            ret.error = true;
        }
        return null;
    }

}
