package org.integratedmodelling.engine.client.distribution;


import org.integratedmodelling.klab.api.data.Version;
import org.integratedmodelling.klab.api.engine.Product;
import org.integratedmodelling.klab.api.engine.Release;
import org.integratedmodelling.klab.api.engine.RunningInstance;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.function.Consumer;

public class ProductImpl implements Product {

	private Status status = Status.UNKNOWN;
	private Properties properties = new Properties();
	private String productId;
	private String name;
	private String description;
	private Type type;
	private File localWorkspace;
	private boolean onlyLocal; // if remote site is not present but the product exists locally
	boolean osSpecific = false;
	private Version version;
	private List<Version> availableVersions = new ArrayList<>();
	private ProductType productType;

	public ProductImpl(String baseUrl, String productId, String ws) {

		this.productId = productId;
		this.localWorkspace = new File(ws + File.separator + productId);
		this.localWorkspace.mkdirs();

		/*
		 * try to read the remote product properties
		 */
		try (InputStream in = new URL(baseUrl + productId + "/" + Release.PRODUCT_PROPERTIES_FILE).openStream()) {
			properties.load(in);
			this.name = properties.getProperty(PRODUCT_NAME_PROPERTY, productId);
			this.description = properties.getProperty(PRODUCT_DESCRIPTION_PROPERTY, "No description provided");
			this.osSpecific = Boolean.parseBoolean(properties.getProperty(PRODUCT_OSSPECIFIC_PROPERTY, "false"));
			this.type = Type.valueOf(properties.getProperty(PRODUCT_TYPE_PROPERTY, "UNKNOWN"));
			this.onlyLocal = false;
		} catch (Exception e) {
			this.status = Status.UNKNOWN;
			try (InputStream input = new FileInputStream(
	                new File(localWorkspace + File.separator + Release.PRODUCT_PROPERTIES_FILE))) {
                properties.load(input);
    		    this.name = properties.getProperty(PRODUCT_NAME_PROPERTY, productId);
                this.description = properties.getProperty(PRODUCT_DESCRIPTION_PROPERTY, "No description provided");
                this.osSpecific = Boolean.parseBoolean(properties.getProperty(PRODUCT_OSSPECIFIC_PROPERTY, "false"));
                this.type = Type.valueOf(properties.getProperty(PRODUCT_TYPE_PROPERTY, "UNKNOWN"));
                this.onlyLocal = false;
                this.onlyLocal = true;
			} catch (Exception el) {
			    this.status = Status.UNAVAILABLE;
            }
		}
	}

	@Override
	public Status getStatus() {
		return this.status;
	}

	@Override
	public String getId() {
		return productId;
	}

	@Override
	public String getName() {
		return name;
	}

	@Override
	public Properties getProperties() {
		return properties;
	}

	@Override
	public String getDescription() {
		return description;
	}

	public File getLocalWorkspace() {
		return localWorkspace;
	}

	@Override
	public Version getVersion() {
		return version;
	}

	@Override
	public List<Version> getAvailableVersions() {
		return availableVersions;
	}

	@Override
	public RunningInstance launch(Consumer<RunningInstance> onSuccess, Consumer<RunningInstance> onError) {

		return null;
	}

	@Override
	public boolean isOsSpecific() {
		return osSpecific;
	}

	@Override
	public Type getType() {
		return type;
	}

	public ProductType getProductType() {
		return productType;
	}

    @Override
    public boolean onlyLocal() {
        return onlyLocal;
    }
}
