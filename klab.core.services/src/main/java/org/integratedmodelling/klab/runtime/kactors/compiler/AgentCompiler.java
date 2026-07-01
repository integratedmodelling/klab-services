package org.integratedmodelling.klab.runtime.kactors.compiler;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.integratedmodelling.klab.api.lang.kactors.KActorsBehavior;
import org.integratedmodelling.klab.api.scope.UserScope;
import org.integratedmodelling.klab.api.services.ResourcesService;

public class AgentCompiler {

  // TODO URN should contain the version/build number (with build == timestamp)
  private static Map<String, Class<? extends AgentBase>> compiledActorClasses =
      new ConcurrentHashMap<>();

  private KActorsBehavior behavior;
  private UserScope scope;

  public AgentCompiler(String behaviorUrn, UserScope scope) {
    this.scope = scope;
    // TODO use all services
    this.behavior =
        scope
            .getService(ResourcesService.class)
            .retrieve(behaviorUrn, KActorsBehavior.class, scope);
  }

  public boolean compile() {

    // TODO use versions intelligently. All versions should have the timestamp of the behavior.
    // TODO store the behavior's last update timestamp in a separate hash and re-compile if it's
    //  different.
    var existing = compiledActorClasses.get(behavior.getUrn());

    return false;
  }

  /// Pattern for the compiler:
  /// 1. Choose the base class and analyze the execution mode
  /// 2. Override the necessary methods and the constructor
  /// 3. In the constructor after calling super(),add any global state to the root scope
  ///
  /// First pass should ensure that all identifiers and verbs are defined; build a catalog
  /// of reactive forms and assign IDs to them proactively. Inferrable return types should be
  /// remembered for parameter matching. Actions that return from the main thread are functions;
  /// actions that return from a reactor body are suppliers. Any fire call makes the action an
  /// emitter. These determine the execution mode of the action. The analysis should build the
  /// same data structure for a k.Actors action that a @Verb annotation provides.
  ///
  /// Compilation pass: for each action:
  ///   Compile each statement and add the code to a temporary buffer.
  ///     When statement is a reactive message call:
  ///         create a temporary buffer for the reactor setup code
  ///         assign action ID for the reactor scope, add scope creation to setup buffer
  ///         listener setup: call onEvent(scope, handler, EventType...) to install a scoped
  // subscriber
  ///           compile the reaction body within the subscription closure
  ///         add the call in asyncRun or completable future consequence to the main code buffer
  ///
  private Class<? extends AgentBase> compileBehavior(KActorsBehavior behavior) {
    return null;
  }
}
