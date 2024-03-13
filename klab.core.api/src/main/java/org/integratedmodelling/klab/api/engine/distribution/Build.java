package org.integratedmodelling.klab.api.engine.distribution;

import org.integratedmodelling.klab.api.data.Version;
import org.integratedmodelling.klab.api.engine.distribution.impl.BuildImpl;
import org.integratedmodelling.klab.api.exceptions.KlabIllegalStateException;
import org.integratedmodelling.klab.api.scope.Scope;

import java.io.File;
import java.time.Instant;

/**
 * A specific build of a {@link Product}. This is the object we use to run a product, which contains zero or
 * more releases, each containing one or more builds. The version of a build must be the same as that of the
 * containing release, plus a build number that must be sortable as a string so that the builds can be
 * presented in reverse chronological order.
 * <p>
 * In a published distribution (such as those made by the <code>klab.product</code> Maven plugin) the build
 * contains a build.properties file which points to the subdirectories containing each version, which in
 * turns contains a release.properties file with the rest of the information. The release name is usually a
 * branch name and has no version. Yet, to keep the hierarchy simpler we build a Release object per each
 * version and add them directly to the product in the API.
 */
public interface Build {

    public static final String DEVELOP_RELEASE = "develop";
    public static final String LATEST_RELEASE = "latest";
    public static final String RELEASE = "release";
    public static final String DEFAULT_RELEASE_URL = "https://products.integratedmodelling.org/klab/";
    public static final String BUILD_PROPERTIES_FILE = "build.properties";
    public static final String BUILD_DIGEST_FILE = "filelist.txt";
    public final static String PRODUCT_OSSPECIFIC_PROPERTY = "klab.build.osspecific";
    public final static String BUILD_VERSION_PROPERTY = "klab.build.version";
    public final static String BUILD_MAINCLASS_PROPERTY = "klab.build.main";
    public final static String BUILD_TIME_PROPERTY = "klab.build.time";
    public final static String BUILD_WORKSPACE_PROPERTY = "klab.build.workspace";
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

    public static Build create(File releasePropertiesFile) {
        return new BuildImpl(releasePropertiesFile) {
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

    Release getRelease();

    /**
     * The executable path in the build. Either a Java class with the main() method or the name of the
     * executable file.
     *
     * @return
     */
    String getExecutable();
}
