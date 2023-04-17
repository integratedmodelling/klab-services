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

        
        public static String HAS_TRAIT = REASONER_BASE + "/has-trait";

        public static String RAW_OBSERVABLE = REASONER_BASE + "/raw-observable";

        public static String SUBSUMES = REASONER_BASE + "/subsumes";

        public static String OPERANDS = REASONER_BASE + "/operands";

        public static String CHILDREN = REASONER_BASE + "/children";

        public static String PARENTS = REASONER_BASE + "/parents";

        public static String PARENT = REASONER_BASE + "/parent";

        public static String ALL_CHILDREN = REASONER_BASE + "/all-children";

        public static String ALL_PARENTS = REASONER_BASE + "/all-parents";

        public static String CLOSURE = REASONER_BASE + "/closure";

        public static String CORE_OBSERVABLE = REASONER_BASE + "/core-observable";

        public static String SPLIT_OPERATORS = REASONER_BASE + "/split-operators";

        public static String DISTANCE = REASONER_BASE + "/distance";

        public static String ROLES = REASONER_BASE + "/roles";

        public static String HAS_ROLE = REASONER_BASE + "/has-role";

        public static String CONTEXT = REASONER_BASE + "/context";

        public static String INHERENT = REASONER_BASE + "/inherent";

        public static String GOAL = REASONER_BASE + "/goal";

        public static String COOCCURRENT = REASONER_BASE + "/cooccurent";

        public static String CAUSANT = REASONER_BASE + "/causant";

        public static String CAUSED = REASONER_BASE + "/caused";

        public static String ADJACENT = REASONER_BASE + "/adjacent";

        public static String COMPRESENT = REASONER_BASE + "/compresent";

        public static String RELATIVE_TO = REASONER_BASE + "/relative-to";

        public static String TRAITS = REASONER_BASE + "/traits";

        public static String IDENTITIES = REASONER_BASE + "/identities";

        public static String ATTRIBUTES = REASONER_BASE + "/attributes";

        public static String REALMS = REASONER_BASE + "/realms";

        public static String BASE_PARENT_TRAIT = REASONER_BASE + "/base-parent-trait";

        public static String BASE_OBSERVABLE = REASONER_BASE + "/base-observable";

        public static String HAS_PARENT_ROLE = REASONER_BASE + "/has-parent-role";

        public static String SEMANTIC_TYPE = REASONER_BASE + "/semantic-type";

        public static String IMPLIED_ROLES = REASONER_BASE + "/implied-roles";

        public static String IMPLIED_ROLE = REASONER_BASE + "/implied-role";

        public static String ROLES_FOR = REASONER_BASE + "/roles-for";

        public static String CREATED = REASONER_BASE + "/created";

        public static String AFFECTED = REASONER_BASE + "/affected";

        public static String AFFECTED_OR_CREATED = REASONER_BASE + "/affected-or-created";

        public static String CREATED_BY = REASONER_BASE + "/created-by";

        public static String AFFECTED_BY = REASONER_BASE + "/affected-by";

        public static String LGC = REASONER_BASE + "/least-generic-common";

        public static String OCCURRENT = REASONER_BASE + "/occurrent";

        public static String CONTEXTUALLY_COMPATIBLE = REASONER_BASE + "/contextually-compatible";

        public static String COMPATIBLE = REASONER_BASE + "/compatible";

        public static String DESCRIBED = REASONER_BASE + "/described";

        public static String APPLICABLE = REASONER_BASE + "/applicable";

        public static String DOMAIN = REASONER_BASE + "/domain";

        public static String NEGATED = REASONER_BASE + "/negated";

        public static String RELATIONSHIP_TARGETS = REASONER_BASE + "/relationship-targets";

        public static String SATISFIABLE = REASONER_BASE + "/satisfiable";

        public static String RELATIONSHIP_TARGET = REASONER_BASE + "/relationship-target";

        public static String RELATIONSHIP_SOURCES = REASONER_BASE + "/relationship-sources";

        public static String RELATIONSHIP_SOURCE = REASONER_BASE + "/relationship-source";

    }
}
