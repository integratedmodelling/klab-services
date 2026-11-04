package org.integratedmodelling.common.knowledge;
import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import org.integratedmodelling.klab.api.lang.kim.*;
import org.integratedmodelling.klab.api.lang.kim.impl.*;
import org.integratedmodelling.klab.api.services.resources.*;
import org.integratedmodelling.klab.api.services.ResourcesService;
import org.junit.jupiter.api.Test;
class ModelCandidateSourcesTest {
  @Test void failedSupplierIsCapturedOncePerAttempt() {
    var calls=new java.util.concurrent.atomic.AtomicInteger();
    var failure=new IllegalStateException("offline");
    for(int attempt=0;attempt<2;attempt++)try(var session=ModelCandidateSources.openSession()) {
      for(int dependency=0;dependency<3;dependency++)
        assertSame(failure,assertThrows(IllegalStateException.class,()->ModelCandidateSources.freeze(
            "supplier",ModelCandidateRequest.from(null),()->{calls.incrementAndGet();throw failure;})));
      assertSame(failure,assertThrows(IllegalStateException.class,()->ModelCandidateSources.freeze(
          "supplier",new ModelCandidateRequest(1,List.of("different"),List.of(),List.of()),
          ()->{calls.incrementAndGet();throw failure;})));
    }
    assertEquals(2,calls.get());
  }
  @Test void projectionsAreDetachedAndScopedToAttemptAndSemanticBinding() {
    var scope=org.mockito.Mockito.mock(org.integratedmodelling.klab.api.scope.ContextScope.class);
    var semantic=new org.integratedmodelling.klab.api.services.reasoner.objects.SemanticCycleBinding(
        org.integratedmodelling.klab.api.services.reasoner.objects.SemanticCycleBinding.VERSION,"reasoner","session",3,"fingerprint","lease");
    var binding=new org.integratedmodelling.klab.api.services.runtime.objects.ResolutionCycleBinding(
        1,"cycle","context","runtime",true,semantic);
    org.mockito.Mockito.when(scope.getResolutionCycleBinding()).thenReturn(binding);
    var item=ModelCandidateSources.capture(List.of(namespace()),ModelCandidateRequest.from(null),"supplier",null)
        .getResults().iterator().next();
    var calls=new java.util.concurrent.atomic.AtomicInteger();
    java.util.function.Function<KimNamespace,org.integratedmodelling.klab.api.knowledge.Model> mapper=source->{
      calls.incrementAndGet();var model=new ModelImpl();model.setUrn(item.getResourceUrn());
      model.setCoverage(org.integratedmodelling.klab.api.geometry.Geometry.create("*"));
      model.getMetadata().put("original","value");return model;
    };
    try(var session=ModelCandidateSources.openSession()) {
      var first=ModelCandidateSources.project(item,scope,mapper);
      first.getMetadata().put("admission","consumer-specific");
      var second=ModelCandidateSources.project(item,scope,mapper);
      assertNotSame(first,second);assertEquals(1,calls.get());
      assertEquals("value",second.getMetadata().get("original"));
      assertFalse(second.getMetadata().containsKey("admission"));
      assertNotNull(second.getCoverage());
      org.mockito.Mockito.when(scope.getResolutionCycleBinding()).thenReturn(
          new org.integratedmodelling.klab.api.services.runtime.objects.ResolutionCycleBinding(
              1,"other","context","runtime",true,semantic));
      ModelCandidateSources.project(item,scope,mapper);assertEquals(2,calls.get());
      item.getMetadata().put(ModelCandidateSources.HASH,"invalid");
      assertThrows(IllegalArgumentException.class,()->ModelCandidateSources.project(item,scope,mapper));
    }
    item.getMetadata().put(ModelCandidateSources.HASH,
        org.integratedmodelling.klab.api.services.reasoner.objects.SemanticValidationRequest.sourceHash(
            item.getMetadata().get(ModelCandidateSources.SOURCE).toString()));
    try(var session=ModelCandidateSources.openSession()) {
      ModelCandidateSources.project(item,scope,mapper);assertEquals(3,calls.get());
    }
  }

  static KimNamespaceImpl namespace() {
    var source=new KimNamespaceImpl();source.setUrn("ns");source.setProjectName("project");source.setScope(KlabStatement.Scope.PUBLIC);
    source.setSourceCode("namespace ns; model test:Width named width;");source.setLastUpdateTimestamp(1);
    for(var visibility:KlabStatement.Scope.values()) {
      var model=new KimModelImpl();model.setUrn("ns."+visibility.name());model.setNamespace("ns");model.setProjectName("project");model.setScope(visibility);source.getStatements().add(model);
    }
    return source;
  }
  @Test void sourceSnapshotsAreDetachedAndCarryNoProviderAdmissionGrant() {
    var source=namespace();source.getMetadata().put(ResourcesService.NAMESPACE_ADMISSION_METADATA,"provider-grant");
    var captured=ModelCandidateSources.capture(List.of(source),new ModelCandidateRequest(1,List.of(),List.of(),List.of()),"supplier",null);
    assertEquals(1,captured.getResults().size());var item=captured.getResults().iterator().next();
    source.setSourceCode("changed");source.setLastUpdateTimestamp(2);
    var restored=ModelCandidateSources.read(item);assertEquals(1,restored.getLastUpdateTimestamp());assertFalse(restored.getMetadata().containsKey(ResourcesService.NAMESPACE_ADMISSION_METADATA));
    assertFalse(restored.getSourceCode().equals("changed"));
    item.getMetadata().put(ModelCandidateSources.HASH,"wrong");assertThrows(IllegalArgumentException.class,()->ModelCandidateSources.read(item));
  }
  @Test void lexicalNamespaceProjectScenarioAndInactiveRulesRemainIndependent() {
    var source=namespace();
    source.setScope(KlabStatement.Scope.PRIVATE);
    assertEquals(1,ModelCandidateSources.capture(List.of(source),ModelCandidateRequest.from(null),"s",null).getResults().size(),
        "A public model remains discoverable without lexical request constraints; namespace scope does not replace model visibility");
    assertEquals(3,ModelCandidateSources.capture(List.of(source),new ModelCandidateRequest(1,List.of("ns"),List.of("project"),List.of()),"s",null).getResults().size());
    source.setScenario(true);
    assertTrue(ModelCandidateSources.capture(List.of(source),new ModelCandidateRequest(1,List.of(),List.of(),List.of()),"s",null).isEmpty());
    assertEquals(1,ModelCandidateSources.capture(List.of(source),new ModelCandidateRequest(1,List.of(),List.of(),List.of("ns")),"s",null).getResults().size());
    source.setInactive(true);assertTrue(ModelCandidateSources.capture(List.of(source),new ModelCandidateRequest(1,List.of("ns"),List.of("project"),List.of()),"s",null).isEmpty());
  }
}
