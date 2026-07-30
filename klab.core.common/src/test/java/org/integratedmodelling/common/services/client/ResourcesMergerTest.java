package org.integratedmodelling.common.services.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.integratedmodelling.klab.api.data.Version;
import org.integratedmodelling.klab.api.knowledge.KlabAsset;
import org.integratedmodelling.klab.api.knowledge.KlabAsset.KnowledgeClass;
import org.integratedmodelling.klab.api.scope.Scope;
import org.integratedmodelling.klab.api.scope.UserScope;
import org.integratedmodelling.klab.api.services.ResourcesService;
import org.integratedmodelling.klab.api.services.resources.ResourceSet;
import org.integratedmodelling.klab.api.services.runtime.Notification;
import org.integratedmodelling.klab.api.utils.Utils;
import org.junit.jupiter.api.Test;

class ResourcesMergerTest {

  @Test
  void queriesAllServicesConcurrentlyAndSurvivesIndividualFailures() {
    var scope = mock(Scope.class);
    var localService = mock(ResourcesService.class);
    var remoteService = mock(ResourcesService.class);
    var failingService = mock(ResourcesService.class);
    var bothQueriesStarted = new CountDownLatch(2);

    when(localService.resolve(eq("urn"), any(Scope.class)))
        .thenAnswer(
            invocation -> {
              bothQueriesStarted.countDown();
              assertTrue(bothQueriesStarted.await(2, TimeUnit.SECONDS));
              return result("urn", "local", true, "1.0.0", 1);
            });
    when(remoteService.resolve(eq("urn"), any(Scope.class)))
        .thenAnswer(
            invocation -> {
              bothQueriesStarted.countDown();
              assertTrue(bothQueriesStarted.await(2, TimeUnit.SECONDS));
              return result("urn", "remote", false, "2.0.0", 2);
            });
    when(failingService.resolve(eq("urn"), any(Scope.class)))
        .thenThrow(new IllegalStateException("temporarily unavailable"));
    doReturn(List.of(localService, remoteService, failingService))
        .when(scope)
        .getServices(ResourcesService.class);

    var merged = new ResourcesMerger(scope).resolve("urn", scope);

    assertEquals(1, merged.getResults().size());
    assertEquals("local", merged.getResults().iterator().next().getServiceId());
    assertEquals(1, merged.getNotifications().size());
    assertTrue(
        merged.getNotifications().getFirst().getMessage().contains("temporarily unavailable"));
    assertFalse(merged.isEmpty());
  }

  @Test
  void nonQueryOperationsUseTheFirstPrioritizedService() {
    var scope = mock(Scope.class);
    var primary = mock(ResourcesService.class);
    var secondary = mock(ResourcesService.class);
    var asset = mock(KlabAsset.class);
    var userScope = mock(UserScope.class);
    when(primary.retrieve("urn", KlabAsset.class, userScope)).thenReturn(asset);
    doReturn(List.of(primary, secondary)).when(scope).getServices(ResourcesService.class);

    var retrieved = new ResourcesMerger(scope).retrieve("urn", KlabAsset.class, userScope);

    assertSame(asset, retrieved);
    verify(primary).retrieve("urn", KlabAsset.class, userScope);
  }

  @Test
  void mergePrioritizesLocalityThenVersionThenTimestampAndCopiesTopLevelState() {
    var remoteNewer = result("duplicate", "remote", false, "3.0.0", 30);
    remoteNewer.setWorkspace("workspace");
    remoteNewer.getNotifications().add(Notification.warning("remote warning"));
    remoteNewer
        .getProjects()
        .add(descriptor("project", "remote", false, "1.0.0", 1, KnowledgeClass.PROJECT));

    var localOlder = result("duplicate", "local", true, "1.0.0", 1);
    localOlder.setWorkspace("workspace");

    var highVersion = result("versioned", "a", false, "2.0.0", 1);
    var lowVersionNewTimestamp = result("versioned", "b", false, "1.0.0", 100);

    var merged =
        Utils.Resources.merge(remoteNewer, localOlder, highVersion, lowVersionNewTimestamp);

    assertEquals("local", result(merged, "duplicate").getServiceId());
    assertEquals("a", result(merged, "versioned").getServiceId());
    assertEquals(1, merged.getProjects().size());
    assertEquals(1, merged.getNotifications().size());
    assertEquals("workspace", merged.getWorkspace());
    assertFalse(merged.isEmpty());
  }

  @Test
  void mergeReturnsIndependentResultAndPreservesEmptySemantics() {
    var source = ResourceSet.empty(Notification.warning("nothing found"));

    var merged = Utils.Resources.merge(source);

    assertNotSame(source, merged);
    assertTrue(merged.isEmpty());
    assertEquals(1, merged.getNotifications().size());
    assertTrue(Utils.Resources.merge((ResourceSet[]) null).isEmpty());
  }

  private static ResourceSet result(
      String urn, String serviceId, boolean local, String version, long timestamp) {
    var ret = new ResourceSet();
    ret.getResults()
        .add(
            descriptor(
                urn, serviceId, local, version, timestamp, KlabAsset.KnowledgeClass.MODEL));
    return ret;
  }

  private static ResourceSet.Resource result(ResourceSet set, String urn) {
    return set.getResults().stream()
        .filter(resource -> urn.equals(resource.getResourceUrn()))
        .findFirst()
        .orElseThrow();
  }

  private static ResourceSet.Resource descriptor(
      String urn,
      String serviceId,
      boolean local,
      String version,
      long timestamp,
      KnowledgeClass knowledgeClass) {
    var ret = new ResourceSet.Resource();
    ret.setResourceUrn(urn);
    ret.setServiceId(serviceId);
    ret.setLocal(local);
    ret.setResourceVersion(Version.create(version));
    ret.setTimestamp(timestamp);
    ret.setKnowledgeClass(knowledgeClass);
    return ret;
  }
}
