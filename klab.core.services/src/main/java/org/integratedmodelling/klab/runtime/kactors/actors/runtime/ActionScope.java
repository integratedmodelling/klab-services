package org.integratedmodelling.klab.runtime.kactors.actors.runtime;

import org.integratedmodelling.klab.runtime.kactors.compiler.ActorBase;
import org.integratedmodelling.klab.runtime.kactors.compiler.LexicalContext;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;

// represents each action during execution, providing access to the k.Actors environment and hashes
// for local variables
public class ActionScope {

  Map<String, Object> variables;

  public ActionScope() {
    this.variables = new HashMap<>();
  }

  public ActionScope(ActionScope parent) {
    this.variables = new HashMap<>(parent.variables);
  }

  /**
   * Called upon any results obtained asynchronously from the k.Actors VM, including both exceptions
   * and normal results. If the exception is null, then the result must be the normal return value
   * passed as the first value of <code>results</code></>. Otherwise, the exception should be
   * handled and an appropriate error value returned.
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
}
