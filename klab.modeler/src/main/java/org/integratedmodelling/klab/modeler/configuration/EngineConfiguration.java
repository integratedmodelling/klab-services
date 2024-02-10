package org.integratedmodelling.klab.modeler.configuration;

import java.util.HashMap;
import java.util.Map;

/**
 * Serializable engine configuration remembering the latest workspaces and
 * documents in focus.
 */
public class EngineConfiguration {

	private String lastResourcesServiceSelected;
	private String lastReasonerServiceSelected;
	private String lastRuntimeServiceSelected;
	private String lastResolverServiceSelected;
	private Map<String, String> lastWorkspaceSelected = new HashMap<>();
	private Map<String, WorkspaceConfiguration> workspaceConfigurations = new HashMap<>();

	public String getLastResourcesServiceSelected() {
		return lastResourcesServiceSelected;
	}

	public void setLastResourcesServiceSelected(String lastResourcesServiceSelected) {
		this.lastResourcesServiceSelected = lastResourcesServiceSelected;
	}

	public String getLastReasonerServiceSelected() {
		return lastReasonerServiceSelected;
	}

	public void setLastReasonerServiceSelected(String lastReasonerServiceSelected) {
		this.lastReasonerServiceSelected = lastReasonerServiceSelected;
	}

	public String getLastRuntimeServiceSelected() {
		return lastRuntimeServiceSelected;
	}

	public void setLastRuntimeServiceSelected(String lastRuntimeServiceSelected) {
		this.lastRuntimeServiceSelected = lastRuntimeServiceSelected;
	}

	public String getLastResolverServiceSelected() {
		return lastResolverServiceSelected;
	}

	public void setLastResolverServiceSelected(String lastResolverServiceSelected) {
		this.lastResolverServiceSelected = lastResolverServiceSelected;
	}

	/**
	 * The last workspace selected for each resources service ID ever used.
	 * 
	 * @return
	 */
	public Map<String, String> getLastWorkspaceSelected() {
		return lastWorkspaceSelected;
	}

	public void setLastWorkspaceSelected(Map<String, String> lastWorkspaceSelected) {
		this.lastWorkspaceSelected = lastWorkspaceSelected;
	}

	/**
	 * The key to the workspace configuration is <serviceID>:<workspaceUrn> to keep the
	 * hierarchy reasonably flat.
	 * 
	 * @return
	 */
	public Map<String, WorkspaceConfiguration> getWorkspaceConfigurations() {
		return workspaceConfigurations;
	}

	public void setWorkspaceConfigurations(Map<String, WorkspaceConfiguration> workspaceConfigurations) {
		this.workspaceConfigurations = workspaceConfigurations;
	}

}
