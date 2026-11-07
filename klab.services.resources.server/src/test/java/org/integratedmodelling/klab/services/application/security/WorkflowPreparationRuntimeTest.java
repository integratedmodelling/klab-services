package org.integratedmodelling.klab.services.application.security;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.*;
import org.integratedmodelling.common.authentication.UserIdentityImpl;
import org.integratedmodelling.common.utils.Utils;
import org.integratedmodelling.klab.api.ServicesAPI;
import org.integratedmodelling.klab.api.authentication.CRUDOperation;
import org.integratedmodelling.klab.api.authentication.CustomProperty;
import org.integratedmodelling.klab.api.identities.Group;
import org.integratedmodelling.klab.api.knowledge.KlabAsset;
import org.integratedmodelling.klab.api.knowledge.organization.ProjectMaterial;
import org.integratedmodelling.klab.api.lang.kactors.KActorsBehavior;
import org.integratedmodelling.klab.api.lang.kim.KimOntology;
import org.integratedmodelling.klab.api.lang.kim.impl.KimOntologyImpl;
import org.integratedmodelling.klab.api.scope.UserScope;
import org.integratedmodelling.klab.api.services.KlabService;
import org.integratedmodelling.klab.api.services.resources.ResourceInfo;
import org.integratedmodelling.klab.api.services.resources.workflow.*;
import org.integratedmodelling.klab.components.ComponentRegistry;
import org.integratedmodelling.klab.resources.WorkflowStore;
import org.integratedmodelling.klab.runtime.kactors.compiler.AgentCompiler;
import org.integratedmodelling.klab.services.application.controllers.KlabErrorHandler;
import org.integratedmodelling.klab.services.resources.storage.WorkspaceManager;
import org.integratedmodelling.klab.services.resources.ResourcesProvider;
import org.integratedmodelling.klab.services.resources.workflow.*;
import org.integratedmodelling.klab.services.scopes.ServiceUserScope;
import org.integratedmodelling.resources.server.ResourcesServer;
import org.integratedmodelling.resources.server.controllers.WorkflowController;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/** Production parser, request scope, component resolver, compiler and action, with test-only storage. */
class WorkflowPreparationRuntimeTest {
  static final String SOURCE = "ontology test.ontology;\nthing Sample;\n";

  @Test
  void advertisedScopeDiscoversAndExecutesPreparationAndReloadsPublishedArtifacts() throws Exception {
    productionControllerDiscoversAndExecutesPreparationAndReloadsPublishedArtifacts(true);
  }

  @Test
  void browserScopeDiscoversAndExecutesPreparationAndReloadsPublishedArtifacts() throws Exception {
    productionControllerDiscoversAndExecutesPreparationAndReloadsPublishedArtifacts(false);
  }

