package org.integratedmodelling.klab.api.engine.distribution;

import org.integratedmodelling.klab.api.configuration.Configuration;
import org.integratedmodelling.klab.api.engine.distribution.impl.ProductImpl;

import java.io.File;
import java.nio.file.Files;
import java.util.Properties;

/** FIXME straight port from ControlCenter. This is for the Engine product. */
public class Settings {

  // Keys for customizable parameters in k.LAB properties, prefixed by lowercase product type
  private final String DEBUG_CONFIGURATION_KEY_POSTFIX = ".debug.enabled";

  public class Setting<T> {
    static final String UNSET = "unset";
    String engineParameter;
    T value;

    Setting() {
      this.engineParameter = UNSET;
    }

    Setting(String engineParameter) {
      this(engineParameter, null);
    }

    Setting(String engineParameter, T value) {
      this.engineParameter = engineParameter;
      this.value = value;
    }

    Setting(T value) {
      this(UNSET, value);
    }

    public void setValue(T value) {
      this.value = value;
    }

    public T getValue() {
      return this.value;
    }
  }

  private Setting<Boolean> startWithCLI = new Setting<Boolean>();
  private Setting<Boolean> detectLocalHub = new Setting<Boolean>();
  private Setting<Boolean> resetAllBuilds = new Setting<Boolean>();
  // private Setting<Boolean> resetAllBuildsButLatest = new Setting<Boolean>();
  private Setting<Boolean> updateAutomatically = new Setting<Boolean>();
  private Setting<Boolean> resetKnowledge = new Setting<Boolean>();
  private Setting<Boolean> resetModelerWorkspace = new Setting<Boolean>();
  private Setting<Integer> buildsToKeep = new Setting<Integer>();
  private Setting<Integer> maxEngineMemory = new Setting<Integer>();
  private Setting<Integer> productUpdateInterval = new Setting<Integer>();
  private Setting<Integer> sessionIdleMaximum = new Setting<Integer>();
  private Setting<Integer> maxLocalSessions = new Setting<Integer>();
  private Setting<Integer> maxRemoteSessions = new Setting<Integer>();
  private Setting<Integer> maxSessionsPerUser = new Setting<Integer>();
  private Setting<Integer> enginePort = new Setting<Integer>();
  private Setting<Boolean> useUTMProjection = new Setting<Boolean>();
  private Setting<Boolean> useGeocoding = new Setting<Boolean>();
  private Setting<Integer> localResourceValidationInterval = new Setting<Integer>();
  private Setting<Integer> publicResourceValidationInterval = new Setting<Integer>();
  private Setting<Boolean> revalidatePublicResources = new Setting<Boolean>();
  private Setting<Boolean> revalidateLocalResources = new Setting<Boolean>();
  private Setting<Integer> maxPolygonCoordinates = new Setting<Integer>();
  private Setting<Integer> maxPolygonSubdivisions = new Setting<Integer>();
  private Setting<Boolean> useNanosecondResolution = new Setting<Boolean>();
  private Setting<String> parallelismStrategy = new Setting<String>();
  private Setting<Boolean> useInMemoryStorage = new Setting<Boolean>();
  private Setting<Boolean> resolveModelsFromNetwork = new Setting<Boolean>();
  private Setting<Boolean> visualizeResolutionGraphs = new Setting<Boolean>();
  private Setting<Boolean> visualizeSpatialDebuggingAids = new Setting<Boolean>();
  private Setting<Boolean> resolveObservationsFromNetwork = new Setting<Boolean>();
  private Setting<Boolean> loadRemoteContext = new Setting<Boolean>();
  private Setting<File> workDirectory = new Setting<File>();
  private Setting<File> workspaceDirectory = new Setting<File>();
  private Setting<File> exportDirectory = new Setting<File>();
  private Setting<File> certificateFile = new Setting<File>();
  private Setting<File> tempDirectory = new Setting<File>();
  private Setting<File> releaseDirectory = new Setting<File>();
  private Setting<String> releaseUrl = new Setting<String>();
  private Setting<String> releasePolicy = new Setting<String>();
  private Setting<String> selectedRelease = new Setting<String>();

  private Setting<Double> minModelCoverage = new Setting<Double>();
  private Setting<Double> minTotalCoverage = new Setting<Double>();
  private Setting<Double> minCoverageImprovement = new Setting<Double>();

