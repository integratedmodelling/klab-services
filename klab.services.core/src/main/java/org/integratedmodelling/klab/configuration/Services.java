package org.integratedmodelling.klab.configuration;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;

import org.integratedmodelling.klab.api.authentication.scope.Scope;
import org.integratedmodelling.klab.api.collections.Pair;
import org.integratedmodelling.klab.api.services.Authority;
import org.integratedmodelling.klab.api.services.CurrencyService;
import org.integratedmodelling.klab.api.services.KlabService;
import org.integratedmodelling.klab.api.services.Language;
import org.integratedmodelling.klab.api.services.Reasoner;
import org.integratedmodelling.klab.api.services.ResourceProvider;
import org.integratedmodelling.klab.api.services.UnitService;
import org.integratedmodelling.klab.api.services.runtime.Channel;

/**
 * Makes the k.LAB services available globally through service discovery,
 * self-notification or injection. In testing and local configurations, service
 * implementations must explicitly register themselves. Needed by small objects
 * such as concepts and observables, unless we want to implement them all as
 * non-static embedded classes.
 * <p>
 * Using this class is <em>not</em> the recommended way to access a service when
 * one is needed from within a k.LAB {@link Scope}. The scope has a
 * {@link Scope#getService(Class)} method that will return the service assigned
 * to the scope. This class should be used only to access federated resources
 * from functions that require them, or by other services that need to perform
 * selected operations outside of any scope (e.g. the reasoner will need a
 * resource service at initialization to access the worldview).
 * <p>
 * This also gives access to any <em>additional</em> federated resource managers
 * and runtimes that were discovered, through the
 * {@link #getFederatedResources()} and {@link #getFederatedRuntimes()}. These
 * should be automatically managed in a microservice environment and always
 * accessed directly from this singleton, never saved, and used only to perform
 * atomic operations. For now we assume that the reasoner and the resolver are
 * singletons within an engine, as they maintain semantic assets and reactive
 * observations that remain available throughout a session.
 * <p>
 * Authorities are also potentially independent and redundant services, and they
 * are discovered and made available through this class. The authority service
 * returned should be the one with the lightest load and shouldn't be saved.
 * 
 * @author Ferd
 *
 */
public enum Services {

	INSTANCE;

	private Reasoner reasoner;
	private ResourceProvider resources;
//	private Resolver resolver;
//	private RuntimeService runtime;
	private UnitService unitService;
	private CurrencyService currencyService;

	private Map<String, Authority> authorities = new HashMap<>();
	private List<Reasoner> federatedRuntimes = new ArrayList<>();
	private List<ResourceProvider> federatedResources = new ArrayList<>();
	private Language languageService;

	public Reasoner getReasoner() {
		return reasoner;
	}

	/**
	 * Return the resource providers available to the passed scope, best matches
	 * first (also considering social features).
	 * 
	 * @param scope
	 * @return
	 */
	public List<ResourceProvider> resourceProviders(Scope scope) {
		return null;
	}

//	/**
//	 * Return the runtimes available to the passed scope.
//	 * 
//	 * @param scope
//	 * @return
//	 */
//	public List<RuntimeService> runtimes(Scope scope) {
//		return null;
//	}
//
	public void setReasoner(Reasoner reasoner) {
		this.reasoner = reasoner;
	}

	public ResourceProvider getResources() {
		return resources;
	}

	public void setResources(ResourceProvider resources) {
		this.resources = resources;
	}

//	public Resolver getResolver() {
//		return resolver;
//	}

//	public void setResolver(Resolver resolver) {
//		this.resolver = resolver;
//	}

//	public RuntimeService getRuntime() {
//		return runtime;
//	}
//
//	public void setRuntime(RuntimeService runtime) {
//		this.runtime = runtime;
//	}

	public List<Reasoner> getFederatedRuntimes(Scope scope) {
		return federatedRuntimes;
	}

//	public void setFederatedRuntimes(List<Reasoner> federatedRuntimes) {
//		this.federatedRuntimes = federatedRuntimes;
//	}

	public List<ResourceProvider> getFederatedResources(Scope scope) {
		return federatedResources;
	}

//	public void setFederatedResources(List<ResourceProvider> federatedResources) {
//		this.federatedResources = federatedResources;
//	}

	public Map<String, Authority> getAuthorities() {
		return authorities;
	}

//	public void setAuthorities(Map<String, Authority> authorities) {
//		this.authorities = authorities;
//	}

	public UnitService getUnitService() {
		return unitService;
	}

	public void setUnitService(UnitService unitService) {
		this.unitService = unitService;
	}

	public CurrencyService getCurrencyService() {
		return currencyService;
	}

	public Language getLanguageService() {
		return languageService;
	}

	public void setLanguageService(Language unitService) {
		this.languageService = unitService;
	}

	public void setCurrencyService(CurrencyService currencyService) {
		this.currencyService = currencyService;
	}

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
	public <S extends KlabService, T> Collection<T> broadcastRequest(Function<S, T> request, Class<S> serviceClass) {

		Collection<KlabService> services = availableServices(serviceClass);

		return null;
	}

	/**
	 * Retrieve every available and online service of the passed class.
	 * 
	 * @param serviceClass
	 * @return
	 */
	public Collection<KlabService> availableServices(Class<? extends KlabService> serviceClass) {
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
//      Collection<Callable<K>> tasks = new ArrayList<>();
//      ISession session = monitor.getIdentity().getParentIdentity(ISession.class);
//      for (INodeIdentity node : onlineNodes.values()) {
//          tasks.add(new Callable<K>(){
//              @Override
//              public K call() throws Exception {
//                  return node.getClient().onBehalfOf(session.getUser()).get(endpoint, individualResponseType, urlVariables);
//              }
//          });
//      }
//
//      ExecutorService executor = Executors.newFixedThreadPool((onlineNodes.size() + offlineNodes.size()) > MAX_THREADS
//              ? MAX_THREADS
//              : (onlineNodes.size() + offlineNodes.size()));
//
//      int failures = 0;
//      List<K> retvals = new ArrayList<>();
//      List<Future<K>> results;
//      try {
//          results = executor.invokeAll(tasks);
//          for (Future<K> result : results) {
//              try {
//                  retvals.add(result.get());
//              } catch (Exception e) {
//                  failures++;
//              }
//          }
//      } catch (Exception e) {
//          throw new KlabIOException(e);
//      }
//
//      if (failures > 0) {
//          String message = "broadcasting to network resulted in " + failures + " failed calls out of " + onlineNodes.size();
//          if (failures >= onlineNodes.size()) {
//              monitor.error(message);
//          } else {
//              monitor.warn(message);
//          }
//      }
//
//      executor.shutdown();
//
//      return merger.apply(retvals);

		return null;
	}

}
