package org.integratedmodelling.engine.client.distribution;

import org.integratedmodelling.klab.api.engine.Distribution;
import org.integratedmodelling.klab.api.engine.Product;
import org.integratedmodelling.klab.api.scope.Scope;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class DistributionImpl implements Distribution {

    List<Product> products = new ArrayList<>();

    @Override
    public void synchronize(Scope scope) {
        // TODO
    }

    @Override
    public Collection<Product> getProducts() {
        return products;
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
}
