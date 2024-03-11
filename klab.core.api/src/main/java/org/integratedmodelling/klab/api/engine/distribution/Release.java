package org.integratedmodelling.klab.api.engine.distribution;

import org.integratedmodelling.klab.api.data.Version;

import java.util.List;

/**
 * A Release is simply a named and versioned container of {@link Build} objects, corresponding to
 * different builds of the same product.
 */
public interface Release {

    public static final String RELEASE_PROPERTIES_FILE = "release.properties";
    public static final String BUILD_VERSIONS_PROPERTY = "klab.build.versions";
    public static final String BUILD_NAME_PROPERTY = "klab.build.versions";
    public static final String BUILD_VERSION_PROPERTY = "klab.build.versions";

    String getName();

    Version getVersion();

    List<Build> getBuilds();

}
