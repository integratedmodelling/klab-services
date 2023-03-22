package org.integratedmodelling.klab.api;

import org.integratedmodelling.klab.api.knowledge.Concept;
import org.integratedmodelling.klab.api.knowledge.Observable;

public interface ServicesAPI {

    public static final String API_BASE = "/api/v2";

    public static String CAPABILITIES = "/capabilities";
    
    /**
     * API for reasoner service.
     * 
     * @author ferd
     *
     */
    public interface REASONER {

        static String REASONER_BASE = API_BASE;

        /**
         * Resolve a concept definition, returning a unique ID for the reasoner, the normalized URN
         * form and any metadata.
         * 
         * @protocol GET
         * @service 
         * @produces {@link Concept}
         */
        public static String RESOLVE_CONCEPT = REASONER_BASE + "/resolve/concept/{definition}";

        /**
         * 
         * @protocol GET
         * @produces {@link Observable}
         */
        public static String RESOLVE_OBSERVABLE = REASONER_BASE + "/resolve/observable/{definition}";

    }
}
