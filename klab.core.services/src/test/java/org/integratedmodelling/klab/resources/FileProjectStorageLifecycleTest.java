package org.integratedmodelling.klab.resources;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.integratedmodelling.klab.api.knowledge.organization.ProjectStorage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FileProjectStorageLifecycleTest {

  @TempDir Path projectRoot;

  @Test
  void updateMovesRenamedBehaviorToItsCanonicalPath() throws Exception {
    var storage = new FileProjectStorage(projectRoot.toFile(), "test.project", null);
    var oldPath = projectRoot.resolve("behaviors/old/name.kactors");
    Files.createDirectories(oldPath.getParent());
    Files.writeString(oldPath, "behavior old.name\n");

    var updated =
        storage.update(
            ProjectStorage.ResourceType.BEHAVIOR,
            "old.name",
            "new.name",
            "behavior new.name\n");

    var newPath = projectRoot.resolve("behaviors/new/name.kactors");
    assertFalse(Files.exists(oldPath));
    assertTrue(Files.isRegularFile(newPath));
    assertEquals(newPath.toUri().toURL(), updated);
    assertEquals("behavior new.name\n", Files.readString(newPath));
  }

  @Test
  void createOrUpdateCanReplaceAnExistingRenameTarget() throws Exception {
    var storage = new FileProjectStorage(projectRoot.toFile(), "test.project", null);
    var oldPath = projectRoot.resolve("behaviors/old/name.kactors");
    var targetPath = projectRoot.resolve("behaviors/new/name.kactors");
    Files.createDirectories(oldPath.getParent());
    Files.createDirectories(targetPath.getParent());
    Files.writeString(oldPath, "behavior old.name\n");
    Files.writeString(targetPath, "stale target\n");

    storage.update(
        ProjectStorage.ResourceType.BEHAVIOR,
        "old.name",
        "new.name",
        "behavior new.name\n",
        true);

    assertFalse(Files.exists(oldPath));
    assertEquals("behavior new.name\n", Files.readString(targetPath));
  }

  @Test
  void testcasePathIncludesTheFilenameInItsUrn() {
    var document =
        ProjectStorage.getDocumentData(
            "testcases/klab/staging/vxii/testsuite.kactors");

    assertEquals(ProjectStorage.ResourceType.TESTCASE, document.getFirst());
    assertEquals("klab.staging.vxii.testsuite", document.getSecond());
  }
}
