package org.integratedmodelling.klab.runtime.language;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.*;
import org.integratedmodelling.common.knowledge.*;
import org.integratedmodelling.common.lang.ContextualizableImpl;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.knowledge.*;
import org.integratedmodelling.klab.api.lang.kim.*;
import org.integratedmodelling.klab.api.lang.kim.impl.*;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.services.*;
import org.integratedmodelling.klab.api.services.reasoner.objects.SemanticValidationResponse;
import org.junit.jupiter.api.*;

class ModelAdmissionReceiptTest {
  final ContextScope scope=mock(ContextScope.class);
  final Reasoner reasoner=mock(Reasoner.class);
  final Reasoner.Capabilities cap=mock(Reasoner.Capabilities.class);
  final ResourcesService resources=mock(ResourcesService.class);
  final ModelImpl model=new ModelImpl();
  final KimNamespaceImpl namespace=new KimNamespaceImpl();
  ModelAdmissionReceiptTest(){
    when(scope.getService(Reasoner.class)).thenReturn(reasoner);when(scope.getService(ResourcesService.class)).thenReturn(resources);
    when(reasoner.capabilities(scope)).thenReturn(cap);when(cap.getServiceId()).thenReturn("reasoner");when(cap.getKnowledgeRevision()).thenReturn(3L);
    namespace.setUrn("test");namespace.setLastUpdateTimestamp(17);namespace.setSourceCode("model test:Quality set to 1;");
    var source=new KimModelImpl();source.setUrn("test:model");namespace.getStatements().add(source);
    when(resources.retrieve("test",KimNamespace.class,scope)).thenReturn(namespace);
    when(resources.declareObservable(anyString())).thenReturn(new KimObservableImpl());
    when(reasoner.validateDocument(any(),eq(scope))).thenAnswer(call->{var response=SemanticValidationResponse.forRequest(call.getArgument(0));
      response.setStatus(SemanticValidationResponse.Status.COMPLETE);response.setKnowledgeRevision(3);return response;});
    var concept=new ConceptImpl();concept.setUrn("test:Quality");concept.setName("quality");concept.setReferenceName("quality");
    concept.setType(EnumSet.of(SemanticType.QUALITY));concept.setDescriptionType(Contextualization.QUANTIFICATION);
    model.setUrn("test:model");model.setNamespace("test");model.setObservables(new ArrayList<>(List.of(ObservableImpl.promote(concept,scope))));
    model.setType(Artifact.Type.NUMBER);model.setCoverage(Geometry.create("t0(1){tstart=0,tend=10,ttype=PHYSICAL}"));
    model.setDescriptionType(Contextualization.QUANTIFICATION);
    var code=new ContextualizableImpl();code.setLiteral(1.0);model.setComputation(new ArrayList<>(List.of(code)));
  }
  private void installedInspection(RuntimeService runtime,String port) {
    doAnswer(invocation->new org.integratedmodelling.klab.api.services.runtime.objects.MemberQualityConsumerInspection(true,
        runtime.supportsMemberQualityInput(invocation.getArgument(0),port,scope)?Set.of(port):Set.of()))
        .when(runtime).inspectMemberQualityInputs(any(),any());
  }

  @Test void namespacePublicationAndAllDescriptorStoresShareOneSourceValidation() {
    try(var publication=ModelValidationGate.openPublicationSession(namespace,scope)) {
      assertNotNull(ModelValidationGate.sourceAdmission(namespace,scope,3));
      ModelValidationGate.require(namespace,scope);
      for(int i=0;i<20;i++)ModelValidationGate.require(model.getUrn(),model.getNamespace(),resources,scope);
      verify(reasoner,times(1)).validateDocument(any(),eq(scope));
      verify(resources,never()).retrieve(anyString(),eq(KimNamespace.class),any());
      namespace.setSourceCode("changed during publication");
      ModelValidationGate.require(namespace,scope);
      verify(reasoner,times(2)).validateDocument(any(),eq(scope));
    }
    ModelValidationGate.require(namespace,scope);
    verify(reasoner,times(3)).validateDocument(any(),eq(scope));
  }

