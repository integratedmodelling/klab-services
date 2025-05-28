package org.integratedmodelling.klab.api.lang.kactors;

import java.util.List;

/**
 * Syntactic peer for an action in a behavior.
 *
 * @author Ferd
 */
public interface KActorsAction extends KActorsStatement {

  /**
   * Action name as declared in the code. Always a simple identifier with no prefix from the
   * behavior URN.
   *
   * @return
   */
  String getUrn();

  /**
   * The code that constitutes the action, normally a ConcurrentGroup at the top level.
   *
   * @return
   */
  List<KActorsStatement> getCode();

  /**
   * Any formal argument names declared for the action, to be matched to actual parameters.
   *
   * @return
   */
  List<String> getArgumentNames();

  /**
   * If this returns true, the action was declared as 'function' and its firing behavior is expected
   * to return a value and exit, as opposed to "firing" and continuing.
   *
   * @return
   */
  boolean isFunction();
}
