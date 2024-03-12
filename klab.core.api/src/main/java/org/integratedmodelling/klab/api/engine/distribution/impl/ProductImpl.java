package org.integratedmodelling.klab.api.engine.distribution.impl;

import org.integratedmodelling.klab.api.data.Version;
import org.integratedmodelling.klab.api.engine.distribution.Distribution;
import org.integratedmodelling.klab.api.engine.distribution.Product;
import org.integratedmodelling.klab.api.engine.distribution.Build;
import org.integratedmodelling.klab.api.engine.distribution.Release;
import org.integratedmodelling.klab.api.utils.PropertyBean;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * {@link Product} bean which implements all the properties and can be initialized from a
 * {@link java.util.Properties} object. Subclasses will need to define any further properties.
 */
public abstract class ProductImpl extends PropertyBean implements Product {
    private String id;
    private ProductType productType;
    private Type type;
    private String name;
    private String description;
    private Version version;
    private List<Release> releases = new ArrayList<>();

    public ProductImpl() {
        super(null);
    }

    public ProductImpl(File propertiesFile, DistributionImpl distribution){
        super(propertiesFile);
        this.setName(getProperty(Product.PRODUCT_NAME_PROPERTY));
        this.setProductType(ProductType.valueOf(getProperty(PRODUCT_CLASS_PROPERTY)));
        this.setType(Type.forOption(getProperty(PRODUCT_TYPE_PROPERTY)));
        this.setDescription(getProperty(PRODUCT_DESCRIPTION_PROPERTY));

        for (String releaseName : getProperty(RELEASE_NAMES_PROPERTY, "").split(",")) {
            File releasePropertyFile = new File(propertiesFile.getParent() + File.separator + releaseName + File.separator + ReleaseImpl.RELEASE_PROPERTIES_FILE);
            if (releasePropertyFile.isFile()) {
                getReleases().add(new LocalReleaseImpl(releasePropertyFile,  distribution, this));
            }
        }
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public ProductType getProductType() {
        return productType;
    }

    @Override
    public Type getType() {
        return this.type;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getDescription() {
        return description;
    }

    @Override
    public Version getVersion() {
        return version;
    }

    @Override
    public List<Release> getReleases() {
        return releases;
    }

    public void setId(String id) {
        this.id = id;
    }

    public void setProductType(ProductType productType) {
        this.productType = productType;
    }

    public void setType(Type type) {
        this.type = type;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public void setVersion(Version version) {
        this.version = version;
    }

    public void setReleases(List<Release> releases) {
        this.releases = releases;
    }
}
