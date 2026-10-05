package org.integratedmodelling.klab.runtime.libraries;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLEncoder;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import org.integratedmodelling.klab.api.data.Metadata;
import org.integratedmodelling.klab.api.exceptions.KlabIOException;
import org.integratedmodelling.klab.api.exceptions.KlabIllegalArgumentException;
import org.integratedmodelling.klab.api.exceptions.KlabIllegalStateException;

/** Implementation shared by the core file and URL actors. No streams survive an operation. */
final class CoreIoSupport {
  private CoreIoSupport() {}

  @FunctionalInterface interface IoOperation<T> { T run() throws IOException; }

  static <T> T io(IoOperation<T> operation) {
    try { return operation.run(); }
    catch (IOException e) { throw new KlabIOException("File/URL operation failed", e); }
  }

  static <T> CompletableFuture<T> async(IoOperation<T> operation) {
    return CompletableFuture.supplyAsync(() -> io(operation), task -> Thread.startVirtualThread(task));
  }

  static Path path(String value) {
    if (value == null || value.isBlank()) throw new KlabIllegalArgumentException("A file path is required");
    try {
      return (value.startsWith("file:") ? Path.of(URI.create(value)) : Path.of(value))
          .toAbsolutePath().normalize();
    } catch (IllegalArgumentException e) {
      throw new KlabIllegalArgumentException("Invalid file path");
    }
  }

  static Path require(Path path) {
    if (path == null) throw new KlabIllegalStateException("Use file.new(path) first");
    return path;
  }

  static Path child(Path path, String relative) {
    if (relative == null) throw new KlabIllegalArgumentException("A relative path is required");
    try {
      var child = Path.of(relative);
      if (child.isAbsolute()) throw new IllegalArgumentException();
      return require(path).resolve(child).normalize();
    } catch (IllegalArgumentException e) {
      throw new KlabIllegalArgumentException("child requires a valid relative path");
    }
  }

  static URI uri(String value) {
    if (value == null || value.isBlank()) throw new KlabIllegalArgumentException("A URL is required");
    try {
      var uri = URI.create(value).normalize();
      if (!uri.isAbsolute() || uri.isOpaque() || uri.getUserInfo() != null || uri.getPort() > 65535
          || !Set.of("http", "https", "file").contains(uri.getScheme().toLowerCase(java.util.Locale.ROOT))
          || (!uri.getScheme().equalsIgnoreCase("file") && uri.getHost() == null)) {
        throw new IllegalArgumentException();
      }
      return uri;
    } catch (IllegalArgumentException e) {
      throw new KlabIllegalArgumentException("Expected an absolute HTTP, HTTPS or file URL without embedded credentials");
    }
  }

  static URI require(URI uri) {
    if (uri == null) throw new KlabIllegalStateException("Use url.new(address) first");
    return uri;
  }

  static URI resolve(URI base, String relative) {
    if (relative == null) throw new KlabIllegalArgumentException("A relative URL is required");
    try { return uri(base.resolve(relative).toString()); }
    catch (IllegalArgumentException e) { throw new KlabIllegalArgumentException("Invalid relative URL"); }
  }

  static Map<String, Object> fileInfo(Path path) {
    return io(() -> {
      var info = new LinkedHashMap<String, Object>();
      info.put("path", path.toString());
      info.put("name", path.getFileName() == null ? "" : path.getFileName().toString());
      info.put("exists", Files.exists(path));
      info.put("file", Files.isRegularFile(path));
      info.put("directory", Files.isDirectory(path));
      info.put("readable", Files.isReadable(path));
      info.put("writable", Files.isWritable(path));
      info.put("size", Files.isRegularFile(path) ? Files.size(path) : 0L);
      info.put("modified", Files.exists(path) ? Files.getLastModifiedTime(path).toMillis() : null);
      return java.util.Collections.unmodifiableMap(info);
    });
  }

  static List<String> list(Path directory) {
    return io(() -> {
      try (var entries = Files.list(directory)) {
        return entries.map(Path::toString).sorted().toList();
      }
    });
  }

  static String write(Path path, String text, boolean append) {
    if (text == null) throw new KlabIllegalArgumentException("Text must not be null");
    return io(() -> {
      Files.writeString(path, text, StandardCharsets.UTF_8, StandardOpenOption.CREATE,
          StandardOpenOption.WRITE, append ? StandardOpenOption.APPEND : StandardOpenOption.TRUNCATE_EXISTING);
      return path.toString();
    });
  }

  static String writeBytes(Path path, byte[] bytes) {
    if (bytes == null) throw new KlabIllegalArgumentException("Bytes must not be null");
    return io(() -> Files.write(path, bytes).toString());
  }

  static Map<String, Object> urlInfo(URI uri) {
    var info = new LinkedHashMap<String, Object>();
    info.put("address", uri.toString());
    info.put("scheme", uri.getScheme());
    info.put("host", uri.getHost());
    info.put("port", uri.getPort());
    info.put("path", uri.getPath());
    info.put("query", uri.getRawQuery());
    info.put("fragment", uri.getRawFragment());
    return java.util.Collections.unmodifiableMap(info);
  }

  static String encode(String text) {
    if (text == null) throw new KlabIllegalArgumentException("Text must not be null");
    return URLEncoder.encode(text, StandardCharsets.UTF_8);
  }

