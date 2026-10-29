package org.integratedmodelling.klab.utilities;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.net.http.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import org.integratedmodelling.klab.api.exceptions.KlabIOException;
import org.integratedmodelling.klab.utilities.Utils.Maven.MavenCoordinates;
import org.junit.jupiter.api.Test;

class MavenSnapshotRepositoryTest {
  private static final String EMPTY = "{\"response\":{\"docs\":[]}}";
  private static final String FOUND = "{\"response\":{\"docs\":[{\"timestamp\":1793000000000,\"v\":\"1-SNAPSHOT\"}]}}";
  private static final String XML = "<metadata><lastUpdated>20261029090000</lastUpdated>"
      + "<snapshot><timestamp>20261029.090000</timestamp><buildNumber>7</buildNumber></snapshot></metadata>";
  private static MavenCoordinates coordinates(String artifact, String version) {
    return new MavenCoordinates("review.test", artifact, version);
  }

  private static class Fixture {
    final AtomicLong clock = new AtomicLong();
    final AtomicInteger searchCalls = new AtomicInteger(), repositoryCalls = new AtomicInteger();
    final HttpClient client = mock(HttpClient.class);
    final MavenSnapshotRepository repository = new MavenSnapshotRepository(client, clock::get, Duration.ofMinutes(20));
    volatile int searchStatus = 200, repositoryStatus = 200;
    volatile String searchBody = EMPTY, xml = XML;
    volatile CountDownLatch entered, release;
    volatile boolean interrupt;

    @SuppressWarnings("unchecked") Fixture() throws Exception {
      when(client.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenAnswer(call -> {
        if (interrupt) throw new InterruptedException("cancelled");
        HttpRequest request = call.getArgument(0);
        boolean search = request.uri().getRawQuery() != null;
        (search ? searchCalls : repositoryCalls).incrementAndGet();
        if (search && entered != null) {
          entered.countDown();
          assertTrue(release.await(5, TimeUnit.SECONDS));
        }
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(search ? searchStatus : repositoryStatus);
        when(response.body()).thenReturn(search ? searchBody : xml);
        return response;
      });
    }
    void minutes(long value) { clock.addAndGet(Duration.ofMinutes(value).toNanos()); }
  }

  @Test void search429FallsBackToRepositoryAndDownloadSharesItsMetadata() throws Exception {
    var f = new Fixture(); f.searchStatus = 429;
    var c = coordinates("fallback", "1-SNAPSHOT");
    assertTrue(f.repository.latest(c).isFound());
    assertEquals("1-20261029.090000-7", f.repository.downloadVersion(c));
    assertEquals("1-20261029.090000-7", f.repository.downloadVersion(c));
    assertEquals(1, f.searchCalls.get()); assertEquals(1, f.repositoryCalls.get());
  }

  @Test void positiveSearchStillCachesRepositoryMetadataForRepeatedDownloads() throws Exception {
    var f = new Fixture(); f.searchBody = FOUND;
    var c = coordinates("download", "1-SNAPSHOT");
    assertTrue(f.repository.latest(c).isFound());
    assertEquals(0, f.repositoryCalls.get());
    assertEquals("1-20261029.090000-7", f.repository.downloadVersion(c));
    assertEquals("1-20261029.090000-7", f.repository.downloadVersion(c));
    assertEquals(1, f.searchCalls.get()); assertEquals(1, f.repositoryCalls.get());
  }

  @Test void missingSnapshotIsCachedForTwoMinutesThenCanAppear() throws Exception {
    var f = new Fixture(); f.repositoryStatus = 404;
    var c = coordinates("missing", "1-SNAPSHOT");
    assertFalse(f.repository.latest(c).isFound());
    assertNull(f.repository.downloadVersion(c));
    f.repositoryStatus = 200; f.minutes(1);
    assertFalse(f.repository.latest(c).isFound()); assertEquals(1, f.repositoryCalls.get());
    f.minutes(1);
    assertTrue(f.repository.latest(c).isFound()); assertEquals(2, f.repositoryCalls.get());
  }

