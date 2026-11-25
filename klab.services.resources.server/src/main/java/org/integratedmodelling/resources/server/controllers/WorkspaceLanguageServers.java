package org.integratedmodelling.resources.server.controllers;

import jakarta.annotation.PreDestroy;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.zip.ZipInputStream;
import org.eclipse.lsp4j.*;
import org.eclipse.lsp4j.jsonrpc.Launcher;
import org.eclipse.lsp4j.services.LanguageClient;
import org.eclipse.lsp4j.services.LanguageServer;
import org.integratedmodelling.klab.api.lang.KlabLanguage;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/** Local stdio sessions for the same Xtext runtime launched by the desktop IDE. */
@Component
public class WorkspaceLanguageServers {

  private static final int MAX_TEXT = 2_000_000;
  private static final Duration IDLE_TIMEOUT = Duration.ofMinutes(5);

  public record Owner(String identity, String workspace, String project, String document) {}
  public record Snapshot(int version, String text) {}
  public record Problem(String level, String message, int line, int column, int endLine, int endColumn) {}
  public record Problems(int version, List<Problem> diagnostics) {}
  public record Suggestion(String label, String detail, String text, boolean snippet, Range range) {}

  private final ConcurrentHashMap<UUID, Session> sessions = new ConcurrentHashMap<>();
  private final Semaphore permits = new Semaphore(8);
  private final java.util.concurrent.ScheduledExecutorService cleanup =
      Executors.newSingleThreadScheduledExecutor(Thread.ofVirtual().name("workspace-lsp-expiry").factory());
  private Path libraries;
  private boolean closed;

  public WorkspaceLanguageServers() {
    cleanup.scheduleWithFixedDelay(this::expire, 30, 30, TimeUnit.SECONDS);
  }

  public UUID open(Owner owner, KlabLanguage language, Snapshot snapshot) {
    checkText(snapshot);
    if (!permits.tryAcquire())
      throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Language server capacity reached");
    Session session = null;
    try {
      var lib = libraries();
      synchronized (this) {
        if (closed) throw new IllegalStateException("Resources is stopping");
        session = new Session(owner, language, lib);
        sessions.put(session.id, session);
      }
      session.initialize();
      session.sync(snapshot);
      return session.id;
    } catch (InterruptedException failure) {
      Thread.currentThread().interrupt();
      failedStart(session, failure);
    } catch (Exception failure) {
      failedStart(session, failure);
    }
    throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Language server could not start");
  }

  private void failedStart(Session session, Exception failure) {
    if (session == null) permits.release();
    else remove(session);
    LoggerFactory.getLogger(WorkspaceLanguageServers.class).warn("Workspace language server startup failed", failure);
  }

  public void sync(Owner owner, UUID id, Snapshot snapshot) {
    checkText(snapshot);
    session(owner, id).sync(snapshot);
  }

  public Problems problems(Owner owner, UUID id) {
    return session(owner, id).problems;
  }

  public List<Suggestion> complete(Owner owner, UUID id, int version, int line, int column) {
    if (line < 0 || column < 0) throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
    var request = session(owner, id).complete(version, line, column);
    try {
      var result = request.get(5, TimeUnit.SECONDS);
      if (result == null) return List.of();
      var items = result.isLeft() ? result.getLeft() : result.getRight().getItems();
      return items.stream().map(item -> {
        var edit = item.getTextEdit();
        var range = edit == null ? null : edit.isLeft() ? edit.getLeft().getRange() : edit.getRight().getReplace();
        var text = edit == null ? item.getInsertText() : edit.isLeft()
            ? edit.getLeft().getNewText() : edit.getRight().getNewText();
        return new Suggestion(item.getLabel(), item.getDetail(), text,
            item.getInsertTextFormat() == InsertTextFormat.Snippet, range);
      }).toList();
    } catch (InterruptedException failure) {
      Thread.currentThread().interrupt();
      request.cancel(true);
    } catch (java.util.concurrent.TimeoutException | java.util.concurrent.ExecutionException failure) {
      request.cancel(true);
    }
    throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Language completion unavailable");
  }

  public void close(Owner owner, UUID id) {
    var session = sessions.get(id);
    if (session != null && session.owner.equals(owner)) remove(session);
  }

  private Session session(Owner owner, UUID id) {
    var session = sessions.get(id);
    if (session == null || !session.owner.equals(owner))
      throw new ResponseStatusException(HttpStatus.NOT_FOUND);
    if (!session.process.isAlive()) {
      remove(session);
      throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Language server stopped");
    }
    session.lastUsed = System.nanoTime();
    return session;
  }

