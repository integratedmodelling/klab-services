package org.integratedmodelling.klab.api.configuration;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.util.Properties;
import org.junit.jupiter.api.Test;

class DataRootSelectionTest {
  private Properties properties() {
    var properties = new Properties();
    properties.setProperty("user.home", Path.of("synthetic-home").toAbsolutePath().toString());
    return properties;
  }

  @Test void explicitRootWinsWithoutChangingInputs() {
    var p = properties();
    Path explicit = Path.of("private-root").toAbsolutePath();
    p.setProperty(DataRootSelection.DATA_DIRECTORY, explicit.toString());
    p.setProperty(DataRootSelection.WORK_DIRECTORY, "ignored");
    var before = new Properties(); before.putAll(p);
    assertEquals(explicit, DataRootSelection.select(p));
    assertEquals(before, p);
  }

  @Test void defaultAndRelativeOverrideUseHome() {
    var p = properties(); Path home = Path.of(p.getProperty("user.home"));
    assertEquals(home.resolve(".klab"), DataRootSelection.select(p));
    p.setProperty(DataRootSelection.WORK_DIRECTORY, "custom");
    assertEquals(home.resolve("custom"), DataRootSelection.select(p));
  }

  @Test void invalidExplicitRootFailsWithoutFallback() {
    var p = properties();
    for (String invalid : new String[]{"", " ", "relative-root"}) {
      p.setProperty(DataRootSelection.DATA_DIRECTORY, invalid);
      assertThrows(IllegalArgumentException.class, () -> DataRootSelection.select(p));
    }
  }

  @Test void conflictingCliAndJvmRootsAreRejected() {
    var p = properties(); Path root = Path.of("private-root").toAbsolutePath();
    p.setProperty(DataRootSelection.DATA_DIRECTORY, root.toString());
    assertEquals(root, DataRootSelection.selectForService(p, root));
    assertThrows(IllegalArgumentException.class,
        () -> DataRootSelection.selectForService(p, root.resolve("different")));
  }

  @Test void cliRootIsSelectedWhenJvmRootIsAbsent() {
    var p = properties(); Path root = Path.of("private-root").toAbsolutePath();
    assertEquals(root, DataRootSelection.selectForService(p, root));
    assertFalse(p.containsKey(DataRootSelection.DATA_DIRECTORY));
  }

  @Test void harnessDetectsUnintendedDefaultRootBeforeAnyConfigurationInitialization() {
    var p = properties(); Path boundary = Path.of("private-boundary").toAbsolutePath();
    // Selection and rejection do not touch disk, System properties, or either singleton.
    assertThrows(IllegalArgumentException.class,
        () -> DataRootSelection.requirePrivateRoots(boundary, DataRootSelection.select(p)));
    DataRootSelection.requirePrivateRoots(boundary, boundary.resolve("home"),
        boundary.resolve("data"), boundary.resolve("temp"));
    assertThrows(IllegalArgumentException.class,
        () -> DataRootSelection.requirePrivateRoots(boundary, boundary.resolve("../escape")));
  }

  @Test void bootstrapPropagatesCliRootForRepeatedClaims() {
    {
      var state = new DataRootBootstrap(); var p = properties();
      Path root = Path.of("private-root").toAbsolutePath();
      assertEquals(root, state.prepare(p, root));
      assertEquals(root.toString(), p.getProperty(DataRootSelection.DATA_DIRECTORY));
      // Both configuration constructors use this same claim before their first write.
      assertEquals(root, state.claim(p)); assertEquals(root, state.claim(p));
    }
  }

  @Test void conflictingBootstrapDoesNotMutatePropertiesOrClaimState() {
    var state = new DataRootBootstrap(); var p = properties();
    Path root = Path.of("private-root").toAbsolutePath();
    p.setProperty(DataRootSelection.DATA_DIRECTORY, root.toString());
    var before = new Properties(); before.putAll(p);
    assertThrows(IllegalArgumentException.class, () -> state.prepare(p, root.resolve("different")));
    assertEquals(before, p); assertEquals(root, state.claim(p));
  }

  @Test void lateRootChangeIsRejectedBeforePropertyMutation() {
    var state = new DataRootBootstrap(); var p = properties();
    Path claimed = state.claim(p);
    var before = new Properties(); before.putAll(p);
    assertThrows(IllegalStateException.class,
        () -> state.prepare(p, claimed.resolve("different")));
    assertEquals(before, p); assertEquals(claimed, state.claim(p));
  }

  @Test void settingsCannotRelocateOneSingletonAwayFromTheClaimedRoot() {
    var state = new DataRootBootstrap(); var p = properties(); Path claimed = state.claim(p);
    assertThrows(IllegalStateException.class, () -> state.requireRoot(claimed.resolve("different")));
    assertEquals(claimed, state.requireRoot(claimed));
  }
}
