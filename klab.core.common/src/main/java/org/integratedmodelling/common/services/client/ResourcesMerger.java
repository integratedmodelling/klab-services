package org.integratedmodelling.common.services.client;

import java.io.File;
import java.io.InputStream;
import java.io.Serializable;
import java.net.URL;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import org.integratedmodelling.klab.api.authentication.ExternalAuthenticationCredentials;
import org.integratedmodelling.klab.api.authentication.ResourcePrivileges;
import org.integratedmodelling.klab.api.collections.Parameters;
import org.integratedmodelling.klab.api.configuration.Setting;
import org.integratedmodelling.klab.api.configuration.Settings;
import org.integratedmodelling.klab.api.data.Data;
import org.integratedmodelling.klab.api.data.Metadata;
import org.integratedmodelling.klab.api.data.RepositoryState;
import org.integratedmodelling.klab.api.data.Version;
import org.integratedmodelling.klab.api.digitaltwin.DigitalTwin;
import org.integratedmodelling.klab.api.digitaltwin.Scheduler;
import org.integratedmodelling.klab.api.exceptions.KlabIllegalArgumentException;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.knowledge.KlabAsset;
import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.api.knowledge.Resource;
import org.integratedmodelling.klab.api.knowledge.Worldview;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.knowledge.organization.Project;
import org.integratedmodelling.klab.api.knowledge.organization.ProjectStorage;
import org.integratedmodelling.klab.api.knowledge.organization.Workspace;
import org.integratedmodelling.klab.api.lang.kactors.KActorsBehavior;
import org.integratedmodelling.klab.api.lang.kim.*;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.scope.Scope;
import org.integratedmodelling.klab.api.scope.SessionScope;
import org.integratedmodelling.klab.api.scope.UserScope;
import org.integratedmodelling.klab.api.services.ResourcesService;
import org.integratedmodelling.klab.api.services.resolver.Coverage;
import org.integratedmodelling.klab.api.services.resources.ResourceInfo;
import org.integratedmodelling.klab.api.services.resources.ResourceSet;
import org.integratedmodelling.klab.api.services.resources.ResourceTransport;
import org.integratedmodelling.klab.api.services.runtime.extension.AdapterDescriptor;

/**
 * A service that merges and prioritizes resource queries from all available services under a scope
 * into a single resource set. It is the default implementation of the {@link ResourcesService} when
 * retrieved through a client scope using {@link Scope#getService(Class)} for any operation that
 * requires merging results from different services. For write/update/delete operations, the client
 * scope will use the default prioritization rules (local first).
 *
 * <p>TODO not yet implemented or wired in.
 */
public class ResourcesMerger implements ResourcesService {

  private Scope owningScope;

  public ResourcesMerger(Scope owningScope) {
    this.owningScope = owningScope;
  }

  @Override
  public Capabilities capabilities(Scope scope) {
    return null;
  }

  @Override
  public ServiceStatus status() {
    return null;
  }

  @Override
  public URL getUrl() {
    return null;
  }

  @Override
  public String serviceName() {
    return "";
  }

  @Override
  public String serviceId() {
    return "";
  }

  @Override
  public Settings settings() {
    return null;
  }

  @Override
  public <T> CompletableFuture<T> set(Setting setting, Object value, Class<T> returnType) {
    return null;
  }

  @Override
  public Scope serviceScope() {
    return null;
  }

  @Override
  public boolean shutdown() {
    return false;
  }

  @Override
  public String declareSessionScope(
      SessionScope sessionScope, UserScope userScope, KActorsBehavior behavior) {
    return "";
  }

  @Override
  public DigitalTwin.Configuration declareContextScope(
      ContextScope contextScope, SessionScope sessionScope, UserScope userScope) {
    return null;
  }

  @Override
  public ResourcePrivileges getRights(String resourceUrn, Scope scope) {
    return null;
  }

  @Override
  public boolean setRights(String resourceUrn, ResourcePrivileges resourcePrivileges, Scope scope) {
    return false;
  }

