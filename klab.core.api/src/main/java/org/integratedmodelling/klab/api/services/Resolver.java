package org.integratedmodelling.klab.api.services;

import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.scope.Scope;
import org.integratedmodelling.klab.api.services.resources.ResourceSet;
import org.integratedmodelling.klab.api.services.runtime.Dataflow;

public interface Resolver extends KlabService {

    default String getServiceName() {
        return "klab.resolver.service";
    }

    /**
     * All services publish capabilities and have a call to obtain them.
     *
     * @author Ferd
     */
    interface Capabilities extends ServiceCapabilities {

    }

    /**
     * Scope CAN be null for generic public capabilities.
     *
     * @param scope
     * @return
     */
    Capabilities capabilities(Scope scope);

    /**
     * Main entry point and sole function of the resolver. Everything else could be non-API.
     *
     * @param observation
     * @param contextScope
     * @return a dataflow that will resolve the passed observation, or an empty dataflow if nothing is needed.
     */
    Dataflow resolve(Observation observation, ContextScope contextScope);

    //    /**
    //     * The main function of the resolver is to resolve knowledge to a dataflow in a context scope.
    //     This is
    //     * done in two steps: resolution of the knowledge to a resolution graph (initialized from the
    //     observations
    //     * in the scope) and compilatio of the resulting graph into a dataflow that can be stored or
    //     passed to a
    //     * runtime service for execution.
    //     * <p>
    //     * The resolvableUrn must specify a {@link Knowledge} object that can be resolved in the passed
    //     context
    //     * scope. It can be a URL containing the service URL or a simple URN that will be resolved
    //     according to
    //     * the scope passed. If the scope is not focused on a direct observation, the resolvable must be an
    //     * {@link Observation} or a defined object specifying one. The Observer's focal scale in the
    //     scope will
    //     * enable dependent observables as well if defined, with a default observation context built, if
    //     possible,
    //     * accordingly.
    //     * <p>
    //     * {@link DescriptionType#INSTANTIATION}.
    //     * <p>
    //     * FIXME should resolve an observation (everything else is in the scope). Also this doesn't need to
    //     *  be API, although exposing the graph probably helps enforce data sanity.
    //     *
    //     * @param resolvableUrn
    //     * @param scope
    //     * @return the dataflow that will create the observation in a runtime.
    //     */
    //    Resolution resolve(String resolvableUrn, ContextScope scope);
    //
    //    /**
    //     * Compile a resolution graph into a dataflow. The scope passed must be the same that the
    //     resolution graph
    //     * was computed into.
    //     * <p>
    //     * FIXME probably also does not need to be API but see above.
    //     *
    //     * @param resolution
    //     * @param scope
    //     * @return
    //     */
    //    Dataflow<Observation> compile(Resolvable knowledge, Resolution resolution, ContextScope scope);
    //

    /**
     * Encode a dataflow to its k.DL specification.
     *
     * @param dataflow
     * @return
     */
    String encodeDataflow(Dataflow dataflow);

//    /**
//     * Query all the resource servers available to find models that can observe the passed observable in the
//     * scope. The result should be merged to keep the latest available versions and ranked in decreasing order
//     * of fit to the context. Once the choice is made the result should be used to populate the inner
//     * knowledge repository, updating only when higher versions become available. Resolution metadata for each
//     * model resolved (optionally including those not chosen) should be available for inspection, reporting
//     * and debugging in the resolution graph connected to the {@link ContextScope}.
//     *
//     * @param observable
//     * @param scope
//     * @param scale       we pass a scale explicitly during resolution chains that may be covering the scope
//     *                    partially.
//     * @return
//     */
//    List<Model> queryModels(Observable observable, ContextScope scope, Scale scale);

    /**
     * Resolver administration functions.
     *
     * @author Ferd
     */
    interface Admin {

        /**
         * Load all usable knowledge from the namespaces included in the passed resource set. If there is a
         * linked semantic server and it is local and/or exclusive, also load any existing semantics,
         * otherwise raise an exception when encountering a concept definition. If the resource set has focal
         * URNs, make the correspondent resources available for consumption by the resolver. If an incoming
         * resource set contains resources already loaded, only substitute the existing ones if they are
         * tagged with a newer version.
         *
         * @param resources
         * @return
         */
        boolean loadKnowledge(ResourceSet resources);

    }

}
