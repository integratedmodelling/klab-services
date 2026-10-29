package org.integratedmodelling.klab.services.runtime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.*;
import org.eclipse.emf.ecore.resource.impl.ResourceImpl;
import org.eclipse.xtext.parser.IParser;
import org.integratedmodelling.common.data.jackson.JacksonConfiguration;
import org.integratedmodelling.common.knowledge.*;
import org.integratedmodelling.common.lang.*;
import org.integratedmodelling.common.runtime.ActuatorImpl;
import org.integratedmodelling.klab.api.configuration.*;
import org.integratedmodelling.klab.api.data.*;
import org.integratedmodelling.klab.api.data.impl.LinkImpl;
import org.integratedmodelling.klab.api.digitaltwin.*;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.identities.UserIdentity;
import org.integratedmodelling.klab.api.knowledge.*;
import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.api.knowledge.observation.*;
import org.integratedmodelling.klab.api.knowledge.observation.impl.ObservationImpl;
import org.integratedmodelling.klab.api.lang.*;
import org.integratedmodelling.klab.api.lang.kim.*;
import org.integratedmodelling.klab.api.lang.kim.impl.*;
import org.integratedmodelling.klab.api.provenance.*;
import org.integratedmodelling.klab.api.provenance.impl.ActivityImpl;
import org.integratedmodelling.klab.api.scope.*;
import org.integratedmodelling.klab.api.services.*;
import org.integratedmodelling.klab.api.services.resources.ResourceSet;
import org.integratedmodelling.klab.api.services.runtime.*;
import org.integratedmodelling.klab.components.ComponentRegistry;
import org.integratedmodelling.klab.configuration.ServiceConfiguration;
import org.integratedmodelling.klab.runtime.language.LanguageAdapter;
import org.integratedmodelling.klab.runtime.storage.*;
import org.integratedmodelling.klab.services.reasoner.*;
import org.integratedmodelling.klab.services.reasoner.owl.SubmissionNativeFixture;
import org.integratedmodelling.klab.services.resolver.*;
import org.integratedmodelling.klab.services.runtime.digitaltwin.DigitalTwinImpl;
import org.integratedmodelling.klab.services.runtime.digitaltwin.scheduler.SchedulerImpl;
import org.integratedmodelling.klab.services.runtime.neo4j.KnowledgeGraphNeo4j;
import org.integratedmodelling.klab.services.scopes.*;
import org.integratedmodelling.languages.ObservationStandaloneSetup;
import org.integratedmodelling.languages.ObservationSyntaxAdapter;
import org.integratedmodelling.languages.api.*;
import org.integratedmodelling.languages.validation.LanguageValidationScope;
import org.junit.jupiter.api.*;
import org.ojalgo.array.BufferArray;

/** Actual selected-model path and numerical execution; catalogue/persistence are fixture boundaries. */
class SelectedModelClassifierSubmissionTest {
  @BeforeAll static void configure(){ServiceConfiguration.injectInstantiators();}
  static void field(Object target,Class<?> owner,String name,Object value)throws Exception{var f=owner.getDeclaredField(name);f.setAccessible(true);f.set(target,value);}
  static KimObservationStrategyDocument strategies() throws Exception {
    String source;
    try (var input = SelectedModelClassifierSubmissionTest.class.getResourceAsStream("/observation/observations-proposed.obs")) {
      source = new String(Objects.requireNonNull(input).readAllBytes(), StandardCharsets.UTF_8);
    }
    var parser = new ObservationStandaloneSetup().createInjectorAndDoEMFRegistration().getInstance(IParser.class);
    var parsed = parser.parse(new StringReader(source));
    assertFalse(parsed.hasSyntaxErrors());
    var root = (org.integratedmodelling.languages.observation.ObservationDocument) parsed.getRootASTElement();
    new ResourceImpl(URI.createURI("memory:/observations.obs")).getContents().add(root);
    var scope = new LanguageValidationScope() {
      public ConceptDescriptor getConceptDescriptor(String urn) {
        var parts = urn.split(":", 2);
        return new ConceptDescriptor(parts[0], parts[1], SemanticSyntax.Type.SUBJECT, urn, "test", false, false);
      }
      public void notifyCoreConcept(String urn, SemanticSyntax.Type type) {}
      public LanguageValidationScope contextualize(EObject context) { return this; }
    };
    var syntax = (ObservationSyntax.StrategyDocument) new ObservationSyntaxAdapter() {
      @Override protected void logWarning(ParsedObject target, EObject object,
          org.eclipse.emf.ecore.EStructuralFeature feature, String message) {}
      @Override protected void logError(ParsedObject target, EObject object,
          org.eclipse.emf.ecore.EStructuralFeature feature, String message) { fail(message); }
    }.adapt(root, scope);
    var bean = LanguageAdapter.INSTANCE.adaptStrategies(syntax, "test", List.of(), 1L);
    var mapper = JacksonConfiguration.newObjectMapper();
    return mapper.readValue(mapper.writerFor(KimObservationStrategyDocument.class).writeValueAsString(bean),
        KimObservationStrategyDocument.class);
  }

