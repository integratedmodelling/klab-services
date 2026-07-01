package org.integratedmodelling.klab.runtime.kactors.actors.runtime;

import java.io.PrintStream;
import java.io.PrintWriter;
import org.integratedmodelling.common.logging.Logging;
import org.integratedmodelling.klab.api.actors.Agent;
import org.integratedmodelling.klab.api.collections.impl.ParametersImpl;
import org.integratedmodelling.klab.runtime.kactors.compiler.ActorBase;
import reactor.core.publisher.Sinks;

// represents each action during execution, providing access to the k.Actors environment and hashes
// for local variables
public abstract class AgentScope extends ParametersImpl<String> implements Agent.Scope {

  private final ActorBase actor;
  boolean done = false;
  private long actionId;

  public AgentScope(ActorBase actor) {
    this.actor = actor;
  }

  public AgentScope(AgentScope parent, long actionId) {
    this.actor = parent.actor;
    this.actionId = actionId;
  }

  @Override
  public Agent getActor() {
    return actor;
  }

  /**
   * Each action has an id that is unique within the actor and is used in filtering events that
   * pertain to the action. The 0 value is reserved for the root scope.
   *
   * @return
   */
  public long actionId() {
    return actionId;
  }

  @Override
  public void doFire(Object firedObject) {
    var result =
        actor
            .getEventBus()
            .tryEmitNext(
                new ActorBase.Event(ActorBase.EventType.RETURN, actor.id, actionId, firedObject));
    if (result != Sinks.EmitResult.OK) {
      // TODO handle error
      Logging.INSTANCE.error("Failed to emit return event");
    }
  }

  @Override
  public void doReturn(Object returnedObject) {
    var result =
        actor
            .getEventBus()
            .tryEmitNext(
                new ActorBase.Event(
                    ActorBase.EventType.RETURN, actor.id, actionId, returnedObject));
    if (result == Sinks.EmitResult.OK) {
      // remove listener due to the RETURN-type subscription
      done = true;
    } else {
      // TODO handle error
      Logging.INSTANCE.error("Failed to emit return event");
    }
  }

  @Override
  public PrintStream getPrintWriter() {
    // TODO configuration!
    return System.out;
  }

  public boolean isDone() {
    return done;
  }

  @Override
  public void done(Object... conditions) {}
}
