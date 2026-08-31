package org.integratedmodelling.klab.services.resources;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.integratedmodelling.klab.api.data.Version;
import org.integratedmodelling.klab.api.services.ResourcesService.SubmissionMode;
import org.integratedmodelling.klab.api.services.resources.ResourceInfo;
import org.integratedmodelling.klab.api.services.resources.impl.ResourceImpl;
import org.junit.jupiter.api.Test;

class ResourcesProviderSubmissionTest {

  @Test
  void temporaryUpdatesAreLimitedToTierZeroOrLocalServices() {
    var tierZero = new ResourceInfo();
    tierZero.setReviewStatus(0);
    var tierOne = new ResourceInfo();
    tierOne.setReviewStatus(1);

    assertTrue(ResourcesProvider.allowsTemporaryUpdate(false, tierZero));
    assertFalse(ResourcesProvider.allowsTemporaryUpdate(false, tierOne));
    assertFalse(ResourcesProvider.allowsTemporaryUpdate(false, null));
    assertTrue(ResourcesProvider.allowsTemporaryUpdate(true, tierOne));
    assertTrue(ResourcesProvider.allowsTemporaryUpdate(true, null));
  }

  @Test
  void replacementKeepsHistoryWhileUpdatePromotesCurrentVersionIntoIt() {
    var old = resource("1.0.0");
    old.setHistory(List.of(resource("0.9.0")));

    var replacement = resource("1.0.0");
    assertNull(
        ResourcesProvider.prepareVersionHistory(replacement, old, SubmissionMode.REPLACE));
    assertEquals(List.of(new Version("0.9.0")), versions(replacement));

    var update = resource("1.1.0");
    assertNull(ResourcesProvider.prepareVersionHistory(update, old, SubmissionMode.UPDATE));
    assertEquals(List.of(new Version("0.9.0"), new Version("1.0.0")), versions(update));
    assertTrue(update.getHistory().stream().allMatch(previous -> previous.getHistory().isEmpty()));
  }

  @Test
  void onlyOwnerOrAdministratorCanUpdateOwnedPermissionRecords() {
    var info = new ResourceInfo();
    info.setOwner("owner");

    assertTrue(ResourcesProvider.allowsRightsUpdate("urn:test", info, "owner", false));
    assertTrue(ResourcesProvider.allowsRightsUpdate("urn:test", info, "admin", true));
    assertFalse(ResourcesProvider.allowsRightsUpdate("urn:test", info, "someone", false));

    info.setPermissionsOwnerUrn("urn:parent");
    assertFalse(ResourcesProvider.allowsRightsUpdate("urn:test", info, "owner", true));
  }

  private static ResourceImpl resource(String version) {
    var resource = new ResourceImpl();
    resource.setUrn("local:test:data:resource");
    resource.setVersion(new Version(version));
    return resource;
  }

  private static List<Version> versions(ResourceImpl resource) {
    return resource.getHistory().stream().map(previous -> previous.getVersion()).toList();
  }
}
