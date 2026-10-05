package org.integratedmodelling.klab.runtime.libraries;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import org.integratedmodelling.common.logging.Logging;
import org.integratedmodelling.klab.api.actors.RuntimeAgent;
import org.integratedmodelling.klab.api.authentication.ResourcePrivileges;
import org.integratedmodelling.klab.api.collections.Constant;
import org.integratedmodelling.klab.api.data.Metadata;
import org.integratedmodelling.klab.api.data.RuntimeAsset;
import org.integratedmodelling.klab.api.digitaltwin.DigitalTwin;
import org.integratedmodelling.klab.api.exceptions.KlabIllegalArgumentException;
import org.integratedmodelling.klab.api.exceptions.KlabIllegalStateException;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.api.knowledge.Urn;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.knowledge.observation.scale.time.TimeDuration;
import org.integratedmodelling.klab.api.knowledge.observation.scale.time.TimeInstant;
import org.integratedmodelling.klab.api.lang.Quantity;
import org.integratedmodelling.klab.api.lang.kim.KimConcept;
import org.integratedmodelling.klab.api.lang.kim.KimObservable;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.scope.Persistence;
import org.integratedmodelling.klab.api.scope.SessionScope;
import org.integratedmodelling.klab.api.services.Reasoner;
import org.integratedmodelling.klab.api.services.RuntimeService;
import org.integratedmodelling.klab.api.services.resolver.ResolutionConstraint;
import org.integratedmodelling.klab.api.services.runtime.extension.Actor;
import org.integratedmodelling.klab.api.services.runtime.extension.Library;
import org.integratedmodelling.klab.api.services.runtime.extension.Verb;
import org.integratedmodelling.klab.api.utils.Utils;
import org.integratedmodelling.klab.runtime.kactors.AgentScope;
import org.integratedmodelling.klab.runtime.kactors.RuntimeAgentBase;
import org.integratedmodelling.klab.runtime.kactors.TestCaseBase;
import org.integratedmodelling.klab.services.base.BaseService;
import org.integratedmodelling.klab.services.base.EmailManager;
import org.integratedmodelling.klab.services.scopes.ServiceUserScope;

@Library(name = "core")
public class CoreActorLibrary {

  /**
   * Universal Java behavior inherited implicitly by every k.Actors behavior.
   *
   * <p>The compiler binds an instance of this class to the recipient of a self/agent call. This
   * keeps the base contract in the ordinary Java actor catalog while ensuring that messages are
   * sent by the calling runtime agent rather than by a detached serialized handle.
   *
   * <p>Because the validator runs before compilation, any new verb should be declared in
   * RuntimeAgent.java explicitly for them to be recognized. TODO we should use the agent descriptor
   * or reflection once to retrieve them.
   */
  @Actor(
      name = "agent",
      description =
          "The universal agent contract. Every k.Actors behavior implicitly inherits these verbs.")
  public static final class Agent {

    private final Object target;

    public Agent(Object target) {
      this.target = target;
    }

    @Verb(
        name = "new",
        executionType = Verb.Type.FUNCTION,
        description =
            "Construction contract implemented by behavior and Java actor specifications.")
    public Object newAgent(RuntimeAgent.Scope scope, Object... arguments) {
      throw new IllegalStateException(
          "The core.agent new verb requires a behavior or Java actor specification");
    }

    @Verb(
        name = "tell",
        executionType = Verb.Type.FUNCTION,
        returns = Void.class,
        description = "Send one custom message to this agent.")
    public void tell(
        RuntimeAgent.Scope scope,
        @Verb.Argument(name = "class", description = "Custom message class", constant = true)
            Constant messageClass,
        @Verb.Argument(name = "payload", description = "Serializable message payload")
            Object payload) {
      runtime(scope).tellAgentValue(target, messageClass, payload);
    }

    @Verb(name = "duration", executionType = Verb.Type.FUNCTION, returns = TimeDuration.class,
        description = "Convert a temporal quantity into a `TimeDuration` for scheduling or request timeouts.")
    public static TimeDuration duration(Quantity time) {
      Objects.requireNonNull(time, "quantity");
      return TimeDuration.of(time);
    }

    @Verb(
        name = "ask",
        executionType = Verb.Type.SUPPLIER,
        description = "Send a correlated custom message and supply its response.")
    public CompletableFuture<Object> ask(
        RuntimeAgent.Scope scope,
        @Verb.Argument(name = "class", description = "Custom message class", constant = true)
            Constant messageClass,
        @Verb.Argument(name = "payload", description = "Serializable request payload")
            Object payload,
        @Verb.Argument(
                name = "timeout",
                description = "Temporal timeout, null for the runtime default, or false to disable",
                optional = true)
            Object timeout) {
      return runtime(scope).askAgentValue(target, messageClass, payload, timeout);
    }

    @Verb(
        name = "name",
        executionType = Verb.Type.FUNCTION,
        returns = String.class,
        description = "Return this agent's non-unique display name.")
    public String name(RuntimeAgent.Scope scope) {
      return runtime(scope).agentName(target);
    }

    @Verb(
        name = "urn",
        executionType = Verb.Type.FUNCTION,
        returns = String.class,
        description = "Return this agent's runtime-wide unique URN.")
    public String urn(RuntimeAgent.Scope scope) {
      return runtime(scope).agentUrn(target);
    }

    private RuntimeAgentBase runtime(RuntimeAgent.Scope scope) {
      if (scope == null || !(scope.getAgent() instanceof RuntimeAgentBase runtime)) {
        throw new IllegalStateException("core.agent requires a generated runtime agent scope");
      }
      return runtime;
    }
  }

  /**
   * Static actor class methods map to static actors. They must be declared (although these core
   * ones may be automatically linked, TBD). It should be illegal to use a constructor if there are
   * only static methods annotated with @Verb.
   */
  @Actor(
      name = "console",
      description =
          "A static actor that prints to whatever console was configured for the agent. All methods are static and can be called directly without instantiating the actor.")
  public static class Console {

    @Verb(name = "println", executionType = Verb.Type.FUNCTION, returns = Void.class,
        description = "Print the rendered messages to the configured standard-output console, followed by a line separator.")
    public static void println(RuntimeAgent.Scope scope, Object... messages) {
      write(
          scope, RuntimeAgent.ConsoleMessageType.STDOUT, render(messages) + System.lineSeparator());
    }

    @Verb(name = "print", executionType = Verb.Type.FUNCTION, returns = Void.class,
        description = "Print the rendered messages to the configured standard-output console without adding a line separator.")
    public static void print(RuntimeAgent.Scope scope, Object... messages) {
      write(scope, RuntimeAgent.ConsoleMessageType.STDOUT, render(messages));
    }

    @Verb(name = "format", executionType = Verb.Type.FUNCTION, returns = Void.class,
        description = "Print text formatted with Java `String.format` to the standard-output console without adding a line separator.")
    public static void format(RuntimeAgent.Scope scope, String format, Object... args) {
      write(scope, RuntimeAgent.ConsoleMessageType.STDOUT, String.format(format, args));
    }

    @Verb(name = "printf", executionType = Verb.Type.FUNCTION, returns = Void.class,
        description = "Alias for `format`: print Java-formatted text to the standard-output console without adding a line separator.")
    public static void printf(RuntimeAgent.Scope scope, String format, Object... args) {
      format(scope, format, args);
    }

    @Verb(name = "error", executionType = Verb.Type.FUNCTION, returns = Void.class,
        description = "Print the rendered messages to the configured standard-error console without adding a line separator.")
    public static void error(RuntimeAgent.Scope scope, Object... messages) {
      write(scope, RuntimeAgent.ConsoleMessageType.STDERR, render(messages));
    }

    @Verb(name = "errorln", executionType = Verb.Type.FUNCTION, returns = Void.class,
        description = "Print the rendered messages to the configured standard-error console, followed by a line separator.")
    public static void errorln(RuntimeAgent.Scope scope, Object... messages) {
      write(
          scope, RuntimeAgent.ConsoleMessageType.STDERR, render(messages) + System.lineSeparator());
    }

    @Verb(name = "errorf", executionType = Verb.Type.FUNCTION, returns = Void.class,
        description = "Print text formatted with Java `String.format` to the standard-error console without adding a line separator.")
    public static void errorf(RuntimeAgent.Scope scope, String format, Object... args) {
      write(scope, RuntimeAgent.ConsoleMessageType.STDERR, String.format(format, args));
    }

    @Verb(name = "flush", executionType = Verb.Type.FUNCTION, returns = Void.class,
        description = "Flush the configured agent console.")
    public static void flush(RuntimeAgent.Scope scope) {
      scope.getPrintWriter().flush();
    }

    private static String render(Object... messages) {
      if (messages == null || messages.length == 0) {
        return "";
      }
      var builder = new StringBuilder();
      for (var message : messages) {
        builder.append(String.valueOf(message));
      }
      return builder.toString();
    }

    private static void write(
        RuntimeAgent.Scope scope, RuntimeAgent.ConsoleMessageType stream, String text) {
      boolean delivered =
          scope.getAgent() instanceof RuntimeAgentBase runtime
              ? runtime.sendToConsole(scope, stream, text)
              : scope.getAgent().sendToConsole(stream, text);
      if (!delivered) {
        if (stream == RuntimeAgent.ConsoleMessageType.STDERR) {
          System.err.print(text);
          System.err.flush();
        } else {
          scope.getPrintWriter().print(text);
          scope.getPrintWriter().flush();
        }
      }
    }
  }

  /** Local filesystem operations and immutable path handles; see docs/AGENTS_REFERENCE.md. */
  @Actor(name = "file", description = "Local file operations and bound file paths")
  public static class File {
    private final java.nio.file.Path path;

    /** Catalog instance; access verbs require a handle returned by new. */
    public File() { this.path = null; }
    private File(java.nio.file.Path path) { this.path = path; }

