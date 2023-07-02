package org.integratedmodelling.klab.api.services.resources;

public class Operation {

	private String name;
	private String description;
	private boolean shouldConfirm;

	public Operation() {
	}

	public Operation(String name, String description, boolean shouldConfirm) {
		this.name = name;
		this.description = description;
		this.shouldConfirm = shouldConfirm;
	}

	public boolean isShouldConfirm() {
		return shouldConfirm;
	}

	public void setShouldConfirm(boolean shouldConfirm) {
		this.shouldConfirm = shouldConfirm;
	}

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

}

