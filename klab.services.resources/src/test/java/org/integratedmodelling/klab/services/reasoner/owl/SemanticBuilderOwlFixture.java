package org.integratedmodelling.klab.services.reasoner.owl;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import org.integratedmodelling.klab.api.knowledge.Concept;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.IRI;

/** Mock semantic lookup with real, isolated storage for builder provenance axioms. */
public final class SemanticBuilderOwlFixture {
  private SemanticBuilderOwlFixture() {}

  public static OWL create() throws Exception {
    var owl = mock(OWL.class);
    owl.manager = OWLManager.createOWLOntologyManager();
    var factory = owl.manager.getOWLDataFactory();
    var ontology = mock(Ontology.class);
    when(ontology.getOWLOntology()).thenReturn(owl.manager.createOntology());
    when(owl.getOntology(anyString())).thenReturn(ontology);
    when(owl.getOWLClass(any(Concept.class))).thenAnswer(call -> {
      Concept concept = call.getArgument(0);
      return factory.getOWLClass(IRI.create("urn:builder-test:"
          + URLEncoder.encode(concept.singular().getUrn(), StandardCharsets.UTF_8)));
    });
    when(owl.getProperty(anyString())).thenAnswer(call ->
        new Property(factory.getOWLObjectProperty(IRI.create("urn:builder-test:"
            + URLEncoder.encode(call.getArgument(0, String.class), StandardCharsets.UTF_8))), "test"));
    return owl;
  }

  public static boolean hasUniversalBearer(OWL owl) {
    return owl.getOntology("test").getOWLOntology().axioms().anyMatch(axiom ->
        axiom.annotations().anyMatch(annotation -> annotation.getProperty().getIRI()
            .equals(OWLSemanticClauseSupport.UNIVERSAL_BEARER)));
  }
}
