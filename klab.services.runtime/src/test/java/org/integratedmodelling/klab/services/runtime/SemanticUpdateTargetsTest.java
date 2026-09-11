package org.integratedmodelling.klab.services.runtime;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import java.util.Map;
import org.integratedmodelling.common.runtime.ActuatorImpl;
import org.integratedmodelling.klab.api.knowledge.observation.impl.ObservationImpl;
import org.integratedmodelling.klab.api.services.runtime.Actuator;
import org.junit.jupiter.api.Test;

class SemanticUpdateTargetsTest {
  @Test void registrationRejectsDirectiveBeforeAccessingTheGraph() {
    var observable = new org.integratedmodelling.common.knowledge.ObservableImpl();
    observable.setDescriptionType(org.integratedmodelling.klab.api.knowledge.Contextualization.CLASSIFICATION);
    var directive = new ObservationImpl(); directive.setObservable(observable);
    var runtime = org.mockito.Mockito.mock(RuntimeService.class, org.mockito.Mockito.CALLS_REAL_METHODS);
    assertThrows(IllegalArgumentException.class, () -> runtime.register(directive, null));
  }
  @Test void deduplicatesDurableAndTransactionMembersAcrossProducers() {
    var durable = new ObservationImpl(); durable.setId(42);
    var copy = new ObservationImpl(); copy.setId(42);
    var provisional = new ObservationImpl(); provisional.setTransientId(42);
    var provisionalCopy = new ObservationImpl(); provisionalCopy.setTransientId(42);
    var binding = new ActuatorImpl.TargetBindingImpl();
    binding.setKind(Actuator.TargetBinding.Kind.COHORT_MEMBERS);
    binding.setSources(List.of("cached", "new"));
    assertEquals(List.of(durable, provisional), SemanticUpdateTargets.bind(binding,
        Map.of("cached", List.of(durable, provisional), "new", List.of(copy, provisionalCopy))));
  }
  @Test void completedEmptyCohortIsDifferentFromMissingProducer() {
    var binding = new ActuatorImpl.TargetBindingImpl();
    binding.setKind(Actuator.TargetBinding.Kind.COHORT_MEMBERS);
    binding.setSources(List.of("members"));
    assertTrue(SemanticUpdateTargets.bind(binding, Map.of("members", List.of())).isEmpty());
    assertThrows(IllegalStateException.class, () -> SemanticUpdateTargets.bind(binding, Map.of()));
  }
  @Test void preflightRejectsNestedUpdateBeforeAllocatingAnything() {
    var root = new ActuatorImpl(); root.setActuatorType(Actuator.Type.OBSERVE);
    var update = new ActuatorImpl(); update.setActuatorType(Actuator.Type.UPDATE);
    root.getChildren().add(update);
    assertThrows(UnsupportedOperationException.class, () -> CompiledDataflow.validateSupportedPlan(root));
    root.getChildren().clear();
    assertDoesNotThrow(() -> CompiledDataflow.validateSupportedPlan(root));
  }
}
