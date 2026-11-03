package org.integratedmodelling.klab.services.reasoner.internal;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.net.URI;
import java.net.http.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.*;

/** Resolve the authored ODO document before freezing a candidate; workers use owned bytes only. */
final class OdoImportCapture {
  private static final int MAX_BYTES = 8 * 1024 * 1024;
  private static final ObjectMapper JSON = new ObjectMapper();
  @FunctionalInterface interface Fetcher { byte[] fetch(URI uri) throws IOException, InterruptedException; }
  record Document(String locator, byte[] bytes, String sha256, String source) {}
  private record Cached(String locator, String sha256, byte[] bytes) {}
  private final Path directory;
  private final Fetcher fetcher;

  OdoImportCapture(Path directory) { this(directory, OdoImportCapture::fetch); }
  OdoImportCapture(Path directory, Fetcher fetcher) { this.directory = directory; this.fetcher = fetcher; }

  Document capture(String locator, byte[] bundled) throws Exception {
    var uri = URI.create(locator);
    if (!("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme()))
        || uri.getHost() == null || uri.getUserInfo() != null || uri.getFragment() != null)
      throw new IllegalArgumentException("ODO import requires an HTTP(S) document URL");
    byte[] bytes;
    try { bytes = fetcher.fetch(uri); }
    catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); throw interrupted; }
    catch (IOException unavailable) {
      var cached = cached(locator);
      if (cached != null) return document(locator, cached, "CACHED_REMOTE");
      // This fallback certifies compatibility of the bundled ontology/version, not byte equality
      // with a document that could not be reached. Prefer the last successful remote capture.
      validate(locator, bundled);
      return document(locator, bundled, "BUNDLED_OFFLINE");
    }
    // A reachable but invalid document must never silently select the bundled fallback.
    validate(locator, bytes);
    var result = document(locator, bytes, "REMOTE");
    try { persist(result); }
    catch (IOException unwritable) {
      System.getLogger(OdoImportCapture.class.getName()).log(System.Logger.Level.WARNING,
          "ODO import was captured, but its offline cache could not be written");
    }
    return result;
  }

  private static Document document(String locator, byte[] bytes, String source) throws Exception {
    String hash = hash(bytes);
    System.getLogger(OdoImportCapture.class.getName()).log(
        "REMOTE".equals(source) ? System.Logger.Level.INFO : System.Logger.Level.WARNING,
        "OWL_IMPORT_CAPTURE alias=odo source=" + source + " sha256=" + hash + " locator=" + locator);
    return new Document(locator, bytes.clone(), hash, source);
  }

  private static void validate(String locator, byte[] bytes) throws Exception {
    if (bytes == null || bytes.length == 0 || bytes.length > MAX_BYTES)
      throw new IllegalArgumentException("ODO document is empty or exceeds 8 MiB");
    var identities = SemanticEnvironmentFactory.ontologyIdentities(bytes);
    if (!identities.contains("http://integratedmodelling.org/odo"))
      throw new IllegalArgumentException("Declared ODO source does not identify the ODO ontology");
    var release = java.util.regex.Pattern.compile("/releases/([^/]+)/[^/]+\\.owl$")
        .matcher(URI.create(locator).getPath());
    if (release.find() && !identities.contains("http://integratedmodelling.org/odo/" + release.group(1)))
      throw new IllegalArgumentException("ODO document version does not match its declared release " + release.group(1));
  }

  private byte[] cached(String locator) {
    try {
      var file = file(locator);
      if (!Files.isRegularFile(file) || Files.size(file) > MAX_BYTES * 2L) return null;
      var entry = JSON.readValue(Files.readAllBytes(file), Cached.class);
      if (!locator.equals(entry.locator()) || !hash(entry.bytes()).equals(entry.sha256())) return null;
      validate(locator, entry.bytes());
      return entry.bytes();
    } catch (Exception invalid) { return null; }
  }

  private void persist(Document document) throws IOException {
    Files.createDirectories(directory);
    var temporary = Files.createTempFile(directory, "odo-", ".tmp");
    try {
      Files.write(temporary, JSON.writeValueAsBytes(new Cached(document.locator(), document.sha256(), document.bytes())));
      try { Files.move(temporary, file(document.locator()), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
      catch (AtomicMoveNotSupportedException unsupported) {
        Files.move(temporary, file(document.locator()), StandardCopyOption.REPLACE_EXISTING);
      }
    } finally { Files.deleteIfExists(temporary); }
  }

  private Path file(String locator) { return directory.resolve(CachedAuthority.digest(locator) + ".json"); }
  private static String hash(byte[] bytes) {
    try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)); }
    catch (java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
  }

  private static byte[] fetch(URI uri) throws IOException, InterruptedException {
    try (var client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(4))
        .followRedirects(HttpClient.Redirect.NORMAL).build()) {
      var request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(10)).GET().build();
      var response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
      if (response.statusCode() != 200) throw new IOException("ODO source returned HTTP " + response.statusCode());
      if (response.body().length > MAX_BYTES) throw new IllegalArgumentException("ODO source exceeds 8 MiB");
      return response.body();
    }
  }
}
