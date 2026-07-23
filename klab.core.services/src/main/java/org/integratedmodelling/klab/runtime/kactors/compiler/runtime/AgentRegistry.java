package org.integratedmodelling.klab.runtime.kactors.compiler.runtime;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.Serializable;
import java.lang.reflect.InvocationTargetException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.FileObject;
import javax.tools.ForwardingJavaFileManager;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileManager;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import org.integratedmodelling.common.runtime.actors.AgentImpl;
import org.integratedmodelling.klab.api.actors.Agent;
import org.integratedmodelling.klab.api.data.Version;
import org.integratedmodelling.klab.api.lang.kactors.KActorsBehavior;
import org.integratedmodelling.klab.api.scope.Scope;
import org.integratedmodelling.klab.api.scope.ServiceSideScope;
import org.integratedmodelling.klab.api.scope.SessionScope;
import org.integratedmodelling.klab.api.scope.UserScope;
import org.integratedmodelling.klab.api.services.ResourcesService;
import org.integratedmodelling.klab.api.services.runtime.Notification;
import org.integratedmodelling.klab.runtime.kactors.RuntimeAgentBase;
import org.integratedmodelling.klab.runtime.kactors.compiler.AgentCompiler;

/**
 * Runtime-wide registry for compiled k.Actors classes and their live instances.
 *
 * <p>Compilation results, including failures, are cached by behavior identity. Generated classes
 * are loaded by a registry-owned class loader, so no source or class files are written to disk.
 * Instance handles are indexed separately by their runtime URN.
 */
public enum AgentRegistry {
  INSTANCE;

  /** Passed to {@link #getOrCreateAgent(Agent, Scope, Options...)}. */
  public enum Options {
    /** Include the generated Java code in service-side handles that support it. */
    INCLUDE_JAVA_CODE,
    /** Stop after source generation and validation; do not compile or instantiate the class. */
    DO_NOT_COMPILE_JAVA
  }

  private record BehaviorKey(String urn, Version version, long timestamp) {

    BehaviorKey(KActorsBehavior behavior) {
      this(behavior.getUrn(), behavior.getVersion(), behavior.getCreationTimestamp());
    }
  }

  private record CompiledBehavior(
      Class<? extends RuntimeAgentBase> agentClass,
      String source,
      List<Notification> notifications) {

    boolean successful() {
      return agentClass != null;
    }
  }

  private final ConcurrentMap<BehaviorKey, CompiledBehavior> classes = new ConcurrentHashMap<>();
  private final ConcurrentMap<String, ManagedAgent> instances = new ConcurrentHashMap<>();
  private final AtomicLong nextAgentId = new AtomicLong();

  /**
   * Resolve the behavior named by the supplied handle and return a stopped runtime agent.
   *
   * <p>If the handle already identifies a registered instance, that canonical handle is returned.
   * Otherwise its behavior URN is resolved through the resources service in the supplied user
   * scope, compiled (or retrieved from the class cache), instantiated, and registered.
   */
  public Agent getOrCreateAgent(Agent agent, Scope scope, Options... options) {
    Objects.requireNonNull(agent, "agent");
    Objects.requireNonNull(scope, "scope");

    if (agent.getUrn() != null) {
      var existing = instances.get(agent.getUrn());
      if (existing != null) {
        return existing;
      }
    }
    if (!(scope instanceof UserScope userScope)) {
      return failedHandle(
          agent, "A user, session, or context scope is required to resolve an agent behavior");
    }
    if (agent.getBehaviorUrn() == null || agent.getBehaviorUrn().isBlank()) {
      return failedHandle(agent, "An agent behavior URN is required");
    }

    try {
      var resources = scope.getService(ResourcesService.class);
      var behavior =
          resources == null
              ? null
              : resources.retrieve(agent.getBehaviorUrn(), KActorsBehavior.class, userScope);
      if (behavior == null) {
        return failedHandle(agent, "Cannot resolve k.Actors behavior " + agent.getBehaviorUrn());
      }
      return getOrCreateAgent(agent, behavior, scope, options);
    } catch (Throwable failure) {
      return failedHandle(
          agent,
          "Cannot resolve k.Actors behavior " + agent.getBehaviorUrn(),
          unwrap(failure));
    }
  }

