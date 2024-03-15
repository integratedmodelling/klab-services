package org.integratedmodelling.klab.api.engine.distribution.impl;

import org.integratedmodelling.klab.api.engine.distribution.Build;
import org.integratedmodelling.klab.api.engine.distribution.Distribution;
import org.integratedmodelling.klab.api.engine.distribution.Product;
import org.integratedmodelling.klab.api.engine.distribution.RunningInstance;
import org.integratedmodelling.klab.api.scope.Scope;
import org.integratedmodelling.klab.api.utils.PropertyBean;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;

/**
 * {@link Distribution} bean which implements all the properties and can be initialized from and saved to a
 * {@link java.util.Properties} object. Subclasses will need to define any further properties.
 */
public abstract class AbstractDistributionImpl extends PropertyBean implements Distribution {

    private Collection<Product> products = new ArrayList<>();

    @Override
    public Collection<Product> getProducts() {
        return products;
    }


    public AbstractDistributionImpl() {
        super(null);
    }

    public AbstractDistributionImpl(File file) {
        super(file);
    }

    @Override
    public Product findProduct(Product.ProductType productType) {
        for (var product : products) {
            if (product.getProductType() == productType) {
                return product;
            }
        }
        return null;
    }

    /**
     * This is used by {@link LocalBuildImpl} so that we can run the build in more capable implementations by
     * just overriding this.
     *
     * @param build
     * @param scope
     * @return
     */
    public RunningInstance runBuild(Build build, Scope scope) {
        return null;
    }

    public RunningInstance getInstance(Build build, Scope scope) {
        return null;
    }

}
