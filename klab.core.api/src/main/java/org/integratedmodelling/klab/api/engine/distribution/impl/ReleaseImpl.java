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
    @Override
    public Product getProduct() {
        return null;
    }

    @Override
    public File getLocalWorkspace() {
        return null;
    }

    @Override
    public Instant getBuildDate() {
        return null;
    }

    @Override
    public Version getVersion() {
        return null;
    }

    @Override
    public boolean isOsSpecific() {
        return false;
    }
}
