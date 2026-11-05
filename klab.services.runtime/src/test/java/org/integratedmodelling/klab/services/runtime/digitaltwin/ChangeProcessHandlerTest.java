package org.integratedmodelling.klab.services.runtime.digitaltwin;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.integratedmodelling.klab.api.data.KnowledgeGraph;
import org.integratedmodelling.klab.api.data.impl.LinkImpl;
import org.integratedmodelling.klab.api.digitaltwin.Consequence;
import org.integratedmodelling.klab.api.digitaltwin.DigitalTwin;
import org.integratedmodelling.klab.api.digitaltwin.GraphModel;
import org.integratedmodelling.klab.api.digitaltwin.ProcessPlan;
import org.integratedmodelling.klab.api.digitaltwin.impl.CommitImpl;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.knowledge.Concept;
import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.api.knowledge.SemanticType;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.knowledge.observation.impl.ObservationImpl;
import org.integratedmodelling.klab.api.lang.UnarySemanticOperator;
import org.integratedmodelling.klab.api.services.resolver.ResolutionConstraint;
import org.integratedmodelling.klab.configuration.ServiceConfiguration;
import org.integratedmodelling.klab.services.scopes.ServiceContextScope;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ChangeProcessHandlerTest {
  static final Geometry PHYSICAL = Geometry.create("T0(1){ttype=PHYSICAL,tstart=1000,tend=4000}");
  static final Geometry GRID = Geometry.create("T1(3){ttype=GRID,tstart=1000,tend=4000,tgrid=1000,tscope=1,tunit=MILLISECOND}");
  @BeforeAll static void configure() { ServiceConfiguration.injectInstantiators(); }

  static ObservationImpl observation(long id, SemanticType type, Geometry geometry) {
    var semantics = mock(Concept.class);
    var observable = mock(Observable.class);
    when(observable.is(type)).thenReturn(true);
    when(observable.getSemantics()).thenReturn(semantics);
    when(observable.getUrn()).thenReturn("test:observable" + id);
    var observation = new ObservationImpl(); observation.setId(id); observation.setObservable(observable);
    observation.setGeometry(geometry); observation.setResolvedCoverage(1);
    return observation;
  }

  static class Fixture {
    final ServiceContextScope scope = mock(ServiceContextScope.class);
    final KnowledgeGraph graph = mock(KnowledgeGraph.class);
    final ObservationImpl bearer = observation(10, SemanticType.SUBJECT, PHYSICAL);
    final ObservationImpl quality = observation(11, SemanticType.QUALITY, PHYSICAL);
    final ObservationImpl other = observation(12, SemanticType.QUALITY, PHYSICAL);
    final ObservationImpl event = observation(20, SemanticType.EVENT, PHYSICAL);
    final ObservationImpl process = observation(30, SemanticType.PROCESS, PHYSICAL);
    final ObservationImpl resolved = observation(31, SemanticType.PROCESS, PHYSICAL);
    final List<Observation> observations = new ArrayList<>(List.of(bearer, quality, other));
    final CommitImpl commit = new CommitImpl();
    final Observation.Builder first;
    final Observation.Builder second;

    Fixture() {
      var twin = mock(DigitalTwin.class); when(twin.getKnowledgeGraph()).thenReturn(graph);
      when(scope.getDigitalTwin()).thenReturn(twin);
      var query = mock(KnowledgeGraph.Query.class); when(graph.query(Observation.class, scope)).thenReturn(query);
      when(query.run(scope)).thenAnswer(call -> List.copyOf(observations));
      when(scope.getParentOf(quality)).thenReturn(bearer);
      when(scope.getParentOf(other)).thenReturn(bearer);
      when(scope.getParentOf(process)).thenReturn(bearer);
      when(scope.getContextObservation()).thenReturn(bearer);
      when(scope.within(bearer)).thenReturn(scope);
      when(scope.withResolutionConstraints(any(ResolutionConstraint[].class))).thenReturn(scope);
      first = builder(quality); second = builder(other);
      commit.setId(1); commit.getAddedObservations().add(event.getId());
    }
    Observation.Builder builder(Observation quality) {
      var semanticBuilder = mock(Observable.Builder.class, RETURNS_SELF);
      when(quality.getObservable().builder(scope)).thenReturn(semanticBuilder);
      var change = mock(Observable.class);
      when(change.is(SemanticType.PROCESS)).thenReturn(true);
      when(change.is(SemanticType.CHANGE)).thenReturn(true);
      when(semanticBuilder.buildObservable()).thenReturn(change);
      var builder = mock(Observation.Builder.class, RETURNS_SELF);
      when(scope.observation(change)).thenReturn(builder);
      when(builder.build()).thenReturn(mock(Observation.class));
      when(process.getObservable().is(SemanticType.CHANGE)).thenReturn(true);
      when(resolved.getObservable().is(SemanticType.CHANGE)).thenReturn(true);
      resolved.getMetadata().put(org.integratedmodelling.common.runtime.ResolutionOutcomeReceipt.KEY, "RESOLVED");
      when(graph.isObservationCurrent(resolved, scope)).thenReturn(true);
      when(graph.getAsset(resolved.getId(), scope, Observation.class)).thenReturn(resolved);
      when(graph.isObservationCurrent(process, scope)).thenReturn(true);
      var reasoner = mock(org.integratedmodelling.klab.api.services.Reasoner.class);
      var capabilities = mock(org.integratedmodelling.klab.api.services.Reasoner.Capabilities.class);
      when(reasoner.capabilities(scope)).thenReturn(capabilities);
      when(reasoner.semanticDistance(any(Observable.class), any(Observable.class), isNull(), anyLong())).thenReturn(0);
      when(scope.getService(org.integratedmodelling.klab.api.services.Reasoner.class)).thenReturn(reasoner);
      when(builder.submit()).thenReturn(CompletableFuture.completedFuture(resolved));
      return builder;
    }
    ConsequenceEngine.Result evaluate() throws Exception { return new ChangeProcessHandler().evaluate(null, commit, scope); }
    KnowledgeGraph.Link writer(Observation quality) {
      var link = new LinkImpl(process, quality, GraphModel.Relationship.AFFECTS);
      link.properties().put(ProcessPlan.EDGE_ROLE, ProcessPlan.INFLUENCE);
      return link;
    }
  }

  @Test void cancelledConsequenceDoesNotStartGraphReadOrSubmission() {
    var f = new Fixture();
    when(f.scope.isInterrupted()).thenReturn(true);
    assertThrows(InterruptedException.class,
        () -> new ChangeProcessHandler().evaluate(null, f.commit, f.scope));
    verify(f.graph, never()).query(eq(Observation.class), any());
    verify(f.first, never()).submit();
    verify(f.second, never()).submit();
  }
  @Test void physicalTimeAloneDoesNotResolveChangeButGridTimeDoes() throws Exception {
    var f = new Fixture();
    assertEquals(Consequence.Outcome.NO_ACTION, f.evaluate().outcome());
    verify(f.first, never()).submit();
    f.bearer.setGeometry(GRID); f.resolved.setGeometry(GRID); f.commit.getAddedObservations().add(f.bearer.getId());
    assertEquals(Consequence.Outcome.APPLIED, f.evaluate().outcome());
    verify(f.first, times(2)).geometry(GRID); verify(f.second).submit();
    verify(f.quality.getObservable().builder(f.scope)).as(UnarySemanticOperator.CHANGE);
    verify(f.quality.getObservable().builder(f.scope)).optional(true);
    verify(f.scope, atLeastOnce()).withResolutionConstraints(argThat((ResolutionConstraint constraint) ->
        constraint.getType() == ResolutionConstraint.Type.ChangeQualities
            && constraint.payload(Observable.class).size() == 2));
  }

  @Test void eventOrProcessArrivalRevisitsOldQualitiesAndLaterNewQualities() throws Exception {
    for (var type : List.of(SemanticType.EVENT, SemanticType.PROCESS)) {
      var f = new Fixture(); f.observations.add(observation(20, type, PHYSICAL));
      assertEquals(List.of(11L, 12L), f.evaluate().observationIds());
      clearInvocations(f.first, f.second);
      f.commit.getAddedObservations().clear(); f.commit.getAddedObservations().add(12L);
      assertEquals(Consequence.Outcome.APPLIED, f.evaluate().outcome());
      verify(f.second).submit();
      verify(f.first, never()).submit();
    }
  }

  @Test void jointWriterCommittedForFirstQualityPreventsSecondAndRepeatSubmissions() throws Exception {
    var f = new Fixture(); f.observations.add(f.event);
    var installed = new java.util.concurrent.atomic.AtomicBoolean();
    when(f.graph.getLinks(any(Observation.class), eq(GraphModel.Relationship.Direction.INCOMING), eq(f.scope),
        eq(GraphModel.Relationship.AFFECTS))).thenAnswer(call -> installed.get() ? List.of(f.writer(call.getArgument(0))) : List.of());
    when(f.first.submit()).thenAnswer(call -> { installed.set(true); return CompletableFuture.completedFuture(f.resolved); });
    assertEquals(List.of(11L), f.evaluate().observationIds());
    verify(f.second, never()).submit();
    assertEquals(Consequence.Outcome.NO_ACTION, f.evaluate().outcome());
    verify(f.first, times(1)).submit();
  }

  @Test void partialCoverageWrongBearerAndDescriptiveEdgesDoNotSuppressResolution() throws Exception {
    for (int variant = 0; variant < 3; variant++) {
      var f = new Fixture(); f.observations.add(f.event);
      var link = f.writer(f.quality);
      if (variant == 0) f.process.setGeometry(Geometry.create("T0(1){ttype=PHYSICAL,tstart=1000,tend=2000}"));
      if (variant == 1) {
        var otherBearer = observation(99, SemanticType.SUBJECT, PHYSICAL);
        when(f.scope.getParentOf(f.process)).thenReturn(otherBearer);
      }
      if (variant == 2) link.properties().put(ProcessPlan.EDGE_ROLE, ProcessPlan.DESCRIPTIVE);
      when(f.graph.getLinks(f.quality, GraphModel.Relationship.Direction.INCOMING, f.scope,
          GraphModel.Relationship.AFFECTS)).thenReturn(List.of(link));
      f.evaluate(); verify(f.first).submit();
    }
  }

  @Test void unresolvedChangeIsDeferredAndAnExceptionalResolutionIsFailed() throws Exception {
    var f = new Fixture(); f.observations.add(f.event);
    when(f.first.submit()).thenReturn(CompletableFuture.completedFuture(Observation.empty()));
    assertEquals(Consequence.Outcome.DEFERRED, f.evaluate().outcome());
    when(f.first.submit()).thenReturn(CompletableFuture.failedFuture(new IllegalStateException("unavailable")));
    assertEquals(Consequence.Outcome.FAILED, f.evaluate().outcome());
  }

  @Test void retainedSemanticBuilderRequestsOptionalChangeForElevationAndSlope() throws Exception {
    var f = new Fixture(); f.bearer.setGeometry(GRID); f.resolved.setGeometry(GRID);
    f.commit.getAddedObservations().add(f.bearer.getId());
    var reasoner=f.scope.getService(org.integratedmodelling.klab.api.services.Reasoner.class);
    var built=new ArrayList<String>();
    when(reasoner.buildObservable(any(org.integratedmodelling.klab.api.knowledge.ObservableBuildStrategy.class),eq(f.scope)))
        .thenAnswer(call -> {
          var strategy=call.getArgument(0,org.integratedmodelling.klab.api.knowledge.ObservableBuildStrategy.class);
          assertTrue(strategy.getOperations().stream().anyMatch(o -> o.getOperator()==UnarySemanticOperator.CHANGE));
          assertTrue(strategy.getOperations().stream().anyMatch(o ->
              o.getType()==org.integratedmodelling.klab.api.knowledge.ObservableBuildStrategy.OperationType.AS_OPTIONAL
                  && Boolean.TRUE.equals(o.getPod())));
          built.add(strategy.getBaseObservable().getUrn());
          var meaning=new org.integratedmodelling.common.knowledge.ConceptImpl();
          meaning.setUrn("change in "+strategy.getBaseObservable().asConcept().getUrn());
          meaning.setType(java.util.EnumSet.of(SemanticType.PROCESS,SemanticType.CHANGE));
          return org.integratedmodelling.common.knowledge.ObservableImpl.promote(meaning,null);
        });
    for (var item:List.of(f.quality,f.other)) {
      var meaning=new org.integratedmodelling.common.knowledge.ConceptImpl();
      meaning.setUrn(item==f.quality?"geography:Elevation":"geography:Slope");
      meaning.setType(java.util.EnumSet.of(SemanticType.QUALITY));
      item.setObservable(org.integratedmodelling.common.knowledge.ObservableImpl.promote(meaning,null));
    }
    when(f.scope.observation(any(Observable.class))).thenAnswer(call -> {
      var requested=call.getArgument(0,Observable.class);
      assertTrue(requested.is(SemanticType.CHANGE));
      return requested.getUrn().contains("Elevation")?f.first:f.second;
    });
    assertEquals(List.of(11L,12L),f.evaluate().observationIds());
    assertEquals(List.of("geography:Elevation","geography:Slope"),built);
    clearInvocations(f.first,f.second);
    f.observations.add(f.process);f.commit.getAddedObservations().clear();f.commit.getAddedObservations().add(30L);
    assertEquals(Consequence.Outcome.NO_ACTION,f.evaluate().outcome());
    verify(f.first,never()).submit();verify(f.second,never()).submit();
  }

  @Test void processOnlyFollowUpDoesNotResubmitUnchangedQualities() throws Exception {
    var f = new Fixture(); f.bearer.setGeometry(GRID); f.resolved.setGeometry(GRID);
    f.commit.getAddedObservations().add(f.bearer.getId());
    f.evaluate();
    clearInvocations(f.first, f.second);
    f.observations.add(f.process);
    f.commit.getAddedObservations().clear(); f.commit.getAddedObservations().add(f.process.getId());
    assertEquals(Consequence.Outcome.NO_ACTION, f.evaluate().outcome());
    verify(f.first, never()).submit(); verify(f.second, never()).submit();
    f.commit.getModifiedAssets().add(f.quality.getId());
    f.evaluate(); verify(f.first).submit(); verify(f.second, never()).submit();
    clearInvocations(f.first, f.second);
    f.commit.getModifiedAssets().clear(); f.commit.getModifiedAssets().add(f.bearer.getId());
    f.evaluate(); verify(f.first).submit(); verify(f.second).submit();
  }

  @Test void firstChangeOccurrenceStillEnablesOldQualities() throws Exception {
    var f = new Fixture(); f.observations.add(f.process);
    f.commit.getAddedObservations().clear(); f.commit.getAddedObservations().add(f.process.getId());
    assertEquals(Consequence.Outcome.APPLIED, f.evaluate().outcome());
  }

  @Test void optionalChangeChildrenDoNotRetriggerEitherQualityThroughTheirBearerOrObserver() throws Exception {
    var f = new Fixture(); f.bearer.setGeometry(GRID);
    f.commit.getAddedObservations().add(f.bearer.getId());
    var absent = org.integratedmodelling.common.runtime.ResolutionOutcomeReceipt.terminal(Observation.empty(),
        org.integratedmodelling.klab.api.services.runtime.Dataflow.ResolutionOutcome.NO_MODEL);
    when(f.first.submit()).thenReturn(CompletableFuture.completedFuture(absent));
    when(f.second.submit()).thenReturn(CompletableFuture.completedFuture(absent));
    assertEquals(Consequence.Outcome.NO_ACTION, f.evaluate().outcome());
    verify(f.first).submit(); verify(f.second).submit();
    var observer = observation(40, SemanticType.AGENT, PHYSICAL);
    observer.setPerceivedGeometry(GRID);
    f.observations.add(observer);
    for (long id : List.of(30L, 32L)) {
      var child = observation(id, SemanticType.PROCESS, GRID);
      when(child.getObservable().is(SemanticType.CHANGE)).thenReturn(true);
      child.setResolvedCoverage(0);
      f.observations.add(child);
      f.commit.setId(id);
      f.commit.getAddedObservations().clear(); f.commit.getAddedObservations().add(id);
      f.commit.getModifiedAssets().addAll(List.of(f.bearer.getId(), observer.getId()));
      f.commit.getBookkeepingOnlyAssets().addAll(List.of(f.bearer.getId(), observer.getId()));
      assertEquals(Consequence.Outcome.NO_ACTION, f.evaluate().outcome());
    }
    verify(f.first, times(1)).submit(); verify(f.second, times(1)).submit();
    // A later actual quality update remains eligible: optional absence is not a negative cache.
    f.commit.getAddedObservations().clear();
    f.commit.getModifiedAssets().add(f.quality.getId());
    f.evaluate();
    verify(f.first, times(2)).submit(); verify(f.second, times(1)).submit();
  }

  @Test void unprovedChangeResultsNeverCountAsApplied() throws Exception {
    for (int variant = 0; variant < 8; variant++) {
      var f = new Fixture(); f.observations.add(f.event);
      switch (variant) {
        case 0 -> f.resolved.setResolvedCoverage(0);
        case 1 -> when(f.resolved.getObservable().is(SemanticType.CHANGE)).thenReturn(false);
        case 2 -> {
          f.resolved.getMetadata().remove(org.integratedmodelling.common.runtime.ResolutionOutcomeReceipt.KEY);
          when(f.graph.getAsset(f.resolved.getId(),f.scope,Observation.class)).thenReturn(null);
        }
        case 3 -> f.resolved.setGeometry(Geometry.create("T0(1){ttype=PHYSICAL,tstart=1000,tend=2000}"));
        case 4 -> when(f.graph.isObservationCurrent(f.resolved, f.scope)).thenReturn(false);
        case 5 -> f.resolved.getMetadata().put(org.integratedmodelling.common.runtime.ResolutionOutcomeReceipt.KEY, "DEFERRED");
        case 7 -> f.resolved.setResolvedCoverage(0.5);
        case 6 -> when(f.scope.getService(org.integratedmodelling.klab.api.services.Reasoner.class)
            .semanticDistance(any(Observable.class), any(Observable.class), isNull(), anyLong())).thenReturn(-1);
      }
      assertEquals(Consequence.Outcome.DEFERRED, f.evaluate().outcome(), "variant " + variant);
    }
  }

  @Test void typedOptionalAbsenceIsNonfatalAndUntypedEmptyIsDeferred() throws Exception {
    var f = new Fixture(); f.observations.add(f.event);
    var absent = org.integratedmodelling.common.runtime.ResolutionOutcomeReceipt.terminal(Observation.empty(),
        org.integratedmodelling.klab.api.services.runtime.Dataflow.ResolutionOutcome.NO_MODEL);
    when(f.first.submit()).thenReturn(CompletableFuture.completedFuture(absent));
    when(f.second.submit()).thenReturn(CompletableFuture.completedFuture(absent));
    assertEquals(Consequence.Outcome.NO_ACTION, f.evaluate().outcome());
    when(f.first.submit()).thenReturn(CompletableFuture.completedFuture(Observation.empty()));
    assertEquals(Consequence.Outcome.DEFERRED, f.evaluate().outcome());
  }

  @Test void completeReuseAndSelectedObserverDiscoveryRemainDistinct() throws Exception {
    for (int variant = 0; variant < 3; variant++) {
      var f = new Fixture(); f.observations.add(f.event);
      when(f.scope.getObservation(any(Observation.class))).thenReturn(f.resolved);
      if (variant > 0) { var observer=observation(40,SemanticType.AGENT,PHYSICAL);
        when(f.scope.getObserver()).thenReturn(observer); }
      if (variant == 2) when(f.resolved.getObservable().getObserverSemantics()).thenReturn(mock(Concept.class));
      assertEquals(variant == 1 ? Consequence.Outcome.APPLIED : Consequence.Outcome.NO_ACTION,
          f.evaluate().outcome());
      if (variant == 1) { verify(f.first).submit(); verify(f.second).submit(); }
      else { verify(f.first,never()).submit(); verify(f.second,never()).submit(); }
    }
  }

  @Test void untypedSuccessRequiresCompleteIndependentNativeEvidence() throws Exception {
    var f=new Fixture();f.observations.add(f.event);
    f.resolved.getMetadata().remove(org.integratedmodelling.common.runtime.ResolutionOutcomeReceipt.KEY);
    assertEquals(Consequence.Outcome.APPLIED,f.evaluate().outcome());
    var partialNative=observation(31,SemanticType.PROCESS,PHYSICAL);
    when(partialNative.getObservable().is(SemanticType.CHANGE)).thenReturn(true);
    partialNative.setResolvedCoverage(0.5);
    when(f.graph.getAsset(31L,f.scope,Observation.class)).thenReturn(partialNative);
    assertEquals(Consequence.Outcome.DEFERRED,f.evaluate().outcome());
  }

  private static Fixture runningWriterWithObserver() {
    var f = new Fixture(); f.observations.remove(f.other); f.observations.add(f.process);
    when(f.process.getObservable().getUrn()).thenReturn("earth:Erosion");
    when(f.quality.getObservable().getUrn()).thenReturn("geography:Elevation");
    var observer = observation(40, SemanticType.AGENT, PHYSICAL);
    when(f.scope.getObserver()).thenReturn(observer);
    f.commit.getAddedObservations().clear(); f.commit.getModifiedAssets().add(f.quality.getId());
    when(f.graph.getLinks(f.quality, GraphModel.Relationship.Direction.INCOMING, f.scope,
        GraphModel.Relationship.AFFECTS)).thenReturn(List.of(f.writer(f.quality)));
    return f;
  }

  @Test void acceptedAbsoluteWriterSuppliesChangeAcrossSimulationCommitsWithAndWithoutObserver() throws Exception {
    for (boolean selected : List.of(false, true)) {
      var f = runningWriterWithObserver();
      if (!selected) when(f.scope.getObserver()).thenReturn(null);
      f.commit.getAddedObservations().clear(); f.commit.getModifiedAssets().add(f.quality.getId());
      for (int step = 1; step <= 12; step++) {
        f.commit.setId(1000 + step);
        var cause = new Consequence.Cause(1, "context", f.commit.getId(), 2000 + step, selected ? 40 : 0,
            "worldview", null, "simulation-" + step, Consequence.Kind.CHANGE_PROCESS);
        assertEquals(Consequence.Outcome.NO_ACTION,
            new ChangeProcessHandler().evaluate(cause, f.commit, f.scope).outcome());
      }
      verify(f.first, never()).submit();
      verify(f.quality.getObservable(), never()).builder(f.scope);

      verify(f.scope, atLeastOnce()).getObserver();
    }
  }

  @Test void observerSelectionDoesNotHideMissingWriterSupportOrAnUnrelatedQuality() throws Exception {
    for (int variant = 0; variant < 4; variant++) {
      var f = runningWriterWithObserver();
      if (variant == 0) f.process.setGeometry(null);
      if (variant == 1) f.process.setGeometry(Geometry.create("T0(1){ttype=PHYSICAL,tstart=1000,tend=2000}"));
      if (variant == 2) f.process.setResolvedCoverage(0);
      if (variant == 3) when(f.graph.getLinks(f.quality, GraphModel.Relationship.Direction.INCOMING,
          f.scope, GraphModel.Relationship.AFFECTS)).thenReturn(List.of(f.writer(f.other)));
      assertEquals(Consequence.Outcome.APPLIED, f.evaluate().outcome());
      verify(f.first).submit();
    }
  }

  @Test void absoluteWriterDoesNotSatisfyAnExplicitQualityPerspective() throws Exception {
    var f = runningWriterWithObserver();
    when(f.quality.getObservable().getObserverSemantics()).thenReturn(mock(Concept.class));
    assertEquals(Consequence.Outcome.APPLIED, f.evaluate().outcome());
    verify(f.first).submit();
  }

  @Test void explicitWriterPerspectiveRetainsCurrentSemanticCompatibilityChecks() throws Exception {
    for (int variant = 0; variant < 3; variant++) {
      var f = runningWriterWithObserver();
      var provided = mock(Concept.class); var requested = mock(Concept.class);
      when(f.process.getObservable().getObserverSemantics()).thenReturn(provided);
      when(f.quality.getObservable().getObserverSemantics()).thenReturn(requested);
      var reasoner = mock(org.integratedmodelling.klab.api.services.Reasoner.class);
      var receipt = mock(org.integratedmodelling.klab.api.services.Reasoner.Capabilities.class);
      when(receipt.getKnowledgeRevision()).thenReturn(7L);
      when(f.scope.getService(org.integratedmodelling.klab.api.services.Reasoner.class)).thenReturn(reasoner);
      when(reasoner.capabilities(f.scope)).thenReturn(variant == 2 ? null : receipt);
      when(reasoner.semanticDistance(provided, requested, null, 7)).thenReturn(variant == 0 ? 0 : -1);
      assertEquals(variant == 0 ? Consequence.Outcome.NO_ACTION : variant == 2 ? Consequence.Outcome.DEFERRED : Consequence.Outcome.APPLIED,
          f.evaluate().outcome());
      if (variant == 0) verify(f.first, never()).submit(); else verify(f.first).submit();
    }
  }

  @Test void inaccessibleSelectedObserverStillFailsBeforeSuppressingResolution() {
    var f = runningWriterWithObserver();
    when(f.scope.getObserver()).thenThrow(new IllegalStateException("Selected observer inaccessible"));
    assertThrows(IllegalStateException.class, f::evaluate);
    verify(f.first, never()).submit();
  }

  @Test void explicitWriterForAbsoluteQualityMustMatchSelectedObserverMeaning() throws Exception {
    for (int variant = 0; variant < 3; variant++) {
      var f = runningWriterWithObserver();
      var provided = mock(Concept.class); var selectedMeaning = mock(Concept.class);
      when(f.process.getObservable().getObserverSemantics()).thenReturn(provided);
      when(f.scope.getObserver().getObservable().asConcept()).thenReturn(selectedMeaning);
      var reasoner = mock(org.integratedmodelling.klab.api.services.Reasoner.class);
      var receipt = mock(org.integratedmodelling.klab.api.services.Reasoner.Capabilities.class);
      when(receipt.getKnowledgeRevision()).thenReturn(7L);
      when(f.scope.getService(org.integratedmodelling.klab.api.services.Reasoner.class)).thenReturn(reasoner);
      when(reasoner.capabilities(f.scope)).thenReturn(variant == 2 ? null : receipt);
      when(reasoner.semanticDistance(provided, selectedMeaning, null, 7)).thenReturn(variant == 0 ? 0 : -1);
      assertEquals(variant == 0 ? Consequence.Outcome.NO_ACTION : variant == 2 ? Consequence.Outcome.DEFERRED : Consequence.Outcome.APPLIED,
          f.evaluate().outcome());
      if (variant == 0) verify(f.first, never()).submit(); else verify(f.first).submit();
    }
  }
}