    @Verb(name = "new", executionType = Verb.Type.FUNCTION, returns = File.class, producesAgent = "core.file",
        description = "Create a `core.file` handle for a normalized absolute local path or `file:` URI. Relative paths use the host working directory; this does not create a file.")
    public static File create(RuntimeAgent.Scope scope,
        @Verb.Argument(name = "path", description = "Local path or file: URI; relative paths use the host working directory") String path) {
      return new File(CoreIoSupport.path(path));
    }

    @Verb(name = "inspect", executionType = Verb.Type.FUNCTION, returns = Map.class,
        description = "Return local path metadata: `path`, `name`, `exists`, `file`, `directory`, `readable`, `writable`, `size`, and `modified` (epoch milliseconds, or null if absent).")
    public static Map<String, Object> inspect(RuntimeAgent.Scope scope,
        @Verb.Argument(name = "path", description = "Local path or file: URI; relative paths use the host working directory") String path) {
      return CoreIoSupport.fileInfo(CoreIoSupport.path(path));
    }

    @Verb(name = "exists", executionType = Verb.Type.FUNCTION, returns = Boolean.class,
        description = "Return whether the local path exists, following symbolic links.")
    public static boolean exists(RuntimeAgent.Scope scope,
        @Verb.Argument(name = "path", description = "Local path or file: URI; relative paths use the host working directory") String path) {
      return java.nio.file.Files.exists(CoreIoSupport.path(path));
    }

    @Verb(name = "isfile", executionType = Verb.Type.FUNCTION, returns = Boolean.class,
        description = "Return whether the local path is a regular file, following symbolic links.")
    public static boolean isFile(RuntimeAgent.Scope scope,
        @Verb.Argument(name = "path", description = "Local path or file: URI; relative paths use the host working directory") String path) {
      return java.nio.file.Files.isRegularFile(CoreIoSupport.path(path));
    }

    @Verb(name = "isdirectory", executionType = Verb.Type.FUNCTION, returns = Boolean.class,
        description = "Return whether the local path is a directory, following symbolic links.")
    public static boolean isDirectory(RuntimeAgent.Scope scope,
        @Verb.Argument(name = "path", description = "Local path or file: URI; relative paths use the host working directory") String path) {
      return java.nio.file.Files.isDirectory(CoreIoSupport.path(path));
    }

    @Verb(name = "size", executionType = Verb.Type.FUNCTION, returns = Long.class,
        description = "Return the local file size in bytes; inaccessible or missing paths raise an I/O error.")
    public static long size(RuntimeAgent.Scope scope,
        @Verb.Argument(name = "path", description = "Local path or file: URI; relative paths use the host working directory") String path) {
      return CoreIoSupport.io(() -> java.nio.file.Files.size(CoreIoSupport.path(path)));
    }

    @Verb(name = "read", executionType = Verb.Type.FUNCTION, returns = String.class,
        description = "Read the entire local file as UTF-8 text; missing or unreadable files raise an I/O error.")
    public static String read(RuntimeAgent.Scope scope,
        @Verb.Argument(name = "path", description = "Local path or file: URI; relative paths use the host working directory") String path) {
      return CoreIoSupport.io(() -> java.nio.file.Files.readString(CoreIoSupport.path(path), java.nio.charset.StandardCharsets.UTF_8));
    }

    @Verb(name = "readbytes", executionType = Verb.Type.FUNCTION, returns = byte[].class,
        description = "Read the entire local file as a byte array; missing or unreadable files raise an I/O error.")
    public static byte[] readBytes(RuntimeAgent.Scope scope,
        @Verb.Argument(name = "path", description = "Local path or file: URI; relative paths use the host working directory") String path) {
      return CoreIoSupport.io(() -> java.nio.file.Files.readAllBytes(CoreIoSupport.path(path)));
    }

    @Verb(name = "write", executionType = Verb.Type.FUNCTION, returns = String.class,
        description = "Create or overwrite a local file with UTF-8 text and return its absolute path. Parent directories must already exist.")
    public static String write(RuntimeAgent.Scope scope,
        @Verb.Argument(name = "path", description = "Local path or file: URI; relative paths use the host working directory") String path,
        @Verb.Argument(name = "text", description = "Text to write or encode/decode as specified by the verb") String text) {
      return CoreIoSupport.write(CoreIoSupport.path(path), text, false);
    }

    @Verb(name = "writebytes", executionType = Verb.Type.FUNCTION, returns = String.class,
        description = "Create or overwrite a local file with bytes and return its absolute path. Parent directories must already exist.")
    public static String writeBytes(RuntimeAgent.Scope scope,
        @Verb.Argument(name = "path", description = "Local path or file: URI; relative paths use the host working directory") String path,
        @Verb.Argument(name = "bytes", description = "Bytes to write") byte[] bytes) {
      return CoreIoSupport.writeBytes(CoreIoSupport.path(path), bytes);
    }

    @Verb(name = "append", executionType = Verb.Type.FUNCTION, returns = String.class,
        description = "Append UTF-8 text to a local file, creating it if absent, and return its absolute path.")
    public static String append(RuntimeAgent.Scope scope,
        @Verb.Argument(name = "path", description = "Local path or file: URI; relative paths use the host working directory") String path,
        @Verb.Argument(name = "text", description = "Text to write or encode/decode as specified by the verb") String text) {
      return CoreIoSupport.write(CoreIoSupport.path(path), text, true);
    }

    @Verb(name = "mkdir", executionType = Verb.Type.FUNCTION, returns = String.class,
        description = "Create a local directory and any missing parent directories; return its absolute path.")
    public static String mkdir(RuntimeAgent.Scope scope,
        @Verb.Argument(name = "path", description = "Local path or file: URI; relative paths use the host working directory") String path) {
      return CoreIoSupport.io(() -> java.nio.file.Files.createDirectories(CoreIoSupport.path(path)).toString());
    }

    @Verb(name = "list", executionType = Verb.Type.FUNCTION, returns = List.class,
        description = "Return sorted absolute paths of the direct entries in a local directory; traversal is not recursive.")
    public static List<String> list(RuntimeAgent.Scope scope,
        @Verb.Argument(name = "path", description = "Local path or file: URI; relative paths use the host working directory") String path) {
      return CoreIoSupport.list(CoreIoSupport.path(path));
    }

    @Verb(name = "copy", executionType = Verb.Type.FUNCTION, returns = String.class,
        description = "Copy a local path without replacing an existing destination; return the destination absolute path. Directory contents are not copied recursively.")
    public static String copy(RuntimeAgent.Scope scope,
        @Verb.Argument(name = "source", description = "Local source path") String source,
        @Verb.Argument(name = "destination", description = "Local destination path; parent directories must exist") String destination) {
      return CoreIoSupport.io(() -> java.nio.file.Files.copy(CoreIoSupport.path(source), CoreIoSupport.path(destination)).toString());
    }

    @Verb(name = "move", executionType = Verb.Type.FUNCTION, returns = String.class,
        description = "Move a local path without replacing an existing destination; return the destination absolute path.")
    public static String move(RuntimeAgent.Scope scope,
        @Verb.Argument(name = "source", description = "Local source path") String source,
        @Verb.Argument(name = "destination", description = "Local destination path; parent directories must exist") String destination) {
      return CoreIoSupport.io(() -> java.nio.file.Files.move(CoreIoSupport.path(source), CoreIoSupport.path(destination)).toString());
    }

    @Verb(name = "delete", executionType = Verb.Type.FUNCTION, returns = Boolean.class,
        description = "Delete a local file or empty directory; return `false` if absent. Nonempty directories raise an I/O error.")
    public static boolean delete(RuntimeAgent.Scope scope,
        @Verb.Argument(name = "path", description = "Local path or file: URI; relative paths use the host working directory") String path) {
      return CoreIoSupport.io(() -> java.nio.file.Files.deleteIfExists(CoreIoSupport.path(path)));
    }

    @Verb(name = "temp", executionType = Verb.Type.FUNCTION, returns = File.class, producesAgent = "core.file",
        description = "Create an empty `.tmp` file in the JVM temporary directory and return a `core.file` handle. The prefix needs at least three characters; callers own cleanup.")
    public static File temp(RuntimeAgent.Scope scope,
        @Verb.Argument(name = "prefix", description = "Temporary filename prefix, at least three characters") String prefix) {
      if (prefix == null || prefix.length() < 3) throw new KlabIllegalArgumentException("Temporary file prefix needs at least three characters");
      return CoreIoSupport.io(() -> new File(java.nio.file.Files.createTempFile(prefix, ".tmp")));
    }

    @Verb(name = "path", executionType = Verb.Type.FUNCTION, returns = String.class,
        description = "Return this handle's normalized absolute local path.")
    public String path(RuntimeAgent.Scope scope) {
      return CoreIoSupport.require(path).toString();
    }

    @Verb(name = "info", executionType = Verb.Type.FUNCTION, returns = Map.class,
        description = "Return metadata for the bound path: `path`, `name`, `exists`, `file`, `directory`, `readable`, `writable`, `size`, and `modified`.")
    public Map<String, Object> info(RuntimeAgent.Scope scope) {
      return CoreIoSupport.fileInfo(CoreIoSupport.require(path));
    }

    @Verb(name = "present", executionType = Verb.Type.FUNCTION, returns = Boolean.class,
        description = "Return whether the bound local path exists, following symbolic links.")
    public boolean present(RuntimeAgent.Scope scope) {
      return exists(scope, path(scope));
    }

    @Verb(name = "text", executionType = Verb.Type.FUNCTION, returns = String.class,
        description = "Read the entire bound file as UTF-8 text; missing or unreadable files raise an I/O error.")
    public String text(RuntimeAgent.Scope scope) {
      return read(scope, path(scope));
    }

    @Verb(name = "bytes", executionType = Verb.Type.FUNCTION, returns = byte[].class,
        description = "Read the entire bound file as a byte array; missing or unreadable files raise an I/O error.")
    public byte[] bytes(RuntimeAgent.Scope scope) {
      return readBytes(scope, path(scope));
    }

