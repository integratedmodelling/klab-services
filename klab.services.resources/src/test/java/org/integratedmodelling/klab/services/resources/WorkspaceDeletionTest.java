package org.integratedmodelling.klab.services.resources;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.google.common.cache.LoadingCache;
import java.util.*;
import org.integratedmodelling.klab.api.authentication.CRUDOperation;
import org.integratedmodelling.klab.api.exceptions.KlabAuthorizationException;
import org.integratedmodelling.klab.api.knowledge.KlabAsset.KnowledgeClass;
import org.integratedmodelling.klab.api.services.resources.ResourceInfo;
import org.integratedmodelling.klab.api.scope.UserScope;
import org.integratedmodelling.klab.resources.ResourcesKBox;
import org.integratedmodelling.klab.services.resources.storage.WorkspaceManager;
import org.junit.jupiter.api.*;

class WorkspaceDeletionTest {
  ResourcesProvider service;
  ResourcesKBox catalog;
  WorkspaceManager manager;
  ResourceInfo info;
  UserScope owner;

  @BeforeEach void setup() throws Exception {
    service = mock(ResourcesProvider.class);
    catalog = mock(ResourcesKBox.class);
    manager = mock(WorkspaceManager.class);
    for (var entry : Map.of("resourcesKbox", catalog, "workspaceManager", manager,
        "concepts", mock(LoadingCache.class), "observables", mock(LoadingCache.class)).entrySet()) {
      var field = ResourcesProvider.class.getDeclaredField(entry.getKey());
      field.setAccessible(true); field.set(service, entry.getValue());
    }
    info = new ResourceInfo(); info.setUrn("owned");
    info.setKnowledgeClass(KnowledgeClass.WORKSPACE); info.setOwner("alice");
    owner = WorkspaceSettingsTest.user("alice");
    when(catalog.getStatus("owned", null)).thenReturn(info);
    doCallRealMethod().when(service).deleteWorkspace(anyString(), any());
    when(service.mayDeleteWorkspace(info, owner)).thenReturn(true);
    when(service.serviceId()).thenReturn("resources-id");
  }

  @Test void ownerRequiresDeletePermissionAndSharedReadersCannotDelete() {
    assertTrue(ResourcesProvider.allowsWorkspaceDelete(info, owner, true, false));
    assertFalse(ResourcesProvider.allowsWorkspaceDelete(info, owner, false, false));
    assertFalse(ResourcesProvider.allowsWorkspaceDelete(info, WorkspaceSettingsTest.user("bob"), true, false));
    info.setRights(org.integratedmodelling.klab.api.authentication.ResourcePrivileges.create("*"));
    assertFalse(ResourcesProvider.allowsWorkspaceDelete(info, WorkspaceSettingsTest.user("bob"), true, false));
    var anonymous = WorkspaceSettingsTest.user("alice");
    when(anonymous.getUser().isAnonymous()).thenReturn(true);
    assertFalse(ResourcesProvider.allowsWorkspaceDelete(info, anonymous, true, true));
    assertTrue(ResourcesProvider.allowsWorkspaceDelete(info, WorkspaceSettingsTest.user("admin"), false, true));
    assertFalse(ResourcesProvider.allowsWorkspaceDelete(info, null, true, true));
    info.setKnowledgeClass(KnowledgeClass.PROJECT);
    assertFalse(ResourcesProvider.allowsWorkspaceDelete(info, owner, true, true));
  }

  @Test void missingWorkspaceIsNotASuccessfulDeletion() {
    when(catalog.getStatus("owned", null)).thenReturn(null);
    var result = service.deleteWorkspace("owned", owner);
    assertTrue(result.getFirst().isEmpty()); verifyNoInteractions(manager);
  }

  @Test void unauthorizedDeletionCannotInspectOrRemoveChildren() {
    when(service.mayDeleteWorkspace(info, owner)).thenReturn(false);
    assertThrows(KlabAuthorizationException.class, () -> service.deleteWorkspace("owned", owner));
    verifyNoInteractions(manager); verify(service, never()).deleteProject(any(), any());
  }

  @Test void nonemptyOrInconsistentWorkspaceNeverCascades() {
    when(manager.removeEmptyWorkspace("owned")).thenReturn(false);
    var result = service.deleteWorkspace("owned", owner);
    assertTrue(result.getFirst().isEmpty());
    assertFalse(result.getFirst().getNotifications().isEmpty());
    verify(service, never()).deleteProject(any(), any());
  }

  @Test void successfulDeletionReturnsExactWorkspaceDescriptor() {
    when(manager.removeEmptyWorkspace("owned")).thenReturn(true);
    var result = service.deleteWorkspace("owned", owner);
    assertEquals(1, result.size()); assertFalse(result.getFirst().isEmpty());
    var descriptor = result.getFirst().getResults().iterator().next();
    assertEquals("owned", result.getFirst().getWorkspace());
    assertEquals("owned", descriptor.getResourceUrn());
    assertEquals("resources-id", descriptor.getServiceId());
    assertEquals(KnowledgeClass.WORKSPACE, descriptor.getKnowledgeClass());
    assertEquals(CRUDOperation.DELETE, descriptor.getOperation());
    verify(service, never()).deleteProject(any(), any());
  }

  @Test void persistenceFailureCannotProduceSuccessReceipt() {
    when(manager.removeEmptyWorkspace("owned")).thenThrow(new IllegalStateException("failed"));
    assertThrows(IllegalStateException.class, () -> service.deleteWorkspace("owned", owner));
    verify(service, never()).deleteProject(any(), any());
  }
}
