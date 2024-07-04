package org.integratedmodelling.klab.runtime.kactors;

import java.beans.PropertyDescriptor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.commons.lang3.reflect.MethodUtils;
import org.integratedmodelling.klab.api.collections.Pair;
import org.integratedmodelling.klab.api.collections.Parameters;
import org.integratedmodelling.klab.api.collections.Triple;
import org.integratedmodelling.klab.api.data.Proxy;
import org.integratedmodelling.klab.api.data.ValueType;
import org.integratedmodelling.klab.api.data.mediation.impl.NumericRangeImpl;
import org.integratedmodelling.klab.api.exceptions.KlabActorException;
import org.integratedmodelling.klab.api.exceptions.KlabIllegalStateException;
import org.integratedmodelling.klab.api.exceptions.KlabUnimplementedException;
import org.integratedmodelling.klab.api.exceptions.KlabValidationException;
import org.integratedmodelling.klab.api.geometry.Locator;
import org.integratedmodelling.klab.api.knowledge.Artifact;
import org.integratedmodelling.klab.api.knowledge.Concept;
import org.integratedmodelling.klab.api.knowledge.Expression;
import org.integratedmodelling.klab.api.knowledge.Expression.Forcing;
import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.api.knowledge.SemanticType;
import org.integratedmodelling.klab.api.knowledge.Urn;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.knowledge.observation.ObservationGroup;
import org.integratedmodelling.klab.api.knowledge.observation.State;
import org.integratedmodelling.klab.api.lang.Annotation;
import org.integratedmodelling.klab.api.lang.ExpressionCode;
import org.integratedmodelling.klab.api.lang.Quantity;
import org.integratedmodelling.klab.api.lang.kactors.KActorsAction;
import org.integratedmodelling.klab.api.lang.kactors.KActorsArguments;
import org.integratedmodelling.klab.api.lang.kactors.KActorsBehavior;
import org.integratedmodelling.klab.api.lang.kactors.KActorsBehavior.Ref;
import org.integratedmodelling.klab.api.lang.kactors.KActorsStatement;
import org.integratedmodelling.klab.api.lang.kactors.KActorsStatement.Assert;
import org.integratedmodelling.klab.api.lang.kactors.KActorsStatement.Assert.Assertion;
import org.integratedmodelling.klab.api.lang.kactors.KActorsStatement.Assignment;
import org.integratedmodelling.klab.api.lang.kactors.KActorsStatement.Call;
import org.integratedmodelling.klab.api.lang.kactors.KActorsStatement.ConcurrentGroup;
import org.integratedmodelling.klab.api.lang.kactors.KActorsStatement.Do;
import org.integratedmodelling.klab.api.lang.kactors.KActorsStatement.Fail;
import org.integratedmodelling.klab.api.lang.kactors.KActorsStatement.FireValue;
import org.integratedmodelling.klab.api.lang.kactors.KActorsStatement.For;
import org.integratedmodelling.klab.api.lang.kactors.KActorsStatement.If;
import org.integratedmodelling.klab.api.lang.kactors.KActorsStatement.Instantiation;
import org.integratedmodelling.klab.api.lang.kactors.KActorsStatement.Sequence;
import org.integratedmodelling.klab.api.lang.kactors.KActorsStatement.TextBlock;
import org.integratedmodelling.klab.api.lang.kactors.KActorsStatement.While;
import org.integratedmodelling.klab.api.lang.kactors.KActorsValue;
import org.integratedmodelling.klab.api.lang.kactors.KActorsValue.ExpressionType;
import org.integratedmodelling.klab.api.lang.kactors.beans.ViewComponent;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.scope.Scope;
import org.integratedmodelling.klab.api.services.Language;
import org.integratedmodelling.klab.api.services.Reasoner;
import org.integratedmodelling.klab.api.services.ResourcesService;
import org.integratedmodelling.klab.api.services.runtime.Message;
import org.integratedmodelling.klab.api.services.runtime.kactors.ActionExecutor;
import org.integratedmodelling.klab.api.services.runtime.kactors.VM;
import org.integratedmodelling.klab.api.services.runtime.kactors.WidgetActionExecutor;
import org.integratedmodelling.klab.configuration.ServiceConfiguration;
import org.integratedmodelling.common.logging.Logging;
import org.integratedmodelling.klab.runtime.kactors.extension.Library;
import org.integratedmodelling.klab.runtime.kactors.extension.Library.CallDescriptor;
import org.integratedmodelling.klab.runtime.kactors.messages.Fire;
import org.integratedmodelling.klab.runtime.kactors.messages.ScriptEvent;
import org.integratedmodelling.klab.runtime.kactors.messages.SetState;
import org.integratedmodelling.klab.runtime.kactors.messages.ViewLayout;
import org.integratedmodelling.klab.utilities.Utils;

/**
 * The basic k.Actors virtual machine (VM). Each actor should own a VM but components will be routines, not
 * actors, thereby saving the whole mess of forward IDs. The VM must be fully reentrant w.r.t. behaviors,
 * actions and state.
 * <p>
 * VMs should be usable without an actor as long as no actor-specific calls are made. These should be aware
 * that the actor can be null and terminate gracefully or warn and move on. Communication with the actor is
 * handled by scope.send(), which in agent-enabled scopes must filter messages that implement VM.AgentMessage
 * and tell() them to the agent Ref instead of sending them along the normal route.
 * <p>
 * All deprecated state should either be eliminated or moved to the scope.
 *
 * @author Ferd
 */
public class KActorsVM implements VM {

    public static final String JAVA_EXTENSION_NAMESPACE = "org.integratedmodelling.klab.components.runtime" +
            ".actors.extensions";

    /**
     * Descriptor for actions to be taken when a firing is recorded with the ID used as key in matchActions.
     *
     * @author Ferd
     */
    protected class MatchActions {

        Ref caller;
        List<Pair<Match, KActorsStatement>> matches = new ArrayList<>();
        KActorsBehavior behavior;
        // this is the original calling scope, to use when the listening action is
        // executed upon a match.
        KActorsScope scope;

        public void match(Object value, Map<String, Object> scopeVars) {

            for (Pair<Match, KActorsStatement> match : matches) {

                if (match.getFirst().matches(value, scope)) {
                    KActorsScope s = scope.withMatch(match.getFirst(), value, scope.withValues(scopeVars));
                    execute(match.getSecond(), behavior, s);
                    break;
                }
            }
        }

        public void match(Object value, KActorsScope matchingScope) {

            for (Pair<Match, KActorsStatement> match : matches) {

                if (match.getFirst().matches(value, scope)) {
                    KActorsScope s = scope.withMatch(match.getFirst(), value, matchingScope);
                    execute(match.getSecond(), behavior, s);
                    break;
                }
            }

        }

        public MatchActions(KActorsBehavior behavior, KActorsScope scope) {
            this.scope = scope;
            this.behavior = behavior;
        }
    }

    // public KActorsVM(Ref actor, Scope scope/* , Map<String, Object> globalState
    // */) {
    // this.receiver = actor;
    // this.globalState.putAll(globalState);
    // this.observationScope = scope;
    // }

    // protected IBehavior behavior;
    // protected Ref receiver;
    // Scope observationScope;

    /*
     * this is set when a behavior is loaded and used to create proper actor paths for application
     * components, so that user messages can be sent to the main application actor and directed to
     * the actor that implements them.
     */
    // @Deprecated
    // private String childActorPath = null;
    // @Deprecated
    // protected String appId;
    // @Deprecated
    // protected IActorIdentity<KlabMessage> identity;
    protected Map<Long, MatchActions> listeners = Collections.synchronizedMap(new HashMap<>());
    protected Map<String, MatchActions> componentFireListeners = Collections.synchronizedMap(new HashMap<>());
    private AtomicLong nextId = new AtomicLong(0);
    private Map<String, Long> actionBindings = Collections.synchronizedMap(new HashMap<>());
    private Map<String, Ref> receivers = Collections.synchronizedMap(new HashMap<>());
    private Map<String, List<Ref>> childInstances = Collections.synchronizedMap(new HashMap<>());
    // set to the environment that comes in with the Load message and never reset
    // private Map<String, Object> globalState = new HashMap<>();
    /*
     * Java objects created by calling a constructor in set statements. Messages will be sent using
     * reflection.
     */
    private Map<String, Object> javaReactors = Collections.synchronizedMap(new HashMap<>());
    // @Deprecated
    // private List<Ref> componentActors = Collections.synchronizedList(new
    // ArrayList<>());
    // private Layout layout;
    private Map<String, Library> libraries = new HashMap<>();
    private Map<String, Object> nativeLibraryInstances = new HashMap<>();
    private boolean stopped = false;

    // /*
    // * This is the parent that generated us through a 'new' instruction, if any.
    // FIXME should be
    // in
    // * the scope
    // */
    // @Deprecated
    // private Ref parentActor = null;

