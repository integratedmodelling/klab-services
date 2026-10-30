package org.integratedmodelling.klab.services.resources;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.*;
import org.integratedmodelling.klab.api.authentication.CRUDOperation;
import org.integratedmodelling.klab.api.collections.Parameters;
import org.integratedmodelling.klab.api.identities.UserIdentity;
import org.integratedmodelling.klab.api.knowledge.organization.impl.WorkspaceImpl;
import org.integratedmodelling.klab.api.scope.UserScope;
import org.integratedmodelling.klab.api.services.resources.ResourceSet;
import org.integratedmodelling.klab.services.resources.library.ProjectLibrary;
import org.integratedmodelling.klab.services.resources.storage.WorkspaceManager;
import org.junit.jupiter.api.Test;

class ProjectTransferTest {
  @Test void zipImportRejectsAnUnexpectedProjectBeforeCreatingAWorkspace(
      @org.junit.jupiter.api.io.TempDir java.nio.file.Path root) throws Exception {
    var source = root.resolve("source"); java.nio.file.Files.createDirectories(source.resolve("META-INF"));
    java.nio.file.Files.writeString(source.resolve("META-INF/manifest.json"), "{}");
    var archive = root.resolve("project.zip");
    try (var stream = org.integratedmodelling.klab.resources.ProjectArchiveIO.exportProject(source, "other")) {
      java.nio.file.Files.copy(stream, archive);
    }
    var service = mock(ResourcesProvider.class); var user = mock(UserScope.class);
    assertThrows(IllegalArgumentException.class, () -> ProjectLibrary.importProjectZip(
        archive.toFile(), Parameters.create("URN", "requested"), service, user));
    verifyNoInteractions(service);
  }

  @Test void gitImportPassesOnlyLocalCredentialIdAndNeverOverwrites() {
    var service = mock(ResourcesProvider.class); var user = mock(UserScope.class);
    var identity = mock(UserIdentity.class); when(user.getUser()).thenReturn(identity);
    var workspace = new WorkspaceImpl(); workspace.setUrn("workspace");
    when(service.retrieveWorkspace("workspace", user)).thenReturn(workspace);
    var result = new ResourceSet();
    when(service.importProject("workspace", "https://example.org/im.examples.git#branch", false, user, "local-id"))
        .thenReturn(List.of(result));
    assertNotNull(ProjectLibrary.importProjectGit(Parameters.create("workspace", "workspace",
        "url", "https://example.org/im.examples.git#branch", "project", "im.examples",
        "credentialId", "local-id"), service, user));
    verify(service).importProject("workspace", "https://example.org/im.examples.git#branch", false, user, "local-id");
    assertThrows(IllegalArgumentException.class, () -> ProjectLibrary.importProjectGit(
        Parameters.create("url", "https://secret:token@example.org/im.examples.git"), service, user));
  }

  @Test void projectArchiveExportChecksReadAccessBeforeTouchingStorage() throws Exception {
    var service = mock(ResourcesProvider.class, CALLS_REAL_METHODS);
    var manager = mock(WorkspaceManager.class); var user = mock(UserScope.class);
    var field = ResourcesProvider.class.getDeclaredField("workspaceManager");
    field.setAccessible(true); field.set(service, manager);
    doThrow(new SecurityException("No read access")).when(service)
        .requireProjectOperation("project", CRUDOperation.READ, user);
    assertThrows(SecurityException.class, () -> service.exportProjectArchive("project", user));
    verifyNoInteractions(manager);
    doNothing().when(service).requireProjectOperation("project", CRUDOperation.READ, user);
    var bytes = new java.io.ByteArrayInputStream(new byte[]{1});
    when(manager.exportProjectArchive("project")).thenReturn(bytes);
    assertSame(bytes, service.exportProjectArchive("project", user));
  }
}
