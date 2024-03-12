package org.integratedmodelling.klab.api.engine.distribution.impl;

import org.integratedmodelling.klab.api.engine.distribution.Build;
import org.integratedmodelling.klab.api.engine.distribution.Release;

import java.io.File;
import java.util.Collections;
import java.util.Comparator;

public class LocalReleaseImpl extends ReleaseImpl {
    public LocalReleaseImpl(File releasePropertyFile, DistributionImpl distribution, ProductImpl product) {
        super(releasePropertyFile);
        this.setProduct(product);
        this.setName(getProperty(RELEASE_NAME_PROPERTY));
        for (String buildName : getProperty(BUILD_VERSIONS_PROPERTY, "").split(",")) {
            File buildPropertyFile = new File(releasePropertyFile.getParent() + File.separator + buildName + Build.BUILD_PROPERTIES_FILE);
            if (buildPropertyFile.isFile()) {
                getBuilds().add(new LocalBuildImpl(buildPropertyFile, distribution, product, this));
            }
        }

        // sort last build first
        Collections.sort(this.getBuilds(), new Comparator<Build>() {
            @Override
            public int compare(Build o1, Build o2) {
                return o2.getVersion().compareTo(o1.getVersion());
            }
        });
    }
}
