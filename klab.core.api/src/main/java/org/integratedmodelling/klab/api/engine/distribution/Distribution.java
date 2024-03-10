package org.integratedmodelling.klab.api.engine.distribution;

import org.integratedmodelling.klab.api.scope.Scope;

import java.util.Collection;

public interface Distribution {

    /**
     * Synchronize with the remote peer, if there is one. In order to know if there is any synchronization to
     * be done, scan the products and compare versions. Synchronization will use the scope using send() to
     * monitor all events implied in the operations and handle interruptions. The scope may also determine
     * which products can be accessed and how.
     *
     * TODO we should add optional parameters for sync of the latest version only, or whichever release is
     *  defined in the product as the currently chosen one.
     *
     * @param scope
     */
    void synchronize(Scope scope);

    /**
     * A distribution is a list of products. Each may be individually versioned.
     *
     * @return
     */
    Collection<Product> getProducts();

    /**
     * Find the product we have available of the passed type, if any.
     *
     * @param productType
     * @return a product or null.
     */
    Product findProduct(Product.ProductType productType);
}