  @Override
  public List<ExternalAuthenticationCredentials.CredentialInfo> getCredentialInfo(Scope scope) {
    return List.of();
  }

  @Override
  public ExternalAuthenticationCredentials.CredentialInfo addCredentials(
      String host, ExternalAuthenticationCredentials credentials, Scope scope) {
    return null;
  }

  @Override
  public <T extends Serializable> T retrieveAsset(
      String urn, Scheduler.Event locator, Class<T> assetClass, Scope scope) {
    return null;
  }

  @Override
  public boolean loadResources(ResourceSet resourceSet, Scope scope) {
    return false;
  }

  @Override
  public InputStream exportAsset(
      String urn,
      KlabAsset.KnowledgeClass knowledgeClass,
      String mediaType,
      Parameters<String> parameters,
      Scope scope) {
    return null;
  }

  @Override
  public CompletableFuture<ResourceSet> importAsset(
      ResourceTransport.Schema schema,
      ResourceTransport.Schema.Asset assetCoordinates,
      String suggestedUrn,
      Scope scope) {
    return null;
  }

  @Override
  public <T extends KlabAsset> T retrieve(String urn, Class<T> assetClass, UserScope scope) {
    return null;
  }

  @Override
  public List<ResourceSet> delete(
      String urn, KlabAsset.KnowledgeClass knowledgeClass, UserScope scope) {
    return List.of();
  }

  @Override
  public <T extends KlabAsset> List<T> list(Class<T> assetClass, UserScope scope) {
    return List.of();
  }

  @Override
  public ResourceSet resolve(String urn, KlabAsset.KnowledgeClass assetClass, UserScope scope) {
    return null;
  }

  @Override
  public <T extends KlabAsset> List<ResourceSet> submit(
      T asset, SubmissionMode submissionMode, UserScope scope) {
    return List.of();
  }

  @Override
  public <T> T info(
      String urn, KlabAsset.KnowledgeClass assetClass, Class<T> infoClass, UserScope scope) {
    return null;
  }

  @Override
  public List<ResourceSet> resolveProjects(Collection<String> projects, Scope scope) {
    return List.of();
  }

  @Override
  public ResourceSet resolveModel(String modelName, Scope scope) {
    return null;
  }

  @Override
  public ResourceSet resolve(String urn, Scope scope) {
    return null;
  }

  @Override
  public KimNamespace retrieveNamespace(String urn, Scope scope) {
    return null;
  }

  @Override
  public KimOntology retrieveOntology(String urn, Scope scope) {
    return null;
  }

  @Override
  public KimObservationStrategyDocument retrieveObservationStrategyDocument(
      String urn, Scope scope) {
    return null;
  }

  @Override
  public Collection<Workspace> listWorkspaces() {
    return List.of();
  }

  @Override
  public KActorsBehavior retrieveBehavior(String urn, Scope scope) {
    return null;
  }

  @Override
  public Resource retrieveResource(List<String> urns, Scope scope) {
    return null;
  }

  @Override
  public Workspace retrieveWorkspace(String urn, Scope scope) {
    return null;
  }

  @Override
  public ResourceSet resolveResourceAdapter(String urn, Scope scope) {
    return null;
  }

  @Override
  public ResourceSet resolveImportSchema(String mediaType, Geometry geometry, Scope scope) {
    return null;
  }

  @Override
  public ResourceSet resolveExportSchema(String mediaType, Geometry geometry, Scope scope) {
    return null;
  }

  @Override
  public ResourceSet resolveServiceCall(String name, Version version, Scope scope) {
    return null;
  }

  @Override
  public ResourceSet resolveResource(String urn, Scope scope) {
    return null;
  }

  @Override
  public Resource contextualizeResource(Resource resource, Geometry geometry, Scope scope) {
    return null;
  }

  @Override
  public ResourceInfo resourceInfo(String urn, Scope scope) {
    return null;
  }

