package org.integratedmodelling.klab.runtime.libraries;

import java.util.TimerTask;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.integratedmodelling.klab.api.knowledge.observation.scale.time.TimeInstant;
import org.integratedmodelling.klab.api.lang.kactors.KActorsBehavior;
import org.integratedmodelling.klab.api.scope.SessionScope;
import org.integratedmodelling.klab.api.services.runtime.extension.Actor;
import org.integratedmodelling.klab.api.services.runtime.extension.Library;
import org.integratedmodelling.klab.api.services.runtime.extension.Verb;
import org.integratedmodelling.klab.runtime.kactors.actors.runtime.ActionScope;
import org.integratedmodelling.klab.runtime.kactors.compiler.ActorBase;

@Library(name = "actors.core")
public class CoreActorLibrary {

  @Actor(
      name = "console",
      description =
          "A simple actor that prints to the console. All methods are static and can be called directly without instantiating the actor.")
  public static class Console extends ActorBase {

    public Console(KActorsBehavior behavior) {
      super(behavior);
    }

    @Override
    protected ActionScope main(ActionScope initialScope, SessionScope session) {
      return null;
    }

    @Verb(name = "print")
    public static void print(Object message) {}
  }

  @Actor(name = "timer", description = "Time event generator")
  public static class Timer extends ActorBase {

    public Timer(KActorsBehavior behavior) {
      super(behavior);
    }

    @Override
    protected ActionScope main(ActionScope initialScope, SessionScope session) {
      return null;
    }

    // return value being a Future means this is an emitter that stays until canceled through a
    // done() or failure() sent to the scope. If static, no need to instantiate the object.
    @Verb(name = "tick")
    public static CompletableFuture<ExitValue> random(
        ActionScope scope, TimeUnit unit, long amount) {
      return CompletableFuture.supplyAsync(
              () -> {
                var timer = new java.util.Timer();
                TimerTask task =
                    new TimerTask() {
                      @Override
                      public void run() {
                        scope.fire(TimeInstant.create());
                      }
                    };

                timer.scheduleAtFixedRate(task, 0, unit.toMillis(amount));

                // Wait until scope signals completion
                try {
                  while (!scope.isDone()) {
                    synchronized (scope) {
                      scope.wait();
                    }
                  }
                } catch (InterruptedException e) {
                  scope.failure(e);
                }
                timer.cancel();
                return ExitValue.success(true);
              })
          .exceptionally(scope::failure);
    }
  }
}