  /**
   * Variant for callers, such as the runtime ingestion endpoint, that already possess the parsed
   * behavior. It also provides the seam needed for initialization arguments in a future API.
   */
  public Agent getOrCreateAgent(
      Agent agent, KActorsBehavior behavior, Scope scope, Options... options) {
    Objects.requireNonNull(agent, "agent");
    Objects.requireNonNull(behavior, "behavior");

    if (agent.getUrn() != null) {
      var existing = instances.get(agent.getUrn());
      if (existing != null) {
        return existing;
      }
    }
    if (behavior.getUrn() == null || behavior.getUrn().isBlank()) {
      return failedHandle(agent, "The parsed k.Actors behavior has no URN");
    }
    if (agent.getBehaviorUrn() != null
        && !agent.getBehaviorUrn().isBlank()
        && !behavior.getUrn().equals(agent.getBehaviorUrn())) {
      return failedHandle(
          agent,
          "Requested behavior "
              + agent.getBehaviorUrn()
              + " does not match parsed behavior "
              + behavior.getUrn());
    }

    var requestedOptions =
        options == null || options.length == 0
            ? java.util.Set.<Options>of()
            : java.util.Set.copyOf(Arrays.asList(options));
    if (requestedOptions.contains(Options.DO_NOT_COMPILE_JAVA)) {
      var translated = translateBehavior(behavior, scope);
      var sourceOnly = copyHandle(agent, behavior);
      sourceOnly.getNotifications().addAll(translated.notifications());
      sourceOnly.setViable(translated.source() != null);
      if (requestedOptions.contains(Options.INCLUDE_JAVA_CODE)) {
        sourceOnly.setJavaCode(translated.source());
      }
      return sourceOnly;
    }

    var key = new BehaviorKey(behavior);
    CompiledBehavior compiled;
    try {
      compiled = classes.computeIfAbsent(key, ignored -> compileBehavior(behavior, scope));
    } catch (Throwable failure) {
      return failedHandle(agent, "Unexpected failure compiling " + behavior.getUrn(), unwrap(failure));
    }

    if (!compiled.successful()) {
      var failed = copyHandle(agent, behavior);
      failed.getNotifications().addAll(compiled.notifications());
      failed.setViable(false);
      if (requestedOptions.contains(Options.INCLUDE_JAVA_CODE)) {
        failed.setJavaCode(compiled.source());
      }
      return failed;
    }

    try {
      var runtime = instantiate(compiled.agentClass(), behavior, scope);
      String urn = createAgentUrn(scope);
      var managed =
          new ManagedAgent(
              urn,
              behavior.getUrn(),
              chooseName(agent, behavior),
              runtime,
              compiled.notifications(),
              requestedOptions.contains(Options.INCLUDE_JAVA_CODE) ? compiled.source() : null);
      instances.put(urn, managed);
      return managed;
    } catch (Throwable failure) {
      var failed = copyHandle(agent, behavior);
      failed.getNotifications().addAll(compiled.notifications());
      failed
          .getNotifications()
          .add(
              Notification.error(
                  "Cannot instantiate compiled agent " + behavior.getUrn(), unwrap(failure)));
      failed.setViable(false);
      if (requestedOptions.contains(Options.INCLUDE_JAVA_CODE)) {
        failed.setJavaCode(compiled.source());
      }
      return failed;
    }
  }

  /** Return the registered handle, or {@code null} if the URN is not active/retained. */
  public Agent getAgent(String urn) {
    return urn == null ? null : instances.get(urn);
  }

  /** Return the cached class for the exact behavior revision, or {@code null}. */
  public Class<? extends RuntimeAgentBase> getCompiledClass(KActorsBehavior behavior) {
    if (behavior == null) {
      return null;
    }
    var compiled = classes.get(new BehaviorKey(behavior));
    return compiled == null ? null : compiled.agentClass();
  }

  public int getCompiledBehaviorCount() {
    return classes.size();
  }

  public int getRegisteredAgentCount() {
    return instances.size();
  }

  /**
   * Release a retained, stopped finite agent. Running agents must be stopped through their handle
   * before they can be removed.
   */
  public boolean releaseAgent(String urn) {
    var agent = instances.get(urn);
    return agent != null && !agent.isAlive() && instances.remove(urn, agent);
  }

