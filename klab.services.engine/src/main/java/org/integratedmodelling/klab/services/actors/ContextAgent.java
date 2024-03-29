package org.integratedmodelling.klab.services.actors;

import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.knowledge.Instance;
import org.integratedmodelling.klab.api.knowledge.Knowledge;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.scope.Scope.Status;
import org.integratedmodelling.klab.api.services.Resolver;
import org.integratedmodelling.klab.api.services.RuntimeService;
import org.integratedmodelling.klab.api.services.runtime.Dataflow;
import org.integratedmodelling.klab.api.services.runtime.kactors.AgentResponse;
import org.integratedmodelling.klab.services.actors.messages.context.GetChildren;
import org.integratedmodelling.klab.services.actors.messages.context.GetParent;
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
    private Geometry currentGeometry;

    public ContextAgent(String name, ContextScope scope, Geometry geometry) {
        super(name, scope);
        this.focalGeometry = geometry == null ? scope.getScale() : geometry;
    }

    protected ReActions.Builder setBehavior() {
        return super.setBehavior()
                .reAct(Observe.class, this::observe)
                .reAct(GetChildren.class, this::getChildren)
                .reAct(GetParent.class, this::getParent);
    }

    protected void getChildren(ReActorContext rctx, GetChildren message) {
        var runtime = scope.getService(RuntimeService.class);
        scope.send(message.response(Status.FINISHED, runtime.children((ContextScope) scope,
                message.getRootObservation())));
    }

    protected void getParent(ReActorContext rctx, GetParent message) {
        var runtime = scope.getService(RuntimeService.class);
        scope.send(message.response(Status.FINISHED, runtime.parent((ContextScope) scope,
                message.getRootObservation())));
    }

    protected void observe(ReActorContext rctx, Observe message) {

        Status status = Status.EMPTY;
        Knowledge resolvable = null;
        Observation result = null;

        var resolver = scope.getService(Resolver.class);

        scope.send(message.statusResponse(Status.STARTED));

        try {

            resolvable = resolver.resolveKnowledge(message.getUrn(), Knowledge.class, scope);
            if (resolvable == null) {
                scope.send(message.response(Status.ABORTED, AgentResponse.ERROR,
                        "Cannot resolve URN " + message.getUrn()));
                return;
            }

            ContextScope resolutionScope = message.getScope();
            if (resolvable instanceof Instance instance) {
                resolutionScope = resolutionScope.withGeometry(instance.getScale());
            }

            /*
             * Build the dataflow in the scope
             */
            var resolution = resolver.resolve(resolvable, resolutionScope);

            if (resolution.getCoverage().isRelevant()) {

                Dataflow<Observation> dataflow = resolver.compile(resolvable, resolution, resolutionScope);

                /*
                 * Run the dataflow
                 */
                result = scope.getService(RuntimeService.class).run(dataflow, resolutionScope).get();

                /*
                 * TODO adjust overall geometry and catalog
                 */
                if (!result.isEmpty()) {
                    status = Status.FINISHED;
                }
            }

        } catch (Throwable e) {
            scope.error(e);
            status = Status.ABORTED;
        }

        scope.send(message.response(status, AgentResponse.RESULT, result));

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
