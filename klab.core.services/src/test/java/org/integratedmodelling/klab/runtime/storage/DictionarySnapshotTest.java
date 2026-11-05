package org.integratedmodelling.klab.runtime.storage;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.List;
import org.integratedmodelling.klab.api.data.Storage;
import org.integratedmodelling.klab.api.data.StorageScan;
import org.integratedmodelling.klab.api.data.mediation.classification.DataKey;
import org.integratedmodelling.klab.api.data.mediation.classification.KeyedData;
import org.integratedmodelling.klab.api.services.Reasoner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;

class DictionarySnapshotTest {
  @Test void legacyKeysExplicitlyLackDurableMetadata() {
    var legacy = mock(DataKey.class, CALLS_REAL_METHODS);
    assertTrue(legacy.dictionarySnapshot().isEmpty());
  }

  @Test void allocatorSnapshotsAreDetachedAndReadOnlyKeysRetainCapturedCodes() {
    var reasoner = mock(Reasoner.class);
    var root = KeyedStorageTest.concept("test:Root", true);
    var a = KeyedStorageTest.concept("test:A", false);
    var b = KeyedStorageTest.concept("test:B", false);
    a.getMetadata().put("klab:authorityId", "authority-A");
    for (var concept : List.of(a, b)) {
      when(reasoner.resolveConcept(concept.getUrn())).thenReturn(concept);
      when(reasoner.is(concept, root)).thenReturn(true);
    }
    var binds = new java.util.concurrent.atomic.AtomicInteger();
    var key = new ConceptDataKey(reasoner, root, KeyedStorageTest.WORLD, binds::incrementAndGet);
    assertTrue(key.dictionarySnapshot().orElseThrow().entries().isEmpty());
    assertEquals(0, binds.get());
    assertEquals(1, key.code(a));
    var frozenKey = key.readOnly();
    var frozen = frozenKey.dictionarySnapshot().orElseThrow();
    assertEquals(key.snapshot(), frozen);
    assertEquals(1, frozen.version());
    assertEquals(KeyedStorageTest.WORLD, frozen.worldview());
    assertEquals(root.getUrn(), frozen.target());
    assertEquals("test:A", frozen.entries().getFirst().definition());
    assertEquals("authority-A", frozen.entries().getFirst().authority());
    assertEquals(key.getLabels().get(1), frozen.entries().getFirst().label());
    assertEquals(2, key.code(b));
    assertEquals(1, frozen.entries().size());
    assertEquals(2, key.dictionarySnapshot().orElseThrow().entries().size());
    assertSame(frozen, frozenKey.dictionarySnapshot().orElseThrow());
    assertEquals(2, frozenKey.size());
    assertThrows(IllegalStateException.class, () -> frozenKey.lookup(2));
    assertThrows(UnsupportedOperationException.class, () -> frozen.entries().clear());
    assertThrows(UnsupportedOperationException.class, () -> frozen.worldview().ontologies().clear());
    clearInvocations(reasoner);
    assertEquals(frozen.fingerprint(), frozenKey.dictionarySnapshot().orElseThrow().fingerprint());
    key.dictionarySnapshot();
    verifyNoInteractions(reasoner);
    assertEquals(1, binds.get());
  }

  @Test void restoredMetadataDoesNotTriggerLazyConceptResolutionOrBinding() {
    var reasoner = mock(Reasoner.class);
    var root = KeyedStorageTest.concept("test:Root", true);
    var binds = new java.util.concurrent.atomic.AtomicInteger();
    var key = new ConceptDataKey(reasoner, root, KeyedStorageTest.WORLD, binds::incrementAndGet);
    var dictionary = new KeyedData.Dictionary(1, KeyedStorageTest.WORLD, root.getUrn(), List.of(
        new KeyedData.Entry("test:A", "Forest & <label> 雪", "authority:α"),
        new KeyedData.Entry("test:B", "Forest & <label> 雪", "authority:β")));
    key.restore(dictionary);
    assertEquals(dictionary, key.dictionarySnapshot().orElseThrow());
    assertEquals(dictionary, key.readOnly().dictionarySnapshot().orElseThrow());
    assertEquals(dictionary.fingerprint(), key.dictionarySnapshot().orElseThrow().fingerprint());
    assertEquals(0, key.reverseLookup(null));
    assertNull(key.lookup(0));
    assertEquals(3, key.size());
    assertEquals(0, binds.get());
    verifyNoInteractions(reasoner);
  }

  @Test void plannedKeyScannersExposeTheirActualCapturedDictionary(@TempDir Path directory) {
    KeyedStorageTest.configure();
    try (var fixture = new KeyedStorageTest.Fixture(directory)) {
      fixture.fill(true);
      var plan = fixture.storage.plan(fixture.request(null, StorageScan.Sampling.EXACT));
      try (var session = fixture.storage.open(plan)) {
        for (var scanner : session.scanners()) {
          var key = ((Storage.KeyScanner<?>) scanner).key();
          var snapshot = key.dictionarySnapshot().orElseThrow();
          assertEquals(plan.description().dictionary(), snapshot);
          assertEquals(plan.description().dictionary().fingerprint(), snapshot.fingerprint());
          assertEquals(key.size() - 1, snapshot.entries().size());
        }
      }
    }
  }
}
