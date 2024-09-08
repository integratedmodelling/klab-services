package org.integratedmodelling.klab.api;

import org.integratedmodelling.klab.api.knowledge.Concept;
import org.integratedmodelling.klab.api.knowledge.Observable;

/**
 * TODO add accepted http requests and payload types for both input (POST) and output
 */
public interface ServicesAPI {

    String API_BASE = "/api/v2";

    /**
     * Request header to communicate and reconstruct the calling scope at server side when requests need a
     * session or context scope. The value is formatted according to the syntax parsed by the
     * {@link org.integratedmodelling.klab.api.scope.ContextScope.ScopeData} class.
     */
    String SCOPE_HEADER = "klab-scope";
    /**
     * Server secret key to match with the service secret to validate local or privileged connections
     * independent of authentication.
     */
    String SERVER_KEY_HEADER = "server-key";
    /**
     * Response header for the URN of a AMQP messaging service, returned along with the
     * {@link RUNTIME#CREATE_SESSION} response when messaging is available.
     */
    String MESSAGING_URN_HEADER = "messaging-urn";

    /**
     * Header used to both request and confirm the set of messaging queues wanted or offered for communication
     * with the digital twin. The value list must conform to the names of the
     * {@link org.integratedmodelling.klab.api.services.runtime.Message.Queue} enum.
     */
    String MESSAGING_QUEUES_HEADER = "messaging-queues";

    /**
     * this is used across the stack as a token for anonymous usage of the services. It enables access to all
     * services with non-privileged, read-only access to knowledge declared public.
     */
    String ANONYMOUS_TOKEN = "018fc355-c123-7608-be4a-89ea1059c33e";

    String CAPABILITIES = "/public/capabilities";
    String STATUS = "/public/status";

    String URN_PARAMETER = "{urn}";

    /**
     * Create a session scope, return the scope ID unless a sessionId is passed as a parameter (which should
     * only be done by "master" services when they need a peer in another service). The original session and
     * context/observation scopes should normally be created in the runtime.
     * <p>
     * The request is a POST endpoint that will detail the URLs of any services used by the owning UserScope
     * at the engine side. These can be overridden in context scopes that are created with the same request.
     * <p>
     * If the receiving service provides AMQP messaging, the MESSAGING_URN_HEADER header in the response will
     * be set to the full URN of the service. Each session ID and context ID will correspond to a queue that
     * clients can subscribe to.
     */
    String CREATE_SESSION = "/createSession";

    /**
     * Create an observation scope in a session in the runtime, return the scope ID.
     * <p>
     * The createContext is a POST endpoint must have the OBSERVER_HEADER set to the ID of a valid session
     * returned by CREATE_SESSION. The context is created empty and without observer, unless the POST data
     * contain the definition of one.
     */
    String CREATE_CONTEXT = "/createContext";

    /**
     * General administration endpoints common to all services
     */
    interface ADMIN {

        String SHUTDOWN = "/shutdown";
        String CHECK_CREDENTIALS = "/checkCredentials";
        String CREDENTIALS = "/credentials";
    }

    //    interface SCOPE {
    //
    ////        /**
    ////         * Create a new scope of the passed type as a precondition for creation of a client scope.
    // Returns the
    ////         * scope ID and any other data such as quotas or permissions, possibly including a
    // Websockets or other
    ////         * channel info for duplex communication.
    ////         */
    ////        String CREATE = "/scope/create/{scopeType}";
    //
    ////        /**
    ////         * Register an existing scope with a service so that the service can associate it to
    // successive
    ////         * requests and potentially open a communication channel for pairing. Implied in CREATE as
    // well, but
    ////         * only for cases when the scope is already fully functional at the client side. May
    // respond with
    ////         * channel details for duplex communication.
    ////         */
    ////        String REGISTER = "/scope/register/{scopeType}/{scopeId}";
    //
    ////        /**
    ////         * Dispose of a previously created or registered scope.
    ////         */
    ////        String DISPOSE = "/scope/dispose/{scopeId}";
    //
    //    }

