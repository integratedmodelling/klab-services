package org.integratedmodelling.klab.runtime.libraries;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.sun.net.httpserver.HttpServer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.integratedmodelling.klab.api.data.Metadata;
import org.integratedmodelling.klab.api.exceptions.KlabIOException;
import org.integratedmodelling.klab.api.exceptions.KlabIllegalArgumentException;
import org.integratedmodelling.klab.api.services.runtime.extension.Actor;
import org.integratedmodelling.klab.api.services.runtime.extension.Extensions;
import org.integratedmodelling.klab.api.services.runtime.extension.Verb;
import org.integratedmodelling.klab.components.ComponentRegistry;
import org.integratedmodelling.klab.runtime.kactors.AgentScope;
import org.integratedmodelling.klab.runtime.kactors.RuntimeAgentBase;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CoreActorLibraryIoTest {
  @TempDir Path directory;

  @Test void staticAndInstanceFileOperationsShareUtf8AndByteSemantics() throws Exception {
    var path = directory.resolve("résumé.txt").toString();
    var file = CoreActorLibrary.File.create(null, path);
    assertFalse(file.present(null));
    assertEquals(path, CoreActorLibrary.File.write(null, path, "Héllo"));
    file.appendText(null, " world");
    assertEquals("Héllo world", CoreActorLibrary.File.read(null, path));
    assertEquals("Héllo world", file.text(null));
    assertArrayEquals("Héllo world".getBytes(StandardCharsets.UTF_8), file.bytes(null));
    file.save(null, "Replacement");
    assertEquals("Replacement", file.text(null));
    byte[] binary = {0, 1, -1, 2};
    CoreActorLibrary.File.writeBytes(null, path, binary);
    assertArrayEquals(binary, CoreActorLibrary.File.readBytes(null, path));
    file.saveBytes(null, new byte[] {7});
    assertEquals(1L, CoreActorLibrary.File.size(null, path));
    assertTrue(CoreActorLibrary.File.isFile(null, path));
    assertEquals(true, file.info(null).get("file"));
    assertEquals("résumé.txt", file.info(null).get("name"));
    assertEquals(path, file.path(null));
  }

  @Test void fileHandlesResolvePathsCreateDirectoriesAndListSortedEntries() {
    var root = CoreActorLibrary.File.create(null, directory.toString());
    var subdirectory = root.child(null, "one/two");
    subdirectory.makeDirectories(null);
    subdirectory.child(null, "b.txt").save(null, "b");
    subdirectory.child(null, "a.txt").save(null, "a");
    assertTrue(CoreActorLibrary.File.isDirectory(null, subdirectory.path(null)));
    assertEquals(List.of(subdirectory.child(null, "a.txt").path(null), subdirectory.child(null, "b.txt").path(null)),
        subdirectory.entries(null));
    assertEquals(CoreActorLibrary.File.list(null, subdirectory.path(null)), subdirectory.entries(null));
    assertEquals(directory.resolve("one").toString(), subdirectory.parent(null).path(null));
    assertEquals(directory.toString(), CoreActorLibrary.File.create(null, directory.toUri().toString()).path(null));
    assertThrows(KlabIllegalArgumentException.class, () -> root.child(null, directory.toString()));
  }

  @Test void copyMoveAndDeleteDoNotReplaceExistingTargetsOrDeleteRecursively() {
    var original = CoreActorLibrary.File.create(null, directory.resolve("source.txt").toString());
    original.save(null, "Source");
    var copied = original.copyTo(null, directory.resolve("copy.txt").toString());
    assertEquals("Source", copied.text(null));
    assertThrows(KlabIOException.class, () -> original.copyTo(null, copied.path(null)));
    var moved = copied.moveTo(null, directory.resolve("moved.txt").toString());
    assertFalse(copied.present(null));
    assertEquals("Source", moved.text(null));
    assertThrows(KlabIOException.class, () -> original.moveTo(null, moved.path(null)));
    assertThrows(KlabIOException.class, () -> CoreActorLibrary.File.delete(null, directory.toString()));
    assertTrue(moved.remove(null));
    assertFalse(moved.remove(null));
    assertThrows(KlabIOException.class, () -> moved.text(null));
    assertThrows(KlabIllegalArgumentException.class, () -> original.save(null, null));
  }

  @Test void temporaryFilesAreRealFilesAndRemainUntilExplicitlyRemoved() {
    var temporary = CoreActorLibrary.File.temp(null, "kactors-test-");
    try { assertTrue(temporary.present(null)); }
    finally { temporary.remove(null); }
    assertThrows(KlabIllegalArgumentException.class, () -> CoreActorLibrary.File.temp(null, "ab"));
  }

  @Test void urlInspectionResolutionAndEncodingPerformNoNetworkAccess() {
    var url = CoreActorLibrary.Url.create(null, "https://example.org/a/b?q=a%20b#part");
    assertEquals("example.org", url.info(null).get("host"));
    assertEquals("q=a%20b", url.info(null).get("query"));
    assertEquals("https://example.org/a/c", url.child(null, "c").address(null));
    assertEquals("https://example.org/root", CoreActorLibrary.Url.resolve(null, url.address(null), "/root").address(null));
    assertEquals(url.info(null), CoreActorLibrary.Url.inspect(null, url.address(null)));
    assertEquals("a+b%2F%C3%A9", CoreActorLibrary.Url.encode(null, "a b/é"));
    assertEquals("a b/é", CoreActorLibrary.Url.decode(null, "a+b%2F%C3%A9"));
    assertThrows(KlabIllegalArgumentException.class, () -> CoreActorLibrary.Url.create(null, "relative/path"));
    assertThrows(KlabIllegalArgumentException.class, () -> CoreActorLibrary.Url.create(null, "ftp://example.org/file"));
    assertThrows(KlabIllegalArgumentException.class, () -> CoreActorLibrary.Url.create(null, "https://user:secret@example.org/"));
    assertThrows(KlabIllegalArgumentException.class, () -> CoreActorLibrary.Url.decode(null, "%ZZ"));
  }

  @Test void fileUrlsReadAndDownloadThroughStaticAndBoundSuppliers() throws Exception {
    var source = Files.writeString(directory.resolve("source.txt"), "Héllo URL");
    var url = CoreActorLibrary.Url.create(null, source.toUri().toString());
    assertEquals("Héllo URL", url.text(null).get(5, TimeUnit.SECONDS));
    assertArrayEquals(Files.readAllBytes(source), CoreActorLibrary.Url.readBytes(null, url.address(null)).join());
    var downloaded = url.downloadTo(null, directory.resolve("downloaded.txt").toString()).join();
    assertEquals("Héllo URL", downloaded.text(null));
    assertEquals(200, url.fetch(null, "GET", null, null).join().get("status"));
  }

  @Test void httpGetPostHeadersAndErrorResponsesAreAccessible() throws Exception {
    var server = server();
    server.createContext("/echo", exchange -> {
      byte[] body = exchange.getRequestMethod().equals("POST")
          ? exchange.getRequestBody().readAllBytes() : "Héllo HTTP".getBytes(StandardCharsets.UTF_8);
      exchange.getResponseHeaders().set("X-Received", exchange.getRequestHeaders().getFirst("X-Test") == null
          ? "none" : exchange.getRequestHeaders().getFirst("X-Test"));
      exchange.sendResponseHeaders(200, body.length);
      try (var output = exchange.getResponseBody()) { output.write(body); }
    });
    server.createContext("/missing", exchange -> {
      var body = "Not found".getBytes(StandardCharsets.UTF_8);
      exchange.sendResponseHeaders(404, body.length);
      try (var output = exchange.getResponseBody()) { output.write(body); }
    });
    server.start();
    try {
      var url = CoreActorLibrary.Url.create(null, address(server, "/echo"));
      assertEquals("Héllo HTTP", url.text(null).join());
      var response = url.fetch(null, "POST", "Payload é", Metadata.create("headers", Map.of("X-Test", "actor"))).join();
      assertEquals(200, response.get("status"));
      assertEquals("Payload é", response.get("body"));
      assertArrayEquals("Payload é".getBytes(StandardCharsets.UTF_8), (byte[]) response.get("bytes"));
      assertTrue(((Map<?, ?>) response.get("headers")).values().contains(List.of("actor")));
      var failure = CoreActorLibrary.Url.request(null, address(server, "/missing"), "GET", null, null).join();
      assertEquals(404, failure.get("status"));
      assertEquals("Not found", failure.get("body"));
      assertInstanceOf(KlabIOException.class, assertThrows(CompletionException.class,
          () -> CoreActorLibrary.Url.read(null, address(server, "/missing")).join()).getCause());
    } finally { server.stop(0); }
  }

  @Test void httpRedirectsAreVisibleAndFailedDownloadsPreserveDestinations() throws Exception {
    var server = server();
    server.createContext("/redirect", exchange -> {
      exchange.getResponseHeaders().set("Location", "/other");
      exchange.sendResponseHeaders(302, -1);
      exchange.close();
    });
    server.start();
    try {
      var address = address(server, "/redirect");
      assertEquals(302, CoreActorLibrary.Url.request(null, address, "GET", null, null).join().get("status"));
      var destination = Files.writeString(directory.resolve("existing.txt"), "Keep me");
      assertThrows(CompletionException.class, () -> CoreActorLibrary.Url.download(null, address, destination.toString()).join());
      assertEquals("Keep me", Files.readString(destination));
    } finally { server.stop(0); }
  }

  @Test void responseLimitAndInvalidRequestOptionsFailSuppliers() throws Exception {
    var server = server();
    server.createContext("/body", exchange -> {
      var body = new byte[100];
      exchange.sendResponseHeaders(200, body.length);
      try (var output = exchange.getResponseBody()) { output.write(body); }
    });
    server.start();
    try {
      var url = CoreActorLibrary.Url.create(null, address(server, "/body"));
      assertInstanceOf(KlabIOException.class, assertThrows(CompletionException.class,
          () -> url.fetch(null, "GET", null, Metadata.create("maxbytes", 10)).join()).getCause());
      for (var options : List.of(Metadata.create("timeout", 0), Metadata.create("unknown", true),
          Metadata.create("headers", Map.of("X-Test", "bad\r\nInjected")))) {
        assertInstanceOf(KlabIllegalArgumentException.class, assertThrows(CompletionException.class,
            () -> url.fetch(null, "GET", null, options).join()).getCause());
      }
      assertThrows(CompletionException.class, () -> url.fetch(null, "GET", "body", null).join());
      assertThrows(CompletionException.class, () -> url.fetch(null, "PATCH", null, null).join());
    } finally { server.stop(0); }
  }

  @Test void urlSuppliersDoNotBlockAndEnforceReadTimeout() throws Exception {
    var entered = new CountDownLatch(1);
    var release = new CountDownLatch(1);
    var server = server();
    server.createContext("/wait", exchange -> {
      entered.countDown();
      try { release.await(5, TimeUnit.SECONDS); }
      catch (InterruptedException e) { Thread.currentThread().interrupt(); }
      exchange.sendResponseHeaders(200, -1);
      exchange.close();
    });
    server.start();
    try {
      var future = CoreActorLibrary.Url.request(null, address(server, "/wait"), "GET", null, Metadata.create("timeout", 1500));
      assertTrue(entered.await(5, TimeUnit.SECONDS));
      assertFalse(future.isDone());
      assertInstanceOf(KlabIOException.class, assertThrows(java.util.concurrent.ExecutionException.class,
          () -> future.get(5, TimeUnit.SECONDS)).getCause());
    } finally { release.countDown(); server.stop(0); }
  }

  @Test void catalogHasUniqueVerbsAndTypedStaticFactories() throws Exception {
    var registry = mock(ComponentRegistry.class, CALLS_REAL_METHODS);
    var instances = ComponentRegistry.class.getDeclaredField("globalInstances");
    instances.setAccessible(true);
    var actorInstances = new HashMap<Class<?>, Object>();
    actorInstances.put(CoreActorLibrary.File.class, new CoreActorLibrary.File());
    actorInstances.put(CoreActorLibrary.Url.class, new CoreActorLibrary.Url());
    instances.set(registry, actorInstances);
    var discover = ComponentRegistry.class.getDeclaredMethod("createActorDescriptor", Actor.class, String.class, Class.class);
    discover.setAccessible(true);
    for (var actor : List.of(CoreActorLibrary.File.class, CoreActorLibrary.Url.class)) {
      var descriptor = (Extensions.ActorDescriptor) discover.invoke(registry, actor.getAnnotation(Actor.class), "core.", actor);
      var names = new HashSet<String>();
      for (var verb : descriptor.verbs) {
        assertTrue(names.add(verb.serviceInfo.getName()));
        assertSame(actor, registry.implementation(verb).implementation);
        if (verb.serviceInfo.getName().endsWith(".new")) {
          assertTrue(verb.staticMethod);
          assertEquals(descriptor.urn, verb.behaviorUrn);
        }
      }
      assertTrue(names.contains(descriptor.urn + ".new"));
      for (var method : actor.getDeclaredMethods()) {
        var verb = method.getAnnotation(Verb.class);
        if (verb != null && CompletableFuture.class.isAssignableFrom(method.getReturnType()))
          assertEquals(Verb.Type.SUPPLIER, verb.executionType());
      }
    }
  }

  @Test void kActorsDynamicDispatchConstructsAndInvokesFileAndUrlHandles() throws Exception {
    var runtime = mock(RuntimeAgentBase.class, CALLS_REAL_METHODS);
    var invoke = RuntimeAgentBase.class.getDeclaredMethod("invokeActorDynamically", Object.class, String.class, AgentScope.class, Object[].class);
    invoke.setAccessible(true);
    var path = directory.resolve("dynamic.txt").toString();
    var file = value(invoke.invoke(runtime, CoreActorLibrary.File.class, "new", null, new Object[] {path}));
    value(invoke.invoke(runtime, file, "save", null, new Object[] {"Through k.Actors"}));
    assertEquals("Through k.Actors", value(invoke.invoke(runtime, CoreActorLibrary.File.class, "read", null, new Object[] {path})));
    assertEquals("Through k.Actors", value(invoke.invoke(runtime, file, "text", null, new Object[0])));
    var url = value(invoke.invoke(runtime, CoreActorLibrary.Url.class, "new", null,
        new Object[] {Path.of(path).toUri().toString()}));
    var text = (CompletableFuture<?>) value(invoke.invoke(runtime, url, "text", null, new Object[0]));
    assertEquals("Through k.Actors", text.join());
  }

  private Object value(Object invocation) throws Exception {
    var accessor = invocation.getClass().getDeclaredMethod("value");
    accessor.setAccessible(true);
    return accessor.invoke(invocation);
  }

  private HttpServer server() throws Exception {
    return HttpServer.create(new java.net.InetSocketAddress("127.0.0.1", 0), 0);
  }

  private String address(HttpServer server, String path) {
    return "http://127.0.0.1:" + server.getAddress().getPort() + path;
  }
}
