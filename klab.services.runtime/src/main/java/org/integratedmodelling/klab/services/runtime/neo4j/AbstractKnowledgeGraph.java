package org.integratedmodelling.klab.services.runtime.neo4j;

import org.integratedmodelling.klab.api.collections.Parameters;
import org.integratedmodelling.klab.api.data.KnowledgeGraph;
import org.integratedmodelling.klab.api.data.RuntimeAsset;
import org.integratedmodelling.klab.api.digitaltwin.DigitalTwin;
import org.integratedmodelling.klab.api.exceptions.KlabInternalErrorException;
import org.integratedmodelling.klab.api.knowledge.SemanticType;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.provenance.Activity;
import org.integratedmodelling.klab.api.provenance.Agent;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.services.runtime.Actuator;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public abstract class AbstractKnowledgeGraph implements KnowledgeGraph {

    protected ContextScope scope;

    record Step(OperationImpl.Type type, List<RuntimeAsset> targets, Map<String, Object> parameters) {
    }

    class OperationImpl implements Operation {

        private Agent agent;
        private String description;

        enum Type {
            CREATE,
            MODIFY,
            LINK,
            SELECT
        }

        private List<Step> steps = new ArrayList<>();

        @Override
        public long run() {
            return runOperation(steps);
        }

        @Override
        public Operation add(RuntimeAsset observation) {
            this.steps.add(new Step(Type.CREATE, List.of(observation), Map.of()));
            return this;
        }

        //        @Override
        //        public Operation create(Object... parameters) {
        ////            this.steps.add(new Step(Type.CREATE, List.of(observation), Map.of()));
        //            return this;
        //        }

        @Override
        public Operation set(RuntimeAsset source, Object... properties) {
            this.steps.add(new Step(Type.MODIFY, List.of(source), Parameters.create(properties)));
            return this;
        }

        @Override
        public Operation link(RuntimeAsset assetFrom, RuntimeAsset assetTo, DigitalTwin.Relationship relationship, Object... linkData) {
            this.steps.add(new Step(Type.LINK, List.of(assetFrom, assetTo), Parameters.create(linkData)));
            return this;
        }

        @Override
        public Operation rootLink(RuntimeAsset asset, Object... linkData) {
            return this;
        }

        @Override
        public Operation success(ContextScope scope) {
            finalizeOperation(this, scope, true);
            return this;
        }

        @Override
        public Operation fail(ContextScope scope) {
            finalizeOperation(this, scope, false);
            return this;
        }

        public Agent getAgent() {
            return agent;
        }

        public void setAgent(Agent agent) {
            this.agent = agent;
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
    }

    protected abstract void finalizeOperation(OperationImpl operation, ContextScope scope, boolean b);

    //    protected abstract Map<String, Object> nodeProperties(long nodeId);

    @Override
    public Operation activity(Agent agent, ContextScope scope, Object... targets) {

        OperationImpl ret = new OperationImpl();

        ret.setAgent(agent);

        if (targets != null && targets.length > 0) {
            for (var target : targets) {
                switch (target) {
                    case Observation observation -> {
                        if (observation.getId() < 0) {
                            ret.add(observation);
                        } else {
                            ret.set(observation);
                        }
                        if (scope.getContextObservation() != null) {
                            ret.link(
                                    observation, scope.getContextObservation(),
                                    DigitalTwin.Relationship.Parent);
                        } else {
                            ret.rootLink(observation);
                        }
                        if (scope.getObserver() != null) {
                            ret.link(observation, scope.getObserver(), DigitalTwin.Relationship.Observer);
                        }
                    }
                    case Actuator actuator -> {
                    }
                    case Activity activity -> {
                    }
                    case String string -> ret.setDescription(string);
                    default -> throw new KlabInternalErrorException("Unexpected target in op");
                }
            }
        }
        return ret;
    }

    long runOperation(List<Step> operation) {

        long ret = Observation.UNASSIGNED_ID;
        for (var step : operation) {
            switch (step.type) {
                case CREATE -> {
                    ret = create(step.targets, step.parameters);
                }
                case MODIFY -> {
                    ret = modify(step.targets, step.parameters);
                }
                case LINK -> {
                    ret = link(step.targets, step.parameters);
                }
            }
            return ret;
        }

        return Observation.UNASSIGNED_ID;
    }

    protected abstract long create(List<RuntimeAsset> targets, Map<String, Object> parameters);

    protected abstract long link(List<RuntimeAsset> targets, Map<String, Object> parameters);

    protected abstract long modify(List<RuntimeAsset> targets, Map<String, Object> parameters);

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
            }
            case Actuator actuator -> {
            }
            case Activity activity -> {
            }
            default -> throw new KlabInternalErrorException(
                    "unexpected value for asParameters: " + asset.getClass().getCanonicalName());
        }
        return ret;
    }

    //    protected RuntimeAsset fromParameters(RuntimeAsset asset, Map<String, Object> parameters) {
    //        return asset;
    //    }


    /**
     * Extract all POD properties and metadata fields from an asset to pass to the operation unless
     * overridden.
     *
     * @param asset
     * @return
     */
    protected Object[] getProperties(RuntimeAsset asset, Object... overridingProperties) {
        return new Object[]{};
    }

}
