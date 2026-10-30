package org.integratedmodelling.common.services.client;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import org.integratedmodelling.common.data.jackson.JacksonConfiguration;
import org.integratedmodelling.common.knowledge.ConceptImpl;
import org.integratedmodelling.common.knowledge.ObservableImpl;
import org.integratedmodelling.common.utils.Utils;
import org.integratedmodelling.klab.api.data.KnowledgeGraph;
import org.integratedmodelling.klab.api.digitaltwin.DigitalTwin;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.knowledge.SemanticType;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.knowledge.observation.impl.ObservationImpl;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.services.resolver.ResolutionConstraint;
import org.integratedmodelling.klab.api.services.resolver.objects.ResolutionRequest;
import org.integratedmodelling.klab.api.knowledge.ObservationStrategy;
import org.integratedmodelling.klab.api.knowledge.IdentificationStrategy;
import org.integratedmodelling.klab.api.services.runtime.Dataflow;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class RelationshipScopeTransportTest {
  static ObservationImpl endpoint(long id) {
    var meaning = new ConceptImpl(); meaning.setUrn("test:Junction"); meaning.setName("Junction");
    meaning.setType(EnumSet.of(SemanticType.SUBJECT, SemanticType.COUNTABLE));
    var observable = new ObservableImpl(); observable.setSemantics(meaning); observable.setUrn(meaning.getUrn());
    var result = new ObservationImpl(); result.setId(id); result.setUrn("instance:" + id);
    result.setObservable(observable); result.setGeometry(Geometry.create("S2(1,1)")); return result;
  }

  static ContextScope scope(Observation source, Observation target) {
    var scope = mock(ContextScope.class);
    when(scope.getResolutionConstraints()).thenReturn(List.of());
    when(scope.getSourceObservation()).thenReturn(source); when(scope.getTargetObservation()).thenReturn(target);
    return scope;
  }

  static ResolutionConstraint from(Observation observation) {
    return ResolutionConstraint.of(ResolutionConstraint.Type.UnresolvedSourceObservation, observation);
  }
  static ResolutionConstraint to(Observation observation) {
    return ResolutionConstraint.of(ResolutionConstraint.Type.UnresolvedTargetObservation, observation);
  }

  @Test void pendingEndpointsRetainFullGeometryAcrossPortableRequestRoundTrip() throws Exception {
    var source = endpoint(-10); var target = endpoint(-12); var scope = scope(source, target);
    var constraints = ResolverClient.scopeForTransport(scope);
    assertEquals(2, constraints.size());
    var request = new ResolutionRequest(); request.setResolutionConstraints(constraints);
    var mapper = JacksonConfiguration.newObjectMapper();
    var restored = mapper.readValue(mapper.writeValueAsString(request), ResolutionRequest.class);
    var recipient = scope(null, null);
    var pair = ResolutionConstraint.unresolvedRelationshipScope(recipient,
        restored.getResolutionConstraints().toArray(ResolutionConstraint[]::new));
    assertEquals(-10, pair.source().getId()); assertEquals(-12, pair.target().getId());
    assertEquals(source.getGeometry().encode(), pair.source().getGeometry().encode());
    assertEquals(target.getObservable().getSemantics(), pair.target().getObservable().getSemantics());
    assertNotSame(source, pair.source());
    verify(scope, never()).getDigitalTwin();
  }

  @Test void committedHeaderScopesKeepTheirExistingPathWithoutSnapshots() {
    var scope = scope(endpoint(10), endpoint(12));
    assertTrue(ResolverClient.scopeForTransport(scope).isEmpty());
    assertNull(ResolutionConstraint.unresolvedRelationshipScope(scope));
    verify(scope, never()).getDigitalTwin();
  }

  @Test void positiveAssignedButUncommittedEndpointsAreTransportedWithoutCommit() {
    var source = endpoint(10); var target = endpoint(12); var scope = scope(source, target);
    var parent = mock(DigitalTwin.Transaction.class); var child = mock(DigitalTwin.Transaction.class);
    when(scope.getCurrentTransaction()).thenReturn(child); when(child.getParent()).thenReturn(parent);
    when(parent.assets()).thenReturn(List.of(source, target));
    when(parent.readObservation(source)).thenReturn(source); when(parent.readObservation(target)).thenReturn(target);
    var constraints = ResolverClient.scopeForTransport(scope);
    assertEquals(2, constraints.size());
    var recipient = scope(null, null); var twin = mock(DigitalTwin.class); var graph = mock(KnowledgeGraph.class);
    when(recipient.getDigitalTwin()).thenReturn(twin); when(twin.getKnowledgeGraph()).thenReturn(graph);
    var pair = ResolutionConstraint.unresolvedRelationshipScope(recipient, constraints.toArray(ResolutionConstraint[]::new));
    assertEquals(10, pair.source().getId()); assertEquals(12, pair.target().getId());
    assertNotSame(source, pair.source());
    verify(child, never()).commit(); verify(parent, never()).commit(); verify(scope, never()).getDigitalTwin();
  }

  @Test void committedCanonicalEndpointsWinOverSnapshotFields() {
    var source = endpoint(10); var target = endpoint(12); var recipient = scope(source, target);
    var twin = mock(DigitalTwin.class); var graph = mock(KnowledgeGraph.class);
    when(recipient.getDigitalTwin()).thenReturn(twin); when(twin.getKnowledgeGraph()).thenReturn(graph);
    when(graph.getAsset(10, recipient, Observation.class)).thenReturn(source);
    when(graph.getAsset(12, recipient, Observation.class)).thenReturn(target);
    var badSource = endpoint(10); badSource.setObservable(null); badSource.setGeometry(null);
    var badTarget = endpoint(12); badTarget.setGeometry(Geometry.UNIVERSAL);
    var pair = ResolutionConstraint.unresolvedRelationshipScope(recipient, from(badSource), to(badTarget));
    assertSame(source, pair.source()); assertSame(target, pair.target());
  }

  @Test void unpairedDuplicateCrossedAndSameEndpointSnapshotsAreRejected() {
    var source = endpoint(-10); var target = endpoint(-12); var recipient = scope(source, target);
    assertThrows(IllegalArgumentException.class, () -> ResolutionConstraint.unresolvedRelationshipScope(recipient, from(source)));
    assertThrows(IllegalArgumentException.class, () -> ResolutionConstraint.unresolvedRelationshipScope(recipient, from(source), from(source), to(target)));
    assertThrows(IllegalArgumentException.class, () -> ResolutionConstraint.unresolvedRelationshipScope(recipient, from(target), to(source)));
    assertThrows(IllegalArgumentException.class, () -> ResolutionConstraint.unresolvedRelationshipScope(recipient, from(source), to(source)));
    assertThrows(IllegalArgumentException.class, () -> ResolverClient.scopeForTransport(scope(source, null)));
  }

  @Test void collectiveQueryUnassignedAndIncompleteEndpointSnapshotsAreRejected() {
    for (long invalid : new long[] {Observation.UNASSIGNED_ID, Observation.QUERY_ID}) {
      var source = endpoint(invalid); var target = endpoint(-12); var recipient = scope(null, null);
      assertThrows(IllegalArgumentException.class, () -> ResolutionConstraint.unresolvedRelationshipScope(recipient, from(source), to(target)));
    }
    var source = endpoint(-10); var target = endpoint(-12); var recipient = scope(null, null);
    ((ConceptImpl)source.getObservable().getSemantics()).setCollective(true);
    assertThrows(IllegalArgumentException.class, () -> ResolutionConstraint.unresolvedRelationshipScope(recipient, from(source), to(target)));
    ((ConceptImpl)source.getObservable().getSemantics()).setCollective(false); source.setGeometry(null);
    assertThrows(IllegalArgumentException.class, () -> ResolutionConstraint.unresolvedRelationshipScope(recipient, from(source), to(target)));
    source.setGeometry(Geometry.create("S2(1,1)"));
    ((ConceptImpl)source.getObservable().getSemantics()).getType().add(SemanticType.ABSTRACT);
    assertThrows(IllegalArgumentException.class, () -> ResolutionConstraint.unresolvedRelationshipScope(recipient, from(source), to(target)));
  }

  @Test void resolverAndBothReasonerRequestPathsUseTheSameEndpointTransport() throws Exception {
    var scope = scope(endpoint(-10), endpoint(-12)); var observed = endpoint(-20);
    var http = mock(Utils.Http.Client.class, RETURNS_SELF);
    var resolver = mock(ResolverClient.class, CALLS_REAL_METHODS);
    var reasoner = mock(ReasonerClient.class, CALLS_REAL_METHODS);
    var field = BaseServiceClient.class.getDeclaredField("client"); field.setAccessible(true);
    field.set(resolver, http); field.set(reasoner, http);
    var async = ArgumentCaptor.forClass(ResolutionRequest.class);
    when(http.postAsync(anyString(), async.capture(), eq(Dataflow.class)))
        .thenReturn(CompletableFuture.completedFuture(null));
    resolver.resolve(observed, scope).join(); assertEquals(2, async.getValue().getResolutionConstraints().size());
    var strategies = ArgumentCaptor.forClass(ResolutionRequest.class);
    when(http.postCollection(anyString(), strategies.capture(), eq(ObservationStrategy.class))).thenReturn(List.of());
    reasoner.computeObservationStrategies(observed, scope);
    assertEquals(2, strategies.getValue().getResolutionConstraints().size());
    var identification = ArgumentCaptor.forClass(ResolutionRequest.class);
    when(http.post(anyString(), identification.capture(), eq(IdentificationStrategy.class))).thenReturn(null);
    reasoner.computeIdentificationStrategies(observed.getObservable(), scope);
    assertEquals(2, identification.getValue().getResolutionConstraints().size());
    for (var request : List.of(async.getValue(), strategies.getValue(), identification.getValue())) {
      var pair = ResolutionConstraint.unresolvedRelationshipScope(scope(null, null),
          request.getResolutionConstraints().toArray(ResolutionConstraint[]::new));
      assertEquals(-10, pair.source().getId()); assertNotNull(pair.source().getGeometry());
    }
  }
}
