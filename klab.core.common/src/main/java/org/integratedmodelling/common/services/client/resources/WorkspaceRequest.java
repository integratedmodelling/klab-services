package org.integratedmodelling.common.services.client.resources;

import org.integratedmodelling.klab.api.knowledge.KlabAsset;

/**
 * Bean class for REST POST endpoints that modify a workspace with new content.
 */
public class WorkspaceRequest {

    private String workspace;
    private String projectName;
    private String documentUrn;
    private KlabAsset.KnowledgeClass knowledgeClass;
    private String newContent;

    public String getWorkspace() {
        return workspace;
    }

    public void setWorkspace(String workspace) {
        this.workspace = workspace;
    }

    public String getProjectName() {
        return projectName;
    }

    public void setProjectName(String projectName) {
        this.projectName = projectName;
    }

    public String getDocumentUrn() {
        return documentUrn;
    }

    public void setDocumentUrn(String documentUrn) {
        this.documentUrn = documentUrn;
    }

    public KlabAsset.KnowledgeClass getKnowledgeClass() {
        return knowledgeClass;
    }

    public void setKnowledgeClass(KlabAsset.KnowledgeClass knowledgeClass) {
        this.knowledgeClass = knowledgeClass;
    }

    public String getNewContent() {
        return newContent;
    }

    public void setNewContent(String newContent) {
        this.newContent = newContent;
    }
}
