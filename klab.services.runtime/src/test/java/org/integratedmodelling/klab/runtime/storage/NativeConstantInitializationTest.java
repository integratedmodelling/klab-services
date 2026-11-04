package org.integratedmodelling.klab.runtime.storage;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.nio.file.Path;
import java.util.*;
import org.integratedmodelling.common.knowledge.*;
import org.integratedmodelling.common.lang.*;
import org.integratedmodelling.common.runtime.ActuatorImpl;
import org.integratedmodelling.klab.api.digitaltwin.*;
import org.integratedmodelling.klab.api.identities.UserIdentity;
import org.integratedmodelling.klab.api.knowledge.*;
import org.integratedmodelling.klab.api.provenance.Activity;
import org.integratedmodelling.klab.api.services.Reasoner;
import org.integratedmodelling.klab.api.services.reasoner.objects.*;
import org.integratedmodelling.klab.api.services.runtime.Actuator;
import org.integratedmodelling.klab.runtime.language.ModelValidationGate;
import org.integratedmodelling.klab.services.runtime.CompiledDataflow;
import org.integratedmodelling.klab.services.scopes.ServiceContextScope;
import org.integratedmodelling.klab.utilities.Utils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** UNRUN focused actual executor/scalar/storage INIT, controlled semantic/native fixture.
 * This is neither full source-model admission nor live acceptance. */
class NativeConstantInitializationTest {
  @TempDir Path directory;
  static final String BASE="semantic-content-v2:"+"a".repeat(64);
  final Reasoner reasoner=mock(Reasoner.class);
  private void semantics() {
    var caps=mock(Reasoner.Capabilities.class);
    when(caps.getServiceId()).thenReturn("reasoner");when(caps.getKnowledgeRevision()).thenReturn(1L);
    when(caps.getSemanticContentFingerprint()).thenReturn(BASE);when(caps.getSemanticSessionId()).thenReturn("owner-one");
    when(reasoner.capabilities(any())).thenReturn(caps);
    when(reasoner.semanticDependencies(any(),any())).thenAnswer(call->witness(call.getArgument(0)));
  }
  static SemanticDependencyWitness witness(SemanticDependencyRequest request) {
    return new SemanticDependencyWitness(SemanticDependencyWitness.VERSION,SemanticDependencyWitness.Status.COMPLETE,
        request.expectedServiceId(),request.expectedKnowledgeRevision(),request.expectedBaseFingerprint(),request.inputHash(),List.of(),null);
  }
  class Fixture extends TemporalProcessIntegrationTest.Fixture {
    Fixture(TemporalProcessIntegrationTest owner)throws Exception{owner.super("elevation - 10");}
    @Override void configureScope(ServiceContextScope scope,UserIdentity user) {
      super.configureScope(scope,user);doReturn(reasoner).when(scope).getService(Reasoner.class);
      when(scope.getConsequenceSubmissionAdmission()).thenReturn(null);
    }
  }
  private ActuatorImpl plan(Fixture f,int value,String function) {
    ((ObservableImpl)f.quality.getObservable()).setDescriptionType(Contextualization.QUANTIFICATION);
    var call=new ServiceCallImpl(function,"value",value);
    var plan=new ActuatorImpl();plan.setObservation(f.quality);plan.setActuatorType(Actuator.Type.OBSERVE);
    plan.setName("input");plan.setContextualization(Contextualization.QUANTIFICATION);plan.getComputation().add(call);
    var model=new ModelImpl();model.setUrn("fixture.input-quantifier");model.setNamespace("fixture");
    model.getObservables().add(f.quality.getObservable());model.getComputation().add(new ContextualizableImpl(call));
    var snapshot=ModelValidationGate.snapshot(model);var terms=ModelValidationGate.declaredSemanticObligations(snapshot);
    var request=new SemanticDependencyRequest("reasoner",1,BASE,terms);
    var receipt=new ModelValidationGate.AdmissionReceipt(model.getUrn(),model.getNamespace(),"1","b".repeat(64),
        snapshot.executableHash(),"reasoner",1,List.of(),BASE,terms,true,witness(request),"owner-one");
    plan.getData().put(Actuator.PRODUCING_MODELS_KEY,List.of(model.getUrn()));
    plan.getData().put("modelAdmissionRevision",1L);plan.getData().put("modelAdmissionReasonerId","reasoner");
    plan.getData().put(ModelValidationGate.PRODUCER_ADMISSIONS,List.of(Utils.Json.asString(receipt)));
    plan.getData().put(ModelValidationGate.PRODUCER_SNAPSHOTS,List.of(Utils.Json.asString(snapshot)));
    return plan;
  }
  @Test void nativeZeroAndNonzeroExecuteScalarInitializationAndIssueOriginalAccounting()throws Exception {
    TemporalProcessIntegrationTest.configure();semantics();
    for(int value:new int[]{0,7}) {
      var owner=new TemporalProcessIntegrationTest();owner.directory=directory;
      try(var f=new Fixture(owner)) {
        f.quality.setEventTimestamps(new ArrayList<>());
        var scope=f.root.executingFresh(Activity.of(Activity.Type.SUBMISSION),f.bearer);
        var plan=plan(f,value,org.integratedmodelling.klab.api.services.RuntimeService.CoreFunctor.CONSTANT_RESOLVER.getServiceCallName());
        var executor=assertDoesNotThrow(()->new CompiledDataflow(f.runtime,f.quality,scope).restoreLeafExecutor(plan));
        scope.getCurrentTransaction().resolveWith(f.quality,executor);
        assertTrue(f.scheduler.executeDependency(f.quality,f.quality.getGeometry(),Scheduler.Event.initialization(),scope),f::failureDescription);
        var actual=scope.getCurrentTransaction().incoming(f.quality).stream()
            .filter(link->link.type()==GraphModel.Relationship.forContextualization(Contextualization.QUANTIFICATION))
            .map(link->(Activity)link.source()).findFirst().orElseThrow();
        assertEquals(Activity.Outcome.SUCCESS,actual.getOutcome());
        var proof=Utils.Json.parseObject(actual.getMetadata().get(SemanticEvidenceBinding.DATA_KEY).toString(),SemanticEvidenceBinding.class);
        assertTrue(proof.complete(),actual.getMetadata().toString());assertNotNull(proof.witness());
        assertTrue(proof.obligations().contains(f.quality.getObservable().getSemantics().getUrn()));
        assertTrue(scope.commit()>0,f::failureDescription);
        assertArrayEquals(new double[]{value,value,value},f.values(TemporalProcessIntegrationTest.START));
      }
    }
  }
  @Test void arbitraryMissingDescriptorStillCannotCompileAnExecutor()throws Exception {
    TemporalProcessIntegrationTest.configure();semantics();var owner=new TemporalProcessIntegrationTest();owner.directory=directory;
    try(var f=new Fixture(owner)) {
      var scope=f.root.executingFresh(Activity.of(Activity.Type.SUBMISSION),f.bearer);
      assertThrows(RuntimeException.class,()->new CompiledDataflow(f.runtime,f.quality,scope)
          .restoreLeafExecutor(plan(f,0,"unregistered.arbitrary")));
      scope.fail(new IllegalStateException("test cleanup"));
    }
  }
}
