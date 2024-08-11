package org.integratedmodelling.klab.services.resources.configuration;

import org.integratedmodelling.klab.api.authentication.ResourcePrivileges;
import org.integratedmodelling.klab.api.knowledge.organization.ProjectStorage;

import java.io.File;
import java.io.Serial;
import java.io.Serializable;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * The resource service configuration is a POJO serialized from/to the resources.yaml file in the .klab
 * directory.
 *
 * @author Ferd
 */
public class ResourcesConfiguration implements Serializable {

    @Serial
    private static final long serialVersionUID = 8407649258899502009L;

    public static class ResourceConfiguration implements Serializable {

        @Serial
        private static final long serialVersionUID = -8966889570222340019L;
        private ResourcePrivileges privileges;
        private String doi;
        private int revisionTier;
        private String localPath;

        public ResourcePrivileges getPrivileges() {
            return privileges;
        }

        public void setPrivileges(ResourcePrivileges privileges) {
            this.privileges = privileges;
        }

        public String getDoi() {
            return doi;
        }

        public void setDoi(String doi) {
            this.doi = doi;
        }

        public int getRevisionTier() {
            return revisionTier;
        }

        public void setRevisionTier(int revisionTier) {
            this.revisionTier = revisionTier;
        }

        public String getLocalPath() {
            return localPath;
        }

        public void setLocalPath(String localPath) {
            this.localPath = localPath;
        }

    }

    public static class ProjectConfiguration implements Serializable {

        @Serial
        private static final long serialVersionUID = -8989429880321748157L;

        private String sourceUrl;
        private String referenceWorldview;
        private boolean served;
        private boolean worldview;
        private ResourcePrivileges privileges;
        private boolean locallyManaged;
        private boolean authoritative;
        private int syncIntervalMinutes;
        private File localPath;
        private String workspaceName;
        private ProjectStorage.Type storageType;

        public ProjectStorage.Type getStorageType() {
            return storageType;
        }

        public void setStorageType(ProjectStorage.Type storageType) {
            this.storageType = storageType;
        }

        public String getSourceUrl() {
            return sourceUrl;
        }

        public void setSourceUrl(String sourceUrl) {
            this.sourceUrl = sourceUrl;
        }

        public String getReferenceWorldview() {
            return referenceWorldview;
        }

        public void setReferenceWorldview(String referenceWorldview) {
            this.referenceWorldview = referenceWorldview;
        }

        public boolean isWorldview() {
            return worldview;
        }

        public void setWorldview(boolean worldview) {
            this.worldview = worldview;
        }

        public ResourcePrivileges getPrivileges() {
            return privileges;
        }

        public void setPrivileges(ResourcePrivileges privileges) {
            this.privileges = privileges;
        }

        public boolean isLocallyManaged() {
            return locallyManaged;
        }

        public void setLocallyManaged(boolean locallyManaged) {
            this.locallyManaged = locallyManaged;
        }

        public boolean isAuthoritative() {
            return authoritative;
        }

        public void setAuthoritative(boolean authoritative) {
            this.authoritative = authoritative;
        }

        public int getSyncIntervalMinutes() {
            return syncIntervalMinutes;
        }

        public void setSyncIntervalMinutes(int syncIntervalMinutes) {
            this.syncIntervalMinutes = syncIntervalMinutes;
        }

        /**
         * Projects that aren't served are there only to make other projects understood.
         *
         * @return
         */
        public boolean isServed() {
            return served;
        }

        public void setServed(boolean served) {
            this.served = served;
        }

        public File getLocalPath() {
            return localPath;
        }

        public void setLocalPath(File localPath) {
            this.localPath = localPath;
        }

        public String getWorkspaceName() {
            return this.workspaceName;
        }

        public void setWorkspaceName(String workspaceName) {
            this.workspaceName = workspaceName;
        }

    }

    /**
     * The service work directory path within the k.LAB work directory
     */
    private String servicePath = "resources";
    /**
     * all other paths are relative to the service path
     */
    private String localResourcePath = "local";
    private String publicResourcePath = "public";

    /**
     * Each workspace name is a subdirectory with a number of projects in them. All are relative to the
     * resource path. The order of declaration in config is the order of loading.
     */
    private Map<String, Set<String>> workspaces = new LinkedHashMap<>();

    private String serviceId;

    /**
     * Each project managed by this service
     */
    private Map<String, ProjectConfiguration> projectConfiguration = new HashMap<>();

    /**
     * Same for individually managed resources (those local to projects are managed in the project config
     * itself)
     */
    private Map<String, ResourceConfiguration> resourceConfiguration = new HashMap<>();

    public String getServicePath() {
        return servicePath;
    }

    public void setServicePath(String servicePath) {
        this.servicePath = servicePath;
    }

    public Map<String, Set<String>> getWorkspaces() {
        return workspaces;
    }

    public void setWorkspaces(Map<String, Set<String>> workspaces) {
        this.workspaces = workspaces;
    }

    public Map<String, ProjectConfiguration> getProjectConfiguration() {
        return projectConfiguration;
    }

    public void setProjectConfiguration(Map<String, ProjectConfiguration> projectConfiguration) {
        this.projectConfiguration = projectConfiguration;
    }

    public String getLocalResourcePath() {
        return localResourcePath;
    }

    public void setLocalResourcePath(String localResourcePath) {
        this.localResourcePath = localResourcePath;
    }

    public String getPublicResourcePath() {
        return publicResourcePath;
    }

    public void setPublicResourcePath(String publicResourcePath) {
        this.publicResourcePath = publicResourcePath;
    }

    public String getServiceId() {
        return serviceId;
    }

    public void setServiceId(String serviceId) {
        this.serviceId = serviceId;
    }
}