  public static class Classifier {
    final AtomicInteger calls=new AtomicInteger();
    final AtomicInteger discoveries=new AtomicInteger();
    final AtomicInteger valuations=new AtomicInteger();
    final List<String> consumed=new CopyOnWriteArrayList<>();
    final List<DependencyFamily.Input> consumedInputs=new CopyOnWriteArrayList<>();
    public void aggregate(Observation self,Storage.DoubleScanner output,ContextScope scope) {
      valuations.incrementAndGet();double total=0;
      for(var family:scope.getDependencyFamilies(self)) {
        if(family.branches().stream().anyMatch(branch->branch.outcome()!=org.integratedmodelling.klab.api.services.runtime.DependencyFamily.Outcome.RESOLVED))
          throw new IllegalStateException("A total requires every valued crop");
        for(var input:scope.getDependencyInputs(self,family.id())) {
          consumed.add(input.branch().id()+"="+input.observation().getObservable().getUrn());consumedInputs.add(input);
          try(var read=StorageReads.open(input.observation(),scope,null,Data.FillCurve.D1_LINEAR,Storage.DoubleScanner.class)) {
            total+=read.scanners().getFirst().get();
          }
        }
      }
      output.add(total);
    }
    java.util.function.Function<Observation,String> classification=member->"audit:Concrete";
    java.util.function.BiFunction<Observable,Observation,String> byOperation;
    Runnable discoveryAction=()->{};
    public void discover(Data.Builder builder){discoveries.incrementAndGet();discoveryAction.run();}
    public Concept classify(Observable operation,Observation member,Scope scope){calls.incrementAndGet();return scope.getService(Reasoner.class).resolveConcept(byOperation==null?classification.apply(member):byOperation.apply(operation,member));}
  }
  static class Fixture implements AutoCloseable {
    final SubmissionNativeFixture semantic;
    final ReasonerService reasoner;
    final ResourcesService resources;
    final RuntimeService runtime=mock(RuntimeService.class,withSettings().defaultAnswer(CALLS_REAL_METHODS)
        .extraInterfaces(org.integratedmodelling.klab.api.services.RuntimeService.Testing.class));
    final ResolverService resolver=mock(ResolverService.class,CALLS_REAL_METHODS);
    final DigitalTwinImpl twin=mock(DigitalTwinImpl.class);
    final KnowledgeGraphNeo4j graph=mock(KnowledgeGraphNeo4j.class);
    final KnowledgeGraph.Transaction nativeTx=mock(KnowledgeGraph.Transaction.class);
    final StorageManagerImpl storage=mock(StorageManagerImpl.class);
    final Map<Observation,StorageImpl> stores=Collections.synchronizedMap(new IdentityHashMap<>());
    final Map<Long,Observation> durable=new ConcurrentHashMap<>();
    final List<Observation> classificationMembers=new CopyOnWriteArrayList<>();
    final Map<Long,KnowledgeGraph.ClassificationMemberSnapshot> persistedClassifierInputs=new ConcurrentHashMap<>();
    final Map<String,KimObservable> observables=new HashMap<>();
    final Map<String,KimNamespace> sources=new HashMap<>();
    final Map<String,String> modelForObservable=new HashMap<>();
    final List<String> modelQueries=new CopyOnWriteArrayList<>();
    List<String> competingParents=List.of();
    List<List<String>> parentQueryPages=List.of();
    final AtomicInteger parentQueryIndex=new AtomicInteger();
    boolean duplicateRootStrategies;
    Integer rootStrategyRank;
    Integer operationStrategyRank;
    final List<String> producerEvidence=new CopyOnWriteArrayList<>();
    final List<DigitalTwinImpl.TransactionImpl> attempts=new CopyOnWriteArrayList<>();
    final AtomicLong ids=new AtomicLong(200);
    final AtomicInteger rootCommits=new AtomicInteger();
    final AtomicBoolean cancelled=new AtomicBoolean();
    final ExecutorService resolverExecutor=Executors.newVirtualThreadPerTaskExecutor();
    final Geometry geometry=Geometry.create("T1(1){ttype=PHYSICAL,tstart=1000,tend=10000,tscope=9.0,tunit=SECOND}");
    final CohortImpl cohort=new CohortImpl();
    final ObservationImpl region=new ObservationImpl();
    final ServiceContextScope scope;
    final SchedulerImpl scheduler;
    final Classifier classifier=new Classifier();
    org.integratedmodelling.klab.runtime.kactors.TestCaseBase.TestCaseScope gatedOwner;
    final KimObservableImpl output,input,original;
    Fixture()throws Exception { this(false); }
    Fixture(boolean categoryOnInput)throws Exception {
      semantic=new SubmissionNativeFixture(categoryOnInput);reasoner=semantic.reasoner;resources=semantic.resources;
      semantic.installMatcher();
      output=semantic.request("Result",categoryOnInput?semantic.named("Region"):semantic.bearer("Concrete"));
      input=semantic.request("Input",semantic.bearer("Concrete"));
      original=semantic.request("Input",semantic.bearer("Category"));original.setFormalName("signal");
      var collective=semantic.named("Region");collective.setCollective(true);semantic.register(collective);
      var directive=semantic.named("Category");directive.setInherent(collective);semantic.register(directive);
      var classification=new KimObservableImpl();classification.setSemantics(directive);
      for(var syntax:List.of(output,input,original,observable(collective),classification))remember(syntax);
      // Persisted fixtures model registered observations, whose Runtime always provisions this bean.
      region.setContextualizationData(new ObservationImpl.ContextualizationDataImpl());
      region.setId(41);region.setName("Observed native region");region.setUrn("context.region");
      region.setObservable(reasoner.declareObservable(observable(semantic.bearer("Concrete"))));region.setGeometry(geometry);durable.put(41L,region);classificationMembers.add(region);
      cohort.setId(10);cohort.setObservable(reasoner.declareObservable(observable(semantic.named("Region"))));cohort.setGeometry(geometry);
      when(resources.declareObservable(anyString())).thenAnswer(i->{var syntax=observables.get(i.getArgument(0));return syntax==null?null:new KimObservableImpl((KimObservableImpl)syntax);});
      model("parent",output,List.of(original),expression("2*signal+1"));
      model("input",input,List.of(),expression("3"));
      var inputModel=(KimModel)sources.values().stream().filter(ns->ns.getSourceCode().equals("input")).findFirst().orElseThrow().getStatements().getFirst();
      inputModel.getAnnotations().add(AnnotationImpl.create("type","value","double"));
      var discovery=new ContextualizableImpl();discovery.setServiceCall(new ServiceCallImpl("fixture.discover"));
      model("producer",observable(collective),List.of(),discovery);
      var call=new ContextualizableImpl();call.setServiceCall(new ServiceCallImpl("fixture.classify"));
      model("classifier",classification,List.of(),call);
      when(resources.retrieve(anyString(),eq(KimNamespace.class),any())).thenAnswer(i->sources.get(i.getArgument(0)));
      when(resources.query(any(),eq(KlabAsset.KnowledgeClass.MODEL),eq(ResourceSet.class),any())).thenAnswer(i->{
        var params=(org.integratedmodelling.klab.api.collections.Parameters<?>)i.getArgument(0);
        var requested=(Observable)params.get("observable");modelQueries.add(requested.getUrn());
        String model=modelForObservable.get(requested.getUrn());if(model==null)model=modelForObservable.get(requested.getSemantics().getUrn());
        var models=requested.getSemantics().getUrn().equals(output.getSemantics().getUrn()) && !competingParents.isEmpty()
            ? competingParents : model==null?List.<String>of():List.of(model);
        if(requested.getSemantics().getUrn().equals(output.getSemantics().getUrn()) && !parentQueryPages.isEmpty())
          models=parentQueryPages.get(Math.min(parentQueryIndex.getAndIncrement(),parentQueryPages.size()-1));
        if(models.isEmpty())return List.of(ResourceSet.empty());
        var result=new ResourceSet();
        for(var candidate:models) {
          String namespace=candidate.substring(0,candidate.lastIndexOf('.'));
          result.getNamespaces().add(new ResourceSet.Resource("resources",namespace,null,null,KlabAsset.KnowledgeClass.NAMESPACE,1,false));
          result.getResults().add(new ResourceSet.Resource("resources",candidate,null,null,KlabAsset.KnowledgeClass.MODEL,1,false));
        }
        return List.of(result);
      });
      var commitment=mock(Reasoner.Capabilities.class);when(commitment.getServiceId()).thenReturn("native-reasoner");when(commitment.getKnowledgeRevision()).thenReturn(3L);
      // Controlled accepted-content identity; ontology reasoning and scoped locality extraction
      // remain real. This authored-only fixture configures no external authorities.
      when(commitment.getSemanticContentFingerprint()).thenReturn("semantic-content-v2:"+"f".repeat(64));
      when(commitment.getSemanticSessionId()).thenReturn("native-fixture-owner");
      field(reasoner,ReasonerService.class,"authorityBindings",new org.integratedmodelling.klab.services.reasoner.internal.AuthorityBindings());
      doReturn(commitment).when(reasoner).capabilities(any());
      doReturn(KlabService.Type.REASONER).when(reasoner).serviceType();doReturn("native-reasoner").when(reasoner).serviceId();
      when(resources.serviceId()).thenReturn("resources");
      var settings=mock(Settings.class);when(settings.get(any(),any())).thenAnswer(i->((Setting)i.getArgument(0)).defaultValue);
      field(runtime,org.integratedmodelling.klab.services.base.BaseService.class,"settings",settings);
      field(runtime,RuntimeService.class,"shardExecution",new ShardExecution(()->1));
      doReturn(KlabService.Type.RUNTIME).when(runtime).serviceType();doReturn("runtime").when(runtime).serviceId();
      doReturn(new java.net.URL("http://localhost:8283")).when(runtime).getUrl();
      doReturn(KlabService.Type.RESOLVER).when(resolver).serviceType();doReturn("resolver").when(resolver).serviceId();
      field(resolver,ResolverService.class,"resolutionExecutor",resolverExecutor);
      field(resolver,ResolverService.class,"configuration",new org.integratedmodelling.klab.services.configuration.ResolverConfiguration());
      var status=mock(KlabService.ServiceStatus.class);when(status.isOperational()).thenReturn(true);
      doReturn(status).when(resolver).status();doReturn(status).when(reasoner).status();doReturn(status).when(runtime).status();when(resources.status()).thenReturn(status);
      var user=mock(UserIdentity.class);when(user.getUsername()).thenReturn("tester");
      var session=new ServiceSessionScope(new ServiceUserScope(user,runtime));
      var base=new ServiceContextScope(session,DigitalTwin.Configuration.builder().id("automatic-submission").name("fixture").persistence(Persistence.EXPLICIT_ACTION).build(),user);
      base.setId(base.getConfiguration().getId());
      base.setDigitalTwin(twin);base.addService(runtime);base.addService(reasoner);base.addService(resolver);base.addService(resources);
      scope=((ServiceContextScope)base.within(region)).withCancellation(cancelled::get);
      scope.getData().put("__RESOLUTION_GRAPH__",ResolutionGraph.create(scope));
      when(twin.getKnowledgeGraph()).thenReturn(graph);when(twin.getOptions()).thenReturn(scope.getConfiguration());when(twin.getStorageManager()).thenReturn(storage);
      field(twin,DigitalTwinImpl.class,"knowledgeGraph",graph);field(twin,DigitalTwinImpl.class,"commitCache",com.google.common.cache.CacheBuilder.newBuilder().build());
      scheduler=new SchedulerImpl(scope,twin);when(twin.getScheduler()).thenReturn(scheduler);field(twin,DigitalTwinImpl.class,"scheduler",scheduler);
      when(twin.transaction(any(),any(),any(Object[].class))).thenAnswer(i->{var tx=twin.new TransactionImpl(i.getArgument(0),i.getArgument(1),Arrays.copyOfRange(i.getArguments(),2,i.getArguments().length));attempts.add(tx);return tx;});
      when(graph.nextKey()).thenAnswer(i->ids.incrementAndGet());when(graph.createTransaction(any())).thenReturn(nativeTx);
      when(graph.dataflow()).thenReturn(RuntimeAsset.DATAFLOW_ASSET);var klab=mock(org.integratedmodelling.klab.api.provenance.Agent.class);when(klab.getId()).thenReturn(3L);when(klab.getName()).thenReturn("k.LAB");when(graph.klab()).thenReturn(klab);
      when(graph.getAsset(anyLong(),any(),eq(Observation.class))).thenAnswer(i->durable.get(i.getArgument(0)));
      var cohortQuery=mock(KnowledgeGraph.Query.class,RETURNS_SELF);when(cohortQuery.run(any())).thenReturn(List.of(cohort));when(graph.query(eq(Cohort.class),any())).thenReturn(cohortQuery);
      var observationQuery=mock(KnowledgeGraph.Query.class,RETURNS_SELF);when(observationQuery.run(any())).thenReturn(List.of());when(graph.query(eq(Observation.class),any())).thenReturn(observationQuery);
      // No historical contextualization activities are supplied by this persistence fixture.
      // Their absence is an unknown semantic footprint, not authority-independence evidence.
      var activityQuery=mock(KnowledgeGraph.Query.class,RETURNS_SELF);
      when(activityQuery.run(any())).thenReturn(List.of());when(graph.query(eq(Activity.class),any())).thenReturn(activityQuery);
      when(graph.getLinks(any(),any(),any(),any(GraphModel.Relationship[].class))).thenAnswer(i->{
        var target=(RuntimeAsset)i.getArgument(0);var direction=(GraphModel.Relationship.Direction)i.getArgument(1);var current=(ContextScope)i.getArgument(2);
        if(current.getCurrentTransaction()==null)return List.of();
        var tx=current.getCurrentTransaction();var links=direction==GraphModel.Relationship.Direction.INCOMING?tx.incoming(target):tx.outgoing(target);
        var types=Arrays.stream(i.getArguments()).skip(3).map(GraphModel.Relationship.class::cast).toList();return links.stream().filter(link->types.isEmpty()||types.contains(link.type())).toList();
      });
      when(nativeTx.captureClassificationMembers(10)).thenAnswer(i->memberReadSet());
      doAnswer(i->{if(!memberReadSet().equals(i.getArgument(0)))throw new IllegalStateException("Native classifier membership or inputs changed");return null;}).when(nativeTx).validateClassificationMembers(any());
      when(graph.materializeClassificationMember(any(),any())).thenAnswer(i->{
        var snapshot=(KnowledgeGraph.ClassificationMemberSnapshot)i.getArgument(0);
        // The fixture Resources transport must retain the authored native observable syntax.
        remember(observable(semantic.resources.declareConcept(snapshot.observable())));
        return i.callRealMethod();
      });
      doAnswer(i->{var asset=(RuntimeAsset)i.getArgument(0);if(asset instanceof ObservationImpl o){if(o.getId()<0)o.setId(ids.incrementAndGet());durable.put(o.getId(),o);}else if(asset instanceof ActivityImpl a){a.setId(ids.incrementAndGet());if(a.getType()==Activity.Type.SUBMISSION)rootCommits.incrementAndGet();}else if(asset instanceof ActuatorImpl a)a.setId(ids.incrementAndGet());else if(asset instanceof CohortImpl c)c.setId(ids.incrementAndGet());else if(asset instanceof org.integratedmodelling.klab.common.data.impl.ShardImpl shard)shard.setId(ids.incrementAndGet());else {producerEvidence.add("Unpersisted fixture asset "+asset.getClass()+" id="+asset.getId());throw new IllegalStateException("Unpersisted fixture asset "+asset.getClass()+" id="+asset.getId());}return null;}).when(nativeTx).store(any());

      doAnswer(i->{if(i.getArgument(0) instanceof Observation parent && i.getArgument(1) instanceof ObservationImpl child
          && i.getArgument(2)==GraphModel.Relationship.HAS_CHILD)child.setParentId(parent.getId());return null;})
          .when(nativeTx).link(any(),any(),any(),any(Object[].class));
      when(storage.getDoubleBuffer(anyLong())).thenAnswer(i->(BufferArray)BufferArray.R064.make(i.getArgument(0,Long.class)));
      when(storage.getStorage(any())).thenAnswer(i->store(i.getArgument(0)));when(storage.createStorage(any())).thenAnswer(i->store(i.getArgument(0)));
      var registry=mock(ComponentRegistry.class);doReturn(registry).when(runtime).getComponentRegistry();
      var descriptor=new org.integratedmodelling.klab.api.services.runtime.extension.Extensions.FunctionDescriptor();
      var implementation=new ComponentRegistry.ServiceImplementation();implementation.mainClassInstance=classifier;implementation.method=Classifier.class.getMethod("classify",Observable.class,Observation.class,Scope.class);
      var discoveryDescriptor=new org.integratedmodelling.klab.api.services.runtime.extension.Extensions.FunctionDescriptor();
      discoveryDescriptor.serviceInfo=new ServiceInfoImpl();
      var discoveryImplementation=new ComponentRegistry.ServiceImplementation();discoveryImplementation.mainClassInstance=classifier;
      discoveryImplementation.method=Classifier.class.getMethod("discover",Data.Builder.class);
      var aggregateDescriptor=new org.integratedmodelling.klab.api.services.runtime.extension.Extensions.FunctionDescriptor();
      var aggregateInfo=new ServiceInfoImpl();aggregateDescriptor.serviceInfo=aggregateInfo;
      var aggregateImplementation=new ComponentRegistry.ServiceImplementation();aggregateImplementation.mainClassInstance=classifier;
      aggregateImplementation.method=Classifier.class.getMethod("aggregate",Observation.class,Storage.DoubleScanner.class,ContextScope.class);
      for(var parameter:aggregateImplementation.method.getParameters())if(parameter.getType()!=ContextScope.class) {
        var argument=new ServiceInfoImpl.ArgumentImpl();argument.setName(parameter.getName());aggregateInfo.getOutputs().add(argument);
      }
      when(registry.implementation(aggregateDescriptor)).thenReturn(aggregateImplementation);
      when(registry.getFunctionDescriptor(any(ServiceCall.class),any(Scope.class))).thenAnswer(i->List.of(switch(((ServiceCall)i.getArgument(0)).getUrn()) {
        case "fixture.discover" -> discoveryDescriptor;
        case "fixture.aggregate" -> aggregateDescriptor;
        default -> descriptor;
      }));
      // Admission inspects the installed method through the one-argument catalogue API;
      // execution uses the lexical-scope overload. Both must expose the same fixture functions.
      when(registry.getFunctionDescriptor(any(ServiceCall.class)))
          .thenAnswer(i -> registry.getFunctionDescriptor(i.getArgument(0, ServiceCall.class), scope));
      when(registry.implementation(descriptor)).thenReturn(implementation);when(registry.implementation(discoveryDescriptor)).thenReturn(discoveryImplementation);
      var requirements=new ResourceSet();requirements.setEmpty(false);doReturn(requirements).when(runtime).resolveContextualizables(anyList(),any());
      doAnswer(i->{var producer=(Observation)i.callRealMethod();var execution=(ServiceContextScope)i.getArgument(2);
        producerEvidence.add(producer.getObservable().getUrn()+" activity="+producer.getObservable().getContextualization()+" geometry="+GeometryRepository.INSTANCE.sanitize(producer.getGeometry()).encode()+" requested="+GeometryRepository.INSTANCE.sanitize(i.getArgument(1)).encode());
        for(var link:execution.getCurrentTransaction().incoming(producer))if(link.source() instanceof Activity activity)
          producerEvidence.add(link.type()+":"+activity.getType()+":"+activity.getOutcome()+":"+activity.getMetadata().get(Metadata.IM_EXECUTION_GEOMETRY));
        return producer;}).when(runtime).executeClassifierProducer(any(),any(),any());
      var strategies=new ObservationReasoner(reasoner,reasoner::declareObservable);strategies().getStatements().stream().filter(s->s.getRank()==0).forEach(strategies::registerStrategy);strategies.initializeStrategies();
      doAnswer(i->{
        var result=strategies.computeMatchingStrategies(i.getArgument(0),i.getArgument(1),true);
        if(rootStrategyRank!=null && ((Observation)i.getArgument(0)).getObservable().getSemantics().getUrn().equals(output.getSemantics().getUrn()))
          for(var strategy:result)((org.integratedmodelling.klab.api.services.resolver.objects.ObservationStrategyImpl)strategy).setRank(rootStrategyRank);
        if(operationStrategyRank!=null && ((Observation)i.getArgument(0)).getObservable().getContextualization()==org.integratedmodelling.klab.api.knowledge.Contextualization.CLASSIFICATION)
          for(var strategy:result)((org.integratedmodelling.klab.api.services.resolver.objects.ObservationStrategyImpl)strategy).setRank(operationStrategyRank);
        if(duplicateRootStrategies && ((Observation)i.getArgument(0)).getObservable().getSemantics().getUrn().equals(output.getSemantics().getUrn())) {
          var mapper=JacksonConfiguration.newObjectMapper();
          var duplicate=mapper.readValue(mapper.writeValueAsString(result.getFirst()),ObservationStrategy.class);
          ((org.integratedmodelling.klab.api.services.resolver.objects.ObservationStrategyImpl)duplicate).setUrn(result.getFirst().getUrn()+".second");
          return List.of(result.getFirst(),duplicate);
        }
        return result;
      }).when(reasoner).computeObservationStrategies(any(),any());
    }
    KimObservableImpl observable(KimConcept semantics){var o=new KimObservableImpl();o.setSemantics(semantics);o.setUrn(semantics.getUrn());o.setNamespace("audit");return o;}
    AutoCloseable useGatedEmptyProducer() throws Exception {
      var agent=mock(org.integratedmodelling.klab.runtime.kactors.TestCaseBase.class);
      when(agent.status()).thenReturn("running");
      var owner=new org.integratedmodelling.klab.runtime.kactors.TestCaseBase.TestCaseScope(agent,null,null);
      gatedOwner=owner;
      owner.registerContext(scope);
      assertTrue(org.integratedmodelling.klab.runtime.libraries.CoreActorLibrary.Inspector.fixtures(owner,scope));
      var producer=(KimModel)sources.values().stream().filter(ns->ns.getSourceCode().equals("producer"))
          .findFirst().orElseThrow().getStatements().getFirst();
      producer.getContextualization().clear();
      var call=new ContextualizableImpl();call.setServiceCall(new ServiceCallImpl("klab.tests.fixtures.empty"));
      producer.getContextualization().add(call);
      var descriptor=new org.integratedmodelling.klab.api.services.runtime.extension.Extensions.FunctionDescriptor();
      descriptor.staticMethod=true;descriptor.serviceInfo=new ServiceInfoImpl();
      var implementation=new ComponentRegistry.ServiceImplementation();
      implementation.method=org.integratedmodelling.klab.runtime.libraries.NativeTestFixtures.class.getMethod("empty",
          Data.Builder.class,Geometry.class,Observable.class,Scope.class);
      var registry=runtime.getComponentRegistry();
      when(registry.implementation(descriptor)).thenReturn(implementation);
      when(registry.getFunctionDescriptor(argThat(c->c!=null&&"klab.tests.fixtures.empty".equals(c.getUrn())),any(Scope.class)))
          .thenReturn(List.of(descriptor));
      return owner::done;
    }
    long gatedDiscoveries() {
      return org.integratedmodelling.klab.runtime.libraries.CoreActorLibrary.Inspector.fixturecount(gatedOwner,scope,"empty");
    }
    void remember(KimObservableImpl syntax){if(syntax.getUrn()==null)syntax.setUrn(syntax.getSemantics().getUrn());observables.put(syntax.getUrn(),syntax);}
    Contextualizable expression(String code){var c=new ContextualizableImpl();c.setExpression(ExpressionCode.of(code,"groovy"));return c;}
    void model(String name,KimObservable primary,List<KimObservable> dependencies,Contextualizable... computations){
      var namespace=new KimNamespaceImpl();String id="fixture."+UUID.randomUUID().toString().replace('-','_');namespace.setUrn(id);namespace.setSourceCode(name);namespace.setLastUpdateTimestamp(1);
      var model=new KimModelImpl();model.setUrn(id+"."+name);model.setNamespace(id);model.getObservables().add(primary);model.getDependencies().addAll(dependencies);model.getContextualization().addAll(List.of(computations));namespace.getStatements().add(model);
      sources.put(id,namespace);modelForObservable.put(primary.getUrn(),model.getUrn());modelForObservable.put(primary.getSemantics().getUrn(),model.getUrn());
    }
    String orderedModel(String sourceName,String prefix) {
      var source=(KimNamespaceImpl)sources.values().stream().filter(ns->ns.getSourceCode().equals(sourceName)).findFirst().orElseThrow();
      var model=(KimModelImpl)source.getStatements().getFirst();String oldNamespace=source.getUrn(),oldModel=model.getUrn();
      String namespace="fixture."+prefix+"_"+UUID.randomUUID().toString().replace('-','_');
      source.setUrn(namespace);model.setNamespace(namespace);model.setUrn(namespace+"."+sourceName);
      sources.remove(oldNamespace);sources.put(namespace,source);
      modelForObservable.replaceAll((observable,urn)->urn.equals(oldModel)?model.getUrn():urn);return model.getUrn();
    }
    StorageImpl store(Observation observation){synchronized(stores){for(var e:stores.entrySet())if(e.getKey()==observation||e.getKey().getId()==observation.getId())return e.getValue();
      var layout=observation.getContextualizationData().getNativeShardingStrategy();if(layout==null)throw new IllegalStateException("Missing fixture layout for "+observation.getId()+" "+observation.getObservable().getUrn()+"; stores="+stores.keySet().stream().map(o->o.getId()+":"+o.getObservable().getUrn()).toList());var result=new StorageImpl(observation,layout,scope,storage);stores.put(observation,result);return result;}}
    KnowledgeGraph.ClassificationReadSet memberReadSet() {
      // The native adapter keeps persisted input separate from the framework's staged objects.
      // Domain membership is live; native payload changes must update this explicit persisted map.
      return new KnowledgeGraph.ClassificationReadSet(scope.getId(),cohort.getId(),cohort.getObservable().getUrn(),classificationMembers.stream().sorted(Comparator.comparingLong(Observation::getId)).map(member->
          persistedClassifierInputs.computeIfAbsent(member.getId(),id->new KnowledgeGraph.ClassificationMemberSnapshot(member.getId(),member.getObservable().getUrn(),GeometryRepository.INSTANCE.sanitize(member.getGeometry()).encode(),0,
              nativeObservationProperties(member),null))).toList());
    }
    @SuppressWarnings("unchecked")
    Map<String,Object> nativeObservationProperties(Observation member) {
      try {
        var encoder=org.integratedmodelling.klab.services.runtime.neo4j.AbstractKnowledgeGraph.class
            .getDeclaredMethod("parameterMap",Object.class,Object[].class);
        encoder.setAccessible(true);
        return (Map<String,Object>)encoder.invoke(null,member,new Object[0]);
      } catch(ReflectiveOperationException failure) {throw new AssertionError("Cannot capture actual native observation properties",failure);}
    }
    void crops(String... crops) {
      semantic.cropVocabulary();sources.clear();modelForObservable.clear();observables.clear();classificationMembers.clear();persistedClassifierInputs.clear();
      semantic.request("HarvestCashValue",semantic.named("Region")); // Parser adapter supplies the detached plain native residual too.
      region.setObservable(reasoner.declareObservable(observable(semantic.named("Region"))));
      for(int index=0;index<crops.length;index++) {
        var member=new ObservationImpl();member.setContextualizationData(new ObservationImpl.ContextualizationDataImpl());
        member.setId(42+index);member.setUrn("context.crop-region."+index);member.setName("Crop region "+index);
        member.setObservable(reasoner.declareObservable(observable(semantic.named("Region"))));member.setGeometry(geometry);member.getMetadata().put("fixture.crop",crops[index]);
        classificationMembers.add(member);durable.put(member.getId(),member);
      }
      classifier.classification=member->"audit:"+member.getMetadata().get("fixture.crop");
      var total=semantic.named("TotalCashValue");total.setInherent(semantic.named("Region"));semantic.register(total);output.setSemantics(total);output.setCurrency("USD@2020");output.setUrn(null);output.setUrn(reasoner.declareObservable(output).getUrn());remember(output);
      var harvest=semantic.named("HarvestCashValue");harvest.getTraits().add(semantic.named("Crop"));harvest.setInherent(semantic.named("Region"));semantic.register(harvest);
      original.setSemantics(harvest);original.setCurrency("USD@2020");original.setFormalName("harvestValues");original.setUrn(null);original.setUrn(reasoner.declareObservable(original).getUrn());remember(original);
      var aggregate=new ContextualizableImpl();aggregate.setServiceCall(new ServiceCallImpl("fixture.aggregate"));model("cropParent",output,List.of(original),aggregate);
      for(var crop:List.of("Wheat","Maize")) {
        var meaning=semantic.named("HarvestCashValue");meaning.getTraits().add(semantic.named(crop));meaning.setInherent(semantic.named("Region"));semantic.register(meaning);
        var monetary=observable(meaning);monetary.setCurrency("USD@2020");monetary.setUrn(reasoner.declareObservable(monetary).getUrn());remember(monetary);
        // Fixture valuations are explicit authored quantity * price constants, in common USD@2020.
        model("cash"+crop,monetary,List.of(),expression(crop.equals("Wheat")?"3*5":"2*11"));
      }
      var collective=semantic.named("Region");collective.setCollective(true);semantic.register(collective);var members=observable(collective);remember(members);
      var discover=new ContextualizableImpl();discover.setServiceCall(new ServiceCallImpl("fixture.discover"));model("cropRegions",members,List.of(),discover);
      var predicate=semantic.named("Crop");predicate.setInherent(collective);semantic.register(predicate);var classify=observable(predicate);remember(classify);
      var call=new ContextualizableImpl();call.setServiceCall(new ServiceCallImpl("fixture.classify"));model("cropClassifier",classify,List.of(),call);
      for(var namespace:sources.values())for(var statement:namespace.getStatements())if(statement instanceof KimModel model && model.getObservables().getFirst().getSemantics().is(SemanticType.QUALITY))
        model.getAnnotations().add(AnnotationImpl.create("type","value","double"));
    }
    void cartesian() {cartesian("Wheat","Maize");}
    void cartesian(String... memberCrops) {
      crops(memberCrops);semantic.seasonVocabulary();
      for(var predicate:List.of("Wheat","Maize","Warm","Cool"))semantic.bearer(predicate);
      for(var member:classificationMembers)member.getMetadata().put("fixture.season",member.getId()==42?"Warm":"Cool");
      classifier.byOperation=(operation,member)->"audit:"+member.getMetadata().get(operation.getSemantics().getUrn().contains("Season")?"fixture.season":"fixture.crop");
      var harvest=semantic.named("HarvestCashValue");harvest.getTraits().add(semantic.register(semantic.named("Crop")));harvest.getTraits().add(semantic.register(semantic.named("Season")));harvest.setInherent(semantic.register(semantic.named("Region")));semantic.register(harvest);original.setSemantics(harvest);
      original.setUrn(null);original.setUrn(reasoner.declareObservable(original).getUrn());remember(original);
      var parent=sources.values().stream().flatMap(ns->ns.getStatements().stream()).filter(KimModel.class::isInstance).map(KimModel.class::cast).filter(m->m.getUrn().endsWith(".cropParent")).findFirst().orElseThrow();
      parent.getDependencies().set(0,original);
      int value=0;
      for(var crop:List.of("Wheat","Maize"))for(var season:List.of("Warm","Cool")) {
        var meaning=semantic.named("HarvestCashValue");meaning.getTraits().add(semantic.register(semantic.named(crop)));meaning.getTraits().add(semantic.register(semantic.named(season)));meaning.setInherent(semantic.register(semantic.named("Region")));semantic.register(meaning);
        var monetary=observable(meaning);monetary.setCurrency("USD@2020");monetary.setUrn(reasoner.declareObservable(monetary).getUrn());remember(monetary);
        model("cash"+crop+season,monetary,List.of(),expression(Integer.toString(++value)));
      }
      var collective=semantic.named("Region");collective.setCollective(true);semantic.register(collective);
      var predicate=semantic.named("Season");predicate.setInherent(collective);semantic.register(predicate);var classify=observable(predicate);remember(classify);
      var call=new ContextualizableImpl();call.setServiceCall(new ServiceCallImpl("fixture.classify"));model("seasonClassifier",classify,List.of(),call);
    }
    void prepareColdFamilySyntax() {
      // The map-based parser adapter must retain full optional observable syntax across restoration.
      // Redeclare from the actual authored concrete model AST; no subtype/admission result is mocked.
      for(var input:classifier.consumedInputs) {
        var observed=input.observation().getObservable();
        var template=observables.values().stream().filter(o->o.getSemantics().getUrn().equals(observed.getSemantics().getUrn())).findFirst().orElseThrow();
        var parsed=new KimObservableImpl((KimObservableImpl)template);parsed.setOptional(observed.isOptional());parsed.setUrn(null);
        assertEquals(observed.getUrn(),reasoner.declareObservable(parsed).getUrn());parsed.setUrn(observed.getUrn());remember(parsed);
      }
    }
    List<String> failures(){try{var field=DigitalTwinImpl.TransactionImpl.class.getDeclaredField("failures");field.setAccessible(true);var result=new ArrayList<String>();for(var attempt:attempts)for(var failure:(Collection<Throwable>)field.get(attempt))result.add(failure.getMessage());return result;}catch(Exception failure){throw new IllegalStateException(failure);}}
    List<String> failureCauses(){try{var field=DigitalTwinImpl.TransactionImpl.class.getDeclaredField("failures");field.setAccessible(true);var result=new ArrayList<String>();for(var attempt:attempts)for(var failure:(Collection<Throwable>)field.get(attempt))for(var cause=failure;cause!=null;cause=cause.getCause())result.add(cause.getMessage());return result;}catch(Exception failure){throw new IllegalStateException(failure);}}
    CompletableFuture<Observation> submit(){var o=new ObservationImpl();o.setObservable(reasoner.declareObservable(output));o.setGeometry(geometry);return runtime.submit(o,scope);}
    public void close(){resolverExecutor.shutdownNow();scheduler.close();for(var store:stores.values())store.close(null);semantic.close();runtime.shardExecution().close();}
  }
  static StorageManagerImpl diskStorage(ServiceContextScope scope,java.nio.file.Path path,String workspace)throws Exception {
    var constructor=StorageManagerImpl.class.getDeclaredConstructor(ServiceContextScope.class,File.class,File.class);constructor.setAccessible(true);
    return constructor.newInstance(scope,path.resolve(workspace).toFile(),path.resolve("persistent").toFile());
  }
  @Test void standaloneSingleShardWriterRetainsItsOwnGeometryOnColdReload()throws Exception {
    try(var f=new Fixture(true);var backend=new org.integratedmodelling.klab.services.runtime.neo4j.NativeFamilyRecovery()) {
      var strategy=new Data.ShardingStrategy();strategy.setDataType(Storage.Type.DOUBLE);strategy.setCurve(Data.FillCurve.D1_LINEAR);
      var shard=new org.integratedmodelling.klab.common.data.impl.ShardImpl(f.geometry,f.region,strategy,0,1,1000,Persistence.EXPLICIT_ACTION,Storage.Type.DOUBLE);
      var restored=backend.standaloneShard(shard,f.scope);assertTrue(restored.getId()>0);assertEquals(1,restored.getShardCount());
      assertNotSame(shard,restored);assertNotNull(restored.getGeometry());assertEquals(GeometryRepository.INSTANCE.sanitize(shard.getGeometry()).encode(),GeometryRepository.INSTANCE.sanitize(restored.getGeometry()).encode());
    }
  }
  @Test void ordinaryCartesianSubmissionCommitsNativeRootAndColdReopensNumericStorage()throws Exception {
    var path=java.nio.file.Files.createTempDirectory("klab-native-root-");
    try(var f=new Fixture(true);var backend=new org.integratedmodelling.klab.services.runtime.neo4j.NativeFamilyRecovery()) {
      f.cartesian();f.remember(f.observable(f.semantic.register(f.semantic.named("Region"))));f.scope.setId("automatic-native-root");f.region.getMetadata().put("fixture.crop","Wheat");f.region.getMetadata().put("fixture.season","Warm");
      // This parser transport retains the compound member syntax that production Resources parses.
      for(var crop:List.of("Wheat","Maize"))for(var season:List.of("Warm","Cool")) {
        var member=f.semantic.named("Region");member.getTraits().add(f.semantic.named(crop));member.getTraits().add(f.semantic.named(season));
        f.remember(f.observable(f.semantic.register(member)));
      }
      var graph=backend.initializeRoot(f.scope);
      when(f.twin.getKnowledgeGraph()).thenReturn(graph);field(f.twin,DigitalTwinImpl.class,"knowledgeGraph",graph);
      var storage=diskStorage(f.scope,path,"live");when(f.twin.getStorageManager()).thenReturn(storage);
      try {
        f.cohort.setId(-1);f.region.setId(-1);
        try(var tx=graph.createTransaction(f.scope)) {
          tx.store(f.cohort);tx.store(f.region);tx.link(RuntimeAsset.CONTEXT_ASSET,f.cohort,GraphModel.Relationship.HAS_CHILD);
          tx.link(f.cohort,f.region,GraphModel.Relationship.HAS_MEMBER);
          for(var member:f.classificationMembers){((ObservationImpl)member).setId(-1);tx.store(member);tx.link(f.cohort,member,GraphModel.Relationship.HAS_MEMBER);}
        }
        try(var read=graph.createTransaction(f.scope)) {
          var snapshot=read.captureClassificationMembers(f.cohort.getId());
          assertEquals(3,snapshot.members().size());assertTrue(snapshot.members().stream().anyMatch(m->m.id()==f.region.getId()));
          assertEquals(f.cohort.getObservable().getUrn(),snapshot.cohortObservable());
          assertEquals(snapshot.cohortObservable(),graph.getAsset(f.cohort.getId(),f.scope,Cohort.class).getObservable().getUrn());
          for (var captured : snapshot.members()) {
            var nativeMember = graph.materializeClassificationMember(captured, f.scope);
            var localMember = captured.id() == f.region.getId() ? f.region : f.classificationMembers.stream()
                .filter(member -> member.getId() == captured.id()).findFirst().orElseThrow();
            assertEquals(org.integratedmodelling.klab.services.runtime.neo4j.AbstractKnowledgeGraph.persistedObservationProperties(nativeMember),
                org.integratedmodelling.klab.services.runtime.neo4j.AbstractKnowledgeGraph.persistedObservationProperties(localMember),
                "Seeded local member must represent its actual native input: " + captured.id());
          }
        }
        var result=f.submit().get(60,TimeUnit.SECONDS);
        assertFalse(result.isEmpty(),result.getNotifications()+" "+f.failures()+" "+f.producerEvidence);
        assertEquals(4,f.classifier.consumedInputs.size());assertEquals(6,f.classifier.calls.get());assertEquals(1,f.classifier.valuations.get());
        assertTrue(result.getId()>0);assertTrue(f.classifier.consumedInputs.stream().allMatch(i->i.observation().getId()>0));
        try(var read=StorageReads.open(result,f.scope,null,Data.FillCurve.D1_LINEAR,Storage.DoubleScanner.class)){assertEquals(10,read.scanners().getFirst().get());}
        f.prepareColdFamilySyntax();storage.close();
        var cold=backend.coldScope(f.scope);doReturn(f.scope.getConfiguration()).when(cold).getConfiguration();doReturn(f.scope.getId()).when(cold).getId();
        var coldStorage=diskStorage(cold,path,"cold");when(cold.getDigitalTwin().getStorageManager()).thenReturn(coldStorage);
        try {
          var restored=cold.getObservation(result.getId());assertNotNull(restored);assertNotSame(result,restored);
          var restoredContext=cold.getObservation(f.region.getId());assertNotNull(restoredContext);
          assertEquals(6,backend.children(f.region.getId()));assertEquals(backend.children(f.region.getId()),restoredContext.getChildrenCount());
          var inputs=new ArrayList<DependencyFamily.Input>();for(var family:cold.getDependencyFamilies(restored))inputs.addAll(cold.getDependencyInputs(restored,family.id()));
          assertEquals(4,inputs.size());assertEquals(f.classifier.consumedInputs.stream().map(i->i.observation().getId()).collect(java.util.stream.Collectors.toSet()),inputs.stream().map(i->i.observation().getId()).collect(java.util.stream.Collectors.toSet()));
          try(var read=StorageReads.open(restored,cold,null,Data.FillCurve.D1_LINEAR,Storage.DoubleScanner.class)){assertEquals(10,read.scanners().getFirst().get());}
          var values=new TreeSet<Double>();for(var input:inputs)try(var read=StorageReads.open(input.observation(),cold,null,Data.FillCurve.D1_LINEAR,Storage.DoubleScanner.class)){values.add(read.scanners().getFirst().get());}
          assertEquals(Set.of(1.0,2.0,3.0,4.0),values);
        } finally {coldStorage.close();}
      } finally {storage.close();}
    }
  }
  @Test @org.junit.jupiter.api.Timeout(120)
  void actualSubmissionCommitsSelectedAgentAndColdRecoveryRetainsItsIdentity()throws Exception {
    var path=java.nio.file.Files.createTempDirectory("klab-observer-commit-");
    try(var f=new Fixture(true);var backend=new org.integratedmodelling.klab.services.runtime.neo4j.NativeFamilyRecovery()) {
      f.scope.setId("observer-native-root");
      var parent=(KimModel)f.sources.values().stream().filter(ns->ns.getSourceCode().equals("parent")).findFirst().orElseThrow().getStatements().getFirst();
      parent.getDependencies().clear();parent.getContextualization().clear();parent.getContextualization().add(f.expression("11"));
      var observerSyntax=f.semantic.observerVocabulary();f.remember(observerSyntax);
      var observer=new ObservationImpl();observer.setObservable(f.reasoner.declareObservable(observerSyntax));observer.setGeometry(f.geometry);
      var graph=backend.initializeRoot(f.scope);
      when(f.twin.getKnowledgeGraph()).thenReturn(graph);field(f.twin,DigitalTwinImpl.class,"knowledgeGraph",graph);
      var storage=diskStorage(f.scope,path,"live");when(f.twin.getStorageManager()).thenReturn(storage);
      try {
        f.cohort.setId(-1);f.region.setId(-1);
        try(var tx=graph.createTransaction(f.scope)) {
          tx.store(f.cohort);tx.store(f.region);tx.store(observer);
          tx.link(RuntimeAsset.CONTEXT_ASSET,f.cohort,GraphModel.Relationship.HAS_CHILD);
          tx.link(f.cohort,f.region,GraphModel.Relationship.HAS_MEMBER);
          tx.link(RuntimeAsset.CONTEXT_ASSET,observer,GraphModel.Relationship.HAS_CHILD);
        }
        var selected=f.scope.withResolutionConstraints(org.integratedmodelling.klab.api.services.resolver.ResolutionConstraint.of(
            org.integratedmodelling.klab.api.services.resolver.ResolutionConstraint.Type.ObserverUrn,observer.getUrn()));
        var request=new ObservationImpl();request.setObservable(f.reasoner.declareObservable(f.output));request.setGeometry(f.geometry);
        var result=f.runtime.submit(request,selected).get(60,TimeUnit.SECONDS);
        assertFalse(result.isEmpty(),result.getNotifications()+" "+f.failures());assertTrue(result.getId()>0);
        assertNull(result.getObservable().getObserverSemantics(),"Selected Agent must not synthesize a query/output clause");
        storage.close();var cold=backend.coldGraph();when(f.twin.getKnowledgeGraph()).thenReturn(cold);
        field(f.twin,DigitalTwinImpl.class,"knowledgeGraph",cold);
        var coldStorage=diskStorage(f.scope,path,"cold");when(f.twin.getStorageManager()).thenReturn(coldStorage);
        try {
          var restored=cold.getAsset(result.getId(),f.scope,Observation.class);assertNotSame(result,restored);
          var recovered=org.integratedmodelling.common.runtime.ObserverProvenance.recover(f.scope,restored);
          assertTrue(recovered.known());assertEquals(observer.getId(),recovered.observer().getId());
          assertEquals(observer.getUrn(),recovered.observer().getUrn());
          assertEquals(observer.getId(),f.scope.of(restored).getObserver().getId());assertNull(f.scope.getObserver());
          try(var read=StorageReads.open(restored,f.scope,null,Data.FillCurve.D1_LINEAR,Storage.DoubleScanner.class)) {
            assertEquals(11,read.scanners().getFirst().get());
          }
        } finally {coldStorage.close();}
      } finally {storage.close();}
    }
  }
  @Test void ordinarySelectedModelClassifiesThenExecutesSingletonInputAndParent()throws Exception {
    try(var f=new Fixture()){
      var target=f.reasoner.declareObservable(f.output);
      var concreteSyntax=new KimObservableImpl(f.input);concreteSyntax.setOptional(true);
      var concrete=f.reasoner.declareObservable(concreteSyntax);var nativeBearer=f.reasoner.inherent(concrete);
      assertNotNull(nativeBearer,"No native bearer for "+concrete.getUrn());
      assertTrue(f.reasoner.is(f.region.getObservable().getSemantics(),nativeBearer),
          "Fixture native proof: "+f.region.getObservable().getSemantics().getUrn()+" -> "+nativeBearer.getUrn()+" input="+concrete.getSemantics().getUrn());
      var source=f.sources.values().stream().filter(ns->ns.getSourceCode().equals("parent")).findFirst().orElseThrow();
      var sourceModel=(KimModel)source.getStatements().getFirst();
      var load=ResolverService.class.getDeclaredMethod("loadModel",KimModel.class,KimNamespace.class,Scope.class);load.setAccessible(true);
      var model=(Model)load.invoke(f.resolver,sourceModel,source,f.scope);
      var receipt=org.integratedmodelling.klab.api.services.reasoner.objects.SemanticValidationRequest.of(source,"1");receipt.setExecutableModels(true);receipt.setKnowledgeRevision(3);
      var validation=f.reasoner.validateDocument(receipt,f.scope);
      assertTrue(validation.valid(),validation.getReason()+" "+validation.getNotifications());
      assertTrue(org.integratedmodelling.klab.runtime.language.ModelValidationGate.validatedRevision(model,f.scope,target)>=0,"Executable gate rejected fixture source: "+validation.getNotifications());
      var distance=f.reasoner.semanticDistance(model.getObservables().getFirst(),target,f.region.getObservable(),3);
      assertTrue(distance>=0,"Actual native contextual distance rejected fixture: "+distance);
      var result=f.submit().get(30,TimeUnit.SECONDS);assertFalse(result.isEmpty(),result.getNotifications()+"; model queries="+f.modelQueries+"; discoveries="+f.classifier.discoveries+"; producer="+f.producerEvidence+"; failures="+f.failures());
      try(var read=StorageReads.open(result,f.scope,null,Data.FillCurve.D1_LINEAR,Storage.DoubleScanner.class)){assertEquals(7,read.scanners().getFirst().get());}
      assertTrue(f.modelQueries.contains(f.reasoner.declareObservable(f.input).getUrn()+" optional"));
      assertEquals(0,f.classifier.calls.get(),"Existing valid attribution is reused after actual classifier planning/dispatch");
      assertEquals(1,f.classifier.discoveries.get(),"A genuine successful producer ran before snapshot capture");
      assertEquals(1,f.rootCommits.get());
      assertEquals(41,result.getParentId());
      var concreteChild=f.durable.values().stream().filter(o->o.getObservable().getSemantics().getUrn().equals(f.input.getSemantics().getUrn())).findFirst().orElseThrow();
      assertTrue(concreteChild.getId()>0);assertEquals(41,concreteChild.getParentId());
      assertEquals(Storage.Type.DOUBLE,concreteChild.getContextualizationData().getNativeShardingStrategy().getDataType());
      assertTrue(concreteChild.getAnnotations().stream().anyMatch(a->"type".equals(a.getName())));
      try(var read=StorageReads.open(concreteChild,f.scope,null,Data.FillCurve.D1_LINEAR,Storage.DoubleScanner.class)){assertEquals(3,read.scanners().getFirst().get());}
    }
  }
  @Test void precomputedTierZeroDoesNotPlanOrDiscoverClassifiers() throws Exception {
    try(var f=new Fixture()) {
      var parent=(KimModel)f.sources.values().stream().filter(ns->ns.getSourceCode().equals("parent")).findFirst().orElseThrow().getStatements().getFirst();
      parent.getDependencies().clear();parent.getContextualization().clear();parent.getContextualization().add(f.expression("11"));
      var result=f.submit().get(30,TimeUnit.SECONDS);
      assertFalse(result.isEmpty(),result.getNotifications()+" "+f.producerEvidence);
      try(var read=StorageReads.open(result,f.scope,null,Data.FillCurve.D1_LINEAR,Storage.DoubleScanner.class)){assertEquals(11,read.scanners().getFirst().get());}
      assertEquals(1,f.modelQueries.size());assertEquals(0,f.classifier.discoveries.get());assertEquals(0,f.classifier.calls.get());
    }
  }
  @Test void recursiveClassifierProducerPlanTerminatesBeforeExecution() throws Exception {
    try(var f=new Fixture()) {
      var producer=(KimModel)f.sources.values().stream().filter(ns->ns.getSourceCode().equals("producer")).findFirst().orElseThrow().getStatements().getFirst();
      producer.getDependencies().add(f.original);
      var result=f.submit().get(30,TimeUnit.SECONDS);
      assertTrue(result.isEmpty());assertTrue(f.modelQueries.size()<20,f.modelQueries.toString());
      assertEquals(0,f.classifier.discoveries.get());assertEquals(0,f.rootCommits.get());
    }
  }
  @Test void missingRequiredClassifierRejectsUnknownMemberWithoutPublishing() throws Exception {
    try(var f=new Fixture(true)) {
      f.region.setObservable(f.reasoner.declareObservable(f.observable(f.semantic.named("Region"))));
      assertEquals("audit:Region",f.region.getObservable().getSemantics().getUrn());
      assertEquals(1,f.memberReadSet().members().size());
      assertTrue(f.modelForObservable.entrySet().removeIf(e->e.getValue().endsWith(".classifier")),"Fixture classifier mapping must exist before removal");
      var result=f.submit().get(30,TimeUnit.SECONDS);
      assertTrue(result.isEmpty(),result.getNotifications()+" "+f.failures());
      // Discovery must complete before a model-free check can distinguish known from unknown members.
      assertEquals(1,f.classifier.discoveries.get());assertEquals(0,f.classifier.calls.get());assertEquals(0,f.rootCommits.get());
      assertTrue(f.failureCauses().stream().anyMatch(message->message!=null && message.contains("Unclassified member requires a classifier model")),f.failureCauses().toString());
      assertEquals("audit:Region",f.region.getObservable().getSemantics().getUrn());
      verify(f.nativeTx,never()).updateSemantics(any(),anyString());
    }
  }

