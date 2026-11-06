package org.integratedmodelling.klab.services.resources.storage;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.net.URI;
import java.nio.file.*;
import java.util.*;
import org.integratedmodelling.klab.api.identities.UserIdentity;
import org.integratedmodelling.klab.api.scope.UserScope;
import org.integratedmodelling.klab.api.lang.kim.impl.KimOntologyImpl;
import org.integratedmodelling.klab.api.services.ResourcesService.SubmissionMode;
import org.integratedmodelling.klab.resources.*;
import org.integratedmodelling.klab.services.resources.ResourcesProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ReviewArchiveProjectAuthorizationTest {
  @TempDir Path root;
  static void set(Object target, String name, Object value) throws Exception {
    var field = WorkspaceManager.class.getDeclaredField(name); field.setAccessible(true); field.set(target, value);
  }
  @Test void actualOntologyHostRequiresCurrentWriteAndRespectsReadOnlyAndOtherMutationOwner() throws Exception {
    var manager = mock(WorkspaceManager.class, CALLS_REAL_METHODS); var service = mock(ResourcesProvider.class);
    var storage = mock(FileProjectStorage.class); var user = mock(UserScope.class); var identity = mock(UserIdentity.class);
    when(user.getUser()).thenReturn(identity); when(identity.isAuthenticated()).thenReturn(true);
    var ontology = new KimOntologyImpl(); ontology.setUrn("ontology"); ontology.setProjectName("actual-host");
    doReturn(ontology).when(manager).getOntology("ontology");
    var descriptorType = Class.forName(WorkspaceManager.class.getName() + "$ProjectDescriptor");
    var constructor = descriptorType.getDeclaredConstructor(WorkspaceManager.class); constructor.setAccessible(true);
    var descriptor = constructor.newInstance(manager); var storageField = descriptorType.getDeclaredField("storage");
    storageField.setAccessible(true); storageField.set(descriptor, storage);
    set(manager, "service", service); set(manager, "projectDescriptors", Map.of("actual-host", descriptor));
    var locks = new HashMap<String, String>(); set(manager, "projectLocks", locks);
    when(storage.getRootFolder()).thenReturn(root.toFile()); when(service.getUrl()).thenReturn(URI.create("https://resources.example").toURL());
    var intent = new ReviewArchiveOutbox.Intent(UUID.randomUUID().toString(), UUID.randomUUID().toString(), "ontology", 2);
    manager.withReviewArchiveProject(intent, user, (project, path, base) -> {
      assertTrue(Thread.holdsLock(manager)); assertEquals("actual-host", project); assertEquals(root, path);
      assertEquals("https://resources.example", base);
    });
    verify(service).requireMaterialWrite("actual-host", SubmissionMode.ADD, user);
    doThrow(new org.integratedmodelling.klab.api.exceptions.KlabResourceAccessException("revoked"))
        .when(service).requireMaterialWrite("actual-host", SubmissionMode.ADD, user);
    assertThrows(RuntimeException.class, () -> manager.withReviewArchiveProject(intent, user, (p, path, base) -> fail("Revoked")));
    doNothing().when(service).requireMaterialWrite("actual-host", SubmissionMode.ADD, user);
    when(storage.isReadOnly()).thenReturn(true);
    assertThrows(java.io.IOException.class, () -> manager.withReviewArchiveProject(intent, user, (p, path, base) -> fail("Read-only")));
    when(storage.isReadOnly()).thenReturn(false); locks.put("actual-host", "another-owner");
    assertThrows(RuntimeException.class, () -> manager.withReviewArchiveProject(intent, user, (p, path, base) -> fail("Locked")));
    assertThrows(RuntimeException.class, () -> manager.withReviewArchiveProject(intent, null, (p, path, base) -> fail("Anonymous")));
    verify(service, never()).requireMaterialWrite(eq("caller-selected"), any(), any());
  }
}
