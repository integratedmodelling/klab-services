package org.integratedmodelling.klab.runtime.language;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.List;
import org.integratedmodelling.klab.api.knowledge.Model;
import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.api.lang.kim.*;
import org.integratedmodelling.klab.api.lang.kim.impl.*;
import org.integratedmodelling.klab.api.scope.UserScope;
import org.integratedmodelling.klab.api.services.*;
import org.integratedmodelling.klab.api.services.reasoner.objects.*;
import org.integratedmodelling.klab.api.services.runtime.Notification;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class ModelValidationGateTest {
  final UserScope scope = mock(UserScope.class);
  final Reasoner reasoner = mock(Reasoner.class);
  final ResourcesService resources = mock(ResourcesService.class);
  final KimNamespaceImpl namespace = new KimNamespaceImpl();
  final KimModelImpl model = new KimModelImpl();

  ModelValidationGateTest() {
    when(scope.getService(Reasoner.class)).thenReturn(reasoner);
    when(scope.getService(ResourcesService.class)).thenReturn(resources);
    namespace.setUrn("ns"); model.setUrn("ns.model"); model.setNamespace("ns");
    namespace.getStatements().add(model);
    when(resources.retrieve("ns", KimNamespace.class, scope)).thenReturn(namespace);
  }

  void status(SemanticValidationResponse.Status status, boolean errors) {
    doAnswer(invocation -> {
      var response = SemanticValidationResponse.forRequest(invocation.getArgument(0));
      response.setStatus(status);
      response.setKnowledgeRevision(7);
      if (errors) response.getNotifications().add(Notification.error("incompatible model dependency"));
      return response;
    }).when(reasoner).validateDocument(any(), same(scope));
  }

  @Test void unavailableStaleAndInvalidSourcesFailClosedWithoutMatching() {
    for (var status : SemanticValidationResponse.Status.values()) {
      status(status, status == SemanticValidationResponse.Status.COMPLETE);
      assertFalse(ModelValidationGate.admits("ns.model", "ns", resources, scope));
    }
    verify(reasoner, never()).semanticDistance(any(), any(), any());
    verify(reasoner, never()).semanticDistance(any(), any(), any(), anyLong());
    assertTrue(namespace.getNotifications().isEmpty());
  }

  @Test void validTemplateSourceIsPreservedAndRequestBindsOnlyPrimaryForExecution() {
    status(SemanticValidationResponse.Status.COMPLETE, false);
    var original = mock(KimObservable.class);
    when(original.getUrn()).thenReturn("w:Template");
    var template = mock(Observable.class);
    when(template.isAbstract()).thenReturn(true);
    when(reasoner.resolveObservable("w:Template")).thenReturn(template);
    var dependency = mock(KimObservable.class);
    model.getObservables().add(original); model.getDependencies().add(dependency);
    var request = mock(Observable.class); when(request.getUrn()).thenReturn("w:Concrete");
    var head = mock(org.integratedmodelling.klab.api.knowledge.Concept.class);
    when(head.getUrn()).thenReturn("w:Head");
    when(reasoner.coreObservable(template)).thenReturn(head);
    when(reasoner.coreObservable(request)).thenReturn(head);
    var concrete = mock(KimObservable.class);
    when(resources.declareObservable("w:Concrete")).thenReturn(concrete);
    assertTrue(ModelValidationGate.admits("ns.model", "ns", resources, scope, request));
    var requests = ArgumentCaptor.forClass(SemanticValidationRequest.class);
    verify(reasoner, times(2)).validateDocument(requests.capture(), same(scope));
    var bound = (KimModel) requests.getAllValues().get(1).getNamespace().getStatements().getFirst();
    assertSame(concrete, bound.getObservables().getFirst());
    assertEquals(List.of(dependency), bound.getDependencies());
    assertEquals(7, requests.getAllValues().get(1).getKnowledgeRevision());
    assertFalse(requests.getAllValues().getFirst().isExecutableModels());
    assertTrue(requests.getAllValues().getLast().isExecutableModels());
    assertSame(original, model.getObservables().getFirst());
    verify(reasoner, never()).semanticDistance(any(), any(), any());
  }

  @Test void missingSavedSourceCannotBeReinterpretedAsAdHocModel() {
    var runtime = mock(Model.class);
    when(runtime.getNamespace()).thenReturn("missing");
    when(runtime.getUrn()).thenReturn("missing.model");
    assertFalse(ModelValidationGate.admits(runtime, scope));
    verify(resources, never()).declareObservable(anyString());
    verify(reasoner, never()).validateDocument(any(), any());
  }

  @Test void mismatchedResponseAndChangedRevisionFailThenRecoverOnFreshValidation() {
    when(reasoner.validateDocument(any(), any())).thenAnswer(invocation -> {
      var response = SemanticValidationResponse.forRequest(invocation.getArgument(0));
      response.setStatus(SemanticValidationResponse.Status.COMPLETE);
      response.setKnowledgeRevision(7); response.setDocumentVersion("old"); return response;
    });
    assertFalse(ModelValidationGate.admits("ns.model", "ns", resources, scope));
    status(SemanticValidationResponse.Status.COMPLETE, false);
    assertEquals(7, ModelValidationGate.validatedRevision("ns.model", "ns", resources, scope, null));
  }

  @Test void validSavedSourceDoesNotHideInvalidActualRuntimeDependencies() {
    status(SemanticValidationResponse.Status.COMPLETE, false);
    var runtime = mock(Model.class);
    when(runtime.getNamespace()).thenReturn("ns"); when(runtime.getUrn()).thenReturn("ns.model");
    var output = mock(Observable.class); when(output.getUrn()).thenReturn("w:Output");
    var dependency = mock(Observable.class); when(dependency.getUrn()).thenReturn("w:BadDependency");
    when(runtime.getObservables()).thenReturn(List.of(output));
    when(runtime.getDependencies()).thenReturn(List.of(dependency));
    when(resources.declareObservable("w:Output")).thenReturn(mock(KimObservable.class));
    var bad = mock(KimObservable.class);
    when(resources.declareObservable("w:BadDependency")).thenReturn(bad);
    when(reasoner.validateDocument(any(), same(scope))).thenAnswer(invocation -> {
      SemanticValidationRequest request = invocation.getArgument(0);
      var response = SemanticValidationResponse.forRequest(request);
      response.setStatus(SemanticValidationResponse.Status.COMPLETE); response.setKnowledgeRevision(7);
      if (request.getNamespace().getStatements().stream().filter(KimModel.class::isInstance)
          .map(KimModel.class::cast).anyMatch(m -> m.getDependencies().contains(bad)))
        response.getNotifications().add(Notification.error("invalid actual dependency"));
      return response;
    });
    assertEquals(-1, ModelValidationGate.validatedRevision(runtime, scope, null));
  }

  @Test void secondaryRequestPreservesConcreteProcessPrimary() {
    status(SemanticValidationResponse.Status.COMPLETE, false);
    var primary = mock(KimObservable.class); when(primary.getUrn()).thenReturn("w:Process");
    model.getObservables().add(primary);
    var process = mock(Observable.class);
    when(reasoner.resolveObservable("w:Process")).thenReturn(process);
    var request = mock(Observable.class); when(request.getUrn()).thenReturn("w:Quality");
    var firstHead = mock(org.integratedmodelling.klab.api.knowledge.Concept.class);
    var secondHead = mock(org.integratedmodelling.klab.api.knowledge.Concept.class);
    when(firstHead.getUrn()).thenReturn("w:ProcessHead"); when(secondHead.getUrn()).thenReturn("w:QualityHead");
    when(reasoner.coreObservable(process)).thenReturn(firstHead);
    when(reasoner.coreObservable(request)).thenReturn(secondHead);
    assertEquals(7, ModelValidationGate.validatedRevision("ns.model", "ns", resources, scope, request));
    var captor = ArgumentCaptor.forClass(SemanticValidationRequest.class);
    verify(reasoner, times(2)).validateDocument(captor.capture(), same(scope));
    var bound = (KimModel) captor.getAllValues().getLast().getNamespace().getStatements().getFirst();
    assertSame(primary, bound.getObservables().getFirst());
    verify(resources, never()).declareObservable("w:Quality");
  }

  @Test void knowledgeChangeBetweenSourceAndBoundValidationIsRejected() {
    var primary = mock(KimObservable.class); model.getObservables().add(primary);
    var request = mock(Observable.class);
    when(reasoner.validateDocument(any(), same(scope))).thenAnswer(invocation -> {
      SemanticValidationRequest validation = invocation.getArgument(0);
      var response = SemanticValidationResponse.forRequest(validation);
      response.setStatus(SemanticValidationResponse.Status.COMPLETE);
      response.setKnowledgeRevision(validation.getKnowledgeRevision() < 0 ? 7 : 8);
      return response;
    });
    assertEquals(-1, ModelValidationGate.validatedRevision("ns.model", "ns", resources, scope, request));
  }

  @Test void runtimeFormalNamesAreDetachedFromCachedParserObjects() {
    status(SemanticValidationResponse.Status.COMPLETE, false);
    var runtime = mock(Model.class);
    var output = mock(Observable.class);
    when(output.getUrn()).thenReturn("w:Output"); when(output.getStatedName()).thenReturn("output");
    when(runtime.getObservables()).thenReturn(List.of(output));
    var cached = new KimObservableImpl();
    var semantics = new KimConceptImpl(); semantics.setName("w:Output");
    cached.setSemantics(semantics); cached.setUrn("w:Output");
    when(resources.declareObservable("w:Output")).thenReturn(cached);
    when(reasoner.resolveObservable("w:Output")).thenReturn(output);
    assertEquals(7, ModelValidationGate.validatedRevision(runtime, scope, null));
    assertNull(cached.getFormalName());
    var requests = ArgumentCaptor.forClass(SemanticValidationRequest.class);
    verify(reasoner).validateDocument(requests.capture(), same(scope));
    var bound = (KimModel) requests.getValue().getNamespace().getStatements().getFirst();
    assertEquals("output", bound.getObservables().getFirst().getFormalName());
    assertNotSame(cached, bound.getObservables().getFirst());
  }

  @Test void admittedTemplateCannotExecuteWhenRequestedBearerSpecializationIsUnproved() {
    status(SemanticValidationResponse.Status.COMPLETE, false);
    var primary = new KimObservableImpl(); primary.setUrn("w:Template");
    var syntax = new KimConceptImpl(); syntax.setName("w:Template"); primary.setSemantics(syntax);
    model.getObservables().add(primary);
    var template = mock(Observable.class); when(template.isAbstract()).thenReturn(true);
    when(reasoner.resolveObservable("w:Template")).thenReturn(template);
    var request = mock(Observable.class);
    var head = mock(org.integratedmodelling.klab.api.knowledge.Concept.class);
    when(head.getUrn()).thenReturn("w:Head");
    when(reasoner.coreObservable(template)).thenReturn(head); when(reasoner.coreObservable(request)).thenReturn(head);
    var allowed = mock(org.integratedmodelling.klab.api.knowledge.Concept.class);
    var unknown = mock(org.integratedmodelling.klab.api.knowledge.Concept.class);
    when(reasoner.inherent(template)).thenReturn(allowed); when(reasoner.inherent(request)).thenReturn(unknown);
    assertEquals(7, ModelValidationGate.require(namespace, scope));
    assertEquals(-1, ModelValidationGate.validatedRevision("ns.model", "ns", resources, scope, request));
    verify(reasoner).is(unknown, allowed);
    verify(reasoner, never()).semanticDistance(any(), any(), any(), anyLong());
  }

  @Test void sourceModeResponseCannotBeReplayedAsExecutableAdmission() {
    model.getObservables().add(mock(KimObservable.class));
    doAnswer(invocation -> {
      SemanticValidationRequest request = invocation.getArgument(0);
      var response = SemanticValidationResponse.forRequest(request);
      response.setStatus(SemanticValidationResponse.Status.COMPLETE); response.setKnowledgeRevision(7);
      response.setExecutableModels(false);
      return response;
    }).when(reasoner).validateDocument(any(), same(scope));
    assertEquals(7, ModelValidationGate.require(namespace, scope));
    assertEquals(-1, ModelValidationGate.validatedRevision("ns.model", "ns", resources, scope,
        mock(Observable.class)));
  }
}
