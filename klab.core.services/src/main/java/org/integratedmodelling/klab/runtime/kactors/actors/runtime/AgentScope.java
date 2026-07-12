package org.integratedmodelling.klab.runtime.kactors.actors.runtime;

import java.io.PrintStream;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import org.integratedmodelling.common.logging.Logging;
import org.integratedmodelling.klab.api.actors.Agent;
import org.integratedmodelling.klab.api.collections.impl.ParametersImpl;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.scope.SessionScope;
import org.integratedmodelling.klab.api.services.runtime.Notification;
import org.integratedmodelling.klab.runtime.kactors.AgentBase;
import reactor.core.Disposable;
import reactor.core.publisher.Sinks;

// represents each action during execution, providing access to the k.Actors environment and hashes
// for local variables
public abstract class AgentScope extends ParametersImpl<String> implements Agent.Scope {

  private final AgentBase actor;
  private final long actionId;
  private final CopyOnWriteArrayList<AgentScope> children = new CopyOnWriteArrayList<>();
  private final CopyOnWriteArrayList<Disposable> disposables = new CopyOnWriteArrayList<>();
  private final AtomicBoolean done = new AtomicBoolean(false);

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
    if (isDone()) {
      return;
    }
    var result =
        actor
            .emitEvent(
                new AgentBase.Event(AgentBase.EventType.FIRE, actor.id, actionId, firedObject));
    if (result != Sinks.EmitResult.OK) {
      actor.handleNotification(Notification.error("Failed to emit fire event: " + result));
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
    if (isDone()) {
      return;
    }
    var result =
        actor
            .emitEvent(
                new AgentBase.Event(
                    AgentBase.EventType.RETURN, actor.id, actionId, returnedObject));
    if (result != Sinks.EmitResult.OK) {
      Logging.INSTANCE.error("Failed to emit return event: " + result);
    }
    done(returnedObject);
  }

  @Override
  public PrintStream getPrintWriter() {
    // TODO configuration!
    // PrintStream noOpStream = new PrintStream(OutputStream.nullOutputStream());
    return System.out;
  }

  @Override
  public boolean isDone() {
    return done.get();
  }

  @Override
  public void done(Object... conditions) {
    if (done.compareAndSet(false, true)) {
      for (var child : children) {
        child.done(conditions);
      }
      var exceptionalValue = exceptionalValue(conditions);
      if (exceptionalValue != null) {
        actor.emitEvent(
            new AgentBase.Event(
                AgentBase.EventType.EXCEPTION, actor.id, actionId, exceptionalValue));
      }
      actor.emitEvent(
          new AgentBase.Event(
              AgentBase.EventType.TERMINATION, actor.id, actionId, terminationValue(conditions)));
      for (var disposable : disposables) {
        disposable.dispose();
      }
      disposables.clear();
    }
    synchronized (this) {
      notifyAll();
    }
  }

  public void disposeWith(Disposable disposable) {
    if (disposable == null) {
      return;
    }
    if (isDone()) {
      disposable.dispose();
      return;
    }
    disposables.add(disposable);
    if (isDone() && disposables.remove(disposable)) {
      disposable.dispose();
    }
  }

  public synchronized void awaitDone() throws InterruptedException {
    while (!done.get()) {
      wait();
    }
  }

  private Object terminationValue(Object... conditions) {
    if (conditions == null || conditions.length == 0) {
      return null;
    }
    return conditions.length == 1 ? conditions[0] : conditions;
  }

  private Object exceptionalValue(Object... conditions) {
    if (conditions != null) {
      for (var condition : conditions) {
        if (condition instanceof Throwable) {
          return condition;
        }
        if (condition instanceof Notification notification
            && notification.getLevel().severity >= Notification.Level.Error.severity) {
          return notification;
        }
      }
    }
    return null;
  }
}
