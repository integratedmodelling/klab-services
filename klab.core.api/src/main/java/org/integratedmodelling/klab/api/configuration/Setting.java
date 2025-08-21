package org.integratedmodelling.klab.api.configuration;

import org.integratedmodelling.klab.api.engine.distribution.Product;

import java.io.File;
import java.util.Map;

/**
 * Settings for all products (engine, modeler, services, user etc.) that can be changed at runtime
 * through the CLI or the API. The {@link Settings} class implements the logic for storage and
 * retrieval.
 *
 * <p>By convention, any setting whose value class is {@link java.util.Map} works as an "action":
 * when that is set, the accompanying data map are the parameters to be sent. If the map contains a
 * "result" field, the type of the result sets the return value of the operation.
 */
public enum Setting {
  POLLING(
      Page.SERVICES,
      "Enable or disable server polling in all service clients",
      Boolean.class,
      true),
  POLLING_INTERVAL_LOCAL(
      Page.SERVICES,
      "Set the service polling interval for local services in seconds",
      Integer.class,
      5),
  POLLING_INTERVAL_REMOTE(
      Page.SERVICES,
      "Set the service polling interval for remote services in seconds",
      Integer.class,
      20),
  NOTIFICATIONS_CACHED(Page.APPEARANCE, "Number of notifications to keep", Integer.class, 100),
  WORK_DIRECTORY(
      Page.GENERAL,
      "The directory where all k.LAB files are stored",
      File.class,
      new File(System.getProperty("user.home") + File.separator + ".klab")),
  CERTIFICATE_FILE(
      Page.GENERAL,
      "The certificate file to use to connect to the k.LAB network",
      File.class,
      new File(
          System.getProperty("user.home")
              + File.separator
              + ".klab"
              + File.separator
              + "klab.cert")),
  LAUNCH_PRODUCT(
      Page.GENERAL,
      "Launch a local service if there is no online service and a distribution is " + "available",
      Boolean.class,
      false),
  LOG_EVENTS(Page.SERVICES, "Log server-side events", Boolean.class, false),
  LOCAL_ONLY(Page.SERVICES, "Disable use of remote services", Boolean.class, false),
  DETECT_LOCAL_HUB(
      Page.DEBUGGING,
      "Look for a hub running on localhost for authentication",
      Boolean.class,
      false),
  RESET_ALL_SERVICE_CONFIGURATION(
      Page.DEBUGGING,
      "Reset the configuration of all local services and their data",
      Boolean.class,
      false),
  RESET_ALL_SERVICE_DATA(
      Page.DEBUGGING,
      "Remove all data for local services but not their configuration",
      Boolean.class,
      false),
  MAX_RESOLVER_SERVICE_MEMORY(
      Page.RESOLVER,
      "Maximum memory for the local resolver in MB",
      Integer.class,
      Product.ProductType.RESOLVER_SERVICE.defaultMaxMemoryLimitMB()),
  MAX_REASONER_SERVICE_MEMORY(
      Page.REASONER,
      "Maximum memory for the local reasoner in MB",
      Integer.class,
      Product.ProductType.REASONER_SERVICE.defaultMaxMemoryLimitMB()),
  MAX_RUNTIME_SERVICE_MEMORY(
      Page.RUNTIME,
      "Maximum memory for the local runtime in MB",
      Integer.class,
      Product.ProductType.RUNTIME_SERVICE.defaultMaxMemoryLimitMB()),
  REINITIALIZE_DATABASE(
      Page.RUNTIME,
      "Remove all digital twins and re-initialize the knowledge graph",
      Map.class,
      Map.of("result", Boolean.class)),
  CLEAR_RUNTIME_COMPONENTS(
      Page.RUNTIME,
      "Execute to remove one or more components from the service",
      Map.class,
      Map.of("component", String.class, "result", Boolean.class)),
  LOAD_REMOTE_RUNTIME_COMPONENTS(
      Page.RUNTIME,
      "If false, no remote components will be loaded when requested, relying only on those explicitly installed",
      Boolean.class,
      false /* TODO should be TRUE by default */),
  MAX_RESOURCES_SERVICE_MEMORY(
      Page.RESOURCES,
      "Maximum memory for the local resources service in MB",
      Integer.class,
      Product.ProductType.RESOURCES_SERVICE.defaultMaxMemoryLimitMB()),
  MAVEN_SNAPSHOT_CHECK_INTERVAL(
      Page.RESOURCES,
      "Interval in seconds for checking for new Maven snapshot components (0 to disable)",
      Integer.class,
      0),
  GIT_REPOSITORY_CHECK_INTERVAL(
      Page.RESOURCES,
      "Interval in seconds for checking for changes in configured Git repositories (0 to disable)",
      Integer.class,
      0),
  START_RESOURCES_SERVICE_IN_DEBUG_MODE(
      Page.DEBUGGING,
      "Start the local resources service in debug mode on port "
          + Product.ProductType.RESOURCES_SERVICE.getDebugPort(),
      Boolean.class,
      false),
  START_RESOLVER_SERVICE_IN_DEBUG_MODE(
      Page.DEBUGGING,
      "Start the local resolver in debug mode on port "
          + Product.ProductType.RESOLVER_SERVICE.getDebugPort(),
      Boolean.class,
      false),
  START_REASONER_SERVICE_IN_DEBUG_MODE(
      Page.DEBUGGING,
      "Start the local reasoner in debug mode on port "
          + Product.ProductType.REASONER_SERVICE.getDebugPort(),
      Boolean.class,
      false),
  START_RUNTIME_SERVICE_IN_DEBUG_MODE(
      Page.DEBUGGING,
      "Start the local runtime service in debug mode on port "
          + Product.ProductType.RUNTIME_SERVICE.getDebugPort(),
      Boolean.class,
      false),
  CLEAR_WORKSPACE(
      Page.RESOURCES,
      "Execute to remove all workspaces from the service. This is a destructive operation.",
      Map.class,
      Map.of("result", Boolean.class)),
  CLEAR_COMPONENTS(
      Page.RESOURCES,
      "Execute to remove one or more components from the service",
      Map.class,
      Map.of("component", String.class, "result", Boolean.class));
  ;

