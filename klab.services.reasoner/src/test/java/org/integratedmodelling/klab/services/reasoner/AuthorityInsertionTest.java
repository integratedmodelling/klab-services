package org.integratedmodelling.klab.services.reasoner;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.*;
import java.lang.reflect.*;
import org.integratedmodelling.klab.api.data.Version;
import org.integratedmodelling.klab.api.knowledge.*;
import org.integratedmodelling.klab.api.knowledge.impl.WorldviewImpl;
import org.integratedmodelling.klab.api.scope.Scope;
import org.integratedmodelling.klab.api.services.Authority;
import org.integratedmodelling.klab.api.services.reasoner.objects.AuthorityIdentitySyntax;
import org.integratedmodelling.klab.api.services.resources.objects.AuthorityIdentity;
import org.integratedmodelling.klab.api.services.runtime.Notification;
import org.integratedmodelling.klab.api.services.runtime.extension.Extensions;
import org.integratedmodelling.klab.components.ComponentRegistry;
import org.integratedmodelling.klab.indexing.SemanticSearchSession;
import org.integratedmodelling.klab.services.reasoner.internal.AuthorityBindings;
import org.integratedmodelling.klab.services.reasoner.owl.OWL;
import org.junit.jupiter.api.Test;
class AuthorityInsertionTest {
  @Test void canonicalLookupRejectsAliasesErrorsAndMissingProvidersBeforeMaterialization() throws Exception {
    var service = mock(ReasonerService.class, CALLS_REAL_METHODS); var scope = mock(Scope.class);
    var registry = mock(ComponentRegistry.class); doReturn(registry).when(service).getComponentRegistry();
    var component = mock(Extensions.ComponentDescriptor.class);
    when(component.id()).thenReturn("component.taxa"); when(component.version()).thenReturn(Version.create("1.0.0"));
    when(registry.getComponents(scope)).thenReturn(List.of(component));
    var worldview = new WorldviewImpl(); worldview.setUrn("test");
    worldview.setAuthorityBindings(List.of(new Worldview.AuthorityBinding("TAXA", "test:Species", "test", 0, 10, "hash",
        new Extensions.AuthorityDescriptor("provider.taxa", Version.create("1.0.0"), true, true, List.of(), List.of()),
        "component.taxa", Version.create("1.0.0"), null)));
    var provider = mock(Authority.class); when(provider.configure(any())).thenReturn("private");
    var bindings = new AuthorityBindings(); bindings.configure(new Authority.ConfigurationRequest("test", "TAXA",
        "test:Species", Map.of("urn", "provider.taxa")), provider);
    var owl = mock(OWL.class); set(service, "worldview", worldview); set(service, "authorityBindings", bindings); set(service, "owl", owl);
    var method = ReasonerService.class.getDeclaredMethod("resolveAuthoritySelection", String.class, String.class, Scope.class);
    method.setAccessible(true);
    var identity = new AuthorityIdentity(); identity.setId("123");
    when(provider.resolveIdentity("private", "alias")).thenReturn(identity);
    assertThrows(InvocationTargetException.class, () -> method.invoke(service, "TAXA", "alias", scope));
    identity.setNotifications(List.of(Notification.error("unresolved")));
    when(provider.resolveIdentity("private", "123")).thenReturn(identity);
    assertThrows(InvocationTargetException.class, () -> method.invoke(service, "TAXA", "123", scope));
    verifyNoInteractions(owl);
    identity.setNotifications(List.of()); var concept = mock(Concept.class);
    when(concept.is(SemanticType.IDENTITY)).thenReturn(true); when(owl.getConcept("TAXA:123")).thenReturn(concept);
    var selected = (SemanticSearchSession.AuthoritySelection) method.invoke(service, "TAXA", "123", scope);
    assertEquals("TAXA:123", selected.declaration()); assertSame(concept, selected.concept());
    assertThrows(InvocationTargetException.class, () -> method.invoke(service, "OTHER", "123", scope));
  }
  private void set(Object target, String field, Object value) throws Exception {
    var f = ReasonerService.class.getDeclaredField(field); f.setAccessible(true); f.set(target, value);
  }
}
