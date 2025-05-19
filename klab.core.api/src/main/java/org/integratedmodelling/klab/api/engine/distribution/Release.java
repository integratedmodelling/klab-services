package org.integratedmodelling.klab.api.engine.distribution;

import org.integratedmodelling.klab.api.data.Version;
import org.integratedmodelling.klab.api.engine.distribution.impl.ProductImpl;

import java.util.List;

/**
 * A Release is simply a named and versioned container of {@link Build} objects, corresponding to
 * different builds of the same product.
 */
public interface Release {

    String RELEASE_PROPERTIES_FILE = "release.properties";
    String BUILD_VERSIONS_PROPERTY = "klab.build.versions";
    String RELEASE_NAME_PROPERTY = "klab.release.name";

    String getName();

    Version getVersion();

    List<Build> getBuilds();

    ProductImpl getProduct();
}
