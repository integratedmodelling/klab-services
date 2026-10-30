package org.integratedmodelling.klab.services.application.controllers;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.sun.net.httpserver.HttpServer;
import java.io.*;
import java.net.InetSocketAddress;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import org.integratedmodelling.common.services.client.BaseServiceClient;
import org.integratedmodelling.common.utils.Utils;
import org.integratedmodelling.klab.api.ServicesAPI;
import org.integratedmodelling.klab.api.collections.Parameters;
import org.integratedmodelling.klab.api.data.RuntimeAsset;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.knowledge.KlabAsset;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.scope.UserScope;
import org.integratedmodelling.klab.api.services.*;
import org.integratedmodelling.klab.api.services.resources.ResourceTransport;
import org.integratedmodelling.klab.api.services.runtime.extension.Extensions;
import org.integratedmodelling.klab.components.ComponentRegistry;
import org.integratedmodelling.klab.configuration.ServiceConfiguration;
import org.integratedmodelling.klab.runtime.libraries.ComponentIOLibrary;
import org.integratedmodelling.klab.services.application.ServiceNetworkedInstance;
import org.integratedmodelling.klab.services.application.security.EngineAuthorization;
import org.integratedmodelling.klab.services.base.BaseService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/** Covers URI-based export, previously omitted by the observation-query merge gate. */
class UriAssetExportTest {
  private static final String COMPONENT = "test.authority@1.0.0-SNAPSHOT";
  private static final String JAR = "application/java-archive";
  @TempDir Path temporary;
  private BaseService service;
  private UserScope scope;
  private Language language;
  private Language previousLanguage;
  private byte[] archive;
  private MockMvc mvc;
  private EngineAuthorization authorization;

  @BeforeEach void setup() throws Exception {
    var file = temporary.resolve("component.jar");
    try (var jar = new JarOutputStream(Files.newOutputStream(file))) {
      jar.putNextEntry(new JarEntry("fixture.txt"));
      jar.write(new byte[] {0, 1, (byte) 255});
      jar.closeEntry();
    }
    archive = Files.readAllBytes(file);
    scope = mock(UserScope.class);
    service = mock(BaseService.class, CALLS_REAL_METHODS);
    var registry = mock(ComponentRegistry.class);
    doReturn(registry).when(service).getComponentRegistry();
    var component = mock(Extensions.ComponentDescriptor.class);
    when(component.sourceArchive()).thenReturn(file.toFile());
    when(registry.getComponent(eq("test.authority"), any())).thenReturn(component);
    var capabilities = mock(ResourcesService.Capabilities.class);
    doReturn(capabilities).when(service).capabilities(scope);
    var schemas = new LinkedHashMap<String, List<ResourceTransport.Schema>>();
    for (var type : List.of(KlabAsset.KnowledgeClass.COMPONENT,
        KlabAsset.KnowledgeClass.NAMESPACE, KlabAsset.KnowledgeClass.OBSERVATION)) {
      var schema = ResourceTransport.Schema.create(type.name().toLowerCase() + ".export",
          ResourceTransport.Schema.Type.STREAM, type, "fixture");
      schema.getMediaTypes().add(JAR);
      schemas.put(schema.getSchemaId(), List.of(schema));
    }
    when(capabilities.getExportSchemata()).thenReturn(schemas);
    previousLanguage = ServiceConfiguration.INSTANCE.getService(Language.class);
    language = mock(Language.class);
    doAnswer(call -> {
      var asset = call.getArgument(3);
      if (asset instanceof Observation) return new ByteArrayInputStream(new byte[] {7, 8});
      var serviceCall = (org.integratedmodelling.klab.api.lang.ServiceCall) call.getArgument(0);
      return serviceCall.getUrn().equals("component.export")
          ? ComponentIOLibrary.exportComponentDirect(serviceCall.getParameters().getUnnamedArguments().getFirst().toString(), service, scope)
          : new ByteArrayInputStream(new byte[] {3, 4});
    }).when(language).execute(any(), eq(scope), eq(InputStream.class),
        nullable(RuntimeAsset.class), same(service), any(Parameters.class), isNull());
    ServiceConfiguration.INSTANCE.registerService(language, Language.class);
    var controller = new KlabServiceController();
    var instance = mock(ServiceNetworkedInstance.class);
    controller.instance = instance;
    when(instance.klabService()).thenReturn(service);
    authorization = mock(EngineAuthorization.class);
    when(authorization.getScope()).thenReturn(scope);
    mvc = MockMvcBuilders.standaloneSetup(controller)
        .setControllerAdvice(new KlabErrorHandler()).build();
  }

  @AfterEach void restoreLanguage() {
    if (previousLanguage != null)
      ServiceConfiguration.INSTANCE.registerService(previousLanguage, Language.class);
  }