  @Test void existingClassificationNeedsNoClassifierModelAndPreservesNativeIdentity() throws Exception {
    try(var f=new Fixture()) {
      long memberId=f.region.getId();var original=f.region.getObservable();
      assertTrue(f.reasoner.directTraits(original).stream().anyMatch(trait->trait.getUrn().equals("audit:Concrete")));
      assertTrue(f.modelForObservable.entrySet().removeIf(e->e.getValue().endsWith(".classifier")),"Fixture classifier mapping must exist before removal");
      var result=f.submit().get(30,TimeUnit.SECONDS);
      assertFalse(result.isEmpty(),result.getNotifications()+" "+f.failures());
      try(var read=StorageReads.open(result,f.scope,null,Data.FillCurve.D1_LINEAR,Storage.DoubleScanner.class)){assertEquals(7,read.scanners().getFirst().get());}
      assertEquals(memberId,f.region.getId());assertSame(original,f.region.getObservable());
      assertEquals(0,f.classifier.calls.get());assertEquals(1,f.classifier.discoveries.get());assertEquals(1,f.rootCommits.get());
      assertTrue(f.modelQueries.contains("audit:Category of each audit:Region"));
      verify(f.nativeTx,never()).updateSemantics(any(),anyString());
    }
  }

  @Test void failedProducerDoesNotBecomeSuccessfulEmpty() throws Exception {
    try(var f=new Fixture()) {
      f.classifier.discoveryAction=()->{throw new IllegalStateException("fixture producer failure");};
      var result=f.submit().get(30,TimeUnit.SECONDS);
      assertTrue(result.isEmpty());assertEquals(1,f.classifier.discoveries.get());assertEquals(0,f.classifier.calls.get());assertEquals(0,f.rootCommits.get());
      assertFalse(f.modelQueries.contains(f.reasoner.declareObservable(f.input).getUrn()));
    }
  }
  @Test void cancellationDuringProducerRollsBackParent() throws Exception {
    try(var f=new Fixture()) {
      f.classifier.discoveryAction=()->f.cancelled.set(true);
      var failure=assertThrows(ExecutionException.class,()->f.submit().get(30,TimeUnit.SECONDS));
      assertInstanceOf(CancellationException.class,failure.getCause());
      assertEquals(0,f.rootCommits.get());assertEquals(0,f.classifier.calls.get());
    }
  }

