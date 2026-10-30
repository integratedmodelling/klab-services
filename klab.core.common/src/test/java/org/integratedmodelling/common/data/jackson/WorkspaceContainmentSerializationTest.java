package org.integratedmodelling.common.data.jackson;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.util.List;
import org.integratedmodelling.common.utils.Utils;
import org.integratedmodelling.klab.api.knowledge.organization.Workspace;
import org.integratedmodelling.klab.api.knowledge.Worldview;
import org.integratedmodelling.klab.api.knowledge.impl.WorldviewImpl;
import org.integratedmodelling.klab.api.knowledge.organization.impl.ProjectImpl;
import org.integratedmodelling.klab.api.knowledge.organization.impl.WorkspaceImpl;
import org.integratedmodelling.klab.api.lang.kim.KimConcept;
import org.integratedmodelling.klab.api.lang.kim.KimConceptStatement;
import org.integratedmodelling.klab.api.lang.kim.impl.KimConceptImpl;
import org.integratedmodelling.klab.api.lang.kim.impl.KimConceptStatementImpl;
import org.integratedmodelling.klab.api.lang.kim.impl.KimOntologyImpl;
import org.junit.jupiter.api.Test;

class WorkspaceContainmentSerializationTest {
  @Test void errorLoggingRetainsSafeCauseAndSuppressedFrames() {
    var logging = org.integratedmodelling.common.logging.Logging.INSTANCE;
    var previous = logging.getErrorWriter();
    var output = new java.util.ArrayList<String>();
    try {
      logging.setErrorWriter(output::add);
      var cause = new IllegalArgumentException("SECRET_TOKEN");
      var failure = new IllegalStateException("SECRET_PAYLOAD", cause);
      failure.addSuppressed(new java.io.IOException("SECRET_AUTH"));
      logging.error("Workspace request failed", failure);
      assertEquals(1, output.size());
      var text = output.getFirst();
      assertTrue(text.contains("IllegalArgumentException"));
      assertTrue(text.contains("Caused by:"));
      assertTrue(text.contains("Suppressed:"));
      assertTrue(text.contains("errorLoggingRetainsSafeCauseAndSuppressedFrames"));
      assertFalse(text.contains("SECRET_"));
      assertSame(cause, failure.getCause());
    } finally { logging.setErrorWriter(previous); }
  }

  @Test void actualHttpFailuresLogOnceWithoutResponseBodyOrCredentials() throws Exception {
    var logging = org.integratedmodelling.common.logging.Logging.INSTANCE;
    var previous = logging.getErrorWriter();
    var output = new java.util.ArrayList<String>();
    var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    var valid = Utils.Json.asString(workspace(true));
    var unknown = valid.replace("\"observationalContainments\":", "\"SECRET_UNKNOWN_FIELD\":");
    server.createContext("/unknown", exchange -> {
      var bytes = unknown.getBytes(java.nio.charset.StandardCharsets.UTF_8);
      exchange.sendResponseHeaders(200, bytes.length); exchange.getResponseBody().write(bytes); exchange.close();
    });
    server.createContext("/malformed", exchange -> {
      var bytes = "{SECRET_MALFORMED".getBytes(java.nio.charset.StandardCharsets.UTF_8);
      exchange.sendResponseHeaders(200, bytes.length); exchange.getResponseBody().write(bytes); exchange.close();
    });
    server.createContext("/failure", exchange -> {
      var bytes = "SECRET_SERVER_BODY".getBytes(java.nio.charset.StandardCharsets.UTF_8);
      exchange.sendResponseHeaders(500, bytes.length); exchange.getResponseBody().write(bytes); exchange.close();
    });
    server.start();
    String base = "http://127.0.0.1:" + server.getAddress().getPort();
    try {
      logging.setErrorWriter(output::add);
      try (var client = Utils.Http.getClient(base, null)) {
        for (var route : List.of("/unknown", "/malformed", "/failure")) {
          output.clear();
          assertNull(client.get(route, Workspace.class));
          assertEquals(1, output.size(), route);
          assertTrue(output.getFirst().contains("HTTP GET failed"));
          assertFalse(output.getFirst().contains("SECRET_"));
          if (route.equals("/failure")) assertTrue(output.getFirst().contains("status 500"));
        }
        output.clear();
        assertNull(client.post("/malformed", "{}", Workspace.class));
        assertEquals(1, output.size());
        assertFalse(output.getFirst().contains("SECRET_"));
        output.clear();
        assertNull(client.put("/malformed", "{}", Workspace.class));
        assertEquals(1, output.size());
        assertFalse(output.getFirst().contains("SECRET_"));
        output.clear();
        var upload = java.nio.file.Files.createTempFile("transport-test-", ".txt");
        try {
          java.nio.file.Files.writeString(upload, "fixture");
          assertNull(client.upload("/malformed", upload.toFile(), Workspace.class));
          assertEquals(1, output.size());
          assertFalse(output.getFirst().contains("SECRET_"));
        } finally { java.nio.file.Files.deleteIfExists(upload); }
        output.clear();
        assertNull(client.postQuietly("/malformed", "{}", Workspace.class));
        assertTrue(output.isEmpty());
        output.clear();
        assertThrows(org.integratedmodelling.klab.api.exceptions.KlabServiceAccessException.class,
            () -> client.postRequired("/failure", "{}", Workspace.class));
        assertEquals(1, output.size());
        assertFalse(output.getFirst().contains("SECRET_"));
        output.clear();
        assertTrue(client.getCollection("/failure", Worldview.class).isEmpty());
        assertEquals(1, output.size());
        assertTrue(output.getFirst().contains("status 500"));
        assertFalse(output.getFirst().contains("SECRET_"));
      }
      server.stop(0);
      output.clear();
      try (var client = Utils.Http.getClient(base, null)) {
        assertNull(client.get("/closed", Workspace.class));
        assertEquals(1, output.size());
        assertFalse(output.getFirst().contains(base));
      }
    } finally { server.stop(0); logging.setErrorWriter(previous); }
  }

