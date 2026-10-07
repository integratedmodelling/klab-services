package org.integratedmodelling.klab.services.reasoner;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.integratedmodelling.common.data.jackson.JacksonConfiguration;
import org.integratedmodelling.common.knowledge.*;
import org.integratedmodelling.common.services.client.*;
import org.integratedmodelling.common.utils.Utils;
import org.integratedmodelling.klab.api.ServicesAPI;
import org.integratedmodelling.klab.api.knowledge.SemanticType;
import org.integratedmodelling.klab.api.services.*;
import org.integratedmodelling.klab.api.services.reasoner.objects.*;
import org.integratedmodelling.klab.api.services.resources.objects.AuthorityIdentity;
import org.integratedmodelling.klab.indexing.*;
import org.integratedmodelling.klab.services.reasoner.internal.AuthorityBindings;
import org.junit.jupiter.api.Test;

class AuthorityHttpRoundTripTest {
  @Test void searchInsertAndUndoUseTheHttpClientAndRealSemanticSession() throws Exception {
    String code = "A:B] with space";
    String token = AuthorityIdentitySyntax.encode("TAXA", code);
    var concept = new ConceptImpl(); concept.setUrn("internal:Taxon");
    concept.setType(EnumSet.of(SemanticType.IDENTITY, SemanticType.PREDICATE, SemanticType.AUTHORITY_IDENTITY));
    var observable = new ObservableImpl(); observable.setSemantics(concept); observable.setUrn(token);
    var reasoner = mock(Reasoner.class); when(reasoner.resolveObservable(token)).thenReturn(observable);
    when(reasoner.satisfiable(concept)).thenReturn(true);
    var session = new SemanticSearchSession(reasoner, (text, scope, limit) -> List.of(),
        new SemanticSearchRequest(), new SemanticClauseSupport(reasoner),
        (authority, selected) -> new SemanticSearchSession.AuthoritySelection(concept,
            AuthorityIdentitySyntax.encode(authority, selected)));
    var provider = mock(Authority.class); var capabilities = mock(Authority.Capabilities.class);
    when(provider.getCapabilities()).thenReturn(capabilities); when(capabilities.isSearchable()).thenReturn(true);
    when(provider.configure(any())).thenReturn("private-configuration");
    var bindings = new AuthorityBindings(); bindings.configure(new Authority.ConfigurationRequest(
        "test", "TAXA", "test:Species", Map.of("urn", "provider.taxa")), provider);
    var candidate = new AuthorityIdentity(); candidate.setId(code); candidate.setLabel("Taxon");
    when(provider.search("taxon", null, "private-configuration")).thenReturn(List.of(candidate));
    var mapper = JacksonConfiguration.newObjectMapper(); var status = new AtomicInteger(200);
    var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(ServicesAPI.REASONER.AUTHORITY_SEARCH, exchange -> {
      var request = mapper.readValue(exchange.getRequestBody(), AuthoritySearchRequest.class);
      byte[] body = mapper.writeValueAsBytes(bindings.search(request));
      exchange.getResponseHeaders().set("Content-Type", "application/json");
      exchange.sendResponseHeaders(status.get(), body.length);
      try (var output = exchange.getResponseBody()) { output.write(body); }
    });
    server.createContext(ServicesAPI.REASONER.SEMANTIC_SEARCH, exchange -> {
      var request = mapper.readValue(exchange.getRequestBody(), SemanticSearchRequest.class);
      byte[] body = mapper.writeValueAsBytes(session.handle(request, 42));
      exchange.getResponseHeaders().set("Content-Type", "application/json");
      exchange.sendResponseHeaders(200, body.length);
      try (var output = exchange.getResponseBody()) { output.write(body); }
    });
    server.start();
    try (var http = Utils.Http.getClient("http://127.0.0.1:" + server.getAddress().getPort(), null)) {
      var client = mock(ReasonerClient.class, CALLS_REAL_METHODS);
      var field = BaseServiceClient.class.getDeclaredField("client"); field.setAccessible(true); field.set(client, http);
      var results = client.searchAuthority(new AuthoritySearchRequest("TAXA", "taxon", null, 0, 10), null);
      assertEquals(code, results.matches().getFirst().getId()); assertEquals(token, results.matches().getFirst().getLocator());
      assertFalse(mapper.writeValueAsString(results).contains("private-configuration"));
      var request = new SemanticSearchRequest(); request.setRequestId(1);
      var initial = client.semanticSearch(request, null); assertEquals(42, initial.getSearchId());
      request.setSearchId(42); request.setRequestId(2); request.setMatchesRequestId(1);
      request.setSearchMode(SemanticSearchRequest.Mode.IDENTITY); request.setAuthority("TAXA"); request.setIdentityCode(code);
      var inserted = client.semanticSearch(request, null);
      assertTrue(inserted.getErrors().isEmpty(), inserted.getErrors().toString());
      assertEquals(token, inserted.getDeclaration()); assertTrue(inserted.isCanUndo());
      request.setSearchMode(SemanticSearchRequest.Mode.UNDO); request.setRequestId(3);
      var undone = client.semanticSearch(request, null); assertEquals("", undone.getDeclaration()); assertFalse(undone.isCanUndo());
      status.set(502);
      assertThrows(org.integratedmodelling.klab.api.exceptions.KlabServiceAccessException.class,
          () -> client.searchAuthority(new AuthoritySearchRequest("TAXA", "taxon", null, 0, 10), null));
    } finally { server.stop(0); }
  }
}
