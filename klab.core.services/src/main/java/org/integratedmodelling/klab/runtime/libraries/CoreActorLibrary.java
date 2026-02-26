package org.integratedmodelling.klab.runtime.libraries;

import org.integratedmodelling.klab.api.lang.Quantity;
import org.integratedmodelling.klab.api.lang.kactors.KActorsBehavior;
import org.integratedmodelling.klab.api.scope.SessionScope;
import org.integratedmodelling.klab.api.services.runtime.extension.Actor;
import org.integratedmodelling.klab.api.services.runtime.extension.Library;
import org.integratedmodelling.klab.api.services.runtime.extension.Verb;
import org.integratedmodelling.klab.runtime.kactors.actors.runtime.ActionScope;
import org.integratedmodelling.klab.runtime.kactors.compiler.ActorBase;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

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

    // return value means this is an emitter that stays until canceled (through a return statement).
    // If
    // static, no need to instantiate the object.
    @Verb(name = "random")
    public static CompletableFuture<ExitValue> random(TimeUnit unit, long amount) {

      return null;
    }
  }
}
