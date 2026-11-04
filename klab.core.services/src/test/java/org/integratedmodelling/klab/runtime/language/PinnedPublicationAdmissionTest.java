package org.integratedmodelling.klab.runtime.language;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.*;
import org.integratedmodelling.klab.api.services.ResourcesService;
import org.integratedmodelling.klab.api.services.reasoner.objects.*;
import org.junit.jupiter.api.Test;

class PinnedPublicationAdmissionTest {
  @Test void newerSameContentPublicationIsReprovedByOldActorAndDoesNotRelabelItsActiveReceipt() {
    var f=new ModelAdmissionReceiptTest();when(f.cap.getSemanticSessionId()).thenReturn("owner");
    var response=ModelValidationGate.sourceAdmission(f.namespace,f.scope,3);response.setKnowledgeRevision(4);
    var request=new SemanticDependencyRequest("reasoner",4,ModelAdmissionReceiptTest.CONTENT,response.getSemanticObligations());
    response.setSemanticWitness(new SemanticDependencyWitness(SemanticDependencyWitness.VERSION,SemanticDependencyWitness.Status.COMPLETE,
        "reasoner",4,ModelAdmissionReceiptTest.CONTENT,request.inputHash(),List.of(),null));
    f.namespace.getMetadata().put(ResourcesService.NAMESPACE_ADMISSION_METADATA,
        org.integratedmodelling.klab.utilities.Utils.Json.asString(new ModelValidationGate.NamespaceAdmission("reasoner",
            org.integratedmodelling.klab.utilities.Utils.Json.asString(response),Map.of(f.model.getUrn(),ModelValidationGate.executableHash(f.model)),ModelAdmissionReceiptTest.CONTENT)));
    clearInvocations(f.reasoner);
    var receipt=ModelValidationGate.validatedPreparableAdmission(f.model,f.scope,null);
    assertNotNull(receipt);assertEquals(3,receipt.knowledgeRevision());assertEquals("owner",receipt.semanticSessionId());
    verify(f.reasoner).semanticDependencies(argThat(proof->proof.expectedKnowledgeRevision()==3),same(f.scope));
    verify(f.reasoner,never()).validateDocument(any(),any());
  }
  private static void bind(ModelAdmissionReceiptTest f) {
    when(f.scope.getResolutionCycleBinding()).thenReturn(new org.integratedmodelling.klab.api.services.runtime.objects.ResolutionCycleBinding(
        1,"cycle","context","runtime",true,new SemanticCycleBinding(SemanticCycleBinding.VERSION,"reasoner","session",3,ModelAdmissionReceiptTest.CONTENT,"lease")));
  }
  @Test void frozenCatalogPropagatesRetryableValidationAndUnexpectedFailureButKeepsKnownRejectionIneligible() {
    var f=new ModelAdmissionReceiptTest();bind(f);
    for(var status:List.of(SemanticValidationResponse.Status.UNAVAILABLE,SemanticValidationResponse.Status.STALE_KNOWLEDGE)) {
      var response=new SemanticValidationResponse();response.setStatus(status);
      var unavailable=new ModelValidationGate.AdmissionFailure("test",response,false);
      doThrow(unavailable).when(f.reasoner).validateDocument(any(),same(f.scope));
      assertSame(unavailable,assertThrows(ModelValidationGate.AdmissionFailure.class,
          ()->ModelValidationGate.validatedPreparableCatalogRevision("test:model","test",f.resources,f.scope,null)));
    }
    for(var status:List.of(SemanticValidationResponse.Status.COMPLETE,SemanticValidationResponse.Status.SYNTAX_ERRORS)) {
      var response=new SemanticValidationResponse();response.setStatus(status);
      response.getNotifications().add(org.integratedmodelling.klab.api.services.runtime.Notification.error("known invalid source"));
      doThrow(new ModelValidationGate.AdmissionFailure("test",response,false)).when(f.reasoner).validateDocument(any(),same(f.scope));
      assertEquals(-1,ModelValidationGate.validatedPreparableCatalogRevision("test:model","test",f.resources,f.scope,null));
    }
    var denied=new SecurityException("source unavailable");doThrow(denied).when(f.resources).retrieve("test",org.integratedmodelling.klab.api.lang.kim.KimNamespace.class,f.scope);
    assertSame(denied,assertThrows(SecurityException.class,()->ModelValidationGate.validatedPreparableCatalogRevision("test:model","test",f.resources,f.scope,null)));
    when(f.scope.getResolutionCycleBinding()).thenReturn(null);
    assertEquals(-1,ModelValidationGate.validatedPreparableCatalogRevision("test:model","test",f.resources,f.scope,null));
  }
  @Test void frozenMissingOrDeferredSourceIsUnavailableButInactiveOrKnownInvalidSourceIsIneligible() {
    var f=new ModelAdmissionReceiptTest();bind(f);
    f.namespace.getMetadata().put(ResourcesService.VALIDATION_DEFERRED_METADATA,true);
    assertThrows(ModelValidationGate.AdmissionFailure.class,()->ModelValidationGate.validatedPreparableCatalogRevision("test:model","test",f.resources,f.scope,null));
    f.namespace.getMetadata().clear();f.namespace.setInactive(true);
    assertEquals(-1,ModelValidationGate.validatedPreparableCatalogRevision("test:model","test",f.resources,f.scope,null));
    doReturn(null).when(f.resources).retrieve("test",org.integratedmodelling.klab.api.lang.kim.KimNamespace.class,f.scope);
    assertThrows(ModelValidationGate.AdmissionFailure.class,()->ModelValidationGate.validatedPreparableCatalogRevision("test:model","test",f.resources,f.scope,null));
    when(f.scope.getResolutionCycleBinding()).thenReturn(null);
    assertEquals(-1,ModelValidationGate.validatedPreparableCatalogRevision("test:model","test",f.resources,f.scope,null));
  }
}

