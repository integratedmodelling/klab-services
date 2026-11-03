package org.integratedmodelling.klab.services.runtime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.EnumSet;
import org.integratedmodelling.klab.api.digitaltwin.*;
import org.integratedmodelling.klab.api.identities.UserIdentity;
import org.integratedmodelling.klab.api.knowledge.*;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.knowledge.observation.impl.ObservationImpl;
import org.integratedmodelling.klab.api.services.runtime.objects.ResolutionCycleBinding;
import org.integratedmodelling.klab.runtime.lifecycle.ResolutionCycle;
import org.integratedmodelling.klab.services.scopes.*;
import org.junit.jupiter.api.Test;

class ConsequenceSubmissionScopeTest {
 private ServiceContextScope scope() {
   var user=mock(UserIdentity.class);when(user.getUsername()).thenReturn("tester");when(user.getId()).thenReturn("tester");
   var runtime=mock(org.integratedmodelling.klab.api.services.RuntimeService.class);
   var base=new ServiceContextScope(new ServiceSessionScope(new ServiceUserScope(user,runtime)),
       DigitalTwin.Configuration.builder().id("context").name("Twin").build(),user);base.setId("context");
   var recipient=new ObservationImpl();recipient.setId(41);recipient.setUrn("context.recipient");
   var observer=new ObservationImpl();observer.setId(91);var agent=mock(Observable.class);
   when(agent.is(SemanticType.AGENT)).thenReturn(true);observer.setObservable(agent);
   var binding=new ResolutionCycleBinding(1,"cycle","context","runtime",true,null);
   return base.within(recipient).withObserver(observer).withResolutionCycle(
       new ResolutionCycle<>("cycle","context",true,binding,()->{}),binding);
 }
 private Observation request(SemanticType type,boolean collective) {
   var target=mock(Observable.class);var semantics=mock(Concept.class);
   when(target.getSemantics()).thenReturn(semantics);when(semantics.getType()).thenReturn(EnumSet.of(type));
   when(semantics.isCollective()).thenReturn(collective);
   when(target.getUrn()).thenReturn(type==SemanticType.RELATIONSHIP ? "test:Connection linking test:Tree to test:Tree" : "each test:Tree");
   var request=new ObservationImpl();request.setObservable(target);return request;
 }
 @Test void consequenceEachAndRelationshipKeepSecondaryFocusObserverCycleAndRequestMeaning() {
   for(var type:new SemanticType[]{SemanticType.SUBJECT,SemanticType.RELATIONSHIP}) {
     var source=scope();var cause=new Consequence.Cause(1,"context",1,2,91,null,null,"decision",Consequence.Kind.ROLE_IMPLICATION);
     var admitted=source.forConsequence(cause).withConsequenceSubmissionAdmission((transaction,execution)->{});
     var target=request(type,type==SemanticType.SUBJECT);var originalMeaning=target.getObservable();var meaningUrn=originalMeaning.getUrn();
     var selected=RuntimeService.submissionScope(target,admitted);
     assertSame(admitted,selected);assertSame(source.getContextObservation(),selected.getContextObservation(),"for recipient supplies secondary inherency");
     assertSame(source.getObserver(),selected.getObserver());assertSame(source.getResolutionCycleBinding(),selected.getResolutionCycleBinding());
     assertSame(source.getResolutionCycle(),((ServiceContextScope)selected).getResolutionCycle());
     assertSame(admitted.getConsequenceSubmissionAdmission(),((ServiceContextScope)selected).getConsequenceSubmissionAdmission());
     assertSame(originalMeaning,target.getObservable());assertEquals(meaningUrn,target.getObservable().getUrn(),"Native target inherency must remain intact");
     source.getResolutionCycle().close();
   }
 }
 @Test void manualIndependentSubstantialStillResetsFocusAndPreservesConnection() {
   var source=scope();var selected=RuntimeService.submissionScope(request(SemanticType.SUBJECT,false),source);
   assertNotSame(source,selected);assertNull(selected.getContextObservation());assertNotNull(source.getContextObservation());
   assertSame(source.getObserver(),selected.getObserver());assertSame(source.getResolutionCycleBinding(),selected.getResolutionCycleBinding());
   source.getResolutionCycle().close();
 }
}
