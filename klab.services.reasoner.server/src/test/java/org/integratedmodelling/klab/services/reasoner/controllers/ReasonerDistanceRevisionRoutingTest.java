package org.integratedmodelling.klab.services.reasoner.controllers;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.integratedmodelling.klab.api.knowledge.Concept;
import org.integratedmodelling.klab.services.reasoner.ReasonerServer;
import org.integratedmodelling.klab.services.reasoner.ReasonerService;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PostMapping;
import org.integratedmodelling.klab.api.ServicesAPI;

class ReasonerDistanceRevisionRoutingTest {
  @Test void suppliedGenerationUsesVersionedMatchingWhileLegacyAndAssertedRoutesStaySeparate() throws Exception {
    var service = mock(ReasonerService.class); var server = mock(ReasonerServer.class);
    when(server.klabService()).thenReturn(service);
    var controller = new ReasonerController();
    var field = ReasonerController.class.getDeclaredField("reasoner"); field.setAccessible(true); field.set(controller, server);
    var a = mock(Concept.class); var b = mock(Concept.class); var context = mock(Concept.class);
    when(service.semanticDistance(a, b, context, 42)).thenReturn(4);
    when(service.semanticDistance(a, b, null)).thenReturn(7);
    when(service.assertedDistance(a, b)).thenReturn(2);
    when(service.assertedDistanceAtRevision(a, b, 42)).thenReturn(3);
    assertEquals(4, controller.assertedDistance(new Concept[] {a, b, context}, false, 42));
    assertEquals(7, controller.assertedDistance(new Concept[] {a, b}, false, -1));
    assertEquals(3, controller.assertedDistance(new Concept[] {a, b}, true, 42));
    assertEquals(2, controller.assertedDistance(new Concept[] {a, b}, true, -1));
    verify(service).semanticDistance(a, b, context, 42);
    verify(service).semanticDistance(a, b, null);
    verify(service).assertedDistance(a, b);
    verify(service).assertedDistanceAtRevision(a, b, 42);
    var parameter = ReasonerController.class.getMethod("assertedDistance", Concept[].class, boolean.class, long.class)
        .getParameters()[2].getAnnotation(RequestParam.class);
    assertEquals("knowledgeRevision", parameter.name()); assertEquals("-1", parameter.defaultValue());
  }

  @Test void strictValidatedRouteRequiresRevisionAndHasDistinctServerCapability() throws Exception {
    var service = mock(ReasonerService.class); var server = mock(ReasonerServer.class);
    when(server.klabService()).thenReturn(service);
    var controller = new ReasonerController();
    var field = ReasonerController.class.getDeclaredField("reasoner"); field.setAccessible(true); field.set(controller, server);
    var a = mock(Concept.class); var b = mock(Concept.class);
    when(service.semanticDistance(a, b, null, 42)).thenReturn(5);
    when(service.assertedDistanceAtRevision(a, b, 42)).thenReturn(2);
    assertEquals(5, controller.validatedDistance(new Concept[] {a, b}, false, 42));
    assertEquals(2, controller.validatedDistance(new Concept[] {a, b}, true, 42));
    assertThrows(org.springframework.web.server.ResponseStatusException.class,
        () -> controller.validatedDistance(new Concept[] {a, b}, false, -1));
    var method = ReasonerController.class.getMethod("validatedDistance", Concept[].class, boolean.class, long.class);
    assertArrayEquals(new String[] {ServicesAPI.REASONER.VALIDATED_DISTANCE}, method.getAnnotation(PostMapping.class).value());
    assertTrue(method.getParameters()[2].getAnnotation(RequestParam.class).required());
    verify(service, never()).semanticDistance(a, b, null);
  }
}
