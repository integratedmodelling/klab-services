package org.integratedmodelling.klab.api.services;

import java.util.Map;
import java.util.concurrent.Future;

import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.scope.Scope;
import org.integratedmodelling.klab.api.services.runtime.Dataflow;

public interface RuntimeService extends KlabService {

    default String getServiceName() {
        return "klab.runtime.service";
    }

    public static final int DEFAULT_PORT = 8094;

    /**
     * All services publish capabilities and have a call to obtain them. Must list all the available
     * contextualizers and verbs, with associated costs, so that they can be checked before sending
     * a dataflow.
     * 
     * @author Ferd
     *
     */
    interface Capabilities extends ServiceCapabilities {

    }

    Capabilities capabilities();

    /**
     * 
     * @param dataflow
     * @param scope
     * @return
     */
    Future<Observation> run(Dataflow<?> dataflow, ContextScope scope);

    interface Admin {

        /**
         * If runtime exceptions have caused the building of test cases, retrieve them as a map of
         * case class->source code, with the option of deleting them after responding.
         * 
         * @param scope if service scope, send all; otherwise send those pertaining to the scope
         * @param deleteExisting delete after sending
         * @return
         */
        Map<String, String> getExceptionTestcases(Scope scope, boolean deleteExisting);
    }

}
