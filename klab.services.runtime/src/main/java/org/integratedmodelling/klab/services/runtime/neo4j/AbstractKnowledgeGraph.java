package org.integratedmodelling.klab.services.runtime.neo4j;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import org.apache.groovy.util.Arrays;
import org.integratedmodelling.klab.api.collections.Pair;
import org.integratedmodelling.klab.api.data.KnowledgeGraph;
import org.integratedmodelling.klab.api.data.RuntimeAsset;
import org.integratedmodelling.klab.api.digitaltwin.DigitalTwin;
import org.integratedmodelling.klab.api.exceptions.KlabInternalErrorException;
import org.integratedmodelling.klab.api.exceptions.KlabUnimplementedException;
import org.integratedmodelling.klab.api.knowledge.SemanticType;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.provenance.Activity;
import org.integratedmodelling.klab.api.provenance.Agent;
import org.integratedmodelling.klab.api.provenance.impl.ActivityImpl;
import org.integratedmodelling.klab.api.provenance.impl.AgentImpl;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.services.runtime.Actuator;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

public abstract class AbstractKnowledgeGraph implements KnowledgeGraph {


    protected static int MAX_CACHED_OBSERVATIONS = 400;

    protected ContextScope scope;
    protected LoadingCache<Long, RuntimeAsset> assetCache =
            CacheBuilder.newBuilder().maximumSize(MAX_CACHED_OBSERVATIONS).build(new CacheLoader<Long,
                    RuntimeAsset>() {
                @Override
                public RuntimeAsset load(Long key) throws Exception {
                    return getAssetForKey(key);
                }
            });


    public record Step(OperationImpl.Type type, List<RuntimeAsset> targets, Object[] parameters) {
    }

    public class OperationImpl implements Operation {

        private AgentImpl agent;
        private String description;
        private ActivityImpl activity = new ActivityImpl();
        /*
         list of the object created with their locator in the query (field -> value)
         FIXME this is lame - rewrite
         */
        private Map<Object, Pair<String, Object>> assetLocators = new HashMap<>();

        public String getAssetKeyProperty(RuntimeAsset asset) {

            if (assetLocators.containsKey(asset)) {
                return assetLocators.get(asset).getFirst();
            }

            if (asset.getId() > 0) {
                return "id";
            }

            throw new KlabInternalErrorException("Unregistered asset in graph operation: " + asset);
        }

        public Object getAssetKey(RuntimeAsset asset) {
            if (assetLocators.containsKey(asset)) {
                return assetLocators.get(asset).getSecond();
            }
            if (asset.getId() > 0) {
                return asset.getId();
            }
            throw new KlabInternalErrorException("Unregistered asset in graph operation: " + asset);
        }

        enum Type {
            CREATE,
            MODIFY,
            LINK,
            ROOT_LINK
        }

        private List<Step> steps = new ArrayList<>();

        @Override
        public long run(ContextScope scope) {
            return runOperation(this, scope);
        }

        @Override
        public Operation add(RuntimeAsset asset) {
            this.steps.add(new Step(Type.CREATE, List.of(asset), null));
            return this;
        }

        @Override
        public Operation set(RuntimeAsset asset, Object... properties) {
            this.steps.add(new Step(Type.MODIFY, List.of(asset), properties));
            return this;
        }

        /**
         * Link the asset to where it pertains on the scope with the pertaining relationship:
         *
         * <ol>
         * <li>If the scope is focused on an observation and the asset is an observation, link it to that;
         * </li>
         * <li>If the asset is an observation and the scope is the root context scope, link it to the
         * context</li>
         * <li>If the asset is an Activity, link it to the Provenance or to the Activity that created the
         * observation the scope is
         * focused on;</li>
         * <li>If the asset is an actuator, link it either to the Dataflow or to the Actuator with
         * the same ID of the observation the scope is focused on;</li>
         * <li>throw {@link org.integratedmodelling.klab.api.exceptions.KlabIllegalArgumentException} in
         * any other situation.</li>
         * </ol>
         *
         * @param asset
         * @param scope
         * @return
         */
        public Operation link(RuntimeAsset asset, ContextScope scope) {
            // TODO
            return this;
        }

        @Override
        public Operation link(RuntimeAsset assetFrom, RuntimeAsset assetTo,
                              DigitalTwin.Relationship relationship, Object... linkData) {
            this.steps.add(new Step(Type.LINK, List.of(assetFrom, assetTo),
                    Arrays.concat(new Object[]{relationship}, linkData)));
            return this;
        }

        @Override
        public Operation rootLink(RuntimeAsset asset, Object... linkData) {
            this.steps.add(new Step(Type.ROOT_LINK, List.of(asset), linkData));
            return this;
        }

        @Override
        public Operation success(ContextScope scope, RuntimeAsset... assets) {
            finalizeOperation(this, scope, true, assets);
            return this;
        }

        @Override
        public Operation fail(ContextScope scope, Object... objects) {
            finalizeOperation(this, scope, false);
            return this;
        }


        public Agent getAgent() {
            return agent;
        }