    /*
     * if we pre-build actions or we run repeatedly we cache them here. Important that their run()
     * method is reentrant.
     */
    protected Map<String, ActionExecutor> actionCache = Collections.synchronizedMap(new HashMap<>());

    /*
     * actions that were created from system actions rather than actual actors, here so we can talk
     * to them from k.Actors
     */
    private Map<String, ActionExecutor.Actor> localActionExecutors =
            Collections.synchronizedMap(new HashMap<>());

    /**
     * Top-level. TODO pass arguments and whatever else needs to be defined in the root scope.
     *
     * @param behavior
     */
    @Override
    public void run(KActorsBehavior behavior, Parameters<String> arguments, Scope scope) {
        runBehavior(behavior, arguments, new KActorsScope(scope, behavior, scope.getAgent()));
    }

    public void runBehavior(KActorsBehavior behavior, Parameters<String> arguments, KActorsScope scope) {

        // this.globalState = scope.getGlobalSymbols();

        new Thread() {

            @Override
            public void run() {

                try {

                    boolean rootView = scope.getViewScope() == null ? false :
                                       (scope.getViewScope().getLayout() == null);

                    /*
                     * preload system actors. We don't add "self" which should be factored out by
                     * the interpreter.
                     */
                    if (!receivers.containsKey("user")) {
                        Ref sact = null;
                        Ref eact = null;
                        // if ((ActorReference) identity.getParentIdentity(Session.class).getActor()
                        // != null) {
                        // sact = ((ActorReference)
                        // identity.getParentIdentity(Session.class).getActor()).actor;
                        // }
                        // if (identity.getParentIdentity(EngineUser.class).getActor() != null) {
                        // eact = ((ActorReference)
                        // identity.getParentIdentity(EngineUser.class).getActor()).actor;
                        // }
                        // // these three are the same. TODO check
                        // receivers.put("session", sact);
                        // receivers.put("view", sact);
                        // receivers.put("system", sact);
                        // // user actor
                        // receivers.put("user", eact);
                        // TODO modeler actor - which can create and modify projects and code
                    }

                    // create a new behavior for each actor. TODO/FIXME this is potentially
                    // expensive. TODO ensure the localization gets there.
                    // KActorsVM.this.behavior = Actors.INSTANCE.newBehavior(message.getBehavior());
                    KActorsVM.this.listeners.clear();
                    KActorsVM.this.actionBindings.clear();
                    KActorsVM.this.actionCache.clear();
                    // KActorsVM.this.childActorPath = message.getChildActorPath();

                    /*
                     * load all imported and default libraries
                     */

                    // if (message.getApplicationId() != null) {
                    // // this only happens when we're spawning a component from a top application
                    // // using new; in that case, the appId is communicated here and the appId in
                    // // the message (which does not come from an application action) is null.
                    // // This ensures that all component actors have the same appId.
                    // KActorsVM.this.appId = message.getApplicationId();
                    // }

                    if (behavior.getType() == KActorsBehavior.Type.UNITTEST
                            || behavior.getType() == KActorsBehavior.Type.SCRIPT) {
                        scope.send(new ScriptEvent(behavior.getUrn(),
                                behavior.getType() == KActorsBehavior.Type.UNITTEST
                                ? ScriptEvent.Type.CASE_START
                                : ScriptEvent.Type.SCRIPT_START));
                    }

                    /*
                     * Init action called no matter what and before the behavior is set; the onLoad
                     * callback intervenes afterwards. Do not create UI (use raw scope).
                     */
                    for (KActorsAction action : getActions(behavior, "init", "@init")) {

                        KActorsScope initScope = scope.forInit();
                        initScope.setMetadata(Parameters.create(scope.getMetadata()));
                        initScope.setLocalizedSymbols(getLocalization(behavior));
                        if (behavior.getType() == KActorsBehavior.Type.SCRIPT
                                || behavior.getType() == KActorsBehavior.Type.UNITTEST) {
                            initScope = initScope.synchronous();
                        }

                        KActorsVM.this.run(action, behavior, initScope);

                        if (initScope.isInterrupted() || initScope.hasErrors()) {
                            /*
                             * TODO if testing and init fails, the test is skipped. If not testing
                             * and init fails, the rest of the behavior is not loaded.
                             */
                            if (initScope.getTestScope() != null) {
                                // TODO send message to notify skipped test case
                            }

                            initScope.warn("Initialization failed: skipping rest of behavior");

                            return;
                        }
                    }

                    /*
                     * run any main actions. This is the only action that may create a UI.
                     */
                    for (KActorsAction action : getActions(behavior, "main", "@main")) {

                        KActorsScope ascope = scope.getChild(/* KActorsVM.this.appId, */ action);
                        // KActorsVM.this.layout = ascope.getViewScope() == null ? null
                        // : ascope.getViewScope().getLayout();
                        ascope.setMetadata(Parameters.create(ascope.getMetadata()));
                        ascope.setLocalizedSymbols(getLocalization(behavior));
                        if (behavior.getType() == KActorsBehavior.Type.SCRIPT
                                || behavior.getType() == KActorsBehavior.Type.UNITTEST) {
                            ascope = scope.synchronous();
                        }
                        if (arguments != null) {
                            ascope.getSymbolTable().putAll(arguments);
                        }
                        KActorsVM.this.run(action, behavior, ascope);
                    }

                    if (behavior.getType() == KActorsBehavior.Type.UNITTEST) {

                        for (KActorsAction action : getActions(behavior, "@test")) {

                            Annotation desc = Utils.Annotations.getAnnotation(action, "test");

                            /*
                             * These should all be messages to the actor ref
                             */
                            // if (scope.getIdentity() instanceof Session) {
                            // ((Session) scope.getIdentity()).notifyTestCaseStart(behavior,
                            // scope.getTestScope().getTestStatistics());

                            scope.send(new ScriptEvent(action.getUrn(), ScriptEvent.Type.TEST_START,
                                    scope.getTestScope().getTestStatistics()));

                            // }

                            if (desc.get("enabled", Boolean.TRUE) && !desc.get("disabled", Boolean.FALSE)) {

                                KActorsScope testScope = scope.forTest(action);
                                testScope.setMetadata(Parameters.create(scope.getMetadata()));
                                testScope.setLocalizedSymbols(getLocalization(behavior));
                                testScope.info(behavior.getUrn() + ": running test " + action.getUrn());

                                KActorsVM.this.run(action, behavior, testScope);

                                // if (identity instanceof Session) {
                                // ((Session) identity).resetAfterTest(action);
                                // }
                                scope.send(new ScriptEvent(action.getUrn(), ScriptEvent.Type.TEST_END));
                                testScope.getTestScope().finalizeTest(action, testScope.getValueScope());
                            }

                        }
                        scope.info(behavior.getUrn() + ": done running tests");
                    }

                    /*
                     * send the view AFTER running main and collecting all components that generate
                     * views.
                     */
                    if (rootView && scope.getViewScope().getLayout() != null) {
                        // if (Configuration.INSTANCE.isEchoEnabled()) {
                        // System.out.println(Actors.INSTANCE.dumpView(scope.getViewScope().getLayout()));
                        // }
                        scope.send(new ViewLayout(scope.getViewScope().getLayout()));
                        // scope.getMainScope().send(Message.MessageClass.UserInterface,
                        // Message.Type.SetupInterface,
                        // scope.getViewScope().getLayout());
                    } /*
                     * TODO else if we have been spawned by a new component inside a group, we
                     * should send the group update message
                     */
                    /*
                     * move on, you waiters FIXME where are these? for components?
                     */
                    // for (Semaphore semaphore : message.getSemaphores(Semaphore.Type.LOAD)) {
                    // Actors.INSTANCE.expire(semaphore);
                    // }

                } catch (Throwable e) {

                    scope.onException(e, null);

                } finally {

                    if (scope.getTestScope() != null) {
                        scope.getTestScope().finalizeTestRun();
                    }

                    // if (scope.getIdentity() instanceof Session) {
                    // if (KActorsVM.this.appId != null && (behavior.getType() ==
                    // KActorsBehavior.Type.SCRIPT
                    // || behavior.getType() == KActorsBehavior.Type.UNITTEST)) {
                    /*
                     * communicate end of script to session
                     */
                    // ((Session) scope.getIdentity()).notifyScriptEnd(KActorsVM.this.appId);
                    scope.send(new ScriptEvent(behavior.getUrn(),
                            behavior.getType() == KActorsBehavior.Type.UNITTEST
                            ? ScriptEvent.Type.CASE_END
                            : ScriptEvent.Type.SCRIPT_END));
                    // }
                    // }
                }

            }

        }.start();
    }

    protected Map<String, String> getLocalization(KActorsBehavior behavior) {
        // TODO Auto-generated method stub
        return null;
    }

