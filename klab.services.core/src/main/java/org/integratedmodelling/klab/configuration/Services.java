package org.integratedmodelling.klab.configuration;

import org.integratedmodelling.klab.api.services.Engine;
import org.integratedmodelling.klab.api.services.Reasoner;
import org.integratedmodelling.klab.api.services.Resolver;
import org.integratedmodelling.klab.api.services.Resources;
import org.integratedmodelling.klab.api.services.Runtime;

/**
 * Makes the k.LAB services available globally through self-notification on injection. Each service
 * implementation is required to manually register itself. Needed by small objects such as concepts
 * and observables, unless we want to implement them all as non-static embedded classes.
 * 
 * @author Ferd
 *
 */
public enum Services {

    INSTANCE;

    private Reasoner reasoner;
    private Resources resources;
    private Engine engine;
    private Resolver resolver;
    private Runtime runtime;
    public Reasoner getReasoner() {
        return reasoner;
    }
    public void setReasoner(Reasoner reasoner) {
        this.reasoner = reasoner;
    }
    public Resources getResources() {
        return resources;
    }
    public void setResources(Resources resources) {
        this.resources = resources;
    }
    public Engine getEngine() {
        return engine;
    }
    public void setEngine(Engine engine) {
        this.engine = engine;
    }
    public Resolver getResolver() {
        return resolver;
    }
    public void setResolver(Resolver resolver) {
        this.resolver = resolver;
    }
    public Runtime getRuntime() {
        return runtime;
    }
    public void setRuntime(Runtime runtime) {
        this.runtime = runtime;
    }

}
