package org.integratedmodelling.klab.services.runtime.digitaltwin.scheduler;
import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.integratedmodelling.klab.api.digitaltwin.*;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.services.runtime.digitaltwin.DigitalTwinImpl;
import org.integratedmodelling.klab.services.scopes.ServiceContextScope;

/** Actual scheduler gates against actual post-commit native currentness, not mocked status. */
public final class CurrentLifecycleSchedulingProbe {
 public static void verify(ServiceContextScope scope,DigitalTwinImpl twin,
     List<? extends Observation> ceased,List<? extends Observation> survivors)throws Exception {
  var scheduler=new SchedulerImpl(scope,twin);var calls=new AtomicInteger();
  try {
   var before=scheduler.currentModeledInstant();
   var dispatch=SchedulerImpl.class.getDeclaredMethod("dispatch",Observation.class,OccurrenceRegistration.class,TransitionEvent.class,boolean.class);dispatch.setAccessible(true);
   for(var target:ceased) {
    scheduler.registerExecutor(target,(g,e,s)->{calls.incrementAndGet();return true;});
    assertFalse(scheduler.submit(target,scope));
    assertFalse(scheduler.executeDependency(target,target.getGeometry(),Scheduler.Event.initialization(),scope));
    assertNull(scheduler.restoreExecutor(target,scope));
    assertEquals(true,dispatch.invoke(scheduler,target,null,new TransitionEvent("queued-before-cessation",100,200,null),true));
   }
   assertEquals(0,calls.get());assertEquals(before,scheduler.currentModeledInstant());
   for(var survivor:survivors)assertTrue(twin.getKnowledgeGraph().isObservationCurrent(survivor,scope));
  } finally {scheduler.close();}
 }
}
