package org.integratedmodelling.klab.runtime.kactors;

import org.integratedmodelling.klab.api.collections.Parameters;
import org.integratedmodelling.klab.api.identities.Identity;
import org.integratedmodelling.klab.api.lang.Annotation;
import org.integratedmodelling.klab.api.lang.kactors.KActorsAction;
import org.integratedmodelling.klab.api.lang.kactors.KActorsBehavior;
import org.integratedmodelling.klab.api.lang.kactors.KActorsBehavior.Ref;
import org.integratedmodelling.klab.api.lang.kactors.KActorsStatement.Call;
import org.integratedmodelling.klab.api.lang.kactors.KActorsStatement.ConcurrentGroup;
import org.integratedmodelling.klab.api.lang.kactors.KActorsValue;
import org.integratedmodelling.klab.api.lang.kactors.beans.Layout;
import org.integratedmodelling.klab.api.lang.kactors.beans.ViewComponent;
import org.integratedmodelling.klab.api.scope.ReactiveScope;
import org.integratedmodelling.klab.api.scope.Scope;
import org.integratedmodelling.klab.api.services.runtime.Message;
import org.integratedmodelling.klab.api.services.runtime.kactors.VM;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * Runtime scope for all k.Actors statements. Root scopes are for each action.
 * Local class so that the identity is accessible.
 * 
 * @author Ferd
 */
public class KActorsScope implements VM.BehaviorScope {

	private boolean synchronous = false;
//    private KActorsScope parent = null;

	private ReactiveScope mainScope;

	private Long listenerId;
	private Identity identity;
	private Object match;
//    private String appId;
	private Map<String, String> localizedSymbols = null;

	// local symbol table, frame-specific, holds counters and matches only
	private Map<String, Object> frameSymbols = new HashMap<>();
	// symbol table is set using 'def' and is local to an action
	private Map<String, Object> symbolTable = new HashMap<>();
	// global symbols are set using 'set' and include the read-only state of the
	// actor identity
	private Map<String, Object> globalSymbols;

	private ViewScope viewScope;
	private Ref sender;
	private boolean initializing;
	private Semaphore semaphore = null;
	// metadata come with the actor specification if created through instantiation
	// and don't
	// change.
	private Parameters<String> metadata;
	private KActorsBehavior behavior;

	/**
	 * The scope is functional if an action that is declared as 'function' is
	 * called, or if the executing action is part of a contextual chain
	 * (a1().a2().a3: ...). In this case any "fire" statement does not fire a value
	 * but "returns" it, setting it in the scope and breaking the execution.
	 */
	private boolean functional = false;

	/*
	 * the following two support chaining of actions, with the ones before the last
	 * "returning" values (may be defined using 'function' or be system actions)
	 * which end up in the scope passed to the next. Because null is a legitimate
	 * value scope, we also use a boolean to check if the scope contains a "context"
	 * value from a previous function.
	 */
	private boolean hasValueScope = false;
	private Object valueScope = null;

	/**
	 * Only instantiated in tests.
	 */
	TestScope testScope;

	public KActorsScope(ReactiveScope scope, KActorsBehavior behavior, Ref sender) {

		this.mainScope = scope;
		this.identity = scope.getIdentity();
		this.viewScope = new ViewScope(this);
		this.metadata = Parameters.create();
		this.behavior = behavior;
		this.globalSymbols = new HashMap<>();
		this.localizedSymbols = getLocalization(behavior);
		this.sender = sender;
		if (behavior.getType() == KActorsBehavior.Type.UNITTEST) {
			// originally the root scope was owned by the session. Here we have a session
			// per application, so the root scope remains in limbo
			this.testScope = new TestScope(identity, scope).getChild(behavior);
		}
	}

	private Map<String, String> getLocalization(KActorsBehavior behavior) {
		// TODO Auto-generated method stub
		return null;
	}

	public String localize(String string) {
		if (string != null) {
			if (string.startsWith("#") && this.localizedSymbols.containsKey(string.substring(1))) {
				string = this.localizedSymbols.get(string.substring(1));
			}
		}
		return string;
	}

