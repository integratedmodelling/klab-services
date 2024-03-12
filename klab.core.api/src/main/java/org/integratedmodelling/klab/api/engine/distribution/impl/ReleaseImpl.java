package org.integratedmodelling.klab.api.engine.distribution.impl;

import org.integratedmodelling.klab.api.data.Version;
import org.integratedmodelling.klab.api.engine.distribution.Build;
import org.integratedmodelling.klab.api.engine.distribution.Release;
import org.integratedmodelling.klab.api.utils.PropertyBean;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public class ReleaseImpl extends PropertyBean implements Release {

    private String name;
    private Version version;
    private List<Build> builds = new ArrayList<>();

    public ReleaseImpl() {
        super(null);
    }

    public ReleaseImpl(File file) {
        super(file);
        // TODO read properties
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public Version getVersion() {
        return version;
    }

    @Override
    public List<Build> getBuilds() {
        return builds;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setVersion(Version version) {
        this.version = version;
    }

    public void setBuilds(List<Build> builds) {
        this.builds = builds;
    }
}
