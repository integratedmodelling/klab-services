package org.integratedmodelling.resources.server.controllers;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

import java.util.List;
import java.util.function.Supplier;
import org.integratedmodelling.klab.api.authentication.CRUDOperation;
import org.integratedmodelling.klab.api.data.RepositoryState;
import org.integratedmodelling.klab.api.exceptions.KlabResourceAccessException;
import org.integratedmodelling.klab.api.identities.UserIdentity;
import org.integratedmodelling.klab.api.knowledge.organization.impl.ProjectImpl;
import org.integratedmodelling.klab.api.knowledge.organization.Workspace;
import org.integratedmodelling.klab.api.knowledge.organization.impl.WorkspaceImpl;
import org.integratedmodelling.klab.api.lang.kactors.KActorsBehavior;
import org.integratedmodelling.klab.api.lang.kactors.impl.KActorsActionImpl;
import org.integratedmodelling.klab.api.lang.kactors.impl.KActorsBehaviorImpl;
import org.integratedmodelling.klab.api.lang.kim.impl.KimConceptStatementImpl;
import org.integratedmodelling.klab.api.lang.kim.impl.KimModelImpl;
import org.integratedmodelling.klab.api.lang.kim.impl.KimNamespaceImpl;
import org.integratedmodelling.klab.api.lang.kim.impl.KimOntologyImpl;
import org.integratedmodelling.klab.api.scope.UserScope;
import org.integratedmodelling.klab.api.services.Reasoner;
import org.integratedmodelling.klab.api.services.reasoner.objects.SemanticValidationRequest;
import org.integratedmodelling.klab.api.services.reasoner.objects.SemanticValidationResponse;
import org.integratedmodelling.klab.api.services.runtime.Notification;
import org.integratedmodelling.klab.api.services.runtime.impl.NotificationImpl;
import org.integratedmodelling.klab.services.application.security.EngineAuthorization;
import org.integratedmodelling.klab.services.resources.ResourcesProvider;
import org.integratedmodelling.klab.services.resources.storage.WorkspaceManager;
import org.integratedmodelling.resources.server.ResourcesServer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class WorkspacesControllerTest {
  private static final String SOURCE = "/api/v1/workspaces/source";
  private final ResourcesProvider service = mock(ResourcesProvider.class);
  private final UserScope scope = mock(UserScope.class);
  private final EngineAuthorization principal = mock(EngineAuthorization.class);
  private final WorkspaceLanguageServers languageServers = mock(WorkspaceLanguageServers.class);
  private MockMvc mvc;

  @BeforeEach
  void setup() {
    var server = mock(ResourcesServer.class);
    when(server.klabService()).thenReturn(service);
    when(principal.getScope()).thenReturn(scope);
    when(scope.getUser()).thenReturn(mock(UserIdentity.class));
    mvc = MockMvcBuilders.standaloneSetup(new WorkspacesController(server, languageServers, 900)).build();
  }

  @Test
  void catalogAndProjectExposeGitStateForTreeDecoration() throws Exception {
    var workspace = workspace();
    var repository = new RepositoryState();
    repository.setOverallStatus(RepositoryState.Status.CONFLICTED);
    repository.getUncommittedPaths().addAll(List.of("src/demo.kwv", "scripts/staged.kactors"));
    repository.getAddedPaths().add("scripts/staged.kactors");
    repository.getConflictingPaths().add("src/demo.kwv");
    repository.getUntrackedFolders().add("behaviors/new");
    ((ProjectImpl) workspace.getProjects().iterator().next()).setRepositoryState(repository);
    var manager = mock(WorkspaceManager.class);
    when(service.getWorkspaceManager()).thenReturn(manager);
    when(manager.projectLockStatus("project", scope))
        .thenReturn(new WorkspaceManager.ProjectLockStatus(true, false, false, null, null));
    when(service.list(Workspace.class, scope)).thenReturn(List.of(workspace));
    when(service.retrieveWorkspace("work", scope)).thenReturn(workspace);

    mvc.perform(get("/api/v1/workspaces").principal(principal)).andExpect(status().isOk())
        .andExpect(jsonPath("$[0].repositoryStatuses.project").value("CONFLICTED"));
    mvc.perform(get("/api/v1/workspaces/project").param("workspace", "work")
        .param("project", "project").principal(principal)).andExpect(status().isOk())
        .andExpect(jsonPath("$.repository.files['src/demo.kwv']").value("CONFLICTED"))
        .andExpect(jsonPath("$.repository.files['scripts/staged.kactors']").value("ADDED"))
        .andExpect(jsonPath("$.repository.untrackedFolders[0]").value("behaviors/new"));
  }

  @Test
  void navigationRetainsNestedConceptsModelsAndBehaviorActions() throws Exception {
    var visible = workspace();
    var project = (ProjectImpl) visible.getProjects().iterator().next();
    var ontology = (KimOntologyImpl) project.getOntologies().getFirst();
    ontology.setLastUpdateTimestamp(1200);
    var parent = new KimConceptStatementImpl();
    parent.setUrn("Habitat");
    var child = new KimConceptStatementImpl();
    child.setUrn("Wetland");
    child.setOffsetInDocument(36);
    child.setLength(7);
    parent.setChildren(List.of(child));
    ontology.setStatements(List.of(parent));
    var namespace = new KimNamespaceImpl();
    namespace.setUrn("landscape");
    var model = new KimModelImpl();
    model.setUrn("cover");
    namespace.setStatements(List.of(model));
    project.getNamespaces().add(namespace);
    var behavior = new KActorsBehaviorImpl();
    behavior.setUrn("monitor");
    behavior.setBehaviorType(KActorsBehavior.Type.BEHAVIOR);
    var action = new KActorsActionImpl();
    action.setUrn("sample");
    behavior.setStatements(List.of(action));
    project.getBehaviors().add(behavior);
    when(service.retrieveWorkspace("work", scope)).thenReturn(visible);

    var endpoint = "/api/v1/workspaces/navigation";
    mvc.perform(get(endpoint).param("workspace", "work").principal(principal))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].children[0].document.id").value("ONTOLOGY:demo"))
        .andExpect(jsonPath("$[0].children[0].document.sourceVersion").value("1200"))
        .andExpect(jsonPath("$[0].children[0].children[0].children[0].label").value("Wetland"))
        .andExpect(jsonPath("$[0].children[0].children[0].children[0].offset").value(36))
        .andExpect(jsonPath("$[0].children[0].children[0].children[0].length").value(7))
        .andExpect(jsonPath("$[0].children[0].document.text").doesNotExist())
        .andExpect(jsonPath("$[0].children[1].children[0].kind").value("model"))
        .andExpect(jsonPath("$[0].children[2].label").value("Behaviors"))
        .andExpect(jsonPath("$[0].children[2].children[0].children[0].kind").value("action"));
    verify(service, times(1)).retrieveWorkspace("work", scope);
    verifyNoMoreInteractions(service);
    mvc.perform(get(endpoint).param("workspace", "unavailable").principal(principal))
        .andExpect(status().isNotFound());
  }

  @Test
  void sourceRequiresIdentityAndMembershipInTheRequestedWorkspace() throws Exception {
    mvc.perform(get(SOURCE).param("workspace", "work").param("project", "another")
        .param("document", "ONTOLOGY:demo")).andExpect(status().isUnauthorized());
    verifyNoInteractions(service);

    when(service.retrieveWorkspace("work", scope)).thenReturn(workspace());
    mvc.perform(get(SOURCE).param("workspace", "work").param("project", "another")
        .param("document", "ONTOLOGY:demo").principal(principal)).andExpect(status().isNotFound());
  }

  @Test
  void explicitSemanticChecksUseTheUsersReasonerAndReturnSourceLocations() throws Exception {
    var visible = workspace();
    var project = (ProjectImpl) visible.getProjects().iterator().next();
    var namespace = new KimNamespaceImpl();
    namespace.setUrn("landscape");
    namespace.setSourceCode("namespace landscape;");
    project.getNamespaces().add(namespace);
    when(service.retrieveWorkspace("work", scope)).thenReturn(visible);
    var reasoner = mock(Reasoner.class);
    when(scope.getService(Reasoner.class)).thenReturn(reasoner);
    when(reasoner.validateDocument(any(), same(scope))).thenAnswer(call -> {
      var request = call.<SemanticValidationRequest>getArgument(0);
      var result = SemanticValidationResponse.forRequest(request);
      result.setStatus(SemanticValidationResponse.Status.COMPLETE);
      var location = new NotificationImpl.LexicalContextImpl();
      location.setOffsetInDocument(4);
      location.setLength(5);
      result.getNotifications().add(Notification.create(Notification.Level.Warning, "Semantic finding", location));
      return result;
    });
    for (var item : project.allDocuments()) {
      var id = org.integratedmodelling.klab.api.knowledge.organization.ProjectStorage.ResourceType.classify(item)
          + ":" + item.getUrn();
      var revision = SemanticValidationRequest.sourceHash(item.getUrn() + "\u0000" + item.getSourceCode());
      mvc.perform(get(SOURCE + "/semantics").param("workspace", "work").param("project", "project")
              .param("document", id).param("revision", revision).principal(principal))
          .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("COMPLETE"))
          .andExpect(jsonPath("$.diagnostics[0].message").value("Semantic finding"))
          .andExpect(jsonPath("$.diagnostics[0].offset").value(4))
          .andExpect(jsonPath("$.diagnostics[0].length").value(5));
      verify(reasoner).validateDocument(argThat(request -> request.document() == item), same(scope));
    }
    mvc.perform(get(SOURCE + "/semantics").param("workspace", "work").param("project", "project")
            .param("document", "ONTOLOGY:demo").param("revision", "older").principal(principal))
        .andExpect(status().isConflict());
    verifyNoMoreInteractions(reasoner);
  }

  @Test
  void staleSourceCannotOverwriteCurrentSource() throws Exception {
    when(service.retrieveWorkspace("work", scope)).thenReturn(workspace());
    var manager = mock(WorkspaceManager.class);
    when(service.getWorkspaceManager()).thenReturn(manager);
    when(manager.withOwnedProjectLock(eq("project"), same(scope), any()))
        .thenAnswer(call -> call.<Supplier<?>>getArgument(2).get());
    mvc.perform(put(SOURCE).param("workspace", "work").param("project", "project")
        .param("document", "ONTOLOGY:demo").principal(principal)
        .contentType(MediaType.APPLICATION_JSON).content("""
            {"revision":"stale","text":"ontology demo","reviewInputs":[]}
            """))
        .andExpect(status().isConflict());
    verify(service, never()).updateDocument(any(), any(), any(), any(), anyBoolean(), any());
    verify(manager, never()).prepareReviewApplication(any(), any(), any(), any(), any(), any());
  }

  @Test
  void savingRequiresOwnedLockBeforeInvokingSourceUpdate() throws Exception {
    when(service.retrieveWorkspace("work", scope)).thenReturn(workspace());
    var manager = mock(WorkspaceManager.class);
    when(service.getWorkspaceManager()).thenReturn(manager);
    when(manager.withOwnedProjectLock(eq("project"), same(scope), any()))
        .thenThrow(new KlabResourceAccessException("An owned project lock is required"));
    mvc.perform(put(SOURCE).param("workspace", "work").param("project", "project")
            .param("document", "ONTOLOGY:demo").principal(principal)
            .contentType(MediaType.APPLICATION_JSON).content("{\"revision\":\"one\",\"text\":\"ontology demo\"}"))
        .andExpect(status().isForbidden());
    verify(service, never()).updateDocument(any(), any(), any(), any(), anyBoolean(), any());
  }

  @Test
  void anOwnedSourceSaveDispatchesExactlyOneUpdate() throws Exception {
    verifySourceSave(false);
  }

  @Test
  void anOwnedCandidateApplyUpdatesOnceAndRecordsTheResultingRevision() throws Exception {
    verifySourceSave(true);
  }

  private void verifySourceSave(boolean applyCandidate) throws Exception {
    var workspace = workspace();
    var ontology = (KimOntologyImpl) workspace.getProjects().iterator().next().getOntologies().getFirst();
    var version = org.integratedmodelling.klab.api.services.reasoner.objects.SemanticValidationRequest
        .sourceHash("demo\u0000" + ontology.getSourceCode());
    var original = ontology.getSourceCode();
    var changed = original + "\n";
    when(service.retrieveWorkspace("work", scope)).thenReturn(workspace);
    var manager = mock(WorkspaceManager.class);
    var application = mock(WorkspaceManager.ReviewApplication.class);
    var expected = SemanticValidationRequest.sourceHash("demo\u0000" + changed);
    when(service.getWorkspaceManager()).thenReturn(manager);
    when(manager.withOwnedProjectLock(eq("project"), same(scope), any()))
        .thenAnswer(call -> call.<Supplier<?>>getArgument(2).get());
    when(manager.prepareReviewApplication("project", "demo", version, original, List.of(), scope)).thenReturn(application);
    when(service.updateDocument(eq("project"), eq("demo"), any(), eq(changed), eq(false), same(scope)))
        .thenAnswer(call -> {
          ontology.setSourceCode(changed);
          return List.of();
        });
    mvc.perform(put(SOURCE).param("workspace", "work").param("project", "project")
            .param("document", "ONTOLOGY:demo").principal(principal)
            .contentType(MediaType.APPLICATION_JSON)
            .content(new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(
                new WorkspacesController.SourceEdit(version, changed, applyCandidate ? List.of() : null))))
        .andExpect(status().isOk()).andExpect(jsonPath("$.source.text").value(changed))
        .andExpect(jsonPath("$.source.revision").value(expected));
    if (applyCandidate) {
      var order = inOrder(manager, service, application);
      order.verify(manager).prepareReviewApplication("project", "demo", version, original, List.of(), scope);
      order.verify(service).updateDocument(eq("project"), eq("demo"), any(), eq(changed), eq(false), same(scope));
      order.verify(application).record(expected, changed);
    } else {
      verify(manager, never()).prepareReviewApplication(any(), any(), any(), any(), any(), any());
      verifyNoInteractions(application);
    }
    verify(service, times(1)).updateDocument(any(), any(), any(), any(), anyBoolean(), any());
    verify(service, times(3)).retrieveWorkspace("work", scope);
  }

  @Test
  void doesNotRecordAnApplicationWhenTheSaveDidNotPersistTheCandidate() throws Exception {
    var workspace = workspace();
    var ontology = workspace.getProjects().iterator().next().getOntologies().getFirst();
    var base = SemanticValidationRequest.sourceHash("demo\u0000" + ontology.getSourceCode());
    when(service.retrieveWorkspace("work", scope)).thenReturn(workspace);
    var manager = mock(WorkspaceManager.class);
    var application = mock(WorkspaceManager.ReviewApplication.class);
    when(service.getWorkspaceManager()).thenReturn(manager);
    when(manager.withOwnedProjectLock(eq("project"), same(scope), any()))
        .thenAnswer(call -> call.<Supplier<?>>getArgument(2).get());
    when(manager.prepareReviewApplication("project", "demo", base, ontology.getSourceCode(), List.of(), scope)).thenReturn(application);
    var edit = new WorkspacesController.SourceEdit(base, ontology.getSourceCode() + "\n", List.of());
    mvc.perform(put(SOURCE).param("workspace", "work").param("project", "project")
            .param("document", "ONTOLOGY:demo").principal(principal)
            .contentType(MediaType.APPLICATION_JSON)
            .content(new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(edit)))
        .andExpect(status().isOk()).andExpect(jsonPath("$.source").doesNotExist());
    verify(manager).prepareReviewApplication("project", "demo", base, ontology.getSourceCode(), List.of(), scope);
    verifyNoInteractions(application);
  }

  @Test
  void acquiringAndReadingALockDoesNotLoadTheWorkspaceTree() throws Exception {
    var manager = mock(WorkspaceManager.class);
    when(service.getWorkspaceManager()).thenReturn(manager);
    when(service.canReadWorkspaceProject("work", "project", scope)).thenReturn(true);
    when(service.canProjectOperation("project", CRUDOperation.UPDATE, scope)).thenReturn(true);
    when(manager.projectLockStatus("project", scope))
        .thenReturn(new WorkspaceManager.ProjectLockStatus(true, false, false, null, null),
            new WorkspaceManager.ProjectLockStatus(true, true, true, 900000L, 900000L));
    when(manager.acquireProjectEditLease("project", scope, 900000L)).thenReturn(true);
    String path = "/api/v1/workspaces/project/lock";
    mvc.perform(get(path).param("workspace", "work").param("project", "project").principal(principal))
        .andExpect(status().isOk()).andExpect(jsonPath("$.editable").value(false))
        .andExpect(jsonPath("$.canLock").value(true));
    mvc.perform(post(path).param("workspace", "work").param("project", "project").principal(principal))
        .andExpect(status().isOk()).andExpect(jsonPath("$.editable").value(true))
        .andExpect(jsonPath("$.idleTimeoutMillis").value(900000));
    mvc.perform(post(org.integratedmodelling.klab.api.ServicesAPI.RESOURCES.PROJECT_EDIT_LOCK)
            .principal(principal).contentType(MediaType.APPLICATION_JSON)
            .content("{\"workspace\":\"work\",\"project\":\"project\",\"action\":\"STATUS\"}"))
        .andExpect(status().isOk()).andExpect(jsonPath("$.owned").value(true));
    verify(service, never()).retrieveWorkspace(any(), any());
  }

  @Test
  void readOnlyUserCannotSubmitDraftForParsing() throws Exception {
    when(service.retrieveWorkspace("work", scope)).thenReturn(workspace());
    doThrow(new KlabResourceAccessException("Read only")).when(service)
        .requireProjectOperation("project", CRUDOperation.UPDATE, scope);
    mvc.perform(post(SOURCE + "/diagnostics").param("workspace", "work").param("project", "project")
        .param("document", "ONTOLOGY:demo").principal(principal)
        .contentType(MediaType.APPLICATION_JSON).content("{\"text\":\"ontology demo\"}"))
        .andExpect(status().isForbidden());
    verify(service, never()).getWorkspaceManager();
  }

  @Test
  void readOnlyUserCannotStartALanguageServer() throws Exception {
    when(service.retrieveWorkspace("work", scope)).thenReturn(workspace());
    doThrow(new KlabResourceAccessException("Read only")).when(service)
        .requireProjectOperation("project", CRUDOperation.UPDATE, scope);
    mvc.perform(post(SOURCE + "/lsp").param("workspace", "work").param("project", "project")
        .param("document", "ONTOLOGY:demo").principal(principal)
        .contentType(MediaType.APPLICATION_JSON).content("{\"version\":1,\"text\":\"ontology demo\"}"))
        .andExpect(status().isForbidden());
    verifyNoInteractions(languageServers);
  }

  private WorkspaceImpl workspace() {
    var ontology = new KimOntologyImpl();
    ontology.setUrn("demo");
    ontology.setSourceCode("ontology demo in domain root version 1.0;");
    var project = new ProjectImpl();
    project.setUrn("project");
    project.getOntologies().add(ontology);
    var workspace = new WorkspaceImpl();
    workspace.setUrn("work");
    workspace.setProjects(List.of(project));
    return workspace;
  }
}
