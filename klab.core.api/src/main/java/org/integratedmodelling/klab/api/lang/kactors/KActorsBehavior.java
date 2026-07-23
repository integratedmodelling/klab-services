package org.integratedmodelling.klab.api.lang.kactors;

import java.net.URL;
import java.util.List;
import org.integratedmodelling.klab.api.lang.kim.KlabDocument;
import org.integratedmodelling.klab.api.lang.kim.KlabStatement;
import org.integratedmodelling.klab.api.scope.UserScope;

/**
 * The syntactic peer resulting from parsing a .kactor file. Specifies a behavior to be used in the
 * k.LAB network. Behaviors represent individual agents, whose state can be defined using <code>def
 * </code> assignments in the <code>init</code> action, and define a collection of actions that can
 * be called explicitly or be executed automatically if they carry special meaning (among these, the
 * <code>init</code> action for initialization, essentially a constructor; the <code>main</code>
 * action that is executed, if present, after <code>init</code>; and any actions annotated
 * with @handle (which will handle a particular class of messages sent to the agent through the AMQP
 * pipeline or a specified type of event coming from a digital twin's scheduler). Other actions can
 * be invoked internally (using the <code>self</code> receiver in a {@link
 * org.integratedmodelling.klab.api.lang.kactors.KActorsStatement.Verb} statement) or on agents
 * imported (through an {@link Import} statement, which will resolve the agent behavior on the k.LAB
 * network and assign the local receiver name).
 *
 * <p>Agents can be defined as k.Actor behaviors, findable through the connected Resources services,
 * or as internal libraries provided by k.LAB components, using the @{@link
 * org.integratedmodelling.klab.api.services.runtime.extension.Actor} annotation for the main agent
 * class and the @{@link org.integratedmodelling.klab.api.services.runtime.extension.Verb}
 * annotation to specify actions. If the Verb annotation is used on a static method, the action can
 * be invoked on the imported agent class directly; otherwise, the <code>new</code> verb can be used
 * to create an agent (matching the parameters in k.Actors to the class constructor(s)) and
 * non-static verbs can be invoked on it.
 *
 * <p>The main feature of k.Actors, in addition to its simplicity and use of metadata to implement
 * conventions, is the concurrency model. Any action called through a {@link
 * org.integratedmodelling.klab.api.lang.kactors.KActorsStatement.Verb} statement can emit a value
 * at any time, and the @{@link
 * org.integratedmodelling.klab.api.lang.kactors.KActorsStatement.Verb.MatchAction}, if present,
 * specifies a choice of matching actions to take based on what was emitted. If the statement
 * following a verb (or a group containing verbs with match actions) starts with <code>then</code>,
 * or if the emitting action is on the right side of an assignment or is the target of a <code>
 * return</code> or <code>fire</code> statement, the compiled code will join the thread and continue
 * after completion. In the normal execution model, actions that contain <code>fire</code>
 * instructions are <em>emitters</em> and can result in repeated emissions; a <code>return</code>
 * within one of their match actions stops scheduled emissions and removes listeners, with its
 * operand available as an exit code.
 * Actions without emissions whose match actions return a value are <em>suppliers</em> and remove
 * their listeners once the value is returned; actions without match actions are simple
 * <em>functions</em> that execute synchronously.
 *
 * <p>For the rest, k.Actors uses standard control flow statements and a simple, terse syntax. The
 * k.LAB environment provides verbs that enable control of all k.LAB functions, such as submitting
 * observations, and neatly encapsulate the working of the k.LAB scopes and services. Actors can
 * send messages to each other and, once associated to {@link
 * org.integratedmodelling.klab.api.knowledge.SemanticType#AGENT} observations in a {@link
 * org.integratedmodelling.klab.api.digitaltwin.DigitalTwin}, can look each other up by type or
 * geometry and invoke actions on each other, or construct overall observations based on what they
 * see and experience, enabling full agent-based modeling capabilities in k.LAB's digital twins.
 * Session-based agents can be used to automate tasks, bridge to external libraries in components,
 * produce test cases or interactive applications, as described in the {@link Type} annotation.
 *
 * <p>The k.Actors syntax does not provide expressions using mathematical or logical operators.
 * Rather, these elements must be included in the code as independently parsed {@link
 * org.integratedmodelling.klab.api.knowledge.Expression} object, which are parsed and compiled
 * using the {@link org.integratedmodelling.klab.api.services.Language} service and executed through
 * the runtime. Expressions are compiled in advance so that the identifiers in them can be
 * validated; in them, <code>self</code> can be used to refer to the agent and other conventional
 * bindings used also in k.IM can be used. The runtime peer of the agent is a Groovy object so that
 * familiar conventions and features will match the use of expressions in other k.LAB constructs.
 *
 * <p>Differently from other {@link KlabDocument}s, k.Actors are not necessarily bound to a project;
 * they are parsed from k.Actor source code using the {@link
 * org.integratedmodelling.klab.api.services.ResourcesService#readBehavior(URL, UserScope)} and
 * compiled/executed by a runtime, in the desired scope, by {@link
 * org.integratedmodelling.klab.api.services.RuntimeService#runAgent(KActorsBehavior, String,
 * boolean, UserScope)}. An {@link org.integratedmodelling.klab.api.actors.Agent} object
 * (serializable in its client version) is the runtime peer of a running agent and can be used to
 * send messages to the agent or inquire about its status. The AMQP pipeline is used by agents to
 * communicate with its connected peers through the {@link
 * org.integratedmodelling.klab.api.scope.Scope}s each agent refers to.
 *
 * <p>Even if the behavior is not <em>necessarily</em> bound to a project, k.LAB {@link
 * org.integratedmodelling.klab.api.knowledge.organization.Project}s can contain all types of
 * k.Actors behaviors and serve as libraries so that the scope-accessible {@link
 * org.integratedmodelling.klab.api.services.ResourcesService}s can locate and provide access to
 * referenced behaviors.
 *
 * @author Ferd
 */
