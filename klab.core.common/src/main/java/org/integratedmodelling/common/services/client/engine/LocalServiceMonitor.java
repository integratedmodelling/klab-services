package org.integratedmodelling.common.services.client.engine;

import java.util.*;
import java.util.function.BiConsumer;
import org.integratedmodelling.common.services.client.ServiceClient;
import org.integratedmodelling.common.services.client.reasoner.ReasonerClient;
import org.integratedmodelling.common.services.client.resolver.ResolverClient;
import org.integratedmodelling.common.services.client.resources.ResourcesClient;
import org.integratedmodelling.common.services.client.runtime.RuntimeClient;
import org.integratedmodelling.klab.api.collections.Parameters;
import org.integratedmodelling.klab.api.engine.Engine;
import org.integratedmodelling.klab.api.exceptions.KlabIllegalStateException;
import org.integratedmodelling.klab.api.identities.Identity;
import org.integratedmodelling.klab.api.services.KlabService;
import org.integratedmodelling.klab.api.services.runtime.Message;

/**
 * Create one of these when there is a local distribution and subscribe to events. Keeps a client
 * for each service and monitors the appearance of a local service. When the service appears an
 * engine or service can switch to it.
 *
 * TODO also the shutdown should be managed here, and messages should be sent when shutdown is
 *  complete.
 */
public class LocalServiceMonitor {

  Map<KlabService.Type, KlabService> clients = new HashMap<>();
  List<BiConsumer<KlabService, KlabService.ServiceStatus>> serviceConsumers = new ArrayList<>();
  List<BiConsumer<Map<KlabService.Type, KlabService>, Boolean>> engineConsumers = new ArrayList<>();
  Map<KlabService.Type, Boolean> currentOnline = Collections.synchronizedMap(new HashMap<>());

  public LocalServiceMonitor(
      Identity identity,
      Parameters<Engine.Setting> settings,
      BiConsumer<KlabService, KlabService.ServiceStatus> serviceChangeMonitor,
      BiConsumer<Map<KlabService.Type, KlabService>, Boolean> engineChangeMonitor) {

    serviceConsumers.add(serviceChangeMonitor);
    engineConsumers.add(engineChangeMonitor);

    for (var type :
        List.of(
            KlabService.Type.RESOURCES,
            KlabService.Type.REASONER,
            KlabService.Type.RESOLVER,
            KlabService.Type.RUNTIME)) {
      clients.put(
          type,
          switch (type) {
            case REASONER -> ReasonerClient.createLocalOffline(identity, settings);
            case RESOURCES -> ResourcesClient.createLocalOffline(identity, settings);
            case RESOLVER -> ResolverClient.createLocalOffline(identity, settings);
            case RUNTIME -> RuntimeClient.createLocalOffline(identity, settings);
            default -> throw new KlabIllegalStateException("Can't happen");
          });
      for (var client : clients.values()) {
        ((ServiceClient) client)
            .connect((scope, message) -> handleMessage((ServiceClient) client, message));
      }
    }
  }

  private void handleMessage(ServiceClient service, Message message) {

    if (message.getMessageClass() == Message.MessageClass.ServiceLifecycle) {
      var online = currentOnline.values().stream().filter(b -> b).count();
      var status = service.status();
      currentOnline.put(status.getServiceType(), status.isOperational());
      for (var serviceListener : serviceConsumers) {
        serviceListener.accept(service, status);
      }

      var onlineNow = currentOnline.values().stream().filter(b -> b).count();
      if (onlineNow != online) {
        for (var engineListener : engineConsumers) {
          engineListener.accept(this.clients, onlineNow == 4);
        }
      }
    }
  }

}
