package org.integratedmodelling.common.services.client.digitaltwin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CyclicBarrier;
import org.integratedmodelling.common.services.client.RuntimeClient;
import org.integratedmodelling.klab.api.collections.Parameters;
import org.integratedmodelling.klab.api.collections.Triple;
import org.integratedmodelling.klab.api.data.KnowledgeGraph;
import org.integratedmodelling.klab.api.data.Metadata;
import org.integratedmodelling.klab.api.data.RuntimeAsset;
import org.integratedmodelling.klab.api.data.impl.LinkInfoImpl;
import org.integratedmodelling.klab.api.digitaltwin.GraphModel;
import org.integratedmodelling.klab.api.digitaltwin.impl.CommitImpl;
import org.integratedmodelling.klab.api.knowledge.observation.impl.ObservationImpl;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ClientKnowledgeGraphTest {

  private ContextScope scope;
  private RuntimeClient runtime;
  private ClientKnowledgeGraph graph;

  @BeforeEach
  void setUp() {
    scope = mock(ContextScope.class);
    runtime = mock(RuntimeClient.class);
    graph = new ClientKnowledgeGraph(scope, runtime);
  }

  @Test
  void walksAnExistingRemoteGraphBeyondTheFormerPreloadDepth() {
    var first = asset(101);
    var second = asset(102);
    stubLinks(RuntimeAsset.CONTEXT_ASSET, List.of(link(-1000, 101, GraphModel.Relationship.HAS_CHILD)));
    stubLinks(first, List.of(link(101, 102, GraphModel.Relationship.HAS_CHILD)));
    when(runtime.getAsset(101, RuntimeAsset.class, scope)).thenReturn(first);
    when(runtime.getAsset(102, RuntimeAsset.class, scope)).thenReturn(second);

    var roots = graph.getChildAssets(RuntimeAsset.CONTEXT_ASSET);
    var grandchildren = graph.getChildAssets(roots.getFirst());

    assertEquals(List.of(first), roots);
    assertEquals(List.of(second), grandchildren);
  }

  @Test
  void retriesAnAdjacencyAfterATransientRemoteFailure() {
    var child = asset(201);
    when(runtime.getLinkInfo(
            eq(RuntimeAsset.CONTEXT_ASSET),
            eq(GraphModel.Relationship.Direction.OUTGOING),
            eq(scope),
            any(GraphModel.Relationship[].class)))
        .thenReturn(null)
        .thenReturn(List.of(link(-1000, 201, GraphModel.Relationship.HAS_CHILD)));
    when(runtime.getAsset(201, RuntimeAsset.class, scope)).thenReturn(child);

    assertTrue(graph.getChildAssets(RuntimeAsset.CONTEXT_ASSET).isEmpty());
    assertEquals(List.of(child), graph.getChildAssets(RuntimeAsset.CONTEXT_ASSET));
    verify(runtime, times(2))
        .getLinkInfo(
            eq(RuntimeAsset.CONTEXT_ASSET),
            eq(GraphModel.Relationship.Direction.OUTGOING),
            eq(scope),
            any(GraphModel.Relationship[].class));
  }

  @Test
  void keepsBuiltInProvenanceAndDataflowBranchesWhenRemoteRootIsEmpty() {
    when(runtime.getLinkInfo(
            eq(RuntimeAsset.CONTEXT_ASSET),
            eq(GraphModel.Relationship.Direction.OUTGOING),
            eq(scope),
            any(GraphModel.Relationship[].class)))
        .thenReturn(List.of());

    var links =
        graph.getLinks(
            RuntimeAsset.CONTEXT_ASSET,
            GraphModel.Relationship.Direction.OUTGOING,
            scope,
            GraphModel.Relationship.HAS_PROVENANCE,
            GraphModel.Relationship.HAS_DATAFLOW);

    assertEquals(2, links.size());
    assertTrue(links.stream().anyMatch(link -> link.target() == RuntimeAsset.PROVENANCE_ASSET));
    assertTrue(links.stream().anyMatch(link -> link.target() == RuntimeAsset.DATAFLOW_ASSET));
  }

  @Test
  void honorsIncomingDirectionWhenFindingAParent() {
    var parent = asset(301);
    var child = asset(302);
    when(runtime.getLinkInfo(
            eq(child),
            eq(GraphModel.Relationship.Direction.INCOMING),
            eq(scope),
            any(GraphModel.Relationship[].class)))
        .thenReturn(List.of(link(301, 302, GraphModel.Relationship.HAS_CHILD)));
    when(runtime.getAsset(301, RuntimeAsset.class, scope)).thenReturn(parent);

    var links =
        graph.getLinks(
            child,
            GraphModel.Relationship.Direction.INCOMING,
            scope,
            GraphModel.Relationship.HAS_CHILD);

    assertEquals(1, links.size());
    assertSame(parent, links.iterator().next().source());
  }

  @Test
  void incomingWalkDoesNotRemoveAnOutgoingChildLink() {
    var parent = asset(311);
    var child = asset(312);
    when(runtime.getLinkInfo(
            eq(parent),
            eq(GraphModel.Relationship.Direction.OUTGOING),
            eq(scope),
            any(GraphModel.Relationship[].class)))
        .thenReturn(List.of(link(311, 312, GraphModel.Relationship.HAS_CHILD)));
    when(runtime.getLinkInfo(
            eq(child),
            eq(GraphModel.Relationship.Direction.INCOMING),
            eq(scope),
            any(GraphModel.Relationship[].class)))
        .thenReturn(List.of(link(311, 312, GraphModel.Relationship.HAS_CHILD)));
    when(runtime.getAsset(312, RuntimeAsset.class, scope)).thenReturn(child);

    assertEquals(List.of(child), graph.getChildAssets(parent));
    assertEquals(
        1,
        graph
            .getLinks(
                child,
                GraphModel.Relationship.Direction.INCOMING,
                scope,
                GraphModel.Relationship.HAS_CHILD)
            .size());
    // The IDE redraw walks outgoing and then incoming adjacency. The latter must not reverse and
    // remove the former from the client graph.
    assertEquals(List.of(child), graph.getChildAssets(parent));
  }

  @Test
  void appliesPeerCommitOnlyOnce() {
    var parent = observation(401, null);
    var child = observation(402, 1L);
    parent.setChildrenCount(0);
    when(runtime.getAsset(401, RuntimeAsset.class, scope)).thenReturn(parent);
    stubLinks(RuntimeAsset.CONTEXT_ASSET, List.of(link(-1000, 401, GraphModel.Relationship.HAS_CHILD)));
    graph.getChildAssets(RuntimeAsset.CONTEXT_ASSET);

    var commit = commit(1);
    commit.setOwner("another-client");
    commit.getAddedAssets().add(402L);
    commit.getAddedObservations().add(402L);
    commit.getAddedLinks().add(Triple.of(401L, 402L, GraphModel.Relationship.HAS_CHILD.name()));
    when(runtime.getCommit(1, scope)).thenReturn(commit);
    stubLinks(parent, List.of(link(401, 402, GraphModel.Relationship.HAS_CHILD)));

    graph.ingest(child);
    var duplicateChild = observation(402, 1L);
    graph.ingest(duplicateChild);

    assertEquals(1, parent.getChildrenCount());
    assertEquals(1, graph.getCommitQueue().size());
    assertEquals(List.of(child), graph.getChildAssets(parent));
    assertSame(child, graph.getAsset(402, scope, RuntimeAsset.class));
    assertSame(
        commit, duplicateChild.getMetadata().get(Metadata.IM_COMMIT, KnowledgeGraph.Commit.class));
    verify(runtime, times(1)).getCommit(1, scope);
  }

  @Test
  void serializesConcurrentHttpAndEventCommitIngestion() {
    var commit = commit(5);
    commit.getAddedAssets().add(602L);
    commit.getAddedObservations().add(602L);
    when(runtime.getCommit(5, scope)).thenReturn(commit);
    var httpResult = observation(602, 5L);
    var eventResult = observation(602, 5L);
    var barrier = new CyclicBarrier(2);

    var first = CompletableFuture.runAsync(() -> ingestAfterBarrier(httpResult, barrier));
    var second = CompletableFuture.runAsync(() -> ingestAfterBarrier(eventResult, barrier));
    CompletableFuture.allOf(first, second).join();

    verify(runtime, times(1)).getCommit(5, scope);
    assertSame(
        commit,
        httpResult.getMetadata().get(Metadata.IM_COMMIT, KnowledgeGraph.Commit.class));
    assertSame(
        commit,
        eventResult.getMetadata().get(Metadata.IM_COMMIT, KnowledgeGraph.Commit.class));
    assertEquals(1, graph.getCommitQueue().size());
  }

  private void ingestAfterBarrier(ObservationImpl observation, CyclicBarrier barrier) {
    try {
      barrier.await();
      graph.ingest(observation);
    } catch (Exception e) {
      throw new AssertionError(e);
    }
  }

  @Test
  void commitChildRemainsVisibleWhenRemoteAdjacencyIsEmpty() {
    var parent = observation(451, 3L);
    var child = observation(452, null);
    var commit = commit(3);
    commit.getAddedAssets().addAll(List.of(451L, 452L));
    commit.getAddedObservations().addAll(List.of(451L, 452L));
    commit.getAddedLinks().add(Triple.of(451L, 452L, GraphModel.Relationship.HAS_CHILD.name()));
    when(runtime.getCommit(3, scope)).thenReturn(commit);
    when(runtime.getAsset(452, RuntimeAsset.class, scope)).thenReturn(child);
    when(runtime.getLinkInfo(
            eq(parent),
            eq(GraphModel.Relationship.Direction.OUTGOING),
            eq(scope),
            any(GraphModel.Relationship[].class)))
        .thenReturn(List.of());

    graph.ingest(parent);

    assertEquals(List.of(child), graph.getChildAssets(parent));
  }

  @Test
  void removesDeletedLinksAndRefreshesModifiedAssets() {
    var oldParent = asset(501);
    var newParent = asset(501);
    var child = asset(502);
    stubLinks(RuntimeAsset.CONTEXT_ASSET, List.of(link(-1000, 501, GraphModel.Relationship.HAS_CHILD)));
    when(runtime.getLinkInfo(
            eq(oldParent),
            eq(GraphModel.Relationship.Direction.OUTGOING),
            eq(scope),
            any(GraphModel.Relationship[].class)))
        .thenReturn(List.of(link(501, 502, GraphModel.Relationship.HAS_CHILD)))
        .thenReturn(List.of());
    when(runtime.getAsset(501, RuntimeAsset.class, scope)).thenReturn(oldParent, newParent);
    when(runtime.getAsset(502, RuntimeAsset.class, scope)).thenReturn(child);

    graph.getChildAssets(RuntimeAsset.CONTEXT_ASSET);
    assertEquals(List.of(child), graph.getChildAssets(oldParent));

    var commit = commit(2);
    commit.getDeletedLinks().add(Triple.of(501L, 502L, GraphModel.Relationship.HAS_CHILD.name()));
    commit.getModifiedAssets().add(501L);
    when(runtime.getCommit(2, scope)).thenReturn(commit);
    graph.ingest(observation(900, 2L));

    assertTrue(graph.getChildAssets(oldParent).isEmpty());
    assertSame(newParent, graph.getAsset(501, scope, RuntimeAsset.class));
  }

  @Test
  void rejectsUnassignedAndQueryAssetsBeforeTheyReachTheCacheOrRuntime() {
    var unresolved = observation(-1, null);
    var query = observation(0, null);

    graph.ingest(unresolved);
    graph.ingest(query);

    assertNull(graph.getAsset(-1, scope, RuntimeAsset.class));
    assertNull(graph.getAsset(0, scope, RuntimeAsset.class));
    assertTrue(
        graph
            .getLinks(
                unresolved,
                GraphModel.Relationship.Direction.OUTGOING,
                scope,
                GraphModel.Relationship.HAS_CHILD)
            .isEmpty());
    assertTrue(
        graph
            .getLinks(
                query,
                GraphModel.Relationship.Direction.OUTGOING,
                scope,
                GraphModel.Relationship.HAS_CHILD)
            .isEmpty());
    verifyNoInteractions(runtime);
  }

  @Test
  void ignoresRemoteLinksWithUnassignedEndpoints() {
    stubLinks(
        RuntimeAsset.CONTEXT_ASSET,
        List.of(link(RuntimeAsset.CONTEXT_ASSET_ID, -1, GraphModel.Relationship.HAS_CHILD)));

    assertTrue(graph.getChildAssets(RuntimeAsset.CONTEXT_ASSET).isEmpty());
    verify(runtime, never()).getAsset(eq(-1L), any(), eq(scope));
  }

  @Test
  void rejectsAnEndpointWhosePayloadHasADifferentId() {
    stubLinks(
        RuntimeAsset.CONTEXT_ASSET,
        List.of(link(RuntimeAsset.CONTEXT_ASSET_ID, 701, GraphModel.Relationship.HAS_CHILD)));
    when(runtime.getAsset(701, RuntimeAsset.class, scope)).thenReturn(observation(-1, null));

    assertTrue(graph.getChildAssets(RuntimeAsset.CONTEXT_ASSET).isEmpty());
    assertNull(graph.getAsset(-1, scope, RuntimeAsset.class));
  }

  @Test
  void rejectsMalformedCommitSubsetsBeforeChangingTheGraph() {
    var commit = commit(4);
    commit.getAddedObservations().add(-1L);
    when(runtime.getCommit(4, scope)).thenReturn(commit);

    graph.ingest(observation(801, 4L));

    assertTrue(graph.getCommitQueue().isEmpty());
    assertNull(graph.getAsset(-1, scope, RuntimeAsset.class));
    verify(runtime, never()).getAsset(eq(-1L), any(), eq(scope));
  }

  private void stubLinks(RuntimeAsset source, Collection<KnowledgeGraph.LinkInfo> links) {
    when(runtime.getLinkInfo(
            eq(source),
            eq(GraphModel.Relationship.Direction.OUTGOING),
            eq(scope),
            any(GraphModel.Relationship[].class)))
        .thenReturn(links);
  }

  private static CommitImpl commit(long id) {
    var ret = new CommitImpl();
    ret.setId(id);
    return ret;
  }

  private static ObservationImpl observation(long id, Long commitId) {
    var ret = new ObservationImpl();
    ret.setId(id);
    ret.setUrn("test:" + id);
    if (commitId != null) {
      ret.getMetadata().put(Metadata.IM_COMMIT_ID, commitId);
    }
    return ret;
  }

  private static RuntimeAsset asset(long id) {
    return new TestAsset(id);
  }

  private static LinkInfoImpl link(
      long source, long target, GraphModel.Relationship relationship) {
    var ret = new LinkInfoImpl();
    ret.setSourceId(source);
    ret.setTargetId(target);
    ret.setType(relationship);
    ret.setProperties(Parameters.create());
    return ret;
  }

  private record TestAsset(long id) implements RuntimeAsset {

    @Override
    public long getId() {
      return id;
    }

    @Override
    public long getParentId() {
      return -1;
    }

    @Override
    public long getTransientId() {
      return id;
    }

    @Override
    public int getChildrenCount() {
      return 0;
    }

    @Override
    public long getParentTransientId() {
      return -1;
    }

    @Override
    public Type classify() {
      return Type.DATA;
    }
  }
}
