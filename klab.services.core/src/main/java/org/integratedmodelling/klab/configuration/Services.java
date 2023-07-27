package org.integratedmodelling.klab.configuration;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;

import org.integratedmodelling.klab.api.collections.Pair;
import org.integratedmodelling.klab.api.exceptions.KServiceAccessException;
import org.integratedmodelling.klab.api.scope.Scope;
import org.integratedmodelling.klab.api.services.Authority;
import org.integratedmodelling.klab.api.services.CurrencyService;
import org.integratedmodelling.klab.api.services.KlabService;
import org.integratedmodelling.klab.api.services.Service;
import org.integratedmodelling.klab.api.services.UnitService;
import org.integratedmodelling.klab.api.services.runtime.Channel;
import org.integratedmodelling.klab.data.mediation.CurrencyServiceImpl;
import org.integratedmodelling.klab.data.mediation.UnitServiceImpl;

/**
 * This singleton manages a catalog of k.LAB {@link Service}s. This class should
 * NOT be used to provide access to the {@link KlabService}s: instead, it can be
 * used to register and access services needed at runtime (such as units,
 * currencies, authorities). Service implementations explicitly register
 * themselves and can be looked up by class (if singletons) or by name when
 * registered with one. The core package automatically registers default
 * services to handle units, language expressions and currencies.
 * <p>
 * If multiple services are registered for the same class, they should be set
 * with parameters that allow the system to differentiate or filter them in
 * queries. Multiple parameters are matched using containsAll on a key set; it
 * is expected that keys used are PODs or comparables.
 * <p>
 * The class also holds utility methods to access multiple services concurrently
 * and merge query results intelligently. These should be used with federated
 * services sourced from the current scope.
 * <p>
 * Using this class is <em>not</em> the way to access a {@link KlabService},
 * only accessed through the k.LAB {@link Scope}. The scope has
 * {@link Scope#getService(Class)} and {@link Scope#getServices(Class)} methods
 * that will return the service(s) assigned to the scope.
 * <p>
 * Authorities are also independent and redundant services, and they are
 * discovered and made available through this class. The authority service
 * returned should be the one with the lightest load and shouldn't be saved.
 * 
 * @author Ferd
 */
public enum Services {

	INSTANCE;

	private Map<Class<?>, Map<Set<Object>, Service>> services = new HashMap<>();
	private Map<String, Authority> authorities = new HashMap<>();

	private Services() {
		// TODO register unit service, currency service, language services...
		registerService(new UnitServiceImpl(), UnitService.class);
		registerService(new CurrencyServiceImpl(), CurrencyService.class);
	}

	/**
	 * Obtain the best service available for the class and parameters. If multiple
	 * are available, choose the one with the lightest load or access cost,
	 * according to implementation. If not found, throw a
	 * {@link KServiceAccessException}.
	 * 
	 * @param <T>
	 * @param serviceClass
	 * @param parameters   any POD or Comparable.
	 * @throws KServiceAccessException if the requested service is not available
	 * @return
	 */
	public <T extends Service> T getService(Class<T> serviceClass, Object... parameters)
			throws KServiceAccessException {
		Collection<T> results = getServices(serviceClass, parameters);
		if (results.size() > 0) {
			return results.iterator().next();
		}
		throw new KServiceAccessException("no service of class " + serviceClass.getCanonicalName() + " was registered");
	}

	/**
	 * Return all registered and available services of the passed class, potentially
	 * filtering through the passed parameters. If none available, return an empty
	 * collection. If multiple services are available, they should be sorted with
	 * the "best" service on top.
	 * 
	 * @param <T>
	 * @param serviceClass
	 * @param parameters   any POD or Comparable
	 * @return
	 */
	@SuppressWarnings("unchecked")
	public <T extends Service> Collection<T> getServices(Class<T> serviceClass, Object... parameters) {

		Set<T> ret = new HashSet<>();
		Set<Object> key = new HashSet<>();
		if (parameters != null) {
			for (Object p : parameters) {
				key.add(p);
			}
		}

		Map<Set<Object>, Service> rets = services.get(serviceClass);

		if (rets != null) {
			for (Set<Object> k : rets.keySet()) {
				if (k.containsAll(key)) {
					ret.add((T) rets.get(k));
				}
			}
		}

		// TODO sort services in case their load, availability or remoteness can be
		// measured

		return ret;
	}

	public void registerService(Service service, Class<? extends Service> keyClass, Object... parameters) {

		Set<Object> key = new HashSet<>();
		if (parameters != null) {
			for (Object p : parameters) {
				key.add(p);
			}
		}
		Map<Set<Object>, Service> rets = services.get(keyClass);
		if (rets == null) {
			rets = new HashMap<>();
			services.put(keyClass, rets);
		}

		rets.put(key, service);

	}

	public Map<String, Authority> getAuthorities() {
		return authorities;
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
	public <S extends KlabService, T> Collection<T> broadcastRequest(Function<S, T> request, Scope scope,
			Class<S> serviceClass) {

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