  @Test void selectedSourceMutationDuringProducerCannotPublishParent() throws Exception {
    try(var f=new Fixture()) {
      var source=f.sources.values().stream().filter(ns->ns.getSourceCode().equals("parent")).findFirst().orElseThrow();
      f.classifier.discoveryAction=()->((KimNamespaceImpl)source).setLastUpdateTimestamp(2);
      assertTrue(f.submit().get(30,TimeUnit.SECONDS).isEmpty());assertEquals(0,f.rootCommits.get());
    }
  }
  @Test void changedPersistedMemberReadSetCannotPublishParent() throws Exception {
    try(var f=new Fixture()) {
      doThrow(new IllegalStateException("fixture member revision changed")).when(f.nativeTx).validateClassificationMembers(any());
      assertTrue(f.submit().get(30,TimeUnit.SECONDS).isEmpty());assertEquals(0,f.rootCommits.get());
    }
  }

  @Test void planSnapshotIgnoresOnlyDerivedMetadataLabels() throws Exception {
    var mapper=JacksonConfiguration.newObjectMapper();var plan=new ActuatorImpl();
    var call=new ServiceCallImpl("fixture.classify",Metadata.SUGGESTED_NAME,"scientific argument");plan.getComputation().add(call);
    var method=ClassifierDependencyContinuation.class.getDeclaredMethod("planSnapshot",com.fasterxml.jackson.databind.ObjectMapper.class,Actuator.class);method.setAccessible(true);
    var original=method.invoke(null,mapper,plan);
    call.getParameters().put(Metadata.SUGGESTED_NAME,"changed scientific argument");
    assertNotEquals(original,method.invoke(null,mapper,plan),"Computational parameters cannot use the presentation exemption");
    var observation=new ObservationImpl();var observable=new ObservableImpl();observable.setUrn("test:Meaning");var meaning=new ConceptImpl();meaning.setUrn("test:Meaning");meaning.setType(EnumSet.of(SemanticType.SUBJECT,SemanticType.OBSERVABLE,SemanticType.COUNTABLE));observable.setSemantics(meaning);observation.setObservable(observable);
    plan.setObservation(observation);var first=method.invoke(null,mapper,plan);
    observation.getMetadata().put(Metadata.SUGGESTED_NAME,"Derived display");
    assertEquals(first,method.invoke(null,mapper,plan));
  }
  @Test void executableRootRejectsChangedReferenceOrQueryIdentityBeforeAllocation() throws Exception {
    try(var f=new Fixture()) {
      var plan=new ActuatorImpl();plan.setActuatorType(Actuator.Type.REFERENCE);plan.setObservation(Observation.forTransport(f.region));plan.setId(41);
      var changed=Observation.forTransport(f.region);((ObservationImpl)changed).setId(42);
      assertThrows(IllegalArgumentException.class,()->new CompiledDataflow(f.runtime,changed,f.scope).compile(plan));
      ((ObservationImpl)changed).setId(Observation.QUERY_ID);
      assertThrows(IllegalArgumentException.class,()->new CompiledDataflow(f.runtime,changed,f.scope).compile(plan));
      assertTrue(f.stores.isEmpty());assertEquals(0,f.rootCommits.get());
    }
  }