    interface ENGINE {

    }


    /**
     * STOMP endpoint for client/server notifications to session receivers. Handled through Websockets
     * protocol.
     */
    String MESSAGE = "/public/message";

    interface HUB {
        /**
         * Base URL path for engine resources on the hub.
         */
        String ENGINE_BASE = API_BASE + "/engines";

        String AUTH_BASE = "/auth-cert";


        // TODO rename /nodes to /services (?)
        String SERVICE_BASE = API_BASE + "/nodes";

        /**
         * Returns authenticated user details and network status with all nodes (including offline if
         * applicable) with refresh rate and unique network access token.
         *
         * <p>
         * <b>Protocol:</b> POST <br/>
         * <b>Response type:</b> Json <br/>
         * <b>Request:</b>
         * {@code org.integratedmodelling.klab.rest.resources.requests.AuthenticationRequest} <br/>
         * <b>Response:</b>
         * {@code org.integratedmodelling.klab.rest.resources.responses.AuthenticationResponse} <br/>
         * <b>Authentication:</b> open
         */
        String AUTHENTICATE_ENGINE = ENGINE_BASE + AUTH_BASE;

        /**
         * Called by nodes on hubs when authenticating with them. Parameters like the engine version.
         */
        String AUTHENTICATE_SERVICE = SERVICE_BASE + AUTH_BASE;
    }

    /**
     * API for reasoner service.
     *
     * @author ferd
     */
    interface REASONER {

        String REASONER_BASE = API_BASE;

        /**
         * Resolve a concept definition, returning a unique ID for the reasoner, the normalized URN form and
         * any metadata.
         *
         * @protocol GET
         * @service
         * @produces {@link Concept}
         */
        String RESOLVE_CONCEPT = REASONER_BASE + "/resolve/concept/{definition}";

        /**
         * @protocol GET for a string definition encoded in the URL
         * @produces {@link Observable}
         */
        String RESOLVE_OBSERVABLE = REASONER_BASE + "/resolve/observable/{definition}";

        /**
         * @protocol POST for a map containing the KimObservable definition as "OBSERVABLE" and possibly
         * pattern variables
         */
        String DECLARE_OBSERVABLE = REASONER_BASE + "/declare/observable";

        String SEMANTIC_SEARCH = REASONER_BASE + "/semanticSearch";

        String HAS_TRAIT = REASONER_BASE + "/hasTrait";

        String RAW_OBSERVABLE = REASONER_BASE + "/rawObservable";

        String SUBSUMES = REASONER_BASE + "/subsumes";

        String OPERANDS = REASONER_BASE + "/operands";

        String CHILDREN = REASONER_BASE + "/children";

        String PARENTS = REASONER_BASE + "/parents";

        String PARENT = REASONER_BASE + "/parent";

        String ALL_CHILDREN = REASONER_BASE + "/allChildren";

        String ALL_PARENTS = REASONER_BASE + "/allParents";

        String CLOSURE = REASONER_BASE + "/closure";

        String CORE_OBSERVABLE = REASONER_BASE + "/coreObservable";

        String SPLIT_OPERATORS = REASONER_BASE + "/splitOperators";

        String DISTANCE = REASONER_BASE + "/distance";

        String ROLES = REASONER_BASE + "/roles";

        String HAS_ROLE = REASONER_BASE + "/hasRole";

        String INHERENT = REASONER_BASE + "/inherent";

        String GOAL = REASONER_BASE + "/goal";

        String COOCCURRENT = REASONER_BASE + "/cooccurent";

        String CAUSANT = REASONER_BASE + "/causant";

        String CAUSED = REASONER_BASE + "/caused";

        String ADJACENT = REASONER_BASE + "/adjacent";

        String COMPRESENT = REASONER_BASE + "/compresent";

        String RELATIVE_TO = REASONER_BASE + "/relativeTo";

        String TRAITS = REASONER_BASE + "/traits";

        String IDENTITIES = REASONER_BASE + "/identities";

