package org.integratedmodelling.klab.services.runtime.neo4j;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.*;
import com.google.common.cache.CacheBuilder;
import org.integratedmodelling.common.knowledge.*;
import org.integratedmodelling.common.runtime.ActuatorImpl;
import org.integratedmodelling.common.lang.ServiceCallImpl;
import org.integratedmodelling.klab.api.configuration.*;
import org.integratedmodelling.klab.api.data.*;
import org.integratedmodelling.klab.api.digitaltwin.*;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.identities.UserIdentity;
import org.integratedmodelling.klab.api.knowledge.*;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.provenance.Activity;
import org.integratedmodelling.klab.api.services.*;
import org.integratedmodelling.klab.api.services.reasoner.objects.*;
import org.integratedmodelling.klab.api.services.runtime.Actuator;
import org.integratedmodelling.klab.components.ComponentRegistry;
import org.integratedmodelling.klab.runtime.kactors.TestCaseBase;
import org.integratedmodelling.klab.runtime.language.ModelValidationGate;
import org.integratedmodelling.klab.services.runtime.*;
import org.integratedmodelling.klab.services.runtime.digitaltwin.*;
import org.integratedmodelling.klab.services.scopes.*;
import org.integratedmodelling.klab.utilities.Utils;
import org.junit.jupiter.api.*;

/** Controlled semantic/model-admission collaborators; actual permitted builtin producer,
 * compiled execution, ordinary member submissions, native graph and private query issuance.
 * No completion metadata, issued query or private execution/membership receipt is fabricated. */
