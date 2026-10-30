package org.integratedmodelling.klab.services.resources.persistence;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.*;
import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.api.knowledge.observation.scale.Scale;
import org.integratedmodelling.klab.api.lang.kim.*;
import org.integratedmodelling.klab.api.lang.kim.impl.*;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.services.*;
import org.integratedmodelling.klab.api.services.resources.ModelDiscoveryMode;
import org.junit.jupiter.api.*;

/** Real document and SQL discovery; controlled semantic and installed-method boundaries. */
class PreparableModelDiscoveryTest {
  @BeforeAll static void geometry() { DocumentModelKboxTest.configureGeometry(); }

  @Test void documentPreparationDefersInstalledProofAndDistanceButDefaultRemainsExecutable() {
    exercise(false);
  }
  @Test void legacySqlPreparationDefersInstalledProofAndDistanceButDefaultRemainsExecutable() {
    exercise(true);
  }
  private void exercise(boolean sql) {
    var fixture=new DocumentModelKboxTest();
    when(fixture.service.serviceName()).thenReturn("preparable-"+UUID.randomUUID());
    var request=mock(Observable.class);var head=fixture.concept;
    when(request.getContextualization()).thenReturn(org.integratedmodelling.klab.api.knowledge.Contextualization.QUANTIFICATION);
    when(fixture.reasoner.coreObservable(any())).thenReturn(head);
    when(fixture.reasoner.resolving(head)).thenReturn(List.of(head));
    var context=mock(ContextScope.class);var runtime=mock(RuntimeService.class);
    when(context.getService(Reasoner.class)).thenReturn(fixture.reasoner);
    when(context.getService(RuntimeService.class)).thenReturn(runtime);
    when(context.withResolutionConstraints(any(org.integratedmodelling.klab.api.services.resolver.ResolutionConstraint[].class))).thenReturn(context);
    var saved=fixture.service.retrieve("ns",KimNamespace.class,context);
    ((KimNamespaceImpl)saved).setSourceCode("model test:Height using fixture.uninstalled();");
    var model=(KimModelImpl)saved.getStatements().getFirst();
    var computation=new org.integratedmodelling.common.lang.ContextualizableImpl();
    computation.setServiceCall(new org.integratedmodelling.common.lang.ServiceCallImpl("fixture.uninstalled"));
    model.getContextualization().add(computation);
    var scale=mock(Scale.class);
    try(ModelCatalog catalog=sql?ModelKbox.create(fixture.service):new DocumentModelKbox(fixture.service,new NitriteKboxStore(null))) {
      catalog.store(fixture.descriptor(),fixture.scope);
      clearInvocations(runtime,fixture.reasoner);
      assertEquals(1,catalog.queryModels(request,scale,null,List.of(),context,ModelDiscoveryMode.PREPARABLE).size());
      verifyNoInteractions(runtime);
      verify(fixture.reasoner,never()).semanticDistance(any(),any(),any(),anyLong());
      assertTrue(catalog.queryModels(request,scale,null,List.of(),context).isEmpty(),"Default matching cannot grant unavailable method shape");
      when(runtime.inspectMemberQualityInputs(any(),any())).thenReturn(
          org.integratedmodelling.klab.api.services.runtime.objects.MemberQualityConsumerInspection.known(Set.of()));
      when(fixture.reasoner.semanticDistance(any(),any(),any(),anyLong())).thenReturn(-1);
      assertTrue(catalog.queryModels(request,scale,null,List.of(),context).isEmpty(),"Default still performs full semantic matching");
      clearInvocations(fixture.reasoner,runtime);
      assertEquals(1,catalog.queryModels(request,scale,null,List.of(),context,ModelDiscoveryMode.PREPARABLE).size());
      verifyNoInteractions(runtime);verify(fixture.reasoner,never()).semanticDistance(any(),any(),any(),anyLong());
      catalog.clearNamespace("ns",fixture.scope);
      var hidden=fixture.descriptor();hidden.setScope(KlabStatement.Scope.PRIVATE);catalog.store(hidden,fixture.scope);
      assertTrue(catalog.queryModels(request,scale,null,List.of(),context,ModelDiscoveryMode.PREPARABLE).isEmpty(),"Preparation never bypasses namespace visibility");
      catalog.clearNamespace("ns",fixture.scope);hidden.setScope(KlabStatement.Scope.PUBLIC);catalog.store(hidden,fixture.scope);
      doReturn(null).when(fixture.reasoner).validateDocument(any(),any());
      assertTrue(catalog.queryModels(request,scale,null,List.of(),context,ModelDiscoveryMode.PREPARABLE).isEmpty(),"Unavailable source proof is not preparable");
    }
  }
}
