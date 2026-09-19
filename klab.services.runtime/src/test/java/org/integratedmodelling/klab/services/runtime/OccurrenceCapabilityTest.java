package org.integratedmodelling.klab.services.runtime;

import static org.junit.jupiter.api.Assertions.*;

import org.integratedmodelling.common.runtime.ActuatorImpl;
import org.integratedmodelling.klab.api.services.runtime.Actuator;
import org.junit.jupiter.api.Test;

class OccurrenceCapabilityTest {
  @Test
  void portableNegotiationAndReferencesAreRevalidatedBeforeActivation() {
    org.integratedmodelling.klab.configuration.ServiceConfiguration.injectInstantiators();
    var observation = new org.integratedmodelling.klab.api.knowledge.observation.impl.ObservationImpl();
    observation.setId(101);
    observation.setGeometry(org.integratedmodelling.klab.api.geometry.Geometry.create(
        "T0(1){tstart=1388534400000,tend=1420070400000,ttype=PHYSICAL}"));
    var time = org.integratedmodelling.common.knowledge.GeometryRepository.INSTANCE.scale(observation.getGeometry()).getTime();
    var daily = new org.integratedmodelling.klab.api.digitaltwin.OccurrenceSchedule(2, "", "", 1,
        org.integratedmodelling.klab.api.knowledge.observation.scale.time.Time.Resolution.Type.DAY,
        true, org.integratedmodelling.klab.api.digitaltwin.OccurrenceSchedule.Source.MODEL);
    var monthly = new org.integratedmodelling.klab.api.digitaltwin.OccurrenceSchedule(2, "", "", 1,
        org.integratedmodelling.klab.api.knowledge.observation.scale.time.Time.Resolution.Type.MONTH,
        true, org.integratedmodelling.klab.api.digitaltwin.OccurrenceSchedule.Source.DEPENDENCY);
    var accepted = org.integratedmodelling.klab.api.digitaltwin.OccurrenceNegotiation.select("m", null,
        java.util.List.of(new org.integratedmodelling.klab.api.digitaltwin.OccurrenceNegotiation.Declaration("m", daily)), time);
    var key = org.integratedmodelling.klab.api.digitaltwin.OccurrenceNegotiation.DATA_KEY;
    var process = new ActuatorImpl();
    process.setObservation(observation);
    process.setExecutionRole(Actuator.ExecutionRole.PROCESS);
    process.getComputation().add(new org.integratedmodelling.common.lang.ServiceCallImpl("test.process"));
    process.getOccurrenceSchedules().put(0, daily);
    process.getData().put(key, org.integratedmodelling.klab.utilities.Utils.Json.asString(accepted));
    assertDoesNotThrow(() -> CompiledDataflow.validateSupportedPlan(process));
    process.getOccurrenceSchedules().put(0, monthly);
    assertThrows(IllegalArgumentException.class, () -> CompiledDataflow.validateSupportedPlan(process));
    process.getOccurrenceSchedules().put(0, daily);
    var request = new org.integratedmodelling.klab.api.digitaltwin.OccurrenceNegotiation.Request("region", "erosion", "test:Erosion", monthly);
    var altered = org.integratedmodelling.klab.api.digitaltwin.OccurrenceNegotiation.select("m", request, accepted.declarations(), time);
    observation.getMetadata().put(key, org.integratedmodelling.klab.utilities.Utils.Json.asString(accepted));
    process.getData().put(key, org.integratedmodelling.klab.utilities.Utils.Json.asString(altered));
    process.getOccurrenceSchedules().put(0, monthly);
    assertThrows(IllegalArgumentException.class, () -> CompiledDataflow.validateSupportedPlan(process));
    var reference = new ActuatorImpl();
    reference.setObservation(observation);
    reference.getData().put(org.integratedmodelling.klab.api.digitaltwin.OccurrenceNegotiation.REQUEST_KEY,
        org.integratedmodelling.klab.utilities.Utils.Json.asString(request));
    assertThrows(org.integratedmodelling.klab.api.exceptions.KlabValidationException.class,
        () -> CompiledDataflow.validateSupportedPlan(reference));
    observation.getMetadata().remove(key);
    assertThrows(IllegalArgumentException.class, () -> CompiledDataflow.validateSupportedPlan(reference));
  }

  @Test
  void partialCoverageAndSemanticUpdatesAreRejectedInOccurrenceClosures() {
    org.integratedmodelling.klab.configuration.ServiceConfiguration.injectInstantiators();
    var observation = new org.integratedmodelling.klab.api.knowledge.observation.impl.ObservationImpl();
    observation.setId(100);
    observation.setGeometry(org.integratedmodelling.klab.api.geometry.Geometry.create(
        "T0(1){tstart=1388534400000,tend=1420070400000,ttype=PHYSICAL}"));
    var process = new ActuatorImpl();
    process.setObservation(observation);
    process.setExecutionRole(Actuator.ExecutionRole.PROCESS);
    process.getComputation().add(new org.integratedmodelling.common.lang.ServiceCallImpl("test.process"));
    process.getOccurrenceSchedules().put(0, new org.integratedmodelling.klab.api.digitaltwin.OccurrenceSchedule(
        1, "", "", 1, org.integratedmodelling.klab.api.knowledge.observation.scale.time.Time.Resolution.Type.MONTH,
        true, org.integratedmodelling.klab.api.digitaltwin.OccurrenceSchedule.Source.MODEL));
    process.setCoverage(org.integratedmodelling.klab.api.geometry.Geometry.create(
        "T0(1){tstart=1388534400000,tend=1391212800000,ttype=PHYSICAL}"));
    assertThrows(UnsupportedOperationException.class, () -> CompiledDataflow.validateSupportedPlan(process));
    process.setCoverage(null);
    assertDoesNotThrow(() -> CompiledDataflow.validateSupportedPlan(process));
    var update = new ActuatorImpl();
    update.setActuatorType(Actuator.Type.UPDATE);
    process.getChildren().add(update);
    assertThrows(UnsupportedOperationException.class, () -> CompiledDataflow.validateSupportedPlan(process));
  }

  @Test
  void incompleteTemporalChildIsRejectedBeforeAllocation() {
    var root = new ActuatorImpl();
    var process = new ActuatorImpl();
    process.setExecutionRole(Actuator.ExecutionRole.PROCESS);
    root.getChildren().add(process);
    var failure = assertThrows(IllegalArgumentException.class,
        () -> CompiledDataflow.validateSupportedPlan(root));
    assertTrue(failure.getMessage().contains("schedule"));
    process.setExecutionRole(Actuator.ExecutionRole.INITIALIZATION);
    assertDoesNotThrow(() -> CompiledDataflow.validateSupportedPlan(root));
  }
}
