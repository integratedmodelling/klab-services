package org.integratedmodelling.klab.services.runtime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.List;
import org.integratedmodelling.klab.api.services.runtime.Actuator;
import org.integratedmodelling.klab.configuration.ServiceConfiguration;
import org.integratedmodelling.klab.runtime.language.ModelValidationGate;
import org.integratedmodelling.klab.services.scopes.ServiceContextScope;
import org.junit.jupiter.api.*;

/** Named producer -> real planner -> transport -> local frozen-admission checks. */
class ProducingModelAdmissionTest {
  @BeforeAll static void configure(){ServiceConfiguration.injectInstantiators();}

  @Test void admittedProducerSurvivesSourceEditsAndRunsWithoutRemoteRevalidation() {
    var original=org.integratedmodelling.klab.services.resolver.CollectiveReductionPlanningTest.admittedNamedPlan();
    var plan=org.integratedmodelling.klab.utilities.Utils.Json.parseObject(
        org.integratedmodelling.klab.utilities.Utils.Json.asString(original.plan()),Actuator.class);
    var scope=mock(ServiceContextScope.class);
    var compiled=new CompiledDataflow(mock(RuntimeService.class),plan.getObservation(),scope);
    assertDoesNotThrow(()->compiled.validateProducingModels(plan,scope));
    ((org.integratedmodelling.klab.api.lang.kim.impl.KimNamespaceImpl)original.namespace())
        .setSourceCode("source changed after planning");
    ((org.integratedmodelling.klab.api.lang.kim.impl.KimNamespaceImpl)original.namespace())
        .setLastUpdateTimestamp(5L);
    assertDoesNotThrow(()->compiled.validateProducingModels(plan,scope));
    verify(scope,never()).getService(org.integratedmodelling.klab.api.services.Reasoner.class);
    verify(scope,never()).getService(org.integratedmodelling.klab.api.services.ResourcesService.class);
  }

  @Test void missingOrMutatedFrozenProducerIsRejectedLocally() {
    var original=org.integratedmodelling.klab.services.resolver.CollectiveReductionPlanningTest.admittedNamedPlan();
    var plan=original.plan();
    var scope=mock(ServiceContextScope.class);
    var compiled=new CompiledDataflow(mock(RuntimeService.class),plan.getObservation(),scope);
    var snapshots=plan.getData().remove(ModelValidationGate.PRODUCER_SNAPSHOTS);
    assertThrows(IllegalStateException.class,()->compiled.validateProducingModels(plan,scope));
    plan.getData().put(ModelValidationGate.PRODUCER_SNAPSHOTS,snapshots);
    plan.getData().put("modelAdmissionRevision",4L);
    assertThrows(IllegalStateException.class,()->compiled.validateProducingModels(plan,scope));
    plan.getData().put("modelAdmissionRevision",3L);
    @SuppressWarnings("unchecked") var encoded=(List<String>)snapshots;
    var frozen=org.integratedmodelling.klab.utilities.Utils.Json.parseObject(encoded.getFirst(),
        ModelValidationGate.ProducerSnapshot.class);
    var changed=new ModelValidationGate.ProducerSnapshot(frozen.modelUrn(),frozen.namespace(),"changed content");
    plan.getData().put(ModelValidationGate.PRODUCER_SNAPSHOTS,List.of(
        org.integratedmodelling.klab.utilities.Utils.Json.asString(changed)));
    assertThrows(IllegalStateException.class,()->compiled.validateProducingModels(plan,scope));
  }
}
