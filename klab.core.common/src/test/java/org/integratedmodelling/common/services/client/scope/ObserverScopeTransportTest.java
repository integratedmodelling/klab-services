package org.integratedmodelling.common.services.client.scope;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.EnumSet;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import org.integratedmodelling.common.knowledge.ConceptImpl;
import org.integratedmodelling.common.knowledge.ObservableImpl;
import org.integratedmodelling.common.services.client.BaseServiceClient;
import org.integratedmodelling.common.services.client.RuntimeClient;
import org.integratedmodelling.common.services.client.digitaltwin.ClientDigitalTwin;
import org.integratedmodelling.common.services.client.digitaltwin.ClientKnowledgeGraph;
import org.integratedmodelling.common.services.client.digitaltwin.TransitionHistory;
import org.integratedmodelling.common.utils.Utils;
import org.integratedmodelling.klab.api.ServicesAPI;
import org.integratedmodelling.klab.api.knowledge.SemanticType;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.knowledge.observation.impl.ObservationImpl;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.junit.jupiter.api.Test;

class ObserverScopeTransportTest {
  private static ObservationImpl observation(long id, SemanticType type) {
    var concept = new ConceptImpl();
    concept.setUrn("test:" + type); concept.setType(EnumSet.of(type));
    var observable = new ObservableImpl(); observable.setSemantics(concept);
    var observation = new ObservationImpl(); observation.setId(id); observation.setObservable(observable);
    return observation;
  }

  /** Only the user, service shell and transition journal are mocked; the client scope is real. */
  private static final class Fixture implements AutoCloseable {
    final ClientContextScope scope;
    final RuntimeClient runtime = mock(RuntimeClient.class);
    final ClientDigitalTwin twin = mock(ClientDigitalTwin.class);
    final TransitionHistory history = mock(TransitionHistory.class);
    final HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    final Utils.Http.Client http;
    final AtomicInteger requests = new AtomicInteger();
    final CopyOnWriteArrayList<String> scopeHeaders = new CopyOnWriteArrayList<>();
    final CopyOnWriteArrayList<String> focusHeaders = new CopyOnWriteArrayList<>();
    final Observation original = observation(8173, SemanticType.AGENT);

    Fixture(int status, Observation answer) throws Exception {
      http = Utils.Http.getClient("http://127.0.0.1:" + server.getAddress().getPort(), null);
      try {
      server.createContext(ServicesAPI.RUNTIME.RETRIEVE_KNOWLEDGE_GRAPH_ASSET.replace("{id}", "8173"), exchange -> {
        requests.incrementAndGet();
        scopeHeaders.add(String.valueOf(exchange.getRequestHeaders().getFirst(ServicesAPI.SCOPE_HEADER)));
        focusHeaders.add(String.valueOf(exchange.getRequestHeaders().getFirst(ServicesAPI.CONTEXT_OBSERVATION_ID_HEADER)));
        var body = (answer == null ? "{}" : Utils.Json.asString(answer)).getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, body.length);
        try (var out = exchange.getResponseBody()) { out.write(body); }
      });
      var clientField = BaseServiceClient.class.getDeclaredField("client");
      clientField.setAccessible(true); clientField.set(runtime, http);
      var user = mock(org.integratedmodelling.klab.api.identities.UserIdentity.class);
      when(user.getId()).thenReturn("test-user");
      when(user.getData()).thenReturn(org.integratedmodelling.klab.api.collections.Parameters.create());
      when(user.getUsername()).thenReturn("test-user");
      when(user.isAnonymous()).thenReturn(true);
      when(runtime.serviceId()).thenReturn("test-runtime");
      when(runtime.getUrl()).thenReturn(java.net.URI.create("http://127.0.0.1:" + server.getAddress().getPort()).toURL());
      var parent = new ClientSessionScope(new ClientUserScope(user, null), "session", runtime).withId("session");
      var configuration = (org.integratedmodelling.klab.api.digitaltwin.impl.ConfigurationImpl)
          org.integratedmodelling.klab.api.digitaltwin.DigitalTwin.Configuration.builder()
              .id("session.context").name("Test context").build();
      configuration.setObserver(original);
      scope = new ClientContextScope(parent, runtime, configuration);
      scope.setId("session.context");
      var twinField = ClientContextScope.class.getDeclaredField("digitalTwin");
      twinField.setAccessible(true); twinField.set(scope, twin);
      var focusField = ClientContextScope.class.getDeclaredField("contextObservation");
      focusField.setAccessible(true); focusField.set(scope, observation(8075, SemanticType.SUBJECT));
      when(twin.getTransitionHistory()).thenReturn(history);
      when(history.modifies(8173)).thenReturn(true);
      when(twin.getKnowledgeGraph()).thenReturn(new ClientKnowledgeGraph(scope, runtime));
      doCallRealMethod().when(runtime).getAsset(8173, Observation.class, scope);
      server.start();
      } catch (Exception | Error failure) {
        try { close(); } catch (Exception cleanup) { failure.addSuppressed(cleanup); }
        throw failure;
      }
    }

    @Override public void close() throws Exception { try { http.close(); } finally { server.stop(0); } }
  }

  @Test void refreshAndRepeatedReadsPreserveObserverAndFocusWithoutRecursiveLoading() throws Exception {
    try (var fixture = new Fixture(200, observation(8173, SemanticType.AGENT))) {
      var refreshed = fixture.scope.getObserver();
      assertEquals(8173, refreshed.getId());
      assertNotSame(fixture.original, refreshed);
      assertSame(refreshed, fixture.scope.getObserver());
      assertEquals(1, fixture.requests.get());
      assertEquals(java.util.List.of("session.context.8075#8173"), fixture.scopeHeaders);
      assertEquals(java.util.List.of("8075"), fixture.focusHeaders);
      verify(fixture.runtime, times(1)).getAsset(8173, Observation.class, fixture.scope);
    }
  }

  @Test void missingObserverStillFailsAccessValidation() throws Exception {
    try (var fixture = new Fixture(404, null)) {
      var error = assertThrows(IllegalStateException.class, fixture.scope::getObserver);
      assertEquals("Selected observer is no longer an accessible Agent", error.getMessage());
      assertEquals(1, fixture.requests.get());
      assertEquals(java.util.List.of("session.context.8075#8173"), fixture.scopeHeaders);
    }
  }

  @Test void refreshedNonAgentStillFailsValidation() throws Exception {
    try (var fixture = new Fixture(200, observation(8173, SemanticType.SUBJECT))) {
      assertThrows(IllegalStateException.class, fixture.scope::getObserver);
      assertEquals(1, fixture.requests.get());
    }
  }

  @Test void headerEncodingDoesNotRefreshButNormalObserverReadsStillDo() throws Exception {
    try (var fixture = new Fixture(404, null)) {
      assertEquals("session.context.8075#8173", ContextScope.getScopeId(fixture.scope));
      assertEquals(0, fixture.requests.get());
      assertThrows(IllegalStateException.class, fixture.scope::getObserver);
      assertEquals(1, fixture.requests.get());
    }
  }
}
