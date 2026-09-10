package org.integratedmodelling.klab.runtime.libraries;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.List;
import org.integratedmodelling.common.services.client.runtime.KnowledgeGraphQuery;
import org.integratedmodelling.klab.api.collections.Constant;
import org.integratedmodelling.klab.api.data.*;
import org.integratedmodelling.klab.api.digitaltwin.*;
import org.integratedmodelling.klab.api.knowledge.Cohort;
import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.scope.Scope;
import org.integratedmodelling.klab.runtime.libraries.CoreActorLibrary.Context;
import org.junit.jupiter.api.Test;

class ContextActorQueryTest {
  @Test
  void defaultQueryUsesAuthorizedScopeAndFirstResult() {
    var f = new Fixture();
    var result = observation(5);
    f.results = List.of(result);
    assertSame(result, f.actor.query(null));
    verify(f.graph).query(Observation.class, f.context);
    assertSame(f.context, f.last.executionScope);
    assertEquals(1, f.last.getLimit());
    assertEquals(GraphModel.Fields.ID, f.last.getOrdering().getFirst().field());
    f.results = List.of();
    assertNull(f.actor.query(null));
    assertEquals(List.of(), f.actor.query(null, Metadata.create("all", true)));
  }

  @Test
  void withinAndIdRemainConjunctiveInsteadOfUsingTheIdShortcut() {
    var f = new Fixture();
    var parent = observation(3);
    var focused = f.focused(parent);
    f.actor.query(null, 12L, Metadata.create("within", parent));
    assertSame(focused, f.last.executionScope);
    assertEquals(3L, f.last.getSource().getId());
    assertEquals(-1, f.last.getId());
    assertEquals(new KnowledgeGraph.Query.Criterion("id", KnowledgeGraph.Query.Operator.EQUALS, 12L),
        f.last.getCriteria().getFirst());
  }

  @Test
  void namedUrnsArePassedUnparsedAndTypeConstantsIgnoreCase() {
    var f = new Fixture();
    f.actor.query(null, Constant.create("activity"), "named:asset", Metadata.create("all", true, "limit", 4, "offset", 2));
    verify(f.graph).query(org.integratedmodelling.klab.api.provenance.Activity.class, f.context);
    assertEquals("named:asset", f.last.getCriteria().getFirst().argument());
    assertEquals(4, f.last.getLimit());
    assertEquals(2, f.last.getOffset());
  }

  @Test
  void semanticsMatchCanonicalObservableIdentity() {
    var f = new Fixture();
    var semantics = mock(Observable.class);
    when(semantics.getUrn()).thenReturn("geography:Elevation in m");
    f.actor.query(null, semantics);
    assertEquals(new KnowledgeGraph.Query.Criterion(GraphModel.Fields.OBSERVABLE,
        KnowledgeGraph.Query.Operator.EQUALS, "geography:Elevation in m"), f.last.getCriteria().getFirst());
    assertThrows(IllegalArgumentException.class,
        () -> f.actor.query(null, RuntimeAsset.Type.ACTIVITY, semantics));
  }

  @Test
  void sourceAndTargetIndividuallyChooseTraversalDirection() {
    var f = new Fixture();
    var anchor = observation(7);
    f.actor.query(null, Metadata.create("source", anchor, "along", Constant.create("has_child"), "depth", 3));
    assertEquals(7L, f.last.getSource().getId());
    assertNull(f.last.getTarget());
    assertEquals(3, f.last.getDepth());
    f.actor.query(null, Metadata.create("target", anchor));
    assertEquals(7L, f.last.getTarget().getId());
    assertNull(f.last.getSource());
  }

  @Test
  void pairedEndpointsSelectDirectedLinksInTheDerivedScope() {
    var f = new Fixture();
    var source = observation(7);
    var target = observation(9);
    var between = mock(ContextScope.class);
    when(f.context.between(source, target)).thenReturn(between);
    when(between.getDigitalTwin()).thenReturn(f.twin);
    f.actor.query(null, Metadata.create("source", source, "target", target, "all", true));
    verify(f.graph).query(KnowledgeGraph.Link.class, between);
    assertEquals(7L, f.last.getRelationshipSource().getId());
    assertEquals(9L, f.last.getRelationshipTarget().getId());
    assertSame(between, f.last.executionScope);
    assertThrows(IllegalArgumentException.class,
        () -> f.actor.query(null, RuntimeAsset.Type.OBSERVATION, Metadata.create("source", source, "target", target)));
  }

