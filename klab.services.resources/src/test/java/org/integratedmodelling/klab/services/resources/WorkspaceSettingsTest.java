package org.integratedmodelling.klab.services.resources;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import org.integratedmodelling.klab.resources.ResourcesKBox;
import org.integratedmodelling.klab.api.authentication.ResourcePrivileges;
import org.integratedmodelling.klab.api.data.Metadata;
import org.integratedmodelling.klab.api.identities.UserIdentity;
import org.integratedmodelling.klab.api.knowledge.KlabAsset.KnowledgeClass;
import org.integratedmodelling.klab.api.scope.UserScope;
import org.integratedmodelling.klab.api.services.resources.ResourceInfo;
import org.integratedmodelling.klab.services.resources.storage.*;

class WorkspaceSettingsTest {
  ResourcesProvider service; ResourcesKBox catalog; WorkspaceManager manager;
  ResourceInfo original; UserScope owner;
  static UserScope user(String name) {
    var scope = mock(UserScope.class); var identity = mock(UserIdentity.class);
    when(scope.getUser()).thenReturn(identity); when(identity.getUsername()).thenReturn(name);
    when(identity.getGroups()).thenReturn(List.of()); return scope;
  }
  @BeforeEach void setup() throws Exception {
    service = mock(ResourcesProvider.class); catalog = mock(ResourcesKBox.class); manager = mock(WorkspaceManager.class);
    for (var entry : Map.of("resourcesKbox", catalog, "workspaceManager", manager).entrySet()) {
      var field = ResourcesProvider.class.getDeclaredField(entry.getKey()); field.setAccessible(true); field.set(service, entry.getValue());
    }
    original = new ResourceInfo(); original.setUrn("workspace"); original.setKnowledgeClass(KnowledgeClass.WORKSPACE);
    original.setOwner("alice"); original.setRights(ResourcePrivileges.create("bob"));
    original.getRights().getAllowedServices().add("trusted-service");
    original.getChildResourceUrns().add("project"); original.getMetadata().put("old", true);
    when(catalog.getStatus("workspace", null)).thenReturn(original); when(catalog.putStatus(any())).thenReturn(true);
    doCallRealMethod().when(service).updateWorkspaceSettings(anyString(), any(), any(), any()); owner = user("alice");
  }
  @Test void onlyOwnerAndAdministratorCanEdit() {
    assertTrue(ResourcesProvider.allowsWorkspaceEdit(original, owner, false));
    assertFalse(ResourcesProvider.allowsWorkspaceEdit(original, user("bob"), false));
    assertTrue(ResourcesProvider.allowsWorkspaceRead(original, user("bob"), false));
    assertFalse(ResourcesProvider.allowsWorkspaceRead(original, user("eve"), false));
    assertTrue(ResourcesProvider.allowsWorkspaceEdit(original, user("admin"), true));
    original.setOwner(null);
    assertFalse(ResourcesProvider.allowsWorkspaceEdit(original, user("bob"), false));
    assertTrue(ResourcesProvider.allowsWorkspaceEdit(original, owner, true));
  }
  @Test void catalogSavePreservesOwnershipMembershipAndServiceGrants() {
    assertTrue(service.updateWorkspaceSettings("workspace", Metadata.create(Map.of("structured", Map.of("flag", true))), ResourcePrivileges.create("TEAM"), owner));
    var saved = ArgumentCaptor.forClass(ResourceInfo.class); verify(catalog).putStatus(saved.capture());
    var info = saved.getValue(); assertEquals("alice", info.getOwner()); assertEquals(List.of("project"), info.getChildResourceUrns());
    assertEquals(Map.of("flag", true), info.getMetadata().get("structured")); assertFalse(info.getMetadata().containsKey("old"));
    assertEquals(Set.of("trusted-service"), info.getRights().getAllowedServices()); assertEquals("TEAM", info.getRights().toString());
    assertEquals("bob", original.getRights().toString()); assertEquals(true, original.getMetadata().get("old"));
    verify(manager).refreshWorkspaceSettings(info);
  }
  @Test void deniedOrFailedSaveDoesNotRefreshWorkspace() {
    assertFalse(service.updateWorkspaceSettings("workspace", Metadata.create(), ResourcePrivileges.empty(), user("bob")));
    verify(catalog, never()).putStatus(any());
    when(catalog.putStatus(any())).thenReturn(false);
    assertFalse(service.updateWorkspaceSettings("workspace", Metadata.create(), ResourcePrivileges.empty(), owner));
    verifyNoInteractions(manager); assertEquals("bob", original.getRights().toString());
  }
  @Test void omittedSettingsArePreservedAndEmptyRightsKeepOwnerAccess() {
    assertTrue(service.updateWorkspaceSettings("workspace", null, null, owner));
    var saved = ArgumentCaptor.forClass(ResourceInfo.class); verify(catalog).putStatus(saved.capture());
    assertEquals(original.getMetadata(), saved.getValue().getMetadata()); assertEquals("bob", saved.getValue().getRights().toString());
    original.setRights(ResourcePrivileges.empty());
    assertTrue(ResourcesProvider.allowsWorkspaceRead(original, owner, false));
    assertFalse(ResourcesProvider.allowsWorkspaceRead(original, user("bob"), false));
  }
  @Test void creatorIsRecordedOnlyAfterSuccessfulCatalogWrite() {
    doCallRealMethod().when(service).createWorkspace(anyString(), any(), any());
    when(owner.getUser().isAuthenticated()).thenReturn(true);
    assertTrue(service.createWorkspace("new-workspace", Metadata.create(), owner));
    var saved = ArgumentCaptor.forClass(ResourceInfo.class); verify(catalog).putStatus(saved.capture());
    assertEquals("alice", saved.getValue().getOwner()); verify(manager).notifyNewWorkspace(saved.getValue());
    clearInvocations(manager); when(catalog.putStatus(any())).thenReturn(false);
    assertFalse(service.createWorkspace("failed-workspace", Metadata.create(), owner)); verifyNoInteractions(manager);
  }
  @Test void infoAllowsAuditButDoesNotExposeSettingsToExcludedUsers() {
    doCallRealMethod().when(service).resourceInfo(anyString(), any());
    var visible = service.resourceInfo("workspace", user("bob"));
    assertEquals(Set.of(org.integratedmodelling.klab.api.authentication.CRUDOperation.READ), visible.getPermissions());
    original.setRights(ResourcePrivileges.create("*,!eve"));
    var denied = service.resourceInfo("workspace", user("eve"));
    assertEquals(ResourceInfo.Type.UNAUTHORIZED, denied.getType()); assertTrue(denied.getMetadata().isEmpty());
    assertTrue(denied.getPermissions().isEmpty());
  }  @Test void genericInfoUpdateCannotBypassWorkspaceOwnershipOrSpoofTheTarget() {
    doCallRealMethod().when(service).setResourceInfo(anyString(), any(), any());
    assertFalse(service.setResourceInfo("workspace", original, user("bob")));
    assertFalse(service.setResourceInfo("different-target", original, owner));
    verify(catalog, never()).putStatus(any());
  }}
