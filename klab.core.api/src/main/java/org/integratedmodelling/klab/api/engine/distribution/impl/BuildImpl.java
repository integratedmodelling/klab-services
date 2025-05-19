package org.integratedmodelling.klab.api.engine.distribution.impl;

import org.integratedmodelling.klab.api.data.Version;
import org.integratedmodelling.klab.api.engine.distribution.Product;
import org.integratedmodelling.klab.api.engine.distribution.Build;
import org.integratedmodelling.klab.api.engine.distribution.RunningInstance;
import org.integratedmodelling.klab.api.scope.Scope;
import org.integratedmodelling.klab.api.utils.PropertyBean;

import java.io.File;
import java.time.Instant;

/**
 * {@link Build} bean which implements all the properties and can be initialized from a
 * {@link java.util.Properties} object. Subclasses will need to define any further properties.
 */
public abstract class BuildImpl extends PropertyBean implements Build {

    private ProductImpl product;

    private ReleaseImpl release;
    private File localWorkspace;
    private Instant buildDate;
    private Version version;
    private boolean osSpecific;
    private String executable;

    public BuildImpl() {
        super(null);
    }

    public BuildImpl(File propertiesFile) {
        super(propertiesFile);
        // TODO read the properties. This creates a stub so we create the product as well.
        readProperties();
        this.product = new ProductImpl() {
            @Override
            public Status getStatus() {
                return null;
            }

            @Override
            public RunningInstance getInstance(Scope scope) {
                return null;
            }

            @Override
            public RunningInstance launch(Scope scope) {
                return null;
            }
        };
        this.product.setName(getProperty(Product.PRODUCT_NAME_PROPERTY));
    }

    private void readProperties() {
        var localWs = getProperty(BUILD_WORKSPACE_PROPERTY);
        if (localWs != null) {
            setLocalWorkspace(new File(localWs));
        }
        setVersion(Version.create(getProperty(BUILD_VERSION_PROPERTY)));
        setOsSpecific(Boolean.valueOf(getProperty(PRODUCT_OSSPECIFIC_PROPERTY)));
        setExecutable(getProperty(BUILD_MAINCLASS_PROPERTY));
        setBuildDate(Instant.ofEpochMilli(Long.valueOf(getProperty(BUILD_TIME_PROPERTY,
                "" + System.currentTimeMillis()))));
    }

    public BuildImpl(File propertiesFile, ProductImpl product, ReleaseImpl release) {
        super(propertiesFile);
        // TODO read the properties. This creates a stub so we create the product as well.
        readProperties();
        this.product = product;
        this.release = release;
    }

    @Override
    public ProductImpl getProduct() {
        return product;
    }

    @Override
    public File getLocalWorkspace() {
        return localWorkspace;
    }

    @Override
    public Instant getBuildDate() {
        return buildDate;
    }

    @Override
    public Version getVersion() {
        return version;
    }

    @Override
    public boolean isOsSpecific() {
        return osSpecific;
    }

    public void setProduct(ProductImpl product) {
        this.product = product;
    }

    public void setLocalWorkspace(File localWorkspace) {
        this.localWorkspace = localWorkspace;
    }

    public void setBuildDate(Instant buildDate) {
        this.buildDate = buildDate;
    }

    public void setVersion(Version version) {
        this.version = version;
    }

    public void setOsSpecific(boolean osSpecific) {
        this.osSpecific = osSpecific;
    }

    @Override
    public ReleaseImpl getRelease() {
        return release;
    }

    public void setRelease(ReleaseImpl release) {
        this.release = release;
    }

    @Override
    public String getExecutable() {
        return executable;
    }

    public void setExecutable(String executable) {
        this.executable = executable;
    }
}
