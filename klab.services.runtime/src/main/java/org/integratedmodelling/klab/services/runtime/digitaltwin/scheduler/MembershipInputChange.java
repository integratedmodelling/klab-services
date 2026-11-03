package org.integratedmodelling.klab.services.runtime.digitaltwin.scheduler;

import java.util.*;
import org.integratedmodelling.klab.api.data.Metadata;
import org.integratedmodelling.klab.api.digitaltwin.DigitalTwin;
import org.integratedmodelling.klab.api.digitaltwin.Scheduler;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.knowledge.CollectiveReductionBinding;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.knowledge.observation.scale.time.Time;
import org.integratedmodelling.klab.api.knowledge.observation.scale.time.TimePeriod;
import org.integratedmodelling.klab.services.runtime.TemporalGeometry;
import org.integratedmodelling.klab.services.runtime.digitaltwin.DigitalTwinImpl;
import org.integratedmodelling.klab.services.scopes.ServiceContextScope;

/** Runtime-only invocation authority. Wire event values and caller supplied member lists are not
 * authority. A private committed token and this actual guarded computation transaction are required. */
public final class MembershipInputChange implements Scheduler.Event {
 private final DigitalTwinImpl.CommittedMembershipChange receipt;
 private final ServiceContextScope origin;
 private final DigitalTwin.Transaction transaction;
 private final org.integratedmodelling.klab.api.services.runtime.objects.ResolutionCycleBinding cycle;
 private final String context,id,user;
 private final Long observer;
 private final long focus,target;
 private final OptionalLong instant;
 private MembershipInputChange(DigitalTwinImpl.CommittedMembershipChange receipt,ServiceContextScope origin,
     ServiceContextScope execution,Observation target,OptionalLong instant) {
  receipt.require(origin);this.receipt=receipt;this.origin=origin;
  this.transaction=Objects.requireNonNull(execution.getCurrentTransaction());
  this.cycle=Objects.requireNonNull(execution.getResolutionCycleBinding());this.context=execution.getId();
  this.user=Objects.requireNonNull(execution.getUser()).getUsername();
  this.observer=execution.getObserverId();this.focus=execution.getContextObservation()==null?0:execution.getContextObservation().getId();
  this.target=target.getId();this.instant=instant;
  this.id="cohort-membership:"+receipt.change().cohortId()+":"+receipt.change().afterRevision()+":"+receipt.commitId()+":"+target.getId();
 }
 static MembershipInputChange admitted(DigitalTwinImpl.CommittedMembershipChange receipt,ServiceContextScope origin,
     ServiceContextScope execution,Observation target,OptionalLong instant) {
  return new MembershipInputChange(receipt,origin,execution,target,instant);
 }
 private void require(ServiceContextScope execution,Observation output) {
  receipt.require(origin);
  long actualFocus=execution.getContextObservation()==null?0:execution.getContextObservation().getId();
  var current=execution.getCurrentTransaction();
  while(current!=null && current.getParent()!=null)current=current.getParent();
  if(current!=transaction || transaction.isFailed()
      || execution.getUser()==null || !Objects.equals(user,execution.getUser().getUsername())
      || !cycle.equals(execution.getResolutionCycleBinding()) || !Objects.equals(context,execution.getId())
      || !Objects.equals(observer,execution.getObserverId()) || focus!=actualFocus
      || output==null || output.getId()!=target)
   throw new SecurityException("Membership invocation differs from its native guarded transaction");
  var clock=execution.getDigitalTwin().getScheduler().currentModeledInstant();
  if(!instant.equals(clock))throw new SecurityException("Membership invocation cannot advance modeled time");
 }
 public void requireInvocation(ServiceContextScope execution,Observation output,Geometry planning,Geometry actual) {
  require(execution,output);
  if(planning==null || actual==null)throw new SecurityException("Membership invocation support is unavailable");
  var expected=TemporalGeometry.localize(planning,this);
  boolean same=expected.encode().equals(actual.encode());
  if(!same && actual instanceof org.integratedmodelling.klab.api.knowledge.observation.scale.Scale scale)
    same=org.integratedmodelling.klab.api.knowledge.observation.scale.Scale.create(expected,execution).encode().equals(scale.encode());
  if(!same)throw new SecurityException("Membership invocation support differs from current-time localization");
 }
 public void requireReduction(ServiceContextScope execution,Observation output,CollectiveReductionBinding binding) {
  require(execution,output);
  if(binding==null)throw new SecurityException("Membership input has no collective reduction binding");
  requireCohortOperand(execution,binding.operand(),receipt.change().observable());
  binding.require(execution,output.getObservable(),Geometry.create(binding.support()));
 }
 public void requireCurrentMembers(ServiceContextScope execution,Observation output,Observation completed,List<Observation> members) {
  require(execution,output);
  var cohort=completed.getMetadata().get(Metadata.IM_QUERY_COHORT_ID,Number.class);
  if(cohort==null || cohort.longValue()!=receipt.change().cohortId())
   throw new SecurityException("Fresh completion query belongs to another cohort");
  var after=new HashSet<>(receipt.change().afterMembers());
  for(var member:members)if(member==null || member.getId()<=0 || !after.contains(member.getId()))
   throw new SecurityException("Fresh collective query contains a historical or uncommitted member");
 }
 public static void requireCohortOperand(org.integratedmodelling.klab.api.scope.ContextScope scope,
     String fullOperand,String cohortHeader) {
  var reasoner=scope.getService(org.integratedmodelling.klab.api.services.Reasoner.class);
  if(reasoner==null)throw new IllegalStateException("Retained semantic view is unavailable");
  var operand=reasoner.resolveObservable(fullOperand);var cohort=reasoner.resolveObservable(cohortHeader);
  if(operand==null||cohort==null)throw new SecurityException("Collective operand or cohort cannot be resolved");
  var base=reasoner.baseSubstantialType(operand,scope);var actual=reasoner.baseSubstantialType(cohort,scope);
  if(base==null||actual==null || base.is(org.integratedmodelling.klab.api.knowledge.SemanticType.NOTHING)
      ||actual.is(org.integratedmodelling.klab.api.knowledge.SemanticType.NOTHING)||!Objects.equals(base.singular().getUrn(),actual.singular().getUrn()))
   throw new SecurityException("Collective operand belongs to a different native cohort");
 }
 public boolean hasTime(){return instant.isPresent();}
 public Time getTime(){if(instant.isEmpty())throw new IllegalStateException("Untimed membership change has no modeled instant");return TimePeriod.create(instant.getAsLong(),instant.getAsLong());}
 public Type getType(){return Type.INPUT_CHANGE;}
 public String toKey(){return id;}
 public Observation getEvent(){return null;}
}
