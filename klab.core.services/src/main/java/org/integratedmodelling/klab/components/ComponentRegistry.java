package org.integratedmodelling.klab.components;

import io.github.classgraph.ClassGraph;
import io.github.classgraph.ClassInfo;
import io.github.classgraph.ScanResult;
import javassist.Modifier;
import org.apache.commons.collections4.MultiValuedMap;
import org.apache.commons.collections4.multimap.HashSetValuedHashMap;
import org.integratedmodelling.common.lang.ServiceInfoImpl;
import org.integratedmodelling.common.logging.Logging;
import org.integratedmodelling.klab.api.collections.Pair;
import org.integratedmodelling.klab.api.data.Version;
import org.integratedmodelling.klab.api.exceptions.KlabIllegalArgumentException;
import org.integratedmodelling.klab.api.exceptions.KlabIllegalStateException;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.knowledge.Artifact;
import org.integratedmodelling.klab.api.knowledge.KlabAsset;
import org.integratedmodelling.klab.api.lang.ServiceCall;
import org.integratedmodelling.klab.api.lang.ServiceInfo;
import org.integratedmodelling.klab.api.scope.Scope;
import org.integratedmodelling.klab.api.services.KlabService;
import org.integratedmodelling.klab.api.services.resources.ResourceSet;
import org.integratedmodelling.klab.api.services.resources.adapters.ResourceAdapter;
import org.integratedmodelling.klab.api.services.runtime.extension.KlabAnnotation;
import org.integratedmodelling.klab.api.services.runtime.extension.KlabFunction;
import org.integratedmodelling.klab.api.services.runtime.extension.Library;
import org.integratedmodelling.klab.api.services.runtime.extension.Verb;
import org.integratedmodelling.klab.configuration.ServiceConfiguration;
import org.integratedmodelling.klab.extension.KlabComponent;
import org.pf4j.*;
import org.pf4j.update.UpdateManager;

import java.io.File;
import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;
import java.util.function.BiConsumer;

public class ComponentRegistry {

    private static final String LOCAL_SERVICE_COMPONENT = "internal.local.service.component";
    private PluginManager componentManager;
    private UpdateManager componentUpdateManager;

    /**
     * Component descriptors, uniquely identified by id + version
     */
    private MultiValuedMap<String, ComponentDescriptor> components = new HashSetValuedHashMap<>();
    /**
     * Here the key is each service URN, linked to all the components that provide it. Not including
     * adapters.
     */
    private MultiValuedMap<String, ComponentDescriptor> serviceFinder = new HashSetValuedHashMap<>();

    private Map<Class<?>, Object> globalInstances = new HashMap<>();


    //    private Map<String, LibraryDescriptor> libraries = new LinkedHashMap<>();
    //    private Map<String, AdapterDescriptor> adapters = new LinkedHashMap<>();


    record AdapterDescriptor(String name /* TODO */) {
    }

    record LibraryDescriptor(String name, String description,
                             List<Pair<ServiceInfo, FunctionDescriptor>> services,
                             List<Pair<ServiceInfo, FunctionDescriptor>> annotations,
                             List<Pair<ServiceInfo, FunctionDescriptor>> verbs) {
    }

    record ComponentDescriptor(String id, Version version, List<LibraryDescriptor> libraries,
                               List<AdapterDescriptor> adapters) {

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            ComponentDescriptor that = (ComponentDescriptor) o;
            return Objects.equals(id, that.id) && Objects.equals(version, that.version);
        }

