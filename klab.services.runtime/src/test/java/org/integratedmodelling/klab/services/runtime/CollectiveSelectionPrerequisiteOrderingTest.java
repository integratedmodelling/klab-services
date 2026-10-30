package org.integratedmodelling.klab.services.runtime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.*;
import org.integratedmodelling.common.runtime.ActuatorImpl;
import org.integratedmodelling.common.runtime.DataflowImpl;
import org.integratedmodelling.klab.api.knowledge.*;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.services.runtime.*;
import org.integratedmodelling.klab.utilities.Utils;
import org.junit.jupiter.api.*;

/** An unresolved ordinary classifier prerequisite must be validated before selected discovery.
 * The intentionally invalid frozen source should fail admission, never reach a producer. */
class CollectiveSelectionPrerequisiteOrderingTest {
  @BeforeAll static void configure() { CollectiveCompletionQueryTest.configure(); }

  @Test void selectedNativeProducerCannotRunBeforeItsOrdinaryClassifierObligationIsValidated() throws Exception {
    var q = new CollectiveSelectionExecutionTest(); q.setup();
    var admission = CollectiveSelectionBinding.capture(q.request, q.f.scope, q.reasoner);
    assertNotNull(admission);
    var selected = new DataflowImpl();
    selected.setCollectiveSelection(new Dataflow.CollectiveSelection(q.qualified,
        q.f.producer.getObservable(), List.of(q.beech), List.of(q.species), List.of("species")));
    selected.getMetadata().put(CollectiveSelectionBinding.DATA_KEY, Utils.Json.asString(admission));
    var discovery = new ActuatorImpl(); discovery.setActuatorType(Actuator.Type.OBSERVE);
    discovery.setObservation(q.f.producer); discovery.setId(q.f.producer.getId());
    discovery.setCoverage(q.request.getGeometry()); discovery.setExecutionRole(Actuator.ExecutionRole.INITIALIZATION);
    discovery.getClassifierDependencies().add(new ClassifierDependencyPlan("test:native", null, 0,
        null, 0, "expanded", null, 0, null, q.request.getGeometry().encode(),
        "semantic-test", 7, List.of()));
    selected.getComputation().add(discovery);
    selected.setResolutionOutcome(Dataflow.ResolutionOutcome.DEFERRED);
    var operation = new ActuatorImpl(); operation.setActuatorType(Actuator.Type.UPDATE);
    operation.setName("species"); operation.setContextualization(Contextualization.CLASSIFICATION);
    operation.setOperationObservable(q.directive(q.species.getUrn())); selected.getComputation().add(operation);
    var port = new ActuatorImpl(); port.setActuatorType(Actuator.Type.REFERENCE);
    port.setName("selected"); port.setObservation(Observation.forTransport(q.request));
    port.setCollectiveSelectionDependency(selected);
    var parent = new DataflowImpl(); parent.getComputation().add(port);
    parent.setResolutionOutcome(Dataflow.ResolutionOutcome.DEFERRED);
    // Use the real compiler's first admission guard if execution is dispatched too soon.
    doAnswer(call -> {
      new CompiledDataflow(q.f.runtime, q.f.producer, q.f.scope, selected)
          .compile(call.getArgument(0));
      throw new AssertionError("An unresolved ordinary prerequisite was compiled");
    }).when(q.f.runtime).executeSelectionProducer(any(), any(), any());
    assertThrows(java.util.concurrent.CompletionException.class, () ->
        SelectedModelClassifierContinuation.resume(q.f.runtime, parent, q.f.scope, new ArrayList<>()).join());
    verify(q.f.runtime, never()).executeSelectionProducer(any(), any(), any());
  }
}
