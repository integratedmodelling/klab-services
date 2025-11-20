package org.integratedmodelling.klab.services.runtime.neo4j;

import org.integratedmodelling.common.authentication.UserIdentityImpl;
import org.integratedmodelling.klab.api.authentication.ResourcePrivileges;
import org.integratedmodelling.klab.api.collections.Parameters;
import org.integratedmodelling.klab.api.data.KnowledgeGraph;
import org.integratedmodelling.klab.api.data.KnowledgeGraph.Query;
import org.integratedmodelling.klab.api.digitaltwin.DigitalTwin;
import org.integratedmodelling.klab.api.digitaltwin.impl.ConfigurationBuilder;
import org.integratedmodelling.klab.api.provenance.impl.ActivityImpl;
import org.integratedmodelling.klab.api.provenance.impl.AgentImpl;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.scope.Persistence;
import org.integratedmodelling.klab.api.scope.UserScope;
import org.integratedmodelling.klab.api.services.KlabService;
import org.junit.jupiter.api.*;

import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Contract tests for KnowledgeGraphNeo4j-based implementations. Subclasses must provide a concrete
 * KnowledgeGraph instance backed by a real database. The tests only rely on the public
 * KnowledgeGraph API so they can be applied to any current or future implementation.
 */
public abstract class KnowledgeGraphNeo4jContractTest {

  protected static class TestUserScope implements UserScope {
    // --- Channel methods (no-op implementations for tests) ---
    public void info(Object... info) {}

    public void warn(Object... o) {}

    public void error(Object... o) {}

    public void debug(Object... o) {}

    public void event(org.integratedmodelling.klab.api.services.runtime.Message message) {}

    public void ui(org.integratedmodelling.klab.api.services.runtime.Message message) {}

    public String onMessage(
        java.util.function.BiConsumer<
                org.integratedmodelling.klab.api.services.runtime.Channel,
                org.integratedmodelling.klab.api.services.runtime.Message>
            consumer,
        org.integratedmodelling.klab.api.services.runtime.Message.Queue... queues) {
      return "listener";
    }

    public void unregisterMessageListener(String listenerId) {}

    public org.integratedmodelling.klab.api.services.runtime.Message send(Object... message) {
      return null;
    }

    public void close() {}

    public boolean hasErrors() {
      return false;
    }

    private final UserIdentityImpl user;
    private final Parameters<String> data = Parameters.create();
    private String hostServiceId = "test-service";

    public TestUserScope(String username) {
      this.user = new UserIdentityImpl();
      this.user.setUsername(username);
    }

    // ---- Scope/ReactiveScope basics ----
    public String getId() {
      return user.getId();
    }

    public String getDispatchId() {
      return user.getUsername();
    }

    public org.integratedmodelling.klab.api.identities.UserIdentity getUser() {
      return user;
    }

    public Parameters<String> getData() {
      return data;
    }

    public boolean isInterrupted() {
      return false;
    }

    public void interrupt() {
      /* no-op */
    }

    public org.integratedmodelling.klab.api.identities.Identity getIdentity() {
      return user;
    }

    public void setStatus(Status status) {
      /* no-op */
    }

    public Status getStatus() {
      return Status.STARTED;
    }

    public void setData(String key, Object value) {
      data.put(key, value);
    }

    public org.integratedmodelling.klab.api.scope.Scope getParentScope() {
      return null;
    }

    public <T extends org.integratedmodelling.klab.api.scope.Scope> T getParentScope(
        org.integratedmodelling.klab.api.scope.Scope.Type type, Class<T> scopeClass) {
      return null;
    }

    public List<org.integratedmodelling.klab.api.scope.SessionScope> getActiveSessions() {
      return List.of();
    }

    public String getHostServiceId() {
      return hostServiceId;
    }

    public void setHostServiceId(String hostServiceId) {
      this.hostServiceId = hostServiceId;
    }

    public void setId(String id) {
      /* no-op for tests */
    }

    // ---- ReactiveScope agent ----
    public org.integratedmodelling.klab.api.lang.kactors.KActorsBehavior.Ref getAgent() {
      return null;
    }

