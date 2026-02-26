package org.integratedmodelling.klab.runtime.kactors.compiler;

import org.integratedmodelling.klab.api.lang.kactors.KActorsBehavior;
import org.integratedmodelling.klab.api.scope.SessionScope;
import org.integratedmodelling.klab.runtime.kactors.actors.runtime.ActionScope;
import org.integratedmodelling.klab.runtime.libraries.CoreActorLibrary;

import java.util.function.BiFunction;

///  # TO BE REMOVED - example translation of following k.Actors code:
///
///
/// ```
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

  // Template ${actorInfo.globalActorInstances()}
  // instantiate all global agents mentioned from library - in this case timer and console
  CoreActorLibrary.Console console;
  CoreActorLibrary.Timer timer;

  public ActorExample(KActorsBehavior behavior) {
    super(behavior);
  }

  @Override
  protected ActionScope main(ActionScope initialScope, SessionScope session) {

    // call the emitter action and enqueue listener for it to fire or return
    emitter_0(initialScope, session, this::main_1);

    return initialScope;
  }

  // translates the action after emitter fires in line 1
  private ActionScope main_1(ActionScope scope, SessionScope session) {
    //    System.out.println(scope.peek());
    return scope;
  }

  private ActionScope emitter_0(
      ActionScope initialScope,
      SessionScope session,
      BiFunction<ActionScope, SessionScope, ActionScope> continuation) {
    return initialScope;
  }



}
