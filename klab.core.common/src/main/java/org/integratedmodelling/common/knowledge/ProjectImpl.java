package org.integratedmodelling.common.knowledge;

import org.integratedmodelling.klab.api.authentication.ResourcePrivileges;
import org.integratedmodelling.klab.api.collections.Pair;
import org.integratedmodelling.klab.api.data.Metadata;
import org.integratedmodelling.klab.api.data.MetadataConvention;
import org.integratedmodelling.klab.api.data.RepositoryState;
import org.integratedmodelling.klab.api.data.Version;
import org.integratedmodelling.klab.api.knowledge.organization.Project;
import org.integratedmodelling.klab.api.knowledge.organization.ProjectStorage;
import org.integratedmodelling.klab.api.lang.Annotation;
import org.integratedmodelling.klab.api.lang.kactors.KActorsBehavior;
import org.integratedmodelling.klab.api.lang.kim.KimNamespace;
import org.integratedmodelling.klab.api.lang.kim.KimObservationStrategyDocument;
import org.integratedmodelling.klab.api.lang.kim.KimOntology;
import org.integratedmodelling.klab.api.lang.kim.KlabDocument;
import org.integratedmodelling.klab.api.services.runtime.Notification;

import java.io.Serial;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;

public class ProjectImpl implements Project {

    @Serial
    private static final long serialVersionUID = 7618524077068234748L;
//    private Repository repositoryMetadata = new RepositoryImpl();

    public static class ManifestImpl implements Manifest {

        @Serial
        private static final long serialVersionUID = -6549113149802016133L;
        private String description;
        private ResourcePrivileges privileges = ResourcePrivileges.PUBLIC;
        private Version version = Version.EMPTY_VERSION;
        private Collection<MetadataConvention> metadataConventions = new HashSet<>();
        private List<Pair<String, Version>> prerequisiteProjects = new ArrayList<>();
        private List<Pair<String, Version>> prerequisiteComponents = new ArrayList<>();
        private String worldview;
        private String definedWorldview;

        @Override
        public String getDescription() {
            return this.description;
        }

        @Override
        public String getWorldview() {
            return this.worldview;
        }

        @Override
        public String getDefinedWorldview() {
            return this.definedWorldview;
        }

        @Override
        public ResourcePrivileges getPrivileges() {
            return this.privileges;
        }

        @Override
        public Version getVersion() {
            return this.version;
        }

        @Override
        public Collection<MetadataConvention> getMetadataConventions() {
            return this.metadataConventions;
        }

        @Override
        public List<Pair<String, Version>> getPrerequisiteProjects() {
            return this.prerequisiteProjects;
        }

        @Override
        public List<Pair<String, Version>> getPrerequisiteComponents() {
            return this.prerequisiteComponents;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public void setPrivileges(ResourcePrivileges privileges) {
            this.privileges = privileges;
        }

        public void setVersion(Version version) {
            this.version = version;
        }

        public void setMetadataConventions(Collection<MetadataConvention> metadataConventions) {
            this.metadataConventions = metadataConventions;
        }

        public void setPrerequisiteProjects(List<Pair<String, Version>> prerequisiteProjects) {
            this.prerequisiteProjects = prerequisiteProjects;
        }

        public void setPrerequisiteComponents(List<Pair<String, Version>> prerequisiteComponents) {
            this.prerequisiteComponents = prerequisiteComponents;
        }

        public void setWorldview(String worldview) {
            this.worldview = worldview;
        }

        public void setDefinedWorldview(String definedWorldview) {
            this.definedWorldview = definedWorldview;
        }
    }

    private Manifest manifest;
    private Metadata metadata = Metadata.create();
    private String urn;
    private List<KimNamespace> namespaces = new ArrayList<>();
    private List<KimOntology> ontologies = new ArrayList<>();
    private List<KActorsBehavior> behaviors = new ArrayList<>();
    private List<KActorsBehavior> apps = new ArrayList<>();
    private List<KActorsBehavior> testCases = new ArrayList<>();
    private List<Notification> notifications = new ArrayList<>();
    private List<String> resourceUrns = new ArrayList<>();
    private List<KimObservationStrategyDocument> observationStrategies = new ArrayList<>();
    private List<Annotation> annotations = new ArrayList<>();
    private RepositoryState repositoryState = new RepositoryState();

