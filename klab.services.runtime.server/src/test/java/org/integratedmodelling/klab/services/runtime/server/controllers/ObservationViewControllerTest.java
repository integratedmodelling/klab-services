package org.integratedmodelling.klab.services.runtime.server.controllers;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.concurrent.CompletableFuture;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;
import jakarta.servlet.http.HttpServletResponse;
import org.integratedmodelling.klab.api.collections.Parameters;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.knowledge.observation.impl.ObservationImpl;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.services.resolver.ResolutionConstraint;
import org.integratedmodelling.klab.api.services.resolver.objects.ResolutionRequest;
import org.integratedmodelling.klab.api.services.runtime.objects.ObservationExportRequest;
import org.integratedmodelling.klab.services.application.security.EngineAuthorization;
import org.integratedmodelling.klab.services.runtime.RuntimeService;
import org.integratedmodelling.klab.services.runtime.server.RuntimeServer;
import org.integratedmodelling.klab.services.scopes.ServiceContextScope;
import org.junit.jupiter.api.Test;

class ObservationViewControllerTest {
  @Test
  void querySubmissionDoesNotCreateAProvenanceAgentAndExportKeepsTheView() throws Exception {
    var runtime = mock(RuntimeService.class);
    var server = mock(RuntimeServer.class);
    when(server.klabService()).thenReturn(runtime);
    var controller = new RuntimeServerController();
    var field = RuntimeServerController.class.getDeclaredField("runtimeService");
    field.setAccessible(true);
    field.set(controller, server);
    var scope = mock(ServiceContextScope.class, RETURNS_DEEP_STUBS);
    when(scope.withResolutionConstraints(any(ResolutionConstraint[].class))).thenReturn(scope);
    var authorization = mock(EngineAuthorization.class);
    when(authorization.getScope(ContextScope.class)).thenReturn(scope);
    var view = new ObservationImpl(); view.setId(Observation.QUERY_ID);
    var request = new ResolutionRequest(); request.setObservation(view);
    when(runtime.submit(view, scope)).thenReturn(CompletableFuture.completedFuture(view));
    controller.submit(request, authorization);
    verify(runtime).submit(view, scope);
    verify(scope, never()).getDigitalTwin();

    var export = new ObservationExportRequest();
    export.setObservation(view); export.setParameters(Parameters.create("viewportX", 123));
    var bytes = new ByteArrayOutputStream();
    var output = new ServletOutputStream() {
      @Override public void write(int value) { bytes.write(value); }
      @Override public boolean isReady() { return true; }
      @Override public void setWriteListener(WriteListener listener) {}
    };
    var response = mock(HttpServletResponse.class);
    when(response.getOutputStream()).thenReturn(output);
    when(runtime.exportObservation(view, "image/png", export.getParameters(), scope))
        .thenReturn(new ByteArrayInputStream(new byte[] {1, 2, 3}));
    controller.exportObservation(export, "image/png", response, authorization);
    assertArrayEquals(new byte[] {1, 2, 3}, bytes.toByteArray());
    verify(response).setContentType("image/png");
    verify(runtime, never()).exportAsset(any(), any(), any(), any(), any());
  }
}
