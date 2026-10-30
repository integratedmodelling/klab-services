package org.integratedmodelling.klab.services.runtime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.List;
import org.integratedmodelling.common.lang.ServiceCallImpl;
import org.integratedmodelling.klab.api.services.runtime.extension.*;
import org.integratedmodelling.klab.api.services.runtime.objects.MemberQualityCollection;
import org.integratedmodelling.klab.components.ComponentRegistry;
import org.junit.jupiter.api.Test;

class MemberQualityConsumerSupportTest {
  public static void mapper(@KlabFunction.Input(name="gradients") MemberQualityCollection values) {}
  public static void ordinary(@KlabFunction.Input(name="gradients") org.integratedmodelling.klab.api.knowledge.observation.Observation values) {}
  public static void unnamed(MemberQualityCollection gradients) {}
  @Test void actualMethodTypeAndExplicitNameAreBothMandatory() throws Exception {
    assertTrue(MemberQualityConsumerSupport.supports(getClass().getMethod("mapper",MemberQualityCollection.class),"gradients"));
    assertFalse(MemberQualityConsumerSupport.supports(getClass().getMethod("mapper",MemberQualityCollection.class),"other"));
    assertFalse(MemberQualityConsumerSupport.supports(getClass().getMethod("ordinary",org.integratedmodelling.klab.api.knowledge.observation.Observation.class),"gradients"));
    assertFalse(MemberQualityConsumerSupport.supports(getClass().getMethod("unnamed",MemberQualityCollection.class),"gradients"));
  }
  @Test void installedDescriptorMustBeUnambiguousAndHaveAnActualImplementation() throws Exception {
    var registry=mock(ComponentRegistry.class);var call=new ServiceCallImpl("test.mapper");
    var descriptor=new Extensions.FunctionDescriptor();descriptor.staticMethod=true;var other=new Extensions.FunctionDescriptor();
    when(registry.getFunctionDescriptor(call)).thenReturn(List.of(descriptor));
    assertFalse(MemberQualityConsumerSupport.supports(registry,call,"gradients"));
    var impl=new ComponentRegistry.ServiceImplementation();impl.method=getClass().getMethod("mapper",MemberQualityCollection.class);
    when(registry.implementation(descriptor)).thenReturn(impl);
    assertTrue(MemberQualityConsumerSupport.supports(registry,call,"gradients"));
    when(registry.getFunctionDescriptor(call)).thenReturn(List.of(descriptor,other));
    assertFalse(MemberQualityConsumerSupport.supports(registry,call,"gradients"));
    verify(registry,never()).getFunctionDescriptor(eq(call),any());
  }
  @Test void ordinaryOverloadsRemainSelectableButTypedOrUnknownOverloadsFailClosed() throws Exception {
    var registry=mock(ComponentRegistry.class);var call=new ServiceCallImpl("test.overloads");
    var first=new Extensions.FunctionDescriptor();first.staticMethod=true;
    var second=new Extensions.FunctionDescriptor();second.staticMethod=true;
    var a=new ComponentRegistry.ServiceImplementation();a.method=getClass().getMethod("ordinary",org.integratedmodelling.klab.api.knowledge.observation.Observation.class);
    var b=new ComponentRegistry.ServiceImplementation();b.method=a.method;
    when(registry.getFunctionDescriptor(call)).thenReturn(List.of(first,second));
    when(registry.implementation(first)).thenReturn(a);when(registry.implementation(second)).thenReturn(b);
    assertTrue(MemberQualityConsumerSupport.inspect(registry,call).available());
    assertTrue(MemberQualityConsumerSupport.inspect(registry,call).ports().isEmpty());
    b.method=getClass().getMethod("mapper",MemberQualityCollection.class);
    assertFalse(MemberQualityConsumerSupport.inspect(registry,call).available(),"Mixed overload set cannot promise the typed implementation");
    a.method=b.method;assertFalse(MemberQualityConsumerSupport.inspect(registry,call).available());
    b.method=null;assertFalse(MemberQualityConsumerSupport.inspect(registry,call).available());
    verify(registry,never()).getFunctionDescriptor(eq(call),any());
  }
  @Test void inspectionDistinguishesUnavailableFromAnActualOrdinaryMethod() throws Exception {
    var registry=mock(ComponentRegistry.class);var call=new ServiceCallImpl("test.mapper");
    assertFalse(MemberQualityConsumerSupport.inspect(registry,call).available());
    var descriptor=new Extensions.FunctionDescriptor();descriptor.staticMethod=true;
    when(registry.getFunctionDescriptor(call)).thenReturn(List.of(descriptor));
    var impl=new ComponentRegistry.ServiceImplementation();impl.method=getClass().getMethod("mapper",MemberQualityCollection.class);
    when(registry.implementation(descriptor)).thenReturn(impl);
    var typed=MemberQualityConsumerSupport.inspect(registry,call);assertTrue(typed.available());assertEquals(java.util.Set.of("gradients"),typed.ports());
    var runtime=mock(RuntimeService.class,CALLS_REAL_METHODS);doReturn(registry).when(runtime).getComponentRegistry();
    assertEquals(typed,runtime.inspectMemberQualityInputs(call,mock(org.integratedmodelling.klab.api.scope.UserScope.class)),"Installed metadata does not require a container");
    impl.method=getClass().getMethod("ordinary",org.integratedmodelling.klab.api.knowledge.observation.Observation.class);
    var ordinary=MemberQualityConsumerSupport.inspect(registry,call);assertTrue(ordinary.available());assertTrue(ordinary.ports().isEmpty());
    when(registry.getFunctionDescriptor(call)).thenReturn(List.of(descriptor,new Extensions.FunctionDescriptor()));
    assertFalse(MemberQualityConsumerSupport.inspect(registry,call).available());
    when(registry.getFunctionDescriptor(call)).thenThrow(new IllegalStateException("test unavailable"));
    assertFalse(MemberQualityConsumerSupport.inspect(registry,call).available());
  }
}
