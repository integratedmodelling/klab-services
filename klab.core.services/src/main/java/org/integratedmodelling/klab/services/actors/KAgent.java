package org.integratedmodelling.klab.services.actors;

import io.reacted.core.config.reactors.ReActorConfig;
import io.reacted.core.messages.reactors.ReActorInit;
import io.reacted.core.messages.reactors.ReActorStop;
import io.reacted.core.reactors.ReActions;
import io.reacted.core.reactors.ReActor;
import io.reacted.core.reactorsystem.ReActorContext;
import io.reacted.core.reactorsystem.ReActorRef;
import org.integratedmodelling.common.logging.Logging;
import org.integratedmodelling.klab.api.collections.Parameters;
import org.integratedmodelling.klab.api.exceptions.KlabActorException;
import org.integratedmodelling.klab.api.lang.kactors.KActorsBehavior;
import org.integratedmodelling.klab.api.lang.kactors.KActorsBehavior.Ref;
import org.integratedmodelling.klab.api.scope.ReactiveScope;
import org.integratedmodelling.klab.api.services.ResourcesService;
import org.integratedmodelling.klab.api.services.runtime.Message;
import org.integratedmodelling.klab.api.services.runtime.impl.MessageImpl;
import org.integratedmodelling.klab.api.services.runtime.kactors.VM;
import org.integratedmodelling.klab.runtime.kactors.KActorsVM;
import org.integratedmodelling.klab.runtime.kactors.messages.SetState;
import org.integratedmodelling.klab.utilities.Utils;

