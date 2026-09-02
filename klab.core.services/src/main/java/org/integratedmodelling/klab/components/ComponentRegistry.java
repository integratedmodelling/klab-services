package org.integratedmodelling.klab.components;

import io.github.classgraph.ClassGraph;
import io.github.classgraph.ClassInfo;
import io.github.classgraph.ScanResult;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.jar.JarFile;
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
import org.integratedmodelling.klab.api.digitaltwin.Scheduler;
import org.integratedmodelling.klab.api.engine.StartupOptions;
import org.integratedmodelling.klab.api.exceptions.*;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.knowledge.*;
import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
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
import org.integratedmodelling.klab.api.services.resources.impl.ParameterImpl;
import org.integratedmodelling.klab.api.services.runtime.Notification;
import org.integratedmodelling.klab.api.services.runtime.extension.*;
import org.integratedmodelling.klab.configuration.ServiceConfiguration;
import org.integratedmodelling.klab.extension.KlabComponent;
import org.integratedmodelling.klab.extension.MavenComponentCache;
import org.integratedmodelling.klab.runtime.language.ArgumentMatcher;
import org.integratedmodelling.klab.services.application.web.WebUiConfiguration;
import org.integratedmodelling.klab.services.base.BaseService;
import org.integratedmodelling.klab.services.configuration.ResourcesConfiguration;
import org.integratedmodelling.klab.utilities.Utils;
import org.pf4j.*;
import org.springframework.web.util.UriUtils;

public class ComponentRegistry {

  private static final String PLUGINS_DIRECTORY = "plugins";
  private static final String PENDING_UPDATES_DIRECTORY = "pending-updates";
  private static final String PENDING_UPDATE_ARCHIVE = "archive";
  private static final String PENDING_UPDATE_COMPONENT = "component";
  private static final String PENDING_UPDATE_VERSION = "version";
  private static final String PENDING_UPDATE_SOURCE_SERVICE = "sourceService";
  private static final String PENDING_UPDATE_SOURCE_TIMESTAMP = "sourceTimestamp";
  private static final String PENDING_UPDATE_TARGET_ARCHIVE = "targetArchive";
  private static final String PLUGIN_REQUIRES_ATTRIBUTE = "Plugin-Requires";
  private final BaseService service;
  private final StartupOptions startupOptions;
  private PluginManager componentManager;
  private File pluginPath = null;
  private MavenComponentCache cache;

  /** Browser resource read from an installed component archive. */
  public record WebUiResource(byte[] content, String filename) {}

  // we keep the local services and adapters in here
  // FIXME the permissions should come from the external permission system, not as the internal
  //  Plugin-License
  private final Extensions.ComponentDescriptor localComponentDescriptor;

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
  // these are found by media type, not URN
  private MultiValuedMap<String, Extensions.ComponentDescriptor> exporterFinder =
      new HashSetValuedHashMap<>();
  private MultiValuedMap<String, Extensions.ComponentDescriptor> importerFinder =
      new HashSetValuedHashMap<>();
  private MultiValuedMap<String, Extensions.ComponentDescriptor> actorFinder =
      new HashSetValuedHashMap<>();
  /*
   * Adapter descriptors include those registered from other services.
   */
  private MultiValuedMap<String, AdapterDescriptor> adapterDescriptorFinder =
      new HashSetValuedHashMap<>();
  private Map<Class<?>, Object> globalInstances = new HashMap<>();
  private File catalogFile;
  private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

  public ComponentRegistry(BaseService service, StartupOptions options) {
    this(service, options, null, List.of());
    readConfiguration(service, options);
  }

  ComponentRegistry(
      BaseService service,
      StartupOptions options,
      MavenComponentCache cache,
      Collection<Extensions.ComponentDescriptor> initialComponents) {
    this.startupOptions = options;
    this.cache = cache;
    this.service = service;
    localComponentDescriptor =
        new Extensions.ComponentDescriptor(
            Extensions.LOCAL_SERVICE_COMPONENT,
            Version.CURRENT_VERSION,
            "Natively available " + "services",
            null,
            null,
            null,
            ResourcePrivileges.PUBLIC,
            new ArrayList<>(),
            new ArrayList<>(),
            new HashMap<>(),
            new HashMap<>(),
            new HashMap<>(),
            new HashMap<>(),
            new HashMap<>(),
            service.serviceId(),
            System.currentTimeMillis(),
            Extensions.ComponentImportType.BUILT_IN,
            Extensions.ComponentUpdateStatus.NOT_UPDATEABLE,
            0L);
    for (var component : initialComponents) {
      components.put(component.id(), component);
    }
  }

  public MavenComponentCache getComponentCache() {
    return this.cache;
  }

  /**
   * Add contributions from installed k.LAB components to the service dashboard configuration.
   * Component IDs are traversed deterministically so their declared order remains stable.
   */
  public synchronized void configureWebUi(WebUiConfiguration.Builder dashboard) {
    if (componentManager == null) {
      return;
    }
    var wrappers = new ArrayList<>(componentManager.getPlugins());
    wrappers.sort(Comparator.comparing(wrapper -> wrapper.getDescriptor().getPluginId()));
    for (var wrapper : wrappers) {
      if (!(wrapper.getPlugin() instanceof KlabComponent component)
          || wrapper.getPluginState() == PluginState.DISABLED
          || wrapper.getPluginState() == PluginState.FAILED
          || getExactComponent(component.getName(), component.getVersion()) == null) {
        continue;
      }
      try {
        for (var module : component.webUiModules().entrySet()) {
          var resourcePath = normalizeWebUiModulePath(module.getValue());
          dashboard.module(
              module.getKey(),
              "public/ui/components/"
                  + encodePathSegment(component.getName())
                  + "/"
                  + encodePathSegment(component.getVersion().toString())
                  + "/"
                  + encodeResourcePath(resourcePath));
        }
        component.configureWebUi(dashboard);
      } catch (Throwable failure) {
        Logging.INSTANCE.error(
            "Unable to configure Web UI contributions from component " + component.getName(),
            failure);
      }
    }
  }

  /** Read one explicitly declared ESM file from an installed component. */
  public synchronized Optional<WebUiResource> getWebUiResource(
      String componentId, String version, String resourcePath) {
    if (componentManager == null || componentId == null || version == null) {
      return Optional.empty();
    }
    var wrapper = componentManager.getPlugin(componentId);
    if (wrapper == null
        || !(wrapper.getPlugin() instanceof KlabComponent component)
        || !component.getVersion().toString().equals(version)) {
      return Optional.empty();
    }
    try {
      var normalizedPath = normalizeWebUiModulePath(resourcePath);
      if (component.webUiModules().values().stream()
          .map(ComponentRegistry::normalizeWebUiModulePath)
          .noneMatch(normalizedPath::equals)) {
        return Optional.empty();
      }
      try (InputStream input =
          wrapper
              .getPluginClassLoader()
              .getResourceAsStream(KlabComponent.WEB_UI_RESOURCE_ROOT + normalizedPath)) {
        return input == null
            ? Optional.empty()
            : Optional.of(new WebUiResource(input.readAllBytes(), normalizedPath));
      }
    } catch (IllegalArgumentException | IOException failure) {
      Logging.INSTANCE.warn(
          "Unable to read Web UI module " + resourcePath + " from component " + componentId,
          failure);
      return Optional.empty();
    }
  }

  private static String normalizeWebUiModulePath(String resourcePath) {
    if (resourcePath == null || resourcePath.isBlank() || resourcePath.contains("\\")) {
      throw new IllegalArgumentException("A relative Web UI module path is required");
    }
    var segments = resourcePath.split("/");
    if (resourcePath.startsWith("/")
        || Arrays.stream(segments).anyMatch(segment -> segment.isBlank() || segment.equals(".."))
        || !(resourcePath.endsWith(".js") || resourcePath.endsWith(".mjs"))) {
      throw new IllegalArgumentException(
          "Web UI modules must be relative .js or .mjs files below "
              + KlabComponent.WEB_UI_RESOURCE_ROOT);
    }
    return resourcePath;
  }

  private static String encodePathSegment(String segment) {
    return UriUtils.encodePathSegment(segment, java.nio.charset.StandardCharsets.UTF_8);
  }

  private static String encodeResourcePath(String resourcePath) {
    return Arrays.stream(resourcePath.split("/"))
        .map(ComponentRegistry::encodePathSegment)
        .collect(java.util.stream.Collectors.joining("/"));
  }

  private static File getPluginDirectory(File componentRoot) {
    return new File(componentRoot, PLUGINS_DIRECTORY);
  }

  private boolean isInPluginDirectory(File resourcePath) {
    if (resourcePath == null || this.pluginPath == null) {
      return false;
    }
    var parent = resourcePath.toPath().toAbsolutePath().normalize().getParent();
    return parent != null && parent.equals(this.pluginPath.toPath().toAbsolutePath().normalize());
  }

  private Extensions.ComponentDescriptor selectBestComponent(
      Collection<Extensions.ComponentDescriptor> candidates, Version version) {
    Extensions.ComponentDescriptor ret = null;
    for (var component : candidates) {
      if (version == null || component.version().compatible(version)) {
        if (ret == null || component.version().greater(ret.version())) {
          ret = component;
        }
      }
    }
    return ret;
  }

  private record MavenSnapshotUpdate(
      Extensions.ComponentDescriptor component, MavenComponentCache.Availability availability) {}

  private record MavenUpdateCheck(List<MavenSnapshotUpdate> updates, ResourceSet report) {}

  record PendingDependencyUpdate(
      String componentId,
      Version version,
      String sourceServiceId,
      long sourceTimestamp,
      File archive,
      File marker) {}

  /**
   * Check Maven-sourced SNAPSHOT components for updates without synchronizing a replacement
   * artifact, unloading, or replacing any component.
   */
  public synchronized ResourceSet checkForUpdates() {
    var check = checkMavenSnapshotUpdates(true);
    for (var update : check.updates()) {
      check
          .report()
          .getNotifications()
          .add(
              Notification.info(
                  "Update available for component "
                      + update.component().id()
                      + " from "
                      + updateSource(update.availability().status())
                      + " repository"));
    }
    return check.report();
  }

  /** Check and update all changed Maven-sourced SNAPSHOT components. */
  public synchronized ResourceSet updateMavenSnapshotComponents() {
    return updateMavenSnapshotComponents(true);
  }

  private ResourceSet updateMavenSnapshotComponents(boolean reportNoUpdates) {
    var check = checkMavenSnapshotUpdates(reportNoUpdates);
    for (var update : check.updates()) {
      merge(
          check.report(),
          applyMavenSnapshotUpdate(update.component(), update.availability().status()));
    }
    return check.report();
  }

