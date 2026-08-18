package org.integratedmodelling.klab.tests.services.resources;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.integratedmodelling.klab.api.lang.kactors.KActorsBehavior;
import org.junit.jupiter.api.Test;

class ManagedTestcaseIdentityTest {

  private final KActorsTestSupport support = new KActorsTestSupport();

  @Test
  void parsedTestcaseKeepsItsCompleteDeclaredUrn() {
    var result =
        support.loadResource("/testcases/klab/staging/vxii/testsuite.kactors");

    assertTrue(result.parserNotifications().isEmpty(), result.parserNotifications().toString());
    assertTrue(
        result.adaptationNotifications().isEmpty(),
        result.adaptationNotifications().toString());
    assertNotNull(result.behavior());
    assertTrue(result.analysisSuccessful(), () -> result.allNotifications().toString());
    assertEquals(KActorsBehavior.Type.UNITTEST, result.behavior().getBehaviorType());
    assertEquals("klab.staging.vxii.testsuite", result.behavior().getUrn());
  }
}
