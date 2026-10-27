package org.integratedmodelling.klab.runtime.libraries;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.*;
import org.integratedmodelling.common.services.client.runtime.KnowledgeGraphQuery;
import org.integratedmodelling.klab.api.collections.Parameters;
import org.integratedmodelling.klab.api.data.*;
import org.integratedmodelling.klab.api.digitaltwin.*;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.knowledge.Cohort;
import org.integratedmodelling.klab.api.knowledge.Concept;
import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.api.knowledge.SemanticType;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.knowledge.observation.scale.Scale;
import org.integratedmodelling.klab.api.knowledge.observation.impl.ObservationImpl;
import org.integratedmodelling.klab.api.provenance.Activity;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.services.Reasoner;
import org.integratedmodelling.klab.configuration.ServiceConfiguration;
import org.integratedmodelling.klab.runtime.scale.space.ShapeImpl;
import org.junit.jupiter.api.*;

class ObservationInspectorTest {
  @BeforeAll static void setup() { ServiceConfiguration.injectInstantiators(); }

  private static Geometry geometry(String text) { return Scale.create(ShapeImpl.create(text)); }

  class Fixture {
    final ContextScope scope = mock(ContextScope.class);
    final DigitalTwin twin = mock(DigitalTwin.class);
    final KnowledgeGraph graph = mock(KnowledgeGraph.class);
    final Reasoner reasoner = mock(Reasoner.class);
    final Cohort cohort = mock(Cohort.class);
    final Map<String, Concept> concepts = new HashMap<>();
    final Map<Concept, List<Concept>> traits = new HashMap<>();
    final Map<Long, Observation> assets = new HashMap<>();
    final List<Observation> members = new ArrayList<>();
    final List<Activity> activities = new ArrayList<>();
    final Map<Long, List<KnowledgeGraph.Link>> incoming = new HashMap<>(), outgoing = new HashMap<>();
    final Concept region = meaning("test:Region", false, SemanticType.SUBJECT);
    final Concept family = meaning("test:Environment", false, SemanticType.REALM, SemanticType.PREDICATE, SemanticType.ABSTRACT);
    final Concept category = meaning("test:Terrestrial", false, SemanticType.REALM, SemanticType.PREDICATE);
    final Concept attributed = meaning("test:Terrestrial test:Region", false, SemanticType.SUBJECT);
    ObservationImpl producer, member;

    Fixture() {
      when(scope.getId()).thenReturn("context"); when(scope.getDigitalTwin()).thenReturn(twin);
      when(scope.getService(Reasoner.class)).thenReturn(reasoner); when(twin.getKnowledgeGraph()).thenReturn(graph);
      when(cohort.getId()).thenReturn(30L);
      when(reasoner.resolveConcept(anyString())).thenAnswer(call -> concepts.get(call.getArgument(0)));
      when(reasoner.directTraits(any())).thenAnswer(call -> traits.getOrDefault(call.getArgument(0), List.of()));
      when(reasoner.directRoles(any())).thenReturn(List.of());
      when(reasoner.is(category, family)).thenReturn(true);
      traits.put(attributed, List.of(category));
      var collective = meaning("each test:Region", true, SemanticType.SUBJECT);
      when(collective.singular()).thenReturn(region);
      producer = observation(10, collective, Geometry.UNIVERSAL);
      member = observation(20, region, Geometry.UNIVERSAL); members.add(member);
      when(graph.query(any(Class.class), eq(scope))).thenAnswer(call ->
          new KnowledgeGraphQuery<>(KnowledgeGraphQuery.AssetType.classify(call.getArgument(0, Class.class))));
      when(graph.query(any(KnowledgeGraph.Query.class), eq(Cohort.class), eq(scope))).thenAnswer(call -> {
        var query = call.getArgument(0, KnowledgeGraphQuery.class);
        return query.getSource() != null && query.getSource().getId() == producer.getId()
            && query.getRelationship() == GraphModel.Relationship.CONTRIBUTED_TO ? List.of(cohort) : List.of();
      });
      when(graph.query(any(KnowledgeGraph.Query.class), eq(Observation.class), eq(scope))).thenAnswer(call -> {
        var query = call.getArgument(0, KnowledgeGraphQuery.class);
        return query.getSource() != null && query.getSource().getId() == cohort.getId()
            && query.getRelationship() == GraphModel.Relationship.HAS_MEMBER ? List.copyOf(members) : List.of();
      });
      when(graph.query(any(KnowledgeGraph.Query.class), eq(Activity.class), eq(scope)))
          .thenAnswer(call -> List.copyOf(activities));
      when(graph.getAsset(anyLong(), eq(scope), eq(Observation.class))).thenAnswer(call -> assets.get(call.getArgument(0)));
      when(graph.getLinks(any(), any(), eq(scope), any(GraphModel.Relationship[].class))).thenAnswer(call -> {
        var asset = call.getArgument(0, RuntimeAsset.class);
        return (call.getArgument(1) == GraphModel.Relationship.Direction.INCOMING ? incoming : outgoing)
            .getOrDefault(asset.getId(), List.of());
      });
    }

