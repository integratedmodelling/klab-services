//package org.integratedmodelling.engine.client.distribution;
//
//
//import org.integratedmodelling.klab.api.data.Version;
//import org.integratedmodelling.klab.api.engine.distribution.Product;
//import org.integratedmodelling.klab.api.engine.distribution.Release;
//
//import java.io.File;
//import java.io.FileInputStream;
//import java.io.InputStream;
//import java.net.URL;
//import java.util.ArrayList;
//import java.util.List;
//import java.util.Properties;
//
//public class ProductImpl extends ProductI {
//
//    private Status status;
//    private Properties properties = new Properties();
//    private String productId;
//    private String name;
//    private String description;
//    private Type type;
//    private File localWorkspace;
//    private Version version;
//    private List<Release> releases = new ArrayList<>();
//    private ProductType productType;
//
//    public ProductImpl(String baseUrl, String productId, String ws) {
//
//        // FIXME move most of this under Release - we should separate product properties from release
//        //  properties. Not all products have properties.
//        this.productId = productId;
//        this.localWorkspace = new File(ws + File.separator + productId);
//        this.localWorkspace.mkdirs();
//
//        /*
//         * try to read the remote product properties
//         */
//        try (InputStream in =
//                     new URL(baseUrl + productId + "/" + Release.PRODUCT_PROPERTIES_FILE).openStream()) {
//            readProperties(in);
//        } catch (Exception e) {
//            this.status = Status.UNAVAILABLE;
//            try (InputStream in = new FileInputStream(
//                    new File(localWorkspace + File.separator + Release.PRODUCT_PROPERTIES_FILE))) {
//                readProperties(in);
//            } catch (Exception el) {
//                this.status = Status.UNAVAILABLE;
//            }
//        }
//    }
//
//    private void readProperties(InputStream in) {
//        //		properties.load(in);
//        //		this.name = properties.getProperty(PRODUCT_NAME_PROPERTY, productId);
//        //		this.description = properties.getProperty(PRODUCT_DESCRIPTION_PROPERTY, "No description
//        //		provided");
//        //		this.osSpecific = Boolean.parseBoolean(properties.getProperty(PRODUCT_OSSPECIFIC_PROPERTY,
//        //		"false"));
//        //		this.type = Type.valueOf(properties.getProperty(PRODUCT_TYPE_PROPERTY, "UNKNOWN"));
//        //		this.onlyLocal = false;
//
//    }
//
//    public ProductImpl(File localPropertiesFile) {
//    }
//
//}
