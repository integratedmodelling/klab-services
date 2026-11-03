package org.integratedmodelling.klab.services.runtime;

import java.util.*;
import org.integratedmodelling.common.lang.ServiceCallImpl;
import org.integratedmodelling.klab.api.data.Metadata;
import org.integratedmodelling.klab.api.digitaltwin.Scheduler;
import org.integratedmodelling.klab.api.knowledge.CollectiveReductionBinding;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.services.Reasoner;
import org.integratedmodelling.klab.api.services.runtime.Actuator;
import org.integratedmodelling.klab.services.scopes.ServiceContextScope;

/** Certified collective query followed by a real scalar-storage computation. */
final class CollectiveReductionExecutor implements CompiledDataflow.ContextualExecutor {
  private final RuntimeService runtime;
  private final Observation output;
  private final Actuator actuator;
  private final CollectiveReductionBinding binding;
  private Throwable cause;

  CollectiveReductionExecutor(RuntimeService runtime, Actuator actuator, CollectiveReductionBinding binding) {
    this.runtime = runtime; this.actuator = actuator; this.output = actuator.getObservation(); this.binding = binding;
  }

  private record NativeRead(Observation view,List<Observation> members) {}
  private static final class ReadProof {
    final NativeRead read;final ConsequenceDiscoverySnapshot.Complete discovery;final ServiceContextScope execution;final Scheduler.Event event;
    final org.integratedmodelling.klab.api.digitaltwin.DigitalTwin.Transaction transaction;
    final org.integratedmodelling.klab.api.services.runtime.objects.ResolutionCycleBinding cycle;
    final List<Observation> inputs;ScalarOperationExecutor writer;
    ReadProof(NativeRead read,ConsequenceDiscoverySnapshot.Complete discovery,ServiceContextScope execution,Scheduler.Event event) {
      this.read=read;this.discovery=discovery;this.execution=execution;this.event=event;this.transaction=execution.getCurrentTransaction();
      this.cycle=execution.getResolutionCycleBinding();var all=new ArrayList<Observation>();all.add(read.view());
      all.addAll(read.members());inputs=List.copyOf(all);
    }
    boolean same(ServiceContextScope scope,Scheduler.Event invocation) {
      return execution==scope && event==invocation && transaction==scope.getCurrentTransaction()
          && Objects.equals(cycle,scope.getResolutionCycleBinding());
    }
  }
  private ReadProof readProof;

