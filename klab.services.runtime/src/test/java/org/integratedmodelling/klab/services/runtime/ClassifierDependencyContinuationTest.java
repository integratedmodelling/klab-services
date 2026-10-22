package org.integratedmodelling.klab.services.runtime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.integratedmodelling.common.knowledge.*;
import org.integratedmodelling.common.lang.ServiceCallImpl;
import org.integratedmodelling.common.runtime.ActuatorImpl;
import org.integratedmodelling.klab.api.collections.Parameters;
import org.integratedmodelling.klab.api.data.*;
import org.integratedmodelling.klab.api.digitaltwin.*;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.knowledge.*;
import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.api.knowledge.observation.*;
import org.integratedmodelling.klab.api.knowledge.observation.impl.ObservationImpl;
import org.integratedmodelling.klab.api.lang.*;
import org.integratedmodelling.klab.api.lang.kim.*;
import org.integratedmodelling.klab.api.lang.kim.impl.*;
import org.integratedmodelling.klab.api.provenance.Activity;
import org.integratedmodelling.klab.api.scope.*;
import org.integratedmodelling.klab.api.services.*;
import org.integratedmodelling.klab.api.services.runtime.*;
import org.integratedmodelling.klab.api.services.resolver.ResolutionConstraint;
import org.integratedmodelling.klab.components.ComponentRegistry;
import org.integratedmodelling.klab.services.reasoner.ReasonerService;
import org.integratedmodelling.klab.services.resolver.*;
import org.integratedmodelling.klab.services.runtime.digitaltwin.DigitalTwinImpl;
import org.integratedmodelling.klab.services.scopes.ServiceContextScope;
import org.junit.jupiter.api.*;