        String ATTRIBUTES = REASONER_BASE + "/attributes";

        String REALMS = REASONER_BASE + "/realms";

        String BASE_PARENT_TRAIT = REASONER_BASE + "/baseParentTrait";

        String BASE_OBSERVABLE = REASONER_BASE + "/baseObservable";

        String HAS_PARENT_ROLE = REASONER_BASE + "/hasParentRole";

        String SEMANTIC_TYPE = REASONER_BASE + "/semanticType";

        String IMPLIED_ROLES = REASONER_BASE + "/impliedRoles";

        String IMPLIED_ROLE = REASONER_BASE + "/impliedRole";

        String ROLES_FOR = REASONER_BASE + "/rolesFor";

        String CREATED = REASONER_BASE + "/created";

        String AFFECTED = REASONER_BASE + "/affected";

        String AFFECTED_OR_CREATED = REASONER_BASE + "/affectedOrCreated";

        String CREATED_BY = REASONER_BASE + "/createdBy";

        String AFFECTED_BY = REASONER_BASE + "/affectedBy";

        String LGC = REASONER_BASE + "/leastGenericCommon";

        String OCCURRENT = REASONER_BASE + "/occurrent";

        String CONTEXTUALLY_COMPATIBLE = REASONER_BASE + "/contextuallyCompatible";

        String COMPATIBLE = REASONER_BASE + "/compatible";

        String DESCRIBED = REASONER_BASE + "/described";

        String APPLICABLE = REASONER_BASE + "/applicable";

        String DOMAIN = REASONER_BASE + "/domain";

        String NEGATED = REASONER_BASE + "/negated";

        String RELATIONSHIP_TARGETS = REASONER_BASE + "/relationshipTargets";

        String SATISFIABLE = REASONER_BASE + "/satisfiable";

        String RELATIONSHIP_TARGET = REASONER_BASE + "/relationshipTarget";

        String RELATIONSHIP_SOURCES = REASONER_BASE + "/relationshipSources";

        String RELATIONSHIP_SOURCE = REASONER_BASE + "/relationshipSource";

        /**
         * Reasoner plug-ins can extend the observation strategies.
         *
         * @author Ferd
         */
        interface ADMIN extends PluginAPI {

            String LOAD_KNOWLEDGE = "/loadKnowledge";
            String UPDATE_KNOWLEDGE = "/updateKnowledge";
            String DEFINE_CONCEPT = "/defineConcept";
        }

        /**
         * Endpoints for authorities configuration, creation, discovery and use
         */
        interface AUTHORITIES extends PluginAPI {


        }

    }

    /**
     * The runtime API uses GraphQL on the context URL (runtime URL + / + contextId) to access anything in the
     * context.
     */
    interface RUNTIME {

        /**
         * Runtime plug-ins can extend the contextualizers and the storage infrastructure.
         *
         * @author Ferd
         */
        interface ADMIN extends PluginAPI {

        }


        /**
         * The GraphQL endpoint for digital twin access.
         */
        String DIGITAL_TWIN_GRAPH = "/dt";

    }

    public interface RESOURCES {