    Concept meaning(String urn, boolean collective, SemanticType... types) {
      var concept = mock(Concept.class); concepts.put(urn, concept);
      var set = EnumSet.noneOf(SemanticType.class); set.addAll(List.of(types));
      when(concept.getUrn()).thenReturn(urn); when(concept.getType()).thenReturn(set);
      when(concept.isCollective()).thenReturn(collective); when(concept.singular()).thenReturn(concept);
      when(concept.is(any(SemanticType.class))).thenAnswer(call -> set.contains(call.getArgument(0)));
      return concept;
    }

    ObservationImpl observation(long id, Concept semantics, Geometry geometry) {
      var urn = semantics.getUrn();
      var observable = mock(Observable.class); when(observable.getSemantics()).thenReturn(semantics);
      when(observable.getUrn()).thenReturn(urn);
      var result = new ObservationImpl(); result.setId(id); result.setUrn("instance:" + id);
      result.setObservable(observable); result.setGeometry(geometry); assets.put(id, result); return result;
    }

    Activity audit(long id, String input, String origin) {
      var activity = Activity.of(Activity.Type.CLASSIFICATION); activity.setId(id); activity.setOutcome(Activity.Outcome.SUCCESS);
      activity.getMetadata().put(Metadata.IM_CLASSIFICATION_MEMBER_RESULTS, Map.of("context", "context", "memberResults", List.of(
          Map.of("memberIdAtInvocation", 20, "inputObservable", input, "requestedPredicate", "test:Environment",
              "concretePredicates", List.of("test:Terrestrial"), "origin", origin))));
      activities.add(activity); return activity;
    }

    KnowledgeGraph.Link edge(RuntimeAsset source, RuntimeAsset target, GraphModel.Relationship relationship, Map<String, Object> properties) {
      var edge = mock(KnowledgeGraph.Link.class); when(edge.source()).thenReturn(source); when(edge.target()).thenReturn(target);
      when(edge.type()).thenReturn(relationship); when(edge.properties()).thenReturn(Parameters.create(properties)); return edge;
    }

    void classify() {
      var urn = attributed.getUrn();
      var after = mock(Observable.class); when(after.getSemantics()).thenReturn(attributed); when(after.getUrn()).thenReturn(urn);
      member.setObservable(after);
      var activity = audit(100, "test:Region", "CLASSIFIER");
      incoming.put(20L, List.of(edge(activity, member, GraphModel.Relationship.CLASSIFIED,
          Map.of("before", "test:Region", "after", "test:Terrestrial test:Region",
              "abstractPredicate", "test:Environment", "predicate", "test:Terrestrial"))));
    }

    ObservationInspector.MemberSnapshot snapshot() { return ObservationInspector.snapshot(scope, producer); }
  }

  @Test void classificationPreservesImmutableBeforeSnapshotAndRequiresTypedMutationEvidence() {
    var f = new Fixture(); var before = f.snapshot(); f.classify();
    assertEquals("test:Region", before.members().get(20L).semantics());
    assertTrue(ObservationInspector.classificationCheck(f.scope, before, "test:Environment"));
    f.incoming.clear();
    assertFalse(ObservationInspector.classificationCheck(f.scope, before, "test:Environment"));
  }

