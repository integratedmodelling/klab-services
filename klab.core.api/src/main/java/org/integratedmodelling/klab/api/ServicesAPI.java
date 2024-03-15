package org.integratedmodelling.klab.api;

import org.integratedmodelling.klab.api.knowledge.Concept;
import org.integratedmodelling.klab.api.knowledge.Observable;

/**
 * TODO add accepted http requests and payload types for both input (POST) and output
 */
public interface ServicesAPI {

	public static final String API_BASE = "/api/v2";

	public static String CAPABILITIES = "/public/capabilities";
	public static String SHUTDOWN = "/shutdown";
	public static String STATUS = "/public/status";

	/**
	 * STOMP endpoint for client/server notifications to session receivers. Handled through
	 * Websockets protocol.
	 */
	public static final String MESSAGE = "/message";

	public interface HUB {
		/**
		 * Base URL path for engine resources on the hub.
		 */
		public static final String ENGINE_BASE = API_BASE + "/engines";

		public static final String AUTH_BASE = "/auth-cert";


		// TODO rename /nodes to /services (?)
		public static final String SERVICE_BASE = API_BASE + "/nodes";

		/**
		 * Returns authenticated user details and network status with all nodes
		 * (including offline if applicable) with refresh rate and unique network access
		 * token.
		 *
		 * <p>
		 * <b>Protocol:</b> POST <br/>
		 * <b>Response type:</b> Json <br/>
		 * <b>Request:</b>
		 * {@code org.integratedmodelling.klab.rest.resources.requests.AuthenticationRequest}
		 * <br/>
		 * <b>Response:</b>
		 * {@code org.integratedmodelling.klab.rest.resources.responses.AuthenticationResponse}
		 * <br/>
		 * <b>Authentication:</b> open
		 */
		public static final String AUTHENTICATE_ENGINE = ENGINE_BASE + AUTH_BASE;

		/**
		 * Called by nodes on hubs when authenticating with them. Parameters like the engine
		 * version.
		 */
		public static final String AUTHENTICATE_SERVICE = SERVICE_BASE + AUTH_BASE;
	}

	/**
	 * API for reasoner service.
	 * 
	 * @author ferd
	 *
	 */
	public interface REASONER {

		static String REASONER_BASE = API_BASE;

		/**
		 * Resolve a concept definition, returning a unique ID for the reasoner, the
		 * normalized URN form and any metadata.
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

		public static String SEMANTIC_SEARCH = REASONER_BASE + "/semantic-search";

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

//		public static String CONTEXT = REASONER_BASE + "/context";

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

		/**
		 * Reasoner plug-ins can extend the observation strategies.
		 * 
		 * @author Ferd
		 *
		 */
		public interface ADMIN extends PluginAPI {

		}

	}

	public interface RUNTIME {

		/**
		 * Runtime plug-ins can extend the contextualizers and the storage
		 * infrastructure.
		 * 
		 * @author Ferd
		 *
		 */
		public interface ADMIN extends PluginAPI {

		}
	}

	/*
	TODO move admin endpoints to ADMIN
	 */
	public interface RESOURCES {

        String RESOLVE_PROJECT = "/resolveProject/{projectName}";
        String QUERY_RESOURCES = "/queryResources";
        String PRECURSORS = "/precursors/{namespaceId}";
        String PROJECTS = "/projects";
        String PROJECT = "/project/{projectName}";
        String MODEL = "/model/{modelName}";
        String RESOLVE_URN = "/resolve/{urn}";
        String RESOLVE_NAMESPACE_URN = "/resolveNamespace/{urn}";
        String RESOLVE_ONTOLOGY_URN = "/resolveOntology/{urn}";
        String RESOLVE_OBSERVATION_STRATEGY_DOCUMENT_URN = "/resolveObservationStrategyDocument/{urn}";
        String LIST_WORKSPACES = "/listWorkspaces";
        String RESOLVE_BEHAVIOR_URN = "/resolveBehavior/{urn}";
        String RESOLVE_RESOURCE_URN = "/resolveResource/{urn}";
        String RESOLVE_WORKSPACE_URN = "/resolveWorkspace/{urn}";
        String RESOLVE_SERVICE_CALL = "/resolveServiceCall/{name}";
        String RESOURCE_STATUS = "/resourceStatus/{urn}";
        String RESOLVE_OBSERVABLE = "/resolveObservable";
        String DESCRIBE_CONCEPT = "/describeConcept/{conceptUrn}";
        String RESOLVE_CONCEPT = "/resolveConcept/{definition}";
        String CONTEXTUALIZE_RESOURCE = "/contextualizeResource";
        String CONTEXTUALIZE = "/contextualize";
        String RESOLVE_DATAFLOW_URN = "/resolveDataflow/{urn}";
        String GET_WORLDVIEW = "/getWorldview";
        String DEPENDENTS = "/dependents/{namespaceId}";
        String QUERY_MODELS = "/queryModels";
        String MODEL_GEOMETRY = "/modelGeometry/{modelUrn}";
        String READ_BEHAVIOR = "/readBehavior";
        String IMPORT_PROJECT = "/importProject";
        String CREATE_PROJECT = "/createProject";
        String UPDATE_PROJECT = "/updateProject";
        String CREATE_NAMESPACE = "/createNamespace";
        String UPDATE_NAMESPACE = "/updateNamespace";
        String CREATE_BEHAVIOR = "/createBehavior";
        String UPDATE_BEHAVIOR = "/updateBehavior";
        String CREATE_ONTOLOGY = "/createOntology";
        String UPDATE_ONTOLOGY = "/updateOntology";
        String PUBLISH_PROJECT = "/publishProject";
        String UNPUBLISH_PROJECT = "/unpublishProject";
        String CREATE_RESOURCE = "/createResource";
        String CREATE_RESOURCE_FROM_PATH = "/createResourceFromPath";
        String CREATE_RESOURCE_FOR_PROJECT = "/createResourceForProject";
        String PUBLISH_RESOURCE = "/publishResource";
        String UNPUBLISH_RESOURCE = "/unpublishResource";
        String REMOVE_PROJECT = "/removeProject";
        String REMOVE_WORKSPACE = "/removeWorkspace";
        String LIST_PROJECTS = "/listProjects";
        String LIST_RESOURCE_URNS = "/listResourceUrns";

        /**
		 * Resource plug-ins provide resource adapters.
		 * 
		 * @author Ferd
		 *
		 */
		public interface ADMIN extends PluginAPI {

		}
	}

	public interface RESOLVER {

		public interface ADMIN extends PluginAPI {

		}
	}

	public interface COMMUNITY {

		public interface ADMIN {

		}
	}

}
