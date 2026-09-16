package org.integratedmodelling.klab.runtime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.integratedmodelling.klab.api.identities.UserIdentity;
import org.integratedmodelling.klab.api.knowledge.DefaultObserver;
import org.integratedmodelling.klab.api.knowledge.Worldview;
import org.integratedmodelling.klab.rest.GroupImpl;
import org.integratedmodelling.common.utils.Utils;
import org.junit.jupiter.api.Test;

class DefaultObserverTest {
  @Test
  void projectsReplaceConflictingValuesAndPreserveOtherMetadata() {
    Map<String, Object> result = new LinkedHashMap<>();
    var warnings = new ArrayList<String>();
    DefaultObserver.mergeMetadata(result,
        Map.of(Worldview.USER_OBSERVER_SEMANTICS, "people:User", "title", "First"), "a", warnings::add);
    DefaultObserver.mergeMetadata(result,
        Map.of(Worldview.USER_OBSERVER_SEMANTICS, " people:Contributor "), "b", warnings::add);
    assertEquals("people:Contributor", result.get(Worldview.USER_OBSERVER_SEMANTICS));
    assertEquals("First", result.get("title"));
    assertEquals(1, warnings.size());
  }

  @Test
  void groupPrecedenceIsIndependentOfCollectionOrder() {
    var first = group("a", "people:User");
    var last = group("z", "people:Contributor");
    var warnings = new ArrayList<String>();
    var metadata = Map.of(Worldview.USER_OBSERVER_SEMANTICS, "people:Visitor");
    assertEquals("people:Contributor", DefaultObserver.select(metadata, List.of(last, first), warnings::add));
    assertEquals(1, warnings.size());
    assertEquals("people:Contributor", DefaultObserver.select(metadata, List.of(first, last), ignored -> {}));
  }

  @Test
  void missingBlankAndIdenticalDeclarationsDoNotWarn() {
    var warnings = new ArrayList<String>();
    assertNull(DefaultObserver.select(Map.of(), List.of(group("a", " ")), warnings::add));
    assertEquals("people:User", DefaultObserver.select(
        Map.of(Worldview.USER_OBSERVER_SEMANTICS, "people:User"),
        List.of(group("a", "people:User"), group("b", "people:User")), warnings::add));
    assertTrue(warnings.isEmpty());
    assertNull(DefaultObserver.select(Map.of(Worldview.USER_OBSERVER_SEMANTICS, 42), List.of(), warnings::add));
    assertEquals(1, warnings.size());
  }

  @Test
  void identitySurvivesSessionsButSeparatesAuthoritiesAndUsers() {
    var user = mock(UserIdentity.class);
    when(user.getUsername()).thenReturn("alice");
    when(user.getServerURL()).thenReturn("https://hub.one");
    var identity = DefaultObserver.identity(user);
    assertEquals(identity, DefaultObserver.identity(user));
    assertEquals(2, identity.split(":").length);
    when(user.getServerURL()).thenReturn("https://hub.two");
    assertNotEquals(identity, DefaultObserver.identity(user));
    when(user.getServerURL()).thenReturn("https://hub.one");
    when(user.getUsername()).thenReturn("bob");
    assertNotEquals(identity, DefaultObserver.identity(user));
    when(user.isAnonymous()).thenReturn(true);
    assertThrows(IllegalArgumentException.class, () -> DefaultObserver.identity(user));
  }

  @Test
  void groupObserverSurvivesJsonTransport() {
    var original = group("a", "people:Contributor");
    var copy = Utils.Json.parseObject(Utils.Json.asString(original), GroupImpl.class);
    assertEquals(original, copy);
    assertEquals("people:Contributor", copy.getObserverSemantics());
  }

  private GroupImpl group(String id, String semantics) {
    var group = new GroupImpl(id);
    group.setName(id);
    group.setObserverSemantics(semantics);
    return group;
  }
}
