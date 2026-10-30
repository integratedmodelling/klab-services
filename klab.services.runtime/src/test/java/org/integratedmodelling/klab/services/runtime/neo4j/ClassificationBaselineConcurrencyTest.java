package org.integratedmodelling.klab.services.runtime.neo4j;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.google.common.cache.CacheBuilder;
import java.util.*;
import org.integratedmodelling.klab.api.data.*;
import org.integratedmodelling.klab.api.digitaltwin.*;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.identities.UserIdentity;
import org.integratedmodelling.klab.api.knowledge.*;
import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.knowledge.observation.impl.ObservationImpl;
import org.integratedmodelling.klab.api.provenance.Activity;
import org.integratedmodelling.klab.api.provenance.impl.ActivityImpl;
import org.integratedmodelling.klab.api.services.Reasoner;
import org.integratedmodelling.klab.services.runtime.MemberClassifierExecutor.PendingAttribution;
import org.integratedmodelling.klab.services.runtime.digitaltwin.DigitalTwinImpl;
import org.integratedmodelling.klab.services.scopes.ServiceContextScope;
import org.junit.jupiter.api.*;

/** Real embedded native snapshots/validation, with a spy commit sink to prove rejection before writes. */
class ClassificationBaselineConcurrencyTest {
  @BeforeAll static void start() { ClassificationPersistenceTest.start(); }
  @AfterAll static void stop() { ClassificationPersistenceTest.stop(); }
  ClassificationPersistenceTest.Fixture nativeGraph;
  KnowledgeGraph.Transaction sink;
  DigitalTwinImpl.TransactionImpl root;
  DigitalTwinImpl.TransactionImpl child;
  ServiceContextScope scope;
  Reasoner reasoner;
  Observable before;
  DigitalTwinImpl twin;
  KnowledgeGraphNeo4j kg;

  @BeforeEach void setup() throws Exception {
    ClassificationPersistenceTest.rows("MATCH (n) DETACH DELETE n",Map.of());
    nativeGraph=new ClassificationPersistenceTest.Fixture("a");
    scope=mock(ServiceContextScope.class); when(scope.getId()).thenReturn("a");
    reasoner=mock(Reasoner.class); when(scope.getService(Reasoner.class)).thenReturn(reasoner);
    when(scope.getService(org.integratedmodelling.klab.api.services.RuntimeService.class))
        .thenReturn(mock(org.integratedmodelling.klab.api.services.RuntimeService.class));
    when(reasoner.satisfiable(any())).thenReturn(true);
    before=meaning("test:Tree"); when(reasoner.resolveObservable("test:Tree")).thenReturn(before);
    var member=member();
    var properties=nativeGraph.properties(member);
    ClassificationPersistenceTest.rows("CREATE (:Context {id:'a'}),(:Context {id:'b'}), "
        + "(:Cohort {id:10,observable:'test:Tree'}),(:Observation $properties)",Map.of("properties",properties));
    ClassificationPersistenceTest.rows("MATCH (a:Context {id:'a'}),(c:Cohort {id:10}),(m:Observation {id:41}) "
        + "CREATE (a)-[:HAS_CHILD]->(c),(c)-[:HAS_MEMBER]->(m),(m)-[:HAS_GEOMETRY]->(:Geometry {definition:'S2'})",Map.of());
    kg=mock(KnowledgeGraphNeo4j.class); sink=mock(KnowledgeGraph.Transaction.class);
    when(kg.nextKey()).thenReturn(900L); when(kg.createTransaction(scope)).thenReturn(sink);
    doAnswer(call->{
      try(var nativeTx=nativeGraph.createTransaction(scope)) {
        nativeTx.validateClassificationMembers(call.getArgument(0));
      }
      return null;
    }).when(sink).validateClassificationMembers(any());
    var counter=new java.util.concurrent.atomic.AtomicLong(1000);
    doAnswer(call->{var asset=call.getArgument(0);if(asset instanceof ActivityImpl activity)activity.setId(counter.incrementAndGet());return null;})
        .when(sink).store(any());
    twin=mock(DigitalTwinImpl.class);
    set(twin,"knowledgeGraph",kg); set(twin,"commitCache",CacheBuilder.newBuilder().build());
    var user=mock(UserIdentity.class); when(user.getUsername()).thenReturn("baseline-test"); when(scope.getUser()).thenReturn(user);
    root=twin.new TransactionImpl(Activity.of(Activity.Type.SUBMISSION),scope,RuntimeAsset.PROVENANCE_ASSET);
    child=(DigitalTwinImpl.TransactionImpl)root.getChild(Activity.of(Activity.Type.CLASSIFICATION,root.getActivity()),scope);
  }