  @Test void missingAuditOrWrongOriginCannotPassClassification() {
    var f = new Fixture(); var before = f.snapshot(); f.classify(); f.activities.clear();
    assertFalse(ObservationInspector.classificationCheck(f.scope, before, "test:Environment"));
    f.audit(101, "test:Region", "EXISTING");
    assertFalse(ObservationInspector.classificationCheck(f.scope, before, "test:Environment"));
  }

  @Test void fractionalAuditMemberIdAndDifferentActivityAttributionCannotPass() {
    var f = new Fixture(); var before = f.snapshot(); f.classify();
    var original = f.activities.removeFirst();
    var other = f.audit(101, "test:Region", "CLASSIFIER");
    assertFalse(ObservationInspector.classificationCheck(f.scope, before, "test:Environment"),
        "A valid edge from a different activity must not certify this audit");
    f.activities.clear(); f.activities.add(original);
    original.getMetadata().put(Metadata.IM_CLASSIFICATION_MEMBER_RESULTS, Map.of("context", "context", "memberResults", List.of(
        Map.of("memberIdAtInvocation", 20.5, "inputObservable", "test:Region", "requestedPredicate", "test:Environment",
            "concretePredicates", List.of("test:Terrestrial"), "origin", "CLASSIFIER"))));
    assertFalse(ObservationInspector.classificationCheck(f.scope, before, "test:Environment"));
  }

  @Test void replacementIdentityOrChangedGeometryCannotMasqueradeAsClassification() {
    var f = new Fixture(); var before = f.snapshot(); f.classify();
    f.members.set(0, f.observation(21, f.attributed, Geometry.UNIVERSAL));
    assertFalse(ObservationInspector.classificationCheck(f.scope, before, "test:Environment"));
    f.members.set(0, f.member); f.member.setGeometry(geometry("EPSG:4326 POINT(0 0)"));
    assertFalse(ObservationInspector.classificationCheck(f.scope, before, "test:Environment"));
  }

  @Test void noopNeedsFreshSuccessfulExistingAuditAndUnchangedState() {
    var f = new Fixture(); f.classify(); var before = f.snapshot();
    assertFalse(ObservationInspector.classificationNoop(f.scope, before, "test:Environment"));
    f.audit(101, "test:Terrestrial test:Region", "EXISTING");
    assertTrue(ObservationInspector.classificationNoop(f.scope, before, "test:Environment"));
    f.audit(102, "test:Terrestrial test:Region", "EXISTING");
    assertFalse(ObservationInspector.classificationNoop(f.scope, before, "test:Environment"));
  }

  @Test void previouslyClassifiedFixtureCannotProveMutationAndWrongContextCannotReuseSnapshot() {
    var f = new Fixture(); f.classify(); var before = f.snapshot(); f.audit(101, "test:Terrestrial test:Region", "EXISTING");
    assertFalse(ObservationInspector.classificationCheck(f.scope, before, "test:Environment"));
    when(f.scope.getId()).thenReturn("another-context");
    assertThrows(IllegalArgumentException.class, () -> ObservationInspector.classificationNoop(f.scope, before, "test:Environment"));
  }

  @Test void emptyOrDuplicateMembersCannotProduceSnapshot() {
    var f = new Fixture(); f.members.clear();
    assertThrows(IllegalStateException.class, f::snapshot);
    f.members.add(f.member); f.members.add(f.member);
    assertThrows(IllegalStateException.class, f::snapshot);
  }

  private Fixture relationships() {
    var f = new Fixture();
    var relation = f.meaning("test:Link", false, SemanticType.RELATIONSHIP, SemanticType.FUNCTIONAL, SemanticType.UNIDIRECTIONAL);
    var collective = f.meaning("each test:Link", true, SemanticType.RELATIONSHIP, SemanticType.FUNCTIONAL, SemanticType.UNIDIRECTIONAL);
    when(collective.singular()).thenReturn(relation);
    var junction = f.meaning("test:Junction", false, SemanticType.SUBJECT);
    when(f.reasoner.relationshipSource(relation)).thenReturn(junction); when(f.reasoner.relationshipTarget(relation)).thenReturn(junction);
    f.producer = f.observation(10, collective, Geometry.UNIVERSAL);
    f.member = f.observation(20, relation, geometry("EPSG:4326 LINESTRING(0 0, 1 1)"));
    f.members.clear(); f.members.add(f.member);
    var left = f.observation(501, junction, geometry("EPSG:4326 POINT(0 0)"));
    var right = f.observation(502, junction, geometry("EPSG:4326 POINT(1 1)"));
    f.outgoing.put(20L, List.of(f.edge(f.member, left, GraphModel.Relationship.HAS_RELATIONSHIP_SOURCE, Map.of()),
        f.edge(f.member, right, GraphModel.Relationship.HAS_RELATIONSHIP_TARGET, Map.of())));
    return f;
  }

