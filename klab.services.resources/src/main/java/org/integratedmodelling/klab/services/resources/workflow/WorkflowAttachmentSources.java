package org.integratedmodelling.klab.services.resources.workflow;

import java.io.*;
import java.net.*;
import java.net.http.*;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;

/** Bounded attachment input for trusted workflow code; local files require configured roots. */
final class WorkflowAttachmentSources {
  static final String FILE_ROOTS = "klab.workflow.attachment.roots";
  static final String URL_HOSTS = "klab.workflow.attachment.hosts";
  private static final HttpClient CLIENT = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10))
      .followRedirects(HttpClient.Redirect.NEVER).build();
  private WorkflowAttachmentSources() {}

  static byte[] file(String source, int limit) {
    try {
      Path path = Path.of(source).toRealPath();
      boolean allowed = false;
      for (String root : System.getProperty(FILE_ROOTS, Path.of(System.getProperty("java.io.tmpdir"), "klab-workflow-attachments").toString()).split(java.util.regex.Pattern.quote(File.pathSeparator))) {
        if (!root.isBlank() && Files.isDirectory(Path.of(root)) && path.startsWith(Path.of(root).toRealPath())) { allowed = true; break; }
      }
      if (!allowed || !Files.isRegularFile(path))
        throw new IllegalArgumentException("Attachment file is outside the configured workflow roots");
      if (Files.size(path) > limit) throw new IllegalArgumentException("Attachment exceeds byte limit");
      try (var input = Files.newInputStream(path)) { return read(input, limit); }
    } catch (IOException failure) { throw new IllegalArgumentException("Cannot read attachment file", failure); }
  }

  static byte[] url(String source, int limit) {
    URI uri = URI.create(source);
    validateUrl(uri);
    var request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(30)).GET().build();
    var download = CLIENT.sendAsync(request, info -> new LimitedBodySubscriber(limit));
    try {
      // Bound both allocation and the entire response body download, including slow streams.
      var response = download.get(30, java.util.concurrent.TimeUnit.SECONDS);
      if (response.statusCode() < 200 || response.statusCode() >= 300)
        throw new IllegalArgumentException("Attachment URL returned HTTP " + response.statusCode());
      return response.body();
    } catch (InterruptedException failure) {
      Thread.currentThread().interrupt(); throw new IllegalStateException("Attachment download interrupted", failure);
    } catch (java.util.concurrent.ExecutionException | java.util.concurrent.TimeoutException failure) {
      throw new IllegalArgumentException("Cannot download attachment URL within its limits", failure);
    } finally { if (!download.isDone()) download.cancel(true); }
  }

  static void validateUrl(URI uri) {
    if (!("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme()))
        || uri.getHost() == null || uri.getUserInfo() != null || uri.getFragment() != null)
      throw new IllegalArgumentException("Attachment URLs must be HTTP(S), without credentials or fragments");
    var hosts = Arrays.stream(System.getProperty(URL_HOSTS, "").split(",")).map(String::trim)
        .filter(host -> !host.isEmpty()).toList();
    boolean allowedHost = hosts.stream().anyMatch(host -> host.equalsIgnoreCase(uri.getHost()));
    if (!hosts.isEmpty() && !allowedHost)
      throw new IllegalArgumentException("Attachment URL host is not allowed");
    if (allowedHost) return; // Explicit host grants may include an internal attachment service.
    try {
      for (var address : InetAddress.getAllByName(uri.getHost()))
        if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()
            || address.isSiteLocalAddress() || address.isMulticastAddress()
            || reservedAddress(address)
            || (address instanceof Inet6Address && (address.getAddress()[0] & 0xfe) == 0xfc))
          throw new IllegalArgumentException("Internal attachment URL hosts require an explicit host grant");
    } catch (UnknownHostException failure) { throw new IllegalArgumentException("Cannot resolve attachment host", failure); }
  }

  private static boolean reservedAddress(InetAddress address) {
    if (!(address instanceof Inet4Address)) return false;
    byte[] bytes = address.getAddress(); int first = bytes[0] & 255, second = bytes[1] & 255;
    return first == 0 || first >= 224 || (first == 100 && second >= 64 && second <= 127)
        || (first == 198 && (second == 18 || second == 19)) || (first == 192 && second == 0);
  }

  static byte[] read(InputStream input, int limit) throws IOException {
    byte[] bytes = input.readNBytes(limit + 1);
    if (bytes.length > limit) throw new IllegalArgumentException("Attachment exceeds byte limit");
    return bytes;
  }

  /** Stop the publisher immediately when the configured bound is crossed. */
  private static final class LimitedBodySubscriber implements HttpResponse.BodySubscriber<byte[]> {
    private final int limit;
    private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
    private final java.util.concurrent.CompletableFuture<byte[]> result = new java.util.concurrent.CompletableFuture<>();
    private java.util.concurrent.Flow.Subscription subscription;
    LimitedBodySubscriber(int limit) { this.limit = limit; }
    public java.util.concurrent.CompletionStage<byte[]> getBody() { return result; }
    public void onSubscribe(java.util.concurrent.Flow.Subscription subscription) {
      this.subscription = subscription; subscription.request(1);
    }
    public void onNext(List<java.nio.ByteBuffer> buffers) {
      for (var buffer : buffers) {
        if ((long) bytes.size() + buffer.remaining() > limit) {
          subscription.cancel(); result.completeExceptionally(new IllegalArgumentException("Attachment exceeds byte limit")); return;
        }
        byte[] chunk = new byte[buffer.remaining()]; buffer.get(chunk); bytes.writeBytes(chunk);
      }
      subscription.request(1);
    }
    public void onError(Throwable failure) { result.completeExceptionally(failure); }
    public void onComplete() { result.complete(bytes.toByteArray()); }
  }
}