class ClassifierDependencyContinuationTest {
  @BeforeAll static void scales(){org.integratedmodelling.klab.configuration.ServiceConfiguration.injectInstantiators();}
  static ConceptImpl concept(String urn,SemanticType... types){
    var c=new ConceptImpl();c.setUrn(urn);c.setName(urn);c.setReferenceName(urn.replace(':','_'));
    c.setType(EnumSet.copyOf(List.of(types)));return c;
  }
  static ObservableImpl observable(String urn,SemanticType type){
    var o=new ObservableImpl();o.setSemantics(concept(urn,type));o.setUrn(urn);o.setName(urn);o.setReferenceName(urn);
    o.setDescriptionType(type==SemanticType.QUALITY?Contextualization.QUANTIFICATION:Contextualization.ACKNOWLEDGEMENT);return o;
  }
  public static class Directive extends ObservableImpl {
    public String family;
    public Directive(){}
    Directive(String family){this.family=family;setSemantics(concept(family,SemanticType.PREDICATE,SemanticType.ATTRIBUTE));setUrn(family+" of each test:Region");setDescriptionType(Contextualization.CLASSIFICATION);}
    @Override public Observable.Builder builder(Scope scope){
      var builder=mock(Observable.Builder.class);when(builder.without(SemanticRole.INHERENT)).thenReturn(builder);
      var predicate=scope.getService(Reasoner.class).resolveConcept(family);
      when(builder.buildConcept()).thenReturn(predicate);return builder;
    }
  }
  public static class Member extends ObservableImpl {
    public Member(){setSemantics(concept("test:Region",SemanticType.SUBJECT));setUrn("test:Region");setDescriptionType(Contextualization.ACKNOWLEDGEMENT);}
    @Override public Observable.Builder builder(Scope scope){
      var values=new TreeSet<String>(List.of(getUrn().split(" ")));var builder=mock(Observable.Builder.class);
      when(builder.withTrait(any(Concept.class))).thenAnswer(i->{values.add(((Concept)i.getArgument(0)).getUrn());return builder;});
      when(builder.buildObservable()).thenAnswer(i->{var o=new Member();o.setUrn(String.join(" ",values));return o;});return builder;
    }
  }
  public static class Classifier {
    final AtomicInteger calls=new AtomicInteger();boolean missing;boolean cancel;
    public Concept classify(Observable operation,Observation member,Scope scope){
      calls.incrementAndGet();if(cancel)throw new CancellationException("classifier cancelled");if(missing)return null;
      String family=((Directive)operation).family;return scope.getService(Reasoner.class).resolveConcept(family+(member.getId()==41?"1":"2"));
    }
  }
  static KimConceptImpl syntax(String name,SemanticType... flags){var c=new KimConceptImpl();c.setName(name);c.setType(EnumSet.copyOf(List.of(flags)));return c;}
  static KimConcept declaration(String name){
    if(name.equals("test:Quality"))return syntax(name,SemanticType.QUALITY);
    if(name.equals("test:Region"))return syntax(name,SemanticType.SUBJECT);
    var c=syntax(name,SemanticType.PREDICATE,SemanticType.ATTRIBUTE);if(name.equals("test:A")||name.equals("test:B"))c.getType().add(SemanticType.ABSTRACT);return c;
  }
  static DependencyExpansion.Prepared prepared(){return prepared(bearer->{});}
  static DependencyExpansion.Prepared prepared(java.util.function.Consumer<KimConceptImpl> qualify){
    var head=syntax("test:Quality",SemanticType.QUALITY);var bearer=syntax("test:Region",SemanticType.SUBJECT);
    bearer.getTraits().addAll(List.of(syntax("test:A",SemanticType.PREDICATE,SemanticType.ATTRIBUTE),syntax("test:B",SemanticType.PREDICATE,SemanticType.ATTRIBUTE)));
    qualify.accept(bearer);head.setInherent(bearer);var o=new KimObservableImpl();o.setSemantics(head);o.setUrn("authored");o.setNamespace("test");
    return DependencyExpansion.prepare(o,ClassifierDependencyContinuationTest::declaration);
  }
  static class Fixture implements AutoCloseable {
    final ServiceContextScope scope=mock(ServiceContextScope.class);
    final RuntimeService runtime=mock(RuntimeService.class);
    final ReasonerService reasoner=mock(ReasonerService.class);
    final ResourcesService resources=mock(ResourcesService.class);
    final ResolverService resolver=mock(ResolverService.class,CALLS_REAL_METHODS);
    final ExecutorService executor=Executors.newVirtualThreadPerTaskExecutor();
    final DigitalTwinImpl twin=mock(DigitalTwinImpl.class);
    final org.integratedmodelling.klab.services.runtime.neo4j.KnowledgeGraphNeo4j graph=mock(org.integratedmodelling.klab.services.runtime.neo4j.KnowledgeGraphNeo4j.class);
    final KnowledgeGraph.Transaction nativeTx=mock(KnowledgeGraph.Transaction.class);
    final DigitalTwinImpl.TransactionImpl root;
    final ObservationImpl producer=new ObservationImpl(),context=new ObservationImpl();
    final CohortImpl cohort=new CohortImpl();final Classifier classifier=new Classifier();
    final Geometry support=Geometry.create("T0(1){tstart=0,tend=10,ttype=PHYSICAL}");
    final Scheduler.Event event=Scheduler.Event.initialization();
    final Map<String,Concept> concepts=new HashMap<>();
    final DependencyExpansion.Prepared prepared;
    final Map<DependencyExpansion.RequestIdentity,ClassifierDependencyContinuation.Prerequisite> prerequisites=new LinkedHashMap<>();
    final AtomicLong ids=new AtomicLong(-30),revision=new AtomicLong(3);
    final List<Long> registered=new CopyOnWriteArrayList<>();
    final List<String> resolved=new CopyOnWriteArrayList<>();
    final org.integratedmodelling.klab.api.provenance.impl.ActivityImpl completion;
    Fixture(boolean reused,int memberCount) throws Exception {this(reused,memberCount,prepared());}
    Fixture(boolean reused,int memberCount,DependencyExpansion.Prepared source) throws Exception {
      prepared=source;
      for(String name:List.of("A","A1","A2","B","B1","B2")){
        var c=concept("test:"+name,SemanticType.PREDICATE,SemanticType.ATTRIBUTE);c.setAbstract(name.length()==1);concepts.put(c.getUrn(),c);
      }
      concepts.put("test:Region",concept("test:Region",SemanticType.SUBJECT));
      when(scope.getId()).thenReturn("context");when(scope.getDigitalTwin()).thenReturn(twin);when(twin.getKnowledgeGraph()).thenReturn(graph);
      when(scope.getService(Reasoner.class)).thenReturn(reasoner);when(scope.getService(ResourcesService.class)).thenReturn(resources);
      when(scope.getService(Resolver.class)).thenReturn(resolver);when(scope.getService(org.integratedmodelling.klab.api.services.RuntimeService.class)).thenReturn(runtime);
      when(scope.getService(RuntimeService.class)).thenReturn(runtime);when(scope.within(any())).thenReturn(scope);when(scope.withResolutionConstraints(any(ResolutionConstraint[].class))).thenReturn(scope);
      when(reasoner.resolveConcept(anyString())).thenAnswer(i->concepts.get(i.getArgument(0)));
      when(reasoner.baseSubstantialType(any(),any())).thenReturn(concepts.get("test:Region"));when(reasoner.inherent(any())).thenReturn(concepts.get("test:Region"));
      when(reasoner.satisfiable(any())).thenReturn(true);
      when(reasoner.is(any(),any())).thenAnswer(i->{var a=(Semantics)i.getArgument(0);var b=(Semantics)i.getArgument(1);return a!=null&&b!=null&&(a.getUrn().equals(b.getUrn())||a.getUrn().matches(java.util.regex.Pattern.quote(b.getUrn())+"[12]"));});
      when(reasoner.directTraits(any())).thenAnswer(i->{var o=(Semantics)i.getArgument(0);return Arrays.stream(o.getUrn().split(" ")).map(concepts::get).filter(Objects::nonNull).filter(c->c.is(SemanticType.PREDICATE)).toList();});
      var capabilities=mock(Reasoner.Capabilities.class);when(capabilities.getServiceId()).thenReturn("reasoner");when(capabilities.getKnowledgeRevision()).thenAnswer(i->revision.get());when(reasoner.capabilities(any())).thenReturn(capabilities);
      when(resources.declareConcept(anyString())).thenAnswer(i->declaration(i.getArgument(0)));
      when(reasoner.declareObservable(any(KimObservable.class))).thenAnswer(i->{
        KimObservable branch=i.getArgument(0);String urn=branch.getSemantics().getInherent().getTraits().stream().map(KimConcept::getName).sorted().reduce((a,b)->a+"/"+b).orElse("none");
        var o=observable(urn,SemanticType.QUALITY);o.setOptional(branch.isOptional());concepts.put(urn,o.asConcept());return o;
      });
      context.setId(21);context.setGeometry(support);context.setObservable(observable("test:Region",SemanticType.SUBJECT));when(scope.getContextObservation()).thenReturn(context);
      var collective=observable("each test:Region",SemanticType.SUBJECT);((ConceptImpl)collective.getSemantics()).setCollective(true);collective.setDescriptionType(Contextualization.INSTANTIATION);
      producer.setId(-9);producer.setGeometry(support);producer.setObservable(collective);
      cohort.setId(10);cohort.setObservable(observable("test:Region",SemanticType.SUBJECT));
      when(runtime.getCohortFor(any(),eq(scope),eq(false))).thenReturn(cohort);
      root=twin.new TransactionImpl(Activity.of(Activity.Type.SUBMISSION),scope,RuntimeAsset.PROVENANCE_ASSET);
      when(scope.getCurrentTransaction()).thenReturn(root);when(scope.getActivity()).thenReturn(root.getActivity());root.add(producer);
      root.link(context,producer,GraphModel.Relationship.HAS_CHILD);root.link(producer,cohort,GraphModel.Relationship.CONTRIBUTED_TO);
      completion=Activity.of(Activity.Type.INSTANTIATION);completion.setOutcome(Activity.Outcome.SUCCESS);
      completion.getMetadata().put(Metadata.IM_EXECUTION_GEOMETRY,GeometryRepository.INSTANCE.sanitize(support).encode());
      root.link(completion,producer,GraphModel.Relationship.INSTANTIATED);
      when(graph.createTransaction(scope)).thenReturn(nativeTx);
      when(graph.materializeClassificationMember(any(),eq(scope))).thenAnswer(i->{KnowledgeGraph.ClassificationMemberSnapshot snapshot=i.getArgument(0);var member=new ObservationImpl();member.setId(snapshot.id());member.setObservable(new Member());member.setGeometry(Geometry.create(snapshot.geometry()));return member;});when(nativeTx.captureClassificationMembers(10)).thenReturn(new KnowledgeGraph.ClassificationReadSet("context",10,"test:Region",List.of()));
      for(int n=0;n<memberCount;n++){
        var member=new ObservationImpl();member.setId(41+n);member.setGeometry(support);var meaning=new Member();
        if(reused)meaning.setUrn("test:A"+(n+1)+" test:B"+(n+1)+" test:Region");member.setObservable(meaning);root.link(cohort,member,GraphModel.Relationship.HAS_MEMBER);
      }
      when(scope.executing(any(Activity.class),any(Object[].class))).thenAnswer(i->{
        Activity a=i.getArgument(0);var childScope=mock(ServiceContextScope.class);var child=root.getChild(a,childScope);
        when(childScope.getCurrentTransaction()).thenReturn(child);when(childScope.getService(Reasoner.class)).thenReturn(reasoner);when(childScope.within(any())).thenReturn(childScope);
        when(childScope.commit()).thenAnswer(j->child.commit());return childScope;
      });
      var registry=mock(ComponentRegistry.class);when(runtime.getComponentRegistry()).thenReturn(registry);
      var descriptor=new org.integratedmodelling.klab.api.services.runtime.extension.Extensions.FunctionDescriptor();
      var implementation=new ComponentRegistry.ServiceImplementation();implementation.mainClassInstance=classifier;
      implementation.method=Classifier.class.getMethod("classify",Observable.class,Observation.class,Scope.class);
      when(registry.getFunctionDescriptor(any(ServiceCall.class),any(Scope.class))).thenReturn(List.of(descriptor));when(registry.implementation(descriptor)).thenReturn(implementation);
      for(var request:prepared.requests()){
        var plan=new ActuatorImpl();plan.setActuatorType(Actuator.Type.UPDATE);plan.setEffect(Actuator.Effect.SEMANTIC_UPDATE);plan.setContextualization(Contextualization.CLASSIFICATION);plan.setOperationObservable(new Directive(request.declaration().getName()));
        var binding=new ActuatorImpl.TargetBindingImpl();binding.setKind(Actuator.TargetBinding.Kind.COHORT_MEMBERS);binding.setSources(List.of("members"));plan.getTargetBindings().add(binding);
        var original=reasoner.declareObservable(request.originalObservable());plan.setModelDependency(original);plan.getComputation().add(new ServiceCallImpl("test.classify"));
        prerequisites.put(request.identity(),new ClassifierDependencyContinuation.Prerequisite(plan,producer));
      }
      when(runtime.resolveClassifiedDependencies(any(),anyMap(),any(),any(),eq(scope))).thenCallRealMethod();
      when(runtime.register(any(),eq(scope))).thenAnswer(i->{var o=(ObservationImpl)i.getArgument(0);o.setId(ids.decrementAndGet());o.setParentId(context.getId());registered.add(o.getId());return o;});
      var field=ResolverService.class.getDeclaredField("resolutionExecutor");field.setAccessible(true);field.set(resolver,executor);
      var data=Parameters.<String>create();data.put("__RESOLUTION_GRAPH__",ResolutionGraph.create(scope));when(scope.getData()).thenReturn(data);
      when(scope.getObservation(any(Observation.class))).thenAnswer(i->{Observation query=i.getArgument(0);resolved.add(query.getObservable().getUrn());var o=new ObservationImpl();o.setId(500+Math.abs(query.getObservable().getUrn().hashCode()));o.setObservable(query.getObservable());o.setGeometry(support);return o;});
    }
    CompletionStage<ClassifierDependencyContinuation.Outcome> run(){return runtime.resolveClassifiedDependencies(prepared,prerequisites,support,event,scope);}
    public void close(){executor.shutdownNow();}
  }
  @Test void precomputedOuterTierZeroDoesNotEnterTheClassifierContinuation() throws Exception {
    try(var f=new Fixture(false,2)){
      var query=new ObservationImpl();query.setId(-200);query.setObservable(observable("test:Precomputed",SemanticType.QUALITY));query.setGeometry(f.support);
      var result=f.resolver.resolve(query,f.scope).get(10,TimeUnit.SECONDS);assertFalse(result.isEmpty());
      assertEquals(0,f.classifier.calls.get());verify(f.nativeTx,never()).captureClassificationMembers(anyLong());
      verify(f.runtime,never()).resolveClassifiedDependencies(any(),anyMap(),any(),any(),any());
      verify(f.reasoner,never()).computeObservationStrategies(any(),any());
    }
  }
  @Test void callerOwnedRequestedGeometryCanChangeAfterDispatchWithoutChangingTheFrozenSupport() throws Exception {
    try(var f=new Fixture(true,2)){
      var actual=new ArrayList<CompletableFuture<Dataflow>>();var gates=new ArrayList<CompletableFuture<Dataflow>>();
      doAnswer(i->{actual.add((CompletableFuture<Dataflow>)i.callRealMethod());var gate=new CompletableFuture<Dataflow>();gates.add(gate);return gate;}).when(f.resolver).resolve(any(),eq(f.scope));
      var caller=Geometry.create(f.support.encode());var result=f.runtime.resolveClassifiedDependencies(f.prepared,f.prerequisites,caller,f.event,f.scope).toCompletableFuture();
      ((org.integratedmodelling.klab.api.geometry.impl.GeometryImpl)caller).setDimensions(new ArrayList<>());
      for(int n=0;n<gates.size();n++)gates.get(n).complete(actual.get(n).get(10,TimeUnit.SECONDS));
      assertEquals(DependencyExpansion.Status.SUCCESS,result.get(10,TimeUnit.SECONDS).status());assertFalse(f.root.isFailed());
    }
  }
  @Test void actualRuntimeClassifiersContinueThroughRealResolverAndReferenceCompilerForFourBranches() throws Exception {
    try(var f=new Fixture(false,2)){
      var result=f.run().toCompletableFuture().get(10,TimeUnit.SECONDS);
      assertEquals(DependencyExpansion.Status.SUCCESS,result.status(),result.diagnostic());assertEquals(4,result.dependencies().size());assertEquals(4,f.classifier.calls.get());
      assertEquals(Set.of("test:A1/test:B1","test:A1/test:B2","test:A2/test:B1","test:A2/test:B2"),new HashSet<>(f.resolved));
      assertEquals(4,new HashSet<>(f.registered).size());assertTrue(result.dependencies().stream().allMatch(d->d.observable().isOptional()&&!d.dataflow().isEmpty()));
      verify(f.reasoner,never()).computeObservationStrategies(any(),any());
      for(var prerequisite:f.prerequisites.values())assertFalse(prerequisite.operation().getModelDependency().isOptional());
    }
  }
  @Test void successfulContinuationCommitsItsRootBeforeAddingCommitOwnedMetadata() throws Exception {
    for(boolean reused:List.of(false,true))try(var f=new Fixture(reused,2)){
      for(var entry:Map.of("knowledgeGraph",f.graph,"commitCache",com.google.common.cache.CacheBuilder.newBuilder().build()).entrySet()){
        var field=DigitalTwinImpl.class.getDeclaredField(entry.getKey());field.setAccessible(true);field.set(f.twin,entry.getValue());
      }
      var user=mock(org.integratedmodelling.klab.api.identities.UserIdentity.class);when(user.getUsername()).thenReturn("test");when(f.scope.getUser()).thenReturn(user);
      when(f.graph.nextKey()).thenReturn(900L);var nextId=new AtomicLong(1000);
      doAnswer(i->{var asset=i.getArgument(0);if(asset instanceof org.integratedmodelling.klab.api.provenance.impl.ActivityImpl a)a.setId(nextId.incrementAndGet());
        if(asset instanceof ObservationImpl o && o.getId()<=0){o.setId(nextId.incrementAndGet());o.setUrn("context."+o.getId());}return null;}).when(f.nativeTx).store(any());
      var result=f.run().toCompletableFuture().get(10,TimeUnit.SECONDS);assertEquals(DependencyExpansion.Status.SUCCESS,result.status(),result.diagnostic());
      assertEquals(900,f.root.commit());assertFalse(f.root.isFailed());assertEquals(900L,f.producer.getMetadata().get(Metadata.IM_COMMIT_ID));
      verify(f.nativeTx,atLeastOnce()).validateClassificationMembers(any());verify(f.nativeTx,atLeastOnce()).store(any());
    }
  }
  @Test void twoOccurrencesUseOneDurableMemberIdentityAcrossSeparateMaterializations() throws Exception {
    try(var f=new Fixture(false,0)){
      when(f.nativeTx.captureClassificationMembers(10)).thenReturn(new KnowledgeGraph.ClassificationReadSet("context",10,"test:Region",List.of(
          new KnowledgeGraph.ClassificationMemberSnapshot(41,"test:Region",f.support.encode(),0),new KnowledgeGraph.ClassificationMemberSnapshot(42,"test:Region",f.support.encode(),0))));
      var result=f.run().toCompletableFuture().get(10,TimeUnit.SECONDS);assertEquals(DependencyExpansion.Status.SUCCESS,result.status(),result.diagnostic());
      assertEquals(4,result.dependencies().size());assertEquals(4,f.classifier.calls.get());
    }
  }
  @Test void invalidSupportFailsInsideTheLiveParentAttempt() throws Exception {
    try(var f=new Fixture(false,0)){
      var result=f.runtime.resolveClassifiedDependencies(f.prepared,f.prerequisites,null,f.event,f.scope).toCompletableFuture().join();
      assertEquals(DependencyExpansion.Status.FAILED,result.status());assertTrue(f.root.isFailed());
    }
  }
  @Test void allReusedMembersStillProduceFourConcretePlansWithoutClassifierCalls() throws Exception {
    try(var f=new Fixture(true,2)){var result=f.run().toCompletableFuture().get(10,TimeUnit.SECONDS);assertEquals(DependencyExpansion.Status.SUCCESS,result.status(),result.diagnostic());assertEquals(4,result.dependencies().size());assertEquals(0,f.classifier.calls.get());}
  }
  @Test void successfulEmptyAndUnresolvedProducerAreDistinct() throws Exception {
    try(var f=new Fixture(false,0)){var result=f.run().toCompletableFuture().join();assertEquals(DependencyExpansion.Status.SUCCESS,result.status(),result.diagnostic());assertTrue(result.dependencies().isEmpty());assertEquals(0,f.classifier.calls.get());assertTrue(f.registered.isEmpty());}
    try(var f=new Fixture(false,2)){f.completion.setOutcome(null);var result=f.run().toCompletableFuture().join();assertEquals(DependencyExpansion.Status.UNKNOWN,result.status());assertFalse(f.root.isFailed());assertEquals(0,f.classifier.calls.get());assertTrue(f.registered.isEmpty());}
  }
  @Test void requiredClassifierNullCannotBeExcusedByGeneratedOptionalDependencies() throws Exception {
    try(var f=new Fixture(false,2)){f.classifier.missing=true;var result=f.run().toCompletableFuture().join();assertEquals(DependencyExpansion.Status.FAILED,result.status());assertTrue(f.root.isFailed());assertTrue(f.registered.isEmpty());}
  }
  @Test void actualClassifierCancellationRemainsCancelledAndRollsBackParent() throws Exception {
    try(var f=new Fixture(false,2)){f.classifier.cancel=true;var future=f.run().toCompletableFuture();assertTrue(future.isCancelled());assertTrue(f.root.isFailed());assertEquals(-1,f.root.commit());}
  }
  @Test void sourceMutationAndSemanticGenerationDriftFailBeforeConcreteResolution() throws Exception {
    try(var f=new Fixture(false,2)){
      doAnswer(i->{f.prerequisites.values().iterator().next().operation().getComputation().add(new ServiceCallImpl("changed"));return null;}).when(f.runtime).characterizePending(anyList(),any());
      assertEquals(DependencyExpansion.Status.FAILED,f.run().toCompletableFuture().join().status());assertTrue(f.root.isFailed());assertTrue(f.registered.isEmpty());
    }
    try(var f=new Fixture(false,2)){
      doAnswer(i->{f.revision.incrementAndGet();return null;}).when(f.runtime).characterizePending(anyList(),any());
      assertEquals(DependencyExpansion.Status.FAILED,f.run().toCompletableFuture().join().status());assertTrue(f.root.isFailed());assertTrue(f.registered.isEmpty());
    }
  }
  @Test void payloadMutationCannotBorrowAnUnchangedSemanticAndGeometryCertificate() throws Exception {
    try(var f=new Fixture(false,2)){
      doAnswer(i->{var member=f.root.assets().stream().filter(Observation.class::isInstance).map(Observation.class::cast).filter(o->o.getId()==41).findFirst().orElseThrow();member.getMetadata().put("classifierInput","changed");return null;}).when(f.runtime).characterizePending(anyList(),any());
      var result=f.run().toCompletableFuture().join();assertEquals(DependencyExpansion.Status.FAILED,result.status());assertTrue(f.root.isFailed());assertTrue(f.registered.isEmpty());
    }
  }
  @Test void positionalParameterOrderCannotChangeBehindAnUnchangedPayloadMap() throws Exception {
    try(var f=new Fixture(false,2)){
      var member=f.root.assets().stream().filter(Observation.class::isInstance).map(Observation.class::cast).filter(o->o.getId()==41).findFirst().orElseThrow();
      member.getMetadata().put("first","a");member.getMetadata().put("second","b");member.getMetadata().getUnnamedKeys().addAll(List.of("first","second"));
      doAnswer(i->{Collections.reverse(member.getMetadata().getUnnamedKeys());return null;}).when(f.runtime).characterizePending(anyList(),any());
      assertEquals(DependencyExpansion.Status.FAILED,f.run().toCompletableFuture().join().status());assertTrue(f.root.isFailed());assertTrue(f.registered.isEmpty());
    }
  }
  @Test void unexpandedRequiredSourceRetainsRequiredDependencyOptionality() throws Exception {
    var source=prepared(bearer->{bearer.getTraits().clear();bearer.getRoles().clear();});
    try(var f=new Fixture(false,0,source)){
      var result=f.run().toCompletableFuture().get(10,TimeUnit.SECONDS);assertEquals(DependencyExpansion.Status.SUCCESS,result.status(),result.diagnostic());
      assertEquals(1,result.dependencies().size());assertFalse(result.dependencies().getFirst().observable().isOptional());assertEquals(0,f.classifier.calls.get());
    }
  }
  @Test void changedMemberSetCannotBeAcceptedAsTheFrozenClassifierDomain() throws Exception {
    try(var f=new Fixture(false,2)){
      var inserted=new AtomicBoolean();
      doAnswer(i->{if(inserted.compareAndSet(false,true)){var member=new ObservationImpl();member.setId(43);member.setObservable(new Member());member.setGeometry(f.support);f.root.link(f.cohort,member,GraphModel.Relationship.HAS_MEMBER);}return null;}).when(f.runtime).characterizePending(anyList(),any());
      var result=f.run().toCompletableFuture().join();assertEquals(DependencyExpansion.Status.FAILED,result.status());assertTrue(f.root.isFailed());assertTrue(f.registered.isEmpty());
    }
  }
  @Test void unsupportedBearerQualifiersCannotBorrowTheBaseCohortDomain() throws Exception {
    var qualifiers=List.<java.util.function.Consumer<KimConceptImpl>>of(
        bearer->bearer.getTraits().add(syntax("test:Concrete",SemanticType.PREDICATE,SemanticType.ATTRIBUTE)),
        bearer->bearer.setCompresent(syntax("test:Other",SemanticType.SUBJECT)),
        bearer->bearer.setNegated(true));
    for(var qualify:qualifiers)try(var f=new Fixture(false,2,prepared(qualify))){
      assertEquals(DependencyExpansion.Status.FAILED,f.run().toCompletableFuture().join().status());
      assertEquals(0,f.classifier.calls.get());verify(f.nativeTx,never()).captureClassificationMembers(anyLong());
    }
    try(var f=new Fixture(false,2)){
      ((ConceptImpl)f.producer.getObservable().getSemantics()).setUrn("each test:QualifiedRegion");
      assertEquals(DependencyExpansion.Status.FAILED,f.run().toCompletableFuture().join().status());assertEquals(0,f.classifier.calls.get());
    }
  }
  @Test void wrongBearerAndForgedProducerCannotBorrowFamilyOrCompletionIdentity() throws Exception {
    try(var f=new Fixture(false,0)){
      when(f.reasoner.baseSubstantialType(any(),any())).thenReturn(concept("test:Other",SemanticType.SUBJECT));
      assertEquals(DependencyExpansion.Status.FAILED,f.run().toCompletableFuture().join().status());assertEquals(0,f.classifier.calls.get());
    }
    try(var f=new Fixture(false,0)){
      var forged=new ObservationImpl();forged.setId(99);forged.setGeometry(f.support);forged.setObservable(f.producer.getObservable());
      f.prerequisites.replaceAll((key,value)->new ClassifierDependencyContinuation.Prerequisite(value.operation(),forged));
      assertEquals(DependencyExpansion.Status.FAILED,f.run().toCompletableFuture().join().status());assertEquals(0,f.classifier.calls.get());
    }
  }
  @Test void canonicalEligibilityIsCheckedBeforeCallingResolverEvenWhenPayloadClaimsConcrete() throws Exception {
    try(var f=new Fixture(true,2)){
      when(f.reasoner.declareObservable(any(KimObservable.class))).thenAnswer(i->{
        KimObservable source=i.getArgument(0);
        if(!source.isOptional()){var original=observable("test:A/test:B",SemanticType.QUALITY);original.setOptional(false);return original;}
        var o=observable("test:Unresolved",SemanticType.QUALITY);o.setOptional(source.isOptional());
        var canonical=concept("test:Unresolved",SemanticType.QUALITY);canonical.setAbstract(true);f.concepts.put(canonical.getUrn(),canonical);return o;
      });
      assertEquals(DependencyExpansion.Status.FAILED,f.run().toCompletableFuture().join().status());assertTrue(f.registered.isEmpty());assertTrue(f.resolved.isEmpty());
    }
  }
  @Test void reverseAsyncResolutionCompletionPreservesCartesianBranchIdentity() throws Exception {
    try(var f=new Fixture(true,2)){
      var real=new ArrayList<CompletableFuture<Dataflow>>();var delayed=new ArrayList<CompletableFuture<Dataflow>>();
      doAnswer(i->{var actual=(CompletableFuture<Dataflow>)i.callRealMethod();real.add(actual);var gate=new CompletableFuture<Dataflow>();delayed.add(gate);return gate;}).when(f.resolver).resolve(any(),eq(f.scope));
      var result=f.run().toCompletableFuture();assertFalse(result.isDone());assertEquals(4,real.size());
      for(int n=3;n>=0;n--)delayed.get(n).complete(real.get(n).get(10,TimeUnit.SECONDS));
      var outcomes=result.get(10,TimeUnit.SECONDS).dependencies();assertEquals(List.of("test:A1/test:B1","test:A1/test:B2","test:A2/test:B1","test:A2/test:B2"),outcomes.stream().map(d->d.observable().getUrn()).toList());
    }
  }
  @Test void asynchronousCancellationFailsParentWithoutCancellingBorrowedSiblingStages() throws Exception {
    try(var f=new Fixture(true,2)){
      var gates=new ArrayList<CompletableFuture<Dataflow>>();
      doAnswer(i->{var gate=new CompletableFuture<Dataflow>();gates.add(gate);return gate;}).when(f.resolver).resolve(any(),eq(f.scope));
      var result=f.run().toCompletableFuture();gates.getFirst().cancel(false);
      assertTrue(result.isCancelled());assertTrue(f.root.isFailed());assertFalse(gates.get(1).isCancelled());assertEquals(-1,f.root.commit());
    }
    try(var f=new Fixture(true,2)){
      var gates=new ArrayList<CompletableFuture<Dataflow>>();doAnswer(i->{var gate=new CompletableFuture<Dataflow>();gates.add(gate);return gate;}).when(f.resolver).resolve(any(),eq(f.scope));
      var result=f.run().toCompletableFuture();assertTrue(result.cancel(false));assertTrue(f.root.isFailed());assertTrue(gates.stream().noneMatch(CompletableFuture::isCancelled));
    }
  }

