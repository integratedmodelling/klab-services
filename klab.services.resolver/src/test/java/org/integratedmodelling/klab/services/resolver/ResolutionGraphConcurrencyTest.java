package org.integratedmodelling.klab.services.resolver;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;

import org.integratedmodelling.klab.api.knowledge.Resource;
import org.integratedmodelling.klab.api.lang.ServiceInfo;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.junit.jupiter.api.Test;

class ResolutionGraphConcurrencyTest {

  @Test
  void attemptsOwnMutableStateAndSnapshotContextResources() {
    var catalog = ResolutionGraph.create(mock(ContextScope.class));
    var firstResource = mock(Resource.class);
    var secondResource = mock(Resource.class);
    catalog.addLocalResource(firstResource);

    var firstAttempt = catalog.createAttempt();
    catalog.addLocalResource(secondResource);
    var secondAttempt = catalog.createAttempt();

    assertNotSame(firstAttempt.graph(), secondAttempt.graph());
    assertEquals(1, firstAttempt.getLocalResources().size());
    assertSame(firstResource, firstAttempt.getLocalResources().getFirst());
    assertEquals(2, secondAttempt.getLocalResources().size());

    var serviceInfo = mock(ServiceInfo.class);
    firstAttempt.addServiceInfo("service", serviceInfo);
    assertSame(serviceInfo, firstAttempt.getServiceInfo("service"));
    assertNull(secondAttempt.getServiceInfo("service"));
    assertNull(catalog.getServiceInfo("service"));
  }
}
