package org.integratedmodelling.klab.services.reasoner.internal.worldview;

import static org.junit.jupiter.api.Assertions.*;
import java.net.URI;
import java.util.*;
import org.integratedmodelling.klab.api.scope.UserScope;
import org.integratedmodelling.klab.api.services.resources.ResourceSet;
import org.integratedmodelling.klab.services.reasoner.internal.worldview.SourceRegistry.*;
import org.junit.jupiter.api.Test;

class SourceRegistryTest {
  private static final Key A = new Key("service-a", "project");
  private static final Key B = new Key("service-b", "project");
  private static final URI ENDPOINT = URI.create("https://example.invalid/resources");
  private static final Capabilities CAPABILITIES = new Capabilities(true, true, true);
  private static Revision revision(String value) { return new Revision(null, value, "fake-digest-v1"); }
  private static Contribution contribution(Key key, String value) {
    return new Contribution(key, revision(value), value, Map.of());
  }
  private static SourceRegistry registered() {
    return SourceRegistry.empty().register(A, ENDPOINT, CAPABILITIES).register(B, ENDPOINT, CAPABILITIES);
  }
  private static SourceRegistry capture(SourceRegistry registry, Contribution contribution) {
    var key = contribution.key();
    return registry.capture(key, registry.generation(), registry.sources().get(key).generation(),
        new Captured(contribution));
  }
  private static SourceRegistry published() {
    var registry = capture(capture(registered(), contribution(A, "a1")), contribution(B, "b1"));
    return registry.acknowledgePublication(registry.generation(), registry.publication(), registry.capturedVector());
  }

  @Test void existingDescriptorIdentityIsCopiedAndEndpointIsNotIdentity() {
    var descriptor = new ResourceSet.Resource();
    descriptor.setServiceId("service-a"); descriptor.setProjectUrn("project");
    var key = Key.from(descriptor);
    descriptor.setProjectUrn("mutated");
    assertEquals(A, key);
    var old = published();
    var moved = old.relocate(A, URI.create("https://other.invalid/resources"));
    assertEquals(old.published(), moved.published());
    assertEquals(Set.of(A, B), moved.sources().keySet());
    assertNotEquals(old.sources().get(A).generation(), moved.sources().get(A).generation());
    assertEquals(ENDPOINT, old.sources().get(A).endpoint());
  }

  @Test void mapsAreDefensivelyCopiedAndExposedAsImmutable() {
    var dependencies = new HashMap<Key, Revision>(); dependencies.put(B, revision("b1"));
    var c = new Contribution(A, revision("a1"), "a1", dependencies);
    dependencies.clear();
    assertEquals(Map.of(B, revision("b1")), c.dependencies());
    var r = capture(capture(registered(), c), contribution(B, "b1"));
    assertThrows(UnsupportedOperationException.class, () -> r.sources().clear());
    assertThrows(UnsupportedOperationException.class, () -> r.capturedVector().clear());
    assertThrows(UnsupportedOperationException.class, () -> c.dependencies().clear());
  }

  @Test void independentArrivalWaitsForExactDependencyVectorAndPreservesPublishedState() {
    var old = published();
    var a2 = new Contribution(A, revision("a2"), "a2", Map.of(B, revision("b2")));
    var pending = capture(old, a2);
    assertThrows(IllegalStateException.class, pending::capturedVector);
    assertEquals(old.published(), pending.published());
    var ready = capture(pending, contribution(B, "b2"));
    var next = ready.acknowledgePublication(ready.generation(), old.publication(), ready.capturedVector());
    assertEquals(Map.of(A, revision("a2"), B, revision("b2")), next.published());
    assertEquals(old.publication() + 1, next.publication());
    assertSame(next, next.acknowledgePublication(next.generation(), next.publication(), next.capturedVector()));
  }

  @Test void duplicateCaptureIsIdempotentAndRevisionCannotChangeContent() {
    var r = capture(registered(), contribution(A, "a1"));
    assertSame(r, capture(r, contribution(A, "a1")));
    assertThrows(IllegalArgumentException.class, () -> capture(r,
        new Contribution(A, revision("a1"), "different", Map.of())));
    assertEquals(revision("a2"), capture(r, contribution(A, "a2")).sources().get(A).captured().revision());
  }

