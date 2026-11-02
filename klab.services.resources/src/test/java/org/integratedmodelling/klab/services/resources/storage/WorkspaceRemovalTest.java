package org.integratedmodelling.klab.services.resources.storage;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.nio.file.*;
import java.util.*;
import org.integratedmodelling.common.utils.Utils;
import org.integratedmodelling.klab.api.knowledge.KlabAsset.KnowledgeClass;
import org.integratedmodelling.klab.api.knowledge.organization.Workspace;
import org.integratedmodelling.klab.api.knowledge.organization.impl.WorkspaceImpl;
import org.integratedmodelling.klab.api.services.resources.ResourceInfo;
import org.integratedmodelling.klab.resources.ResourcesKBox;
import org.integratedmodelling.klab.services.configuration.ResourcesConfiguration;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

class WorkspaceRemovalTest {
  @TempDir Path directory;
  WorkspaceManager manager;
  ResourcesKBox catalog;
  ResourcesConfiguration configuration;
  Map<String, WorkspaceImpl> workspaces;
  ResourceInfo info;

  static void field(Object target, String name, Object value) throws Exception {
    var field = WorkspaceManager.class.getDeclaredField(name);
    field.setAccessible(true); field.set(target, value);
  }

  @BeforeEach void setup() throws Exception {
    manager = mock(WorkspaceManager.class, CALLS_REAL_METHODS);
    catalog = mock(ResourcesKBox.class);
    configuration = new ResourcesConfiguration();
    configuration.getWorkspaces().put("owned", new LinkedHashSet<>());
    configuration.getWorkspaces().put("other", new LinkedHashSet<>());
    var workspace = new WorkspaceImpl(); workspace.setUrn("owned");
    workspaces = new LinkedHashMap<>(); workspaces.put("owned", workspace);
    field(manager, "configuration", configuration); field(manager, "resourcesKbox", catalog);
    field(manager, "workspaces", workspaces); field(manager, "projectDescriptors", new HashMap<>());
    info = new ResourceInfo(); info.setUrn("owned"); info.setKnowledgeClass(KnowledgeClass.WORKSPACE);
    when(catalog.getStatus("owned", null)).thenReturn(info);
    when(catalog.deleteMetadata("owned")).thenReturn(true); when(catalog.putStatus(info)).thenReturn(true);
    doAnswer(call -> { Utils.YAML.save(configuration, directory.resolve("resources.yaml").toFile()); return null; })
        .when(manager).saveConfiguration();
  }

  @Test void removesOnlyEmptyDescriptorAndPersistsConfiguration() throws Exception {
    assertTrue(manager.removeEmptyWorkspace("owned"));
    assertFalse(workspaces.containsKey("owned"));
    assertFalse(configuration.getWorkspaces().containsKey("owned"));
    assertTrue(configuration.getWorkspaces().containsKey("other"));
    var restored = Utils.YAML.load(directory.resolve("resources.yaml").toFile(), ResourcesConfiguration.class);
    assertFalse(restored.getWorkspaces().containsKey("owned"));
    assertTrue(restored.getWorkspaces().containsKey("other"));
    verify(catalog).deleteMetadata("owned");
    verify(catalog, never()).deleteResource(any()); verify(manager, never()).removeProject(any());
  }

  @Test void refusesReservedUnknownMissingAndNonemptyStateWithoutMutations() {
    assertFalse(manager.removeEmptyWorkspace(null));
    assertFalse(manager.removeEmptyWorkspace(Workspace.EXTERNAL_WORKSPACE_URN));
    assertFalse(manager.removeEmptyWorkspace("missing"));
    configuration.getWorkspaces().get("owned").add("project");
    assertFalse(manager.removeEmptyWorkspace("owned"));
    verify(catalog, never()).deleteMetadata(any()); verify(manager, never()).saveConfiguration();
  }

