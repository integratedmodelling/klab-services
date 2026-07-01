package org.integratedmodelling.klab.runtime.libraries;

import java.util.TimerTask;
import java.util.concurrent.TimeUnit;
import org.integratedmodelling.klab.api.actors.Agent;
import org.integratedmodelling.klab.api.knowledge.observation.scale.time.TimeInstant;
import org.integratedmodelling.klab.api.services.runtime.extension.Actor;
import org.integratedmodelling.klab.api.services.runtime.extension.Library;
import org.integratedmodelling.klab.api.services.runtime.extension.Verb;

@Library(name = "actors.core")
public class CoreActorLibrary {

  @Actor(
      name = "console",
      description =
          "A static actor that prints to whatever console was configured for the agent. All methods are static and can be called directly without instantiating the actor.")
  public static class Console {

    @Verb(name = "println", executionType = Verb.Type.FUNCTION)
    public static void println(Agent.Scope scope, Object message) {
      scope.getPrintWriter().println(message);
    }

    @Verb(name = "print", executionType = Verb.Type.FUNCTION)
    public static void print(Agent.Scope scope, Object message) {
      scope.getPrintWriter().println(message);
    }

    @Verb(name = "format", executionType = Verb.Type.FUNCTION)
    public static void format(Agent.Scope scope, String format, Object... args) {
      scope.getPrintWriter().format(format, args);
    }
  }

  @Actor(name = "timer", description = "Time event generator")
  public static class Timer {

    // return value being a Future means this is an emitter that stays until canceled through a
    // done() or failure() sent to the scope. If static, no need to instantiate the object.
    @Verb(name = "tick", executionType = Verb.Type.EMITTER)
    public static boolean random(Agent.Scope scope, TimeUnit unit, long amount) {

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
      return false;
    }
  }
}
