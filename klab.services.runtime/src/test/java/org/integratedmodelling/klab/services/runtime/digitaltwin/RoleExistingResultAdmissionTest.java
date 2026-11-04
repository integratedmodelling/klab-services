package org.integratedmodelling.klab.services.runtime.digitaltwin;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import org.integratedmodelling.common.knowledge.*;
import org.integratedmodelling.common.runtime.ResolutionOutcomeReceipt;
import org.integratedmodelling.klab.api.data.*;
import org.integratedmodelling.klab.api.digitaltwin.*;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.knowledge.*;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.knowledge.observation.impl.ObservationImpl;
import org.integratedmodelling.klab.api.lang.kim.KimConsequenceRule;
import org.integratedmodelling.klab.api.provenance.Activity;
import org.integratedmodelling.klab.api.services.runtime.Dataflow;
import org.integratedmodelling.klab.services.scopes.ConsequenceSubmissionAdmission;
import org.junit.jupiter.api.*;

class RoleExistingResultAdmissionTest {
  @BeforeAll static void configure(){org.integratedmodelling.klab.configuration.ServiceConfiguration.injectInstantiators();}
  @AfterEach void cleanup(){DefaultRoleConsequenceRuntimeTest.cleanup();}
  record Case(DefaultRoleConsequenceRuntimeTest.Fixture fixture,ObservationImpl target,ConsequenceSubmissionAdmission admission) {}
  static ObservationImpl target(DefaultRoleConsequenceRuntimeTest.Fixture f,long id,String urn,long parent) {
    var meaning=new ConceptImpl();meaning.setUrn(urn);meaning.setType(EnumSet.of(SemanticType.QUALITY,SemanticType.OBSERVABLE));meaning.setDescriptionType(Contextualization.QUANTIFICATION);
    var observable=ObservableImpl.promote(meaning,f.role.f.scope);observable.setDescriptionType(Contextualization.QUANTIFICATION);
    var target=new ObservationImpl();target.setId(id);target.setUrn("context."+id);target.setObservable(observable);target.setGeometry(Geometry.create("1"));target.setParentId(parent);
    f.assets.put(id,target);when(f.role.f.reasoner.resolveObservable(urn)).thenReturn(observable);return target;
  }
  static Case prepared()throws Exception {
    var f=new DefaultRoleConsequenceRuntimeTest.Fixture();when(f.role.f.reasoner.hasRole(eq(f.role.before),any())).thenReturn(true);
    var result=target(f,90,"test:Dependent",RuntimeAsset.CONTEXT_ASSET_ID);
    f.authored(KimConsequenceRule.Kind.IMPLIES,result.getObservable().getUrn(),f.role.role,null);
    doAnswer(call->{var strategy=call.getArgument(0,ObservableBuildStrategy.class);var value=new ObservableImpl((ObservableImpl)strategy.getBaseObservable());value.setOptional(true);return value;}).when(f.role.f.reasoner).buildObservable(any(),any());
    doAnswer(call->{var observable=call.getArgument(0,org.integratedmodelling.klab.api.knowledge.Observable.class);return new Observation.NaiveBuilder(observable,f.role.f.scope){@Override public ObservationImpl build(){return (ObservationImpl)Observation.forTransport(result);}};}).when(f.role.f.scope).observation(any());
    when(f.localRuntime.submit(any(),eq(f.role.f.scope))).thenReturn(CompletableFuture.completedFuture(ResolutionOutcomeReceipt.terminal(Observation.empty(),Dataflow.ResolutionOutcome.NO_MODEL)));
    f.runtime.implications(null,new RoleConsequenceRuntime.Arrival(70,41,f.role.role,"rule",true),f.role.f.scope);
    return new Case(f,result,Objects.requireNonNull(f.requestAdmission.get()));
  }
  @Test void topExistingResultMustMatchOriginalDeclaredGoalEvenWithMatchingNativePayload()throws Exception {
    var c=prepared();var wrong=target(c.fixture(),90,"test:Unrelated",RuntimeAsset.CONTEXT_ASSET_ID);
    assertThrows(IllegalStateException.class,()->c.admission().prepareExisting(wrong,c.fixture().role.f.scope));
    verify(c.fixture().role.f.storage,never()).store(any());
  }
  @Test void existingResultNativeGuardRejectsSourceDriftBeforeAnyPerceptionWrite()throws Exception {
    var c=prepared();var guard=c.admission().prepareExisting(c.target(),c.fixture().role.f.scope);
    c.fixture().role.recipient.getMetadata().put("external-source-drift",true);
    assertThrows(IllegalStateException.class,()->guard.validate(c.fixture().role.f.storage));
    verify(c.fixture().role.f.storage,never()).perceive(any(),any());verify(c.fixture().role.f.storage,never()).store(any());
  }
  @Test void legitimateDifferentDependencyGoalAndFocusAreAcceptedOnlyInsideTheExactAdmittedRoot()throws Exception {
    var c=prepared();var f=c.fixture();var transaction=f.role.f.twin.transaction(Activity.of(Activity.Type.SUBMISSION),f.role.f.scope,RuntimeAsset.PROVENANCE_ASSET);
    try {
      c.admission().bind(transaction,f.role.f.scope);var original=c.admission().semanticEvidence();assertNotNull(original);
      var focus=target(f,42,"test:DependencyContext",RuntimeAsset.CONTEXT_ASSET_ID);
      var subject=new ConceptImpl();subject.setUrn("test:DependencyContext");subject.setType(EnumSet.of(SemanticType.SUBJECT,SemanticType.OBSERVABLE));
      var context=ObservableImpl.promote(subject,f.role.f.scope);context.setDescriptionType(Contextualization.ACKNOWLEDGEMENT);focus.setObservable(context);
      var dependency=target(f,91,"test:OtherDependency",42);
      var nested=f.registered.owner.scope().forConsequence(f.role.f.scope.getConsequenceCause()).within(focus).withTransaction(transaction);
      when(f.cycles.require(nested)).thenReturn(nested);
      var guard=c.admission().prepareExisting(dependency,nested);guard.validate(f.role.f.storage);
      assertEquals(original,c.admission().semanticEvidence(),"Dependency checking must not overwrite the causal root closure");
    } finally {transaction.fail(new IllegalStateException("fixture cleanup"));}
  }
  @Test void matchingFocusCannotAuthorizeADifferentOrUnboundNativeRoot()throws Exception {
    var c=prepared();var f=c.fixture();var first=f.role.f.twin.transaction(Activity.of(Activity.Type.SUBMISSION),f.role.f.scope,RuntimeAsset.PROVENANCE_ASSET);
    var foreign=f.role.f.twin.transaction(Activity.of(Activity.Type.SUBMISSION),f.role.f.scope,RuntimeAsset.PROVENANCE_ASSET);
    try {
      var unknown=f.registered.owner.scope().forConsequence(f.role.f.scope.getConsequenceCause()).withTransaction(first);when(f.cycles.require(unknown)).thenReturn(unknown);
      assertThrows(IllegalStateException.class,()->c.admission().prepareExisting(c.target(),unknown));
      c.admission().bind(first,f.role.f.scope);
      var another=f.registered.owner.scope().forConsequence(f.role.f.scope.getConsequenceCause()).withTransaction(foreign);when(f.cycles.require(another)).thenReturn(another);
      assertThrows(IllegalStateException.class,()->c.admission().prepareExisting(c.target(),another));
    } finally {first.fail(new IllegalStateException("fixture cleanup"));foreign.fail(new IllegalStateException("fixture cleanup"));}
  }
  @Test void cancellationRejectsBeforeNativeLockAndPreservesThreadInterrupt()throws Exception {
    var c=prepared();var guard=c.admission().prepareExisting(c.target(),c.fixture().role.f.scope);
    Thread.currentThread().interrupt();
    try {
      assertThrows(java.util.concurrent.CancellationException.class,()->guard.validate(c.fixture().role.f.storage));
      assertTrue(Thread.currentThread().isInterrupted());verify(c.fixture().role.f.storage,never()).lockConsequenceInputs(anyCollection());
    }finally{Thread.interrupted();}
  }

}
