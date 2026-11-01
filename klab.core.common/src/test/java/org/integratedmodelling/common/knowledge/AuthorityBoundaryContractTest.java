package org.integratedmodelling.common.knowledge;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.*;
import org.integratedmodelling.klab.api.services.Authority;
import org.junit.jupiter.api.Test;

class AuthorityBoundaryContractTest {
  @Test void configurationCopiesMappingsAndRejectsMalformedOrUnsupportedBoundaries() {
    var boundaries = new HashMap<String, String>();
    boundaries.put("codelist:official", "biology:Species");
    var request = new Authority.ConfigurationRequest("wv", "TAXA", "biology:Taxon",
        Map.of("urn", "provider", "semanticBoundaries", boundaries));
    boundaries.clear();
    assertEquals(Map.of("codelist:official", "biology:Species"), request.semanticBoundaries());
    assertThrows(UnsupportedOperationException.class, () -> request.semanticBoundaries().clear());
    assertThrows(IllegalArgumentException.class, () -> request.requireSupportedBoundaries(Set.of()));
    assertDoesNotThrow(() -> request.requireSupportedBoundaries(Set.of("codelist:official")));
    for (Object invalid : List.of("SPECIES", Map.of("SPECIES", "TAXA:123"), Map.of("", "biology:Species"))) {
      assertThrows(IllegalArgumentException.class, () -> new Authority.ConfigurationRequest(
          "wv", "TAXA", "biology:Taxon", Map.of("urn", "provider", "semanticBoundaries", invalid)));
    }
  }

  @Test void deferralKeepsCanonicalIdentityButCannotMasqueradeAsFullAncestry() {
    var identity = mock(Authority.Identity.class);
    when(identity.getId()).thenReturn("Canonical");
    when(identity.getParentIds()).thenReturn(List.of("Parent"));
    when(identity.getBaseIdentity()).thenReturn("Root");
    var deferred = new Authority.ClassifiedIdentity(identity, Set.of("SPECIES"), Authority.HierarchyStatus.DEFERRED);
    assertEquals("Canonical", deferred.getId());
    assertTrue(deferred.getParentIds().isEmpty());
    assertNull(deferred.getBaseIdentity());
    var provider = mock(Authority.class, CALLS_REAL_METHODS);
    when(provider.resolveIdentity("config", "Canonical")).thenReturn(deferred);
    assertThrows(UnsupportedOperationException.class, () -> provider.resolveIdentity(
        "config", "Canonical", Authority.ResolutionMode.FULL_HIERARCHY));
  }
}
