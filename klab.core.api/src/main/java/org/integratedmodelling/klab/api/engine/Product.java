package org.integratedmodelling.klab.api.engine;

import org.integratedmodelling.klab.api.utils.Utils;

import java.io.File;
import java.util.Properties;

public interface Product {

    public enum Status {

        /**
         * Status before status is assessed and when offline but with available
         * downloaded distributions.
         */
        UNKNOWN,

        /**
         * Status when offline or other error has occurred
         */
        UNAVAILABLE,

        /**
         * When the latest available build is available locally.
         */
        UP_TO_DATE,

        /**
         * When builds are available but there are unsynchronized more recent builds.
         */
        OBSOLETE
    }

    enum Type {

        UNKNOWN,

        /**
         * Jar packaging with bin/, lib/ and a main jar file with a main class in
         * properties, OS independent distribution with potential OS-specific
         * subcomponents to merge in from subdirs.
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
                return  baseUrl + "/" + getId();
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
        KMODELER {
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
        /**
         * The id used in paths
         * @return the id
         */
        public abstract String getId();
        /**
         * The name used in product.properties
         * @return the name used in product.properties
         */
        public abstract String getName();
        public abstract String getRemoteUrl(String baseUrl);
        public String getRemotePropertiesFileUrl(String baseUrl) {
            return  baseUrl + "/" + getId() + "/" + Release.PRODUCT_PROPERTIES_FILE;
        }
        public File getLocalPath(String basePath) {
            return new File(basePath + File.separator + getId());
        }
    }

    public final static String PRODUCT_NAME_PROPERTY = "klab.product.name";
    public final static String PRODUCT_DESCRIPTION_PROPERTY = "klab.product.description";
    //public final static String PRODUCT_AVAILABLE_BUILDS_PROPERTY = "klab.product.builds";
    public final static String PRODUCT_TYPE_PROPERTY = "klab.product.type";
    public final static String PRODUCT_OSSPECIFIC_PROPERTY = "klab.product.osspecific";

    public final static String BUILD_VERSION_PROPERTY = "klab.product.build.version";
    public final static String BUILD_MAINCLASS_PROPERTY = "klab.product.build.main";
    public final static String BUILD_TIME_PROPERTY = "klab.product.build.time";

    /**
     * Name of product is a lowercase string, short and without spaces,
     * corresponding to the directory where the product is hosted.
     *
     * @return product ID
     */
    String getId();

    /**
     * True if a different distribution is needed per supported operating system.
     *
     * @return
     */
    boolean isOsSpecific();

    /**
     * Get the type of product. The type enum is of course limited to the current
     * usage and should be expanded as needed.
     *
     * @return
     */
    Type getType();

    /**
     * Name of product is the user-readable name, potentially with more words but
     * short and suitable for buttons or choice boxes.
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
     * The contents of the product.properties file in the product directory.
     * Non-null values for all the static property names starting with PRODUCT_ in
     * this interface are mandatory.
     *
     * @return
     */
    Properties getProperties();

    /**
     * Status of the product. If the product is UNAVAILABLE or UNKNOWN, properties,
     * builds and the like may not be accessible.
     *
     * @return
     */
    Status getStatus();

    /**
     * Indicate that we have a product locally but the remote update site is not
     * available, so we don't know the update situation.
     * Used to avoid breaking the control center if we have some issues remotely.
     * @return
     */
    boolean onlyLocal();

    File getLocalWorkspace();

}
