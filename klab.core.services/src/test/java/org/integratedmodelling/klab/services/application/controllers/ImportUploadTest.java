package org.integratedmodelling.klab.services.application.controllers;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import org.integratedmodelling.klab.api.knowledge.KlabAsset;
import org.integratedmodelling.klab.api.knowledge.Urn;
import org.integratedmodelling.klab.api.services.KlabService;
import org.integratedmodelling.klab.api.services.resources.ResourceSet;
import org.integratedmodelling.klab.api.services.resources.ResourceTransport;
import org.integratedmodelling.klab.api.scope.Scope;
import org.integratedmodelling.klab.services.JobManager;
import org.integratedmodelling.klab.services.application.ServiceNetworkedInstance;
import org.integratedmodelling.klab.services.application.security.EngineAuthorization;
import org.integratedmodelling.klab.services.base.BaseService;
import org.integratedmodelling.klab.services.scopes.ServiceUserScope;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

class ImportUploadTest {
  @Test
  void acceptsAnUploadWithoutAFilenameExtensionAndDispatchesItToTheAdvertisedSchema() throws Exception {
    var schema = ResourceTransport.Schema.create("example.upload", ResourceTransport.Schema.Type.STREAM,
        KlabAsset.KnowledgeClass.PROJECT, "Fixture import format");
    var capabilities = mock(KlabService.ServiceCapabilities.class);
    when(capabilities.getImportSchemata()).thenReturn(Map.of("example", List.of(schema)));
    var scope = mock(ServiceUserScope.class);
    var jobs = new JobManager();
    when(scope.getJobManager()).thenReturn(jobs);
    var authorization = mock(EngineAuthorization.class);
    when(authorization.getScope()).thenReturn(scope);
    var service = mock(BaseService.class);
    when(service.capabilities(scope)).thenReturn(capabilities);
    var upload = new AtomicReference<Path>();
    when(service.importAsset(eq(schema), any(), eq(Urn.UNDEFINED_URN), eq(scope))).thenAnswer(call -> {
      var asset = call.getArgument(1, ResourceTransport.Schema.Asset.class);
      upload.set(asset.getFile().toPath());
      return CompletableFuture.completedFuture(new ResourceSet());
    });
    var controller = new KlabServiceController();
    controller.instance = mock(ServiceNetworkedInstance.class);
    doReturn(service).when(controller.instance).klabService();

    byte[] bytes = {1, 2, 3};
    var file = new MockMultipartFile("file", "download", "application/octet-stream", bytes);
    try {
      long job = controller.uploadAsset(schema.getSchemaId(), Urn.UNDEFINED_URN, file, authorization);
      assertEquals(Scope.Status.FINISHED, jobs.status(job).getStatus());
      assertArrayEquals(bytes, Files.readAllBytes(upload.get()));
    } finally {
      if (upload.get() != null) Files.deleteIfExists(upload.get());
    }
  }
}
