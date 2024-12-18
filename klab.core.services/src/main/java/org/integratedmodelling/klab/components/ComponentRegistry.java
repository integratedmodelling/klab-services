package org.integratedmodelling.klab.components;

import io.github.classgraph.ClassGraph;
import io.github.classgraph.ClassInfo;
import io.github.classgraph.ScanResult;
import javassist.Modifier;
import org.apache.commons.collections4.MultiValuedMap;
import org.apache.commons.collections4.multimap.HashSetValuedHashMap;
import org.apache.tika.config.Param;
import org.integratedmodelling.common.lang.ServiceInfoImpl;
import org.integratedmodelling.common.logging.Logging;
import org.integratedmodelling.klab.api.authentication.ResourcePrivileges;
import org.integratedmodelling.klab.api.collections.Pair;
import org.integratedmodelling.klab.api.collections.Parameters;
import org.integratedmodelling.klab.api.data.Version;
import org.integratedmodelling.klab.api.exceptions.KlabIllegalArgumentException;
import org.integratedmodelling.klab.api.exceptions.KlabIllegalStateException;
import org.integratedmodelling.klab.api.exceptions.KlabUnimplementedException;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.knowledge.Artifact;
import org.integratedmodelling.klab.api.knowledge.KlabAsset;
import org.integratedmodelling.klab.api.lang.ServiceCall;
import org.integratedmodelling.klab.api.lang.ServiceInfo;
import org.integratedmodelling.klab.api.scope.Scope;
import org.integratedmodelling.klab.api.services.KlabService;
import org.integratedmodelling.klab.api.services.ResourcesService;
import org.integratedmodelling.klab.api.services.resources.ResourceSet;
import org.integratedmodelling.klab.api.services.resources.ResourceTransport;
import org.integratedmodelling.klab.api.services.resources.adapters.Adapter;
import org.integratedmodelling.klab.api.services.resources.adapters.Exporter;
import org.integratedmodelling.klab.api.services.resources.adapters.Importer;
import org.integratedmodelling.klab.api.services.resources.adapters.ResourceAdapter;
import org.integratedmodelling.klab.api.services.runtime.Notification;
import org.integratedmodelling.klab.api.services.runtime.extension.KlabAnnotation;
import org.integratedmodelling.klab.api.services.runtime.extension.KlabFunction;
import org.integratedmodelling.klab.api.services.runtime.extension.Library;
import org.integratedmodelling.klab.api.services.runtime.extension.Verb;
import org.integratedmodelling.klab.configuration.ServiceConfiguration;
import org.integratedmodelling.klab.extension.KlabComponent;
import org.integratedmodelling.klab.services.configuration.ResourcesConfiguration;
import org.integratedmodelling.klab.utilities.Utils;
import org.pf4j.*;

import java.io.File;
import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.function.BiConsumer;

public class ComponentRegistry {

    public static final String LOCAL_SERVICE_COMPONENT = "internal.local.service.component";
    private PluginManager componentManager;
    private File pluginPath = null;

    // we keep the local services and adapters in here
    // FIXME the permissions should come from the external permission system, not as the internal
    //  Plugin-License
    private final ComponentDescriptor localComponentDescriptor =
            new ComponentDescriptor(LOCAL_SERVICE_COMPONENT, Version.CURRENT_VERSION, "Natively available " +
                    "services", null, ResourcePrivileges.PUBLIC, new ArrayList<>(), new ArrayList<>(),
                    new HashMap<>(), new HashMap<>(), new HashMap<>());

    /**
     * Component descriptors, uniquely identified by id + version
     */
    private MultiValuedMap<String, ComponentDescriptor> components = new HashSetValuedHashMap<>();