        String RESOLVE_PROJECT = "/resolveProject/{projectName}";
        String QUERY_RESOURCES = "/queryResources";
        String PRECURSORS = "/precursors/{namespaceId}";
        String PROJECTS = "/projects";
        String PROJECT = "/project/{projectName}";
        String MODEL = "/model/{modelName}";
        String RESOLVE_URN = "/resolve/" + URN_PARAMETER;
        String RESOLVE_NAMESPACE_URN = "/resolveNamespace/" + URN_PARAMETER;
        String RESOLVE_ONTOLOGY_URN = "/resolveOntology/" + URN_PARAMETER;
        String RESOLVE_OBSERVATION_STRATEGY_DOCUMENT_URN =
                "/resolveObservationStrategyDocument/" + URN_PARAMETER;
        String LIST_WORKSPACES = "/listWorkspaces";
        String RESOLVE_BEHAVIOR_URN = "/resolveBehavior/" + URN_PARAMETER;
        String RESOLVE_RESOURCE_URN = "/resolveResource/" + URN_PARAMETER;
        String RESOLVE_WORKSPACE_URN = "/resolveWorkspace/" + URN_PARAMETER;
        String RESOLVE_SERVICE_CALL = "/resolveServiceCall/{name}";
        String RESOURCE_STATUS = "/resourceStatus/" + URN_PARAMETER;
        String RESOLVE_OBSERVABLE = "/resolveObservable";
        String DESCRIBE_CONCEPT = "/describeConcept/{conceptUrn}";
        String RESOLVE_CONCEPT = "/resolveConcept/{definition}";
        String CONTEXTUALIZE_RESOURCE = "/contextualizeResource";
        String CONTEXTUALIZE = "/contextualize";
        String RESOLVE_DATAFLOW_URN = "/resolveDataflow/" + URN_PARAMETER;
        String GET_WORLDVIEW = "/getWorldview";
        String DEPENDENTS = "/dependents/{namespaceId}";
        String QUERY_MODELS = "/queryModels";
        String MODEL_GEOMETRY = "/modelGeometry/{modelUrn}";
        String READ_BEHAVIOR = "/readBehavior";
        //        String PUBLISH_PROJECT = "/publishProject";
        //        String UNPUBLISH_PROJECT = "/unpublishProject";
        String CREATE_RESOURCE = "/createResource";
        //        String PUBLISH_RESOURCE = "/publishResource";
        //        String UNPUBLISH_RESOURCE = "/unpublishResource";
        String LIST_PROJECTS = "/listProjects";
        String LIST_RESOURCE_URNS = "/listResourceUrns";
        /**
         * Set/get the access rights for the passed resource URN
         */
        String RESOURCE_RIGHTS = "/rights/{urn}";

        /**
         * Resource plug-ins provide resource adapters.
         *
         * @author Ferd
         */
        public interface ADMIN extends PluginAPI {

            /**
             * Import project. POST endpoint with ProjectRequest body.
             */
            String IMPORT_PROJECT = "/importProject";
            /**
             * Create new empty project in passed workspace.
             */
            String CREATE_PROJECT = "/createProject/{workspaceName}/{projectName}";

            /**
             * POST request to update an existing project's manifest
             */
            String UPDATE_PROJECT = "/createProject/{projectName}";

            /**
             * GET endpoint: create new document with passed URN. Return changes in each workspace affected.
             */
            String CREATE_DOCUMENT = "/createDocument/{projectName}/{documentType}/{urn}";
            /**
             * Update document with passed type. POST endpoint whose body is the document content. Return
             * changes in each workspace affected.
             */
            String UPDATE_DOCUMENT = "/updateDocument/{projectName}/{documentType}";
            String CREATE_RESOURCE = "/createResource";
            String IMPORT_RESOURCE = "/importResource";
            String UPLOAD_RESOURCE = "/uploadResource";
            String REMOVE_PROJECT = "/removeProject/{urn}";
            String REMOVE_WORKSPACE = "/removeWorkspace/{urn}";
            String REMOVE_DOCUMENT = "/removeDocument/{projectName}/{urn}";
            String MANAGE_PROJECT = "/manageProject/{urn}";
            /**
             * If successful, stop automatic file management for the project and respond with a URL to either
             * the file:/ location of the project (if the request comes from a client sharing the same
             * filesystem) or the http:// URL to a zip containing the current version of the project. Prepare
             * to receive project updates allowing the requesting user to modify files to the UPDATE_*
             * endpoints.
             */
            String LOCK_PROJECT = "/lockProject/{urn}";

            /**
             * Resume file management and disallow the user from updating project files for the project.
             */
            String UNLOCK_PROJECT = "/unlockProject/{urn}";

        }
    }

    interface RESOLVER {

        interface ADMIN extends PluginAPI {

        }

        String RESOLVE_OBSERVATION = "/resolve";
    }

    interface COMMUNITY {

        interface ADMIN {

        }
    }

}