	public KActorsScope withMatch(Match match, Object value, KActorsScope matchingScope) {

		KActorsScope ret = new KActorsScope(this);

		ret.symbolTable.putAll(matchingScope.getSymbolTable());
		ret.globalSymbols.putAll(matchingScope.getGlobalSymbols());

		/*
		 * if we have identifiers either as key or in list key, match them to the
		 * values. Otherwise match to $, $1, ... #n
		 */
		if (match.isIdentifier(ret)) {
			ret.frameSymbols.put(match.getIdentifier(), value);
		} else if (match.isImplicit()) {
			String matchId = match.getMatchName() == null ? "$" : match.getMatchName();
			ret.frameSymbols.put(matchId, value);
			if (value instanceof Collection) {
				int n = 1;
				for (Object o : ((Collection<?>) value)) {
					ret.frameSymbols.put(matchId + (n++), o);
				}
			}
		}
		ret.match = value;
		return ret;
	}

	public KActorsScope(KActorsScope scope) {
		this.globalSymbols = scope.globalSymbols;
		this.synchronous = scope.synchronous;
		this.listenerId = scope.listenerId;
		this.sender = scope.sender;
		this.symbolTable = scope.symbolTable;
		this.frameSymbols.putAll(scope.frameSymbols);
		this.identity = scope.identity;
		this.viewScope = scope.viewScope;
		this.semaphore = scope.semaphore;
		this.metadata = scope.metadata;
		this.behavior = scope.behavior;
		this.localizedSymbols = scope.localizedSymbols;
		this.testScope = scope.testScope;
		this.mainScope = scope.mainScope;
	}

	public String toString() {
		return "{S " + listenerId + "}";
	}

	public KActorsScope synchronous() {
		KActorsScope ret = new KActorsScope(this);
		ret.synchronous = true;
		return ret;
	}

	public KActorsScope concurrent() {
		KActorsScope ret = new KActorsScope(this);
		ret.synchronous = false;
		return ret;
	}

	public KActorsScope withNotifyId(Long id) {
		KActorsScope ret = new KActorsScope(this);
		ret.listenerId = id;
		return ret;
	}

	public Long getNotifyId() {
		return listenerId;
	}

	public boolean isSynchronous() {
		return this.synchronous;
	}

	public Map<String, Object> getSymbolTable() {
		return this.symbolTable;
	}

	public KActorsScope withSender(Ref sender) {
		KActorsScope ret = new KActorsScope(this);
		ret.sender = sender;
		return ret;
	}

	public boolean hasValue(String string) {
		if (frameSymbols.containsKey(string)) {
			return true;
		} else if (symbolTable.containsKey(string)) {
			return true;
		} else if (globalSymbols != null && globalSymbols.containsKey(string)) {
			return true;
		}
		return false;
	}

	public Object getValue(String string) {
		if (frameSymbols.containsKey(string)) {
			return frameSymbols.get(string);
		} else if (symbolTable.containsKey(string)) {
			return symbolTable.get(string);
		} else if (globalSymbols != null && globalSymbols.containsKey(string)) {
			return globalSymbols.get(string);
		}
		return mainScope.getData().get(string, Object.class);
	}

	/**
	 * Get a child scope for this action, which will create a panel viewscope if the
	 * action has a view.
	 * 
	 * @param action
	 * @return
	 */
	public KActorsScope getChild(KActorsAction action) {
		KActorsScope ret = forAction(action);
		ret.viewScope = this.viewScope.getChild(action, behavior, ret);
		return ret;
	}

	/**
	 * Copy of scope with specialized variable values in frame table.
	 * 
	 * @param variables
	 * @return
	 */
	public KActorsScope withValues(Map<String, Object> variables) {
		KActorsScope ret = new KActorsScope(this);
		ret.frameSymbols.putAll(variables);
		return ret;
	}

