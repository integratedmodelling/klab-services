package org.integratedmodelling.klab.runtime.kactors.compiler;

import java.util.function.BiFunction;
import org.integratedmodelling.klab.api.lang.kactors.KActorsBehavior;
import org.integratedmodelling.klab.api.scope.SessionScope;
import org.integratedmodelling.klab.runtime.kactors.actors.runtime.AgentScope;

///  # TO BE REMOVED - example translation of following k.Actors code:
///
///
/// ```
/// behavior test.main;
///
/// action main:
///    emitter: sentence -> console.print(["Emitter said " + sentence])
///
///  // an emitter (fires periodically, no return, continues firing)
/// action emitter:
///         timer.random(step=10.s): time -> fire "dio puto"
/// ```
///
/// This must be created by the actor compiler and compiled to a .class internally.
/// Actors that do not return must be run within an asynchronous container in order to work
/// properly.
///
public class ActorExample extends ActorBase {

  public ActorExample(KActorsBehavior behavior, SessionScope scope) {
    super(behavior, scope);
  }

  @Override
  public ExitValue run() {
    var scope = initializeScope();

    /*
    install the needed reactors if there are non-static actors involved. This may
    require importing and creating instances of other behaviors. In this case all
    reactors are static.
    */

    /*
     * The compiler has decided that this behavior does not return, so it spawns the main()
     * method in a thread and signals this by returning
     * TASK_RUNNING. The actor will continue running until it is explicitly closed.
     */

    return TASK_RUNNING;
  }

  /* Generated */
  @Override
  protected AgentScope main(AgentScope initialScope, SessionScope session) {

    return initialScope;
  }

  /**
   * Named action <em>main</em> with id == 0 compiled from line 10 of <em>test.main</em>.
   *
   * <p>No <em>return</em> statement, so returns false to prevent decommissioning.
   *
   * @param scope
   * @param session
   * @return true if the action was executed successfully and its child listeners should be removed.
   */
  private boolean main_0(AgentScope scope, SessionScope session) {
    return false;
  }

  /**
   * Named action <em>emitter</em> with id == 1 compiled from line 14 of <em>test.main</em>.
   *
   * @param initialScope
   * @param session
   * @param continuation
   * @return
   */
  private boolean emitter_1(
      AgentScope initialScope,
      SessionScope session,
      BiFunction<AgentScope, SessionScope, AgentScope> continuation) {
    return true;
  }

  public static void main(String[] args) {}
}
