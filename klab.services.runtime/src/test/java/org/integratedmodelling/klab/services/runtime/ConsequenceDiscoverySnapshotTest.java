package org.integratedmodelling.klab.services.runtime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.*;
import java.lang.reflect.*;
import org.integratedmodelling.common.knowledge.*;
import org.integratedmodelling.klab.api.data.*;
import org.integratedmodelling.klab.api.digitaltwin.*;
import org.integratedmodelling.klab.api.digitaltwin.impl.ConfigurationImpl;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.identities.UserIdentity;
import org.integratedmodelling.klab.api.knowledge.*;
import org.integratedmodelling.klab.api.knowledge.observation.*;
import org.integratedmodelling.klab.api.knowledge.observation.impl.ObservationImpl;
import org.integratedmodelling.klab.api.provenance.*;
import org.integratedmodelling.klab.api.provenance.impl.ActivityImpl;
import org.integratedmodelling.klab.api.services.Reasoner;
import org.integratedmodelling.klab.api.services.reasoner.objects.*;
import org.integratedmodelling.klab.api.services.runtime.objects.ResolutionCycleBinding;
import org.integratedmodelling.klab.services.runtime.neo4j.*;
import org.integratedmodelling.klab.services.runtime.digitaltwin.DigitalTwinImpl;
import org.integratedmodelling.klab.services.runtime.digitaltwin.ResolutionCycles;
import org.integratedmodelling.klab.services.scopes.*;
import org.integratedmodelling.klab.utilities.Utils;
import org.junit.jupiter.api.*;

