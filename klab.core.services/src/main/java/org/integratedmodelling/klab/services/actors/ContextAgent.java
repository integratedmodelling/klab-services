package org.integratedmodelling.klab.services.actors;

import io.reacted.core.messages.reactors.ReActorStop;
import io.reacted.core.reactorsystem.ReActorContext;
import org.integratedmodelling.klab.api.digitaltwin.DigitalTwin;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.services.runtime.Message;

public class ContextAgent extends KAgent {

    private DigitalTwin digitalTwin;

    public ContextAgent(ContextScope scope) {
        super(scope.getId(), scope);
    }

    @Override
    protected void handleMessage(ReActorContext reActorContext, Message message) {
        if (message.getMessageType() == Message.MessageType.InitializeObservationContext) {
            this.digitalTwin = message.getPayload(DigitalTwin.class);
        }
        super.handleMessage(reActorContext, message);
    }

    @Override
    protected void stop(ReActorContext rctx, ReActorStop message) {
        digitalTwin.dispose();
        super.stop(rctx, message);
    }

}