        public void setAgent(Agent agent) {
            this.agent = Agent.promote(agent);
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public List<Step> getSteps() {
            return steps;
        }

        public void setSteps(List<Step> steps) {
            this.steps = steps;
        }

        public void setAgent(AgentImpl agent) {
            this.agent = agent;
        }

        public ActivityImpl getActivity() {
            return activity;
        }

        public void setActivity(ActivityImpl activity) {
            this.activity = activity;
        }

        /**
         * Register an asset after it has been created in the graph so that it can be located in a successive
         * query at finalization.
         *
         * @param asset the asset to register
         * @param field the field that identifies it in a query
         * @param key   the value of the field that uniquely identifies the assed
         */
        public void registerAsset(Object asset, String field, Object key) {
            if (key instanceof Long longKey && asset instanceof RuntimeAsset runtimeAsset) {
                assetCache.put(longKey, runtimeAsset);
            }
            assetLocators.put(asset, Pair.of(field, key));
        }
    }

    protected RuntimeAsset getAssetForKey(long key) {
        throw new KlabUnimplementedException("RETRIEVAL OF ARBITRARY ASSET FROM DB INTO CACHE");
    }

    protected abstract long runOperation(OperationImpl operation, ContextScope scope);

    /**
     * Call at the end of each activity on the result of {@link #activity(Agent, ContextScope, Object...)},
     * passing any assets that must be updated or added to the graph.
     *
     * @param operation
     * @param scope
     * @param success
     * @param resultsToUpdate
     */
    protected abstract void finalizeOperation(OperationImpl operation, ContextScope scope, boolean success,
                                              RuntimeAsset... resultsToUpdate);

    @Override
    public Operation activity(Agent agent, ContextScope scope, Object... targets) {

        OperationImpl ret = new OperationImpl();

        ret.activity.setStart(System.currentTimeMillis());

        // first thing is to add the activity we represent
        ret.add(ret.activity);

        ret.setAgent(agent);
        boolean hasParent = false;
        if (targets != null && targets.length > 0) {
            for (var target : targets) {

                // allow nulls and ignore them for fluency in calls
                if (target == null) {
                    continue;
                }

                switch (target) {
                    case Observation observation -> {
                        if (observation.getId() < 0) {
                            ret.add(observation);
                            ret.activity.setType(Activity.Type.INITIALIZATION);
                        } else {
                            ret.set(observation);
                        }
                        if (scope.getContextObservation() != null) {
                            ret.link(
                                    observation, scope.getContextObservation(),
                                    DigitalTwin.Relationship.HAS_PARENT);
                        } else {
                            ret.rootLink(observation);
                        }
                        if (scope.getObserver() != null) {
                            ret.link(observation, scope.getObserver(), DigitalTwin.Relationship.HAS_OBSERVER);
                        }
                    }
                    case Activity activity -> {
                        // this is the parent activity
                        ret.link(ret.activity, activity, DigitalTwin.Relationship.HAS_PARENT);
                        hasParent = true;
                    }
                    case Actuator actuator -> {
                        ret.add(actuator);
                        ret.link(ret.activity, actuator, DigitalTwin.Relationship.HAS_PLAN);
                        // TODO must have ID of observation: link to the obs it contextualizes. Activity
                        //  becomes a
                        // contextualization.
                    }
                    case String string -> ret.activity.setDescription(string);
                    // override activity type
                    case Activity.Type type -> ret.activity.setType(type);
                    // TODO the plan: should include the resolution constraints in the scope
                    default -> throw new KlabInternalErrorException("Unexpected target in op");
                }
            }

            if (!hasParent) {
                ret.rootLink(ret.activity, DigitalTwin.Relationship.HAS_PARENT);
            }
        }
        return ret;
    }

    @Override
    public <T extends RuntimeAsset> T get(long id, Class<T> resultClass) {
        try {
            return (T)assetCache.get(id);
        } catch (ExecutionException e) {
            scope.error(e);
            return null;
        }
    }

    /**
     * Define all properties for the passed asset.
     *
     * @param asset
     * @return
     */
    protected Map<String, Object> asParameters(Object asset) {
        Map<String, Object> ret = new HashMap<>();
        switch (asset) {
            case Observation observation -> {
                ret.putAll(observation.getMetadata());
                ret.put("name", observation.getName());
                ret.put("updated", observation.getLastUpdate());
                ret.put("resolved", observation.isResolved());
                ret.put("type", observation.getType().name());
                ret.put("urn", observation.getUrn());
                ret.put("semantictype", SemanticType.fundamentalType(
                        observation.getObservable().getSemantics().getType()).name());
                ret.put("semantics", observation.getObservable().getUrn());
            }
            case Agent agent -> {
                // TODO
            }
            case Actuator actuator -> {
                // TODO
            }
            case Activity activity -> {
                ret.putAll(activity.getMetadata());
                ret.put("credits", activity.getCredits());
                ret.put("description", activity.getDescription());
                ret.put("end", activity.getEnd());
                ret.put("start", activity.getStart());
                ret.put("schedulerTime", activity.getSchedulerTime());
                ret.put("size", activity.getSize());
                ret.put("type", activity.getType().name());
                ret.put("name", activity.getName());
            }
            default -> throw new KlabInternalErrorException(
                    "unexpected value for asParameters: " + asset.getClass().getCanonicalName());
        }
        return ret;
    }

}