  @Test void failedProducerAndChangedCompletionAreFailuresRatherThanPendingOrEmpty() throws Exception {
    try(var f=new Fixture(false,0)){
      f.completion.setOutcome(Activity.Outcome.FAILURE);
      assertEquals(DependencyExpansion.Status.FAILED,f.run().toCompletableFuture().join().status());assertTrue(f.root.isFailed());
    }
    try(var f=new Fixture(false,2)){
      doAnswer(i->{f.completion.setOutcome(Activity.Outcome.FAILURE);return null;}).when(f.runtime).characterizePending(anyList(),any());
      assertEquals(DependencyExpansion.Status.FAILED,f.run().toCompletableFuture().join().status());assertTrue(f.registered.isEmpty());
    }
  }

  @Test void persistedMembersWithoutLocalMembershipLinksIncludeTentativeViews() throws Exception {
    try(var f=new Fixture(false,0)){
      when(f.nativeTx.captureClassificationMembers(10)).thenReturn(new KnowledgeGraph.ClassificationReadSet("context",10,"test:Region",List.of(
          new KnowledgeGraph.ClassificationMemberSnapshot(41,"test:Region",f.support.encode(),0),new KnowledgeGraph.ClassificationMemberSnapshot(42,"test:Region",f.support.encode(),0))));
      when(f.reasoner.resolveObservable("test:Region")).thenAnswer(i->new Member());
      var member=new ObservationImpl();member.setId(41);member.setGeometry(f.support);member.setObservable(new Member());f.root.add(member);
      var child=(DigitalTwinImpl.TransactionImpl)f.root.getChild(Activity.of(Activity.Type.CLASSIFICATION),f.scope);
      child.stageAttributions(List.of(new MemberClassifierExecutor.PendingAttribution(member,member.getObservable(),f.concepts.get("test:A"),f.concepts.get("test:A1"),f.support,f.event)),f.scope);
      var result=f.run().toCompletableFuture().get(10,TimeUnit.SECONDS);
      assertEquals(DependencyExpansion.Status.SUCCESS,result.status(),result.diagnostic());assertEquals(4,result.dependencies().size());assertEquals(3,f.classifier.calls.get());
    }
  }
  @Test void unrelatedTentativeAttributionDuringCharacterizationInvalidatesProjectedInput() throws Exception {
    try(var f=new Fixture(false,2)){
      var once=new AtomicBoolean();var extra=concept("test:C1",SemanticType.PREDICATE,SemanticType.ATTRIBUTE);var family=concept("test:C",SemanticType.PREDICATE,SemanticType.ATTRIBUTE);
      f.concepts.put("test:C1",extra);f.concepts.put("test:C",family);
      doAnswer(i->{if(once.compareAndSet(false,true)){
        var member=f.root.assets().stream().filter(Observation.class::isInstance).map(Observation.class::cast).filter(o->o.getId()==41).findFirst().orElseThrow();
        var child=(DigitalTwinImpl.TransactionImpl)f.root.getChild(Activity.of(Activity.Type.CLASSIFICATION),f.scope);
        child.stageAttributions(List.of(new MemberClassifierExecutor.PendingAttribution(member,f.root.readObservation(member).getObservable(),family,extra,f.support,f.event)),f.scope);
      }return null;}).when(f.runtime).characterizePending(anyList(),any());
      assertEquals(DependencyExpansion.Status.FAILED,f.run().toCompletableFuture().join().status());assertTrue(f.root.isFailed());assertTrue(f.registered.isEmpty());
    }
  }
  @Test void missingContributionAndWrongFamilyFailEvenForSuccessfullyEmptyProducer() throws Exception {
    try(var f=new Fixture(false,0)){
      var other=new CohortImpl();other.setId(11);other.setObservable(f.cohort.getObservable());when(f.runtime.getCohortFor(any(),eq(f.scope),eq(false))).thenReturn(other);
      assertEquals(DependencyExpansion.Status.FAILED,f.run().toCompletableFuture().join().status());assertEquals(0,f.classifier.calls.get());
    }
    try(var f=new Fixture(false,0)){
      ((ActuatorImpl)f.prerequisites.values().iterator().next().operation()).setOperationObservable(new Directive("test:B"));
      assertEquals(DependencyExpansion.Status.FAILED,f.run().toCompletableFuture().join().status());assertEquals(0,f.classifier.calls.get());
    }
  }
  @Test void immediatelyCancelledResolverStopsFurtherRegistrationAndLaunch() throws Exception {
    try(var f=new Fixture(true,2)){
      doAnswer(i->{var cancelled=new CompletableFuture<Dataflow>();cancelled.cancel(false);return cancelled;}).when(f.resolver).resolve(any(),eq(f.scope));
      var result=f.run().toCompletableFuture();assertTrue(result.isCancelled());assertEquals(1,f.registered.size());assertTrue(f.root.isFailed());
    }
  }

