package org.integratedmodelling.klab.runtime.libraries;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.TimerTask;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import org.integratedmodelling.klab.api.actors.RuntimeAgent;
import org.integratedmodelling.klab.api.knowledge.observation.scale.time.TimeDuration;
import org.integratedmodelling.klab.api.knowledge.observation.scale.time.TimeInstant;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.services.runtime.extension.Actor;
import org.integratedmodelling.klab.api.services.runtime.extension.Library;
import org.integratedmodelling.klab.api.services.runtime.extension.Verb;
import org.integratedmodelling.klab.runtime.kactors.AgentScope;

@Library(name = "core")
public class CoreActorLibrary {

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

    public Context(ContextScope context) {
      this.context = context;
    }

    /**
     * Constructor. Must take all context options
     *
     * @param agentScope
     * @return
     */
    @Verb(name = "new", executionType = Verb.Type.FUNCTION)
    public static Context createContext(AgentScope agentScope) {
      return null;
    }
  }

  @Actor(name = "log", description = "Logging actor")
  public static class Logger {

    // TODO info, warn, error, debug
    @Verb(name = "info", executionType = Verb.Type.FUNCTION, returns = Void.class)
    public static void info(RuntimeAgent.Scope scope, Object... messages) {}

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
        @Verb.Argument(name = "replacement", description = "Replacement text")
            String replacement) {
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
