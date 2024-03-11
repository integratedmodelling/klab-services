package org.integratedmodelling.klab.api.engine.distribution.impl;

import org.integratedmodelling.klab.api.data.Version;
import org.integratedmodelling.klab.api.engine.distribution.Product;
import org.integratedmodelling.klab.api.engine.distribution.Build;
import org.integratedmodelling.klab.api.utils.PropertiesBasedObject;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * {@link Product} bean which implements all the properties and can be initialized from a
 * {@link java.util.Properties} object. Subclasses will need to define any further properties.
 */
public abstract class ProductImpl extends PropertiesBasedObject implements Product {
    private String id;
    private ProductType productType;
    private Type type;
    private String name;
    private String description;
    private String currentReleaseId;
    private Version version;
    private List<Build> releases = new ArrayList<>();

    public ProductImpl() {
        super(null);
    }

    public ProductImpl(File propertiesFile){
        super(propertiesFile);
        // TODO read the properties
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
    public String getCurrentReleaseId() {
        return currentReleaseId;
    }

    @Override
    public Version getVersion() {
        return version;
    }

    @Override
    public List<Build> getReleases() {
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

    public void setCurrentReleaseId(String currentReleaseId) {
        this.currentReleaseId = currentReleaseId;
    }

    public void setVersion(Version version) {
        this.version = version;
    }

    public void setReleases(List<Build> releases) {
        this.releases = releases;
    }
}
