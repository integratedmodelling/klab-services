package org.integratedmodelling.klab.services.runtime.neo4j;

import org.integratedmodelling.klab.api.data.KnowledgeGraph;
import org.integratedmodelling.klab.api.data.RuntimeAsset;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.scope.ContextScope;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public abstract class AbstractKnowledgeGraph implements KnowledgeGraph {

    protected ContextScope scope;

    record Step(OperationImpl.Type type, List<RuntimeAsset> targets, Map<String, Object> parameters) {}

    class OperationImpl implements Operation {

        enum Type {
            CREATE,
            MODIFY,
            LINK
        }

        List<Step> steps = new ArrayList<>();

        @Override
        public long run() {
            return runOperation(steps);
        }

        @Override
        public Operation add(RuntimeAsset observation) {

            return this;
        }

        @Override
        public Operation create(Object... parameters) {
            return this;
        }

        @Override
        public Operation set(RuntimeAsset source, Object... properties) {
            return this;
        }

        @Override
        public Operation linkTo(RuntimeAsset asset, Object... linkData) {
            return this;
        }

        @Override
        public Operation linkFrom(RuntimeAsset asset, Object... linkData) {
            return this;
        }
    }

    protected abstract Map<String, Object> nodeProperties(long nodeId);

    @Override
    public Operation op(Object... target) {
        OperationImpl ret = new OperationImpl();
        if (target != null && target.length > 0) {

        }
        return ret;
    }

    long runOperation(List<Step> operation) {

        long ret = Observation.UNASSIGNED_ID;
        for (var step : operation) {
            switch(step.type) {
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

    protected Map<String, Object> asParameters(Object asset) {
        return Map.of();
    }

    protected RuntimeAsset fromParameters(RuntimeAsset asset, Map<String, Object> parameters) {
        return asset;
    }


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
