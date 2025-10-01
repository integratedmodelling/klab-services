package org.integratedmodelling.common.authentication.scope;

import org.integratedmodelling.klab.api.exceptions.KlabServiceAccessException;
import org.integratedmodelling.klab.api.identities.Identity;
import org.integratedmodelling.klab.api.services.KlabService;

import java.util.*;
import java.util.function.Predicate;

/**
 * The scopes at service side contain an explicitly allocated map of services, all coordinated in a
 * ServiceCatalog singleton and specialized to the running identity.
 */
public abstract class AbstractServiceScope extends AbstractReactiveScopeImpl {

  protected Map<KlabService.Type, List<? extends KlabService>> serviceMap = new HashMap<>();

  public AbstractServiceScope(Identity identity, boolean isSender, boolean isReceiver) {
    super(identity, isSender, isReceiver);
  }

  @Override
  public final <T extends KlabService> T getService(
      Class<T> serviceClass, Predicate<T>... selectors) {

    var services = getServices(serviceClass);

    if (selectors == null || selectors.length == 0) {
      if (services.isEmpty()) {
        throw new KlabServiceAccessException(
            "No suitable service for request of " + serviceClass.getSimpleName());
      }
      return (T) services.iterator().next();
    }

    for (var selector : selectors) {
      var ret =
          services.stream().filter(serviceClient -> selector.test((T) serviceClient)).toList();
      if (!ret.isEmpty()) {
        return (T) ret.getFirst();
      }
    }

    throw new KlabServiceAccessException(
        "No suitable service for request of " + serviceClass.getSimpleName());
  }

  @Override
  public final <T extends KlabService> Collection<T> getServices(Class<T> serviceClass) {
    if (serviceClass.equals(KlabService.class)) {
      var ret = new ArrayList<T>();
      for (var services : serviceMap.values()) {
        ret.addAll(services.stream().map(s -> (T) s).toList());
      }
      return ret;
    }
    return (Collection<T>)
        serviceMap.get(KlabService.Type.classify(serviceClass)).stream()
            .filter(s -> s.status().isOperational())
            .toList();
  }
}
