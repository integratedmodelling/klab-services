package org.integratedmodelling.klab.services.reasoner.internal;

import java.util.Comparator;
import java.util.Objects;
import org.integratedmodelling.klab.api.knowledge.Worldview;
import org.integratedmodelling.klab.api.scope.ServiceScope;
import org.integratedmodelling.klab.api.services.ResourcesService;
import org.integratedmodelling.klab.api.utils.Utils;

/** Resolves request-triggered changes at the service's global publication boundary. */
public final class ServiceWorldviewAdmission {
  private ServiceWorldviewAdmission() {}

  /**
   * A caller's authorized snapshot is evidence of a change, not authority to publish its
   * private worldview globally. Independently discover the same complete source through
   * the startup path, and use those beans and the service owner for candidate admission.
   * Never fall back to the caller's snapshot if discovery is unavailable or differs.
   */
  public static Worldview resolve(Worldview requested, ServiceScope service) {
    if (requested == null) throw new IllegalArgumentException("Requested worldview is unavailable");
    var owned = discover(service);
    if (!Objects.equals(requested.getWorldviewId(), owned.getWorldviewId())
          || !SemanticEnvironmentFactory.sources(requested).equals(SemanticEnvironmentFactory.sources(owned))
          || !SemanticEnvironmentFactory.sourceOrder(requested).equals(SemanticEnvironmentFactory.sourceOrder(owned)))
      throw new IllegalArgumentException("Requested source differs from the service worldview; retry after discovery");
    return owned;
  }

  /** Resolve only service-visible beans; request errors must never become global rejection evidence. */
  public static Worldview discover(ServiceScope service) {
    var sources = service.getServices(ResourcesService.class).stream()
        .sorted(Comparator.comparing(resources -> !Utils.URLs.isLocalHost(resources.getUrl())))
        .toList();
    for (var resources : sources) {
      if (!resources.status().isAvailable()
          || !resources.capabilities(service).isWorldviewProvider()) continue;
      var discovered = resources.list(Worldview.class, null).stream().findFirst().orElse(null);
      if (discovered == null) continue;
      var owned = BeanIngestionPreflight.snapshot(discovered);
      return owned;
    }
    throw new IllegalArgumentException("Service worldview discovery is unavailable");
  }
}