  @Test void directedRelationshipRequiresCommittedOrderedEndpointsWithConnectingGeometry() {
    var f = relationships();
    assertTrue(ObservationInspector.relationshipsCheck(f.scope, f.producer, "test:Link"));
    f.member.setGeometry(geometry("EPSG:4326 LINESTRING(0 0, 2 0)"));
    assertFalse(ObservationInspector.relationshipsCheck(f.scope, f.producer, "test:Link"));
    f.member.setGeometry(geometry("EPSG:4326 LINESTRING(0 0, 1 1)"));
    ((ObservationImpl)f.assets.get(501L)).setGeometry(geometry("EPSG:4326 LINESTRING(0 0, 0.2 0.2)"));
    assertThrows(UnsupportedOperationException.class,
        () -> ObservationInspector.relationshipsCheck(f.scope, f.producer, "test:Link"));
  }

  @Test void polygonsInteriorEndpointsAndExtraVerticesAreNotPointConnections() {
    for (String shape : List.of("POLYGON((-1 -1, 2 -1, 2 2, -1 2, -1 -1))",
        "LINESTRING(-1 -1, 2 2)", "LINESTRING(0 0, 0.5 0.5, 1 1)")) {
      var f = relationships(); f.member.setGeometry(geometry("EPSG:4326 " + shape));
      assertFalse(ObservationInspector.relationshipsCheck(f.scope, f.producer, "test:Link"), shape);
    }
  }

  @Test void canonicalWkbMayReverseSupportWithoutChangingDirectedParticipantRoles() {
    var f = relationships();
    // The actual shared shape encoder canonicalizes coordinate order. Reproduce a directed
    // high-coordinate source -> low-coordinate target before and after transport/persistence.
    var raw = ShapeImpl.create("EPSG:4326 LINESTRING(1 1, 0 0)");
    var decoded = ShapeImpl.create(raw.asWKB());
    var before = (org.locationtech.jts.geom.LineString) raw.getJTSGeometry();
    var after = (org.locationtech.jts.geom.LineString) decoded.getJTSGeometry();
    assertTrue(before.getCoordinateN(0).equals2D(after.getCoordinateN(1)));
    assertTrue(before.getCoordinateN(1).equals2D(after.getCoordinateN(0)));
    f.outgoing.put(20L, List.of(f.edge(f.member, f.assets.get(502L), GraphModel.Relationship.HAS_RELATIONSHIP_SOURCE, Map.of()),
        f.edge(f.member, f.assets.get(501L), GraphModel.Relationship.HAS_RELATIONSHIP_TARGET, Map.of())));
    f.member.setGeometry(Scale.create(raw));
    assertTrue(ObservationInspector.relationshipsCheck(f.scope, f.producer, "test:Link"));
    f.member.setGeometry(Scale.create(decoded));
    assertTrue(ObservationInspector.relationshipsCheck(f.scope, f.producer, "test:Link"));
  }

  @Test void directedEndpointSemanticRolesRemainStrictWhenSupportHasNoOrientation() {
    var f = relationships();
    var other = f.meaning("test:OtherEndpoint", false, SemanticType.SUBJECT);
    var relation = f.member.getObservable().getSemantics();
    when(f.reasoner.relationshipTarget(relation)).thenReturn(other);
    var target = f.observation(502, other, geometry("EPSG:4326 POINT(1 1)"));
    f.outgoing.put(20L, List.of(f.edge(f.member, f.assets.get(501L), GraphModel.Relationship.HAS_RELATIONSHIP_SOURCE, Map.of()),
        f.edge(f.member, target, GraphModel.Relationship.HAS_RELATIONSHIP_TARGET, Map.of())));
    assertTrue(ObservationInspector.relationshipsCheck(f.scope, f.producer, "test:Link"));
    f.outgoing.put(20L, List.of(f.edge(f.member, target, GraphModel.Relationship.HAS_RELATIONSHIP_SOURCE, Map.of()),
        f.edge(f.member, f.assets.get(501L), GraphModel.Relationship.HAS_RELATIONSHIP_TARGET, Map.of())));
    assertFalse(ObservationInspector.relationshipsCheck(f.scope, f.producer, "test:Link"));
  }

