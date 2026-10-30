package org.integratedmodelling.klab.services.resources.storage;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.nio.file.*;
import java.util.*;
import org.integratedmodelling.common.utils.Utils;
import org.integratedmodelling.klab.api.authentication.ResourcePrivileges;
import org.integratedmodelling.klab.api.identities.UserIdentity;
import org.integratedmodelling.klab.api.knowledge.organization.impl.ProjectImpl;
import org.integratedmodelling.klab.api.scope.UserScope;
import org.integratedmodelling.klab.api.services.resources.ResourceInfo;
import org.integratedmodelling.klab.api.settings.ProjectSettings;
import org.integratedmodelling.klab.resources.*;
import org.integratedmodelling.klab.services.resources.ResourcesProvider;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

class ProjectSettingsSaveTest {
  @TempDir Path root;
  WorkspaceManager manager;
  ResourcesProvider service;
  ResourcesKBox catalog;
  UserScope user;
  ResourceInfo info;
  Map<String, String> locks;
  @BeforeEach void setup() throws Exception {
    manager = mock(WorkspaceManager.class, CALLS_REAL_METHODS);
    service = mock(ResourcesProvider.class); catalog = mock(ResourcesKBox.class);
    user = mock(UserScope.class); var identity = mock(UserIdentity.class);
    when(identity.getId()).thenReturn("alice-id"); when(user.getIdentity()).thenReturn(identity);
    when(service.canEditProject("project", user)).thenReturn(true);
    when(service.serviceId()).thenReturn("resources");
    info = new ResourceInfo(); info.setUrn("project"); info.setRights(ResourcePrivileges.create("alice"));
    info.getRights().getAllowedServices().add("trusted-service");
    when(catalog.getStatus("project", null)).thenReturn(info);
    when(catalog.putStatus(any())).thenReturn(true);
    set(manager, "service", service); set(manager, "resourcesKbox", catalog);
    locks = new HashMap<>(); locks.put("project", WorkspaceManager.projectLockOwner(user)); set(manager, "projectLocks", locks);
    var type = Class.forName(WorkspaceManager.class.getName() + "$ProjectDescriptor");
    var constructor = type.getDeclaredConstructor(WorkspaceManager.class); constructor.setAccessible(true);
    var descriptor = constructor.newInstance(manager);
    set(descriptor, "name", "project"); set(descriptor, "workspace", "workspace");
    set(descriptor, "storage", new FileProjectStorage(root.toFile(), "project", null));
    set(manager, "projectDescriptors", Map.of("project", descriptor));
    doReturn(new ProjectImpl()).when(manager).createProjectData("project", "workspace");
  }
  private UserScope principal(String username, String token, String federation) {
    var requester = mock(UserScope.class);
    var identity = mock(UserIdentity.class);
    when(identity.getId()).thenReturn(token);
    when(identity.getUsername()).thenReturn(username);
    var data = org.integratedmodelling.klab.api.collections.Parameters.<String>create();
    data.put(UserIdentity.FEDERATION_DATA_PROPERTY,
        new org.integratedmodelling.klab.api.identities.Federation(federation, null));
    when(identity.getData()).thenReturn(data);
    when(requester.getIdentity()).thenReturn(identity);
    return requester;
  }

  @Test void reconnectRenewsLockAndAllowsSettingsSaveAndUnlock() throws Exception {
    set(manager, "scope", mock(org.integratedmodelling.klab.api.scope.Scope.class));
    locks.clear();
    var original = principal("alice", "old-token", "federation");
    var reconnected = principal("alice", "new-token", "federation");
    when(service.canEditProject("project", reconnected)).thenReturn(true);
    assertTrue(manager.lockProject("project", original));
    assertTrue(manager.lockProject("project", reconnected));
    assertFalse(Utils.Notifications.hasErrors(manager.replaceProjectSettings(
        "workspace", "project", settings(null), reconnected).getNotifications()));
    when(service.canEditProject("project", reconnected)).thenReturn(false);
    assertTrue(Utils.Notifications.hasErrors(manager.replaceProjectSettings(
        "workspace", "project", settings(null), reconnected).getNotifications()));
    assertTrue(manager.unlockProject("project", reconnected));
    assertTrue(locks.isEmpty());
  }

  @Test void reconnectCannotTakeAnotherPrincipalLock() throws Exception {
    set(manager, "scope", mock(org.integratedmodelling.klab.api.scope.Scope.class));
    locks.clear();
    assertTrue(manager.lockProject("project", principal("alice", "first", "federation")));
    for (var other : List.of(principal("bob", "second", "federation"),
        principal("alice", "third", "another-federation"))) {
      assertFalse(manager.lockProject("project", other));
      assertFalse(manager.unlockProject("project", other));
      assertTrue(Utils.Notifications.hasErrors(manager.replaceProjectSettings(
          "workspace", "project", settings(null), other).getNotifications()));
    }
  }

