package org.integratedmodelling.klab.services.runtime;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import java.util.*;
import org.integratedmodelling.common.knowledge.*;
import org.integratedmodelling.klab.api.data.*;
import org.integratedmodelling.klab.api.digitaltwin.*;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.knowledge.*;
import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.knowledge.observation.impl.ObservationImpl;
import org.integratedmodelling.klab.api.lang.kim.*;
import org.integratedmodelling.klab.api.lang.kim.impl.KimConceptImpl;
import org.integratedmodelling.klab.api.services.reasoner.objects.*;
import org.integratedmodelling.klab.services.runtime.digitaltwin.*;
import org.integratedmodelling.klab.services.scopes.*;

/** Existing-reference EACH unit setup; original query issuance still uses the real local Runtime. */
public final class RoleBackendDiscoveryFixture extends ConsequenceDiscoverySnapshotTest.Fixture {
  private static final ThreadLocal<List<RoleBackendDiscoveryFixture>> OWNED=ThreadLocal.withInitial(ArrayList::new);
  public static void closeOwned(){try{for(var value:OWNED.get())value.close();}finally{OWNED.remove();}}
  private final ObservationImpl source=new ObservationImpl();
  private final ConceptImpl role=new ConceptImpl();
  private final ServiceContextScope execution;
  private final DefaultRoleConsequenceRuntime backend;
  public RoleBackendDiscoveryFixture(int count)throws Exception {
    super(count);OWNED.get().add(this);
    var process=new ConceptImpl();process.setUrn("test:Process");process.setName("Process");process.setNamespace("test");process.setReferenceName("test_process");process.setType(EnumSet.of(SemanticType.PROCESS,SemanticType.OBSERVABLE));
    var processObservable=ObservableImpl.promote(process,scope());processObservable.setDescriptionType(Contextualization.SIMULATION);source.setId(70);source.setUrn("context.70");source.setObservable(processObservable);source.setGeometry(Geometry.create("1"));
    nativeAssets().put(70L,snapshot(source,GraphModel.Labels.OBSERVATION));
    nativeEdges().add(new KnowledgeGraph.ConsequenceEdgeSnapshot("root-process",new KnowledgeGraph.ConsequenceAssetRef("root","context",List.of(GraphModel.Labels.CONTEXT)),ref(70),GraphModel.Relationship.HAS_CHILD,Map.of()));

    var knownProducer=(ObservationImpl)decode(200,Observation.class);knownProducer.setParentId(RuntimeAsset.CONTEXT_ASSET_ID);nativeAssets().put(200L,snapshot(knownProducer,GraphModel.Labels.OBSERVATION));
    var birth=(org.integratedmodelling.klab.api.provenance.impl.ActivityImpl)org.integratedmodelling.klab.api.provenance.Activity.of(org.integratedmodelling.klab.api.provenance.Activity.Type.SIMULATION);
    birth.setId(350);birth.setOutcome(org.integratedmodelling.klab.api.provenance.Activity.Outcome.SUCCESS);
    var birthRequest=new SemanticDependencyRequest("reasoner",1,scope().getResolutionCycleBinding().semanticBinding().semanticFingerprint(),List.of("test:Process","test:ControlledSourceModel"));
    birth.getMetadata().put(SemanticEvidenceBinding.DATA_KEY,org.integratedmodelling.klab.utilities.Utils.Json.asString(new SemanticEvidenceBinding(SemanticEvidenceBinding.VERSION,true,birthRequest.obligations(),proof(birthRequest))));
    nativeAssets().put(350L,snapshot(birth,GraphModel.Labels.ACTIVITY));nativeEdges().add(new KnowledgeGraph.ConsequenceEdgeSnapshot("source-proof",new KnowledgeGraph.ConsequenceAssetRef("native-350",350L,List.of(GraphModel.Labels.ACTIVITY)),ref(70),GraphModel.Relationship.SIMULATED,Map.of()));
    role.setUrn("test:Marked");role.setName("Marked");role.setNamespace("test");role.setReferenceName("test_marked");role.setType(EnumSet.of(SemanticType.ROLE,SemanticType.PREDICATE));
    when(reasoner().resolveObservable(process.getUrn())).thenReturn(processObservable);when(reasoner().resolveConcept(process.getUrn())).thenReturn(process);
    when(reasoner().resolveConcept(role.getUrn())).thenReturn(role);
    when(reasoner().buildObservable(any(),any())).thenAnswer(call->{
      var strategy=call.getArgument(0,ObservableBuildStrategy.class);
      var result=new ObservableImpl((ObservableImpl)strategy.getBaseObservable());result.setOptional(true);return result;
    });
    var roleSyntax=new KimConceptImpl();roleSyntax.setUrn(role.getUrn());var recipient=new KimConceptImpl();recipient.setUrn("test:Stream");
    var rule=new KimConsequenceRule(KimConsequenceRule.Kind.CONFERS,roleSyntax,new KimConsequenceRule.Recipient(KimConsequenceRule.RecipientKind.EXPRESSION,recipient),null,null,
        new KimConceptStatement.DeclarationClause("confers","confers test:Marked to test:Stream",1,32));
    var entry=new ConsequenceRuleResponse.Entry("rule","test:Process",process,rule,"test","1","c".repeat(64));
    var epoch=scope().getResolutionCycleBinding().semanticBinding();
    when(reasoner().consequenceRules(any())).thenReturn(new ConsequenceRuleResponse(ConsequenceRuleResponse.Status.COMPLETE,
        new ConsequenceRuleResponse.Epoch(epoch.reasonerServiceId(),epoch.knowledgeRevision(),epoch.semanticFingerprint(),epoch.reasonerSessionId()),List.of(entry),proof(new SemanticDependencyRequest("reasoner",1,epoch.semanticFingerprint(),List.of("test:Process","test:Marked","test:Stream"))),"complete"));
    // Same trusted context copies are normal request scopes. The native in-memory backend is
    // the ordinary Runtime query backend; it is not the closed captured replay projection.
    doAnswer(call->{cycles.require(call.getArgument(1));return sourceQuery(call.getArgument(0));}).when(graph()).query(any(Class.class),any());
    doAnswer(call->{cycles.require(call.getArgument(1));return decode(((Number)call.getArgument(0)).longValue(),call.getArgument(2));}).when(graph()).getAsset(anyLong(),any(),any(Class.class));
    doAnswer(call->{cycles.require(call.getArgument(0));return transaction();}).when(graph()).createTransaction(any());
    var cause=new Consequence.Cause(1,"context",1,300,0,null,null,"test-decision",Consequence.Kind.ROLE_CONFERRAL);
    execution=new ReadyReferenceScope(scope().forConsequence(cause),decode(200,Observation.class) instanceof Observation producer?producer:null);
    var producer=(Observation)decode(200,Observation.class);
    backend=new DefaultRoleConsequenceRuntime(runtime(),sourceScope->new ReadyReferenceScope(cycles.require(sourceScope),producer),null);
  }
  /** Only the initial already-known producer reference is supplied; normal ID-0 query is real. */
  private static final class ReadyReferenceScope extends ServiceContextScope {
    private final Observation producer;
    ReadyReferenceScope(ServiceContextScope parent,Observation producer){super(parent);this.producer=Objects.requireNonNull(producer);}
    @Override public ServiceContextScope withConsequenceSubmissionAdmission(ConsequenceSubmissionAdmission admission) {
      return new ReadyReferenceScope(super.withConsequenceSubmissionAdmission(admission),producer);
    }
    @Override public Observation.Builder observation(Observable observable) {
      if(observable.getUrn().equals(producer.getObservable().getUrn()))return new Observation.NaiveBuilder(observable,this) {
        @Override public ObservationImpl build(){return (ObservationImpl)Observation.forTransport(producer);}
      };
      return super.observation(observable);
    }
  }
  /** The ordinary downstream collector must inherit the actual staged scientific Activity. */
  public static SemanticEvidenceBinding inheritActivity(org.integratedmodelling.klab.api.provenance.Activity activity,ServiceContextScope scope) {
    var collector=new SemanticExecutionEvidence.Collector();collector.inherit(SemanticExecutionEvidence.decode(activity),scope);return collector.finish(scope);
  }
  public DefaultRoleConsequenceRuntime backend(){return backend;}
  public Observation source(){return source;}
  public ServiceContextScope execution(){return execution;}
}
