package org.integratedmodelling.klab.runtime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.net.URI;
import java.util.*;
import org.integratedmodelling.common.knowledge.ConceptImpl;
import org.integratedmodelling.common.knowledge.ObservableImpl;
import org.integratedmodelling.common.services.client.ResolverClient;
import org.integratedmodelling.common.services.client.digitaltwin.ClientDigitalTwin;
import org.integratedmodelling.common.services.client.digitaltwin.ClientKnowledgeGraph;
import org.integratedmodelling.common.services.client.scope.*;
import org.integratedmodelling.klab.api.collections.Parameters;
import org.integratedmodelling.klab.api.data.KnowledgeGraph;
import org.integratedmodelling.klab.api.digitaltwin.DigitalTwin;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.identities.UserIdentity;
import org.integratedmodelling.klab.api.knowledge.SemanticType;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.knowledge.observation.impl.ObservationImpl;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.services.RuntimeService;
import org.integratedmodelling.klab.api.services.resolver.ResolutionConstraint;
import org.integratedmodelling.klab.services.scopes.*;
import org.junit.jupiter.api.Test;

class RelationshipScopeRestorationTest {
  private ObservationImpl observation(long id, SemanticType kind) {
    var meaning = new ConceptImpl(); meaning.setUrn("test:" + kind); meaning.setName(kind.name());
    meaning.setType(EnumSet.of(kind, SemanticType.COUNTABLE));
    var observable = new ObservableImpl(); observable.setSemantics(meaning); observable.setUrn(meaning.getUrn());
    var observation = new ObservationImpl(); observation.setId(id); observation.setUrn("instance:" + id);
    observation.setObservable(observable); observation.setGeometry(Geometry.create("S2(1,1)")); return observation;
  }

  private List<ContextScope> roots() throws Exception {
    var user = mock(UserIdentity.class); when(user.getId()).thenReturn("alice"); when(user.getUsername()).thenReturn("alice");
    when(user.getData()).thenReturn(Parameters.create());
    var runtime = mock(RuntimeService.class); when(runtime.serviceId()).thenReturn("runtime");
    when(runtime.getUrl()).thenReturn(URI.create("http://127.0.0.1:8094").toURL());
    var configuration = DigitalTwin.Configuration.builder().id("session.twin").name("Twin").build();
    var parent = new ClientSessionScope(new ClientUserScope(user, null), "session", runtime).withId("session");
    var client = new ClientContextScope(parent, runtime, configuration); client.setId("session.twin");
    var session = new ServiceSessionScope(new ServiceUserScope(user, runtime));
    return List.of(client, new ServiceContextScope(session, configuration, user));
  }

  private ResolutionConstraint[] pair(Observation source, Observation target) {
    return new ResolutionConstraint[] {
        ResolutionConstraint.of(ResolutionConstraint.Type.UnresolvedSourceObservation, source),
        ResolutionConstraint.of(ResolutionConstraint.Type.UnresolvedTargetObservation, target)};
  }

  @Test void pairedRestorationPreservesAmbientContextObserverAndDerivedScopes() throws Exception {
    for (var root : roots()) {
      var source = observation(-10, SemanticType.SUBJECT); var target = observation(-12, SemanticType.SUBJECT);
      var ambient = observation(-20, SemanticType.SUBJECT); var observer = observation(50, SemanticType.AGENT);
      var selected = root.within(ambient).withObserver(observer);
      var restored = selected.withResolutionConstraints(pair(source, target));
      assertNull(root.getSourceObservation()); assertSame(ambient, restored.getContextObservation());
      assertSame(observer, restored.getObserver()); assertSame(source, restored.getSourceObservation());
      assertSame(target, restored.getTargetObservation());
      var clone = restored.within(null).withResolutionConstraints(
          ResolutionConstraint.of(ResolutionConstraint.Type.ResolutionProject, "fixture"));
      assertNull(clone.getContextObservation()); assertSame(observer, clone.getObserver());
      assertEquals(50L, clone.getConstraint(ResolutionConstraint.Type.Observer, Long.class));
      assertSame(source, clone.getSourceObservation()); assertSame(target, clone.getTargetObservation());
    }
  }

  @Test void restorationRejectsUnpairedAndCrossedPayloadWithoutMutatingSharedScope() throws Exception {
    for (var root : roots()) {
      var source = observation(-10, SemanticType.SUBJECT); var target = observation(-12, SemanticType.SUBJECT);
      assertThrows(IllegalArgumentException.class, () -> root.withResolutionConstraints(pair(source, target)[0]));
      assertNull(root.getSourceObservation()); assertNull(root.getTargetObservation());
      var between = root.between(source, target);
      assertThrows(IllegalArgumentException.class, () -> between.withResolutionConstraints(pair(target, source)));
      assertSame(source, between.getSourceObservation()); assertSame(target, between.getTargetObservation());
    }
  }

