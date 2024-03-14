package org.integratedmodelling.klab.api.engine.distribution.impl;

import org.integratedmodelling.klab.api.engine.distribution.Build;
import org.integratedmodelling.klab.api.engine.distribution.RunningInstance;
import org.integratedmodelling.klab.api.scope.Scope;

import java.io.File;

public class LocalProductImpl extends ProductImpl {

    public LocalProductImpl(File file, AbstractDistributionImpl distribution) {
        super(file, distribution);
    }

    @Override
    public Status getStatus() {
        return null;
    }

    @Override
    public RunningInstance launch(Scope scope) {

        var lastLaunched = getProperty(LAST_BUILD_LAUNCHED);
        var build = locateBuild(lastLaunched);
        if (build != null) {
            return build.launch(scope);
        }
        return null;
    }

    private Build locateBuild(String lastLaunched) {
        if (lastLaunched != null) {
            String[] launched = lastLaunched.split("\\/");
            var release = getReleases().stream().filter(r -> r.getName().equals(launched[0])).findAny();
            if (release.isPresent()) {
                var ret =
                        release.get().getBuilds().stream().filter(b -> b.getVersion().getBuild() == Long.valueOf(launched[1])).findAny();
                if (ret.isPresent()) {
                    return ret.get();
                }
            }
        }
        if (!getReleases().isEmpty()) {
            var release = getReleases().get(0);
            if (!release.getBuilds().isEmpty()) {
                return release.getBuilds().get(0);
            }
        }
        return null;
    }
}