        @Override
        public int hashCode() {
            return Objects.hash(id, version);
        }
    }

    /**
     * This descriptor contains everything needed to execute a service, including the
     * service info.
     */
    public static class FunctionDescriptor {
        public ServiceInfo serviceInfo;
        public Class<?> implementation;
        public Object mainClassInstance;
        public Object wrappingClassInstance;
        public Method method;
        public Constructor<?> constructor;
        // check call style: 1 = call, scope, prototype; 2 = call, scope; 3 = custom, matched at
        // each call
        public int methodCall;
        public  boolean staticMethod;
        public boolean staticClass;
        public boolean error;
    }

    /**
     * Return the function descriptor that corresponds to the passed call, considering any
     * version requirements and arguments. If no version requirements are present, return the
     * highest version among the compatible ones.
     * @param call
     * @return
     */
    public FunctionDescriptor getFunctionDescriptor(ServiceCall call) {
        // TODO dio crosta
        return null;
    }

    /**
     * Discover and register all the extensions provided by this component but do not start it.
     *
     * @param component
     */
    public void registerComponent(KlabComponent component) {

        var componentName = component.getName();
        var componentVersion = component.getVersion();
        var libraries = new ArrayList<LibraryDescriptor>();
        var adapters = new ArrayList<AdapterDescriptor>();

        scanPackage(component, Map.of(Library.class, (annotation, cls) -> registerLibrary(
                        (Library) annotation, cls, libraries), ResourceAdapter.class,
                (annotation, cls) -> registerAdapter((ResourceAdapter) annotation, cls,
                        adapters)));

        var componentDescriptor = new ComponentDescriptor(componentName, componentVersion, libraries,
                adapters);
        this.components.put(componentName, componentDescriptor);
        // TODO fill in the finder catalogue
    }

    private void registerLibrary(Library annotation, Class<?> cls,
                                 List<LibraryDescriptor> libraries) {

        String namespacePrefix = Library.CORE_LIBRARY.equals(annotation.name()) ? "" :
                                 (annotation.name() + ".");

        var prototypes = new ArrayList<Pair<ServiceInfo, FunctionDescriptor>>();
        var annotations = new ArrayList<Pair<ServiceInfo, FunctionDescriptor>>();
        var verbs = new ArrayList<Pair<ServiceInfo, FunctionDescriptor>>();

        for (Class<?> clss : cls.getClasses()) {
            if (clss.isAnnotationPresent(KlabFunction.class)) {
                var serviceInfo = createContextualizerPrototype(namespacePrefix,
                        clss.getAnnotation(KlabFunction.class));
                prototypes.add(Pair.of(serviceInfo, createFunctionDescriptor(serviceInfo, clss, null)));
            } else if (clss.isAnnotationPresent(Verb.class)) {
                var serviceInfo = createVerbPrototype(namespacePrefix, clss.getAnnotation(Verb.class));
                verbs.add(Pair.of(serviceInfo, createFunctionDescriptor(serviceInfo, clss, null)));
            } else if (clss.isAnnotationPresent(KlabAnnotation.class)) {
                var serviceInfo = createAnnotationPrototype(namespacePrefix,
                        clss.getAnnotation(KlabAnnotation.class));
                annotations.add(Pair.of(serviceInfo, createFunctionDescriptor(serviceInfo, clss, null)));
            }
        }

        // annotated methods
        for (Method method : cls.getDeclaredMethods()) {
            if (Modifier.isPublic(method.getModifiers()) && method.isAnnotationPresent(KlabFunction.class)) {
                var serviceInfo = createContextualizerPrototype(namespacePrefix,
                        method.getAnnotation(KlabFunction.class));
                prototypes.add(Pair.of(serviceInfo, createFunctionDescriptor(serviceInfo, cls, method)));
            } else if (method.isAnnotationPresent(KlabAnnotation.class)) {
                var serviceInfo = createAnnotationPrototype(namespacePrefix,
                        cls.getAnnotation(KlabAnnotation.class));
                annotations.add(Pair.of(serviceInfo, createFunctionDescriptor(serviceInfo, cls, method)));
            } else if (method.isAnnotationPresent(Verb.class)) {
                var serviceInfo = createVerbPrototype(namespacePrefix, cls.getAnnotation(Verb.class));
                verbs.add(Pair.of(serviceInfo, createFunctionDescriptor(serviceInfo, cls, method)));
            }
        }

        libraries.add(new LibraryDescriptor(annotation.name(), annotation.description(), prototypes,
                annotations, verbs));
    }

    private FunctionDescriptor createFunctionDescriptor(ServiceInfo serviceInfo, Class<?> clss,
                                                        Method method) {

        FunctionDescriptor ret = new FunctionDescriptor();

        ret.serviceInfo = serviceInfo;
        ret.implementation = clss;

        if (method != null) {
            ret.method = method;
            ret.methodCall = 1;
            if (java.lang.reflect.Modifier.isStatic(ret.method.getModifiers()) || serviceInfo.isReentrant()) {
                // use a global class instance
                ret.mainClassInstance = createGlobalClassInstance(ret);
                ret.staticMethod = java.lang.reflect.Modifier.isStatic(ret.method.getModifiers());
            } else if (!serviceInfo.isReentrant()) {
                // create the instance just for this prototype
                try {
                    if (ServiceConfiguration.INSTANCE.getMainService() != null) {

                        var mainService = ServiceConfiguration.INSTANCE.getMainService();
                    /*
                    try first with the actual service class
                     */
                        try {
                            ret.mainClassInstance =
                                    ret.implementation.getDeclaredConstructor(ServiceConfiguration.INSTANCE.getMainService().getClass()).newInstance(mainService);
                        } catch (Throwable t) {
                        }
                        if (ret.mainClassInstance == null) {
                            try {
                                ret.mainClassInstance =
                                        ret.implementation.getDeclaredConstructor(KlabService.class).newInstance(mainService);
                            } catch (Throwable t) {
                            }
                        }
                    }
                    if (ret.mainClassInstance == null) {
                        ret.mainClassInstance = ret.implementation.getDeclaredConstructor().newInstance();
                    }
                } catch (Exception e) {
                    Logging.INSTANCE.error("Cannot instantiate main class for function library " + ret.implementation.getCanonicalName() + ": " + e.getMessage());
                    ret.error = true;
                }
            }
        } else {

            // analyze constructor
            if (serviceInfo.isReentrant()) {
                // create the instance just for this prototype
                try {
                    ret.mainClassInstance = createGlobalClassInstance(ret);
                } catch (Exception e) {
                    ret.error = true;
                }
            } else {
                try {
                    ret.constructor =
                            ret.implementation.getDeclaredConstructor(getParameterClasses(serviceInfo, ret));
                    ret.methodCall = 1;
                } catch (NoSuchMethodException | SecurityException e) {
                }
                if (ret.constructor == null) {
                    try {
                        ret.constructor =
                                ret.implementation.getDeclaredConstructor(getParameterClasses(serviceInfo,
                                        ret));
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
     * Return the default parameterization for functions and constructors according to function type and
     * allowed "style".
     *
     * @param serviceInfo
     * @param functionDescriptor
     * @return
     */
    private Class<?>[] getParameterClasses(ServiceInfo serviceInfo, FunctionDescriptor functionDescriptor) {
        switch (serviceInfo.getFunctionType()) {
            case ANNOTATION:
                break;
            case FUNCTION:
                if (functionDescriptor.constructor != null) {
                    // TODO check: using the last constructor with parameters, or the empty constructor if
                    //  found.
                    Class<?> cls = functionDescriptor.implementation;
                    if (cls == null) {
                        throw new KlabIllegalStateException("no declared executor class for service " + serviceInfo.getName() + ": constructor can't be extracted");
                    }
                    Class[] ret = null;
                    for (Constructor<?> constructor : cls.getConstructors()) {
                        if (ret == null || ret.length == 0) {
                            ret = constructor.getParameterTypes();
                        }
                    }
                    if (ret == null) {
                        throw new KlabIllegalStateException("no usable constructor for service " + serviceInfo.getName() + " served by class " + cls.getCanonicalName());
                    }
                    return ret;

                } else {
                    //                    if (callMethod == 1) {
                    //                        return new Class[]{ServiceCall.class, Scope.class,
                    //                        ServiceInfo.class};
                    //                    } else if (callMethod == 2) {
                    //                        return new Class[]{ServiceCall.class, Scope.class};
                    //                    }
                }

                break;
            case VERB:
                break;
        }
        throw new KlabIllegalArgumentException("can't assess parameter types for " + serviceInfo.getName());
    }

    private Object createGlobalClassInstance(FunctionDescriptor ret) {
        try {
            Object instance = this.globalInstances.get(ret.implementation);
            if (instance == null) {
                // look for a constructor we know what to do with. If we are a service, we can first try
                // with a constructor that takes it.
                if (ServiceConfiguration.INSTANCE.getMainService() != null) {

                    var mainService = ServiceConfiguration.INSTANCE.getMainService();
                    /*
                    try first with the actual service class
                     */
                    try {
                        instance =
                                ret.implementation.getDeclaredConstructor(ServiceConfiguration.INSTANCE.getMainService().getClass()).newInstance(mainService);
                    } catch (Throwable t) {
                    }
                    if (instance == null) {
                        try {
                            instance =
                                    ret.implementation.getDeclaredConstructor(KlabService.class).newInstance(mainService);
                        } catch (Throwable t) {
                        }
                    }
                }
                if (instance == null) {
                    instance = ret.implementation.getDeclaredConstructor().newInstance();
                }
                this.globalInstances.put(ret.implementation, instance);
            }
            return instance;
        } catch (InstantiationException | IllegalAccessException | IllegalArgumentException |
                 InvocationTargetException | NoSuchMethodException | SecurityException e) {
            ret.error = true;
            Logging.INSTANCE.error("Cannot instantiate main class for function library " + ret.implementation.getCanonicalName() + ": " + e.getMessage());
        }
        return null;
    }

    private void registerAdapter(ResourceAdapter annotation, Class<?> cls,
                                 List<AdapterDescriptor> adapters) {
        System.out.println("ZIO PORCO UN ADAPTER " + annotation.name());
    }

    /**
     * Start the passed component so that its services can be used.
     *
     * @param component
     */
    public void loadComponent(KlabComponent component) {
    }

    /**
     * Load any component in the passed resource set that is not already present.
     *
     * @param resourceSet
     * @return
     */
    public boolean loadComponents(ResourceSet resourceSet, Scope scope) {
        return false;
    }

    public void scanPackage(String[] internalPackages, Map<Class<? extends Annotation>, BiConsumer<Annotation
            , Class<?>>> annotationHandlers) {

        try (ScanResult scanResult =
                     new ClassGraph().enableAnnotationInfo().acceptPackages(internalPackages).scan()) {
            for (Class<? extends Annotation> ah : annotationHandlers.keySet()) {
                for (ClassInfo routeClassInfo : scanResult.getClassesWithAnnotation(ah)) {
                    try {
                        Class<?> cls = Class.forName(routeClassInfo.getName());
                        Annotation annotation = cls.getAnnotation(ah);
                        if (annotation != null) {
                            annotationHandlers.get(ah).accept(annotation, cls);
                        }
                    } catch (ClassNotFoundException e) {
                        Logging.INSTANCE.error(e);
                    }
                }
            }
        }
    }

    public void scanPackage(KlabComponent component, Map<Class<? extends Annotation>, BiConsumer<Annotation
            , Class<?>>> annotationHandlers) {

        try (ScanResult scanResult =
                     new ClassGraph().enableAnnotationInfo().addClassLoader(component.getWrapper().getPluginClassLoader()).acceptPackages(component.getClass().getPackageName()).scan()) {
            for (Class<? extends Annotation> ah : annotationHandlers.keySet()) {
                for (ClassInfo routeClassInfo : scanResult.getClassesWithAnnotation(ah)) {
                    try {
                        Class<?> cls = Class.forName(routeClassInfo.getName(), false,
                                component.getWrapper().getPluginClassLoader());
                        Annotation annotation = cls.getAnnotation(ah);
                        if (annotation != null) {
                            annotationHandlers.get(ah).accept(annotation, cls);
                        }
                    } catch (ClassNotFoundException e) {
                        Logging.INSTANCE.error(e);
                    }
                }
            }
        }
    }

    private ServiceInfo createVerbPrototype(String namespacePrefix, Verb annotation) {
        // TODO
        return null;
    }

    private ServiceInfo createContextualizerPrototype(String namespacePrefix, KlabFunction annotation) {

        var ret = new ServiceInfoImpl();

        ret.setName(namespacePrefix + annotation.name());
        ret.setDescription(annotation.description());
        ret.setFilter(annotation.filter());
        //        ret.setImplementation(clss);
        //        ret.setExecutorMethod(method == null ? null : method.getName());
        ret.setGeometry(annotation.geometry().isEmpty() ? null : Geometry.create(annotation.geometry()));
        ret.setLabel(annotation.dataflowLabel());
        ret.setReentrant(annotation.reentrant());
        ret.setFunctionType(ServiceInfo.FunctionType.FUNCTION);

        for (Artifact.Type a : annotation.type()) {
            ret.getType().add(a);
        }

        for (KlabFunction.Argument argument : annotation.parameters()) {
            var arg = createArgument(argument);
            ret.getArguments().put(arg.getName(), arg);
        }
        for (KlabFunction.Argument argument : annotation.exports()) {
            var arg = createArgument(argument);
            ret.getImports().add(arg);
        }
        for (KlabFunction.Argument argument : annotation.imports()) {
            var arg = createArgument(argument);
            ret.getExports().add(arg);
        }


        return ret;
    }

    private ServiceInfo createAnnotationPrototype(String namespacePrefix, KlabAnnotation annotation) {

        var ret = new ServiceInfoImpl();

        ret.setName(namespacePrefix + annotation.name());
        ret.setDescription(annotation.description());
        //        ret.setImplementation(clss);
        //        ret.setExecutorMethod(method == null ? null : method.getName());
        ret.setFunctionType(ServiceInfo.FunctionType.ANNOTATION);
        for (KlabAsset.KnowledgeClass kcl : annotation.targets()) {
            ret.getTargets().add(kcl);
        }

        for (KlabFunction.Argument argument : annotation.parameters()) {
            var arg = createArgument(argument);
            ret.getArguments().put(arg.getName(), arg);
        }

        return ret;
    }

    private ServiceInfoImpl.ArgumentImpl createArgument(KlabFunction.Argument argument) {
        var arg = new ServiceInfoImpl.ArgumentImpl();
        arg.setName(argument.name());
        arg.setDescription(argument.description());
        arg.setOptional(argument.optional());
        arg.setConst(argument.constant());
        arg.setArtifact(argument.artifact());
        for (Artifact.Type a : argument.type()) {
            arg.getType().add(a);
        }
        return arg;
    }

    public void loadExtensions(String... packageName) {

        var libraries = new ArrayList<LibraryDescriptor>();
        var adapters = new ArrayList<AdapterDescriptor>();

        scanPackage(packageName, Map.of(Library.class, (annotation, cls) -> registerLibrary(
                        (Library) annotation, cls, libraries), ResourceAdapter.class,
                (annotation, cls) -> registerAdapter((ResourceAdapter) annotation, cls,
                        adapters)));

        var componentDescriptor = new ComponentDescriptor(LOCAL_SERVICE_COMPONENT, Version.CURRENT_VERSION,
                libraries,
                adapters);
        this.components.put(LOCAL_SERVICE_COMPONENT, componentDescriptor);


    }

    /**
     * Call to initialize and use the plugin system. No plugins will be discovered unless this is called. This
     * finds but does not load the configured plugins.
     *
     * @param pluginRoot
     */
    public void initializeComponents(File pluginRoot) {
        this.componentManager = new DefaultPluginManager(pluginRoot.toPath());
        this.componentManager.loadPlugins();
        // TODO configuration
        for (var wrapper : this.componentManager.getPlugins()) {
            Plugin plugin = wrapper.getPlugin();
            if (plugin instanceof KlabComponent component) {
                registerComponent(component);
            }
        }

        this.componentManager.addPluginStateListener(new PluginStateListener() {
            @Override
            public void pluginStateChanged(PluginStateEvent event) {
                System.out.println("HOLA! Plugin state: " + event);
            }
        });
    }

    //    /**
    //     * Check for updates and load all registered components; return the set of packages to scan from
    //     them.
    //     * <p>
    //     * TODO!
    //     *
    //     * @return
    //     */
    //    public Set<String> updateAndLoadComponents(String serviceName) {
    //
    //        Set<String> ret = new LinkedHashSet<>();
    //        if (componentUpdateManager == null) {
    //            componentUpdateManager = new UpdateManager(componentManager, new ArrayList<>());
    //            componentUpdateManager.addRepository(new LocalComponentRepository(serviceName));
    //        }
    //
    //        if (componentUpdateManager.hasUpdates()) {
    //            List<PluginInfo> updates = componentUpdateManager.getUpdates();
    //            Logging.INSTANCE.debug("Found {} updates", updates.size());
    //            for (PluginInfo plugin : updates) {
    //                Logging.INSTANCE.debug("Found update for plugin '{}'", plugin.id);
    //                PluginInfo.PluginRelease lastRelease = componentUpdateManager.getLastPluginRelease
    //                (plugin.id);
    //                String lastVersion = lastRelease.version;
    //                String installedVersion = componentManager.getPlugin(plugin.id).getDescriptor()
    //                .getVersion();
    //                Logging.INSTANCE.debug("Update plugin '{}' from version {} to version {}", plugin.id,
    //                        installedVersion,
    //                        lastVersion);
    //                boolean updated = componentUpdateManager.updatePlugin(plugin.id, lastVersion);
    //                if (updated) {
    //                    Logging.INSTANCE.debug("Updated plugin '{}'", plugin.id);
    //                } else {
    //                    Logging.INSTANCE.error("Cannot update plugin '{}'", plugin.id);
    //                }
    //            }
    //        } else {
    //            Logging.INSTANCE.debug("No updates found");
    //        }
    //
    //        // check for available (new) plugins
    //        if (componentUpdateManager.hasAvailablePlugins()) {
    //            List<PluginInfo> availablePlugins = componentUpdateManager.getAvailablePlugins();
    //            Logging.INSTANCE.debug("Found {} available plugins", availablePlugins.size());
    //            for (PluginInfo plugin : availablePlugins) {
    //                Logging.INSTANCE.debug("Found available plugin '{}'", plugin.id);
    //                PluginInfo.PluginRelease lastRelease = componentUpdateManager.getLastPluginRelease
    //                (plugin.id);
    //                String lastVersion = lastRelease.version;
    //                Logging.INSTANCE.debug("Install plugin '{}' with version {}", plugin.id, lastVersion);
    //                boolean installed = componentUpdateManager.installPlugin(plugin.id, lastVersion);
    //                if (installed) {
    //                    Logging.INSTANCE.debug("Installed plugin '{}'", plugin.id);
    //                } else {
    //                    Logging.INSTANCE.error("Cannot install plugin '{}'", plugin.id);
    //                }
    //            }
    //        } else {
    //            Logging.INSTANCE.debug("No available plugins found");
    //        }
    //
    //        // Disable temporarily - creates issues in modeler
    //        //
    //        componentManager.loadPlugins();
    //
    //        return ret;
    //
    //    }


}