  @Test void gapsAndEpochChangesRequireReconciliationAndOldDeliveriesAreIgnored() {
    var r = published(); long token = r.sources().get(A).generation();
    r = r.hint(new Hint(A, token, "epoch1", 10L));
    assertEquals(Status.RESYNC_REQUIRED, r.sources().get(A).status());
    r = capture(r, contribution(A, "a1"));
    var next = r.hint(new Hint(A, token, "epoch1", 11L));
    assertEquals(Status.DIRTY, next.sources().get(A).status());
    assertSame(next, next.hint(new Hint(A, token, "epoch1", 10L)));
    assertSame(next, next.hint(new Hint(A, token, "epoch1", 11L)));
    var gap = next.hint(new Hint(A, token, "epoch1", 13L));
    assertEquals(Status.RESYNC_REQUIRED, gap.sources().get(A).status());
    assertEquals(Status.RESYNC_REQUIRED, gap.hint(new Hint(A, token, "epoch1", 14L)).sources().get(A).status());
    assertEquals(Status.RESYNC_REQUIRED, next.hint(new Hint(A, token, "epoch2", 0L)).sources().get(A).status());
    var moved = next.relocate(A, ENDPOINT);
    assertSame(moved, moved.hint(new Hint(A, token, "epoch1", 12L)));
  }

  @Test void unsequencedHintsAreInvalidationsNotComparableNumbers() {
    var r = SourceRegistry.empty().register(A, ENDPOINT, new Capabilities(true, false, false));
    long token = r.sources().get(A).generation();
    r = r.hint(new Hint(A, token, "ignored", 9L));
    var next = r.hint(new Hint(A, token, "ignored", 1L));
    assertTrue(next.generation() > r.generation());
    assertNull(next.sources().get(A).sequence());
    assertEquals(Status.DIRTY, next.sources().get(A).status());
  }

  @Test void fakeAdapterWithoutCoherentCaptureCannotCertifyMutableReads() {
    Adapter fake = new Adapter() {
      public Capabilities capabilities(Key key, UserScope scope) { return new Capabilities(false, false, false); }
      public Capture capture(Key key, Revision expected, UserScope scope) {
        return new Captured(contribution(key, "mutable"));
      }
    };
    var r = SourceRegistry.empty().register(A, ENDPOINT, fake.capabilities(A, null));
    var next = r.capture(A, r.generation(), r.sources().get(A).generation(), fake.capture(A, null, null));
    assertEquals(Status.CAPTURE_UNAVAILABLE, next.sources().get(A).status());
    assertNull(next.sources().get(A).captured());
    assertThrows(IllegalStateException.class, next::capturedVector);
  }

  @Test void unavailableOrRejectedSourceRetainsPriorCaptureAndPublishedVector() {
    for (var status : List.of(Status.MISSING, Status.AUTH_EXPIRED, Status.REJECTED, Status.UNRESOLVED)) {
      var r = published();
      Adapter fake = new Adapter() {
        public Capabilities capabilities(Key key, UserScope scope) { return CAPABILITIES; }
        public Capture capture(Key key, Revision expected, UserScope scope) { return new Unavailable(status, "fake failure"); }
      };
      var next = r.capture(A, r.generation(), r.sources().get(A).generation(), fake.capture(A, revision("a1"), null));
      assertEquals(r.published(), next.published());
      assertEquals(r.sources().get(A).captured(), next.sources().get(A).captured());

      assertThrows(IllegalStateException.class, next::capturedVector);
    }
  }

  @Test void removalDoesNotChangePublishedVectorAndCannotBreakDependencies() {
    var r = capture(capture(registered(), new Contribution(A, revision("a1"), "a1", Map.of(B, revision("b1")))), contribution(B, "b1"));
    r = r.acknowledgePublication(r.generation(), r.publication(), r.capturedVector());
    var removed = r.remove(B);
    assertEquals(r.published(), removed.published());
    assertThrows(IllegalStateException.class, removed::capturedVector);
    var replacement = removed.register(B, ENDPOINT, CAPABILITIES);
    assertTrue(replacement.sources().get(B).generation() > r.sources().get(B).generation());
    assertThrows(IllegalStateException.class, replacement::capturedVector);
    var ready = capture(replacement, contribution(B, "b1"));
    assertEquals(r.published(), ready.capturedVector());
  }

  @Test void removalCanPublishOnlyCompleteRemainingVector() {
    var r = published().remove(B);
    var next = r.acknowledgePublication(r.generation(), r.publication(), r.capturedVector());
    assertEquals(Map.of(A, revision("a1")), next.published());
    assertEquals(Status.REMOVED, next.sources().get(B).status());
  }

  @Test void concurrentCaptureAndPublicationAreRejectedWithoutMutation() {
    var r = published();
    var changed = capture(r, contribution(A, "a2"));
    assertThrows(ConcurrentModificationException.class, () -> changed.capture(B, r.generation(),
        r.sources().get(B).generation(), new Captured(contribution(B, "b2"))));
    assertThrows(ConcurrentModificationException.class, () -> changed.acknowledgePublication(
        r.generation(), r.publication(), r.published()));
    assertThrows(ConcurrentModificationException.class, () -> changed.acknowledgePublication(
        changed.generation(), r.publication() - 1, changed.capturedVector()));
    assertThrows(IllegalArgumentException.class, () -> changed.acknowledgePublication(
        changed.generation(), changed.publication(), Map.of(A, revision("a2"))));
    assertEquals(r.published(), changed.published());
  }

