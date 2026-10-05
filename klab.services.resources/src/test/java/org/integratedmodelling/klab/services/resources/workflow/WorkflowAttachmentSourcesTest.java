package org.integratedmodelling.klab.services.resources.workflow;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.net.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WorkflowAttachmentSourcesTest {
  @TempDir Path directory;

  @Test void filesRequireAllowedRootsAndRespectByteLimits() throws Exception {
    String previous = System.getProperty(WorkflowAttachmentSources.FILE_ROOTS);
    try {
      Path allowed = Files.createDirectory(directory.resolve("allowed"));
      Path file = Files.writeString(allowed.resolve("note.txt"), "hello");
      Path outside = Files.writeString(directory.resolve("private.txt"), "private");
      System.setProperty(WorkflowAttachmentSources.FILE_ROOTS, allowed.toString());
      assertArrayEquals("hello".getBytes(), WorkflowAttachmentSources.file(file.toString(), 5));
      assertThrows(IllegalArgumentException.class, () -> WorkflowAttachmentSources.file(file.toString(), 4));
      assertThrows(IllegalArgumentException.class, () -> WorkflowAttachmentSources.file(outside.toString(), 100));
    } finally { restore(WorkflowAttachmentSources.FILE_ROOTS, previous); }
  }

  @Test void urlsRequireExplicitInternalHostGrantsAndRejectRedirectsAndOversizedBodies() throws Exception {
    String previous = System.getProperty(WorkflowAttachmentSources.URL_HOSTS);
    var server = com.sun.net.httpserver.HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext("/file", exchange -> {
      byte[] bytes = "hello".getBytes(); exchange.sendResponseHeaders(200, bytes.length);
      try (var output = exchange.getResponseBody()) { output.write(bytes); }
    });
    server.createContext("/redirect", exchange -> {
      exchange.getResponseHeaders().add("Location", "/file");
      exchange.sendResponseHeaders(302, -1); exchange.close();
    });
    server.start();
    try {
      String base = "http://127.0.0.1:" + server.getAddress().getPort();
      System.clearProperty(WorkflowAttachmentSources.URL_HOSTS);
      assertThrows(IllegalArgumentException.class, () -> WorkflowAttachmentSources.url(base + "/file", 5));
      System.setProperty(WorkflowAttachmentSources.URL_HOSTS, "127.0.0.1");
      assertArrayEquals("hello".getBytes(), WorkflowAttachmentSources.url(base + "/file", 5));
      assertThrows(IllegalArgumentException.class, () -> WorkflowAttachmentSources.url(base + "/file", 4));
      assertThrows(IllegalArgumentException.class, () -> WorkflowAttachmentSources.url(base + "/redirect", 5));
      assertThrows(IllegalArgumentException.class, () -> WorkflowAttachmentSources.validateUrl(URI.create("file:///etc/passwd")));
      assertThrows(IllegalArgumentException.class, () -> WorkflowAttachmentSources.validateUrl(URI.create("http://user:pass@127.0.0.1/file")));
      assertThrows(IllegalArgumentException.class, () -> WorkflowAttachmentSources.validateUrl(URI.create("https://example.org/file")));
    } finally { server.stop(0); restore(WorkflowAttachmentSources.URL_HOSTS, previous); }
  }

  private static void restore(String key, String value) {
    if (value == null) System.clearProperty(key); else System.setProperty(key, value);
  }
}
