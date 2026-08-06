package org.integratedmodelling.klab.api.configuration;

import java.io.File;
import java.util.Map;
import org.integratedmodelling.klab.api.engine.distribution.Distribution;
import org.integratedmodelling.klab.api.utils.Utils;

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
      10),
  DO_NOT_CREATE_A_DEFAULT_OBSERVER(
      Page.GENERAL,
      "Do not create a default observer for a connected digital twin",
      Boolean.class,
      false),
  FORCE_INDIVIDUAL_OBSERVER(
      Page.GENERAL,
      "Create an individual observer for the connected digital twin even when the user is federated",
      Boolean.class,
      false),
  NOTIFICATIONS_CACHED(Page.APPEARANCE, "Number of notifications to keep", Integer.class, 100),
  WORK_DIRECTORY(
      Page.GENERAL,
      "The directory where all k.LAB files are stored",
      File.class,
      new File(
          System.getProperty("user.home")
              + File.separator
              + Configuration.KLAB_RELATIVE_WORK_PATH)),
  MONOSPACE_FONT(
      Page.APPEARANCE,
      "The font to use for the monospaced text in the modeler",
      String.class,
      Utils.OS.get() == Utils.OS.WIN ? "Consolas" : "Monospaced"),
  RUN_DIRECTORY(
      Page.GENERAL,
      "The directory where PIDs and other runtime files are stored",
      File.class,
      new File(
          System.getProperty("user.home") + File.separator + ".klab" + File.separator + "run")),
  DISTRIBUTION_DIRECTORY(
      Page.GENERAL,
      "The directory where k.LAB distribution files will be stored. Contents will be large.",
      File.class,
      new File(
          System.getProperty("user.home")
              + File.separator
              + ".klab"
              + File.separator
              + "distribution")),
  NUMBER_OF_DISTRIBUTION_TO_KEEP(
      Page.GENERAL,
      "The number of previous k.LAB distributions to keep on disk when updating to a new one.",
      Integer.class,
      1),
  // WARNING: this is used in the graphdb local service without importing the setting. If changed,
  // the graphdb service will need to be updated to use the new setting.
  DATABASE_DIRECTORY(
      Page.SERVICES,
      "The directory where the graph database for local runtime will be hosted.",
      File.class,
      new File(
          System.getProperty("user.home")
              + File.separator
              + ".klab"
              + File.separator
              + "services"
              + File.separator
              + "graphdb")),
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
      "Launch local services automatically if a distribution is available",
      Boolean.class,
      false),
  EXIT_WHEN_STOPPING_SERVICES(
      Page.GENERAL,
      "Close main and aux services and exit the engine/modeler on local services stop",
      Boolean.class,
      false),
  REMEMBER_WORKBENCH_CONFIGURATION(
      Page.APPEARANCE,
      "Record editing history and restore open workspaces and editors when a service is connected",
      Boolean.class,
      false),
  LOG_EVENTS(Page.SERVICES, "Log server-side events", Boolean.class, false),
  LOCAL_ONLY(Page.SERVICES, "Disable use of remote services", Boolean.class, false),
  DISTRIBUTION_SOURCE_URL(
      Page.SERVICES,
      "Alternative source URL for the k.LAB stack distribution",
      String.class,
      "https://resources.integratedmodelling.org/klab/products/klab"),
  STOP_AUXILIARY_SERVICES(
      Page.SERVICES,
      "Stop local auxiliary services (graph database, AMQP broker) when k.LAB services are stopped",
      Boolean.class,
      false),
  SYNCHRONIZE_STACK_ON_STARTUP(
      Page.SERVICES,
      "Automatically update the software stack when the modeler is started with no services running",
      Boolean.class,
      false),
  CHECK_FOR_UPDATES_INTERVAL_MINUTES(
      Page.SERVICES,
      "Interval in minutes between automatic software stack update checks. Set to 0 to disable.",
      Integer.class,
      5),
  CURRENT_DISTRIBUTION_TAG(
      Page.SERVICES,
      "Physical identity of the software stack distribution selected by the user",
      String.class,
      ""),
  START_LSP_SERVER_ON_STARTUP(
      Page.EDITOR,
      "Automatically start and restart the k.LAB LSP language server if a software stack is available.",
      Boolean.class,
      true),
  DETECT_LOCAL_HUB(
      Page.DEBUGGING,
      "Look for a hub running on localhost for authentication",
      Boolean.class,
      false),
  MAX_RESOLVER_SERVICE_MEMORY(
      Page.RESOLVER,
      "Maximum memory for the local resolver in MB",
      Integer.class,
      Distribution.Product.Type.RESOLVER_SERVICE.defaultMaxMemoryLimitMB()),
  MAX_REASONER_SERVICE_MEMORY(
      Page.REASONER,
      "Maximum memory for the local reasoner in MB",
      Integer.class,
      Distribution.Product.Type.REASONER_SERVICE.defaultMaxMemoryLimitMB()),
  MAX_RUNTIME_SERVICE_MEMORY(
      Page.RUNTIME,
      "Maximum memory for the local runtime in MB",
      Integer.class,
      Distribution.Product.Type.RUNTIME_SERVICE.defaultMaxMemoryLimitMB()),
  USE_SHORT_FLOAT_REPRESENTATION(
      Page.RUNTIME,
      "Use short floats to save space instead of doubles, at the expense of precision",
      Boolean.class,
      Boolean.FALSE),
  PARALLELIZE_OBSERVATIONS(
      Page.RUNTIME,
      "Honor configured parallelism strategy and splits when processing quality observations with multiple states",
      Boolean.class,
      // default is FALSE for now. Will eventually become true.
      Boolean.FALSE),
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
      true),
  DIGITAL_TWIN_TIMEOUT_MINUTES(
      Page.RUNTIME,
      "Maximum idle lifetime in minutes for a digital twin configured to be deleted on timeout",
      Integer.class,
      60),
  DIGITAL_TWIN_REINITIALIZATION_TIMEOUT_MINUTES(
      Page.RUNTIME,
      "Maximum idle lifetime in minutes for a digital twin configured to be reinitialized on timeout",
      Integer.class,
      120),
  GRAPH_DATABASE_URL(
      Page.RUNTIME,
      "The Bolt URL for the connected neo4j database",
      String.class,
      "bolt://0.0.0.0:7687"),
  USE_LOCAL_FEDERATION(
      Page.RUNTIME,
      "Use the local federation with the broker embedded in the runtime until downtime",
      Map.class,
      Map.of()),
  MAX_RESOURCES_SERVICE_MEMORY(
      Page.RESOURCES,
      "Maximum memory for the local resources service in MB",
      Integer.class,
      Distribution.Product.Type.RESOURCES_SERVICE.defaultMaxMemoryLimitMB()),
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
  USE_LOCAL_MESSAGE_BROKER(
      Page.MESSAGING,
      "Deploy a local message broker with the runtime service for internal communication when user is not federated",
      Boolean.class,
      true),
  SUBSCRIBE_TO_WARNING_NOTIFICATIONS(
      Page.MESSAGING,
      "Have the runtime send warning notifications to the local client through the messaging system",
      Boolean.class,
      false),
  SUBSCRIBE_TO_INFO_NOTIFICATIONS(
      Page.MESSAGING,
      "Have the runtime send informational notifications to the local client through the messaging system",
      Boolean.class,
      false),
  SUBSCRIBE_TO_DEBUG_NOTIFICATIONS(
      Page.MESSAGING,
      "Have the runtime send debug notifications to the local client through the messaging system",
      Boolean.class,
      false),
  LOGIN_ANONYMOUSLY(
      Page.DEBUGGING, "Ignore the user certificate and login anonymously", Boolean.class, false),
  USE_DEVELOPMENT_DISTRIBUTION_IF_AVAILABLE(
      Page.DEBUGGING,
      "Use the compiled binary distribution in ~/git/klab-services if available",
      Boolean.class,
      Boolean.TRUE),
  DISTRIBUTION_SOURCE_LOCATION(
      Page.DEBUGGING,
      "Location of the k.LAB software distribution",
      File.class,
      new File(
          System.getProperty("user.home")
              + File.separator
              + "git"
              + File.separator
              + "klab-services")),
  START_RESOURCES_SERVICE_IN_DEBUG_MODE(
      Page.DEBUGGING,
      "Start the local resources service in debug mode on port "
          + Distribution.Product.Type.RESOURCES_SERVICE.getDebugPort(),
      Boolean.class,
      false),
  START_RESOLVER_SERVICE_IN_DEBUG_MODE(
      Page.DEBUGGING,
      "Start the local resolver in debug mode on port "
          + Distribution.Product.Type.RESOLVER_SERVICE.getDebugPort(),
      Boolean.class,
      false),
  START_REASONER_SERVICE_IN_DEBUG_MODE(
      Page.DEBUGGING,
      "Start the local reasoner in debug mode on port "
          + Distribution.Product.Type.REASONER_SERVICE.getDebugPort(),
      Boolean.class,
      false),
  START_RUNTIME_SERVICE_IN_DEBUG_MODE(
      Page.DEBUGGING,
      "Start the local runtime service in debug mode on port "
          + Distribution.Product.Type.RUNTIME_SERVICE.getDebugPort(),
      Boolean.class,
      false),
  RESET_ALL_SERVICE_CONFIGURATION(
      Page.DEBUGGING,
      "Reset the configuration of all local services and their data",
      Map.class,
      Map.of()),
  RESET_ALL_SERVICE_DATA(
      Page.DEBUGGING,
      "Remove all data for local services but not their configuration",
      Map.class,
      Map.of()),
  LAUNCH_DATABASE_INSPECTOR(
      Page.DEBUGGING,
      "Launch the online Neo4j inspector. Connect to neo4j://0.0.0.0:7687 for local.",
      Map.class,
      Map.of()),
  LAUNCH_DEBUG_GUI(
      Page.DEBUGGING,
      "Launch a DevToolsFX debugging tool for the GUI when in graphical mode",
      Map.class,
      Map.of()),
  LIST_LOCAL_COMMIT_OPERATIONS(
      Page.DEBUGGING,
      "List local commit/push operations in project team actions",
      Boolean.class,
      Boolean.FALSE),
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