  private CompiledBehavior compileBehavior(KActorsBehavior behavior, Scope scope) {
    var notifications = new ArrayList<Notification>();
    String source = null;
    try {
      var compiler =
          scope instanceof UserScope userScope
              ? new AgentCompiler(behavior, userScope)
              : new AgentCompiler(behavior);
      if (!compiler.compile()) {
        notifications.addAll(compiler.getNotifications());
        return new CompiledBehavior(null, compiler.getSourceCode(), List.copyOf(notifications));
      }
      notifications.addAll(compiler.getNotifications());
      source = compiler.getSourceCode();
      var loaded = compileJava(compiler.getGeneratedSources(), compiler.getQualifiedClassName());
      if (!RuntimeAgentBase.class.isAssignableFrom(loaded)) {
        notifications.add(
            Notification.error(
                "Generated class "
                    + loaded.getName()
                    + " does not derive from "
                    + RuntimeAgentBase.class.getName()));
        return new CompiledBehavior(null, source, List.copyOf(notifications));
      }
      @SuppressWarnings("unchecked")
      var agentClass = (Class<? extends RuntimeAgentBase>) loaded;
      AgentCompiler.registerCompiledClass(behavior.getUrn(), agentClass);
      return new CompiledBehavior(agentClass, source, List.copyOf(notifications));
    } catch (JavaCompilationException failure) {
      notifications.addAll(failure.notifications());
    } catch (Throwable failure) {
      notifications.add(
          Notification.error(
              "Cannot compile k.Actors behavior " + behavior.getUrn(), unwrap(failure)));
    }
    return new CompiledBehavior(null, source, List.copyOf(notifications));
  }

  private CompiledBehavior translateBehavior(KActorsBehavior behavior, Scope scope) {

    /*
    FIXME - supply the compiler with a sensible validator and resolver!
     */

    var compiler =
        scope instanceof UserScope userScope
            ? new AgentCompiler(behavior, userScope)
            : new AgentCompiler(behavior);
    try {
      boolean success = compiler.compile();
      return new CompiledBehavior(
          null,
          success ? compiler.getSourceCode() : null,
          List.copyOf(compiler.getNotifications()));
    } catch (Throwable failure) {
      var notifications = new ArrayList<>(compiler.getNotifications());
      notifications.add(
          Notification.error(
              "Cannot translate k.Actors behavior " + behavior.getUrn(), unwrap(failure)));
      return new CompiledBehavior(null, null, List.copyOf(notifications));
    }
  }

  private RuntimeAgentBase instantiate(
      Class<? extends RuntimeAgentBase> agentClass, KActorsBehavior behavior, Scope scope)
      throws ReflectiveOperationException {
    var constructor =
        agentClass.getConstructor(
            KActorsBehavior.class, SessionScope.class, Map.class, Object[].class);
    var sessionScope = scope instanceof SessionScope session ? session : null;
    return constructor.newInstance(behavior, sessionScope, Map.of(), (Object) new Object[0]);
  }

  private Class<?> compileJava(Map<String, String> sources, String primaryClassName)
      throws JavaCompilationException, ClassNotFoundException {
    var javaCompiler = ToolProvider.getSystemJavaCompiler();
    if (javaCompiler == null) {
      throw new JavaCompilationException(
          List.of(
              Notification.error(
                  "No system Java compiler is available; the runtime must run on a JDK")));
    }
    if (sources == null || sources.isEmpty() || primaryClassName == null) {
      throw new JavaCompilationException(
          List.of(Notification.error("Agent source generation produced no compilable Java class")));
    }

    var diagnostics = new DiagnosticCollector<JavaFileObject>();
    try (StandardJavaFileManager standard =
            javaCompiler.getStandardFileManager(diagnostics, null, null);
        MemoryFileManager files = new MemoryFileManager(standard)) {
      var units =
          sources.values().stream()
              .map(source -> new SourceFile(binaryName(source), source))
              .toList();
      var task =
          javaCompiler.getTask(
              null,
              files,
              diagnostics,
              List.of("-proc:none", "-classpath", System.getProperty("java.class.path")),
              null,
              units);
      if (!Boolean.TRUE.equals(task.call())) {
        throw new JavaCompilationException(javaDiagnostics(diagnostics));
      }
      return files.classLoader(getClass().getClassLoader()).loadClass(primaryClassName);
    } catch (IOException failure) {
      throw new JavaCompilationException(
          List.of(Notification.error("Cannot close the in-memory Java compiler", failure)));
    }
  }