  private Setting<Boolean> useDebugParameters = new Setting<Boolean>();
  private Setting<Boolean> deleteTempStorage = new Setting<Boolean>();

  private Setting<String> googleApiKey = new Setting<String>();
  private Setting<String> bingApiKey = new Setting<String>();
  private Setting<String> mapboxLayerURL = new Setting<String>();
  private Setting<String> mapboxLayerName = new Setting<String>();
  private Setting<String> mapboxLayerAttribution = new Setting<String>();
  private Setting<String> authenticationEndpoint = new Setting<String>();
  private Setting<Integer> debugPort = new Setting<>();

  public Settings(Release release) {
    startWithCLI.setValue(Boolean.TRUE);
    detectLocalHub.setValue(Boolean.FALSE);
    resetAllBuilds.setValue(Boolean.FALSE);
    // resetAllBuildsButLatest.setValue(Boolean.FALSE);
    updateAutomatically.setValue(Boolean.FALSE);
    resetKnowledge.setValue(Boolean.FALSE);
    resetModelerWorkspace.setValue(Boolean.FALSE);
    buildsToKeep.setValue(3);
    maxEngineMemory.setValue(release.getProduct().getProductType().defaultMaxMemoryLimitMB());
    productUpdateInterval.setValue(1);
    sessionIdleMaximum.setValue(7);
    maxLocalSessions.setValue(10);
    maxRemoteSessions.setValue(0);
    maxSessionsPerUser.setValue(3);
    //        enginePort.setValue(IConfigurationService.DEFAULT_ENGINE_PORT);
    useUTMProjection.setValue(Boolean.FALSE);
    useGeocoding.setValue(Boolean.TRUE);
    localResourceValidationInterval.setValue(10);
    publicResourceValidationInterval.setValue(10);
    revalidatePublicResources.setValue(Boolean.FALSE);
    revalidateLocalResources.setValue(Boolean.FALSE);
    maxPolygonCoordinates.setValue(1000);
    maxPolygonSubdivisions.setValue(2000);
    useNanosecondResolution.setValue(Boolean.FALSE);
    parallelismStrategy.setValue("Standard");
    useInMemoryStorage.setValue(Boolean.FALSE);
    resolveModelsFromNetwork.setValue(Boolean.TRUE);
    visualizeResolutionGraphs.setValue(Boolean.FALSE);
    visualizeSpatialDebuggingAids.setValue(Boolean.FALSE);
    resolveObservationsFromNetwork.setValue(Boolean.FALSE);
    loadRemoteContext.setValue(Boolean.FALSE);
    workDirectory.setValue(new File(System.getProperty("user.home") + File.separator + ".klab"));
    workspaceDirectory.setValue(
        new File(
            System.getProperty("user.home")
                + File.separator
                + ".klab"
                + File.separator
                + "workspace"));
    exportDirectory.setValue(
        new File(
            System.getProperty("user.home")
                + File.separator
                + ".klab"
                + File.separator
                + "export"));
    certificateFile.setValue(
        new File(
            System.getProperty("user.home")
                + File.separator
                + ".klab"
                + File.separator
                + "klab.cert"));
    tempDirectory.setValue(
        new File(
            Configuration.INSTANCE.getProperty(
                Configuration.KLAB_TEMPORARY_DATA_DIRECTORY,
                System.getProperty("java.io.tmpdir"))));
    releaseDirectory.setValue(
        new File(
            System.getProperty("user.home")
                + File.separator
                + ".klab"
                + File.separator
                + "releases"));

    minModelCoverage.setValue(0.01);
    minTotalCoverage.setValue(0.95);
    minCoverageImprovement.setValue(0.2);

    useDebugParameters.setValue(Boolean.FALSE);
    deleteTempStorage.setValue(Boolean.TRUE);

    googleApiKey.setValue("");
    bingApiKey.setValue("");
    mapboxLayerURL.setValue("");
    mapboxLayerName.setValue("");
    mapboxLayerAttribution.setValue("");
    authenticationEndpoint.setValue("");
    debugPort.setValue(5005);

    // not used on window

    //        releaseUrl.setValue(Release.DEFAULT_RELEASE_URL);
    releasePolicy.setValue(release.getName());
    //        selectedRelease.setValue(null);
  }

