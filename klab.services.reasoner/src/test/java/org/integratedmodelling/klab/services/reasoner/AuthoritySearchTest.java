package org.integratedmodelling.klab.services.reasoner;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.*;
import org.integratedmodelling.klab.api.collections.Pair;
import org.integratedmodelling.klab.api.services.Authority;
import org.integratedmodelling.klab.api.services.reasoner.objects.*;
import org.integratedmodelling.klab.api.services.resources.objects.AuthorityIdentity;
import org.integratedmodelling.klab.api.services.runtime.Notification;
import org.integratedmodelling.klab.services.reasoner.internal.AuthorityBindings;
import org.junit.jupiter.api.Test;

class AuthoritySearchTest {
  Authority provider = mock(Authority.class);
  Authority.Capabilities capabilities = mock(Authority.Capabilities.class);
  AuthorityBindings bindings = new AuthorityBindings();
  AuthoritySearchTest() {
    when(provider.getCapabilities()).thenReturn(capabilities);
    when(capabilities.isSearchable()).thenReturn(true);
    when(provider.configure(any())).thenReturn("private-provider-configuration");
    bindings.configure(new Authority.ConfigurationRequest("test", "TAXA", "test:Species",
        Map.of("urn", "provider.taxa")), provider);
  }
  AuthorityIdentity identity(String code) {
    var identity = new AuthorityIdentity(); identity.setId(code); identity.setLabel("Taxon " + code);
    identity.setAuthorityName("provider.taxa"); identity.setScore(.8f); return identity;
  }
  @Test void pagesCanonicalCandidatesWithoutExposingProviderMetadata() {
    var a = identity("123"); var b = identity("A:B"); b.setNotifications(List.of(Notification.warning("Uncertain")));
    when(provider.search("tree", null, "private-provider-configuration")).thenReturn(List.of(a, a, b));
    var first = bindings.search(new AuthoritySearchRequest("TAXA", "tree", null, 0, 1));
    assertEquals(AuthoritySearchResponse.Status.OK, first.status()); assertEquals(2, first.total());
    assertEquals(1, first.nextOffset()); assertEquals("TAXA", first.matches().getFirst().getAuthorityName());
    var second = bindings.search(new AuthoritySearchRequest("TAXA", "tree", null, 1, 1));
    assertEquals("TAXA:[A:B]", second.matches().getFirst().getLocator());
    assertEquals(1, second.matches().getFirst().getNotifications().size());
    assertEquals(-1, second.nextOffset()); assertTrue(second.matches().getFirst().getDocumentation().isEmpty());
  }
  @Test void failuresUnsupportedAndEmptySuccessAreDistinct() {
    var request = new AuthoritySearchRequest("TAXA", "tree", null, 0, 10);
    when(provider.search(anyString(), isNull(), anyString())).thenReturn(List.of());
    assertEquals(AuthoritySearchResponse.Status.OK, bindings.search(request).status());
    when(provider.search(anyString(), isNull(), anyString())).thenThrow(new IllegalArgumentException("secret"));
    assertEquals(AuthoritySearchResponse.Status.FAILED, bindings.search(request).status());
    assertFalse(bindings.search(request).notifications().toString().contains("secret"));
    when(capabilities.isSearchable()).thenReturn(false);
    assertEquals(AuthoritySearchResponse.Status.UNSUPPORTED, bindings.search(request).status());
    assertEquals(AuthoritySearchResponse.Status.UNAVAILABLE,
        bindings.search(new AuthoritySearchRequest("OTHER", "tree", null, 0, 10)).status());
  }
  @Test void filtersRequireExplicitProviderSemanticsAndAliasesAreNotBindings() {
    when(capabilities.getSubAuthorities()).thenReturn(List.of(Pair.of("GENUS", "Genus")));
    var request = new AuthoritySearchRequest("TAXA", "tree", "GENUS", 0, 10);
    assertEquals(AuthoritySearchResponse.Status.UNSUPPORTED, bindings.search(request).status());
    verify(provider, never()).search(any(), any(), any());
    when(capabilities.areSubAuthoritiesSearchFilters()).thenReturn(true);
    when(provider.search("tree", "GENUS", "private-provider-configuration")).thenReturn(List.of(identity("123")));
    assertEquals(1, bindings.search(request).matches().size());
    assertEquals(AuthoritySearchResponse.Status.UNAVAILABLE,
        bindings.search(new AuthoritySearchRequest("TAXA.GENUS", "tree", null, 0, 10)).status());
  }
}
