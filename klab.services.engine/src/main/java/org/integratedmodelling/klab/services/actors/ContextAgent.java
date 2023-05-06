package org.integratedmodelling.klab.services.actors;

import org.integratedmodelling.klab.api.authentication.scope.ContextScope;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.knowledge.Urn;
import org.integratedmodelling.klab.services.actors.messages.context.Observe;

import io.reacted.core.messages.reactors.ReActorInit;
import io.reacted.core.messages.reactors.ReActorStop;
import io.reacted.core.reactors.ReActions;
import io.reacted.core.reactorsystem.ReActorContext;

public class ContextAgent extends KAgent {

    /**
     * The geometry of focus when the context was created.
     */
    private final Geometry focalGeometry;
    private final ContextScope scope;
    private Geometry currentGeometry;
    
    public ContextAgent(String name, ContextScope scope) {
        super(name);
        this.focalGeometry = scope.getGeometry();
        this.scope = scope;
    }

    protected ReActions.Builder setBehavior() {
        return super.setBehavior().reAct(Observe.class, this::observe);
    }

    
    protected void observe(ReActorContext rctx, Observe message) {
        
        System.out.println("OBSERVE " + message);
        
        /*
         * Establish URN type
         */
        switch (Urn.classify(message.getUrn())) {
        case KIM_OBJECT:
            break;
        case OBSERVABLE:
            break;
        case REMOTE_URL:
            break;
        case RESOURCE:
            break;
        case UNKNOWN:
            break;
        }
        
        /*
         * resolve URN through scope
         */
        
        /*
         * Build the dataflow in the scope
         */
        
        /*
         * Run the dataflow
         */
        
        /*
         * Adjust overall geometry and catalog
         */
        
        /*
         * Send the message response back with status and results
         */
    }
    
    
    @Override
    protected void initialize(ReActorContext rctx, ReActorInit message) {
        super.initialize(rctx, message);
        
        /*
         * establish temporary storage 
         */
    }

    @Override
    protected void stop(ReActorContext rctx, ReActorStop message) {
        
        /*
         * save status if requested
         */
        
        /*
         * deallocate any storage used
         */
        
        super.stop(rctx, message);
    }

    
}
