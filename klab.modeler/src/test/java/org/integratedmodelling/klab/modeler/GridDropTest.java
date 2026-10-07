package org.integratedmodelling.klab.modeler;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.*;
import org.integratedmodelling.klab.api.digitaltwin.GridAlignment;
import org.integratedmodelling.klab.api.lang.kim.impl.KimSymbolDefinitionImpl;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.services.RuntimeService;
import org.integratedmodelling.klab.api.services.runtime.Notification;
import org.junit.jupiter.api.Test;

class GridDropTest {
  @Test
  void droppingGridConfiguresTheTwinAndReturnsInformationalSuccess() {
    var modeler = mock(ModelerImpl.class, CALLS_REAL_METHODS);
    var scope = mock(ContextScope.class);
    var runtime = mock(RuntimeService.class);
    doReturn(runtime).when(scope).getService(RuntimeService.class);
    var alignment =
        new GridAlignment(
            1,
            "test.grid",
            "fingerprint",
            "EPSG:32634",
            0,
            0,
            50,
            50,
            true,
            true,
            List.of(1.0),
            List.of("Adjusted anchor"));
    when(runtime.configureGrid("test.grid", scope)).thenReturn(alignment);
    var definition = new KimSymbolDefinitionImpl();
    definition.setDefineClass("grid");
    definition.setUrn("test.grid");
    var result = modeler.observe(scope, definition, false).join();
    assertTrue(result.isEmpty());
    assertEquals(Notification.Level.Info, result.getNotifications().getFirst().getLevel());
    assertEquals(Notification.Level.Warning, result.getNotifications().getLast().getLevel());
    verify(runtime).configureGrid("test.grid", scope);
    verify(runtime, never()).submit(any(), any());
  }
}
