package org.integratedmodelling.klab.services.runtime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

import java.util.List;
import org.integratedmodelling.common.runtime.ActuatorImpl;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.knowledge.observation.impl.ObservationImpl;
import org.integratedmodelling.klab.api.lang.Annotation;
import org.integratedmodelling.klab.api.services.runtime.Actuator;
import org.integratedmodelling.klab.services.scopes.ServiceContextScope;
import org.junit.jupiter.api.Test;

class ObservationAnnotationsRuntimeTest {
  @Test
  void compilationMergesRemoteAnnotationsIntoTheSubmittedRoot() throws Exception {
    var submitted = new ObservationImpl();
    submitted.mergeAnnotations(List.of(Annotation.of("style", "value", "define")), 2);
    var remote = (ObservationImpl) Observation.forTransport(submitted);
    remote.mergeAnnotations(List.of(Annotation.of("style", "value", "model"),
        Annotation.of("label", "value", "model")), 1);
    var actuator = new ActuatorImpl();
    actuator.setActuatorType(Actuator.Type.OBSERVE);
    actuator.setObservation(remote);
    var compiled = new CompiledDataflow(mock(RuntimeService.class), submitted,
        mock(ServiceContextScope.class));
    var allocate = CompiledDataflow.class.getDeclaredMethod("requireObservations", Actuator.class);
    allocate.setAccessible(true);
    allocate.invoke(compiled, actuator);
    assertEquals(2, submitted.getAnnotations().size());
    assertEquals("define", submitted.getAnnotations().stream()
        .filter(a -> a.getName().equals("style")).findFirst().orElseThrow().get("value"));
    assertNotSame(submitted.getAnnotations(), remote.getAnnotations());
  }
}