  @Test void refusesRuntimeMembershipAndWrongCatalogClass() {
    var project = mock(org.integratedmodelling.klab.api.knowledge.organization.Project.class);
    workspaces.get("owned").getProjects().add(project);
    assertFalse(manager.removeEmptyWorkspace("owned"));
    workspaces.get("owned").getProjects().clear();
    info.setKnowledgeClass(KnowledgeClass.PROJECT);
    assertFalse(manager.removeEmptyWorkspace("owned"));
    verify(catalog, never()).deleteMetadata(any());
  }

  @Test void catalogRefusalLeavesConfigurationAndRuntimeUntouched() {
    when(catalog.deleteMetadata("owned")).thenReturn(false);
    assertFalse(manager.removeEmptyWorkspace("owned"));
    assertTrue(workspaces.containsKey("owned")); assertTrue(configuration.getWorkspaces().containsKey("owned"));
    verify(manager, never()).saveConfiguration();
  }

  @Test void staleOrInconsistentIndexesCannotHideAnActiveProject() throws Exception {
    var descriptorType = Class.forName(WorkspaceManager.class.getName() + "$ProjectDescriptor");
    var constructor = descriptorType.getDeclaredConstructor(WorkspaceManager.class);
    constructor.setAccessible(true);
    var descriptor = constructor.newInstance(manager);
    var workspace = descriptorType.getDeclaredField("workspace"); workspace.setAccessible(true);
    workspace.set(descriptor, "owned");
    field(manager, "projectDescriptors", Map.of("active", descriptor));
    assertFalse(manager.removeEmptyWorkspace("owned"));
    verify(catalog, never()).deleteMetadata(any());
  }

  @Test void failureAfterConfigurationWriteRestoresPersistedMembership() throws Exception {
    var saves = new java.util.concurrent.atomic.AtomicInteger();
    doAnswer(call -> {
      Utils.YAML.save(configuration, directory.resolve("resources.yaml").toFile());
      if (saves.getAndIncrement() == 0) throw new IllegalStateException("after write");
      return null;
    }).when(manager).saveConfiguration();
    assertThrows(IllegalStateException.class, () -> manager.removeEmptyWorkspace("owned"));
    var restored = Utils.YAML.load(directory.resolve("resources.yaml").toFile(), ResourcesConfiguration.class);
    assertTrue(restored.getWorkspaces().containsKey("owned"));
    assertTrue(restored.getWorkspaces().containsKey("other"));
    verify(catalog).putStatus(info);
  }

  @Test void persistenceFailureRestoresCatalogAndInMemoryStateAndReportsFailure() {
    doThrow(new IllegalStateException("disk failure")).when(manager).saveConfiguration();
    var failure = assertThrows(IllegalStateException.class, () -> manager.removeEmptyWorkspace("owned"));
    assertEquals(0, failure.getSuppressed().length); // Mockito rethrows the same instance; never self-suppress.
    assertTrue(workspaces.containsKey("owned")); assertTrue(configuration.getWorkspaces().containsKey("owned"));
    verify(catalog).putStatus(info); verify(manager, times(2)).saveConfiguration();
  }

  @Test void distinctRestorationFailureIsRetainedOnTheOriginalFailure() {
    doAnswer(call -> { throw new IllegalStateException("disk failure"); }).when(manager).saveConfiguration();
    var failure = assertThrows(IllegalStateException.class, () -> manager.removeEmptyWorkspace("owned"));
    assertEquals(1, failure.getSuppressed().length);
    assertEquals("disk failure", failure.getMessage());
    assertNotSame(failure, failure.getSuppressed()[0]);
    assertTrue(configuration.getWorkspaces().containsKey("owned"));
  }

  @Test void catalogExceptionAndFailedCompensationAreNeverHidden() {
    when(catalog.deleteMetadata("owned")).thenThrow(new IllegalStateException("catalog failure"));
    when(catalog.putStatus(info)).thenReturn(false);
    var failure = assertThrows(IllegalStateException.class, () -> manager.removeEmptyWorkspace("owned"));
    assertEquals(1, failure.getSuppressed().length);
    assertTrue(workspaces.containsKey("owned")); assertTrue(configuration.getWorkspaces().containsKey("owned"));
  }
}
