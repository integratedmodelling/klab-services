package org.integratedmodelling.klab.api.services;

import java.net.URI;
import java.net.URL;
import java.util.List;
import java.util.Map;

import org.integratedmodelling.klab.api.data.KnowledgeGraph;
import org.integratedmodelling.klab.api.data.RuntimeAsset;
import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.lang.Contextualizable;
import org.integratedmodelling.klab.api.provenance.Provenance;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.scope.Scope;
import org.integratedmodelling.klab.api.scope.SessionScope;
import org.integratedmodelling.klab.api.services.resources.ResourceSet;
import org.integratedmodelling.klab.api.services.runtime.Dataflow;
import org.integratedmodelling.klab.api.services.runtime.objects.SessionInfo;

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

    default String getServiceName() {
        return "klab.runtime.service";
    }

    /**
     * Submit an observation to the runtime in the passed scope. If startResolution is true, resolution will
     * start after insertion in the knowledge graph. API calls will normally want to start the resolution;
     * passing false is normally done only by the resolver itself, to create the observations that will later
     * be resolved explicitly. In all cases the return value is the observation ID, which can be used to
     * follow the resolution task progress through AMPQ messaging (if resolving and configured) or for polling
     * and retrieval.
     *
     * @param observation
     * @param scope
     * @param startResolution if true, invoke the resolver and run the resolving dataflow, otherwise just add
     *                        the asset to the graph
     * @param agentName       the name of the agent requesting the observation, for provenance recording. Null
     *                        can be passed but shouldn't.
     * @return
     */
    long submit(Observation observation, ContextScope scope, boolean startResolution);

    /**
     * The main function of the runtime.
     * <p>
     * FIXME have it return a coverage
     *
     * @param dataflow
     * @param operation    the activity that ran this. The dataflow will create child activity of it.
     * @param contextScope
     * @return
     */
    Provenance runDataflow(Dataflow<Observation> dataflow, ContextScope contextScope);

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

    @Override
    default boolean scopesAreReactive() {
        return true;
    }

    /**
     * Retrieve information for all the active sessions accessible to the passed scope.
     *
     * @param scope any scope, which will define visibility. User scopes with admin role will obtain
     *              everything.
     * @return the list of sessions with their contexts
     */
    List<SessionInfo> getSessionInfo(Scope scope);

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
