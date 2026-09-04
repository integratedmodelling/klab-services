package org.integratedmodelling.klab.api;

import org.integratedmodelling.klab.api.knowledge.Concept;
import org.integratedmodelling.klab.api.knowledge.Observable;

/**
 * TODO add accepted http requests and payload types for both input (POST) and output
 *
 * <p>TODO make this an enum hierarchy with all those, use method for the path
 */
public interface ServicesAPI {

  String API_BASE = "/api/v1";

  /** Retrieve a typed information projection from any k.LAB service. */
  String INFO = API_BASE + "/info/{knowledgeClass}/{urn}";

  /** Query typed information projections from any k.LAB service. */
  String QUERY = API_BASE + "/query/{knowledgeClass}";

  // legacy - hub is same architecture as before
  String HUB_API_BASE = "/api/v2";

  /**
   * Request header to communicate and reconstruct the calling scope at server side when requests
   * need a session or context scope. The value is formatted according to the syntax parsed by the
   * {@link org.integratedmodelling.klab.api.scope.ContextScope.ScopeData} class.
   */
  String SCOPE_HEADER = "klab-scope";

  /** Should be checked at each request at production */
  String KLAB_VERSION_HEADER = "klab-version";

  /** For resolution requests */
  String RESOLUTION_PROJECT_HEADER = "klab-resolution-project";

  /** For resolution requests */
  String RESOLUTION_NAMESPACE_HEADER = "klab-resolution-namespace";

  /**
   * Scopes that have their natural home in a service will set the native service ID in this header,
   * so that other services can access their native functions through a client if they have it.
   */
  String SERVICE_ID_HEADER = "klab-service";

  String TRANSACTION_ID_HEADER = "klab-transaction";
  String CONTEXT_OBSERVATION_ID_HEADER = "klab-context-observation";
  String SOURCE_OBSERVATION_ID_HEADER = "klab-source-observation";
  String TARGET_OBSERVATION_ID_HEADER = "klab-target-observation";

  /**
   * Request header to communicate and reconstruct the calling scope at server side when requests
   * need a session or context scope. The value is formatted according to the syntax parsed by the
   * {@link org.integratedmodelling.klab.api.scope.ContextScope.ScopeData} class.
   */
  String TASK_ID_HEADER = "klab-task";

  /**
   * Server secret key to match with the service secret to validate local or privileged connections
   * independent of authentication.
   */
  String SERVER_KEY_HEADER = "server-key";

  /**
   * Header used to both request and confirm the set of messaging queues wanted or offered for
   * communication with the digital twin. The value list must conform to the names of the {@link
   * org.integratedmodelling.klab.api.services.runtime.Message.Queue} enum.
   */
  String MESSAGING_QUEUES_HEADER = "messaging-queues";

  /**
   * this is used across the stack as a token for anonymous usage of the services. It enables access
   * to all services with non-privileged, read-only access to knowledge declared public.
   */
  String ANONYMOUS_TOKEN = "018fc355-c123-7608-be4a-89ea1059c33e";

  String CAPABILITIES = "/public/capabilities";
  String HEALTH = "/public/health";
  String STATUS = "/public/status";

  /**
   * Sent to all services by the engine implementation upon authentication of a user scope after
   * successful service connection. Contains details of all services known to the scope. The service
   * will create a peer user scope, refreshing the JWT access token in case it exists, ensure that
   * it can access them, and store clients for all new services for fast access through peer scopes
   * in successive calls.
   */
  String NOTIFY_USER_SCOPE = "/notifyUserScope";

  /**
   * Create a session scope, return the scope ID unless a sessionId is passed as a parameter (which
   * should only be done by "master" services when they need a peer in another service). The
   * original session and context/observation scopes should normally be created in the runtime.
   *
   * <p>The request is a POST endpoint that will detail the URLs of any services used by the owning
   * UserScope at the engine side. These can be overridden in context scopes that are created with
   * the same request.
   *
   * <p>If the receiving service provides AMQP messaging, the MESSAGING_URN_HEADER header in the
   * response will be set to the full URN of the service. Each session ID and context ID will
   * correspond to a queue that clients can subscribe to.
   */
  String CREATE_SESSION = "/createSession";