  @Test void concreteAliasCannotOverwriteAnOrdinaryInput() throws Exception {
    try(var f=new Fixture()) {
      var parent=(KimModel)f.sources.values().stream().filter(ns->ns.getSourceCode().equals("parent")).findFirst().orElseThrow().getStatements().getFirst();
      f.input.setFormalName("signal");parent.getDependencies().add(f.input);
      var result=f.submit().get(30,TimeUnit.SECONDS);
      assertTrue(result.isEmpty());assertEquals(0,f.rootCommits.get());
      assertTrue(result.getNotifications().toString().contains("Duplicate or reserved"),result.getNotifications().toString());
    }
  }

  @Test void optionalGeneratedNoModelOmitsInputButStillRequiresClassifierWork() throws Exception {
    try(var f=new Fixture()) {
      var parent=(KimModel)f.sources.values().stream().filter(ns->ns.getSourceCode().equals("parent")).findFirst().orElseThrow().getStatements().getFirst();
      parent.getContextualization().clear();parent.getContextualization().add(f.expression("17"));
      f.modelForObservable.entrySet().removeIf(e->e.getValue().endsWith(".input"));
      var result=f.submit().get(30,TimeUnit.SECONDS);
      assertFalse(result.isEmpty(),result.getNotifications()+" "+f.failures());
      try(var read=StorageReads.open(result,f.scope,null,Data.FillCurve.D1_LINEAR,Storage.DoubleScanner.class)){assertEquals(17,read.scanners().getFirst().get());}
      assertEquals(1,f.classifier.discoveries.get());assertEquals(1,f.rootCommits.get());
    }
  }

  @Test void precomputedAlternativeBeatsAnEarlierClassifierDependentCandidate() throws Exception {
    try(var f=new Fixture()) {
      String recursive=f.orderedModel("parent","a");
      f.model("precomputed",f.output,List.of(),f.expression("19"));
      String precomputed=f.orderedModel("precomputed","z");f.competingParents=List.of(recursive,precomputed);
      assertTrue(recursive.compareTo(precomputed)<0,"Equal scientific ranking puts recursive candidate first by stable URN");
      var result=f.submit().get(30,TimeUnit.SECONDS);
      assertFalse(result.isEmpty(),result.getNotifications()+" "+f.failures());
      try(var read=StorageReads.open(result,f.scope,null,Data.FillCurve.D1_LINEAR,Storage.DoubleScanner.class)){assertEquals(19,read.scanners().getFirst().get());}
      assertEquals(0,f.classifier.discoveries.get());assertEquals(0,f.classifier.calls.get());
      assertEquals(List.of(f.reasoner.declareObservable(f.output).getUrn()),f.modelQueries,
          "The classifier and member producer were not even planned before successful Tier-0");
    }
  }

  @Test void laterPrecomputedStrategyWinsBeforeEarlierStrategyCanPlanClassifiers() throws Exception {
    try(var f=new Fixture()) {
      String recursive=f.orderedModel("parent","a");f.model("precomputed",f.output,List.of(),f.expression("23"));
      String precomputed=f.orderedModel("precomputed","z");
      f.duplicateRootStrategies=true;f.parentQueryPages=List.of(List.of(recursive),List.of(precomputed));
      var result=f.submit().get(30,TimeUnit.SECONDS);
      assertFalse(result.isEmpty(),result.getNotifications()+" "+f.failures());
      try(var read=StorageReads.open(result,f.scope,null,Data.FillCurve.D1_LINEAR,Storage.DoubleScanner.class)){assertEquals(23,read.scanners().getFirst().get());}
      assertEquals(0,f.classifier.discoveries.get());assertEquals(0,f.classifier.calls.get());assertEquals(2,f.modelQueries.size());
      assertTrue(f.modelQueries.stream().allMatch(urn->urn.equals(f.output.getUrn())));
    }
  }

  @Test void nestedDependencyCannotReenableClassifierWorkDuringStrictTierZero() throws Exception {
    try(var f=new Fixture()) {
      var parent=(KimModel)f.sources.values().stream().filter(ns->ns.getSourceCode().equals("parent")).findFirst().orElseThrow().getStatements().getFirst();
      parent.getDependencies().clear();f.input.setFormalName("signal");parent.getDependencies().add(f.input);
      var inputModel=(KimModel)f.sources.values().stream().filter(ns->ns.getSourceCode().equals("input")).findFirst().orElseThrow().getStatements().getFirst();
      inputModel.getDependencies().add(f.original);
      String recursive=f.orderedModel("parent","a");f.model("precomputed",f.output,List.of(),f.expression("29"));
      String precomputed=f.orderedModel("precomputed","z");f.competingParents=List.of(recursive,precomputed);
      var result=f.submit().get(30,TimeUnit.SECONDS);
      assertFalse(result.isEmpty(),result.getNotifications()+" "+f.failures());
      try(var read=StorageReads.open(result,f.scope,null,Data.FillCurve.D1_LINEAR,Storage.DoubleScanner.class)){assertEquals(29,read.scanners().getFirst().get());}
      assertTrue(f.modelQueries.contains(f.input.getUrn()));
      assertTrue(f.modelQueries.stream().noneMatch(urn->urn.startsWith("each ") || urn.contains(" of each ")),f.modelQueries.toString());
      assertEquals(0,f.classifier.discoveries.get());assertEquals(0,f.classifier.calls.get());
    }
  }

  @Test void partialModelInsideFallbackCannotBeComposedWithAnAutomaticClassifierParent() throws Exception {
    try(var f=new Fixture()) {
      f.model("partial",f.output,List.of(),f.expression("31"));
      String partial=f.orderedModel("partial","a"),recursive=f.orderedModel("parent","z");
      var source=f.sources.values().stream().filter(ns->ns.getSourceCode().equals("partial")).findFirst().orElseThrow();
      var resources=new ResourceSet();
      resources.getNamespaces().add(new ResourceSet.Resource("resources",source.getUrn(),null,null,KlabAsset.KnowledgeClass.NAMESPACE,1,false));
      resources.getResults().add(new ResourceSet.Resource("resources",partial,null,null,KlabAsset.KnowledgeClass.MODEL,1,false));
      var ingest=ResolverService.class.getDeclaredMethod("ingestResources",ResourceSet.class,Scope.class,Class.class,boolean.class);ingest.setAccessible(true);
      var loaded=(List<?>)ingest.invoke(f.resolver,resources,f.scope,Model.class,true);
      assertEquals(1,loaded.size());var model=(ModelImpl)loaded.getFirst();
      // Mutate the actual ingested catalogue model, avoiding a second cache registration.
      // Source loading currently defaults coverage to universal; this adapter supplies partial support.
      model.setCoverage(Geometry.create("T1(1){ttype=PHYSICAL,tstart=1000,tend=5000,tscope=4.0,tunit=SECOND}"));
      f.competingParents=List.of(partial,recursive);f.rootStrategyRank=1;
      var request=new ObservationImpl();request.setObservable(f.reasoner.declareObservable(f.output));request.setGeometry(f.geometry);
      var requested=f.scope.withResolutionConstraints(org.integratedmodelling.klab.api.services.resolver.ResolutionConstraint.of(
          org.integratedmodelling.klab.api.services.resolver.ResolutionConstraint.Type.ResolutionNamespace,source.getUrn()));
      var result=f.runtime.submit(request,requested).get(30,TimeUnit.SECONDS);
      assertTrue(result.isEmpty(),"Partial+deferred parent composition is outside this capability");
      assertEquals(0,f.classifier.discoveries.get());assertEquals(0,f.classifier.calls.get());assertEquals(0,f.rootCommits.get());
      assertEquals(List.of(f.output.getUrn()),f.modelQueries,"No classifier/member producer was planned after partial output");
    }
  }

  @Test void higherTierExplicitClassificationDependencyCannotRunDuringStrictTierZero() throws Exception {
    try(var f=new Fixture()) {
      var parent=(KimModel)f.sources.values().stream().filter(ns->ns.getSourceCode().equals("parent")).findFirst().orElseThrow().getStatements().getFirst();
      var classification=f.observables.values().stream().filter(syntax->f.reasoner.declareObservable(syntax).getContextualization()==org.integratedmodelling.klab.api.knowledge.Contextualization.CLASSIFICATION).findFirst().orElseThrow();
      parent.getDependencies().clear();parent.getDependencies().add(classification);
      parent.getContextualization().clear();parent.getContextualization().add(f.expression("3"));
      String recursive=f.orderedModel("parent","a");f.model("precomputed",f.output,List.of(),f.expression("33"));
      String precomputed=f.orderedModel("precomputed","z");f.competingParents=List.of(recursive,precomputed);f.operationStrategyRank=1;
      var result=f.submit().get(30,TimeUnit.SECONDS);
      assertFalse(result.isEmpty(),result.getNotifications()+" "+f.failures());
      try(var read=StorageReads.open(result,f.scope,null,Data.FillCurve.D1_LINEAR,Storage.DoubleScanner.class)){assertEquals(33,read.scanners().getFirst().get());}
      assertEquals(0,f.classifier.discoveries.get());assertEquals(0,f.classifier.calls.get());
      assertEquals(List.of(f.output.getUrn()),f.modelQueries,"A higher-tier semantic operation must not plan its producer during strict Tier-0");
    }
  }

  @Test void directAbstractAttributeHasPendingSourceProofButStrictExecutionStillRejectsIt() throws Exception {
    try(var f=new Fixture(true)) {
      var source=f.sources.values().stream().filter(ns->ns.getSourceCode().equals("parent")).findFirst().orElseThrow();
      var model=(KimModel)source.getStatements().getFirst();
      var concept=f.semantic.named("Input");concept.getTraits().add(f.semantic.named("Category"));concept.setInherent(f.semantic.named("Region"));f.semantic.register(concept);
      var dependency=f.observable(concept);f.remember(dependency);model.getDependencies().clear();model.getDependencies().add(dependency);
      var request=org.integratedmodelling.klab.api.services.reasoner.objects.SemanticValidationRequest.of(source,"1");request.setKnowledgeRevision(3);
      var authoring=f.reasoner.validateDocument(request,f.scope);assertTrue(authoring.valid(),authoring.getNotifications().toString());assertEquals(1,authoring.getPendingClassifierDependencies().size());
      request.setExecutableModels(true);var strict=f.reasoner.validateDocument(request,f.scope);assertFalse(strict.valid());assertTrue(strict.getPendingClassifierDependencies().isEmpty());
      request.setClassifierPreparableDependencies(true);var preparable=f.reasoner.validateDocument(request,f.scope);assertTrue(preparable.valid(),preparable.getNotifications().toString());
      assertEquals(1,preparable.getPendingClassifierDependencies().size());assertEquals(dependency.getUrn(),preparable.getPendingClassifierDependencies().getFirst().originalObservable());
      request.setClassifierPreparableDependencies(false);assertFalse(preparable.matches(request,3),"Strict requests cannot accept conditional receipts");
      assertEquals("audit:Category",dependency.getSemantics().getTraits().getFirst().getName());
    }
  }