  private static void set(Object target,String field,Object value)throws Exception {
    var member=DigitalTwinImpl.class.getDeclaredField(field); member.setAccessible(true);member.set(target,value);
  }
  private Observable meaning(String urn) {
    var meaning=mock(Observable.class);var concept=mock(Concept.class);
    when(meaning.getUrn()).thenReturn(urn);when(meaning.getSemantics()).thenReturn(concept);
    when(meaning.getArtifactType()).thenReturn(Artifact.Type.OBJECT);when(meaning.displayLabel()).thenReturn("Tree");
    when(concept.getUrn()).thenReturn(urn);when(concept.getType()).thenReturn(EnumSet.of(SemanticType.SUBJECT,SemanticType.OBSERVABLE));
    return meaning;
  }
  private ObservationImpl member() {
    var member=new ObservationImpl();member.setId(41);member.setUrn("a.41");member.setName("Tree");member.setObservable(before);
    member.setGeometry(Geometry.create("S2"));member.setParentId(77);member.setResolvedCoverage(1);
    member.getMetadata().put("origin","captured");
    member.setContextualizationData(new ObservationImpl.ContextualizationDataImpl());
    return member;
  }
  private KnowledgeGraph.ClassificationReadSet capture() throws Exception {
    try(var tx=nativeGraph.createTransaction(scope)){return tx.captureClassificationMembers(10);}
  }
  private List<Observation> materialize(KnowledgeGraph.ClassificationReadSet read) {
    return read.members().stream().map(snapshot->nativeGraph.materializeClassificationMember(snapshot,scope)).toList();
  }
  private void attribute(Observation member) {
    var builder=mock(Observable.Builder.class);when(before.builder(scope)).thenReturn(builder);
    var attributed=meaning("test:BeechTree");when(builder.buildObservable()).thenReturn(attributed);
    var family=mock(Concept.class);when(family.getUrn()).thenReturn("test:Species");
    var predicate=mock(Concept.class);when(predicate.getUrn()).thenReturn("test:Beech");when(predicate.is(SemanticType.PREDICATE)).thenReturn(true);
    child.stageAttributions(List.of(new PendingAttribution(member,before,family,predicate,
        Geometry.create("S2"),Scheduler.Event.initialization())),scope);
  }

  @Test void sameObservablePayloadChangeAfterClassifierReadRejectsRootBeforeWrites() throws Exception {
    var read=capture();var fresh=materialize(read);root.guardClassificationBaseline(read,fresh);
    attribute(child.readObservation(fresh.getFirst()));
    ClassificationPersistenceTest.rows("MATCH (n:Observation {id:41}) SET n.metadata=$metadata",Map.of("metadata","{\"origin\":\"external\"}"));
    assertEquals(-1,root.commit());verify(sink,never()).store(any());
    assertEquals("test:Tree",ClassificationPersistenceTest.rows("MATCH (n:Observation {id:41}) RETURN n.observable AS value",Map.of()).getFirst().get("value"));
  }

  @Test void sameObservableGeometryChangeAfterClassifierReadRejectsRootBeforeWrites() throws Exception {
    var read=capture();var fresh=materialize(read);root.guardClassificationBaseline(read,fresh);
    attribute(child.readObservation(fresh.getFirst()));
    ClassificationPersistenceTest.rows("MATCH (:Observation {id:41})-[:HAS_GEOMETRY]->(g) SET g.definition='S3'",Map.of());
    assertEquals(-1,root.commit());verify(sink,never()).store(any());
  }

