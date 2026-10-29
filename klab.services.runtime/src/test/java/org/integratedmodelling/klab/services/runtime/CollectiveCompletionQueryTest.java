package org.integratedmodelling.klab.services.runtime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import com.google.common.cache.CacheBuilder;
import org.integratedmodelling.common.knowledge.*;
import org.integratedmodelling.klab.api.data.*;
import org.integratedmodelling.klab.api.digitaltwin.*;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.identities.UserIdentity;
import org.integratedmodelling.klab.api.knowledge.*;
import org.integratedmodelling.klab.api.knowledge.observation.*;
import org.integratedmodelling.klab.api.knowledge.observation.impl.ObservationImpl;
import org.integratedmodelling.klab.api.provenance.Activity;
import org.integratedmodelling.klab.api.provenance.impl.ActivityImpl;
import org.integratedmodelling.klab.configuration.ServiceConfiguration;
import org.integratedmodelling.klab.services.runtime.digitaltwin.DigitalTwinImpl;
import org.integratedmodelling.klab.services.runtime.neo4j.KnowledgeGraphNeo4j;
import org.integratedmodelling.klab.services.scopes.ServiceContextScope;
import org.junit.jupiter.api.*;

/** Real query and attempt lifecycle, with an atomic in-memory persistence boundary. */
class CollectiveCompletionQueryTest {
  @Test void nativeBinarySnapshotIsImmutableValueEqualAndMaterializesAsBinary() {
    byte[] bytes={0,1,-1};
    var captured=new KnowledgeGraph.ClassificationMemberSnapshot(1,"test:Stream","*",1,Map.of("wkb",bytes),null);
    var same=new KnowledgeGraph.ClassificationMemberSnapshot(1,"test:Stream","*",1,Map.of("wkb",new byte[]{0,1,-1}),null);
    assertEquals(same,captured);
    bytes[0]=8;
    assertEquals(same,captured,"Mutable native storage values must not change a read set");
    var restored=org.neo4j.driver.Values.value(captured.nativeProperties()).get("wkb").asByteArray();
    assertArrayEquals(new byte[]{0,1,-1},restored);
    restored[1]=9;
    assertEquals(same,captured);
    assertArrayEquals(new byte[]{0,1,-1},(byte[])captured.nativeProperties().get("wkb"));
    var changed=new KnowledgeGraph.ClassificationMemberSnapshot(1,"test:Stream","*",1,Map.of("wkb",bytes),null);
    assertNotEquals(captured,changed,"A fresh changed native value must invalidate the read set");
  }
  @BeforeAll static void configure() { ServiceConfiguration.injectInstantiators(); }
  ServiceContextScope scope;
  RuntimeService runtime;
  DigitalTwinImpl twin;
  DigitalTwinImpl.TransactionImpl root;
  KnowledgeGraphNeo4j graph;
  KnowledgeGraph.Transaction storage;
  ObservationImpl producer, query;
  CohortImpl cohort;
  final Map<Long,RuntimeAsset> staged=new LinkedHashMap<>(), durable=new LinkedHashMap<>();
  final List<List<Object>> stagedLinks=new ArrayList<>(), durableLinks=new ArrayList<>();
  final com.fasterxml.jackson.databind.ObjectMapper mapper=
      org.integratedmodelling.common.data.jackson.JacksonConfiguration.newObjectMapper();

