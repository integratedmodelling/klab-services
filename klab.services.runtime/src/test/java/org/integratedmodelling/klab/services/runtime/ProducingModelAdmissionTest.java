package org.integratedmodelling.klab.services.runtime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.*;
import org.integratedmodelling.klab.api.knowledge.Model;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.lang.kim.*;
import org.integratedmodelling.klab.api.lang.kim.impl.*;
import org.integratedmodelling.klab.api.services.*;
import org.integratedmodelling.klab.api.services.runtime.Actuator;
import org.integratedmodelling.klab.api.services.reasoner.objects.SemanticValidationResponse;
import org.integratedmodelling.klab.configuration.ServiceConfiguration;
import org.integratedmodelling.klab.services.scopes.ServiceContextScope;
import org.junit.jupiter.api.*;

/** Named Model -> real compiler -> production transport -> actual source admission gate. */
class ProducingModelAdmissionTest {
  @BeforeAll static void configure(){ServiceConfiguration.injectInstantiators();}
  @Test void ordinaryNamedProducerSurvivesTransportAndIsRevalidatedBeforeUse() {
    var original=org.integratedmodelling.klab.services.resolver.CollectiveReductionPlanningTest.admittedNamedPlan();
    var plan=original.plan();
    var transported=org.integratedmodelling.klab.utilities.Utils.Json.parseObject(org.integratedmodelling.klab.utilities.Utils.Json.asString(plan),Actuator.class);
    assertEquals(3L,((Number)transported.getData().get("modelAdmissionRevision")).longValue());
    assertEquals("reasoner",transported.getData().get("modelAdmissionReasonerId"));
    var scope=mock(ServiceContextScope.class);var runtime=mock(RuntimeService.class);var reasoner=mock(Reasoner.class);var cap=mock(Reasoner.Capabilities.class);
    when(scope.getService(Reasoner.class)).thenReturn(reasoner);when(reasoner.capabilities(scope)).thenReturn(cap);
    when(cap.getServiceId()).thenReturn("reasoner");when(cap.getKnowledgeRevision()).thenReturn(3L);
    var resources=mock(ResourcesService.class);when(scope.getService(ResourcesService.class)).thenReturn(resources);
    var namespace=original.namespace();
    when(resources.retrieve("test",KimNamespace.class,scope)).thenReturn(namespace);
    when(resources.declareObservable(anyString())).thenReturn(new KimObservableImpl());
    when(reasoner.validateDocument(any(),eq(scope))).thenAnswer(call->{var response=SemanticValidationResponse.forRequest(call.getArgument(0));response.setStatus(SemanticValidationResponse.Status.COMPLETE);response.setKnowledgeRevision(3);return response;});
    var source=original.model();
    when(runtime.observerSourceModel("test:instantiateTrees",scope)).thenReturn(source);
    var compiled=new CompiledDataflow(runtime,plan.getObservation(),scope);
    assertDoesNotThrow(()->compiled.validateProducingModels(transported,scope));
    verify(reasoner,atLeastOnce()).validateDocument(any(),eq(scope));
    ((KimNamespaceImpl)namespace).setSourceCode("changed source at SAME semantic revision");
    assertThrows(IllegalStateException.class,()->compiled.validateProducingModels(transported,scope));
    ((KimNamespaceImpl)namespace).setSourceCode("fixture source");
    ((KimNamespaceImpl)namespace).setLastUpdateTimestamp(5L);
    assertThrows(IllegalStateException.class,()->compiled.validateProducingModels(transported,scope));
    ((KimNamespaceImpl)namespace).setLastUpdateTimestamp(0L);
    var changed=new org.integratedmodelling.common.lang.ContextualizableImpl();changed.setLiteral(2.0);
    var originalCode=source.getComputation();when(source.getComputation()).thenReturn(List.of(changed));
    assertThrows(IllegalStateException.class,()->compiled.validateProducingModels(transported,scope));
    when(source.getComputation()).thenReturn(originalCode);
    when(cap.getKnowledgeRevision()).thenReturn(4L);
    assertThrows(IllegalStateException.class,()->compiled.validateProducingModels(transported,scope));
    when(cap.getKnowledgeRevision()).thenReturn(3L);when(cap.getServiceId()).thenReturn("replacement");
    assertThrows(IllegalStateException.class,()->compiled.validateProducingModels(transported,scope));
    when(cap.getServiceId()).thenReturn("reasoner");when(runtime.observerSourceModel("test:instantiateTrees",scope)).thenReturn(null);
    assertThrows(IllegalStateException.class,()->compiled.validateProducingModels(transported,scope));
  }
}
