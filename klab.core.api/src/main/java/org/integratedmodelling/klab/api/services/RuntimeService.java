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
     * Create a session with the passed name in the passed user scope. If urn(s) are passed, match them to the
     * correspondent behaviors, test namespaces or scenarios and initialize the session accordingly. Return
     * the unique session ID.
     *
     * @param sessionScope a client scope that should record the ID for future communication. If the ID is
     *                     null, the call has failed.
     * @return the ID of the new session created at server side, or null in case of failure.
     */
    String registerSession(SessionScope sessionScope);

    /**
     * Create a context with the passed name in the passed session. Context starts empty with the default
     * observer for the worldview, using the services available to the user and passed as parameters. The same
     * runtime that hosts the context must become the one and only runtime accessible to the resulting scope.
     *
     * @param contextScope a client scope that should record the ID for future communication. If the ID is
     *                     null, the call has failed.
     * @return the ID of the new session created at server side, or null in case of failure.
     */
    String registerContext(ContextScope contextScope);

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

        /**
         * URL for the message broker. If null, the engine doesn't have messaging capabilities and will not
         * enable distributed digital twin functionalities. If this isn't null, the context/DT ID is the
         * channel for communication of that context.
         *
         * @return the broker URL or null
         */
        URI getBrokerURI();
    }

    /**
     * Scope CAN be null for generic public capabilities.
     *
     * @param scope
     * @return
     */
    Capabilities capabilities(Scope scope);


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
