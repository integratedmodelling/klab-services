package org.integratedmodelling.common.services.client;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.integratedmodelling.common.data.jackson.JacksonConfiguration;
import org.integratedmodelling.common.knowledge.ConceptImpl;
import org.integratedmodelling.common.knowledge.ObservableImpl;
import org.integratedmodelling.common.utils.Utils;
import org.integratedmodelling.klab.api.ServicesAPI;
import org.integratedmodelling.klab.api.knowledge.SemanticType;
import org.integratedmodelling.klab.api.lang.ValueOperator;
import org.integratedmodelling.klab.api.services.reasoner.objects.*;
import org.junit.jupiter.api.Test;

class SemanticSearchTransportTest {
  @Test void semanticSearchPostsJsonAndPreservesProposalAndObservableData() throws Exception {
    var mapper = JacksonConfiguration.newObjectMapper();
    var concept = new ConceptImpl(); concept.setUrn("test:Height"); concept.setName("Height");
    concept.getType().addAll(List.of(SemanticType.OBSERVABLE, SemanticType.QUALITY));
    var observable = new ObservableImpl(); observable.setSemantics(concept); observable.setUrn(concept.getUrn());
    var response = new SemanticSearchResponse(42, 8); response.setObservable(observable);
    response.setCurrentConcept(concept);
    var clause = new SemanticClauseRestriction(org.integratedmodelling.klab.api.knowledge.SemanticRole.INHERENT, concept, true);
    clause.setCode(List.of(StyledKimToken.create(concept))); response.setClauses(List.of(clause));
    response.setDeclaration("test:Height"); response.setCanUndo(true);
    response.getMatches().add(new SemanticMatch(ValueOperator.GREATER));
    var received = new AtomicReference<String>(); var path = new AtomicReference<String>();
    var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext("/", exchange -> {
      path.set(exchange.getRequestMethod() + " " + exchange.getRequestURI().getPath());
      received.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
      byte[] body = mapper.writeValueAsBytes(response);
      exchange.getResponseHeaders().set("Content-Type", "application/json");
      exchange.sendResponseHeaders(200, body.length);
      try (var output = exchange.getResponseBody()) { output.write(body); }
    });
    server.start();
    try (var http = Utils.Http.getClient("http://127.0.0.1:" + server.getAddress().getPort(), null)) {
      var client = mock(ReasonerClient.class, CALLS_REAL_METHODS);
      var field = BaseServiceClient.class.getDeclaredField("client"); field.setAccessible(true); field.set(client, http);
      var request = new SemanticSearchRequest(); request.setSearchId(42); request.setRequestId(8);
      request.setSearchMode(SemanticSearchRequest.Mode.SELECT); request.setSelectedMatchId("test:Height");
      request.setMatchesRequestId(7);
      var result = client.semanticSearch(request);
      assertEquals("POST " + ServicesAPI.REASONER.SEMANTIC_SEARCH, path.get());
      var posted = mapper.readValue(received.get(), SemanticSearchRequest.class);
      assertEquals("test:Height", posted.getSelectedMatchId()); assertEquals(7, posted.getMatchesRequestId());
      assertEquals(42, result.getSearchId()); assertTrue(result.isCanUndo());
      assertEquals(ValueOperator.GREATER, result.getMatches().getFirst().getValueOperator());
      assertEquals("test:Height", result.getObservable().getUrn());
      assertEquals("test:Height", result.getCurrentConcept().getUrn());
      assertTrue(result.getClauses().getFirst().isInherited());
      assertEquals("test:Height", result.getClauses().getFirst().getFiller().getUrn());
      assertEquals("test:Height", result.getClauses().getFirst().getCode().getFirst().getValue());
    } finally { server.stop(0); }
  }
}
