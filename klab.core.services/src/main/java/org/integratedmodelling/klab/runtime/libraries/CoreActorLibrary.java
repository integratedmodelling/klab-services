package org.integratedmodelling.klab.runtime.libraries;

import java.util.Objects;
import java.util.TimerTask;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import org.integratedmodelling.klab.api.actors.Agent;
import org.integratedmodelling.klab.api.knowledge.observation.scale.time.TimeDuration;
import org.integratedmodelling.klab.api.knowledge.observation.scale.time.TimeInstant;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.services.runtime.extension.Actor;
import org.integratedmodelling.klab.api.services.runtime.extension.Library;
import org.integratedmodelling.klab.api.services.runtime.extension.Verb;
import org.integratedmodelling.klab.runtime.kactors.AgentScope;

@Library(name = "actors.core")
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

    // TODO handle multiple arguments
    @Verb(name = "println", executionType = Verb.Type.FUNCTION, returns = Void.class)
    public static void println(Agent.Scope scope, Object message) {
      scope.getPrintWriter().println(message);
    }

    // TODO handle multiple arguments
    @Verb(name = "print", executionType = Verb.Type.FUNCTION, returns = Void.class)
    public static void print(Agent.Scope scope, Object message) {
      scope.getPrintWriter().println(message);
    }

    @Verb(name = "format", executionType = Verb.Type.FUNCTION, returns = Void.class)
    public static void format(Agent.Scope scope, String format, Object... args) {
      scope.getPrintWriter().format(format, args);
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

    // TODO emitter that catches log entries from the code with pattern

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
    public static <T> CompletableFuture<T> at(Agent.Scope scope, TimeInstant time, T object) {

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
    public static <T> CompletableFuture<T> in(Agent.Scope scope, TimeDuration time, T object) {

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
    public static void tick(Agent.Scope scope, TimeUnit unit, long amount) {

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
    public static void random(Agent.Scope scope, TimeUnit unit, long amount) {

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
        Agent.Scope scope, java.util.Timer timer, long averageDelayMilliseconds) {

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
