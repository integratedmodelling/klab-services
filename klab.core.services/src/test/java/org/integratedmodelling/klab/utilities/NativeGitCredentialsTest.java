package org.integratedmodelling.klab.utilities;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class NativeGitCredentialsTest {
  @TempDir Path root;

  @Test void usesInstalledGitHelperWithoutPersistingItsSecrets() throws Exception {
    var init = new ProcessBuilder("git", "init", root.toString()).redirectErrorStream(true).start();
    assertEquals(0, init.waitFor());
    // A repository-scoped fixture overrides any real user helpers and never contacts a server.
    Files.writeString(root.resolve(".git/config"), "[core]\n repositoryformatversion = 0\n"
        + "[credential]\n helper =\n helper = \"!f() { echo username=fixture; echo password=fixture-token; }; f\"\n");
    var credentials = NativeGitCredentials.resolve("https://example.invalid/org/repo.git", root.toFile());
    assertNotNull(credentials);
    assertEquals("fixture", credentials.getParameters().get("username"));
    assertEquals("fixture-token", credentials.getParameters().get("password"));
    assertEquals("https://example.invalid/org/repo.git", credentials.getEndpoint());
    assertNull(credentials.getId());
    assertFalse(Files.readString(root.resolve(".git/config")).contains("credentialId"));
  }

  @Test void rejectsEmbeddedCredentialsAndProtocolInjection() {
    assertNull(NativeGitCredentials.resolve("https://user:secret@example.invalid/repo.git", root.toFile()));
    assertNull(NativeGitCredentials.resolve("file:///repo", root.toFile()));
  }
}
