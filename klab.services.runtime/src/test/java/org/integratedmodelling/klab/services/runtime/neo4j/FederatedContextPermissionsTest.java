package org.integratedmodelling.klab.services.runtime.neo4j;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.integratedmodelling.klab.api.digitaltwin.GraphModel;
import org.integratedmodelling.klab.api.identities.UserIdentity;
import org.integratedmodelling.klab.api.scope.UserScope;
import org.junit.jupiter.api.Test;

class FederatedContextPermissionsTest {
  private UserScope requester(String username) {
    var scope = mock(UserScope.class);
    when(scope.getType()).thenReturn(org.integratedmodelling.klab.api.scope.Scope.Type.USER);
    var user = mock(UserIdentity.class);
    when(scope.getUser()).thenReturn(user);
    when(user.getUsername()).thenReturn(username);
    when(user.getGroups()).thenReturn(Set.of());
    return scope;
  }

  @Test void persistedAclUsesTheCallerAndHonorsPublicExclusions() {
    var row = Map.<String, Object>of(GraphModel.Fields.USER, "alice", GraphModel.Fields.RIGHTS, "*,!bob");
    assertTrue(KnowledgeGraphNeo4j.authorizesPersistedContext(row, requester("alice")));
    assertFalse(KnowledgeGraphNeo4j.authorizesPersistedContext(row, requester("bob")));
    assertTrue(KnowledgeGraphNeo4j.authorizesPersistedContext(row, requester("carol")));
    var shared = Map.<String, Object>of(GraphModel.Fields.USER, "alice", GraphModel.Fields.RIGHTS, "bob");
    assertTrue(KnowledgeGraphNeo4j.authorizesPersistedContext(shared, requester("bob")));
    assertFalse(KnowledgeGraphNeo4j.authorizesPersistedContext(shared, requester("carol")));
  }

  @Test void missingLegacyAclIsOwnerOnlyAndMalformedAuthorityFailsClosed() {
    var legacy = Map.<String, Object>of(GraphModel.Fields.USER, "alice");
    assertTrue(KnowledgeGraphNeo4j.authorizesPersistedContext(legacy, requester("alice")));
    assertFalse(KnowledgeGraphNeo4j.authorizesPersistedContext(legacy, requester("bob")));
    assertFalse(KnowledgeGraphNeo4j.authorizesPersistedContext(Map.of(), requester("bob")));
    var malformed = new HashMap<>(legacy);
    malformed.put(GraphModel.Fields.RIGHTS, 42);
    assertThrows(org.integratedmodelling.klab.api.exceptions.KlabStorageException.class,
        () -> KnowledgeGraphNeo4j.authorizesPersistedContext(malformed, requester("bob")));
  }

  @Test void coldRecoveryReturnsPersistedOwnerAclAndSessionPolicy() throws Exception {
    var bob = requester("bob");
    var graph = mock(KnowledgeGraphNeo4j.class, CALLS_REAL_METHODS);
    var result = mock(org.neo4j.driver.EagerResult.class);
    doReturn(result).when(graph).query(anyString(), anyMap(), eq(bob));
    var row = Map.<String, Object>of(GraphModel.Fields.USER, "alice", GraphModel.Fields.RIGHTS, "bob",
        GraphModel.Fields.NAME, "persisted twin", GraphModel.Fields.EXPIRATION, "EXPLICIT_ACTION",
        "sessionFederationId", "test.federation");
    doReturn(java.util.List.of(row)).when(graph).adapt(eq(result), eq(Map.class), eq(bob));
    var configuration = graph.getAuthorizedConfiguration("test_federation.context", bob);
    assertEquals("alice", configuration.getOwner());
    assertEquals("test.federation", configuration.getSessionFederationId());
    assertTrue(configuration.getAccessRights().checkAuthorization("bob", Set.of()));
    assertFalse(configuration.getAccessRights().checkAuthorization("carol", Set.of()));
    doReturn(java.util.List.of(Map.of(GraphModel.Fields.USER, "alice"))).when(graph).adapt(eq(result), eq(Map.class), eq(bob));
    assertThrows(org.integratedmodelling.klab.api.exceptions.KlabAuthorizationException.class,
        () -> graph.getAuthorizedConfiguration("test_federation.context", bob));
  }
}
