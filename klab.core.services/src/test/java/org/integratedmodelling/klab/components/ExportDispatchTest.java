package org.integratedmodelling.klab.components;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.ArrayList;
import java.util.List;
import org.integratedmodelling.common.data.jackson.JacksonConfiguration;
import org.integratedmodelling.common.lang.ServiceCallImpl;
import org.integratedmodelling.klab.api.collections.Parameters;
import org.integratedmodelling.klab.api.data.Storage;
import org.integratedmodelling.klab.api.knowledge.KlabAsset;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.knowledge.observation.impl.ObservationImpl;
import org.integratedmodelling.klab.api.lang.Annotation;
import org.integratedmodelling.klab.api.scope.Scope;
import org.integratedmodelling.klab.api.services.runtime.extension.*;
import org.integratedmodelling.klab.api.services.resources.adapters.Exporter;
import org.integratedmodelling.klab.api.view.modeler.visualization.ColorRamp;
import org.integratedmodelling.klab.runtime.language.LanguageService;
import org.junit.jupiter.api.Test;

class ExportDispatchTest {
  @Test void missingPngTriggersComponentLoadAndCapabilityRefresh() {
    var scope = mock(org.integratedmodelling.klab.api.scope.UserScope.class);
    var resources = mock(org.integratedmodelling.klab.api.services.ResourcesService.class);
    var runtime = mock(org.integratedmodelling.klab.api.services.KlabService.class);
    var capabilities = mock(org.integratedmodelling.klab.api.services.KlabService.ServiceCapabilities.class);
    var exports = new java.util.HashMap<String, List<org.integratedmodelling.klab.api.services.resources.ResourceTransport.Schema>>();
    when(capabilities.getExportSchemata()).thenReturn(exports);
    when(runtime.capabilities(scope)).thenReturn(capabilities);
    when(scope.getService(org.integratedmodelling.klab.api.services.ResourcesService.class)).thenReturn(resources);
    var result = new org.integratedmodelling.klab.api.services.resources.ResourceSet();
    result.setEmpty(false);
    when(resources.resolve("export-schema:image/png", KlabAsset.KnowledgeClass.INFORMATION, scope)).thenReturn(result);
    when(runtime.loadResources(result, scope)).thenAnswer(invocation -> {
      exports.put("test", List.of(org.integratedmodelling.klab.api.services.resources.ResourceTransport.Schema.create(
          "test.png", org.integratedmodelling.klab.api.services.resources.ResourceTransport.Schema.Type.PROPERTIES,
          KlabAsset.KnowledgeClass.OBSERVATION, "PNG").mediaType("image/png")));
      return true;
    });
    var found = org.integratedmodelling.klab.api.services.resources.ResourceTransport.INSTANCE.findExportSchemata(
        KlabAsset.KnowledgeClass.OBSERVATION, "image/png", null, runtime, scope);
    assertEquals("test.png", found.getFirst().getSchemaId());
    verify(runtime).loadResources(result, scope);
    verify(runtime, times(2)).capabilities(scope);
  }
  @Library(name="test.png.dispatch", description="Export dispatch regression")
  public static class Exporters {
    @Exporter(schema="png", mediaType="image/png", knowledgeClass=KlabAsset.KnowledgeClass.OBSERVATION,
        description="Numeric overload")
    public String numeric(Observation observation, Storage.DoubleScanner scanner, Parameters<String> parameters) {
      return Integer.toHexString(ColorRamp.fromObservation(observation).argb(scanner.get(), -1, 1))
          + ":" + parameters.get("viewportX");
    }
    @Exporter(schema="png", mediaType="image/png", knowledgeClass=KlabAsset.KnowledgeClass.OBSERVATION,
        description="Keyed overload")
    public String keyed(Observation observation, Storage.KeyScanner<?> scanner, Parameters<String> parameters) {
      return "keyed:" + parameters.get("viewportX");
    }
  }

  @Test void overloadedExportRetainsTransportedAnnotationAndRequestParameters() throws Exception {
    var registry = mock(ComponentRegistry.class, CALLS_REAL_METHODS);
    var libraries = new ArrayList<Extensions.LibraryDescriptor>();
    var register = ComponentRegistry.class.getDeclaredMethod("registerLibrary", Library.class, Class.class, List.class);
    register.setAccessible(true);
    register.invoke(registry, Exporters.class.getAnnotation(Library.class), Exporters.class, libraries);
    var descriptors = libraries.getFirst().exporters().stream().map(pair -> pair.getSecond()).toList();
    assertEquals(2, descriptors.size());
    assertNotEquals(registry.implementation(descriptors.get(0)).method, registry.implementation(descriptors.get(1)).method);
    var call = ServiceCallImpl.create("test.png.dispatch.png", "MEDIA_TYPE", "image/png");
    doReturn(descriptors).when(registry).getFunctionDescriptor(call);
    var language = new LanguageService();
    var field = LanguageService.class.getDeclaredField("componentRegistry");
    field.setAccessible(true);
    field.set(language, registry);
    var submitted = new ObservationImpl();
    submitted.mergeAnnotations(List.of(Annotation.of("colormap", "palette", "terrain", "center", 0)), 2);
    var mapper = JacksonConfiguration.newObjectMapper();
    var remote = (ObservationImpl) mapper.readValue(mapper.writeValueAsString(Observation.forTransport(submitted)), Observation.class);
    remote.mergeAnnotations(List.of(Annotation.of("colormap", "palette", "gray")), 1);
    var restored = mapper.readValue(mapper.writeValueAsString(remote), Observation.class);
    var scanner = mock(Storage.FloatScanner.class);
    when(scanner.get()).thenReturn(0f);
    assertEquals("ffe8e6b5:123", language.execute(call, mock(Scope.class), String.class,
        restored, scanner, Parameters.create("viewportX", 123)));
    assertEquals("keyed:456", language.execute(call, mock(Scope.class), String.class,
        restored, mock(Storage.KeyScanner.class), Parameters.create("viewportX", 456)));
  }
}