	/**
	 * Same, one value at a time.
	 * 
	 * @param variable
	 * @param value
	 * @return
	 */
	public KActorsScope withValue(String variable, Object value) {
		KActorsScope ret = new KActorsScope(this);
		ret.frameSymbols.put(variable, value);
		return ret;
	}

	public KActorsScope withComponent(ViewComponent component) {
		KActorsScope ret = new KActorsScope(this);
		ret.viewScope.setCurrentComponent(component);
		return ret;
	}

	public KActorsScope getChild(ConcurrentGroup code) {
		KActorsScope ret = new KActorsScope(this);
		if (!initializing && this.viewScope != null) {
			ret.viewScope = this.viewScope.getChild(code, ret);
		}
		return ret;
	}

	public Map<String, Object> getSymbols(Identity identity) {
		Map<String, Object> ret = new HashMap<>();
		ret.putAll(identity.getData());
		if (globalSymbols != null) {
			ret.putAll(globalSymbols);
		}
		ret.putAll(symbolTable);
		ret.putAll(frameSymbols);
		return ret;
	}

	public KActorsScope forInit() {
		KActorsScope ret = new KActorsScope(this);
		ret.initializing = true;
		return ret;
	}

	public KActorsScope forTest(KActorsAction action) {
		KActorsScope ret = new KActorsScope(this);
		ret.initializing = true;
		ret.synchronous = true;
		ret.testScope = ret.testScope.getChild(action);
		return ret;
	}

	public void waitForGreen(final int linenumber) {

		if (semaphore != null) {
			int cnt = 0;
			while (!Semaphore.expired(semaphore)) {

				if (mainScope.isInterrupted()) {
					break;
				}

				try {
					Thread.sleep(60);
					cnt++;
					if (cnt % 1000 == 0 && !semaphore.isWarned()) {
						mainScope.warn("Blocking action is taking longer than 1 minute at " + getBehavior().getUrn()
								+ ":" + linenumber);
						semaphore.setWarned();
					}
				} catch (InterruptedException e) {
					return;
				}
			}
		}
	}

	public KActorsScope fence(boolean synchronize) {
		KActorsScope ret = this;
		if (synchronize) {
			ret = new KActorsScope(this);
			ret.semaphore = Semaphore.create(Semaphore.Type.FIRE);
		}
		return ret;
	}

	public KActorsScope forWindow(Annotation wspecs, String actionId) {
		KActorsScope ret = new KActorsScope(this);
		ret.viewScope = ret.viewScope.createLayout(wspecs, actionId, ret);
		return ret;
	}

	public KActorsScope forAction(KActorsAction action) {
		KActorsScope ret = action.isFunction() ? new KActorsScope(this) : functional();
		ret.symbolTable = new HashMap<>(this.symbolTable);
		return ret;
	}

	public KActorsScope functional() {
		KActorsScope ret = new KActorsScope(this);
		ret.functional = true;
		return ret;
	}

	public KActorsScope functional(Object valueScope) {
		KActorsScope ret = new KActorsScope(this);
		ret.functional = true;
		ret.valueScope = valueScope;
		return ret;
	}

	public KActorsScope withReceiver(Object valueScope) {
		KActorsScope ret = new KActorsScope(this);
		ret.valueScope = valueScope;
		return ret;
	}

	public KActorsBehavior getBehavior() {
		return this.behavior;
	}

	public KActorsScope matchFormalArguments(Call code, KActorsAction actionCode) {

		KActorsScope ret = this;

		if (!actionCode.getArgumentNames().isEmpty()) {
			ret = new KActorsScope(this);
			int i = 0;
			for (String farg : actionCode.getArgumentNames()) {
				Object value = null;
				if (code.getArguments().getUnnamedArguments().size() > i) {
					Object argument = code.getArguments().getUnnamedArguments().get(i);
					value = argument instanceof KActorsValue ? KActorsVM.evaluateInScope((KActorsValue) argument, this)
							: argument;
				}
				ret.symbolTable.put(farg, value);
				i++;
			}
		}

		return ret;
	}

	public KActorsScope withLayout(Layout layout) {
		if (this.viewScope != null) {
			this.viewScope.setLayout(layout);
		}
		return this;
	}