  @Test void ordinarySubmissionUsesAConcretizedDirectQualityAttribute() throws Exception {
    try(var f=new Fixture(true)) {
      var model=(KimModel)f.sources.values().stream().filter(ns->ns.getSourceCode().equals("parent")).findFirst().orElseThrow().getStatements().getFirst();
      var abstractHead=f.semantic.named("Input");abstractHead.getTraits().add(f.semantic.named("Category"));abstractHead.setInherent(f.semantic.named("Region"));f.semantic.register(abstractHead);
      var dependency=f.observable(abstractHead);dependency.setFormalName("signal");f.remember(dependency);model.getDependencies().clear();model.getDependencies().add(dependency);
      var concreteHead=f.semantic.named("Input");concreteHead.getTraits().add(f.semantic.named("Concrete"));concreteHead.setInherent(f.semantic.named("Region"));f.semantic.register(concreteHead);
      var concrete=f.observable(concreteHead);f.remember(concrete);f.model("headInput",concrete,List.of(),f.expression("3"));
      model.getContextualization().clear();var aggregate=new ContextualizableImpl();aggregate.setServiceCall(new ServiceCallImpl("fixture.aggregate"));model.getContextualization().add(aggregate);
      var result=f.submit().get(30,TimeUnit.SECONDS);assertFalse(result.isEmpty(),result.getNotifications()+" "+f.failures());
      try(var read=StorageReads.open(result,f.scope,null,Data.FillCurve.D1_LINEAR,Storage.DoubleScanner.class)){assertEquals(3,read.scanners().getFirst().get());}
      assertEquals(1,f.classifier.discoveries.get());assertEquals(0,f.classifier.calls.get());
      assertNotNull(result.getMetadata().get(org.integratedmodelling.klab.api.services.runtime.DependencyFamily.METADATA_KEY));
      assertEquals(1,f.classifier.valuations.get());assertEquals(1,f.classifier.consumed.size());
    }
  }

  @Test void qualifiedAttributeAndSecondaryOutputNeverReceiveConditionalErasure() throws Exception {
    try(var f=new Fixture(true)) {
      var source=f.sources.values().stream().filter(ns->ns.getSourceCode().equals("parent")).findFirst().orElseThrow();
      var model=(KimModel)source.getStatements().getFirst();
      var head=f.semantic.named("Input");var predicate=f.semantic.named("Category");predicate.setGoal(f.semantic.named("Region"));head.getTraits().add(predicate);head.setInherent(f.semantic.named("Region"));f.semantic.register(head);
      var dependency=f.observable(head);f.remember(dependency);model.getDependencies().clear();model.getDependencies().add(dependency);
      var request=org.integratedmodelling.klab.api.services.reasoner.objects.SemanticValidationRequest.of(source,"1");request.setKnowledgeRevision(3);request.setExecutableModels(true);request.setClassifierPreparableDependencies(true);
      var qualified=f.reasoner.validateDocument(request,f.scope);assertFalse(qualified.valid());assertTrue(qualified.getPendingClassifierDependencies().isEmpty());

    }
  }

  @Test void missingExplicitBearerAndWrongBearerRemainIneligibleInThisBoundedMode() throws Exception {
    try(var f=new Fixture(true)) {
      var source=f.sources.values().stream().filter(ns->ns.getSourceCode().equals("parent")).findFirst().orElseThrow();var model=(KimModel)source.getStatements().getFirst();
      var head=f.semantic.named("Input");head.getTraits().add(f.semantic.named("Category"));f.semantic.register(head);var dependency=f.observable(head);f.remember(dependency);model.getDependencies().clear();model.getDependencies().add(dependency);
      var request=org.integratedmodelling.klab.api.services.reasoner.objects.SemanticValidationRequest.of(source,"1");request.setKnowledgeRevision(3);request.setExecutableModels(true);request.setClassifierPreparableDependencies(true);
      var implicit=f.reasoner.validateDocument(request,f.scope);assertFalse(implicit.valid());assertTrue(implicit.getPendingClassifierDependencies().isEmpty());
      head.setInherent(f.semantic.named("Other"));f.semantic.register(head);dependency.setUrn(head.getUrn());
      var wrong=f.reasoner.validateDocument(request,f.scope);assertFalse(wrong.valid());
    }
  }

  @Test void cropCashValueUsesFreshClassifiersAndIndependentGraphChildren() throws Exception {
    try(var f=new Fixture(true)) {
      f.crops("Wheat","Maize");var source=f.original.getUrn();
      var authored=f.sources.values().stream().filter(ns->ns.getStatements().stream().anyMatch(st->st instanceof KimModel m && m.getUrn().endsWith(".cropParent"))).findFirst().orElseThrow();
      var validation=org.integratedmodelling.klab.api.services.reasoner.objects.SemanticValidationRequest.of(authored,"1");validation.setKnowledgeRevision(3);validation.setExecutableModels(true);validation.setClassifierPreparableDependencies(true);
      var receipt=f.reasoner.validateDocument(validation,f.scope);assertTrue(receipt.valid(),receipt.getNotifications().toString());assertEquals(1,receipt.getPendingClassifierDependencies().size());assertTrue(receipt.getPendingClassifierDependencies().getFirst().residualObservable().contains("USD@2020"));
      assertNotNull(f.reasoner.declareObservable(f.original).getCurrency());
      assertEquals("USD@2020",((org.integratedmodelling.klab.api.data.mediation.impl.CurrencyImpl)f.reasoner.declareObservable(f.original).getCurrency()).getDefinition());
      assertEquals(source,f.reasoner.declareObservable(new KimObservableImpl(f.original)).getUrn(),"Currency-qualified declaration must be deterministic");
      var result=f.submit().get(30,TimeUnit.SECONDS);assertFalse(result.isEmpty(),result.getNotifications()+" "+f.failures()+" "+f.modelQueries);
      try(var read=StorageReads.open(result,f.scope,null,Data.FillCurve.D1_LINEAR,Storage.DoubleScanner.class)){assertEquals(37,read.scanners().getFirst().get());}
      assertEquals(2,f.classifier.calls.get());assertEquals(1,f.classifier.discoveries.get());assertEquals(1,f.classifier.valuations.get());assertEquals(2,f.classifier.consumed.size());
      var children=f.durable.values().stream().filter(o->o.getObservable().getSemantics().getUrn().contains("audit:HarvestCashValue")).toList();
      assertEquals(2,children.size());assertEquals(2,children.stream().map(Observation::getId).distinct().count());
      assertTrue(children.stream().allMatch(o->o.getParentId()==41));assertEquals("audit:Region",f.region.getObservable().getSemantics().getUrn());
      assertTrue(children.stream().allMatch(o->((org.integratedmodelling.klab.api.data.mediation.impl.CurrencyImpl)o.getObservable().getCurrency()).getDefinition().equals("USD@2020")));
    }
  }

  @Test void changedCropMembershipChangesConcreteChildrenAndCashValue() throws Exception {
    try(var f=new Fixture(true)) {
      f.crops("Maize");var result=f.submit().get(30,TimeUnit.SECONDS);assertFalse(result.isEmpty(),result.getNotifications()+" "+f.failures());
      try(var read=StorageReads.open(result,f.scope,null,Data.FillCurve.D1_LINEAR,Storage.DoubleScanner.class)){assertEquals(22,read.scanners().getFirst().get());}
      assertEquals(1,f.classifier.calls.get());assertEquals(1,f.classifier.valuations.get());assertEquals(1,f.classifier.consumed.size());
      assertTrue(f.classifier.consumed.getFirst().contains("audit:Maize"));
    }
  }

  @Test void secondaryQualityOutputRemainsStrictWhenSameExpressionIsAPendingDependency() throws Exception {
    try(var f=new Fixture(true)) {
      var source=f.sources.values().stream().filter(ns->ns.getSourceCode().equals("parent")).findFirst().orElseThrow();var model=(KimModel)source.getStatements().getFirst();
      var head=f.semantic.named("Input");head.getTraits().add(f.semantic.named("Category"));head.setInherent(f.semantic.named("Region"));f.semantic.register(head);
      var same=f.observable(head);f.remember(same);model.getDependencies().clear();model.getDependencies().add(same);model.getObservables().add(same);
      var request=org.integratedmodelling.klab.api.services.reasoner.objects.SemanticValidationRequest.of(source,"1");request.setKnowledgeRevision(3);request.setExecutableModels(true);request.setClassifierPreparableDependencies(true);
      var result=f.reasoner.validateDocument(request,f.scope);assertFalse(result.valid());
      assertTrue(result.getNotifications().stream().anyMatch(n->n.getMessage().contains("MODEL_DEPENDENCY_UNRESOLVED")),result.getNotifications().toString());
      assertEquals(1,result.getPendingClassifierDependencies().size(),"Only the dependency occurrence can be pending");
    }
  }

  @Test void emptySuccessfulCropClosureIsExplicitAndParentAuthoredCalculationReturnsZero() throws Exception {
    try(var f=new Fixture(true)) {
      f.crops();var result=f.submit().get(30,TimeUnit.SECONDS);assertFalse(result.isEmpty(),result.getNotifications()+" "+f.failures());
      try(var read=StorageReads.open(result,f.scope,null,Data.FillCurve.D1_LINEAR,Storage.DoubleScanner.class)){assertEquals(0,read.scanners().getFirst().get());}
      assertEquals(0,f.classifier.calls.get());assertEquals(1,f.classifier.discoveries.get());assertEquals(1,f.classifier.valuations.get());assertTrue(f.classifier.consumed.isEmpty());
      var families=JacksonConfiguration.newObjectMapper().readValue(result.getMetadata().get(org.integratedmodelling.klab.api.services.runtime.DependencyFamily.METADATA_KEY).toString(),org.integratedmodelling.klab.api.services.runtime.DependencyFamily[].class);
      assertEquals(1,families.length);assertTrue(families[0].branches().isEmpty());
    }
  }

  @Test void reusedCropAttributionsSupplyCompleteIndependentChildSet() throws Exception {
    try(var f=new Fixture(true)) {
      f.crops("Wheat","Maize");
      for(var member:f.classificationMembers)((ObservationImpl)member).setObservable(f.reasoner.declareObservable(f.observable(f.semantic.bearer(member.getMetadata().get("fixture.crop").toString()))));
      var result=f.submit().get(30,TimeUnit.SECONDS);assertFalse(result.isEmpty(),result.getNotifications()+" "+f.failures());
      try(var read=StorageReads.open(result,f.scope,null,Data.FillCurve.D1_LINEAR,Storage.DoubleScanner.class)){assertEquals(37,read.scanners().getFirst().get());}
      assertEquals(0,f.classifier.calls.get());assertEquals(1,f.classifier.discoveries.get());assertEquals(1,f.classifier.valuations.get());assertEquals(2,f.classifier.consumed.size());
    }
  }

  @Test void requiredCropClassifierFailureIsNotSuccessfulEmptyExpansion() throws Exception {
    try(var f=new Fixture(true)) {
      f.crops("Wheat","Maize");f.classifier.classification=member->null;
      var result=f.submit().get(30,TimeUnit.SECONDS);assertTrue(result.isEmpty());assertEquals(0,f.classifier.valuations.get());assertEquals(0,f.rootCommits.get());
      assertTrue(f.durable.values().stream().noneMatch(o->o.getObservable().getSemantics().getUrn().contains("HarvestCashValue")));
    }
  }

  @Test void cropTierZeroAlternativePreventsMemberDiscoveryAndClassifiers() throws Exception {
    try(var f=new Fixture(true)) {
      f.crops("Wheat","Maize");String recursive=f.orderedModel("cropParent","a");f.model("precomputedCrop",f.output,List.of(),f.expression("99"));String precomputed=f.orderedModel("precomputedCrop","z");f.competingParents=List.of(recursive,precomputed);
      var result=f.submit().get(30,TimeUnit.SECONDS);assertFalse(result.isEmpty(),result.getNotifications()+" "+f.failures());
      try(var read=StorageReads.open(result,f.scope,null,Data.FillCurve.D1_LINEAR,Storage.DoubleScanner.class)){assertEquals(99,read.scanners().getFirst().get());}
      assertEquals(0,f.classifier.discoveries.get());assertEquals(0,f.classifier.calls.get());assertEquals(0,f.classifier.valuations.get());assertEquals(List.of(f.output.getUrn()),f.modelQueries);
    }
  }

  @Test void conditionalDirectTraitOnASubjectDoesNotBecomeAQualityFamily() throws Exception {
    try(var f=new Fixture(true)) {
      var source=f.sources.values().stream().filter(ns->ns.getSourceCode().equals("parent")).findFirst().orElseThrow();var model=(KimModel)source.getStatements().getFirst();
      var head=f.semantic.named("Region");head.getTraits().add(f.semantic.named("Category"));head.setInherent(f.semantic.named("Region"));f.semantic.register(head);var dependency=f.observable(head);f.remember(dependency);model.getDependencies().clear();model.getDependencies().add(dependency);
      var request=org.integratedmodelling.klab.api.services.reasoner.objects.SemanticValidationRequest.of(source,"1");request.setKnowledgeRevision(3);request.setExecutableModels(true);request.setClassifierPreparableDependencies(true);
      var result=f.reasoner.validateDocument(request,f.scope);assertFalse(result.valid());assertTrue(result.getPendingClassifierDependencies().isEmpty());
    }
  }

  @Test void currencyQualifiedDeclarationAndTransportUseStablePortableDefinitions() throws Exception {
    try(var f=new Fixture(true)) {
      f.crops("Wheat");var original=org.integratedmodelling.common.utils.Utils.Json.asString(f.output);
      var declared=f.reasoner.declareObservable(f.output);var again=f.reasoner.declareObservable(new KimObservableImpl(f.output));
      assertEquals(declared.getUrn(),again.getUrn());assertTrue(declared.getUrn().endsWith(" in USD@2020"));
      var mapper=JacksonConfiguration.newObjectMapper();var restored=mapper.readValue(mapper.writerFor(Observable.class).writeValueAsString(declared),Observable.class);
      assertEquals(StorageScan.semantics(declared),StorageScan.semantics(restored));assertEquals(original,org.integratedmodelling.common.utils.Utils.Json.asString(f.output));
    }
  }