  /**
   * Update one registered component from its source. At present only Maven-imported SNAPSHOT
   * components support an update action.
   */
  public synchronized ResourceSet updateComponent(String componentId, Version version) {
    var component =
        components.get(componentId).stream()
            .filter(candidate -> version == null || Objects.equals(candidate.version(), version))
            .findFirst()
            .orElse(null);
    if (component == null) {
      return ResourceSet.empty(
          Notification.error(
              "Cannot update unknown component "
                  + componentId
                  + (version == null ? "" : " version " + version)));
    }
    if (component.importType() != Extensions.ComponentImportType.MAVEN
        || component.mavenCoordinates() == null
        || !component.mavenCoordinates().contains("SNAPSHOT")) {
      return ResourceSet.empty(
          Notification.warning(
              "Component " + component.id() + " does not support Maven SNAPSHOT updates"));
    }
    var coordinates = component.mavenCoordinates().split(":");
    if (coordinates.length != 3) {
      return ResourceSet.empty(
          Notification.error("Invalid Maven coordinates for component " + component.id()));
    }
    try {
      var availability =
          cache.getAvailabilityInfo(
              coordinates[0], coordinates[1], coordinates[2], "component", "kar");
      return switch (availability.status()) {
        case NEEDS_UPDATE_FROM_LOCAL_REPOSITORY, NEEDS_UPDATE_FROM_REMOTE_REPOSITORY ->
            applyMavenSnapshotUpdate(component, availability.status());
        case UP_TO_DATE ->
            ResourceSet.empty(
                Notification.info("Component " + component.id() + " is already up to date"));
        case UNKNOWN ->
            ResourceSet.empty(
                Notification.warning(
                    "Unable to establish update status for component " + component.id()));
      };
    } catch (KlabIOException e) {
      return ResourceSet.empty(
          Notification.warning(
              "Unable to establish update status for component "
                  + component.id()
                  + " from "
                  + component.mavenCoordinates()
                  + " with cause "
                  + e.getMessage()));
    }
  }

  private MavenUpdateCheck checkMavenSnapshotUpdates(boolean reportNoUpdates) {
    var ret = new ResourceSet();
    var updates = new ArrayList<MavenSnapshotUpdate>();
    for (var component : new ArrayList<>(components.values())) {
      if (component.mavenCoordinates() != null
          && component.mavenCoordinates().contains("SNAPSHOT")) {
        var coords = component.mavenCoordinates().split(":");
        if (coords.length == 3) {
          try {
            var availability =
                cache.getAvailabilityInfo(coords[0], coords[1], coords[2], "component", "kar");
            if (availability.status()
                    == MavenComponentCache.Status.NEEDS_UPDATE_FROM_LOCAL_REPOSITORY
                || availability.status()
                    == MavenComponentCache.Status.NEEDS_UPDATE_FROM_REMOTE_REPOSITORY) {
              updates.add(new MavenSnapshotUpdate(component, availability));
            } else if (availability.status() == MavenComponentCache.Status.UNKNOWN
                && reportNoUpdates) {
              ret.getNotifications()
                  .add(
                      Notification.warning(
                          "Error establishing update status for component "
                              + component.id()
                              + " from "
                              + component.mavenCoordinates()));
            }
          } catch (KlabIOException e) {
            if (reportNoUpdates) {
              ret.getNotifications()
                  .add(
                      Notification.warning(
                          "Unable to establish update status for component "
                              + component.id()
                              + " from "
                              + component.mavenCoordinates()
                              + " with cause "
                              + e.getMessage()));
            }
          }
        } else if (reportNoUpdates) {
          ret.getNotifications()
              .add(
                  Notification.warning(
                      "Ignoring invalid Maven coordinates for component "
                          + component.id()
                          + ": "
                          + component.mavenCoordinates()));
        }
      }
    }
    if (updates.isEmpty() && ret.getNotifications().isEmpty()) {
      ret.setEmpty(true);
      if (reportNoUpdates) {
        ret.getNotifications().add(Notification.info("No Maven SNAPSHOT component updates found"));
      }
    }
    return new MavenUpdateCheck(List.copyOf(updates), ret);
  }

  private void merge(ResourceSet target, ResourceSet source) {
    if (source != null) {
      target.getNotifications().addAll(source.getNotifications());
      target.getResults().addAll(source.getResults());
    }
  }

  private ResourceSet applyMavenSnapshotUpdate(
      Extensions.ComponentDescriptor component, MavenComponentCache.Status status) {

    Logging.INSTANCE.info(
        "Attempting update of modified component "
            + component.id()
            + " from "
            + component.mavenCoordinates());

    // TODO must unload first. Whether this will free up the file in Win remains to be seen.
    var mavenCoordinates = component.mavenCoordinates().split(":");
    if (mavenCoordinates.length != 3) {
      return ResourceSet.empty(
          Notification.error("Invalid Maven coordinates for component " + component.id()));
    }

    try {
      var file =
          cache.synchronizeArtifact(
              mavenCoordinates[0], mavenCoordinates[1], mavenCoordinates[2], "component", "kar");
      if (file != null && file.exists()) {
        var fileHash = Utils.Files.hash(file);
        if (component.fileHash() != null && component.fileHash().equals(fileHash)) {
          if (status == MavenComponentCache.Status.NEEDS_UPDATE_FROM_LOCAL_REPOSITORY
              || status == MavenComponentCache.Status.NEEDS_UPDATE_FROM_REMOTE_REPOSITORY) {
            return ResourceSet.empty(
                Notification.warning(
                    "Update was indicated for component "
                        + component.id()
                        + " but the retrieved artifact hash is unchanged"));
          }
          return ResourceSet.empty(
              Notification.info("Component " + component.id() + " is already up to date"));
        }
        if (!isCompatibleWithCurrentKlab(file)) {
          return ResourceSet.empty(
              Notification.warning(
                  "Skipping update of component "
                      + component.id()
                      + " because it is not compatible with k.LAB "
                      + Version.CURRENT));
        }
        // TODO the build number should be incremented if the component is local/snapshot. This will
        //  allow other services to know the update must be loaded.
        unloadComponent(component.id(), component.version());
        // TODO remove the previous file if any, as a change from remote to local may leave two
        //  versions in the plugin dir
        var result = installComponent(file, component.mavenCoordinates());
        if (result != null && result.getFirst() != null) {
          componentManager.enablePlugin(result.getFirst().id());
          result
              .getSecond()
              .getNotifications()
              .add(Notification.info("Component " + component.id() + " updated successfully"));
        }
        Logging.INSTANCE.info(
            "Component "
                + component.id()
                + " updated successfully from "
                + (status == MavenComponentCache.Status.NEEDS_UPDATE_FROM_LOCAL_REPOSITORY
                    ? "local"
                    : "remote")
                + " repository");
        return result == null ? ResourceSet.empty() : result.getSecond();
      }
    } catch (Exception e) {
      Logging.INSTANCE.error("Unable to update outdated component " + component.id(), e);
      return ResourceSet.empty(
          Notification.error("Unable to update outdated component " + component.id(), e));
    }
    return ResourceSet.empty(
        Notification.warning(
            "Updated Maven artifact could not be retrieved for component " + component.id()));
  }

  private String updateSource(MavenComponentCache.Status status) {
    return status == MavenComponentCache.Status.NEEDS_UPDATE_FROM_LOCAL_REPOSITORY
        ? "local"
        : "remote";
  }

  private boolean isCompatibleWithCurrentKlab(File pluginFile) {
    try (var jarFile = new JarFile(pluginFile)) {
      var manifest = jarFile.getManifest();
      var requires =
          manifest == null
              ? null
              : manifest.getMainAttributes().getValue(PLUGIN_REQUIRES_ATTRIBUTE);
      if (requires == null || requires.isBlank() || "*".equals(requires.trim())) {
        return true;
      }
      var versionManager =
          componentManager == null
              ? new DefaultVersionManager()
              : componentManager.getVersionManager();
      return versionManager.checkVersionConstraint(Version.CURRENT, requires);
    } catch (Exception e) {
      Logging.INSTANCE.warn(
          "Unable to verify k.LAB version compatibility for " + pluginFile.getAbsolutePath(), e);
      return false;
    }
  }

  private void readConfiguration(BaseService service, StartupOptions options) {

    this.catalogFile =
        ServiceConfiguration.INSTANCE.getFileWithTemplate(
            "services/" + service.serviceType().name().toLowerCase() + "/components/catalog.json",
            "[]");

    this.cache =
        new MavenComponentCache(
            ServiceConfiguration.INSTANCE.getDataDirectory(
                "services/" + service.serviceType().name().toLowerCase() + "/components/cache/"));

    for (var descriptor :
        Utils.Json.load(this.catalogFile, Extensions.ComponentDescriptor[].class)) {

      if (descriptor.id().equals(Extensions.LOCAL_SERVICE_COMPONENT)) {
        continue;
      }

      for (var adapter : descriptor.adapters()) {
        adapterFinder.put(adapter.getName(), descriptor);
      }
      for (var serv : descriptor.services().keySet()) {
        serviceFinder.put(serv, descriptor);
      }
      for (var annotation : descriptor.annotations().keySet()) {
        annotationFinder.put(annotation, descriptor);
      }
      for (var verb : descriptor.actors().keySet()) {
        actorFinder.put(verb, descriptor);
      }

      components.put(descriptor.id(), descriptor);
    }
  }

  private void saveConfiguration() {
    Utils.Json.save(
        components.values().toArray(new Extensions.ComponentDescriptor[] {}), this.catalogFile);
  }

  private boolean removeComponentRegistration(String urn, Version version) {
    var ret = false;
    for (var component : new ArrayList<>(components.get(urn))) {
      if (Objects.equals(component.version(), version)) {
        ret |= removeComponentRegistration(component);
      }
    }
    return ret;
  }

  private Extensions.ComponentDescriptor getExactComponent(String urn, Version version) {
    for (var component : components.get(urn)) {
      if (Objects.equals(component.version(), version)) {
        return component;
      }
    }
    return null;
  }

  private boolean removeComponentRegistration(Extensions.ComponentDescriptor component) {
    var ret = components.removeMapping(component.id(), component);

    removeDescriptorReferences(adapterFinder, component);
    removeDescriptorReferences(serviceFinder, component);
    removeDescriptorReferences(annotationFinder, component);
    removeDescriptorReferences(actorFinder, component);
    removeDescriptorReferences(exporterFinder, component);
    removeDescriptorReferences(importerFinder, component);

    removeFunctionImplementations(component.services());
    removeFunctionImplementations(component.annotations());
    removeActorImplementations(component.actors());
    removeFunctionImplementations(component.exporters());
    removeFunctionImplementations(component.importers());
    removeComponentAdapters(component);

    return ret;
  }

  private void removeActorImplementations(Map<String, List<Extensions.ActorDescriptor>> actors) {
    for (var descriptors : actors.values()) {
      for (var actor : descriptors) {
        actor.verbs.forEach(this::removeFunctionImplementation);
      }
    }
    actors.clear();
  }