  @Test void newBetweenSelectionReplacesInheritedSnapshotsInOutgoingRequests() throws Exception {
    for (var root : roots()) {
      var source = observation(-10, SemanticType.SUBJECT); var target = observation(-12, SemanticType.SUBJECT);
      var original = root.withResolutionConstraints(pair(source, target));
      var nextSource = observation(-30, SemanticType.SUBJECT); var nextTarget = observation(-32, SemanticType.SUBJECT);
      var changed = original.between(nextSource, nextTarget).withResolutionConstraints(
          ResolutionConstraint.of(ResolutionConstraint.Type.Provenance,
              org.integratedmodelling.klab.api.provenance.Agent.create("actor")));
      var outgoing = ResolverClient.scopeForTransport(changed);
      var recipient = mock(ContextScope.class);
      var restored = ResolutionConstraint.unresolvedRelationshipScope(recipient, outgoing.toArray(ResolutionConstraint[]::new));
      assertEquals(-30, restored.source().getId()); assertEquals(-32, restored.target().getId());
      assertEquals(2, outgoing.stream().filter(c -> ResolutionConstraint.isRelationshipSnapshot(c.getType())).count());
      assertThrows(IllegalArgumentException.class,
          () -> changed.withResolutionConstraints(original.getResolutionConstraints().toArray(ResolutionConstraint[]::new)));
      assertSame(nextSource, changed.getSourceObservation()); assertSame(nextTarget, changed.getTargetObservation());
    }
  }

  @Test void clearingBetweenFocusDoesNotResurrectInheritedEndpointSnapshots() throws Exception {
    for (var root : roots()) {
      var restored = root.withResolutionConstraints(pair(observation(-10, SemanticType.SUBJECT), observation(-12, SemanticType.SUBJECT)));
      var focused = restored.between(null, null).within(observation(-20, SemanticType.SUBJECT))
          .withResolutionConstraints(ResolutionConstraint.of(ResolutionConstraint.Type.ResolutionNamespace, "fixture"));
      assertNull(focused.getSourceObservation()); assertNull(focused.getTargetObservation());
      assertTrue(ResolverClient.scopeForTransport(focused).stream()
          .noneMatch(c -> ResolutionConstraint.isRelationshipSnapshot(c.getType())));
    }
  }

  @Test void nonnullWithinReplacesBetweenAndPendingSnapshotsWithoutMutatingParent() throws Exception {
    for (var root : roots()) {
      var source = observation(-10, SemanticType.SUBJECT); var target = observation(-12, SemanticType.SUBJECT);
      var ambient = observation(-20, SemanticType.SUBJECT); var observer = observation(50, SemanticType.AGENT);
      var paired = root.within(ambient).withObserver(observer).withResolutionConstraints(pair(source, target));
      var replacement = observation(-30, SemanticType.SUBJECT);
      var focused = paired.within(replacement).withResolutionConstraints(
          ResolutionConstraint.of(ResolutionConstraint.Type.ResolutionNamespace, "fixture"));
      assertSame(replacement, focused.getContextObservation()); assertSame(observer, focused.getObserver());
      assertNull(focused.getSourceObservation()); assertNull(focused.getTargetObservation());
      assertTrue(focused.getResolutionConstraints().stream().noneMatch(c -> ResolutionConstraint.isRelationshipSnapshot(c.getType())));
      assertTrue(ResolverClient.scopeForTransport(focused).stream().noneMatch(c -> ResolutionConstraint.isRelationshipSnapshot(c.getType())));
      assertSame(ambient, paired.getContextObservation()); assertSame(source, paired.getSourceObservation());
      assertSame(target, paired.getTargetObservation());
      var ambientCleared = paired.within(null);
      assertNull(ambientCleared.getContextObservation()); assertSame(source, ambientCleared.getSourceObservation());
      assertSame(target, ambientCleared.getTargetObservation());
      assertEquals(2, ResolverClient.scopeForTransport(ambientCleared).stream()
          .filter(c -> ResolutionConstraint.isRelationshipSnapshot(c.getType())).count());
    }
  }

  @Test void mixedCommittedAndPendingPairUsesCanonicalCommittedEndpoint() throws Exception {
    for (var root : roots()) {
      var canonical = observation(10, SemanticType.SUBJECT); var pending = observation(-12, SemanticType.SUBJECT);
      KnowledgeGraph graph;
      if (root instanceof ClientContextScope) {
        var twin = mock(ClientDigitalTwin.class); var clientGraph = mock(ClientKnowledgeGraph.class);
        when(twin.getKnowledgeGraph()).thenReturn(clientGraph); graph = clientGraph;
        var field = ClientContextScope.class.getDeclaredField("digitalTwin"); field.setAccessible(true); field.set(root, twin);
      } else {
        var twin = mock(DigitalTwin.class); graph = mock(KnowledgeGraph.class);
        when(twin.getKnowledgeGraph()).thenReturn(graph);
        var field = ServiceContextScope.class.getDeclaredField("digitalTwin"); field.setAccessible(true); field.set(root, twin);
      }
      when(graph.getAsset(eq(10L), any(), eq(Observation.class))).thenReturn(canonical);
      var claimed = observation(10, SemanticType.SUBJECT); claimed.setGeometry(null); claimed.setObservable(null);
      var restored = root.withResolutionConstraints(pair(claimed, pending));
      assertSame(canonical, restored.getSourceObservation()); assertSame(pending, restored.getTargetObservation());
      assertNull(root.getSourceObservation());
      verify(graph, never()).createTransaction(any());
    }
  }
}
