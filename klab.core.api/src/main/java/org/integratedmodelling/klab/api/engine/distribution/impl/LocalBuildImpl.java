package org.integratedmodelling.klab.api.engine.distribution.impl;

import org.integratedmodelling.klab.api.engine.distribution.Product;
import org.integratedmodelling.klab.api.engine.distribution.RunningInstance;
import org.integratedmodelling.klab.api.scope.Scope;

import java.io.File;

public class LocalBuildImpl extends BuildImpl {

    AbstractDistributionImpl distribution;

    public LocalBuildImpl(File buildPropertyFile, AbstractDistributionImpl distribution, ProductImpl product, ReleaseImpl release) {
        super(buildPropertyFile, product, release);
        this.distribution = distribution;
    }

    @Override
    public Product.Status getStatus() {
        return Product.Status.UP_TO_DATE;
    }

    @Override
    public boolean synchronize(Scope scope) {
        return true;
    }

    @Override
    public RunningInstance launch(Scope scope) {
        return distribution.runBuild(this, scope);
    }

    @Override
    public String getRemotePath() {
        return null;
    }
}