  @Test void anonymousLocksRemainCredentialScoped() throws Exception {
    set(manager, "scope", mock(org.integratedmodelling.klab.api.scope.Scope.class));
    locks.clear();
    var first = principal("anonymous", "first", "federation");
    var second = principal("anonymous", "second", "federation");
    when(((UserIdentity) first.getIdentity()).isAnonymous()).thenReturn(true);
    when(((UserIdentity) second.getIdentity()).isAnonymous()).thenReturn(true);
    assertTrue(manager.lockProject("project", first));
    assertFalse(manager.lockProject("project", second));
    assertFalse(manager.unlockProject("project", second));
    assertTrue(manager.unlockProject("project", first));
  }

  static void set(Object object, String name, Object value) throws Exception {
    var field = object.getClass().getDeclaredField(name); field.setAccessible(true); field.set(object, value);
  }
  ProjectSettings settings(String permissions) {
    var settings = new ProjectSettings(); settings.setPermissions(permissions);
    settings.getMetadata().put("custom", Map.of("flag", true)); return settings;
  }
  @Test void savePersistsRightsSeparatelyAndPreservesServiceGrants() throws Exception {
    var result = manager.replaceProjectSettings("workspace", "project", settings("TEAM"), user);
    assertFalse(Utils.Notifications.hasErrors(result.getNotifications()));
    assertTrue(info.getRights().getAllowedGroups().contains("TEAM"));
    assertEquals(Set.of("trusted-service"), info.getRights().getAllowedServices());
    verify(catalog).putStatus(info);
    var json = Files.readString(root.resolve("META-INF/manifest.json"));
    assertFalse(json.contains("permissions")); assertTrue(json.contains("custom"));
  }
  @Test void lostLockOrRevokedAccessWritesNeitherStore() {
    locks.put("project", "another-user");
    assertTrue(Utils.Notifications.hasErrors(manager.replaceProjectSettings("workspace", "project", settings("*"), user).getNotifications()));
    locks.put("project", WorkspaceManager.projectLockOwner(user)); when(service.canEditProject("project", user)).thenReturn(false);
    assertTrue(Utils.Notifications.hasErrors(manager.replaceProjectSettings("workspace", "project", settings("*"), user).getNotifications()));
    verify(catalog, never()).putStatus(any());
    assertFalse(Files.exists(root.resolve("META-INF/manifest.json")));
  }
  @Test void failedRightsUpdateRestoresBothStores() throws Exception {
    Files.createDirectories(root.resolve("META-INF"));
    var file = root.resolve("META-INF/manifest.json");
    String original = "{\"metadata\":{\"original\":true}}"; Files.writeString(file, original);
    when(catalog.putStatus(any())).thenReturn(false, true);
    assertTrue(Utils.Notifications.hasErrors(manager.replaceProjectSettings("workspace", "project", settings("*"), user).getNotifications()));
    assertEquals(original, Files.readString(file));
    assertEquals("alice", info.getRights().toString());
  }
  @Test void omittedRightsRemainUnchangedAndEmptyRightsMeanOwnerOnly() {
    assertFalse(Utils.Notifications.hasErrors(manager.replaceProjectSettings("workspace", "project", settings(null), user).getNotifications()));
    verify(catalog, never()).putStatus(any());
    assertFalse(Utils.Notifications.hasErrors(manager.replaceProjectSettings("workspace", "project", settings(""), user).getNotifications()));
    assertEquals("", info.getRights().toString());
    assertEquals(Set.of("trusted-service"), info.getRights().getAllowedServices());
  }
  @Test void worldviewDeclarationRequiresAdministratorAndObserverRequiresWorldview() throws Exception {
    var settings = settings(null); settings.setDefinedWorldview("earth");
    assertTrue(Utils.Notifications.hasErrors(manager.replaceProjectSettings("workspace", "project", settings, user).getNotifications()));
    assertFalse(Files.exists(root.resolve("META-INF/manifest.json")));
    when(service.canAdministerProjectSettings(user)).thenReturn(true);
    settings.getMetadata().put(org.integratedmodelling.klab.api.knowledge.Worldview.USER_OBSERVER_SEMANTICS, "people:User");
    assertFalse(Utils.Notifications.hasErrors(manager.replaceProjectSettings("workspace", "project", settings, user).getNotifications()));
    assertTrue(Files.readString(root.resolve("META-INF/manifest.json")).contains("earth"));
    when(service.canAdministerProjectSettings(user)).thenReturn(false);
    settings.setDefinedWorldview(null);
    assertFalse(Utils.Notifications.hasErrors(manager.replaceProjectSettings("workspace", "project", settings, user).getNotifications()));
    Files.writeString(root.resolve("META-INF/manifest.json"), "{\"metadata\":{}}");
    assertTrue(Utils.Notifications.hasErrors(manager.replaceProjectSettings("workspace", "project", settings, user).getNotifications()));
  }}
