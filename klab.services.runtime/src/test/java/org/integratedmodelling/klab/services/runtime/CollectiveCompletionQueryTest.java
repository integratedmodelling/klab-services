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
    var observable=new ObservableImpl(); observable.setSemantics(semantics);
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
    when(graph.query(Observation.class,scope)).thenReturn(observations);
    when(observations.run(scope)).thenAnswer(call->durableLinks.stream()
        .filter(link->link.get(2)==GraphModel.Relationship.CONTRIBUTED_TO)
        .map(link->(Observation)durable.get(((RuntimeAsset)link.getFirst()).getId())).toList());
    @SuppressWarnings("unchecked") var activities=(KnowledgeGraph.Query<Activity>)mock(KnowledgeGraph.Query.class,RETURNS_SELF);
    when(graph.query(Activity.class,scope)).thenReturn(activities);
    var target=new Observation[1]; var effect=new GraphModel.Relationship[1];
    when(activities.target(any())).thenAnswer(call->{target[0]=call.getArgument(0);return activities;});
    when(activities.along(any(),any(Object[].class))).thenAnswer(call->{effect[0]=call.getArgument(0);return activities;});
    when(activities.run(scope)).thenAnswer(call->durableLinks.stream()
        .filter(link->link.get(2)==effect[0] && ((RuntimeAsset)link.get(1)).getId()==target[0].getId())
        .map(link->(Activity)durable.get(((RuntimeAsset)link.getFirst()).getId())).toList());
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

  @Test void registeredContributorGeometryAndCommitMetadataAreNotCompletion() {
    producer.getMetadata().put(Metadata.IM_COMMIT_ID,900L);
    assertTrue(runtime.queryCollective(query,scope).isEmpty());
    assertFalse(RuntimeService.completedCollective(producer,scope));
    assertFalse(root.isFailed());
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
    return Geometry.create(period(start,end).encode()+"S2(1,1){proj=EPSG:4326,shape=EPSG:4326 POLYGON (("
        +west+" 0&comma;"+west+" 1&comma;"+east+" 1&comma;"+east+" 0&comma;"+west+" 0))}");
  }
}