import java.io.File;
import java.io.Serializable;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * The basic k.LAB agent is created in a k.LAB scope, has a "global" state hash, and responds to
 * {@link org.integratedmodelling.klab.api.services.runtime.Message}s with a {@link
 * org.integratedmodelling.klab.api.services.runtime.Message.MessageClass} of {@link
 * org.integratedmodelling.klab.api.services.runtime.Message.MessageClass#ActorCommunication}. The
 * {@link Ref actor reference} is available through the context; to communicate with the agent, it's
 * enough to use {@link org.integratedmodelling.klab.api.services.runtime.Channel#send(Object...)}
 * on the scope that owns the agent using message class {@link
 * org.integratedmodelling.klab.api.services.runtime.Message.MessageClass#ActorCommunication}.
 *
 * <p>Each agent can have its own k.Actors VM which runs all the behaviors sent concurrently in
 * separate threads. The agent still processes any messages sent to it (explicitly or through
 * behavior execution) sequentially. Each run uses a run scope that contains the actor ref and the
 * Scope that the agent was created in.
 *
 * <p>In this implementation, users, sessions/scripts/applications, top-level contexts and
 * individual observations bound to behaviors wrap an agent and can run k.Actors behaviors.
 *
 * @author ferdinando villa
 */
public abstract class KAgent implements ReActor {

  /** Max timeout for quick ask in seconds. May be changeable through configuration. */
  public static long DEFAULT_TIMEOUT_FOR_ASK = 2;

  private String name;
  private Parameters<String> globalState = Parameters.create();
  private VM vm;
  protected ReactiveScope scope;
  private Ref self;
  private File scratchPath;

  public KAgent(String name, ReactiveScope scope) {
    this.name = name;
    this.scope = scope;
  }

  public static String sanitizeName(String name) {
    // TODO check for other characters. At the moment we just need the dot. Don't know why ReAct is
    // so
    //  anal about names.
    return name.replace(".", "_");
  }

  /**
   * The scratch path is created and deleted entirely when the agent finishes its life cycle.
   *
   * @return
   */
  public File getScratchPath() {
    if (scratchPath == null) {
      // create, mkdirs and register for deletion
    }
    return scratchPath;
  }

  public static class KAgentRef implements Ref {

    private static final long serialVersionUID = -519986929796662952L;

    private ReActorRef ref;
    private static AtomicLong nextId = new AtomicLong(0);

    private KAgentRef(ReActorRef ref) {
      this.setRef(ref);
    }

    /**
     * Obtain a reference to an agent from the ReActor ref.
     *
     * @param ref
     * @return
     */
    public static KAgentRef get(ReActorRef ref) {
      return new KAgentRef(ref);
    }

    @Override
    public <T extends Serializable> void tell(T message) {
      ref.tell(message);
    }

    @Override
    public <T extends Serializable, R extends Serializable> R ask(
        T message, Class<? extends R> responseClass) {
      return ask(message, responseClass, Duration.ofSeconds(DEFAULT_TIMEOUT_FOR_ASK));
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T extends Serializable, R extends Serializable> R ask(
        T message, Class<? extends R> responseClass, Duration timeout) {

      boolean referencing = false;
      AtomicReference<R> result = new AtomicReference<>();

      /*
       * If we ask for a Ref, translate to a ReActorRef and reconvert at output,
       * intercepting NO_REACTOR_REF at empty check.
       */
      Class<? extends R> replyClass = responseClass;
      if (Ref.class.isAssignableFrom(responseClass)) {
        replyClass = (Class<? extends R>) ReActorRef.class;
        referencing = true;
      }

      /*
       * TODO/CHECK this may use the reactor system's spawn() directly instead of this
       * involved pattern.
       */
      final Class<? extends R> rClass = replyClass;
      this.ref
          .ask(message, replyClass, timeout, "request_" + nextId.incrementAndGet())
          .exceptionally(
              (ex) -> {
                Logging.INSTANCE.error(new KlabActorException(ex));
                return null;
              })
          .whenComplete(
              (reply, failure) -> {
                if (reply != null && rClass.isAssignableFrom(reply.getClass())) {
                  result.set(reply);
                }
              })
          .toCompletableFuture()
          .join();

      R ret = result.get();
      if (referencing && ret instanceof ReActorRef) {
        ret = (R) new KAgentRef((ReActorRef) ret);
      }

      return ret;
    }

    public ReActorRef getRef() {
      return ref;
    }

    public void setRef(ReActorRef ref) {
      this.ref = ref;
    }

    @Override
    public boolean isEmpty() {
      return ref == null || ref == ReActorRef.NO_REACTOR_REF;
    }

    @Override
    public String toString() {
      return isEmpty() ? "Ref[EMPTY]" : ref.toString();
    }
  }

  @Override
  public ReActorConfig getConfig() {
    return configure().build();
  }

  /**
   * Extend this (call super.configure()!) for further configuration
   *
   * @return
   */
  protected ReActorConfig.Builder configure() {
    return ReActorConfig.newBuilder().setReActorName(name);
  }

  @Override
  public ReActions getReActions() {
    return setBehavior().build();
  }

  /**
   * Sets up the basic message response mechanism, handling init, stop and k.LAB messaging. Can be
   * overridden if needed (call super.setBehavior()!) to handle more messages, but overriding {@link
   * #handleMessage(ReActorContext, Message)} should serve most purposes.
   *
   * @return
   */
  protected ReActions.Builder setBehavior() {
    return ReActions.newBuilder()
        .reAct(ReActorInit.class, this::initialize)
        .reAct(ReActorStop.class, this::stop)
        .reAct(MessageImpl.class, this::handleMessage);
  }

  /*
  Key handler that dispatches to the message routing function based on type. Redefine and call super() as
   needed. These only get here if the message class is AgentCommunication. In the abstract agent, the only
   handling is to run a behavior, either as a URN or a script text.
   */
  protected void handleMessage(ReActorContext reActorContext, Message message) {

    if (message.getMessageType() == Message.MessageType.RunBehavior) {
      KActorsBehavior behavior = resolveBehavior(message);
      if (behavior != null) {
        if (this.vm == null) {
          this.vm = new KActorsVM();
        }
        this.vm.run(behavior, Parameters.create(globalState, scope.getData()), scope);
      } else {
        scope.error(
            "Behavior could not be identified from message with content "
                + message.getPayload(String.class));
      }
    }
  }

  protected Ref self() {
    return self;
  }

  private KActorsBehavior resolveBehavior(Message message) {

    KActorsBehavior ret = null;
    var behaviorReference = message.getPayload(String.class);
    if (Utils.Urns.isUrn(behaviorReference)) {
      ret = scope.getService(ResourcesService.class).retrieveBehavior(behaviorReference, scope);
    } else {
    } //            ret = scope.getService(ResourcesService.class).readBehavior(behaviorReference,
      // scope);

    return ret;
  }

  /**
   * Extend to provide initialization. MUST call super.initialize()! NOTE: when this is called, the
   * scope is defined but it does not contain the agent ref yet. Use self only.
   *
   * @param rctx
   * @param message
   */
  protected void initialize(ReActorContext rctx, ReActorInit message) {
    this.self = new KAgentRef(rctx.getSelf());
  }

  /**
   * Extend to provide reaction to states set (call super()!)
   *
   * @param rctx
   * @param message
   */
  protected void setState(ReActorContext rctx, SetState message) {
    this.globalState.put(message.getKey(), message.getValue());
  }

  /**
   * Extend to provide finalization
   *
   * @param rctx
   * @param message
   */
  protected void stop(ReActorContext rctx, ReActorStop message) {
    if (vm != null) {
      vm.stop();
    }
    if (scratchPath != null) {
      Utils.Files.deleteQuietly(scratchPath);
    }
  }
}
