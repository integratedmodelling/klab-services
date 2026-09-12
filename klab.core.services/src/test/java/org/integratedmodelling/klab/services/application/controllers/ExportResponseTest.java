package org.integratedmodelling.klab.services.application.controllers;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import jakarta.servlet.http.HttpServletResponse;
import java.util.Map;
import org.integratedmodelling.klab.api.exceptions.KlabResourceAccessException;
import org.integratedmodelling.klab.api.knowledge.KlabAsset;
import org.integratedmodelling.klab.services.application.ServiceNetworkedInstance;
import org.integratedmodelling.klab.services.application.security.EngineAuthorization;
import org.integratedmodelling.klab.services.base.BaseService;
import org.junit.jupiter.api.Test;

class ExportResponseTest {
  public static java.io.InputStream failingExporter() { throw new IllegalArgumentException("Invalid ramp"); }

  @Test void exporterFailureRetainsItsOriginalCause() throws Exception {
    var registry = mock(org.integratedmodelling.klab.components.ComponentRegistry.class);
    var descriptor = new org.integratedmodelling.klab.api.services.runtime.extension.Extensions.FunctionDescriptor();
    var info = new org.integratedmodelling.common.lang.ServiceInfoImpl();
    info.setFunctionType(org.integratedmodelling.klab.api.lang.ServiceInfo.FunctionType.FREEFORM);
    descriptor.serviceInfo = info;
    descriptor.staticMethod = true;
    var implementation = new org.integratedmodelling.klab.components.ComponentRegistry.ServiceImplementation();
    implementation.method = ExportResponseTest.class.getMethod("failingExporter");
    implementation.method.setAccessible(true);
    when(registry.implementation(descriptor)).thenReturn(implementation);
    var call = org.integratedmodelling.common.lang.ServiceCallImpl.create("test.broken");
    when(registry.getFunctionDescriptor(call)).thenReturn(java.util.List.of(descriptor));
    var language = new org.integratedmodelling.klab.runtime.language.LanguageService();
    var field = language.getClass().getDeclaredField("componentRegistry");
    field.setAccessible(true);
    field.set(language, registry);
    var failure = assertThrows(KlabResourceAccessException.class, () -> language.execute(call,
        mock(org.integratedmodelling.klab.api.scope.Scope.class), java.io.InputStream.class));
    assertInstanceOf(IllegalArgumentException.class, failure.getCause());
    assertEquals("Invalid ramp", failure.getCause().getMessage());
  }
  @Test void nullExporterResultFailsBeforeCommittingPngHeaders() {
    var service = mock(BaseService.class);
    var instance = mock(ServiceNetworkedInstance.class);
    when(instance.klabService()).thenReturn(service);
    var controller = new KlabServiceController();
    controller.instance = instance;
    var response = mock(HttpServletResponse.class);
    var error = assertThrows(KlabResourceAccessException.class, () -> controller.exportAsset(
        "test.slope", KlabAsset.KnowledgeClass.OBSERVATION, "image/png", Map.of(),
        response, mock(EngineAuthorization.class)));
    assertTrue(error.getMessage().contains("Exporter returned no data"));
    verifyNoInteractions(response);
  }
}
