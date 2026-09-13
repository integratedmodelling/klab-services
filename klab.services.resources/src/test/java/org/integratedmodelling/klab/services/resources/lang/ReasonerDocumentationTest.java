package org.integratedmodelling.klab.services.resources.lang;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.EnumSet;
import java.util.List;
import org.integratedmodelling.common.knowledge.ObservableImpl;
import org.integratedmodelling.common.lang.Axiom;
import org.integratedmodelling.klab.api.knowledge.KlabAsset.KnowledgeClass;
import org.integratedmodelling.klab.api.knowledge.SemanticType;
import org.integratedmodelling.klab.services.reasoner.ReasonerService;
import org.integratedmodelling.klab.services.reasoner.owl.OWL;
import org.junit.jupiter.api.Test;
import org.semanticweb.owlapi.apibinding.OWLManager;

class ReasonerDocumentationTest {
  @Test void reasonerInfoDocumentsConceptAndObservableCounterparts() throws Exception {
    var owl = new OWL(null);
    var managerField = OWL.class.getDeclaredField("manager");
    managerField.setAccessible(true);
    managerField.set(owl, OWLManager.createOWLOntologyManager());
    var ontology = owl.requireOntology("audit");
    ontology.define(List.of(Axiom.ClassAssertion("Tree", EnumSet.of(SemanticType.SUBJECT))));
    var concept = ontology.getConcept("Tree");
    concept.getMetadata().put("custom", "Tree metadata");
    var observable = ObservableImpl.promote(concept, null);
    var reasoner = mock(ReasonerService.class, CALLS_REAL_METHODS);
    var owlField = ReasonerService.class.getDeclaredField("owl");
    owlField.setAccessible(true);
    owlField.set(reasoner, owl);
    doReturn(concept).when(reasoner).resolveConcept("audit:Tree");
    doReturn(observable).when(reasoner).resolveObservable("audit:Tree");
    for (var cls : List.of(KnowledgeClass.CONCEPT, KnowledgeClass.OBSERVABLE)) {
      String markdown = reasoner.info("audit:Tree", cls, String.class, null);
      assertTrue(markdown.contains("Semantic documentation"));
      assertTrue(markdown.contains("Tree metadata"));
      assertTrue(markdown.contains("SUBJECT"));
      assertTrue(markdown.contains("## OWL identity"));
    }
  }
}
