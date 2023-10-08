package org.integratedmodelling.klab.api.services;

import java.util.Map;
import java.util.concurrent.Future;

import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.scope.Scope;
import org.integratedmodelling.klab.api.services.runtime.Dataflow;

/**
 * TODO consider whether to add a <T extends Artifact> and leave the possibility
 * of implementing one that handles non-semantic artifacts. Could be a door into
 * wrapping external computational services like OpenEO that can interpret k.LAB
 * dataflows.
 *
 * @author Ferd
 */
public interface RuntimeService extends KlabService {

    default String getServiceName() {
        return "klab.runtime.service";
    }

    public static final int DEFAULT_PORT = 8094;

    /**
     * Release all data related to this scope. This should be called at scope finalization.
     *
     * @param scope
     * @return
     */
    boolean releaseScope(ContextScope scope);

    /**
     * All services publish capabilities and have a call to obtain them. Must list all the available contextualizers and
     * verbs, with associated costs, so that they can be checked before sending a dataflow.
     *
     * @author Ferd
     */
    interface Capabilities extends ServiceCapabilities {

    }

    Capabilities capabilities();

    /**
     * @param dataflow
     * @param scope
     * @return
     */
    Future<Observation> run(Dataflow<Observation> dataflow, ContextScope scope);

    interface Admin {

        /**
         * If runtime exceptions have caused the building of test cases, retrieve them as a map of case class->source
         * code, with the option of deleting them after responding.
         *
         * @param scope          if service scope, send all; otherwise send those pertaining to the scope
         * @param deleteExisting delete after sending
         * @return
         */
        Map<String, String> getExceptionTestcases(Scope scope, boolean deleteExisting);
    }

}