  public Setting<Boolean> startWithCLI() {
    return startWithCLI;
  }

  public void setStartWithCLI(boolean startWithCLI) {
    this.startWithCLI.setValue(startWithCLI);
  }

  public Setting<Boolean> isDetectLocalHub() {
    return detectLocalHub;
  }

  public void setDetectLocalHub(boolean detectLocalHub) {
    this.detectLocalHub.setValue(detectLocalHub);
  }

  public Setting<Boolean> isResetAllBuilds() {
    return resetAllBuilds;
  }

  public void setResetAllBuilds(boolean resetAllBuilds) {
    this.resetAllBuilds.setValue(resetAllBuilds);
  }

  public Setting<Boolean> isUpdateAutomatically() {
    return updateAutomatically;
  }

  public void setUpdateAutomatically(boolean updateAutomatically) {
    this.updateAutomatically.setValue(updateAutomatically);
  }

  public Setting<Boolean> isResetKnowledge() {
    return resetKnowledge;
  }

  public void setResetKnowledge(boolean resetKnowledge) {
    this.resetKnowledge.setValue(resetKnowledge);
  }

  public Setting<Boolean> isResetModelerWorkspace() {
    return resetModelerWorkspace;
  }

  public void setResetModelerWorkspace(boolean resetModelerWorkspace) {
    this.resetModelerWorkspace.setValue(resetModelerWorkspace);
  }

  public Setting<Integer> getBuildsToKeep() {
    return buildsToKeep;
  }

  public void setBuildsToKeep(int buildsToKeep) {
    this.buildsToKeep.setValue(buildsToKeep);
  }

  public Setting<Integer> getMaxEngineMemory() {
    return maxEngineMemory;
  }

  public void setMaxEngineMemory(int maxEngineMemory) {
    this.maxEngineMemory.setValue(maxEngineMemory);
  }

  public Setting<Integer> getProductUpdateInterval() {
    return productUpdateInterval;
  }

  public void setProductUpdateInterval(int productUpdateInterval) {
    this.productUpdateInterval.setValue(productUpdateInterval);
  }

  public Setting<Integer> getSessionIdleMaximum() {
    return sessionIdleMaximum;
  }

  public void setSessionIdleMaximum(int sessionIdleMaximum) {
    this.sessionIdleMaximum.setValue(sessionIdleMaximum);
  }

  public Setting<Integer> getMaxLocalSessions() {
    return maxLocalSessions;
  }

  public void setMaxLocalSessions(int maxLocalSessions) {
    this.maxLocalSessions.setValue(maxLocalSessions);
  }

  public Setting<Integer> getMaxRemoteSessions() {
    return maxRemoteSessions;
  }

  public void setMaxRemoteSessions(int maxRemoteSessions) {
    this.maxRemoteSessions.setValue(maxRemoteSessions);
  }

  public Setting<Integer> getMaxSessionsPerUser() {
    return maxSessionsPerUser;
  }

  public void setMaxSessionsPerUser(int maxSessionsPerUser) {
    this.maxSessionsPerUser.setValue(maxSessionsPerUser);
  }

  public Setting<Integer> getEnginePort() {
    return enginePort;
  }

  public void setEnginePort(int enginePort) {
    this.enginePort.setValue(enginePort);
  }

  public Setting<Boolean> isUseUTMProjection() {
    return useUTMProjection;
  }

  public void setUseUTMProjection(boolean useUTMProjection) {
    this.useUTMProjection.setValue(useUTMProjection);
  }

  public Setting<Boolean> isUseGeocoding() {
    return useGeocoding;
  }

  public void setUseGeocoding(boolean useGeocoding) {
    this.useGeocoding.setValue(useGeocoding);
  }

  public Setting<Integer> getLocalResourceValidationInterval() {
    return localResourceValidationInterval;
  }

  public void setLocalResourceValidationInterval(int localResourceValidationInterval) {
    this.localResourceValidationInterval.setValue(localResourceValidationInterval);
  }

  public Setting<Integer> getPublicResourceValidationInterval() {
    return publicResourceValidationInterval;
  }

