package org.integratedmodelling.klab.api.services;

import java.io.Serializable;

import org.integratedmodelling.klab.api.knowledge.Concept;
import org.integratedmodelling.klab.api.knowledge.observation.scope.Scope;
import org.integratedmodelling.klab.api.lang.kim.KimModelStatement;
import org.integratedmodelling.klab.api.services.resources.ResourceSet;
import org.integratedmodelling.klab.api.services.runtime.Dataflow;

public interface Resolver extends KlabService {

    /**
     * All services publish capabilities and have a call to obtain them.
     * 
     * @author Ferd
     *
     */
    interface Capabilities extends Serializable {

    }

    /**
     * Get the service capabilities.
     * 
     * @return
     */
    Capabilities getCapabilities();

    /**
     * The main function of the resolver. Returns a dataflow that must be executed by a runtime
     * service. Observable may be or resolve to any knowledge compatible with the observation scope.
     * If the scope is a session scope, the observable must be an acknowledgement unless the scope
     * has a set scale, in which case it can be a subject concept.
     * 
     * @param observable
     * @param scope
     * @return the dataflow that will create the observation in a runtime.
     */
    Dataflow<?> resolve(Object observable, Scope scope);

    interface Admin {

        /**
         * Load all usable models from the namespaces included in the passed resource set. If there
         * is a linked semantic server and it is local and/or exclusive, also load any existing
         * semantics, otherwise raise an exception when encountering a concept definition.
         * 
         * @param resources
         * @return
         */
        boolean loadKnowledge(ResourceSet resources);

        /**
         * The "port" to ingest a model. Order of ingestion must be such that all knowledge and
         * constraints are resolved. Automatically stores the model in the local k.Box.
         * 
         * @param statement
         * @return
         */
        Concept addModel(KimModelStatement statement);
    }

}
