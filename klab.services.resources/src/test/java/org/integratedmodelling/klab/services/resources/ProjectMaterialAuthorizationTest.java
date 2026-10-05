package org.integratedmodelling.klab.services.resources;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.*;
import org.integratedmodelling.klab.api.authentication.CRUDOperation;
import org.integratedmodelling.klab.api.identities.UserIdentity;
import org.integratedmodelling.klab.api.knowledge.KlabAsset.KnowledgeClass;
import org.integratedmodelling.klab.api.knowledge.organization.ProjectMaterial;
import org.integratedmodelling.klab.api.knowledge.organization.Project;
import org.integratedmodelling.klab.api.lang.kim.KimOntology;
import org.integratedmodelling.klab.api.lang.kim.impl.KimOntologyImpl;
import org.integratedmodelling.klab.api.services.ResourcesService.SubmissionMode;
import org.integratedmodelling.klab.api.services.resources.ResourceInfo;
import org.integratedmodelling.klab.resources.ResourcesKBox;
import org.integratedmodelling.klab.services.configuration.ResourcesConfiguration;
import org.integratedmodelling.klab.services.resources.storage.WorkspaceManager;
import org.integratedmodelling.klab.services.scopes.ServiceUserScope;
import org.junit.jupiter.api.Test;

class ProjectMaterialAuthorizationTest {
  @Test void metadataPrivilegeCannotBeUsedToWriteDocumentsOrDeleteMaterialAndRequiresProjectAccess() throws Exception {
    var service = mock(ResourcesProvider.class, CALLS_REAL_METHODS);
    var manager = mock(WorkspaceManager.class); var catalog = mock(ResourcesKBox.class);
    for (var entry : Map.of("workspaceManager", manager, "resourcesKbox", catalog).entrySet()) {
      var field = ResourcesProvider.class.getDeclaredField(entry.getKey()); field.setAccessible(true); field.set(service, entry.getValue());
    }
    when(manager.getConfiguration()).thenReturn(new ResourcesConfiguration());
    var user = mock(ServiceUserScope.class); var identity = mock(UserIdentity.class);
    when(user.getUser()).thenReturn(identity); when(identity.getUsername()).thenReturn("curator");
    when(user.isAuthorized(CRUDOperation.UPDATE_METADATA)).thenReturn(true);
    var info = new ResourceInfo(); info.setKnowledgeClass(KnowledgeClass.PROJECT); info.setOwner("curator");
    when(catalog.getStatus("project", null)).thenReturn(info);
    var material = new ProjectMaterial("project", "review/report.txt", new byte[]{1});
    service.submit(material, SubmissionMode.ADD, user); service.submit(material, SubmissionMode.UPDATE, user);
    verify(manager).writeMaterial(material, SubmissionMode.ADD, user);
    verify(manager).writeMaterial(material, SubmissionMode.UPDATE, user);
    assertThrows(RuntimeException.class, () -> service.delete(material.getUrn(), KnowledgeClass.ADDITIONAL_MATERIAL, user));
    verify(manager, never()).deleteMaterial(any(), any());
    var document = new KimOntologyImpl(); document.setUrn("test"); document.setProjectName("project"); document.setSourceCode("ontology test");
    var project = new org.integratedmodelling.klab.api.knowledge.organization.impl.ProjectImpl(); project.setUrn("project");
    when(manager.getProject("project")).thenReturn(project);
    when(manager.retrieve("test", KimOntology.class)).thenReturn(document);
    assertThrows(RuntimeException.class, () -> service.retrieve("test", KimOntology.class, user));
    for (var mode : List.of(SubmissionMode.ADD, SubmissionMode.UPDATE, SubmissionMode.CREATE_OR_UPDATE, SubmissionMode.REPLACE))
      assertThrows(RuntimeException.class, () -> service.submit(document, mode, user));
    assertThrows(RuntimeException.class, () -> service.delete("project/test", KnowledgeClass.ONTOLOGY, user));
    assertThrows(RuntimeException.class, () -> service.deleteProject("project", user));
    assertThrows(RuntimeException.class, () -> service.createProject("workspace", "new-project", user));
    when(manager.list(Project.class)).thenReturn(List.of(project)); assertTrue(service.list(Project.class, user).isEmpty());
    var workspace = new org.integratedmodelling.klab.api.knowledge.organization.impl.WorkspaceImpl();
    workspace.setUrn("workspace"); workspace.setProjects(List.of(project));
    var workspaceInfo = new ResourceInfo(); workspaceInfo.setKnowledgeClass(KnowledgeClass.WORKSPACE); workspaceInfo.setOwner("curator");
    when(catalog.getStatus("workspace", null)).thenReturn(workspaceInfo); when(manager.getWorkspace("workspace")).thenReturn(workspace);
    assertTrue(service.retrieveWorkspace("workspace", user).getProjects().isEmpty(), "Workspace retrieval must not expose inaccessible documents through projects");
    info.setOwner("somebody-else");
    assertThrows(RuntimeException.class, () -> service.submit(material, SubmissionMode.ADD, user));
  }
}
