package org.integratedmodelling.klab.services.resources;

import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
class ResourceAdaptTest {
  @org.junit.jupiter.api.Test
  void preservesSemanticParsingAndAdaptsJson() {
    var service = mock(org.integratedmodelling.klab.services.resources.ResourcesProvider.class, CALLS_REAL_METHODS);
    var concept = mock(org.integratedmodelling.klab.api.lang.kim.KimConcept.class);
    doReturn(concept).when(service).declareConcept("test:Concept");
    assertSame(concept, service.parseAsset("test:Concept", org.integratedmodelling.klab.api.knowledge.KlabAsset.KnowledgeClass.CONCEPT));
    var workflow = new org.integratedmodelling.klab.api.services.resources.workflow.impl.WorkflowImpl();
    workflow.setId("review");
    doReturn(workflow).when(service).parseAsset("source", org.integratedmodelling.klab.api.knowledge.KlabAsset.KnowledgeClass.WORKFLOW);
    var json = new String(service.adapt("source", "application/json", org.integratedmodelling.klab.api.knowledge.KlabAsset.KnowledgeClass.WORKFLOW, null), java.nio.charset.StandardCharsets.UTF_8);
    assertTrue(json.contains("review"));
  }
}