  @Test void compiledReferenceBranchesRetainDistinctEdgesAndRejectTargetCopyTampering() throws Exception {
    try(var f=new Fixture(true)) {
      var authored=(KimModel)f.sources.values().stream().filter(ns->ns.getSourceCode().equals("parent")).findFirst().orElseThrow().getStatements().getFirst();authored.getDependencies().clear();authored.getContextualization().clear();authored.getContextualization().add(f.expression("1"));
      var execution=f.scope.executing(Activity.of(Activity.Type.SUBMISSION,null,"Reference family contract"));
      var requested=new ObservationImpl();requested.setObservable(f.reasoner.declareObservable(f.output));requested.setGeometry(f.geometry);var target=f.runtime.register(requested,execution);
      var dataflow=f.resolver.resolve(target,execution).get(30,TimeUnit.SECONDS);assertFalse(dataflow.isEmpty());var parent=(ActuatorImpl)dataflow.getComputation().getFirst();
      var source=new ObservationImpl();source.setId(901);source.setUrn("context.cached-input");source.setObservable(f.reasoner.declareObservable(f.input));source.setGeometry(f.geometry);source.setParentId(41);source.setResolvedCoverage(1);
      var cd=new ObservationImpl.ContextualizationDataImpl();cd.setNativeShardingStrategy(new Data.ShardingStrategy(Data.FillCurve.D1_LINEAR,1,0,0,Storage.Type.DOUBLE));source.setContextualizationData(cd);f.durable.put(901L,source);
      var optionalSyntax=new KimObservableImpl(f.input);optionalSyntax.setOptional(true);var optional=f.reasoner.declareObservable(optionalSyntax);
      var branches=new ArrayList<org.integratedmodelling.klab.api.services.runtime.DependencyFamily.Branch>();String familyId=UUID.randomUUID().toString();
      for(int index=0;index<2;index++) {
        String id=familyId+"/"+index;branches.add(new org.integratedmodelling.klab.api.services.runtime.DependencyFamily.Branch(id,optional.getUrn(),StorageScan.semantics(optional),true,org.integratedmodelling.klab.api.services.runtime.DependencyFamily.Outcome.RESOLVED));
        var reference=new ActuatorImpl();reference.setId(901);reference.setName("cached_"+index);reference.setActuatorType(Actuator.Type.REFERENCE);reference.setObservation(source);reference.setCoverage(f.geometry);reference.setDependencyFamilyBinding(new org.integratedmodelling.klab.api.services.runtime.DependencyFamily.Binding(familyId,id,execution.getCurrentTransaction().getId(),optional.getUrn()));parent.getChildren().add(reference);
      }
      parent.getDependencyFamilies().add(new org.integratedmodelling.klab.api.services.runtime.DependencyFamily(familyId,execution.getCurrentTransaction().getId(),authored.getUrn(),0,"cached",f.input.getUrn(),List.of(List.of("semantics","traits","0")),branches));
      var compiled=new CompiledDataflow(f.runtime,target,execution,dataflow);assertTrue(compiled.compile(parent));assertTrue(compiled.store((DigitalTwinImpl.TransactionImpl)execution.getCurrentTransaction()));
      var inputs=execution.getDependencyInputs(target,familyId);assertEquals(2,inputs.size());assertEquals(1,inputs.stream().map(input->input.observation().getId()).distinct().count());assertNotEquals(inputs.get(0).branch().id(),inputs.get(1).branch().id());assertFalse(inputs.getFirst().observation().getObservable().isOptional());assertTrue(inputs.getFirst().branch().optional());
      var mapper=JacksonConfiguration.newObjectMapper();var copy=mapper.readValue(mapper.writerFor(Observation.class).writeValueAsString(target),Observation.class);copy.getMetadata().put(org.integratedmodelling.klab.api.services.runtime.DependencyFamily.METADATA_KEY,"[]");assertThrows(IllegalStateException.class,()->execution.getDependencyFamilies(copy));
      var originalInventory=target.getMetadata().get(org.integratedmodelling.klab.api.services.runtime.DependencyFamily.METADATA_KEY);
      var foreign=new org.integratedmodelling.klab.api.services.runtime.DependencyFamily(familyId,"foreign-attempt",authored.getUrn(),0,"cached",f.input.getUrn(),List.of(List.of("semantics","traits","0")),branches);
      target.getMetadata().put(org.integratedmodelling.klab.api.services.runtime.DependencyFamily.METADATA_KEY,org.integratedmodelling.common.utils.Utils.Json.asString(List.of(foreign)));
      assertThrows(IllegalStateException.class,()->execution.getDependencyInputs(target,familyId));target.getMetadata().put(org.integratedmodelling.klab.api.services.runtime.DependencyFamily.METADATA_KEY,originalInventory);
      var existingLink=execution.getCurrentTransaction().incoming(target).stream().filter(link->link.properties().containsKey(org.integratedmodelling.klab.api.services.runtime.DependencyFamily.Binding.PROPERTY)).findFirst().orElseThrow();
      execution.getCurrentTransaction().link(source,target,GraphModel.Relationship.AFFECTS,org.integratedmodelling.klab.api.digitaltwin.ProcessPlan.EDGE_ROLE,"WRONG_ROLE","readState","CURRENT",
          StorageScan.Binding.PROPERTY,existingLink.properties().get(StorageScan.Binding.PROPERTY),org.integratedmodelling.klab.api.services.runtime.DependencyFamily.Binding.PROPERTY,existingLink.properties().get(org.integratedmodelling.klab.api.services.runtime.DependencyFamily.Binding.PROPERTY));
      assertThrows(IllegalStateException.class,()->execution.getDependencyInputs(target,familyId));
      execution.getCurrentTransaction().fail(new IllegalStateException("Fixture rollback"));assertTrue(execution.commit()<0);assertEquals(0,f.rootCommits.get());
    }
  }

  @Test void missingOptionalCropValueIsRecordedAndAuthoredTotalRejectsIncompleteValuation() throws Exception {
    try(var f=new Fixture(true)) {
      f.crops("Wheat","Maize");f.modelForObservable.entrySet().removeIf(entry->entry.getValue().endsWith(".cashMaize"));
      var result=f.submit().get(30,TimeUnit.SECONDS);assertTrue(result.isEmpty());assertEquals(1,f.classifier.valuations.get());assertEquals(0,f.rootCommits.get());assertTrue(f.failures().stream().anyMatch(message->message.contains("every valued crop")),f.failures().toString());
    }
  }

  @Test void cropCancellationCannotPublishOrRunTheParent() throws Exception {
    try(var f=new Fixture(true)) {
      f.crops("Wheat","Maize");f.classifier.discoveryAction=()->f.cancelled.set(true);
      var failure=assertThrows(ExecutionException.class,()->f.submit().get(30,TimeUnit.SECONDS));assertInstanceOf(CancellationException.class,failure.getCause());assertEquals(0,f.classifier.valuations.get());assertEquals(0,f.rootCommits.get());
    }
  }

  @Test void cropMembershipMutationDuringClassifierCannotPublish() throws Exception {
    try(var f=new Fixture(true)) {
      f.crops("Wheat","Maize");var changed=new AtomicBoolean();
      f.classifier.classification=member->{if(changed.compareAndSet(false,true))f.classificationMembers.removeLast();return "audit:"+member.getMetadata().get("fixture.crop");};
      var result=f.submit().get(30,TimeUnit.SECONDS);assertTrue(result.isEmpty());assertEquals(0,f.classifier.valuations.get());assertEquals(0,f.rootCommits.get());
    }
  }

  @Test void cropSourceMutationAfterCaptureCannotPublish() throws Exception {
    try(var f=new Fixture(true)) {
      f.crops("Wheat");var source=f.sources.values().stream().filter(namespace->namespace.getSourceCode().equals("cropParent")).findFirst().orElseThrow();
      f.classifier.classification=member->{((KimNamespaceImpl)source).setLastUpdateTimestamp(2);return "audit:Wheat";};
      var result=f.submit().get(30,TimeUnit.SECONDS);assertTrue(result.isEmpty());assertEquals(0,f.classifier.valuations.get());assertEquals(0,f.rootCommits.get());
    }
  }

  @Test void nativeCropInputPayloadMutationAfterCaptureCannotPublish() throws Exception {
    try(var f=new Fixture(true)) {
      f.crops("Wheat");
      f.classifier.classification=member->{var old=f.persistedClassifierInputs.get(member.getId());var changed=new LinkedHashMap<String,Object>(old.properties());changed.put(GraphModel.Fields.METADATA,"{\"fixture.crop\":\"Maize\"}");changed.put("fixture.crop","Maize");f.persistedClassifierInputs.put(member.getId(),new KnowledgeGraph.ClassificationMemberSnapshot(old.id(),old.observable(),old.geometry(),old.semanticRevision(),changed,old.perceivedGeometry()));return "audit:Wheat";};
      var result=f.submit().get(30,TimeUnit.SECONDS);assertTrue(result.isEmpty());assertEquals(0,f.classifier.valuations.get());assertEquals(0,f.rootCommits.get());
    }
  }

  @Test void twoIndependentPredicateClosuresExecuteTheFullCartesianProduct() throws Exception {
    try(var f=new Fixture(true)) {
      f.cartesian();var result=f.submit().get(30,TimeUnit.SECONDS);assertFalse(result.isEmpty(),result.getNotifications()+" "+f.failures()+" "+f.producerEvidence);
      try(var read=StorageReads.open(result,f.scope,null,Data.FillCurve.D1_LINEAR,Storage.DoubleScanner.class)){assertEquals(10,read.scanners().getFirst().get());}
      assertEquals(4,f.classifier.calls.get());assertEquals(1,f.classifier.discoveries.get());assertEquals(1,f.classifier.valuations.get());assertEquals(4,f.classifier.consumed.size());
      var family=f.scope.getDependencyFamilies(result).getFirst();assertEquals(2,family.occurrences().size());assertEquals(4,family.branches().size());
      assertTrue(family.branches().stream().allMatch(b->b.observable().contains("USD@2020")));
      assertTrue(family.branches().stream().anyMatch(b->b.observable().contains("Wheat")&&b.observable().contains("Cool")));assertTrue(family.branches().stream().anyMatch(b->b.observable().contains("Maize")&&b.observable().contains("Warm")));
      try(var nativeGraph=new org.integratedmodelling.klab.services.runtime.neo4j.NativeFamilyRecovery()) {
        f.prepareColdFamilySyntax();var cold=nativeGraph.persist(result,f.region,f.classifier.consumedInputs,f.scope);
        assertNotSame(result,cold.target());assertEquals(family,cold.scope().getDependencyFamilies(cold.target()).getFirst());assertEquals(4,cold.inputs().size());
        assertEquals(4,cold.inputs().stream().map(i->i.observation().getId()).distinct().count());
        for(int i=0;i<4;i++) { var original=f.classifier.consumedInputs.get(i);var recovered=cold.inputs().get(i);
          assertNotSame(original.observation(),recovered.observation());assertEquals(cold.persistedIds().get(original.observation().getId()).longValue(),recovered.observation().getId());assertEquals(original.branch(),recovered.branch());assertEquals(original.storageBinding(),recovered.storageBinding());
          assertEquals(original.observation().getObservable().getUrn(),recovered.observation().getObservable().getUrn());assertEquals(GeometryRepository.INSTANCE.sanitize(original.observation().getGeometry()).encode(),GeometryRepository.INSTANCE.sanitize(recovered.observation().getGeometry()).encode());
          assertEquals(cold.target().getParentId(),recovered.observation().getParentId());
        }
      }

    }
  }

  @Test void ordinaryCropFamilyRecoversFromRealNativeStorageAndRejectsChangedEdgeEvidence() throws Exception {
    try(var f=new Fixture(true);var nativeGraph=new org.integratedmodelling.klab.services.runtime.neo4j.NativeFamilyRecovery()) {
      f.crops("Wheat","Maize");var result=f.submit().get(30,TimeUnit.SECONDS);assertFalse(result.isEmpty());
      f.prepareColdFamilySyntax();var cold=nativeGraph.persist(result,f.region,f.classifier.consumedInputs,f.scope);assertEquals(2,cold.inputs().size());
      assertNotSame(result,cold.target());assertEquals(f.scope.getDependencyFamilies(result),cold.scope().getDependencyFamilies(cold.target()));
      assertEquals(2,cold.inputs().stream().map(i->i.observation().getId()).distinct().count());
      nativeGraph.execute("MATCH ()-[r:AFFECTS]->() SET r.occurrenceRole='OTHER'");
      assertThrows(IllegalStateException.class,()->cold.scope().getDependencyInputs(cold.target(),cold.inputs().getFirst().family().id()));
    }
  }

  @Test void laterPredicateClassifierFailureRollsBackEarlierAttributions() throws Exception {
    try(var f=new Fixture(true)) {
      f.cartesian();f.classifier.byOperation=(operation,member)->operation.getSemantics().getUrn().contains("Season")?null:"audit:"+member.getMetadata().get("fixture.crop");
      var result=f.submit().get(30,TimeUnit.SECONDS);assertTrue(result.isEmpty());assertEquals(3,f.classifier.calls.get());assertEquals(0,f.classifier.valuations.get());assertEquals(0,f.rootCommits.get());
      assertTrue(f.classificationMembers.stream().allMatch(member->member.getObservable().getSemantics().getUrn().equals("audit:Region")));
      verify(f.nativeTx,never()).updateSemantics(any(),anyString());
    }
  }
  @Test void missingSecondClassifierRollsBackActualFirstFamilyAttributionsOnUnknownMembers() throws Exception {
    try(var f=new Fixture(true)) {
      f.cartesian();assertEquals(2,f.memberReadSet().members().size());
      var before=f.classificationMembers.stream().collect(java.util.stream.Collectors.toMap(Observation::getId,member->member.getObservable().getSemantics().getUrn()));
      assertTrue(before.values().stream().allMatch("audit:Region"::equals));
      var staged=new AtomicBoolean();
      doAnswer(call->{
        var pending=(List<MemberClassifierExecutor.PendingAttribution>)call.getArgument(0);
        var execution=(ServiceContextScope)call.getArgument(1);
        if(!pending.isEmpty() && pending.stream().allMatch(effect->effect.abstractPredicate().getUrn().equals("audit:Crop"))) {
          assertEquals(2,pending.size());
          var tx=(DigitalTwinImpl.TransactionImpl)execution.getCurrentTransaction();
          for(var effect:pending) assertTrue(f.reasoner.directTraits(tx.readObservation(effect.member()).getObservable()).contains(effect.predicate()),"First-family attribution must actually be staged before the missing second-family check");
          staged.set(true);
        }
        return call.callRealMethod();
      }).when(f.runtime).characterizePending(anyList(),any());
      assertTrue(f.modelForObservable.entrySet().removeIf(e->e.getValue().endsWith(".seasonClassifier")),"Fixture classifier mapping must exist before removal");
      var result=f.submit().get(30,TimeUnit.SECONDS);
      assertTrue(result.isEmpty(),result.getNotifications()+" "+f.failures());assertTrue(staged.get());
      assertEquals(1,f.classifier.discoveries.get());assertEquals(2,f.classifier.calls.get());assertEquals(0,f.classifier.valuations.get());assertEquals(0,f.rootCommits.get());
      assertTrue(f.failureCauses().stream().anyMatch(message->message!=null && message.contains("Unclassified member requires a classifier model")),f.failureCauses().toString());
      assertEquals(before,f.classificationMembers.stream().collect(java.util.stream.Collectors.toMap(Observation::getId,member->member.getObservable().getSemantics().getUrn())));
      assertTrue(f.persistedClassifierInputs.values().stream().allMatch(member->member.observable().equals("audit:Region")));
      verify(f.nativeTx,never()).updateSemantics(any(),anyString());
    }
  }