  public void setPublicResourceValidationInterval(int publicResourceValidationInterval) {
    this.publicResourceValidationInterval.setValue(publicResourceValidationInterval);
  }

  public Setting<Boolean> isRevalidatePublicResources() {
    return revalidatePublicResources;
  }

  public void setRevalidatePublicResources(boolean revalidatePublicResources) {
    this.revalidatePublicResources.setValue(revalidatePublicResources);
  }

  public Setting<Boolean> isRevalidateLocalResources() {
    return revalidateLocalResources;
  }

  public void setRevalidateLocalResources(boolean revalidateLocalResources) {
    this.revalidateLocalResources.setValue(revalidateLocalResources);
  }

  public Setting<Integer> getMaxPolygonCoordinates() {
    return maxPolygonCoordinates;
  }

  public void setMaxPolygonCoordinates(int maxPolygonCoordinates) {
    this.maxPolygonCoordinates.setValue(maxPolygonCoordinates);
  }

  public Setting<Integer> getMaxPolygonSubdivisions() {
    return maxPolygonSubdivisions;
  }

  public void setMaxPolygonSubdivisions(int maxPolygonSubdivisions) {
    this.maxPolygonSubdivisions.setValue(maxPolygonSubdivisions);
  }

  public Setting<Boolean> isUseNanosecondResolution() {
    return useNanosecondResolution;
  }

  public void setUseNanosecondResolution(boolean useNanosecondResolution) {
    this.useNanosecondResolution.setValue(useNanosecondResolution);
  }

  public Setting<String> getParallelismStrategy() {
    return parallelismStrategy;
  }

  public void setParallelismStrategy(String parallelismStrategy) {
    this.parallelismStrategy.setValue(parallelismStrategy);
  }

  public Setting<Boolean> isUseInMemoryStorage() {
    return useInMemoryStorage;
  }

  public void setUseInMemoryStorage(boolean useInMemoryStorage) {
    this.useInMemoryStorage.setValue(useInMemoryStorage);
  }

  public Setting<Boolean> isResolveModelsFromNetwork() {
    return resolveModelsFromNetwork;
  }

  public void setResolveModelsFromNetwork(boolean resolveModelsFromNetwork) {
    this.resolveModelsFromNetwork.setValue(resolveModelsFromNetwork);
  }

  public Setting<Boolean> isVisualizeResolutionGraphs() {
    return visualizeResolutionGraphs;
  }

  public void setVisualizeResolutionGraphs(boolean visualizeResolutionGraphs) {
    this.visualizeResolutionGraphs.setValue(visualizeResolutionGraphs);
  }

  public Setting<Boolean> isVisualizeSpatialDebuggingAids() {
    return visualizeSpatialDebuggingAids;
  }

  public void setVisualizeSpatialDebuggingAids(boolean visualizeSpatialDebuggingAids) {
    this.visualizeSpatialDebuggingAids.setValue(visualizeSpatialDebuggingAids);
  }

  public Setting<Boolean> isResolveObservationsFromNetwork() {
    return resolveObservationsFromNetwork;
  }

  public void setResolveObservationsFromNetwork(boolean resolveObservationsFromNetwork) {
    this.resolveObservationsFromNetwork.setValue(resolveObservationsFromNetwork);
  }

  public Setting<Boolean> isLoadRemoteContext() {
    return loadRemoteContext;
  }

  public void setLoadRemoteContext(boolean loadRemoteContext) {
    this.loadRemoteContext.setValue(loadRemoteContext);
  }

  public Setting<File> getWorkDirectory() {
    return workDirectory;
  }

  public void setWorkDirectory(File workDirectory) {
    this.workDirectory.setValue(workDirectory);
  }

  public Setting<File> getWorkspaceDirectory() {
    return workspaceDirectory;
  }

  public void setWorkspaceDirectory(File workspaceDirectory) {
    this.workspaceDirectory.setValue(workspaceDirectory);
  }

  public Setting<File> getExportDirectory() {
    return exportDirectory;
  }

  public void setExportDirectory(File exportDirectory) {
    this.exportDirectory.setValue(exportDirectory);
  }

  public Setting<File> getCertificateFile() {
    return certificateFile;
  }

  public void setCertificateFile(File certificateFile) {
    this.certificateFile.setValue(certificateFile);
  }