  @Test void staleCanonicalPayloadPresentBeforeFirstCaptureCannotBeBlessedByLaterReadSet() throws Exception {
    var stale=member();root.add(stale);
    ClassificationPersistenceTest.rows("MATCH (n:Observation {id:41}) SET n.metadata=$metadata",Map.of("metadata","{\"origin\":\"newer\"}"));
    var read=capture();assertThrows(IllegalStateException.class,()->root.guardClassificationBaseline(read,materialize(read)));
    assertTrue(root.isFailed());assertEquals(-1,root.commit());verify(sink,never()).store(any());
  }

  @Test void staleCanonicalGeometryPresentBeforeFirstCaptureCannotBeBlessedByLaterReadSet() throws Exception {
    root.add(member());
    ClassificationPersistenceTest.rows("MATCH (:Observation {id:41})-[:HAS_GEOMETRY]->(g) SET g.definition='S3'",Map.of());
    var read=capture();assertThrows(IllegalStateException.class,()->root.guardClassificationBaseline(read,materialize(read)));
    assertEquals(-1,root.commit());verify(sink,never()).store(any());
  }

  @Test void changedNumericKindBeforeFirstCaptureDoesNotBlessStaleCanonicalMetadata() throws Exception {
    var stale=member();stale.getMetadata().put("kind",1);root.add(stale);
    ClassificationPersistenceTest.rows("MATCH (n:Observation {id:41}) SET n.metadata=$metadata",
        Map.of("metadata","{\"origin\":\"captured\",\"kind\":1.0}"));
    var read=capture();assertThrows(IllegalStateException.class,()->root.guardClassificationBaseline(read,materialize(read)));
    assertEquals(-1,root.commit());verify(sink,never()).store(any());
  }

  @Test void earliestBaselineIsSharedAndLaterDifferentSnapshotNeverReplacesIt() throws Exception {
    var read=capture();child.guardClassificationBaseline(read,materialize(read));assertTrue(root.hasClassificationBaseline(10));
    ClassificationPersistenceTest.rows("MATCH (n:Observation {id:41}) SET n.name='external'",Map.of());
    var newer=capture();assertNotEquals(read,newer);
    assertThrows(IllegalStateException.class,()->root.guardClassificationBaseline(newer,materialize(newer)));
    assertEquals(-1,root.commit());verify(sink,never()).store(any());
  }

  @Test void nativeBaselineWinsOverAnOlderScopeCacheObjectWithoutCreatingAnObservation() throws Exception {
    var read=capture();var fresh=materialize(read);
    assertFalse(fresh.getFirst().getMetadata().containsKey("semanticRevision"));
    assertFalse(fresh.getFirst().getMetadata().containsKey(GraphModel.Fields.RESOLVED_COVERAGE));
    root.guardClassificationBaseline(read,fresh);
    var stale=member();stale.getMetadata().put("origin","old-cache");
    assertEquals("captured",child.readObservation(stale).getMetadata().get("origin"));
    assertTrue(root.assets().stream().noneMatch(Observation.class::isInstance));
    assertTrue(root.commit()>0);
  }

