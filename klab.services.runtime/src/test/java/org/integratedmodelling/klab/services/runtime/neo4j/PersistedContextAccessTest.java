package org.integratedmodelling.klab.services.runtime.neo4j;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.net.URI;
import java.util.List;
import java.util.Map;
import org.integratedmodelling.klab.api.digitaltwin.DigitalTwin;
import org.integratedmodelling.klab.api.identities.UserIdentity;
import org.integratedmodelling.klab.api.scope.UserScope;
import org.integratedmodelling.klab.api.scope.Scope;
import org.integratedmodelling.klab.api.services.RuntimeService;
import org.junit.jupiter.api.Test;
import org.neo4j.configuration.connectors.BoltConnector;
import org.neo4j.driver.EagerResult;
import org.neo4j.driver.Values;
import org.neo4j.harness.Neo4jBuilders;

class PersistedContextAccessTest {
  private UserScope user(String username) throws Exception {
    var user = mock(UserIdentity.class);
    when(user.getUsername()).thenReturn(username);
    when(user.getGroups()).thenReturn(List.of());
    var scope = mock(UserScope.class);
    when(scope.getType()).thenReturn(Scope.Type.USER);
    when(scope.getUser()).thenReturn(user);
    var runtime = mock(RuntimeService.class);
    when(runtime.getUrl()).thenReturn(URI.create("http://127.0.0.1:8094/runtime").toURL());
    when(runtime.settings()).thenReturn(mock(org.integratedmodelling.klab.api.configuration.Settings.class));
    when(runtime.settings().get(org.integratedmodelling.klab.api.configuration.Setting.DIGITAL_TWIN_TIMEOUT_MINUTES,
        Integer.class)).thenReturn(30);
    when(scope.getService(RuntimeService.class)).thenReturn(runtime);
    return scope;
  }

  @Test void persistedAclNotFederationControlsColdLookupAndOriginalRightsArePreserved() throws Exception {
    try (var database = Neo4jBuilders.newInProcessBuilder().withDisabledServer()
        .withConfig(BoltConnector.enabled, false).build()) {
      database.defaultDatabaseService().executeTransactionally("""
          CREATE (:Context {id:'owner.private', user:'alice', rights:'alice', federation:'local',
                    name:'Private', description:'test', expiration:'EXPLICIT_ACTION', created:1, lastUpdate:1}),
                 (:Context {id:'owner.shared', user:'alice', rights:'alice,bob', federation:'local',
                    name:'Shared', description:'test', expiration:'EXPLICIT_ACTION', created:1, lastUpdate:1}),
                 (:Context {id:'owner.public', user:'alice', rights:'*', federation:'other',
                    name:'Public', description:'test', expiration:'EXPLICIT_ACTION', created:1, lastUpdate:1})
          """);
      // Execute actual Cypher/ACL restoration without enabling Bolt: this module's
      // test dependency graph uses a Netty version incompatible with the harness
      // Bolt connector. The separate live stack uses its supported graphdb module.
      var graph = mock(KnowledgeGraphNeo4j.class, CALLS_REAL_METHODS);
      doAnswer(invocation -> {
        String cypher = invocation.getArgument(0);
        Map<String, Object> params = invocation.getArgument(1);
        var records = database.defaultDatabaseService().executeTransactionally(cypher, params, rows -> {
          var result = new java.util.ArrayList<org.neo4j.driver.Record>();
          while (rows.hasNext()) {
            var properties = ((org.neo4j.graphdb.Node) rows.next().get("c")).getAllProperties();
            var record = mock(org.neo4j.driver.Record.class);
            when(record.values()).thenReturn(List.of(Values.value(properties)));
            result.add(record);
          }
          return result;
        });
        var result = mock(EagerResult.class);
        when(result.records()).thenReturn(records);
        return result;
      }).when(graph).query(anyString(), anyMap(), any(Scope.class));
      assertNull(graph.getAuthorizedConfiguration("owner.private", user("bob")));
      var original = graph.getAuthorizedConfiguration("owner.private", user("alice"));
      assertEquals("alice", original.getOwner());
      assertEquals("alice", original.getAccessRights().toString());
      var shared = graph.getAuthorizedConfiguration("owner.shared", user("bob"));
      assertEquals("alice", shared.getOwner());
      assertTrue(shared.getAccessRights().getAllowedUsers().containsAll(List.of("alice", "bob")));
      assertEquals("*", graph.getAuthorizedConfiguration("owner.public", user("bob")).getAccessRights().toString());
      assertEquals(2, graph.getContextInfo(user("bob")).size());
      assertNull(graph.getAuthorizedConfiguration("does.notexist", user("bob")));
    }
  }

  @Test void missingAclIsOwnerOnlyMalformedAclIsRejectedAndMissingOwnerNeverGrantsAccess() throws Exception {
    var alice = user("alice");
    var bob = user("bob");
    assertTrue(KnowledgeGraphNeo4j.authorizesPersistedContext(Map.of("user", "alice"), alice));
    assertFalse(KnowledgeGraphNeo4j.authorizesPersistedContext(Map.of("user", "alice", "federation", "local"), bob));
    assertFalse(KnowledgeGraphNeo4j.authorizesPersistedContext(Map.of("rights", "*"), bob));
    assertThrows(org.integratedmodelling.klab.api.exceptions.KlabStorageException.class,
        () -> KnowledgeGraphNeo4j.authorizesPersistedContext(Map.of("user", "alice", "rights", 123), bob));
  }
}