  //  private Setting<Double> minModelCoverage = new Setting<Double>();
  //  private Setting<Double> minTotalCoverage = new Setting<Double>();
  //  private Setting<Double> minCoverageImprovement = new Setting<Double>();

  //  private Setting<Boolean> startWithCLI = new Setting<Boolean>();
  //  private Setting<Boolean> detectLocalHub = new Setting<Boolean>();
  //  private Setting<Boolean> resetAllBuilds = new Setting<Boolean>();
  //  // private Setting<Boolean> resetAllBuildsButLatest = new Setting<Boolean>();
  //  private Setting<Boolean> updateAutomatically = new Setting<Boolean>();
  //  private Setting<Boolean> resetKnowledge = new Setting<Boolean>();
  //  private Setting<Boolean> resetModelerWorkspace = new Setting<Boolean>();
  //  private Setting<Integer> buildsToKeep = new Setting<Integer>();
  //  private Setting<Integer> maxEngineMemory = new Setting<Integer>();
  //  private Setting<Integer> productUpdateInterval = new Setting<Integer>();
  //  private Setting<Integer> sessionIdleMaximum = new Setting<Integer>();
  //  private Setting<Integer> maxLocalSessions = new Setting<Integer>();
  //  private Setting<Integer> maxRemoteSessions = new Setting<Integer>();
  //  private Setting<Integer> maxSessionsPerUser = new Setting<Integer>();
  //  private Setting<Integer> enginePort = new Setting<Integer>();
  //  private Setting<Boolean> useUTMProjection = new Setting<Boolean>();
  //  private Setting<Boolean> useGeocoding = new Setting<Boolean>();
  //  private Setting<Integer> localResourceValidationInterval = new Setting<Integer>();
  //  private Setting<Integer> publicResourceValidationInterval = new Setting<Integer>();
  //  private Setting<Boolean> revalidatePublicResources = new Setting<Boolean>();
  //  private Setting<Boolean> revalidateLocalResources = new Setting<Boolean>();
  //  private Setting<Integer> maxPolygonCoordinates = new Setting<Integer>();
  //  private Setting<Integer> maxPolygonSubdivisions = new Setting<Integer>();
  //  private Setting<Boolean> useNanosecondResolution = new Setting<Boolean>();
  //  private Setting<String> parallelismStrategy = new Setting<String>();
  //  private Setting<Boolean> useInMemoryStorage = new Setting<Boolean>();
  //  private Setting<Boolean> resolveModelsFromNetwork = new Setting<Boolean>();
  //  private Setting<Boolean> visualizeResolutionGraphs = new Setting<Boolean>();
  //  private Setting<Boolean> visualizeSpatialDebuggingAids = new Setting<Boolean>();
  //  private Setting<Boolean> resolveObservationsFromNetwork = new Setting<Boolean>();
  //  private Setting<Boolean> loadRemoteContext = new Setting<Boolean>();
  //  private Setting<File> workDirectory = new Setting<File>();
  //  private Setting<File> workspaceDirectory = new Setting<File>();
  //  private Setting<File> exportDirectory = new Setting<File>();
  //  private Setting<File> certificateFile = new Setting<File>();
  //  private Setting<File> tempDirectory = new Setting<File>();
  //  private Setting<File> releaseDirectory = new Setting<File>();
  //  private Setting<String> releaseUrl = new Setting<String>();
  //  private Setting<String> releasePolicy = new Setting<String>();
  //  private Setting<String> selectedRelease = new Setting<String>();
  //

  //
  //  private Setting<Boolean> useDebugParameters = new Setting<Boolean>();
  //  private Setting<Boolean> deleteTempStorage = new Setting<Boolean>();
  //
  //  private Setting<String> googleApiKey = new Setting<String>();
  //  private Setting<String> bingApiKey = new Setting<String>();
  //  private Setting<String> mapboxLayerURL = new Setting<String>();
  //  private Setting<String> mapboxLayerName = new Setting<String>();
  //  private Setting<String> mapboxLayerAttribution = new Setting<String>();
  //  private Setting<String> authenticationEndpoint = new Setting<String>();
  //  private Setting<Integer> debugPort = new Setting<>();

  public enum Page {
    GENERAL,
    APPEARANCE,
    EDITOR,
    SERVICES,
    MESSAGING,
    RESOURCES,
    REASONER,
    RESOLVER,
    RUNTIME,
    DEBUGGING
  }

  // if this is null, any string value is admitted
  public final String[] values;
  public final Class<?> valueClass;
  public final String description;
  public final Page page;
  public final Object defaultValue;

  Setting(Page page, String description, Class<?> valueClass, Object defaultValue) {
    this.description = description;
    this.valueClass = valueClass;
    this.page = page;
    this.defaultValue = defaultValue;
    this.values = new String[] {};
  }

  Setting(Page page, String description, String defaultValue, String... stringValues) {
    this.description = description;
    this.values = stringValues;
    this.valueClass = String.class;
    this.defaultValue = defaultValue;
    this.page = page;
  }

  public boolean validate(Object value) {
    if (String.class.equals(valueClass)) {
      if (value instanceof String && values != null && values.length > 0) {
        for (var v : values) {
          if (value.equals(v)) {
            return true;
          }
        }
        return false;
      }
    }
    return value != null && valueClass.isAssignableFrom(value.getClass());
  }
}
