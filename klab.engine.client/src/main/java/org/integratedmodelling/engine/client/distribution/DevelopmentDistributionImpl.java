package org.integratedmodelling.engine.client.distribution;

import org.integratedmodelling.klab.api.engine.distribution.impl.DistributionImpl;
import org.integratedmodelling.klab.api.scope.Scope;

/**
 * Finds or reads a git repository with Maven artifacts and builds a distribution out of all the products
 * found in target. Used for testing when the code artifacts are there.
 */
public class DevelopmentDistributionImpl extends DistributionImpl {

    public static void main(String args[]) {
        var distribution = new DevelopmentDistributionImpl();
        distribution.synchronize(null);
    }

    @Override
    public void synchronize(Scope scope) {
        // do nothing
    }
}