  @Override
  public boolean setResourceInfo(String urn, ResourceInfo info, Scope scope) {
    return false;
  }

  @Override
  public KimObservable declareObservable(String definition) {
    return null;
  }

  @Override
  public KimConcept.Descriptor describeConcept(String conceptUrn) {
    return null;
  }

  @Override
  public KimConcept declareConcept(String definition) {
    return null;
  }

  @Override
  public CompletableFuture<Data> contextualize(
      Resource contextualizedResource,
      Observation observation,
      Geometry geometry,
      Scheduler.Event event,
      Data input,
      Scope scope) {
    return null;
  }

  @Override
  public KimObservationStrategyDocument retrieveDataflow(String urn, Scope scope) {
    return null;
  }

  @Override
  public Worldview retrieveWorldview() {
    return null;
  }

  @Override
  public List<String> dependents(String namespaceId) {
    return List.of();
  }

  @Override
  public AdapterDescriptor retrieveAdapterInfo(String adapterType, Scope scope) {
    return null;
  }

  @Override
  public List<String> precursors(String namespaceId) {
    return List.of();
  }

  @Override
  public List<ResourceInfo> queryResources(
      String queryString, Scope scope, KlabAsset.KnowledgeClass... resourceTypes) {
    return List.of();
  }

  @Override
  public Future<ResourceSet> importResource(Resource resource, UserScope scope) {
    return null;
  }

  @Override
  public Project retrieveProject(String projectName, Scope scope) {
    return null;
  }

  @Override
  public ResourceSet resolveModels(Observable observable, ContextScope scope) {
    return null;
  }

  @Override
  public Coverage modelGeometry(String modelUrn) throws KlabIllegalArgumentException {
    return null;
  }

  @Override
  public KActorsBehavior readBehavior(URL url, UserScope scope) {
    return null;
  }

  @Override
  public ResourceInfo registerResource(
      String urn,
      KlabAsset.KnowledgeClass knowledgeClass,
      File fileLocation,
      ResourcePrivileges rights,
      Scope submittingScope) {
    return null;
  }

  @Override
  public boolean createWorkspace(String workspace, Metadata metadata, UserScope scope) {
    return false;
  }

  @Override
  public ResourceSet createProject(String workspaceName, String projectName, UserScope scope) {
    return null;
  }

  @Override
  public ResourceSet updateProject(
      String projectName, Project.Manifest manifest, Metadata metadata, UserScope scope) {
    return null;
  }

  @Override
  public List<ResourceSet> createDocument(
      String projectName,
      String documentUrn,
      ProjectStorage.ResourceType documentType,
      UserScope scope) {
    return List.of();
  }

  @Override
  public List<ResourceSet> updateDocument(
      String projectName,
      ProjectStorage.ResourceType documentType,
      String content,
      UserScope scope) {
    return List.of();
  }

  @Override
  public List<ResourceSet> manageRepository(
      String projectName, RepositoryState.Operation operation, String... arguments) {
    return List.of();
  }

  @Override
  public List<ResourceSet> deleteDocument(
      String projectName,
      String assetUrn,
      ProjectStorage.ResourceType documentType,
      UserScope scope) {
    return List.of();
  }

  @Override
  public CompletableFuture<Resource> publishObservation(
      Observation observation, ContextScope scope) {
    return null;
  }

  @Override
  public List<ResourceSet> deleteProject(String projectName, UserScope scope) {
    return List.of();
  }

  @Override
  public List<ResourceSet> deleteWorkspace(String workspaceName, UserScope scope) {
    return List.of();
  }

  @Override
  public Collection<Project> listProjects(Scope scope) {
    return List.of();
  }

  @Override
  public Collection<String> listResourceUrns(Scope scope) {
    return List.of();
  }

  @Override
  public boolean lockProject(String urn, UserScope scope) {
    return false;
  }

  @Override
  public boolean unlockProject(String urn, UserScope scope) {
    return false;
  }
}
