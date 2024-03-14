package org.integratedmodelling.klab.api.engine.distribution;

import org.integratedmodelling.klab.api.scope.Scope;

import java.util.Collection;

/**
 * A {@link Distribution} is the top-level object in a k.LAB software stack. It contains one or more
 * {@link Product}s. It can be built from a local or remote distribution file or URL; if remote, the
 * distribution will be able of synchronizing its contents with the network.
 */
public interface Distribution {

    public static final String DISTRIBUTION_PROPERTIES_FILE = "distribution.properties";

    public static final String DISTRIBUTION_NAME_PROPERTY = "klab.distribution.name";
    public static final String DISTRIBUTION_DATE_PROPERTY = "klab.distribution.date";
    public static final String DISTRIBUTION_PRODUCTS_PROPERTY = "klab.distribution.products";
    public static final String DISTRIBUTION_URL_PROPERTY = "klab.distribution.url";

    /**
     * Synchronize with the remote peer, if there is one. In order to know if there is any synchronization to
     * be done, scan the products and compare versions. Synchronization will use the scope using send() to
     * monitor all events implied in the operations and handle interruptions. The scope may also determine
     * which products can be accessed and how.
     * <p>
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
