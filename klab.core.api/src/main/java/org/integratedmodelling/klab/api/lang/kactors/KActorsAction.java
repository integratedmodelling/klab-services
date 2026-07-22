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
   * Any formal argument names declared for the action, to be matched to actual parameters. Action
   * arguments are simple names without types; when a {@link
   * org.integratedmodelling.klab.api.services.runtime.extension.Verb} annotation specifies actions
   * in Java, the types of the methods will be used for parameter matching, otherwise the order of
   * arguments will simply associate to the names.
   *
   * @return
   */
  List<String> getArgumentNames();

  /**
   * A static action can be invoked directly on the imported agent name, intended as the class of
   * the agent. A non-static action must be invoked on an instance of the agent created through the
   * <code>new</code> verb, whose parameters are matched to the agent's constructor (if defined in
   * Java through an {@link org.integratedmodelling.klab.api.services.runtime.extension.Actor}
   * annotation) or to the agent's <code>init</code> action if defined in k.Actors.
   *
   * @return
   */
  boolean isStatic();

  /**
   * The type of action, established by post-processing analysis of the action code. This will drive
   * the action's compilation into Java. The Java equivalent of a {@link
   * org.integratedmodelling.klab.api.services.runtime.extension.Verb.Type#EMITTER} is a <code>void
   * </code> function that starts a thread where objects can be fired using the {@link
   * org.integratedmodelling.klab.api.actors.RuntimeAgent.Scope}; a reactive return may stop its
   * scheduled emissions and remove its listeners without changing its emitter type, using the
   * required return operand as an exit code. A pure
   * {@link org.integratedmodelling.klab.api.services.runtime.extension.Verb.Type#SUPPLIER} will contain
   * <code>return</code> statements embedded in match actions that will compile into {@link
   * java.util.concurrent.CompletableFuture}s that will remove the correspondent listeners once
   * completed; and a {@link
   * org.integratedmodelling.klab.api.services.runtime.extension.Verb.Type#FUNCTION} will simply
   * return a value without declaring any match action for verbs, and will block when called.
   *
   * @return
   */
  org.integratedmodelling.klab.api.services.runtime.extension.Verb.Type getActionType();

  /** Set by behavior analysis before the action is handed to the compiler. */
  void setActionType(
      org.integratedmodelling.klab.api.services.runtime.extension.Verb.Type actionType);
}
