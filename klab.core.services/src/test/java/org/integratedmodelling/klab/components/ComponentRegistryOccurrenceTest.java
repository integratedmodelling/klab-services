package org.integratedmodelling.klab.components;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.lang.reflect.Method;
import org.integratedmodelling.common.lang.ServiceInfoImpl;
import org.integratedmodelling.klab.api.knowledge.observation.scale.time.Time;
import org.integratedmodelling.klab.api.services.runtime.extension.Contextualizer;
import org.integratedmodelling.klab.api.services.runtime.extension.KlabFunction;
import org.junit.jupiter.api.Test;

class ComponentRegistryOccurrenceTest {
  @Contextualizer(timeStep = 1, timeUnit = Time.Resolution.Type.MONTH, timeOverridable = false,
      timeMinStep = 1, timeMinStepUnit = Time.Resolution.Type.DAY,
      timeMaxStep = 1, timeMaxStepUnit = Time.Resolution.Type.MONTH)
  @KlabFunction(name = "process", description = "test")
  public static class Monthly {
    @KlabFunction(name = "inherited", description = "test")
    public static void inherited() {}

    @Contextualizer(timeStep = 2, timeUnit = Time.Resolution.Type.DAY)
    @KlabFunction(name = "method", description = "test")
    public static void overridden() {}
  }

  @KlabFunction(name = "static", description = "test")
  public static class Static {}

  private ServiceInfoImpl prototype(Class<?> owner, Method method) throws Exception {
    var registry = mock(ComponentRegistry.class, CALLS_REAL_METHODS);
    var factory = ComponentRegistry.class.getDeclaredMethod("createContextualizerPrototype",
        String.class, KlabFunction.class, Method.class, Class.class);
    factory.setAccessible(true);
    var function = method == null ? owner.getAnnotation(KlabFunction.class) : method.getAnnotation(KlabFunction.class);
    return (ServiceInfoImpl) factory.invoke(registry, "test.", function, method, owner);
  }

  @Test
  void classAndMethodDeclarationsReachPortablePrototypes() throws Exception {
    var classSchedule = prototype(Monthly.class, null).getOccurrenceSchedule();
    assertEquals(Time.Resolution.Type.MONTH, classSchedule.unit());
    assertFalse(classSchedule.overridable());
    assertEquals(Time.Resolution.Type.DAY, classSchedule.minStep().unit());
    assertEquals(Time.Resolution.Type.MONTH, classSchedule.maxStep().unit());
    assertEquals(1, classSchedule.minStep().step());
    assertEquals(classSchedule, prototype(Monthly.class, Monthly.class.getMethod("inherited")).getOccurrenceSchedule());
    var methodSchedule = prototype(Monthly.class, Monthly.class.getMethod("overridden")).getOccurrenceSchedule();
    assertEquals(Time.Resolution.Type.DAY, methodSchedule.unit());
    assertEquals(2, methodSchedule.step());
    assertTrue(methodSchedule.overridable());
    assertNull(methodSchedule.minStep());
    assertNull(prototype(Static.class, null).getOccurrenceSchedule());
  }
}
