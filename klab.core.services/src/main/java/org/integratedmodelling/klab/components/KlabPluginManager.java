package org.integratedmodelling.klab.components;

import org.pf4j.DefaultPluginManager;
import org.pf4j.PluginLoader;

import java.nio.file.Path;

public class KlabPluginManager extends DefaultPluginManager {
    public KlabPluginManager(Path pluginsRoot) {
        super(pluginsRoot);
    }

    @Override
    protected PluginLoader createPluginLoader() {
        return new KlabPluginLoader(this);
    }
}