public interface KActorsBehavior extends KlabDocument<KActorsAction> {

  enum Type {
    /**
     * The behavior defines an observed gent. Normally bound to observations through a k.IM @bind
     * annotation, it can also be run independently from the IDE.
     */
    BEHAVIOR,
    /**
     * The behavior will be incorporated in a session actor, creating a session-level application.
     * The specialized application scope will first collect the intended UI structure from the group
     * structure (specifying, through group metadata, layout, IDs and composition for all UI
     * containers) and the UI verbs they contain (specifying actions on user interaction), then
     * build a JSON specification of the UI which will be sent to the intended front-end for
     * rendering. The UI verbs support all widgets and can be rendered in a desktop, mobile, or web
     * application according to the front-end technology. The UI is dynamic and containers/widgets
     * can be added to, enabled, disabled, and removed under the behavior's control.
     */
    APP,
    /**
     * The behavior will be incorporated in a user actor, specifying initialization actions (such as
     * selecting a specific digital twin), interception of errors and any calls that won't make it
     * to other actors, listeners for specific observables and the like. A user actor definition is
     * typically located in a user profile saved to the k.LAB data directory or in online group
     * metadata. Formally a USER behavior is identical to a standard BEHAVIOR, but the USER
     * characterization in the preamble is needed for safety. A USER behavior can stand for a {@link
     * org.integratedmodelling.klab.api.identities.Federation} in federated contexts, in which case
     * it must be provided with the same group metadata that specify the federation.
     */
    USER,
    /**
     * The behavior is a collection of actions to be incorporated in another actor definition as a
     * collection of traits (a "personality"). Traits are adopted through inheritance and cannot be
     * bound or instantiated directly. They may define {@code init} and {@code main}; inherited
     * initialization and main behavior become part of the adopting agent.
     */
    TRAITS,

