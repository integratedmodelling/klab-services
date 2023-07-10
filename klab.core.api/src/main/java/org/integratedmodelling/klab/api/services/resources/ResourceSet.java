package org.integratedmodelling.klab.api.services.resources;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.integratedmodelling.klab.api.data.Version;
import org.integratedmodelling.klab.api.services.ResourcesService;

/**
 * The output of any resources GET endpoint that provides models, projects,
 * worldview, behaviors or resources. The returned resources should be matched
 * with anything already loaded and their versions; the order of each array is
 * the load dependency order.
 * <p>
 * Each resource lists a URN, a version and the service that provides it. The
 * serializable service object would normally be just a client with a URL and
 * access methods; for local resources it can be the operational service object
 * which may provide a URL or not, but should always provide it if the request
 * comes from a remote client. All resource sets must contain at least the
 * service that produced it.
 * <p>
 * A method isEmpty() is provided to streamline usage and possibly differentiate
 * from a resource set that contains no resources but actually represents as a
 * non-empty result.
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

	}

	private Map<String, ResourcesService> services = new HashMap<>();
	private List<Resource> namespaces = new ArrayList<>();
	private List<Resource> behaviors = new ArrayList<>();
	private List<Resource> resources = new ArrayList<>();
	private Set<String> urns = new HashSet<>();

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
	 * URNs of reference for any knowledge that is the focus of the query. Its use
	 * is only mandatory in situations when the contents of the resource set are
	 * instrumental to the use of those URNs - for example, when the query is for
	 * models and the remaining fields are needed to run them.
	 * 
	 * @return
	 */
	public Set<String> getUrns() {
		return urns;
	}

	public void setUrns(Set<String> urns) {
		this.urns = urns;
	}

}
