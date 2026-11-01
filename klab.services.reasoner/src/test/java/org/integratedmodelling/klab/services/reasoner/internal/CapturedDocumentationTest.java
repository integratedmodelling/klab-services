package org.integratedmodelling.klab.services.reasoner.internal;

import static org.junit.jupiter.api.Assertions.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.HexFormat;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CapturedDocumentationTest {
  @TempDir Path directory;
  private final ObjectMapper json = new ObjectMapper();
  private Path state() throws Exception { return Files.createDirectories(directory.resolve("state")); }
  private ObjectNode present(Path state, byte[] contents) throws Exception {
    String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(contents));
    String name = hash + ".md"; Path file = Files.createDirectories(state.resolve("documentation-files")).resolve(name);
    Files.write(file, contents);
    return json.createObjectNode().put("originalUri", "file:/never-read/klab.authority.taxa/documentation/" + name)
        .put("targetPath", "/never-write/original-temp/" + name)
        .put("bundlePath", "documentation-files/" + name).put("sha256", hash)
        .put("bytes", contents.length).put("missingAtCapture", false);
  }
  private void manifest(Path state, ObjectNode... entries) throws Exception {
    var array = json.createArrayNode(); for (var entry : entries) array.add(entry);
    Files.write(state.resolve("documentation-materialization.json"), json.writeValueAsBytes(array));
  }
  @Test void ownedBytesRetainOriginalUriAndNeverFollowOriginalTarget() throws Exception {
    Path state = state(); byte[] contents = "original bytes".getBytes(); var entry = present(state, contents);
    manifest(state, entry); byte[] before = Files.readAllBytes(state.resolve("documentation-materialization.json"));
    var captured = CapturedDocumentation.read(state);
    assertArrayEquals(contents, captured.get(entry.path("originalUri").asText()));
    assertArrayEquals(before, Files.readAllBytes(state.resolve("documentation-materialization.json")));
    assertEquals(1, captured.size());
  }
  @Test void missingMarkerRemainsMissingWithoutReadingOrInventingPayload() throws Exception {
    Path state = state(); var entry = json.createObjectNode()
        .put("originalUri", "file:/never-read/klab.authority.taxa/documentation/absent.md")
        .put("targetPath", "/never-write/absent.md").put("missingAtCapture", true);
    manifest(state, entry); assertEquals(0, CapturedDocumentation.read(state).get(entry.path("originalUri").asText()).length);
    assertFalse(Files.exists(state.resolve("documentation-files")));
    entry.put("bundlePath", "invented.md"); manifest(state, entry);
    assertThrows(IllegalArgumentException.class, () -> CapturedDocumentation.read(state));
  }
  @Test void changedBytesAndChangedHashRejectInsteadOfFollowingOriginalUri() throws Exception {
    Path state = state(); var entry = present(state, "first".getBytes()); manifest(state, entry);
    Files.write(state.resolve(entry.path("bundlePath").asText()), "other".getBytes());
    assertThrows(IllegalArgumentException.class, () -> CapturedDocumentation.read(state));
    entry.put("sha256", "0".repeat(64)); manifest(state, entry);
    assertThrows(IllegalArgumentException.class, () -> CapturedDocumentation.read(state));
  }
  @Test void traversalAbsolutePathsAndMissingPresentPayloadReject() throws Exception {
    Path state = state(); var entry = present(state, "content".getBytes());
    for (String path : new String[]{"../outside.md", directory.resolve("outside.md").toString(), "documentation-files/missing.md"}) {
      entry.put("bundlePath", path); manifest(state, entry);
      assertThrows(IllegalArgumentException.class, () -> CapturedDocumentation.read(state));
    }
  }
  @Test void wrongSizeAndDuplicateOriginalIdentityReject() throws Exception {
    Path state = state(); var entry = present(state, "content".getBytes());
    entry.put("bytes", 2); manifest(state, entry);
    assertThrows(IllegalArgumentException.class, () -> CapturedDocumentation.read(state));
    entry.put("bytes", 7); manifest(state, entry, entry);
    assertThrows(IllegalArgumentException.class, () -> CapturedDocumentation.read(state));
  }
  @Test void absentManifestKeepsLegacyContractWithoutSearchingElsewhere() throws Exception {
    assertNull(CapturedDocumentation.read(state()));
  }
  @Test void copiesAreIndependentAndPreviouslyReadBytesStayFrozenAfterPayloadMutation() throws Exception {
    Path state = state(); byte[] original = "original".getBytes(); var entry = present(state, original); manifest(state, entry);
    String uri = entry.path("originalUri").asText(); var first = CapturedDocumentation.read(state);
    var second = CapturedDocumentation.read(state); first.get(uri)[0] = 0;
    assertArrayEquals(original, second.get(uri));
    Files.write(state.resolve(entry.path("bundlePath").asText()), "modified".getBytes());
    assertArrayEquals(original, second.get(uri));
    assertThrows(IllegalArgumentException.class, () -> CapturedDocumentation.read(state));
  }
  @Test void returnedFilesBelongToIndependentCandidateAssets() throws Exception {
    Path state = state(); byte[] original = "original".getBytes(); var entry = present(state, original); manifest(state, entry);
    String uri = entry.path("originalUri").asText(); var frozen = CapturedDocumentation.read(state);
    Path first = CapturedDocumentation.materialize(directory.resolve("candidate-one"), uri, frozen.get(uri));
    Path second = CapturedDocumentation.materialize(directory.resolve("candidate-two"), uri, frozen.get(uri));
    assertTrue(first.startsWith(directory.resolve("candidate-one"))); assertTrue(second.startsWith(directory.resolve("candidate-two")));
    assertNotEquals(first, second); Files.writeString(first, "changed first copy");
    assertArrayEquals(original, Files.readAllBytes(second)); assertArrayEquals(original, frozen.get(uri));
    assertThrows(IllegalArgumentException.class, () -> CapturedDocumentation.materialize(directory.resolve("missing"), uri, new byte[0]));
    assertFalse(Files.exists(directory.resolve("missing")));
  }
  @Test void absentIdentityNeverFallsBackAndMissingStaysMissingIfOriginalTargetAppears() throws Exception {
    Path state = state(); var entry = present(state, "original".getBytes()); manifest(state, entry);
    var map = CapturedDocumentation.read(state);
    Path external = Files.createDirectories(directory.resolve("klab.authority.taxa/documentation")).resolve("later.md");
    Files.writeString(external, "newly appeared bytes"); String uri = external.toUri().toString();
    assertThrows(IllegalArgumentException.class, () -> CapturedDocumentation.lookup(map, uri));
    var marker = json.createObjectNode().put("originalUri", uri).put("missingAtCapture", true);
    manifest(state, marker); assertEquals(0, CapturedDocumentation.lookup(CapturedDocumentation.read(state), uri).length);
    String original = entry.path("originalUri").asText(); byte[] copy = CapturedDocumentation.lookup(map, original); copy[0] = 0;
    assertArrayEquals("original".getBytes(), CapturedDocumentation.lookup(map, original));
  }
  @Test void junctionPayloadOutsideStateRejectsBeforeReadingItsContent() throws Exception {
    Path state = state(); Path outside = Files.createDirectories(directory.resolve("outside"));
    var entry = present(outside, "outside content".getBytes()); manifest(state, entry);
    Path link = state.resolve("documentation-files"); Path target = outside.resolve("documentation-files");
    if (System.getProperty("os.name").startsWith("Windows")) {
      String command = "New-Item -ItemType Junction -Path '" + link.toString().replace("'", "''")
          + "' -Target '" + target.toString().replace("'", "''") + "' | Out-Null";
      var process = new ProcessBuilder("powershell.exe", "-NoProfile", "-NonInteractive", "-Command", command)
          .redirectErrorStream(true).start();
      String output = new String(process.getInputStream().readAllBytes());
      assertEquals(0, process.waitFor(), output);
    } else Files.createSymbolicLink(link, target);
    assertThrows(IllegalArgumentException.class, () -> CapturedDocumentation.read(state));
  }
}
