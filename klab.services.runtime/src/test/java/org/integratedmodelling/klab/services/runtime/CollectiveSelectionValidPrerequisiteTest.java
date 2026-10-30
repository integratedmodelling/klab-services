package org.integratedmodelling.klab.services.runtime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.integratedmodelling.common.runtime.*;
import org.integratedmodelling.klab.api.data.Metadata;
import org.integratedmodelling.klab.api.knowledge.*;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.knowledge.observation.impl.ObservationImpl;
import org.integratedmodelling.klab.api.provenance.Activity;
import org.integratedmodelling.klab.api.services.runtime.*;
import org.integratedmodelling.klab.utilities.Utils;
import org.junit.jupiter.api.*;

/** Genuine frozen abstract-quality expansion composed inside a selected plan. Native completion
 * is an explicit fixture boundary here; native discovery/provenance are tested separately. */
class CollectiveSelectionValidPrerequisiteTest {
  @BeforeAll static void configure() { SelectedModelClassifierSubmissionTest.configure(); }

  @Test void validOrdinaryClassifierInputExpandsBeforeSelectedPlanExecution() throws Exception {
    try (var f = new SelectedModelClassifierSubmissionTest.Fixture(); var permit=f.useGatedEmptyProducer()) {
      var execution = f.scope.executing(Activity.of(Activity.Type.SUBMISSION, null, "Nested selection prerequisites"));
      var target = new ObservationImpl(); target.setObservable(f.reasoner.declareObservable(f.output)); target.setGeometry(f.geometry);
      var ordinary = f.resolver.resolve(f.runtime.register(target, execution), execution).get(30, TimeUnit.SECONDS);
      assertEquals(Dataflow.ResolutionOutcome.DEFERRED, ordinary.getResolutionOutcome());
      var ordinaryRoot = ordinary.getComputation().getFirst();
      assertFalse(ordinaryRoot.getClassifierDependencies().isEmpty());
      var witness = ordinaryRoot.getClassifierDependencies().getFirst().contextWitness();
      assertNotNull(witness, "Full Resolver lowering must transport the supported observed model context");
      assertEquals(f.region.getId(), witness.id());
      assertEquals(0, witness.transientId());
      assertEquals(f.region.getObservable().getSemantics().getUrn(), witness.semantics());
      assertEquals(execution.getId(), witness.scopeId());
      var syntax = f.semantic.named("Region"); syntax.getTraits().add(f.semantic.named("Concrete"));
      syntax.setCollective(true); f.semantic.register(syntax);
      var requestedObservable = f.reasoner.declareObservable(f.observable(syntax));
      var requested = new ObservationImpl(); requested.setId(Observation.QUERY_ID);
      requested.setObservable(requestedObservable); requested.setGeometry(f.geometry);
      var admission = CollectiveSelectionBinding.capture(requested, execution, f.reasoner);
      assertNotNull(admission);
      var nativeObservable = f.reasoner.resolveObservable(admission.nativeMembers());
      assertNotNull(nativeObservable);
      var nativeView = new ObservationImpl(); nativeView.setId(Observation.QUERY_ID);
      nativeView.setObservable(nativeObservable); nativeView.setGeometry(f.geometry); nativeView.setResolvedCoverage(1); nativeView.setChildrenCount(1);
      nativeView.getMetadata().put(Metadata.IM_QUERY_SOURCE_IDS, List.of(f.region.getId()));
      nativeView.getMetadata().put(Metadata.IM_QUERY_COHORT_ID, f.cohort.getId());
      // This test deliberately stubs native completion. Supply an explicit controlled proof
      // at that boundary, rather than treating missing metadata as authority independence.
      var nativeEvidence=new SemanticExecutionEvidence.Collector();nativeEvidence.scope(execution);
      nativeEvidence.observation(nativeView);nativeEvidence.observation(f.region);
      var proof=nativeEvidence.finish(execution);
      assertTrue(SemanticExecutionEvidence.verified(proof,execution),()->Utils.Json.asString(proof));
      nativeView.getMetadata().put(SemanticExecutionEvidence.DATA_KEY,Utils.Json.asString(proof));
      SemanticExecutionEvidence.issue(nativeView,proof,execution);
      doNothing().when(f.runtime).captureSelectionBaseline(any(), any());
      doReturn(nativeView).when(f.runtime).queryCollective(any(), any(), any(), any(), any());
      doReturn(List.of(f.region)).when(f.runtime).classificationMembers(any(), any(), any());
      doReturn(List.of(f.region)).when(f.runtime).classificationMembersForCheck(any(), any(), any());
      var nativeSource = new ObservationImpl(); nativeSource.setId(f.region.getId());
      nativeSource.setObservable(nativeObservable); nativeSource.setGeometry(f.geometry);
      var reference = new ActuatorImpl(); reference.setActuatorType(Actuator.Type.REFERENCE);
      reference.setObservation(nativeView); reference.setId(Observation.QUERY_ID); reference.setCoverage(f.geometry);
      var operation = new ActuatorImpl(); operation.setName("family"); operation.setActuatorType(Actuator.Type.UPDATE);
      operation.setContextualization(Contextualization.CLASSIFICATION); operation.setEffect(Actuator.Effect.SEMANTIC_UPDATE);
      operation.getData().put(Actuator.CLASSIFICATION_CHECK_ONLY,true); // This fixture starts with a known Concrete family.
      operation.setOperationObservable(f.reasoner.resolveObservable(admission.directives().getFirst()));
      operation.getChildren().add(ordinaryRoot);
      var selected = new DataflowImpl(); selected.setResolutionOutcome(Dataflow.ResolutionOutcome.DEFERRED);
      selected.setCollectiveSelection(new Dataflow.CollectiveSelection(requestedObservable, nativeObservable,
          admission.predicates().stream().map(f.reasoner::resolveConcept).toList(),
          admission.families().stream().map(f.reasoner::resolveConcept).toList(), List.of("family")));
      selected.getMetadata().put(CollectiveSelectionBinding.DATA_KEY, Utils.Json.asString(admission));
      selected.getComputation().add(reference); selected.getComputation().add(operation);
      var port = new ActuatorImpl(); port.setActuatorType(Actuator.Type.REFERENCE); port.setName("selected");
      port.setObservation(requested); port.setId(Observation.QUERY_ID); port.setCollectiveSelectionDependency(selected);
      var incoming = new DataflowImpl(); incoming.setResolutionOutcome(Dataflow.ResolutionOutcome.DEFERRED); incoming.getComputation().add(port);
      var checks = new ArrayList<Runnable>();
      var resolved = SelectedModelClassifierContinuation.resume(f.runtime, incoming, execution, checks).get(30, TimeUnit.SECONDS);
      assertNull(resolved.getComputation().getFirst().getCollectiveSelectionDependency());
      assertEquals(1, resolved.getComputation().getFirst().getObservation().getChildrenCount());
      assertTrue(f.gatedDiscoveries() > 0, "The ordinary expansion's actual gated producer must execute first");
      verify(f.runtime, atLeastOnce()).executeClassifierProducer(any(), any(), any());
      assertEquals(0, f.rootCommits.get(), "The continuation does not commit an independent submission");
      checks.forEach(Runnable::run);
      var previousGeometry = f.region.getGeometry();
      f.region.setGeometry(org.integratedmodelling.klab.api.geometry.Geometry.create(
          "T1(1){ttype=PHYSICAL,tstart=1000,tend=5000,tscope=4.0,tunit=SECOND}"));
      assertThrows(IllegalStateException.class, () -> checks.forEach(Runnable::run),
          "The captured context must be revalidated after prerequisite work and before root commit");
      f.region.setGeometry(previousGeometry);
      checks.forEach(Runnable::run);
      execution.fail(new IllegalStateException("Test attempt cleanup"));
      assertEquals(-1, execution.commit());
    }
  }
}
