package org.integratedmodelling.klab.services.resources.storage;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.eclipse.jgit.api.Git;
import org.integratedmodelling.klab.api.scope.Scope;
import org.integratedmodelling.klab.api.services.runtime.Notification;
import org.integratedmodelling.klab.resources.FileProjectStorage;
import org.integratedmodelling.klab.services.resources.ResourcesProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;

class SnapshotProjectLoadTest {
  @TempDir Path root;

  private void set(Object target, String name, Object value) throws Exception {
    var field = target.getClass().getDeclaredField(name);
    field.setAccessible(true);
    field.set(target, value);
  }

  private void manifest(boolean snapshot) throws Exception {
    Files.createDirectories(root.resolve("META-INF"));
    Files.writeString(root.resolve("META-INF/manifest.json"), "{\"snapshot\":" + snapshot + "}");
  }

  private WorkspaceManager manager(ResourcesProvider service, boolean locked) throws Exception {
    var manager = mock(WorkspaceManager.class, CALLS_REAL_METHODS);
    set(manager, "service", service);
    set(manager, "scope", mock(Scope.class));
    set(manager, "projectLocks", locked ? Map.of("project", "owner") : Map.of());
    var type = Class.forName(WorkspaceManager.class.getName() + "$ProjectDescriptor");
    var constructor = type.getDeclaredConstructor(WorkspaceManager.class);
    constructor.setAccessible(true);
    var descriptor = constructor.newInstance(manager);
    set(descriptor, "name", "project");
    set(descriptor, "storage", new FileProjectStorage(root.toFile(), "project", null));
    set(manager, "projectDescriptors", Map.of("project", descriptor));
    return manager;
  }

  @Test void disabledOrUntrackedProjectsDoNotAttemptSynchronization() throws Exception {
    var service = mock(ResourcesProvider.class);
    manifest(true);
    manager(service, false).synchronizeSnapshotProjects();
    try (var git = Git.init().setDirectory(root.toFile()).call()) {
      manifest(false);
      manager(service, false).synchronizeSnapshotProjects();
    }
    verifyNoInteractions(service);
  }

  @Test void lockedSnapshotReportsWarning() throws Exception {
    manifest(true);
    var service = mock(ResourcesProvider.class);
    try (var git = Git.init().setDirectory(root.toFile()).call()) {
      manager(service, true).synchronizeSnapshotProjects();
    }
    var notification = ArgumentCaptor.forClass(Notification.class);
    verify(service).reportProjectSynchronization(notification.capture());
    assertEquals(Notification.Level.Warning, notification.getValue().getLevel());
    assertTrue(notification.getValue().getMessage().contains("project"));
  }

  @Test void fetchFailureIsRetainedAsServiceError() throws Exception {
    manifest(true);
    var service = mock(ResourcesProvider.class);
    when(service.credentialVault()).thenReturn(
        mock(org.integratedmodelling.common.authentication.CredentialVault.class));
    try (var git = Git.init().setDirectory(root.toFile()).call()) {
      // A Git project without an origin must remain loadable and report the failed update.
      manager(service, false).synchronizeSnapshotProjects();
    }
    var notification = ArgumentCaptor.forClass(Notification.class);
    verify(service, atLeastOnce()).reportProjectSynchronization(notification.capture());
    assertTrue(notification.getAllValues().stream()
        .anyMatch(n -> n.getLevel() == Notification.Level.Error && n.getMessage().contains("project")));
  }
}
