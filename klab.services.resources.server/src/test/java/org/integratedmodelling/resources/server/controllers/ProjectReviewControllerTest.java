package org.integratedmodelling.resources.server.controllers;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.integratedmodelling.klab.api.authentication.CRUDOperation;
import org.integratedmodelling.klab.api.identities.UserIdentity;
import org.integratedmodelling.klab.api.scope.UserScope;
import org.integratedmodelling.klab.services.application.security.EngineAuthorization;
import org.integratedmodelling.klab.services.resources.ResourcesProvider;
import org.integratedmodelling.klab.services.resources.storage.WorkspaceManager;
import org.integratedmodelling.resources.server.ResourcesServer;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class ProjectReviewControllerTest {
  @Test
  void catalogueKeepsReaderCommentsAndMaterialPermissionsSeparate() throws Exception {
    var server = mock(ResourcesServer.class);
    var resources = mock(ResourcesProvider.class);
    var manager = mock(WorkspaceManager.class);
    var scope = mock(UserScope.class);
    var principal = mock(EngineAuthorization.class);
    when(server.klabService()).thenReturn(resources);
    when(resources.getWorkspaceManager()).thenReturn(manager);
    when(principal.getScope()).thenReturn(scope);
    when(scope.getUser()).thenReturn(mock(UserIdentity.class));
    when(resources.canReadWorkspaceProject("local", "demo", scope)).thenReturn(true);
    when(manager.projectReviewStaging("demo", scope))
        .thenReturn(new WorkspaceManager.ReviewStaging(true, true));
    var mvc = MockMvcBuilders.standaloneSetup(new ProjectReviewController(server)).build();
    String endpoint = "/api/v1/project-review";
    mvc.perform(get(endpoint).param("workspace", "local").param("project", "private").principal(principal))
        .andExpect(status().isNotFound());
    verifyNoInteractions(manager);
    var readable = get(endpoint).param("workspace", "local").param("project", "demo").principal(principal);
    mvc.perform(readable).andExpect(status().isOk())
        .andExpect(jsonPath("$.canComment").value(true)).andExpect(jsonPath("$.canPropose").value(false))
        .andExpect(jsonPath("$.canUpload").value(false));
    when(resources.canProjectOperation("demo", CRUDOperation.UPDATE_METADATA, scope)).thenReturn(true);
    mvc.perform(readable).andExpect(status().isOk())
        .andExpect(jsonPath("$.canPropose").value(true)).andExpect(jsonPath("$.canUpload").value(false));
    when(resources.canProjectOperation("demo", CRUDOperation.UPDATE, scope)).thenReturn(true);
    mvc.perform(readable).andExpect(status().isOk()).andExpect(jsonPath("$.canUpload").value(true));
    when(manager.projectReviewStaging("demo", scope))
        .thenReturn(new WorkspaceManager.ReviewStaging(true, false));
    mvc.perform(readable).andExpect(status().isOk())
        .andExpect(jsonPath("$.canComment").value(true)).andExpect(jsonPath("$.canPropose").value(false))
        .andExpect(jsonPath("$.canUpload").value(false));
  }
}