  /**
   * Create an observation scope in a session in the runtime, return the scope ID.
   *
   * <p>The createContext is a POST endpoint must have the OBSERVER_HEADER set to the ID of a valid
   * session returned by CREATE_SESSION. The context is created empty and without observer, unless
   * the POST data contain the definition of one.
   */
  String CREATE_CONTEXT = "/createContext";

  /**
   * Release a session. Invoked by the master session (in the runtime) on all services where a slave
   * session was created.
   */
  String RELEASE_SESSION = "/releaseSession";

  /**
   * Release a context. Invoked by the master context (in the runtime) on all services where a slave
   * context was created.
   */
  String RELEASE_CONTEXT = "/releaseContext";

  /**
   * Asset import using either multipart file import or properties, according to passed schema.
   * Schema ID must be in capabilities and a schema compatible with the media type will be looked
   * up.
   *
   * <p>If no URN is suggested, pass X:X:X:X
   */
  String IMPORT = "/import/{schema}/{urn}";

  /**
   * Asset stream download for all services that have assets to download, using URN and content
   * negotiation for specifics.
   */
  String EXPORT = "/export/{class}/{urn}";

  /** General administration endpoints common to all services */
  interface ADMIN {

    String SHUTDOWN = "/shutdown";
    String CHECK_CREDENTIALS = "/checkCredentials";
    String CREDENTIALS = "/credentials";
    String SETTINGS = "/settings";
    String SET = "/set/{setting}";
  }

  /**
   * The jobs system is managed through the submission of completable futures indexed by an ID whose
   * status can be polled and eventual results retrieved through the API. Each session has a job
   * manager.
   */
  interface JOBS {
    /** Inquire about the status of a job */
    String STATUS = "/jobs/status/{id}";

    /** Retrieve the JSON results of a finished job */
    String RETRIEVE = "/jobs/retrieve/{id}";

    /** Retrieve the {@link org.integratedmodelling.klab.api.data.Data} result of a finished job */
    String RETRIEVE_DATA = "/jobs/retrieveData/{id}";

    /** Cancel a running job */
    String CANCEL = "/jobs/cancel/{id}";
  }

  interface ENGINE {}

  interface HUB {
    /** Base URL path for engine resources on the hub. */
    String ENGINE_BASE = HUB_API_BASE + "/engines";

    String AUTH_BASE = "/auth-cert";

    // TODO rename /nodes to /services (?)
    String SERVICE_BASE = HUB_API_BASE + "/nodes";

    String USER_BASE = HUB_API_BASE + "/users";

    /**
     * Returns authenticated user details and network status with all nodes (including offline if
     * applicable) with refresh rate and unique network access token.
     *
     * <p><b>Protocol:</b> POST <br>
     * <b>Response type:</b> Json <br>
     * <b>Request:</b> {@code
     * org.integratedmodelling.klab.rest.resources.requests.AuthenticationRequest} <br>
     * <b>Response:</b> {@code
     * org.integratedmodelling.klab.rest.resources.responses.AuthenticationResponse} <br>
     * <b>Authentication:</b> open
     */
    String AUTHENTICATE_ENGINE = ENGINE_BASE + AUTH_BASE;

    /**
     * Called by nodes on hubs when authenticating with them. Parameters like the engine version.
     */
    String AUTHENTICATE_SERVICE = SERVICE_BASE + AUTH_BASE;

    /** POST endpoint to receive user authentication from a UserAuthenticationRequest */
    String AUTHENTICATE_USER = USER_BASE + "/log-in";

    /** Called from services to have information about the user */
    String USER_BASE_ID_SERVICES = HUB_API_BASE + "/users/services/{id}";

    String USER_BASE_ID = USER_BASE + "/{id}";

