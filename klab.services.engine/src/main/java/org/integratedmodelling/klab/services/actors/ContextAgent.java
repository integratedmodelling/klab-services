package org.integratedmodelling.klab.services.actors;

import org.integratedmodelling.klab.api.authentication.scope.ContextScope;
import org.integratedmodelling.klab.api.authentication.scope.Scope.Status;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.knowledge.Knowledge;
import org.integratedmodelling.klab.api.knowledge.Urn;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.services.Reasoner;
import org.integratedmodelling.klab.api.services.ResourceProvider;
import org.integratedmodelling.klab.services.actors.messages.AgentResponse;
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
        
        scope.send(message.response(Status.STARTED));
        
        Knowledge resolvable = null;
        
        /*
         * Establish URN type and resolve through the scope
         */
        switch (Urn.classify(message.getUrn())) {
        case KIM_OBJECT:
//            scope.getService(ResourceProvider.class).
            break;
        case OBSERVABLE:
            resolvable = message.getScope().getService(Reasoner.class).resolveObservable(message.getUrn());
            break;
        case REMOTE_URL:
            break;
        case RESOURCE:
            resolvable = message.getScope().getService(ResourceProvider.class).resolveResource(message.getUrn(), scope);
            break;
        case UNKNOWN:
            break;
        }
        
        if (resolvable == null) {
            scope.send(message.response(Status.ABORTED, AgentResponse.ERROR, "Cannot resolve URN " + message.getUrn()));
        }
        
        Observation result = null;
        
        
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
        
        scope.send(message.response(Status.FINISHED, AgentResponse.RESULT, result));
        
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
