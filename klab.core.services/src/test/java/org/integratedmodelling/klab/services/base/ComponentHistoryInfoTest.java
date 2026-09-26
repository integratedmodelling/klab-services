package org.integratedmodelling.klab.services.base;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import org.integratedmodelling.klab.api.data.Version;
import org.integratedmodelling.klab.api.knowledge.KlabAsset;
import org.integratedmodelling.klab.api.scope.UserScope;
import org.integratedmodelling.klab.api.services.runtime.extension.ComponentHistory;
import org.integratedmodelling.klab.api.services.runtime.extension.Extensions;
import org.integratedmodelling.klab.components.ComponentRegistry;
import org.junit.jupiter.api.Test;

class ComponentHistoryInfoTest {

  @Test
  void componentHistoryIsAnInfoProjectionWithComponentVisibility() {
    var service = mock(BaseService.class, CALLS_REAL_METHODS);
    var registry = mock(ComponentRegistry.class);
    var scope = mock(UserScope.class);
    var descriptor = mock(Extensions.ComponentDescriptor.class);
    var version = Version.create("1.0.0");
    var history = new ComponentHistory("component.one", version, List.of());

    doReturn(registry).when(service).getComponentRegistry();
    when(descriptor.id()).thenReturn("component.one");
    when(descriptor.version()).thenReturn(version);
    when(registry.getComponents(scope)).thenReturn(List.of(descriptor));
    when(registry.getComponentHistory("component.one@1.0.0")).thenReturn(history);

    assertSame(
        history,
        service.info(
            "component.one@1.0.0",
            KlabAsset.KnowledgeClass.COMPONENT,
            ComponentHistory.class,
            scope));
    assertNull(
        service.info(
            "component.one@2.0.0",
            KlabAsset.KnowledgeClass.COMPONENT,
            ComponentHistory.class,
            scope));
  }
}
