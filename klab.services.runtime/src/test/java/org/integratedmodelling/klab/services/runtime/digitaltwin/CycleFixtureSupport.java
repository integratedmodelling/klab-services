package org.integratedmodelling.klab.services.runtime.digitaltwin;

import static org.mockito.Mockito.*;
import java.util.concurrent.CompletableFuture;
import org.integratedmodelling.klab.api.services.Reasoner;
import org.integratedmodelling.klab.api.services.runtime.objects.ResolutionCycleBinding;
import org.integratedmodelling.klab.runtime.lifecycle.ResolutionCycle;
import org.integratedmodelling.klab.services.scopes.ServiceContextScope;

/** Numeric scheduling fixtures isolate admission; real lifecycle/transport have separate tests. */
public final class CycleFixtureSupport {
 private CycleFixtureSupport() {}
 public static void isolate(DigitalTwinImpl twin) {
   var cycles=mock(ResolutionCycles.class);
   when(twin.getResolutionCycles()).thenReturn(cycles);
   org.mockito.stubbing.Answer<CompletableFuture<ResolutionCycles.Admission>> admitted = call->{
     var admission=mock(ResolutionCycles.Admission.class);
     when(admission.scope()).thenReturn(call.getArgument(0));
     return CompletableFuture.completedFuture(admission);
   };
   when(cycles.admit(any(ServiceContextScope.class),anyBoolean())).thenAnswer(admitted);
   when(cycles.admit(any(ServiceContextScope.class),any(java.util.function.BooleanSupplier.class))).thenAnswer(admitted);
 }
 public static void transparentCycleOverlay(ServiceContextScope scope) {
   when(scope.withResolutionCycle(nullable(ResolutionCycle.class),nullable(ResolutionCycleBinding.class))).thenReturn(scope);
   when(scope.withCycleReasoner(nullable(Reasoner.class))).thenReturn(scope);
 }
}
