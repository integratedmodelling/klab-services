package org.integratedmodelling.klab.components;

import org.pf4j.DefaultPluginLoader;
import org.pf4j.PluginClassLoader;
import org.pf4j.PluginDescriptor;
import org.pf4j.PluginManager;

import java.nio.file.Path;

public class KlabPluginLoader extends DefaultPluginLoader {
    public KlabPluginLoader( PluginManager pluginManager) {
        super(pluginManager);
    }

    @Override
    protected PluginClassLoader createPluginClassLoader(
            Path pluginPath,
            PluginDescriptor pluginDescriptor
    ) {
        System.out.println("Loading plugin libraries with order: Application -> Plugin -> Dependencies");
        return new PluginClassLoader(
                pluginManager,
                pluginDescriptor,
                getClass().getClassLoader(),
                org.pf4j.ClassLoadingStrategy.APD
        );
    }

}