  @Test void aRealTwinCommitReceiptDoesNotMakeItsCachedContextStaleForTheNextClassification() throws Exception {
    var cachedContext=member();
    cachedContext.setId(-41);
    when(kg.nextKey()).thenReturn(900L,901L,902L);
    // Persist at the actual twin's store boundary, including provenance metadata added before it.
    doAnswer(call->{
      var observation=call.getArgument(0,Observation.class);
      ((ObservationImpl)observation).setId(41);
      ClassificationPersistenceTest.rows("MATCH (n:Observation {id:41}) SET n=$properties",
          Map.of("properties",nativeGraph.properties(observation)));
      return null;
    }).when(sink).store(isA(Observation.class));
    root.add(cachedContext);
    assertTrue(root.commit()>0);
    assertEquals(900L,cachedContext.getMetadata().get(Metadata.IM_COMMIT_ID));
    var referencing=twin.new TransactionImpl(Activity.of(Activity.Type.SUBMISSION),scope,RuntimeAsset.PROVENANCE_ASSET);
    referencing.link(referencing.getActivity(),cachedContext,GraphModel.Relationship.HAS_CONTEXT);
    assertTrue(referencing.commit()>0);
    assertEquals(901L,cachedContext.getMetadata().get(Metadata.IM_COMMIT_ID),
        "A subsequent commit updates its cached context receipt without rewriting that unmodified observation");
    var read=capture();var fresh=materialize(read);
    assertEquals(900L,((Number)fresh.getFirst().getMetadata().get(Metadata.IM_COMMIT_ID)).longValue());
    assertEquals(AbstractKnowledgeGraph.persistedObservationProperties(fresh.getFirst()),
        AbstractKnowledgeGraph.persistedObservationProperties(cachedContext));
    var next=twin.new TransactionImpl(Activity.of(Activity.Type.SUBMISSION),scope,RuntimeAsset.PROVENANCE_ASSET);
    // HAS_CONTEXT puts the cached, returned Region in the new native transaction before baseline capture.
    next.link(next.getActivity(),cachedContext,GraphModel.Relationship.HAS_CONTEXT);
    assertDoesNotThrow(()->next.guardClassificationBaseline(read,fresh));
    assertTrue(next.commit()>0);
    verify(sink).validateClassificationMembers(read);
  }

  @Test void receiptNormalizationStillGuardsAllOtherMetadataAndTheRawNativeReadSet() throws Exception {
    var cached=member();cached.getMetadata().put(Metadata.IM_COMMIT_ID,99L);root.add(cached);
    var read=capture();root.guardClassificationBaseline(read,materialize(read));
    // Even the normalized receipt remains part of the independent raw native read-set guard.
    ClassificationPersistenceTest.rows("MATCH (n:Observation {id:41}) SET n."+GraphModel.Fields.METADATA+"=$metadata",
        Map.of("metadata","{\"origin\":\"captured\",\"im:commit-id\":88}"));
    assertEquals(-1,root.commit());verify(sink,never()).store(any());
  }

  @Test void spatialGeometryTypeIsStructuralWhileUnknownLegacyUserMetadataRemainsInput() throws Exception {
    ClassificationPersistenceTest.rows("MATCH (n:Observation {id:41}) SET n.gtype=3,n.userLegacy='retained'",Map.of());
    var read=capture();var fresh=materialize(read);
    assertEquals(3L,((Number)read.members().getFirst().nativeProperties().get("gtype")).longValue());
    assertFalse(fresh.getFirst().getMetadata().containsKey("gtype"));
    assertEquals("retained",fresh.getFirst().getMetadata().get("userLegacy"));
    var cached=member();cached.getMetadata().put("userLegacy","retained");
    root.link(root.getActivity(),cached,GraphModel.Relationship.HAS_CONTEXT);
    assertDoesNotThrow(()->root.guardClassificationBaseline(read,fresh));
    assertTrue(root.commit()>0);verify(sink).validateClassificationMembers(read);
  }

  @Test void spatialStructuralPropertyMutationStillInvalidatesTheRawNativeReadSet() throws Exception {
    ClassificationPersistenceTest.rows("MATCH (n:Observation {id:41}) SET n.gtype=3",Map.of());
    var read=capture();root.guardClassificationBaseline(read,materialize(read));
    ClassificationPersistenceTest.rows("MATCH (n:Observation {id:41}) SET n.gtype=6",Map.of());
    assertEquals(-1,root.commit());verify(sink,never()).store(any());
  }

  @Test void unknownLegacyUserMetadataMutationStillInvalidatesTheNativeReadSet() throws Exception {
    ClassificationPersistenceTest.rows("MATCH (n:Observation {id:41}) SET n.userLegacy='before'",Map.of());
    var read=capture();root.guardClassificationBaseline(read,materialize(read));
    ClassificationPersistenceTest.rows("MATCH (n:Observation {id:41}) SET n.userLegacy='after'",Map.of());
    assertEquals(-1,root.commit());verify(sink,never()).store(any());
  }