  @Test void heterogeneousBondsMatchEndpointMeaningsWithoutIdOrdering() {
    var f = relationships();
    var bond = f.meaning("test:Bond", false, SemanticType.RELATIONSHIP, SemanticType.STRUCTURAL, SemanticType.BIDIRECTIONAL);
    var collective = f.meaning("each test:Bond", true, SemanticType.RELATIONSHIP, SemanticType.STRUCTURAL, SemanticType.BIDIRECTIONAL);
    when(collective.singular()).thenReturn(bond);
    var other = f.meaning("test:OtherEndpoint", false, SemanticType.SUBJECT);
    var junction = f.concepts.get("test:Junction");
    when(f.reasoner.relationshipSource(bond)).thenReturn(other); when(f.reasoner.relationshipTarget(bond)).thenReturn(junction);
    f.producer = f.observation(10, collective, Geometry.UNIVERSAL);
    f.member = f.observation(20, bond, geometry("EPSG:4326 LINESTRING(0 0, 1 1)"));
    f.members.clear(); f.members.add(f.member);
    var source = f.observation(502, other, geometry("EPSG:4326 POINT(1 1)"));
    var target = f.observation(501, junction, geometry("EPSG:4326 POINT(0 0)"));
    f.outgoing.put(20L, List.of(f.edge(f.member, target, GraphModel.Relationship.HAS_RELATIONSHIP_PARTICIPANT, Map.of()),
        f.edge(f.member, source, GraphModel.Relationship.HAS_RELATIONSHIP_PARTICIPANT, Map.of())));
    assertTrue(ObservationInspector.relationshipsCheck(f.scope, f.producer, "test:Bond"));
  }

  @Test void relationshipSelfLoopsMissingParticipantsAndWrongEndpointSemanticsAreRejected() {
    var f = relationships(); var source = f.assets.get(501L);
    f.outgoing.put(20L, List.of(f.edge(f.member, source, GraphModel.Relationship.HAS_RELATIONSHIP_SOURCE, Map.of()),
        f.edge(f.member, source, GraphModel.Relationship.HAS_RELATIONSHIP_TARGET, Map.of())));
    assertFalse(ObservationInspector.relationshipsCheck(f.scope, f.producer, "test:Link"));
    f.outgoing.clear(); assertFalse(ObservationInspector.relationshipsCheck(f.scope, f.producer, "test:Link"));
    f = relationships(); f.assets.remove(502L);
    assertFalse(ObservationInspector.relationshipsCheck(f.scope, f.producer, "test:Link"));
  }

  @Test void duplicateOrderedPairsAndEmptyRelationshipResultsCannotPass() {
    var f = relationships(); var duplicate = f.observation(21, f.member.getObservable().getSemantics(), f.member.getGeometry());
    f.members.add(duplicate); f.outgoing.put(21L, f.outgoing.get(20L));
    assertFalse(ObservationInspector.relationshipsCheck(f.scope, f.producer, "test:Link"));
    f.members.clear(); assertFalse(ObservationInspector.relationshipsCheck(f.scope, f.producer, "test:Link"));
  }

  @Test void staleParticipantReferencesCannotHideCurrentCommittedMeaningOrGeometry() {
    var f = relationships();
    f.observation(501, f.region, geometry("EPSG:4326 POINT(0 0)"));
    assertFalse(ObservationInspector.relationshipsCheck(f.scope, f.producer, "test:Link"),
        "A stale edge meaning must not override the current committed endpoint");
    f = relationships();
    var junction = f.assets.get(501L).getObservable().getSemantics();
    f.observation(501, junction, geometry("EPSG:4326 POINT(0 1)"));
    assertFalse(ObservationInspector.relationshipsCheck(f.scope, f.producer, "test:Link"),
        "A stale edge geometry must not override the current committed endpoint");
  }
}
