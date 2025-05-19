package org.integratedmodelling.klab.rest;

import org.integratedmodelling.klab.api.lang.ServiceInfo;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Deprecated
public class AdapterInfo {

	public static class OperationReference {
		private String name;
		private String description;
		private boolean requiresConfirmation;

		public String getName() {
			return name;
		}

		public void setName(String name) {
			this.name = name;
		}

		public String getDescription() {
			return description;
		}

		public void setDescription(String description) {
			this.description = description;
		}

		public boolean isRequiresConfirmation() {
			return requiresConfirmation;
		}

		public void setRequiresConfirmation(boolean requiresConfirmation) {
			this.requiresConfirmation = requiresConfirmation;
		}

	}

	private String name;
	private String description;
	private String label;
	private ServiceInfo parameters;
	private boolean universal;
	private Map<String, String> exportCapabilities = new HashMap<>();
	private boolean multipleResources;
	private List<OperationReference> operations = new ArrayList<>();
	private boolean acceptsDrops;
	private boolean canCreateEmpty;
	
	@Deprecated // unused - REMOVE when all nodes and engines are updated
	private boolean fileBased;
	
	public ServiceInfo getParameters() {
		return parameters;
	}

	public void setParameters(ServiceInfo parameters) {
		this.parameters = parameters;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	public String getLabel() {
		return label;
	}

	public void setLabel(String label) {
		this.label = label;
	}

	public Map<String, String> getExportCapabilities() {
		return exportCapabilities;
	}

	public void setExportCapabilities(Map<String, String> exportCapabilities) {
		this.exportCapabilities = exportCapabilities;
	}

	public boolean isMultipleResources() {
		return multipleResources;
	}

	public void setMultipleResources(boolean multipleResources) {
		this.multipleResources = multipleResources;
	}

	public boolean isUniversal() {
		return universal;
	}

	public void setUniversal(boolean universal) {
		this.universal = universal;
	}

	public List<OperationReference> getOperations() {
		return operations;
	}

	public void setOperations(List<OperationReference> operations) {
		this.operations = operations;
	}

	public boolean isAcceptsDrops() {
		return acceptsDrops;
	}

	public void setAcceptsDrops(boolean acceptsDrops) {
		this.acceptsDrops = acceptsDrops;
	}

	public boolean isCanCreateEmpty() {
		return canCreateEmpty;
	}

	public void setCanCreateEmpty(boolean canCreateEmpty) {
		this.canCreateEmpty = canCreateEmpty;
	}

	public boolean isFileBased() {
		return fileBased;
	}

	public void setFileBased(boolean fileBased) {
		this.fileBased = fileBased;
	}
}