  private void removeDescriptorReferences(
      MultiValuedMap<String, Extensions.ComponentDescriptor> finder,
      Extensions.ComponentDescriptor component) {
    for (var key : new ArrayList<>(finder.keySet())) {
      finder.removeMapping(key, component);
    }
  }

  private void removeFunctionImplementations(
      Map<String, List<Extensions.FunctionDescriptor>> functions) {
    for (var descriptors : functions.values()) {
      for (var descriptor : descriptors) {
        removeFunctionImplementation(descriptor);
      }
    }
  }

  private void removeFunctionImplementation(Extensions.FunctionDescriptor descriptor) {
    if (descriptor != null && descriptor.serviceInfo != null) {
      serviceImplementations.remove(descriptor.serviceInfo.getName());
    }
  }

  private void removeComponentAdapters(Extensions.ComponentDescriptor component) {
    for (var adapterName : new ArrayList<>(adapters.keySet())) {
      for (var adapter : new ArrayList<>(adapters.get(adapterName))) {
        if (component.id().equals(adapter.getComponentUrn())
            && component.version().equals(adapter.getComponentVersion())) {
          removeAdapterImplementations(adapter);
          adapters.removeMapping(adapterName, adapter);
          adapterDescriptorFinder.removeMapping(adapterName, adapter.getAdapterInfo());
        }
      }
    }
  }

  private void removeAdapterImplementations(Adapter adapter) {
    removeFunctionImplementation(adapter.getEncoder());
    removeFunctionImplementation(adapter.getInspector());
    removeFunctionImplementation(adapter.getContextualizer());
    removeFunctionImplementation(adapter.getPublisher());
    removeFunctionImplementation(adapter.getSanitizer());
    for (var phase : ResourceAdapter.Validator.LifecyclePhase.values()) {
      removeFunctionImplementation(adapter.getValidator(phase));
    }
  }

  public List<Extensions.ComponentDescriptor> resolveExportSchemata(
      String mediaType, Geometry geometry) {
    List<Extensions.ComponentDescriptor> ret = new ArrayList<>();
    Extensions.ComponentDescriptor target = null;
    var empty = true;
    for (var component : exporterFinder.get(mediaType)) {
      if (geometry != null) {
        // TODO check geometry applies; continue otherwise
      }
      ret.add(component);
    }
    return ret;
  }

  public List<Extensions.ComponentDescriptor> resolveServiceCall(String name, Version version) {
    List<Extensions.ComponentDescriptor> ret = new ArrayList<>();
    Extensions.ComponentDescriptor target = selectBestComponent(serviceFinder.get(name), version);
    if (target != null) {
      /*
      TODO add all dependencies first
       */
      ret.add(target);
    }
    return ret;
  }

  public Collection<Extensions.ComponentDescriptor> getComponents(Scope scope) {
    return components.values().stream()
        .filter(/* TODO permissions */ c -> true)
        .map(component -> computeUpdateStatus(component, scope))
        .toList();
  }

  private Extensions.ComponentDescriptor computeUpdateStatus(
      Extensions.ComponentDescriptor component, Scope scope) {
    if (!component.isUpdateable()) {
      return component.withUpdateStatus(Extensions.ComponentUpdateStatus.NOT_UPDATEABLE, 0L);
    }
    if (component.importType() == Extensions.ComponentImportType.FILE) {
      return component.withUpdateStatus(
          Extensions.ComponentUpdateStatus.UP_TO_DATE, component.timestamp());
    }
    if (component.importType() == Extensions.ComponentImportType.MAVEN) {
      var coordinates = component.mavenCoordinates().split(":");
      if (coordinates.length != 3) {
        return component.withUpdateStatus(Extensions.ComponentUpdateStatus.UNKNOWN, 0L);
      }
      try {
        var availability =
            cache.getAvailabilityInfo(
                coordinates[0], coordinates[1], coordinates[2], "component", "kar");
        var status =
            switch (availability.status()) {
              case UP_TO_DATE -> Extensions.ComponentUpdateStatus.UP_TO_DATE;
              case NEEDS_UPDATE_FROM_LOCAL_REPOSITORY, NEEDS_UPDATE_FROM_REMOTE_REPOSITORY ->
                  Extensions.ComponentUpdateStatus.UPDATE_AVAILABLE;
              case UNKNOWN -> Extensions.ComponentUpdateStatus.UNKNOWN;
            };
        return component.withUpdateStatus(status, availability.latestVersionTimestamp());
      } catch (RuntimeException e) {
        Logging.INSTANCE.warn(
            "Unable to establish update status for component " + component.id(), e);
        return component.withUpdateStatus(Extensions.ComponentUpdateStatus.UNKNOWN, 0L);
      }
    }
    return computeDependencyUpdateStatus(component, scope);
  }

  private Extensions.ComponentDescriptor computeDependencyUpdateStatus(
      Extensions.ComponentDescriptor component, Scope scope) {
    if (scope == null || component.sourceServiceId() == null) {
      return component.withUpdateStatus(Extensions.ComponentUpdateStatus.UNKNOWN, 0L);
    }
    var source =
        scope
            .findService(
                ResourcesService.class,
                candidate -> Objects.equals(candidate.serviceId(), component.sourceServiceId()))
            .orElse(null);
    if (source == null) {
      return component.withUpdateStatus(Extensions.ComponentUpdateStatus.UNKNOWN, 0L);
    }
    try {
      var sourceDescriptor =
          source.capabilities(scope).getComponents().stream()
              .filter(candidate -> Objects.equals(candidate.id(), component.id()))
              .filter(candidate -> Objects.equals(candidate.version(), component.version()))
              .findFirst()
              .orElse(null);
      if (sourceDescriptor == null) {
        return component.withUpdateStatus(Extensions.ComponentUpdateStatus.UNKNOWN, 0L);
      }
      return applyDependencySourceStatus(component, sourceDescriptor);
    } catch (RuntimeException e) {
      Logging.INSTANCE.warn(
          "Unable to obtain component update status from service " + component.sourceServiceId(),
          e);
      return component.withUpdateStatus(Extensions.ComponentUpdateStatus.UNKNOWN, 0L);
    }
  }

  static Extensions.ComponentDescriptor applyDependencySourceStatus(
      Extensions.ComponentDescriptor component, Extensions.ComponentDescriptor sourceDescriptor) {
    var latestTimestamp =
        Math.max(sourceDescriptor.timestamp(), sourceDescriptor.latestVersionTimestamp());
    var status =
        sourceDescriptor.updateStatus() == Extensions.ComponentUpdateStatus.UPDATE_AVAILABLE
                || latestTimestamp > component.timestamp()
            ? Extensions.ComponentUpdateStatus.UPDATE_AVAILABLE
            : sourceDescriptor.updateStatus();
    return component.withUpdateStatus(status, latestTimestamp);
  }

  static boolean isInstalledDependencyUpdateAvailable(
      Extensions.ComponentDescriptor dependency, Extensions.ComponentDescriptor sourceDescriptor) {
    return dependency != null
        && sourceDescriptor != null
        && dependency.importType() == Extensions.ComponentImportType.DEPENDENCY
        && Objects.equals(dependency.id(), sourceDescriptor.id())
        && Objects.equals(dependency.version(), sourceDescriptor.version())
        && sourceDescriptor.timestamp() > dependency.timestamp();
  }

  /**
   * Refresh the dependency component providing the requested service when its source service has a
   * newer installation of the same component. Failures are logged and leave (or restore) the
   * current component whenever possible so callers can continue resolution transparently.
   *
   * @return true only when a replacement was installed successfully
   */
  public synchronized boolean refreshDependencyComponentIfAvailable(
      String serviceUrn, Version requiredVersion, Scope scope) {
    var dependency = selectBestComponent(serviceFinder.get(serviceUrn), requiredVersion);
    if (dependency == null
        || dependency.importType() != Extensions.ComponentImportType.DEPENDENCY
        || dependency.sourceServiceId() == null
        || scope == null) {
      return false;
    }
    var source =
        scope
            .findService(
                ResourcesService.class,
                candidate -> Objects.equals(candidate.serviceId(), dependency.sourceServiceId()))
            .orElse(null);
    if (source == null) {
      return false;
    }
    try {
      var sourceDescriptor =
          source.capabilities(scope).getComponents().stream()
              .filter(candidate -> Objects.equals(candidate.id(), dependency.id()))
              .filter(candidate -> Objects.equals(candidate.version(), dependency.version()))
              .findFirst()
              .orElse(null);
      if (!isInstalledDependencyUpdateAvailable(dependency, sourceDescriptor)) {
        return false;
      }

      Logging.INSTANCE.info(
          "Refreshing dependency component "
              + dependency.id()
              + " version "
              + dependency.version()
              + " from service "
              + dependency.sourceServiceId());
      if (replaceDependencyComponent(dependency, sourceDescriptor, source, scope)) {
        Logging.INSTANCE.info(
            "Dependency component "
                + dependency.id()
                + " version "
                + dependency.version()
                + " refreshed successfully from service "
                + dependency.sourceServiceId());
        return true;
      }
      Logging.INSTANCE.warn(
          "Could not refresh dependency component "
              + dependency.id()
              + " version "
              + dependency.version()
              + " from service "
              + dependency.sourceServiceId()
              + "; continuing with the installed component");
    } catch (Throwable t) {
      Logging.INSTANCE.error(
          "Unable to refresh dependency component "
              + dependency.id()
              + " version "
              + dependency.version()
              + " from service "
              + dependency.sourceServiceId()
              + "; continuing with the installed component",
          t);
    }
    return false;
  }