    @Verb(name = "save", executionType = Verb.Type.FUNCTION, returns = String.class,
        description = "Create or overwrite the bound file with UTF-8 text and return its absolute path. Parent directories must already exist.")
    public String save(RuntimeAgent.Scope scope,
        @Verb.Argument(name = "text", description = "Text to write or encode/decode as specified by the verb") String text) {
      return write(scope, path(scope), text);
    }

    @Verb(name = "savebytes", executionType = Verb.Type.FUNCTION, returns = String.class,
        description = "Create or overwrite the bound file with bytes and return its absolute path. Parent directories must already exist.")
    public String saveBytes(RuntimeAgent.Scope scope,
        @Verb.Argument(name = "bytes", description = "Bytes to write") byte[] bytes) {
      return writeBytes(scope, path(scope), bytes);
    }

    @Verb(name = "appendtext", executionType = Verb.Type.FUNCTION, returns = String.class,
        description = "Append UTF-8 text to the bound file, creating it if absent, and return its absolute path.")
    public String appendText(RuntimeAgent.Scope scope,
        @Verb.Argument(name = "text", description = "Text to write or encode/decode as specified by the verb") String text) {
      return append(scope, path(scope), text);
    }

    @Verb(name = "makedirectories", executionType = Verb.Type.FUNCTION, returns = String.class,
        description = "Create the bound directory and any missing parent directories; return its absolute path.")
    public String makeDirectories(RuntimeAgent.Scope scope) {
      return mkdir(scope, path(scope));
    }

    @Verb(name = "entries", executionType = Verb.Type.FUNCTION, returns = List.class,
        description = "Return sorted absolute paths of the bound directory's direct entries; traversal is not recursive.")
    public List<String> entries(RuntimeAgent.Scope scope) {
      return list(scope, path(scope));
    }

    @Verb(name = "copyto", executionType = Verb.Type.FUNCTION, returns = File.class, producesAgent = "core.file",
        description = "Copy the bound path without replacing the destination and return a new `core.file` handle. The source handle is unchanged; directory contents are not copied recursively.")
    public File copyTo(RuntimeAgent.Scope scope,
        @Verb.Argument(name = "destination", description = "Local destination path; parent directories must exist") String destination) {
      return create(scope, copy(scope, path(scope), destination));
    }

    @Verb(name = "moveto", executionType = Verb.Type.FUNCTION, returns = File.class, producesAgent = "core.file",
        description = "Move the bound path without replacing the destination and return a new `core.file` handle. The original handle retains its old path.")
    public File moveTo(RuntimeAgent.Scope scope,
        @Verb.Argument(name = "destination", description = "Local destination path; parent directories must exist") String destination) {
      return create(scope, move(scope, path(scope), destination));
    }

    @Verb(name = "remove", executionType = Verb.Type.FUNCTION, returns = Boolean.class,
        description = "Delete the bound file or empty directory; return `false` if absent. Nonempty directories raise an I/O error.")
    public boolean remove(RuntimeAgent.Scope scope) {
      return delete(scope, path(scope));
    }

    @Verb(name = "child", executionType = Verb.Type.FUNCTION, returns = File.class, producesAgent = "core.file",
        description = "Resolve a relative path against the bound path and return a normalized `core.file` handle without creating a file. Parent traversal (`..`) is allowed.")
    public File child(RuntimeAgent.Scope scope,
        @Verb.Argument(name = "relative", description = "Relative path or URL reference, resolved against the handle or base") String relative) {
      return new File(CoreIoSupport.child(path, relative));
    }

    @Verb(name = "parent", executionType = Verb.Type.FUNCTION, returns = File.class, producesAgent = "core.file",
        description = "Return a `core.file` handle for the parent path, or `null` for a filesystem root.")
    public File parent(RuntimeAgent.Scope scope) {
      var parent = CoreIoSupport.require(path).getParent();
      return parent == null ? null : new File(parent);
    }
  }

  /** URL inspection and bounded asynchronous access; see docs/AGENTS_REFERENCE.md. */
  @Actor(name = "url", description = "HTTP, HTTPS and file URL operations and bound URL handles")
  public static class Url {
    private final java.net.URI uri;

    /** Catalog instance; access verbs require a handle returned by new. */
    public Url() { this.uri = null; }
    private Url(java.net.URI uri) { this.uri = uri; }

    @Verb(name = "new", executionType = Verb.Type.FUNCTION, returns = Url.class, producesAgent = "core.url",
        description = "Create a `core.url` handle for a normalized absolute `http:`, `https:`, or `file:` URL without accessing it. Embedded user credentials are rejected.")
    public static Url create(RuntimeAgent.Scope scope,
        @Verb.Argument(name = "address", description = "Absolute http:, https:, or file: URL without embedded credentials") String address) {
      return new Url(CoreIoSupport.uri(address));
    }

    @Verb(name = "inspect", executionType = Verb.Type.FUNCTION, returns = Map.class,
        description = "Return parsed URL components: `address`, `scheme`, `host`, `port`, `path`, `query`, and `fragment`. No network access occurs.")
    public static Map<String, Object> inspect(RuntimeAgent.Scope scope,
        @Verb.Argument(name = "address", description = "Absolute http:, https:, or file: URL without embedded credentials") String address) {
      return CoreIoSupport.urlInfo(CoreIoSupport.uri(address));
    }

    @Verb(name = "resolve", executionType = Verb.Type.FUNCTION, returns = Url.class, producesAgent = "core.url",
        description = "Resolve a relative reference against an absolute URL and return a `core.url` handle without accessing it.")
    public static Url resolve(RuntimeAgent.Scope scope,
        @Verb.Argument(name = "base", description = "Absolute base URL") String base,
        @Verb.Argument(name = "relative", description = "Relative path or URL reference, resolved against the handle or base") String relative) {
      return new Url(CoreIoSupport.resolve(CoreIoSupport.uri(base), relative));
    }

    @Verb(name = "encode", executionType = Verb.Type.FUNCTION, returns = String.class,
        description = "Encode text using UTF-8 form URL encoding: spaces become `+` and reserved characters are percent-encoded.")
    public static String encode(RuntimeAgent.Scope scope,
        @Verb.Argument(name = "text", description = "Text to write or encode/decode as specified by the verb") String text) {
      return CoreIoSupport.encode(text);
    }

    @Verb(name = "decode", executionType = Verb.Type.FUNCTION, returns = String.class,
        description = "Decode UTF-8 form URL encoding, including `+` as a space; malformed escapes are errors.")
    public static String decode(RuntimeAgent.Scope scope,
        @Verb.Argument(name = "text", description = "Text to write or encode/decode as specified by the verb") String text) {
      return CoreIoSupport.decode(text);
    }

    @Verb(name = "read", executionType = Verb.Type.SUPPLIER, returns = String.class,
        description = "Asynchronously GET the URL and supply its entire body as UTF-8 text. HTTP non-2xx responses fail; redirects are not followed. Defaults: 10-second connect/read timeouts and a 16 MiB response limit.")
    public static CompletableFuture<String> read(RuntimeAgent.Scope scope,
        @Verb.Argument(name = "address", description = "Absolute http:, https:, or file: URL without embedded credentials") String address) {
      var uri = CoreIoSupport.uri(address);
      return CoreIoSupport.async(() -> new String(CoreIoSupport.get(uri), java.nio.charset.StandardCharsets.UTF_8));
    }

    @Verb(name = "readbytes", executionType = Verb.Type.SUPPLIER, returns = byte[].class,
        description = "Asynchronously GET the URL and supply its entire body as bytes. HTTP non-2xx responses fail; redirects are not followed. Defaults: 10-second connect/read timeouts and a 16 MiB response limit.")
    public static CompletableFuture<byte[]> readBytes(RuntimeAgent.Scope scope,
        @Verb.Argument(name = "address", description = "Absolute http:, https:, or file: URL without embedded credentials") String address) {
      var uri = CoreIoSupport.uri(address);
      return CoreIoSupport.async(() -> CoreIoSupport.get(uri));
    }

    @Verb(name = "request", executionType = Verb.Type.SUPPLIER, returns = Map.class,
        description = "Asynchronously request the URL using `GET`, `HEAD`, `POST`, `PUT`, `DELETE`, or `OPTIONS` and an optional UTF-8 body. Metadata: `:headers` string map, `:timeout` positive milliseconds (default 10000), `:maxbytes` positive byte limit (default 16777216). Supply a map with `status`, `headers`, UTF-8 `body`, `bytes`, and `address`, including non-2xx responses. Redirects are not followed; GET/HEAD reject a body, and file URLs support only GET without headers.")
    public static CompletableFuture<Map<String, Object>> request(RuntimeAgent.Scope scope,
        @Verb.Argument(name = "address", description = "Absolute http:, https:, or file: URL without embedded credentials") String address,
        @Verb.Argument(name = "method", description = "Case-sensitive HTTP method: GET, HEAD, POST, PUT, DELETE, or OPTIONS") String method,
        @Verb.Argument(name = "body", description = "Optional UTF-8 request body; not permitted for GET or HEAD", optional = true) String body,
        Metadata options) {
      var uri = CoreIoSupport.uri(address);
      var snapshot = CoreIoSupport.options(options);
      return CoreIoSupport.async(() -> CoreIoSupport.request(uri, method, body, snapshot).asMap());
    }

    @Verb(name = "download", executionType = Verb.Type.SUPPLIER, returns = File.class, producesAgent = "core.file",
        description = "Asynchronously GET the URL, then create or replace the destination with response bytes and supply a `core.file` handle. HTTP non-2xx responses fail before writing; parents must exist. Defaults: 10-second connect/read timeouts and a 16 MiB response limit; redirects are not followed.")
    public static CompletableFuture<File> download(RuntimeAgent.Scope scope,
        @Verb.Argument(name = "address", description = "Absolute http:, https:, or file: URL without embedded credentials") String address,
        @Verb.Argument(name = "destination", description = "Local destination path; parent directories must exist") String destination) {
      var uri = CoreIoSupport.uri(address);
      var path = CoreIoSupport.path(destination);
      return CoreIoSupport.async(() -> CoreIoSupport.download(uri, path));
    }