  private KimConcept concept(String urn) {
    var ret = new KimConceptImpl(); ret.setName(urn); ret.setUrn(urn); return ret;
  }

  private KimConceptStatementImpl declaration(boolean populated) {
    var ret = new KimConceptStatementImpl(); ret.setUrn("Region"); ret.setNamespace("earth");
    if (populated) {
      ret.getObservationalContainments().add(new KimConceptStatement.ObservationalContainment(
          concept("earth:Region"), concept("earth:Stream")));
      ret.getObservationalContainments().add(new KimConceptStatement.ObservationalContainment(
          concept("earth:Region"), concept("earth:RiverBasin")));
      ret.getDeclarationClauses().add(new KimConceptStatement.DeclarationClause(
          "containsClause", "contains earth:Stream, earth:RiverBasin", 37, 42));
      ret.getDeclaredReferences().add(concept("earth:Location"));
      ret.getInterpretedPhysicalDomains().add(concept("imod:EarthDomain"));
      ret.getRequiredIdentities().add(concept("imod:Areal or imod:Volumetric"));
      ret.getEmergenceTriggers().add(concept("earth:StreamConnection"));
      ret.getChildren().add(declaration(false));
    }
    return ret;
  }

  private Workspace workspace(boolean populated) {
    var ontology = new KimOntologyImpl(); ontology.setUrn("earth");
    ontology.getStatements().add(declaration(populated));
    var project = new ProjectImpl(); project.setUrn("imod"); project.getOntologies().add(ontology);
    var ret = new WorkspaceImpl(); ret.setUrn("im-main"); ret.getProjects().add(project); return ret;
  }

  private void preserved(Workspace restored, boolean populated) {
    assertEquals("im-main", restored.getUrn());
    var project = restored.getProjects().iterator().next(); assertEquals("imod", project.getUrn());
    KimConceptStatement statement = project.getOntologies().iterator().next().getStatements().getFirst();
    assertInstanceOf(KimConceptStatementImpl.class, statement);
    assertEquals(populated ? 2 : 0, statement.getObservationalContainments().size());
    if (populated) {
      var first = statement.getObservationalContainments().getFirst();
      assertInstanceOf(KimConceptImpl.class, first.container());
      assertInstanceOf(KimConceptImpl.class, first.contained());
      assertEquals("earth:Region", first.container().getUrn());
      assertEquals("earth:Stream", first.contained().getUrn());
      assertEquals("earth:RiverBasin", statement.getObservationalContainments().get(1).contained().getUrn());
      assertEquals(37, statement.getDeclarationClauses().getFirst().offset());
      assertEquals("earth:Location", statement.getDeclaredReferences().getFirst().getUrn());
      assertEquals("imod:EarthDomain", statement.getInterpretedPhysicalDomains().getFirst().getUrn());
      assertEquals("imod:Areal or imod:Volumetric", statement.getRequiredIdentities().getFirst().getUrn());
      assertEquals("earth:StreamConnection", statement.getEmergenceTriggers().getFirst().getUrn());
      assertTrue(statement.getChildren().getFirst().getObservationalContainments().isEmpty());
    }
  }