  @Test void classifierOptionalityAndOriginalSourceCannotBeWeakened() throws Exception {
    try(var f=new Fixture(false,0)){
      ((ObservableImpl)f.prerequisites.values().iterator().next().operation().getModelDependency()).setOptional(true);
      assertEquals(DependencyExpansion.Status.FAILED,f.run().toCompletableFuture().join().status());assertEquals(0,f.classifier.calls.get());
    }
    try(var f=new Fixture(false,0)){
      ((ObservableImpl)f.prerequisites.values().iterator().next().operation().getModelDependency()).setUrn("different source");
      assertEquals(DependencyExpansion.Status.FAILED,f.run().toCompletableFuture().join().status());assertEquals(0,f.classifier.calls.get());
    }
  }
  @Test void absenceAndTransportFailureRemainDistinctForOptionalConcretePlans() throws Exception {
    try(var f=new Fixture(true,2)){
      doReturn(CompletableFuture.completedFuture(Dataflow.noModel(List.of()))).when(f.resolver).resolve(any(),eq(f.scope));
      var result=f.run().toCompletableFuture().join();assertEquals(DependencyExpansion.Status.SUCCESS,result.status(),result.diagnostic());assertEquals(4,result.dependencies().size());assertTrue(result.dependencies().stream().allMatch(d->d.dataflow().getResolutionOutcome()==Dataflow.ResolutionOutcome.NO_MODEL));
    }
    try(var f=new Fixture(true,2)){
      doReturn(CompletableFuture.failedFuture(new IllegalStateException("transport failed"))).when(f.resolver).resolve(any(),eq(f.scope));
      var result=f.run().toCompletableFuture().join();assertEquals(DependencyExpansion.Status.FAILED,result.status());assertTrue(f.root.isFailed());
    }
  }

}
