package org.integratedmodelling.klab.runtime.kactors.actors.runtime;

import org.integratedmodelling.klab.runtime.kactors.compiler.ActorBase;
import org.integratedmodelling.klab.runtime.kactors.compiler.LexicalContext;

public class TestScope extends ActionScope {

  /**
   * Add reporting of exceptions and results to the test scope.
   *
   * @param t
   * @param actor
   * @param lexicalContext
   * @param returnValueClass
   * @param results
   * @return
   * @param <T>
   */
  @Override
  public <T> T handle(
      Throwable t,
      ActorBase actor,
      LexicalContext lexicalContext,
      Class<T> returnValueClass,
      Object... results) {
    return super.handle(t, actor, lexicalContext, returnValueClass, results);
  }
}
