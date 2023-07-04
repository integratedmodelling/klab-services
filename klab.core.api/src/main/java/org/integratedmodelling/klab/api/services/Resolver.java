package org.integratedmodelling.klab.api.services;

import java.util.List;

import org.integratedmodelling.klab.api.authentication.scope.ContextScope;
import org.integratedmodelling.klab.api.authentication.scope.Scope;
import org.integratedmodelling.klab.api.knowledge.Concept;
import org.integratedmodelling.klab.api.knowledge.Knowledge;
import org.integratedmodelling.klab.api.knowledge.Model;
import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.api.lang.kim.KimModelStatement;
import org.integratedmodelling.klab.api.services.resources.ResourceSet;
import org.integratedmodelling.klab.api.services.runtime.Dataflow;

public interface Resolver extends KlabService {

	/**
	 * Metadata key for the ranking score
	 */
	public static final String RESOLUTION_SCORE = "klab.resolution.score";
	/**
	 * Metadata key for the detailed ranking data (a map in metadata)
	 */
	public static final String RESOLUTION_DATA = "klab.resolution.data";

	default String getServiceName() {
		return "klab.resolver.service";
	}

	public static final int DEFAULT_PORT = 8093;

	/**
	 * All services publish capabilities and have a call to obtain them.
	 * 
	 * @author Ferd
	 *
	 */
	interface Capabilities extends ServiceCapabilities {

	}

	/**
	 * Get the service capabilities.
	 * 
	 * @return
	 */
	Capabilities getCapabilities();

	/**
	 * Resolve a model or an instance, using the resources service to obtain the
	 * requested knowledge object, and loading the closure of all knowledge needed
	 * to understand it.
	 * 
	 * @param urn
	 * @param scope
	 * @return
	 */
	<T extends Knowledge> T resolveKnowledge(String urn, Class<T> knowledgeClass, Scope scope);

	/**
	 * The main function of the resolver is to resolve knowledge to a dataflow (in a
	 * context scope). Returns a dataflow that must be executed by a runtime
	 * service. Observable may be or resolve to any knowledge compatible with the
	 * observation scope. If the scope is a session scope, the observable must be an
	 * acknowledgement unless the scope has a set scale, in which case it can be a
	 * subject concept.
	 * 
	 * @param resolvable
	 * @param scope
	 * @return the dataflow that will create the observation in a runtime.
	 */
	Dataflow<?> resolve(Knowledge resolvable, ContextScope scope);

	/**
	 * Query all the resource servers available to find models that can observe the
	 * passed observable in the scope. The result should be merged to keep the
	 * latest available versions and ranked in decreasing order of fit to the
	 * context; the {@link #RESOLUTION_SCORE} ranking and corresponding
	 * {@link #RESOLUTION_DATA} should be in their metadata.
	 * <p>
	 * While search for models (by scale and semantics) should happen in the
	 * resource servers with help from the reasoner, yielding a ResourceSet through
	 * TODO, the ranking should be done inside the resolver.
	 * 
	 * @param observable
	 * @param scope
	 * @return
	 */
	List<Model> queryModels(Observable observable, ContextScope scope);

	/**
	 * Resolver administration functions.
	 * 
	 * @author Ferd
	 *
	 */
	interface Admin {

		/**
		 * Load all usable knowledge from the namespaces included in the passed resource
		 * set. If there is a linked semantic server and it is local and/or exclusive,
		 * also load any existing semantics, otherwise raise an exception when
		 * encountering a concept definition. If the resource set has focal URNs, make
		 * the correspondent resources available for consumption by the resolver. If an
		 * incoming resource set contains resources already loaded, only substitute the
		 * existing ones if they are tagged with a newer version.
		 * 
		 * @param resources
		 * @return
		 */
		boolean loadKnowledge(ResourceSet resources);

		/**
		 * The "port" to ingest a model. Order of ingestion must be such that all
		 * knowledge and constraints are resolved. Automatically stores the model in the
		 * local k.Box.
		 * 
		 * @param statement
		 * @return
		 */
		Concept addModel(KimModelStatement statement);
	}

}
