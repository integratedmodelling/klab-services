package org.integratedmodelling.common.authentication.scope;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import org.integratedmodelling.common.services.client.engine.EngineImpl;
import org.integratedmodelling.klab.api.exceptions.KlabServiceAccessException;
import org.integratedmodelling.klab.api.identities.Identity;
import org.integratedmodelling.klab.api.services.KlabService;

/**
 * The scopes at client side do not provide service clients directly, but through the service
 * monitor maintained by the engine implementation provided on construction.
 */
public abstract class AbstractClientScope extends AbstractReactiveScopeImpl {

  private final EngineImpl engine;

  public AbstractClientScope(
      Identity identity, boolean isSender, boolean isReceiver, EngineImpl engine) {
    super(identity, isSender, isReceiver);
    this.engine = engine;
  }

  public EngineImpl getEngine() {
    return engine;
  }

  @Override
  public <T extends KlabService> Collection<T> getServices(Class<T> serviceClass) {
    return engine.getServiceMonitor() == null
        ? List.of()
        : engine.getServiceMonitor().getServices(serviceClass);
  }

  @Override
  public <T extends KlabService> T getService(Class<T> serviceClass) {
    return getServices(serviceClass).stream()
        .findAny()
        .orElseThrow(
            () ->
                new KlabServiceAccessException(
                    "No suitable service for request of " + serviceClass.getSimpleName()));
  }

  @Override
  public <T extends KlabService> Optional<T> findService(
      Class<T> serviceClass, Predicate<T> selector) {

    var services = getServices(serviceClass);
    var ret = services.stream().filter(serviceClient -> selector.test((T) serviceClient)).toList();
    if (!ret.isEmpty()) {
      return Optional.of((T) ret.getFirst());
    }

    return Optional.empty();
  }
}