  @Test void componentUrnExportsTheRegisteredArchiveWithoutAResolvedObservation() throws Exception {
    assertNull(service.resolveUrn(COMPONENT, KlabAsset.KnowledgeClass.COMPONENT, scope));
    try (var input = service.exportAsset(COMPONENT, KlabAsset.KnowledgeClass.COMPONENT,
        JAR, Parameters.create(), scope)) {
      assertArrayEquals(archive, input.readAllBytes());
    }
    verify(language).execute(any(), eq(scope), eq(InputStream.class), isNull(),
        same(service), any(Parameters.class), isNull());
  }

  @Test void otherUriExportStillUsesItsMatchingSchema() throws Exception {
    try (var input = service.exportAsset("test.namespace", KlabAsset.KnowledgeClass.NAMESPACE,
        JAR, Parameters.create(), scope)) {
      assertArrayEquals(new byte[] {3, 4}, input.readAllBytes());
    }
  }

  @Test void missingObservationIsRejectedBeforeSchemaDispatch() {
    assertThrows(IllegalArgumentException.class, () -> service.exportAsset("missing",
        KlabAsset.KnowledgeClass.OBSERVATION, JAR, Parameters.create(), scope));
    verifyNoInteractions(language);
  }

  @Test void validObservationStillExportsThroughResolvedAsset() throws Exception {
    var observation = mock(Observation.class);
    when(observation.getGeometry()).thenReturn(Geometry.create("1"));
    doReturn(observation).when(service).resolveUrn("known", KlabAsset.KnowledgeClass.OBSERVATION, scope);
    try (var input = service.exportAsset("known", KlabAsset.KnowledgeClass.OBSERVATION,
        JAR, Parameters.create(), scope)) {
      assertArrayEquals(new byte[] {7, 8}, input.readAllBytes());
    }
    verify(language).execute(any(), eq(scope), eq(InputStream.class), same(observation),
        same(service), any(Parameters.class), isNull());
  }

  @Test void componentCannotUseObservationOnlyMediation() {
    assertThrows(IllegalArgumentException.class, () -> service.exportAsset(COMPONENT,
        KlabAsset.KnowledgeClass.COMPONENT, JAR,
        Parameters.create("storageObservable", "test:quality"), scope));
    verifyNoInteractions(language);
  }

  @Test void sharedExportControllerRetainsArchiveBytesAndRejectsMissingObservation() throws Exception {
    var response = mvc.perform(get(ServicesAPI.EXPORT, "COMPONENT", COMPONENT)
        .principal(authorization).accept(JAR)).andReturn().getResponse();
    assertEquals(200, response.getStatus());
    assertEquals(JAR, response.getContentType());
    assertArrayEquals(archive, response.getContentAsByteArray());
    var missing = mvc.perform(get(ServicesAPI.EXPORT, "OBSERVATION", "missing")
        .principal(authorization).accept(JAR)).andReturn().getResponse();
    assertEquals(500, missing.getStatus());
  }

  @Test void productionDownloadClientAndControllerPreserveTheComponentArchive() throws Exception {
    var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    var failure = new AtomicReference<Throwable>();
    server.createContext("/export/", exchange -> {
      try {
        var response = mvc.perform(get(exchange.getRequestURI())
            .principal(authorization).header("Accept", exchange.getRequestHeaders().getFirst("Accept")))
            .andReturn().getResponse();
        var bytes = response.getContentAsByteArray();
        exchange.getResponseHeaders().set("Content-Type", response.getContentType());
        exchange.sendResponseHeaders(response.getStatus(), bytes.length);
        try (var out = exchange.getResponseBody()) { out.write(bytes); }
      } catch (Throwable error) {
        failure.set(error); exchange.sendResponseHeaders(500, -1); exchange.close();
      }
    });
    server.start();
    try (var http = Utils.Http.getClient("http://127.0.0.1:" + server.getAddress().getPort(), null)) {
      var client = mock(BaseServiceClient.class, CALLS_REAL_METHODS);
      var field = BaseServiceClient.class.getDeclaredField("client");
      field.setAccessible(true); field.set(client, http);
      try (var input = client.exportAsset(COMPONENT, KlabAsset.KnowledgeClass.COMPONENT,
          JAR, Parameters.create(), scope)) {
        assertArrayEquals(archive, input.readAllBytes());
      }
      assertNull(failure.get());
      var rejected = assertThrows(Utils.Http.Client.RequestFailure.class, () -> client.exportAsset("missing",
          KlabAsset.KnowledgeClass.OBSERVATION, JAR, Parameters.create(), scope));
      assertEquals(500, rejected.getStatus());
      assertNull(failure.get());
    } finally { server.stop(0); }
  }
}
