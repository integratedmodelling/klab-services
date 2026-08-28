package org.integratedmodelling.klab.services.application.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.integratedmodelling.klab.api.services.KlabService;
import org.junit.jupiter.api.Test;

class WebUiConfigurationTest {

  @Test
  void buildsAnAnonymousDashboardAndOrdersPanels() {
    var configuration =
        WebUiConfiguration.builder(KlabService.Type.REASONER)
            .keycloak(null, null, null)
            .panel("late", "Late", "", "late-panel", 20, false)
            .panel("early", "Early", "", "early-panel", 10, true)
            .build();

    assertEquals("k.LAB Reasoner service", configuration.title());
    assertFalse(configuration.authentication().enabled());
    assertNull(configuration.authentication().url());
    assertEquals("early", configuration.panels().getFirst().id());
    assertTrue(configuration.panels().getFirst().requiresAuthentication());
  }

  @Test
  void publishesOnlyPublicKeycloakClientSettings() {
    var configuration =
        WebUiConfiguration.builder(KlabService.Type.RUNTIME)
            .keycloak("https://identity.example.org", "network", "service-dashboard")
            .build();

    assertTrue(configuration.authentication().enabled());
    assertEquals("https://identity.example.org", configuration.authentication().url());
    assertEquals("network", configuration.authentication().realm());
    assertEquals("service-dashboard", configuration.authentication().clientId());
  }
}
