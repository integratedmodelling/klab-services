package org.integratedmodelling.klab.services.runtime.digitaltwin;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import org.integratedmodelling.common.lang.QuantityImpl;
import org.integratedmodelling.klab.api.lang.AnnotationImpl;
import org.integratedmodelling.klab.api.lang.Quantity;
import org.integratedmodelling.klab.api.knowledge.observation.impl.ObservationImpl;
import org.integratedmodelling.klab.services.runtime.neo4j.AbstractKnowledgeGraph;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Actual guardConsequenceBaseline serialization with an authored typed time annotation. */
class RoleTimeAnnotationBaselineTest {
  @BeforeAll static void configure(){ConsequenceRoleTransactionTest.configure();}
  @Test void authoredTimeQuantitySurvivesActualRoleNativeBaselineComparison() throws Exception {
    var fixture=new ConsequenceRoleTransactionTest();fixture.setup();
    var step=new QuantityImpl();step.setValue(1);step.setUnit("month");
    var source=(ObservationImpl)fixture.source;
    source.mergeAnnotations(List.of(AnnotationImpl.create("time","step",step)),ObservationImpl.DEFINITION_ANNOTATIONS);
    // The authored concrete annotation is already valid native JSON. The guard's detached
    // projection must preserve that shape rather than introduce a serializable-API proxy.
    var nativeBefore=AbstractKnowledgeGraph.persistedObservationProperties(source);
    assertNotNull(nativeBefore);
    assertEquals(List.of(41L),assertDoesNotThrow(fixture::stage));
    assertEquals("time",source.getAnnotations().getFirst().getName());
    var retained=(Quantity)source.getAnnotations().getFirst().get("step");
    assertEquals(1,retained.getValue().intValue());assertEquals("month",retained.getUnit());
    assertEquals(ObservationImpl.DEFINITION_ANNOTATIONS,source.getAnnotationPriorities().get("time"));
    assertSame(fixture.before,fixture.recipient.getObservable());
    assertEquals(nativeBefore,AbstractKnowledgeGraph.persistedObservationProperties(source));
  }
  @Test void changedTimeQuantityCannotPassTheOriginalNativeGuard() throws Exception {
    for(var field:List.of("value","unit","currency")) {
    var f=new ConsequenceRoleTransactionTest();f.setup();
    var step=new QuantityImpl();step.setValue(1);step.setUnit("month");
    ((ObservationImpl)f.source).mergeAnnotations(List.of(AnnotationImpl.create("time","step",step)),ObservationImpl.DEFINITION_ANNOTATIONS);
    org.mockito.Mockito.doAnswer(call->{
      switch(field) {case "value" -> step.setValue(2);case "unit" -> step.setUnit("day");case "currency" -> step.setCurrency("USD");}
      return f.after;
    }).when(f.f.reasoner)
        .buildObservable(org.mockito.ArgumentMatchers.any(org.integratedmodelling.klab.api.knowledge.ObservableBuildStrategy.class),org.mockito.ArgumentMatchers.eq(f.f.scope));
    assertThrows(IllegalStateException.class,f::stage);
    assertSame(f.before,f.recipient.getObservable());
    assertFalse(f.f.activity.getMetadata().containsKey("klab.consequence.roleAttributions"));
    }
  }
  public static class UnknownQuantity extends QuantityImpl {}
  @Test void arbitraryQuantitySubclassRemainsRejectedAtTheBoundary() throws Exception {
    var f=new ConsequenceRoleTransactionTest();f.setup();
    var step=new UnknownQuantity();step.setValue(1);step.setUnit("month");
    ((ObservationImpl)f.source).mergeAnnotations(List.of(AnnotationImpl.create("time","step",step)),ObservationImpl.DEFINITION_ANNOTATIONS);
    assertThrows(IllegalArgumentException.class,f::stage);
    assertSame(f.before,f.recipient.getObservable());
    assertFalse(f.f.activity.getMetadata().containsKey("klab.consequence.roleAttributions"));
  }
}
