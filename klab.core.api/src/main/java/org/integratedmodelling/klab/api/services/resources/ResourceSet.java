package org.integratedmodelling.klab.api.services.resources;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.integratedmodelling.klab.api.data.Version;
import org.integratedmodelling.klab.api.knowledge.KlabAsset.KnowledgeClass;
import org.integratedmodelling.klab.api.services.ResourcesService;
import org.integratedmodelling.klab.api.services.runtime.Notification;
import org.integratedmodelling.klab.api.utils.Utils;

/**
 * The output of any resources GET endpoint that provides models, projects,
 * worldview, behaviors or resources. The returned resources should be matched
 * with anything already loaded and their versions; the order of each array is
 * the load dependency order.
 * <p>
 * Each resource lists a URN, its "knowledge class", a version and the URL of
 * the service that provides it. All resource sets must contain at least the
 * service that produced it. If the ResultSet is returned as the response for
 * querying a specific URN or a pattern, the result should contain the resource
 * descriptor(s) in {@link #getResults()}. All other fields contain the needed
 * objects that must be loaded to make use of it.
 * <p>
 * A method isEmpty() is provided to streamline usage and possibly differentiate
 * from a resource set that contains no resources but actually represents as a
 * non-empty result. A ResultSet can also carry notifications so that the
 * reasons for a non-expected empty set can be investigated.
 * 
 * @author Ferd
 *
 */
public class ResourceSet implements Serializable {

	private static final long serialVersionUID = 6465699208972901806L;

	public static class Resource implements Serializable {

		private static final long serialVersionUID = 4391465185554063863L;

		private String serviceId;
		private String resourceUrn;
		private Version resourceVersion;
		private KnowledgeClass knowledgeClass;

		public Resource() {
		}

		public Resource(String serviceId, String resourceUrn, Version resourceVersion, KnowledgeClass knowledgeClass) {
			super();
			this.serviceId = serviceId;
			this.resourceUrn = resourceUrn;
			this.resourceVersion = resourceVersion;
			this.knowledgeClass = knowledgeClass;
		}

		/**
		 * The service ID maps to the services hash in the enclosing class, to avoid
		 * duplications.
		 * 
		 * @return
		 */
		public String getServiceId() {
			return serviceId;
		}

		public void setServiceId(String serviceId) {
			this.serviceId = serviceId;
		}

		public String getResourceUrn() {
			return resourceUrn;
		}

		public void setResourceUrn(String resourceUrn) {
			this.resourceUrn = resourceUrn;
		}

		public Version getResourceVersion() {
			return resourceVersion;
		}

		public void setResourceVersion(Version resourceVersion) {
			this.resourceVersion = resourceVersion;
		}

		public KnowledgeClass getKnowledgeClass() {
			return knowledgeClass;
		}

		public void setKnowledgeClass(KnowledgeClass knowledgeClass) {
			this.knowledgeClass = knowledgeClass;
		}

		@Override
		public String toString() {
			return Utils.Strings.capitalize(this.knowledgeClass.name().toLowerCase()) + " " + this.resourceUrn + " v"
					+ this.resourceVersion + " (" + this.serviceId + ")";
		}

	}

	private Map<String, ResourcesService> services = new HashMap<>();
	private List<Resource> namespaces = new ArrayList<>();
	private List<Resource> behaviors = new ArrayList<>();
	private List<Resource> resources = new ArrayList<>();
	private Set<Resource> results = new HashSet<>();
	private List<Notification> notifications = new ArrayList<>();

	private boolean empty;

	public List<Resource> getNamespaces() {
		return namespaces;
	}

	public void setNamespaces(List<Resource> namespaces) {
		this.namespaces = namespaces;
	}

	public List<Resource> getBehaviors() {
		return behaviors;
	}

	public void setBehaviors(List<Resource> behaviors) {
		this.behaviors = behaviors;
	}

	public List<Resource> getResources() {
		return resources;
	}

	public void setResources(List<Resource> resources) {
		this.resources = resources;
	}

	public boolean isEmpty() {
		return empty;
	}

	public void setEmpty(boolean empty) {
		this.empty = empty;
	}

	public Map<String, ResourcesService> getServices() {
		return services;
	}

	public void setServices(Map<String, ResourcesService> services) {
		this.services = services;
	}

	/**
	 * Result for any knowledge that is the focus of the query. Empty if the
	 * resource set is not the result of a query for a specific URN or set thereof.
	 * 
	 * @return
	 */
	public Set<Resource> getResults() {
		return results;
	}

	public void setResults(Set<Resource> urns) {
		this.results = urns;
	}

	public List<Notification> getNotifications() {
		return notifications;
	}

	public void setNotifications(List<Notification> notifications) {
		this.notifications = notifications;
	}

}