  @Test void coldRepositoryErrorBacksOffAcrossUpdateAndDownloadCalls() throws Exception {
    var f = new Fixture(); f.searchStatus = 429; f.repositoryStatus = 429;
    var c = coordinates("retry", "1-SNAPSHOT");
    assertThrows(KlabIOException.class, () -> f.repository.latest(c));
    assertThrows(KlabIOException.class, () -> f.repository.downloadVersion(c));
    assertEquals(1, f.searchCalls.get()); assertEquals(1, f.repositoryCalls.get());
    f.repositoryStatus = 200; f.minutes(1);
    assertEquals("1-20261029.090000-7", f.repository.downloadVersion(c));
    assertEquals(2, f.searchCalls.get()); assertEquals(2, f.repositoryCalls.get());
  }

  @Test void positiveDateDoesNotBypassRepositoryErrorBackoffDuringDownloads() throws Exception {
    var f = new Fixture(); f.searchBody = FOUND; f.repositoryStatus = 429;
    var c = coordinates("downloadretry", "1-SNAPSHOT");
    assertTrue(f.repository.latest(c).isFound());
    assertThrows(KlabIOException.class, () -> f.repository.downloadVersion(c));
    assertThrows(KlabIOException.class, () -> f.repository.downloadVersion(c));
    assertEquals(1, f.repositoryCalls.get());
    f.repositoryStatus = 200; f.minutes(1);
    assertEquals("1-20261029.090000-7", f.repository.downloadVersion(c));
    assertEquals(2, f.repositoryCalls.get()); assertEquals(1, f.searchCalls.get());
  }

  @Test void expiredSuccessSurvivesFailureButRetriesInOneMinute() throws Exception {
    var f = new Fixture(); var c = coordinates("stale", "1-SNAPSHOT");
    var first = f.repository.latest(c);
    f.minutes(20); f.repositoryStatus = 503;
    assertSame(first, f.repository.latest(c));
    assertEquals("1-20261029.090000-7", f.repository.downloadVersion(c));
    assertEquals(2, f.repositoryCalls.get());
    f.repositoryStatus = 200; f.xml = XML.replace("090000", "100000").replace(">7<", ">8<");
    f.minutes(1);
    assertNotSame(first, f.repository.latest(c));
    assertEquals("1-20261029.100000-8", f.repository.downloadVersion(c));
    assertEquals(3, f.repositoryCalls.get());
  }

  @Test void concurrentUpdateAndDownloadLookupsRefreshEachEndpointOnce() throws Exception {
    var f = new Fixture(); f.entered = new CountDownLatch(1); f.release = new CountDownLatch(1);
    var c = coordinates("concurrent", "1-SNAPSHOT");
    try (var pool = Executors.newVirtualThreadPerTaskExecutor()) {
      var futures = new ArrayList<Future<String>>();
      for (int i = 0; i < 12; i++) futures.add(pool.submit(() -> f.repository.downloadVersion(c)));
      assertTrue(f.entered.await(5, TimeUnit.SECONDS)); f.release.countDown();
      for (var future : futures) assertEquals("1-20261029.090000-7", future.get(5, TimeUnit.SECONDS));
    } finally { f.release.countDown(); }
    assertEquals(1, f.searchCalls.get()); assertEquals(1, f.repositoryCalls.get());
  }

  @Test void coordinatesKeepArtifactAndVersionEntriesSeparate() throws Exception {
    var f = new Fixture();
    assertTrue(f.repository.latest(coordinates("one", "1-SNAPSHOT")).isFound());
    assertTrue(f.repository.latest(coordinates("two", "1-SNAPSHOT")).isFound());
    assertTrue(f.repository.latest(coordinates("one", "2-SNAPSHOT")).isFound());
    assertEquals(3, f.repositoryCalls.get());
    assertEquals("2-20261029.090000-7", f.repository.downloadVersion(coordinates("one", "2-SNAPSHOT")));
    assertEquals(3, f.repositoryCalls.get());
  }

  @Test void interruptedLookupIsNotHiddenOrCached() throws Exception {
    var f = new Fixture(); f.interrupt = true;
    var c = coordinates("interrupt", "1-SNAPSHOT");
    try {
      assertThrows(KlabIOException.class, () -> f.repository.latest(c));
      assertTrue(Thread.currentThread().isInterrupted());
    } finally { Thread.interrupted(); }
    f.interrupt = false;
    assertTrue(f.repository.latest(c).isFound());
  }
}
