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
    public final static String RELEASE_NAMES_PROPERTY = "klab.product.releases";

    /**
     * Used only in local products to remember the release and build we used last time when launch() is
     * called. If not present, the most recent build in the first release found is used. Must be in
     * release/build format.
     */
    public final static String LAST_BUILD_LAUNCHED = "klab.product.lastbuildlaunched";

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

        UNKNOWN("unknown"),

        /**
         * Jar packaging with bin/, lib/ and a main jar file with a main class in properties, OS independent
         * distribution with potential OS-specific subcomponents to merge in from subdirs.
         */
        JAR("jar"),

        /**
         * Installer executable packaging.
         */
        INSTALLER_EXECUTABLE("installer"),

        /**
         * Direct executable packaging.
         */
        DIRECT_EXE("exe"),

        /**
         * Eclipse packaging with a zipped or unzipped distribution per supported OS.
         */
        ECLIPSE("eclipse");

        // user-defined name of the product in build.properties options, set in Maven
        // configuration of klab.product plugin
        public String userOption;

        public static Type forOption(String option) {
            return switch(option) {
                case "jar" -> JAR;
                case "installer" -> INSTALLER_EXECUTABLE;
                case "exe" -> DIRECT_EXE;
                case "eclipse" -> ECLIPSE;
                default -> UNKNOWN;
            };
        }

        Type(String userOption) {
            this.userOption = userOption;
        }
    }

    enum ProductType {
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

            @Override
            public int getDebugPort() {return 5005; }

            @Override
            public int defaultMaxMemoryLimitMB() {
                return 1024;
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

            @Override
            public int getDebugPort() {return 5006; }

            @Override
            public int defaultMaxMemoryLimitMB() {
                return 4096;
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

            @Override
            public int getDebugPort() {return 5007; }

            @Override
            public int defaultMaxMemoryLimitMB() {
                return 2048;
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

            @Override
            public int getDebugPort() {return 5008; }

            @Override
            public int defaultMaxMemoryLimitMB() {
                return 2048;
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

            @Override
            public int getDebugPort() {return 5009; }

            @Override
            public int defaultMaxMemoryLimitMB() {
                return 4096;
            }

        },
        COMMUNITY_SERVICE {
            @Override
            public String getRemoteUrl(String baseUrl) {
                return baseUrl + "/" + getId();
            }

            @Override
            public String getId() {
                return "community";
            }

            @Override
            public String getName() {
                return "k.LAB Community service";
            }

            @Override
            public int getDebugPort() {return 5010; }

            @Override
            public int defaultMaxMemoryLimitMB() {
                return 2048;
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

            @Override
            public int getDebugPort() {return 5011; }

            @Override
            public int defaultMaxMemoryLimitMB() {
                return 2048;
            }
        };

        public static ProductType forService(KlabService.Type serviceType) {
            return switch (serviceType) {
                case REASONER -> REASONER_SERVICE;
                case RESOURCES -> RESOURCES_SERVICE;
                case RESOLVER -> RESOLVER_SERVICE;
                case RUNTIME -> RUNTIME_SERVICE;
                case COMMUNITY -> COMMUNITY_SERVICE;
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

        public abstract int getDebugPort();

        public abstract int defaultMaxMemoryLimitMB();
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
    List<Release> getReleases();

    /**
     * Create a new instance that will have to be started manually.
     * @param scope
     * @return
     */
    RunningInstance getInstance(Scope scope);

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
