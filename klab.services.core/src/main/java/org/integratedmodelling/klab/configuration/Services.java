package org.integratedmodelling.klab.configuration;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.integratedmodelling.klab.api.authentication.scope.Scope;
import org.integratedmodelling.klab.api.services.Authority;
import org.integratedmodelling.klab.api.services.CurrencyService;
import org.integratedmodelling.klab.api.services.Engine;
import org.integratedmodelling.klab.api.services.Reasoner;
import org.integratedmodelling.klab.api.services.Resolver;
import org.integratedmodelling.klab.api.services.ResourceProvider;
import org.integratedmodelling.klab.api.services.Runtime;
import org.integratedmodelling.klab.api.services.UnitService;

/**
 * Makes the k.LAB services available globally through service discovery, self-notification or
 * injection. In testing and local configurations, service implementations must explicitly register
 * themselves. Needed by small objects such as concepts and observables, unless we want to implement
 * them all as non-static embedded classes.
 * <p>
 * This also gives access to any <em>additional</em> federated resource managers and runtimes that
 * were discovered, through the {@link #getFederatedResources()} and
 * {@link #getFederatedRuntimes()}. These should be automatically managed in a microservice
 * environment and always accessed directly from this singleton, never saved and used only to
 * perform atomic operations. For now we assume that the reasoner and the resolver are singletons
 * within an engine, as they maintain semantic assets and reactive observations that remain
 * available throughout a session.
 * <p>
 * Authorities are also potentially independent and redundant services, and they are discovered and
 * made available through this class.
 * 
 * @author Ferd
 *
 */
public enum Services {

    INSTANCE;

    private Reasoner reasoner;
    private ResourceProvider resources;
    private Engine engine;
    private Resolver resolver;
    private Runtime runtime;
    private UnitService unitService;
    private CurrencyService currencyService;

    private Map<String, Authority> authorities = new HashMap<>();
    private List<Reasoner> federatedRuntimes = new ArrayList<>();
    private List<ResourceProvider> federatedResources = new ArrayList<>();

    public Reasoner getReasoner() {
        return reasoner;
    }

    /**
     * Return the resource providers available to the passed scope, best matches first (also
     * considering social features).
     * 
     * @param scope
     * @return
     */
    public List<ResourceProvider> resourceProviders(Scope scope) {
        return null;
    }

    /**
     * Return the runtimes available to the passed scope.
     * 
     * @param scope
     * @return
     */
    public List<Runtime> runtimes(Scope scope) {
        return null;
    }

    public void setReasoner(Reasoner reasoner) {
        this.reasoner = reasoner;
    }
    public ResourceProvider getResources() {
        return resources;
    }
    public void setResources(ResourceProvider resources) {
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
    public List<Reasoner> getFederatedRuntimes() {
        return federatedRuntimes;
    }
    public void setFederatedRuntimes(List<Reasoner> federatedRuntimes) {
        this.federatedRuntimes = federatedRuntimes;
    }
    public List<ResourceProvider> getFederatedResources() {
        return federatedResources;
    }
    public void setFederatedResources(List<ResourceProvider> federatedResources) {
        this.federatedResources = federatedResources;
    }
    public Map<String, Authority> getAuthorities() {
        return authorities;
    }
    public void setAuthorities(Map<String, Authority> authorities) {
        this.authorities = authorities;
    }

    public UnitService getUnitService() {
        return unitService;
    }

    public void setUnitService(UnitService unitService) {
        this.unitService = unitService;
    }

    public CurrencyService getCurrencyService() {
        return currencyService;
    }

    public void setCurrencyService(CurrencyService currencyService) {
        this.currencyService = currencyService;
    }

}
