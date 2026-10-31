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
      when(semanticBuilder.buildObservable()).thenReturn(change);
      var builder = mock(Observation.Builder.class, RETURNS_SELF);
      when(scope.observation(change)).thenReturn(builder);
      when(builder.build()).thenReturn(mock(Observation.class));
      when(builder.submit()).thenReturn(CompletableFuture.completedFuture(process));
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
    f.bearer.setGeometry(GRID);
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
    }
  }

  @Test void jointWriterCommittedForFirstQualityPreventsSecondAndRepeatSubmissions() throws Exception {
    var f = new Fixture(); f.observations.add(f.event);
    var installed = new java.util.concurrent.atomic.AtomicBoolean();
    when(f.graph.getLinks(any(Observation.class), eq(GraphModel.Relationship.Direction.INCOMING), eq(f.scope),
        eq(GraphModel.Relationship.AFFECTS))).thenAnswer(call -> installed.get() ? List.of(f.writer(call.getArgument(0))) : List.of());
    when(f.first.submit()).thenAnswer(call -> { installed.set(true); return CompletableFuture.completedFuture(f.process); });
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

  private static Fixture runningWriterWithObserver() {
    var f = new Fixture(); f.observations.remove(f.other); f.observations.add(f.process);
    when(f.process.getObservable().getUrn()).thenReturn("earth:Erosion");
    when(f.quality.getObservable().getUrn()).thenReturn("geography:Elevation");
    var observer = observation(40, SemanticType.AGENT, PHYSICAL);
    when(f.scope.getObserver()).thenReturn(observer);
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
      verify(f.scope, never()).getService(org.integratedmodelling.klab.api.services.Reasoner.class);
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
      assertEquals(variant == 0 ? Consequence.Outcome.NO_ACTION : Consequence.Outcome.APPLIED,
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
      assertEquals(variant == 0 ? Consequence.Outcome.NO_ACTION : Consequence.Outcome.APPLIED,
          f.evaluate().outcome());
      if (variant == 0) verify(f.first, never()).submit(); else verify(f.first).submit();
    }
  }
}

