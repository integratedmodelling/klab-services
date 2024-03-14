package org.integratedmodelling.common.distribution;

import org.integratedmodelling.klab.api.configuration.Configuration;
import org.integratedmodelling.klab.api.engine.distribution.Distribution;

import java.io.File;

/**
 * A {@link org.integratedmodelling.klab.api.engine.distribution.Distribution} that prioritizes lookup of
 * source code distributions to any synchronizable distro in ~/.klab. This one won't look for a synchronized
 * distribution in the k.LAB configuration directory.
 */
public class DevelopmentDistributionImpl extends DistributionImpl {

    public DevelopmentDistributionImpl() {
        File distributionDirectory =
                new File(Configuration.INSTANCE.getProperty(Configuration.KLAB_DEVELOPMENT_SOURCE_REPOSITORY, System.getProperty("user.home") + File.separator + "git" + File.separator + "klab" + "-services"));
        if (distributionDirectory.isDirectory()) {
            File distributionProperties = new File(distributionDirectory + File.separator + "klab" +
                    ".distribution" + File.separator + "target" + File.separator + "distribution" + File.separator + Distribution.DISTRIBUTION_PROPERTIES_FILE);
            if (distributionProperties.isFile()) {
                initialize(distributionProperties);
            }
        }
    }
}