  private static void checkText(Snapshot snapshot) {
    if (snapshot.version() < 1 || snapshot.text() == null)
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
    if (snapshot.text().length() > MAX_TEXT)
      throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE);
  }

  private void expire() {
    long now = System.nanoTime();
    sessions.values().forEach(session -> {
      if (!session.process.isAlive() || now - session.lastUsed > IDLE_TIMEOUT.toNanos()) remove(session);
    });
  }

  private void remove(Session session) {
    if (sessions.remove(session.id, session)) {
      try { session.close(); }
      finally { permits.release(); }
    }
  }

  private synchronized Path libraries() throws IOException {
    if (closed) throw new IOException("Resources is stopping");
    if (libraries != null) return libraries;
    var directory = Files.createTempDirectory("klab-workspace-lsp-");
    try (var resource = getClass().getResourceAsStream("/klab-lsp/editor-runtime.zip")) {
      if (resource == null) throw new IOException("Missing bundled workspace language server");
      try (var zip = new ZipInputStream(resource)) {
        for (var entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
          if (entry.isDirectory()) continue;
          if (!entry.getName().matches("lib/[^/\\\\]+\\.jar")) throw new IOException("Invalid language runtime entry");
          Files.copy(zip, directory.resolve(entry.getName().substring(4)));
        }
      }
      libraries = directory;
      return directory;
    } finally {
      if (libraries == null) deleteLibraries(directory);
    }
  }

  @PreDestroy
  public synchronized void shutdown() {
    closed = true;
    cleanup.shutdownNow();
    sessions.values().forEach(this::remove);
    if (libraries != null) deleteLibraries(libraries);
  }

  private static void deleteLibraries(Path directory) {
    try (var files = Files.newDirectoryStream(directory)) {
      for (var file : files) Files.deleteIfExists(file);
    } catch (IOException ignored) {
      // The JVM may still be releasing a child process's files.
    }
    deleteDirectory(directory);
  }

  private static void deleteDirectory(Path path) {
    try { Files.deleteIfExists(path); } catch (IOException ignored) { }
  }

  private static final class Session implements LanguageClient {
    private final UUID id = UUID.randomUUID();
    private final Owner owner;
    private final KlabLanguage language;
    private final Path root;
    private final Process process;
    private final ExecutorService messages = Executors.newCachedThreadPool(Thread.ofVirtual().factory());
    private LanguageServer server;
    private volatile long lastUsed = System.nanoTime();
    private volatile Problems problems;
    private int version;
    private String uri;

    Session(Owner owner, KlabLanguage language, Path libraries) throws IOException {
      this.owner = owner;
      this.language = language;
      root = Files.createTempDirectory("klab-editor-");
      try {
        var java = ProcessHandle.current().info().command().orElseGet(
            () -> Path.of(System.getProperty("java.home"), "bin", "java").toString());
        process = new ProcessBuilder(java, "-Xmx256m", "-Dfile.encoding=UTF-8",
            "-Dxtext.disable.standalone.setup=true", "-cp", libraries + File.separator + "*",
            "org.eclipse.xtext.ide.server.ServerLauncher")
            .directory(root.toFile()).redirectError(ProcessBuilder.Redirect.DISCARD).start();
      } catch (IOException failure) {
        deleteDirectory(root);
        throw failure;
      }
    }

    void initialize() throws Exception {
      var launcher = Launcher.createLauncher(this, LanguageServer.class, process.getInputStream(),
          process.getOutputStream(), messages, Function.identity());
      server = launcher.getRemoteProxy();
      launcher.startListening();
      var params = new InitializeParams();
      params.setCapabilities(new ClientCapabilities());
      params.setRootUri(root.toUri().toString());
      server.initialize(params).get(30, TimeUnit.SECONDS);
      server.initialized(new InitializedParams());
    }

    synchronized void sync(Snapshot snapshot) {
      if (snapshot.version() <= version) return;
      var previous = uri;
      version = snapshot.version();
      // Xtext emits unversioned diagnostics. A new URI per snapshot lets the client reject
      // delayed results without guessing which buffer produced them.
      uri = root.resolve("source-" + version + "." + language.fileExtension()).toUri().toString();
      problems = null;
      var documents = server.getTextDocumentService();
      if (previous != null) documents.didClose(new DidCloseTextDocumentParams(new TextDocumentIdentifier(previous)));
      documents.didOpen(new DidOpenTextDocumentParams(new TextDocumentItem(uri, language.languageId(), version, snapshot.text())));
    }

    synchronized CompletableFuture<org.eclipse.lsp4j.jsonrpc.messages.Either<List<CompletionItem>, CompletionList>>
        complete(int requested, int line, int column) {
      if (requested != version) throw new ResponseStatusException(HttpStatus.CONFLICT);
      return server.getTextDocumentService().completion(
          new CompletionParams(new TextDocumentIdentifier(uri), new Position(line, column)));
    }

    @Override
    public synchronized void publishDiagnostics(PublishDiagnosticsParams params) {
      if (!uriMatches(params.getUri())) return;
      problems = new Problems(version, params.getDiagnostics().stream().map(item -> {
        var start = item.getRange().getStart();
        var end = item.getRange().getEnd();
        return new Problem(item.getSeverity() == DiagnosticSeverity.Error ? "Error"
            : item.getSeverity() == DiagnosticSeverity.Warning ? "Warning" : "Info",
            item.getMessage(), start.getLine(), start.getCharacter(), end.getLine(), end.getCharacter());
      }).toList());
    }

    private boolean uriMatches(String candidate) {
      if (uri == null) return false;
      try { return Path.of(java.net.URI.create(uri)).equals(Path.of(java.net.URI.create(candidate))); }
      catch (IllegalArgumentException failure) { return false; }
    }

    @Override public void telemetryEvent(Object event) {}
    @Override public void showMessage(MessageParams message) {}
    @Override public void logMessage(MessageParams message) {}
    @Override public CompletableFuture<MessageActionItem> showMessageRequest(ShowMessageRequestParams message) {
      return CompletableFuture.completedFuture(null);
    }

    void close() {
      process.destroyForcibly();
      try { process.waitFor(2, TimeUnit.SECONDS); }
      catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
      messages.shutdownNow();
      deleteDirectory(root);
    }
  }
}
