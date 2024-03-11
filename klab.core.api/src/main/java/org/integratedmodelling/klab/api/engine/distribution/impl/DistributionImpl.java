package org.integratedmodelling.klab.api.engine.distribution.impl;

import org.integratedmodelling.klab.api.engine.distribution.Distribution;
import org.integratedmodelling.klab.api.engine.distribution.Product;
import org.integratedmodelling.klab.api.scope.Scope;
import org.integratedmodelling.klab.api.utils.PropertiesBasedObject;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;

/**
 * {@link Distribution} bean which implements all the properties and can be initialized from and saved to a
 * {@link java.util.Properties} object. Subclasses will need to define any further properties.
 */
public abstract class DistributionImpl extends PropertiesBasedObject implements Distribution {

    private Collection<Product> products = new ArrayList<>();

    @Override
    public Collection<Product> getProducts() {
        return products;
    }


    public DistributionImpl() {
        super(null);
    }

    public DistributionImpl(File file) {
        super(file);
        // TODO read the properties
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
     * Create all needed property files for the passed classes, creating subdirectories as specified by the
     * relative paths in the linked objects.
     *
     * @param outputDirectory the place to put the main distribution.properties file
     */
    public void createPropertyFiles(File outputDirectory, boolean addLocalPaths) {
        
    }
}
