package org.integratedmodelling.klab.services.runtime.digitaltwin;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import org.integratedmodelling.common.knowledge.ConceptImpl;
import org.integratedmodelling.common.runtime.ResolutionOutcomeReceipt;
import org.integratedmodelling.klab.api.data.*;
import org.integratedmodelling.klab.api.digitaltwin.*;
import org.integratedmodelling.klab.api.knowledge.*;
import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.knowledge.observation.impl.ObservationImpl;
import org.integratedmodelling.klab.api.lang.kim.*;
import org.integratedmodelling.klab.api.lang.kim.impl.KimConceptImpl;
import org.integratedmodelling.klab.api.provenance.Activity;
import org.integratedmodelling.klab.api.services.reasoner.objects.*;
import org.integratedmodelling.klab.api.services.runtime.Dataflow;
import org.integratedmodelling.klab.api.services.runtime.objects.ResolutionCycleBinding;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class DefaultRoleConsequenceRuntimeTest {
  static final String FP="semantic-content-v2:"+"a".repeat(64);
  @BeforeAll static void configure(){org.integratedmodelling.klab.configuration.ServiceConfiguration.injectInstantiators();}
  static KimConcept syntax(String urn){var syntax=new KimConceptImpl();syntax.setUrn(urn);return syntax;}
  static ObservationImpl actualObservation(long id,SemanticType type) {
    var meaning=new ConceptImpl();meaning.setUrn("test:observation"+id);meaning.setType(EnumSet.of(type,SemanticType.OBSERVABLE));
    var observable=new org.integratedmodelling.common.knowledge.ObservableImpl();observable.setUrn(meaning.getUrn());observable.setSemantics(meaning);
    observable.setDescriptionType(type==SemanticType.PROCESS?Contextualization.SIMULATION:type==SemanticType.RELATIONSHIP?Contextualization.CONNECTION:Contextualization.ACKNOWLEDGEMENT);
    observable.setArtifactType(Artifact.Type.forSemantics(meaning.getType()));
    var observation=new ObservationImpl();observation.setId(id);observation.setUrn("context."+id);observation.setObservable(observable);observation.setGeometry(org.integratedmodelling.klab.api.geometry.Geometry.create("1"));observation.setParentId(RuntimeAsset.CONTEXT_ASSET_ID);return observation;
  }
  static SemanticDependencyWitness witness(String hash) {
    return new SemanticDependencyWitness(SemanticDependencyWitness.VERSION,SemanticDependencyWitness.Status.COMPLETE,"reasoner",1,FP,hash,List.of(),"complete");
  }
  static final ThreadLocal<List<Fixture>> OWNED=ThreadLocal.withInitial(ArrayList::new);
  static void cleanup(){try{for(var f:OWNED.get())f.close();}finally{OWNED.remove();org.integratedmodelling.klab.services.runtime.RoleBackendDiscoveryFixture.closeOwned();}}
  @AfterEach void closeFixtures(){cleanup();}
  static final class RegisteredTransactions extends ClassificationTransactionTest {
    final org.integratedmodelling.klab.services.runtime.RuntimeService runtime;
    ResolutionCycles registry;ResolutionCycles.Admission owner;
    RegisteredTransactions(org.integratedmodelling.klab.services.runtime.RuntimeService runtime){this.runtime=runtime;}
    @Override void setup()throws Exception {
      super.setup();when(runtime.serviceId()).thenReturn("runtime");when(runtime.settings()).thenReturn(mock(org.integratedmodelling.klab.api.configuration.Settings.class));
      var status=mock(org.integratedmodelling.klab.api.services.KlabService.ServiceStatus.class);when(status.isOperational()).thenReturn(true);when(reasoner.status()).thenReturn(status);
      when(reasoner.beginSemanticCycleResult(anyString(),anyString(),anyString(),any())).thenAnswer(call->new SemanticCycleBeginResult(SemanticCycleBeginResult.Status.BOUND,
          new SemanticCycleBinding(SemanticCycleBinding.VERSION,"reasoner","session",1,FP,"lease-"+call.getArgument(2)),null));
      when(reasoner.bindSemanticCycle(any(),anyString(),anyString(),anyString(),any())).thenReturn(reasoner);
      var user=mock(org.integratedmodelling.klab.api.identities.UserIdentity.class);when(user.getUsername()).thenReturn("tester");when(user.getId()).thenReturn("tester");when(user.isAuthenticated()).thenReturn(true);
      var neutral=new org.integratedmodelling.klab.services.scopes.ServiceContextScope(new org.integratedmodelling.klab.services.scopes.ServiceSessionScope(new org.integratedmodelling.klab.services.scopes.ServiceUserScope(user,runtime)),
          DigitalTwin.Configuration.builder().id("context").name("test").build(),user);neutral.setId("context");neutral.setDigitalTwin(twin);neutral.addService(reasoner);neutral.addService(runtime);
      registry=new ResolutionCycles(runtime,"context");field("resolutionCycles",registry);when(twin.getResolutionCycles()).thenReturn(registry);
      owner=registry.admit(neutral,true).get(5,java.util.concurrent.TimeUnit.SECONDS);
      scope=spy(owner.scope().forConsequence(new Consequence.Cause(1,"context",1,2,0,null,null,"decision",Consequence.Kind.ROLE_CONFERRAL)));
      when(kg.createTransaction(any())).thenReturn(storage);
      var software=mock(org.integratedmodelling.klab.api.provenance.Agent.class);when(software.getName()).thenReturn("k.LAB");when(software.getId()).thenReturn(3L);when(kg.klab()).thenReturn(software);
      var trigger=(org.integratedmodelling.klab.api.provenance.impl.ActivityImpl)Activity.of(Activity.Type.SUBMISSION);trigger.setId(2);trigger.setOutcome(Activity.Outcome.SUCCESS);
      when(kg.getAsset(eq(2L),argThat(candidate->candidate instanceof org.integratedmodelling.klab.services.scopes.ServiceContextScope context&&context.getId().equals("context")&&context.getUser()!=null&&context.getUser().getUsername().equals("tester")&&context.getResolutionCycle()==owner.scope().getResolutionCycle()),eq(Activity.class))).thenReturn(trigger);
      root=twin.new TransactionImpl(Activity.of(Activity.Type.SUBMISSION),scope,RuntimeAsset.PROVENANCE_ASSET);
      activity=Activity.of(Activity.Type.CLASSIFICATION,root.getActivity());child=(DigitalTwinImpl.TransactionImpl)root.getChild(activity,scope);
    }
    void close(){if(owner!=null)owner.close();if(registry!=null){registry.close();registry.awaitQuiescence(5,java.util.concurrent.TimeUnit.SECONDS);}}
  }
  static class Fixture implements AutoCloseable {
    final ConsequenceRoleTransactionTest role=new ConsequenceRoleTransactionTest();
    final DefaultRoleConsequenceRuntime.Cycles cycles=mock(DefaultRoleConsequenceRuntime.Cycles.class);
    final org.integratedmodelling.klab.services.runtime.RuntimeService localRuntime=mock(org.integratedmodelling.klab.services.runtime.RuntimeService.class);
    ResolutionCycleBinding binding;
    RegisteredTransactions registered;
    final DefaultRoleConsequenceRuntime runtime=new DefaultRoleConsequenceRuntime(localRuntime,cycles,null);
    final Map<Long,Observation> assets=new HashMap<>();
    final org.integratedmodelling.klab.services.runtime.neo4j.KnowledgeGraphNeo4j nativeSerializer=mock(org.integratedmodelling.klab.services.runtime.neo4j.KnowledgeGraphNeo4j.class,CALLS_REAL_METHODS);
    final List<KnowledgeGraph.ConsequenceEdgeSnapshot> extraEdges=new ArrayList<>();
    final java.util.concurrent.atomic.AtomicReference<org.integratedmodelling.klab.services.scopes.ConsequenceSubmissionAdmission> requestAdmission=new java.util.concurrent.atomic.AtomicReference<>();
    boolean executionAccounting=true;
    KimConsequenceRule rule;
    ConsequenceRuleResponse response;
    Fixture() throws Exception {
      registered=new RegisteredTransactions(localRuntime);role.setup(registered);binding=role.f.scope.getResolutionCycleBinding();OWNED.get().add(this);((ObservationImpl)role.source).setParentId(role.recipient.getId());
      when(localRuntime.serviceId()).thenReturn("runtime");
      assets.put(role.source.getId(),role.source);assets.put(role.recipient.getId(),role.recipient);
      when(cycles.require(role.f.scope)).thenReturn(role.f.scope);when(role.f.scope.getResolutionCycleBinding()).thenReturn(binding);
      when(role.f.scope.getObserverId()).thenReturn(null);
      doAnswer(call->{requestAdmission.set(call.getArgument(0));return role.f.scope;}).when(role.f.scope).withConsequenceSubmissionAdmission(any());
      when(role.f.scope.getDigitalTwin()).thenReturn(role.f.twin);when(role.f.twin.getKnowledgeGraph()).thenReturn(role.f.kg);
      doReturn(role.f.scope).when(role.f.scope).forConsequence(any());doReturn(role.f.scope).when(role.f.scope).within(any(Observation.class));
      when(role.f.twin.transaction(any(),eq(role.f.scope),eq(RuntimeAsset.PROVENANCE_ASSET))).thenCallRealMethod();
      var trigger=Activity.of(Activity.Type.SUBMISSION);((org.integratedmodelling.klab.api.provenance.impl.ActivityImpl)trigger).setId(2);
      when(role.f.kg.getAsset(2L,role.f.scope,Activity.class)).thenReturn(trigger);
      when(role.f.storage.captureConsequenceInputs(anyCollection(),anyCollection())).thenAnswer(call->{
        var ids=call.getArgument(0,Collection.class);var selections=List.copyOf(call.getArgument(1,Collection.class));
        var snapshots=new ArrayList<KnowledgeGraph.ConsequenceAssetSnapshot>();
        for(var value:ids) {
          long id=((Number)value).longValue();
          if(id>=1000) {
            var terms=new SemanticDependencyRequest("reasoner",1,FP,id>=2000?List.of("test:OriginalModel",role.role.getUrn()):List.of("test:OriginalModel"));
            var binding=new SemanticEvidenceBinding(SemanticEvidenceBinding.VERSION,true,terms.obligations(),witness(terms.inputHash()));
            snapshots.add(new KnowledgeGraph.ConsequenceAssetSnapshot(id,"native-"+id,List.of(GraphModel.Labels.ACTIVITY),Map.of(
                GraphModel.Fields.OUTCOME,Activity.Outcome.SUCCESS.name(),GraphModel.Fields.METADATA,
                org.integratedmodelling.klab.utilities.Utils.Json.asString(executionAccounting?Map.of(SemanticEvidenceBinding.DATA_KEY,org.integratedmodelling.klab.utilities.Utils.Json.asString(binding)):Map.of())),null,null));
          } else {
            var observation=Objects.requireNonNull(assets.get(id));var descriptor=role.snapshot(observation);
            snapshots.add(new KnowledgeGraph.ConsequenceAssetSnapshot(id,descriptor.elementId(),descriptor.labels(),
                nativeProperties(observation),
                observation.getGeometry()==null?null:observation.getGeometry().encode(),descriptor.perceivedGeometry()));
          }
        }
        var edges=new ArrayList<KnowledgeGraph.ConsequenceEdgeSnapshot>();
        if(selections.stream().anyMatch(value->((KnowledgeGraph.ConsequenceEdgeSelection)value).assetId()==70))
          {edges.add(new KnowledgeGraph.ConsequenceEdgeSnapshot("bearer-edge",ref(41),ref(70),GraphModel.Relationship.HAS_CHILD,Map.of()));edges.addAll(extraEdges.stream().filter(edge->selections.stream().anyMatch(value->{var selected=(KnowledgeGraph.ConsequenceEdgeSelection)value;return selected.relationship()==edge.relationship()&&Objects.equals(selected.assetId(),selected.direction()==GraphModel.Relationship.Direction.INCOMING?edge.target().id():edge.source().id());})).toList());}
        for(var selection:selections) {
          var selected=(KnowledgeGraph.ConsequenceEdgeSelection)selection;
          var observation=assets.get(selected.assetId());
          if(observation!=null&&selected.relationship()==GraphModel.Relationship.forContextualization(observation.getObservable().getContextualization()))
            edges.add(new KnowledgeGraph.ConsequenceEdgeSnapshot("execution-"+observation.getId(),new KnowledgeGraph.ConsequenceAssetRef("native-"+(1000+observation.getId()),1000+observation.getId(),List.of(GraphModel.Labels.ACTIVITY)),ref(observation.getId()),selected.relationship(),Map.of()));
          if(observation!=null&&selected.relationship()==GraphModel.Relationship.CLASSIFIED&&role.f.reasoner.hasRole(observation.getObservable(),role.role))
            edges.add(new KnowledgeGraph.ConsequenceEdgeSnapshot("attribution-"+observation.getId(),new KnowledgeGraph.ConsequenceAssetRef("native-"+(2000+observation.getId()),2000+observation.getId(),List.of(GraphModel.Labels.ACTIVITY)),ref(observation.getId()),GraphModel.Relationship.CLASSIFIED,Map.of()));
        }
        return new KnowledgeGraph.ConsequenceReadSet("context",snapshots,selections,edges);
      });
      when(role.f.kg.materializeConsequenceObservation(any(),any())).thenAnswer(call->{
        var snapshot=call.getArgument(0,KnowledgeGraph.ConsequenceAssetSnapshot.class);return assets.get(snapshot.id());
      });
      when(role.f.reasoner.is(any(),eq(role.source.getObservable().asConcept()))).thenReturn(true);
      when(role.f.reasoner.resolveConcept(role.role.getUrn())).thenReturn(role.role);
      doAnswer(call->witness(call.getArgument(0,SemanticDependencyRequest.class).inputHash())).when(role.f.reasoner).semanticDependencies(any(),eq(role.f.scope));
      doAnswer(call->{
        var expected=call.getArgument(0,KnowledgeGraph.ConsequenceReadSet.class);
        if(!expected.equals(role.f.storage.captureConsequenceInputs(expected.assetIds(),expected.selections())))throw new IllegalStateException("Native source/target inputs changed");return null;
      }).when(role.f.storage).validateConsequenceInputs(any());
      var capabilities=mock(org.integratedmodelling.klab.api.services.Reasoner.Capabilities.class);
      when(capabilities.getServiceId()).thenReturn("reasoner");when(capabilities.getKnowledgeRevision()).thenReturn(1L);when(capabilities.getSemanticContentFingerprint()).thenReturn(FP);when(capabilities.getSemanticSessionId()).thenReturn("session");
      when(role.f.reasoner.capabilities(any())).thenReturn(capabilities);
      authored(KimConsequenceRule.Kind.CONFERS,role.role.getUrn(),role.source.getObservable().asConcept(),null);
    }
    @SuppressWarnings("unchecked") private Map<String,Object> nativeProperties(Observation observation)throws Exception {
      var method=org.integratedmodelling.klab.services.runtime.neo4j.AbstractKnowledgeGraph.class.getDeclaredMethod("asParameters",Object.class,Object[].class);
      method.setAccessible(true);var properties=(Map<String,Object>)method.invoke(nativeSerializer,observation,new Object[0]);
      properties.values().removeIf(Objects::isNull);return properties;
    }
    @Override public void close(){if(registered!=null)registered.close();}
    KnowledgeGraph.ConsequenceAssetRef ref(long id){return new KnowledgeGraph.ConsequenceAssetRef("native-"+id,id,List.of(GraphModel.Labels.OBSERVATION));}
    void authored(KimConsequenceRule.Kind kind,String target,Concept owner,KimConcept within) {
      rule=new KimConsequenceRule(kind,syntax(target),KimConsequenceRule.Recipient.implicit(),null,within,
          new KimConceptStatement.DeclarationClause(kind.name(),"authored rule",1,12));
      var entry=new ConsequenceRuleResponse.Entry("rule","test:Declaration",owner,rule,"test","1","c".repeat(64));
      response=new ConsequenceRuleResponse(ConsequenceRuleResponse.Status.COMPLETE,
          new ConsequenceRuleResponse.Epoch("reasoner",1,FP,"session"),List.of(entry),witness("b".repeat(64)),"complete");
      when(role.f.reasoner.consequenceRules(any())).thenReturn(response);
    }
    RoleConsequenceRuntime.Rule discover()throws Exception{return runtime.conferrals(role.source,role.f.scope).values().getFirst();}
    void recipient(KimConsequenceRule.Recipient recipient) {
      rule=new KimConsequenceRule(rule.kind(),rule.target(),recipient,rule.secondaryInherency(),rule.within(),rule.source());
      var old=response.rules().getFirst();response=new ConsequenceRuleResponse(response.status(),response.epoch(),
          List.of(new ConsequenceRuleResponse.Entry(old.ruleId(),old.declarationUrn(),old.owner(),rule,old.sourceOntology(),old.sourceVersion(),old.sourceHash())),response.witness(),response.reason());
      when(role.f.reasoner.consequenceRules(any())).thenReturn(response);
    }
    Observation relationship() {
      var sourceMeaning=new ConceptImpl();sourceMeaning.setUrn("test:Connection");sourceMeaning.setType(EnumSet.of(SemanticType.RELATIONSHIP,SemanticType.OBSERVABLE,SemanticType.COUNTABLE));
      var relationshipObservable=org.integratedmodelling.common.knowledge.ObservableImpl.promote(sourceMeaning,role.f.scope);
      relationshipObservable.setDescriptionType(Contextualization.CONNECTION);((ObservationImpl)role.source).setObservable(relationshipObservable);
      when(role.f.reasoner.is(any(),eq(sourceMeaning))).thenReturn(true);
      authored(KimConsequenceRule.Kind.CONFERS,role.role.getUrn(),sourceMeaning,null);
      var other=new ObservationImpl();other.setId(42);other.setUrn("member:42");other.setObservable(new org.integratedmodelling.common.knowledge.ObservableImpl(role.before));other.setGeometry(role.recipient.getGeometry());other.setParentId(role.recipient.getParentId());assets.put(42L,other);
      extraEdges.add(new KnowledgeGraph.ConsequenceEdgeSnapshot("from",ref(70),ref(41),GraphModel.Relationship.HAS_RELATIONSHIP_SOURCE,Map.of()));
      extraEdges.add(new KnowledgeGraph.ConsequenceEdgeSnapshot("to",ref(70),ref(42),GraphModel.Relationship.HAS_RELATIONSHIP_TARGET,Map.of()));return other;
    }
    RoleConsequenceRuntime.Effect attribute(RoleConsequenceRuntime.Rule rule)throws Exception {
      return runtime.attribute(role.f.scope.getConsequenceCause(),role.source,rule,List.of(role.recipient),role.f.scope);
    }
    Observable target(String urn,SemanticType type,Observation returned) {
      var target=mock(Observable.class);when(target.getUrn()).thenReturn(urn);when(target.is(type)).thenReturn(true);
      var builder=mock(Observable.Builder.class);when(target.builder(role.f.scope)).thenReturn(builder);
      when(builder.of(any())).thenReturn(builder);when(builder.optional(true)).thenReturn(builder);when(builder.buildObservable()).thenReturn(target);
      when(role.f.reasoner.resolveObservable(urn)).thenReturn(target);var request=mock(Observation.Builder.class);
      var built=new ObservationImpl();built.setId(-7);built.setObservable(target);
      doReturn(request).when(role.f.scope).observation(target);when(request.build()).thenReturn(built);
      when(localRuntime.submit(built,role.f.scope)).thenReturn(CompletableFuture.completedFuture(returned));return target;
    }
  }
  @Test void nativeConferralUsesRetainedRuleProofAndCommitsBeforeAnyModelRequest()throws Exception {
    var f=new Fixture();var rule=f.discover();var effect=f.attribute(rule);
    assertEquals(Consequence.Outcome.APPLIED,effect.outcome());assertEquals(List.of(41L),effect.observations());
    assertEquals(f.role.after.getUrn(),f.role.recipient.getObservable().getUrn());
    verify(f.role.f.scope,never()).observation(any());verify(f.role.f.reasoner,never()).capabilities(any());
    var publication=inOrder(f.role.f.storage);var guarded=org.mockito.ArgumentCaptor.forClass(KnowledgeGraph.ConsequenceReadSet.class);
    publication.verify(f.role.f.storage).lockConsequenceInputs(anyCollection());
    publication.verify(f.role.f.storage).validateConsequenceInputs(guarded.capture());
    assertTrue(guarded.getValue().assetIds().containsAll(List.of(70L,41L,1070L)));
    assertTrue(guarded.getValue().edges().stream().anyMatch(edge->edge.relationship()==GraphModel.Relationship.HAS_CHILD&&Objects.equals(edge.source().id(),41L)&&Objects.equals(edge.target().id(),70L)));
    publication.verify(f.role.f.storage).updateSemantics(any(),eq(f.role.before.getUrn()));
  }
  @Test void intrinsicRoleActivityPreservesOriginalModelClosureForOrdinaryDownstreamCollector()throws Exception {
    var f=new Fixture();var caps=mock(org.integratedmodelling.klab.api.services.Reasoner.Capabilities.class);
    when(caps.getServiceId()).thenReturn("reasoner");when(caps.getKnowledgeRevision()).thenReturn(1L);when(caps.getSemanticContentFingerprint()).thenReturn(FP);when(caps.getSemanticSessionId()).thenReturn("session");
    when(f.role.f.reasoner.capabilities(f.role.f.scope)).thenReturn(caps);
    assertEquals(Consequence.Outcome.APPLIED,f.attribute(f.discover()).outcome());
    var stored=org.mockito.ArgumentCaptor.forClass(RuntimeAsset.class);verify(f.role.f.storage,atLeastOnce()).store(stored.capture());
    var scientific=stored.getAllValues().stream().filter(value->value instanceof Activity activity&&activity.getMetadata().containsKey("klab.consequence.roleAttributions")).map(value->(Activity)value).findFirst().orElseThrow();
    var inherited=org.integratedmodelling.klab.services.runtime.RoleBackendDiscoveryFixture.inheritActivity(scientific,f.role.f.scope);
    assertTrue(inherited.complete());assertTrue(inherited.obligations().contains("test:OriginalModel"));assertTrue(inherited.obligations().contains(f.role.role.getUrn()));
    scientific.getMetadata().remove(SemanticEvidenceBinding.DATA_KEY);
    assertFalse(org.integratedmodelling.klab.services.runtime.RoleBackendDiscoveryFixture.inheritActivity(scientific,f.role.f.scope).complete());
  }
  @Test void missingOriginalSourceExecutionAccountingCannotBeInventedByRuleAuthority()throws Exception {
    var f=new Fixture();var rule=f.discover();f.executionAccounting=false;
    assertThrows(IllegalStateException.class,()->f.attribute(rule));verify(f.role.f.storage,never()).store(any());
    assertSame(f.role.before,f.role.recipient.getObservable());
  }
  @Test void unprovenOrForeignBackendEvidenceNeverCreatesATransaction()throws Exception {
    var f=new Fixture();var rule=f.discover();
    assertThrows(IllegalStateException.class,()->f.attribute(new RoleConsequenceRuntime.Rule(rule.id(),rule.role(),rule.selector(),rule.recipient())));
    var foreign=new DefaultRoleConsequenceRuntime(f.localRuntime,f.cycles,null);var foreignRule=foreign.conferrals(f.role.source,f.role.f.scope).values().getFirst();
    assertThrows(IllegalStateException.class,()->f.attribute(foreignRule));verify(f.role.f.twin,never()).transaction(any(),any(),any());
  }
  @Test void mutatingTheExposedRoleCannotRewriteItsPrivateAuthoredProof()throws Exception {
    var f=new Fixture();var rule=f.discover();((ConceptImpl)rule.role()).setUrn("test:UnrelatedRole");
    assertThrows(IllegalStateException.class,()->f.attribute(rule));verify(f.role.f.twin,never()).transaction(any(),any(),any());
  }
  @Test void wrongCycleOrContextRejectsOldRuleEvidence()throws Exception {
    var f=new Fixture();var rule=f.discover();
    var other=new ResolutionCycleBinding(1,"next-cycle","context","runtime",true,f.binding.semanticBinding());
    when(f.role.f.scope.getResolutionCycleBinding()).thenReturn(other);
    assertThrows(IllegalStateException.class,()->f.attribute(rule));
    when(f.role.f.scope.getId()).thenReturn("foreign-context");assertThrows(IllegalStateException.class,()->f.attribute(rule));
    verify(f.role.f.twin,never()).transaction(any(),any(),any());
  }
  @Test void changedObserverPerspectiveRejectsOldRuleEvidence()throws Exception {
    var f=new Fixture();var observer=actualObservation(88,SemanticType.AGENT);f.assets.put(88L,observer);
    when(f.role.f.scope.getObserverId()).thenReturn(88L);when(f.role.f.scope.getObserver()).thenReturn(observer);var rule=f.discover();
    var otherObserver=actualObservation(89,SemanticType.AGENT);
    when(f.role.f.scope.getObserverId()).thenReturn(89L);when(f.role.f.scope.getObserver()).thenReturn(otherObserver);
    assertThrows(IllegalStateException.class,()->f.attribute(rule));verify(f.role.f.twin,never()).transaction(any(),any(),any());
  }
  @Test void expiredSourceWitnessCannotAuthorizeDiscovery()throws Exception {
    var f=new Fixture();var expired=new SemanticDependencyWitness(SemanticDependencyWitness.VERSION,SemanticDependencyWitness.Status.COMPLETE,
        "reasoner",1,FP,"b".repeat(64),List.of(new SemanticDependencyWitness.AuthorityUse("AUTH",SemanticDependencyWitness.Kind.CAPTURED,"d".repeat(64),1)),"expired");
    when(f.role.f.reasoner.consequenceRules(any())).thenReturn(new ConsequenceRuleResponse(f.response.status(),f.response.epoch(),f.response.rules(),expired,"expired"));
    assertThrows(IllegalStateException.class,f::discover);verify(f.role.f.twin,never()).transaction(any(),any(),any());
  }
  @Test void withinChecksRoleBearingSubstantialAndNotDeclaringProcess()throws Exception {
    var f=new Fixture();var context=new ConceptImpl();context.setUrn("test:RecipientSubclass");context.setType(EnumSet.of(SemanticType.SUBJECT));
    f.authored(KimConsequenceRule.Kind.CONFERS,f.role.role.getUrn(),f.role.source.getObservable().asConcept(),syntax(context.getUrn()));
    when(f.role.f.reasoner.resolveConcept(context.getUrn())).thenReturn(context);
    when(f.role.f.reasoner.is(f.role.recipient.getObservable().asConcept(),context)).thenReturn(false);
    assertEquals(Consequence.Outcome.NO_ACTION,f.attribute(f.discover()).outcome());
    verify(f.role.f.reasoner).is(f.role.recipient.getObservable().asConcept(),context);verify(f.role.f.reasoner,never()).is(f.role.source.getObservable().asConcept(),context);
    verify(f.role.f.twin,never()).transaction(any(),any(),any());
  }
  @Test void characterizationPreservesNoModelDeferredAndFailureWithoutUndoingRole()throws Exception {
    for(var outcome:Dataflow.ResolutionOutcome.values()) {
      var f=new Fixture();when(f.role.f.reasoner.hasRole(f.role.before,f.role.role)).thenReturn(true);
      var target=f.target(f.role.role.getUrn(),SemanticType.PREDICATE,ResolutionOutcomeReceipt.terminal(Observation.empty(),outcome));
      when(target.getContextualization()).thenReturn(Contextualization.CHARACTERIZATION);
      when(f.localRuntime.characterizeConsequence(eq(f.role.recipient),eq(target),eq(f.role.f.scope))).thenAnswer(call->{
        var admission=f.requestAdmission.get();assertNotNull(admission);
        admission.requireTarget(target,f.role.f.scope);
        var other=mock(Observable.class);when(other.getUrn()).thenReturn("test:Unrelated");
        assertThrows(SecurityException.class,()->admission.requireTarget(other,f.role.f.scope));
        return outcome;
      });
      var effect=f.runtime.characterize(null,new RoleConsequenceRuntime.Arrival(70,41,f.role.role,"rule",true),f.role.f.scope);
      assertEquals(switch(outcome){case NO_MODEL->RoleConsequenceRuntime.Resolution.NO_MODEL;case DEFERRED->RoleConsequenceRuntime.Resolution.UNRESOLVED;case RESOLVED->RoleConsequenceRuntime.Resolution.RESOLVED;case FAILED->RoleConsequenceRuntime.Resolution.FAILED;},effect.resolution());
      assertSame(f.role.before,f.role.recipient.getObservable());verify(f.role.f.storage,never()).updateSemantics(any(),any());
      verify(f.localRuntime,never()).submit(any(),any());
    }
  }
  @Test void roleImplicationsQueryActualRoleAndUseOrdinaryOptionalResolution()throws Exception {
    var f=new Fixture();when(f.role.f.reasoner.hasRole(f.role.before,f.role.role)).thenReturn(true);
    f.authored(KimConsequenceRule.Kind.IMPLIES,"test:Dependent",f.role.role,null);
    f.target("test:Dependent",SemanticType.QUALITY,ResolutionOutcomeReceipt.terminal(Observation.empty(),Dataflow.ResolutionOutcome.NO_MODEL));
    var effect=f.runtime.implications(null,new RoleConsequenceRuntime.Arrival(70,41,f.role.role,"rule",true),f.role.f.scope);
    assertEquals(RoleConsequenceRuntime.Resolution.NO_MODEL,effect.resolution());
    verify(f.role.f.reasoner).consequenceRules(argThat(query->query.ownerTriggers().equals(List.of(f.role.role))&&query.expectedEpoch().semanticSessionId().equals("session")));
    verify(f.role.f.scope).observation(any());verify(f.role.f.twin,never()).transaction(any(),any(),any());
  }
  @Test void sourceDriftAfterOrdinaryRequestAdmissionRejectsTheActualDerivedCommit()throws Exception {
    var f=new Fixture();when(f.role.f.reasoner.hasRole(eq(f.role.before),any())).thenReturn(true);
    f.authored(KimConsequenceRule.Kind.IMPLIES,"test:Dependent",f.role.role,null);
    var target=f.target("test:Dependent",SemanticType.QUALITY,Observation.empty());
    var rejectedAfterAdmission=new java.util.concurrent.atomic.AtomicBoolean();
    var request=f.role.f.scope.observation(target);
    when(f.localRuntime.submit(request.build(),f.role.f.scope)).thenAnswer(call->{
      var transaction=f.role.f.twin.transaction(Activity.of(Activity.Type.SUBMISSION),f.role.f.scope,RuntimeAsset.PROVENANCE_ASSET);
      assertNotNull(f.requestAdmission.get());f.requestAdmission.get().bind(transaction,f.role.f.scope);
      f.role.recipient.getMetadata().put("changed-after-rule-admission",true);
      assertEquals(-1,transaction.commit());
      rejectedAfterAdmission.set(true);
      return CompletableFuture.completedFuture(ResolutionOutcomeReceipt.terminal(Observation.empty(),Dataflow.ResolutionOutcome.FAILED));
    });
    var result=f.runtime.implications(null,new RoleConsequenceRuntime.Arrival(70,41,f.role.role,"rule",true),f.role.f.scope);
    assertEquals(RoleConsequenceRuntime.Resolution.FAILED,result.resolution());
    assertTrue(rejectedAfterAdmission.get(),result.reason());
    verify(f.role.f.storage,never()).store(any());verify(f.role.f.storage,never()).updateSemantics(any(),any());
  }
  @Test void nativeTargetInherencySurvivesActualBuilderAndSeparateRecipientFocus()throws Exception {
    var f=new Fixture();when(f.role.f.reasoner.hasRole(eq(f.role.before),any())).thenReturn(true);
    var nativeMeaning=new ConceptImpl();nativeMeaning.setUrn("test:Height of test:Tree");
    nativeMeaning.setType(EnumSet.of(SemanticType.QUALITY,SemanticType.LENGTH,SemanticType.OBSERVABLE));
    var nativeTarget=org.integratedmodelling.common.knowledge.ObservableImpl.promote(nativeMeaning,f.role.f.scope);
    var nativeInherent=new ConceptImpl();nativeInherent.setUrn("test:Tree");nativeInherent.setType(EnumSet.of(SemanticType.SUBJECT));
    when(f.role.f.reasoner.inherent(nativeTarget)).thenReturn(nativeInherent);
    f.authored(KimConsequenceRule.Kind.IMPLIES,nativeTarget.getUrn(),f.role.role,null);
    when(f.role.f.reasoner.resolveObservable(nativeTarget.getUrn())).thenReturn(nativeTarget);
    var focus=spy(f.registered.owner.scope().forConsequence(f.role.f.scope.getConsequenceCause()).within(f.role.recipient));
    when(f.role.f.scope.within(f.role.recipient)).thenReturn(focus);when(f.cycles.require(focus)).thenReturn(focus);
    when(focus.getId()).thenReturn("context");when(focus.getResolutionCycleBinding()).thenReturn(f.binding);
    when(focus.getService(org.integratedmodelling.klab.api.services.Reasoner.class)).thenReturn(f.role.f.reasoner);
    when(focus.getContextObservation()).thenReturn(f.role.recipient);when(focus.getObserverId()).thenReturn(null);
    doAnswer(call->{f.requestAdmission.set(call.getArgument(0));return focus;}).when(focus).withConsequenceSubmissionAdmission(any());
    when(f.role.f.reasoner.buildObservable(any(),eq(focus))).thenAnswer(call->{
      var strategy=call.getArgument(0,ObservableBuildStrategy.class);
      assertTrue(strategy.getOperations().stream().noneMatch(operation->operation.getType()==ObservableBuildStrategy.OperationType.OF));
      assertEquals(nativeMeaning.getUrn(),strategy.getBaseObservable().getUrn());
      var optional=new org.integratedmodelling.common.knowledge.ObservableImpl((org.integratedmodelling.common.knowledge.ObservableImpl)strategy.getBaseObservable());optional.setOptional(true);return optional;
    });
    var requested=new java.util.concurrent.atomic.AtomicReference<Observable>();var submit=mock(Observation.Builder.class);
    doAnswer(call->{requested.set(call.getArgument(0));return submit;}).when(focus).observation(any());
    when(submit.build()).thenAnswer(call->{var request=new ObservationImpl();request.setId(-9);request.setObservable(requested.get());return request;});
    when(f.localRuntime.submit(any(),eq(focus))).thenReturn(CompletableFuture.completedFuture(ResolutionOutcomeReceipt.terminal(Observation.empty(),Dataflow.ResolutionOutcome.NO_MODEL)));
    var effect=f.runtime.implications(null,new RoleConsequenceRuntime.Arrival(70,41,f.role.role,"rule",true),f.role.f.scope);
    assertEquals(RoleConsequenceRuntime.Resolution.NO_MODEL,effect.resolution(),effect.reason());assertNotNull(requested.get());
    assertEquals(nativeMeaning.getUrn(),requested.get().getUrn());assertEquals(nativeMeaning.getUrn(),requested.get().asConcept().getUrn());
    assertSame(f.role.recipient,focus.getContextObservation());assertTrue(requested.get().isOptional());
  }
  @Test void relationshipConferralCommitsBothOrOnlyTheAuthoredDirectedEndpoint()throws Exception {
    for(var kind:List.of(KimConsequenceRule.RecipientKind.DEFAULT,KimConsequenceRule.RecipientKind.SOURCE,KimConsequenceRule.RecipientKind.TARGET)) {
      var f=new Fixture();var other=f.relationship();f.recipient(new KimConsequenceRule.Recipient(kind,null));var rule=f.discover();
      List<Observation> recipients=kind==KimConsequenceRule.RecipientKind.DEFAULT?List.of(f.role.recipient,other):kind==KimConsequenceRule.RecipientKind.SOURCE?List.of(f.role.recipient):List.of(other);
      var result=f.runtime.attribute(f.role.f.scope.getConsequenceCause(),f.role.source,rule,recipients,f.role.f.scope);
      assertEquals(Consequence.Outcome.APPLIED,result.outcome());assertEquals(recipients.stream().map(Observation::getId).toList(),result.observations());
      verify(f.role.f.scope,never()).observation(any());
      if(kind==KimConsequenceRule.RecipientKind.SOURCE)assertEquals(f.role.before.getUrn(),other.getObservable().getUrn());
      if(kind==KimConsequenceRule.RecipientKind.TARGET)assertEquals(f.role.before.getUrn(),f.role.recipient.getObservable().getUrn());
    }
  }
  @Test void explicitRecipientRulesRejectBareListsAndUnprovenSelectionsEvenWhenMembersMatch()throws Exception {
    var f=new Fixture();f.recipient(new KimConsequenceRule.Recipient(KimConsequenceRule.RecipientKind.EXPRESSION,syntax("test:Recipient")));var rule=f.discover();
    assertThrows(IllegalStateException.class,()->f.attribute(rule));
    assertThrows(IllegalStateException.class,()->f.runtime.attributeSelected(f.role.f.scope.getConsequenceCause(),f.role.source,rule,
        ConsequenceRecipients.Selection.complete(List.of(f.role.recipient)),f.role.f.scope));
    verify(f.role.f.twin,never()).transaction(any(),any(),any());
  }
  @Test void actualLocalQueryIssuerSelectionRejectsMatchingSubsetWithoutAnyAttributionWrite()throws Exception {
    var f=new org.integratedmodelling.klab.services.runtime.RoleBackendDiscoveryFixture(2);
    var rule=f.backend().conferrals(f.source(),f.execution()).values().getFirst();
    var selected=f.backend().resolveRecipients(f.source(),rule,f.execution());
    assertEquals(ConsequenceRecipients.Status.COMPLETE,selected.status());assertEquals(2,selected.recipients().size());assertNotNull(selected.evidence());
    verify(f.runtime(),atLeastOnce()).submit(argThat(request->request.getId()==Observation.QUERY_ID),any());
    var subset=new ConsequenceRecipients.Selection(ConsequenceRecipients.Status.COMPLETE,List.of(selected.recipients().getFirst()),"matching subset",selected.evidence());
    assertThrows(IllegalStateException.class,()->f.backend().attributeSelected(f.execution().getConsequenceCause(),f.source(),rule,subset,f.execution()));
    verify(f.transaction(),never()).store(any());
  }
  @Test void completeNativeSelectionRejectsCertificationDriftBeforeAttribution()throws Exception {
    var f=new org.integratedmodelling.klab.services.runtime.RoleBackendDiscoveryFixture(1);
    var rule=f.backend().conferrals(f.source(),f.execution()).values().getFirst();var selected=f.backend().resolveRecipients(f.source(),rule,f.execution());
    var capabilities=f.reasoner().capabilities(f.execution());when(capabilities.getKnowledgeRevision()).thenReturn(2L);
    assertThrows(IllegalStateException.class,()->f.backend().attributeSelected(f.execution().getConsequenceCause(),f.source(),rule,selected,f.execution()));
    verify(f.transaction(),never()).store(any());
  }
}