  @BeforeEach void setup() throws Exception {
    scope=mock(ServiceContextScope.class); twin=mock(DigitalTwinImpl.class);
    graph=mock(KnowledgeGraphNeo4j.class); storage=mock(KnowledgeGraph.Transaction.class);
    when(scope.getDigitalTwin()).thenReturn(twin); when(twin.getKnowledgeGraph()).thenReturn(graph);
    when(scope.getId()).thenReturn("completion-test");
    var user=mock(UserIdentity.class); when(user.getUsername()).thenReturn("tester"); when(scope.getUser()).thenReturn(user);
    var knowledgeGraph=DigitalTwinImpl.class.getDeclaredField("knowledgeGraph"); knowledgeGraph.setAccessible(true); knowledgeGraph.set(twin,graph);
    var cache=DigitalTwinImpl.class.getDeclaredField("commitCache"); cache.setAccessible(true); cache.set(twin,CacheBuilder.newBuilder().build());
    var semantics=new ConceptImpl(); semantics.setUrn("each test:Stream"); semantics.setReferenceName("each_test_stream");
    semantics.setType(EnumSet.of(SemanticType.SUBJECT,SemanticType.COUNTABLE,SemanticType.OBSERVABLE));
    semantics.setCollective(true); semantics.setDescriptionType(Contextualization.INSTANTIATION);
    var observable=new ObservableImpl(); observable.setSemantics(semantics); observable.setArtifactType(Artifact.Type.OBJECT);
    observable.setUrn("each test:Stream"); observable.setDescriptionType(Contextualization.INSTANTIATION);
    producer=new ObservationImpl(); producer.setId(-10); producer.setObservable(observable);
    producer.setGeometry(Geometry.create("T0(1){tstart=0,tend=10,ttype=PHYSICAL}"));
    query=new ObservationImpl(); query.setId(0); query.setObservable(observable); query.setGeometry(producer.getGeometry());
    cohort=new CohortImpl(); cohort.setId(-20); cohort.setObservable(observable); cohort.setGeometry(producer.getGeometry());
    root=twin.new TransactionImpl(Activity.of(Activity.Type.SUBMISSION),scope,RuntimeAsset.PROVENANCE_ASSET);
    when(scope.getCurrentTransaction()).thenReturn(root);
    root.link(producer,cohort,GraphModel.Relationship.CONTRIBUTED_TO);
    runtime=mock(RuntimeService.class,CALLS_REAL_METHODS);
    doReturn(cohort).when(runtime).getCohortFor(any(),eq(scope),eq(false));
    when(graph.nextKey()).thenReturn(900L); when(graph.createTransaction(scope)).thenReturn(storage);
    var ids=new AtomicLong(1000);
    doAnswer(call->{
      RuntimeAsset asset=call.getArgument(0);
      if(asset instanceof ObservationImpl observation) observation.setId(ids.incrementAndGet());
      if(asset instanceof CohortImpl value) value.setId(ids.incrementAndGet());
      if(asset instanceof ActivityImpl activity) activity.setId(ids.incrementAndGet());
      staged.put(asset.getId(),asset); return null;
    }).when(storage).store(any());
    doAnswer(call->{stagedLinks.add(List.of(call.getArgument(0),call.getArgument(1),call.getArgument(2))); return null;})
        .when(storage).link(any(),any(),any(),any(Object[].class));
    doAnswer(call->{
      for(var asset:staged.values()) {
        RuntimeAsset recovered=asset;
        if(asset instanceof Activity activity) recovered=mapper.readValue(mapper.writeValueAsBytes(activity),Activity.class);
        if(asset instanceof Observation observation) recovered=mapper.readValue(mapper.writeValueAsBytes(observation),Observation.class);
        durable.put(asset.getId(),recovered);
      }
      durableLinks.addAll(stagedLinks); return null;
    }).when(storage).close();
    @SuppressWarnings("unchecked") var observations=(KnowledgeGraph.Query<Observation>)mock(KnowledgeGraph.Query.class,RETURNS_SELF);
    var observationSource = new RuntimeAsset[1]; var observationTarget = new RuntimeAsset[1];
    var observationEffect = new GraphModel.Relationship[1];
    when(graph.query(Observation.class,scope)).thenAnswer(call -> {
      observationSource[0] = null; observationTarget[0] = null; observationEffect[0] = null;
      return observations;
    });
    when(observations.source(any())).thenAnswer(call -> { observationSource[0] = call.getArgument(0); return observations; });
    when(observations.target(any())).thenAnswer(call -> { observationTarget[0] = call.getArgument(0); return observations; });
    when(observations.along(any(), any(Object[].class))).thenAnswer(call -> { observationEffect[0] = call.getArgument(0); return observations; });
    when(observations.run(scope)).thenAnswer(call->durableLinks.stream()
        .filter(link->link.get(2)==observationEffect[0])
        .filter(link->observationSource[0] == null || ((RuntimeAsset)link.getFirst()).getId()==observationSource[0].getId())
        .filter(link->observationTarget[0] == null || ((RuntimeAsset)link.get(1)).getId()==observationTarget[0].getId())
        .map(link->durable.get(((RuntimeAsset)link.get(observationSource[0] == null ? 0 : 1)).getId()))
        .filter(Observation.class::isInstance).map(Observation.class::cast).toList());
    @SuppressWarnings("unchecked") var activities=(KnowledgeGraph.Query<Activity>)mock(KnowledgeGraph.Query.class,RETURNS_SELF);
    when(graph.query(Activity.class,scope)).thenReturn(activities);
    var target=new Observation[1]; var effect=new GraphModel.Relationship[1];
    when(activities.target(any())).thenAnswer(call->{target[0]=call.getArgument(0);return activities;});
    when(activities.along(any(),any(Object[].class))).thenAnswer(call->{effect[0]=call.getArgument(0);return activities;});
    when(activities.run(scope)).thenAnswer(call->durableLinks.stream()
        .filter(link->link.get(2)==effect[0] && ((RuntimeAsset)link.get(1)).getId()==target[0].getId())
        .map(link->(Activity)durable.get(((RuntimeAsset)link.getFirst()).getId())).toList());
  }