    /**
     * Here the key is each service URN, linked to all the components that provide it.
     */
    private MultiValuedMap<String, Adapter> adapters = new HashSetValuedHashMap<>();
    private MultiValuedMap<String, ComponentDescriptor> adapterFinder = new HashSetValuedHashMap<>();
    private MultiValuedMap<String, ComponentDescriptor> serviceFinder = new HashSetValuedHashMap<>();
    private MultiValuedMap<String, ComponentDescriptor> annotationFinder = new HashSetValuedHashMap<>();
    private MultiValuedMap<String, ComponentDescriptor> verbFinder = new HashSetValuedHashMap<>();
    private Map<Class<?>, Object> globalInstances = new HashMap<>();
    //    private Map<String, FunctionDescriptor> importHandlers = new HashMap<>();
    //    private Map<String, FunctionDescriptor> exportHandlers = new HashMap<>();

    public List<ComponentDescriptor> resolveServiceCall(String name, Version version) {
        List<ComponentDescriptor> ret = new ArrayList<>();
        ComponentDescriptor target = null;
        for (var component : serviceFinder.get(name)) {
            if (version == null) {
                if (target == null || component.version.greater(target.version)) {
                    target = component;
                }
            } else if (version.compatible(component.version)) {
                target = component;
            }
        }
        if (target != null) {
            /*
            TODO add all dependencies first
             */
            ret.add(target);
        }
        return ret;
    }

    /**
     * The adapter identifier may include a version after @; if not, retrieve the latest version available.
     * Even if present, it must be authorized to the passed scope.
     *
     * @param adapterType
     * @param scope
     * @return
     */
    public Adapter getAdapter(String adapterType, Scope scope) {
        return null;
    }

    public record AdapterDescriptor(String name /* TODO */) {
    }

    public record LibraryDescriptor(String name, String description,
                                    List<Pair<ServiceInfo, FunctionDescriptor>> services,
                                    List<Pair<ServiceInfo, FunctionDescriptor>> annotations,
                                    List<Pair<ServiceInfo, FunctionDescriptor>> verbs) {
    }

    public record ComponentDescriptor(String id, Version version, String description, File sourceArchive,
                                      ResourcePrivileges permissions, List<LibraryDescriptor> libraries,
                                      List<AdapterDescriptor> adapters,
                                      Map<String, FunctionDescriptor> services,
                                      Map<String, FunctionDescriptor> annotations,
                                      Map<String, FunctionDescriptor> verbs) {

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

        public Notification extractInfo() {
            return Notification.info("Component " + id() + " [" + version + "]: " + services().size() +
                    "services, " + adapters.size() + " adapters, " + annotations.size() + " annotations");
        }
    }

    /**
     * This descriptor contains everything needed to execute a service, including the service info.
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
        public boolean staticMethod;
        public boolean staticClass;
        public boolean error;
    }


    public Pair<ComponentDescriptor, ResourceSet.Resource> installComponent(String mavenGroupId,
                                                                            String mavenArtifactId,
                                                                            Version version, Scope scope) {


        return null;
    }

    public Pair<ComponentDescriptor, ResourceSet> installComponent(File resourcePath, Scope scope) {

        // TODO allow same path with different versions and replacing same version

        var ret = new ResourceSet();
        ComponentDescriptor info = null;
        try {
            var pluginId = componentManager.loadPlugin(resourcePath.toPath());
            var plugin = componentManager.getPlugin(pluginId);
            ResourceSet.Resource result = new ResourceSet.Resource("SERVICE ID TODO", pluginId, null,
                    Version.create(plugin.getDescriptor().getVersion()), KlabAsset.KnowledgeClass.COMPONENT);

            // TODO dependencies

            Plugin component = plugin.getPlugin();
            if (component instanceof KlabComponent comp) {
                info = registerComponent(comp);
                ret.getNotifications().add(info.extractInfo());
                ret.getResults().add(result);

                if (pluginPath != null) {
                    componentManager.unloadPlugin(pluginId);
                    var pluginDestination =
                            new File(pluginPath + File.separator + Utils.Files.getFileName(resourcePath));
                    Files.copy(resourcePath.toPath(), pluginDestination.toPath(),
                            StandardCopyOption.REPLACE_EXISTING);
                    // descriptor is already OK, just reload in the manager
                    componentManager.loadPlugin(pluginDestination.toPath());
                }

            } else {
                ret.getNotifications().add(Notification.error("Plugin " + Utils.Files.getFileName(resourcePath) + " is " + "not a valid k.LAB component"));
                ret.setEmpty(true);
            }
        } catch (Throwable t) {
            ret.getNotifications().add(Notification.create(t));
            ret.setEmpty(true);
        }
        return Pair.of(info, ret);
    }


    /**
     * Return the function descriptor that corresponds to the passed call, considering any version
     * requirements and arguments. If no version requirements are present, return the highest version among
     * the compatible ones.
     * <p>
     * The service call can also be used to locate export/import schemata by passing the schema ID as a
     * service name and the properties as parameters, with "FILE" or "URL" as argument for bytestream-based
     * schemata.
     *
     * @param call
     * @return
     */
    public FunctionDescriptor getFunctionDescriptor(ServiceCall call) {
        var version = call.getRequiredVersion();
        ComponentDescriptor target = null;
        for (var component : serviceFinder.get(call.getUrn())) {
            if (version == null) {
                if (target == null || component.version.greater(target.version)) {
                    target = component;
                }
            } else if (version.compatible(component.version)) {
                target = component;
            }
        }
        return target == null ? null : target.services.get(call.getUrn());
    }

