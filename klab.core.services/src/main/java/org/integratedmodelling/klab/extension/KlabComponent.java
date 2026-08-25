package org.integratedmodelling.klab.extension;

import org.apache.groovy.util.Maps;
import org.integratedmodelling.klab.api.data.Version;
import org.integratedmodelling.klab.api.scope.Scope;
import org.integratedmodelling.klab.api.services.runtime.extension.Library;
import org.integratedmodelling.klab.configuration.ServiceConfiguration;
import org.pf4j.Plugin;
import org.pf4j.PluginWrapper;

/**
 * Implementation of a k.LAB component that discovers and advertises all k.LAB extensions on startup and
 * manages loading and unloading of libraries, adapters and all extendable endpoints. To work as expected, the
 * component class must be located in the root package of a component.
 */
public class KlabComponent extends Plugin {

    private final Version version;
    private final String name;

    /**
     * Constructor to be used by plugin manager for plugin instantiation. Your plugins have to provide
     * constructor with this exact signature to be successfully loaded by manager.
     *
     * @param wrapper
     */
    public KlabComponent(PluginWrapper wrapper) {
        super(wrapper);
        this.version = Version.create(wrapper.getDescriptor().getVersion());
        this.name = wrapper.getDescriptor().getPluginId();
        // TODO handle dependencies
    }

    boolean isResolved(Scope scope) {
        // TODO deps in scope
        return true;
    }

    @Override
    public void delete() {
        super.delete();
    }

    @Override
    public void start() {
        super.start();
    }

    @Override
    public void stop() {
        super.stop();
    }

    public Version getVersion() {
        return this.version;
    }

    public String getName() {
        return this.name;
    }
}