  private List<Notification> javaDiagnostics(
      DiagnosticCollector<JavaFileObject> diagnostics) {
    var ret = new ArrayList<Notification>();
    for (var diagnostic : diagnostics.getDiagnostics()) {
      if (diagnostic.getKind() == Diagnostic.Kind.ERROR) {
        String source =
            diagnostic.getSource() == null ? "<generated agent>" : diagnostic.getSource().getName();
        ret.add(
            Notification.error(
                source
                    + ":"
                    + diagnostic.getLineNumber()
                    + ":"
                    + diagnostic.getColumnNumber()
                    + " "
                    + diagnostic.getMessage(null)));
      }
    }
    if (ret.isEmpty()) {
      ret.add(Notification.error("The Java compiler rejected the generated agent source"));
    }
    return List.copyOf(ret);
  }

  private String binaryName(String source) {
    var packageMatcher =
        java.util.regex.Pattern.compile("(?m)^package\\s+([\\w.]+)\\s*;").matcher(source);
    var classMatcher =
        java.util.regex.Pattern.compile("(?m)^public\\s+(?:final\\s+)?class\\s+(\\w+)")
            .matcher(source);
    if (!classMatcher.find()) {
      throw new IllegalArgumentException("Generated Java source contains no public class");
    }
    return packageMatcher.find()
        ? packageMatcher.group(1) + "." + classMatcher.group(1)
        : classMatcher.group(1);
  }

  private AgentImpl copyHandle(Agent request, KActorsBehavior behavior) {
    var ret = new AgentImpl();
    ret.setBehaviorUrn(behavior.getUrn());
    ret.setName(chooseName(request, behavior));
    return ret;
  }

  private Agent failedHandle(Agent request, Object... failure) {
    var ret = new AgentImpl();
    ret.setBehaviorUrn(request.getBehaviorUrn());
    ret.setName(
        request.getName() == null || request.getName().isBlank() ? "agent" : request.getName());
    ret.setViable(false);
    ret.getNotifications().add(Notification.error(failure));
    return ret;
  }

  private String chooseName(Agent request, KActorsBehavior behavior) {
    if (request.getName() != null && !request.getName().isBlank()) {
      return request.getName();
    }
    String urn = behavior.getUrn();
    int separator = Math.max(urn.lastIndexOf('.'), urn.lastIndexOf(':'));
    return separator < 0 ? urn : urn.substring(separator + 1);
  }

  private String createAgentUrn(Scope scope) {
    String scopeId;
    if (scope instanceof ServiceSideScope serviceScope
        && serviceScope.getId() != null
        && !serviceScope.getId().isBlank()) {
      scopeId = serviceScope.getId();
    } else if (scope instanceof SessionScope sessionScope
        && sessionScope.getId() != null
        && !sessionScope.getId().isBlank()) {
      scopeId = sessionScope.getId();
    } else {
      scopeId = "runtime";
    }
    return scopeId + ":agent:" + nextAgentId.incrementAndGet();
  }

  private static Throwable unwrap(Throwable failure) {
    if (failure instanceof InvocationTargetException invocation
        && invocation.getTargetException() != null) {
      return invocation.getTargetException();
    }
    return failure;
  }

  /** Service-side handle backed by an instantiated generated agent. */
  public final class ManagedAgent implements Agent {

    private final String urn;
    private final String behaviorUrn;
    private final String name;
    private final RuntimeAgentBase runtime;
    private final List<Notification> notifications;
    private final String javaCode;
    private volatile boolean viable = true;

    private ManagedAgent(
        String urn,
        String behaviorUrn,
        String name,
        RuntimeAgentBase runtime,
        List<Notification> notifications,
        String javaCode) {
      this.urn = urn;
      this.behaviorUrn = behaviorUrn;
      this.name = name;
      this.runtime = runtime;
      this.notifications = new CopyOnWriteArrayList<>(notifications);
      this.javaCode = javaCode;
    }

