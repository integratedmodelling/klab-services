package org.integratedmodelling.klab.api.settings;

public class EngineSettings {

    private ServiceSettings reasonerSettings;
    private ServiceSettings resourcesSettings;
    private ServiceSettings resolverSettings;
    private ServiceSettings runtimeSettings;
    private ServiceSettings engineSettings;
    private ModelerSettings modelerSettings;

    public ServiceSettings getReasonerSettings() {
        return reasonerSettings;
    }

    public void setReasonerSettings(ServiceSettings reasonerSettings) {
        this.reasonerSettings = reasonerSettings;
    }

    public ServiceSettings getResourcesSettings() {
        return resourcesSettings;
    }

    public void setResourcesSettings(ServiceSettings resourcesSettings) {
        this.resourcesSettings = resourcesSettings;
    }

    public ServiceSettings getResolverSettings() {
        return resolverSettings;
    }

    public void setResolverSettings(ServiceSettings resolverSettings) {
        this.resolverSettings = resolverSettings;
    }

    public ServiceSettings getRuntimeSettings() {
        return runtimeSettings;
    }

    public void setRuntimeSettings(ServiceSettings runtimeSettings) {
        this.runtimeSettings = runtimeSettings;
    }

    public ServiceSettings getEngineSettings() {
        return engineSettings;
    }

    public void setEngineSettings(ServiceSettings engineSettings) {
        this.engineSettings = engineSettings;
    }

    public ModelerSettings getModelerSettings() {
        return modelerSettings;
    }

    public void setModelerSettings(ModelerSettings modelerSettings) {
        this.modelerSettings = modelerSettings;
    }
}