  private void publish() {
    var response=ModelValidationGate.sourceAdmission(namespace,scope,3);
    namespace.getMetadata().put(ResourcesService.NAMESPACE_ADMISSION_METADATA,
        org.integratedmodelling.klab.utilities.Utils.Json.asString(new ModelValidationGate.NamespaceAdmission(
            "reasoner",org.integratedmodelling.klab.utilities.Utils.Json.asString(response),
            Map.of(model.getUrn(),ModelValidationGate.executableHash(model)))));
    clearInvocations(reasoner,resources);
  }

  @Test void unchangedPublishedModelNeedsNoSubmissionDocumentValidationOrReparsing() {
    publish();
    var receipt=ModelValidationGate.validatedPreparableAdmission(model,scope,null);
    assertNotNull(receipt);
    assertTrue(receipt.sourceBacked());assertEquals(3,receipt.knowledgeRevision());
    verify(reasoner,never()).validateDocument(any(),any());
    verify(resources,never()).declareObservable(anyString());
  }

  @Test void catalogDiscoveryReusesPublicationWithoutSemanticDocumentValidation() {
    publish();
    assertEquals(3,ModelValidationGate.validatedPreparableCatalogRevision(
        model.getUrn(),model.getNamespace(),resources,scope,model.getObservables().getFirst()));
    assertEquals(3,ModelValidationGate.validatedRevision(
        model.getUrn(),model.getNamespace(),resources,scope,model.getObservables().getFirst()));
    verify(reasoner,never()).validateDocument(any(),any());
    verify(resources,never()).declareObservable(anyString());
  }

  @Test void modifiedExecutableRetainsPublicationSourceProofButNeedsExecutableAdmission() {
    publish();
    var changed=new ContextualizableImpl();changed.setLiteral(2.0);model.setComputation(List.of(changed));
    assertNotNull(ModelValidationGate.validatedPreparableAdmission(model,scope,null));
    verify(reasoner,times(1)).validateDocument(argThat(request -> request.isExecutableModels()),eq(scope));
  }

  @Test void conceptBuiltAdHocModelStillNeedsAdmission() {
    publish();model.setNamespace(null);
    assertNotNull(ModelValidationGate.validatedPreparableAdmission(model,scope,null));
    verify(reasoner,times(1)).validateDocument(argThat(request -> request.isExecutableModels()),eq(scope));
  }

  @Test void staleOrMalformedNamespacePublicationCannotBypassAdmission() {
    publish();namespace.setLastUpdateTimestamp(18);
    assertNotNull(ModelValidationGate.validatedPreparableAdmission(model,scope,null));
    verify(reasoner,times(2)).validateDocument(any(),eq(scope));
    namespace.getMetadata().put(ResourcesService.NAMESPACE_ADMISSION_METADATA,"malformed");
    clearInvocations(reasoner);
    assertNotNull(ModelValidationGate.validatedPreparableAdmission(model,scope,null));
    verify(reasoner,times(2)).validateDocument(any(),eq(scope));
  }

  @Test void contextDependentPublishedModelStillChecksActualInvocationContext() {
    var response=ModelValidationGate.sourceAdmission(namespace,scope,3);
    response.getConditionalModelContexts().add(new SemanticValidationResponse.ConditionalModelContext(
        model.getUrn(),List.of("test:Region")));
    namespace.getMetadata().put(ResourcesService.NAMESPACE_ADMISSION_METADATA,
        org.integratedmodelling.klab.utilities.Utils.Json.asString(new ModelValidationGate.NamespaceAdmission(
            "reasoner",org.integratedmodelling.klab.utilities.Utils.Json.asString(response),
            Map.of(model.getUrn(),ModelValidationGate.executableHash(model)))));
    clearInvocations(reasoner);
    assertNotNull(ModelValidationGate.validatedPreparableAdmission(model,scope,null));
    verify(reasoner,times(1)).validateDocument(argThat(request -> request.isExecutableModels()),eq(scope));
    assertEquals(-1,ModelValidationGate.validatedPreparableCatalogRevision(
        model.getUrn(),model.getNamespace(),resources,scope,model.getObservables().getFirst()),
        "A publication with pending context cannot authorize catalog admission by its model-name entry alone");
  }