  ObservableImpl memberObservable() {
    var observable = new ObservableImpl();
    observable.setSemantics(producer.getObservable().getSemantics().singular());
    observable.setUrn(observable.getSemantics().getUrn());
    observable.setDescriptionType(Contextualization.ACKNOWLEDGEMENT);
    observable.setArtifactType(Artifact.Type.OBJECT);
    return observable;
  }

  DigitalTwin.Transaction execution(Activity.Type type) {
    return execution(type,producer,producer.getGeometry());
  }
  DigitalTwin.Transaction execution(Activity.Type type, Observation target, Geometry support) {
    var activity=(ActivityImpl)CompiledDataflow.createContextualizationActivity(null,target,null,support);
    activity.setType(type);
    return root.getChild(activity,scope,target);
  }
  void complete() { assertEquals(DigitalTwin.Transaction.INTERMEDIATE_COMMIT_ID,execution(Activity.Type.INSTANTIATION).commit()); }

  @Test void satisfactionCheckRejectsUnknownAndAcceptsOnlyCompletedEmptyMembership() {
    assertThrows(IllegalStateException.class,()->runtime.classificationMembersForCheck(producer,producer.getGeometry(),scope));
    complete();
    assertTrue(runtime.classificationMembersForCheck(producer,producer.getGeometry(),scope).isEmpty());
    assertTrue(root.commit()>0);
  }
  @Test void satisfactionCheckCapturesPersistedMembershipOnRootCommitTransaction() {
    cohort.setId(10); complete();
    var read=new KnowledgeGraph.ClassificationReadSet(scope.getId(),10,cohort.getObservable().getUrn(),List.of());
    when(storage.captureClassificationMembers(10)).thenReturn(read);
    assertTrue(runtime.classificationMembersForCheck(producer,producer.getGeometry(),scope).isEmpty());
    assertTrue(root.commit()>0);
    // The earliest baseline and final read-set capture are independent guards, both validated.
    verify(storage,times(2)).validateClassificationMembers(read);
  }
  @Test void satisfactionCaptureRejectsContributorGeometryChangeDuringNativeRead() {
    cohort.setId(10);complete();
    var read=new KnowledgeGraph.ClassificationReadSet(scope.getId(),10,cohort.getObservable().getUrn(),List.of());
    when(storage.captureClassificationMembers(10)).thenAnswer(call->{producer.setGeometry(period(0,5));return read;});
    assertThrows(IllegalStateException.class,()->runtime.classificationMembersForCheck(query,query.getGeometry(),scope));
    verify(storage,never()).store(any());
  }
  @Test void stalePersistedSatisfactionReadAbortsBeforeRootWrites() {
    cohort.setId(10);complete();
    var read=new KnowledgeGraph.ClassificationReadSet(scope.getId(),10,cohort.getObservable().getUrn(),List.of());
    when(storage.captureClassificationMembers(10)).thenReturn(read);
    assertTrue(runtime.classificationMembersForCheck(producer,producer.getGeometry(),scope).isEmpty());
    doThrow(new IllegalStateException("stale membership")).when(storage).validateClassificationMembers(read);
    assertEquals(-1,root.commit());assertTrue(durable.isEmpty());verify(storage,never()).store(any());
  }
  @Test void satisfactionCheckGuardsMembershipThroughRootCommit() {
    complete();
    assertTrue(runtime.classificationMembersForCheck(producer,producer.getGeometry(),scope).isEmpty());
    var member=new ObservationImpl();member.setId(-30);member.setObservable(memberObservable());member.setGeometry(producer.getGeometry());
    root.link(cohort,member,GraphModel.Relationship.HAS_MEMBER);
    assertEquals(-1,root.commit()); assertTrue(durable.isEmpty());
  }
  @Test void satisfactionCheckGuardsExistingMemberMeaningAndIdentity() {
    var member=new ObservationImpl();member.setId(-30);member.setObservable(memberObservable());member.setGeometry(producer.getGeometry());
    root.link(cohort,member,GraphModel.Relationship.HAS_MEMBER);complete();
    var original=member.getObservable();
    var members=runtime.classificationMembersForCheck(producer,producer.getGeometry(),scope);
    assertEquals(1,members.size());assertEquals(member.getId(),members.getFirst().getId());assertSame(original,member.getObservable());
    member.getMetadata().put("foreign",true);
    assertEquals(-1,root.commit());assertTrue(durable.isEmpty());
  }
  @Test void registeredContributorGeometryAndCommitMetadataAreNotCompletion() {
    producer.getMetadata().put(Metadata.IM_COMMIT_ID,900L);
    assertTrue(runtime.queryCollective(query,scope).isEmpty());
    assertFalse(RuntimeService.completedCollective(producer,scope));
    assertFalse(root.isFailed());
  }
  @Test void consolidatedDiagnosticRecordsActualCompletionAndUnsupportedProducerGate() throws Exception {
    var method=RuntimeService.class.getDeclaredMethod("completedCollectiveEvidence",Observation.class,ServiceContextScope.class,
        CollectiveReductionBinding.class,org.integratedmodelling.klab.api.services.runtime.objects.MemberQualityCollection.class,
        CollectiveSelectionBinding.class,List.class);method.setAccessible(true);
    complete();var checks=new ArrayList<Object>();
    assertFalse(((List<?>)method.invoke(null,producer,scope,null,null,null,checks)).isEmpty());
    assertTrue(checks.stream().filter(Map.class::isInstance).map(Map.class::cast).anyMatch(row->Boolean.TRUE.equals(row.get("accepted"))&&"supportIntersection".equals(row.get("gate"))));
    ((ObservableImpl)producer.getObservable()).setDescriptionType(Contextualization.QUANTIFICATION);
    checks.clear();assertTrue(((List<?>)method.invoke(null,producer,scope,null,null,null,checks)).isEmpty());
    assertEquals("contextualization",((Map<?,?>)checks.getFirst()).get("gate"));
    verify(storage,never()).store(any());
  }