public class ConsequenceDiscoverySnapshotTest {
  static final String FP="semantic-content-v2:"+"a".repeat(64);
  @BeforeAll public static void configure(){org.integratedmodelling.klab.configuration.ServiceConfiguration.injectInstantiators();}
  private static final ThreadLocal<List<Fixture>> fixtures=ThreadLocal.withInitial(ArrayList::new);
  @AfterEach void closeCycles(){try{for(var f:fixtures.get())f.close();}finally{fixtures.remove();}}
  public static class Fixture implements AutoCloseable {
    final RuntimeService runtime=mock(RuntimeService.class,CALLS_REAL_METHODS);
    final KnowledgeGraphNeo4j graph=mock(KnowledgeGraphNeo4j.class,CALLS_REAL_METHODS);
    final KnowledgeGraph.Transaction nativeTransaction=mock(KnowledgeGraph.Transaction.class);
    final Reasoner reasoner=mock(Reasoner.class);
    final ServiceContextScope scope;
    final ResolutionCycles cycles;
    final ResolutionCycles.Admission ownerAdmission;
    final ConceptImpl singular=new ConceptImpl(),collective=new ConceptImpl();
    final ObservableImpl one=new ObservableImpl(),each=new ObservableImpl();
    final ObservationImpl producer=new ObservationImpl(),request=new ObservationImpl();
    final CohortImpl cohort=new CohortImpl();
    final List<ObservationImpl> members=new ArrayList<>();
    final Map<Long,KnowledgeGraph.ConsequenceAssetSnapshot> assets=new LinkedHashMap<>();
    final List<KnowledgeGraph.ConsequenceEdgeSnapshot> edges=new ArrayList<>();
    public Fixture(int count)throws Exception {
      doReturn("runtime").when(runtime).serviceId();
      var serviceStatus=mock(org.integratedmodelling.klab.api.services.KlabService.ServiceStatus.class);when(serviceStatus.isOperational()).thenReturn(true);when(reasoner.status()).thenReturn(serviceStatus);doReturn(serviceStatus).when(runtime).status();var user=mock(UserIdentity.class);when(user.getId()).thenReturn("user");when(user.getUsername()).thenReturn("user");when(user.isAuthenticated()).thenReturn(true);
      var config=mock(DigitalTwin.Configuration.class);when(config.getName()).thenReturn("test");
      var original=new ServiceContextScope(new ServiceSessionScope(new ServiceUserScope(user,runtime)),config,user);original.setId("context");original.addService(reasoner);original.addService(runtime);
      var settings=mock(org.integratedmodelling.klab.api.configuration.Settings.class);
      doReturn(settings).when(runtime).settings();
      when(settings.get(org.integratedmodelling.klab.api.configuration.Setting.DISABLE_CONSEQUENCE_ENGINE,Boolean.class)).thenReturn(false);
      when(reasoner.beginSemanticCycleResult(anyString(),anyString(),anyString(),any())).thenAnswer(c->
          new SemanticCycleBeginResult(SemanticCycleBeginResult.Status.BOUND,
              new SemanticCycleBinding(SemanticCycleBinding.VERSION,"reasoner","session",1,FP,"lease-"+c.getArgument(2)),null));
      when(reasoner.bindSemanticCycle(any(),anyString(),anyString(),anyString(),any())).thenReturn(reasoner);
      // Physical storage is in-memory, but the LOCAL twin boundary and its cycle registry are
      // genuine: the scope comes from admit(), never from an envelope-only fabricated cycle.
      var twin=mock(DigitalTwinImpl.class);when(twin.getKnowledgeGraph()).thenReturn(graph);
      cycles=new ResolutionCycles(runtime,"context");when(twin.getResolutionCycles()).thenReturn(cycles);
      original.setDigitalTwin(twin);
      ownerAdmission=cycles.admit(original,true).get(5,java.util.concurrent.TimeUnit.SECONDS);
      scope=ownerAdmission.scope();fixtures.get().add(this);
      singular.setUrn("test:Stream");singular.setReferenceName("test_stream");singular.setType(EnumSet.of(SemanticType.SUBJECT,SemanticType.COUNTABLE,SemanticType.OBSERVABLE));singular.setDescriptionType(Contextualization.ACKNOWLEDGEMENT);
      collective.setUrn("each test:Stream");collective.setReferenceName("each_test_stream");collective.setType(EnumSet.copyOf(singular.getType()));collective.setCollective(true);collective.setDescriptionType(Contextualization.INSTANTIATION);
      one.setUrn(singular.getUrn());one.setSemantics(singular);one.setArtifactType(Artifact.Type.OBJECT);one.setDescriptionType(Contextualization.ACKNOWLEDGEMENT);
      each.setUrn(collective.getUrn());each.setSemantics(collective);each.setArtifactType(Artifact.Type.OBJECT);each.setDescriptionType(Contextualization.INSTANTIATION);
      when(reasoner.resolveObservable(anyString())).thenAnswer(c->c.getArgument(0).equals(each.getUrn())?each:one);
      when(reasoner.resolveConcept(anyString())).thenAnswer(c->c.getArgument(0).equals(each.getUrn())?collective:singular);
      when(reasoner.baseSubstantialType(any(),any())).thenReturn(singular);
      when(reasoner.is(any(),any())).thenAnswer(c->((Semantics)c.getArgument(0)).getUrn().equals(((Semantics)c.getArgument(1)).getUrn()));
      var caps=mock(Reasoner.Capabilities.class);when(caps.getServiceId()).thenReturn("reasoner");when(caps.getKnowledgeRevision()).thenReturn(1L);when(caps.getSemanticContentFingerprint()).thenReturn(FP);when(caps.getSemanticSessionId()).thenReturn("session");when(reasoner.capabilities(any())).thenReturn(caps);
      when(reasoner.semanticDependencies(any(),any())).thenAnswer(c->proof(c.getArgument(0)));
      producer.setId(200);producer.setUrn("context.200");producer.setObservable(each);producer.setGeometry(Geometry.create("1"));producer.setChildrenCount(count);
      request.setId(Observation.QUERY_ID);request.setObservable(each);request.setGeometry(Geometry.create("1"));
      cohort.setId(100);cohort.setUrn("context.100");cohort.setObservable(one);cohort.setGeometry(Geometry.create("1"));cohort.setChildrenCount(count);
      assets.put(100L,snapshot(cohort,GraphModel.Labels.COHORT));assets.put(200L,snapshot(producer,GraphModel.Labels.OBSERVATION));
      edges.add(new KnowledgeGraph.ConsequenceEdgeSnapshot("root-cohort",new KnowledgeGraph.ConsequenceAssetRef("root","context",List.of(GraphModel.Labels.CONTEXT)),ref(100),GraphModel.Relationship.HAS_CHILD,Map.of()));
      edges.add(edge("producer",200,100,GraphModel.Relationship.CONTRIBUTED_TO));
      for(int i=1;i<=count;i++){var member=new ObservationImpl();member.setId(i);member.setUrn("context."+i);member.setObservable(one);member.setGeometry(Geometry.create("1"));members.add(member);assets.put((long)i,snapshot(member,GraphModel.Labels.OBSERVATION));edges.add(edge("member"+i,100,i,GraphModel.Relationship.HAS_MEMBER));}
      var activity=(ActivityImpl)Activity.of(Activity.Type.INSTANTIATION);activity.setId(300);activity.setOutcome(Activity.Outcome.SUCCESS);activity.getMetadata().put(Metadata.IM_EXECUTION_GEOMETRY,"1");
      var req=new SemanticDependencyRequest("reasoner",1,FP,List.of("test:Stream"));activity.getMetadata().put(SemanticEvidenceBinding.DATA_KEY,Utils.Json.asString(new SemanticEvidenceBinding(SemanticEvidenceBinding.VERSION,true,req.obligations(),proof(req))));
      assets.put(300L,snapshot(activity,GraphModel.Labels.ACTIVITY));edges.add(edge("execution",300,200,GraphModel.Relationship.INSTANTIATED));
      doAnswer(call->{requireNativeScope(call.getArgument(1));return sourceQuery(call.getArgument(0));}).when(graph).query(any(Class.class),any(org.integratedmodelling.klab.api.scope.Scope.class));
      doAnswer(call->{requireNativeScope(call.getArgument(1));return decode(((Number)call.getArgument(0)).longValue(),call.getArgument(2));}).when(graph).getAsset(anyLong(),any(org.integratedmodelling.klab.api.scope.Scope.class),any(Class.class));
      when(graph.isObservationCurrent(any(),any())).thenAnswer(call->{
        requireNativeScope(call.getArgument(1));
        var observation=call.getArgument(0,Observation.class);
        var asset=observation==null?null:assets.get(observation.getId());
        return asset!=null && asset.labels().contains(GraphModel.Labels.OBSERVATION);
      });
      doAnswer(call->{requireNativeScope(call.getArgument(0));return nativeTransaction;}).when(graph).createTransaction(any(org.integratedmodelling.klab.api.scope.ContextScope.class));
      when(nativeTransaction.captureConsequenceInputs(anyCollection(),anyCollection())).thenAnswer(c->read(c.getArgument(0),c.getArgument(1)));
      doAnswer(c->{var expected=c.getArgument(0,KnowledgeGraph.ConsequenceReadSet.class);if(!expected.equals(read(expected.assetIds(),expected.selections())))throw new IllegalStateException("Native inputs changed");return null;}).when(nativeTransaction).validateConsequenceInputs(any());
    }
    public void qualifiedReceipt()throws Exception {
      var role=new ConceptImpl();role.setUrn("test:Helpful");role.setType(EnumSet.of(SemanticType.ROLE,SemanticType.PREDICATE));
      var family=new ConceptImpl();family.setUrn("test:RoleFamily");family.setType(EnumSet.of(SemanticType.ROLE,SemanticType.PREDICATE,SemanticType.ABSTRACT));family.setAbstract(true);
      var qualified=observable("each test:Helpful test:Stream",true);var qualifiedMember=observable("test:Helpful test:Stream",false);
      for(var member:members){member.setObservable(qualifiedMember);assets.put(member.getId(),snapshot(member,GraphModel.Labels.OBSERVATION));}
      when(reasoner.directTraits(any())).thenReturn(List.of());
      when(reasoner.directRoles(any())).thenAnswer(c->((Semantics)c.getArgument(0)).getUrn().contains("Helpful")?List.of(role):List.of());
      when(reasoner.lexicalRoot(any())).thenReturn(family);when(reasoner.satisfiable(any())).thenReturn(true);
      doAnswer(c->{var a=((Semantics)c.getArgument(0)).getUrn();var b=((Semantics)c.getArgument(1)).getUrn();return a.equals(b)||a.equals(role.getUrn())&&b.equals(family.getUrn())||a.contains("Helpful test:Stream")&&b.equals(singular.getUrn());}).when(reasoner).is(any(),any());
      var directive=new ObservableImpl();directive.setSemantics(family);directive.setUrn("test:RoleFamily of each test:Stream");directive.setDescriptionType(Contextualization.CLASSIFICATION);
      doAnswer(c->{String urn=c.getArgument(0);return urn.equals(qualified.getUrn())?qualified:urn.equals(qualifiedMember.getUrn())?qualifiedMember:urn.equals(directive.getUrn())?directive:urn.equals(each.getUrn())?each:one;}).when(reasoner).resolveObservable(anyString());
      doAnswer(c->{String urn=c.getArgument(0);return urn.equals(role.getUrn())?role:urn.equals(family.getUrn())?family:urn.equals(qualified.getUrn())?qualified.asConcept():urn.equals(qualifiedMember.getUrn())?qualifiedMember.asConcept():urn.equals(each.getUrn())?collective:singular;}).when(reasoner).resolveConcept(anyString());
      when(reasoner.buildObservable(any(),any())).thenAnswer(c->{var strategy=c.getArgument(0,ObservableBuildStrategy.class);return strategy.getBaseConcept()!=null&&strategy.getBaseConcept().getUrn().equals(family.getUrn())?directive:each;});
      var execution=(ActivityImpl)ConsequenceDiscoverySnapshot.activity(assets.get(300L));RuntimeService.recordCollectiveSemanticRevision(execution,scope);execution.getMetadata().put(Metadata.IM_OBSERVER_SELECTION_ID,0L);assets.put(300L,snapshot(execution,GraphModel.Labels.ACTIVITY));
      var classified=(ActivityImpl)Activity.of(Activity.Type.CLASSIFICATION);classified.setId(400);classified.setOutcome(Activity.Outcome.SUCCESS);var dependency=new SemanticDependencyRequest("reasoner",1,FP,List.of(role.getUrn(),singular.getUrn()));
      classified.getMetadata().put(SemanticEvidenceBinding.DATA_KEY,Utils.Json.asString(new SemanticEvidenceBinding(SemanticEvidenceBinding.VERSION,true,dependency.obligations(),proof(dependency))));
      assets.put(400L,snapshot(classified,GraphModel.Labels.ACTIVITY));for(var member:members)edges.add(new KnowledgeGraph.ConsequenceEdgeSnapshot("classified-"+member.getId(),new KnowledgeGraph.ConsequenceAssetRef("native-400",400L,List.of(GraphModel.Labels.ACTIVITY)),ref(member.getId()),GraphModel.Relationship.CLASSIFIED,Map.of("predicate",role.getUrn(),"support","1")));
      // Existing authorized local Runtime.submit/query is the only fixture issuer. The
      // captured projection below only replays matching facts, never substitutes an issuer.
      var qualifiedRequest=new ObservationImpl();qualifiedRequest.setId(Observation.QUERY_ID);qualifiedRequest.setObservable(qualified);qualifiedRequest.setGeometry(Geometry.create("1"));
      var completed=runtime.submit(qualifiedRequest,scope).join();
      if(completed.isEmpty()||SemanticExecutionEvidence.issuedForConsequence(completed,scope)==null)
        throw new IllegalStateException("Fixture authorized production query did not issue a receipt");
      request.setObservable(qualified);request.getMetadata().putAll(completed.getMetadata());
    }
    ObservableImpl observable(String urn,boolean all) {
      var meaning=new ConceptImpl();meaning.setUrn(urn);meaning.setReferenceName(urn.replace(' ','_'));meaning.setType(EnumSet.copyOf(singular.getType()));meaning.setCollective(all);meaning.setDescriptionType(all?Contextualization.INSTANTIATION:Contextualization.ACKNOWLEDGEMENT);
      var value=new ObservableImpl();value.setSemantics(meaning);value.setUrn(urn);value.setDescriptionType(meaning.getDescriptionType());value.setArtifactType(Artifact.Type.OBJECT);return value;
    }
    public Reasoner reasoner(){return reasoner;}
    public Observation request(){return request;}
    public ServiceContextScope scope(){return scope;}
    public RuntimeService runtime(){return runtime;}
    public KnowledgeGraphNeo4j graph(){return graph;}
    public KnowledgeGraph.Transaction transaction(){return nativeTransaction;}
    public Map<Long,KnowledgeGraph.ConsequenceAssetSnapshot> nativeAssets(){return assets;}
    public List<KnowledgeGraph.ConsequenceEdgeSnapshot> nativeEdges(){return edges;}
    public List<ObservationImpl> authoredMembers(){return members;}
    SemanticDependencyWitness proof(SemanticDependencyRequest req){return new SemanticDependencyWitness(SemanticDependencyWitness.VERSION,SemanticDependencyWitness.Status.COMPLETE,"reasoner",1,FP,req.inputHash(),List.of(),null);}
    KnowledgeGraph.ConsequenceAssetSnapshot snapshot(RuntimeAsset asset,String label)throws Exception {
      var method=AbstractKnowledgeGraph.class.getDeclaredMethod("asParameters",Object.class,Object[].class);method.setAccessible(true);
      var values=(Map<String,Object>)method.invoke(graph,asset,new Object[0]);values.values().removeIf(Objects::isNull);
      return new KnowledgeGraph.ConsequenceAssetSnapshot(asset.getId(),"native-"+asset.getId(),List.of(label),values,
          asset instanceof Observation o?o.getGeometry().encode():null,null);
    }
    KnowledgeGraph.ConsequenceAssetRef ref(long id){return new KnowledgeGraph.ConsequenceAssetRef("native-"+id,id,List.of(id==100?GraphModel.Labels.COHORT:id==300?GraphModel.Labels.ACTIVITY:GraphModel.Labels.OBSERVATION));}
    KnowledgeGraph.ConsequenceEdgeSnapshot edge(String id,long from,long to,GraphModel.Relationship type){return new KnowledgeGraph.ConsequenceEdgeSnapshot(id,ref(from),ref(to),type,Map.of());}
    KnowledgeGraph.ConsequenceReadSet read(Collection<Long> ids,Collection<KnowledgeGraph.ConsequenceEdgeSelection> selections) {
      var found=ids.stream().sorted().map(id->{var value=assets.get(id);if(value==null)throw new IllegalStateException("Missing asset");return value;}).toList();
      var selected=edges.stream().filter(edge->selections.stream().anyMatch(s->s.relationship()==edge.relationship()&&Objects.equals(s.direction()==GraphModel.Relationship.Direction.INCOMING?edge.target().id():edge.source().id(),s.assetId()))).sorted(Comparator.comparing(KnowledgeGraph.ConsequenceEdgeSnapshot::elementId)).toList();
      return new KnowledgeGraph.ConsequenceReadSet("context",found,List.copyOf(selections),selected);
    }
    void requireNativeScope(org.integratedmodelling.klab.api.scope.Scope incoming){if(!(incoming instanceof ServiceContextScope context))throw new SecurityException("Local bound scope required");cycles.require(context);}
    @Override public void close(){ownerAdmission.close();cycles.close();cycles.awaitQuiescence(5,java.util.concurrent.TimeUnit.SECONDS);}
    public Observation issue(){return runtime.submit(request,scope).join();}
    public ConsequenceDiscoverySnapshot.Complete capture(){return ConsequenceDiscoverySnapshot.capture(runtime,issue(),scope);}
    RuntimeAsset decode(long id,Class<?> type) {
      var snapshot=assets.get(id);if(snapshot==null)throw new IllegalStateException("Missing persisted fixture asset");
      RuntimeAsset value=snapshot.labels().contains(GraphModel.Labels.OBSERVATION)?graph.materializeConsequenceObservation(snapshot,scope)
          :snapshot.labels().contains(GraphModel.Labels.ACTIVITY)?ConsequenceDiscoverySnapshot.activity(snapshot):cohort;
      if(!type.isInstance(value))throw new IllegalStateException("Wrong persisted fixture asset type");return value;
    }
    Object sourceQuery(Class<?> type) {
      var query=mock(KnowledgeGraph.Query.class,RETURNS_SELF);var anchor=new Object[1];var outgoing=new boolean[1];var relation=new GraphModel.Relationship[1];var predicates=new ArrayList<KnowledgeGraph.Query.Criterion>();
      when(query.source(any())).thenAnswer(c->{anchor[0]=c.getArgument(0);outgoing[0]=true;return query;});
      when(query.target(any())).thenAnswer(c->{anchor[0]=c.getArgument(0);outgoing[0]=false;return query;});
      when(query.along(any(),any(Object[].class))).thenAnswer(c->{relation[0]=c.getArgument(0);return query;});
      when(query.where(anyString(),any(),any())).thenAnswer(c->{predicates.add(new KnowledgeGraph.Query.Criterion(c.getArgument(0),c.getArgument(1),c.getArgument(2)));return query;});
      when(query.run(any())).thenAnswer(c->{
        var start=(RuntimeAsset)anchor[0];var found=new LinkedHashMap<Long,RuntimeAsset>();
        for(var edge:edges)if(edge.relationship()==relation[0]) {
          var from=outgoing[0]?edge.source():edge.target();var to=outgoing[0]?edge.target():edge.source();
          boolean same=start==RuntimeAsset.CONTEXT_ASSET?Objects.equals(from.id(),"context"):Objects.equals(from.id(),start.getId());
          if(!same||!(to.id() instanceof Number number))continue;var raw=assets.get(number.longValue());if(raw==null)continue;
          if(type==Observation.class&&!raw.labels().contains(GraphModel.Labels.OBSERVATION)||type==Activity.class&&!raw.labels().contains(GraphModel.Labels.ACTIVITY)||type==Cohort.class&&!raw.labels().contains(GraphModel.Labels.COHORT))continue;
          var asset=decode(number.longValue(),type);boolean matches=true;
          for(var predicate:predicates) {
            var value=asset instanceof Observation o?o.getObservable().getUrn():asset instanceof Cohort g?g.getObservable().getUrn():null;
            matches&=predicate.operator()==KnowledgeGraph.Query.Operator.EQUALS&&predicate.field().equals(GraphModel.Fields.OBSERVABLE)&&Objects.equals(value,predicate.argument());
          }
          if(matches)found.put(asset.getId(),asset);
        }
        return List.copyOf(found.values());
      });return query;
    }
  }
  /** Records transaction boundaries, including the deliberately lower member ID discovered
   * after the cohort. Native capture owns ascending acquisition within each call. */
  static final class CaptureRecorder {
    final Fixture fixture;
    final List<List<Long>> captures=new ArrayList<>();
    final List<KnowledgeGraph.ConsequenceReadSet> earlier=new ArrayList<>();
    int open,closed,validated;
    Runnable beforeFinal=()->{};
    CaptureRecorder(Fixture fixture)throws Exception {
      this.fixture=fixture;
      doAnswer(call->{
        assertEquals(0,open,"Previous expansion locks must be released before another capture");open++;
        var transaction=mock(KnowledgeGraph.Transaction.class);var captured=new boolean[1];
        when(transaction.captureConsequenceInputs(anyCollection(),anyCollection())).thenAnswer(c->{
          assertFalse(captured[0],"A transaction may acquire only one complete sorted union");captured[0]=true;
          var ids=new ArrayList<Long>(c.getArgument(0));var sorted=new ArrayList<>(ids);Collections.sort(sorted);assertEquals(sorted,ids);
          captures.add(List.copyOf(ids));
          // Expansion reaches this fixture's complete union before the final capture.
          if(!earlier.isEmpty()&&earlier.getLast().assetIds().equals(ids)
              &&new HashSet<>(earlier.getLast().selections()).equals(new HashSet<>(c.<Collection<KnowledgeGraph.ConsequenceEdgeSelection>>getArgument(1))))beforeFinal.run();
          var reads=fixture.read(c.getArgument(0),c.getArgument(1));earlier.add(reads);return reads;
        });
        doAnswer(c->{
          var expected=c.getArgument(0,KnowledgeGraph.ConsequenceReadSet.class);
          assertTrue(earlier.subList(0,earlier.size()-1).stream().anyMatch(r->r==expected),"Final capture must validate original round receipts");
          assertEquals(1,open);validated++;
          if(!expected.equals(fixture.read(expected.assetIds(),expected.selections())))throw new IllegalStateException("Native inputs changed");
          return null;
        }).when(transaction).validateConsequenceInputs(any());
        doAnswer(c->{assertEquals(1,open);open--;closed++;return null;}).when(transaction).close();
        return transaction;
      }).when(fixture.graph).createTransaction(fixture.scope);
    }
  }
  @Test void expansionReleasesLocksBeforeLowerIdsAndFinalUnionValidatesEveryRound()throws Exception {
    var f=new Fixture(1);var issued=f.issue();var recorder=new CaptureRecorder(f);
    var complete=ConsequenceDiscoverySnapshot.capture(f.runtime,issued,f.scope);
    assertEquals(Set.of(1L),complete.memberIds());
    assertEquals(List.of(100L),recorder.captures.getFirst());
    assertTrue(recorder.captures.get(1).contains(1L),"Member lower than locked cohort is discovered later");
    assertEquals(List.of(1L,100L,200L,300L),recorder.captures.getLast());
    assertEquals(recorder.captures.size()-1,recorder.validated);
    assertEquals(recorder.captures.size(),recorder.closed);assertEquals(0,recorder.open);
  }
  @Test void driftBetweenExpansionAndFinalUnionRejectsBeforeReplay()throws Exception {
    var f=new Fixture(1);var issued=f.issue();clearInvocations(f.runtime);var recorder=new CaptureRecorder(f);
    recorder.beforeFinal=()->f.edges.removeIf(e->e.relationship()==GraphModel.Relationship.HAS_MEMBER);
    assertThrows(IllegalStateException.class,()->ConsequenceDiscoverySnapshot.capture(f.runtime,issued,f.scope));
    verify(f.runtime,never()).completedCohortReference(any(),any());
    assertEquals(recorder.captures.size(),recorder.closed);assertEquals(0,recorder.open);
  }
  @Test void publicIssuerRejectsForgedOrClosedCycleBeforeAnyQuery()throws Exception {
    var f=new Fixture(0);clearInvocations(f.graph);
    var current=f.scope.getResolutionCycleBinding();
    var forged=f.scope.withResolutionCycleBinding(new ResolutionCycleBinding(current.version(),"foreign-cycle",current.contextId(),current.runtimeServiceId(),current.consequencesEnabled(),current.semanticBinding()));
    assertThrows(java.util.concurrent.CompletionException.class,()->f.runtime.submit(f.request,forged).join());
    verify(f.graph,never()).query(any(Class.class),any());
    f.close();
    assertThrows(java.util.concurrent.CompletionException.class,f::issue);
    verify(f.graph,never()).query(any(Class.class),any());
  }
  @Test void actualCompletionReplayReturnsWholeCapturedMembershipAndRejectsValidSubsets()throws Exception {
    var f=new Fixture(2);var complete=f.capture();assertEquals(Set.of(1L,2L),complete.memberIds());complete.requireExactMembers(complete.members());complete.revalidate(f.scope);
    assertThrows(IllegalStateException.class,()->complete.requireExactMembers(List.of(f.members.getFirst())));
    assertThrows(IllegalStateException.class,()->complete.requireExactMembers(List.of(f.members.getFirst(),f.members.getFirst())));
    verify(f.runtime).completedCohortReference(any(),any());verify(f.runtime).getMembers(any(),any());assertNotNull(SemanticExecutionEvidence.issuedForConsequence(f.issue(),f.scope));
  }
  @Test void exposedMemberAndQueryBeansCannotRewritePrivateDiscoveryEvidence()throws Exception {
    var f=new Fixture(1);var proof=f.capture();var exposed=proof.members();
    ((ObservableImpl)exposed.getFirst().getObservable()).setUrn("test:Forged");
    assertThrows(IllegalStateException.class,()->proof.requireExactMembers(exposed));
    proof.requireExactMembers(proof.members());
    ((ObservableImpl)proof.completedQuery().getObservable()).setUrn("each test:Forged");
    assertEquals("each test:Stream",proof.expression());assertEquals("test:Stream",proof.members().getFirst().getObservable().getUrn());
  }
  @Test void originalLocalRuntimeQueryReceiptIsMandatoryAndTransportCopiesNeverGainAuthority()throws Exception {
    var f=new Fixture(1);var original=f.issue();assertNotNull(SemanticExecutionEvidence.issuedForConsequence(original,f.scope));
    var copy=Observation.forTransport(original);clearInvocations(f.runtime,f.graph);
    assertThrows(IllegalStateException.class,()->ConsequenceDiscoverySnapshot.capture(f.runtime,copy,f.scope));
    verify(f.runtime,never()).submit(any(),any());verify(f.graph,never()).createTransaction(any());verify(f.graph,never()).query(any(Class.class),any());
    ((ObservationImpl)original).setChildrenCount(99);
    assertNull(SemanticExecutionEvidence.issuedForConsequence(original,f.scope));
    assertThrows(IllegalStateException.class,()->ConsequenceDiscoverySnapshot.capture(f.runtime,original,f.scope));
  }
  @Test void secondaryRequestRevalidatesOriginalQueryReceiptWithoutErasingOriginalFocusGuard()throws Exception {
    var f=new Fixture(1);var complete=f.capture();var targetScope=f.scope.within(complete.members().getFirst());
    assertThrows(IllegalStateException.class,()->complete.revalidate(targetScope));complete.revalidateForRequest(targetScope);
    var other=targetScope.withResolutionCycleBinding(new ResolutionCycleBinding(1,"other","context","runtime",true,f.scope.getResolutionCycleBinding().semanticBinding()));
    assertThrows(IllegalStateException.class,()->complete.revalidateForRequest(other));
  }
  @Test void completedEmptyIsAffirmativeButMissingOrFailedExecutionIsUnavailable()throws Exception {
    var f=new Fixture(0);assertTrue(f.capture().memberIds().isEmpty());
    f.assets.remove(300L);assertThrows(IllegalStateException.class,f::capture);
    var bad=new Fixture(0);var raw=new HashMap<>(bad.assets.get(300L).nativeProperties());raw.put(GraphModel.Fields.OUTCOME,Activity.Outcome.FAILURE.name());
    bad.assets.put(300L,new KnowledgeGraph.ConsequenceAssetSnapshot(300,"native-300",List.of(GraphModel.Labels.ACTIVITY),raw,null,null));assertThrows(IllegalStateException.class,bad::capture);
  }
  @Test void originalNativeReadSetRejectsAddedRemovedAndChangedMembershipAtCommit()throws Exception {
    var f=new Fixture(2);var proof=f.capture();f.nativeTransaction.validateConsequenceInputs(proof.inputs());
    f.edges.removeIf(edge->edge.elementId().equals("member2"));assertThrows(IllegalStateException.class,()->f.nativeTransaction.validateConsequenceInputs(proof.inputs()));
    f.edges.add(f.edge("member2",100,2,GraphModel.Relationship.HAS_MEMBER));f.edges.add(f.edge("phantom",100,3,GraphModel.Relationship.HAS_MEMBER));
    assertThrows(IllegalStateException.class,()->f.nativeTransaction.validateConsequenceInputs(proof.inputs()));
  }
  @Test void tamperedSemanticActivityReceiptCannotCertifyDiscovery()throws Exception {
    var f=new Fixture(1);var original=f.assets.get(300L);var raw=new HashMap<>(original.nativeProperties());raw.put(GraphModel.Fields.METADATA,"{}");
    f.assets.put(300L,new KnowledgeGraph.ConsequenceAssetSnapshot(300,original.elementId(),original.labels(),raw,null,null));assertThrows(IllegalStateException.class,f::capture);
  }
  @Test void incompleteCapturedNeighborhoodHasNoLiveFallback()throws Exception {
    var f=new Fixture(1);var complete=f.capture();var read=complete.inputs();var partial=new KnowledgeGraph.ConsequenceReadSet(read.contextId(),read.assets(),read.selections().stream().filter(s->s.relationship()!=GraphModel.Relationship.INSTANTIATED).toList(),read.edges());
    var issued=f.issue();clearInvocations(f.graph);assertThrows(IllegalStateException.class,()->ConsequenceDiscoverySnapshot.verifyCaptured(f.runtime,issued,f.scope,100,partial,f.graph));
    verify(f.graph,never()).query(any(Class.class),any());verify(f.graph,never()).createTransaction(any());
  }
  @Test void realIssuedPredicateViewReplaysAndCapturedClassificationReceiptsCannotBeTampered()throws Exception {
    var f=new Fixture(1);f.qualifiedReceipt();var complete=f.capture();assertEquals(Set.of(1L),complete.memberIds());
    assertTrue(complete.inputs().selections().stream().anyMatch(s->s.relationship()==GraphModel.Relationship.CLASSIFIED));
    assertTrue(complete.inputs().selections().stream().anyMatch(s->s.relationship()==GraphModel.Relationship.CHARACTERIZED));
    var original=f.assets.get(400L);var properties=new HashMap<>(original.nativeProperties());properties.put(GraphModel.Fields.METADATA,"{}");
    f.assets.put(400L,new KnowledgeGraph.ConsequenceAssetSnapshot(400,original.elementId(),original.labels(),properties,null,null));
    var rejected=assertThrows(java.util.concurrent.CompletionException.class,f::capture);
    assertInstanceOf(IllegalStateException.class,rejected.getCause());
    assertTrue(rejected.getCause().getMessage().contains("Selection semantic completeness requires successful normal revalidation"));
    assertThrows(IllegalStateException.class,()->f.nativeTransaction.validateConsequenceInputs(complete.inputs()));
  }
  @Test void qualifiedViewWithoutIssuedReceiptIsRejectedAndAffirmativeEmptySelectionRemainsComplete()throws Exception {
    var f=new Fixture(0);f.qualifiedReceipt();assertTrue(f.capture().memberIds().isEmpty());
    var forged=Observation.forTransport(f.issue());forged.getMetadata().remove(CollectiveSelectionView.DATA_KEY);
    assertThrows(IllegalStateException.class,()->ConsequenceDiscoverySnapshot.capture(f.runtime,forged,f.scope));
  }
  @Test void activitySnapshotDecoderMatchesTheNativeReadAdapterExactly()throws Exception {
    var f=new Fixture(0);var raw=f.assets.get(300L);var record=mock(org.neo4j.driver.Record.class);when(record.values()).thenReturn(List.of(org.neo4j.driver.Values.value(raw.nativeProperties())));
    var result=mock(org.neo4j.driver.EagerResult.class);when(result.records()).thenReturn(List.of(record));
    var method=KnowledgeGraphNeo4j.class.getDeclaredMethod("adapt",org.neo4j.driver.EagerResult.class,Class.class,org.integratedmodelling.klab.api.scope.Scope.class);method.setAccessible(true);
    var nativeActivity=((List<Activity>)method.invoke(f.graph,result,Activity.class,f.scope)).getFirst();
    var projected=ConsequenceDiscoverySnapshot.activity(raw);assertEquals(Utils.Json.asString(nativeActivity),Utils.Json.asString(projected));
  }
}