  @Test void explicitlySerializedUserMetadataIsPreservedEvenWhenItsNameMatchesSpatialStructure() throws Exception {
    ClassificationPersistenceTest.rows("MATCH (n:Observation {id:41}) SET n.gtype=3,n."
        +GraphModel.Fields.METADATA+"=$metadata",
        Map.of("metadata","{\"origin\":\"captured\",\"gtype\":\"user-value\"}"));
    var read=capture();var fresh=materialize(read);
    assertEquals("user-value",fresh.getFirst().getMetadata().get("gtype"));
    root.guardClassificationBaseline(read,fresh);
    assertTrue(root.commit()>0);
  }

  @Test void ownPendingAttributionChildAndInitializationWritesPreserveRawBaseline() throws Exception {
    var read=capture();var fresh=materialize(read);root.guardClassificationBaseline(read,fresh);
    var original=fresh.getFirst();attribute(child.readObservation(original));
    var quality=new ObservationImpl();quality.setId(99);quality.setUrn("a.99");quality.setObservable(before);quality.setGeometry(Geometry.create("S2"));
    child.link(original,quality,GraphModel.Relationship.HAS_CHILD);
    child.markSchedulerRegistered(original);child.appendSchedulerInitializationTimestamps(original,List.of(123L));
    assertTrue(root.commit()>0);verify(sink).validateClassificationMembers(read);
  }

  @Test void persistedProjectionIgnoresRuntimeFieldsButPreservesTypedMetadataAndPriorities() {
    var first=member();var second=member();
    ((ObservationImpl.ContextualizationDataImpl)first.getContextualizationData()).setServiceId("local-runtime");
    first.getNotifications().add(org.integratedmodelling.klab.api.services.runtime.Notification.info("runtime diagnostic"));
    first.setTransientId(123);second.setTransientId(456);
    assertEquals(AbstractKnowledgeGraph.persistedObservationProperties(first),AbstractKnowledgeGraph.persistedObservationProperties(second));
    first.getMetadata().put("typed",1);second.getMetadata().put("typed","1");
    assertNotEquals(AbstractKnowledgeGraph.persistedObservationProperties(first),AbstractKnowledgeGraph.persistedObservationProperties(second));
    second.getMetadata().put("typed",1.0);
    assertNotEquals(AbstractKnowledgeGraph.persistedObservationProperties(first),AbstractKnowledgeGraph.persistedObservationProperties(second));
    second.getMetadata().put("typed",1);first.setAnnotationPriorities(Map.of("test",10));second.setAnnotationPriorities(Map.of("test",20));
    assertNotEquals(AbstractKnowledgeGraph.persistedObservationProperties(first),AbstractKnowledgeGraph.persistedObservationProperties(second));
  }

  @Test void numericProjectionUsesWireKindWithoutRejectingEquivalentDecimalCodecValues() {
    var first=member();var second=member();
    first.getMetadata().put("typed",new java.math.BigDecimal("1"));second.getMetadata().put("typed",1);
    assertEquals(AbstractKnowledgeGraph.persistedObservationProperties(first),AbstractKnowledgeGraph.persistedObservationProperties(second));
    first.getMetadata().put("typed",new java.math.BigDecimal("1.0"));second.getMetadata().put("typed",1.0);
    assertEquals(AbstractKnowledgeGraph.persistedObservationProperties(first),AbstractKnowledgeGraph.persistedObservationProperties(second));
    second.getMetadata().put("typed",1);
    assertNotEquals(AbstractKnowledgeGraph.persistedObservationProperties(first),AbstractKnowledgeGraph.persistedObservationProperties(second));
    var exponent=new java.math.BigDecimal("1E+1");first.getMetadata().put("typed",exponent);
    second.getMetadata().put("typed",org.integratedmodelling.klab.utilities.Utils.Json.parseObject(
        org.integratedmodelling.klab.utilities.Utils.Json.asString(exponent),Object.class));
    assertEquals(AbstractKnowledgeGraph.persistedObservationProperties(first),AbstractKnowledgeGraph.persistedObservationProperties(second));
  }
}
