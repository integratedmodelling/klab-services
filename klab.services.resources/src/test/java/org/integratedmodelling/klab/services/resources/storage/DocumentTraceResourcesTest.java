package org.integratedmodelling.klab.services.resources.storage;
import static org.junit.jupiter.api.Assertions.*;
import org.integratedmodelling.klab.api.lang.kim.impl.KimNamespaceImpl;
import org.integratedmodelling.klab.api.services.resources.ResourceSet;
import org.integratedmodelling.klab.api.services.runtime.Notification;
import org.junit.jupiter.api.Test;
class DocumentTraceResourcesTest {
  @Test void tracedSnapshotStillReplacesCorrectedDiagnosticsWithoutChangingSource() {
    var document = new KimNamespaceImpl(); document.setUrn("staging.vxii.basic");
    document.setProjectName("klab.staging.vxii"); document.setSourceCode("fixed source");
    var resource = new ResourceSet.Resource(); resource.setResourceUrn(document.getUrn());
    resource.getNotifications().add(Notification.error("old diagnostic"));
    WorkspaceManager.refreshValidationSnapshot(resource,document);
    assertTrue(resource.getNotifications().isEmpty()); assertEquals("fixed source",document.getSourceCode());
  }
}