  @Test void completedEmptyFamilyNeedsNoClassifierModelAndAuthoredAggregateReturnsZero() throws Exception {
    try(var f=new Fixture(true)) {
      f.cartesian(new String[0]);
      assertEquals(Set.of(f.region.getId()),f.durable.keySet(),"The empty fixture must not retain seeded native members");
      assertTrue(f.modelForObservable.entrySet().removeIf(e->e.getValue().endsWith(".seasonClassifier")),"Fixture classifier mapping must exist before removal");
      assertTrue(f.memberReadSet().members().isEmpty());
      var result=f.submit().get(30,TimeUnit.SECONDS);
      assertFalse(result.isEmpty(),result.getNotifications()+" "+f.failures());
      try(var read=StorageReads.open(result,f.scope,null,Data.FillCurve.D1_LINEAR,Storage.DoubleScanner.class)){assertEquals(0,read.scanners().getFirst().get());}
      assertEquals(1,f.classifier.discoveries.get());assertEquals(0,f.classifier.calls.get());assertEquals(1,f.classifier.valuations.get());assertEquals(1,f.rootCommits.get());
      var families=f.scope.getDependencyFamilies(result);assertEquals(1,families.size());assertTrue(families.getFirst().branches().isEmpty());
      assertTrue(f.scope.getDependencyInputs(result,families.getFirst().id()).isEmpty());assertTrue(f.classifier.consumedInputs.isEmpty());
      assertTrue(f.memberReadSet().members().isEmpty());assertTrue(f.persistedClassifierInputs.isEmpty());
      assertEquals(3,f.durable.size(),"Only the existing context, real empty producer and authored aggregate may be observations");
      verify(f.nativeTx,atLeastOnce()).store(argThat(asset->asset instanceof Activity activity && activity.getType()==Activity.Type.INSTANTIATION && activity.getOutcome()==Activity.Outcome.SUCCESS));
      verify(f.nativeTx,never()).updateSemantics(any(),anyString());
    }
  }
  @Test void failedMandatoryCharacterizationPreventsParentPublication() throws Exception {
    try(var f=new Fixture(true)) {
      f.crops("Wheat");var lookups=new AtomicInteger();
      doAnswer(i->{if(((Observation)i.getArgument(0)).getObservable().getContextualization()==Contextualization.CHARACTERIZATION){lookups.incrementAndGet();return CompletableFuture.completedFuture(Dataflow.empty());}return i.callRealMethod();}).when(f.resolver).resolve(any(),any());
      var result=f.submit().get(30,TimeUnit.SECONDS);assertTrue(result.isEmpty());assertEquals(1,lookups.get());assertEquals(0,f.classifier.valuations.get());assertEquals(0,f.rootCommits.get());verify(f.nativeTx,never()).updateSemantics(any(),anyString());
    }
  }
  @Test void multiPredicateTierZeroSuccessAvoidsAllClassifierAndProducerWork() throws Exception {
    try(var f=new Fixture(true)) {
      f.cartesian();String recursive=f.orderedModel("cropParent","a");f.model("precomputedProduct",f.output,List.of(),f.expression("99"));String direct=f.orderedModel("precomputedProduct","z");f.competingParents=List.of(recursive,direct);
      var result=f.submit().get(30,TimeUnit.SECONDS);assertFalse(result.isEmpty());try(var read=StorageReads.open(result,f.scope,null,Data.FillCurve.D1_LINEAR,Storage.DoubleScanner.class)){assertEquals(99,read.scanners().getFirst().get());}
      assertEquals(0,f.classifier.discoveries.get());assertEquals(0,f.classifier.calls.get());assertEquals(0,f.classifier.valuations.get());
    }
  }
  @Test void producerIdentityNormalizesOnlyProvenLeafQueryPlaceholders() throws Exception {
    try(var f=new Fixture(true)) {
      var collective=f.semantic.named("Region");collective.setCollective(true);f.semantic.register(collective);
      var a=new ActuatorImpl();a.setActuatorType(Actuator.Type.OBSERVE);a.setExecutionRole(Actuator.ExecutionRole.INITIALIZATION);a.setContextualization(Contextualization.INSTANTIATION);a.setId(-3);a.getData().put(Actuator.PRODUCING_MODELS_KEY,List.of("same.namespace.producerA"));a.setName("members");a.setCoverage(f.geometry);
      var observation=new ObservationImpl();observation.setId(-3);observation.setObservable(f.reasoner.declareObservable(f.observable(collective)));observation.setGeometry(f.geometry);a.setObservation(observation);a.getComputation().add(new ServiceCallImpl("fixture.discover","transientId",1));
      var mapper=JacksonConfiguration.newObjectMapper();var b=(ActuatorImpl)mapper.readValue(mapper.writeValueAsString(a),Actuator.class);b.setId(-4);b.setTransientId(a.getTransientId()+10);((ObservationImpl)b.getObservation()).setId(-4);((ObservationImpl)b.getObservation()).setTransientId(observation.getTransientId()+10);
      assertEquals(SelectedModelClassifierContinuation.producerIdentity(a),SelectedModelClassifierContinuation.producerIdentity(b));
      b.getData().put(Actuator.PRODUCING_MODELS_KEY,List.of("same.namespace.producerB"));assertNotEquals(SelectedModelClassifierContinuation.producerIdentity(a),SelectedModelClassifierContinuation.producerIdentity(b));
      b.getData().remove(Actuator.PRODUCING_MODELS_KEY);assertNotEquals(SelectedModelClassifierContinuation.producerIdentity(a),SelectedModelClassifierContinuation.producerIdentity(b));b.getData().put(Actuator.PRODUCING_MODELS_KEY,List.of(""));assertNotEquals(SelectedModelClassifierContinuation.producerIdentity(a),SelectedModelClassifierContinuation.producerIdentity(b));b.getData().put(Actuator.PRODUCING_MODELS_KEY,List.of("same.namespace.producerA"));

      b.getComputation().clear();b.getComputation().add(new ServiceCallImpl("fixture.discover","transientId",2));assertNotEquals(SelectedModelClassifierContinuation.producerIdentity(a),SelectedModelClassifierContinuation.producerIdentity(b));
      b.getComputation().clear();b.getComputation().addAll(a.getComputation());b.setCoverage(Geometry.create("T1(1){ttype=PHYSICAL,tstart=2000,tend=10000,tscope=8.0,tunit=SECOND}"));assertNotEquals(SelectedModelClassifierContinuation.producerIdentity(a),SelectedModelClassifierContinuation.producerIdentity(b));
      b.setCoverage(f.geometry);((ObservationImpl)b.getObservation()).setParentTransientId(123);assertNotEquals(SelectedModelClassifierContinuation.producerIdentity(a),SelectedModelClassifierContinuation.producerIdentity(b));
      ((ObservationImpl)b.getObservation()).setParentTransientId(0);((ObservationImpl)b.getObservation()).setId(42);b.setId(42);assertNotEquals(SelectedModelClassifierContinuation.producerIdentity(a),SelectedModelClassifierContinuation.producerIdentity(b));
      assertEquals(-3,a.getId());assertEquals(-3,a.getObservation().getId());
    }
  }

  @Test void nativeFamilyInventoryRetainsSharedSourcesOptionalAbsenceAndSuccessfulEmpty() throws Exception {
    try(var f=new Fixture(true);var nativeGraph=new org.integratedmodelling.klab.services.runtime.neo4j.NativeFamilyRecovery()) {
      f.crops("Wheat","Maize");var result=f.submit().get(30,TimeUnit.SECONDS);assertFalse(result.isEmpty());f.prepareColdFamilySyntax();
      var first=f.classifier.consumedInputs.getFirst();var base=first.family();var duplicate=new DependencyFamily.Branch("second-binding",first.branch().observable(),first.branch().requested(),true,DependencyFamily.Outcome.RESOLVED);
      var absent=new DependencyFamily.Branch("absent-binding",first.branch().observable(),first.branch().requested(),true,DependencyFamily.Outcome.OPTIONAL_NO_MODEL);
      var family=new DependencyFamily(base.id(),base.rootAttempt(),base.modelUrn(),base.dependencyIndex(),base.localName(),base.sourceObservable(),base.occurrences(),List.of(first.branch(),duplicate,absent));
      var mapper=JacksonConfiguration.newObjectMapper();var target=mapper.readValue(mapper.writeValueAsString(result),Observation.class);target.getMetadata().put(DependencyFamily.METADATA_KEY,org.integratedmodelling.common.utils.Utils.Json.asString(List.of(family)));
      var inputs=List.of(new DependencyFamily.Input(family,first.branch(),first.observation(),first.storageBinding()),new DependencyFamily.Input(family,duplicate,first.observation(),first.storageBinding()));
      var cold=nativeGraph.persist(target,f.region,inputs,f.scope);assertEquals(family,cold.scope().getDependencyFamilies(cold.target()).getFirst());assertEquals(2,cold.inputs().size());assertEquals(1,cold.inputs().stream().map(i->i.observation().getId()).distinct().count());
      nativeGraph.execute("MATCH ()-[r:AFFECTS]->() SET r.classifierDependencyBinding=$value",Map.of("value",org.integratedmodelling.common.utils.Utils.Json.asString(new DependencyFamily.Binding(family.id(),first.branch().id(),"foreign-attempt",first.branch().observable()))));
      assertThrows(IllegalStateException.class,()->cold.scope().getDependencyInputs(cold.target(),family.id()));
      var empty=new DependencyFamily(base.id(),base.rootAttempt(),base.modelUrn(),base.dependencyIndex(),base.localName(),base.sourceObservable(),base.occurrences(),List.of());target.getMetadata().put(DependencyFamily.METADATA_KEY,org.integratedmodelling.common.utils.Utils.Json.asString(List.of(empty)));
      var recoveredEmpty=nativeGraph.persist(target,f.region,List.of(),f.scope);assertEquals(empty,recoveredEmpty.scope().getDependencyFamilies(recoveredEmpty.target()).getFirst());assertTrue(recoveredEmpty.inputs().isEmpty());
    }
  }
  @Test void nativeFamilyWritesRollBackWithoutPublishingPartialInventoryOrEdges() throws Exception {
    try(var f=new Fixture(true);var nativeGraph=new org.integratedmodelling.klab.services.runtime.neo4j.NativeFamilyRecovery()) {
      f.crops("Wheat");var result=f.submit().get(30,TimeUnit.SECONDS);assertFalse(result.isEmpty());f.prepareColdFamilySyntax();
      assertNull(nativeGraph.persist(result,f.region,f.classifier.consumedInputs,f.scope,true));assertEquals(0,nativeGraph.observations());
    }
  }

  @Test void provisionalProducerAlreadyOwnedByTheAttemptCannotBeSharedOrExecuted() throws Exception {
    try(var f=new Fixture(true)) {
      f.cartesian();
      doAnswer(i->{var actual=(CompletableFuture<Dataflow>)i.callRealMethod();var request=(Observation)i.getArgument(0);var scope=(ServiceContextScope)i.getArgument(1);
        if(!request.getObservable().getUrn().equals(f.output.getUrn()))return actual;
        return actual.thenApply(plan->{if(plan.getResolutionOutcome()==Dataflow.ResolutionOutcome.DEFERRED) {
          var producer=plan.getComputation().getFirst().getClassifierDependencies().getFirst().occurrences().getFirst().classifier().getChildren().getFirst().getObservation();
          scope.getCurrentTransaction().link(f.region,producer,GraphModel.Relationship.HAS_CHILD);
        }return plan;});
      }).when(f.resolver).resolve(any(),any());
      var result=f.submit().get(30,TimeUnit.SECONDS);assertTrue(result.isEmpty());assertEquals(0,f.classifier.discoveries.get());assertEquals(0,f.classifier.calls.get());assertEquals(0,f.rootCommits.get());assertTrue(f.failures().stream().anyMatch(failure->failure.contains("already owned")),f.failures().toString());
    }
  }

  @Test void producerIdentityPreservesAuthoredArraysEvenWithSpoofedConceptTags() {
    for(boolean spoof:List.of(false,true)) {
      var a=new ActuatorImpl();var b=new ActuatorImpl();
      var left=new LinkedHashMap<String,Object>();var right=new LinkedHashMap<String,Object>();
      if(spoof){left.put("@CLASS","org.integratedmodelling.common.knowledge.ConceptImpl");right.put("@CLASS","org.integratedmodelling.common.knowledge.ConceptImpl");}
      left.put("type",List.of("SUBJECT","OBSERVABLE"));right.put("type",List.of("OBSERVABLE","SUBJECT"));
      var first=new ServiceCallImpl("fixture.discover","payload",left);var second=new ServiceCallImpl("fixture.discover","payload",right);first.setSourceCode("same source spelling");second.setSourceCode("same source spelling");a.getComputation().add(first);b.getComputation().add(second);
      assertNotEquals(SelectedModelClassifierContinuation.producerIdentity(a),SelectedModelClassifierContinuation.producerIdentity(b));
    }
  }

  @Test void incompleteProducerProvenanceStopsAProductBeforeDiscovery() throws Exception {
    try(var f=new Fixture(true)) {
      f.cartesian();
      doAnswer(i->{var actual=(CompletableFuture<Dataflow>)i.callRealMethod();var request=(Observation)i.getArgument(0);
        if(!request.getObservable().getUrn().equals(f.output.getUrn()))return actual;
        return actual.thenApply(plan->{if(plan.getResolutionOutcome()==Dataflow.ResolutionOutcome.DEFERRED)
          for(var occurrence:plan.getComputation().getFirst().getClassifierDependencies().getFirst().occurrences())occurrence.classifier().getChildren().getFirst().getData().put(Actuator.PRODUCING_MODELS_KEY,List.of("named.producer",""));
          return plan;});
      }).when(f.resolver).resolve(any(),any());
      var result=f.submit().get(30,TimeUnit.SECONDS);assertTrue(result.isEmpty());assertEquals(0,f.classifier.discoveries.get());assertEquals(0,f.classifier.calls.get());assertEquals(0,f.rootCommits.get());assertTrue(f.failures().stream().anyMatch(failure->failure.contains("one identical unowned leaf")),f.failures().toString());
    }
  }

}
