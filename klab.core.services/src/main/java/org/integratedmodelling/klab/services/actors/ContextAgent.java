//package org.integratedmodelling.klab.services.actors;
//
//import io.reacted.core.messages.reactors.ReActorStop;
//import io.reacted.core.reactorsystem.ReActorContext;
//import org.integratedmodelling.klab.api.digitaltwin.DigitalTwin;
//import org.integratedmodelling.klab.api.scope.ContextScope;
//import org.integratedmodelling.klab.api.services.runtime.Message;
//
///**
// * At the moment the digital twin is held in the service-side context scope, so the context actor is simply a
// * coordinator for observation actors; it may also host a context-wide behavior that represents the digital
// * twin itself. It has the scope so it can install messaging callbacks either directly or through behaviors.
// */
//public class ContextAgent extends KAgent {
//
//    public ContextAgent(ContextScope scope) {
//        super(sanitizeName(scope.getId()), scope);
//    }
//
//    @Override
//    protected void handleMessage(ReActorContext reActorContext, Message message) {
//        super.handleMessage(reActorContext, message);
//    }
//
//    @Override
//    protected void stop(ReActorContext rctx, ReActorStop message) {
//        super.stop(rctx, message);
//    }
//
//}