class ModeledMembershipCountTest {
 static class CapturingScope extends ServiceContextScope {
  final List<Throwable> failures=new ArrayList<>();
  CapturingScope(ServiceSessionScope parent,DigitalTwin.Configuration configuration,UserIdentity user){super(parent,configuration,user);}
  @Override public void error(Object... messages){if(failures!=null)for(var value:messages)if(value instanceof Throwable failure)failures.add(failure);super.error(messages);}
 }
 static class Graph extends ModeledCessationPersistenceTest.Graph {
  Graph(){super("a");}
  void bind(org.integratedmodelling.klab.api.scope.UserScope scope){userScope=scope;serviceId="runtime";klab=getOrCreateAgent("k.LAB","AI");user=getOrCreateAgent(scope.getUser().getUsername(),"USER");}
 }
 @BeforeAll static void start(){org.integratedmodelling.klab.configuration.ServiceConfiguration.injectInstantiators();ClassificationPersistenceTest.start();}
 @AfterAll static void stop(){ClassificationPersistenceTest.stop();}
 static void admission(ActuatorImpl plan,String name) {
  var model=new ModelImpl();model.setUrn(name);model.setNamespace("fixture");model.getObservables().add(plan.getObservation().getObservable());
  var snapshot=ModelValidationGate.snapshot(model);var terms=ModelValidationGate.declaredSemanticObligations(snapshot);
  var request=new SemanticDependencyRequest("reasoner",1,ModeledMembershipCommitTest.BASE,terms);
  var receipt=new ModelValidationGate.AdmissionReceipt(name,"fixture","1","b".repeat(64),snapshot.executableHash(),"reasoner",1,List.of(),ModeledMembershipCommitTest.BASE,terms,true,ModeledMembershipCommitTest.witness(request),"session");
  plan.getData().put(Actuator.PRODUCING_MODELS_KEY,List.of(name));plan.getData().put("modelAdmissionRevision",1L);plan.getData().put("modelAdmissionReasonerId","reasoner");plan.getData().put(ModelValidationGate.PRODUCER_ADMISSIONS,List.of(Utils.Json.asString(receipt)));plan.getData().put(ModelValidationGate.PRODUCER_SNAPSHOTS,List.of(Utils.Json.asString(snapshot)));
 }
 @Test void actualPermittedDiscoveryProducesTheCompletedNativeCohortNeededForMembershipCount()throws Exception { exercise(true); }
 @Test void unexecutedMemberCannotGainCessationAuthority()throws Exception { exercise(false); }
 private void exercise(boolean initializeMember)throws Exception {
  ClassificationPersistenceTest.rows("MATCH(n) DETACH DELETE n",Map.of());ClassificationPersistenceTest.rows("CREATE(c:Context{id:'a'}),(p:Provenance{id:'a.PROVENANCE'}),(d:Dataflow{id:'a.DATAFLOW'}) CREATE(c)-[:HAS_PROVENANCE]->(p),(c)-[:HAS_DATAFLOW]->(d)",Map.of());
  var graph=new Graph();
  var runtime=mock(org.integratedmodelling.klab.services.runtime.RuntimeService.class,CALLS_REAL_METHODS);
  var inFlight=org.integratedmodelling.klab.services.runtime.RuntimeService.class.getDeclaredField("inFlightSubmissions");inFlight.setAccessible(true);inFlight.set(runtime,new java.util.concurrent.ConcurrentHashMap<>());
  var settings=mock(Settings.class);doAnswer(c->c.getArgument(0,Setting.class).defaultValue).when(settings).get(any(),any());doReturn(settings).when(runtime).settings();doReturn("runtime").when(runtime).serviceId();doReturn(KlabService.Type.RUNTIME).when(runtime).serviceType();doReturn(java.net.URI.create("http://localhost:1").toURL()).when(runtime).getUrl();
  var nativeSettings=org.integratedmodelling.klab.services.base.BaseService.class.getDeclaredField("settings");nativeSettings.setAccessible(true);nativeSettings.set(runtime,settings);
  var status=mock(KlabService.ServiceStatus.class);when(status.isOperational()).thenReturn(true);doReturn(status).when(runtime).status();
  var user=mock(UserIdentity.class);when(user.getUsername()).thenReturn("tester");when(user.getId()).thenReturn("tester");
  var scope=new CapturingScope(new ServiceSessionScope(new ServiceUserScope(user,runtime)),DigitalTwin.Configuration.builder().id("a").name("isolated").persistence(org.integratedmodelling.klab.api.scope.Persistence.EXPLICIT_ACTION).build(),user);scope.setId("a");
  var reasoner=mock(Reasoner.class);when(reasoner.status()).thenReturn(status);scope.addService(reasoner);scope.addService(runtime);
  graph.bind(scope);
  when(reasoner.beginSemanticCycleResult(anyString(),anyString(),anyString(),any())).thenReturn(new SemanticCycleBeginResult(SemanticCycleBeginResult.Status.BOUND,new SemanticCycleBinding(SemanticCycleBinding.VERSION,"reasoner","session",1,ModeledMembershipCommitTest.BASE,"lease"),null));when(reasoner.bindSemanticCycle(any(),anyString(),anyString(),anyString(),any())).thenReturn(reasoner);
  var caps=mock(Reasoner.Capabilities.class);when(reasoner.capabilities(any())).thenReturn(caps);when(caps.getServiceId()).thenReturn("reasoner");when(caps.getKnowledgeRevision()).thenReturn(1L);when(caps.getSemanticSessionId()).thenReturn("session");when(caps.getSemanticContentFingerprint()).thenReturn(ModeledMembershipCommitTest.BASE);when(caps.isConsistent()).thenReturn(true);when(reasoner.semanticDependencies(any(),any())).thenAnswer(c->ModeledMembershipCommitTest.witness(c.getArgument(0)));
  var tree=ModeledMembershipCommitTest.observation(-8,"test:Tree",Contextualization.ACKNOWLEDGEMENT).getObservable();
  var producer=ModeledMembershipCommitTest.observation(-7,"each test:Tree",Contextualization.INSTANTIATION);((ConceptImpl)producer.getObservable().getSemantics()).setCollective(true);
  var controller=ModeledMembershipCommitTest.observation(-9,"test:Controller",Contextualization.ACKNOWLEDGEMENT);
  var clock=ModeledMembershipCommitTest.observation(-30,"test:Clock",Contextualization.SIMULATION);((ConceptImpl)clock.getObservable().getSemantics()).setType(EnumSet.of(SemanticType.PROCESS,SemanticType.OBSERVABLE));((ObservableImpl)clock.getObservable()).setArtifactType(Artifact.Type.PROCESS);clock.setGeometry(Geometry.create("T1(1){tstart=1000,tend=2000,ttype=GRID,tscope=1,tunit=SECOND}"));
  var count=ModeledMembershipCommitTest.observation(-20,"count of each test:Tree",Contextualization.QUANTIFICATION);((ConceptImpl)count.getObservable().getSemantics()).setType(EnumSet.of(SemanticType.QUALITY,SemanticType.NUMEROSITY,SemanticType.OBSERVABLE));((ConceptImpl)count.getObservable().getSemantics()).setReferenceName("tree_count");((ObservableImpl)count.getObservable()).setArtifactType(Artifact.Type.NUMBER);var data=new org.integratedmodelling.klab.api.knowledge.observation.impl.ObservationImpl.ContextualizationDataImpl();data.setNativeShardingStrategy(new Data.ShardingStrategy(Data.FillCurve.D1_LINEAR,1,0,0,Storage.Type.DOUBLE));count.setContextualizationData(data);
  var support=Geometry.create("t0(1){tstart=1000,tend=2000,ttype=PHYSICAL}");producer.setGeometry(support);controller.setGeometry(org.integratedmodelling.common.knowledge.GeometryRepository.INSTANCE.sanitize(support));count.setGeometry(support);
  when(reasoner.resolveObservable(anyString())).thenAnswer(c->switch(c.getArgument(0,String.class)){case "test:Tree"->tree;case "each test:Tree"->producer.getObservable();case "test:Controller"->controller.getObservable();case "test:Clock"->clock.getObservable();case "count of each test:Tree"->count.getObservable();default->throw new IllegalArgumentException("Unexpected controlled meaning");});
  when(reasoner.splitOperators(count.getObservable())).thenReturn(org.integratedmodelling.klab.api.collections.Pair.of(tree.getSemantics(),List.of(SemanticType.NUMEROSITY)));
  when(reasoner.is(any(),any())).thenAnswer(c->c.getArgument(0,Semantics.class).getUrn().equals(c.getArgument(1,Semantics.class).getUrn()));
  when(reasoner.buildObservable(any(),any())).thenReturn(tree);when(reasoner.baseSubstantialType(any(),any())).thenReturn(tree.getSemantics());
  var resolver=mock(Resolver.class);when(resolver.status()).thenReturn(status);scope.addService(resolver);
  when(resolver.resolve(any(),any())).thenAnswer(c->{
   var target=c.getArgument(0,Observation.class);var ack=new ActuatorImpl();ack.setId(target.getId());ack.setName("acknowledge-fixed-member");ack.setObservation(target);ack.setActuatorType(Actuator.Type.RESOLVE);ack.setExecutionRole(Actuator.ExecutionRole.INITIALIZATION);
   if(initializeMember){ack.getComputation().add(new ServiceCallImpl("klab.tests.fixtures.empty"));admission(ack,"fixture.member-acknowledgement");}
   var dataflow=new org.integratedmodelling.common.runtime.DataflowImpl();dataflow.setCoverage(target.getGeometry());dataflow.setRequirements(org.integratedmodelling.klab.api.services.resources.ResourceSet.empty());dataflow.setResolvedCoverage(1);dataflow.setResolutionOutcome(org.integratedmodelling.klab.api.services.runtime.Dataflow.ResolutionOutcome.RESOLVED);dataflow.getComputation().add(ack);
   return java.util.concurrent.CompletableFuture.completedFuture(dataflow);
  });
  var twin=mock(DigitalTwinImpl.class,CALLS_REAL_METHODS);var cycles=new ResolutionCycles(runtime,"a");ModeledMembershipCommitTest.inject(twin,"knowledgeGraph",graph);ModeledMembershipCommitTest.inject(twin,"commitCache",CacheBuilder.newBuilder().build());ModeledMembershipCommitTest.inject(twin,"resolutionCycles",cycles);scope.setDigitalTwin(twin);
  doReturn(scope.getConfiguration()).when(twin).getOptions();
  var scheduler=spy(new org.integratedmodelling.klab.services.runtime.digitaltwin.scheduler.SchedulerImpl(scope,twin));ModeledMembershipCommitTest.inject(twin,"scheduler",scheduler);
  var membershipOutcomes=new ArrayList<Scheduler.MembershipPropagationOutcome>();
  doAnswer(c->{var source=c.getArgument(1,ServiceContextScope.class); var check=source.forConsequence(null).of(count); var ctx=check.getContextObservation(); System.out.println("CALLBACK_CONTEXT id="+(ctx==null?null:ctx.getId())+" urn="+(ctx==null?null:ctx.getUrn())+" meaning="+(ctx==null?null:ctx.getObservable().getUrn())+" support="+(ctx==null?null:Geometry.forTransport(ctx.getGeometry()).encode())+" events="+(ctx==null?null:ctx.getEventTimestamps())+" eligible="+org.integratedmodelling.klab.api.knowledge.SingularContextBinding.eligible(ctx));var actual=(Scheduler.MembershipPropagationOutcome)c.callRealMethod();membershipOutcomes.add(actual);return actual;}).when(scheduler).membershipChanged(any(),any());
  var directory=java.nio.file.Files.createTempDirectory("membership-count-controlled-");
  MembershipCountInvocation.scalar(runtime);
  var store=org.integratedmodelling.klab.runtime.storage.MembershipStorageFactory.open(scope,directory.resolve("storage"));doReturn(store).when(twin).getStorageManager();
  var registry=ComponentRegistry.ownedBootstrap(runtime,mock(org.integratedmodelling.klab.api.engine.StartupOptions.class),directory.resolve("components"));doReturn(registry).when(runtime).getComponentRegistry();registry.loadExtensions("org.integratedmodelling.klab.runtime.libraries");
  var agent=mock(TestCaseBase.class);when(agent.status()).thenReturn("running");var owner=new TestCaseBase.TestCaseScope(agent,null,null);owner.registerContext(scope);
  try {
   assertTrue(org.integratedmodelling.klab.runtime.libraries.CoreActorLibrary.Inspector.fixtures(owner,scope));
   var cohort=new CohortImpl();cohort.setId(-10);cohort.setObservable(tree);cohort.setGeometry(support);
   try(var tx=graph.createTransaction(scope)){tx.store(controller);tx.store(cohort);tx.link(RuntimeAsset.CONTEXT_ASSET,controller,GraphModel.Relationship.HAS_CHILD);tx.link(RuntimeAsset.CONTEXT_ASSET,cohort,GraphModel.Relationship.HAS_CHILD);}
   Observation initializedController;
   try(var admitted=cycles.admit(scope,true).get(5,java.util.concurrent.TimeUnit.SECONDS)) {
    var executing=admitted.scope().executing(Activity.of(Activity.Type.SUBMISSION),controller);org.integratedmodelling.common.runtime.ObserverProvenance.recordSelection(executing.getActivity(),admitted.scope().getObserver());
    var plan=new ActuatorImpl();plan.setId(controller.getId());plan.setName("initialize-controller");plan.setObservation(controller);plan.setActuatorType(Actuator.Type.RESOLVE);plan.setExecutionRole(Actuator.ExecutionRole.INITIALIZATION);plan.getComputation().add(new ServiceCallImpl("klab.tests.fixtures.empty"));admission(plan,"fixture.controller");
    var compiled=new CompiledDataflow(runtime,controller,executing);assertTrue(compiled.compile(plan));assertTrue(compiled.store((DigitalTwinImpl.TransactionImpl)executing.getCurrentTransaction()));assertTrue(scheduler.submit(controller,executing));assertTrue(executing.commit()>0);
    initializedController=graph.getAsset(controller.getId(),admitted.scope(),Observation.class);
    var fresh=new ModeledCessationPersistenceTest.Graph("a").getAsset(controller.getId(),admitted.scope(),Observation.class);
    System.out.println("CONTROLLER_INIT old="+controller.getEventTimestamps()+" cached="+initializedController.getEventTimestamps()+" fresh="+fresh.getEventTimestamps());
    scope.invalidateObservation(controller.getId()); initializedController=scope.getObservation(controller.getId()); // Reload the manually bootstrapped context through the current authorized cache path.
    assertEquals(Geometry.forTransport(initializedController.getGeometry()).encode(),Geometry.forTransport(fresh.getGeometry()).encode(),"Manual bootstrap must use the same sanitized native context support as Runtime.submit");
    assertFalse(initializedController.getEventTimestamps().isEmpty(),"Actual committed context initialization must be visible to an authorized lookup");
   }
   var focused=scope.within(initializedController);
   long memberId;
   try(var admitted=cycles.admit(focused,true).get(5,java.util.concurrent.TimeUnit.SECONDS)) {
    var executing=admitted.scope().executing(Activity.of(Activity.Type.SUBMISSION),producer);org.integratedmodelling.common.runtime.ObserverProvenance.recordSelection(executing.getActivity(),admitted.scope().getObserver());executing.getCurrentTransaction().link(executing.getActivity(),producer,GraphModel.Relationship.CREATED);var root=executing.getCurrentTransaction();root.link(controller,producer,GraphModel.Relationship.HAS_CHILD);root.link(producer,cohort,GraphModel.Relationship.CONTRIBUTED_TO);
    var plan=new ActuatorImpl();plan.setId(producer.getId());plan.setName("fixed-discovery");plan.setObservation(producer);plan.setOperationObservable(producer.getObservable());plan.setRequestedSupport(producer.getGeometry());plan.setActuatorType(Actuator.Type.RESOLVE);plan.setExecutionRole(Actuator.ExecutionRole.INITIALIZATION);plan.getComputation().add(new ServiceCallImpl("klab.tests.fixtures.fixed"));admission(plan,"fixture.fixed");
    var compiled=new CompiledDataflow(runtime,producer,executing);assertTrue(compiled.compile(plan),()->String.valueOf(producer.getNotifications()));
    assertTrue(MembershipCountInvocation.execute(compiled,producer.getGeometry(),executing),()->String.valueOf(producer.getNotifications()));
    assertTrue(executing.commit()>0,"Actual scientific discovery root must commit");
    var builder=new Observation.NaiveBuilder(producer.getObservable(),admitted.scope());builder.geometry(producer.getGeometry()).query();var query=builder.make();
    var checked=MembershipCountInvocation.query(runtime,query,admitted.scope());assertFalse(checked.isEmpty());var members=runtime.getMembers(checked,admitted.scope());assertEquals(1,members.size());memberId=members.getFirst().getId();
   }
   count.setParentId(controller.getId());
   clock.setParentId(controller.getId());
   try(var admitted=cycles.admit(focused,true).get(5,java.util.concurrent.TimeUnit.SECONDS)) {
    var executing=admitted.scope().executing(Activity.of(Activity.Type.SUBMISSION),clock);org.integratedmodelling.common.runtime.ObserverProvenance.recordSelection(executing.getActivity(),admitted.scope().getObserver());executing.getCurrentTransaction().link(executing.getActivity(),clock,GraphModel.Relationship.CREATED);executing.getCurrentTransaction().link(controller,clock,GraphModel.Relationship.HAS_CHILD);
    var plan=new ActuatorImpl();plan.setId(clock.getId());plan.setName("establish-modeled-instant");plan.setObservation(clock);plan.setActuatorType(Actuator.Type.RESOLVE);plan.setExecutionRole(Actuator.ExecutionRole.PROCESS);plan.getComputation().add(new ServiceCallImpl("klab.tests.fixtures.empty"));
    plan.getOccurrenceSchedules().put(0,new OccurrenceSchedule(1,"","",1,org.integratedmodelling.klab.api.knowledge.observation.scale.time.Time.Resolution.Type.SECOND,true,OccurrenceSchedule.Source.MODEL));
    plan.getData().put(ProcessPlan.DATA_KEY,Utils.Json.asString(new ProcessPlan(3,controller.getId(),"fixture.clock",List.of(),List.of())));admission(plan,"fixture.clock");
    var compiled=new CompiledDataflow(runtime,clock,executing);assertTrue(compiled.compile(plan));assertTrue(compiled.store((DigitalTwinImpl.TransactionImpl)executing.getCurrentTransaction()));assertTrue(scheduler.submit(clock,executing));assertTrue(executing.commit()>0);
   }
   if(scheduler.currentModeledInstant().isEmpty())throw new AssertionError("Actual clock transition failed",scope.failures.isEmpty()?null:scope.failures.getFirst());
   assertEquals(2000,scheduler.currentModeledInstant().orElseThrow(),"Only an actual committed scheduled transition establishes modeled time");
   try(var admitted=cycles.admit(focused,true).get(5,java.util.concurrent.TimeUnit.SECONDS)) {
    var binding=CollectiveReductionBinding.capture(count,admitted.scope(),reasoner);assertNotNull(binding);System.out.println("EXPECTED_CONTEXT "+binding.context());
    var executing=admitted.scope().executing(Activity.of(Activity.Type.SUBMISSION),count);org.integratedmodelling.common.runtime.ObserverProvenance.recordSelection(executing.getActivity(),admitted.scope().getObserver());executing.getCurrentTransaction().link(executing.getActivity(),count,GraphModel.Relationship.CREATED);executing.getCurrentTransaction().link(controller,count,GraphModel.Relationship.HAS_CHILD);
    var plan=new ActuatorImpl();plan.setId(count.getId());plan.setName("count-current-trees");plan.setObservation(count);plan.setOperationObservable(count.getObservable());plan.setRequestedSupport(count.getGeometry());plan.setActuatorType(Actuator.Type.OBSERVE);plan.setExecutionRole(Actuator.ExecutionRole.INITIALIZATION);plan.getData().put(CollectiveReductionBinding.DATA_KEY,Utils.Json.asString(binding));admission(plan,"fixture.count");
    var compiled=new CompiledDataflow(runtime,count,executing);assertTrue(compiled.compile(plan));assertTrue(compiled.store((DigitalTwinImpl.TransactionImpl)executing.getCurrentTransaction()));
    var counterQuery=new Observation.NaiveBuilder(producer.getObservable(),executing);counterQuery.geometry(count.getGeometry()).query();var view=MembershipCountInvocation.query(runtime,counterQuery.make(),executing,binding);
    System.out.println("COUNT_QUERY_SUPPORT requested="+count.getGeometry().encode()+" actual="+(view.getGeometry()==null?null:view.getGeometry().encode())+" coverage="+view.getMetadata().get(Metadata.IM_QUERY_COVERAGE)+" empty="+view.isEmpty());
    if(!scheduler.submit(count,executing))throw new AssertionError("Controlled COUNT executor failed",MembershipCountInvocation.failure(compiled));assertTrue(executing.commit()>0);
    try(var values=org.integratedmodelling.klab.runtime.storage.StorageReads.open(count,admitted.scope(),null,Data.FillCurve.D1_LINEAR,Storage.DoubleScanner.class)) {var scanner=values.scanners().getFirst();assertTrue(scanner.isValid());assertEquals(1,scanner.get());}
    assertTrue(graph.getLinks(cohort,GraphModel.Relationship.Direction.OUTGOING,admitted.scope(),GraphModel.Relationship.AFFECTS).stream().anyMatch(edge->edge.target().getId()==count.getId()),"Actual COUNT consumption must publish the cohort dependency before cessation propagation is attempted");
   }
   var recovered=focused.forConsequence(null).of(count);
   assertNotNull(recovered.getContextObservation(),"Committed COUNT parent must be recovered before cessation");
   assertEquals(controller.getId(),recovered.getContextObservation().getId());
   assertTrue(org.integratedmodelling.klab.api.knowledge.SingularContextBinding.eligible(recovered.getContextObservation()),"Recovered COUNT context must retain committed initialization");
   var beforeTime=scheduler.currentModeledInstant();
   // The actual registered emitter receives a declared resolved input. No Runtime output interception.
   try(var admitted=cycles.admit(focused,true).get(5,java.util.concurrent.TimeUnit.SECONDS)) {
    var target=admitted.scope().getObservation(clock.getId());var executing=admitted.scope().executing(Activity.of(Activity.Type.SUBMISSION),target);org.integratedmodelling.common.runtime.ObserverProvenance.recordSelection(executing.getActivity(),admitted.scope().getObserver());
    var plan=new ActuatorImpl();plan.setId(target.getId());plan.setName("modeled-member-cessation");plan.setObservation(target);plan.setActuatorType(Actuator.Type.RESOLVE);plan.setExecutionRole(Actuator.ExecutionRole.INITIALIZATION);plan.getComputation().add(new ServiceCallImpl("klab.tests.fixtures.cease"));
    var input=admitted.scope().getObservation(memberId);assertNotNull(input);
    var reference=new ActuatorImpl();reference.setId(input.getId());reference.setName("target");reference.setObservation(input);reference.setRequestedSupport(input.getGeometry());reference.setActuatorType(Actuator.Type.REFERENCE);plan.getChildren().add(reference);
    admission(plan,"fixture.cessation");
    for(var descriptor:registry.getFunctionDescriptor(plan.getComputation().getFirst(),executing)) {var implementation=registry.implementation(descriptor);System.out.println("EMITTER_DESCRIPTOR method="+implementation.method.toGenericString()+" inputs="+descriptor.serviceInfo.listInputs().stream().map(inputInfo->inputInfo.getName()).toList());}
    var compiled=new CompiledDataflow(runtime,target,executing);
    if(!compiled.compile(plan))throw new AssertionError("Emitter compile rejected; notifications="+target.getNotifications(),compiled.getCause());
    assertTrue(compiled.store((DigitalTwinImpl.TransactionImpl)executing.getCurrentTransaction()),"Emitter actual plan/dependencies must be stored before execution");
    boolean executed=MembershipCountInvocation.execute(compiled,target.getGeometry(),executing);
    assertEquals(1,org.integratedmodelling.klab.runtime.libraries.CoreActorLibrary.Inspector.fixturecount(owner,scope,"cessation"),"The actual registered emitter must have run exactly once");
    if(!initializeMember){
      assertFalse(executed,"Unexecuted reference input must remain UNKNOWN and cannot authorize cessation");assertTrue(executing.getCurrentTransaction().isFailed());assertTrue(membershipOutcomes.isEmpty());assertNotNull(scope.getObservation(memberId));assertTrue(graph.getObservationHistory(memberId,scope).isEmpty());assertEquals(1,readWholeCount(count,scope,Scheduler.Event.initialization(),count.getGeometry()));return;
    }
    if(!executed){System.out.println("EMITTER_ACCOUNTING "+MembershipCountInvocation.accounting(executing,input));throw new AssertionError("Emitter execution rejected",MembershipCountInvocation.failure(compiled));}
    assertTrue(executing.commit()>0,"Emitter actual native root commit must succeed");
   }
   try(var next=cycles.admit(focused,true).get(5,java.util.concurrent.TimeUnit.SECONDS)) {
    assertNotNull(next.scope().getResolutionCycleBinding());
    assertNull(next.scope().getObservation(memberId),"Next explicit cycle starts only after cessation and its native callback drain");
   }
   System.out.println("MEMBERSHIP_PROPAGATION "+membershipOutcomes);
   assertEquals(List.of(Scheduler.MembershipPropagationOutcome.APPLIED),membershipOutcomes,"The actual private callback must execute the scalar consumer");
   var currentCount=scope.getObservation(count.getId());
   var readAtCurrentTime=new StorageScan.Slice(Scheduler.Event.Type.INPUT_CHANGE,"read-only-current-membership",2000,2000).event();
   var savedPlan=Utils.Json.parseObject(currentCount.getMetadata().get(Scheduler.PLAN_METADATA_KEY).toString(),org.integratedmodelling.klab.api.services.runtime.Actuator.class);
   var originalSupport=savedPlan.getRequestedSupport();assertNotNull(originalSupport);
   assertEquals(org.integratedmodelling.common.knowledge.GeometryRepository.INSTANCE.scale(originalSupport).encode(),org.integratedmodelling.common.knowledge.GeometryRepository.INSTANCE.scale(currentCount.getGeometry()).encode());
   assertEquals(0,readWholeCount(currentCount,scope,readAtCurrentTime,originalSupport),"Current COUNT must be zero after the committed member cessation");
   assertEquals(1,readWholeCount(currentCount,scope,Scheduler.Event.initialization(),originalSupport),"Earlier COUNT initialization must remain immutable");
   assertEquals(beforeTime,scheduler.currentModeledInstant());assertNotNull(scope.getObservation(controller.getId()));assertNull(scope.getObservation(memberId));assertEquals(1,graph.getObservationHistory(memberId,scope).size());
  } finally {owner.done();registry.closeOwnedBootstrap();store.close();scheduler.close();}
 }
 private static double readWholeCount(Observation current,ServiceContextScope scope,Scheduler.Event event,Geometry support) {
  var storage=scope.getDigitalTwin().getStorageManager().getStorage(current);
  var request=org.integratedmodelling.klab.runtime.storage.StorageReads.request(current,event,new Data.ShardingStrategy(Data.FillCurve.D1_LINEAR,1,0,0,null),List.of(),Storage.DoubleScanner.class,support);
  try(var values=storage.open(storage.plan(request))){var scanner=values.scanners().getFirst();assertTrue(scanner.isValid());return scanner.get();}
 }}