    /**
     * Base URL path for user's agreements resources on the hub. This GET endpoint returns a
     * generated certificate. Needs the agreement ID that isn't known in advance.
     */
    String USER_AGREEMENT_BASE_ID = USER_BASE_ID + "/{agreementId}";
  }

  /**
   * API for reasoner service.
   *
   * @author ferd
   */
  interface REASONER {

    /**
     * Resolve a concept definition passed as a request body, returning a unique ID for the
     * reasoner, the normalized URN form and any metadata.
     *
     * @protocol POST
     * @service
     * @produces {@link Concept}
     */
    String RESOLVE_CONCEPT = API_BASE + "/resolve/concept";

    /**
     * @protocol POST for a string definition passed as request body
     * @produces {@link Observable}
     */
    String RESOLVE_OBSERVABLE = API_BASE + "/resolve/observable";

    String SEMANTIC_SEARCH = API_BASE + "/semanticSearch";

    String HAS_TRAIT = API_BASE + "/hasTrait";

    String RAW_OBSERVABLE = API_BASE + "/rawObservable";

    String SUBSUMES = API_BASE + "/subsumes";

    String OPERANDS = API_BASE + "/operands";

    String CHILDREN = API_BASE + "/children";

    String PARENTS = API_BASE + "/parents";

    String PARENT = API_BASE + "/parent";

    String ALL_CHILDREN = API_BASE + "/allChildren";

    String ALL_PARENTS = API_BASE + "/allParents";

    String CLOSURE = API_BASE + "/closure";

    String CORE_OBSERVABLE = API_BASE + "/coreObservable";

    String CORE_SUBSTANTIAL = API_BASE + "/coreSubstantial";

    String SPLIT_OPERATORS = API_BASE + "/splitOperators";

    String DISTANCE = API_BASE + "/distance";

    String ROLES = API_BASE + "/roles";

    String HAS_ROLE = API_BASE + "/hasRole";

    String INHERENT = API_BASE + "/inherent";

    String GOAL = API_BASE + "/goal";

    String COOCCURRENT = API_BASE + "/cooccurent";

    String CAUSANT = API_BASE + "/causant";

    String CAUSED = API_BASE + "/caused";

    String ADJACENT = API_BASE + "/adjacent";

    String COMPRESENT = API_BASE + "/compresent";

    String RELATIVE_TO = API_BASE + "/relativeTo";

    String TRAITS = API_BASE + "/traits";

    String IDENTITIES = API_BASE + "/identities";

    String ATTRIBUTES = API_BASE + "/attributes";

    String REALMS = API_BASE + "/realms";

    String LEXICAL_ROOT = API_BASE + "/lexicalRoot";

    String BASE_OBSERVABLE = API_BASE + "/baseObservable";

    String HAS_PARENT_ROLE = API_BASE + "/hasParentRole";

    String SEMANTIC_TYPE = API_BASE + "/semanticType";

    String IMPLIED_ROLES = API_BASE + "/impliedRoles";

    String IMPLIED_ROLE = API_BASE + "/impliedRole";

    String COMPUTE_OBSERVATION_STRATEGIES = API_BASE + "/observationStrategies";

    String COMPUTE_IDENTIFICATION_STRATEGY = API_BASE + "/identificationStrategy";

    String ROLES_FOR = API_BASE + "/rolesFor";

    String CREATED = API_BASE + "/created";

    String AFFECTED = API_BASE + "/affected";

    String AFFECTED_OR_CREATED = API_BASE + "/affectedOrCreated";

    String CREATED_BY = API_BASE + "/createdBy";

    String AFFECTED_BY = API_BASE + "/affectedBy";

    String LGC = API_BASE + "/leastGenericCommon";

    String OCCURRENT = API_BASE + "/occurrent";

    String CONTEXTUALLY_COMPATIBLE = API_BASE + "/contextuallyCompatible";

    String COMPATIBLE = API_BASE + "/compatible";

    String DESCRIBED = API_BASE + "/described";

    String APPLICABLE = API_BASE + "/applicable";

    String DOMAIN = API_BASE + "/domain";

    String NEGATED = API_BASE + "/negated";

