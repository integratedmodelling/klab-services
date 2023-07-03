package org.integratedmodelling.klab.api.services.resources;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import org.integratedmodelling.klab.api.authentication.ResourcePrivileges;
import org.integratedmodelling.klab.api.authentication.scope.Scope;
import org.integratedmodelling.klab.api.knowledge.KlabAsset.KnowledgeClass;
import org.integratedmodelling.klab.api.services.ResourceProvider;
import org.integratedmodelling.klab.api.services.runtime.Notification;

/**
 * Resource status reports on availability (overall and/or in scope) and review
 * status of any resource. It should be stored and maintained as a secure,
 * persistent catalog indexed by resource URN in the resource service. While all
 * other information is stored reflecting the status for the service at the time
 * of last update, the availability ({@link #getType()} should always be
 * assessed in scope and in realtime when this is retrieved from the API
 * ({@link ResourceProvider#resourceStatus(String, Scope)}), reflecting
 * permissions, server status, adapter status, availability of dependencies and
 * any other needed factor. The stored type should be AUTHORIZED or DEPRECATED,
 * OFFLINE only if it starts its lifecycle with compilation errors, and never
 * UNAUTHORIZED or DELAYED.
 * 
 * @author Ferd
 *
 */
public class ResourceStatus {

	public enum Type {
		AVAILABLE(true), DELAYED(true), UNAUTHORIZED(false), OFFLINE(false),
		/**
		 * Deprecated also implies AVAILABLE. Delayed or Partial status resources that
		 * are deprecated are considered unavailable for now.
		 */
		DEPRECATED(true);
		
		private boolean usable;

		private Type(boolean usable) {
			this.usable = usable;
		}

		public boolean isUsable() {
			return usable;
		}
	}

	private Type type;
	private int retryTimeSeconds;
	private List<Notification> notifications = new ArrayList<>();
	private int reviewStatus;
	private ResourcePrivileges privileges = ResourcePrivileges.empty();
	private String owner;
	private File fileLocation;
	private boolean legacy;
	private KnowledgeClass knowledgeClass;

	public Type getType() {
		return type;
	}

	public void setType(Type availability) {
		this.type = availability;
	}

	/**
	 * This will be different from 0 iif the status is {@link Type#DELAYED} and it
	 * should be taken as an indication only.
	 * 
	 * @return
	 */
	public int getRetryTimeSeconds() {
		return retryTimeSeconds;
	}

	public void setRetryTimeSeconds(int retryTimeSeconds) {
		this.retryTimeSeconds = retryTimeSeconds;
	}

	public static ResourceStatus immediate() {
		ResourceStatus ret = new ResourceStatus();
		ret.setType(Type.AVAILABLE);
		return ret;
	}

	public static ResourceStatus offline() {
		ResourceStatus ret = new ResourceStatus();
		ret.setType(Type.OFFLINE);
		return ret;
	}

	public List<Notification> getNotifications() {
		return notifications;
	}

	public void setNotifications(List<Notification> notifications) {
		this.notifications = notifications;
	}

	/**
	 * This ranges from 0 (unreviewed) through 1 (staging if local, in review if
	 * public) to 2 (reviewed and accepted, with a DOI) and up. Resources at level
	 * higher than 2 may move down in level as well as up but not go below 2 unless
	 * retracted. Level -1 is rejected or retracted; lower negative rankings may
	 * indicate special infamy such as fake resources, at the discretion of the
	 * implementation. Resources with negative rankings should not be used in any
	 * circumstance, and all normal operation APIs should not return them.
	 * 
	 * @param reviewStatus
	 */
	public int getReviewStatus() {
		return reviewStatus;
	}

	public void setReviewStatus(int reviewStatus) {
		this.reviewStatus = reviewStatus;
	}

	/**
	 * Privileges start empty, which enables use only for the resource owner and
	 * (according to implementation) administrators, auditors and reviewers. The
	 * owner of the resource is not necessarily the holder of the copyright or of
	 * any license set for the resource.
	 * 
	 * @return
	 */
	public ResourcePrivileges getPrivileges() {
		return privileges;
	}

	public void setPrivileges(ResourcePrivileges privileges) {
		this.privileges = privileges;
	}

	/**
	 * For now resources are owned uniquely by users, which may be institutional or
	 * personal, and should always be in the form <code>hub:username</code>.
	 * 
	 * @return
	 */
	public String getOwner() {
		return owner;
	}

	public void setOwner(String owner) {
		this.owner = owner;
	}

	public File getFileLocation() {
		return fileLocation;
	}

	public void setFileLocation(File fileLocation) {
		this.fileLocation = fileLocation;
	}

	/**
	 * True if pre-k.LAB 12.0
	 * 
	 * @return
	 */
	public boolean isLegacy() {
		return legacy;
	}

	public void setLegacy(boolean legacy) {
		this.legacy = legacy;
	}

	public KnowledgeClass getKnowledgeClass() {
		return knowledgeClass;
	}

	public void setKnowledgeClass(KnowledgeClass knowledgeClass) {
		this.knowledgeClass = knowledgeClass;
	}

}
