package org.integratedmodelling.klab.api.engine.distribution;

import org.integratedmodelling.klab.api.data.Version;
import org.integratedmodelling.klab.api.engine.distribution.impl.ReleaseImpl;
import org.integratedmodelling.klab.api.exceptions.KlabIllegalStateException;
import org.integratedmodelling.klab.api.scope.Scope;

import java.io.File;
import java.time.Instant;

/**
 * A specific release of a {@link Product}. This is the object we use to run a product, which contains zero or
 * more releases and can produce them with {@link Product#getReleases()}, most recent first.
 */
public interface Release {

    public static final String DEVELOP_RELEASE = "develop";
    public static final String LATEST_RELEASE = "latest";
    public static final String RELEASE = "release";
    public static final String DEFAULT_RELEASE_URL = "https://products.integratedmodelling.org/klab/";
    public static final String PRODUCT_PROPERTIES_FILE = "product.properties";
    public static final String RELEASE_PROPERTIES_FILE = "release.properties";
    public static final String PRODUCT_PROPERTIES_PROP = "klab.product.name";
    public static final String RELEASE_DIGEST_FILE = "filelist.txt";

    /**
     * The product we're part of.
     *
     * @return
     */
    Product getProduct();

    /**
     * Synchronization status. Call {@link #synchronize(Scope)} to change it.
     *
     * @return
     */
    Product.Status getStatus();

    /**
     * Synchronize if necessary. Uses the listeners from the distribution.
     *
     * @param scope
     * @return
     */
    boolean synchronize(Scope scope);

    /**
     * Launch the release, returning a {@link RunningInstance} to control and monitor execution. If the
     * release is not available, an exception is thrown. Does not call {#synchronize(Scope)}. The scope is
     * notified of any events related to the instance.
     *
     * @param scope
     * @return a running instance.
     */
    RunningInstance launch(Scope scope);

    /**
     * Only relevant in locally available distributions. After successful synchronization, this must be valid
     * and point to a full distribution.
     *
     * @return
     */
    File getLocalWorkspace();

    /**
     * Date of deployment of build.
     *
     * @return
     */
    Instant getBuildDate();

    /**
     * Only relevant in remote distribution. The remote path to the directory containing the
     * release.properties and filelist.txt digest relative to the containing product.
     *
     * @return
     */
    String getRemotePath();

    /**
     * Version of specified build.
     *
     * @return
     */
    Version getVersion();

    /**
     * True if a different distribution is needed per supported operating system.
     *
     * @return
     */
    boolean isOsSpecific();

    public static Release create(File releasePropertiesFile) {
        return new ReleaseImpl(releasePropertiesFile) {
            @Override
            public Product.Status getStatus() {
                return Product.Status.UNAVAILABLE;
            }

            @Override
            public boolean synchronize(Scope scope) {
                throw new KlabIllegalStateException("This release is a stub");
            }

            @Override
            public RunningInstance launch(Scope scope) {
                throw new KlabIllegalStateException("This release is a stub");
            }

            @Override
            public String getRemotePath() {
                throw new KlabIllegalStateException("This release is a stub");
            }
        };
    }

}