    String MATCHES = API_BASE + "/matches";

    String RELATIONSHIP_TARGETS = API_BASE + "/relationshipTargets";

    String SATISFIABLE = API_BASE + "/satisfiable";

    String RESOLVING = API_BASE + "/resolving";

    String RELATIONSHIP_TARGET = API_BASE + "/relationshipTarget";

    String RELATIONSHIP_SOURCES = API_BASE + "/relationshipSources";

    String RELATIONSHIP_SOURCE = API_BASE + "/relationshipSource";

    String LOAD_KNOWLEDGE = API_BASE + "/loadKnowledge";
    String UPDATE_KNOWLEDGE = API_BASE + "/updateKnowledge";
    String DEFINE_CONCEPT = API_BASE + "/defineConcept";

    /** Endpoints for authorities configuration, creation, discovery and use */
    interface AUTHORITIES {}
  }

  /**
   * The runtime API uses GraphQL on the context URL (runtime URL + / + contextId) to access
   * anything in the context.
   */
  interface RUNTIME {

    String GET_CONTEXT_INFO = API_BASE + "/contexts";

    String DIGITAL_TWIN_PREFIX = API_BASE + "/dt/";

    /**
     * The endpoint for digital twin access. With JSON media type, this will send the top-level DT
     * information. With HTML media type, this will return the DT's explorer application.
     */
    String DIGITAL_TWIN = DIGITAL_TWIN_PREFIX + "{id}";

    String CONNECT = API_BASE + "/connect";

    String GET_COMMIT_INFO = API_BASE + "/commit";

    String GET_SERVICE_INFO = API_BASE + "/service/{urn}";

    /**
     * Retrieve the configuration correspondent to the passed ID. Execute in a valid session scope.
     */
    String GET_DIGITAL_TWIN_CONFIGURATION = API_BASE + "/configuration/{id}";

    /**
     * PUT endpoint to ingest and start resolving an observation. Returns the observation ID that
     * can be used to follow the resolution task. Payload is a {@link
     * org.integratedmodelling.klab.api.services.resolver.objects.ResolutionRequest} instance.
     */
    String SUBMIT_OBSERVATION = API_BASE + "/submit";

    String REGISTER_OBSERVATION = API_BASE + "/register";

    String GET_SHARDING_STRATEGY = API_BASE + "/sharding";

    /** Structured Knowledge Graph query */
    String QUERY = API_BASE + "/query";

    /**
     * GET endpoint to quickly retrieve a specific asset from the knowledge graph by using its long
     * ID.
     */
    String RETRIEVE_KNOWLEDGE_GRAPH_ASSET = API_BASE + "/asset/{id}";

    /** Retrieve a knowledge-graph asset whose stable identity is a URN rather than a numeric ID. */
    String RETRIEVE_KNOWLEDGE_GRAPH_ASSET_BY_URN = API_BASE + "/asset/urn";

    /**
     * GET endpoint to retrieve the links between assets in the knowledge graph, returning only the
     * asset IDs as per {@link org.integratedmodelling.klab.api.data.KnowledgeGraph.LinkInfo}.
     */
    String RETRIEVE_KNOWLEDGE_GRAPH_LINKS = API_BASE + "/links";

    /**
     * POST or GET endpoint for visualization of a URN, including the "adapter" identifier and the
     * URN of the visualized asset. GET will return the full visualization with the standard
     * geometry and options. POST will enable specifying different options and geometry.
     */
    String VISUALIZE_ASSET = API_BASE + "/visualize/{method}/{urn}";

    /**
     * POST endpoint that takes a list of contextualizer references and returns the ResourceSet that
     * specifies whether those will be available to the runtime and upon which conditions.
     */
    String RESOLVE_CONTEXTUALIZERS = API_BASE + "/resolve";

    /**
     * POST endpoint that takes an AgentInstantiationRequest and returns the Agent that was
     * instantiated.
     */
    String INSTANTIATE_AGENT = API_BASE + "/instantiate/agent";

