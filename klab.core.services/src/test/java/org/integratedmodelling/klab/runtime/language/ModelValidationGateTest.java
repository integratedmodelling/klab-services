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
    namespace.setUrn("ns");namespace.setSourceCode("source fixture"); model.setUrn("ns.model"); model.setNamespace("ns");
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

  @Test void explicitAtomicBearerAllowsOnlyTransparentParserWrappers() {
    var source=new KimObservableImpl();var quality=new KimConceptImpl();quality.setName("w:Quality");source.setSemantics(quality);
    var leaf=new KimConceptImpl();leaf.setName("w:Member");var wrapper=new KimConceptImpl();wrapper.setObservable(leaf);quality.setInherent(wrapper);
    assertSame(leaf,ModelValidationGate.explicitAtomicInherent(source));
    var outer=new KimConceptImpl();outer.setObservable(wrapper);quality.setInherent(outer);
    assertSame(leaf,ModelValidationGate.explicitAtomicInherent(source));
    wrapper.setSemanticModifier(org.integratedmodelling.klab.api.lang.UnarySemanticOperator.COUNT);
    assertNull(ModelValidationGate.explicitAtomicInherent(source));wrapper.setSemanticModifier(null);
    wrapper.getTraits().add(leaf);assertNull(ModelValidationGate.explicitAtomicInherent(source));wrapper.getTraits().clear();
    wrapper.setNegated(true);assertNull(ModelValidationGate.explicitAtomicInherent(source));wrapper.setNegated(false);
    wrapper.setExpressionType(KimConcept.Expression.UNION);wrapper.getOperands().add(leaf);
    assertNull(ModelValidationGate.explicitAtomicInherent(source));wrapper.setExpressionType(KimConcept.Expression.SINGLETON);wrapper.getOperands().clear();
    wrapper.setObservable(outer);assertNull(ModelValidationGate.explicitAtomicInherent(source),"Cyclic AST cannot be treated as transparent");
    quality.setInherent(null);assertNull(ModelValidationGate.explicitAtomicInherent(source),"Inherited native bearer is not authored of evidence");
  }
  @Test void catalogPendingNativeInputRequiresSavedExplicitOccurrenceAndInstalledTypedConsumer() {
    var context=mock(org.integratedmodelling.klab.api.scope.ContextScope.class);
    var runtime=mock(RuntimeService.class);
    when(context.getService(Reasoner.class)).thenReturn(reasoner);
    when(context.getService(ResourcesService.class)).thenReturn(resources);
    when(context.getService(RuntimeService.class)).thenReturn(runtime);
    when(context.withResolutionConstraints(any(org.integratedmodelling.klab.api.services.resolver.ResolutionConstraint[].class))).thenReturn(context);
    when(resources.retrieve("ns",KimNamespace.class,context)).thenReturn(namespace);
    var primary=new KimObservableImpl();primary.setUrn("w:Output");model.getObservables().add(primary);
    var dependency=new KimObservableImpl();var syntax=new KimConceptImpl();syntax.setName("w:Ratio");
    var member=new KimConceptImpl();member.setName("w:Member");syntax.setInherent(member);dependency.setSemantics(syntax);
    dependency.setUrn("w:Ratio of w:Member");dependency.setFormalName("nativeInput");model.getDependencies().add(dependency);
    var consumer=new org.integratedmodelling.common.lang.ContextualizableImpl();
    consumer.setServiceCall(new org.integratedmodelling.common.lang.ServiceCallImpl("fixture.collection"));model.getContextualization().add(consumer);
    var pending=new SemanticValidationResponse.PendingMemberQualityDependency(model.getUrn(),0,dependency.getUrn(),"nativeInput","w:Member",dependency.getUrn());
    when(reasoner.validateDocument(any(),same(context))).thenAnswer(invocation->{
      var request=invocation.getArgument(0,SemanticValidationRequest.class);
      var response=SemanticValidationResponse.forRequest(request);response.setStatus(SemanticValidationResponse.Status.COMPLETE);response.setKnowledgeRevision(7);
      response.getPendingMemberQualityDependencies().add(pending);return response;
    });
    when(runtime.supportsMemberQualityInput(any(),eq("nativeInput"),same(context))).thenReturn(true);
    doAnswer(invocation->new org.integratedmodelling.klab.api.services.runtime.objects.MemberQualityConsumerInspection(true,
        runtime.supportsMemberQualityInput(invocation.getArgument(0),"nativeInput",context)?java.util.Set.of("nativeInput"):java.util.Set.of()))
        .when(runtime).inspectMemberQualityInputs(any(),any());
    var requested=mock(Observable.class);when(requested.getUrn()).thenReturn("w:Output");
    assertEquals(7,ModelValidationGate.validatedPreparableCatalogRevision(model.getUrn(),"ns",resources,context,requested));
    verifyNoInteractions(runtime);
    assertEquals(7,ModelValidationGate.validatedRevision(model.getUrn(),"ns",resources,context,requested));
    var captured=ArgumentCaptor.forClass(SemanticValidationRequest.class);
    verify(reasoner,times(4)).validateDocument(captured.capture(),same(context));
    assertEquals(List.of(pending),captured.getAllValues().getLast().getPreparableMemberQualityDependencies());
    when(runtime.supportsMemberQualityInput(any(),eq("nativeInput"),same(context))).thenReturn(false);
    assertEquals(-1,ModelValidationGate.validatedRevision(model.getUrn(),"ns",resources,context,requested));
    when(runtime.supportsMemberQualityInput(any(),eq("nativeInput"),same(context))).thenReturn(true);
    syntax.setInherent(null);
    assertEquals(-1,ModelValidationGate.validatedPreparableCatalogRevision(model.getUrn(),"ns",resources,context,requested),"Pure discovery does not waive exact authored-of proof");
    assertEquals(-1,ModelValidationGate.validatedRevision(model.getUrn(),"ns",resources,context,requested));
    syntax.setInherent(member);var ordinary=new KimObservableImpl();ordinary.setUrn("w:Other");ordinary.setFormalName("nativeInput");model.getDependencies().add(ordinary);
    assertEquals(-1,ModelValidationGate.validatedRevision(model.getUrn(),"ns",resources,context,requested),"Typed alias cannot also name an ordinary input");
    model.getDependencies().remove(ordinary);syntax.setInherent(null);
    doAnswer(invocation->{var request=invocation.getArgument(0,SemanticValidationRequest.class);
      var response=SemanticValidationResponse.forRequest(request);response.setStatus(SemanticValidationResponse.Status.COMPLETE);response.setKnowledgeRevision(7);return response;
    }).when(reasoner).validateDocument(any(),same(context));
    assertEquals(-1,ModelValidationGate.validatedRevision(model.getUrn(),"ns",resources,context,requested),"Installed collection parameter requires saved collection result-kind proof even for compatible input");
    when(runtime.supportsMemberQualityInput(any(),eq("nativeInput"),same(context))).thenReturn(false);
    assertEquals(7,ModelValidationGate.validatedRevision(model.getUrn(),"ns",resources,context,requested),"Ordinary input parameter stays eligible");
    model.getDependencies().clear();when(runtime.supportsMemberQualityInput(any(),eq("nativeInput"),same(context))).thenReturn(true);
    assertEquals(-1,ModelValidationGate.validatedRevision(model.getUrn(),"ns",resources,context,requested),"Catalog matching needs every installed typed port even without authored aliases");
    when(runtime.supportsMemberQualityInput(any(),eq("nativeInput"),same(context))).thenReturn(false);
    assertEquals(7,ModelValidationGate.validatedRevision(model.getUrn(),"ns",resources,context,requested),"Known empty ordinary method is unaffected");
    doReturn(new org.integratedmodelling.klab.api.services.runtime.objects.MemberQualityConsumerInspection(false,java.util.Set.of()))
        .when(runtime).inspectMemberQualityInputs(any(),any());
    assertEquals(-1,ModelValidationGate.validatedRevision(model.getUrn(),"ns",resources,context,requested),"Unavailable catalog inspection fails closed");    verify(reasoner,never()).semanticDistance(any(),any(),any());
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

  @Test void actualContextIsBoundOnlyForExecutableValidationAndCannotBeReplayed() {
    var contextual=mock(org.integratedmodelling.klab.api.scope.ContextScope.class);
    when(contextual.getService(Reasoner.class)).thenReturn(reasoner);
    when(resources.retrieve("ns",KimNamespace.class,contextual)).thenReturn(namespace);
    var context=mock(org.integratedmodelling.klab.api.knowledge.observation.Observation.class);
    var observable=mock(Observable.class);
    var semantics=mock(org.integratedmodelling.klab.api.knowledge.Concept.class);
    when(semantics.getUrn()).thenReturn("earth:TerrestrialRegion");
    when(observable.getSemantics()).thenReturn(semantics);when(context.getObservable()).thenReturn(observable);
    when(contextual.getContextObservation()).thenReturn(context);
    model.getObservables().add(mock(KimObservable.class));
    var requests=new java.util.ArrayList<SemanticValidationRequest>();
    doAnswer(invocation -> {
      SemanticValidationRequest request=invocation.getArgument(0);requests.add(request);
      var response=SemanticValidationResponse.forRequest(request);
      response.setStatus(SemanticValidationResponse.Status.COMPLETE);response.setKnowledgeRevision(7);return response;
    }).when(reasoner).validateDocument(any(),same(contextual));
    assertEquals(7,ModelValidationGate.validatedRevision("ns.model","ns",resources,contextual,mock(Observable.class)));
    assertNull(requests.getFirst().getExecutionContext());
    assertEquals("earth:TerrestrialRegion",requests.getLast().getExecutionContext());
    doAnswer(invocation -> {
      SemanticValidationRequest request=invocation.getArgument(0);
      var response=SemanticValidationResponse.forRequest(request);
      response.setStatus(SemanticValidationResponse.Status.COMPLETE);response.setKnowledgeRevision(7);
      if(request.isExecutableModels())response.setExecutionContext("earth:Region");return response;
    }).when(reasoner).validateDocument(any(),same(contextual));
    assertEquals(-1,ModelValidationGate.validatedRevision("ns.model","ns",resources,contextual,mock(Observable.class)));
    verify(reasoner,never()).semanticDistance(any(),any(),any(),anyLong());
  }

  @Test void pendingSourceObligationCannotMasqueradeAsAnExecutableReceipt() {
    model.getObservables().add(mock(KimObservable.class));
    doAnswer(invocation -> {
      SemanticValidationRequest request=invocation.getArgument(0);
      var response=SemanticValidationResponse.forRequest(request);
      response.setStatus(SemanticValidationResponse.Status.COMPLETE);response.setKnowledgeRevision(7);
      response.getPendingModelContexts().add(new SemanticValidationResponse.PendingModelContext(
          "ns.model","earth:Region",List.of("geography:Elevation")));
      return response;
    }).when(reasoner).validateDocument(any(),same(scope));
    assertEquals(7,ModelValidationGate.require(namespace,scope));
    assertEquals(-1,ModelValidationGate.validatedRevision("ns.model","ns",resources,scope,mock(Observable.class)));
  }
}
