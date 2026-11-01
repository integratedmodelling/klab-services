package org.integratedmodelling.klab.services.runtime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.List;
import org.integratedmodelling.klab.api.authentication.ResourcePrivileges;
import org.integratedmodelling.klab.api.digitaltwin.DigitalTwin;
import org.integratedmodelling.klab.api.identities.Group;
import org.integratedmodelling.klab.api.identities.UserIdentity;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.scope.UserScope;
import org.integratedmodelling.klab.services.scopes.ScopeManager;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class RuntimeContextAuthorizationTest {
  @ParameterizedTest
  @CsvSource(delimiter = ';', value = {
      "*;bob;READERS;true", "*,!bob;bob;READERS;false",
      "*,!READERS;bob;READERS;false", "READERS;bob;READERS;true",
      "READERS,!bob;bob;READERS;false", "bob,!READERS;bob;READERS;false",
      "alice;bob;READERS;false", "*,!alice;alice;READERS;true"})
  void configurationLookupHonorsActualAcl(String rights, String username,
      String groupName, boolean expected) {
    var runtime = mock(RuntimeService.class, CALLS_REAL_METHODS);
    var manager = mock(ScopeManager.class);
    doReturn(manager).when(runtime).getScopeManager();
    var context = mock(ContextScope.class);
    var configuration = DigitalTwin.Configuration.builder().id("s.c").owner("alice")
        .accessRights(ResourcePrivileges.create(rights)).build();
    when(context.getConfiguration()).thenReturn(configuration);
    when(manager.getScope("s.c", ContextScope.class)).thenReturn(context);
    var user = mock(UserIdentity.class);
    var group = mock(Group.class);
    when(user.getUsername()).thenReturn(username);
    when(group.getName()).thenReturn(groupName);
    when(user.getGroups()).thenReturn(List.of(group));
    var requester = mock(UserScope.class);
    when(requester.getUser()).thenReturn(user);
    if (expected) {
      assertSame(configuration, runtime.getConfiguration("s.c", requester));
    } else {
      assertThrows(org.integratedmodelling.klab.api.exceptions.KlabAuthorizationException.class,
          () -> runtime.getConfiguration("s.c", requester));
    }
  }

  @org.junit.jupiter.api.Test
  void reconnectReloadsAuthorityAndDoesNotTrustCallerPermissions() {
    var runtime = mock(RuntimeService.class, CALLS_REAL_METHODS);
    var manager = mock(ScopeManager.class);
    doReturn(manager).when(runtime).getScopeManager();
    var user = mock(UserIdentity.class);
    when(user.getUsername()).thenReturn("bob");
    var requester = mock(UserScope.class);
    when(requester.getUser()).thenReturn(user);
    var forged = DigitalTwin.Configuration.builder().id("s.c").owner("bob")
        .accessRights(ResourcePrivileges.PUBLIC).build();
    var actual = mock(ContextScope.class);
    when(actual.getConfiguration()).thenReturn(DigitalTwin.Configuration.builder().id("s.c")
        .owner("alice").accessRights(ResourcePrivileges.empty()).build());
    when(manager.getScope("s.c", ContextScope.class)).thenReturn(actual);
    assertThrows(org.integratedmodelling.klab.api.exceptions.KlabAuthorizationException.class,
        () -> runtime.connectContext(forged, requester));
  }

  @org.junit.jupiter.api.Test
  void missingReconnectIsDistinctFromDeniedAccess() {
    var runtime = mock(RuntimeService.class, CALLS_REAL_METHODS);
    var user = mock(UserIdentity.class);
    when(user.getUsername()).thenReturn("bob");
    var requester = mock(UserScope.class);
    when(requester.getUser()).thenReturn(user);
    doReturn(null).when(runtime).getConfiguration("missing.context", requester);
    assertThrows(org.integratedmodelling.klab.api.exceptions.KlabResourceNotFoundException.class,
        () -> runtime.connectContext(DigitalTwin.Configuration.builder().id("missing.context").build(), requester));
    assertThrows(org.integratedmodelling.klab.api.exceptions.KlabIllegalArgumentException.class,
        () -> runtime.connectContext(null, requester));
  }

  @org.junit.jupiter.api.Test
  void coldConfigurationKeepsPersistedSessionMetadataAndRuntimeUrls() throws Exception {
    var runtime = mock(RuntimeService.class, CALLS_REAL_METHODS);
    doReturn(mock(ScopeManager.class)).when(runtime).getScopeManager();
    doReturn("runtime-id").when(runtime).serviceId();
    var url = java.net.URI.create("http://127.0.0.1:8094/runtime").toURL();
    doReturn(url).when(runtime).getUrl();
    var graph = mock(org.integratedmodelling.klab.services.runtime.neo4j.KnowledgeGraphNeo4j.class);
    var field = RuntimeService.class.getDeclaredField("knowledgeGraph");
    field.setAccessible(true);
    field.set(runtime, graph);
    var user = mock(UserIdentity.class);
    when(user.getUsername()).thenReturn("bob");
    var requester = mock(UserScope.class);
    when(requester.getUser()).thenReturn(user);
    var persisted = DigitalTwin.Configuration.builder().id("shared.context").owner("alice")
        .sessionFederationId("test.federation").accessRights(ResourcePrivileges.create("bob")).build();
    when(graph.getAuthorizedConfiguration("shared.context", requester)).thenReturn(persisted);
    var restored = runtime.getConfiguration("shared.context", requester);
    assertSame(persisted, restored);
    assertEquals("test.federation", restored.getSessionFederationId());
    assertEquals("alice", restored.getOwner());
    assertEquals("runtime-id", restored.getServiceId());
    assertEquals(url, restored.getServiceUrl());
    assertEquals(url + "/dt/shared.context", restored.getUrl().toString());
  }
}
