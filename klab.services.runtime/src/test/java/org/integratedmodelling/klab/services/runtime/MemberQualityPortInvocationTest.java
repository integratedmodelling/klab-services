package org.integratedmodelling.klab.services.runtime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import org.integratedmodelling.common.lang.*;
import org.integratedmodelling.klab.api.digitaltwin.Scheduler;
import org.integratedmodelling.klab.api.services.runtime.extension.*;
import org.integratedmodelling.klab.api.services.runtime.objects.*;
import org.integratedmodelling.klab.components.ComponentRegistry;
import org.junit.jupiter.api.*;

/** Actual registered-method invocation/injection; scalar storage is exercised separately. */
class MemberQualityPortInvocationTest {
  static final AtomicReference<List<Long>> seen=new AtomicReference<>();
  public static boolean inspect(@KlabFunction.Input(name="gradients") MemberQualityCollection values) {
    seen.set(values.getEntries().stream().map(entry->entry.getMember().getId()).toList());return true;
  }
  public static boolean ordinary() {return true;}
  MemberQualityResolutionTest f;MemberQualityCollection view;ComponentRegistry registry;
  Extensions.FunctionDescriptor descriptor;ContextualizerExecutor executor;
  @BeforeAll static void configure() {MemberQualityResolutionTest.configure();}
  @BeforeEach void setup() throws Exception {
    f=new MemberQualityResolutionTest();f.setup();view=f.resolve().join();seen.set(null);
    for(var entry:view.getEntries())when(f.scope.getObservation(entry.getQuality().getId())).thenReturn(entry.getQuality());
    var info=new ServiceInfoImpl();info.setName("test.mapper");
    var input=new ServiceInfoImpl.ArgumentImpl();input.setName("gradients");info.getInputs().add(input);
    descriptor=new Extensions.FunctionDescriptor();descriptor.serviceInfo=info;descriptor.staticMethod=true;
    var implementation=new ComponentRegistry.ServiceImplementation();implementation.method=getClass().getMethod("inspect",MemberQualityCollection.class);
    registry=mock(ComponentRegistry.class);when(registry.implementation(descriptor)).thenReturn(implementation);
    var call=new ServiceCallImpl("test.mapper");when(registry.getFunctionDescriptor(call)).thenReturn(List.of(descriptor));
    executor=new ContextualizerExecutor(registry,new CompiledDataflow.CallDescriptors(null,descriptor,null,null),
        f.region,Map.of(),call,f.scope,new ShardExecution(()->1));
    executor.bindMemberQualityInputs(Map.of("gradients",view));
  }
  @Test void registeredMethodReceivesTheExactNamedCollectionWithoutScalarCoercion() {
    assertTrue(executor.run(Scheduler.Event.initialization(),Map.of(),f.scope,null));
    assertEquals(List.of(11L,12L),seen.get());
  }
  @Test void missingNamedPortAndInaccessibleNativeResultFailBeforeInvocation() {
    executor.bindMemberQualityInputs(Map.of("other",view));
    assertThrows(IllegalStateException.class,()->executor.run(Scheduler.Event.initialization(),Map.of(),f.scope,null));assertNull(seen.get());
    executor.bindMemberQualityInputs(Map.of("gradients",view));when(f.scope.getObservation(111)).thenReturn(null);
    assertThrows(IllegalStateException.class,()->executor.run(Scheduler.Event.initialization(),Map.of(),f.scope,null));assertNull(seen.get());
  }
  @Test void changingInstalledOverloadsFailsClosedBeforeInvocation() {
    var second=new Extensions.FunctionDescriptor();when(registry.getFunctionDescriptor(any(org.integratedmodelling.klab.api.lang.ServiceCall.class)))
        .thenReturn(List.of(descriptor,second));
    assertThrows(IllegalStateException.class,()->executor.run(Scheduler.Event.initialization(),Map.of(),f.scope,null));assertNull(seen.get());
  }
  @Test void missingSourceAliasAndEmptyMapCannotReachDiscoveryOrAllocation() throws Exception {
    var runtime=mock(RuntimeService.class);when(runtime.getComponentRegistry()).thenReturn(registry);
    when(registry.getFunctionDescriptor(any(org.integratedmodelling.klab.api.lang.ServiceCall.class))).thenReturn(List.of(descriptor));
    var twin=mock(org.integratedmodelling.klab.api.digitaltwin.DigitalTwin.class);when(f.scope.getDigitalTwin()).thenReturn(twin);
    var plan=new org.integratedmodelling.common.runtime.ActuatorImpl();plan.setId(f.region.getId());plan.setObservation(f.region);
    plan.setOperationObservable(f.region.getObservable());plan.setActuatorType(org.integratedmodelling.klab.api.services.runtime.Actuator.Type.OBSERVE);
    plan.getComputation().add(new ServiceCallImpl("test.mapper"));
    var compiled=new CompiledDataflow(runtime,f.region,f.scope);
    var failure=assertThrows(IllegalStateException.class,()->compiled.compile(plan));
    assertTrue(failure.getMessage().contains("exact source-backed"));verifyNoInteractions(twin);
    verify(runtime,never()).resolveMemberQualities(any(),any());
    var implementation=new ComponentRegistry.ServiceImplementation();implementation.method=getClass().getMethod("ordinary");
    when(registry.implementation(descriptor)).thenReturn(implementation);
    assertDoesNotThrow(()->compiled.validateProducingModels(plan,f.scope));
  }
}
