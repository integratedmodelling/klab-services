package org.integratedmodelling.klab.components;

import io.github.classgraph.ClassGraph;
import io.github.classgraph.ClassInfo;
import io.github.classgraph.ScanResult;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import javassist.Modifier;
import org.apache.commons.collections4.MultiValuedMap;
import org.apache.commons.collections4.multimap.HashSetValuedHashMap;
import org.h2.util.IOUtils;
import org.integratedmodelling.common.lang.ServiceInfoImpl;
import org.integratedmodelling.common.logging.Logging;
import org.integratedmodelling.klab.api.authentication.ResourcePrivileges;
import org.integratedmodelling.klab.api.collections.Pair;
import org.integratedmodelling.klab.api.collections.Parameters;
import org.integratedmodelling.klab.api.data.Data;
import org.integratedmodelling.klab.api.data.Storage;
import org.integratedmodelling.klab.api.data.Version;
import org.integratedmodelling.klab.api.data.mediation.classification.LookupTable;
import org.integratedmodelling.klab.api.digitaltwin.DigitalTwin;
import org.integratedmodelling.klab.api.engine.StartupOptions;
import org.integratedmodelling.klab.api.exceptions.*;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.knowledge.*;
import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.knowledge.observation.scale.Scale;
import org.integratedmodelling.klab.api.knowledge.observation.scale.space.Space;
import org.integratedmodelling.klab.api.knowledge.observation.scale.time.Time;
import org.integratedmodelling.klab.api.lang.ServiceCall;
import org.integratedmodelling.klab.api.lang.ServiceInfo;
import org.integratedmodelling.klab.api.scope.ContextScope;
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
import org.integratedmodelling.klab.api.services.runtime.extension.*;
import org.integratedmodelling.klab.configuration.ServiceConfiguration;
import org.integratedmodelling.klab.extension.KlabComponent;
import org.integratedmodelling.klab.runtime.storage.*;
import org.integratedmodelling.klab.services.base.BaseService;
import org.integratedmodelling.klab.services.configuration.ResourcesConfiguration;
import org.integratedmodelling.klab.utilities.Utils;
import org.pf4j.*;

public class ComponentRegistry {

  public static final String LOCAL_SERVICE_COMPONENT = "internal.local.service.component";
  private final BaseService service;
  private PluginManager componentManager;
  private File pluginPath = null;

  // we keep the local services and adapters in here
  // FIXME the permissions should come from the external permission system, not as the internal
  //  Plugin-License
  private final Extensions.ComponentDescriptor localComponentDescriptor =
      new Extensions.ComponentDescriptor(
          LOCAL_SERVICE_COMPONENT,
          Version.CURRENT_VERSION,
          "Natively available " + "services",
          null,
          null,
          null,
          new ArrayList<>(),
          new ArrayList<>(),
          new HashMap<>(),
          new HashMap<>(),
          new HashMap<>());

  /** Component descriptors, uniquely identified by id + version */
  private MultiValuedMap<String, Extensions.ComponentDescriptor> components =
      new HashSetValuedHashMap<>();

  private static Map<String, ServiceImplementation> serviceImplementations = new HashMap<>();

  /** Here the key is each service URN, linked to all the components that provide it. */
  private MultiValuedMap<String, Adapter> adapters = new HashSetValuedHashMap<>();

  private MultiValuedMap<String, Extensions.ComponentDescriptor> adapterFinder =
      new HashSetValuedHashMap<>();
  private MultiValuedMap<String, Extensions.ComponentDescriptor> serviceFinder =
      new HashSetValuedHashMap<>();
  private MultiValuedMap<String, Extensions.ComponentDescriptor> annotationFinder =
      new HashSetValuedHashMap<>();
  private MultiValuedMap<String, Extensions.ComponentDescriptor> verbFinder =
      new HashSetValuedHashMap<>();
  /*
   * Adapter descriptors include those registered from other services.
   */
  private MultiValuedMap<String, Extensions.AdapterDescriptor> adapterDescriptorFinder =
      new HashSetValuedHashMap<>();
  private Map<Class<?>, Object> globalInstances = new HashMap<>();
  private File catalogFile;
  private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

  public ComponentRegistry(BaseService service, StartupOptions options) {
    readConfiguration(service, options);
    this.service = service;
    scheduler.scheduleAtFixedRate(() -> checkForUpdates(), 0, 5, TimeUnit.MINUTES);
  }

  /**
   * Call passing the capabilities of any service whose components we want to index.
   *
   * @param capabilities
   */
  public void registerService(KlabService.ServiceCapabilities capabilities) {
    for (var component : capabilities.getComponents()) {
      for (var adapter : component.adapters()) {
        this.adapterDescriptorFinder.put(adapter.name(), adapter);
      }
    }
  }

  private synchronized void checkForUpdates() {
    for (var component : components.values()) {
      if (component.mavenCoordinates() != null) {
        System.out.println("TODO - check for updated Maven component");
      }
    }
  }

  private void readConfiguration(BaseService service, StartupOptions options) {

    this.catalogFile =
        ServiceConfiguration.INSTANCE.getFileWithTemplate(
            "services/" + service.serviceType().name().toLowerCase() + "/components/catalog.json",
            "[]");

    for (var descriptor :
        Utils.Json.load(this.catalogFile, Extensions.ComponentDescriptor[].class)) {

      for (var adapter : descriptor.adapters()) {
        adapterFinder.put(adapter.name(), descriptor);
      }
      for (var serv : descriptor.services().keySet()) {
        serviceFinder.put(serv, descriptor);
      }
      for (var annotation : descriptor.annotations().keySet()) {
        annotationFinder.put(annotation, descriptor);
      }
      for (var verb : descriptor.verbs().keySet()) {
        verbFinder.put(verb, descriptor);
      }

      components.put(descriptor.id(), descriptor);
    }
  }

  private void saveConfiguration() {
    Utils.Json.save(
        components.values().toArray(new Extensions.ComponentDescriptor[] {}), this.catalogFile);
  }

