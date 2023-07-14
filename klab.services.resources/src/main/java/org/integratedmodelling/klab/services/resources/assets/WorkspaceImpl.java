package org.integratedmodelling.klab.services.resources.assets;

import java.util.ArrayList;
import java.util.Collection;

import org.integratedmodelling.klab.api.knowledge.organization.Project;
import org.integratedmodelling.klab.api.knowledge.organization.Workspace;

public class WorkspaceImpl implements Workspace {

	private static final long serialVersionUID = -9221855512336458408L;

	private String name;
	private Collection<Project> projects = new ArrayList<>();

	@Override
	public String getName() {
		return this.name;
	}

	@Override
	public Collection<Project> getProjects() {
		return this.projects;
	}

	public void setName(String name) {
		this.name = name;
	}

	public void setProjects(Collection<Project> projects) {
		this.projects = projects;
	}

}
