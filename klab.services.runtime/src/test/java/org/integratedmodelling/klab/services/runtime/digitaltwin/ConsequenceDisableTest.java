package org.integratedmodelling.klab.services.runtime.digitaltwin;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.integratedmodelling.klab.api.configuration.Setting;
import org.integratedmodelling.klab.api.digitaltwin.Consequence;
import org.integratedmodelling.klab.api.provenance.Activity;
import org.integratedmodelling.klab.configuration.ServiceConfiguration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ConsequenceDisableTest {
  @BeforeAll static void configure(){ServiceConfiguration.injectInstantiators();}
  @Test void disabledFrozenCyclePreservesExplicitCommitWithoutAutomaticWork() throws Exception {
    try(var f=new ConsequenceEngineTest.Fixture()){
      var old=f.scope.getResolutionCycleBinding();
      var binding=new org.integratedmodelling.klab.api.services.runtime.objects.ResolutionCycleBinding(
          1,"disabled","context","runtime",false,old.semanticBinding());
      var cycle=new org.integratedmodelling.klab.runtime.lifecycle.ResolutionCycle<>(
          binding.cycleId(),binding.contextId(),false,binding,()->{});
      var disabled=f.scope.withResolutionCycle(cycle,binding);
      var calls=new AtomicInteger();
      for(var kind:Consequence.Kind.values())f.engine.register(kind,(cause,changes,scope)->{
        calls.incrementAndGet();return new ConsequenceEngine.Result(Consequence.Outcome.APPLIED,"unexpected",List.of());
      });
      var transaction=f.transaction(disabled);assertEquals(900L,transaction.commit());
      assertEquals(Activity.Outcome.SUCCESS,transaction.getActivity().getOutcome());
      cycle.close();cycle.quiescence().toCompletableFuture().get(5,TimeUnit.SECONDS);
      assertEquals(0,calls.get());assertNull(f.events.poll(100,TimeUnit.MILLISECONDS));
    }
  }
  @Test void missingSemanticViewExplicitlyDefersWithoutInvokingHandlers() throws Exception {
    try(var f=new ConsequenceEngineTest.Fixture()){
      var binding=new org.integratedmodelling.klab.api.services.runtime.objects.ResolutionCycleBinding(
          1,"unbound","context","runtime",true,null);
      var cycle=new org.integratedmodelling.klab.runtime.lifecycle.ResolutionCycle<>(
          binding.cycleId(),binding.contextId(),true,binding,()->{});
      var calls=new AtomicInteger();
      for(var kind:Consequence.Kind.values())f.engine.register(kind,(cause,changes,scope)->{
        calls.incrementAndGet();return new ConsequenceEngine.Result(Consequence.Outcome.APPLIED,"unexpected",List.of());
      });
      f.engine.committed(ConsequenceEngineTest.commit(5),f.scope.withResolutionCycle(cycle,binding),100,0);
      cycle.close();cycle.quiescence().toCompletableFuture().get(5,TimeUnit.SECONDS);
      assertEquals(0,calls.get());
      for(var event:f.messages(Consequence.Kind.values().length))
        assertEquals(Consequence.Outcome.DEFERRED,event.getPayload(Consequence.Decision.class).outcome());
    }
  }
  @Test void defaultIsEnabled(){assertEquals(Boolean.FALSE,Setting.DISABLE_CONSEQUENCE_ENGINE.defaultValue);}
}
