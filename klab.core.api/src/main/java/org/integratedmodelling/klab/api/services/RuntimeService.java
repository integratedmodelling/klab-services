package org.integratedmodelling.klab.api.services;

import org.integratedmodelling.klab.api.data.RuntimeAsset;
import org.integratedmodelling.klab.api.knowledge.Artifact;
import org.integratedmodelling.klab.api.knowledge.ObservationStrategyObsolete;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.lang.Contextualizable;
import org.integratedmodelling.klab.api.lang.ServiceCall;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.scope.Scope;
import org.integratedmodelling.klab.api.scope.SessionScope;
import org.integratedmodelling.klab.api.services.resources.ResourceSet;
import org.integratedmodelling.klab.api.services.runtime.Dataflow;
import org.integratedmodelling.klab.api.services.runtime.objects.SessionInfo;

import java.util.List;
import java.util.Map;
import java.util.concurrent.Future;

/**
 * The runtime service holds the actual digital twins referred to by context scopes. Client scopes will
 * register themselves at creation to obtain the scope header
 * ({@link org.integratedmodelling.klab.api.ServicesAPI#SCOPE_HEADER} that enables communication. Scopes
 * should unregister themselves after use.
 * <p>
 * All other main functions of the runtime service are exposed through the GraphQL endpoint that gives access
 * to each context.
 *
 * @author Ferd
 */
public interface RuntimeService extends KlabService {

    /**
     * The core functors for k.LAB dataflow. The runtime must support all of these.
     * <p>
     * Calls to these functions are created directly by the resolver when {@link Contextualizable}s of
     * different k.IM types and/or {@link ObservationStrategyObsolete}es from the reasoner are translated into
     * dataflow actuators.
     *
     * @author Ferd
     */
    enum CoreFunctor {

        // TODO add descriptions and arguments + flags for constant, scalar vs. vector
        URN_RESOLVER("klab.core.urn.resolver"),
        URN_INSTANTIATOR("klab.core.urn.instantiator"),
        EXPRESSION_RESOLVER("klab.core.urn.resolver"),
        LUT_RESOLVER("klab.core.urn.resolver"),
        CONSTANT_RESOLVER("klab.core.urn.resolver"),
        DEFER_RESOLUTION("klab.core.resolution.defer");

        private String serviceCall;
        private Map<String, Artifact.Type> arguments;

        CoreFunctor(String serviceCall) {
            this.serviceCall = serviceCall;
        }

        public String getServiceCall() {
            return serviceCall;
        }

        public static CoreFunctor classify(ServiceCall serviceCall) {
            if (serviceCall.getUrn().startsWith("klab.core.")) {
                try {
                    return CoreFunctor.valueOf(serviceCall.getUrn());
                } catch (IllegalArgumentException t) {
                    // do nothing
                }
            }
            return null;
        }
    }

    default String getServiceName() {
        return "klab.runtime.service";
    }

    /**
     * Submit an observation to the runtime in the passed scope. The return value is the observation ID, which
     * should be passed to {@link #resolve(long, ContextScope)} to start the resolution unless the value is
     * {@link Observation#UNASSIGNED_ID} which signals that the digital twin has rejected the observation (for
     * example because one was already present). The observation exists in the DT in an unresolved state until
     * resolution has finished.
     *
     * @param observation
     * @param scope
     * @return
     */
    long submit(Observation observation, ContextScope scope);

    /**
     * The main function of the runtime. It will be invoked externally only when the dataflow is externally
     * supplied and fully resolved, like from a {@link org.integratedmodelling.klab.api.knowledge.Resource}.
     *
     * @param dataflow
     * @param contextScope
     * @return
     */
    Observation runDataflow(Dataflow<Observation> dataflow, ContextScope contextScope);

    /**
     * Submit the ID of a valid observation to invoke the resolver, build a dataflow and run it to obtain the
     * resolved observation. Pass the ID of an accepted observation obtained through
     * {@link #submit(Observation, ContextScope)}. The two operations are used in
     * {@link ContextScope#observe(Observation)} to provide the full functionality with notification to the
     * scope.
     *
     * @param id
     * @param scope
     * @return the ID of the task running in the runtime, which must be identical to the observation URN and
     * will be sent to the scope with the resolution result message.
     */
    Future<Observation> resolve(long id, ContextScope scope);

    /**
     * Retrieve any assets from the knowledge graph in the digital twin matching a given class and some query
     * objects.
     *
     * @param contextScope    the scope for the request, which will determine the point in the knowledge graph
     *                        to start searching from
     * @param assetClass      the type of asset requested
     * @param queryParameters any objects that will identify one or more assets of the passed type in the
     *                        passed scope, such as an observable, a string for a name or a geometry. All
     *                        passed objects will restrict the search.
     * @param <T>
     * @return
     */
    <T extends RuntimeAsset> List<T> retrieveAssets(ContextScope contextScope, Class<T> assetClass,
                                                    Object... queryParameters);

    /**
     * Use the resources service and the plug-in system to handle a model proposal from the resolver. The
     * incoming request will propose to use resources, functions and the like; the runtime may provide some of
     * those natively or use the resources services to locate them and load them. If the empty resource set is
     * returned, it should contain informative notifications and the resolver will look for a different
     * strategy.
     *
     * @param contextualizables
     * @param scope
     * @return
     */
    ResourceSet resolveContextualizables(List<Contextualizable> contextualizables, ContextScope scope);

    /**
     * All services publish capabilities and have a call to obtain them. Must list all the available
     * contextualizers and verbs, with associated costs, so that they can be checked before sending a
     * dataflow.
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
     * Retrieve information for all the active sessions accessible to the passed scope. The info is enough to
     * recreate the same scopes at client side.
     *
     * @param scope any scope, which will define visibility. User scopes with admin role will obtain
     *              everything.
     * @return the list of sessions with their contexts
     */
    List<SessionInfo> getSessionInfo(Scope scope);

    /**
     * Release the passed session, releasing any context scopes created in it.
     *
     * @param scope
     * @return
     */
    boolean releaseSession(SessionScope scope);

    /**
     * Release the passed scope, deleting all data. Should
     * @param scope
     * @return
     */
    boolean releaseContext(ContextScope scope);

    interface Admin {

        /**
         * If runtime exceptions have caused the building of test cases, retrieve them as a map of case
         * class->source code, with the option of deleting them after responding.
         *
         * @param scope          if service scope, send all; otherwise send those pertaining to the scope
         * @param deleteExisting delete after sending
         * @return
         */
        Map<String, String> getExceptionTestcases(Scope scope, boolean deleteExisting);
    }

}
