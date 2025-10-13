package org.integratedmodelling.klab.runtime.kactors.compiler;

import org.integratedmodelling.klab.api.lang.kactors.KActorsBehavior;

///  # TO BE REMOVED - example translation of following k.Actors code:
///
///
/// ```
/// action main:
///    emitter: sentence -> console.print(["Emitter said " + sentence])
///    functor: x -> console.print(x)
///
/// // a functor
/// action functor:
///     console.print("Dio porco ho bestemmiato, dio can")
/// 	return "dio can"
///
///  // an emitter (fires periodically, no return, continues firing)
/// action emitter:
///         timer.random(step=10.s): time -> fire "dio puto"
/// ```
///
/// This must be created by the actor compiler and compiled to a .class internally.
///
public class ActorExample extends ActorBase {

  public ActorExample(KActorsBehavior behavior) {
    super(behavior);
  }
}
