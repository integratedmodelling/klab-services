package org.integratedmodelling.klab.api.services;

import java.net.URI;
import java.net.URL;
import java.util.Map;

import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.provenance.Provenance;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.scope.Scope;
import org.integratedmodelling.klab.api.scope.SessionScope;
import org.integratedmodelling.klab.api.services.runtime.Dataflow;

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
     * Submit an observation to the runtime in the passed scope. The result is the observation ID, which
     * can be used to follow the resolution task progress through AMPQ messaging (if configured) or polling.
     *
     * @param observation
     * @param scope
     * @return
     */
    long submit(Observation observation, ContextScope scope);


    /**
     * The main function of the runtime.
     *
     * @param contextScope
     * @return
     */
    Provenance runDataflow(Dataflow<Observation> dataflow, ContextScope contextScope);


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