    @Override
    public Manifest getManifest() {
        return this.manifest;
    }

    @Override
    public Metadata getMetadata() {
        return this.metadata;
    }

    @Override
    public String getUrn() {
        return this.urn;
    }

    @Override
    public List<KimNamespace> getNamespaces() {
        return this.namespaces;
    }

    @Override
    public List<KActorsBehavior> getBehaviors() {
        return this.behaviors;
    }

    @Override
    public List<KActorsBehavior> getApps() {
        return this.apps;
    }

    @Override
    public List<KActorsBehavior> getTestCases() {
        return this.testCases;
    }

    @Override
    public List<Notification> getNotifications() {
        return this.notifications;
    }

    public void setManifest(Manifest manifest) {
        this.manifest = manifest;
    }

    public void setMetadata(Metadata metadata) {
        this.metadata = metadata;
    }

    public void setUrn(String urn) {
        this.urn = urn;
    }

    public void setNamespaces(List<KimNamespace> namespaces) {
        this.namespaces = namespaces;
    }

    public void setBehaviors(List<KActorsBehavior> behaviors) {
        this.behaviors = behaviors;
    }

    public void setApps(List<KActorsBehavior> apps) {
        this.apps = apps;
    }

    public void setTestCases(List<KActorsBehavior> testCases) {
        this.testCases = testCases;
    }

    public void setNotifications(List<Notification> notifications) {
        this.notifications = notifications;
    }

    public List<String> getResourceUrns() {
        return resourceUrns;
    }

    public void setResourceUrns(List<String> resourceUrns) {
        this.resourceUrns = resourceUrns;
    }

    @Override
    public List<KimOntology> getOntologies() {
        return ontologies;
    }

    @Override
    public List<KimObservationStrategyDocument> getObservationStrategies() {
        return this.observationStrategies;
    }

    public void setOntologies(List<KimOntology> ontologies) {
        this.ontologies = ontologies;
    }

    public void setObservationStrategies(List<KimObservationStrategyDocument> observationStrategies) {
        this.observationStrategies = observationStrategies;
    }

    public List<Annotation> getAnnotations() {
        return annotations;
    }

    public void setAnnotations(List<Annotation> annotations) {
        this.annotations = annotations;
    }

    @Override
    public RepositoryState getRepositoryState() {
        return repositoryState;
    }

    public void setRepositoryState(RepositoryState repositoryState) {
        this.repositoryState = repositoryState;
    }

    //    public Repository getRepository() {
//        return repositoryMetadata;
//    }
//
//    public void setRepositoryMetadata(Repository repositoryMetadata) {
//        this.repositoryMetadata = repositoryMetadata;
//    }

    // TODO lots
    public KlabDocument<?> findDocument(String documentPath) {
        var ddata = ProjectStorage.getDocumentData(documentPath);
        if (ddata != null) {

            return switch (ddata.getFirst()) {
                case ONTOLOGY -> {
                    for (var ontology : getOntologies()) {
                        if (ontology.getUrn().equals(ddata.getSecond())) {
                            yield ontology;
                        }
                    }
                    yield null;
                }
                case MODEL_NAMESPACE -> {
                    for (var namespace : getNamespaces()) {
                        if (namespace.getUrn().equals(ddata.getSecond())) {
                            yield namespace;
                        }
                    }
                    yield null;
                }
                case MANIFEST -> null;
                case DOCUMENTATION_NAMESPACE -> null;
                case STRATEGY -> {
                    for (var strategy : getObservationStrategies()) {
                        if (strategy.getUrn().equals(ddata.getSecond())) {
                            yield strategy;
                        }
                    }
                    yield null;
                }
                case BEHAVIOR -> null;
                case RESOURCE -> null;
                case RESOURCE_ASSET -> null;
                default -> null;
            };
        }
        return null;
    }
}
