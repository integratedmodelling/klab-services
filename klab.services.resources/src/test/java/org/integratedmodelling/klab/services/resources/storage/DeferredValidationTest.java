package org.integratedmodelling.klab.services.resources.storage;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.integratedmodelling.klab.api.identities.UserIdentity;
import org.integratedmodelling.klab.api.lang.kim.impl.KimOntologyImpl;
import org.integratedmodelling.klab.api.scope.UserScope;
import org.integratedmodelling.klab.api.services.ResourcesService;
import org.integratedmodelling.klab.api.knowledge.organization.ProjectStorage;
import org.integratedmodelling.klab.resources.FileProjectStorage;
import org.integratedmodelling.klab.services.resources.ResourcesProvider;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

class DeferredValidationTest {
  @TempDir Path directory;
  WorkspaceManager manager;
  UserScope user;
  Path source;
  @BeforeAll static void initialize() { org.integratedmodelling.klab.configuration.ServiceConfiguration.injectInstantiators(); }
  static void set(Object target, String name, Object value) throws Exception {
    Class<?> type = target instanceof WorkspaceManager ? WorkspaceManager.class : target.getClass();
    var field = type.getDeclaredField(name); field.setAccessible(true); field.set(target, value);
  }
  @BeforeEach void setup() throws Exception {
    manager = mock(WorkspaceManager.class, CALLS_REAL_METHODS);
    user = mock(UserScope.class); var identity = mock(UserIdentity.class);
    when(identity.getId()).thenReturn("owner"); when(user.getIdentity()).thenReturn(identity);
    var service = mock(ResourcesProvider.class);
    when(service.canEditProject("project", user)).thenReturn(true); when(service.serviceId()).thenReturn("resources");
    set(manager,"service",service); set(manager,"deferredDocuments",new LinkedHashMap<>());
    set(manager,"syntaxOnly",ThreadLocal.withInitial(() -> false));
    set(manager,"projectLocks",new HashMap<>(Map.of("project", WorkspaceManager.projectLockOwner(user))));
    set(manager,"loading",new AtomicBoolean());
    set(manager,"_ontologyMap",new HashMap<>());
    var type=Class.forName(WorkspaceManager.class.getName()+"$ProjectDescriptor");
    var constructor=type.getDeclaredConstructor(WorkspaceManager.class);constructor.setAccessible(true);
    var descriptor=constructor.newInstance(manager);
    set(descriptor,"workspace","workspace");set(descriptor,"name","project");
    set(descriptor,"storage",new FileProjectStorage(directory.toFile(),"project",null));
    set(manager,"projectDescriptors",Map.of("project",descriptor));
    doReturn("workspace").when(manager).getWorkspaceForProject("project");
    var baseline=new KimOntologyImpl();baseline.setUrn("test");baseline.setProjectName("project");baseline.setSourceCode("baseline");
    doReturn(baseline).when(manager).getOntology("project", "test");
    source=directory.resolve(ProjectStorage.getRelativeFilePath("test",ProjectStorage.ResourceType.ONTOLOGY,"/"));
    Files.createDirectories(source.getParent());Files.writeString(source,"baseline");
  }
  KimOntologyImpl document(String text) {
    var document=new KimOntologyImpl();document.setUrn("test");document.setProjectName("project");document.setSourceCode(text);return document;
  }
  @Test void savesSyntaxErrorsWithoutFanoutAndCoalescesLatestSource() throws Exception {
    var first=manager.saveDeferred(document(" "),user);
    manager.saveDeferred(document("  "),user);
    assertEquals("  ",Files.readString(source));
    assertEquals(1,first.size());assertEquals("workspace",first.getFirst().getWorkspace());
    assertFalse(first.getFirst().getOntologies().getFirst().getNotifications().isEmpty());
    verify(manager,never()).handleFileChange(anyString(),anyList());
    doAnswer(invocation -> {
      assertEquals("  ",Files.readString(source));
      assertEquals(1,((List<?>)invocation.getArgument(1)).size());return List.of();
    }).when(manager).handleFileChange(anyString(),anyList());
    manager.validateDeferred("workspace",user);
    manager.validateDeferred("workspace",user);
    verify(manager,times(1)).handleFileChange(eq("project"),anyList());
  }
  @Test void validSourceIsRetrievableAndMarkedOutOfDateWithoutDependencyValidation() throws Exception {
    set(manager,"ontologyParser",new WorkspaceManager.OntologyParser());
    set(manager,"languageValidationScope",new org.integratedmodelling.klab.runtime.language.WorldviewValidationScope());
    var text="ontology test in domain root version 1.0;\n// latest source\n";
    var results=manager.saveDeferred(document(text),user);
    assertEquals(text,Files.readString(source));
    var mapField=WorkspaceManager.class.getDeclaredField("_ontologyMap");mapField.setAccessible(true);
    var saved=(org.integratedmodelling.klab.api.lang.kim.KimOntology)((Map<?,?>)mapField.get(manager)).get("test");
    assertEquals(text,saved.getSourceCode());
    assertEquals(true,saved.getMetadata().get(ResourcesService.VALIDATION_DEFERRED_METADATA));
    assertFalse(org.integratedmodelling.common.utils.Utils.Notifications.hasErrors(results.getFirst().getOntologies().getFirst().getNotifications()));
    verify(manager,never()).handleFileChange(anyString(),anyList());
  }