  @Test void canonicalDigestIgnoresMapOrderButDetectsContentAtSameTimestampAndDeletion() {
    var first = new LinkedHashMap<String, String>();
    first.put("manifest", "timestamp=123"); first.put("a.kwv", "source-a"); first.put("b.kwv", "source-b");
    var reversed = new LinkedHashMap<String, String>();
    reversed.put("b.kwv", "source-b"); reversed.put("a.kwv", "source-a"); reversed.put("manifest", "timestamp=123");
    var original = SourceRegistry.contentRevision(SourceRegistry.encodeContent(first));
    assertEquals(original, SourceRegistry.contentRevision(SourceRegistry.encodeContent(reversed)));
    reversed.put("a.kwv", "changed");
    assertNotEquals(original, SourceRegistry.contentRevision(SourceRegistry.encodeContent(reversed)));
    first.remove("b.kwv");
    assertNotEquals(original, SourceRegistry.contentRevision(SourceRegistry.encodeContent(first)));
    var envelope = SourceRegistry.encodeContent(first);
    var documentRemoved = new Contribution(A, SourceRegistry.contentRevision(envelope), envelope, Map.of());
    var updated = capture(published(), documentRemoved);
    assertEquals(Status.CAPTURED, updated.sources().get(A).status());
    assertEquals(Set.of(A, B), updated.capturedVector().keySet());
    assertThrows(IllegalArgumentException.class, () -> new Contribution(A, original, envelope, Map.of()));
    assertNotEquals(SourceRegistry.encodeContent(Map.of("ab", "c")), SourceRegistry.encodeContent(Map.of("a", "bc")));
  }

  @Test void conditionalCaptureMismatchIsDistinctAndDoesNotRequestHistory() {
    var r = published();
    class Fake implements Adapter {
      public Capabilities capabilities(Key key, UserScope scope) { return CAPABILITIES; }
      public Capture capture(Key key, Revision expected, UserScope scope) {
        var current = contribution(key, "a2");
        return expected != null && !expected.equals(current.revision())
            ? new Mismatch(current.revision()) : new Captured(current);
      }
    }
    var fake = new Fake();
    var result = fake.capture(A, revision("a1"), null);
    assertInstanceOf(Mismatch.class, result);
    var mismatch = r.capture(A, r.generation(), r.sources().get(A).generation(), result);
    assertEquals(Status.REVISION_MISMATCH, mismatch.sources().get(A).status());
    assertEquals(r.published(), mismatch.published());
    assertEquals(r.sources().get(A).captured(), mismatch.sources().get(A).captured());
    assertThrows(IllegalStateException.class, mismatch::capturedVector);
    var accepted = mismatch.capture(A, mismatch.generation(), mismatch.sources().get(A).generation(),
        fake.capture(A, revision("a2"), null));
    assertEquals(revision("a2"), accepted.capturedVector().get(A));
  }

  @Test void deleteHintProviderDeletionAndDesiredRemovalAreDifferentTransitions() {
    var r = published();
    var hinted = r.hint(new Hint(A, r.sources().get(A).generation(), "epoch", 1L,
        org.integratedmodelling.klab.api.authentication.CRUDOperation.DELETE));
    assertNotEquals(Status.REMOVED, hinted.sources().get(A).status());
    var deleted = hinted.capture(A, hinted.generation(), hinted.sources().get(A).generation(),
        new Unavailable(Status.PROVIDER_DELETED, "Provider reports project deleted"));
    assertEquals(Status.PROVIDER_DELETED, deleted.sources().get(A).status());
    assertEquals(r.published(), deleted.published());
    assertThrows(IllegalStateException.class, deleted::capturedVector);
    var removed = deleted.remove(A);
    assertEquals(Status.REMOVED, removed.sources().get(A).status());
    assertEquals(Map.of(B, revision("b1")), removed.capturedVector());
  }

  @Test void staleSubscriptionCaptureCannotEnterEvenWithCurrentRegistryGeneration() {
    var r = published();
    var moved = r.relocate(A, ENDPOINT);
    assertThrows(ConcurrentModificationException.class, () -> moved.capture(A, moved.generation(),
        r.sources().get(A).generation(), new Captured(contribution(A, "a2"))));
    assertThrows(IllegalArgumentException.class, () -> r.register(A, ENDPOINT, CAPABILITIES));
  }
}
