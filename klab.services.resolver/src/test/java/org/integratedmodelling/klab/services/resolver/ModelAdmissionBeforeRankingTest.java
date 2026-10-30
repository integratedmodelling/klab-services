package org.integratedmodelling.klab.services.resolver;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.Map;
import org.integratedmodelling.klab.api.knowledge.Model;
import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.api.lang.kim.KimNamespace;
import org.integratedmodelling.klab.api.lang.kim.impl.KimModelImpl;
import org.integratedmodelling.klab.api.lang.kim.impl.KimNamespaceImpl;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.services.Reasoner;
import org.integratedmodelling.klab.api.services.ResourcesService;
import org.integratedmodelling.klab.api.services.reasoner.objects.SemanticValidationResponse;
import org.integratedmodelling.klab.api.services.runtime.Notification;
import org.junit.jupiter.api.Test;

class ModelAdmissionBeforeRankingTest {
  @Test void invalidAndUnavailableModelsAreRejectedBeforeAnyDistanceInteraction() {
    var scope = mock(ContextScope.class);
    var reasoner = mock(Reasoner.class);
    var resources = mock(ResourcesService.class);
    when(scope.getService(Reasoner.class)).thenReturn(reasoner);
    when(scope.getService(ResourcesService.class)).thenReturn(resources);
    var namespace = new KimNamespaceImpl(); namespace.setUrn("ns");
    var syntax = new KimModelImpl(); syntax.setUrn("ns.model");
    namespace.getStatements().add(syntax);
    when(resources.retrieve("ns", KimNamespace.class, scope)).thenReturn(namespace);
    var model = mock(Model.class);
    when(model.getNamespace()).thenReturn("ns"); when(model.getUrn()).thenReturn("ns.model");
    var request = mock(Observable.class);
    for (var status : SemanticValidationResponse.Status.values()) {
      when(reasoner.validateDocument(any(), same(scope))).thenAnswer(invocation -> {
        var response = SemanticValidationResponse.forRequest(invocation.getArgument(0));
        response.setStatus(status);
        response.setKnowledgeRevision(7);
        if (status == SemanticValidationResponse.Status.COMPLETE)
          response.getNotifications().add(Notification.error("unresolved dependency"));
        return response;
      });
      var ranking = new PrioritizerImpl(scope, null, Map.of(), request, null);
      assertEquals(Integer.MAX_VALUE, ranking.semanticDistance(model));
    }
    verify(reasoner, never()).semanticDistance(any(), any(), any());
    verify(reasoner, never()).semanticDistance(any(), any(), any(), anyLong());
  }
}