    // ---- MessagingChannel stubs ----
    public void onMessage(org.integratedmodelling.klab.api.services.runtime.Message message) {}

    public void sendMessage(org.integratedmodelling.klab.api.services.runtime.Message message) {}

    public void sendMessages(
        java.util.Collection<org.integratedmodelling.klab.api.services.runtime.Message> messages) {}

    public void onNotification(
        org.integratedmodelling.klab.api.services.runtime.Notification notification) {}

    public void subscribe(
        java.util.Collection<
                java.util.function.Predicate<
                    org.integratedmodelling.klab.api.services.runtime.Message>>
            messageFilters) {}

    public void subscribeNotifications(
        java.util.Collection<
                java.util.function.Predicate<
                    org.integratedmodelling.klab.api.services.runtime.Notification>>
            messageFilters) {}

    public void unsubscribe() {}

    // ---- Messaging status ----
    public boolean hasMessaging() {
      return false;
    }

    public boolean isConnected() {
      return false;
    }

    public boolean isSender() {
      return false;
    }

    public boolean isReceiver() {
      return false;
    }

    // ---- Service hooks (unused in tests) ----
    public <T extends org.integratedmodelling.klab.api.services.KlabService> T getService(
        Class<T> serviceClass) {
      return null;
    }

    @Override
    public <T extends KlabService> Optional<T> findService(
        Class<T> serviceClass, Predicate<T>... selectors) {
      return Optional.empty();
    }

    public <T extends org.integratedmodelling.klab.api.services.KlabService>
        java.util.Collection<T> getServices(Class<T> serviceClass) {
      return java.util.List.of();
    }

    public String declareSessionScope(
        org.integratedmodelling.klab.api.scope.SessionScope scope,
        org.integratedmodelling.klab.api.scope.UserScope parentScope,
        java.util.function.BiConsumer<
                org.integratedmodelling.klab.api.services.runtime.Message,
                org.integratedmodelling.klab.api.services.runtime.Message>
            responseHandler) {
      return null;
    }

    public void closeScope(org.integratedmodelling.klab.api.scope.Scope scope) {}

    public org.integratedmodelling.klab.api.scope.SessionScope getUserSession(
        org.integratedmodelling.klab.api.services.RuntimeService hostService) {
      return null;
    }

    public org.integratedmodelling.klab.api.scope.SessionScope run(
        String behaviorName, org.integratedmodelling.klab.api.services.RuntimeService hostService) {
      return null;
    }

    // ---- UserScope connectors (unused in tests) ----
    public ContextScope connect(URL digitalTwinURL) {
      return null;
    }

    public ContextScope connect(DigitalTwin.Configuration configuration) {
      return null;
    }

    // ---- ReactiveScope ask (unused) ----
    public <T extends java.io.Serializable> T ask(Class<T> resultClass, Object... messageArgs) {
      return null;
    }
  }

  protected Path tempDir;
  protected KnowledgeGraph graph;
  protected TestUserScope userScope;

  @BeforeEach
  void setupGraph() throws Exception {
    tempDir = Files.createTempDirectory("kg-neo4j-test-");
    userScope = new TestUserScope("tester");
    graph = createGraph(tempDir);
    assertNotNull(graph, "createGraph(Path) must return a non-null KnowledgeGraph");
  }

  @AfterEach
  void tearDown() throws IOException {
    if (graph != null) {
      graph.shutdown();
    }
    if (tempDir != null) {
      // best-effort cleanup
      try (var walk = Files.walk(tempDir)) {
        walk.sorted((a, b) -> b.compareTo(a))
            .forEach(
                p -> {
                  try {
                    Files.deleteIfExists(p);
                  } catch (IOException ignored) {
                  }
                });
      }
    }
  }

  protected abstract KnowledgeGraph createGraph(Path dbPath) throws Exception;

