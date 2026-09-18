package org.integratedmodelling.common.services.client;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.util.concurrent.atomic.AtomicReference;
import org.integratedmodelling.common.data.jackson.JacksonConfiguration;
import org.integratedmodelling.common.utils.Utils;
import org.integratedmodelling.klab.api.ServicesAPI;
import org.integratedmodelling.klab.api.lang.kim.impl.*;
import org.integratedmodelling.klab.api.scope.Scope;
import org.integratedmodelling.klab.api.services.reasoner.objects.*;
import org.integratedmodelling.klab.api.services.runtime.Notification;
import org.junit.jupiter.api.Test;

class SemanticValidationTransportTest {
  @Test void transportsOccurrencesAndRevisionBoundDiagnostics() throws Exception {
    var mapper = JacksonConfiguration.newObjectMapper();
    var namespace = new KimNamespaceImpl(); namespace.setUrn("test"); namespace.setProjectName("project");
    namespace.setSourceCode("model each earth:StreamConnection;");
    var source = new KimConceptImpl(); source.setName("earth:StreamConnection");
    source.setOffsetInDocument(11); source.setLength(22);
    var observable = new KimObservableImpl(); observable.setSemantics(source);
    var model = new KimModelImpl(); model.getObservables().add(observable);
    namespace.getStatements().add(model);
    var request = SemanticValidationRequest.of(namespace, "edit-3");
    request.setKnowledgeRevision(7);
    var response = SemanticValidationResponse.forRequest(request);
    response.setStatus(SemanticValidationResponse.Status.COMPLETE); response.setKnowledgeRevision(7);
    response.getNotifications().add(Notification.error("Invalid endpoint", Notification.LexicalContext.of(source, namespace)));
    var received = new AtomicReference<SemanticValidationRequest>();
    var path = new AtomicReference<String>();
    var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext("/", exchange -> {
      path.set(exchange.getRequestURI().getPath());
      received.set(mapper.readValue(exchange.getRequestBody(), SemanticValidationRequest.class));
      byte[] body = mapper.writeValueAsBytes(response);
      exchange.getResponseHeaders().set("Content-Type", "application/json");
      exchange.sendResponseHeaders(200, body.length);
      try (var out = exchange.getResponseBody()) { out.write(body); }
    });
    server.start();
    try (var http = Utils.Http.getClient("http://127.0.0.1:" + server.getAddress().getPort(), null)) {
      var client = mock(ReasonerClient.class, CALLS_REAL_METHODS);
      var field = BaseServiceClient.class.getDeclaredField("client"); field.setAccessible(true); field.set(client, http);
      var result = client.validateDocument(request, mock(Scope.class));
      assertEquals(ServicesAPI.REASONER.VALIDATE_DOCUMENT, path.get());
      assertEquals(7, received.get().getKnowledgeRevision());
      assertEquals(namespace.getSourceCode(), received.get().document().getSourceCode());
      assertFalse(result.valid()); assertTrue(result.matches(request, 7));
      var location = result.getNotifications().getFirst().getLexicalContext();
      assertEquals("test", location.getDocumentUrn()); assertEquals("project", location.getProjectUrn());
      assertEquals(11, location.getOffsetInDocument()); assertEquals(22, location.getLength());
      assertFalse(result.matches(request, 8));
      namespace.setSourceCode(namespace.getSourceCode() + "\n");
      assertFalse(result.matches(request, 7));
    } finally { server.stop(0); }
  }

  @Test void absentReasonerIsUnavailableAndNotAValidDocument() {
    var document = new KimNamespaceImpl(); document.setUrn("test"); document.setSourceCode("");
    var response = DocumentSemanticValidation.validate(SemanticValidationRequest.of(document, "1"), mock(Scope.class));
    assertEquals(SemanticValidationResponse.Status.UNAVAILABLE, response.getStatus());
    assertFalse(response.valid()); assertTrue(response.getNotifications().isEmpty());
  }
}