    /**
     * A library is a reusable collection of callable actions imported by agents. Unlike a trait it
     * does not contribute an inherited personality and, because it is not independently
     * constructed or started, it cannot define {@code init} or {@code main}.
     */
    LIBRARY,

    /**
     * The behavior defines a collection of annotated unit tests, through actions annotated
     * with @test. Can only be run directly and explicitly, and they run in a specialized agent
     * scope that collects results and creates a report. The report can later be visualized using a
     * preferred front-end.
     */
    UNITTEST,

    /**
     * A component is an actor that exists in its own right but is created and used from another
     * agent. It normally provides UI elements or another composable subsystem. Components may
     * define {@code init} as their constructor and {@code main} as their startup action. The system
     * rejects direct observation bindings to components and creates them through actor construction.
     */
    COMPONENT,

    /** A script is a batch job run in synchronous mode as the behavior of a session. */
    SCRIPT,

    /**
     * A task is a script that runs in normal asynchronous mode. It must have a main and can only be
     * run from the IDE, CLI or through an engine launched with the task URN as an option (which
     * will run the task and then exit).
     */
    TASK
  }

  /** Applications can be localized to a specific platform, */
  enum Platform {
    ANY,
    DESKTOP,
    WEB,
    MOBILE
  }

  /**
   * Declaring imports for each new agent is now mandatory. We may automatically import the
   * klab.core.* agents eventually. The syntax associates the full URN of a behavior to the name of
   * the receiver used in {@link
   * org.integratedmodelling.klab.api.lang.kactors.KActorsStatement.Verb} statements. It is an error
   * to use a receiver that was not declared in an import statement. Static actions can use the
   * imported behavior as a receiver; actions that are not static must be invoked on an agent
   * created in advance with a <code>new</code> verb.
   */
  interface Import {

    /**
     * The URN of the imported behavior. This will be resolved transparently through the connected
     * {@link org.integratedmodelling.klab.api.services.ResourcesService}s to either a component
     * plug-in (if declared in Java code) or another k.Actors behavior.
     *
     * @return
     */
    String getImportedBehavior();

    /**
     * The local name that will be used internally for the receiver. These names cannot be
     * overridden by assignments in a behavior.
     *
     * @return
     */
    String getImportedAlias();

    /**
     * For future use in case we need to import only individual actions from the imported behavior.
     * Not used currently and because the k.Actors code is compiled in Java, enabling optimization,
     * it may be removed.
     *
     * @return
     */
    List<String> getImportedComponents();
  }

  /**
   * The fully qualified URN for the behavior - a dot-separated path of lowercase identifiers,
   * unique within the k.LAB system.
   *
   * @return
   */
  String getUrn();

  /**
   * The type of this behavior. This will trigger compilation and execution with different
   * assumptions and conventions.
   *
   * @return
   */
  Type getBehaviorType();

  /**
   * If this is an application, return the platform this is specialized for, which may be ANY. For
   * any other behavior, return ANY.
   *
   * @return
   */
  Platform getPlatform();

  /**
   * All behaviors imported, which will be resolved and parsed. May refer to imported behaviors or
   * to libraries, both native and k.Actors.
   *
   * @return
   */
  List<Import> getImports();

  /**
   * The URNs of the behaviors this behavior inherits from, in order of precedence. The actions
   * defined in these will be available and can be overridden, generating a warning if the code does
   * not carry an <code>@override</code> annotation. Any <code>init</code> action provided by an
   * inherited behavior will be executed before the <code>init</code> action of this behavior.All
   * inherited state variables are considered protected and they can be changed using <code>set
   * </code>; it is illegal to override an inherited state variable.
   *
   * @return
   */
  List<String> getInheritedBehaviors();

  /**
   * Description (docstring). A description in the preamble is mandatory.
   *
   * @return
   */
  String getDescription();

  /**
   * Private, project private, public
   *
   * @return
   */
  KlabStatement.Scope getScope();
}
