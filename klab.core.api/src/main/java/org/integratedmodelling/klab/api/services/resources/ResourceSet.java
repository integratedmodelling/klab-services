package org.integratedmodelling.klab.api.services.resources;

import org.integratedmodelling.klab.api.authentication.CRUDOperation;
import org.integratedmodelling.klab.api.data.Metadata;
import org.integratedmodelling.klab.api.data.RepositoryState;
import org.integratedmodelling.klab.api.data.Version;
import org.integratedmodelling.klab.api.knowledge.KlabAsset.KnowledgeClass;
import org.integratedmodelling.klab.api.services.runtime.Notification;
import org.integratedmodelling.klab.api.utils.Utils;

import java.io.Serial;
import java.io.Serializable;
import java.net.URL;
import java.util.*;

/**
 * The output of any resources GET endpoint that provides models, projects, worldview, behaviors or resources,
 * or reports the resources affected by a change. The returned resources should be matched with anything
 * already loaded and their versions; the order of each array is always the load dependency order if one is
 * defined. The main descriptor includes a service ID so that we can establish whether the result comes from
 * the current service or another.
 * <p>
 * Each resource descriptor lists a URN, its "knowledge class", a version and the unique ID of the service
 * that provides it, plus a {@link CRUDOperation} that details what happened to it. Each service ID should be
 * matched with the correspondent URL in the main object. Metadata may be added to better describe the context
 * of the result, provenance, authors, or rationale when appropriate. The actual resources must be loaded
 * independently; the service guarantees that any URNs mentioned are served and available to the requesting
 * identity. All ResourceSets (even if empty) must contain the ID of the service that produced them. If the
 * ResultSet is returned as the response for querying a specific URN or a pattern, the result should contain
 * the resource descriptor(s) in {@link #getResults()}. All other fields contain the needed objects that must
 * be loaded or reloaded before any use of it is made.
 * <p>
 * A method isEmpty() is provided to streamline usage and possibly differentiate from a resource set that
 * contains no resources but actually represents as a non-empty result, or from an empty result that does
 * contain resources. A ResultSet can also carry notifications so that the reasons for a non-expected empty
 * set can be investigated.
 *
 * @author Ferd
 */
public class ResourceSet implements Serializable {

    @Serial
    private static final long serialVersionUID = 6465699208972901806L;

    private Map<String, URL> services = new HashMap<>();
    private List<Resource> namespaces = new ArrayList<>();
    private String workspace;
    private List<Resource> ontologies = new ArrayList<>();
    private List<Resource> behaviors = new ArrayList<>();
    private List<Resource> resources = new ArrayList<>();
    private List<Resource> observationStrategies = new ArrayList<>();
    private Set<Resource> results = new HashSet<>();
    private List<Notification> notifications = new ArrayList<>();
    private RepositoryState repositoryState;

    private boolean empty;

    /**
     * Each resource descriptor may contain metadata that describe what was done and where, or what prompted a
     * change.
     */
    public static class Resource implements Serializable {

        @Serial
        private static final long serialVersionUID = 4391465185554063863L;

        private String serviceId;
        private String resourceUrn;
        private Version resourceVersion;
        private KnowledgeClass knowledgeClass;
        private CRUDOperation operation = CRUDOperation.UPDATE;
        private Metadata metadata = Metadata.create();
        private List<Notification> notifications = new ArrayList<>();
        private String projectUrn;

        public Resource() {
        }

        public Resource(String serviceId, String resourceUrn, String projectUrn, Version resourceVersion,
                        KnowledgeClass knowledgeClass) {
            super();
            this.serviceId = serviceId;
            this.resourceUrn = resourceUrn;
            this.resourceVersion = resourceVersion;
            this.knowledgeClass = knowledgeClass;
            this.projectUrn = projectUrn;
        }

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

        public KnowledgeClass getKnowledgeClass() {
            return knowledgeClass;
        }

        public void setKnowledgeClass(KnowledgeClass knowledgeClass) {
            this.knowledgeClass = knowledgeClass;
        }

        public CRUDOperation getOperation() {
            return operation;
        }

        public void setOperation(CRUDOperation operation) {
            this.operation = operation;
        }

        public Metadata getMetadata() {
            return metadata;
        }

        public void setMetadata(Metadata metadata) {
            this.metadata = metadata;
        }

        public List<Notification> getNotifications() {
            return notifications;
        }

        public void setNotifications(List<Notification> notifications) {
            this.notifications = notifications;
        }

        public String getProjectUrn() {
            return projectUrn;
        }

        public void setProjectUrn(String projectUrn) {
            this.projectUrn = projectUrn;
        }

        @Override
        public String toString() {
            return Utils.Strings.capitalize(this.knowledgeClass.name().toLowerCase()) + " " + this.resourceUrn + " v"
                    + this.resourceVersion + "/" + this.projectUrn + " (" + this.serviceId + ")";
        }
    }

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

    public Map<String, URL> getServices() {
        return services;
    }

    public void setServices(Map<String, URL> services) {
        this.services = services;
    }

    /**
     * Result for any knowledge that is the focus of the query. Empty if the resource set is not the result of
     * a query for a specific URN or set thereof.
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

    public List<Resource> getOntologies() {
        return ontologies;
    }

    public void setOntologies(List<Resource> ontologies) {
        this.ontologies = ontologies;
    }

    /**
     * If not null, the descriptor refers to a particular workspace. This is used particularly when reporting
     * changes.
     *
     * @return
     */
    public String getWorkspace() {
        return workspace;
    }

    public void setWorkspace(String workspace) {
        this.workspace = workspace;
    }

    public List<Resource> getObservationStrategies() {
        return observationStrategies;
    }

    public void setObservationStrategies(List<Resource> observationStrategies) {
        this.observationStrategies = observationStrategies;
    }

    public RepositoryState getRepositoryState() {
        return repositoryState;
    }

    public void setRepositoryState(RepositoryState repositoryState) {
        this.repositoryState = repositoryState;
    }

    public static ResourceSet empty() {
        var ret = new ResourceSet();
        ret.setEmpty(true);
        return ret;
    }

    public static ResourceSet empty(Notification notification) {
        var ret = empty();
        ret.getNotifications().add(notification);
        return ret;
    }

    @Override
    public String toString() {
        return "ResourceSet{" +
                "workspace='" + workspace + '\'' +
                ", results=" + results +
                ", resources=" + resources +
                ", ontologies=" + ontologies +
                ", observationStrategies=" + observationStrategies +
                ", notifications=" + notifications +
                ", namespaces=" + namespaces +
                ", empty=" + empty +
                ", behaviors=" + behaviors +
                '}';
    }
}