  @Test void changedSemanticGenerationDoesNotReusePublicationAdmission() {
    publish();when(cap.getKnowledgeRevision()).thenReturn(4L);
    assertNull(ModelValidationGate.validatedPreparableAdmission(model,scope,null));
    verify(reasoner,times(1)).validateDocument(any(),eq(scope));
  }

  @Test void inactiveOrDeferredNamespaceIsNotAnAdmittedSubmission() {
    publish();namespace.setInactive(true);
    assertNull(ModelValidationGate.validatedPreparableAdmission(model,scope,null));
    namespace.setInactive(false);namespace.getMetadata().put(ResourcesService.VALIDATION_DEFERRED_METADATA,true);
    assertNull(ModelValidationGate.validatedPreparableAdmission(model,scope,null));
    verify(reasoner,never()).validateDocument(any(),any());
  }

  @Test void oneResolutionValidatesEachExactSourceAndExecutableSnapshotOnlyOnce() {
    try (var session = ModelValidationGate.openSession()) {
      var admitted = ModelValidationGate.validatedPreparableAdmission(model,scope,null);
      assertNotNull(admitted);
      for (int i=0;i<10;i++)
        assertEquals(admitted,ModelValidationGate.validatedPreparableAdmission(model,scope,null));
      verify(reasoner,times(2)).validateDocument(any(),eq(scope));
      verify(reasoner,times(1)).capabilities(scope);
      verify(resources,times(1)).retrieve("test",KimNamespace.class,scope);
    }
    assertNotNull(ModelValidationGate.validatedPreparableAdmission(model,scope,null));
    verify(reasoner,times(4)).validateDocument(any(),eq(scope));
  }

  @Test void sourceEditsAndNewSemanticGenerationsCannotRewriteAnInFlightSnapshot() {
    ModelValidationGate.AdmissionReceipt admitted;
    try (var session = ModelValidationGate.openSession()) {
      admitted=ModelValidationGate.validatedPreparableAdmission(model,scope,null);
      assertNotNull(admitted);
      namespace.setSourceCode("new source while transaction is running");
      namespace.setLastUpdateTimestamp(18);
      when(cap.getKnowledgeRevision()).thenReturn(4L);
      assertTrue(ModelValidationGate.matches(admitted,model,scope));
      assertEquals(admitted,ModelValidationGate.validatedPreparableAdmission(model,scope,null));
      verify(reasoner,times(2)).validateDocument(any(),eq(scope));
    }
    assertFalse(ModelValidationGate.matches(admitted,model,scope));
  }

  @Test void executableMutationsStillNeedTheirOwnAdmissionWithinAResolution() {
    try (var session = ModelValidationGate.openSession()) {
      var admitted=ModelValidationGate.validatedPreparableAdmission(model,scope,null);
      assertNotNull(admitted);
      var changed=new ContextualizableImpl();changed.setLiteral(2.0);
      model.setComputation(List.of(changed));
      assertFalse(ModelValidationGate.matches(admitted,model,scope));
      assertNotEquals(admitted,ModelValidationGate.validatedPreparableAdmission(model,scope,null));
      // Saved namespace is unchanged; only the new executable needs validation.
      verify(reasoner,times(3)).validateDocument(any(),eq(scope));
    }
  }
  @Test void sourcePreparationIsNotInstalledAdmissionAndRejectsPreparationDrift() {
    var call=new ContextualizableImpl();call.setServiceCall(new org.integratedmodelling.common.lang.ServiceCallImpl("fixture.uninstalled"));
    model.setComputation(List.of(call));
    var runtime=mock(RuntimeService.class);when(scope.getService(RuntimeService.class)).thenReturn(runtime);
    when(scope.withResolutionConstraints(any(org.integratedmodelling.klab.api.services.resolver.ResolutionConstraint[].class))).thenReturn(scope);
    var preparation=ModelValidationGate.validatedSourcePreparation(model,scope,null);assertNotNull(preparation);
    assertEquals("17",preparation.sourceVersion());assertEquals("reasoner",preparation.reasonerId());
    assertTrue(ModelValidationGate.matches(preparation,model,scope));
    verifyNoInteractions(runtime);
    assertNull(ModelValidationGate.validatedPreparableAdmission(model,scope,null),"Unknown installed shape is not executable");
    doReturn(org.integratedmodelling.klab.api.services.runtime.objects.MemberQualityConsumerInspection.known(Set.of()))
        .when(runtime).inspectMemberQualityInputs(any(),any());
    var admission=ModelValidationGate.validatedPreparableAdmission(model,scope,null);assertNotNull(admission);
    assertTrue(ModelValidationGate.sameSnapshot(preparation,admission));
    namespace.setSourceCode("changed while preparing");assertFalse(ModelValidationGate.matches(preparation,model,scope));
    var changed=ModelValidationGate.validatedPreparableAdmission(model,scope,null);assertNotNull(changed);
    assertFalse(ModelValidationGate.sameSnapshot(preparation,changed),"A new full admission cannot relabel the original source snapshot");
    namespace.setSourceCode("model test:Quality set to 1;");call.setServiceCall(null);
    assertFalse(ModelValidationGate.matches(preparation,model,scope));
  }

