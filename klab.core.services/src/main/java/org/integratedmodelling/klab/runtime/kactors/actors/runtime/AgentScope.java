package org.integratedmodelling.klab.runtime.kactors.actors.runtime;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.integratedmodelling.common.logging.Logging;
import org.integratedmodelling.klab.api.actors.Agent;
import org.integratedmodelling.klab.api.collections.impl.ParametersImpl;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.scope.SessionScope;
import org.integratedmodelling.klab.api.services.runtime.Notification;
import org.integratedmodelling.klab.runtime.kactors.compiler.AgentBase;
import reactor.core.publisher.Sinks;

// represents each action during execution, providing access to the k.Actors environment and hashes
// for local variables
public abstract class AgentScope extends ParametersImpl<String> implements Agent.Scope {

  private final AgentBase actor;
  private final long actionId;
  private final List<AgentScope> children = new ArrayList<>();
  private AtomicBoolean done = new AtomicBoolean(false);

  public AgentScope(AgentBase actor) {
    this.actor = actor;
    this.actionId = 0;
  }

  public AgentScope(AgentScope parent, long actionId) {
    this.actor = parent.actor;
    this.actionId = actionId;
    parent.children.add(this);
  }

  @Override
  public Agent getAgent() {
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
                new AgentBase.Event(AgentBase.EventType.FIRE, actor.id, actionId, firedObject));
    if (result != Sinks.EmitResult.OK) {
      // TODO handle error
      actor.handleNotification(Notification.error("Failed to emit return event"));
    }
  }

  /**
   * Create a child scope with the passed action ID. IDs are unique to code blocks and are managed
   * by the compiler.
   *
   * @param actionId
   * @return
   */
  public AgentScope withId(long actionId) {
    final var session = getSession();
    final var context = getContext();
    return new AgentScope(AgentScope.this, actionId) {

      @Override
      public SessionScope getSession() {
        return session;
      }

      @Override
      public ContextScope getContext() {
        return context;
      }
    };
  }

  @Override
  public void doReturn(Object returnedObject) {
    var result =
        actor
            .getEventBus()
            .tryEmitNext(
                new AgentBase.Event(
                    AgentBase.EventType.RETURN, actor.id, actionId, returnedObject));
    if (result == Sinks.EmitResult.OK) {
      // remove listener due to the RETURN-type subscription
      actor
          .getEventBus()
          .tryEmitNext(
              new AgentBase.Event(
                  AgentBase.EventType.TERMINATION, actor.id, actionId, returnedObject));
      // scope is done
      done();
    } else {
      // TODO handle error
      Logging.INSTANCE.error("Failed to emit return event");
    }
  }

  @Override
  public PrintStream getPrintWriter() {
    // TODO configuration!
    // PrintStream noOpStream = new PrintStream(OutputStream.nullOutputStream());
    return System.out;
  }

  @Override
  public synchronized boolean isDone() {
    return done.get();
  }

  @Override
  public synchronized void done(Object... conditions) {
    if (!done.get()) {
      for (var child : children) {
        child.done(conditions);
      }
      done.set(true);
    }
    notifyAll();
  }

  public synchronized void awaitDone() throws InterruptedException {
    while (!done.get()) {
      wait();
    }
  }
}
