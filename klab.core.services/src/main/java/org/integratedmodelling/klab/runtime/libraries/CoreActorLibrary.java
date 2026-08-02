package org.integratedmodelling.klab.runtime.libraries;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import org.geotools.process.vector.TransformProcess;
import org.integratedmodelling.common.logging.Logging;
import org.integratedmodelling.klab.api.actors.RuntimeAgent;
import org.integratedmodelling.klab.api.authentication.ResourcePrivileges;
import org.integratedmodelling.klab.api.collections.Constant;
import org.integratedmodelling.klab.api.data.Metadata;
import org.integratedmodelling.klab.api.digitaltwin.DigitalTwin;
import org.integratedmodelling.klab.api.exceptions.KlabIllegalArgumentException;
import org.integratedmodelling.klab.api.exceptions.KlabIllegalStateException;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.api.knowledge.Urn;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.knowledge.observation.scale.time.TimeDuration;
import org.integratedmodelling.klab.api.knowledge.observation.scale.time.TimeInstant;
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

    @Verb(name = "println", executionType = Verb.Type.FUNCTION, returns = Void.class)
    public static void println(RuntimeAgent.Scope scope, Object... messages) {
      write(
          scope, RuntimeAgent.ConsoleMessageType.STDOUT, render(messages) + System.lineSeparator());
    }

    @Verb(name = "print", executionType = Verb.Type.FUNCTION, returns = Void.class)
    public static void print(RuntimeAgent.Scope scope, Object... messages) {
      write(scope, RuntimeAgent.ConsoleMessageType.STDOUT, render(messages));
    }

    @Verb(name = "format", executionType = Verb.Type.FUNCTION, returns = Void.class)
    public static void format(RuntimeAgent.Scope scope, String format, Object... args) {
      write(scope, RuntimeAgent.ConsoleMessageType.STDOUT, String.format(format, args));
    }

    @Verb(name = "printf", executionType = Verb.Type.FUNCTION, returns = Void.class)
    public static void printf(RuntimeAgent.Scope scope, String format, Object... args) {
      format(scope, format, args);
    }

    @Verb(name = "error", executionType = Verb.Type.FUNCTION, returns = Void.class)
    public static void error(RuntimeAgent.Scope scope, Object... messages) {
      write(scope, RuntimeAgent.ConsoleMessageType.STDERR, render(messages));
    }

    @Verb(name = "errorln", executionType = Verb.Type.FUNCTION, returns = Void.class)
    public static void errorln(RuntimeAgent.Scope scope, Object... messages) {
      write(
          scope, RuntimeAgent.ConsoleMessageType.STDERR, render(messages) + System.lineSeparator());
    }

    @Verb(name = "errorf", executionType = Verb.Type.FUNCTION, returns = Void.class)
    public static void errorf(RuntimeAgent.Scope scope, String format, Object... args) {
      write(scope, RuntimeAgent.ConsoleMessageType.STDERR, String.format(format, args));
    }

    @Verb(name = "flush", executionType = Verb.Type.FUNCTION, returns = Void.class)
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
      if (!scope.getAgent().sendToConsole(stream, text)) {
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

  public static class File {}

  // TODO non-static context actor that instruments the ContextScope
  // suppliers wrapping submit()
  // emitters for KG commits including matchers to select KG objects
  // emitters for failed observations and activities
  // (enum args matched to constants with lexical analysis)
  @Actor(name = "context", description = "Digital twin actor")
  public static class Context {

    private final ContextScope context;

    public Context() {
      this.context = null;
    }

    public Context(ContextScope context) {
      this.context = context;
    }

    /**
     * Constructor. Must take all context options.
     *
     * <p>TODO if we receive another context or more, we should build a meta-context
     *
     * @param agentScope
     * @return
     */
    @Verb(name = "new", executionType = Verb.Type.FUNCTION, description = "Create a new context")
    public static Context createContext(AgentScope agentScope, Object... args) {

      var aScope = agentScope.getAgent().getCreationScope();
      if (aScope instanceof SessionScope sessionScope) {

        var builder =
            DigitalTwin.Configuration.builder()
                .name(Utils.Collections.findElement(args, "Unnamed context"))
                .persistence(Utils.Collections.findElement(args, Persistence.ONE_OFF))
                .serviceId(aScope.getService(RuntimeService.class).serviceId())
                .serverUrl(aScope.getService(RuntimeService.class).getUrl())
                .owner(sessionScope.getUser().getUsername())
                .description(
                    "Created by agent "
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

    @Verb(
        name = "submit",
        description =
            """
            Submit an observation to the digital twin""")
    public CompletableFuture<Observation> submit(AgentScope scope, Object... arguments) {

      var runtimeService = context.getService(RuntimeService.class);

      var builder = Observation.builder(context);
      var metadata = Utils.Collections.findElement(arguments, Metadata.create());
      var definition = Utils.Collections.findElement(arguments, Map.class, metadata);
      var observable = Utils.Collections.findElement(arguments, KimObservable.class);
      var concept = Utils.Collections.findElement(arguments, KimConcept.class);
      var urn = Utils.Collections.findElement(arguments, Urn.class);
      var geometry = Utils.Collections.findElement(arguments, Geometry.class);

      String semanticDef =
          concept == null ? (observable == null ? null : observable.getUrn()) : concept.getUrn();
      Observable semantics = null;
      if (semanticDef != null) {
        semantics =
            scope
                .getAgent()
                .getCreationScope()
                .getService(Reasoner.class)
                .resolveObservable(semanticDef);
      }

      // definition MUST remain last
      var target = builder.observable(semantics).identity(urn).geometry(geometry).definition(definition).build();

      var submissionScope = context;
      if (metadata.get("within") instanceof Observation contextObservation) {
        submissionScope = submissionScope.within(contextObservation);
      } else if (metadata.get("source") instanceof Observation sourceObservation
          && metadata.get("target") instanceof Observation targetObservation)
        if (metadata.get("namespace") instanceof String namespace) {
          submissionScope = submissionScope.between(sourceObservation, targetObservation);
        }

      return runtimeService.submit(target, submissionScope);
    }
  }

  @Actor(name = "inspector", description = "Observation lifecycle inspector")
  public static class Inspector {

    @Verb(
        name = "viable",
        executionType = Verb.Type.FUNCTION,
        returns = Boolean.class,
        description =
            """
                        Check if an observation is viable; report through the log or the test case if run in a test.
                        Recognize if the call is downstream of an assertion in a test and fill in an assertion slot if so.
                        Returns true if the observation is viable, false otherwise, so it can be used in a conditional as
                        well as an assert.

                        The basic test is that the observation is not empty and has been resolved successfully if it
                        is a dependent. This can be extended or modified through metadata options.

                        Enabled metadata:

                        * `+nodata` fail for a quality if all the values are no-data
                        * `!nodata` fail for a quality if any of the values are no-data
                        * `!resolved` fail for a substantial if its resolution was empty

                        """)
    public static boolean checkViable(RuntimeAgent.Scope scope, Object... arguments) {
      return true;
    }
  }

  @Actor(name = "log", description = "Logging actor")
  public static class Logger {

    @Verb(name = "info", executionType = Verb.Type.FUNCTION)
    public static void info(RuntimeAgent.Scope scope, Object... messages) {
      var uscope = scope.getScope();
      if (uscope != null) {
        uscope.info(messages);
      } else {
        Logging.INSTANCE.info(messages);
      }
    }

    @Verb(name = "error", executionType = Verb.Type.FUNCTION)
    public static void error(RuntimeAgent.Scope scope, Object... messages) {
      var uscope = scope.getScope();
      if (uscope != null) {
        uscope.error(messages);
      } else {
        Logging.INSTANCE.error(messages);
      }
    }

    @Verb(name = "warning", executionType = Verb.Type.FUNCTION)
    public static void warning(RuntimeAgent.Scope scope, Object... messages) {
      var uscope = scope.getScope();
      if (uscope != null) {
        uscope.warn(messages);
      } else {
        Logging.INSTANCE.warn(messages);
      }
    }

    @Verb(name = "debug", executionType = Verb.Type.FUNCTION)
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

    @Verb(name = "lowercase", executionType = Verb.Type.FUNCTION)
    public static String lowercase(
        RuntimeAgent.Scope scope,
        @Verb.Argument(name = "text", description = "Text to convert") String text) {
      return text == null ? null : text.toLowerCase(Locale.ROOT);
    }

    @Verb(name = "uppercase", executionType = Verb.Type.FUNCTION)
    public static String uppercase(
        RuntimeAgent.Scope scope,
        @Verb.Argument(name = "text", description = "Text to convert") String text) {
      return text == null ? null : text.toUpperCase(Locale.ROOT);
    }

    @Verb(name = "capitalize", executionType = Verb.Type.FUNCTION)
    public static String capitalize(
        RuntimeAgent.Scope scope,
        @Verb.Argument(name = "text", description = "Text whose first character is capitalized")
            String text) {
      return org.integratedmodelling.klab.api.utils.Utils.Strings.capitalize(text);
    }

    @Verb(name = "labelize", executionType = Verb.Type.FUNCTION)
    public static String labelize(
        RuntimeAgent.Scope scope,
        @Verb.Argument(name = "identifier", description = "Identifier to turn into a label")
            String identifier) {
      return identifier == null
          ? null
          : org.integratedmodelling.klab.api.utils.Utils.Strings.labelizeIdentifier(identifier);
    }

    @Verb(name = "trim", executionType = Verb.Type.FUNCTION)
    public static String trim(
        RuntimeAgent.Scope scope,
        @Verb.Argument(name = "text", description = "Text to strip at both ends") String text) {
      return text == null ? null : text.strip();
    }

    @Verb(name = "normalize", executionType = Verb.Type.FUNCTION)
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

    @Verb(name = "length", executionType = Verb.Type.FUNCTION)
    public static int length(
        RuntimeAgent.Scope scope,
        @Verb.Argument(name = "text", description = "Text whose length is returned") String text) {
      return org.integratedmodelling.klab.api.utils.Utils.Strings.length(text);
    }

    @Verb(name = "isempty", executionType = Verb.Type.FUNCTION)
    public static boolean isEmpty(
        RuntimeAgent.Scope scope,
        @Verb.Argument(name = "text", description = "Text to test", optional = true) String text) {
      return org.integratedmodelling.klab.api.utils.Utils.Strings.isEmpty(text);
    }

    @Verb(name = "contains", executionType = Verb.Type.FUNCTION)
    public static boolean contains(
        RuntimeAgent.Scope scope,
        @Verb.Argument(name = "text", description = "Text to search") String text,
        @Verb.Argument(name = "fragment", description = "Literal fragment to find")
            String fragment) {
      return text != null && fragment != null && text.contains(fragment);
    }

    @Verb(name = "startswith", executionType = Verb.Type.FUNCTION)
    public static boolean startsWith(
        RuntimeAgent.Scope scope,
        @Verb.Argument(name = "text", description = "Text to inspect") String text,
        @Verb.Argument(name = "prefix", description = "Literal prefix") String prefix) {
      return text != null && prefix != null && text.startsWith(prefix);
    }

    @Verb(name = "endswith", executionType = Verb.Type.FUNCTION)
    public static boolean endsWith(
        RuntimeAgent.Scope scope,
        @Verb.Argument(name = "text", description = "Text to inspect") String text,
        @Verb.Argument(name = "suffix", description = "Literal suffix") String suffix) {
      return text != null && suffix != null && text.endsWith(suffix);
    }

    @Verb(name = "equalsignorecase", executionType = Verb.Type.FUNCTION)
    public static boolean equalsIgnoreCase(
        RuntimeAgent.Scope scope,
        @Verb.Argument(name = "text", description = "First text") String text,
        @Verb.Argument(name = "other", description = "Text to compare") String other) {
      return text == null ? other == null : other != null && text.equalsIgnoreCase(other);
    }

    @Verb(name = "indexof", executionType = Verb.Type.FUNCTION)
    public static int indexOf(
        RuntimeAgent.Scope scope,
        @Verb.Argument(name = "text", description = "Text to search") String text,
        @Verb.Argument(name = "fragment", description = "Literal fragment to find")
            String fragment) {
      return text == null || fragment == null ? -1 : text.indexOf(fragment);
    }

    @Verb(name = "count", executionType = Verb.Type.FUNCTION)
    public static int count(
        RuntimeAgent.Scope scope,
        @Verb.Argument(name = "text", description = "Text to search") String text,
        @Verb.Argument(name = "fragment", description = "Literal fragment to count")
            String fragment) {
      return org.integratedmodelling.klab.api.utils.Utils.Strings.countMatches(text, fragment);
    }

    @Verb(name = "matches", executionType = Verb.Type.FUNCTION)
    public static boolean matches(
        RuntimeAgent.Scope scope,
        @Verb.Argument(name = "text", description = "Text to test") String text,
        @Verb.Argument(name = "regex", description = "Java regular expression") String regex) {
      return text != null && regex != null && Pattern.matches(regex, text);
    }

    @Verb(name = "replace", executionType = Verb.Type.FUNCTION)
    public static String replace(
        RuntimeAgent.Scope scope,
        @Verb.Argument(name = "text", description = "Text to modify") String text,
        @Verb.Argument(name = "target", description = "Literal text to replace") String target,
        @Verb.Argument(name = "replacement", description = "Replacement text") String replacement) {
      return text == null || target == null
          ? text
          : text.replace(target, replacement == null ? "" : replacement);
    }

    @Verb(name = "substring", executionType = Verb.Type.FUNCTION)
    public static String substring(
        RuntimeAgent.Scope scope,
        @Verb.Argument(name = "text", description = "Source text") String text,
        @Verb.Argument(name = "start", description = "Inclusive start index") int start,
        @Verb.Argument(name = "end", description = "Exclusive end index") int end) {
      return org.integratedmodelling.klab.api.utils.Utils.Strings.substring(text, start, end);
    }

    @Verb(name = "split", executionType = Verb.Type.FUNCTION)
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

    @Verb(name = "tokenize", executionType = Verb.Type.FUNCTION)
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

    @Verb(name = "join", executionType = Verb.Type.FUNCTION)
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

    @Verb(name = "concat", executionType = Verb.Type.FUNCTION)
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

    @Verb(name = "repeat", executionType = Verb.Type.FUNCTION)
    public static String repeat(
        RuntimeAgent.Scope scope,
        @Verb.Argument(name = "text", description = "Text to repeat") String text,
        @Verb.Argument(name = "times", description = "Number of repetitions") int times) {
      return text == null ? null : text.repeat(Math.max(0, times));
    }

    @Verb(name = "abbreviate", executionType = Verb.Type.FUNCTION)
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
     * @param <T>
     */
    @Verb(name = "at", executionType = Verb.Type.SUPPLIER)
    public static <T> CompletableFuture<T> at(
        RuntimeAgent.Scope scope, TimeInstant time, T object) {

      Objects.requireNonNull(time, "time");
      return completeAfter(time.getMilliseconds() - System.currentTimeMillis(), object);
    }

    /**
     * Supplier of an object after a given interval from method call. Objects that are not constants
     * must be dereferenced when supplied.
     *
     * @param time
     * @param object
     * @return
     * @param <T>
     */
    @Verb(name = "in", executionType = Verb.Type.SUPPLIER)
    public static <T> CompletableFuture<T> in(
        RuntimeAgent.Scope scope, TimeDuration time, T object) {

      Objects.requireNonNull(time, "time");
      return completeAfter(time.getMilliseconds(), object);
    }

    private static <T> CompletableFuture<T> completeAfter(long delayMilliseconds, T object) {
      if (delayMilliseconds <= 0) {
        return CompletableFuture.completedFuture(object);
      }

      var future = new CompletableFuture<T>();
      var timer = new java.util.Timer(true);
      var task =
          new TimerTask() {
            @Override
            public void run() {
              future.complete(object);
            }
          };
      timer.schedule(task, delayMilliseconds);
      future.whenComplete((value, throwable) -> timer.cancel());
      return future;
    }

    @Verb(name = "tick", fires = TimeInstant.class)
    public static void tick(RuntimeAgent.Scope scope, TimeUnit unit, long amount) {

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

    @Verb(name = "random", fires = TimeInstant.class)
    public static void random(RuntimeAgent.Scope scope, TimeUnit unit, long amount) {

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
}