  @Test
  void inheritedEndpointFocusAndPositionalRelationshipAreHonored() {
    var f = new Fixture();
    var source = observation(7);
    var target = observation(9);
    when(f.context.getSourceObservation()).thenReturn(source);
    when(f.context.getTargetObservation()).thenReturn(target);
    when(f.context.between(source, target)).thenReturn(f.context);
    f.actor.query(null, Constant.create("has_child"));
    verify(f.graph).query(KnowledgeGraph.Link.class, f.context);
    assertEquals(7L, f.last.getRelationshipSource().getId());
    assertEquals(9L, f.last.getRelationshipTarget().getId());
  }

  @Test
  void cohortMembersAreAnIterableSnapshotWithPaging() {
    var f = new Fixture();
    var cohort = mock(Cohort.class);
    when(cohort.getId()).thenReturn(20L);
    var member = observation(21);
    f.results = List.of(member);
    assertEquals(List.of(member), f.actor.members(null, cohort, Metadata.create("offset", 5, "limit", 3)));
    assertEquals(GraphModel.Relationship.HAS_MEMBER, f.last.getRelationship());
    assertEquals(20L, f.last.getSource().getId());
    assertEquals(5, f.last.getOffset());
    assertEquals(3, f.last.getLimit());
  }

  @Test
  void invalidOptionsFailInsteadOfBroadeningTheQuery() {
    var f = new Fixture();
    for (var options : List.of(Metadata.create("within", "wrong"), Metadata.create("all", "true"),
        Metadata.create("limit", -2), Metadata.create("offset", 1.5), Metadata.create("depth", 65),
        Metadata.create("unknown", true), Metadata.create("id", -1), Metadata.create("urn", ""),
        Metadata.create("id", 3, "urn", "x"), Metadata.create("source", "wrong"))) {
      assertThrows(IllegalArgumentException.class, () -> f.actor.query(null, options), options.toString());
    }
    var obs = observation(1);
    assertThrows(IllegalArgumentException.class,
        () -> f.actor.query(null, Metadata.create("within", obs, "source", obs)));
  }

  @Test
  void existingContextProxiesPreserveIdentityAndNeverCreateTwins() {
    var f = new Fixture();
    var runtimeScope = mock(org.integratedmodelling.klab.api.actors.RuntimeAgent.Scope.class);
    when(runtimeScope.getContext()).thenReturn(f.context);
    assertSame(f.context, Context.current(runtimeScope).scope(null));
    assertSame(f.context, Context.wrap(null, f.context).scope(null));
    var parent = observation(2);
    var focused = f.focused(parent);
    assertSame(focused, f.actor.focus(null, Metadata.create("within", parent)).scope(null));
    assertSame(f.context, f.actor.scope(null));
    verify(f.context, never()).createContext(any());
    assertThrows(RuntimeException.class, () -> new Context().query(null));
    assertThrows(RuntimeException.class, () -> Context.current(null));
  }

  @Test
  void timelineAndStorageDelegateWithoutSchedulingWork() {
    var f = new Fixture();
    var scheduler = mock(Scheduler.class);
    var manager = mock(StorageManager.class);
    var storage = mock(Storage.class);
    var obs = observation(7);
    when(f.twin.getScheduler()).thenReturn(scheduler);
    when(f.twin.getStorageManager()).thenReturn(manager);
    when(manager.getStorage(obs)).thenReturn(storage);
    assertSame(storage, f.actor.storage(null, obs));
    assertTrue(f.actor.timeline(null).containsKey("epochStart"));
    verify(scheduler, never()).switchToRealTime(anyLong());
    verify(scheduler, never()).submit(any(), any());
    f.actor.close(null);
    verify(f.context).close();
  }

  private static Observation observation(long id) {
    var observation = mock(Observation.class);
    when(observation.getId()).thenReturn(id);
    return observation;
  }

  private static class Fixture {
    final ContextScope context = mock(ContextScope.class);
    final DigitalTwin twin = mock(DigitalTwin.class);
    final KnowledgeGraph graph = mock(KnowledgeGraph.class);
    final Context actor = new Context(context);
    List<RuntimeAsset> results = List.of();
    RecordingQuery last;
    Fixture() {
      when(context.getDigitalTwin()).thenReturn(twin);
      when(twin.getKnowledgeGraph()).thenReturn(graph);
      when(graph.query(any(), any())).thenAnswer(call -> last = new RecordingQuery(results));
    }
    ContextScope focused(Observation parent) {
      var focused = mock(ContextScope.class);
      when(context.within(parent)).thenReturn(focused);
      when(focused.getDigitalTwin()).thenReturn(twin);
      when(focused.getContextObservation()).thenReturn(parent);
      return focused;
    }
  }

  private static class RecordingQuery extends KnowledgeGraphQuery<RuntimeAsset> {
    final List<RuntimeAsset> results;
    Scope executionScope;
    RecordingQuery(List<RuntimeAsset> results) { super(AssetType.ANY); this.results = results; }
    @Override public List<RuntimeAsset> run(Scope scope) { executionScope = scope; return results; }
  }
}
