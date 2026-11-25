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
import org.integratedmodelling.klab.api.lang.kim.impl.KimOntologyImpl;
import org.integratedmodelling.klab.api.scope.UserScope;
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
    mvc = MockMvcBuilders.standaloneSetup(new WorkspacesController(server, languageServers)).build();
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
  void sourceRequiresIdentityAndMembershipInTheRequestedWorkspace() throws Exception {
    mvc.perform(get(SOURCE).param("workspace", "work").param("project", "another")
        .param("document", "ONTOLOGY:demo")).andExpect(status().isUnauthorized());
    verifyNoInteractions(service);

    when(service.retrieveWorkspace("work", scope)).thenReturn(workspace());
    mvc.perform(get(SOURCE).param("workspace", "work").param("project", "another")
        .param("document", "ONTOLOGY:demo").principal(principal)).andExpect(status().isNotFound());
  }

  @Test
  void staleSourceCannotOverwriteCurrentSource() throws Exception {
    when(service.retrieveWorkspace("work", scope)).thenReturn(workspace());
    var manager = mock(WorkspaceManager.class);
    when(service.getWorkspaceManager()).thenReturn(manager);
    when(manager.withProjectLock(eq("project"), same(scope), any()))
        .thenAnswer(call -> call.<Supplier<?>>getArgument(2).get());
    mvc.perform(put(SOURCE).param("workspace", "work").param("project", "project")
        .param("document", "ONTOLOGY:demo").principal(principal)
        .contentType(MediaType.APPLICATION_JSON).content("""
            {"revision":"stale","text":"ontology demo"}
            """))
        .andExpect(status().isConflict());
    verify(service, never()).updateDocument(any(), any(), any(), any(), anyBoolean(), any());
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
