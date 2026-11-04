package org.integratedmodelling.klab.runtime.lifecycle;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.integratedmodelling.klab.api.ServicesAPI;
import org.integratedmodelling.klab.api.digitaltwin.Consequence;
import org.integratedmodelling.klab.api.digitaltwin.DigitalTwin;
import org.integratedmodelling.klab.api.identities.UserIdentity;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.services.RuntimeService;
import org.integratedmodelling.klab.api.services.reasoner.objects.SemanticCycleBinding;
import org.integratedmodelling.klab.api.services.runtime.objects.ResolutionCycleBinding;
import org.integratedmodelling.klab.services.scopes.*;
import org.integratedmodelling.klab.utilities.Utils;
import org.junit.jupiter.api.Test;

class ResolutionCycleScopeTest {
  @Test void nestedConsequenceRetainsEngineLifetimeWhileDetachingCompletedUserRequest() {
    var requestCancelled = new java.util.concurrent.atomic.AtomicBoolean();
    var engineClosed = new java.util.concurrent.atomic.AtomicBoolean();
    var source = scope().withCancellation(requestCancelled::get);
    var cause = new Consequence.Cause(1,"context",1,2,0,null,null,"decision",Consequence.Kind.CONFIGURATION_DETECTION);
    var handler = source.forConsequence(cause).withConsequenceCancellation(engineClosed::get);
    var nested = handler.forConsequence(cause);
    var membership = nested.forConsequence(null);
    requestCancelled.set(true);
    assertTrue(source.isInterrupted());
    assertFalse(handler.isInterrupted()); assertFalse(nested.isInterrupted()); assertFalse(membership.isInterrupted());
    engineClosed.set(true);
    assertTrue(handler.isInterrupted()); assertTrue(nested.isInterrupted()); assertTrue(membership.isInterrupted());
    assertTrue(handler.forConsequence(cause).isInterrupted(), "New nested work must see an already closed engine");
  }
  private ServiceContextScope scope() {
    var user=mock(UserIdentity.class);when(user.getUsername()).thenReturn("tester");when(user.getId()).thenReturn("tester");
    var scope=new ServiceContextScope(new ServiceSessionScope(new ServiceUserScope(user,mock(RuntimeService.class))),
        DigitalTwin.Configuration.builder().id("context").name("Twin").build(),user);
    scope.setId("context");return scope;
  }
  private ResolutionCycleBinding binding(String context) {
    return new ResolutionCycleBinding(1,"cycle",context,"runtime",false,
        new SemanticCycleBinding(SemanticCycleBinding.VERSION,"reasoner","session",7,"semantic-content-v2:"+"a".repeat(64),"lease"));
  }
  @Test void connectionCopiesAndConsequencesPreserveFrozenIdentityButDoNotRetainOwnership() {
    var root=scope();var binding=binding("context");var released=new AtomicInteger();
    var cycle=new ResolutionCycle<>(binding.cycleId(),binding.contextId(),binding.consequencesEnabled(),binding,released::incrementAndGet);
    var frozen=root.withResolutionCycle(cycle,binding);
    var cause=new Consequence.Cause(1,"context",1,2,0,null,null,"decision",Consequence.Kind.CHANGE_PROCESS);
    var derived=frozen.withObserver(null).forConsequence(cause);
    assertEquals(binding,derived.getResolutionCycleBinding());assertSame(cycle,derived.getResolutionCycle());
    assertNull(root.getResolutionCycleBinding());
    var remote=derived.withResolutionCycleBinding(binding);assertNull(remote.getResolutionCycle());
    cycle.close();cycle.quiescence().toCompletableFuture().join();assertEquals(1,released.get());
    assertThrows(IllegalStateException.class,cycle::retain);
  }
  @Test void remoteRestoreRejectsForeignContextAndDoesNotLeakIntoOrdinaryScope() {
    var root=scope();var manager=new ScopeManager(mock(org.integratedmodelling.klab.api.services.Reasoner.class));
    try {
      var token=ContextScope.parseScopeId("context");var binding=binding("context");
      var encoded=Base64.getEncoder().encodeToString(Utils.Json.asString(binding).getBytes(StandardCharsets.UTF_8));
      var restored=(ServiceContextScope)manager.contextualizeScope(root,token,Map.of(ServicesAPI.RESOLUTION_CYCLE_HEADER,encoded));
      assertEquals(binding,restored.getResolutionCycleBinding());assertNull(restored.getResolutionCycle());
      assertNull(manager.contextualizeScope(root,token,Map.of()).getResolutionCycleBinding());
      var foreign=Base64.getEncoder().encodeToString(Utils.Json.asString(binding("foreign")).getBytes(StandardCharsets.UTF_8));
      assertThrows(IllegalArgumentException.class,()->manager.contextualizeScope(root,token,Map.of(ServicesAPI.RESOLUTION_CYCLE_HEADER,foreign)));
    }finally{manager.shutdown();}
  }
  @Test void sourceAdmissionReceivesOnlyActualPositiveRootCommitReceipts() {
    var admission=mock(org.integratedmodelling.klab.services.scopes.ConsequenceSubmissionAdmission.class);
    var root=scope().withConsequenceSubmissionAdmission(admission);
    for(long outcome:new long[]{0,-1,37}){
      var transaction=mock(DigitalTwin.Transaction.class);when(transaction.commit()).thenReturn(outcome);
      root.withTransaction(transaction).commit();
    }
    var child=mock(DigitalTwin.Transaction.class);when(child.getParent()).thenReturn(mock(DigitalTwin.Transaction.class));
    when(child.commit()).thenReturn(39L);root.withTransaction(child).commit();
    verify(admission).committed(37L);verifyNoMoreInteractions(admission);
    assertNull(root.forConsequence(null).getConsequenceSubmissionAdmission());
    assertNull(root.withoutConsequenceSubmissionAdmission().getConsequenceSubmissionAdmission());
  }

}