  /** Finite built-in reads are certifiable only with this real committed native membership event.
   * INIT keeps UNKNOWN until its provisional/transient source closure has equivalent authority. */
  Optional<List<Observation>> semanticInputs(ServiceContextScope scope,Scheduler.Event event) {
    if(!(event instanceof org.integratedmodelling.klab.services.runtime.digitaltwin.scheduler.MembershipInputChange))
      return Optional.empty();
    try {
      if(readProof==null || !readProof.same(scope,event)) {
        var read=read(event,scope);
        if(!issuedComplete(read.view(),scope))return Optional.empty();
        if(!(scope.getCurrentTransaction() instanceof org.integratedmodelling.klab.services.runtime.digitaltwin.DigitalTwinImpl.TransactionImpl transaction))
          return Optional.empty();
        var discovery=ConsequenceDiscoverySnapshot.captureOrdinaryInputs(runtime,read.view(),scope);
        discovery.requireExactMembers(read.members());discovery.revalidate(scope);
        transaction.guardConsequenceReadSet(discovery.inputs());
        readProof=new ReadProof(read,discovery,scope,event);
      } else if(!revalidate(readProof,scope))return Optional.empty();
      if(readProof.writer!=null) {
        var scalar=readProof.writer.semanticInputs(scope);
        if(scalar.isEmpty() || !scalar.get().isEmpty())return Optional.empty();
      }
      return Optional.of(readProof.inputs);
    } catch(RuntimeException unavailable) {org.integratedmodelling.klab.runtime.computation.ScalarComputationGroovy.diagnoseFailure(unavailable,output,event,scope);return Optional.empty();}
  }
  private boolean issuedComplete(Observation view,ServiceContextScope scope) {
    var evidence=SemanticExecutionEvidence.issuedForConsequence(view,scope);
    return evidence!=null && evidence.complete() && evidence.witness()!=null
        && evidence.witness().status()==org.integratedmodelling.klab.api.services.reasoner.objects.SemanticDependencyWitness.Status.COMPLETE;
  }
  private boolean revalidate(ReadProof proof,ServiceContextScope scope) {
    proof.discovery.revalidate(scope);
    proof.discovery.requireExactMembers(proof.read.members());
    if(!issuedComplete(proof.read.view(),scope))return false;
    var fresh=read(proof.event,scope);
    if(!issuedComplete(fresh.view(),scope) || !SemanticExecutionEvidence.sameCheckedPayload(proof.read.view(),fresh.view())
        || proof.read.members().size()!=fresh.members().size())return false;
    var actual=new HashMap<Long,Observation>();for(var member:fresh.members())actual.put(member.getId(),member);
    for(var member:proof.read.members()) {
      var checked=actual.get(member.getId());
      if(checked==null || !SemanticExecutionEvidence.sameCheckedPayload(member,checked))return false;
    }
    return true;
  }
  /** The admitted request retains whole-support intent; native scale encoding may discard it.
   * Both original request and current native physical support must independently match. */
  private org.integratedmodelling.klab.api.geometry.Geometry reductionSupport(ServiceContextScope scope) {
    var requested=actuator.getRequestedSupport();
    binding.require(scope,actuator.getOperationObservable(),requested);
    binding.require(scope,actuator.getOperationObservable(),
        org.integratedmodelling.common.knowledge.GeometryRepository.INSTANCE.scale(output.getGeometry(),scope));
    return requested;
  }
  private NativeRead read(Scheduler.Event event,ServiceContextScope scope) {
    var membership=event instanceof org.integratedmodelling.klab.services.runtime.digitaltwin.scheduler.MembershipInputChange admitted?admitted:null;
    if(membership!=null)membership.requireReduction(scope,output,binding);
    var requested=reductionSupport(scope);
    var collective=scope.getService(Reasoner.class).resolveObservable(binding.operand());
    var builder=new Observation.NaiveBuilder(collective,scope);builder.geometry(requested).query();
    var completed=runtime.queryCollective(builder.make(),scope,binding);
    if(completed==null||completed.isEmpty())throw new IllegalStateException("Collective discovery is unresolved; no zero/absence can be produced");
    var coverage=completed.getMetadata().get(Metadata.IM_QUERY_COVERAGE,Number.class);
    if(coverage==null||!Double.isFinite(coverage.doubleValue())||coverage.doubleValue()<1.0-1e-9)
      throw new IllegalStateException("Collective discovery does not cover the whole requested support");
    var members=new ArrayList<Observation>();runtime.getMembers(completed,scope).forEach(members::add);
    if(membership!=null)membership.requireCurrentMembers(scope,output,completed,members);
    for(var member:members) {
      if(member==null||member.isEmpty()||member.getId()==Observation.UNASSIGNED_ID||member.getId()==Observation.QUERY_ID||member.getTransientId()==0)
        throw new IllegalStateException("Unregistered or invalid collective member");
      runtime.requireReductionMemberObserver(member,scope,binding);
    }
    return new NativeRead(completed,List.copyOf(members));
  }

  @Override public boolean validate() { return output != null && binding != null; }
  @Override public Throwable getCause() { return cause; }

  @Override public boolean execute(Scheduler.Event event, ServiceContextScope scope,
      RuntimeService.ContextualizationScope results) {
    try {
      var membership=event instanceof org.integratedmodelling.klab.services.runtime.digitaltwin.scheduler.MembershipInputChange admitted
          ?admitted:null;
      if (event == null || event.getType() != Scheduler.Event.Type.INITIALIZATION && membership==null)
        throw new IllegalStateException("Collective reduction requires initialization or an admitted native membership change");
      NativeRead consumed;
      if(readProof!=null&&readProof.same(scope,event)) {
        if(!revalidate(readProof,scope))throw new IllegalStateException("Collective semantic/native reads changed before execution");
        consumed=readProof.read;
      } else consumed=read(event,scope);
      var ids=new HashSet<String>();
      for(var member:consumed.members())ids.add(member.getId()>0?"id:"+member.getId():"transient:"+member.getTransientId());
      Object value = binding.mode() == CollectiveReductionBinding.Mode.COUNT ? (Object)(long)ids.size() : !ids.isEmpty();
      reductionSupport(scope);
      var builder = runtime.getComputationBuilder(output, scope, actuator, Map.of());
      if (!builder.add(new ServiceCallImpl("klab.core.constant.resolver", "value", value)))
        throw new IllegalStateException("Cannot compile collective scalar result");
      var writer = new ScalarOperationExecutor(builder, output, Map.of(), scope, runtime.shardExecution());
      if (!writer.validate() || !writer.execute(event, scope, results)) {
        cause = writer.getCause();
        if (cause == null) cause = new IllegalStateException("Collective scalar storage failed");
        return false;
      }
      reductionSupport(scope);
      if(readProof!=null&&readProof.same(scope,event))readProof.writer=writer;
      return true;
    } catch (RuntimeException failure) { cause = failure; return false; }
  }
}