    protected Collection<KActorsAction> getActions(KActorsBehavior behavior, String... match) {
        List<KActorsAction> ret = new ArrayList<>();
        for (var action : behavior.getStatements()) {
            if (match == null || match.length == 0) {
                ret.add(action);
                continue;
            }
            boolean ok = false;
            for (String m : match) {
                if (!ok && m.startsWith("@")) {
                    ok = Utils.Annotations.hasAnnotation(action, m.substring(1));
                } else if (!ok) {
                    ok = m.equals(action.getUrn());
                }
                if (ok) {
                    break;
                }
            }
            if (ok) {
                ret.add(action);
            }
        }
        return ret;
    }

    protected KActorsAction getAction(KActorsBehavior behavior, String match) {
        if (match != null) {
            for (var action : behavior.getStatements()) {
                if (match.equals(action.getUrn())) {
                    return action;
                }
            }
        }
        return null;
    }

    protected void run(KActorsAction action, KActorsBehavior behavior, KActorsScope scope) {

        Annotation wspecs = Utils.Annotations.getAnnotation(action, "modal");
        if (wspecs == null) {
            wspecs = Utils.Annotations.getAnnotation(action, "window");
        }

        if (wspecs != null) {
            scope = ((KActorsScope) scope).forWindow(wspecs, action.getUrn());
        }

        if (action.isFunction()) {
            scope = ((KActorsScope) scope).functional();
        }

        try {

            execute(action.getCode(), behavior, ((KActorsScope) scope).forAction(action));

        } catch (Throwable t) {

            scope.onException(t, "action " + behavior + " " + action.getUrn());

            if (scope.getSender() != null) {
                scope.send(new Fire(scope));
            } /*
             * else if (parentActor != null) {
             *
             *
             * No sender = the fire is not coming from an internal action but goes out to the
             * world, which in this case is the parent actor. Let our parent know we've fired with
             * a message carrying the name it knows us by, so that the value can be matched to
             * what is caught after the 'new' verb. Listener ID is the actor's name.
             *
             * // parentActor.tell(new ComponentFire(receiver.path().name(), t, receiver));
             *
             * }
             */ else {

                /*
                 * Fart in space: nothing is listening from the behavior being executed. TODO - an
                 * actor firing with no action listening and no parent should just send to either
                 * the user actor or (maybe) its parent identity? TODO - the outer group may be
                 * listening.
                 */
            }
        }

        if (wspecs != null) {
            // if (Configuration.INSTANCE.isEchoEnabled()) {
            // System.out.println(Actors.INSTANCE.dumpView(scope.getViewScope().getLayout()));
            // }
            // scope.getIdentity().setView(new ViewImpl(scope.getViewScope().getLayout()));
            scope.send(Message.MessageClass.UserInterface,
                    "modal".equals(wspecs.getName()) ? Message.MessageType.CreateModalWindow :
                    Message.MessageType.CreateWindow,
                    scope.getViewScope().getLayout());
        }
    }

    private boolean execute(KActorsStatement code, KActorsBehavior behavior, KActorsScope scope) {

        if (stopped || scope.isInterrupted()) {
            return false;
        }

        try {
            switch (code.getType()) {
                case ACTION_CALL:
                    executeCall((KActorsStatement.Call) code, behavior, scope);
                    break;
                case ASSIGNMENT:
                    executeAssignment((KActorsStatement.Assignment) code, scope);
                    break;
                case DO_STATEMENT:
                    executeDo((KActorsStatement.Do) code, scope);
                    break;
                case FIRE_VALUE:
                    return executeFire((KActorsStatement.FireValue) code, scope);
                case FOR_STATEMENT:
                    executeFor((KActorsStatement.For) code, behavior, scope);
                    break;
                case IF_STATEMENT:
                    executeIf((KActorsStatement.If) code, behavior, scope);
                    break;
                case CONCURRENT_GROUP:
                    executeGroup((KActorsStatement.ConcurrentGroup) code, behavior, scope);
                    break;
                case SEQUENCE:
                    executeSequence((KActorsStatement.Sequence) code, behavior, scope);
                    break;
                case TEXT_BLOCK:
                    executeText((KActorsStatement.TextBlock) code, behavior, scope);
                    break;
                case WHILE_STATEMENT:
                    executeWhile((KActorsStatement.While) code, scope);
                    break;
                case INSTANTIATION:
                    executeInstantiation((KActorsStatement.Instantiation) code, behavior, scope);
                    break;
                case ASSERT_STATEMENT:
                    executeAssert((KActorsStatement.Assert) code, behavior, scope);
                    break;
                case FAIL_STATEMENT:
                    if (scope.getTestScope() != null) {
                        scope.getTestScope().fail((Fail) code);
                    }
                    // fall through
                case BREAK_STATEMENT:
                    return false;
                default:
                    break;
            }
        } catch (Throwable t) {
            if (scope.getTestScope() != null) {
                scope.getTestScope().onException(t);
            }
            Logging.INSTANCE.warn("Exception thrown in k.Actors interpreter: " + t.getMessage());
        }

        return true;
    }

    private void executeInstantiation(Instantiation code, KActorsBehavior behavior, KActorsScope scope) {

        KActorsBehavior child = null;
        // if (scope.getIdentity()I instanceof Observation) {
        // child = ObservationActor.create((Observation) this.identity, null);
        // } else if (this.identity instanceof Session) {
        // /**
        // * TODO if the actor has a view, use a behavior can address
        // enable/disable/hide
        // * messages and the like.
        // */
        // child = SessionActor.create((Session) this.identity, null);
        // } else if (this.identity instanceof EngineUser) {
        // child = UserActor.create((EngineUser) this.identity);
        // }

        // existing actors for this behavior
        List<Ref> actors = this.childInstances.get(code.getActorBaseName());
        String actorName = code.getActorBaseName() + (actors == null ? "" : ("_" + (actors.size() + 1)));

        /**
         * TODO substitute with specialized message with ask pattern
         */
        Ref actor = null; // getContext().spawn(child, actorName);

        /*
         * use the actor name to install a listener for any actions that may be connected to this
         * instance; it will be used as listener ID for the ComponentFire message sent when the
         * child fires.
         */
        if (!code.getActions().isEmpty()) {

            MatchActions actions = new MatchActions(behavior, scope);
            for (Triple<KActorsValue, KActorsStatement, String> adesc : code.getActions()) {
                actions.matches.add(Pair.of(new Match(adesc.getFirst(), adesc.getThird()),
                        adesc.getSecond()));
            }
            this.componentFireListeners.put(actorName, actions);
        }

        // remove the appId for the children, otherwise their messages will be rerouted
        Map<String, Object> arguments = new HashMap<>();
        Map<String, Object> metadata = new HashMap<>();
        if (code.getArguments() != null) {
            /*
             * TODO match the arguments to the correspondent names for the declaration of main()
             */
            KActorsBehavior childBehavior = scope.getMainScope().getService(ResourcesService.class)
                                                 .resolveBehavior(code.getBehavior(), scope.getMainScope());
            if (childBehavior == null) {
                scope.error("unreferenced child behavior: " + code.getBehavior() + " when execute " +
                        "instantiation");
                return;
            }
            KActorsAction main = getAction(childBehavior, "main");
            int n = 0;
            for (int i = 0; i < main.getArgumentNames().size(); i++) {
                String arg = main.getArgumentNames().get(i);
                Object value = code.getArguments().get(arg);
                if (value == null && code.getArguments().getUnnamedKeys().size() > n) {
                    value = code.getArguments().get(code.getArguments().getUnnamedKeys().get(n++));
                    if (value instanceof KActorsValue) {
                        value = evaluateInScope((KActorsValue) value, scope); // deferred=false
                    }
                }
                arguments.put(arg, value);
            }
            for (String arg : ((KActorsArguments) code.getArguments()).getMetadataKeys()) {
                Object value = code.getArguments().get(arg);
                if (value instanceof KActorsValue) {
                    value = evaluateInScope((KActorsValue) value, scope); // deferred=false
                }
                metadata.put(arg, value);
            }
        }

        KActorsBehavior actorBehavior = scope.getMainScope().getService(ResourcesService.class)
                                             .resolveBehavior(code.getBehavior(), scope.getMainScope());
        if (actorBehavior != null) {

            /*
             * AppID in message is null because this is run by the newly spawned actor; we
             * communicate the overall appID through the specific field below.
             */
            // Load loadMessage = new Load(this.identity, code.getBehavior(), null,
            // scope.forComponent())
            // .withChildActorPath(this.childActorPath == null ? actorName :
            // (this.childActorPath +
            // "." + actorName))
            // .withActorBaseName(code.getActorBaseName()).withMainArguments(arguments).withMetadata(metadata)
            // .withApplicationId(this.appId).withParent(receiver);
            //
            // Semaphore semaphore = null;
            // if (actorBehavior.getDestination() == Type.COMPONENT) {
            // /*
            // * synchronize by default
            // */
            // semaphore = Actors.INSTANCE.createSemaphore(Semaphore.Type.LOAD);
            // loadMessage.withSemaphore(semaphore);
            // componentActors.add(actor);
            // }
            //
            // actor.tell(loadMessage);
            //
            // if (semaphore != null) {
            // waitForGreen(semaphore);
            // }

            receivers.put(actorName, actor);

            if (actors == null) {
                actors = new ArrayList<>();
                this.childInstances.put(actorName, actors);
            }

            actors.add(actor);
        }
    }