  @Test void newLockHolderCanRecoverQueueAfterEditorHandoff() throws Exception {
    manager.saveDeferred(document(" "),user);
    var next=mock(UserScope.class);var identity=mock(UserIdentity.class);
    when(identity.getId()).thenReturn("next");when(next.getIdentity()).thenReturn(identity);
    var field=WorkspaceManager.class.getDeclaredField("service");field.setAccessible(true);
    when(((ResourcesProvider)field.get(manager)).canEditProject("project",next)).thenReturn(true);
    set(manager,"projectLocks",new HashMap<>(Map.of("project",WorkspaceManager.projectLockOwner(next))));
    doReturn(List.of()).when(manager).handleFileChange(anyString(),anyList());
    manager.validateDeferred("workspace",next);
    verify(manager).handleFileChange(eq("project"),anyList());
  }

  @Test void failedValidationRetainsQueueAndLatestSavedSourceForRetry() throws Exception {
    manager.saveDeferred(document("  "),user);
    doThrow(new IllegalStateException("temporary failure")).when(manager).handleFileChange(anyString(),anyList());
    assertThrows(IllegalStateException.class,()->manager.validateDeferred("workspace",user));
    assertEquals("  ",Files.readString(source));
    doReturn(List.of()).when(manager).handleFileChange(anyString(),anyList());
    manager.validateDeferred("workspace",user);
    verify(manager,times(2)).handleFileChange(eq("project"),anyList());
  }

  @Test void watcherCannotFlushDeferredSave() throws Exception {
    manager.saveDeferred(document(" "),user);
    assertTrue(manager.handleFileChange("project",List.of(org.integratedmodelling.klab.api.collections.Triple.of(
        ProjectStorage.ResourceType.ONTOLOGY,org.integratedmodelling.klab.api.authentication.CRUDOperation.UPDATE,
        source.toUri().toURL()))).isEmpty());
  }
  @Test void unrelatedPrincipalCannotSaveOrFlushOwnersQueue() throws Exception {
    manager.saveDeferred(document(" "),user);
    var other=mock(UserScope.class);var identity=mock(UserIdentity.class);
    when(identity.getId()).thenReturn("other");when(other.getIdentity()).thenReturn(identity);
    assertThrows(RuntimeException.class,()->manager.saveDeferred(document("  "),other));
    assertTrue(manager.validateDeferred("workspace",other).isEmpty());
    verify(manager,never()).handleFileChange(anyString(),anyList());
    assertEquals(" ",Files.readString(source));
  }
}
