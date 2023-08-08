package org.integratedmodelling.klab.api.services;

import java.util.List;

import org.integratedmodelling.klab.api.knowledge.DescriptionType;
import org.integratedmodelling.klab.api.knowledge.Instance;
import org.integratedmodelling.klab.api.knowledge.KlabAsset;
import org.integratedmodelling.klab.api.knowledge.Knowledge;
import org.integratedmodelling.klab.api.knowledge.Model;
import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.knowledge.observation.scale.Scale;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.scope.Scope;
import org.integratedmodelling.klab.api.services.resolver.Resolution;
import org.integratedmodelling.klab.api.services.resources.ResourceSet;
import org.integratedmodelling.klab.api.services.runtime.Dataflow;

public interface Resolver extends KlabService {

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
    Capabilities capabilities();

    /**
     * The resolver holds the translation of the lexical {@link KlabAsset}s into resolvable
     * {@link Knowledge}, using the {@link ResourcesService} in the scope to build them when
     * necessary. This method resolves a {@link Model} or an {@link Instance} from their syntactic
     * peers in resources, efficiently loading and validating the closure of all other knowledge
     * needed to understand it.
     * <p>
     * The search for models (by scale and semantics) should happen in the resource servers with
     * help from the reasoner, yielding a {@link ResourceSet} from
     * {@link ResourcesService#queryModels(Observable, ContextScope)}}. Models that are accessible
     * to the Resolvers but invalid should generate an info message. Should cache the build
     * {@link Model}s and refresh the cache based on version matching.
     * 
     * @param urn
     * @param scope
     * @return
     */
    <T extends Knowledge> T resolveKnowledge(String urn, Class<T> knowledgeClass, Scope scope);

    /**
     * The main function of the resolver is to resolve knowledge to a dataflow in a context scope.
     * This is done in two steps: resolution of the knowledge to a resolution graph (initialized
     * from the observations in the scope) and compilatio of the resulting graph into a dataflow
     * that can be stored or passed to a runtime service for execution.
     * <p>
     * Any {@link Knowledge} object can be resolved, compatibly with the observation scope and its
     * current observation focus. If the scope is not focused on a direct observation, the
     * resolvable must be an {@link Instance} unless the scope has a set focal scale, in which case
     * it can be a countable observable with description type {@link DescriptionType#INSTANTIATION}.
     * 
     * @param resolvable
     * @param scope
     * @return the dataflow that will create the observation in a runtime.
     */
    Resolution resolve(Knowledge resolvable, ContextScope scope);

    /**
     * Compile a resolution graph into a dataflow. The scope passed must be the same that the
     * resolution graph was computed into.
     * 
     * @param resolution
     * @param scope
     * @return
     */
    Dataflow<Observation> compile(Knowledge knowledge, Resolution resolution, ContextScope scope);

    /**
     * Encode a dataflow to its k.DL specification.
     * 
     * @param dataflow
     * @return
     */
    String encodeDataflow(Dataflow<Observation> dataflow);

    /**
     * Query all the resource servers available to find models that can observe the passed
     * observable in the scope. The result should be merged to keep the latest available versions
     * and ranked in decreasing order of fit to the context. Once the choice is made,
     * {@link #resolve(Knowledge, ContextScope)} should be used to populate the inner knowledge
     * repository. Resolution metadata for each model resolved (optionally including those not
     * chosen) should be available for inspection, reporting and debugging in the resolution graph
     * connected to the {@link ContextScope}.
     * 
     * @param observable
     * @param scope
     * @param scale we pass a scale explicitly during resolution chains that may be covering the
     *        scope partially.
     * @return
     */
    List<Model> queryModels(Observable observable, ContextScope scope, Scale scale);

    /**
     * Resolver administration functions.
     * 
     * @author Ferd
     *
     */
    interface Admin {

        /**
         * Load all usable knowledge from the namespaces included in the passed resource set. If
         * there is a linked semantic server and it is local and/or exclusive, also load any
         * existing semantics, otherwise raise an exception when encountering a concept definition.
         * If the resource set has focal URNs, make the correspondent resources available for
         * consumption by the resolver. If an incoming resource set contains resources already
         * loaded, only substitute the existing ones if they are tagged with a newer version.
         * 
         * @param resources
         * @return
         */
        boolean loadKnowledge(ResourceSet resources);

    }

}
