package org.integratedmodelling.klab.api.services.resources;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.integratedmodelling.klab.api.collections.impl.PairImpl;
import org.integratedmodelling.klab.api.data.Version;
import org.integratedmodelling.klab.api.services.Resources;

/**
 * The output of any resources GET endpoint that provides models, projects, worldview, behaviors or
 * resources. The returned resources should be matched with anything already loaded and their
 * versions; the order of each array is the load dependency order.
 * <p>
 * Each resource lists a URN, a version and the service that provides it. The serializable service
 * object would normally be just a client with a URL and access methods but in local configurations
 * it may be the operational service object.
 * <p>
 * A method isEmpty() is provided to streamline usage and possibly differentiate from a resource set
 * that contains no resources but actually represents as a non-empty result.
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
         * The service ID maps to the services hash in the enclosing class, to avoid duplications.
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

    private Map<String, Resources> services = new HashMap<>();
    private List<Resource> namespaces = new ArrayList<>();
    private List<Resource> behaviors = new ArrayList<>();
    private List<Resource> resources = new ArrayList<>();
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
    public Map<String, Resources> getServices() {
        return services;
    }
    public void setServices(Map<String, Resources> services) {
        this.services = services;
    }

}