  protected DigitalTwin.Configuration newConfig(String name) {
    String id = "test." + UUID.randomUUID();
    return new ConfigurationBuilder()
        .name(name)
        .id(id)
        .persistence(Persistence.IDLE_TIMEOUT)
        .accessRights(ResourcePrivileges.create(userScope))
        .build();
  }

  @Test
  void testIsOnlineAndShutdown() {
    assertTrue(graph.isOnline(), "Graph should report online after initialization");
    graph.shutdown();
  }

  @Test
  void testContextualizeAndAgentsExist() {
    var config = newConfig("ctx1");
    var ctxGraph = graph.contextualize(config, userScope);
    assertNotNull(ctxGraph);

    // user() and klab() should be available on concrete implementations
    assertNotNull(((KnowledgeGraphNeo4j) graph).user());
    assertNotNull(((KnowledgeGraphNeo4j) graph).klab());
  }

  @Test
  void testStoreRetrieveAndUpdateWithinTransaction() throws Exception {
    var config = newConfig("ctx2");
    graph.contextualize(config, userScope);

    var agent = new AgentImpl();
    agent.setName("Agent A");

    var activity = new ActivityImpl();
    activity.setName("Activity 1");

    try (var tx = graph.createTransaction()) {
      tx.store(agent, "type", "USER");
      tx.store(activity, "type", "RUN");
      tx.update(activity, "description", "desc");
    }

    assertTrue(agent.getId() > 0, "Stored agent should have a persistent id");
    assertTrue(activity.getId() > 0, "Stored activity should have a persistent id");

    var fetchedAgent = graph.getAsset(agent.getId(), userScope, AgentImpl.class);
    assertNotNull(fetchedAgent);
    assertEquals("Agent A", fetchedAgent.getName());
  }

  @Test
  void testLinkAndGetLinks() throws Exception {
    var config = newConfig("ctx3");
    graph.contextualize(config, userScope);

    var agent1 = new AgentImpl();
    agent1.setName("Agent 1");
    var agent2 = new AgentImpl();
    agent2.setName("Agent 2");

    try (var tx = graph.createTransaction()) {
      tx.store(agent1, "type", "USER");
      tx.store(agent2, "type", "USER");
      // Use a valid relationship constant
      tx.link(
          agent1,
          agent2,
          org.integratedmodelling.klab.api.digitaltwin.GraphModel.Relationship.HAS_CHILD);
    }

    var links =
        graph.getLinks(
            agent1,
            org.integratedmodelling.klab.api.digitaltwin.GraphModel.Relationship.Direction.OUTGOING,
            (ContextScope) null,
            org.integratedmodelling.klab.api.digitaltwin.GraphModel.Relationship.HAS_CHILD);
    assertNotNull(links);
    assertFalse(links.isEmpty());
    var link = links.iterator().next();
    assertEquals(agent1.getId(), link.source().getId());
    assertEquals(agent2.getId(), link.target().getId());
  }

  @Test
  void testQueryAPI() throws Exception {
    var config = newConfig("ctx4");
    graph.contextualize(config, userScope);

    var agent = new AgentImpl();
    agent.setName("Queried Agent");

    try (var tx = graph.createTransaction()) {
      tx.store(agent, "type", "USER");
    }

    var results =
        graph
            .query(AgentImpl.class, userScope)
            .where("name", Query.Operator.EQUALS, "Queried Agent")
            .limit(10)
            .run(userScope);

    assertNotNull(results);
    assertFalse(results.isEmpty());
    assertEquals("Queried Agent", results.getFirst().getName());
  }

  @Test
  void testExistingContextsAndDelete() {
    var config = newConfig("ctx5");
    graph.contextualize(config, userScope);

    var contexts = graph.getExistingContexts(userScope);
    assertNotNull(contexts);
    assertTrue(
        contexts.stream().anyMatch(c -> c.getConfiguration().getId().equals(config.getId())));

    graph.deleteContext();

    var contextsAfter = graph.getExistingContexts(userScope);
    assertNotNull(contextsAfter);
    assertFalse(
        contextsAfter.stream().anyMatch(c -> c.getConfiguration().getId().equals(config.getId())));
  }
}
