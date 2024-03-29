package org.integratedmodelling.klab.services.actors;

import org.integratedmodelling.klab.api.scope.Scope;
import org.integratedmodelling.klab.services.actors.messages.user.CreateContext;

import io.reacted.core.messages.reactors.ReActorInit;
import io.reacted.core.messages.reactors.ReActorStop;
import io.reacted.core.reactors.ReActions;
import io.reacted.core.reactorsystem.ReActorContext;

public class SessionAgent extends KAgent {

    public SessionAgent(String name, Scope scope) {
        super(name, scope);
    }

    protected ReActions.Builder setBehavior() {
        return super.setBehavior().reAct(CreateContext.class, this::createContext);
    }
    
    @Override
    protected void initialize(ReActorContext rctx, ReActorInit message) {
        super.initialize(rctx, message);
    }

    @Override
    protected void stop(ReActorContext rctx, ReActorStop message) {
        super.stop(rctx, message);
        // TODO stop VM if not null, notify listeners
    }

    private void createContext(ReActorContext rctx, CreateContext message) {
//        KActorsBehavior behavior = message.getScope().getService(ResourceProvider.class)
//                .resolveBehavior(message.getApplicationId(), message.getScope());
//        if (behavior == null) {
//            message.getScope().error("cannot find behavior " + message.getApplicationId());
//            rctx.reply(ReActorRef.NO_REACTOR_REF);
//        } else {
//
            rctx.spawnChild(new ContextAgent(message.getContextId(), message.getScope(), message.getGeometry())).ifSuccess((ref) -> rctx.reply(ref));
//        }
    }
}