  @Test void successfulEmptyDiscoveryBecomesQueryableWithoutFabricatingMembers() {
    complete();
    var result=runtime.queryCollective(query,scope);
    assertFalse(result.isEmpty()); assertEquals(0,result.getChildrenCount());
    assertEquals(1.0,result.getMetadata().get(Metadata.IM_QUERY_COVERAGE,Number.class).doubleValue());
    assertTrue(root.outgoing(cohort).stream().noneMatch(link->link.type()==GraphModel.Relationship.HAS_MEMBER));
    assertNull(result.getValue());
  }
  @Test void nonemptyCompletionRetainsActualMemberIdentity() {
    var observable=new ObservableImpl(); observable.setSemantics(producer.getObservable().getSemantics().singular());
    observable.setUrn("test:Stream"); observable.setDescriptionType(Contextualization.ACKNOWLEDGEMENT);
    var member=new ObservationImpl(); member.setId(-30); member.setObservable(observable); member.setGeometry(producer.getGeometry());
    root.link(cohort,member,GraphModel.Relationship.HAS_MEMBER); producer.setChildrenCount(1); complete();
    assertEquals(1,runtime.queryCollective(query,scope).getChildrenCount());
    assertSame(member,root.outgoing(cohort).stream().filter(link->link.type()==GraphModel.Relationship.HAS_MEMBER).findFirst().orElseThrow().target());
  }
  @Test void unresolvedFailedAndWrongActivityCannotSupplyCompletion() {
    var activity=execution(Activity.Type.INSTANTIATION);
    assertTrue(runtime.queryCollective(query,scope).isEmpty());
    activity.fail(new java.util.concurrent.CancellationException("cancelled"));
    assertTrue(root.isFailed()); assertTrue(runtime.queryCollective(query,scope).isEmpty());
  }
  @Test void resolutionSuccessIsNotInstantiationSuccess() {
    assertEquals(DigitalTwin.Transaction.INTERMEDIATE_COMMIT_ID,execution(Activity.Type.RESOLUTION).commit());
    assertTrue(runtime.queryCollective(query,scope).isEmpty());
  }
  @Test void connectionRequiresItsOwnTypedCompletionEffect() {
    var observable=(ObservableImpl)producer.getObservable(); observable.setDescriptionType(Contextualization.CONNECTION);
    var semantics=(ConceptImpl)observable.getSemantics();
    semantics.setType(EnumSet.of(SemanticType.RELATIONSHIP,SemanticType.COUNTABLE,SemanticType.OBSERVABLE));
    semantics.setDescriptionType(Contextualization.CONNECTION);
    assertEquals(DigitalTwin.Transaction.INTERMEDIATE_COMMIT_ID,execution(Activity.Type.INSTANTIATION).commit());
    assertTrue(runtime.queryCollective(query,scope).isEmpty());
    assertEquals(DigitalTwin.Transaction.INTERMEDIATE_COMMIT_ID,execution(Activity.Type.CONNECTION).commit());
    assertFalse(runtime.queryCollective(query,scope).isEmpty());
  }
  @Test void successfulProducerCannotBeReusedAfterSiblingFailure() {
    complete(); assertFalse(runtime.queryCollective(query,scope).isEmpty());
    root.getChild(Activity.of(Activity.Type.RESOLUTION),scope).fail(new IllegalStateException("sibling failed"));
    assertTrue(root.isFailed()); assertTrue(runtime.queryCollective(query,scope).isEmpty());
    assertEquals(-1,root.commit()); assertTrue(durable.isEmpty());
  }
  @Test void successfulCompletionSurvivesCommitAndRecoveredActivityTransport() {
    complete(); assertTrue(root.commit()>0); assertFalse(root.isFailed());
    when(scope.getCurrentTransaction()).thenReturn(null);
    var result=runtime.queryCollective(query,scope);
    assertFalse(result.isEmpty()); assertEquals(0,result.getChildrenCount());
    assertEquals(List.of(producer.getId()),result.getMetadata().get(Metadata.IM_QUERY_SOURCE_IDS));
    assertTrue(durable.values().stream().anyMatch(asset->asset instanceof Activity activity
        && activity.getType()==Activity.Type.INSTANTIATION && activity.getOutcome()==Activity.Outcome.SUCCESS));
  }
  @Test void failedDurableCloseCannotPublishCompletionDespiteAssignedIds() throws Exception {
    complete(); doThrow(new IllegalStateException("durable close failed")).when(storage).close();
    assertEquals(-1,root.commit()); assertTrue(root.isFailed()); assertTrue(producer.getId()>0);
    assertNotNull(producer.getMetadata().get(Metadata.IM_COMMIT_ID));
    assertTrue(runtime.queryCollective(query,scope).isEmpty());
    when(scope.getCurrentTransaction()).thenReturn(null);
    assertTrue(runtime.queryCollective(query,scope).isEmpty()); assertTrue(durable.isEmpty());
  }
  @Test void legacyPersistedContributorWithoutSuccessfulOutcomeRemainsUnproven() {
    complete(); assertTrue(root.commit()>0); when(scope.getCurrentTransaction()).thenReturn(null);
    for(var asset:durable.values()) if(asset instanceof ActivityImpl activity) activity.setOutcome(null);
    assertTrue(runtime.queryCollective(query,scope).isEmpty());
  }
  @Test void successfulLegacyActivityWithoutInvocationSupportRemainsUnproven() {
    complete(); assertTrue(root.commit()>0); when(scope.getCurrentTransaction()).thenReturn(null);
    for(var asset:durable.values()) if(asset instanceof Activity activity)
      activity.getMetadata().remove(Metadata.IM_EXECUTION_GEOMETRY);
    assertTrue(runtime.queryCollective(query,scope).isEmpty());
  }
  @Test void oneSuccessfulPeriodDoesNotCompleteTheProducersWholeRegisteredExtent() {
    execution(Activity.Type.INSTANTIATION,producer,period(0,2)).commit();
    var result=runtime.queryCollective(query,scope);
    assertFalse(result.isEmpty()); assertEquals(0.2,result.getMetadata().get(Metadata.IM_QUERY_COVERAGE,Number.class).doubleValue(),1e-9);
    assertEquals(0,result.getChildrenCount());
  }
  @Test void adjacentSuccessfulPeriodsCompleteOneRequestedSupportWithoutNewWork() {
    execution(Activity.Type.INSTANTIATION,producer,period(0,5)).commit();
    execution(Activity.Type.INSTANTIATION,producer,period(5,10)).commit();
    var before=root.assets().size();var result=runtime.queryCollective(query,scope);
    assertEquals(1.0,result.getMetadata().get(Metadata.IM_QUERY_COVERAGE,Number.class).doubleValue(),1e-9);
    assertEquals(List.of(producer.getId()),result.getMetadata().get(Metadata.IM_QUERY_SOURCE_IDS));
    assertEquals(before,root.assets().size());verify(storage,never()).store(any());
  }
  @Test void complementarySpatialProducersExposeBothExistingAndNewMembers() {
    var whole=spaceTime(0,10,0,2);var left=spaceTime(0,10,0,1);var right=spaceTime(0,10,1,2);
    producer.setGeometry(left);query.setGeometry(whole);cohort.setGeometry(whole);
    var other=new ObservationImpl();other.setId(-11);other.setObservable(producer.getObservable());other.setGeometry(right);
    root.link(other,cohort,GraphModel.Relationship.CONTRIBUTED_TO);
    var first=new ObservationImpl();first.setId(41);first.setObservable(memberObservable());first.setGeometry(left);
    var second=new ObservationImpl();second.setId(-30);second.setObservable(memberObservable());second.setGeometry(right);
    root.link(cohort,first,GraphModel.Relationship.HAS_MEMBER);root.link(cohort,second,GraphModel.Relationship.HAS_MEMBER);
    execution(Activity.Type.INSTANTIATION,producer,left).commit();
    var fragment=runtime.queryCollective(query,scope);
    assertEquals(List.of(-10L),fragment.getMetadata().get(Metadata.IM_QUERY_SOURCE_IDS));
    assertThrows(IllegalStateException.class,()->runtime.classificationMembers(producer,whole,scope));
    execution(Activity.Type.INSTANTIATION,other,right).commit();
    var result=runtime.completedCohortReference(query,scope);
    assertEquals(Set.of(-10L,-11L),new HashSet<>((List<Long>)result.getMetadata().get(Metadata.IM_QUERY_SOURCE_IDS)));
    assertEquals(2,result.getChildrenCount());
    assertEquals(Set.of(41L,-30L),runtime.classificationMembersForCheck(fragment,whole,scope).stream()
        .map(Observation::getId).collect(java.util.stream.Collectors.toSet()));
    assertEquals(Set.of(41L,-30L),runtime.classificationMembers(producer,whole,scope).stream().map(Observation::getId).collect(java.util.stream.Collectors.toSet()));
    assertEquals(41,first.getId());assertEquals(-30,second.getId());verify(storage,never()).store(any());
    other.setGeometry(left); // Invalidate a real contributor rather than the detached query view.
    assertEquals(-1,root.commit());assertTrue(durable.isEmpty());
  }
  @Test void replayOfPartialQueryPreservesItsCoverageWarningWithoutAccumulation() {
    execution(Activity.Type.INSTANTIATION,producer,period(0,2)).commit();
    var first=runtime.queryCollective(query,scope);
    var replay=(ObservationImpl)Observation.forTransport(first);
    replay.setGeometry(Geometry.create(first.getMetadata().get(Metadata.IM_QUERY_GEOMETRY,String.class)));
    var checked=runtime.queryCollective(replay,scope);
    assertEquals(first.getGeometry().encode(),checked.getGeometry().encode());
    assertEquals(first.getChildrenCount(),checked.getChildrenCount());
    assertEquals(first.getResolvedCoverage(),checked.getResolvedCoverage());
    assertEquals(first.getMetadata().get(Metadata.IM_QUERY_GEOMETRY),checked.getMetadata().get(Metadata.IM_QUERY_GEOMETRY));
    assertEquals(1,first.getNotifications().size());
    assertEquals(1,checked.getNotifications().size(),"Replay must not append a second timestamped coverage warning");
    // This fixture isolates the payload contract from its intentionally unaudited source proof.
    first.getMetadata().remove(SemanticExecutionEvidence.DATA_KEY);
    checked.getMetadata().remove(SemanticExecutionEvidence.DATA_KEY);
    assertTrue(SemanticExecutionEvidence.sameCheckedPayload(first,checked));
  }

