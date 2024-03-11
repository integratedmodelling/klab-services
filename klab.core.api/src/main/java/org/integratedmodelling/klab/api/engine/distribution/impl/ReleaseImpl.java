package org.integratedmodelling.klab.api.engine.distribution.impl;

import org.integratedmodelling.klab.api.data.Version;
import org.integratedmodelling.klab.api.engine.distribution.Product;
import org.integratedmodelling.klab.api.engine.distribution.Release;
import org.integratedmodelling.klab.api.engine.distribution.RunningInstance;
import org.integratedmodelling.klab.api.scope.Scope;

import java.io.File;
import java.time.Instant;

/**
 * {@link Release} bean which implements all the properties and can be initialized from a
 * {@link java.util.Properties} object. Subclasses will need to define any further properties.
 */
public abstract class ReleaseImpl implements Release {

    private Product product;
    private File localWorkspace;
    private Instant buildDate;
    private Version version;
    private boolean osSpecific;

    public ReleaseImpl() {}

    public ReleaseImpl(File propertiesFile) {

    }

    @Override
    public Product getProduct() {
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

    public void setProduct(Product product) {
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
}