  public List<Extensions.ComponentDescriptor> resolveServiceCall(String name, Version version) {
    List<Extensions.ComponentDescriptor> ret = new ArrayList<>();
    Extensions.ComponentDescriptor target = null;
    for (var component : serviceFinder.get(name)) {
      if (version == null) {
        if (target == null || component.version().greater(target.version())) {
          target = component;
        }
      } else if (version.compatible(component.version())) {
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

  public Collection<Extensions.ComponentDescriptor> getComponents(Scope scope) {
    return components.values().stream().filter(/* TODO permissions */ c -> true).toList();
  }

  /*
  The part of the function descriptor that can't serialize to JSON
   */
  public static class ServiceImplementation {
    public Class<?> implementation;
    // if not null, the class is reentrant and we use this instance
    public Object mainClassInstance;
    // otherwise we create it on demand using this constructor, with argument matching
    public Constructor<?> constructor;
    // if not null, the class is a non-static subclass
    public Object wrappingClassInstance;
    // if
    public Method method;
  }

  public ServiceImplementation implementation(Extensions.FunctionDescriptor descriptor) {
    return serviceImplementations.get(descriptor.serviceInfo.getName());
  }

  public Pair<Extensions.ComponentDescriptor, ResourceSet> installComponent(
      File resourcePath, String mavenCoordinates, Scope scope) {

    // TODO allow same path with different versions and replacing same version
    var pluginDestination =
        new File(pluginPath + File.separator + Utils.Files.getFileName(resourcePath));

    // check if we're installing from a different location
    if (resourcePath.getParent() == null
        || !resourcePath.toPath().getParent().equals(pluginPath.toPath())) {
      try {
        Files.copy(
            resourcePath.toPath(), pluginDestination.toPath(), StandardCopyOption.REPLACE_EXISTING);
      } catch (IOException e) {
        scope.error(e);
        return Pair.of(null, ResourceSet.empty(Notification.error(e)));
      }
    } else if (!pluginDestination.exists()) {
      pluginDestination = resourcePath;
    }

    var ret = new ResourceSet();
    Extensions.ComponentDescriptor info = null;
    try {
      var pluginId = componentManager.loadPlugin(pluginDestination.toPath());
      var plugin = componentManager.getPlugin(pluginId);
      ResourceSet.Resource result =
          new ResourceSet.Resource(
              service.serviceId(),
              pluginId,
              null,
              Version.create(plugin.getDescriptor().getVersion()),
              KlabAsset.KnowledgeClass.COMPONENT);

      Plugin component = plugin.getPlugin();
      if (component instanceof KlabComponent comp) {
        info = registerComponent(comp, mavenCoordinates, pluginDestination);
        ret.getNotifications().add(info.extractInfo());
        ret.getResults().add(result);
      } else {
        ret =
            ResourceSet.empty(
                Notification.error(
                    "Plugin "
                        + Utils.Files.getFileName(resourcePath)
                        + " is "
                        + "not a valid k.LAB component"));
        Utils.Files.deleteQuietly(pluginDestination);
      }
    } catch (Throwable t) {
      ret = ResourceSet.empty(Notification.create(t));
      Utils.Files.deleteQuietly(pluginDestination);
    }

    return Pair.of(info, ret);
  }

  /**
   * Return the function descriptor that corresponds to the passed call, considering any version
   * requirements and arguments. If no version requirements are present, return the highest version
   * among the compatible ones.
   *
   * <p>The service call can also be used to locate export/import schemata by passing the schema ID
   * as a service name and the properties as parameters, with "FILE" or "URL" as argument for
   * bytestream-based schemata.
   *
   * @param call
   * @return
   */
  public Extensions.FunctionDescriptor getFunctionDescriptor(ServiceCall call) {
    var version = call.getRequiredVersion();
    Extensions.ComponentDescriptor target = null;
    for (var component : serviceFinder.get(call.getUrn())) {
      if (version == null) {
        if (target == null || component.version().greater(target.version())) {
          target = component;
        }
      } else if (version.compatible(component.version())) {
        target = component;
      }
    }
    return target == null ? null : target.services().get(call.getUrn());
  }

  /**
   * Call with a new plugin file (located anywhere) and optional Maven coordinates to build the
   * descriptors, entries in the catalog, and return a {@link KlabComponent} that can be activated,
   * or null.
   *
   * @param componentJar
   * @param mavenCoordinates
   * @return the component URN or null
   */
  public String registerComponent(File componentJar, String mavenCoordinates, Scope scope) {
    var result = installComponent(componentJar, mavenCoordinates, scope);
    if (result != null && !result.getSecond().isEmpty()) {
      return result.getFirst().id();
    }
    return null;
  }

  /**
   * Discover and register all the extensions provided by this component but do not start it. If the
   * plugin exists and has Maven coordinates in components.json, it is provided to other services
   * and we're in charge of checking for updates, so ensure we have a file hash for the update
   * service to check and replace the file if a new snapshot is discovered in the local repository
   * with a different hash.
   *
   * @param component
   */
  public Extensions.ComponentDescriptor registerComponent(
      KlabComponent component, String mavenCoordinates, File pluginFile) {

    // TODO negotiate updates before we open the file.

    var componentName = component.getName();
    var componentVersion = component.getVersion();
    var libraries = new ArrayList<Extensions.LibraryDescriptor>();
    var adapters = new ArrayList<Extensions.AdapterDescriptor>();
    var license = component.getWrapper().getDescriptor().getLicense();
    var description = component.getWrapper().getDescriptor().getPluginDescription();
    var sourceArchive =
        component.getWrapper().getPluginPath() == null
            ? null
            : component.getWrapper().getPluginPath().toFile();
    var permissions =
        license == null ? ResourcePrivileges.PUBLIC : ResourcePrivileges.create(license);

    scanPackage(
        component,
        Map.of(
            Library.class,
            (annotation, cls) -> registerLibrary((Library) annotation, cls, libraries),
            ResourceAdapter.class,
            (annotation, cls) -> registerAdapter((ResourceAdapter) annotation, cls, adapters)));

    var componentDescriptor =
        new Extensions.ComponentDescriptor(
            componentName,
            componentVersion,
            description,
            sourceArchive,
            Utils.Files.hash(sourceArchive),
            mavenCoordinates,
            libraries,
            adapters,
            new HashMap<>(),
            new HashMap<>(),
            new HashMap<>());

    // update catalog
    for (var library : componentDescriptor.libraries()) {
      for (var service : library.services()) {
        serviceFinder.put(service.getFirst().getName(), componentDescriptor);
        componentDescriptor.services().put(service.getFirst().getName(), service.getSecond());
      }
      for (var service : library.annotations()) {
        annotationFinder.put(service.getFirst().getName(), componentDescriptor);
        componentDescriptor.annotations().put(service.getFirst().getName(), service.getSecond());
      }
      for (var service : library.verbs()) {
        verbFinder.put(service.getFirst().getName(), componentDescriptor);
        componentDescriptor.verbs().put(service.getFirst().getName(), service.getSecond());
      }
    }

    this.components.put(componentName, componentDescriptor);

    saveConfiguration();

    return componentDescriptor;
  }

  private void registerLibrary(
      Library annotation, Class<?> cls, List<Extensions.LibraryDescriptor> libraries) {

    String namespacePrefix =
        Library.CORE_LIBRARY.equals(annotation.name()) ? "" : (annotation.name() + ".");

    var prototypes = new ArrayList<Pair<ServiceInfo, Extensions.FunctionDescriptor>>();
    var annotations = new ArrayList<Pair<ServiceInfo, Extensions.FunctionDescriptor>>();
    var verbs = new ArrayList<Pair<ServiceInfo, Extensions.FunctionDescriptor>>();

    for (Class<?> clss : cls.getClasses()) {
      if (clss.isAnnotationPresent(KlabFunction.class)) {
        var serviceInfo =
            createContextualizerPrototype(namespacePrefix, clss.getAnnotation(KlabFunction.class));
        prototypes.add(Pair.of(serviceInfo, createFunctionDescriptor(serviceInfo, clss, null)));
      } else if (clss.isAnnotationPresent(Verb.class)) {
        var serviceInfo = createVerbPrototype(namespacePrefix, clss.getAnnotation(Verb.class));
        verbs.add(Pair.of(serviceInfo, createFunctionDescriptor(serviceInfo, clss, null)));
      } else if (clss.isAnnotationPresent(KlabAnnotation.class)) {
        var serviceInfo =
            createPrototype(namespacePrefix, clss.getAnnotation(KlabAnnotation.class));
        annotations.add(Pair.of(serviceInfo, createFunctionDescriptor(serviceInfo, clss, null)));
      }
    }

    // annotated methods
    for (Method method : cls.getDeclaredMethods()) {
      if (Modifier.isPublic(method.getModifiers())
          && method.isAnnotationPresent(KlabFunction.class)) {
        var serviceInfo =
            createContextualizerPrototype(
                namespacePrefix, method.getAnnotation(KlabFunction.class));
        prototypes.add(Pair.of(serviceInfo, createFunctionDescriptor(serviceInfo, cls, method)));
      } else if (method.isAnnotationPresent(KlabAnnotation.class)) {
        var serviceInfo =
            createPrototype(namespacePrefix, method.getAnnotation(KlabAnnotation.class));
        annotations.add(Pair.of(serviceInfo, createFunctionDescriptor(serviceInfo, cls, method)));
      } else if (method.isAnnotationPresent(Verb.class)) {
        var serviceInfo = createVerbPrototype(namespacePrefix, method.getAnnotation(Verb.class));
        verbs.add(Pair.of(serviceInfo, createFunctionDescriptor(serviceInfo, cls, method)));
      } else if (method.isAnnotationPresent(Importer.class)) {
        var serviceInfo = createPrototype(namespacePrefix, method.getAnnotation(Importer.class));
        prototypes.add(Pair.of(serviceInfo, createFunctionDescriptor(serviceInfo, cls, method)));
        ResourceTransport.INSTANCE.registerImportSchema(serviceInfo);
      } else if (method.isAnnotationPresent(Exporter.class)) {
        var serviceInfo = createPrototype(namespacePrefix, method.getAnnotation(Exporter.class));
        prototypes.add(Pair.of(serviceInfo, createFunctionDescriptor(serviceInfo, cls, method)));
        ResourceTransport.INSTANCE.registerExportSchema(serviceInfo);
      }
    }

    libraries.add(
        new Extensions.LibraryDescriptor(
            annotation.name(), annotation.description(), prototypes, annotations, verbs));
  }

  private Extensions.FunctionDescriptor createFunctionDescriptor(
      ServiceInfo serviceInfo, Class<?> clss, Method method) {

    var ret = new Extensions.FunctionDescriptor();
    ServiceImplementation implementation = new ServiceImplementation();
    serviceImplementations.put(serviceInfo.getName(), implementation);

    ret.serviceInfo = serviceInfo;
    implementation.implementation = clss;

    if (method != null) {
      implementation.method = method;
      ret.methodCall = 3;
      if (java.lang.reflect.Modifier.isStatic(implementation.method.getModifiers())
          || serviceInfo.isReentrant()) {
        // use a global class instance
        implementation.mainClassInstance = createGlobalClassInstance(ret);
        ret.staticMethod =
            java.lang.reflect.Modifier.isStatic(implementation.method.getModifiers());
      } else if (!serviceInfo.isReentrant()) {
        // create the instance just for this prototype
        try {
          if (ServiceConfiguration.INSTANCE.getMainService() != null) {

            var mainService = ServiceConfiguration.INSTANCE.getMainService();
            /*
            try first with the actual service class
             */
            try {
              implementation.mainClassInstance =
                  implementation
                      .implementation
                      .getDeclaredConstructor(
                          ServiceConfiguration.INSTANCE.getMainService().getClass())
                      .newInstance(mainService);
            } catch (Throwable t) {
            }
            if (implementation.mainClassInstance == null) {
              try {
                implementation.mainClassInstance =
                    implementation
                        .implementation
                        .getDeclaredConstructor(KlabService.class)
                        .newInstance(mainService);
              } catch (Throwable t) {
              }
            }
          }
          if (implementation.mainClassInstance == null) {
            implementation.mainClassInstance =
                implementation.implementation.getDeclaredConstructor().newInstance();
          }
        } catch (Exception e) {
          Logging.INSTANCE.error(
              "Cannot instantiate main class for function library "
                  + implementation(ret).implementation.getCanonicalName()
                  + ": "
                  + e.getMessage());
          ret.error = true;
        }
      }
    } else {

      // analyze constructor
      if (serviceInfo.isReentrant()) {
        // create the instance just for this prototype
        try {
          implementation.mainClassInstance = createGlobalClassInstance(ret);
        } catch (Exception e) {
          ret.error = true;
        }
      } else {
        try {
          implementation.constructor =
              implementation.implementation.getDeclaredConstructor(
                  getParameterClasses(serviceInfo, ret));
          ret.methodCall = 1;
        } catch (NoSuchMethodException | SecurityException e) {
          // move along
        }
        if (implementation.constructor == null) {
          try {
            implementation.constructor =
                implementation.implementation.getDeclaredConstructor(
                    getParameterClasses(serviceInfo, ret));
            ret.methodCall = 2;
          } catch (NoSuchMethodException | SecurityException e) {
            // move along
          }
        }
        if (implementation.constructor == null) {
          ret.methodCall = 3;
        }
      }
    }

    return ret;
  }

  /**
   * Find an adapter provided by one of the known services. The correct call sequence for this one
   * passes the adapter ID from a resolved {@link Resource}, which guarantees that the adapter is
   * available on the same service the resource comes from.
   */
  public Extensions.AdapterDescriptor findAdapter(String adapterId, Version version) {
    // TODO handle permissions

    return adapterDescriptorFinder.containsKey(adapterId)
        ? /* TODO handle version */ adapterDescriptorFinder.get(adapterId).iterator().next()
        : null;
  }

  /**
   * Retrieve an adapter if any. Only works if the adapter is locally present.
   *
   * @param urn
   * @param version
   * @param scope
   * @return
   */
  public Adapter getAdapter(String urn, Version version, Scope scope) {
    // TODO handle permissions

    return adapters.containsKey(urn)
        ? /* TODO handle version */ adapters.get(urn).iterator().next()
        : null;
  }

  /**
   * Return the default parameterization for functions and constructors according to function type
   * and allowed "style".
   *
   * @param serviceInfo
   * @param functionDescriptor
   * @return
   */
  private Class<?>[] getParameterClasses(
      ServiceInfo serviceInfo, Extensions.FunctionDescriptor functionDescriptor) {
    switch (serviceInfo.getFunctionType()) {
      case ANNOTATION:
        break;
      case FUNCTION:
        if (implementation(functionDescriptor).constructor != null) {
          // TODO check: using the last constructor with parameters, or the empty constructor if
          //  found.
          Class<?> cls = implementation(functionDescriptor).implementation;
          if (cls == null) {
            throw new KlabIllegalStateException(
                "no declared executor class for service "
                    + serviceInfo.getName()
                    + ": "
                    + "constructor can't be extracted");
          }
          Class[] ret = null;
          for (Constructor<?> constructor : cls.getConstructors()) {
            if (ret == null || ret.length == 0) {
              ret = constructor.getParameterTypes();
            }
          }
          if (ret == null) {
            throw new KlabIllegalStateException(
                "no usable constructor for service "
                    + serviceInfo.getName()
                    + " served by "
                    + "class "
                    + cls.getCanonicalName());
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
    throw new KlabIllegalArgumentException(
        "can't assess parameter types for " + serviceInfo.getName());
  }

  private Object createGlobalClassInstance(Extensions.FunctionDescriptor ret) {
    try {
      Object instance = this.globalInstances.get(implementation(ret).implementation);
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
                implementation(ret)
                    .implementation
                    .getDeclaredConstructor(
                        ServiceConfiguration.INSTANCE.getMainService().getClass())
                    .newInstance(mainService);
          } catch (Throwable t) {
          }
          if (instance == null) {
            try {
              instance =
                  implementation(ret)
                      .implementation
                      .getDeclaredConstructor(KlabService.class)
                      .newInstance(mainService);
            } catch (Throwable t) {
            }
          }
        }
        if (instance == null) {
          instance = implementation(ret).implementation.getDeclaredConstructor().newInstance();
        }
        this.globalInstances.put(implementation(ret).implementation, instance);
      }
      return instance;
    } catch (InstantiationException
        | IllegalAccessException
        | IllegalArgumentException
        | InvocationTargetException
        | NoSuchMethodException
        | SecurityException e) {
      ret.error = true;
      Logging.INSTANCE.error(
          "Cannot instantiate main class for function library "
              + implementation(ret).implementation.getCanonicalName()
              + ": "
              + e.getMessage());
    }
    return null;
  }

  private void registerAdapter(
      ResourceAdapter annotation, Class<?> cls, List<Extensions.AdapterDescriptor> adapters) {

    try {
      var adapter = new AdapterImpl(cls, annotation);
      if (adapter.initialize()) {
        this.adapters.put(adapter.getName(), adapter);
        this.adapterDescriptorFinder.put(adapter.getName(), adapter.getAdapterInfo());
        adapters.add(adapter.getAdapterInfo());
      } else {
        Logging.INSTANCE.info("Skipping adapter " + adapter.getName() + ": initialization failed");
      }
    } catch (Throwable t) {
      Logging.INSTANCE.error("Adapter loading threw an exception", t);
    }
  }

  /**
   * Retrieve a component in a given version or the latest. TODO use this in other methods that use
   * the logic.
   *
   * @param urn
   * @param version
   * @return
   */
  public Extensions.ComponentDescriptor getComponent(String urn, Version version) {

    Extensions.ComponentDescriptor ret = null;
    for (var component : components.get(urn)) {
      if (version == null) {
        if (ret == null || component.version().greater(ret.version())) {
          ret = component;
        }
      } else if (version.compatible(component.version())) {
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
  public synchronized boolean loadComponents(ResourceSet resourceSet, Scope scope) {

    Set<String> available = new HashSet<>();
    for (var result : resourceSet.getResults()) {
      if (result.getKnowledgeClass() == KlabAsset.KnowledgeClass.COMPONENT) {
        for (var existing : components.get(result.getResourceUrn())) {
          if (result.getResourceVersion() == null
              || existing.version().compatible(result.getResourceVersion())) {
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

        final String mediaType = "application/java-archive";
        var schemata =
            ResourceTransport.INSTANCE.findExportSchemata(
                KlabAsset.KnowledgeClass.COMPONENT, mediaType, service.capabilities(scope), scope);
        if (schemata.isEmpty()) {
          throw new KlabAuthorizationException(
              "No authorized export schema with media type " + mediaType + " is available");
        } else if (schemata.size() > 1) {
          scope.warn(
              "Ambiguous request: more than one export schema with "
                  + "media type "
                  + mediaType
                  + " is available");
        }

        File plugin = new File(pluginPath + File.separator + result.getResourceUrn() + ".jar");
        try (var input =
                service.exportAsset(
                    result.getResourceUrn(), schemata.getFirst(), mediaType, scope);
            var output = new FileOutputStream(plugin)) {
          IOUtils.copy(input, output);
          // give the OS time to react - found that often the file is truncated
          TimeUnit.SECONDS.sleep(2);
        } catch (Exception e) {
          scope.error(e);
          return false;
        }
        installComponent(plugin, null, scope);
      }
    }

    // hopefully this is OK with plugins that have started already
    componentManager.startPlugins();

    return true;
  }

  public void scanPackage(
      String[] internalPackages,
      Map<Class<? extends Annotation>, BiConsumer<Annotation, Class<?>>> annotationHandlers) {

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

  public void scanPackage(
      KlabComponent component,
      Map<Class<? extends Annotation>, BiConsumer<Annotation, Class<?>>> annotationHandlers) {

    try (ScanResult scanResult =
        new ClassGraph()
            .enableAnnotationInfo()
            .addClassLoader(component.getWrapper().getPluginClassLoader())
            .acceptPackages(component.getClass().getPackageName())
            .scan()) {
      for (Class<? extends Annotation> ah : annotationHandlers.keySet()) {
        for (ClassInfo routeClassInfo : scanResult.getClassesWithAnnotation(ah)) {
          try {
            Class<?> cls =
                Class.forName(
                    routeClassInfo.getName(), false, component.getWrapper().getPluginClassLoader());
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

  private ServiceInfoImpl createVerbPrototype(String namespacePrefix, Verb annotation) {
    // TODO
    return null;
  }

  private ServiceInfoImpl createContextualizerPrototype(
      String namespacePrefix, KlabFunction annotation) {

    var ret = new ServiceInfoImpl();

    ret.setName(namespacePrefix + annotation.name());
    ret.setDescription(annotation.description());
    ret.setFilter(annotation.filter());
    ret.setGeometry(
        annotation.geometry().isEmpty() ? null : Geometry.create(annotation.geometry()));
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
    for (KlabFunction.Export argument : annotation.exports()) {
      var arg = createArgument(argument);
      ret.getImports().add(arg);
    }
    for (KlabFunction.Import argument : annotation.imports()) {
      var arg = createArgument(argument);
      ret.getExports().add(arg);
    }

    if (annotation.fillingCurve() != null) {
      ret.getAnnotations()
          .add(
              org.integratedmodelling.klab.api.lang.Annotation.of(
                  "fillcurve",
                  org.integratedmodelling.klab.api.lang.Annotation.VALUE_PARAMETER_KEY,
                  annotation.fillingCurve()));
    }
    if (annotation.split() > 0) {
      ret.getAnnotations()
          .add(
              org.integratedmodelling.klab.api.lang.Annotation.of(
                  "split",
                  org.integratedmodelling.klab.api.lang.Annotation.VALUE_PARAMETER_KEY,
                  annotation.split()));
    }

    return ret;
  }

  private ServiceInfoImpl createPrototype(String namespacePrefix, KlabAnnotation annotation) {

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

  private ServiceInfoImpl createPrototype(String namespacePrefix, Exporter annotation) {

    var ret = new ServiceInfoImpl();

    ret.setName(namespacePrefix + annotation.schema());
    ret.setDescription(annotation.description());
    ret.setFunctionType(ServiceInfo.FunctionType.FREEFORM);
    ret.getTargets().add(annotation.knowledgeClass());
    if (annotation.mediaType() != null) {
      ret.getMediaTypes().add(annotation.mediaType());
    }

    for (KlabFunction.Argument argument : annotation.properties()) {
      var arg = createArgument(argument);
      ret.getArguments().put(arg.getName(), arg);
    }

    /*
    TODO create the records in ResourceTransport!
     */

    return ret;
  }

  private ServiceInfoImpl createPrototype(String namespacePrefix, Importer annotation) {

    var ret = new ServiceInfoImpl();

    ret.setName(namespacePrefix + annotation.schema());
    ret.setDescription(annotation.description());
    ret.setFunctionType(ServiceInfo.FunctionType.FREEFORM);
    ret.getTargets().add(annotation.knowledgeClass());
    if (annotation.mediaType() != null) {
      ret.getMediaTypes().add(annotation.mediaType());
    }
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

  private ServiceInfoImpl.ArgumentImpl createArgument(KlabFunction.Import argument) {
    var arg = new ServiceInfoImpl.ArgumentImpl();
    arg.setName(argument.name());
    arg.setDescription(argument.description());
    arg.setOptional(argument.optional());
    arg.setObservableUrn(argument.observable());
    for (Artifact.Type a : argument.type()) {
      arg.getType().add(a);
    }

    return arg;
  }

  private ServiceInfoImpl.ArgumentImpl createArgument(KlabFunction.Export argument) {
    var arg = new ServiceInfoImpl.ArgumentImpl();
    arg.setName(argument.name());
    arg.setDescription(argument.description());
    arg.setOptional(argument.optional());
    arg.setObservableUrn(argument.observable());
    for (Artifact.Type a : argument.type()) {
      arg.getType().add(a);
    }

    return arg;
  }

  public void loadExtensions(String... packageName) {

    var libraries = new ArrayList<Extensions.LibraryDescriptor>();
    var adapters = new ArrayList<Extensions.AdapterDescriptor>();

    scanPackage(
        packageName,
        Map.of(
            Library.class,
            (annotation, cls) -> registerLibrary((Library) annotation, cls, libraries),
            ResourceAdapter.class,
            (annotation, cls) -> registerAdapter((ResourceAdapter) annotation, cls, adapters)));

    localComponentDescriptor.libraries().addAll(libraries);
    localComponentDescriptor.adapters().addAll(adapters);

    this.components.put(LOCAL_SERVICE_COMPONENT, localComponentDescriptor);

    // update catalog
    for (var library : localComponentDescriptor.libraries()) {
      for (var service : library.services()) {
        serviceFinder.put(service.getFirst().getName(), localComponentDescriptor);
        localComponentDescriptor.services().put(service.getFirst().getName(), service.getSecond());
      }
      for (var service : library.annotations()) {
        annotationFinder.put(service.getFirst().getName(), localComponentDescriptor);
        localComponentDescriptor
            .annotations()
            .put(service.getFirst().getName(), service.getSecond());
      }
      for (var service : library.verbs()) {
        verbFinder.put(service.getFirst().getName(), localComponentDescriptor);
        localComponentDescriptor.verbs().put(service.getFirst().getName(), service.getSecond());
      }
    }
  }

  /**
   * Use this call for the "master" service that installs components based on configuration.
   *
   * @param configuration
   * @param pluginPath
   */
  public void initializeComponents(ResourcesConfiguration configuration, File pluginPath) {

//    /*
//    TODO check all existing resources against the configuration; retrieve whatever needs updating;
//     remove anything not configured or deprecated; check integrity and certification for all components
//      before loading them.
//     */
//    if (Utils.Maven.needsUpdate(
//        "org.integratedmodelling", "klab.component.generators", "1.0-SNAPSHOT")) {
//      // shitdown
//
//    }

    initializeComponents(pluginPath);
  }

  /**
   * Call to initialize and use the plugin system. No plugins will be discovered unless this is
   * called. This finds but does not load the configured plugins. Call this one at initialization.
   *
   * <p>TODO use the catalog and register components from Maven after update check
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
        registerComponent(component, null, null /* TODO */);
      }
    }

    this.componentManager.addPluginStateListener(
        new PluginStateListener() {
          @Override
          public void pluginStateChanged(PluginStateEvent event) {
            System.out.println("HOLA! Plugin state: " + event);
          }
        });
  }

  public class AdapterImpl implements Adapter {

    private final String name;
    private Artifact.Type resourceType;
    private final Version version;
    boolean universal;
    boolean threadSafe;
    Class<?> implementationClass;
    Object implementation;
    Set<ResourceAdapter.Validator.LifecyclePhase> validationPhases =
        EnumSet.noneOf(ResourceAdapter.Validator.LifecyclePhase.class);
    private Extensions.FunctionDescriptor typeAttributor;
    private Extensions.FunctionDescriptor encoder;
    private Extensions.FunctionDescriptor contextualizer;
    private Extensions.FunctionDescriptor validator;
    private Extensions.FunctionDescriptor inspector;
    private Extensions.FunctionDescriptor initializer;
    private Extensions.FunctionDescriptor sanitizer;
    private Extensions.FunctionDescriptor publisher;
    private final Extensions.AdapterDescriptor adapterInfo;

    public AdapterImpl(Class<?> implementationClass, ResourceAdapter annotation) {
      this.name = annotation.name();
      this.version = Version.create(annotation.version());
      this.universal = annotation.universal();
      this.threadSafe = annotation.threadSafe();
      this.implementationClass = implementationClass;
      if (this.threadSafe) {
        try {
          this.implementation = implementationClass.getDeclaredConstructor().newInstance();
        } catch (Exception e) {
          throw new KlabInternalErrorException(
              name + ": thread safe adapters must have a single no-argument constructor");
        }
      }
      this.adapterInfo = scanAdapterClass(implementationClass);
    }

    @Override
    public String getName() {
      return this.name;
    }

    @Override
    public Artifact.Type resourceType(Urn urn) {
      if (typeAttributor != null) {
        // TODO
      }
      return this.resourceType;
    }

    @Override
    public Version getVersion() {
      return this.version;
    }

    @Override
    public boolean hasContextualizer() {
      return contextualizer != null;
    }

    @Override
    public boolean hasInspector() {
      return inspector != null;
    }

    @Override
    public boolean hasValidator() {
      return validator != null;
    }

    @Override
    public boolean hasSanitizer() {
      return sanitizer != null;
    }

    @Override
    public boolean hasPublisher() {
      return publisher != null;
    }

    @Override
    public Extensions.FunctionDescriptor getEncoder() {
      return this.encoder;
    }

    @Override
    public Extensions.FunctionDescriptor getInspector() {
      return this.inspector;
    }

    @Override
    public Extensions.FunctionDescriptor getContextualizer() {
      return this.contextualizer;
    }

    @Override
    public Extensions.FunctionDescriptor getPublisher() {
      return this.publisher;
    }

    @Override
    public Extensions.FunctionDescriptor getSanitizer() {
      return this.sanitizer;
    }

    @Override
    public Extensions.FunctionDescriptor getValidator() {
      return this.validator;
    }

    public boolean initialize() {
      if (initializer != null) {
        // TODO return false iif: 1) no suitable parameters for the method; 2) calling a method
        // throws
        // an exception; 3) the method returns Boolean.FALSE
      }
      return true;
    }

    @Override
    public Resource contextualize(
        Resource resource, Geometry geometry, Scope scope, Object... contextParameters) {
      if (contextualizer != null) {
        // TODO
      }
      return resource;
    }

    @Override
    public Extensions.AdapterDescriptor getAdapterInfo() {
      return this.adapterInfo;
    }

    @Override
    public boolean encode(
        Resource resource,
        Geometry geometry,
        Data.Builder builder,
        Observation observation,
        Observable observable,
        Urn urn,
        Parameters<String> urnParameters,
        Data inputData,
        Scope scope) {

      var implementation = implementation(this.encoder);
      if (implementation != null) {

        // TODO create implementation with own instance if not reentrant

        var ret =
            executeMethod(
                implementation,
                resource,
                geometry,
                builder,
                observation,
                observable,
                urn,
                urnParameters,
                null,
                null,
                null,
                null,
                null,
                List.of(),
                scope);

        if (ret instanceof Throwable) {
          scope.error(ret);
          return false;
        } else if (ret instanceof Boolean) {
          return (Boolean) ret;
        }

        return true;
      }

      return false;
    }

    private Extensions.AdapterDescriptor scanAdapterClass(Class<?> adapterClass) {

      var capabilities = service.capabilities(service.serviceScope());

      var validations = EnumSet.noneOf(ResourceAdapter.Validator.LifecyclePhase.class);
      List<ResourceTransport.Schema> exportSchemata = new ArrayList<>();
      List<ResourceTransport.Schema> importSchemata = new ArrayList<>();

      // annotated methods
      for (Method method : adapterClass.getDeclaredMethods()) {

        if (method.isAnnotationPresent(ResourceAdapter.Encoder.class)) {
          var funcData =
              createServiceImplementation(
                  method, method.getAnnotation(ResourceAdapter.Encoder.class));
          serviceImplementations.put(
              funcData.getFirst().serviceInfo.getName(), funcData.getSecond());
          this.encoder = funcData.getFirst();

        } else if (method.isAnnotationPresent(ResourceAdapter.Contextualizer.class)) {

          if (!Resource.class.isAssignableFrom(method.getReturnType())) {
            throw new KlabIllegalStateException(
                "Adapter methods annotated with @Contextualizer must return a Resource");
          }

          var funcData =
              createServiceImplementation(
                  method, method.getAnnotation(ResourceAdapter.Validator.class));
          serviceImplementations.put(
              funcData.getFirst().serviceInfo.getName(), funcData.getSecond());
          this.contextualizer = funcData.getFirst();

        } else if (method.isAnnotationPresent(ResourceAdapter.Inspector.class)) {
          var funcData =
              createServiceImplementation(
                  method, method.getAnnotation(ResourceAdapter.Inspector.class));
          serviceImplementations.put(
              funcData.getFirst().serviceInfo.getName(), funcData.getSecond());
          this.inspector = funcData.getFirst();
        } else if (method.isAnnotationPresent(ResourceAdapter.Initializer.class)) {
          var funcData =
              createServiceImplementation(
                  method, method.getAnnotation(ResourceAdapter.Initializer.class));
          serviceImplementations.put(
              funcData.getFirst().serviceInfo.getName(), funcData.getSecond());
          this.initializer = funcData.getFirst();
        } else if (method.isAnnotationPresent(ResourceAdapter.Publisher.class)) {
          var funcData =
              createServiceImplementation(
                  method, method.getAnnotation(ResourceAdapter.Publisher.class));
          serviceImplementations.put(
              funcData.getFirst().serviceInfo.getName(), funcData.getSecond());
          this.publisher = funcData.getFirst();
        } else if (method.isAnnotationPresent(ResourceAdapter.Sanitizer.class)) {
          var funcData =
              createServiceImplementation(
                  method, method.getAnnotation(ResourceAdapter.Sanitizer.class));
          serviceImplementations.put(
              funcData.getFirst().serviceInfo.getName(), funcData.getSecond());
          this.sanitizer = funcData.getFirst();
        } else if (method.isAnnotationPresent(ResourceAdapter.Validator.class)) {
          var a = method.getAnnotation(ResourceAdapter.Validator.class);
          var funcData =
              createServiceImplementation(
                  method, method.getAnnotation(ResourceAdapter.Validator.class));
          serviceImplementations.put(
              funcData.getFirst().serviceInfo.getName(), funcData.getSecond());
          this.validator = funcData.getFirst();
          validations.addAll(Arrays.asList(a.phase()));
        } else if (method.isAnnotationPresent(ResourceAdapter.Type.class)) {

          if (!Artifact.Type.class.isAssignableFrom(method.getReturnType())) {
            throw new KlabIllegalStateException(
                "Adapter methods annotated with @Type must return an Artifact.Type");
          }
          var funcData =
              createServiceImplementation(method, method.getAnnotation(ResourceAdapter.Type.class));
          serviceImplementations.put(
              funcData.getFirst().serviceInfo.getName(), funcData.getSecond());
          this.typeAttributor = funcData.getFirst();

        } else if (method.isAnnotationPresent(Importer.class)) {
          var serviceInfo = createPrototype(name, method.getAnnotation(Importer.class));
          var schema = ResourceTransport.INSTANCE.registerImportSchema(serviceInfo);
          schema.setAdapter(name);
          importSchemata.add(schema);
          serviceImplementations.put(
              schema.getSchemaId(),
              createServiceImplementation(method, method.getAnnotation(Importer.class))
                  .getSecond());
        } else if (method.isAnnotationPresent(Exporter.class)) {
          var serviceInfo = createPrototype(name, method.getAnnotation(Exporter.class));
          var schema = ResourceTransport.INSTANCE.registerExportSchema(serviceInfo);
          schema.setAdapter(name);
          exportSchemata.add(schema);
          serviceImplementations.put(
              schema.getSchemaId(),
              createServiceImplementation(method, method.getAnnotation(Exporter.class))
                  .getSecond());
        }
      }

      if (this.encoder == null) {
        throw new KlabIllegalStateException(
            "Cannot load adapter " + name + ": missing encoder method");
      }
      if ((this.resourceType == null || this.resourceType == Artifact.Type.VOID)
          && typeAttributor == null) {
        throw new KlabIllegalStateException(
            "Cannot load adapter "
                + name
                + ": missing type attribution in annotation or "
                + "methods");
      }

      return new Extensions.AdapterDescriptor(
          name,
          version,
          capabilities.getServiceId(),
          capabilities.getType(),
          universal,
          threadSafe,
          hasContextualizer(),
          hasSanitizer(),
          hasInspector(),
          hasPublisher(),
          validations,
          importSchemata,
          exportSchemata);
    }

    private Pair<Extensions.FunctionDescriptor, ServiceImplementation> createServiceImplementation(
        Method method, Annotation annotation) {
      ServiceImplementation impl = new ServiceImplementation();
      impl.method = method;
      if (!Modifier.isStatic(method.getModifiers())) {
        if (this.threadSafe) {
          impl.mainClassInstance = this.implementation;
        } else {
          try {
            for (var constructor : this.getClass().getConstructors()) {
              if (impl.constructor != null) {
                throw new KlabIllegalStateException(
                    name + ": adapter classes can only have one constructor");
              }
              impl.constructor = constructor;
            }
          } catch (Exception e) {
            throw new KlabInternalErrorException(e);
          }
        }
      }

      // function URN is non-conflicting with anything user-related and will be linked to
      // the service implementation so it can be called as usual
      String functionUrn = "ADAPTER." + name + "." + annotation.getClass().getCanonicalName();
      var ret = new Extensions.FunctionDescriptor();
      ret.methodCall = 3;
      ret.staticMethod = Modifier.isStatic(method.getModifiers());
      var serviceInfo = new ServiceInfoImpl();
      serviceInfo.setName(functionUrn);
      ret.serviceInfo = serviceInfo;
      return Pair.of(ret, impl);
    }
  }

  public static Object executeMethod(
      ServiceImplementation implementation,
      Resource resource,
      Geometry geometry,
      Data.Builder builder,
      Observation observation,
      Observable observable,
      Urn urn,
      Parameters<String> urnParameters,
      ServiceCall serviceCall,
      Storage storage,
      Expression expression,
      LookupTable lookupTable,
      Data inputData,
      Collection<org.integratedmodelling.klab.api.lang.Annotation> annotations,
      Scope scope) {

    var arguments =
        matchArguments(
            implementation.method,
            resource,
            geometry,
            builder,
            observation,
            observable,
            urn,
            urnParameters,
            serviceCall,
            storage,
            expression,
            lookupTable,
            inputData,
            annotations,
            scope);
    if (arguments == null) {
      return new KlabCompilationError(
          "Cannot match arguments for call to " + implementation.method);
    }

    try {
      return implementation.method.invoke(implementation.mainClassInstance, arguments.toArray());
    } catch (Exception e) {
      return new KlabCompilationError(e);
    }
  }

  /**
   * Painful argument matcher for method using or inferring all possible arguments
   *
   * @param method
   * @param resource
   * @param geometry
   * @param builder
   * @param observation
   * @param observable
   * @param urn
   * @param urnParameters
   * @param serviceCall
   * @param storage
   * @param expression
   * @param lookupTable
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
      Storage<?> storage,
      Expression expression,
      LookupTable lookupTable,
      Data inputData,
      Collection<org.integratedmodelling.klab.api.lang.Annotation> annotations,
      Scope scope) {
    List<Object> runArguments = new ArrayList<>();
    DigitalTwin digitalTwin = null;
    if (scope instanceof ContextScope contextScope) {
      digitalTwin = contextScope.getDigitalTwin();
    }
    Scale scale = geometry instanceof Scale scale1 ? scale1 : null;

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
        } else if (Storage.Buffer.class.isAssignableFrom(argument)) {
          storage =
              digitalTwin == null
                  ? null
                  : digitalTwin
                      .getStateStorage()
                      .promoteStorage(
                          observation, storage, AbstractBuffer.getStorageClass(argument, annotations));
          if (storage != null) {
            var buffers =
                storage.buffers(geometry, argument.asSubclass(Storage.Buffer.class), annotations);
            if (buffers.size() != 1) {
              throw new KlabInternalErrorException(
                  "Wrong buffer numerosity for single-buffer parameter: review configuration");
            }
            runArguments.add(buffers.getFirst());
          } else {
            runArguments.add(null);
          }

        } else if (DoubleStorage.class.isAssignableFrom(argument)) {
          storage =
              digitalTwin == null
                  ? null
                  : digitalTwin
                      .getStateStorage()
                      .promoteStorage(observation, storage, DoubleStorage.class);
          runArguments.add(storage);
        } /*else if (LongStorage.class.isAssignableFrom(argument)) {
            storage =
                digitalTwin == null
                    ? null
                    : digitalTwin
                        .getStateStorage()
                        .promoteStorage(observation, storage, LongStorage.class);
            runArguments.add(storage);
          } else if (FloatStorage.class.isAssignableFrom(argument)) {
            storage =
                digitalTwin == null
                    ? null
                    : digitalTwin
                        .getStateStorage()
                        .promoteStorage(observation, storage, FloatStorage.class);
            runArguments.add(storage);
          } else if (BooleanStorage.class.isAssignableFrom(argument)) {
            storage =
                digitalTwin == null
                    ? null
                    : digitalTwin
                        .getStateStorage()
                        .promoteStorage(observation, storage, BooleanStorage.class);
            runArguments.add(storage);
          } else if (KeyedStorage.class.isAssignableFrom(argument)) {
            storage =
                digitalTwin == null
                    ? null
                    : digitalTwin
                        .getStateStorage()
                        .promoteStorage(observation, storage, KeyedStorage.class);
            runArguments.add(storage);
          } */ else if (Scale.class.isAssignableFrom(argument)) {
          if (scale == null && geometry != null) {
            scale = Scale.create(geometry);
          }
          runArguments.add(scale);
        } else if (Geometry.class.isAssignableFrom(argument)) {
          runArguments.add(geometry);
        } else if (Observable.class.isAssignableFrom(argument)) {
          runArguments.add(observable);
        } else if (Space.class.isAssignableFrom(argument)) {
          if (scale == null && geometry != null) {
            scale = Scale.create(geometry);
          }
          runArguments.add(scale == null ? null : scale.getSpace());
        } else if (Time.class.isAssignableFrom(argument)) {
          if (scale == null && geometry != null) {
            scale = Scale.create(geometry);
          }
          runArguments.add(scale == null ? null : scale.getTime());
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