  private boolean replaceDependencyComponent(
      Extensions.ComponentDescriptor dependency,
      Extensions.ComponentDescriptor sourceDescriptor,
      ResourcesService source,
      Scope scope)
      throws Throwable {
    if (dependency.sourceArchive() == null || !dependency.sourceArchive().isFile()) {
      Logging.INSTANCE.warn(
          "Cannot back up dependency component "
              + dependency.id()
              + " because its installed archive is unavailable");
      return false;
    }

    var temporaryDirectory = Files.createTempDirectory("klab-component-refresh-").toFile();
    var stagedDirectory = new File(temporaryDirectory, "staged");
    var backupDirectory = new File(temporaryDirectory, "backup");
    stagedDirectory.mkdirs();
    backupDirectory.mkdirs();
    var archiveName = dependency.sourceArchive().getName();
    var stagedArchive = new File(stagedDirectory, archiveName);
    var backupArchive = new File(backupDirectory, archiveName);
    var stagedAndValidated = false;
    var componentUnloaded = false;
    var updateComplete = false;

    try {
      Files.copy(
          dependency.sourceArchive().toPath(),
          backupArchive.toPath(),
          StandardCopyOption.REPLACE_EXISTING);

      final String mediaType = "application/java-archive";
      var schemata =
          ResourceTransport.INSTANCE.findExportSchemata(
              KlabAsset.KnowledgeClass.COMPONENT, mediaType, null, source, scope);
      if (schemata.isEmpty()) {
        Logging.INSTANCE.warn(
            "No authorized component export schema is available from service "
                + dependency.sourceServiceId());
        return false;
      }
      var componentUrn = dependency.id() + "@" + dependency.version();
      try (var input =
              source.exportAsset(
                  componentUrn,
                  KlabAsset.KnowledgeClass.COMPONENT,
                  mediaType,
                  Parameters.create(),
                  scope);
          var output = new FileOutputStream(stagedArchive)) {
        if (input == null) {
          Logging.INSTANCE.warn(
              "Source service returned no archive for dependency component " + componentUrn);
          return false;
        }
        IOUtils.copy(input, output);
      }
      try (var ignored = new JarFile(stagedArchive)) {
        // Opening the archive validates its ZIP/JAR structure before the installed copy is removed.
      }
      stagedAndValidated = true;

      if (!unloadDependencyComponentForReplacement(dependency)) {
        Logging.INSTANCE.warn(
            "Could not unload dependency component "
                + dependency.id()
                + " version "
                + dependency.version());
        scheduleDependencyUpdate(dependency, sourceDescriptor, stagedArchive);
        return false;
      }
      componentUnloaded = true;

      var installed =
          installComponent(
              stagedArchive,
              null,
              Extensions.ComponentImportType.DEPENDENCY,
              dependency.sourceServiceId(),
              sourceDescriptor.timestamp());
      if (installed != null && installed.getFirst() != null) {
        componentManager.startPlugins();
        updateComplete = true;
        return true;
      }

      restoreDependencyComponent(dependency, backupArchive);
      scheduleDependencyUpdate(dependency, sourceDescriptor, stagedArchive);
      return false;
    } catch (Throwable updateFailure) {
      if (componentUnloaded && !updateComplete) {
        restoreDependencyComponent(dependency, backupArchive);
      }
      if (stagedAndValidated) {
        scheduleDependencyUpdate(dependency, sourceDescriptor, stagedArchive);
      }
      throw updateFailure;
    } finally {
      try {
        org.apache.commons.io.FileUtils.deleteDirectory(temporaryDirectory);
      } catch (IOException cleanupFailure) {
        Logging.INSTANCE.warn(
            "Unable to remove temporary component update directory " + temporaryDirectory,
            cleanupFailure);
      }
    }
  }

  private void scheduleDependencyUpdate(
      Extensions.ComponentDescriptor dependency,
      Extensions.ComponentDescriptor sourceDescriptor,
      File stagedArchive) {
    try {
      var pendingDirectory = pendingUpdatesDirectory();
      pendingDirectory.mkdirs();
      var key = Integer.toUnsignedString(Objects.hash(dependency.id(), dependency.version()), 16);
      var archive = new File(pendingDirectory, key + ".jar");
      var marker = new File(pendingDirectory, key + ".properties");
      Files.copy(stagedArchive.toPath(), archive.toPath(), StandardCopyOption.REPLACE_EXISTING);
      var properties = new Properties();
      properties.setProperty(PENDING_UPDATE_ARCHIVE, archive.getName());
      properties.setProperty(PENDING_UPDATE_COMPONENT, dependency.id());
      properties.setProperty(PENDING_UPDATE_VERSION, dependency.version().toString());
      properties.setProperty(PENDING_UPDATE_SOURCE_SERVICE, dependency.sourceServiceId());
      properties.setProperty(
          PENDING_UPDATE_SOURCE_TIMESTAMP, Long.toString(sourceDescriptor.timestamp()));
      properties.setProperty(PENDING_UPDATE_TARGET_ARCHIVE, dependency.sourceArchive().getName());
      try (var output = Files.newOutputStream(marker.toPath())) {
        properties.store(output, null);
      }
      Logging.INSTANCE.info(
          "Scheduled dependency component "
              + dependency.id()
              + " version "
              + dependency.version()
              + " for update at the next service restart");
    } catch (Throwable schedulingFailure) {
      Logging.INSTANCE.error(
          "Unable to schedule dependency component "
              + dependency.id()
              + " version "
              + dependency.version()
              + " for update at restart",
          schedulingFailure);
    }
  }

  /**
   * Remove a dependency only after PF4J has actually released and deleted its archive. The general
   * unload operation also reports success when it only removed registry metadata, which is useful
   * for administrative removal but would make a failed hot replacement unavailable to the current
   * resolution.
   */
  private boolean unloadDependencyComponentForReplacement(
      Extensions.ComponentDescriptor dependency) {
    if (componentManager == null || componentManager.getPlugin(dependency.id()) == null) {
      return false;
    }
    if (!componentManager.deletePlugin(dependency.id())) {
      return false;
    }
    removeComponentRegistration(dependency);
    saveConfiguration();
    return true;
  }

  private File pendingUpdatesDirectory() {
    return pendingUpdatesDirectory(pluginPath);
  }

  static File pendingUpdatesDirectory(File pluginPath) {
    return new File(pluginPath.getParentFile(), PENDING_UPDATES_DIRECTORY);
  }

  private Map<String, PendingDependencyUpdate> applyPendingDependencyUpdates() {
    return applyPendingDependencyUpdates(pluginPath);
  }