    /**
     * Discover and register all the extensions provided by this component but do not start it.
     * <p>
     * TODO make this return ComponentInfo
     *
     * @param component
     */
    public ComponentDescriptor registerComponent(KlabComponent component) {

        var componentName = component.getName();
        var componentVersion = component.getVersion();
        var libraries = new ArrayList<LibraryDescriptor>();
        var adapters = new ArrayList<AdapterDescriptor>();
        var license = component.getWrapper().getDescriptor().getLicense();
        var description = component.getWrapper().getDescriptor().getPluginDescription();
        var sourceArchive = component.getWrapper().getPluginPath() == null ? null :
                            component.getWrapper().getPluginPath().toFile();
        var permissions = license == null ? ResourcePrivileges.PUBLIC : ResourcePrivileges.create(license);

        scanPackage(component, Map.of(Library.class,
                (annotation, cls) -> registerLibrary((Library) annotation, cls, libraries),
                ResourceAdapter.class, (annotation, cls) -> registerAdapter((ResourceAdapter) annotation,
                        cls, adapters)));

        var componentDescriptor = new ComponentDescriptor(componentName, componentVersion, description,
                sourceArchive, permissions, libraries, adapters, new HashMap<>(), new HashMap<>(),
                new HashMap<>());

        // update catalog
        for (var library : componentDescriptor.libraries) {
            for (var service : library.services) {
                serviceFinder.put(service.getFirst().getName(), componentDescriptor);
                componentDescriptor.services().put(service.getFirst().getName(), service.getSecond());
            }
            for (var service : library.annotations) {
                annotationFinder.put(service.getFirst().getName(), componentDescriptor);
                componentDescriptor.annotations().put(service.getFirst().getName(), service.getSecond());
            }
            for (var service : library.verbs) {
                verbFinder.put(service.getFirst().getName(), componentDescriptor);
                componentDescriptor.verbs().put(service.getFirst().getName(), service.getSecond());
            }
        }

        this.components.put(componentName, componentDescriptor);

        return componentDescriptor;
    }

