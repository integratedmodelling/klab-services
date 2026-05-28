package org.integratedmodelling.klab.runtime.kactors.actors.runtime;

import javax.annotation.Nullable;
import org.integratedmodelling.klab.api.collections.Parameters;
import org.integratedmodelling.klab.runtime.kactors.compiler.ActorBase;
import org.integratedmodelling.klab.runtime.kactors.compiler.LexicalContext;

// represents each action during execution, providing access to the k.Actors environment and hashes
// for local variables
public class ActionScope {

  ActorBase.ExitValue exitValue;
  Parameters<String> variables;
  boolean done = false;

  public ActionScope() {
    this.variables = Parameters.create();
  }

  public ActionScope(ActionScope parent) {
    this.variables = Parameters.create(parent.variables);
  }

  public static ActionScope of(Object[] parameters) {
    return null;
  }

  public ActorBase.ExitValue getExitValue() {
    return exitValue;
  }

  /**
   * Fire an event to the actor's connected receiver(s). Nothing happens if the actor in this
   * context has no receivers.
   *
   * @param payload
   */
  public synchronized void fire(Object... payload) {
    // TODO ziocan
  }

  // mark end of actor represented. This is called in the actor code to return from an action
  // (removing any listeners). If this is called at root scope level, the actor is terminated.
  public ActorBase.ExitValue done(Object returnValue) {
    // TODO
    this.done = true;
    notify();
    return exitValue = ActorBase.ExitValue.success(returnValue);
  }

  public ActorBase.ExitValue failure(Object... args) {
    // TODO
    this.done = true;
    notify();
    return exitValue = ActorBase.ExitValue.failure(args);
  }

  /**
   * Called upon any results obtained asynchronously from the k.Actors VM, including both exceptions
   * and normal results. If the exception is null, then the result must be the normal return value
   * passed as the first value of <code>results</code></>. Otherwise, the exception should be
   * handled and an appropriate error value returned.
   *
   * <p>TODO probably not the way this should be done
   *
   * @param t
   * @param actor
   * @param lexicalContext
   * @param returnValueClass
   * @param results
   */
  public <T> T handle(
      @Nullable Throwable t,
      ActorBase actor,
      LexicalContext lexicalContext,
      Class<T> returnValueClass,
      @Nullable Object... results) {
    // TODO
    return null;
  }

  public boolean isDone() {
    return done;
  }
}