	public void onException(Throwable e, String message) {

		mainScope.error("actor exception: " + (message == null ? "" : message) + e.getMessage());

		if (testScope != null) {
			testScope.onException(e);
		}

	}

	public KActorsScope getChild(KActorsBehavior behavior) {
		KActorsScope ret = new KActorsScope(this);
		ret.behavior = behavior;
		if (this.testScope != null) {
			ret.testScope = ret.testScope.getChild(behavior);
		}
		return ret;
	}

	/**
	 * The scope for a child component detaches both local and global symbols to
	 * keep them local to the child.
	 * 
	 * @return
	 */
	public KActorsScope forComponent() {
		KActorsScope ret = new KActorsScope(this);
		ret.globalSymbols = new HashMap<>(globalSymbols);
		ret.symbolTable = new HashMap<>();
		ret.frameSymbols.clear();
		return ret;
	}

//    @Override
//    public IContextualizationScope getRuntimeScope() {
//        return runtimeScope;
//    }

//    @Override
	public Parameters<String> getMetadata() {
		return metadata;
	}

//    @Override
	public Object getValueScope() {
		return valueScope;
	}

//    @Override
	public Long getListenerId() {
		return listenerId;
	}

//    @Override
	public boolean isFunctional() {
		return functional;
	}

//    @Override
	public Map<String, Object> getGlobalSymbols() {
		return globalSymbols;
	}

//    @Override
	public Semaphore getSemaphore() {
		return this.semaphore;
	}

	public Identity getIdentity() {
		return this.identity;
	}

//    @Override
	public ViewScope getViewScope() {
		return this.viewScope;
	}

//    @Override
//    public String getAppId() {
//        return appId;
//    }

	public TestScope getTestScope() {
		return testScope;
	}

//    @Override
//	public void tellSender(AgentMessage message) {
//		if (this.sender != null) {
//			this.sender.tell(message);
//		}
//		throw new KlabActorException("no sender for message: " + message);
//	}

//    @Override
	public Object getMatchValue() {
		return match;
	}

//    @Override
	public Map<String, Object> getFrameSymbols() {
		return frameSymbols;
	}

//    @Override
	public Map<String, String> getLocalizedSymbols() {
		return localizedSymbols;
	}

	public void setMetadata(Parameters<String> parameters) {
		this.metadata = parameters;
	}

	public void setLocalizedSymbols(Map<String, String> localization) {
		this.localizedSymbols = localization;
	}

	public Ref getSender() {
		return sender;
	}

	public void setValueScope(Object valueScope) {
		this.valueScope = valueScope;
	}

	public void setSemaphore(Semaphore semaphore) {
		this.semaphore = semaphore;
	}

	public Scope getMainScope() {
		return this.mainScope;
	}

	@Override
	public void info(Object... info) {
		mainScope.info(info);
	}

	@Override
	public void warn(Object... o) {
		mainScope.warn(o);
	}

	@Override
	public void error(Object... o) {
		mainScope.error(o);
	}

	@Override
	public void debug(Object... o) {
		mainScope.debug(o);
	}

	@Override
	public void status(Scope.Status status) {
		mainScope.status(status);
	}

	@Override
	public void event(Message message) {
		mainScope.event(message);
	}

	@Override
	public void ui(Message message) {
		mainScope.ui(message);
	}

	@Override
	public void subscribe(Message.Queue... queues) {
		mainScope.subscribe(queues);
	}

	@Override
	public void unsubscribe(Message.Queue... queues) {
		mainScope.unsubscribe(queues);
	}

	@Override
	public Message send(Object... message) {
		return mainScope.send(message);
	}

//	@Override
//	public Message post(Consumer<Message> handler, Object... message) {
//		return mainScope.post(handler, message);
//	}

	@Override
	public void interrupt() {
		// TODO
	}

	@Override
	public boolean isInterrupted() {
		return mainScope.isInterrupted();
	}

	@Override
	public boolean hasErrors() {
		return mainScope.hasErrors();
	}

}