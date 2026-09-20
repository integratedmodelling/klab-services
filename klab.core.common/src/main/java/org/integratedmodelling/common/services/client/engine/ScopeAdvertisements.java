package org.integratedmodelling.common.services.client.engine;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import org.integratedmodelling.common.logging.Logging;
import org.integratedmodelling.common.services.client.BaseServiceClient;
import org.integratedmodelling.klab.api.services.runtime.objects.UserScopeNotification;

/** Retains the latest advertisement until acknowledged, with one delivery per target at a time. */
final class ScopeAdvertisements implements AutoCloseable {
  private record Delivery(BaseServiceClient client, UserScopeNotification notification) {}

  private final ConcurrentHashMap<String, Delivery> pending = new ConcurrentHashMap<>();
  private final Set<String> delivering = ConcurrentHashMap.newKeySet();
  private final Consumer<Runnable> dispatch;
  private ScheduledExecutorService retries;

  ScopeAdvertisements() {
    this(task -> Thread.ofVirtual().start(task));
    retries = Executors.newSingleThreadScheduledExecutor(
        Thread.ofPlatform().daemon(true).name("klab-scope-advertisements").factory());
    retries.scheduleWithFixedDelay(this::deliverPending, 5, 5, TimeUnit.SECONDS);
  }

  ScopeAdvertisements(Consumer<Runnable> dispatch) {
    this.dispatch = dispatch;
  }

  void submit(BaseServiceClient client, UserScopeNotification notification) {
    var id = client.serviceId();
    if (id != null) {
      pending.put(id, new Delivery(client, notification.copy()));
    }
  }

  void deliverPending() {
    pending.forEach((id, delivery) -> {
      if (delivering.add(id)) {
        dispatch.accept(() -> {
          try {
            if (delivery.client().notifyScope(delivery.notification())) {
              // An older acknowledgement must not discard a newer advertisement.
              pending.remove(id, delivery);
            }
          } catch (Exception e) {
            Logging.INSTANCE.warn("Scope advertisement to " + id + " failed; will retry: " + e);
          } finally {
            delivering.remove(id);
          }
        });
      }
    });
  }

  void clear() {
    pending.clear();
  }

  @Override
  public void close() {
    if (retries != null) {
      retries.shutdownNow();
    }
    clear();
  }
}