  static String decode(String text) {
    if (text == null) throw new KlabIllegalArgumentException("Text must not be null");
    try { return URLDecoder.decode(text, StandardCharsets.UTF_8); }
    catch (IllegalArgumentException e) { throw new KlabIllegalArgumentException("Invalid URL-encoded text"); }
  }

  record Response(int status, Map<String, List<String>> headers, byte[] bytes, String address) {
    Map<String, Object> asMap() {
      return Map.of("status", status, "headers", headers, "body", new String(bytes, StandardCharsets.UTF_8),
          "bytes", bytes, "address", address);
    }
  }

  private static int positiveOption(Metadata options, String name, int fallback) {
    var value = options.get(name);
    if (value == null && !options.containsKey(name)) return fallback;
    if (!(value instanceof Number number) || !Double.isFinite(number.doubleValue())
        || number.doubleValue() != number.intValue() || number.intValue() <= 0) {
      throw new KlabIllegalArgumentException(name + " must be a positive integer");
    }
    return number.intValue();
  }

  static Metadata options(Metadata supplied) {
    var snapshot = Metadata.create();
    if (supplied != null) snapshot.putAll(supplied);
    if (snapshot.get("headers") instanceof Map<?, ?> headers)
      snapshot.put("headers", new LinkedHashMap<>(headers));
    return snapshot;
  }

  static Response request(URI uri, String method, String body, Metadata suppliedOptions) throws IOException {
    var options = suppliedOptions == null ? Metadata.create() : suppliedOptions;
    for (var key : options.keySet()) {
      if (!Set.of("timeout", "maxbytes", "headers").contains(key))
        throw new KlabIllegalArgumentException("Unknown URL request option: " + key);
    }
    int timeout = positiveOption(options, "timeout", 10000);
    int maxbytes = positiveOption(options, "maxbytes", 16 * 1024 * 1024);
    if (method == null || !Set.of("GET", "HEAD", "POST", "PUT", "DELETE", "OPTIONS").contains(method))
      throw new KlabIllegalArgumentException("Unsupported HTTP method");
    if (("GET".equals(method) || "HEAD".equals(method)) && body != null)
      throw new KlabIllegalArgumentException("GET and HEAD do not accept a request body");
    if (uri.getScheme().equalsIgnoreCase("file") && (!method.equals("GET") || body != null || options.containsKey("headers")))
      throw new KlabIllegalArgumentException("File URLs support only GET without headers or a body");
    var connection = uri.toURL().openConnection();
    connection.setConnectTimeout(timeout);
    connection.setReadTimeout(timeout);
    connection.setUseCaches(false);
    var http = connection instanceof HttpURLConnection h ? h : null;
    try {
      if (http != null) {
        http.setInstanceFollowRedirects(false);
        http.setRequestMethod(method);
      }
      if (options.containsKey("headers")) {
        if (!(options.get("headers") instanceof Map<?, ?> headers))
          throw new KlabIllegalArgumentException("headers must be a map of strings");
        for (var entry : headers.entrySet()) {
          if (!(entry.getKey() instanceof String key) || !(entry.getValue() instanceof String value)
              || !key.matches("[!#$%&'*+.^_`|~0-9A-Za-z-]+") || value.contains("\r") || value.contains("\n"))
            throw new KlabIllegalArgumentException("Invalid HTTP header");
          connection.setRequestProperty(key, value);
        }
      }
      if (body != null) {
        connection.setDoOutput(true);
        var bytes = body.getBytes(StandardCharsets.UTF_8);
        if (http != null) http.setFixedLengthStreamingMode(bytes.length);
        try (var output = connection.getOutputStream()) { output.write(bytes); }
      }
      int status = http == null ? 200 : http.getResponseCode();
      var headers = new LinkedHashMap<String, List<String>>();
      connection.getHeaderFields().forEach((key, value) -> {
        if (key != null) headers.put(key, List.copyOf(value));
      });
      byte[] bytes;
      if (method.equals("HEAD")) bytes = new byte[0];
      else {
        try (var input = http != null && status >= 400 ? http.getErrorStream() : connection.getInputStream()) {
          bytes = input == null ? new byte[0] : readBounded(input, maxbytes);
        }
      }
      return new Response(status, java.util.Collections.unmodifiableMap(headers), bytes, uri.toString());
    } finally { if (http != null) http.disconnect(); }
  }

  private static byte[] readBounded(InputStream input, int maximum) throws IOException {
    var output = new java.io.ByteArrayOutputStream();
    var buffer = new byte[8192];
    for (int count; (count = input.read(buffer)) != -1; ) {
      if ((long) output.size() + count > maximum) throw new IOException("URL response exceeds maxbytes");
      output.write(buffer, 0, count);
    }
    return output.toByteArray();
  }

  static byte[] get(URI uri) throws IOException {
    var response = request(uri, "GET", null, null);
    if (response.status < 200 || response.status >= 300)
      throw new IOException("URL returned HTTP status " + response.status);
    return response.bytes;
  }

  static CoreActorLibrary.File download(URI uri, Path destination) throws IOException {
    var bytes = get(uri); // Complete and validate the response before changing the destination.
    Files.write(destination, bytes);
    return CoreActorLibrary.File.create(null, destination.toString());
  }
}