  @Test void emptyDeclarationRetainsConcreteClassThroughInterface() throws Exception {
    var mapper = JacksonConfiguration.newObjectMapper();
    String wire = mapper.writerFor(KimConceptStatement.class).writeValueAsString(declaration(false));
    assertTrue(wire.contains("\"@CLASS\":\"" + KimConceptStatementImpl.class.getName() + "\""));
    assertTrue(wire.contains("\"observationalContainments\":[]"));
    var restored = Utils.Json.parseObject(wire, KimConceptStatement.class);
    assertInstanceOf(KimConceptStatementImpl.class, restored);
    assertTrue(restored.getObservationalContainments().isEmpty());
  }

  @Test void productionMapperRoundTripsFullWorkspaceAndNestedSemanticRecords() throws Exception {
    ObjectMapper serverMapper = JacksonConfiguration.newObjectMapper();
    for (boolean populated : List.of(false, true)) {
      String wire = serverMapper.writerFor(Workspace.class).writeValueAsString(workspace(populated));
      assertTrue(wire.contains("\"@CLASS\":\"" + WorkspaceImpl.class.getName() + "\""));
      preserved(Utils.Json.parseObject(wire, Workspace.class), populated);
      preserved(serverMapper.readValue(wire, Workspace.class), populated);
    }
  }

  @Test void actualHttpGetClientRestoresWorkspaceWithoutDroppingContainment() throws Exception {
    var wire = JacksonConfiguration.newObjectMapper().writerFor(Workspace.class)
        .writeValueAsBytes(workspace(true));
    var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext("/api/v1/retrieve/WORKSPACE/im-main", exchange -> {
      exchange.getResponseHeaders().set("Content-Type", "application/json");
      exchange.sendResponseHeaders(200, wire.length);
      try (var body = exchange.getResponseBody()) { body.write(wire); }
    });
    server.start();
    try (var client = Utils.Http.getClient("http://127.0.0.1:" + server.getAddress().getPort(), null)) {
      preserved(client.get("/api/v1/retrieve/WORKSPACE/im-main", Workspace.class), true);
    } finally { server.stop(0); }
  }

  @Test void resourcesWorkspaceProjectionClonePreservesSemanticFields() throws Exception {
    // ResourcesProvider.projectWorkspaceForUser clones through this exact utility pair
    // before filtering projects and returning the workspace to the HTTP serializer.
    Workspace projected = Utils.Json.parseObject(Utils.Json.asString(workspace(true)), Workspace.class);
    preserved(projected, true);
    var serverMapper = JacksonConfiguration.newObjectMapper();
    preserved(Utils.Json.parseObject(serverMapper.writerFor(Workspace.class)
        .writeValueAsString(projected), Workspace.class), true);
  }

  @Test void reasonerWorldviewCollectionClientRetainsTheSameStatementContract() throws Exception {
    var source = workspace(true);
    var world = new WorldviewImpl(); world.setUrn("imod"); world.setEmpty(false);
    world.getOntologies().addAll(source.getProjects().iterator().next().getOntologies());
    var wire = JacksonConfiguration.newObjectMapper().writeValueAsBytes(List.of(world));
    var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext("/api/v1/list/WORLDVIEW", exchange -> {
      exchange.getResponseHeaders().set("Content-Type", "application/json");
      exchange.sendResponseHeaders(200, wire.length);
      try (var body = exchange.getResponseBody()) { body.write(wire); }
    });
    server.start();
    try (var client = Utils.Http.getClient("http://127.0.0.1:" + server.getAddress().getPort(), null)) {
      var restored = client.getCollection("/api/v1/list/WORLDVIEW", Worldview.class);
      assertEquals(1, restored.size());
      var declaration = restored.getFirst().getOntologies().getFirst().getStatements().getFirst();
      assertInstanceOf(KimConceptStatementImpl.class, declaration);
      assertEquals(2, declaration.getObservationalContainments().size());
      assertEquals("earth:Stream", declaration.getObservationalContainments().getFirst().contained().getUrn());
      assertEquals(37, declaration.getDeclarationClauses().getFirst().offset());
    } finally { server.stop(0); }
  }

  @Test void unknownTransportFieldsRemainRejected() throws Exception {
    var mapper = JacksonConfiguration.newObjectMapper();
    var wire = mapper.valueToTree(declaration(false));
    ((com.fasterxml.jackson.databind.node.ObjectNode) wire).put("unrecognizedSemanticContract", true);
    var failure = assertThrows(Exception.class, () -> mapper.treeToValue(wire, KimConceptStatement.class));
    assertTrue(failure.toString().contains("Unexpected field name unrecognizedSemanticContract"));
  }
}
