package org.integratedmodelling.klab.services.resolver;

import static org.mockito.Mockito.*;
import org.integratedmodelling.klab.api.knowledge.Model;
import org.integratedmodelling.klab.api.lang.kim.*;
import org.integratedmodelling.klab.api.lang.kim.impl.*;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.services.*;
import org.integratedmodelling.klab.api.services.reasoner.objects.SemanticValidationResponse;

/** Admission prerequisites for tests whose subject is pipeline/ranking, not semantic validation. */
final class ModelAdmissionFixtures {
  static ResourcesService prepare(ContextScope scope, Reasoner reasoner) {
    var resources = scope.getService(ResourcesService.class);
    if (resources == null) {
      resources = mock(ResourcesService.class);
      when(scope.getService(ResourcesService.class)).thenReturn(resources);
    }
    doAnswer(invocation -> {
      var response = SemanticValidationResponse.forRequest(invocation.getArgument(0));
      response.setStatus(SemanticValidationResponse.Status.COMPLETE); response.setKnowledgeRevision(7);
      return response;
    }).when(reasoner).validateDocument(any(), any());
    doAnswer(invocation -> mock(KimObservable.class)).when(resources).declareObservable(nullable(String.class));
    return resources;
  }

  static void saved(ResourcesService resources, Model model, ContextScope scope) {
    var namespace = new KimNamespaceImpl(); namespace.setUrn(model.getNamespace());
    var source = new KimModelImpl(); source.setUrn(model.getUrn());
    namespace.getStatements().add(source);
    when(resources.retrieve(model.getNamespace(), KimNamespace.class, scope)).thenReturn(namespace);
  }
}
