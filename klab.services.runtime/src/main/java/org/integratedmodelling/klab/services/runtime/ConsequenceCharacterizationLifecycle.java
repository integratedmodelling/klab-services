package org.integratedmodelling.klab.services.runtime;

import java.util.Objects;
import java.util.concurrent.*;
import org.integratedmodelling.klab.api.digitaltwin.Consequence;
import org.integratedmodelling.klab.api.digitaltwin.Scheduler;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.knowledge.Contextualization;
import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.api.knowledge.SemanticType;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.provenance.Activity;
import org.integratedmodelling.klab.api.services.Resolver;
import org.integratedmodelling.klab.api.services.runtime.Dataflow;
import org.integratedmodelling.klab.services.runtime.digitaltwin.DigitalTwinImpl;
import org.integratedmodelling.klab.services.scopes.ServiceContextScope;

/** Runtime-owned semantic UPDATE lifecycle; a directive is never registered as an observation. */
final class ConsequenceCharacterizationLifecycle {
  private ConsequenceCharacterizationLifecycle() {}

  static Dataflow.ResolutionOutcome run(RuntimeService runtime,Observation recipient,
      Observable requested,ServiceContextScope admitted)throws InterruptedException {
    Objects.requireNonNull(runtime);Objects.requireNonNull(admitted);
    if(!(admitted.getDigitalTwin() instanceof DigitalTwinImpl twin))
      throw new IllegalStateException("Consequence characterization requires the local digital twin");
    var scope=twin.getResolutionCycles().require(admitted);
    check(scope);
    if(scope.getCurrentTransaction()!=null||scope.getConsequenceCause()==null
        ||scope.getConsequenceCause().kind()!=Consequence.Kind.ROLE_IMPLICATION
        ||scope.getResolutionCycleBinding()==null||scope.getResolutionCycleBinding().semanticBinding()==null
        ||!Objects.equals(runtime.serviceId(),scope.getResolutionCycleBinding().runtimeServiceId())
        ||!scope.getResolutionCycleBinding().consequencesEnabled())
      throw new IllegalStateException("Characterization requires an active consequence pass without an inherited transaction");
    var admission=Objects.requireNonNull(scope.getConsequenceSubmissionAdmission(),"Consequence target admission");
    if(requested==null||requested.getContextualization()!=Contextualization.CHARACTERIZATION
        ||requested.getSemantics()==null||requested.getSemantics().isCollective()||!requested.is(SemanticType.ROLE)
        ||requested.getUrn()==null||recipient==null||recipient.getId()<=0)
      throw new IllegalArgumentException("An exact characterization directive and durable recipient are required");
    var focus=scope.getContextObservation();var member=scope.getObservation(recipient.getId());
    if(focus==null||focus.getId()!=recipient.getId()||member==null||member.isEmpty()
        ||member.getObservable()==null||member.getObservable().getSemantics().isCollective()
        ||!SemanticType.isEnumerableSubstantial(member.getObservable().getSemantics().getType())
        ||recipient.getObservable()==null||recipient.getGeometry()==null||member.getGeometry()==null
        ||!Objects.equals(member.getObservable().getUrn(),recipient.getObservable().getUrn())
        ||!Objects.equals(Geometry.forTransport(member.getGeometry()).encode(),Geometry.forTransport(recipient.getGeometry()).encode()))
      throw new IllegalStateException("Characterization recipient or original focus is no longer accessible");
    // Exact original target, before optionality normalization, is checked by the server's
    // private RequestAdmission. A portable cycle envelope alone is not authorization.
    admission.requireTarget(requested,scope);
    var optional=requested.builder(scope).optional(true).buildObservable();
    if(optional==null||optional.getContextualization()!=Contextualization.CHARACTERIZATION)
      throw new IllegalStateException("Optional characterization construction failed");
    var submission=Activity.of(Activity.Type.CONSEQUENCE_SUBMISSION,scope.getActivity(),"Post-conferral characterization");
    org.integratedmodelling.common.runtime.ObserverProvenance.recordSelection(submission,scope.getObserver());
    // A durable recipient is an UPDATE target, never a newly CREATED observation.
    var execution=scope.executing(submission);
    try {
      admission.bind(execution.getCurrentTransaction(),execution);
      check(execution);
      var activity=Activity.of(Activity.Type.RESOLUTION,submission,"Role characterization resolution");
      org.integratedmodelling.common.runtime.ObserverProvenance.recordSelection(activity,execution.getObserver());
      var resolution=execution.executing(activity);
      try {
        var builder=new Observation.NaiveBuilder(optional,resolution);builder.geometry(member.getGeometry());
        var request=builder.make();
        if(request==null||request.isEmpty())throw new IllegalStateException("Characterization directive construction failed");
        var resolver=Objects.requireNonNull(resolution.getService(Resolver.class),"Characterization resolver");
        var plan=await(resolver.resolve(request,resolution));check(resolution);
        if(plan==null||plan.getResolutionOutcome()==null)
          throw new IllegalStateException("Characterization resolution outcome is unavailable");
        if(plan.getResolutionOutcome()==Dataflow.ResolutionOutcome.DEFERRED) {
          if(org.integratedmodelling.common.utils.Utils.Notifications.hasErrors(plan.getNotifications()))
            throw new IllegalStateException("Deferred characterization contains resolution errors");
          // A partial plan must not publish semantic changes or become NO_MODEL.
          RuntimeService.attachResolutionDiagnostics(plan,activity);
          activity.getMetadata().put("resolutionOutcome",Dataflow.ResolutionOutcome.DEFERRED.name());
          execution.getCurrentTransaction().fail(null);return Dataflow.ResolutionOutcome.DEFERRED;
        }
        CharacterizationLifecycle.validate(plan);
        RuntimeService.attachResolutionDiagnostics(plan,activity);
        activity.getMetadata().put("resolutionOutcome",plan.getResolutionOutcome().name());
        if(plan.getResolutionOutcome()!=Dataflow.ResolutionOutcome.NO_MODEL)
          runtime.executeCharacterization(plan,member,Scheduler.Event.initialization(),resolution);
        check(resolution);
        if(resolution.commit()<0)throw new IllegalStateException("Characterization resolution commit failed");
        check(execution);
        if(execution.commit()<=0)throw new IllegalStateException("Characterization root commit failed");
        return plan.getResolutionOutcome();
      } catch(Throwable failure) {fail(resolution,failure);throw failure;}
    } catch(InterruptedException interrupted) {fail(execution,interrupted);throw interrupted;}
    catch(RuntimeException|Error failure){fail(execution,failure);throw failure;}
  }

  private static void fail(ServiceContextScope scope,Throwable failure) {
    try{scope.fail(failure);}
    catch(Throwable cleanup){if(cleanup!=failure)failure.addSuppressed(cleanup);}
  }

  private static void check(ServiceContextScope scope)throws InterruptedException {
    if(Thread.currentThread().isInterrupted()||scope.isInterrupted())throw new InterruptedException("Consequence characterization cancelled");
  }
  private static Dataflow await(CompletableFuture<Dataflow> task)throws InterruptedException {
    boolean interrupted=false;
    try {
      for(;;)try{return task.get();}
      catch(InterruptedException cancellation){interrupted=true;}
      catch(ExecutionException failure){throw new CompletionException(failure.getCause());}
    } finally {
      if(interrupted){Thread.currentThread().interrupt();throw new InterruptedException("Characterization cancelled after resolver drain");}
    }
  }
}
