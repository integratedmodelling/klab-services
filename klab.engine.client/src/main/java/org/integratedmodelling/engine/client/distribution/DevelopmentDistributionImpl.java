package org.integratedmodelling.engine.client.distribution;

import org.integratedmodelling.klab.api.configuration.Configuration;
import org.integratedmodelling.klab.api.engine.distribution.Distribution;
import org.integratedmodelling.klab.api.engine.distribution.Product;
import org.integratedmodelling.klab.api.engine.distribution.Release;
import org.integratedmodelling.klab.api.engine.distribution.impl.DistributionImpl;
import org.integratedmodelling.klab.api.scope.Scope;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitOption;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

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
