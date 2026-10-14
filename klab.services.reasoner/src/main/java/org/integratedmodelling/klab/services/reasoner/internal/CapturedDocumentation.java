package org.integratedmodelling.klab.services.reasoner.internal;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;

/** Reads owned bytes keyed by the original URI; never accesses the URI's filesystem target. */
final class CapturedDocumentation {
  private CapturedDocumentation() {}

  static byte[] lookup(Map<String, byte[]> owned, String originalUri) {
    byte[] contents = owned.get(originalUri);
    if (contents == null) throw new IllegalArgumentException("Original documentation URI is absent from owned manifest");
    return contents.clone();
  }

  static Path materialize(Path assets, String originalUri, byte[] contents) throws Exception {
    if (contents.length == 0) throw new IllegalArgumentException("Missing captured documentation");
    Path owned = Files.createDirectories(assets).toRealPath();
    Path root = Files.createDirectories(assets.resolve("provider-documentation"));
    if (!root.toRealPath().startsWith(owned)) throw new IllegalArgumentException("Documentation assets escape candidate");
    String path = URI.create(originalUri).getPath(); int dot = path.lastIndexOf('.');
    String suffix = dot < 0 ? "bin" : path.substring(dot + 1).replaceAll("[^A-Za-z0-9]", "");
    String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(contents));
    Path target = root.resolve(hash + "." + suffix);
    if (Files.exists(target, LinkOption.NOFOLLOW_LINKS) && !target.toRealPath().startsWith(root.toRealPath()))
      throw new IllegalArgumentException("Documentation target escapes candidate");
    Files.write(target, contents); return target;
  }

  static Map<String, byte[]> read(Path state) throws Exception {
    Path manifest = state.resolve("documentation-materialization.json");
    if (!Files.exists(manifest)) return null; // Legacy capture keeps its existing temp-root contract.
    Path root = state.toRealPath();
    if (!root.startsWith(state.toAbsolutePath().getParent().toRealPath())
        || !manifest.toRealPath().startsWith(root) || Files.size(manifest) > 2 * 1024 * 1024)
      throw new IllegalArgumentException("Invalid owned documentation manifest");
    var entries = new ObjectMapper().readTree(Files.readAllBytes(manifest));
    if (!entries.isArray() || entries.size() > 4096)
      throw new IllegalArgumentException("Invalid owned documentation entries");
    var result = new LinkedHashMap<String, byte[]>(); long total = 0;
    for (var entry : entries) {
      String original = entry.path("originalUri").asText(); URI uri = URI.create(original);
      if (!"file".equals(uri.getScheme()) || uri.getPath() == null
          || !(uri.getPath().contains("/klab.authority.taxa/documentation/")
              || uri.getPath().contains("/klab.authority.pubchem/documentation/"))
          || result.containsKey(original) || !entry.path("missingAtCapture").isBoolean())
        throw new IllegalArgumentException("Invalid or duplicate original documentation URI");
      if (entry.path("missingAtCapture").asBoolean()) {
        if (entry.has("bundlePath") || entry.has("sha256") || entry.has("bytes"))
          throw new IllegalArgumentException("Missing documentation marker contains payload");
        result.put(original, new byte[0]); continue;
      }
      String hash = entry.path("sha256").asText();
      String basename = uri.getPath().substring(uri.getPath().lastIndexOf('/') + 1);
      if (!hash.matches("[0-9a-f]{64}") || !basename.startsWith(hash + "."))
        throw new IllegalArgumentException("Documentation hash differs from original content identity");
      Path relative = Path.of(entry.path("bundlePath").asText());
      Path file = state.resolve(relative).toAbsolutePath().normalize();
      if (relative.isAbsolute() || !file.startsWith(state.toAbsolutePath().normalize())
          || !Files.isRegularFile(file) || !file.toRealPath().startsWith(root))
        throw new IllegalArgumentException("Owned documentation escapes capture state or is missing");
      long length = Files.size(file);
      if (length == 0 || length > 4 * 1024 * 1024 || !entry.path("bytes").isIntegralNumber()
          || length != entry.path("bytes").asLong() || (total += length) > 64 * 1024 * 1024)
        throw new IllegalArgumentException("Invalid owned documentation size");
      byte[] contents = Files.readAllBytes(file);
      if (!HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(contents)).equals(hash))
        throw new IllegalArgumentException("Owned documentation checksum mismatch");
      result.put(original, contents);
    }
    return Collections.unmodifiableMap(result);
  }
}