    @Verb(name = "address", executionType = Verb.Type.FUNCTION, returns = String.class,
        description = "Return this handle's normalized absolute URL string.")
    public String address(RuntimeAgent.Scope scope) {
      return CoreIoSupport.require(uri).toString();
    }

    @Verb(name = "info", executionType = Verb.Type.FUNCTION, returns = Map.class,
        description = "Return parsed components of the bound URL: `address`, `scheme`, `host`, `port`, `path`, `query`, and `fragment`. No network access occurs.")
    public Map<String, Object> info(RuntimeAgent.Scope scope) {
      return CoreIoSupport.urlInfo(CoreIoSupport.require(uri));
    }

    @Verb(name = "child", executionType = Verb.Type.FUNCTION, returns = Url.class, producesAgent = "core.url",
        description = "Resolve a relative reference against the bound URL and return a new `core.url` handle without accessing it.")
    public Url child(RuntimeAgent.Scope scope,
        @Verb.Argument(name = "relative", description = "Relative path or URL reference, resolved against the handle or base") String relative) {
      return new Url(CoreIoSupport.resolve(CoreIoSupport.require(uri), relative));
    }

    @Verb(name = "text", executionType = Verb.Type.SUPPLIER, returns = String.class,
        description = "Asynchronously GET the bound URL and supply UTF-8 text. HTTP non-2xx responses fail; redirects are not followed. Defaults: 10-second connect/read timeouts and a 16 MiB response limit.")
    public CompletableFuture<String> text(RuntimeAgent.Scope scope) {
      return read(scope, address(scope));
    }

    @Verb(name = "bytes", executionType = Verb.Type.SUPPLIER, returns = byte[].class,
        description = "Asynchronously GET the bound URL and supply response bytes. HTTP non-2xx responses fail; redirects are not followed. Defaults: 10-second connect/read timeouts and a 16 MiB response limit.")
    public CompletableFuture<byte[]> bytes(RuntimeAgent.Scope scope) {
      return readBytes(scope, address(scope));
    }

    @Verb(name = "fetch", executionType = Verb.Type.SUPPLIER, returns = Map.class,
        description = "Asynchronously request the bound URL using `GET`, `HEAD`, `POST`, `PUT`, `DELETE`, or `OPTIONS` and an optional UTF-8 body. Metadata: `:headers` string map, `:timeout` positive milliseconds (default 10000), `:maxbytes` positive byte limit (default 16777216). Supply `status`, `headers`, UTF-8 `body`, `bytes`, and `address`, including non-2xx responses. Redirects are not followed; GET/HEAD reject a body, and file URLs support only GET without headers.")
    public CompletableFuture<Map<String, Object>> fetch(RuntimeAgent.Scope scope,
        @Verb.Argument(name = "method", description = "Case-sensitive HTTP method: GET, HEAD, POST, PUT, DELETE, or OPTIONS") String method,
        @Verb.Argument(name = "body", description = "Optional UTF-8 request body; not permitted for GET or HEAD", optional = true) String body,
        Metadata options) {
      return request(scope, address(scope), method, body, options);
    }

    @Verb(name = "downloadto", executionType = Verb.Type.SUPPLIER, returns = File.class, producesAgent = "core.file",
        description = "Asynchronously GET the bound URL, then create or replace the destination and supply a `core.file` handle. HTTP non-2xx responses fail before writing; parents must exist. Defaults: 10-second connect/read timeouts and a 16 MiB response limit; redirects are not followed.")
    public CompletableFuture<File> downloadTo(RuntimeAgent.Scope scope,
        @Verb.Argument(name = "destination", description = "Local destination path; parent directories must exist") String destination) {
      return download(scope, address(scope), destination);
    }
  }

  /** Service-owned outgoing email; configuration remains exclusively in the service settings API. */
  @Actor(name = "email", description = "Send email using the calling agent's host service configuration")
  public static class Email {

    @Verb(name = "configured", executionType = Verb.Type.FUNCTION, returns = Boolean.class,
        description = "Check whether outgoing email is enabled and configured; never contacts SMTP")
    public static boolean configured(RuntimeAgent.Scope scope) {
      var manager = manager(scope);
      return manager != null && manager.isConfigured();
    }

    @Verb(name = "status", executionType = Verb.Type.FUNCTION, returns = Map.class,
        description = "Return available, enabled, configured and missing setting names without credentials")
    public static Map<String, Object> status(RuntimeAgent.Scope scope) {
      var manager = manager(scope);
      if (manager == null) {
        return Map.of("available", false, "enabled", false, "configured", false, "missing", List.of());
      }
      var status = manager.getConfigurationStatus();
      return Map.of("available", true, "enabled", status.enabled(), "configured", status.configured(),
          "missing", status.missingOrInvalidSettings().stream().map(Enum::name).toList());
    }

    @Verb(name = "send", executionType = Verb.Type.SUPPLIER, returns = Boolean.class,
        description = "Send UTF-8 plain text; supply false if unavailable or unconfigured, true on SMTP acceptance")
    public static CompletableFuture<Boolean> send(
        RuntimeAgent.Scope scope,
        @Verb.Argument(name = "to", description = "Recipient email address") String recipient,
        @Verb.Argument(name = "subject", description = "Single-line subject") String subject,
        @Verb.Argument(name = "body", description = "Plain text message body") String body) {
      return send(scope, recipient, subject, body, false);
    }

    @Verb(name = "sendhtml", executionType = Verb.Type.SUPPLIER, returns = Boolean.class,
        description = "Send UTF-8 HTML; supply false if unavailable or unconfigured, true on SMTP acceptance")
    public static CompletableFuture<Boolean> sendHtml(
        RuntimeAgent.Scope scope,
        @Verb.Argument(name = "to", description = "Recipient email address") String recipient,
        @Verb.Argument(name = "subject", description = "Single-line subject") String subject,
        @Verb.Argument(name = "body", description = "HTML message body") String body) {
      return send(scope, recipient, subject, body, true);
    }

    private static CompletableFuture<Boolean> send(
        RuntimeAgent.Scope scope, String recipient, String subject, String body, boolean html) {
      var manager = manager(scope);
      if (manager == null) return CompletableFuture.completedFuture(false);
      // Blocking SMTP work runs on a virtual thread, outside the calling action thread.
      return CompletableFuture.supplyAsync(
          () -> manager.send(recipient, subject, body, html), task -> Thread.startVirtualThread(task));
    }

    private static EmailManager manager(RuntimeAgent.Scope scope) {
      // Session and context scopes inherit ServiceUserScope and retain their exact host service.
      // Never borrow SMTP configuration or credentials from a connected peer service/client.
      if (scope != null && scope.getScope() instanceof ServiceUserScope serviceScope
          && serviceScope.getService() instanceof BaseService service) {
        return service.getEmailManager();
      }
      return null;
    }
  }

  /** ContextScope proxy; query contracts and proposed event emitters are in docs/DIGITALTWINS.md. */
  @Actor(name = "context", description = "Digital twin actor")
  public static class Context {

    private final ContextScope context;

    public Context() {
      this.context = null;
    }

    public Context(ContextScope context) {
      this.context = Objects.requireNonNull(context, "context");
    }

    private ContextScope requireContext() {
      if (context == null) throw new KlabIllegalStateException("Use context.new, context.current or context.wrap first");
      return context;
    }

    @Verb(name = "wrap", executionType = Verb.Type.FUNCTION, producesAgent = "core.context",
        description = "Borrow an existing ContextScope without creating a twin or taking cleanup ownership")
    public static Context wrap(RuntimeAgent.Scope scope,
        @Verb.Argument(name = "context", description = "Existing ContextScope") ContextScope context) {
      return new Context(context);
    }

    @Verb(name = "current", executionType = Verb.Type.FUNCTION, producesAgent = "core.context",
        description = "Borrow the calling agent's current context; fails if the agent has no context")
    public static Context current(RuntimeAgent.Scope scope) {
      if (scope == null || scope.getContext() == null) throw new KlabIllegalStateException("The calling agent has no context");
      return new Context(scope.getContext());
    }

    @Verb(name = "focus", executionType = Verb.Type.FUNCTION, producesAgent = "core.context",
        description = "Return a proxy focused by :within observation or :source observation :target observation")
    public Context focus(RuntimeAgent.Scope scope, Metadata options) {
      for (String key : options.keySet()) if (!Set.of("within", "source", "target").contains(key))
        throw new IllegalArgumentException("Unknown focus option: " + key);
      return new Context(ContextActorSupport.focus(requireContext(), options));
    }

    @Verb(name = "scope", executionType = Verb.Type.FUNCTION, returns = ContextScope.class,
        description = "Return the underlying `ContextScope` of this context handle.")
    public ContextScope scope(RuntimeAgent.Scope scope) { return requireContext(); }

    @Verb(name = "twin", executionType = Verb.Type.FUNCTION, returns = DigitalTwin.class,
        description = "Return the underlying `DigitalTwin`; fail if no twin is available in this context.")
    public DigitalTwin twin(RuntimeAgent.Scope scope) {
      var twin = requireContext().getDigitalTwin();
      if (twin == null) throw new KlabIllegalStateException("No digital twin is available in this context");
      return twin;
    }

    @Verb(name = "graph", executionType = Verb.Type.FUNCTION, returns = org.integratedmodelling.klab.api.data.KnowledgeGraph.class,
        description = "Return the digital twin's knowledge graph.")
    public org.integratedmodelling.klab.api.data.KnowledgeGraph graph(RuntimeAgent.Scope scope) { return twin(scope).getKnowledgeGraph(); }

    @Verb(name = "scheduler", executionType = Verb.Type.FUNCTION, returns = org.integratedmodelling.klab.api.digitaltwin.Scheduler.class,
        description = "Return the digital twin's scheduler without advancing time.")
    public org.integratedmodelling.klab.api.digitaltwin.Scheduler scheduler(RuntimeAgent.Scope scope) { return twin(scope).getScheduler(); }

