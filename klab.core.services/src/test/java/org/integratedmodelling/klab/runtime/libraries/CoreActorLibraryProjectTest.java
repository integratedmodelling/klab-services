package org.integratedmodelling.klab.runtime.libraries;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.*;
import org.integratedmodelling.klab.api.actors.RuntimeAgent;
import org.integratedmodelling.klab.api.authentication.CRUDOperation;
import org.integratedmodelling.klab.api.knowledge.KlabAsset;
import org.integratedmodelling.klab.api.knowledge.organization.ProjectMaterial;
import org.integratedmodelling.klab.api.lang.kim.impl.KimOntologyImpl;
import org.integratedmodelling.klab.api.scope.UserScope;
import org.integratedmodelling.klab.api.services.ResourcesService;
import org.integratedmodelling.klab.api.services.resources.ResourceInfo;
import org.integratedmodelling.klab.runtime.kactors.RuntimeAgentBase;
import org.junit.jupiter.api.Test;

class CoreActorLibraryProjectTest {
  @Test void metadataOnlyActorCanWriteMaterialButCannotMutateOrReadDocumentsOrDeleteMaterial() {
    var user = mock(UserScope.class); var service = mock(ResourcesService.class);
    when(user.getServices(ResourcesService.class)).thenReturn(List.of(service));
    when(service.serviceId()).thenReturn("resources");
    var info = new ResourceInfo(); info.setKnowledgeClass(KlabAsset.KnowledgeClass.PROJECT);
    info.setPermissions(EnumSet.of(CRUDOperation.UPDATE_METADATA));
    when(service.info("project", KlabAsset.KnowledgeClass.PROJECT, ResourceInfo.class, user)).thenReturn(info);
    var scope = mock(RuntimeAgent.Scope.class); when(scope.getScope()).thenReturn(user);
    var project = new CoreActorLibrary.Project("project", "resources");
    project.writeText(scope, "review/note.txt", "hello");
    verify(service).submit(argThat((ProjectMaterial value) -> value.getPath().equals("review/note.txt")
        && Arrays.equals(value.getContent(), "hello".getBytes())), eq(ResourcesService.SubmissionMode.CREATE_OR_UPDATE), same(user));
    assertThrows(RuntimeException.class, () -> project.createDocument(scope, "ONTOLOGY", "test", "ontology test"));
    assertThrows(RuntimeException.class, () -> project.updateDocument(scope, "ONTOLOGY", "test", "ontology test"));
    assertThrows(RuntimeException.class, () -> project.deleteDocument(scope, "ONTOLOGY", "test"));
    assertThrows(RuntimeException.class, () -> project.document(scope, "ONTOLOGY", "test"));
    assertThrows(RuntimeException.class, () -> project.deleteMaterial(scope, "review/note.txt"));
    verify(service, never()).delete(anyString(), any(), any());
  }

  @Test void referencesSurviveCheckpointAndInvocationUsesCurrentParticipant() {
    var document = new KimOntologyImpl(); document.setUrn("test.ontology"); document.setProjectName("project"); document.setServiceId("resources");
    var wrapper = CoreActorLibrary.Document.wrap(document);
    assertInstanceOf(CoreActorLibrary.Ontology.class, wrapper);
    var first = new TestAgent(); var second = new TestAgent();
    try {
      first.save(wrapper);
      var snapshot = first.checkpointState();
      second.restoreCheckpointState(snapshot);
      assertInstanceOf(CoreActorLibrary.Ontology.class, second.value());
      assertEquals(wrapper.checkpointReference(), ((CoreActorLibrary.Document) second.value()).checkpointReference());
      var owner = mock(UserScope.class); var participant = mock(UserScope.class); var service = mock(ResourcesService.class);
      when(participant.getServices(ResourcesService.class)).thenReturn(List.of(service)); when(service.serviceId()).thenReturn("resources");
      var info = new ResourceInfo(); info.setKnowledgeClass(KlabAsset.KnowledgeClass.PROJECT); info.setPermissions(EnumSet.of(CRUDOperation.UPDATE_METADATA));
      when(service.info("project", KlabAsset.KnowledgeClass.PROJECT, ResourceInfo.class, participant)).thenReturn(info);
      second.setCheckpointParticipant(participant);
      var scope = mock(RuntimeAgent.Scope.class); when(scope.getAgent()).thenReturn(second); when(scope.getScope()).thenReturn(owner);
      ((CoreActorLibrary.Document) second.value()).project().writeText(scope, "notes.txt", "participant");
      verify(service).submit(any(ProjectMaterial.class), any(), same(participant)); verifyNoInteractions(owner);
    } finally { first.stop(); second.stop(); }
  }

  static class TestAgent extends RuntimeAgentBase {
    void save(Object value) { setActorState("document", value); }
    Object value() { return ((Map<?, ?>) rootScope()).get("document"); }
    protected ExitValue main(org.integratedmodelling.klab.runtime.kactors.AgentScope scope) { return NORMAL_EXIT; }
    public org.integratedmodelling.klab.api.services.runtime.extension.Verb.Type getAgentExecutionMode() { return org.integratedmodelling.klab.api.services.runtime.extension.Verb.Type.FUNCTION; }
  }
}