    @Override
    public String getUrn() {
      return urn;
    }

    @Override
    public String getBehaviorUrn() {
      return behaviorUrn;
    }

    @Override
    public String getName() {
      return name;
    }

    @Override
    public boolean isViable() {
      return viable;
    }

    @Override
    public boolean isAlive() {
      return "running".equals(runtime.status());
    }

    @Override
    public boolean start(Object... arguments) {
      if (!viable) {
        return false;
      }
      if (arguments != null && arguments.length > 0) {
        notifications.add(
            Notification.warning(
                "Initialization parameters are not yet supported after agent construction"));
      }
      try {
        var result = runtime.run();
        if (result != null && result.getErrorCode() != 0) {
          viable = false;
          notifications.add(
              Notification.error(
                  result.getErrorMessage() == null
                      ? "Agent startup failed"
                      : result.getErrorMessage()));
          return false;
        }
        return true;
      } catch (Throwable failure) {
        viable = false;
        notifications.add(Notification.error("Agent startup failed", unwrap(failure)));
        return false;
      }
    }

    @Override
    public boolean stop() {
      try {
        runtime.stop();
        return true;
      } catch (Throwable failure) {
        notifications.add(Notification.error("Agent stop failed", unwrap(failure)));
        return false;
      } finally {
        instances.remove(urn, this);
      }
    }

    @Override
    public List<Notification> getNotifications() {
      return notifications;
    }

    /** Included in JSON/debug output only when explicitly requested. */
    public String getJavaCode() {
      return javaCode;
    }

    @Override
    public <T extends Serializable> void tell(T message) {
      notifications.add(
          Notification.warning("Direct serializable agent messaging is not implemented yet"));
    }

    @Override
    public <T extends Serializable, R extends Serializable> CompletableFuture<R> ask(
        T message, Class<? extends R> responseClass) {
      return CompletableFuture.failedFuture(
          new UnsupportedOperationException(
              "Direct serializable agent messaging is not implemented yet"));
    }
  }

  private static final class SourceFile extends SimpleJavaFileObject {

    private final String source;

    private SourceFile(String binaryName, String source) {
      super(
          URI.create("string:///" + binaryName.replace('.', '/') + JavaFileObject.Kind.SOURCE.extension),
          JavaFileObject.Kind.SOURCE);
      this.source = source;
    }

    @Override
    public CharSequence getCharContent(boolean ignoreEncodingErrors) {
      return source;
    }
  }

  private static final class ClassFile extends SimpleJavaFileObject {

    private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();

    private ClassFile(String binaryName, Kind kind) {
      super(URI.create("memory:///" + binaryName.replace('.', '/') + kind.extension), kind);
    }

    @Override
    public OutputStream openOutputStream() {
      return bytes;
    }

    private byte[] bytes() {
      return bytes.toByteArray();
    }
  }

  private static final class MemoryFileManager
      extends ForwardingJavaFileManager<StandardJavaFileManager> {

    private final ConcurrentMap<String, ClassFile> classes = new ConcurrentHashMap<>();

    private MemoryFileManager(StandardJavaFileManager delegate) {
      super(delegate);
    }

    @Override
    public JavaFileObject getJavaFileForOutput(
        JavaFileManager.Location location,
        String className,
        JavaFileObject.Kind kind,
        FileObject sibling) {
      var output = new ClassFile(className, kind);
      classes.put(className, output);
      return output;
    }

    private ClassLoader classLoader(ClassLoader parent) {
      var bytecode = new ConcurrentHashMap<String, byte[]>();
      classes.forEach((name, file) -> bytecode.put(name, file.bytes()));
      return new ClassLoader(parent) {
        @Override
        protected Class<?> findClass(String name) throws ClassNotFoundException {
          var bytes = bytecode.get(name);
          if (bytes == null) {
            return super.findClass(name);
          }
          return defineClass(name, bytes, 0, bytes.length);
        }
      };
    }
  }

  private static final class JavaCompilationException extends Exception {

    private final List<Notification> notifications;

    private JavaCompilationException(List<Notification> notifications) {
      super("Generated Java source did not compile");
      this.notifications = List.copyOf(notifications);
    }

    private List<Notification> notifications() {
      return notifications;
    }
  }
}
