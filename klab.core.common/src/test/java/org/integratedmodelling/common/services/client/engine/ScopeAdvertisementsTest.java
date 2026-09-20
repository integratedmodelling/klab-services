package org.integratedmodelling.common.services.client.engine;

import static org.mockito.Mockito.*;
import java.util.ArrayList;
import org.integratedmodelling.common.services.client.BaseServiceClient;
import org.integratedmodelling.klab.api.services.runtime.objects.UserScopeNotification;
import org.junit.jupiter.api.Test;

class ScopeAdvertisementsTest {
  @Test
  void retriesFailureWithoutAnotherStatusChangeAndStopsAfterAcknowledgement() {
    var client = mock(BaseServiceClient.class);
    when(client.serviceId()).thenReturn("resolver");
    when(client.notifyScope(any())).thenReturn(false, true);
    var advertisements = new ScopeAdvertisements(Runnable::run);
    advertisements.submit(client, new UserScopeNotification());
    advertisements.deliverPending();
    advertisements.deliverPending();
    advertisements.deliverPending();
    verify(client, times(2)).notifyScope(any());
  }

  @Test
  void newerTopologySurvivesOlderAcknowledgementAndDeliveryDoesNotOverlap() {
    var client = mock(BaseServiceClient.class);
    when(client.serviceId()).thenReturn("resolver");
    when(client.notifyScope(any())).thenReturn(true);
    var tasks = new ArrayList<Runnable>();
    var advertisements = new ScopeAdvertisements(tasks::add);
    advertisements.submit(client, new UserScopeNotification());
    advertisements.deliverPending();
    var updated = new UserScopeNotification();
    var runtime = new UserScopeNotification.ServiceInfo();
    runtime.setId("runtime");
    updated.getServices().add(runtime);
    advertisements.submit(client, updated);
    advertisements.deliverPending();
    org.junit.jupiter.api.Assertions.assertEquals(1, tasks.size());
    tasks.removeFirst().run();
    advertisements.deliverPending();
    tasks.removeFirst().run();
    verify(client).notifyScope(argThat(n -> n.getServices().size() == 1
        && "runtime".equals(n.getServices().getFirst().getId())));
    verify(client, times(2)).notifyScope(any());
  }

  @Test
  void retriesThrownDeliveryFailure() {
    var client = mock(BaseServiceClient.class);
    when(client.serviceId()).thenReturn("resolver");
    when(client.notifyScope(any())).thenThrow(new IllegalStateException("unreachable"))
        .thenReturn(true);
    var advertisements = new ScopeAdvertisements(Runnable::run);
    advertisements.submit(client, new UserScopeNotification());
    advertisements.deliverPending();
    advertisements.deliverPending();
    verify(client, times(2)).notifyScope(any());
  }
}