  public Setting<File> getTempDirectory() {
    return tempDirectory;
  }

  public void setTempDirectory(File tempDirectory) {
    this.tempDirectory.setValue(tempDirectory);
  }

  public Setting<Double> getMinModelCoverage() {
    return minModelCoverage;
  }

  public void setMinModelCoverage(double minModelCoverage) {
    this.minModelCoverage.setValue(minModelCoverage);
  }

  public Setting<Double> getMinTotalCoverage() {
    return minTotalCoverage;
  }

  public void setMinTotalCoverage(double minTotalCoverage) {
    this.minTotalCoverage.setValue(minTotalCoverage);
  }

  public Setting<Double> getMinCoverageImprovement() {
    return minCoverageImprovement;
  }

  public void setMinCoverageImprovement(double minCoverageImprovement) {
    this.minCoverageImprovement.setValue(minCoverageImprovement);
  }

  public Setting<Boolean> isUseDebugParameters() {
    return useDebugParameters;
  }

  public void setUseDebugParameters(boolean useDebugParameters) {
    this.useDebugParameters.setValue(useDebugParameters);
  }

  public Setting<Boolean> isDeleteTempStorage() {
    return deleteTempStorage;
  }

  public void setDeleteTempStorage(boolean deleteTempStorage) {
    this.deleteTempStorage.setValue(deleteTempStorage);
  }

  public Setting<String> getGoogleApiKey() {
    return googleApiKey;
  }

  public void setGoogleApiKey(String googleApiKey) {
    this.googleApiKey.setValue(googleApiKey);
  }

  public Setting<String> getBingApiKey() {
    return bingApiKey;
  }

  public void setBingApiKey(String bingApiKey) {
    this.bingApiKey.setValue(bingApiKey);
  }

  public Setting<String> getMapboxLayerURL() {
    return mapboxLayerURL;
  }

  public void setMapboxLayerURL(String mapboxLayerURL) {
    this.mapboxLayerURL.setValue(mapboxLayerURL);
  }

  public Setting<String> getMapboxLayerName() {
    return mapboxLayerName;
  }

  public void setMapboxLayerName(String mapboxLayerName) {
    this.mapboxLayerName.setValue(mapboxLayerName);
  }

  public Setting<String> getMapboxLayerAttribution() {
    return mapboxLayerAttribution;
  }

  public void setMapboxLayerAttribution(String mapboxLayerAttribution) {
    this.mapboxLayerAttribution.setValue(mapboxLayerAttribution);
  }

  public Setting<String> getAuthenticationEndpoint() {
    return authenticationEndpoint;
  }

  public void setAuthenticationEndpoint(String authenticationEndpoint) {
    this.authenticationEndpoint.setValue(authenticationEndpoint);
  }

  public Setting<File> getReleasesDirectory() {
    return releaseDirectory;
  }

  public void setReleasesDirectory(File releaseDirectory) {
    this.releaseDirectory.setValue(releaseDirectory);
  }

  public Setting<String> getReleaseUrl() {
    return releaseUrl;
  }

  public void setReleaseUrl(String releaseUrl) {
    this.releaseUrl.setValue(releaseUrl);
  }

  public Setting<String> getReleasePolicy() {
    return releasePolicy;
  }

  //    public void setReleasePolicy(String releasePolicy) {
  //        if (Release.LATEST_RELEASE.equals(releasePolicy)
  //                || Release.DEVELOP_RELEASE.equals(releasePolicy)
  //                || Release.RELEASE.equals(releasePolicy)) {
  //            this.releasePolicy.setValue(releasePolicy);
  //        } else {
  //            throw new IllegalArgumentException("Unknown release policy option: " +
  // releasePolicy);
  //        }
  //
  //    }

  public Setting<String> getSelectedRelease() {
    return selectedRelease;
  }

  public void setSelectedRelease(String selectedRelease) {
    this.selectedRelease.setValue(selectedRelease);
  }

  public void initialize(ProductImpl product, Properties properties) {

    var debugKey = product.getProductType().name().toLowerCase() + DEBUG_CONFIGURATION_KEY_POSTFIX;

    if (properties.containsKey(debugKey)) {
      this.setUseDebugParameters(Boolean.parseBoolean(properties.getProperty(debugKey)));
    }
  }
}
