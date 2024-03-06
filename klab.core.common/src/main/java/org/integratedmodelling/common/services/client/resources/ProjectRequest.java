package org.integratedmodelling.common.services.client.resources;

import org.integratedmodelling.klab.api.authentication.ResourcePrivileges;
import org.integratedmodelling.klab.api.data.Metadata;

import java.util.ArrayList;
import java.util.List;

/**
 * Request bean for most POST endpoints that concern projects. Which fields are used depend on the request.
 */
public class ProjectRequest {

    private String workspaceName;
    private String projectName;
    private String projectUrl;
    private Metadata projectMetadata = Metadata.create();
    private ResourcePrivileges projectPrivileges;
    private List<String> namespaceUrns = new ArrayList<>();
    private List<String> behaviorUrns = new ArrayList<>();
    private boolean overwrite;

    public String getProjectName() {
        return projectName;
    }

    public void setProjectName(String projectName) {
        this.projectName = projectName;
    }

    public String getProjectUrl() {
        return projectUrl;
    }

    public void setProjectUrl(String projectUrl) {
        this.projectUrl = projectUrl;
    }

    public Metadata getProjectMetadata() {
        return projectMetadata;
    }

    public void setProjectMetadata(Metadata projectMetadata) {
        this.projectMetadata = projectMetadata;
    }

    public ResourcePrivileges getProjectPrivileges() {
        return projectPrivileges;
    }

    public void setProjectPrivileges(ResourcePrivileges projectPrivileges) {
        this.projectPrivileges = projectPrivileges;
    }

    public List<String> getNamespaceUrns() {
        return namespaceUrns;
    }

    public void setNamespaceUrns(List<String> namespaceUrns) {
        this.namespaceUrns = namespaceUrns;
    }

    public List<String> getBehaviorUrns() {
        return behaviorUrns;
    }

    public void setBehaviorUrns(List<String> behaviorUrns) {
        this.behaviorUrns = behaviorUrns;
    }

    public String getWorkspaceName() {
        return workspaceName;
    }

    public void setWorkspaceName(String workspaceName) {
        this.workspaceName = workspaceName;
    }

    public boolean isOverwrite() {
        return overwrite;
    }

    public void setOverwrite(boolean overwrite) {
        this.overwrite = overwrite;
    }
}
