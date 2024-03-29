package org.integratedmodelling.klab.api.services.resources.objects;

import java.util.ArrayList;
import java.util.List;

import org.integratedmodelling.klab.api.services.Authority;
import org.integratedmodelling.klab.api.services.runtime.Notification;

public class AuthorityIdentity implements Authority.Identity {

	private List<String> parentRelationships = new ArrayList<>();
	private String id;
	private String conceptName;
	private List<String> parentIds;
	private String label;
	private String description;
	private float score = 1.0f;
	private String authorityName;
	private String baseIdentity;
	private String locator;
	private List<Notification> notifications = new ArrayList<>();

	@Override
	public String getId() {
		return id;
	}

	@Override
	public String getConceptName() {
		return conceptName;
	}

	@Override
	public List<String> getParentIds() {
		return parentIds;
	}

	@Override
	public String getDescription() {
		return description;
	}

	@Override
	public String getLabel() {
		return label;
	}

	@Override
	public float getScore() {
		return score;
	}

	public void setId(String id) {
		this.id = id;
	}

	public void setConceptName(String conceptName) {
		this.conceptName = conceptName;
	}

	public void setParentIds(List<String> parentIds) {
		this.parentIds = parentIds;
	}

	public void setLabel(String label) {
		this.label = label;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	public void setScore(float score) {
		this.score = score;
	}
	
	@Override
	public String getAuthorityName() {
		return this.authorityName;
	}

	@Override
	public String getBaseIdentity() {
		return this.baseIdentity;
	}

	@Override
	public List<String> getParentRelationship() {
		return this.parentRelationships;
	}

	public List<String> getParentRelationships() {
		return parentRelationships;
	}

	public void setParentRelationships(List<String> parentRelationships) {
		this.parentRelationships = parentRelationships;
	}

	public void setAuthorityName(String authorityName) {
		this.authorityName = authorityName;
	}

	public void setBaseIdentity(String baseIdentity) {
		this.baseIdentity = baseIdentity;
	}

	@Override
	public String getLocator() {
		return locator;
	}

	public void setLocator(String locator) {
		this.locator = locator;
	}

	@Override
	public List<Notification> getNotifications() {
		return notifications;
	}

	public void setNotifications(List<Notification> notifications) {
		this.notifications = notifications;
	}

}