  void productionControllerDiscoversAndExecutesPreparationAndReloadsPublishedArtifacts(boolean advertiseHost) throws Exception {
    var f = new Fixture(advertiseHost);
    var flow = f.create();
    var stage = flow.getCurrentStateIds().iterator().next();
    String actions = ServicesAPI.RESOURCES.FLOW_ACTIONS.replace("{flowId}", flow.getId()).replace("{stateId}", stage);
    var discovered = f.mvc.perform(get(actions).principal(f.principal)).andExpect(status().isOk()).andReturn().getResponse();
    var json = Utils.Json.parseObject(discovered.getContentAsString(), List.class);
    assertEquals(1, json.size());
    assertEquals(6, ((List<?>) ((Map<?, ?>) json.getFirst()).get("missing")).size());
    String execute = ServicesAPI.RESOURCES.FLOW_ACTION.replace("{flowId}", flow.getId())
        .replace("{stateId}", stage).replace("{actionId}", "prepare-review-draft");
    var request = new WorkflowBehavior.ActionRequest(flow.getRevision(), Map.of(
        "proposal_id", "test-proposal", "revision_id", "test-r1", "domain", "test domain",
        "purpose", "Review ontology", "requested_tier", 1, "target_community", "test community"));
    var response = f.mvc.perform(post(execute).principal(f.principal).contentType(MediaType.APPLICATION_JSON)
        .content(Utils.Json.asString(request))).andExpect(status().isOk()).andReturn().getResponse();
    var updated = Utils.Json.parseObject(response.getContentAsString(), Flow.class);
    assertEquals(flow.getRevision() + 1, updated.getRevision());
    var attachments = updated.getStates().get(stage).getAttachments();
    assertEquals(5, attachments.size());
    var proposal = attachments.stream().filter(a -> "bootstrap-proposal".equals(a.getType())).findFirst().orElseThrow();
    assertEquals(ProposalReview.CheckStatus.PASS, ProposalSchemaValidator.bundled().validate(f.store.blobs.get(proposal.getId())).status());
    var candidate = attachments.stream().filter(a -> "candidate-ontology".equals(a.getType())).findFirst().orElseThrow();
    assertEquals(SOURCE, new String(f.store.blobs.get(candidate.getId()), StandardCharsets.UTF_8));
    assertNotNull(updated.getStates().get(stage).getMetadata().get("ontologyReviewBootstrap"));
    String reload = ServicesAPI.RESOURCES.FLOW.replace("{flowId}", flow.getId());
    var loaded = f.mvc.perform(get(reload).principal(f.principal)).andExpect(status().isOk()).andReturn().getResponse();
    assertEquals(5, Utils.Json.parseObject(loaded.getContentAsString(), Flow.class).getStates().get(stage).getAttachments().size());
    // A fresh manager reads the same stored output; stale replay cannot duplicate it.
    var restarted = new WorkflowManager(f.store);
    assertEquals(5, restarted.getFlow(flow.getId(), f.scope).getStates().get(stage).getAttachments().size());
    assertArrayEquals(f.store.blobs.get(proposal.getId()), restarted.getAttachment(flow.getId(), proposal.getId(), f.scope));
    f.mvc.perform(post(execute).principal(f.principal).contentType(MediaType.APPLICATION_JSON)
        .content(Utils.Json.asString(request))).andExpect(status().isConflict());
    assertEquals(5, f.store.blobs.size());
    verify(f.provider, atLeastOnce()).info("test-project", KlabAsset.KnowledgeClass.PROJECT, ResourceInfo.class, f.scope);
  }