    private void registerLibrary(Library annotation, Class<?> cls, List<LibraryDescriptor> libraries) {

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
                        method.getAnnotation(KlabAnnotation.class));
                annotations.add(Pair.of(serviceInfo, createFunctionDescriptor(serviceInfo, cls, method)));
            } else if (method.isAnnotationPresent(Verb.class)) {
                var serviceInfo = createVerbPrototype(namespacePrefix, method.getAnnotation(Verb.class));
                verbs.add(Pair.of(serviceInfo, createFunctionDescriptor(serviceInfo, cls, method)));
            } else if (method.isAnnotationPresent(Importer.class)) {
                var serviceInfo = createAnnotationPrototype(namespacePrefix,
                        method.getAnnotation(Importer.class));
                prototypes.add(Pair.of(serviceInfo, createFunctionDescriptor(serviceInfo, cls, method)));
            } else if (method.isAnnotationPresent(Exporter.class)) {
                var serviceInfo = createAnnotationPrototype(namespacePrefix,
                        method.getAnnotation(Exporter.class));
                prototypes.add(Pair.of(serviceInfo, createFunctionDescriptor(serviceInfo, cls, method)));
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
            ret.methodCall = 3;
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
                    // move along
                }
                if (ret.constructor == null) {
                    try {
                        ret.constructor =
                                ret.implementation.getDeclaredConstructor(getParameterClasses(serviceInfo,
                                        ret));
                        ret.methodCall = 2;
                    } catch (NoSuchMethodException | SecurityException e) {
                        // move along
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

    private void registerAdapter(ResourceAdapter annotation, Class<?> cls, List<AdapterDescriptor> adapters) {

        try {
            var adapter = new AdapterImpl(cls, annotation);
        } catch (Throwable t) {
            Logging.INSTANCE.error("Adapter " + annotation.name() + " caused errors when loading and was " + "rejected", t);
        }

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
     * Retrieve a component in a given version or the latest. TODO use this in other methods that use the
     * logic.
     *
     * @param urn
     * @param version
     * @return
     */
    public ComponentDescriptor getComponent(String urn, Version version) {

        ComponentDescriptor ret = null;
        for (var component : components.get(urn)) {
            if (version == null) {
                if (ret == null || component.version.greater(ret.version)) {
                    ret = component;
                }
            } else if (version.compatible(component.version)) {
                ret = component;
            }
        }
        return ret;

    }

    /**
     * Load any component in the passed resource set that is not already present.
     *
     * @param resourceSet
     * @return
     */
    public boolean loadComponents(ResourceSet resourceSet, Scope scope) {

        Set<String> available = new HashSet<>();
        for (var result : resourceSet.getResults()) {
            if (result.getKnowledgeClass() == KlabAsset.KnowledgeClass.COMPONENT) {
                for (var existing : components.get(result.getResourceUrn())) {
                    if (result.getResourceVersion() == null || existing.version().compatible(result.getResourceVersion())) {
                        available.add(result.getResourceUrn());
                    }
                }
            }
        }

        // if we get here, we need to retrieve and load the component
        if (available.size() == resourceSet.getResults().size()) {
            return true;
        }

        for (var result : resourceSet.getResults()) {

            if (!available.contains(result.getResourceUrn())) {
                // load from service
                var service = scope.getService(result.getServiceId(), ResourcesService.class);
                if (service == null) {
                    return false;
                }

                File plugin = new File(pluginPath + File.separator + result.getResourceUrn() + ".jar");
                throw new KlabUnimplementedException("DIOCÜ reimplement the component retrieval");
                //                try (var input = service.retrieveResource(result.getResourceUrn(),
                //                        result.getResourceVersion(), result.getAccessKey(),
                //                        "application/java-archive", scope); var output = new
                //                        FileOutputStream(plugin)) {
                //                    IOUtils.copy(input, output);
                //                } catch (IOException e) {
                //                    scope.error(e);
                //                    return false;
                //                }
                //                loadComponents(pluginPath);
            }
        }

        // hopefully this is OK with plugins that have started already
        componentManager.startPlugins();

        return true;
    }

    public void scanPackage(String[] internalPackages, Map<Class<? extends Annotation>,
            BiConsumer<Annotation, Class<?>>> annotationHandlers) {

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

    private ServiceInfo createAnnotationPrototype(String namespacePrefix, Exporter annotation) {

        var ret = new ServiceInfoImpl();

        ret.setName(namespacePrefix + annotation.schema());
        ret.setDescription(annotation.description());
        ret.setFunctionType(ServiceInfo.FunctionType.FREEFORM);
        ret.getTargets().add(annotation.knowledgeClass());

        for (KlabFunction.Argument argument : annotation.properties()) {
            var arg = createArgument(argument);
            ret.getArguments().put(arg.getName(), arg);
        }

        /*
        TODO create the records in ResourceTransport!
         */

        return ret;
    }

    private ServiceInfo createAnnotationPrototype(String namespacePrefix, Importer annotation) {

        var ret = new ServiceInfoImpl();

        ret.setName(namespacePrefix + annotation.schema());
        ret.setDescription(annotation.description());
        ret.setFunctionType(ServiceInfo.FunctionType.FREEFORM);
        ret.getTargets().add(annotation.knowledgeClass());

        for (KlabFunction.Argument argument : annotation.properties()) {
            var arg = createArgument(argument);
            ret.getArguments().put(arg.getName(), arg);
        }

        /*
        TODO create the records in ResourceTransport!
         */

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

        scanPackage(packageName, Map.of(Library.class,
                (annotation, cls) -> registerLibrary((Library) annotation, cls, libraries),
                ResourceAdapter.class, (annotation, cls) -> registerAdapter((ResourceAdapter) annotation,
                        cls, adapters)));

        localComponentDescriptor.libraries.addAll(libraries);
        localComponentDescriptor.adapters.addAll(adapters);

        this.components.put(LOCAL_SERVICE_COMPONENT, localComponentDescriptor);

        // update catalog
        for (var library : localComponentDescriptor.libraries) {
            for (var service : library.services) {
                serviceFinder.put(service.getFirst().getName(), localComponentDescriptor);
                localComponentDescriptor.services().put(service.getFirst().getName(), service.getSecond());
            }
            for (var service : library.annotations) {
                annotationFinder.put(service.getFirst().getName(), localComponentDescriptor);
                localComponentDescriptor.annotations().put(service.getFirst().getName(), service.getSecond());
            }
            for (var service : library.verbs) {
                verbFinder.put(service.getFirst().getName(), localComponentDescriptor);
                localComponentDescriptor.verbs().put(service.getFirst().getName(), service.getSecond());
            }
        }

    }

    /**
     * Call this one if you plan on USING the plugin; call {@link #initializeComponents(File)} if you want to
     * build the plugin archive but not load the plugins themselves. This one is for working with the plugins,
     * the other for hosting and serving plugins. One or the other must be called before anything else.
     *
     * @param pluginRoot
     */
    public void loadComponents(File pluginRoot) {
        initializeComponents(pluginRoot);
        componentManager.startPlugins();
    }

    /**
     * Use this call for the "master" service that installs components based on configuration. The resources
     * service should use this entry point; others should use {@link #initializeComponents(File)} or
     * {@link #loadComponents(File)}.
     *
     * @param configuration
     * @param pluginPath
     */
    public void initializeComponents(ResourcesConfiguration configuration, File pluginPath) {

        /*
        TODO check all existing resources against the configuration; retrieve whatever needs updating;
         remove anything not configured or deprecated; check integrity and certification for all components
          before loading them.
         */
        if (Utils.Maven.needsUpdate("org.integratedmodelling", "klab.component.generators", "1.0-SNAPSHOT")) {
            // shitdown

        }

        initializeComponents(pluginPath);
    }

    /**
     * Call to initialize and use the plugin system. No plugins will be discovered unless this is called. This
     * finds but does not load the configured plugins. Call this one at initialization or
     * {@link #loadComponents(File)} but not both.
     *
     * @param pluginRoot
     */
    public void initializeComponents(File pluginRoot) {
        this.componentManager = new DefaultPluginManager(pluginRoot.toPath());
        this.componentManager.loadPlugins();
        this.pluginPath = pluginRoot;
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