    @Verb(name = "timeline", executionType = Verb.Type.FUNCTION, returns = Map.class,
        description = "Read scheduler epochStart, epochEnd and resolution without advancing time")
    public Map<String, Object> timeline(RuntimeAgent.Scope scope) {
      var scheduler = Objects.requireNonNull(scheduler(scope), "No scheduler available");
      var snapshot = new LinkedHashMap<String, Object>();
      snapshot.put("epochStart", scheduler.epochStart());
      snapshot.put("epochEnd", scheduler.epochEnd());
      snapshot.put("resolution", scheduler.resolution());
      return Collections.unmodifiableMap(snapshot);
    }

    @Verb(name = "close", executionType = Verb.Type.FUNCTION, returns = Void.class,
        description = "Explicitly close the underlying context according to its persistence policy; affects all proxies")
    public void close(RuntimeAgent.Scope scope) { requireContext().close(); }

    @Verb(name = "storagemanager", executionType = Verb.Type.FUNCTION, returns = org.integratedmodelling.klab.api.digitaltwin.StorageManager.class,
        description = "Return the digital twin's storage manager.")
    public org.integratedmodelling.klab.api.digitaltwin.StorageManager storageManager(RuntimeAgent.Scope scope) { return twin(scope).getStorageManager(); }

    @Verb(name = "storage", executionType = Verb.Type.FUNCTION, returns = org.integratedmodelling.klab.api.data.Storage.class,
        description = "Retrieve existing observation storage; absent or inaccessible storage raises a backend error")
    public org.integratedmodelling.klab.api.data.Storage storage(RuntimeAgent.Scope scope,
        @Verb.Argument(name = "observation", description = "Observation in this twin") Observation observation) {
      return storageManager(scope).getStorage(Objects.requireNonNull(observation, "observation"));
    }

    @Verb(name = "members", executionType = Verb.Type.FUNCTION, returns = List.class,
        description = "Return an iterable snapshot of a cohort's direct HAS_MEMBER observations; accepts limit and offset")
    public List<?> members(RuntimeAgent.Scope scope,
        @Verb.Argument(name = "cohort", description = "Cohort in this twin") org.integratedmodelling.klab.api.knowledge.Cohort cohort,
        Metadata options) {
      for (String key : options.keySet()) if (!Set.of("limit", "offset").contains(key))
        throw new IllegalArgumentException("Unknown members option: " + key);
      var queryOptions = Metadata.create();
      queryOptions.putAll(options);
      queryOptions.put("source", Objects.requireNonNull(cohort, "cohort"));
      queryOptions.put("along", org.integratedmodelling.klab.api.digitaltwin.GraphModel.Relationship.HAS_MEMBER);
      queryOptions.put("all", true);
      return (List<?>) ContextActorSupport.query(requireContext(), queryOptions);
    }

    /**
     * Create a session-owned context. Context composition remains a documented proposal.
     *
     * @param agentScope
     * @return
     */
    @Verb(name = "new", executionType = Verb.Type.FUNCTION, producesAgent = "core.context", description = "Create a new context")
    public static Context createContext(AgentScope agentScope, Object... args) {

      var options = ContextActorSupport.metadata(args);
      for (String key : options.keySet()) if (!Set.of("name", "description", "persistence").contains(key))
        throw new IllegalArgumentException("Unknown context creation option: " + key);
      if (args != null) for (Object argument : args)
        if (!(argument instanceof String || argument instanceof Persistence || argument instanceof Metadata))
          throw new IllegalArgumentException("Context.new accepts a name, Persistence and creation metadata; use wrap for an existing scope");
      for (String key : List.of("name", "description"))
        if (options.containsKey(key) && !(options.get(key) instanceof String))
          throw new IllegalArgumentException(key + " must be a string");
      var persistence = options.containsKey("persistence")
          ? (Persistence) org.integratedmodelling.klab.runtime.kactors.JavaArgumentConversions.enumValue(options.get("persistence"), Persistence.class)
          : Utils.Collections.findElement(args, Persistence.ONE_OFF);

      var aScope = agentScope.getAgent().getCreationScope();
      if (aScope instanceof SessionScope sessionScope) {

        var builder =
            DigitalTwin.Configuration.builder()
                .name(options.containsKey("name") ? (String) options.get("name") : Utils.Collections.findElement(args, "Unnamed context"))
                .persistence(persistence)
                .serviceId(aScope.getService(RuntimeService.class).serviceId())
                .serverUrl(aScope.getService(RuntimeService.class).getUrl())
                .owner(sessionScope.getUser().getUsername())
                .description(
                    options.containsKey("description") ? (String) options.get("description") : "Created by agent "
                        + agentScope.getAgent().getName()
                        + " on "
                        + TimeInstant.create())
                .accessRights(ResourcePrivileges.create(sessionScope));

        var context = sessionScope.createContext(builder.build());

        // register for disposal if we're running a test
        if (agentScope instanceof TestCaseBase.TestCaseScope testScope) {
          testScope.registerContext(context);
        }

        return new Context(context);
      }

      throw new KlabIllegalStateException("Context creation is only supported in a session scope");
    }

    @Verb(name = "query", executionType = Verb.Type.FUNCTION,
        description = "Select typed RuntimeAssets by ID, URN or exact observation semantics. "
            + "Use :within for focused children, :source/:target for directed traversal, "
            + "both endpoints for LINK results, :along for the edge type, and +all for a list. "
            + "Supports :limit, :offset and :depth. See docs/DIGITALTWINS.md.")
    public Object query(AgentScope scope, Object... arguments) {
      return ContextActorSupport.query(requireContext(), arguments);
    }

    @Verb(
        name = "submit",
        executionType = Verb.Type.SUPPLIER,
        returns = Observation.class,
        description =
            """
            Submit an observation to the digital twin""")
    public CompletableFuture<Observation> submit(AgentScope scope, Object... arguments) {

      var metadata = ContextActorSupport.metadata(arguments);
      var selectedContext = ContextActorSupport.focus(requireContext(), metadata);
      var runtimeService = selectedContext.getService(RuntimeService.class);

      var provenanceAgent = selectedContext.getDigitalTwin().getKnowledgeGraph()
          .requireAgent(scope.getAgent().getName());

      var builder = Observation.builder(selectedContext);
      var definition = Utils.Collections.findElement(arguments, Map.class, metadata);
      var observable = Utils.Collections.findElement(arguments, KimObservable.class);
      var concept = Utils.Collections.findElement(arguments, KimConcept.class);
      var urn = Utils.Collections.findElement(arguments, Urn.class);
      var geometry = Utils.Collections.findElement(arguments, Geometry.class);

      String semanticDef =
          concept == null ? (observable == null ? null : observable.getUrn()) : concept.getUrn();
      Observable semantics = Utils.Collections.findElement(arguments, Observable.class);
      if (semanticDef != null) {
        semantics =
            scope
                .getAgent()
                .getCreationScope()
                .getService(Reasoner.class)
                .resolveObservable(semanticDef);
      }

      // definition MUST remain last
      var target =
          builder
              .observable(semantics)
              .identity(urn)
              .geometry(geometry)
              .definition(definition)
              .build();

      var submissionScope =
          selectedContext.withResolutionConstraints(
              ResolutionConstraint.of(ResolutionConstraint.Type.Provenance, provenanceAgent));

      if (metadata.get("namespace") instanceof String namespace) {
        submissionScope =
            submissionScope.withResolutionConstraints(
                ResolutionConstraint.of(ResolutionConstraint.Type.ResolutionNamespace, namespace));
      }

      if (metadata.get("project") instanceof String project) {
        submissionScope =
            submissionScope.withResolutionConstraints(
                ResolutionConstraint.of(ResolutionConstraint.Type.ResolutionProject, project));
      }

      return runtimeService.submit(target, submissionScope);
    }
  }

  /** Snapshot assertion functions; see docs/TESTING.md for policies and capture boundaries. */
  @Actor(name = "inspector", description = "Read-only asset and graph assertion factory")
  public static class Inspector {

    @Verb(
        name = "viable",
        executionType = Verb.Type.FUNCTION,
        returns = Boolean.class,
        description =
            "Check all assets. +data requires some data, +nodata all no-data, !nodata complete data. "
                + "!resolved requires substantial resolution; :mincoverage sets a coverage threshold.")
    public static boolean checkViable(
        RuntimeAgent.Scope scope,
        @Verb.Argument(
                name = "assets",
                description = "Assets to inspect and inline policy metadata")
            Object... arguments) {
      var problems = problems(scope, arguments);
      if (!problems.isEmpty() && scope != null) Console.println(scope, String.join("; ", problems));
      return problems.isEmpty();
    }

    @Verb(
        name = "problems",
        executionType = Verb.Type.FUNCTION,
        returns = List.class,
        description = "Return viability failure reasons without recording assertions or printing.")
    public static List<String> problems(
        RuntimeAgent.Scope scope,
        @Verb.Argument(
                name = "assets",
                description = "Assets to inspect and inline policy metadata")
            Object... arguments) {
      Object[] normalized =
          arguments == null
              ? null
              : Arrays.stream(arguments)
                  .map(a -> a instanceof Context context ? context.context : a)
                  .toArray();
      return InspectorSupport.problems(normalized);
    }

    @Verb(
        name = "present",
        executionType = Verb.Type.FUNCTION,
        returns = Boolean.class,
        description = "Test whether a captured value is non-null, without interpreting viability.")
    public static boolean present(
        RuntimeAgent.Scope scope,
        @Verb.Argument(name = "asset", description = "Captured value", optional = true)
            Object asset) {
      return asset != null;
    }

    @Verb(
        name = "resolved",
        executionType = Verb.Type.FUNCTION,
        returns = Boolean.class,
        description = "Require positive resolution coverage, optionally at least minimum (0..1).")
    public static boolean resolved(
        RuntimeAgent.Scope scope,
        @Verb.Argument(name = "observation", description = "Observation to inspect")
            Observation observation,
        @Verb.Argument(
                name = "minimum",
                description = "Minimum coverage; default zero means any positive coverage",
                optional = true)
            Number minimum) {
      return InspectorSupport.resolved(observation, minimum == null ? 0 : minimum.doubleValue());
    }

