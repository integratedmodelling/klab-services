package org.integratedmodelling.klab.services.reasoner.internal;

import static org.junit.jupiter.api.Assertions.*;
import org.integratedmodelling.klab.api.services.ResourcesService;
import org.integratedmodelling.klab.api.services.impl.ServiceStatusImpl;
import org.integratedmodelling.klab.api.scope.ServiceScope;
import org.integratedmodelling.klab.api.knowledge.Worldview;
import org.mockito.Mockito;
import java.util.List;
import org.integratedmodelling.klab.api.knowledge.impl.WorldviewImpl;
import org.integratedmodelling.klab.api.lang.kim.impl.KimOntologyImpl;
import org.integratedmodelling.klab.api.lang.kim.KimObservationStrategyDocument;
import org.junit.jupiter.api.Test;

class WorldviewSourcesTest {
  private WorldviewImpl view(String service) {
    var view = new WorldviewImpl();
    view.setUrn("base");
    view.setServiceId(service);
    return view;
  }

  private KimOntologyImpl ontology(WorldviewImpl view, String project, String urn, String... imports) {
    var doc = new KimOntologyImpl();
    doc.setProjectName(project);
    doc.setSourceCode(urn);
    doc.setUrn(urn);
    doc.getImportedOntologies().addAll(List.of(imports));
    view.getOntologies().add(doc);
    return doc;
  }

  @Test void overridesWholeProjectsAndOrdersCrossProviderDependencies() {
    var local = view("local");
    var remote = view("remote");
    var root = ontology(local, "upper", "base");
    var localDomain = ontology(local, "working", "fields", "earth");
    ontology(remote, "upper", "base");
    ontology(remote, "working", "fields");
    ontology(remote, "working", "removed");
    var strategy = Mockito.mock(KimObservationStrategyDocument.class);
    Mockito.when(strategy.getProjectName()).thenReturn("working");
    Mockito.when(strategy.getUrn()).thenReturn("removed.strategy");
    remote.getObservationStrategies().add(strategy);
    var earth = ontology(remote, "planet", "earth", "base");
    var result = WorldviewSources.compose(List.of(local, remote));
    assertEquals(List.of(root, earth, localDomain), result.getOntologies());
    assertTrue(result.getObservationStrategies().isEmpty());
    assertEquals(java.util.Map.of("upper","local","working","local","planet","remote"),
        result.getMetadata().get(Worldview.PROJECT_SOURCES));
    assertEquals(4, remote.getOntologies().size());
  }

  @Test void discoversBothProvidersWhenRemoteIsAdvertisedFirst() throws Exception {
    var local = view("local");
    ontology(local, "upper", "base");
    local.getMetadata().put(Worldview.PROJECT_SOURCES,
        java.util.Map.of("retired", "local"));
    var remote = view("remote");
    ontology(remote, "upper", "base");
    ontology(remote, "retired", "deleted");
    ontology(remote, "remote-only", "earth", "base");
    var scope = Mockito.mock(ServiceScope.class);
    var r = provider(scope, remote, "https://192.0.2.10/resources");
    var l = provider(scope, local, "http://127.0.0.1:8092/resources");
    Mockito.when(scope.getServices(ResourcesService.class))
        .thenReturn(List.of(r, l));
    var selection = WorldviewSources.discover(scope);
    assertEquals(List.of("base", "earth"), selection.getOntologies().stream().map(o -> o.getUrn()).toList());
    assertEquals(local.getWorldviewId(), selection.getWorldviewId());

    var caller = Mockito.mock(org.integratedmodelling.klab.api.scope.UserScope.class);
    Mockito.when(caller.getServices(ResourcesService.class)).thenReturn(List.of(r, l));
    for (var provider : List.of(r, l)) {
      var capabilities = provider.capabilities(scope);
      Mockito.when(provider.capabilities(caller)).thenReturn(capabilities);
    }
    Mockito.when(r.list(Worldview.class, caller)).thenReturn(List.of(remote));
    Mockito.when(l.list(Worldview.class, caller)).thenReturn(List.of(local));
    var reasoner = Mockito.mock(org.integratedmodelling.klab.services.reasoner.ReasonerService.class,
        Mockito.CALLS_REAL_METHODS);
    Mockito.when(reasoner.serviceScope()).thenReturn(scope);
    var changes = org.integratedmodelling.klab.api.services.resources.ResourceSet.empty();
    Mockito.doReturn(changes).when(reasoner).loadKnowledge(Mockito.any(), Mockito.same(scope));
    assertTrue(reasoner.updateKnowledge(changes, caller).getNotifications().isEmpty());
    var captured = org.mockito.ArgumentCaptor.forClass(Worldview.class);
    Mockito.verify(reasoner).loadKnowledge(captured.capture(), Mockito.same(scope));
    assertEquals(List.of("base", "earth"),
        captured.getValue().getOntologies().stream().map(o -> o.getUrn()).toList());
  }

  private ResourcesService provider(
      ServiceScope scope, WorldviewImpl view, String url) throws Exception {
    var provider = Mockito.mock(ResourcesService.class);
    var status = new ServiceStatusImpl();
    status.setAvailable(true);
    var caps = Mockito.mock(ResourcesService.Capabilities.class);
    Mockito.when(caps.isWorldviewProvider()).thenReturn(true);
    Mockito.when(provider.serviceId()).thenReturn(view.getServiceId());
    Mockito.when(provider.status()).thenReturn(status);
    Mockito.when(provider.getUrl()).thenReturn(java.net.URI.create(url).toURL());
    Mockito.when(provider.capabilities(scope)).thenReturn(caps);
    Mockito.when(provider.list(Worldview.class, null))
        .thenReturn(List.of(view));
    return provider;
  }

  @Test void rejectsNamespaceCollisionsAndMissingImports() {
    var local = view("local");
    var remote = view("remote");
    ontology(local, "upper", "base");
    ontology(remote, "other", "base");
    assertThrows(IllegalArgumentException.class, () -> WorldviewSources.compose(List.of(local, remote)));
    remote.getOntologies().clear();
    ontology(remote, "other", "extra", "missing");
    assertThrows(IllegalArgumentException.class, () -> WorldviewSources.compose(List.of(local, remote)));
  }
}