    /**
     * CRUD endpoint to control a remote agent. The request is one of the operations that can be
     * performed on the agent (TBD as an enum)
     */
    String AGENT = API_BASE + "/agent/{urn}/{request}";
  }

  interface RESOURCES {

    String WORKFLOW = API_BASE + "/workflows/{workflowId}";
    String FLOWS = API_BASE + "/flows";
    String FLOW_INITIALIZATION = API_BASE + "/flows/initialize";
    String FLOW = API_BASE + "/flows/{flowId}";
    String FLOW_REOPEN = API_BASE + "/flows/{flowId}/reopen";
    String FLOW_STATES = API_BASE + "/flows/{flowId}/states";
    String FLOW_STATE = API_BASE + "/flows/{flowId}/states/{stateId}";
    String FLOW_TRANSITIONS = API_BASE + "/flows/{flowId}/transitions";
    String FLOW_ATTACHMENTS = API_BASE + "/flows/{flowId}/states/{stateId}/attachments";
    String FLOW_ATTACHMENT = API_BASE + "/flows/{flowId}/attachments/{attachmentId}";

    // TODO this can also be INFO using rights as type linked to the URN
    String RIGHTS = API_BASE + "/rights/{urn}";
    // TODO and this
    String STATUS = API_BASE + "/status/{knowledgeClass}/{urn}";

    String CONTEXTUALIZE = "/contextualize";
    String CONTEXTUALIZE_RESOURCE = "/contextualizeResource";

    /**
     * The RESOLVE endpoint always returns a ResourceSet with the full dependency closure for the
     * intended asset. The URN may also be a comma-separated list of URNs.
     */
    String RESOLVE = API_BASE + "/resolve/{knowledgeClass}/{urn}";

    String DELETE = API_BASE + "/delete/{knowledgeClass}/{urn}";

    /** PUT endpoint to ingest an asset for addition, update or replacement */
    String SUBMIT = API_BASE + "/submit/{knowledgeClass}/{submissionMode}/{urn}";

    /**
     * GET endpoint to retrieve a list of assets of a given type. A POST endpoint may specify a
     * query.
     */
    String LIST = API_BASE + "/list/{knowledgeClass}";

    /**
     * RETRIEVE endpoints are GET endpoints that return the full asset definition for the passed
     * URN.
     */
    String RETRIEVE = API_BASE + "/retrieve/{knowledgeClass}/{urn}";

    String RESOLVE_URN = "/resolve/{urn}";

    /** FIXME deprecate? */
    String RESOLVE_EXPORT_SCHEMA = "/resolveExportSchema";

    /** FIXME deprecate? */
    String RESOLVE_IMPORT_SCHEMA = "/resolveImportSchema";

    /** Set/get the access rights for the passed resource URN */
    String RESOURCE_INFO = "/resourceInfo/{urn}";

    /** Mark the retained local source as published on an authoritative remote service. */
    String MARK_PUBLISHED = "/resourceInfo/{urn}/publication/{serviceId}";

    String PARSE_ASSET = "/parseAsset/{assetClass}";

    String MANAGE_PROJECT = API_BASE + "/project/manage/{urn}";

    /**
     * If successful, stop automatic file management for the project and respond with a URL to
     * either the file:/ location of the project (if the request comes from a client sharing the
     * same filesystem) or the http:// URL to a zip containing the current version of the project.
     * Prepare to receive project updates allowing the requesting user to modify files to the
     * UPDATE_* endpoints.
     *
     * <p>TODO this should be PUT
     */
    String LOCK_PROJECT = API_BASE + "/project/lock/{urn}";

    /**
     * Resume file management and disallow the user from updating project files for the project.
     * TODO make this PUT
     */
    String UNLOCK_PROJECT = API_BASE + "/project/unlock/{urn}";
  }

  interface RESOLVER {

    String RESOLVE_OBSERVATION = API_BASE + "/resolve";

    String SUBMIT_RESOURCE = API_BASE + "/resource";

    String GET_SUBMITTED_RESOURCES = API_BASE + "/resources";
  }
}
