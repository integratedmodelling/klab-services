package org.integratedmodelling.klab.runtime.language;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.*;
import org.integratedmodelling.klab.api.services.ResourcesService;
import org.integratedmodelling.klab.api.services.reasoner.objects.*;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class FrozenSourceAdmissionTest {
  private SemanticValidationResponse publish(ModelAdmissionReceiptTest f,boolean covered) {
    var response=ModelValidationGate.sourceAdmission(f.namespace,f.scope,3);

    response.setSemanticObligationsComplete(false);response.setSourceSemanticsComplete(covered);
    var request=new SemanticDependencyRequest(f.cap.getServiceId(),3,f.cap.getSemanticContentFingerprint(),response.getSemanticObligations());
    response.setSemanticWitness(SemanticDependencyWitness.unknown(request,"Opaque execution footprint"));

    f.namespace.getMetadata().put(ResourcesService.NAMESPACE_ADMISSION_METADATA,
        org.integratedmodelling.klab.utilities.Utils.Json.asString(new ModelValidationGate.NamespaceAdmission(
            "reasoner",org.integratedmodelling.klab.utilities.Utils.Json.asString(response),
            Map.of(f.model.getUrn(),ModelValidationGate.executableHash(f.model)),response.getSemanticContentFingerprint(),ModelValidationGate.SOURCE_ADMISSION_CONTRACT)));
    clearInvocations(f.reasoner,f.resources);return response;
  }

  @Test void frozenSourceAvoidsWholeNamespaceValidationButKeepsExecutableValidation() {
    var f=new ModelAdmissionReceiptTest();var source=publish(f,true);
    assertNull(ModelValidationGate.publicationHash(f.model,source));
    var bytes=f.namespace.getMetadata().get(ResourcesService.NAMESPACE_ADMISSION_METADATA);
    for(int i=0;i<2;i++)assertNotNull(ModelValidationGate.validatedPreparableAdmission(f.model,f.scope,null));
    var requests=ArgumentCaptor.forClass(SemanticValidationRequest.class);
    verify(f.reasoner,times(2)).validateDocument(requests.capture(),same(f.scope));
    assertTrue(requests.getAllValues().stream().allMatch(SemanticValidationRequest::isExecutableModels));
    verify(f.reasoner,never()).semanticDependencies(any(),same(f.scope));
    assertEquals(bytes,f.namespace.getMetadata().get(ResourcesService.NAMESPACE_ADMISSION_METADATA));
    assertFalse(source.isSemanticObligationsComplete());
    assertEquals(SemanticDependencyWitness.Status.UNKNOWN,source.getSemanticWitness().status());
  }

  @Test void catalogDiscoveryWithRequestedObservableStillValidatesTheSelectedModel() {
    var f=new ModelAdmissionReceiptTest();
    var syntax=new org.integratedmodelling.klab.api.lang.kim.impl.KimObservableImpl();syntax.setUrn("test:Quality");
    var concept=new org.integratedmodelling.klab.api.lang.kim.impl.KimConceptImpl();concept.setName("test:Quality");
    concept.setType(java.util.EnumSet.of(org.integratedmodelling.klab.api.knowledge.SemanticType.QUALITY));syntax.setSemantics(concept);
    ((org.integratedmodelling.klab.api.lang.kim.KimModel)f.namespace.getStatements().getFirst()).getObservables().add(syntax);
    when(f.reasoner.resolveObservable("test:Quality")).thenReturn(f.model.getObservables().getFirst());
    when(f.resources.declareObservable(anyString())).thenReturn(syntax);
    publish(f,true);
    assertEquals(3,ModelValidationGate.validatedPreparableCatalogRevision(f.model.getUrn(),f.model.getNamespace(),
        f.resources,f.scope,f.model.getObservables().getFirst()));
    var request=ArgumentCaptor.forClass(SemanticValidationRequest.class);verify(f.reasoner).validateDocument(request.capture(),same(f.scope));
    assertTrue(request.getValue().isExecutableModels());
  }

  @Test void incompleteExecutableEvidenceIsNeverPromotedBySourceAcceptance() {
    var f=new ModelAdmissionReceiptTest();publish(f,true);
    when(f.reasoner.validateDocument(any(),same(f.scope))).thenAnswer(call->{
      var request=call.getArgument(0,SemanticValidationRequest.class);
      assertTrue(request.isExecutableModels());
      var response=SemanticValidationResponse.forRequest(request);response.setStatus(SemanticValidationResponse.Status.COMPLETE);
      response.setKnowledgeRevision(3);response.setSemanticContentFingerprint(f.cap.getSemanticContentFingerprint());
      response.setSourceSemanticsComplete(true);response.setSemanticObligations(List.of("test:Quality"));
      response.setSemanticWitness(SemanticDependencyWitness.unknown(new SemanticDependencyRequest("reasoner",3,
          f.cap.getSemanticContentFingerprint(),response.getSemanticObligations()),"Unknown evaluator"));return response;
    });
    var receipt=ModelValidationGate.validatedPreparableAdmission(f.model,f.scope,null);assertNotNull(receipt);
    assertFalse(receipt.semanticObligationsComplete());assertFalse(receipt.semanticWitness().completeAt(System.currentTimeMillis()));
  }

  @Test void completeFlagWithUnknownExecutableWitnessStillNeedsExecutableValidation() {
    var f=new ModelAdmissionReceiptTest();var response=publish(f,true);response.setSemanticObligationsComplete(true);
    var key=ResourcesService.NAMESPACE_ADMISSION_METADATA;
    var previous=org.integratedmodelling.klab.utilities.Utils.Json.parseObject(f.namespace.getMetadata().get(key).toString(),ModelValidationGate.NamespaceAdmission.class);
    f.namespace.getMetadata().put(key,org.integratedmodelling.klab.utilities.Utils.Json.asString(new ModelValidationGate.NamespaceAdmission(
        previous.reasonerId(),org.integratedmodelling.klab.utilities.Utils.Json.asString(response),previous.modelHashes(),previous.semanticFingerprint(),previous.sourceContract())));
    assertNull(ModelValidationGate.publicationHash(f.model,response));
    assertNotNull(ModelValidationGate.validatedPreparableAdmission(f.model,f.scope,null));
    var request=ArgumentCaptor.forClass(SemanticValidationRequest.class);verify(f.reasoner).validateDocument(request.capture(),same(f.scope));
    assertTrue(request.getValue().isExecutableModels());
  }

  @Test void unsupportedStaticCoverageCannotReuseAnInjectedKnownSignatureProof() {
    var f=new ModelAdmissionReceiptTest();publish(f,false);
    assertNotNull(ModelValidationGate.validatedPreparableAdmission(f.model,f.scope,null));
    var requests=ArgumentCaptor.forClass(SemanticValidationRequest.class);
    verify(f.reasoner,times(2)).validateDocument(requests.capture(),same(f.scope));
    assertFalse(requests.getAllValues().getFirst().isExecutableModels());
    assertTrue(requests.getAllValues().getLast().isExecutableModels());
    verify(f.reasoner,never()).semanticDependencies(any(),same(f.scope));
  }

  @Test void changedSourceRecipeAndEligibilityRejectFrozenSourceAcceptance() {
    for(String change:List.of("source","version","recipe","inactive","deferred")) {
      var f=new ModelAdmissionReceiptTest();publish(f,true);
      switch(change) {
        case "source" -> f.namespace.setSourceCode("edited source");
        case "version" -> f.namespace.setLastUpdateTimestamp(18);
        case "recipe" -> when(f.cap.getSemanticContentFingerprint()).thenReturn("semantic-content-v2:"+"b".repeat(64));
        case "inactive" -> f.namespace.setInactive(true);
        case "deferred" -> f.namespace.getMetadata().put(ResourcesService.VALIDATION_DEFERRED_METADATA,true);
      }
      ModelValidationGate.validatedPreparableAdmission(f.model,f.scope,null);
      if(Set.of("inactive","deferred").contains(change))verify(f.reasoner,never()).validateDocument(any(),any());
      else {
        var requests=ArgumentCaptor.forClass(SemanticValidationRequest.class);
        verify(f.reasoner,atLeastOnce()).validateDocument(requests.capture(),same(f.scope));
        assertTrue(requests.getAllValues().stream().anyMatch(request->!request.isExecutableModels()),change);
      }
    }
  }

  @Test void deniedAuthorityAccessCannotReuseSavedSourceProof() {
    var f=new ModelAdmissionReceiptTest();publish(f,true);
    when(f.reasoner.semanticDependencies(any(),same(f.scope))).thenThrow(new SecurityException("revoked"));
    when(f.reasoner.validateDocument(any(),same(f.scope))).thenAnswer(call->{
      var response=SemanticValidationResponse.forRequest(call.getArgument(0));response.setStatus(SemanticValidationResponse.Status.UNAVAILABLE);return response;
    });
    assertNull(ModelValidationGate.validatedPreparableAdmission(f.model,f.scope,null));
    verify(f.reasoner,never()).semanticDependencies(any(),same(f.scope));
    verify(f.reasoner).validateDocument(any(),same(f.scope));
  }

  @Test void unrelatedMutableNamespaceProofDoesNotReplaceSelectedExecutableValidation() {
    var f=new ModelAdmissionReceiptTest();publish(f,true);
    when(f.reasoner.semanticDependencies(any(),same(f.scope))).thenAnswer(call->{
      var request=call.getArgument(0,SemanticDependencyRequest.class);
      return new SemanticDependencyWitness(SemanticDependencyWitness.VERSION,SemanticDependencyWitness.Status.REVALIDATION_REQUIRED,
          "reasoner",3,f.cap.getSemanticContentFingerprint(),request.inputHash(),List.of(
              new SemanticDependencyWitness.AuthorityUse("TAXA",SemanticDependencyWitness.Kind.UNVERSIONED,null,0)),"Unrelated namespace dependency");
    });
    assertNotNull(ModelValidationGate.validatedPreparableAdmission(f.model,f.scope,null));
    var requests=ArgumentCaptor.forClass(SemanticValidationRequest.class);
    verify(f.reasoner).validateDocument(requests.capture(),same(f.scope));assertTrue(requests.getValue().isExecutableModels());
    verify(f.reasoner,never()).semanticDependencies(any(),same(f.scope));
  }

  @Test void changedSelectedAuthorityProofComesFromCurrentExecutableValidation() {
    var f=new ModelAdmissionReceiptTest();publish(f,true);
    when(f.reasoner.validateDocument(any(),same(f.scope))).thenAnswer(call->{
      var request=call.getArgument(0,SemanticValidationRequest.class);assertTrue(request.isExecutableModels());
      var response=SemanticValidationResponse.forRequest(request);response.setStatus(SemanticValidationResponse.Status.COMPLETE);
      response.setKnowledgeRevision(3);response.setSemanticContentFingerprint(f.cap.getSemanticContentFingerprint());
      response.setSemanticObligations(List.of("test:Quality"));response.setSemanticObligationsComplete(true);
      var dependency=new SemanticDependencyRequest("reasoner",3,f.cap.getSemanticContentFingerprint(),response.getSemanticObligations());
      response.setSemanticWitness(new SemanticDependencyWitness(SemanticDependencyWitness.VERSION,SemanticDependencyWitness.Status.COMPLETE,
          "reasoner",3,f.cap.getSemanticContentFingerprint(),dependency.inputHash(),List.of(
              new SemanticDependencyWitness.AuthorityUse("TAXA",SemanticDependencyWitness.Kind.SNAPSHOT,"b".repeat(64),Long.MAX_VALUE)),null));
      return response;
    });
    var receipt=ModelValidationGate.validatedPreparableAdmission(f.model,f.scope,null);assertNotNull(receipt);
    assertEquals("b".repeat(64),receipt.semanticWitness().authorities().getFirst().proof());
    verify(f.reasoner).validateDocument(any(),same(f.scope));
  }
}
