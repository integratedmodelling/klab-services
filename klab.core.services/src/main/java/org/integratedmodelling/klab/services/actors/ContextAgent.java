package org.integratedmodelling.klab.services.actors;

import io.reacted.core.messages.reactors.ReActorInit;
import io.reacted.core.messages.reactors.ReActorStop;
import io.reacted.core.reactors.ReActions;
import io.reacted.core.reactorsystem.ReActorContext;
import org.integratedmodelling.klab.api.digitaltwin.DigitalTwin;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.services.RuntimeService;
import org.integratedmodelling.klab.runtime.kactors.messages.InstrumentContextScope;
import org.integratedmodelling.klab.runtime.kactors.messages.context.GetChildren;
import org.integratedmodelling.klab.runtime.kactors.messages.context.GetParent;
import org.integratedmodelling.klab.api.services.runtime.kactors.messages.Observe;

public class ContextAgent extends KAgent {


    private DigitalTwin digitalTwin;
    private boolean persistent;

    public ContextAgent(String name, ContextScope scope) {
        super(name, scope);
//        this.focalGeometry = geometry == null ? scope.getContextObservation().getGeometry() : geometry;
    }

    protected ReActions.Builder setBehavior() {
        return super.setBehavior()
//                .reAct(Observe.class, this::observe)
                .reAct(GetChildren.class, this::getChildren)
                .reAct(InstrumentContextScope.class, this::instrumentScope)
                .reAct(GetParent.class, this::getParent);
    }

    protected void instrumentScope(ReActorContext reActorContext, InstrumentContextScope instrumentContextScope) {
        this.digitalTwin = instrumentContextScope.getDigitalTwin();
        this.persistent = instrumentContextScope.isPersistent();
    }

    protected void getChildren(ReActorContext rctx, GetChildren message) {
        var runtime = scope.getService(RuntimeService.class);
//        scope.send(message.response(Status.FINISHED, runtime.children((ContextScope) scope,
//                message.getRootObservation())));
    }

    protected void getParent(ReActorContext rctx, GetParent message) {
        var runtime = scope.getService(RuntimeService.class);
//        scope.send(message.response(Status.FINISHED, scope.parent((ContextScope) scope,
//                message.getRootObservation())));
    }

//    protected void observe(ReActorContext rctx, Observe message) {
//       digitalTwin.startResolution();
//    }

    @Override
    protected void initialize(ReActorContext rctx, ReActorInit message) {
        super.initialize(rctx, message);
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
