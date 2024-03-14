package org.integratedmodelling.common.distribution;

import org.integratedmodelling.common.authentication.AnonymousUser;
import org.integratedmodelling.common.authentication.scope.AbstractDelegatingScope;
import org.integratedmodelling.common.authentication.scope.ChannelImpl;
import org.integratedmodelling.klab.api.configuration.Configuration;
import org.integratedmodelling.klab.api.engine.StartupOptions;
import org.integratedmodelling.klab.api.engine.distribution.Build;
import org.integratedmodelling.klab.api.engine.distribution.Distribution;
import org.integratedmodelling.klab.api.engine.distribution.Product;
import org.integratedmodelling.klab.api.engine.distribution.RunningInstance;
import org.integratedmodelling.klab.api.engine.distribution.impl.AbstractDistributionImpl;
import org.integratedmodelling.klab.api.engine.distribution.impl.LocalProductImpl;
import org.integratedmodelling.klab.api.engine.distribution.impl.ProductImpl;
import org.integratedmodelling.klab.api.scope.Scope;
import org.integratedmodelling.klab.api.services.KlabService;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Collection;

/**
 * The main {@link Distribution} implementation looks up a synchronized, remote distribution in the k.LAB
 * configuration directory, and if not found, tries to synchronize the remote repository from any configured
 * URL or from the official public URL in the k.LAB official site.
 * <p>
 * Failing that, it will look up a git repository with Maven artifacts (configured in or using defaults) and
 * builds a distribution out of all the products found in target. This can be used for testing when the code
 * artifacts are there.
 */
public class DistributionImpl extends AbstractDistributionImpl {

    private boolean isAvailable = false;
    private boolean isRemote = false;
    private URL distributionUrl;

    /**
     * When the URL is known and the distribution may or may not have been synchronized, use this to download
     * whatever is necessary and add the URL to the distribution properties. After that, the other constructor
     * may be used.
     *
     * @param url
     * @param scope
     * @param synchronizeIfIncomplete
     * @param synchronizeAnyway
     */
    public DistributionImpl(URL url, Scope scope, boolean synchronizeIfIncomplete, boolean synchronizeAnyway) {
        // TODO launch synchronization with messages to scope
    }

    /**
     * Check if there is any trace of a remote distribution on the filesystem (which may be completely
     * unusable).
     *
     * @return
     */
    public static boolean isRemoteDistributionAvailable() {
        File distributionDirectory =
                new File(Configuration.INSTANCE.getDataPath() + File.separator + "distribution");
        if (distributionDirectory.isDirectory()) {
            File propertiesFile =
                    new File(distributionDirectory + File.separator + DISTRIBUTION_PROPERTIES_FILE);
            return propertiesFile.isFile();
        }
        return false;
    }

    public static boolean isDevelopmentDistributionAvailable() {
        File distributionDirectory =
                new File(Configuration.INSTANCE.getProperty(Configuration.KLAB_DEVELOPMENT_SOURCE_REPOSITORY, System.getProperty("user.home") + File.separator + "git" + File.separator + "klab" + "-services"));
        if (!distributionDirectory.isDirectory()) {
            File distributionProperties = new File(distributionDirectory + File.separator + "klab" +
                    ".distribution" + File.separator + "target" + File.separator + "distribution" + File.separator + Distribution.DISTRIBUTION_PROPERTIES_FILE);
            return distributionProperties.isFile();
        }
        return false;
    }

    public DistributionImpl() {
        File distributionDirectory =
                new File(Configuration.INSTANCE.getDataPath() + File.separator + "distribution");
        if (distributionDirectory.isDirectory()) {
            isRemote = true;
        }
        if (!distributionDirectory.isDirectory()) {
            distributionDirectory =
                    new File(Configuration.INSTANCE.getProperty(Configuration.KLAB_DEVELOPMENT_SOURCE_REPOSITORY, System.getProperty("user.home") + File.separator + "git" + File.separator + "klab" + "-services"));
        }
        if (!distributionDirectory.isDirectory()) {
            File distributionProperties = new File(distributionDirectory + File.separator + "klab" +
                    ".distribution" + File.separator + "target" + File.separator + "distribution" + File.separator + Distribution.DISTRIBUTION_PROPERTIES_FILE);
            if (distributionProperties.isFile()) {
                isAvailable = true;
                initialize(distributionProperties);
            }
        }
    }

    @Override
    protected void initialize(File propertiesFile) {
        super.initialize(propertiesFile);
        File distributionPath = propertiesFile.getParentFile();
        var distributionURL = getProperty(DISTRIBUTION_URL_PROPERTY);
        if (distributionURL != null) {
            try {
                this.distributionUrl = new URL(distributionURL);
            } catch (MalformedURLException e) {
                isRemote = false;
            }
        } else {
            isRemote = false;
        }
        for (String productName : getProperty(DISTRIBUTION_PRODUCTS_PROPERTY, "").split(",")) {
            this.getProducts().add(new LocalProductImpl(new File(distributionPath + File.separator + productName + File.separator + ProductImpl.PRODUCT_PROPERTIES_FILE), this));
        }
    }

    @Override
    public void synchronize(Scope scope) {
        if (isRemote && distributionUrl != null) {

        }
    }

    public boolean isAvailable() {
        return getProducts().size() > 0;
    }

    @Override
    public RunningInstance runBuild(Build build, Scope scope) {
        if (build.getLocalWorkspace() != null) {
            var ret = new RunningInstanceImpl(build, scope, makeOptions(build, scope));
            if (ret.start()) {
                return ret;
            }
        }
        return super.runBuild(build, scope);
    }

    /**
     * Startup options for the specific instance
     *
     * @param build
     * @param scope
     * @return
     */
    private StartupOptions makeOptions(Build build, Scope scope) {
        return null;
    }

    public static void main(String[] args) {

        var distribution = new DistributionImpl();
        var resources = distribution.findProduct(Product.ProductType.RESOURCES_SERVICE);
        var instance = resources.launch(new AbstractDelegatingScope(new ChannelImpl(new AnonymousUser())) {
            @Override
            public <T extends KlabService> T getService(Class<T> serviceClass) {
                return null;
            }

            @Override
            public <T extends KlabService> Collection<T> getServices(Class<T> serviceClass) {
                return null;
            }
        });
        while (instance.getStatus() != RunningInstance.Status.STOPPED) {
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                return;
            }
        }
    }
}