    @Verb(
        name = "hasdata",
        executionType = Verb.Type.FUNCTION,
        returns = Boolean.class,
        description = "Require at least one valid value in scalar data or histogram snapshots.")
    public static boolean hasData(
        RuntimeAgent.Scope scope,
        @Verb.Argument(name = "asset", description = "Observation, storage, shard or histogram")
            Object asset) {
      return InspectorSupport.hasData(asset, false);
    }

    @Verb(
        name = "nodata",
        executionType = Verb.Type.FUNCTION,
        returns = Boolean.class,
        description = "Require nonempty evidence consisting entirely of no-data.")
    public static boolean noData(
        RuntimeAgent.Scope scope,
        @Verb.Argument(name = "asset", description = "Observation, storage, shard or histogram")
            Object asset) {
      return InspectorSupport.allNoData(asset);
    }

    @Verb(
        name = "complete",
        executionType = Verb.Type.FUNCTION,
        returns = Boolean.class,
        description = "Require valid data and zero missing values in every captured slice.")
    public static boolean complete(
        RuntimeAgent.Scope scope,
        @Verb.Argument(name = "asset", description = "Observation, storage, shard or histogram")
            Object asset) {
      return InspectorSupport.hasData(asset, true);
    }

    @Verb(
        name = "inrange",
        executionType = Verb.Type.FUNCTION,
        returns = Boolean.class,
        description =
            "Check inclusive numeric bounds across all captured histogram slices or a scalar value. Missing values are ignored; combine with complete.")
    public static boolean inRange(
        RuntimeAgent.Scope scope,
        @Verb.Argument(name = "asset", description = "Observation, storage, shard or histogram")
            Object asset,
        @Verb.Argument(name = "minimum", description = "Inclusive finite lower bound")
            double minimum,
        @Verb.Argument(name = "maximum", description = "Inclusive finite upper bound")
            double maximum) {
      return InspectorSupport.inRange(asset, minimum, maximum);
    }

    @Verb(
        name = "metadata",
        executionType = Verb.Type.FUNCTION,
        returns = Boolean.class,
        description =
            "Check that an observation metadata key exists and equals the expected value.")
    public static boolean metadata(
        RuntimeAgent.Scope scope,
        @Verb.Argument(name = "observation", description = "Observation") Observation observation,
        @Verb.Argument(name = "key", description = "Metadata key") String key,
        @Verb.Argument(name = "expected", description = "Expected value", optional = true)
            Object expected) {
      return observation != null
          && observation.getMetadata() != null
          && observation.getMetadata().containsKey(key)
          && Objects.deepEquals(observation.getMetadata().get(key), expected);
    }

    @Verb(
        name = "graph",
        executionType = Verb.Type.FUNCTION,
        returns = org.integratedmodelling.klab.api.data.KnowledgeGraph.class,
        description =
            "Get the knowledge graph from a context actor, context scope, digital twin or graph.")
    public static org.integratedmodelling.klab.api.data.KnowledgeGraph graph(
        RuntimeAgent.Scope scope,
        @Verb.Argument(name = "context", description = "Context actor, scope, twin or graph")
            Object context) {
      if (context instanceof Context actor) context = actor.context;
      if (context instanceof ContextScope contextScope) context = contextScope.getDigitalTwin();
      if (context instanceof DigitalTwin twin) context = twin.getKnowledgeGraph();
      if (context instanceof org.integratedmodelling.klab.api.data.KnowledgeGraph graph)
        return graph;
      throw new IllegalArgumentException("Expected a context, digital twin or knowledge graph");
    }

    @Verb(
        name = "contains",
        executionType = Verb.Type.FUNCTION,
        returns = Boolean.class,
        description =
            "Check committed knowledge-graph membership by asset ID, numeric ID, or canonical URN.")
    public static boolean contains(
        RuntimeAgent.Scope scope,
        @Verb.Argument(name = "context", description = "Context actor or context scope")
            Object context,
        @Verb.Argument(
                name = "asset",
                description = "Runtime asset, Long/Integer ID, String or URN")
            Object asset) {
      ContextScope ctx = contextScope(context);
      var graph = graph(scope, ctx);
      if (asset instanceof RuntimeAsset runtimeAsset) asset = runtimeAsset.getId();
      if (asset instanceof Long || asset instanceof Integer) {
        long id = ((Number) asset).longValue();
        return id > 0 && graph.getAsset(id, ctx, RuntimeAsset.class) != null;
      }
      if (asset instanceof Urn urn) asset = urn.toString();
      if (asset instanceof String urn) return graph.getAsset(urn, ctx, RuntimeAsset.class) != null;
      if (asset == null) return false;
      throw new IllegalArgumentException("Expected an asset, integer ID or canonical URN");
    }

    @Verb(
        name = "linked",
        executionType = Verb.Type.FUNCTION,
        returns = Boolean.class,
        description =
            "Check a directed knowledge-graph relationship, including transaction-local links.")
    public static boolean linked(
        RuntimeAgent.Scope scope,
        @Verb.Argument(name = "context", description = "Context actor or context scope")
            Object context,
        @Verb.Argument(name = "source", description = "Source asset") RuntimeAsset source,
        @Verb.Argument(name = "target", description = "Target asset") RuntimeAsset target,
        @Verb.Argument(name = "relationship", description = "Graph relationship")
            org.integratedmodelling.klab.api.digitaltwin.GraphModel.Relationship relationship) {
      if (source == null || target == null) return false;
      Objects.requireNonNull(relationship, "relationship");
      var ctx = contextScope(context);
      return graph(scope, ctx)
          .getLinks(
              source,
              org.integratedmodelling.klab.api.digitaltwin.GraphModel.Relationship.Direction
                  .OUTGOING,
              ctx,
              relationship)
          .stream()
          .anyMatch(link -> link.type() == relationship && sameAsset(link.target(), target));
    }

    @Verb(
        name = "acyclic",
        executionType = Verb.Type.FUNCTION,
        returns = Boolean.class,
        description = "Check a captured directed JGraphT graph for cycles. Empty graphs pass.")
    public static boolean acyclic(
        RuntimeAgent.Scope scope,
        @Verb.Argument(name = "graph", description = "Captured directed graph")
            org.jgrapht.Graph<?, ?> graph) {
      if (graph == null) return false;
      if (!graph.getType().isDirected())
        throw new IllegalArgumentException("Expected a directed graph");
      return !new org.jgrapht.alg.cycle.CycleDetector<>(graph).detectCycles();
    }

    private static ContextScope contextScope(Object context) {
      if (context instanceof Context actor) context = actor.context;
      if (context instanceof ContextScope ctx) return ctx;
      throw new IllegalArgumentException("Expected a context actor or context scope");
    }

    @Verb(
        name = "storage",
        executionType = Verb.Type.FUNCTION,
        returns = org.integratedmodelling.klab.api.data.Storage.class,
        description =
            "Retrieve existing observation storage from the supplied context; backend errors, including absent storage, propagate.")
    public static org.integratedmodelling.klab.api.data.Storage storage(
        RuntimeAgent.Scope scope,
        @Verb.Argument(name = "context", description = "Context actor or context scope")
            Object context,
        @Verb.Argument(name = "observation", description = "Observation owned by this context")
            Observation observation) {
      if (observation == null) return null;
      return contextScope(context).getDigitalTwin().getStorageManager().getStorage(observation);
    }

    @Verb(
        name = "vertices",
        executionType = Verb.Type.FUNCTION,
        returns = Integer.class,
        description = "Count vertices of a captured JGraphT graph.")
    public static int vertices(
        RuntimeAgent.Scope scope,
        @Verb.Argument(name = "graph", description = "Captured graph")
            org.jgrapht.Graph<?, ?> graph) {
      return Objects.requireNonNull(graph, "graph").vertexSet().size();
    }

    @Verb(
        name = "edges",
        executionType = Verb.Type.FUNCTION,
        returns = Integer.class,
        description = "Count edges of a captured JGraphT graph.")
    public static int edges(
        RuntimeAgent.Scope scope,
        @Verb.Argument(name = "graph", description = "Captured graph")
            org.jgrapht.Graph<?, ?> graph) {
      return Objects.requireNonNull(graph, "graph").edgeSet().size();
    }

    @Verb(name = "scancheck", executionType = Verb.Type.FUNCTION, returns = Boolean.class,
        description = "Compare bounded samples in partitioned, export and indexed text views; backend failures propagate.")
    public static boolean scancheck(RuntimeAgent.Scope scope,
        @Verb.Argument(name = "context", description = "Context actor or context scope") Object context,
        @Verb.Argument(name = "observation", description = "Committed quality or detached quality query") Observation observation,
        @Verb.Argument(name = "curve", description = "Supported fill curve constant") org.integratedmodelling.klab.api.data.Data.FillCurve curve,
        @Verb.Argument(name = "splits", description = "Consumer partitions, 1..256") int splits,
        @Verb.Argument(name = "samples", description = "Samples per partition, 1..64") int samples) {
      return org.integratedmodelling.klab.runtime.storage.StorageReadInspector.check(
          contextScope(context), observation, curve, splits, samples);
    }

    @Verb(name = "unitcheck", executionType = Verb.Type.FUNCTION, returns = Boolean.class,
        description = "Check a lazy unit view against an independent factor and offset across storage read routes.")
    public static boolean unitcheck(RuntimeAgent.Scope scope,
        @Verb.Argument(name = "context", description = "Context actor or scope") Object context,
        @Verb.Argument(name = "observation", description = "Committed numeric quality") Observation observation,
        @Verb.Argument(name = "unit", description = "Requested ordinary unit") String unit,
        @Verb.Argument(name = "factor", description = "Expected source-to-target multiplier") double factor,
        @Verb.Argument(name = "offset", description = "Expected source-to-target offset") double offset) {
      return org.integratedmodelling.klab.runtime.storage.StorageReadInspector.unitCheck(
          contextScope(context), observation, unit, factor, offset);
    }

