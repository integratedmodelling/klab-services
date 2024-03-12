package org.integratedmodelling.engine.client.distribution;

import org.integratedmodelling.klab.api.configuration.Configuration;
import org.integratedmodelling.klab.api.engine.distribution.Distribution;
import org.integratedmodelling.klab.api.engine.distribution.impl.DistributionImpl;
import org.integratedmodelling.klab.api.engine.distribution.impl.LocalProductImpl;
import org.integratedmodelling.klab.api.engine.distribution.impl.ProductImpl;
import org.integratedmodelling.klab.api.scope.Scope;

import java.io.File;

/**
 * Finds or reads a git repository with Maven artifacts and builds a distribution out of all the products
 * found in target. Used for testing when the code artifacts are there.
 */
public class DevelopmentDistributionImpl extends DistributionImpl {

    public DevelopmentDistributionImpl() {
        File distributionDirectory =
                new File(Configuration.INSTANCE.getProperty(Configuration.KLAB_DEVELOPMENT_SOURCE_REPOSITORY, System.getProperty("user.home") + File.separator + "git" + File.separator + "klab" + "-services"));
        if (distributionDirectory.isDirectory()) {
            File distributionProperties = new File(distributionDirectory + File.separator + "klab" +
                    ".distribution" + File.separator + "target" + File.separator + "distribution" + File.separator + Distribution.DISTRIBUTION_PROPERTIES_FILE);
            if (distributionProperties.isFile()) {
                initialize(distributionProperties);
            }
        }
    }

    @Override
    protected void initialize(File propertiesFile) {
        super.initialize(propertiesFile);
        File distributionPath = propertiesFile.getParentFile();
        for (String productName : getProperty(DISTRIBUTION_PRODUCTS_PROPERTY, "").split(",")) {
            this.getProducts().add(new LocalProductImpl(new File(distributionPath + File.separator + productName + File.separator + ProductImpl.PRODUCT_PROPERTIES_FILE), this));
        }
    }

    public static void main(String args[]) {
        var distribution = new DevelopmentDistributionImpl();
        distribution.synchronize(null);
    }

    @Override
    public void synchronize(Scope scope) {
        // do nothing
    }

    public boolean isAvailable() {
        return getProducts().size() > 0;
    }
}
