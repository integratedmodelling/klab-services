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
}