  @Test void disjointPeriodsForOneProducerDoNotFabricateTheirTemporalHull() {
    execution(Activity.Type.INSTANTIATION,producer,period(0,2)).commit();
    execution(Activity.Type.INSTANTIATION,producer,period(8,10)).commit();
    assertEquals(0.2,runtime.queryCollective(query,scope).getMetadata().get(Metadata.IM_QUERY_COVERAGE,Number.class).doubleValue(),1e-9);
  }
  @Test void disjointProducersDoNotFabricateCombinedDiscoverySupport() {
    execution(Activity.Type.INSTANTIATION,producer,period(0,2)).commit();
    var other=new ObservationImpl(); other.setId(-11); other.setObservable(producer.getObservable()); other.setGeometry(producer.getGeometry());
    root.link(other,cohort,GraphModel.Relationship.CONTRIBUTED_TO);
    execution(Activity.Type.INSTANTIATION,other,period(8,10)).commit();
    var result=runtime.queryCollective(query,scope);
    assertEquals(0.2,result.getMetadata().get(Metadata.IM_QUERY_COVERAGE,Number.class).doubleValue(),1e-9);
    assertEquals(1,((List<?>)result.getMetadata().get(Metadata.IM_QUERY_SOURCE_IDS)).size());
  }
  @Test void differentSpaceTimeFragmentsDoNotBecomeACartesianProduct() {
    var whole=spaceTime(0,10,0,2); producer.setGeometry(whole); query.setGeometry(whole);
    execution(Activity.Type.INSTANTIATION,producer,spaceTime(0,5,0,1)).commit();
    execution(Activity.Type.INSTANTIATION,producer,spaceTime(5,10,1,2)).commit();
    var result=runtime.queryCollective(query,scope);
    assertFalse(result.isEmpty());
    assertTrue(result.getMetadata().get(Metadata.IM_QUERY_COVERAGE,Number.class).doubleValue()<0.5);
  }
  @Test void invocationCannotAcquireMissingDimensionsFromRegisteredProducerSupport() {
    var whole=spaceTime(0,10,0,2); producer.setGeometry(whole); query.setGeometry(whole);
    execution(Activity.Type.INSTANTIATION,producer,period(0,10)).commit();
    assertTrue(runtime.queryCollective(query,scope).isEmpty());
  }
  @Test void queryCannotAcquireMissingDimensionsFromACompletedFragment() {
    complete(); query.setGeometry(spaceTime(0,10,0,2));
    assertTrue(runtime.queryCollective(query,scope).isEmpty());
  }
  @Test void malformedInvocationSupportDoesNotBecomeSuccessfulEmptyDiscovery() {
    var attempt=execution(Activity.Type.INSTANTIATION);
    attempt.getActivity().getMetadata().put(Metadata.IM_EXECUTION_GEOMETRY,"invalid-geometry");
    attempt.commit(); assertTrue(runtime.queryCollective(query,scope).isEmpty());
  }
  Geometry period(long start,long end) {
    return Geometry.create("T0(1){tstart="+start+",tend="+end+",ttype=PHYSICAL}");
  }
  Geometry spaceTime(long start,long end,int west,int east) {
    var shape=org.integratedmodelling.klab.runtime.scale.space.ShapeImpl.create("EPSG:4326 POLYGON (("
        +west+" 0,"+west+" 1,"+east+" 1,"+east+" 0,"+west+" 0))");
    return Geometry.create(period(start,end).encode()+"S2(1,1){proj=EPSG:4326,shape="+shape.asWKB()+"}");
  }
}
