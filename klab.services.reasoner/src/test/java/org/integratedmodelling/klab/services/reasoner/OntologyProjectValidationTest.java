package org.integratedmodelling.klab.services.reasoner;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.integratedmodelling.common.data.jackson.JacksonConfiguration;
import org.integratedmodelling.klab.api.knowledge.*;
import org.integratedmodelling.klab.api.knowledge.organization.Project;
import org.integratedmodelling.klab.api.lang.kim.*;
import org.integratedmodelling.klab.api.lang.kim.impl.*;
import org.integratedmodelling.klab.api.scope.*;
import org.integratedmodelling.klab.api.services.ResourcesService;
import org.integratedmodelling.klab.api.services.reasoner.objects.*;
import org.integratedmodelling.klab.api.services.runtime.Notification;
import org.integratedmodelling.klab.services.reasoner.internal.*;

public class OntologyProjectValidationTest {
  public static SemanticValidationResponse blockedResponse(String source) throws Exception {
    return validateBlocked(SemanticValidationRequest.of(document(source),"saved"));
  }
  public static SemanticValidationResponse validateBlocked(SemanticValidationRequest request) throws Exception {
    var fixture=new Fixture(null);var project=request.document().getProjectName();
    when(fixture.project.getUrn()).thenReturn(project);
    when(fixture.resources.retrieve(project,Project.class,fixture.scope)).thenReturn(fixture.project);
    return fixture.reasoner.validateDocument(request,fixture.scope);
  }
  static KimOntologyImpl document(String source) {var d=new KimOntologyImpl();d.setUrn("demo.pattern");d.setProjectName("demo");d.setSourceCode(source);return d;}
  static class Fixture {
    final ReasonerService reasoner=mock(ReasonerService.class,CALLS_REAL_METHODS);
    final AcceptedSemanticRuntime runtime=mock(AcceptedSemanticRuntime.class);
    final ResourcesService resources=mock(ResourcesService.class);final UserScope scope=mock(UserScope.class);
    final Project project=mock(Project.class);final Project.Manifest manifest=mock(Project.Manifest.class);
    final CandidatePublicationCoordinator.Status status=new CandidatePublicationCoordinator.Status(3,2,CandidatePublicationCoordinator.BeanStatus.VALID,CandidatePublicationCoordinator.LogicalStatus.VALID,Map.of("ONTOLOGY:base","accepted"),Map.of("ONTOLOGY:base","accepted"),List.of(),List.of());
    Fixture(String defined) throws Exception {
      var f=ReasonerService.class.getDeclaredField("candidateRuntime");f.setAccessible(true);f.set(reasoner,runtime);
      when(runtime.status()).thenReturn(status);when(runtime.submittedSources()).thenReturn(Map.of("ONTOLOGY:base","accepted"));
      when(scope.getService(ResourcesService.class)).thenReturn(resources);when(project.getUrn()).thenReturn("demo");when(project.getManifest()).thenReturn(manifest);when(manifest.getDefinedWorldview()).thenReturn(defined);
      when(resources.retrieve("demo",Project.class,scope)).thenReturn(project);
      when(runtime.validate(any(),any())).thenAnswer(i->{var r=(SemanticValidationResponse)i.getArgument(1);r.setStatus(SemanticValidationResponse.Status.COMPLETE);return r;});
    }
  }
  @Test void authoritativeOrdinaryProjectReturnsSourceBoundErrorsWithoutRepairOrAcceptedMutation() throws Exception {
    var f=new Fixture(null);var d=document("ontology demo.pattern in domain root version 1.0;\n// all concepts commented");
    for(int attempt=0;attempt<3;attempt++) {
      var request=SemanticValidationRequest.of(d,"save-1");var response=f.reasoner.validateDocument(request,f.scope);
      assertEquals(SemanticValidationResponse.Status.COMPLETE,response.getStatus());assertFalse(response.valid());assertEquals(2,response.getKnowledgeRevision());
      assertEquals(SemanticValidationRequest.sourceHash(d.getSourceCode()),response.getSourceHash());
      var issue=response.getNotifications().getFirst();assertEquals(Notification.Level.Error,issue.getLevel());assertTrue(issue.getMessage().contains(OntologyProjectPolicy.CODE));
      assertEquals("demo.pattern",issue.getLexicalContext().getDocumentUrn());assertEquals("demo",issue.getLexicalContext().getProjectUrn());assertEquals(KlabAsset.KnowledgeClass.ONTOLOGY,issue.getLexicalContext().getDocumentType());assertEquals(0,issue.getLexicalContext().getOffsetInDocument());
      assertSame(f.status,f.runtime.status());
    }
    verify(f.runtime,never()).validate(any(),any());verify(f.reasoner,never()).loadKnowledge(any(Worldview.class),any(Scope.class));
    verify(f.resources,never()).retrieve(anyString(),eq(Worldview.class),any());verify(f.resources,never()).retrieve("demo.pattern",KimOntology.class,f.scope);
  }
  @Test void diagnosticSurvivesServiceClientJsonEnvelopeWithoutChangingRequestIdentity() throws Exception {
    var f=new Fixture(" ");var request=SemanticValidationRequest.of(document("ontology demo.pattern;"),"v2");
    var response=f.reasoner.validateDocument(request,f.scope);var mapper=JacksonConfiguration.newObjectMapper();
    var received=mapper.readValue(mapper.writeValueAsBytes(response),SemanticValidationResponse.class);
    assertTrue(received.matches(request,2));assertFalse(received.valid());assertEquals(Notification.Level.Error,received.getNotifications().getFirst().getLevel());assertEquals("demo",received.getNotifications().getFirst().getLexicalContext().getProjectUrn());assertTrue(received.getNotifications().getFirst().getMessage().contains("worldview-defining"));
  }
  @Test void worldviewDefiningProjectKeepsTheExistingValidationPath() throws Exception {
    var f=new Fixture("imod");var response=f.reasoner.validateDocument(SemanticValidationRequest.of(document("ontology demo.pattern;"),"saved"),f.scope);
    assertTrue(response.valid());verify(f.runtime).validate(any(),any());
  }
  @Test void ordinaryKimNamespaceNeverRequiresAWorldviewDefiningProject() throws Exception {
    var f=new Fixture(null);var d=new KimNamespaceImpl();d.setUrn("demo.models");d.setProjectName("demo");d.setSourceCode("namespace demo.models;");
    var response=f.reasoner.validateDocument(SemanticValidationRequest.of(d,"saved"),f.scope);
    assertTrue(response.valid());verify(f.resources,never()).retrieve(anyString(),eq(Project.class),any());verify(f.runtime).validate(any(),any());
  }
}