    @Verb(name = "celltext", executionType = Verb.Type.FUNCTION, returns = String.class,
        description = "Read one cell through the export view, preserving exact longs and missingness.")
    public static String celltext(RuntimeAgent.Scope scope,
        @Verb.Argument(name = "context", description = "Context actor or context scope") Object context,
        @Verb.Argument(name = "observation", description = "Committed quality or detached quality query") Observation observation,
        @Verb.Argument(name = "curve", description = "Supported fill curve constant") org.integratedmodelling.klab.api.data.Data.FillCurve curve,
        @Verb.Argument(name = "offset", description = "Zero-based consumer traversal offset") long offset) {
      return org.integratedmodelling.klab.runtime.storage.StorageReads.text(
          observation, contextScope(context), null, curve, offset);
    }

    private static boolean sameAsset(RuntimeAsset left, RuntimeAsset right) {
      return left != null
          && (left == right
              || (left.getId() != -1 && right.getId() != -1 && left.getId() == right.getId())
              || (left.getId() == -1
                  && right.getId() == -1
                  && left.getTransientId() != 0
                  && left.getTransientId() == right.getTransientId()));
    }
  }

  @Actor(name = "log", description = "Logging actor")
  public static class Logger {

    @Verb(name = "info", executionType = Verb.Type.FUNCTION,
        description = "Log messages at info level through the calling scope, falling back to the global logger when no service scope is available.")
    public static void info(RuntimeAgent.Scope scope, Object... messages) {
      var uscope = scope.getScope();
      if (uscope != null) {
        uscope.info(messages);
      } else {
        Logging.INSTANCE.info(messages);
      }
    }

    @Verb(name = "error", executionType = Verb.Type.FUNCTION,
        description = "Log messages at error level through the calling scope, falling back to the global logger when no service scope is available.")
    public static void error(RuntimeAgent.Scope scope, Object... messages) {
      var uscope = scope.getScope();
      if (uscope != null) {
        uscope.error(messages);
      } else {
        Logging.INSTANCE.error(messages);
      }
    }

    @Verb(name = "warning", executionType = Verb.Type.FUNCTION,
        description = "Log messages at warning level through the calling scope, falling back to the global logger when no service scope is available.")
    public static void warning(RuntimeAgent.Scope scope, Object... messages) {
      var uscope = scope.getScope();
      if (uscope != null) {
        uscope.warn(messages);
      } else {
        Logging.INSTANCE.warn(messages);
      }
    }

    @Verb(name = "debug", executionType = Verb.Type.FUNCTION,
        description = "Log messages at debug level through the calling scope, falling back to the global logger when no service scope is available.")
    public static void debug(RuntimeAgent.Scope scope, Object... messages) {
      var uscope = scope.getScope();
      if (uscope != null) {
        uscope.debug(messages);
      } else {
        Logging.INSTANCE.debug(messages);
      }
    }

    // TODO emitter that catches log entries from the code with pattern

  }

  @Actor(
      name = "strings",
      description =
          "Null-safe string conversion, inspection, searching, splitting, joining and formatting functions.")
  public static class Strings {

    @Verb(name = "lowercase", executionType = Verb.Type.FUNCTION,
        description = "Convert text to lowercase using the locale-independent root locale; preserve `null`.")
    public static String lowercase(
        RuntimeAgent.Scope scope,
        @Verb.Argument(name = "text", description = "Text to convert") String text) {
      return text == null ? null : text.toLowerCase(Locale.ROOT);
    }

    @Verb(name = "uppercase", executionType = Verb.Type.FUNCTION,
        description = "Convert text to uppercase using the locale-independent root locale; preserve `null`.")
    public static String uppercase(
        RuntimeAgent.Scope scope,
        @Verb.Argument(name = "text", description = "Text to convert") String text) {
      return text == null ? null : text.toUpperCase(Locale.ROOT);
    }

    @Verb(name = "capitalize", executionType = Verb.Type.FUNCTION,
        description = "Capitalize the first character using the shared string utility.")
    public static String capitalize(
        RuntimeAgent.Scope scope,
        @Verb.Argument(name = "text", description = "Text whose first character is capitalized")
            String text) {
      return org.integratedmodelling.klab.api.utils.Utils.Strings.capitalize(text);
    }

    @Verb(name = "labelize", executionType = Verb.Type.FUNCTION,
        description = "Convert an identifier into a readable label using the shared string utility; preserve `null`.")
    public static String labelize(
        RuntimeAgent.Scope scope,
        @Verb.Argument(name = "identifier", description = "Identifier to turn into a label")
            String identifier) {
      return identifier == null
          ? null
          : org.integratedmodelling.klab.api.utils.Utils.Strings.labelizeIdentifier(identifier);
    }

    @Verb(name = "trim", executionType = Verb.Type.FUNCTION,
        description = "Strip leading and trailing whitespace; preserve `null`.")
    public static String trim(
        RuntimeAgent.Scope scope,
        @Verb.Argument(name = "text", description = "Text to strip at both ends") String text) {
      return text == null ? null : text.strip();
    }

    @Verb(name = "normalize", executionType = Verb.Type.FUNCTION,
        description = "Strip leading and trailing whitespace and replace internal whitespace runs with single spaces; preserve `null`.")
    public static String normalize(
        RuntimeAgent.Scope scope,
        @Verb.Argument(
                name = "text",
                description = "Text to trim and normalize to single internal spaces")
            String text) {
      return text == null
          ? null
          : org.integratedmodelling.klab.api.utils.Utils.Strings.replaceWhitespace(
              text.strip(), " ");
    }

    @Verb(name = "length", executionType = Verb.Type.FUNCTION,
        description = "Return the text length; return zero for `null`.")
    public static int length(
        RuntimeAgent.Scope scope,
        @Verb.Argument(name = "text", description = "Text whose length is returned") String text) {
      return org.integratedmodelling.klab.api.utils.Utils.Strings.length(text);
    }

    @Verb(name = "isempty", executionType = Verb.Type.FUNCTION,
        description = "Return whether text is `null` or empty.")
    public static boolean isEmpty(
        RuntimeAgent.Scope scope,
        @Verb.Argument(name = "text", description = "Text to test", optional = true) String text) {
      return org.integratedmodelling.klab.api.utils.Utils.Strings.isEmpty(text);
    }

    @Verb(name = "contains", executionType = Verb.Type.FUNCTION,
        description = "Test for a literal fragment; return `false` if either argument is `null`.")
    public static boolean contains(
        RuntimeAgent.Scope scope,
        @Verb.Argument(name = "text", description = "Text to search") String text,
        @Verb.Argument(name = "fragment", description = "Literal fragment to find")
            String fragment) {
      return text != null && fragment != null && text.contains(fragment);
    }

    @Verb(name = "startswith", executionType = Verb.Type.FUNCTION,
        description = "Test for a literal prefix; return `false` if either argument is `null`.")
    public static boolean startsWith(
        RuntimeAgent.Scope scope,
        @Verb.Argument(name = "text", description = "Text to inspect") String text,
        @Verb.Argument(name = "prefix", description = "Literal prefix") String prefix) {
      return text != null && prefix != null && text.startsWith(prefix);
    }

    @Verb(name = "endswith", executionType = Verb.Type.FUNCTION,
        description = "Test for a literal suffix; return `false` if either argument is `null`.")
    public static boolean endsWith(
        RuntimeAgent.Scope scope,
        @Verb.Argument(name = "text", description = "Text to inspect") String text,
        @Verb.Argument(name = "suffix", description = "Literal suffix") String suffix) {
      return text != null && suffix != null && text.endsWith(suffix);
    }

    @Verb(name = "equalsignorecase", executionType = Verb.Type.FUNCTION,
        description = "Compare text ignoring case; two `null` values are equal.")
    public static boolean equalsIgnoreCase(
        RuntimeAgent.Scope scope,
        @Verb.Argument(name = "text", description = "First text") String text,
        @Verb.Argument(name = "other", description = "Text to compare") String other) {
      return text == null ? other == null : other != null && text.equalsIgnoreCase(other);
    }

    @Verb(name = "indexof", executionType = Verb.Type.FUNCTION,
        description = "Return the zero-based index of the first literal fragment, or `-1` if absent or either argument is `null`.")
    public static int indexOf(
        RuntimeAgent.Scope scope,
        @Verb.Argument(name = "text", description = "Text to search") String text,
        @Verb.Argument(name = "fragment", description = "Literal fragment to find")
            String fragment) {
      return text == null || fragment == null ? -1 : text.indexOf(fragment);
    }

    @Verb(name = "count", executionType = Verb.Type.FUNCTION,
        description = "Count nonoverlapping occurrences of a literal fragment using the shared string utility.")
    public static int count(
        RuntimeAgent.Scope scope,
        @Verb.Argument(name = "text", description = "Text to search") String text,
        @Verb.Argument(name = "fragment", description = "Literal fragment to count")
            String fragment) {
      return org.integratedmodelling.klab.api.utils.Utils.Strings.countMatches(text, fragment);
    }

    @Verb(name = "matches", executionType = Verb.Type.FUNCTION,
        description = "Test whether the entire text matches a Java regular expression; return `false` for null arguments. Invalid patterns raise an error.")
    public static boolean matches(
        RuntimeAgent.Scope scope,
        @Verb.Argument(name = "text", description = "Text to test") String text,
        @Verb.Argument(name = "regex", description = "Java regular expression") String regex) {
      return text != null && regex != null && Pattern.matches(regex, text);
    }

    @Verb(name = "replace", executionType = Verb.Type.FUNCTION,
        description = "Replace every literal target occurrence. A null replacement removes matches; null text or target leaves the text unchanged.")
    public static String replace(
        RuntimeAgent.Scope scope,
        @Verb.Argument(name = "text", description = "Text to modify") String text,
        @Verb.Argument(name = "target", description = "Literal text to replace") String target,
        @Verb.Argument(name = "replacement", description = "Replacement text") String replacement) {
      return text == null || target == null
          ? text
          : text.replace(target, replacement == null ? "" : replacement);
    }