    private void waitForGreen(Semaphore semaphore) {

        while (!Semaphore.expired(semaphore)) {
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                return;
            }
        }
    }

    private void executeWhile(While code, KActorsScope scope) {
        // TODO Auto-generated method stub

    }

    private void executeText(TextBlock code, KActorsBehavior behavior, KActorsScope scope) {
        // executeCall(new KActorsActionCall(code), behavior, scope);
    }

    private void executeSequence(Sequence code, KActorsBehavior behavior, KActorsScope scope) {
        if (code.getStatements().size() == 1) {
            execute(code.getStatements().get(0), behavior, scope);
        } else {
            for (KActorsStatement statement : code.getStatements()) {
                if (!execute(statement, behavior, scope.synchronous())) {
                    break;
                }
                // TODO waitForCompletion(message);
            }
        }
    }

    private void executeGroup(ConcurrentGroup code, KActorsBehavior behavior, KActorsScope scope) {
        KActorsScope groupScope = scope.getChild(code);
        if (code.getTag() != null) {
            /*
             * install executor for group actions
             */
            // this.localActionExecutors.put(code.getTag(), new GroupHandler(this.identity,
            // appId,
            // groupScope, receiver, null));
        }
        for (KActorsStatement statement : code.getStatements()) {
            if (!execute(statement, behavior, groupScope) || scope.isInterrupted()) {
                break;
            }
        }
    }

    private void executeIf(If code, KActorsBehavior behavior, KActorsScope scope) {

        Object check = evaluateInScope(code.getCondition(), scope);
        if (isTrue(check)) {
            if (code.getThenBody() != null) {
                execute(code.getThenBody(), behavior, scope);
            }
        } else {
            for (Pair<KActorsValue, KActorsStatement> conditions : code.getElseIfs()) {
                check = evaluateInScope(conditions.getFirst(), scope);
                if (isTrue(check)) {
                    execute(conditions.getSecond(), behavior, scope);
                    return;
                }
            }
            if (code.getElseBody() != null) {
                execute(code.getElseBody(), behavior, scope);
            }
        }

    }

    public static boolean isTrue(Object check) {
        if (check instanceof Boolean) {
            return (Boolean) check;
        } else if (check instanceof Integer) {
            return ((Integer) check) != 0;
        } else if (check instanceof Number) {
            return ((Number) check).doubleValue() != 0;
        } else if (check instanceof Artifact) {
            return !((Artifact) check).isEmpty();
        } else if (check instanceof Collection) {
            return !((Collection<?>) check).isEmpty();
        }
        return check != null;
    }

    private void executeFor(For code, KActorsBehavior behavior, KActorsScope scope) {
        for (Object o : getIterable(code.getIterable(), scope)) {
            if (!execute(code.getBody(), behavior, scope.withValue(code.getVariable(), o)) || scope.isInterrupted()) {
                break;
            }
        }
    }

    private void executeAssert(Assert code, KActorsBehavior behavior, KActorsScope scope) {

        for (Assertion assertion : code.getAssertions()) {
            executeCallChain(assertion.getCalls(), behavior, scope);
            if (assertion.getValue() != null || assertion.getExpression() != null) {
                // target is the match if we come from a trigger, or the value scope.
                evaluateAssertion(scope.getMatchValue() == null ? scope.getValueScope() :
                                  scope.getMatchValue(), assertion, scope,
                        code.getArguments());
            }
        }
    }

    private static Object executeFunctionChain(List<Call> functions, KActorsBehavior behavior,
                                               KActorsScope scope) {
        Object contextReceiver = null;
        for (int i = 0; i < functions.size(); i++) {
            if (scope.isInterrupted()) {
                break;
            }
            boolean last = (i == functions.size() - 1);
            KActorsScope fscope = last ? scope.withReceiver(contextReceiver) :
                                  scope.functional(contextReceiver);
            callFunctionOrMethod(functions.get(i), fscope);
            contextReceiver = fscope.getValueScope();
        }
        return contextReceiver;
    }

    /**
     * If the call is a known function, call it and leave the value in the scope. Otherwise check if it's a
     * method of the valueScope receiver if we have it.
     *
     * @param call
     * @param fscope
     */
    private static void callFunctionOrMethod(Call call, KActorsScope fscope) {
        // TODO Auto-generated method stub

    }

    /**
     * A call sequence is a one or more calls to be executed in sequence. The last call is a standard message
     * call which will either fire or return according to the scope; the ones preceding it, if any, are
     * necessarily functional and the return value of the first provides the execution context for the next.
     *
     * @param calls
     * @param scope
     */
    private void executeCallChain(List<Call> calls, KActorsBehavior behavior, KActorsScope scope) {

        Object contextReceiver = null;
        for (int i = 0; i < calls.size(); i++) {
            boolean last = (i == calls.size() - 1);
            if (scope.isInterrupted()) {
                break;
            }
            KActorsScope fscope = last ? scope.withReceiver(contextReceiver) :
                                  scope.functional(contextReceiver);
            executeCall(calls.get(i), behavior, fscope);
            contextReceiver = fscope.getValueScope();
        }
        ((KActorsScope) scope).setValueScope(contextReceiver);
    }

    /**
     * TODO add handling of test cases - all fires (including exceptions) should be intercepted
     *
     * @param code
     * @param scope\
     * @return false if the scope is functional and execution should stop.
     */
    private boolean executeFire(FireValue code, KActorsScope scope) {

        if (scope.isFunctional()) {
            // ((AgentScope) scope).hasValueScope = true;
            scope.setValueScope(evaluateInScope(code.getValue(), scope));
            return false;
        }

        if (scope.getNotifyId() != null) {
            // my fire, my action
            if (listeners.containsKey(scope.getNotifyId())) {
                MatchActions actions = listeners.get(scope.getNotifyId());
                if (actions != null) {
                    actions.match(evaluateInScope(code.getValue(), scope), scope);
                }
            }
        }

        if (scope.getSender() != null) {

            /*
             * this should happen when a non-main action executes the fire. Must be checked first.
             * Fire may happen if the action firing is called again, so don't remove the listener.
             */

            // ((KActorsScope) scope).getSender().tell(new Fire(scope.getListenerId(),
            // evaluateInScope(code.getValue(), scope),
            // scope.getAppId(), scope.getSemaphore(), scope.getSymbols(this.identity)));

        } /*
         * else if (parentActor != null) {
         *
         *
         * No sender = the fire is not coming from an internal action but goes out to the world,
         * which in this case is the parent actor. Let our parent know we've fired with a message
         * carrying the name it knows us by, so that the value can be matched to what is caught
         * after the 'new' verb. Listener ID is the actor's name.
         *
         * // parentActor // .tell(new ComponentFire(receiver.path().name(), //
         * code.getValue().evaluate(scope, // identity, false), receiver));
         *
         * }
         */ else {

            /*
             * Fart in space: nothing is listening from the behavior being executed. TODO - an actor
             * firing with no action listening and no parent should just send to either the user
             * actor or (maybe) its parent identity? TODO - the outer group may be listening.
             */
            System.out.println("PROOOOOOPZ");

        }

        return true;
    }

    private void executeDo(Do code, KActorsScope scope) {
        // TODO Auto-generated method stub

    }

    private void executeAssignment(Assignment code, KActorsScope scope) {
        if (code.getRecipient() != null) {
            if ("self".equals(code.getRecipient())) {
                scope.send(new SetState(code.getVariable(), evaluateInScope(code.getValue(), scope)));
                // scope.getIdentity().getState().put(code.getVariable(),
                // evaluateInScope(code.getValue(), scope)); // false
            } else {
                // TODO find the actor reference and send it an internal message to set the
                // state. Should be subject to scope and authorization
                throw new KlabUnimplementedException("klab actor state setting is unimplemented");
            }
        } else if (((KActorsValue) code.getValue()).getConstructor() != null) {

            Object o = evaluateInScope((KActorsValue) code.getValue(), scope); // false
            this.javaReactors.put(code.getVariable(), o);
            switch (code.getAssignmentScope()) {
                case ACTION:
                    scope.getSymbolTable().put(code.getVariable(), o);
                    break;
                case ACTOR:
                    scope.getGlobalSymbols().put(code.getVariable(), o);
                    break;
                case FRAME:
                    scope.getFrameSymbols().put(code.getVariable(), o);
                    break;
            }
        } else {
            Object o = evaluateInScope((KActorsValue) code.getValue(), scope); // false
            switch (code.getAssignmentScope()) {
                case ACTION:
                    scope.getSymbolTable().put(code.getVariable(), o);
                    break;
                case ACTOR:
                    scope.getGlobalSymbols().put(code.getVariable(), o);
                    break;
                case FRAME:
                    scope.getFrameSymbols().put(code.getVariable(), o);
                    break;
            }
        }
    }

    @SuppressWarnings("unchecked")
    public static Object evaluateInScope(KActorsValue arg, KActorsScope scope) {

        Object ret = null;

        switch (arg.getType()) {
            case OBJECT:
                ret = createJavaObject(arg.getConstructor(), scope);
                break;
            case COMPONENT:
                ret = arg.getConstructor();
                break;
            case QUANTITY:
                ret = arg.getStatedValue().get(Quantity.class);
                break;
            case OBSERVABLE:
                ret = arg.getStatedValue().get(Observable.class);
                break;
            case ERROR:
                throw arg.getStatedValue() instanceof Throwable
                      ? new KlabActorException((Throwable) arg.getStatedValue())
                      : new KlabActorException(arg.getStatedValue() == null
                                               ? "Unspecified actor error from error value"
                                               : arg.getStatedValue().toString());

            case NUMBERED_PATTERN:
                if (!"$".equals(arg.getStatedValue().toString())) {
                    // TODO
                } /* else fall through to IDENTIFIER */

            case IDENTIFIER:

                // TODO check for recipient in ID
                if (scope.hasValue(arg.getStatedValue().toString())) {
                    ret = scope.getValue(arg.getStatedValue().toString());
                } else {
                    ret = arg.getStatedValue().toString();
                }
                break;

            case EXPRESSION:

                // if (arg.getData() == null) {
                //
                // Object val = arg.getStatedValue();
                // if (val instanceof String) {
                // val = Extensions.INSTANCE.parse((String) val);
                // }
                //
                // arg.setData(new ObjectExpression((IKimExpression) val, (IRuntimeScope)
                // scope.getRuntimeScope()));
                // }

                try {
                    /*
                     * 'metadata' is bound to the actor metadata map, initialized in the call
                     */
                    // ret = ((ObjectExpression) arg.getData()).eval((IRuntimeScope)
                    // scope.getRuntimeScope(), scope.getIdentity(),
                    // Parameters.create(scope.getSymbols(identity), "metadata",
                    // scope.getMetadata(),
                    // "self", identity));
                } catch (Throwable t) {
                    scope.error(t);
                    return null;
                }

                break;

            case OBSERVATION:
                // TODO
                break;
            case SET:
                // TODO eval all args
                break;
            case LIST:
                ret = new ArrayList<Object>();
                for (Object o : arg.getStatedValue().get(Collection.class)) {
                    ((List<Object>) ret).add(o instanceof KActorsValue ? evaluateInScope((KActorsValue) o,
                            scope) : o);
                }
                break;
            case TREE:
                // TODO eval all args
                break;
            case MAP:
                // TODO eval all args
                break;
            case TABLE:
                // TODO eval all args
                break;
            case URN:
                ret = new Urn(arg.getStatedValue().get(String.class));
                break;
            case CALLCHAIN:
                ret = executeFunctionChain(arg.getCallChain(), scope.getBehavior(), scope);
                break;
            case LOCALIZED_KEY:

                if (scope.getLocalizedSymbols() != null) {
                    ret = scope.getLocalizedSymbols().get(arg.getStatedValue().get(String.class));
                }
                if (ret == null) {
                    // ensure invariance in copies of the behavior
                    ret = "#" + arg.getStatedValue().get(Object.class);
                    // .capitalize(arg.getStatedValue().toString().toLowerCase().replace("__",
                    // ":").replace("_", " "));
                }
                break;
            default:
                ret = arg.getStatedValue().get(Object.class);
        }

        if (arg.getExpressionType() == ExpressionType.TERNARY_OPERATOR) {
            if (asBooleanValue(ret)) {
                ret = arg.getTrueCase() == null ? null : evaluateInScope(arg.getTrueCase(), scope);
            } else {
                ret = arg.getFalseCase() == null ? null : evaluateInScope(arg.getFalseCase(), scope);
            }
        }

        return ret;
    }

    /**
     * Build a Java object through reflection when invoked by a k.Actors constructor
     *
     * @param constructor
     * @param scope
     * @return
     */
    public static Object createJavaObject(KActorsValue.Constructor constructor, KActorsScope scope) {

        Class<?> cls = null;
        String className = constructor.getClassname();
        Object ret = null;

        if (constructor.getClasspath() == null) {
            className = JAVA_EXTENSION_NAMESPACE + "." + className;
        } else {
            className = constructor.getClasspath() + "." + className;
            throw new KlabIllegalStateException(
                    "k.Actors: creation of Java object with explicit classpath requires a security " +
                            "exception, unimplemented so far.");
        }

        try {

            cls = Class.forName(className);
            if (cls != null) {
                /*
                 * arguments without a key are the constructor argument; keyed arguments will be
                 * handled by looking up setXxxx(arg) through reflection.
                 */
                List<Object> arguments = new ArrayList<>();
                Map<String, Object> settings = new HashMap<>();

                for (Object arg : constructor.getArguments().getUnnamedArguments()) {
                    if (arg instanceof KActorsValue) {
                        arguments.add(evaluateInScope((KActorsValue) arg, scope));
                    } else {
                        arguments.add(arg);
                    }
                }

                for (String key : constructor.getArguments().keySet()) {
                    if (constructor.getArguments().getUnnamedKeys().contains(key)) {
                        continue;
                    }
                    Object arg = constructor.getArguments().get(key);
                    settings.put(key, arg instanceof KActorsValue ? evaluateInScope((KActorsValue) arg,
                            scope) : arg);
                }

                java.lang.reflect.Constructor<?> constr = null;

                if (arguments.size() == 0) {
                    constr = cls.getConstructor();
                } else {
                    Class<?>[] cclasses = new Class<?>[arguments.size()];
                    int i = 0;
                    for (Object o : arguments) {
                        cclasses[i++] = o == null ? Object.class : o.getClass();
                    }
                    constr = cls.getConstructor(cclasses);
                }

                if (constr == null) {
                    throw new KlabValidationException(
                            "k.Actors: cannot find a constructor for the arguments specified for " + className);
                }

                ret = constr.newInstance(arguments.toArray());

                // shouldn't happen w/o exception
                if (ret != null) {
                    for (String setting : settings.keySet()) {
                        String methodName = setting.startsWith("set") ? setting :
                                            ("set" + org.integratedmodelling.common.utils.Utils.Strings.capitalize(setting));
                        Object argument = settings.get(setting);
                        Method method = null;
                        try {
                            method = cls.getMethod(methodName, argument == null ? Object.class :
                                                               argument.getClass());
                        } catch (NoSuchMethodException e) {
                            // ok, we dont'have it.
                        }
                        if (method == null) {
                            methodName = "setProperty";
                            try {
                                method = cls.getMethod(methodName, String.class, Object.class);
                            } catch (NoSuchMethodException e) {
                                // not this one, either.
                            }
                        }

                        if (method != null) {
                            if ("setProperty".equals(methodName)) {
                                method.invoke(ret, setting, argument);
                            } else {
                                method.invoke(ret, argument);
                            }
                        } else {
                            if (scope != null) {
                                scope.warn("k.Actors: cannot find a " + methodName + " method to invoke on " +
                                        "constructed object");
                            } else {
                                Logging.INSTANCE.warn(
                                        "k.Actors: cannot find a " + methodName + " method to invoke on " +
                                                "constructed object");
                            }
                        }
                    }

                    // check for void, no-args initialization method to call after all properties
                    // are set
                    try {
                        Method method = cls.getMethod("initialize");
                        if (method != null) {
                            method.invoke(ret);
                        }
                    } catch (NoSuchMethodException e) {
                        // not this one, either.
                    }

                }

            }

        } catch (Throwable e) {
            if (scope != null) {
                scope.error("error creating k.Actors object of class " + className + ": " + e.getMessage());
            } else {
                Logging.INSTANCE.error("error creating k.Actors object of class " + className + ": " + e.getMessage());
            }
        }

        /**
         * If a proxy, unproxy.
         */
        if (ret instanceof Proxy) {
            ret = ((Proxy) ret).get();
        }

        return ret;
    }

    @SuppressWarnings("unchecked")
    private void executeCall(Call code, KActorsBehavior behavior, KActorsScope scope) {

        Long notifyId = scope.getListenerId();

        /**
         * Exec any calls that precede this one, so that the receiver is set
         */
        Object contextReceiver = null;
        for (Call chained : code.getChainedCalls()) {
            KActorsScope fscope = scope.functional(contextReceiver);
            executeCall(chained, behavior, fscope);
            contextReceiver = fscope.getValueScope();
        }

        boolean synchronize = false;

        if (code.getActions().size() > 0) {

            synchronize = scope.isSynchronous();

            notifyId = nextId.incrementAndGet();
            MatchActions actions = new MatchActions(behavior, scope);
            for (Triple<KActorsValue, KActorsStatement, String> adesc : code.getActions()) {
                actions.matches.add(Pair.of(new Match(adesc.getFirst(), adesc.getThird()),
                        adesc.getSecond()));
            }
            this.listeners.put(notifyId, actions);
        }

        if (code.getGroup() != null) {
            // TODO finish handling group actions
            execute(code.getGroup(), behavior, ((KActorsScope) scope).withNotifyId(notifyId));
            return;
        }

        String receiverName = "self";
        String messageName = code.getMessage();
        if (messageName.contains(".")) {
            receiverName = Utils.Paths.getLeading(messageName, '.');
            messageName = Utils.Paths.getLast(messageName, '.');
        }

        if (!"self".equals(receiverName)) {

            /*
             * Check first if the recipient is a Java peer and in that case, use reflection to send
             * it the message and return.
             */
            if (this.javaReactors.containsKey(receiverName)
                    || scope.getFrameSymbols().containsKey(receiverName)
                    && !Utils.Data.isPOD(scope.getSymbolTable().get(receiverName))
                    || scope.getSymbolTable().containsKey(receiverName)
                    && !Utils.Data.isPOD(scope.getSymbolTable().get(receiverName))
                    || scope.getGlobalSymbols().containsKey(receiverName)
                    && !Utils.Data.isPOD(scope.getGlobalSymbols().get(receiverName))) {

                Object reactor = this.javaReactors.get(receiverName);
                if (reactor == null) {
                    reactor = scope.getFrameSymbols().get(receiverName);
                }
                if (reactor == null) {
                    reactor = scope.getSymbolTable().get(receiverName);
                }
                if (reactor == null) {
                    reactor = scope.getGlobalSymbols().get(receiverName);
                }
                if (reactor != null) {
                    invokeReactorMethod(reactor, messageName, code.getArguments(), scope);
                }

                return;
            }

            /*
             * Check if the name corresponds to the tag of an executor created using new. If so, the
             * actor (or component) has priority over a possible actor of the same name or a
             * variable containing an actor.
             */
            if (this.localActionExecutors.containsKey(receiverName)) {
                // KActorsMessage m = new KActorsMessage(receiver, messageName,
                // code.getCallId(),
                // code.getArguments(),
                // ((KActorsScope) scope).withNotifyId(notifyId), appId);
                // this.localActionExecutors.get(receiverName).onMessage(m, scope);
                // ((KActorsScope) scope).waitForGreen(code.getFirstLine());
                return;
            }

            /*
             * Otherwise, an actor reference with this local name may have been passed as a
             * parameter or otherwise set in the symbol table as a variable.
             */
            Ref recipient = null;
            // Object potentialRecipient = scope.getFrameSymbols().get(receiverName);
            /*
             * if (!(potentialRecipient instanceof IActorIdentity)) { potentialRecipient =
             * scope.getSymbolTable().get(receiverName); }
             */
            // if (potentialRecipient instanceof IActorIdentity) {
            // try {
            // // recipient = ((ActorReference) ((IActorIdentity<KlabMessage>)
            // // potentialRecipient).getActor()).actor;
            // } catch (Throwable t) {
            // // TODO do something with the failed call, the actor should probably remember
            // // if (scope.getIdentity() instanceof IRuntimeIdentity) {
            // scope.error("error executing actor call " + messageName + ": " + t.getMessage());
            // // }
            // return;
            // }
            // } /* TODO check if it's a library! */ else {
            /*
             * Only remaining choice for an explicit actor name must be in the recipient table.
             */
            recipient = receivers.get(receiverName);
            // }

            if (recipient == null) {
                /*
                 * No recipient, we just set this to the user actor which will turn the message into
                 * whatever is set for unknown messages. This not returning null guarantees that the
                 * message will arrive.
                 */
                // recipient = ((ActorReference)
                // (identity.getParentIdentity(EngineUser.class).getActor())).actor;
            }

            if (synchronize) {
                scope.warn("External actor calls are being made within a synchronous scope: this should "
                        + " never happen. The synchronization is being ignored.");
            }

            // recipient.tell(new KActorsMessage(receiver, messageName, code.getCallId(),
            // code.getArguments(),
            // ((KActorsScope) scope).withNotifyId(notifyId), appId));

            return;

        }

        KActorsAction libraryActionCode = null;

        /*
         * check if the call is a method from the library and if it applies to the context receiver
         * in case we have one.
         */
        for (Library library : libraries.values()) {
            if (library.getMethods().containsKey(messageName)) {

                CallDescriptor method = library.getMethods().get(messageName);
                if (method.getMethod() != null) {

                    if (scope.getValueScope() != null) {

                        /*
                         * must be compatible with the same argument of the method; otherwise we
                         * continue on to receiver call.
                         */
                        boolean ok =
                                method.getMethod().getParameterCount() > 0 && scope.getValueScope().getClass()
                                                                                   .isAssignableFrom(method.getMethod().getParameters()[0].getType());

                        if (!ok) {
                            continue;
                        }

                    }

                    /*
                     * run through reflection and set the value scope to the result
                     */
                    List<Object> args = new ArrayList<>();
                    for (Object arg : code.getArguments().getUnnamedArguments()) {
                        args.add(arg instanceof KActorsValue ? evaluateInScope((KActorsValue) arg, scope) :
                                 arg);
                    }
                    try {
                        ((KActorsScope) scope).setValueScope(
                                method.getMethod().invoke(nativeLibraryInstances.get(library.getName()),
                                        args.toArray()));
                        return;
                    } catch (Throwable e) {
                        throw new KlabActorException(e);
                    }

                } else {

                    /*
                     * TODO it's an action from a k.Actors-specified library - just set it as the
                     * value of actionCode. It may be functional or not.
                     */
                }
            }
        }

        /*
         * at this point if we have a valueScope, we are calling a method on it.
         */
        if (scope.getValueScope() != null) {
            scope.setValueScope(invokeReactorMethod(scope.getValueScope(), messageName, code.getArguments()
                    , scope));
            return;
        }

        /*
         * If we get here, the message is directed to self and it may specify an executor or a
         * k.Actors behavior action. A coded action takes preference over a system behavior
         * executor.
         *
         * Now we're in the appropriate scope for synchronous execution if we have actions after
         * fire.
         */
        scope = scope.fence(synchronize);

        // TODO if libraryActionCode is not null, we should override only if the library
        // wasn't
        // explicitly
        // stated.
        KActorsAction actionCode = getAction(behavior, messageName);
        if (actionCode != null || libraryActionCode != null) {
            /*
             * local action overrides a library action
             */
            run(actionCode, behavior, ((KActorsScope) scope)
                    .matchFormalArguments(code, (actionCode == null ? libraryActionCode : actionCode)).withNotifyId(notifyId));
            return;
        }

        String executorId = /* (this.childActorPath == null ? "" : (this.childActorPath + "_")) +
         */ code.getCallId();

        /*
         * Remaining option is a code action executor installed through a system behavior. The
         * executor cache is populated at every execution of the same call, so this will be
         * instantiated only if the call has been executed before (in a loop or upon repeated calls
         * of the same action).
         */
        ActionExecutor executor = actionCache.get(executorId);

        if (executor == null) {
            Class<? extends ActionExecutor> actionClass = KActorsRuntime.INSTANCE.getActionClass(messageName);
            if (actionClass != null) {

                // TODO/FIXME was passing receiver and callIds and executorIds and whatever
                executor = KActorsRuntime.INSTANCE.getSystemAction(messageName, code.getArguments(), scope);

                if (executor != null) {

                    if (!executor.isSynchronized()) {
                        // disable the fencing if it's there
                        ((KActorsScope) scope).setSemaphore(null);
                    }

                    actionCache.put(executorId, executor);

                    if (executor instanceof ActionExecutor.Actor) {

                        /*
                         * if it has a tag, store for later reference.
                         */
                        if (code.getArguments().containsKey("tag")) {
                            Object t = code.getArguments().get("tag");
                            if (t instanceof KActorsValue) {
                                t = evaluateInScope((KActorsValue) t, scope);
                            }
                            ((ActionExecutor.Actor) executor).setName(t.toString());
                            this.localActionExecutors.put(((ActionExecutor.Actor) executor).getName(),
                                    (ActionExecutor.Actor) executor);
                        }
                    }

                    /*
                     * if there are actions, set the bindings
                     */
                    if (code.getActions().size() > 0) {
                        this.actionBindings.put(executorId, notifyId);
                    }
                }
            }
        }

        if (executor instanceof WidgetActionExecutor) {

            /*
             * the run() method in these is never called: they act through their view components
             */
            ViewComponent viewComponent = ((WidgetActionExecutor) executor).getViewComponent();

            // may be null if the addition of the component happens as the result of an
            // action
            // enqueued by the component on this actor, run and notified by the message
            // handler
            // after the call.
            if (viewComponent != null) {
                scope.getViewScope().setViewMetadata(viewComponent, executor.getArguments(), scope);
                viewComponent.setIdentity(scope.getIdentity().getId());
                // viewComponent.setApplicationId(this.appId);
                viewComponent.setParentId(code.getCallId()); // check - seems
                // wrong
                viewComponent.setId(executorId);
                // viewComponent.setActorPath(this.childActorPath);
                ((WidgetActionExecutor) executor).setInitializedComponent(viewComponent);
                scope.getViewScope().getCurrentComponent().getComponents().add(viewComponent);
            }

        } else if (executor != null) {
            executor.run(scope.withNotifyId(notifyId));
        }

        /*
         * if the scope was not synchronous, or there were no actions after a fire, this does
         * nothing. TODO In case of errors causing no fire, though, it will wait forever, so there
         * should be a way to break the wait.
         * FIXME why the offset parameter? Probably wrong
         */
        scope.waitForGreen(code.getOffsetInDocument());

    }

    @SuppressWarnings("unchecked")
    public Iterable<Object> getIterable(KActorsValue iterable, KActorsScope scope) {
        switch (iterable.getType()) {
            case ANYTHING:
                break;
            case ANYTRUE:
                break;
            case ANYVALUE:
                break;
            case EMPTY:
                break;
            case CONSTANT:
            case DATE:
            case CLASS:
            case BOOLEAN:
            case ERROR:
            case EXPRESSION:
            case NUMBER:
            case OBSERVABLE:
            case OBJECT:
            case IDENTIFIER:
            case LIST:
            case SET:
                Object o = evaluateInScope((KActorsValue) iterable, scope);
                if (o instanceof Iterable) {
                    return (Iterable<Object>) o;
                } else if (o instanceof String && Utils.Urns.isUrn(o.toString())) {
                    return iterateResource(o.toString());
                }
                return Collections.singletonList(o);
            case STRING:
                if (Utils.Urns.isUrn(iterable.getStatedValue().toString())) {
                    return iterateResource(iterable.getStatedValue().toString());
                }
                return Collections.singletonList(evaluateInScope((KActorsValue) iterable, scope));
            case MAP:
                break;
            case NODATA:
                return Collections.singletonList(null);
            case NUMBERED_PATTERN:
                break;
            case OBSERVATION:
                return (Iterable<Object>) evaluateInScope((KActorsValue) iterable, scope);
            case RANGE:
                // TODO iterate the range
                break;
            case TABLE:
                break;
            case TREE:
                break;
            case TYPE:
                break;
            case URN:
                return iterateResource(iterable.getStatedValue().toString());
            default:
                break;
        }
        return new ArrayList<>();
    }

    /**
     * Used within KlabActor to compare a returned value with an expected one in a test scope. If we're not in
     * test scope, send an exception to the monitor on lack of match.
     *
     * @param target
     * @param assertion
     * @param scope
     * @param arguments
     */
    public void evaluateAssertion(Object target, Assertion assertion, KActorsScope scope,
                                  Parameters<String> arguments) {

        if (target instanceof KActorsValue) {
            target = evaluateInScope((KActorsValue) target, scope);
        }

        Scope runtimeScope = scope.getMainScope();
        // if (target instanceof Observation) {
        // runtimeScope = ((Observation) target).getScope();
        // }

        KActorsValue comparison = assertion.getValue();

        // TODO Auto-generated method stub
        ExpressionCode selector = null;
        ObservationGroup distribute = null;
        boolean ok = false;

        if (arguments.containsKey("select")) {
            Object sel = arguments.get("select");
            if (sel instanceof Expression) {
                selector = (ExpressionCode) sel;
            } else if (sel instanceof KActorsValue && ((KActorsValue) sel).getType() == ValueType.EXPRESSION) {
                selector = ((KActorsValue) sel).as(ExpressionCode.class);
            }
        }

        if (arguments.containsKey("foreach")) {

        }

        Object compareValue = null;
        Expression.Descriptor compareDescriptor;
        Expression compareExpression = null;
        Expression.Descriptor selectDescriptor;
        Expression selectExpression = null;
        Map<String, State> states = new HashMap<>();
        Language languageService = ServiceConfiguration.INSTANCE.getService(Language.class);

        if (comparison != null) {
            if (comparison.getType() == ValueType.EXPRESSION) {

                ExpressionCode expr = comparison.as(ExpressionCode.class);
                compareDescriptor = languageService.describe(expr.getCode(), expr.getLanguage(),
                                                           scope.getMainScope())
                                                   .scalar(expr.isForcedScalar() ? Forcing.Always :
                                                           Forcing.AsNeeded);
                compareExpression = compareDescriptor.compile();
                for (String input : compareDescriptor.getIdentifiers()) {
                    // if (compareDescriptor.isScalar(input) && runtimeScope.getArtifact(input,
                    // IState.class) != null) {
                    // IState state = runtimeScope.getArtifact(input, IState.class);
                    // if (state != null) {
                    // states.put(state.getObservable().getName(), state);
                    // }
                    // }
                }
            } else {
                compareValue = evaluateInScope(comparison, scope);
            }
        }

        if (selector != null) {
            selectDescriptor = languageService
                    // TODO parameter only if target is a state
                    .describe(selector.getCode(), selector.getLanguage(), scope.getMainScope()).scalar(Forcing.Always);
            selectExpression = selectDescriptor.compile();
            for (String input : selectDescriptor.getIdentifiers()) {
                // if (selectDescriptor.isScalar(input) && runtimeScope.getArtifact(input,
                // IState.class) != null) {
                // IState state = runtimeScope.getArtifact(input, IState.class);
                // if (state != null) {
                // states.put(state.getObservable().getName(), state);
                // }
                // }
            }
        }

        Parameters<String> args = Parameters.create();
        long nErr = 0;

        if (target instanceof State && runtimeScope instanceof ContextScope) {

            states.put("self", (State) target);

            for (Locator locator : ((ContextScope) runtimeScope).getContextObservation().getGeometry()) {

                args.clear();
                for (String key : states.keySet()) {
                    args.put(key, states.get(key).get(locator));
                }
                if (selectExpression != null) {
                    Object selectValue = selectExpression.eval(runtimeScope, args);
                    if (selectValue instanceof Boolean && !((Boolean) selectValue)) {
                        continue;
                    }
                }

                if (compareExpression != null) {
                    compareValue = compareExpression.eval(runtimeScope, args);
                    ok = compareValue instanceof Boolean && (Boolean) compareValue;
                } else {
                    ok = args.get("self") == null && compareValue == null
                            || (args.get("self") != null && args.get("self").equals(compareValue));
                }

                if (!ok) {
                    nErr++;
                }
            }
        } else {

            if (assertion.getExpression() != null) {

                Object ook = evaluateInScope(assertion.getExpression(), scope);
                ok = ook instanceof Boolean && (Boolean) ook;

            } else if (comparison == null) {
                ok = target == null;
            } else {
                ok = matches(comparison, target, scope);
            }

            if (!ok) {
                nErr++;
            }
        }

        if (scope.getTestScope() == null && nErr > 0) {
            throw new KlabActorException("assertion failed on '" + comparison + "' with " + nErr + " " +
                    "mismatches");
        }

        scope.getTestScope().notifyAssertion(target, comparison, ok, assertion);
    }

    public boolean matches(KActorsValue kvalue, Object value, KActorsScope scope) {

        switch (kvalue.getType()) {

            case ANNOTATION:
                for (Annotation annotation : Utils.Annotations.collectAnnotations(value)) {
                    if (annotation.getName().equals(kvalue.getStatedValue().get(String.class))) {
                        scope.getSymbolTable().put(annotation.getName(), annotation);
                        return true;
                    }
                }
                break;
            case ANYTHING:
                return true;
            case ANYVALUE:
                return value != null && !(value instanceof Throwable);
            case ANYTRUE:
                boolean ret =
                        value != null && !(value instanceof Throwable) && !(value instanceof Boolean && !((Boolean) value));
                // if (ret) {
                // scope.symbolTable.put("$", value);
                // if (value instanceof Collection) {
                // int n = 1;
                // for (Object v : ((Collection<?>)value)) {
                // scope.symbolTable.put("$" + (n++), v);
                // }
                // }
                // }
                return ret;
            case BOOLEAN:
                return value instanceof Boolean && value.equals(kvalue.getStatedValue());
            case CLASS:
                break;
            case DATE:
                break;
            case EXPRESSION:
                System.out.println("ACH AN EXPRESSION");
                break;
            case IDENTIFIER:
                if (scope.getSymbolTable().containsKey(kvalue.getStatedValue().get(String.class))) {
                    return kvalue.getStatedValue().get(String.class).equals(scope.getSymbolTable().get(value));
                }
                if (!notMatch(value)) {
                    // NO - if defined in scope, match to its value, else just return true.
                    // scope.symbolTable.put(kvalue.getValue().toString(), value);
                    return true;
                }
                break;
            case SET:
                // TODO OR match for values in list
                break;
            case LIST:
                // TODO multi-identifier match
                break;
            case MAP:
                break;
            case NODATA:
                return value == null || value instanceof Number && Double.isNaN(((Number) value).doubleValue());
            case NUMBER:
                return value instanceof Number && value.equals(kvalue.getStatedValue().get(Number.class));
            case NUMBERED_PATTERN:
                break;
            case OBSERVABLE:
                Object obj = evaluateInScope(kvalue, scope);
                if (obj instanceof Observable) {
                    if (value instanceof Observation) {
                        return scope.getMainScope().getService(Reasoner.class).resolves(((Observation) value).getObservable(),
                                (Observable) obj, null);
                        // return ((Observation) value).getObservable().resolves((Observable) obj,
                        // null);
                    }
                }
                break;
            case QUANTITY:
                break;
            case RANGE:
                return value instanceof Number && ((NumericRangeImpl) (kvalue.getStatedValue())).contains(((Number) value).doubleValue());
            case REGEXP:
                break;
            case STRING:
                return value instanceof String && value.equals(kvalue.getStatedValue().get(String.class));
            case TABLE:
                break;
            case TYPE:
                return value != null && (kvalue.getStatedValue().get(String.class).equals(value.getClass().getCanonicalName())
                        || kvalue.getStatedValue().get(String.class)
                                 .equals(Utils.Paths.getLast(value.getClass().getCanonicalName(), '.')));
            case URN:
                break;
            case ERROR:
                // match any error? any literal for that?
                return value instanceof Throwable;
            case OBSERVATION:
                // might
                break;
            case TREE:
                break;
            case CONSTANT:
                return (value instanceof Enum
                        && ((Enum<?>) value).name().toUpperCase().equals(kvalue.getStatedValue().get(String.class)))
                        || (value instanceof String && value.equals(kvalue.getStatedValue().get(String.class)));
            case EMPTY:
                return value == null || (value instanceof Collection && ((Collection<?>) value).isEmpty())
                        || (value instanceof String && ((String) value).isEmpty())
                        || (value instanceof Concept && ((Concept) value).is(SemanticType.NOTHING))
                        || (value instanceof Observable && ((Observable) value).is(SemanticType.NOTHING))
                        || (value instanceof Artifact && !(value instanceof ObservationGroup) && ((Artifact) value).isEmpty())
                        || (value instanceof Observation && ((Observation) value).getObservable().is(SemanticType.NOTHING));
            case OBJECT:
                break;
            default:
                break;
        }
        return false;
    }

    private boolean notMatch(Object value) {
        return value == null || value instanceof Throwable || (value instanceof Boolean && !((Boolean) value));
    }

    /**
     * Invoke a method based on parameters from a call to a Java reactor inside the k.Actors code.
     *
     * @param reactor
     * @param arguments
     * @param scope
     */
    public Object invokeReactorMethod(Object reactor, String methodName, Parameters<String> arguments,
                                      KActorsScope scope) {

        Object ret = null;
        List<Object> jargs = new ArrayList<>();
        Map<String, Object> kargs = null;
        for (Object v : arguments.getUnnamedArguments()) {
            jargs.add(v instanceof KActorsValue ? evaluateInScope((KActorsValue) v, scope) : v);
        }
        for (String k : arguments.getNamedKeys()) {
            if (kargs == null) {
                kargs = new HashMap<>();
            }
            Object v = arguments.get(k);
            kargs.put(k, v instanceof KActorsValue ? evaluateInScope((KActorsValue) v, scope) : v);
        }
        if (kargs != null) {
            jargs.add(kargs);
        }

        Class<?>[] clss = new Class[jargs.size()];

        int i = 0;
        for (Object jarg : jargs) {
            clss[i++] = jarg == null ? Object.class : jarg.getClass();
        }

        Method method = null;
        try {
            method = MethodUtils.getMatchingAccessibleMethod(reactor.getClass(), methodName, clss);
        } catch (Throwable t) {
            Logging.INSTANCE.error("invokeReactorMethod threw exception: " + t.getMessage());
            // leave method = null
        }

        if (method != null) {
            try {
                ret = method.invoke(reactor, jargs.toArray());
            } catch (Throwable e) {
                if (scope != null) {
                    scope.error(e);
                } else {
                    Logging.INSTANCE.error(e);
                }
            }
        } else {

            /*
             * check for no-arg "get" or single arg "set" method.
             */
            if (jargs.size() == 0) {
                try {
                    // getter
                    method =
                            reactor.getClass().getDeclaredMethod("get" + org.integratedmodelling.common.utils.Utils.Strings.capitalize(methodName));
                    if (method != null) {
                        ret = method.invoke(reactor, jargs.toArray());
                    }
                } catch (Throwable e) {
                    // move on
                }

            } else if (jargs.size() == 1) {
                try {
                    // setter
                    method = new PropertyDescriptor(methodName, reactor.getClass()).getWriteMethod();
                    if (method != null) {
                        ret = method.invoke(reactor, jargs.toArray());
                    }
                } catch (Throwable e) {
                    // move on
                }
            }

            if (method == null && kargs == null) {
                /*
                 * see if we have a method with the same args + a map of options and pass an empty
                 * option map if so.
                 */
                clss = new Class[jargs.size() + 1];

                i = 0;
                for (Object jarg : jargs) {
                    clss[i++] = jarg == null ? Object.class : jarg.getClass();
                }
                clss[i] = Map.class;

                try {
                    method = MethodUtils.getMatchingAccessibleMethod(reactor.getClass(), methodName, clss);
                    if (method != null) {
                        jargs.add(new HashMap<Object, Object>());
                        ret = method.invoke(reactor, jargs.toArray());
                    }

                } catch (Throwable t) {
                    Logging.INSTANCE.error("invokeReactorMethod threw exception: " + t.getMessage());
                    // leave method = null
                }

            }

            if (method == null) {

                /*
                 * last chance: lookup a method taking Object[] and if found, pass whatever we have
                 */
                try {
                    method = reactor.getClass().getDeclaredMethod(methodName, Object[].class);
                    if (method != null) {
                        ret = method.invoke(reactor, (Object) jargs.toArray());
                    }
                } catch (Throwable e) {
                    if (scope != null) {
                        scope.error(e);
                    } else {
                        Logging.INSTANCE.error(e);
                    }
                }
            }

            if (ret != null || method != null) {
                return ret;
            }

            if (scope != null) {
                scope.warn("k.Actors: cannot find a '" + methodName + "' method to invoke on object of class "
                        + reactor.getClass().getCanonicalName());
            } else {
                Logging.INSTANCE.warn("k.Actors: cannot find a '" + methodName + "' method to invoke on " +
                        "object of class "
                        + reactor.getClass().getCanonicalName());
            }
        }

        return ret;
    }

    /**
     * Return the boolean value of the passed object. In k.Actors, anything that isn't a null, false, empty
     * string or zero is true.
     *
     * @param ret
     * @return
     */
    private static boolean asBooleanValue(Object ret) {
        if (ret == null || (ret instanceof String && ((String) ret).trim().isEmpty())
                || (ret instanceof Boolean && !((Boolean) ret) || (ret instanceof Number && ((Number) ret).longValue() == 0))) {
            return false;
        }
        return true;
    }

    public Iterable<Object> iterateResource(String urn) {

        return null;
        // VisitingDataBuilder builder = new VisitingDataBuilder(1);
        // IKlabData data = Resources.INSTANCE.getResourceData(urn, builder, monitor);
        // return data.getObjectCount() == 0 ? new ArrayList<>() : new
        // Iterable<Object>(){
        //
        // @Override
        // public Iterator<Object> iterator() {
        //
        // return new Iterator<Object>(){
        //
        // int n = 0;
        //
        // @Override
        // public boolean hasNext() {
        // return n < data.getObjectCount();
        // }
        //
        // @Override
        // public Object next() {
        // // wrap into an Artifact wrapper for reference inside k.Actors
        // Object ret = new Artifact(
        // new ObjectArtifact(data.getObjectName(n), data.getObjectScale(n),
        // data.getObjectMetadata(n)));
        // n++;
        // return ret;
        // }
        // };
        // }
        //
        // };
    }

    @Override
    public void stop() {
        stopped = true;
    }
}
