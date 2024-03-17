package org.integratedmodelling.klab.api.knowledge.impl;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.integratedmodelling.klab.api.data.Metadata;
import org.integratedmodelling.klab.api.data.Version;
import org.integratedmodelling.klab.api.knowledge.organization.Project;
import org.integratedmodelling.klab.api.knowledge.organization.Workspace;
import org.integratedmodelling.klab.api.lang.Annotation;

public class WorkspaceImpl implements Workspace {

    private static final long serialVersionUID = -9221855512336458408L;

    private String urn;
    private Collection<Project> projects = new ArrayList<>();
    private Metadata metadata = Metadata.create();

    @Override
    public String getUrn() {
        return this.urn;
    }

    @Override
    public Metadata getMetadata() {
        return this.metadata;
    }

    @Override
    public Collection<Project> getProjects() {
        return this.projects;
    }

    public void setUrn(String urn) {
        this.urn = urn;
    }

    public void setProjects(Collection<Project> projects) {
        this.projects = projects;
    }

    public void setMetadata(Metadata metadata) {
        this.metadata = metadata;
    }
}