  @Test void preparationRequiresKnownNamedSourceAndDoesNotAcceptInvalidOrChangedGeneration() {
    assertNotNull(ModelValidationGate.validatedSourcePreparation(model,scope,null));
    namespace.setSourceCode(" ");assertNull(ModelValidationGate.validatedSourcePreparation(model,scope,null));
    namespace.setSourceCode("model test:Quality set to 1;");
    doReturn(null).when(reasoner).validateDocument(any(),eq(scope));assertNull(ModelValidationGate.validatedSourcePreparation(model,scope,null));
    model.setNamespace(null);assertNull(ModelValidationGate.validatedSourcePreparation(model,scope,null));
  }

  @Test void exactReceiptSurvivesProductionModelAndReceiptTransportWithoutRepeatedValidation() throws Exception {
    var receipt=ModelValidationGate.validatedPreparableAdmission(model,scope,null);assertNotNull(receipt);
    verify(reasoner,times(2)).validateDocument(any(),eq(scope)); // Source and executable obligations once each.
    var mapper=org.integratedmodelling.common.data.jackson.JacksonConfiguration.newObjectMapper();
    var transported=mapper.readValue(mapper.writeValueAsBytes(model),Model.class);
    var recovered=mapper.readValue(mapper.writeValueAsBytes(receipt),ModelValidationGate.AdmissionReceipt.class);
    assertEquals(receipt,recovered);assertEquals(ModelValidationGate.executableHash(model),ModelValidationGate.executableHash(transported));
    assertTrue(ModelValidationGate.matches(recovered,transported,scope));verify(reasoner,times(2)).validateDocument(any(),eq(scope));
    model.setServiceId("different-route");model.getMetadata().put("diagnostic","not executable meaning");
    assertTrue(ModelValidationGate.matches(receipt,model,scope));
  }
  @Test void unitfulExecutableHashSurvivesInterfaceTransportAndEquivalentReconstruction() throws Exception {
    var unit=new org.integratedmodelling.klab.api.data.mediation.impl.UnitImpl("m");unit.setContextual(true);
    unit.getAggregatedDimensions().put(org.integratedmodelling.klab.api.knowledge.observation.scale.ExtentDimension.AREAL,
        org.integratedmodelling.klab.api.knowledge.observation.scale.ExtentDistribution.EXTENSIVE);
    ((ObservableImpl)model.getObservables().getFirst()).setUnit(unit);
    var expected=ModelValidationGate.executableHash(model);
    var mapper=org.integratedmodelling.common.data.jackson.JacksonConfiguration.newObjectMapper();
    var transported=mapper.readValue(mapper.writeValueAsBytes(model),Model.class);
    assertEquals(expected,ModelValidationGate.executableHash(transported));
    var equivalent=new org.integratedmodelling.klab.api.data.mediation.impl.UnitImpl("m");equivalent.setContextual(true);
    equivalent.setAggregatedDimensions(new HashMap<>(unit.getAggregatedDimensions()));
    ((ObservableImpl)model.getObservables().getFirst()).setUnit(equivalent);
    assertEquals(expected,ModelValidationGate.executableHash(model));
    equivalent.setDefinition("cm");assertNotEquals(expected,ModelValidationGate.executableHash(model));
    equivalent.setDefinition("m");equivalent.setContextual(false);assertNotEquals(expected,ModelValidationGate.executableHash(model));
    equivalent.setContextual(true);equivalent.getAggregatedDimensions().clear();assertNotEquals(expected,ModelValidationGate.executableHash(model));
  }
  @Test void pendingMemberReceiptsComeOnlyFromSavedSourceAndRemainNonExecutableWithoutProof() throws Exception {
    var dependency=(ObservableImpl)model.getObservables().getFirst();dependency.setStatedName("nativeInput");model.setDependencies(List.of(dependency));
    var consumer=new ContextualizableImpl();consumer.setServiceCall(new org.integratedmodelling.common.lang.ServiceCallImpl("fixture.collection.consumer"));model.setComputation(List.of(consumer));
    var runtime=mock(org.integratedmodelling.klab.api.services.RuntimeService.class);
    when(scope.getService(org.integratedmodelling.klab.api.services.RuntimeService.class)).thenReturn(runtime);
    when(scope.withResolutionConstraints(any(org.integratedmodelling.klab.api.services.resolver.ResolutionConstraint[].class))).thenReturn(scope);
    when(runtime.supportsMemberQualityInput(any(),eq("nativeInput"),eq(scope))).thenReturn(true);
    installedInspection(runtime,"nativeInput");
    var pending=new org.integratedmodelling.klab.api.services.reasoner.objects.SemanticValidationResponse.PendingMemberQualityDependency(
        model.getUrn(),0,"authored:Quality of authored:Member","nativeInput","authored:Member",dependency.getUrn());
    when(reasoner.validateDocument(any(),eq(scope))).thenAnswer(call->{
      var request=call.getArgument(0,org.integratedmodelling.klab.api.services.reasoner.objects.SemanticValidationRequest.class);
      var response=SemanticValidationResponse.forRequest(request);response.setStatus(SemanticValidationResponse.Status.COMPLETE);response.setKnowledgeRevision(3);
      response.getPendingMemberQualityDependencies().add(pending);
      return response;
    });
    assertEquals(-1,ModelValidationGate.validatedRevision(model,scope,null));
    var receipt=ModelValidationGate.validatedPreparableAdmission(model,scope,null);assertNotNull(receipt);
    assertEquals(List.of(pending),receipt.pendingMemberQualities());
    var ordinary=new ObservableImpl(dependency);ordinary.setStatedName("nativeInput");model.setDependencies(List.of(dependency,ordinary));
    assertNull(ModelValidationGate.validatedPreparableAdmission(model,scope,null),"Collection alias must identify exactly one executable input");
    model.setDependencies(List.of(dependency));
    var mapper=org.integratedmodelling.common.data.jackson.JacksonConfiguration.newObjectMapper();
    assertEquals(receipt,mapper.readValue(mapper.writeValueAsBytes(receipt),ModelValidationGate.AdmissionReceipt.class));
    // A reconstructed normalized expression cannot fabricate explicit source evidence.
    when(reasoner.validateDocument(any(),eq(scope))).thenAnswer(call->{
      var request=call.getArgument(0,org.integratedmodelling.klab.api.services.reasoner.objects.SemanticValidationRequest.class);
      var response=SemanticValidationResponse.forRequest(request);response.setStatus(SemanticValidationResponse.Status.COMPLETE);response.setKnowledgeRevision(3);
      if(request.isExecutableModels())response.getPendingMemberQualityDependencies().add(pending);return response;
    });
    assertNull(ModelValidationGate.validatedPreparableAdmission(model,scope,null));
  }

