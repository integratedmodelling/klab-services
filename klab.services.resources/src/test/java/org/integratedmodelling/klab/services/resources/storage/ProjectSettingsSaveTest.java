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
    locks = new HashMap<>(); locks.put("project", "alice-id"); set(manager, "projectLocks", locks);
    var type = Class.forName(WorkspaceManager.class.getName() + "$ProjectDescriptor");
    var constructor = type.getDeclaredConstructor(WorkspaceManager.class); constructor.setAccessible(true);
    var descriptor = constructor.newInstance(manager);
    set(descriptor, "name", "project"); set(descriptor, "workspace", "workspace");
    set(descriptor, "storage", new FileProjectStorage(root.toFile(), "project", null));
    set(manager, "projectDescriptors", Map.of("project", descriptor));
    doReturn(new ProjectImpl()).when(manager).createProjectData("project", "workspace");
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
    locks.put("project", "alice-id"); when(service.canEditProject("project", user)).thenReturn(false);
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