  static Map<String, PendingDependencyUpdate> applyPendingDependencyUpdates(File pluginPath) {
    var ret = new HashMap<String, PendingDependencyUpdate>();
    var pendingDirectory = pendingUpdatesDirectory(pluginPath);
    var markers =
        pendingDirectory.listFiles(file -> file.isFile() && file.getName().endsWith(".properties"));
    if (markers == null) {
      return ret;
    }
    for (var marker : markers) {
      try {
        var properties = new Properties();
        try (var input = Files.newInputStream(marker.toPath())) {
          properties.load(input);
        }
        var archive = new File(pendingDirectory, properties.getProperty(PENDING_UPDATE_ARCHIVE));
        var targetArchive = properties.getProperty(PENDING_UPDATE_TARGET_ARCHIVE);
        var update =
            new PendingDependencyUpdate(
                properties.getProperty(PENDING_UPDATE_COMPONENT),
                Version.create(properties.getProperty(PENDING_UPDATE_VERSION)),
                properties.getProperty(PENDING_UPDATE_SOURCE_SERVICE),
                Long.parseLong(properties.getProperty(PENDING_UPDATE_SOURCE_TIMESTAMP)),
                archive,
                marker);
        if (!archive.isFile() || targetArchive == null || targetArchive.isBlank()) {
          Logging.INSTANCE.warn("Ignoring incomplete pending component update " + marker);
          continue;
        }
        var target = new File(pluginPath, targetArchive);
        var replacement = new File(pluginPath, targetArchive + ".pending");
        Files.copy(archive.toPath(), replacement.toPath(), StandardCopyOption.REPLACE_EXISTING);
        try {
          Files.move(
              replacement.toPath(),
              target.toPath(),
              StandardCopyOption.ATOMIC_MOVE,
              StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException e) {
          Files.move(replacement.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } finally {
          Utils.Files.deleteQuietly(replacement);
        }
        ret.put(target.toPath().toAbsolutePath().normalize().toString(), update);
        Logging.INSTANCE.info(
            "Applied pending archive for dependency component "
                + update.componentId()
                + " version "
                + update.version()
                + " before plug-in initialization");
      } catch (Throwable updateFailure) {
        Logging.INSTANCE.error(
            "Unable to apply pending dependency component update " + marker, updateFailure);
      }
    }
    return ret;
  }

  private void completePendingDependencyUpdate(PendingDependencyUpdate update) {
    if (update == null) {
      return;
    }
    Utils.Files.deleteQuietly(update.archive());
    Utils.Files.deleteQuietly(update.marker());
    Logging.INSTANCE.info(
        "Completed pending dependency component update for "
            + update.componentId()
            + " version "
            + update.version());
  }

  private void restoreDependencyComponent(
      Extensions.ComponentDescriptor dependency, File backupArchive) {
    try {
      unloadComponent(dependency.id(), dependency.version());
      var restored =
          installComponent(
              backupArchive,
              null,
              Extensions.ComponentImportType.DEPENDENCY,
              dependency.sourceServiceId(),
              dependency.timestamp());
      if (restored == null || restored.getFirst() == null) {
        Logging.INSTANCE.error(
            "Rollback failed for dependency component "
                + dependency.id()
                + " version "
                + dependency.version());
      } else {
        componentManager.startPlugins();
        Logging.INSTANCE.info(
            "Restored dependency component "
                + dependency.id()
                + " version "
                + dependency.version()
                + " after an unsuccessful refresh");
      }
    } catch (Throwable rollbackFailure) {
      Logging.INSTANCE.error(
          "Rollback failed for dependency component "
              + dependency.id()
              + " version "
              + dependency.version(),
          rollbackFailure);
    }
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

  /**
   * Use the Maven cache to install a component from the nearest updated repository.
   *
   * @param groupId
   * @param artifactId
   * @param version
   * @return
   */
  public Pair<Extensions.ComponentDescriptor, ResourceSet> installMavenComponent(
      String groupId, String artifactId, String version) {

    if (pluginPath == null) {
      return Pair.of(
          null,
          ResourceSet.empty(Notification.error("Component registry has not been initialized")));
    }

    var mavenCoordinates = groupId + ":" + artifactId + ":" + version;
    File file = cache.synchronizeArtifact(groupId, artifactId, version, "component", "kar"); // TODO
    if (file != null && file.exists()) {
      pluginPath.mkdirs();
      file = cache.install(groupId, artifactId, version, pluginPath);
      if (file != null && file.exists()) {
        return installComponent(
            file, mavenCoordinates, Extensions.ComponentImportType.MAVEN, service.serviceId(), 0L);
      }
    }
    return null;
  }

  /**
   * Pass a valid component file (renamed to jar) that must have been already set into the
   * pluginPath to load it into the plugin manager and update all records.
   *
   * @param resourcePath
   * @param mavenCoordinates
   * @return
   */
  public Pair<Extensions.ComponentDescriptor, ResourceSet> installComponent(
      File resourcePath, String mavenCoordinates) {

    return installComponent(
        resourcePath,
        mavenCoordinates,
        mavenCoordinates == null
            ? Extensions.ComponentImportType.FILE
            : Extensions.ComponentImportType.MAVEN,
        service.serviceId(),
        0L);
  }

  private Pair<Extensions.ComponentDescriptor, ResourceSet> installComponent(
      File resourcePath,
      String mavenCoordinates,
      Extensions.ComponentImportType importType,
      String sourceServiceId,
      long sourceTimestamp) {

    if (pluginPath == null) {
      return Pair.of(
          null,
          ResourceSet.empty(Notification.error("Component registry has not been initialized")));
    }
    pluginPath.mkdirs();

    // TODO allow same path with different versions and replacing same version
    var pluginDestination =
        new File(pluginPath, Utils.Files.getFileBaseName(resourcePath) + ".jar");

    // check if we're installing from a different location
    if (!isInPluginDirectory(resourcePath)) {
      try {
        // TODO must unload from componentManager - which may be problematic if anything is using
        // the classes
        Files.copy(
            resourcePath.toPath(), pluginDestination.toPath(), StandardCopyOption.REPLACE_EXISTING);
      } catch (IOException e) {
        Logging.INSTANCE.error(
            "Unable to copy " + resourcePath.getAbsolutePath() + " to " + pluginDestination, e);
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
              KlabAsset.KnowledgeClass.COMPONENT,
              resourcePath.lastModified(),
              false);

      Plugin component = plugin.getPlugin();
      if (component instanceof KlabComponent comp) {
        info =
            registerComponent(
                comp,
                mavenCoordinates,
                pluginDestination,
                importType,
                sourceServiceId,
                sourceTimestamp);
        result.setTimestamp(info.timestamp());
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
      ret = ResourceSet.empty(Notification.error(t.getMessage()));
      Utils.Files.deleteQuietly(pluginDestination);
    }

    return Pair.of(info, ret);
  }

  public List<Extensions.FunctionDescriptor> getFunctionDescriptor(ServiceCall call) {
    return getFunctionDescriptor(call.getUrn(), call.getRequiredVersion());
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
   * @return
   */
  public List<Extensions.FunctionDescriptor> getFunctionDescriptor(String urn, Version version) {
    Extensions.ComponentDescriptor target = selectBestComponent(serviceFinder.get(urn), version);
    if (target == null) {
      return null;
    }
    var ret = target.services().get(urn);
    if (ret != null) {
      return ret;
    }
    ret = target.annotations().get(urn);
    if (ret != null) {
      return ret;
    }
    ret = target.exporters().get(urn);
    if (ret != null) {
      return ret;
    }
    ret = target.importers().get(urn);
    if (ret != null) {
      return ret;
    }
    return null;
  }

  /**
   * Return the Java actor descriptors registered under the requested actor URN. The descriptors are
   * serializable; use {@link #implementation(Extensions.FunctionDescriptor)} for the matching
   * reflective method/class information retained by this registry.
   */
  public List<Extensions.ActorDescriptor> getActorDescriptors(String urn, Version version) {
    Extensions.ComponentDescriptor target = selectBestComponent(actorFinder.get(urn), version);
    if (target == null) {
      return List.of();
    }
    var descriptors = target.actors().get(urn);
    return descriptors == null ? List.of() : List.copyOf(descriptors);
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
  public String registerComponent(File componentJar, String mavenCoordinates) {
    var result = installComponent(componentJar, mavenCoordinates);
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

    return registerComponent(component, mavenCoordinates, pluginFile, null, null, 0L);
  }

  private Extensions.ComponentDescriptor registerComponent(
      KlabComponent component,
      String mavenCoordinates,
      File pluginFile,
      Extensions.ComponentImportType importType,
      String sourceServiceId,
      long sourceTimestamp) {

    // TODO negotiate updates before we open the file.

    var componentName = component.getName();
    var componentVersion = component.getVersion();
    var libraries = new ArrayList<Extensions.LibraryDescriptor>();
    var actors = new ArrayList<Extensions.LibraryDescriptor>();
    var adapters = new ArrayList<AdapterDescriptor>();
    var license = component.getWrapper().getDescriptor().getLicense();
    var description = component.getWrapper().getDescriptor().getPluginDescription();
    var timestamp = sourceTimestamp > 0 ? sourceTimestamp : pluginFile.lastModified();

    var sourceArchive =
        component.getWrapper().getPluginPath() == null
            ? null
            : component.getWrapper().getPluginPath().toFile();
    var permissions =
        license == null ? ResourcePrivileges.PUBLIC : ResourcePrivileges.create(license);

    var existingDescriptor = getExactComponent(componentName, componentVersion);
    if (mavenCoordinates == null && existingDescriptor != null) {
      mavenCoordinates = existingDescriptor.mavenCoordinates();
    }
    if (importType == null) {
      importType =
          existingDescriptor == null
              ? (mavenCoordinates == null
                  ? Extensions.ComponentImportType.FILE
                  : Extensions.ComponentImportType.MAVEN)
              : existingDescriptor.importType();
    }
    if (sourceServiceId == null) {
      sourceServiceId =
          existingDescriptor == null ? service.serviceId() : existingDescriptor.sourceServiceId();
    }
    removeComponentRegistration(componentName, componentVersion);

    scanPackage(
        component,
        Map.of(
            Library.class,
            (annotation, cls) -> registerLibrary((Library) annotation, cls, libraries),
            Actor.class,
            (annotation, cls) -> registerActor((Actor) annotation, cls, actors),
            ResourceAdapter.class,
            (annotation, cls) ->
                registerAdapter(
                    (ResourceAdapter) annotation, cls, componentName, componentVersion, adapters)));

    var componentDescriptor =
        new Extensions.ComponentDescriptor(
            componentName,
            componentVersion,
            description,
            sourceArchive,
            Utils.Files.hash(sourceArchive),
            mavenCoordinates,
            permissions,
            libraries,
            adapters,
            new HashMap<>(),
            new HashMap<>(),
            new HashMap<>(),
            new HashMap<>(),
            new HashMap<>(),
            sourceServiceId,
            timestamp,
            importType,
            importType == Extensions.ComponentImportType.FILE
                    || importType == Extensions.ComponentImportType.DEPENDENCY
                ? Extensions.ComponentUpdateStatus.UP_TO_DATE
                : null,
            importType == Extensions.ComponentImportType.FILE
                    || importType == Extensions.ComponentImportType.DEPENDENCY
                ? timestamp
                : 0L);

    // update catalog
    for (var library : componentDescriptor.libraries()) {
      for (var service : library.services()) {
        serviceFinder.put(service.getFirst().getName(), componentDescriptor);
        componentDescriptor
            .services()
            .computeIfAbsent(service.getFirst().getName(), key -> new ArrayList<>())
            .add(service.getSecond());
      }
      for (var service : library.annotations()) {
        annotationFinder.put(service.getFirst().getName(), componentDescriptor);
        componentDescriptor
            .annotations()
            .computeIfAbsent(service.getFirst().getName(), key -> new ArrayList<>())
            .add(service.getSecond());
      }
      for (var actor : library.actors()) {
        actorFinder.put(actor.getFirst(), componentDescriptor);
        componentDescriptor
            .actors()
            .computeIfAbsent(actor.getFirst(), key -> new ArrayList<>())
            .add(actor.getSecond());
      }
      for (var service : library.exporters()) {
        serviceFinder.put(service.getFirst().getName(), componentDescriptor);
        for (var mediaType : service.getFirst().getMediaTypes()) {
          exporterFinder.put(mediaType, componentDescriptor);
          componentDescriptor
              .exporters()
              .computeIfAbsent(service.getFirst().getName(), key -> new ArrayList<>())
              .add(service.getSecond());
        }
      }
      for (var service : library.importers()) {
        // we need it as a service, too
        serviceFinder.put(service.getFirst().getName(), componentDescriptor);
        for (var mediaType : service.getFirst().getMediaTypes()) {
          importerFinder.put(mediaType, componentDescriptor);
          componentDescriptor
              .importers()
              .computeIfAbsent(service.getFirst().getName(), key -> new ArrayList<>())
              .add(service.getSecond());
        }
      }
    }

    this.components.put(componentName, componentDescriptor);

    saveConfiguration();

    return componentDescriptor;
  }

  private void registerActor(
      Actor annotation, Class<?> cls, List<Extensions.LibraryDescriptor> libraries) {

    String namespacePrefix = (annotation.name() + ".");

    var prototypes = new ArrayList<Pair<ServiceInfo, Extensions.FunctionDescriptor>>();
    var annotations = new ArrayList<Pair<ServiceInfo, Extensions.FunctionDescriptor>>();
    var actors = new ArrayList<Pair<String, Extensions.ActorDescriptor>>();
    var exporters = new ArrayList<Pair<ServiceInfo, Extensions.FunctionDescriptor>>();
    var importers = new ArrayList<Pair<ServiceInfo, Extensions.FunctionDescriptor>>();

    for (Class<?> clss : cls.getClasses()) {
      if (clss.isAnnotationPresent(KlabFunction.class)) {
        var serviceInfo =
            createContextualizerPrototype(
                namespacePrefix, clss.getAnnotation(KlabFunction.class), null);
        prototypes.add(Pair.of(serviceInfo, createFunctionDescriptor(serviceInfo, clss, null)));
      } else if (clss.isAnnotationPresent(Actor.class)) {
        var descriptor =
            createActorDescriptor(clss.getAnnotation(Actor.class), namespacePrefix, clss);
        actors.add(Pair.of(descriptor.urn, descriptor));
      } else if (clss.isAnnotationPresent(KlabAnnotation.class)) {
        var serviceInfo =
            createPrototype(namespacePrefix, clss.getAnnotation(KlabAnnotation.class));
        annotations.add(Pair.of(serviceInfo, createFunctionDescriptor(serviceInfo, clss, null)));
      }
      // TODO class-level still unsupported for extensions that should be available as classes
    }

    // annotated methods
    for (Method method : cls.getDeclaredMethods()) {
      if (Modifier.isPublic(method.getModifiers())
          && method.isAnnotationPresent(KlabFunction.class)) {
        var serviceInfo =
            createContextualizerPrototype(
                namespacePrefix, method.getAnnotation(KlabFunction.class), method);
        prototypes.add(Pair.of(serviceInfo, createFunctionDescriptor(serviceInfo, cls, method)));
      } else if (method.isAnnotationPresent(KlabAnnotation.class)) {
        var serviceInfo =
            createPrototype(namespacePrefix, method.getAnnotation(KlabAnnotation.class));
        annotations.add(Pair.of(serviceInfo, createFunctionDescriptor(serviceInfo, cls, method)));
      } /*else if (method.isAnnotationPresent(Verb.class)) {
          var serviceInfo = createVerbPrototype(namespacePrefix, method.getAnnotation(Verb.class));
          verbs.add(Pair.of(serviceInfo, createFunctionDescriptor(serviceInfo, cls, method)));
        }*/
    }

    libraries.add(
        new Extensions.LibraryDescriptor(
            annotation.name(),
            annotation.description(),
            prototypes,
            annotations,
            actors,
            exporters,
            importers));
  }

  private void registerLibrary(
      Library annotation, Class<?> cls, List<Extensions.LibraryDescriptor> libraries) {

    String namespacePrefix =
        Library.CORE_LIBRARY.equals(annotation.name()) ? "" : (annotation.name() + ".");

    var prototypes = new ArrayList<Pair<ServiceInfo, Extensions.FunctionDescriptor>>();
    var annotations = new ArrayList<Pair<ServiceInfo, Extensions.FunctionDescriptor>>();
    var actors = new ArrayList<Pair<String, Extensions.ActorDescriptor>>();
    var importers = new ArrayList<Pair<ServiceInfo, Extensions.FunctionDescriptor>>();
    var exporters = new ArrayList<Pair<ServiceInfo, Extensions.FunctionDescriptor>>();

    for (Class<?> clss : cls.getClasses()) {
      if (clss.isAnnotationPresent(KlabFunction.class)) {
        var serviceInfo =
            createContextualizerPrototype(
                namespacePrefix, clss.getAnnotation(KlabFunction.class), null);
        prototypes.add(Pair.of(serviceInfo, createFunctionDescriptor(serviceInfo, clss, null)));
      } /*else if (clss.isAnnotationPresent(Verb.class)) {
          var serviceInfo = createVerbPrototype(namespacePrefix, clss.getAnnotation(Verb.class));
          verbs.add(Pair.of(serviceInfo, createFunctionDescriptor(serviceInfo, clss, null)));
        } */ else if (clss.isAnnotationPresent(KlabAnnotation.class)) {
        var serviceInfo =
            createPrototype(namespacePrefix, clss.getAnnotation(KlabAnnotation.class));
        annotations.add(Pair.of(serviceInfo, createFunctionDescriptor(serviceInfo, clss, null)));
      } else if (clss.isAnnotationPresent(Actor.class)) {
        var descriptor =
            createActorDescriptor(clss.getAnnotation(Actor.class), namespacePrefix, clss);
        actors.add(Pair.of(descriptor.urn, descriptor));
      }
    }

    // annotated methods
    for (Method method : cls.getDeclaredMethods()) {
      if (Modifier.isPublic(method.getModifiers())
          && method.isAnnotationPresent(KlabFunction.class)) {
        var serviceInfo =
            createContextualizerPrototype(
                namespacePrefix, method.getAnnotation(KlabFunction.class), method);
        prototypes.add(Pair.of(serviceInfo, createFunctionDescriptor(serviceInfo, cls, method)));
      } else if (method.isAnnotationPresent(KlabAnnotation.class)) {
        var serviceInfo =
            createPrototype(namespacePrefix, method.getAnnotation(KlabAnnotation.class));
        annotations.add(Pair.of(serviceInfo, createFunctionDescriptor(serviceInfo, cls, method)));
      } /*else if (method.isAnnotationPresent(Verb.class)) { // no verbs in libraries
          var serviceInfo = createVerbPrototype(namespacePrefix, method.getAnnotation(Verb.class));
          verbs.add(Pair.of(serviceInfo, createFunctionDescriptor(serviceInfo, cls, method)));
        }*/ else if (method.isAnnotationPresent(Importer.class)) {
        var serviceInfo = createPrototype(namespacePrefix, method.getAnnotation(Importer.class));
        importers.add(Pair.of(serviceInfo, createFunctionDescriptor(serviceInfo, cls, method)));
        ResourceTransport.INSTANCE.registerImportSchema(serviceInfo);
      } else if (method.isAnnotationPresent(Exporter.class)) {
        var serviceInfo = createPrototype(namespacePrefix, method.getAnnotation(Exporter.class));
        exporters.add(Pair.of(serviceInfo, createFunctionDescriptor(serviceInfo, cls, method)));
        ResourceTransport.INSTANCE.registerExportSchema(serviceInfo);
      }
    }

    libraries.add(
        new Extensions.LibraryDescriptor(
            annotation.name(),
            annotation.description(),
            prototypes,
            annotations,
            actors,
            exporters,
            importers));
  }

  private Extensions.ActorDescriptor createActorDescriptor(
      Actor actor, String namespacePrefix, Class<?> cls) {

    var ret = new Extensions.ActorDescriptor();
    ret.urn = namespacePrefix + actor.name();
    ret.description = actor.description();
    ret.javaClassName = cls.getName();

    // annotated methods
    for (Method method : cls.getDeclaredMethods()) {
      if (Modifier.isPublic(method.getModifiers())
          && method.isAnnotationPresent(Verb.class)) { // no verbs in libraries
        var serviceInfo = createVerbPrototype(namespacePrefix, method.getAnnotation(Verb.class));
        ret.verbs.add(createFunctionDescriptor(serviceInfo, cls, method));
      } else if (method.isAnnotationPresent(AgentAdapter.class)) {
        var serviceInfo = createAgentAdapterPrototype(ret.urn, method);
        var adapter = createFunctionDescriptor(serviceInfo, cls, method);
        if (!Modifier.isPublic(method.getModifiers())
            || method.getReturnType() == void.class
            || adapterSourceParameterCount(method) != 1
            || adapterScopeParameterCount(method) > 1) {
          adapter.error = true;
          Logging.INSTANCE.error(
              "Invalid @AgentAdapter method "
                  + cls.getName()
                  + "."
                  + method.getName()
                  + ": it must be public, return a value, and accept exactly one source value"
                  + " plus at most one RuntimeAgent.Scope");
        }
        if (ret.adapter == null) {
          ret.adapter = adapter;
        } else {
          ret.adapter.error = true;
          adapter.error = true;
          Logging.INSTANCE.error("Actor " + ret.urn + " declares more than one @AgentAdapter");
        }
      }
    }

    return ret;
  }

  private ServiceInfoImpl createAgentAdapterPrototype(String actorUrn, Method method) {
    var ret = new ServiceInfoImpl();
    ret.setName(actorUrn + ".@adapter." + method.getName());
    ret.setDescription("Adapt an object to actor " + actorUrn);
    ret.setFunctionType(ServiceInfo.FunctionType.VERB);
    return ret;
  }

  private int adapterSourceParameterCount(Method method) {
    int ret = 0;
    for (var parameter : method.getParameterTypes()) {
      if (!org.integratedmodelling.klab.api.actors.RuntimeAgent.Scope.class.isAssignableFrom(
          parameter)) {
        ret++;
      }
    }
    return ret;
  }

  private int adapterScopeParameterCount(Method method) {
    int ret = 0;
    for (var parameter : method.getParameterTypes()) {
      if (org.integratedmodelling.klab.api.actors.RuntimeAgent.Scope.class.isAssignableFrom(
          parameter)) {
        ret++;
      }
    }
    return ret;
  }

  /**
   * Invoke the adapter advertised by a Java actor descriptor. The source argument is matched by
   * runtime type and an optional {@link org.integratedmodelling.klab.api.actors.RuntimeAgent.Scope}
   * parameter is injected in any position. Supplier adapters are joined before returning.
   */
  public Object invokeAgentAdapter(
      Extensions.ActorDescriptor actor,
      Object source,
      org.integratedmodelling.klab.api.actors.RuntimeAgent.Scope scope) {
    if (actor == null || actor.adapter == null || actor.adapter.error) {
      throw new KlabIllegalStateException(
          "Actor " + (actor == null ? "<unknown>" : actor.urn) + " has no usable adapter");
    }
    var implementation = implementation(actor.adapter);
    if (implementation == null || implementation.method == null) {
      throw new KlabIllegalStateException("No implementation is available for actor " + actor.urn);
    }
    Method method = implementation.method;
    Object target = null;
    if (!java.lang.reflect.Modifier.isStatic(method.getModifiers())) {
      target = implementation.mainClassInstance;
      if (target == null) {
        try {
          target = implementation.implementation.getDeclaredConstructor().newInstance();
          implementation.mainClassInstance = target;
        } catch (ReflectiveOperationException failure) {
          throw new KlabIllegalStateException(
              new ReflectiveOperationException(
                  "Cannot instantiate adapter owner " + implementation.implementation.getName(),
                  failure));
        }
      }
    }
    var arguments = new Object[method.getParameterCount()];
    boolean sourceMatched = false;
    for (int i = 0; i < method.getParameterCount(); i++) {
      Class<?> parameter = method.getParameterTypes()[i];
      if (org.integratedmodelling.klab.api.actors.RuntimeAgent.Scope.class.isAssignableFrom(
          parameter)) {
        arguments[i] = scope;
      } else if (!sourceMatched && acceptsAdapterSource(parameter, source)) {
        arguments[i] = source;
        sourceMatched = true;
      } else {
        throw new KlabIllegalArgumentException(
            "Cannot match "
                + (source == null ? "null" : source.getClass().getName())
                + " to @AgentAdapter parameter "
                + parameter.getName());
      }
    }
    if (!sourceMatched) {
      throw new KlabIllegalArgumentException(
          "@AgentAdapter " + method + " does not accept the source value");
    }
    try {
      Object result = method.invoke(target, arguments);
      return result instanceof java.util.concurrent.CompletableFuture<?> future
          ? future.join()
          : result;
    } catch (InvocationTargetException failure) {
      Throwable cause = failure.getTargetException();
      if (cause instanceof RuntimeException runtimeException) {
        throw runtimeException;
      }
      throw new KlabIllegalStateException(cause);
    } catch (ReflectiveOperationException failure) {
      throw new KlabIllegalStateException(
          new ReflectiveOperationException(
              "Cannot invoke adapter for actor " + actor.urn, failure));
    }
  }

  private boolean acceptsAdapterSource(Class<?> parameter, Object source) {
    if (source == null) {
      return !parameter.isPrimitive();
    }
    if (parameter.isInstance(source)) {
      return true;
    }
    return parameter.isPrimitive()
        && switch (parameter.getName()) {
          case "boolean" -> source instanceof Boolean;
          case "byte" -> source instanceof Byte;
          case "short" -> source instanceof Short;
          case "int" -> source instanceof Integer;
          case "long" -> source instanceof Long;
          case "float" -> source instanceof Float;
          case "double" -> source instanceof Double;
          case "char" -> source instanceof Character;
          default -> false;
        };
  }

  /**
   * Negotiate Java verb parameters that cannot be matched positionally. Future component-aware
   * implementations may split or combine values (for example, a temporal quantity into a numeric
   * value and {@link java.util.concurrent.TimeUnit}) and return them in declaration order.
   *
   * <p>The default implementation deliberately rejects the match. The compiler converts this into a
   * lexical parameter-mismatch notification and runtime dynamic calls fail explicitly.
   */
  public List<Object> negotiateAgentParameters(
      List<Class<?>> unmatchedParameterTypes, List<?> suppliedParameters) {
    return null;
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
      var verb = method.getAnnotation(Verb.class);
      if (verb != null && !verb.producesAgent().isBlank()) {
        ret.behaviorUrn = verb.producesAgent().trim();
      }
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
          // that's actually OK in many situations. TODO/FIXME revise
          Logging.INSTANCE.warn(
              "Cannot instantiate main class for function library "
                  + implementation(ret).implementation.getCanonicalName()
                  + ": "
                  + e.getMessage());
          //          ret.error = true;
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
   * Find an adapter provided by one of the known services. If the adapter is not present locally
   * and is embeddable, retrieve the component it's in and load it.
   */
  public AdapterDescriptor resolveAdapter(String adapterId, Version version, Scope scope) {

    var existing = getAdapter(adapterId, version, scope);
    if (existing != null) {
      return existing.getAdapterInfo();
    }

    return null;
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

    Adapter ret = null;
    for (var adapter : adapters.get(urn)) {
      if (version == null || adapter.getVersion().compatible(version)) {
        if (ret == null || adapter.getVersion().greater(ret.getVersion())) {
          ret = adapter;
        }
      }
    }
    return ret;
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
      ResourceAdapter annotation,
      Class<?> cls,
      String componentUrn,
      Version componentVersion,
      List<AdapterDescriptor> adapters) {

    /** Do not load adapters that aren't embeddable unless we are a resources service. */
    if (this.service.serviceType() != KlabService.Type.RESOURCES && !annotation.embeddable()) {
      return;
    }
    try {
      var adapter = new AdapterImpl(cls, annotation, componentUrn, componentVersion);
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
    return selectBestComponent(components.get(urn), version);
  }

  public synchronized boolean unloadComponent(String urn, Version version) {
    var component = getComponent(urn, version);
    if (component != null) {
      var ret = false;
      if (componentManager != null && componentManager.getPlugin(component.id()) != null) {
        ret = componentManager.deletePlugin(component.id());
      }
      ret |= removeComponentRegistration(component);
      saveConfiguration();
      return ret;
    }
    return false;
  }

  /**
   * Load any component in the passed resource set that is not already present.
   *
   * @param resourceSet
   * @return
   */
  public synchronized boolean loadComponents(ResourceSet resourceSet, Scope scope) {

    var missingComponents =
        resourceSet.getResults().stream()
            .filter(resource -> resource.getKnowledgeClass() == KlabAsset.KnowledgeClass.COMPONENT)
            .filter(
                resource -> {
                  var split = Version.splitVersion(resource.getResourceUrn());
                  var components = this.components.get(split.getFirst());
                  if (components == null) {
                    return true;
                  }
                  return components.stream()
                      .noneMatch(component -> component.version().compatible(split.getSecond()));
                })
            .toList();

    for (var result : missingComponents) {

      // load from service
      var service =
          scope
              .findService(
                  ResourcesService.class, s -> Objects.equals(s.serviceId(), result.getServiceId()))
              .orElse(null);

      if (service == null) {
        return false;
      }

      final String mediaType = "application/java-archive";
      var schemata =
          ResourceTransport.INSTANCE.findExportSchemata(
              KlabAsset.KnowledgeClass.COMPONENT, mediaType, null, service, scope);
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

      File plugin = new File(pluginPath, result.getResourceUrn() + ".jar");
      try (var input =
              service.exportAsset(
                  result.getResourceUrn(),
                  KlabAsset.KnowledgeClass.COMPONENT,
                  mediaType,
                  Parameters.create(),
                  scope);
          var output = new FileOutputStream(plugin)) {
        IOUtils.copy(input, output);
        // give the OS time to react - found that often the file is truncated
        TimeUnit.SECONDS.sleep(2);
      } catch (Exception e) {
        scope.error(e);
        return false;
      }
      installComponent(
          plugin,
          null,
          Extensions.ComponentImportType.DEPENDENCY,
          result.getServiceId(),
          result.getTimestamp());
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

    var ret = new ServiceInfoImpl();

    // TODO finish the descriptor - needs fires/returns, type, etc.
    // TODO needs arguments

    ret.setName(annotation.name());
    ret.setDescription(annotation.description());
    ret.setFunctionType(ServiceInfo.FunctionType.VERB);

    return ret;
  }

  private ServiceInfoImpl createContextualizerPrototype(
      String namespacePrefix, KlabFunction annotation, Method method) {

    var ret = new ServiceInfoImpl();

    ret.setName(namespacePrefix + annotation.name());
    ret.setDescription(annotation.description());
    ret.setFilter(annotation.filter());
    ret.setGeometry(
        annotation.geometry().isEmpty() ? null : Geometry.create(annotation.geometry()));
    ret.setLabel(annotation.dataflowLabel());
    ret.setReentrant(annotation.reentrant());
    ret.setFunctionType(ServiceInfo.FunctionType.FUNCTION);

    var distribution = new Data.ShardingStrategy();
    distribution.setCurve(annotation.fillCurve());
    distribution.setMaxBufferSize(annotation.maxSize());
    distribution.setMinSplitSize(annotation.minSizeForSplitting());
    distribution.setSuggestedSplits(annotation.split());
    distribution.setDataType(Storage.Type.defaultFor(annotation.type()));
    ret.setShardingStrategy(distribution);
    ret.getType().add(annotation.type());

    for (KlabFunction.Argument argument : annotation.parameters()) {
      var arg = createArgument(argument);
      ret.getArguments().put(arg.getName(), arg);
    }
    for (KlabFunction.Output argument : annotation.outputs()) {
      var arg = createArgument(argument);
      ret.getInputs().add(arg);
    }
    for (KlabFunction.Input argument : annotation.inputs()) {
      var arg = createArgument(argument);
      ret.getOutputs().add(arg);
    }

    if (method != null) {
      /*
       * Scan method parameters for Input/Output annotations and add to parameters if not already
       * present.
       */
      for (var parameter : method.getParameters()) {
        if (parameter.isAnnotationPresent(KlabFunction.Input.class)) {
          var def = parameter.getAnnotation(KlabFunction.Input.class);
          var name = def.name().isEmpty() ? parameter.getName() : def.name();
          if (ret.getInputs().stream().noneMatch(a -> a.getName().equals(name))) {
            var arg = new ServiceInfoImpl.ArgumentImpl();
            arg.setName(name);
            arg.setDescription(def.description());
            arg.setOptional(false);
            arg.setObservableUrn(null);
            for (Artifact.Type a : def.type()) {
              arg.getType().add(a);
            }
            if (arg.getType().isEmpty()) {
              // TODO infer type from parameter unless the type is specified
            }
            arg.getTags().add(ServiceInfo.Tag.INPUT);
            ret.getInputs().add(arg);
          }
        } else if (parameter.isAnnotationPresent(KlabFunction.Output.class)) {
          var def = parameter.getAnnotation(KlabFunction.Output.class);
          var name = def.name().isEmpty() ? parameter.getName() : def.name();
          if (ret.getInputs().stream().noneMatch(a -> a.getName().equals(name))) {
            var arg = new ServiceInfoImpl.ArgumentImpl();
            arg.setName(name);
            arg.setDescription(def.description());
            arg.setOptional(false);
            arg.setObservableUrn(null);
            for (Artifact.Type a : def.type()) {
              arg.getType().add(a);
            }
            arg.getTags().add(ServiceInfo.Tag.OUTPUT);
            if (arg.getType().isEmpty()) {
              // TODO infer type from parameter unless the type is specified
            }
            ret.getOutputs().add(arg);
          }
        }
      }
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
    if (annotation.geometry() != null) {
      ret.setGeometry(Geometry.create(annotation.geometry()));
    }
    if (annotation.fillCurve() != null) {
      var distribution = new Data.ShardingStrategy();
      distribution.setCurve(annotation.fillCurve());
      ret.setShardingStrategy(distribution);
    }
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

  private ServiceInfoImpl.ArgumentImpl createArgument(KlabFunction.Input argument) {
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

  private ServiceInfoImpl.ArgumentImpl createArgument(KlabFunction.Output argument) {
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
    var adapters = new ArrayList<AdapterDescriptor>();
    var actors = new ArrayList<Extensions.LibraryDescriptor>();

    scanPackage(
        packageName,
        Map.of(
            Library.class,
            (annotation, cls) -> registerLibrary((Library) annotation, cls, libraries),
            Actor.class,
            (annotation, cls) -> registerActor((Actor) annotation, cls, actors),
            ResourceAdapter.class,
            (annotation, cls) ->
                registerAdapter(
                    (ResourceAdapter) annotation,
                    cls,
                    Extensions.LOCAL_SERVICE_COMPONENT,
                    Version.CURRENT_VERSION,
                    adapters)));

    localComponentDescriptor.libraries().addAll(libraries);
    localComponentDescriptor.adapters().addAll(adapters);

    // update catalog
    for (var library : localComponentDescriptor.libraries()) {
      for (var service : library.services()) {
        serviceFinder.put(service.getFirst().getName(), localComponentDescriptor);
        localComponentDescriptor
            .services()
            .computeIfAbsent(service.getFirst().getName(), key -> new ArrayList<>())
            .add(service.getSecond());
      }
      // we need these to be findable by URN
      for (var service : library.importers()) {
        serviceFinder.put(service.getFirst().getName(), localComponentDescriptor);
        localComponentDescriptor
            .services()
            .computeIfAbsent(service.getFirst().getName(), key -> new ArrayList<>())
            .add(service.getSecond());
      }
      for (var actor : library.actors()) {
        actorFinder.put(actor.getFirst(), localComponentDescriptor);
        localComponentDescriptor
            .actors()
            .computeIfAbsent(actor.getFirst(), key -> new ArrayList<>())
            .add(actor.getSecond());
      }
      // we need these to be findable by URN as well, dio carciofo
      for (var service : library.exporters()) {
        serviceFinder.put(service.getFirst().getName(), localComponentDescriptor);
        localComponentDescriptor
            .services()
            .computeIfAbsent(service.getFirst().getName(), key -> new ArrayList<>())
            .add(service.getSecond());
      }
      for (var service : library.annotations()) {
        annotationFinder.put(service.getFirst().getName(), localComponentDescriptor);
        localComponentDescriptor
            .annotations()
            .computeIfAbsent(service.getFirst().getName(), key -> new ArrayList<>())
            .add(service.getSecond());
      }
    }

    this.components.put(Extensions.LOCAL_SERVICE_COMPONENT, localComponentDescriptor);
  }

  /**
   * Use this call for the "master" service that installs components based on configuration.
   *
   * @param configuration
   * @param pluginPath
   */
  public void initializeComponents(ResourcesConfiguration configuration, File pluginPath) {

    //    /*
    //    TODO check all existing resources against the configuration; retrieve whatever needs
    // updating;
    //     remove anything not configured or deprecated; check integrity and certification for all
    // components
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
   * @param pluginRoot the component repository root. Loadable archives are stored in its {@code
   *     plugins} subdirectory.
   */
  public void initializeComponents(File pluginRoot) {
    this.pluginPath = getPluginDirectory(pluginRoot);
    this.pluginPath.mkdirs();
    migrateExistingRootPlugins(pluginRoot, this.pluginPath);
    // Pending archives were downloaded during an earlier resolution and require no source service
    // here; remote services may not be visible yet during component initialization.
    var pendingDependencyUpdates = applyPendingDependencyUpdates();
    this.componentManager = new DefaultPluginManager(this.pluginPath.toPath());
    this.componentManager.setSystemVersion(Version.CURRENT);
    this.componentManager.loadPlugins();
    // TODO configuration
    for (var wrapper : this.componentManager.getPlugins()) {
      Plugin plugin = wrapper.getPlugin();
      if (plugin instanceof KlabComponent component) {
        var file = component.getWrapper().getPluginPath().toFile();
        var pending =
            pendingDependencyUpdates.get(file.toPath().toAbsolutePath().normalize().toString());
        if (pending == null) {
          registerComponent(component, null, file);
        } else {
          var registered =
              registerComponent(
                  component,
                  null,
                  file,
                  Extensions.ComponentImportType.DEPENDENCY,
                  pending.sourceServiceId(),
                  pending.sourceTimestamp());
          if (registered != null
              && Objects.equals(registered.id(), pending.componentId())
              && Objects.equals(registered.version(), pending.version())) {
            completePendingDependencyUpdate(pending);
          } else {
            Logging.INSTANCE.error(
                "Pending dependency update did not contain expected component "
                    + pending.componentId()
                    + " version "
                    + pending.version());
          }
        }
      }
    }

    this.componentManager.addPluginStateListener(
        new PluginStateListener() {
          @Override
          public void pluginStateChanged(PluginStateEvent event) {
            System.out.println("HOLA! Plugin state: " + event);
          }
        });

    if (startupOptions != null && startupOptions.isComponentUpdateOnStartup()) {
      var result = updateMavenSnapshotComponents();
      Logging.INSTANCE.notifications(result.getNotifications().toArray(new Notification[0]));
    }
    if (startupOptions != null && startupOptions.isComponentAutoUpdateEnabled()) {
      var interval = Math.max(1, startupOptions.getComponentUpdateIntervalMinutes());
      scheduler.scheduleAtFixedRate(
          () -> runScheduledComponentUpdate(), interval, interval, TimeUnit.MINUTES);
    }
  }

  private synchronized void runScheduledComponentUpdate() {
    try {
      updateMavenSnapshotComponents(false);
    } catch (Throwable t) {
      Logging.INSTANCE.error("Scheduled component update failed", t);
    }
  }

  private void migrateExistingRootPlugins(File pluginRoot, File pluginDirectory) {
    var existingRootPlugins =
        pluginRoot.listFiles(
            file -> file.isFile() && "jar".equalsIgnoreCase(Utils.Files.getFileExtension(file)));
    if (existingRootPlugins == null) {
      return;
    }
    for (var existingPlugin : existingRootPlugins) {
      var target = new File(pluginDirectory, existingPlugin.getName());
      if (target.exists()) {
        Logging.INSTANCE.warn(
            "Ignoring root-level component plugin "
                + existingPlugin.getAbsolutePath()
                + " because "
                + target.getAbsolutePath()
                + " already exists");
        continue;
      }
      try {
        Files.move(existingPlugin.toPath(), target.toPath());
      } catch (IOException e) {
        Logging.INSTANCE.warn(
            "Unable to migrate existing component plugin "
                + existingPlugin.getAbsolutePath()
                + " to "
                + target.getAbsolutePath(),
            e);
      }
    }
  }

  public class AdapterImpl implements Adapter {

    private final String name;
    private final int splits;
    private final Data.FillCurve fillCurve;
    private final long minSplitSize;
    private Set<Artifact.Type> resourceType = EnumSet.noneOf(Artifact.Type.class);
    private final Version version;
    boolean universal;
    boolean threadSafe;
    boolean embeddable;
    Class<?> implementationClass;
    Object implementation;
    Set<ResourceAdapter.Validator.LifecyclePhase> validationPhases =
        EnumSet.noneOf(ResourceAdapter.Validator.LifecyclePhase.class);
    private Extensions.FunctionDescriptor typeAttributor;
    private Extensions.FunctionDescriptor encoder;
    private Extensions.FunctionDescriptor contextualizer;
    private Map<ResourceAdapter.Validator.LifecyclePhase, Extensions.FunctionDescriptor> validator =
        new HashMap<>();
    private Extensions.FunctionDescriptor inspector;
    private Extensions.FunctionDescriptor initializer;
    private Extensions.FunctionDescriptor sanitizer;
    private Extensions.FunctionDescriptor publisher;
    private List<Extensions.FunctionDescriptor> batchAdapters = new ArrayList<>();
    private List<Adapter.Parameter> parameters = new ArrayList<>();
    private final AdapterDescriptor adapterInfo;
    private String componentUrn;
    private Version componentVersion;

    public AdapterImpl(
        Class<?> implementationClass,
        ResourceAdapter annotation,
        String componentUrn,
        Version componentVersion) {

      this.name = annotation.name();
      this.version = Version.create(annotation.version());
      this.universal = annotation.universal();
      this.threadSafe = annotation.threadSafe();
      this.embeddable = annotation.embeddable();
      this.componentUrn = componentUrn;
      this.componentVersion = componentVersion;
      this.splits = annotation.splits();
      this.fillCurve = annotation.fillCurve();
      this.minSplitSize = annotation.minSizeForSplitting();

      if (annotation.type() != Artifact.Type.VOID) {
        this.resourceType.add(annotation.type());
      }
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
      for (var parameter : annotation.parameters()) {
        this.parameters.add(
            new ParameterImpl(
                parameter.name(),
                parameter.description(),
                parameter.optional(),
                parameter.type(),
                parameter.enumValues()));
      }
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
      return this.resourceType.isEmpty() ? null : this.resourceType.iterator().next();
    }

    @Override
    public boolean isBatchable() {
      return batchAdapters.size() > 0;
    }

    @Override
    public Version getVersion() {
      return this.version;
    }

    @Override
    public Version getComponentVersion() {
      return this.componentVersion;
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
    public boolean hasValidator(ResourceAdapter.Validator.LifecyclePhase phase) {
      return validator.containsKey(phase);
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
    public List<Extensions.FunctionDescriptor> getBatchAdapters() {
      return batchAdapters;
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
    public Extensions.FunctionDescriptor getValidator(
        ResourceAdapter.Validator.LifecyclePhase phase) {
      return this.validator.get(phase);
    }

    @Override
    public List<Parameter> getParameters() {
      return this.parameters;
    }

    @Override
    public String getComponentUrn() {
      return this.componentUrn;
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
    public boolean isEmbeddable() {
      return embeddable;
    }

    public void setEmbeddable(boolean embeddable) {
      this.embeddable = embeddable;
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
    public AdapterDescriptor getAdapterInfo() {
      return this.adapterInfo;
    }

    @Override
    public boolean validate(
        Resource resource, Scope scope, ResourceAdapter.Validator.LifecyclePhase phase) {
      var validator = getValidator(phase);
      if (validator != null) {
        var implementation = implementation(validator);
        if (implementation != null) {
          try {
            var ret =
                executeMethod(
                    implementation,
                    resource,
                    null,
                    null,
                    null,
                    null,
                    Urn.of(resource.getUrn()),
                    resource.getParameters(),
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    null,
                    scope);

            if (ret instanceof Boolean) {
              return (Boolean) ret;
            } else if (ret instanceof Notification notification) {
              scope.send(notification);
              return notification.getLevel().severity <= Notification.Level.Warning.severity;
            }
            return true;

          } catch (Throwable e) {
            scope.error(
                "Validation of "
                    + resource.getUrn()
                    + " in phase "
                    + phase
                    + " failed: "
                    + e.getMessage(),
                e);
            return false;
          }
        }
      }
      return true;
    }

    @Override
    public boolean encode(
        Resource resource,
        Geometry geometry,
        Scheduler.Event event,
        Data.Builder builder,
        Storage.Scanner scanner,
        Observation observation,
        Observable observable,
        Urn urn,
        Parameters<String> urnParameters,
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
                scanner,
                null,
                null,
                null,
                null,
                event,
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

    private AdapterDescriptor scanAdapterClass(Class<?> adapterClass) {

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
          for (var phase : a.phase()) {
            this.validator.put(phase, funcData.getFirst());
          }
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
          var serviceInfo = createPrototype(name + ".", method.getAnnotation(Importer.class));
          var schema = ResourceTransport.INSTANCE.registerImportSchema(serviceInfo);
          schema.setAdapter(name);
          importSchemata.add(schema);
          serviceImplementations.put(
              schema.getSchemaId(),
              createServiceImplementation(method, method.getAnnotation(Importer.class))
                  .getSecond());
        } else if (method.isAnnotationPresent(Exporter.class)) {
          var serviceInfo = createPrototype(name + ".", method.getAnnotation(Exporter.class));
          var schema = ResourceTransport.INSTANCE.registerExportSchema(serviceInfo);
          schema.setAdapter(name);
          exportSchemata.add(schema);
          serviceImplementations.put(
              schema.getSchemaId(),
              createServiceImplementation(method, method.getAnnotation(Exporter.class))
                  .getSecond());
        } else if (method.isAnnotationPresent(ResourceAdapter.BatchIngestor.class)) {
          var funcData =
              createServiceImplementation(
                  method, method.getAnnotation(ResourceAdapter.BatchIngestor.class));
          serviceImplementations.put(
              funcData.getFirst().serviceInfo.getName(), funcData.getSecond());
          this.batchAdapters.add(funcData.getFirst());
        }
      }

      if (this.encoder == null) {
        throw new KlabIllegalStateException(
            "Cannot load adapter " + name + ": missing encoder method");
      }
      if ((this.resourceType == null || this.resourceType.isEmpty()) && typeAttributor == null) {
        throw new KlabIllegalStateException(
            "Cannot load adapter "
                + name
                + ": missing type attribution in annotation or "
                + "methods");
      }

      return new AdapterDescriptor(
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
          isEmbeddable(),
          isBatchable(),
          fillCurve,
          splits,
          minSplitSize,
          validations,
          importSchemata,
          exportSchemata,
          this.parameters);
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
      Storage.Scanner scanner,
      Expression expression,
      LookupTable lookupTable,
      Data inputData,
      Data.ShardingStrategy shardingStrategy,
      Scheduler.Event schedulerEvent,
      Scope scope) {

    // TODO use the builder to match inputs/outputs to scanners. This requires the prototype for
    // functions and adapters

    var arguments =
        ArgumentMatcher.matchArguments(
            implementation.method,
            resource,
            geometry,
            builder,
            observation,
            observable,
            urn,
            urnParameters,
            serviceCall,
            scanner,
            expression,
            lookupTable,
            inputData,
            schedulerEvent,
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
}
