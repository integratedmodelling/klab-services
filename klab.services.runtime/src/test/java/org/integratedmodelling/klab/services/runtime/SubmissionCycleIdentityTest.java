package org.integratedmodelling.klab.services.runtime;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.integratedmodelling.klab.api.knowledge.Concept;
import org.integratedmodelling.klab.api.knowledge.SemanticType;
import org.integratedmodelling.klab.api.services.runtime.objects.ResolutionCycleBinding;
import org.integratedmodelling.klab.configuration.ServiceConfiguration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
class SubmissionCycleIdentityTest {
 @BeforeAll static void configure(){ServiceConfiguration.injectInstantiators();}
 @Test void sameCycleSharesInflightIdentityButDifferentFrozenCycleCannot() throws Exception {
   var f=new SubmissionCancellationTest.Fixture();f.scope.setId("cancellation-test");
   var registry=RuntimeService.class.getDeclaredField("inFlightSubmissions");registry.setAccessible(true);
   registry.set(f.runtime,new ConcurrentHashMap<>());
   var base=mock(Concept.class);when(base.getUrn()).thenReturn("test:subject");
   when(f.reasoner.baseSubstantialType(any(),any())).thenReturn(base);
   // The phase fixture bypasses real root admission, allowing both identities to be held pending.
   // Real per-twin serialization and semantic capture are tested by RuntimeResolutionCycleTest.
   var a=f.scope.withResolutionCycleBinding(new ResolutionCycleBinding(1,"a","cancellation-test","runtime",true,null));
   var b=f.scope.withResolutionCycleBinding(new ResolutionCycleBinding(1,"b","cancellation-test","runtime",true,null));
   var scopes=new java.util.concurrent.ConcurrentLinkedQueue<org.integratedmodelling.klab.api.scope.ContextScope>();
   scopes.add(a);scopes.add(a);scopes.add(b);
   doAnswer(call->((java.util.function.Function<org.integratedmodelling.klab.api.scope.ContextScope,
       CompletableFuture<org.integratedmodelling.klab.api.knowledge.observation.Observation>>)call.getArgument(2)).apply(scopes.remove()))
       .when(f.runtime).withinResolutionCycle(any(),any(),any());
   var first=f.submit(SemanticType.SUBJECT,"same");var sibling=f.submit(SemanticType.SUBJECT,"same");
   verify(f.resolver).resolve(any(),any());var other=f.submit(SemanticType.SUBJECT,"same");
   verify(f.resolver,times(2)).resolve(any(),any());f.resolution.complete(f.dataflow);
   assertFalse(first.join().isEmpty());assertFalse(sibling.join().isEmpty());assertFalse(other.join().isEmpty());
 }
 @Test void completedCoalescedWorkRetiresBeforeSynchronousCallbackSubmitsAgain() {
   var map=new ConcurrentHashMap<String,CompletableFuture<String>>();var actual=new CompletableFuture<String>();
   var count=new AtomicInteger();
   var first=RuntimeService.coalesce(map,"identity",()->{count.incrementAndGet();return actual;});
   var callback=first.thenApply(value->RuntimeService.coalesce(map,"identity",()->{
     count.incrementAndGet();return CompletableFuture.completedFuture("new");}).join());
   actual.complete("old");assertEquals("new",callback.join());assertEquals(2,count.get());assertTrue(map.isEmpty());
 }
}
