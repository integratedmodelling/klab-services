package org.integratedmodelling.klab.api.engine.distribution;

import org.integratedmodelling.klab.api.data.Version;
import org.integratedmodelling.klab.api.exceptions.KlabIllegalArgumentException;
import org.integratedmodelling.klab.api.scope.Scope;
import org.integratedmodelling.klab.api.services.KlabService;
import org.integratedmodelling.klab.api.utils.Utils;

import java.io.File;
import java.util.List;

/**
 * A {@link Product} contains zero or more named {@link Release}s, each containing one or more versioned
 * {@link Build}s, one of which, that, once populated with artifacts and synchronized with a possibly online
 * distribution, can be launched to run the Product.
 */
public interface Product {

    public static final String PRODUCT_PROPERTIES_FILE = "product.properties";

    public final static String PRODUCT_NAME_PROPERTY = "klab.product.name";
    public final static String PRODUCT_DESCRIPTION_PROPERTY = "klab.product.description";
    public final static String PRODUCT_TYPE_PROPERTY = "klab.product.type";
    public final static String PRODUCT_CLASS_PROPERTY = "klab.product.class";


    public enum Status {

        /**
         * Status when offline or other error has occurred and there is no usable local distribution.
         */
        UNAVAILABLE(false),

        /**
         * Status when the product is available locally with at least one internally consistent {@link Build}
         * but we cannot access the online repository so we don't know if we're up to date or not.
         */
        LOCAL_ONLY(true),

        /**
         * When the latest available build is available locally.
         */
        UP_TO_DATE(true),

        /**
         * When builds are available but there are unsynchronized more recent builds.
         */
        OBSOLETE(true);

        private boolean usable;

        public boolean isUsable() {
            return usable;
        }

        private Status(boolean usable) {
            this.usable = usable;
        }

    }

    enum Type {

        UNKNOWN,

        /**
         * Jar packaging with bin/, lib/ and a main jar file with a main class in properties, OS independent
         * distribution with potential OS-specific subcomponents to merge in from subdirs.
         */
        JAR,

        /**
         * Installer executable packaging.
         */
        INSTALLER_EXECUTABLE,

        /**
         * Direct executable packaging.
         */
        DIRECT_EXE,

        /**
         * Eclipse packaging with a zipped or unzipped distribution per supported OS.
         */
        ECLIPSE
    }

    public enum ProductType {
        CLI {
            @Override
            public String getRemoteUrl(String baseUrl) {
                return baseUrl + "/" + getId();
            }

            @Override
            public String getId() {
                return "cli";
            }

            @Override
            public String getName() {
                return "k.LAB Engine";
            }
        },
        RESOURCES_SERVICE {
            @Override
            public String getRemoteUrl(String baseUrl) {
                return baseUrl + "/" + getId();
            }

            @Override
            public String getId() {
                return "resources";
            }

            @Override
            public String getName() {
                return "k.LAB Resources service";
            }
        },
        REASONER_SERVICE {
            @Override
            public String getRemoteUrl(String baseUrl) {
                return baseUrl + "/" + getId();
            }

            @Override
            public String getId() {
                return "reasoner";
            }

            @Override
            public String getName() {
                return "k.LAB Reasoner service";
            }
        },
        RESOLVER_SERVICE {
            @Override
            public String getRemoteUrl(String baseUrl) {
                return baseUrl + "/" + getId();
            }

            @Override
            public String getId() {
                return "resolver";
            }

            @Override
            public String getName() {
                return "k.LAB Resolver service";
            }
        },
        RUNTIME_SERVICE {
            @Override
            public String getRemoteUrl(String baseUrl) {
                return baseUrl + "/" + getId();
            }

            @Override
            public String getId() {
                return "runtime";
            }

            @Override
            public String getName() {
                return "k.LAB Runtime service";
            }
        },
        MODELER {
            @Override
            public String getRemoteUrl(String baseUrl) {
                return baseUrl + "/" + getId() + "/" + Utils.OS.get().toString().toLowerCase();
            }

            @Override
            public String getId() {
                return "kmodeler";
            }

            @Override
            public String getName() {
                return "k.LAB Modeler";
            }
        };

        public static ProductType forService(KlabService.Type serviceType) {
            return switch (serviceType) {
                case REASONER -> REASONER_SERVICE;
                case RESOURCES -> RESOURCES_SERVICE;
                case RESOLVER -> RESOLVER_SERVICE;
                case RUNTIME -> RUNTIME_SERVICE;
                default -> throw new KlabIllegalArgumentException("wrong service type for product");
            };
        }

        /**
         * The id used in paths
         *
         * @return the id
         */
        public abstract String getId();

        /**
         * The name used in product.properties
         *
         * @return the name used in product.properties
         */
        public abstract String getName();

        public abstract String getRemoteUrl(String baseUrl);

        public File getLocalPath(String basePath) {
            return new File(basePath + File.separator + getId());
        }
    }

    /**
     * Name of product is a lowercase string, short and without spaces, corresponding to the directory where
     * the product is hosted.
     *
     * @return product ID
     */
    String getId();

    ProductType getProductType();

    /**
     * Get the type of product. The type enum is of course limited to the current usage and should be expanded
     * as needed.
     *
     * @return
     */
    Type getType();

    /**
     * Name of product is the user-readable name, potentially with more words but short and suitable for
     * buttons or choice boxes.
     *
     * @return product name
     */
    String getName();

    /**
     * Longer description of the product.
     *
     * @return
     */
    String getDescription();

    /**
     * Only relevant in local distributions.
     * <p>
     * Status of the product. Corresponds to the status of the worse-off {@link Build} that is currently
     * selected or needed in order to run. If the product is UNAVAILABLE there's nothing we can do. LOCAL_ONLY
     * means we're usable with at least one release but without knowing anything about updates and the like.
     * Otherwise, we can tell if we're up-to-date or not. {@link Build} also implements {@link #getStatus()}
     * for release-specific information.
     *
     * @return
     */
    Status getStatus();

    /**
     * If the product properties define a "currently chosen" release, we should return its ID here. Otherwise
     * we should default to the ID of the most current release among those we have synchronized, or return
     * null if unavailable (we should always call {@link #getStatus()} before using, so we shouldn't use the
     * null result).
     *
     * @return
     */
    String getCurrentReleaseId();

    /**
     * Version of the most current release in this product
     *
     * @return
     */
    Version getVersion();

    /**
     * Releases of product available in remote distribution (and possibly locally), most recent first.
     *
     * @return
     */
    List<Build> getReleases();

    /**
     * Shorthand for "retrieve the currently configured or most recent {@link Release} and {@link Build}, then
     * launch that in this {@link Scope}. The scope will be notified of any events related to the launch.
     * <p>
     * TODO add parameters for automatic synchronization,
     *  {@link org.integratedmodelling.klab.api.engine.StartupOptions} and anything else we may need here.
     *
     * @param scope
     * @return
     */
    RunningInstance launch(Scope scope);

}
