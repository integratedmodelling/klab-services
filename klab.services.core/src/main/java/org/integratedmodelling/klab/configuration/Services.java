package org.integratedmodelling.klab.configuration;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;

import org.integratedmodelling.klab.api.collections.Pair;
import org.integratedmodelling.klab.api.scope.Scope;
import org.integratedmodelling.klab.api.services.Authority;
import org.integratedmodelling.klab.api.services.CurrencyService;
import org.integratedmodelling.klab.api.services.KlabService;
import org.integratedmodelling.klab.api.services.Language;
import org.integratedmodelling.klab.api.services.Reasoner;
import org.integratedmodelling.klab.api.services.ResourcesService;
import org.integratedmodelling.klab.api.services.UnitService;
import org.integratedmodelling.klab.api.services.runtime.Channel;
import org.integratedmodelling.klab.api.services.runtime.extension.Library;

/**
 * Makes secondary k.LAB services available through service discovery,
 * self-notification or injection. This class should NOT be used to provide
 * access to the main k.LAB services: instead, it can be used to register and
 * access services needed at runtime (such as units, currencies, authorities).
 * Service implementations explicitly register themselves and can be looked up
 * by class (if singletons) or by name when registered with one.
 * <p>
 * The class also holds utility methods to access multiple services concurrently
 * and merge query results intelligently. These should be used with federated
 * services sourced from the current scope.
 * <p>
 * Using this class is <em>not</em> the recommended way to access a service when
 * one is needed from within a k.LAB {@link Scope}. The scope has a
 * {@link Scope#getService(Class)} method that will return the service assigned
 * to the scope. This class should be used only to access federated resources
 * from functions that require them, or by other services that need to perform
 * selected operations outside of any scope (e.g. the reasoner will need a
 * resource service at initialization to access the worldview).
 * <p>
 * Authorities are also potentially independent and redundant services, and they
 * are discovered and made available through this class. The authority service
 * returned should be the one with the lightest load and shouldn't be saved.
 * 
 * @author Ferd
 */
public enum Services {

	INSTANCE;

	private UnitService unitService;
	private CurrencyService currencyService;

	private Map<String, Authority> authorities = new HashMap<>();
	private List<Reasoner> federatedRuntimes = new ArrayList<>();
	private List<ResourcesService> federatedResources = new ArrayList<>();
	private Language languageService;

	/**
	 * Return the resource providers available to the passed scope, best matches
	 * first (also considering social features).
	 * 
	 * @param scope
	 * @return
	 */
	public List<ResourcesService> resourceProviders(Scope scope) {
		return null;
	}

	@Deprecated
	public List<Reasoner> getFederatedRuntimes(Scope scope) {
		return federatedRuntimes;
	}

	@Deprecated
	public List<ResourcesService> getFederatedResources(Scope scope) {
		return federatedResources;
	}

	public Map<String, Authority> getAuthorities() {
		return authorities;
	}

	@Deprecated
	public UnitService getUnitService() {
		return unitService;
	}

	@Deprecated
	public void setUnitService(UnitService unitService) {
		this.unitService = unitService;
	}

	@Deprecated
	public CurrencyService getCurrencyService() {
		return currencyService;
	}

	@Deprecated
	public Language getLanguageService() {
		return languageService;
	}

	@Deprecated
	public void setLanguageService(Language unitService) {
		this.languageService = unitService;
	}

	@Deprecated
	public void setCurrencyService(CurrencyService currencyService) {
		this.currencyService = currencyService;
	}

	@Deprecated
	public void registerAuthority(Authority authority) {
		if (authority.getCapabilities().getSubAuthorities().isEmpty()) {
			this.authorities.put(authority.getName(), authority);
		} else {
			for (Pair<String, String> sub : authority.getCapabilities().getSubAuthorities()) {
				String aname = authority.getName() + (sub.getFirst().isEmpty() ? "" : ("." + sub.getFirst()));
				this.authorities.put(aname,
						sub.getFirst().isEmpty() ? authority : authority.subAuthority(sub.getFirst()));
			}
		}
	}

	private static int MAX_THREADS = 10;

	/**
	 * Broadcast a request to all accessible services of a given type concurrently
	 * and merge the results of the request function into a collection.
	 * 
	 * @param <S>
	 * @param <T>
	 * @param request
	 * @param serviceClass
	 * @return
	 */
	public <S extends KlabService, T> Collection<T> broadcastRequest(Function<S, T> request, Scope scope,
			Class<S> serviceClass) {

		Collection<KlabService> services = availableServices(scope, serviceClass);

		return null;
	}

	/**
	 * Retrieve every available and online service of the passed class.
	 * 
	 * @param serviceClass
	 * @return
	 */
	public Collection<KlabService> availableServices(Scope scope, Class<? extends KlabService> serviceClass) {
		// TODO Auto-generated method stub
		return null;
	}

	/**
	 * Use to concurrently submit a request to the available federated services of a
	 * particular type and merge the results into a given collection.
	 * 
	 * @param <T>                    type of result in the resulting collections
	 * @param serviceType            type of the service (use the interfaces!)
	 * @param retriever              a function that retrieves results from each
	 *                               individual service
	 * @param merger                 a function that takes the results of all
	 *                               services and returns the final organization of
	 *                               them as a single collection
	 * @param individualResponseType the type of the response object (not sure it's
	 *                               needed)
	 * @param monitor                a monitor to check on progress and report
	 *                               errors
	 * @return the merged collection
	 */
	public <S extends KlabService, T> Collection<T> mergeServiceResults(Class<S> serviceClass,
			Supplier<Collection<T>> retriever, Function<Collection<Collection<T>>, Collection<T>> merger,
			Class<? extends T> individualResponseType, Channel monitor) {

		//
		// Collection<Callable<K>> tasks = new ArrayList<>();
		// ISession session = monitor.getIdentity().getParentIdentity(ISession.class);
		// for (INodeIdentity node : onlineNodes.values()) {
		// tasks.add(new Callable<K>(){
		// @Override
		// public K call() throws Exception {
		// return node.getClient().onBehalfOf(session.getUser()).get(endpoint,
		// individualResponseType, urlVariables);
		// }
		// });
		// }
		//
		// ExecutorService executor = Executors.newFixedThreadPool((onlineNodes.size() +
		// offlineNodes.size()) > MAX_THREADS
		// ? MAX_THREADS
		// : (onlineNodes.size() + offlineNodes.size()));
		//
		// int failures = 0;
		// List<K> retvals = new ArrayList<>();
		// List<Future<K>> results;
		// try {
		// results = executor.invokeAll(tasks);
		// for (Future<K> result : results) {
		// try {
		// retvals.add(result.get());
		// } catch (Exception e) {
		// failures++;
		// }
		// }
		// } catch (Exception e) {
		// throw new KlabIOException(e);
		// }
		//
		// if (failures > 0) {
		// String message = "broadcasting to network resulted in " + failures + " failed
		// calls out
		// of " + onlineNodes.size();
		// if (failures >= onlineNodes.size()) {
		// monitor.error(message);
		// } else {
		// monitor.warn(message);
		// }
		// }
		//
		// executor.shutdown();
		//
		// return merger.apply(retvals);

		return null;
	}

}
