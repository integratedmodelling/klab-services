package org.integratedmodelling.klab.services.resources.storage;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.integratedmodelling.klab.api.knowledge.organization.Project;
import org.integratedmodelling.klab.api.services.resources.ResourceSet;
import org.integratedmodelling.klab.resources.FileProjectStorage;
import org.integratedmodelling.klab.services.resources.ResourcesProvider;
import org.integratedmodelling.klab.utilities.Utils;
import org.junit.jupiter.api.Test;

class ResetAllGitProjectsTest {
  @Test
  @SuppressWarnings("unchecked")
  void skipsNonGitProjectsAndContinuesAfterAProjectFails() throws Exception {
    var manager = mock(WorkspaceManager.class, CALLS_REAL_METHODS);
    var service = mock(ResourcesProvider.class);
    when(service.serviceId()).thenReturn("resources");
    set(manager, "service", service);
    Map<String, Object> descriptors = new LinkedHashMap<>();
    var type = Class.forName(WorkspaceManager.class.getName() + "$ProjectDescriptor");
    var constructor = type.getDeclaredConstructor(WorkspaceManager.class);
    constructor.setAccessible(true);
    for (var name : List.of("a-fails", "b-works", "c-not-git")) {
      var descriptor = constructor.newInstance(manager);
      var storage = mock(FileProjectStorage.class);
      when(storage.isTracked()).thenReturn(!name.equals("c-not-git"));
      when(storage.getRootFolder()).thenReturn(new File(name));
      set(descriptor, "name", name);
      set(descriptor, "workspace", "workspace");
      set(descriptor, "manifest", mock(Project.Manifest.class));
      set(descriptor, "storage", storage);
      descriptors.put(name, descriptor);
    }
    set(manager, "projectDescriptors", descriptors);
    try (var git = mockStatic(Utils.Git.class)) {
      git.when(() -> Utils.Git.resetToOriginAndFetch(new File("a-fails"), null))
          .thenThrow(new IllegalStateException("Unavailable origin"));
      git.when(() -> Utils.Git.resetToOriginAndFetch(new File("b-works"), null))
          .thenReturn(new Utils.Git.Modifications());
      var result = manager.resetAllGitProjects();
      assertEquals(false, result.get("result"));
      var projects = (Map<String, List<ResourceSet>>) result.get("projects");
      assertEquals(java.util.Set.of("a-fails", "b-works"), projects.keySet());
      assertTrue(Utils.Notifications.hasErrors(projects.get("a-fails").getFirst().getNotifications()));
      assertFalse(Utils.Notifications.hasErrors(projects.get("b-works").getFirst().getNotifications()));
      git.verify(() -> Utils.Git.resetToOriginAndFetch(new File("a-fails"), null));
      git.verify(() -> Utils.Git.resetToOriginAndFetch(new File("b-works"), null));
      git.verifyNoMoreInteractions();
    }
  }

  private static void set(Object target, String name, Object value) throws Exception {
    var field = target.getClass().getDeclaredField(name);
    field.setAccessible(true);
    field.set(target, value);
  }
}