  @Test void compatibleOrdinaryQualityCannotSatisfyInstalledCollectionInputWithoutSourceProof() {
    var dependency=new ObservableImpl((ObservableImpl)model.getObservables().getFirst());dependency.setStatedName("nativeInput");model.setDependencies(List.of(dependency));
    var consumer=new ContextualizableImpl();consumer.setServiceCall(new org.integratedmodelling.common.lang.ServiceCallImpl("fixture.consumer"));model.setComputation(List.of(consumer));
    var runtime=mock(RuntimeService.class);when(scope.getService(RuntimeService.class)).thenReturn(runtime);
    when(scope.withResolutionConstraints(any(org.integratedmodelling.klab.api.services.resolver.ResolutionConstraint[].class))).thenReturn(scope);
    when(runtime.supportsMemberQualityInput(any(),eq("nativeInput"),eq(scope))).thenReturn(true);
    installedInspection(runtime,"nativeInput");
    assertNull(ModelValidationGate.validatedPreparableAdmission(model,scope,null),"Ordinary compatible semantics are not a complete native collection result");
    verify(reasoner,times(1)).validateDocument(any(),eq(scope));
    verify(reasoner,never()).semanticDistance(any(),any(),any());
    when(runtime.supportsMemberQualityInput(any(),eq("nativeInput"),eq(scope))).thenReturn(false);
    assertNotNull(ModelValidationGate.validatedPreparableAdmission(model,scope,null),"Ordinary consumers retain normal admission");
    model.setDependencies(List.of());when(runtime.supportsMemberQualityInput(any(),eq("nativeInput"),eq(scope))).thenReturn(true);
    assertNull(ModelValidationGate.validatedPreparableAdmission(model,scope,null),"A required installed port cannot disappear merely because no source alias names it");
    when(runtime.supportsMemberQualityInput(any(),eq("nativeInput"),eq(scope))).thenReturn(false);
    assertNotNull(ModelValidationGate.validatedPreparableAdmission(model,scope,null),"Known empty input shape remains a valid ordinary consumer");
    doReturn(new org.integratedmodelling.klab.api.services.runtime.objects.MemberQualityConsumerInspection(false,Set.of()))
        .when(runtime).inspectMemberQualityInputs(any(),any());
    assertNull(ModelValidationGate.validatedPreparableAdmission(model,scope,null),"Unavailable inspection is not known empty");
    doReturn(null).when(runtime).inspectMemberQualityInputs(any(),any());
    assertNull(ModelValidationGate.validatedPreparableAdmission(model,scope,null),"Missing transport result is not known empty");
    verify(reasoner,never()).semanticDistance(any(),any(),any(),anyLong());
    verify(runtime,never()).submit(any(org.integratedmodelling.klab.api.knowledge.observation.Observation.class),eq(scope));
  }
  @Test void changedSourceSameSemanticRevisionAndChangedNamespaceVersionAreRejected() {
    var receipt=ModelValidationGate.validatedPreparableAdmission(model,scope,null);assertNotNull(receipt);
    namespace.setSourceCode("model test:Quality set to 2;");assertFalse(ModelValidationGate.matches(receipt,model,scope));
    namespace.setSourceCode("model test:Quality set to 1;");namespace.setLastUpdateTimestamp(18);assertFalse(ModelValidationGate.matches(receipt,model,scope));
  }
  @Test void changedExecutableWithoutNamespaceChangeIsRejected() {
    var receipt=ModelValidationGate.validatedPreparableAdmission(model,scope,null);assertNotNull(receipt);
    var code=new ContextualizableImpl();code.setLiteral(2.0);model.setComputation(List.of(code));assertFalse(ModelValidationGate.matches(receipt,model,scope));
  }
  @Test void changedReasonerIdentityOrRevisionIsRejectedEvenWhenSourceUnchanged() {
    var receipt=ModelValidationGate.validatedPreparableAdmission(model,scope,null);assertNotNull(receipt);
    when(cap.getServiceId()).thenReturn("replacement");assertFalse(ModelValidationGate.matches(receipt,model,scope));
    when(cap.getServiceId()).thenReturn("reasoner");when(cap.getKnowledgeRevision()).thenReturn(4L);assertFalse(ModelValidationGate.matches(receipt,model,scope));
  }
  @Test void namespaceFreeAdHocReceiptDoesNotPretendToHaveSavedSource() {
    model.setNamespace(null);var receipt=ModelValidationGate.validatedPreparableAdmission(model,scope,null);assertNotNull(receipt);
    assertFalse(receipt.sourceBacked());assertNull(receipt.sourceHash());assertNull(receipt.sourceVersion());
    assertTrue(ModelValidationGate.matches(receipt,model,scope));verify(resources,never()).retrieve(anyString(),eq(KimNamespace.class),any());
  }
}
