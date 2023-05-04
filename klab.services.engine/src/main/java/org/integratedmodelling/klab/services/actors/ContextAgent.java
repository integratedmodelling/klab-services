package org.integratedmodelling.klab.services.actors;

import org.integratedmodelling.klab.api.authentication.scope.ContextScope;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.lang.kactors.KActorsBehavior;

import io.reacted.core.messages.reactors.ReActorInit;
import io.reacted.core.messages.reactors.ReActorStop;
import io.reacted.core.reactorsystem.ReActorContext;

public class ContextAgent extends KAgent {

    Geometry originalGeometry;
    Geometry currentGeometry;
    ContextScope scope;
    
    public ContextAgent(String name, Geometry geometry, ContextScope scope) {
        super(name);
        this.originalGeometry = geometry;
    }

    public ContextAgent(KActorsBehavior application) {
        super(application.getName());
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
