package org.integratedmodelling.klab.services.runtime.neo4j;

import org.integratedmodelling.klab.api.data.KnowledgeGraph;
import org.integratedmodelling.klab.api.data.RuntimeAsset;

public abstract class AbstractKnowledgeGraph implements KnowledgeGraph {

    protected boolean contextualized;

    class OperationImpl implements Operation {

        @Override
        public long run() {
            return runOperation(this);
        }

        @Override
        public Operation add(RuntimeAsset observation) {
            return null;
        }

        @Override
        public Operation create(Object... parameters) {
            return null;
        }

        @Override
        public Operation set(RuntimeAsset source, Object... properties) {
            return null;
        }

        @Override
        public Operation linkTo(RuntimeAsset asset, Object... linkData) {
            return null;
        }

        @Override
        public Operation linkFrom(RuntimeAsset asset, Object... linkData) {
            return null;
        }
    }

    @Override
    public Operation op(Object... target) {
        OperationImpl ret = new OperationImpl();
        if (target != null && target.length > 0) {

        }
        return ret;
    }

    protected abstract long runOperation(OperationImpl operation);

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
