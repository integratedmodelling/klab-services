package org.integratedmodelling.klab.extension;

import java.util.Map;
import org.integratedmodelling.klab.api.data.Version;
import org.integratedmodelling.klab.api.scope.Scope;
import org.integratedmodelling.klab.services.application.web.WebUiConfiguration;
import org.pf4j.Plugin;
import org.pf4j.PluginWrapper;

/**
 * Implementation of a k.LAB component that discovers and advertises all k.LAB extensions on startup and
 * manages loading and unloading of libraries, adapters and all extendable endpoints. To work as expected, the
 * component class must be located in the root package of a component.
 */
public class KlabComponent extends Plugin {

    /** Classpath root reserved for browser assets packaged in a component archive. */
    public static final String WEB_UI_RESOURCE_ROOT = "META-INF/klab/webui/";

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

    /**
     * Add dashboard panels or full-page entries exposed by this installed component. This method is
     * called when the public Web UI configuration is built and must not perform expensive work.
     */
    public void configureWebUi(WebUiConfiguration.Builder dashboard) {}

    /**
     * Map Vue component IDs to prebuilt ESM files below {@link #WEB_UI_RESOURCE_ROOT}. Only declared
     * files are published by the hosting service.
     */
    public Map<String, String> webUiModules() {
        return Map.of();
    }
}