    @Verb(name = "substring", executionType = Verb.Type.FUNCTION,
        description = "Return text between inclusive start and exclusive end indices. Negative indices count from the end; bounds are clamped and reversed bounds return empty text. Preserve `null`.")
    public static String substring(
        RuntimeAgent.Scope scope,
        @Verb.Argument(name = "text", description = "Source text") String text,
        @Verb.Argument(name = "start", description = "Inclusive start index") int start,
        @Verb.Argument(name = "end", description = "Exclusive end index") int end) {
      return org.integratedmodelling.klab.api.utils.Utils.Strings.substring(text, start, end);
    }

    @Verb(name = "split", executionType = Verb.Type.FUNCTION,
        description = "Split by a literal separator, preserving empty fields. Null text returns an empty list; null or empty separators split into Unicode code points.")
    public static List<String> split(
        RuntimeAgent.Scope scope,
        @Verb.Argument(name = "text", description = "Text to split") String text,
        @Verb.Argument(name = "separator", description = "Literal separator") String separator) {
      if (text == null) {
        return List.of();
      }
      if (separator == null || separator.isEmpty()) {
        return text.codePoints().mapToObj(Character::toString).toList();
      }
      return List.of(text.split(Pattern.quote(separator), -1));
    }

    @Verb(name = "tokenize", executionType = Verb.Type.FUNCTION,
        description = "Split text on whitespace while preserving quoted phrases; null text returns an empty list.")
    public static List<String> tokenize(
        RuntimeAgent.Scope scope,
        @Verb.Argument(
                name = "text",
                description = "Text to split on whitespace while preserving quoted phrases")
            String text) {
      return text == null
          ? List.of()
          : List.copyOf(org.integratedmodelling.klab.api.utils.Utils.Strings.tokenize(text));
    }

    @Verb(name = "join", executionType = Verb.Type.FUNCTION,
        description = "Join rendered values with a separator. Null values render as `null`; a null separator means no separator and a null iterable returns empty text.")
    public static String join(
        RuntimeAgent.Scope scope,
        @Verb.Argument(name = "values", description = "Values to join") Iterable<?> values,
        @Verb.Argument(name = "separator", description = "Separator placed between values")
            String separator) {
      if (values == null) {
        return "";
      }
      var builder = new StringBuilder();
      for (var value : values) {
        if (!builder.isEmpty()) {
          builder.append(separator == null ? "" : separator);
        }
        builder.append(String.valueOf(value));
      }
      return builder.toString();
    }

    @Verb(name = "concat", executionType = Verb.Type.FUNCTION,
        description = "Concatenate rendered values without separators. Null elements render as `null`; a null argument array returns empty text.")
    public static String concat(
        RuntimeAgent.Scope scope,
        @Verb.Argument(name = "values", description = "Values to concatenate") Object... values) {
      if (values == null) {
        return "";
      }
      var builder = new StringBuilder();
      for (var value : values) {
        builder.append(String.valueOf(value));
      }
      return builder.toString();
    }

    @Verb(name = "repeat", executionType = Verb.Type.FUNCTION,
        description = "Repeat text the requested number of times; negative counts return empty text and null text stays null.")
    public static String repeat(
        RuntimeAgent.Scope scope,
        @Verb.Argument(name = "text", description = "Text to repeat") String text,
        @Verb.Argument(name = "times", description = "Number of repetitions") int times) {
      return text == null ? null : text.repeat(Math.max(0, times));
    }

    @Verb(name = "abbreviate", executionType = Verb.Type.FUNCTION,
        description = "Abbreviate text to a maximum width with an ellipsis using the shared string utility. Null stays null; too-small widths are invalid.")
    public static String abbreviate(
        RuntimeAgent.Scope scope,
        @Verb.Argument(name = "text", description = "Text to abbreviate") String text,
        @Verb.Argument(name = "width", description = "Maximum result width") int width) {
      return org.integratedmodelling.klab.api.utils.Utils.Strings.abbreviate(text, width);
    }
  }

  /** TODO: timer.at(datetime-string) timer.in(duration-string, quantity) */
  @Actor(name = "timer", description = "Time event generator")
  public static class Timer {

    /**
     * Supplier of an object at given time. Objects that are not constants must be dereferenced when
     * supplied.
     *
     * @param time
     * @param object
     * @return
     */
    @Verb(name = "at", executionType = Verb.Type.SUPPLIER,
        description = "Supply the given object at the requested time, or the current `TimeInstant` when the object is null. Past times complete immediately.")
    public static CompletableFuture<Object> at(
        RuntimeAgent.Scope scope, TimeInstant time, Object object) {
      Objects.requireNonNull(time, "time");

      return completeAfter(time.getMilliseconds() - System.currentTimeMillis(), object);
    }

    /**
     * Supplier of an object after a given interval from method call. Objects that are not constants
     * must be dereferenced when supplied.
     *
     * @param time
     * @param optionalObject
     * @return
     */
    @Verb(name = "in", executionType = Verb.Type.SUPPLIER,
        description = "Supply the first optional object after a temporal quantity, or the current `TimeInstant` when omitted or null. Nonpositive delays complete immediately; supported units: `ms`, `s`, `sec`, `min`, `h`, `hr`, `d`.")
    public static CompletableFuture<Object> in(
        RuntimeAgent.Scope scope, Quantity time, Object... optionalObject) {

      Objects.requireNonNull(time, "quantity");
      TimeUnit unit = extractTimeUnit(time);
      long amount = time.getValue().longValue();
      var millis = unit.toMillis(amount);

      if (optionalObject == null || optionalObject.length == 0) {
        return completeAfter(millis, null);
      }

      return completeAfter(millis, optionalObject[0]);
    }

    private static CompletableFuture<Object> completeAfter(long delayMilliseconds, Object object) {

      if (delayMilliseconds <= 0) {
        return CompletableFuture.completedFuture(object == null ? TimeInstant.create() : object);
      }

      var future = new CompletableFuture<Object>();
      var timer = new java.util.Timer(true);
      var task =
          new TimerTask() {
            @Override
            public void run() {
              future.complete(object == null ? TimeInstant.create() : object);
            }
          };
      timer.schedule(task, delayMilliseconds);
      future.whenComplete((value, throwable) -> timer.cancel());
      return future;
    }

    @Verb(name = "tick", executionType = Verb.Type.EMITTER, fires = TimeInstant.class,
        description = "Emit `TimeInstant` events immediately and then at fixed intervals until the scope completes. The interval must be positive; supported units: `ms`, `s`, `sec`, `min`, `h`, `hr`, `d`.")
    public static void tick(RuntimeAgent.Scope scope, Quantity quantity) {

      Objects.requireNonNull(quantity, "quantity");

      TimeUnit unit = extractTimeUnit(quantity);
      long amount = quantity.getValue().longValue();

      var timer = new java.util.Timer();
      TimerTask task =
          new TimerTask() {
            @Override
            public void run() {
              scope.doFire(TimeInstant.create());
            }
          };

      timer.scheduleAtFixedRate(task, 0, unit.toMillis(amount));

      // Wait until scope signals completion
      try {
        synchronized (scope) {
          while (!scope.isDone()) {
            scope.wait();
          }
        }
      } catch (InterruptedException e) {
        scope.done(e);
      }
      timer.cancel();
    }

    @Verb(name = "random", executionType = Verb.Type.EMITTER, fires = TimeInstant.class,
        description = "Emit `TimeInstant` events at randomized intervals around the given average until the scope completes. Supported units: `ms`, `s`, `sec`, `min`, `h`, `hr`, `d`.")
    public static void random(RuntimeAgent.Scope scope, Quantity quantity) {

      Objects.requireNonNull(quantity, "quantity");

      TimeUnit unit = extractTimeUnit(quantity);
      long amount = quantity.getValue().longValue();

      var timer = new java.util.Timer();
      scheduleRandomTick(scope, timer, unit.toMillis(amount));

      // Wait until scope signals completion
      try {
        synchronized (scope) {
          while (!scope.isDone()) {
            scope.wait();
          }
        }
      } catch (InterruptedException e) {
        scope.done(e);
      }
      timer.cancel();
    }

    private static void scheduleRandomTick(
        RuntimeAgent.Scope scope, java.util.Timer timer, long averageDelayMilliseconds) {

      if (scope.isDone()) {
        return;
      }

      TimerTask task =
          new TimerTask() {
            @Override
            public void run() {
              if (!scope.isDone()) {
                scope.doFire(TimeInstant.create());
              }
              scheduleRandomTick(scope, timer, averageDelayMilliseconds);
            }
          };

      timer.schedule(task, randomDelayMilliseconds(averageDelayMilliseconds));
    }

    private static long randomDelayMilliseconds(long averageDelayMilliseconds) {
      if (averageDelayMilliseconds <= 1) {
        return Math.max(0, averageDelayMilliseconds);
      }

      var minimumDelayMilliseconds = Math.max(1, averageDelayMilliseconds / 2);
      var maximumDelayMilliseconds =
          Math.max(
              minimumDelayMilliseconds + 1, averageDelayMilliseconds + minimumDelayMilliseconds);
      return ThreadLocalRandom.current()
          .nextLong(minimumDelayMilliseconds, maximumDelayMilliseconds);
    }
  }

  private static TimeUnit extractTimeUnit(Quantity quantity) {
    return switch (quantity.getUnit()) {
      case "ms" -> TimeUnit.MILLISECONDS;
      case "s", "sec" -> TimeUnit.SECONDS;
      case "d" -> TimeUnit.DAYS;
      case "min" -> TimeUnit.MINUTES;
      case "h", "hr" -> TimeUnit.HOURS;
      default ->
          throw new KlabIllegalArgumentException("Invalid time unit for quantity: " + quantity);
    };
  }
}
