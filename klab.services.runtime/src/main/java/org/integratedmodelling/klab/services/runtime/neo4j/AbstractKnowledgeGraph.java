package org.integratedmodelling.klab.services.runtime.neo4j;

import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import org.apache.groovy.util.Arrays;
import org.integratedmodelling.klab.api.collections.Pair;
import org.integratedmodelling.klab.api.data.KnowledgeGraph;
import org.integratedmodelling.klab.api.data.RuntimeAsset;
import org.integratedmodelling.klab.api.digitaltwin.DigitalTwin;
import org.integratedmodelling.klab.api.engine.Engine;
import org.integratedmodelling.klab.api.exceptions.KlabInternalErrorException;
import org.integratedmodelling.klab.api.knowledge.SemanticType;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.provenance.Activity;
import org.integratedmodelling.klab.api.provenance.Agent;
import org.integratedmodelling.klab.api.provenance.impl.ActivityImpl;
import org.integratedmodelling.klab.api.provenance.impl.AgentImpl;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.scope.Scope;
import org.integratedmodelling.klab.api.services.Language;
import org.integratedmodelling.klab.api.services.runtime.Actuator;
import org.neo4j.driver.Transaction;

import java.io.IOException;
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
                    return retrieve(key, RuntimeAsset.class, scope);
                }
            });

    public record Step(OperationImplObsolete.Type type, List<RuntimeAsset> targets, Object[] parameters) {
    }

    /**
     * A provenance-linked "transaction" that can be committed or rolled back by reporting failure or success.
     * The related activity becomes part of the graph in any case and success/failure is recorded with it.
     * Everything else stored or linked is rolled back in case of failure.
     */
    public class OperationImpl implements Operation {

        ActivityImpl activity;
        AgentImpl agent;
        Transaction transaction;
        Scope.Status outcome;
        Throwable exception;

        /**
         * What should be passed: an agent that will own the activity; the current context scope for graph
         * operations; the activity type
         * <p>
         * What can be passed: another operation so that its activity becomes the parent of the one we create
         * here AND the transaction isn't finished upon execution; content for the activity such as description
         * <p>
         * The store/link methods use the same on the database, under the transaction we opened.
         *
         * Each ExecutorOperation must include a previously built Operation; only the wrapping one should
         * commit/rollback.
         *
         * @param arguments
         */
        public OperationImpl(Object... arguments) {

            // select arguments and put them where they belong

            // validate arguments and complain loudly if anything is missing

            // create and commit the activity record as a node, possibly linked to a parent
            // activity.

            // open the transaction for the remaining operations
        }

        @Override
        public Agent getAgent() {
            return null;
        }

        @Override
        public Activity getActivity() {
            return null;
        }

        @Override
        public long store(RuntimeAsset asset, Object... additionalProperties) {
            return 0;
        }

        @Override
        public void link(RuntimeAsset source, RuntimeAsset destination,
                         DigitalTwin.Relationship relationship, Scope scope, Object... additionalProperties) {

        }

        @Override
        public long run(ContextScope scope) {
            return 0;
        }

        @Override
        public Operation add(RuntimeAsset observation) {
            return null;
        }

        @Override
        public Operation set(RuntimeAsset source, Object... properties) {
            // TODO remove
            return null;
        }

        @Override
        public Operation link(RuntimeAsset assetFrom, RuntimeAsset assetTo,
                              DigitalTwin.Relationship relationship, Object... linkData) {
            // TODO remove
            return null;
        }

        @Override
        public Operation rootLink(RuntimeAsset asset, Object... linkData) {
            // TODO remove
            return null;
        }

        @Override
        public Operation success(ContextScope scope, Object... assets) {
            // commit
            return null;
        }

        @Override
        public Operation fail(ContextScope scope, Object... assets) {
            // rollback
            return null;
        }

        @Override
        public void close() throws IOException {
            // TODO commit or rollback based on status after success() or fail(). If none has been
            // called, status is null and this is an internal error, logged with the activity
        }
    }

    public class OperationImplObsolete implements Operation {

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

        @Override
        public void close() throws IOException {

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
        public Operation success(ContextScope scope, Object... assets) {
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

        @Override
        public long store(RuntimeAsset asset, Object... additionalProperties) {
            return 0;
        }

        @Override
        public void link(RuntimeAsset source, RuntimeAsset destination,
                         DigitalTwin.Relationship relationship, Scope scope, Object... additionalProperties) {

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

    protected abstract RuntimeAsset getContextNode();

    /**
     * Return a RuntimeAsset representing the overall dataflow related to the scope, so that it can be used
     * for linking using the other CRUD methods.
     *
     * @return the dataflow root node, unique for the context.
     * @throws org.integratedmodelling.klab.api.exceptions.KlabIllegalStateException if the graph is not
     *                                                                               contextualized.
     */
    protected abstract RuntimeAsset getDataflowNode();

    /**
     * Return a RuntimeAsset representing the overall provenance related to the scope, so that it can be used
     * for linking using the other CRUD methods.
     *
     * @return the dataflow root node, unique for the context.
     * @throws org.integratedmodelling.klab.api.exceptions.KlabIllegalStateException if the graph is not
     *                                                                               contextualized.
     */
    protected abstract RuntimeAsset getProvenanceNode();

    /**
     * Retrieve the asset with the passed key.
     *
     * @param key
     * @param assetClass
     * @param <T>
     * @return
     */
    protected abstract <T extends RuntimeAsset> T retrieve(long key, Class<T> assetClass, Scope scope);

    /**
     * Store the passed asset, return its unique long ID.
     *
     * @param asset
     * @param additionalProperties any pair of properties we want overridden. Pass pairs and do it right or
     *                             you'll get an exception.
     * @return
     */
    protected abstract long store(RuntimeAsset asset, Scope scope, Object... additionalProperties);

    /**
     * Link the two passed assets.
     *
     * @param source
     * @param destination
     * @param additionalProperties any pair of properties we want overridden. Pass pairs and do it right or
     *                             you'll get an exception.
     */
    protected abstract void link(RuntimeAsset source, RuntimeAsset destination,
                                 DigitalTwin.Relationship relationship, Scope scope,
                                 Object... additionalProperties);

    protected abstract long runOperation(OperationImplObsolete operation, ContextScope scope);

    /**
     * Call at the end of each activity on the result of {@link #activity(Agent, ContextScope, Object...)},
     * passing any assets that must be updated or added to the graph.
     *
     * @param operation
     * @param scope
     * @param success
     * @param resultsToUpdate
     */
    protected abstract void finalizeOperation(OperationImplObsolete operation, ContextScope scope,
                                              boolean success,
                                              Object... resultsToUpdate);

    @Override
    public Operation activity(Agent agent, ContextScope scope, Object... targets) {

        OperationImplObsolete ret = new OperationImplObsolete();

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
            return (T) assetCache.get(id);
        } catch (ExecutionException e) {
            scope.error(e);
            return null;
        }
    }

    /**
     * Define all properties for the passed asset.
     *
     * @param asset
     * @param additionalParameters any pair of additional parameters to add
     * @return
     */
    protected Map<String, Object> asParameters(Object asset, Object... additionalParameters) {
        Map<String, Object> ret = new HashMap<>();
        if (asset != null) {
            switch (asset) {
                case Observation observation -> {
                    ret.putAll(observation.getMetadata());
                    ret.put("name", observation.getName() == null ? observation.getObservable().codeName()
                                                                  : observation.getName());
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

                    ret.put("observationId", actuator.getId());
                    StringBuilder code = new StringBuilder();
                    for (var call : actuator.getComputation()) {
                        // TODO skip any recursive resolution calls and prepare for linking later
                        code.append(call.encode(Language.DEFAULT_EXPRESSION_LANGUAGE)).append("\n");
                    }
                    ret.put("semantics", actuator.getObservable().getUrn());
                    ret.put("computation", code.toString());
                    ret.put("strategy", actuator.getStrategyUrn());
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
        }

        if (additionalParameters != null) {
            for (int i = 0; i < additionalParameters.length; i++) {
                ret.put(additionalParameters[i].toString(), additionalParameters[++i]);
            }
        }

        return ret;
    }

}