  @Test void browserSessionWithoutEngineAdvertisementCanDiscoverPreparation() throws Exception {
    var f = new Fixture(false);
    var flow = f.create();
    var stage = flow.getCurrentStateIds().iterator().next();
    String path = ServicesAPI.RESOURCES.FLOW_ACTIONS.replace("{flowId}", flow.getId()).replace("{stateId}", stage);
    var response = f.mvc.perform(get(path).principal(f.principal)).andReturn().getResponse();
    assertEquals(200, response.getStatus(), response.getContentAsString());
  }
  static class Fixture {
    final Store store;
    final boolean advertised;
    final ResourcesProvider provider = mock(ResourcesProvider.class, CALLS_REAL_METHODS);
    final WorkflowManager manager;
    final ServiceUserScope scope;
    final EngineAuthorization principal;
    final MockMvc mvc;
    Fixture(boolean advertiseHost) throws Exception { this(advertiseHost, new Store()); }
    Fixture(boolean advertiseHost, Store store) throws Exception {
      this.store = store; this.manager = new WorkflowManager(store); this.advertised = advertiseHost;
      doReturn("test-resources").when(provider).serviceId();
      doReturn(KlabService.Type.RESOURCES).when(provider).serviceType();
      doReturn(true).when(provider).isLocal();
      var status = mock(KlabService.ServiceStatus.class);
      when(status.isAvailable()).thenReturn(true); when(status.isOperational()).thenReturn(true);
      doReturn(status).when(provider).status();
      var user = new UserIdentityImpl(); user.setUsername("editor"); user.setAuthenticated(true); user.setAnonymous(false);
      var group = mock(Group.class); when(group.getId()).thenReturn("editors");
      var roles = new CustomProperty(); roles.setKey(WorkflowParticipant.ROLES_PROPERTY); roles.setValue("EDITOR");
      var permitted = new CustomProperty(); permitted.setKey(WorkflowParticipant.PERMITTED_WORKFLOWS_PROPERTY); permitted.setValue("*");
      when(group.getCustomProperties()).thenReturn(List.of(roles, permitted)); user.getGroups().add(group);
      var managed = new ServiceUserScope(user, provider); managed.setId("webui:editor"); if (advertiseHost) managed.addService(provider);
      if (advertiseHost) {
        principal = new EngineAuthorization("fixture", "editor", "synthetic-test-token", Map.of(), List.of(group), List.of(Role.ROLE_USER));
        principal.setAuthenticated(true); scope = managed.forAuthorization(principal); principal.setScope(scope);
      } else {
        // No engine advertisement or UI application launcher: use the real browser login path.
        var now = java.time.Instant.now();
        var claims = new org.jose4j.jwt.JwtClaims();
        claims.setSubject("editor"); claims.setIssuer("fixture"); claims.setAudience("engine");
        claims.setIssuedAt(org.jose4j.jwt.NumericDate.fromSeconds(now.getEpochSecond()));
        claims.setExpirationTime(org.jose4j.jwt.NumericDate.fromSeconds(now.plusSeconds(900).getEpochSecond()));
        claims.setStringListClaim("roles", List.of("ROLE_USER"));
        var signature = new org.jose4j.jws.JsonWebSignature(); signature.setPayload(claims.toJson());
        signature.setAlgorithmHeaderValue("RS256");
        signature.setKey(org.jose4j.jwk.RsaJwkGenerator.generateJwk(2048).getPrivateKey());
        String jwt = signature.getCompactSerialization();
        var authentication = new HubWebAuthentication(new org.springframework.core.env.StandardEnvironment(),
            java.time.Clock.fixed(now, java.time.ZoneOffset.UTC), (endpoint, accessToken) ->
                new com.fasterxml.jackson.databind.ObjectMapper().valueToTree(Map.of("jwtToken", jwt, "name", "editor")));
        authentication.configureHub("https://hub.example/hub", "fixture");
        var instance = mock(org.integratedmodelling.klab.services.ServiceInstance.class);
        when(instance.klabService()).thenReturn(provider); when(instance.getServiceOwner()).thenReturn(user);
        var login = authentication.exchange("synthetic-access-token", instance);
        principal = authentication.authorize(login.token(), Map.of());
        scope = (ServiceUserScope) principal.getScope();
      }
      ReflectionTestUtils.setField(provider, "workflowManager", manager);
      var info = mock(ResourceInfo.class); when(info.getKnowledgeClass()).thenReturn(KlabAsset.KnowledgeClass.PROJECT);
      when(info.getPermissions()).thenReturn(EnumSet.of(CRUDOperation.READ));      var kbox = mock(org.integratedmodelling.klab.resources.ResourcesKBox.class);
      when(kbox.getStatus("test-project", org.integratedmodelling.klab.api.data.Version.ANY_VERSION)).thenReturn(info);
      ReflectionTestUtils.setField(provider, "resourcesKbox", kbox);
      doReturn(info).when(provider).info(eq("test-project"), eq(KlabAsset.KnowledgeClass.PROJECT), eq(ResourceInfo.class), any(UserScope.class));
      var ontology = new KimOntologyImpl(); ontology.setUrn("test.ontology"); ontology.setProjectName("test-project"); ontology.setSourceCode(SOURCE);
      doReturn(ontology).when(provider).retrieve(eq("test.ontology"), eq(KimOntology.class), any(UserScope.class));
      doReturn(new ProjectMaterial("test-project", "BOOTSTRAP.md", "# Context\nSynthetic rationale".getBytes(StandardCharsets.UTF_8)))
          .when(provider).retrieve(eq("test-project/BOOTSTRAP.md"), eq(ProjectMaterial.class), any(UserScope.class));
      var workspace = mock(WorkspaceManager.class, CALLS_REAL_METHODS);
      ReflectionTestUtils.setField(workspace, "service", provider);
      var parserClass = Class.forName(WorkspaceManager.class.getName() + "$BehaviorParser");
      var parserConstructor = parserClass.getDeclaredConstructor(WorkspaceManager.class);
      parserConstructor.setAccessible(true);
      ReflectionTestUtils.setField(workspace, "behaviorParser", parserConstructor.newInstance(workspace));
      var registryConstructor = Arrays.stream(ComponentRegistry.class.getDeclaredConstructors()).filter(c -> c.getParameterCount() == 4).findFirst().orElseThrow();
      registryConstructor.setAccessible(true);
      var registry = (ComponentRegistry) registryConstructor.newInstance(provider, null, null, List.of());
      registry.loadExtensions("org.integratedmodelling.klab.runtime.libraries");
      manager.setBehaviorBridge(new WorkflowBehaviorBridge(() -> AgentCompiler.componentResolver(registry), owner -> scope,
          () -> WorkflowBehaviorBridge.bundledOntologyReview(source -> workspace.parseAsset(source, KActorsBehavior.class))));
      var server = mock(ResourcesServer.class); when(server.klabService()).thenReturn(provider);
      var controller = new WorkflowController(); ReflectionTestUtils.setField(controller, "resourcesServer", server);
      doCallRealMethod().when(server).objectMapper();
      mvc = MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(new KlabErrorHandler())
          .setMessageConverters(new org.springframework.http.converter.StringHttpMessageConverter(StandardCharsets.UTF_8),
              new org.springframework.http.converter.json.MappingJackson2HttpMessageConverter(server.objectMapper())).build();
    }
    Flow create() throws Exception {
      if (!advertised) {
        var response = mvc.perform(post(ServicesAPI.RESOURCES.FLOW_INITIALIZATION)
            .param("workflowId", "ontology-review-pilot").principal(principal).contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"initialState":{"schemaId":"bootstrap","assetUrn":"test.ontology","assetType":"ONTOLOGY",
                 "title":"Browser preparation","description":"Created through the production JSON binding"},"attachments":[]}
                """))
            .andExpect(status().isOk()).andReturn().getResponse();
        return Utils.Json.parseObject(response.getContentAsString(), Flow.class);
      }
      var initial = Flow.State.create(); initial.setSchemaId("bootstrap"); initial.setAssetUrn("test.ontology");
      initial.setPermissionsOwnerUrn("test.ontology"); initial.setAssetType(KlabAsset.KnowledgeClass.ONTOLOGY);
      return manager.createFlow("ontology-review-pilot", initial, scope);
    }
  }

  static class Store implements WorkflowStore {
    final Map<String, Workflow> workflows = new LinkedHashMap<>();
    final Map<String, Flow> flows = new LinkedHashMap<>();
    final Map<String, byte[]> blobs = new LinkedHashMap<>();
    public boolean putWorkflow(Workflow w) { workflows.put(w.getId() + "@" + w.getVersion(), w); return true; }
    public Workflow getWorkflow(String id) { return workflows.values().stream().filter(w -> w.getId().equals(id)).max((a,b) -> WorkflowVersions.compare(a.getVersion(),b.getVersion())).orElse(null); }
    public Workflow getWorkflow(String id, String version) { return workflows.get(id + "@" + version); }
    public List<Workflow> listWorkflows() { return new ArrayList<>(workflows.values()); }
    public boolean putFlow(Flow f) { flows.put(f.getId(), copy(f)); return true; }
    public boolean compareAndSetFlow(Flow f,long revision) { var current=flows.get(f.getId()); return current != null && current.getRevision()==revision && f.getRevision()==revision+1 && putFlow(f); }
    public boolean deleteFlow(String id) { return flows.remove(id)!=null; }
    public Flow getFlow(String id) { return copy(flows.get(id)); }
    public List<Flow> listFlows() { return flows.values().stream().map(this::copy).toList(); }
    public boolean putWorkflowAttachment(String id,String flow,String stage,byte[] content) { blobs.put(id,content.clone()); return true; }
    public byte[] getWorkflowAttachment(String id) { return blobs.get(id); }
    public boolean deleteWorkflowAttachment(String id) { return blobs.remove(id)!=null; }
    public boolean updateResourceInfoForFlow(Flow f,ResourceInfo.Stage s,int review) { return true; }
    public boolean removeResourceInfoForFlow(Flow f) { return true; }
    Flow copy(Flow f) { return f == null ? null : Utils.Json.parseObject(Utils.Json.asString(f), Flow.class); }
  }
}
