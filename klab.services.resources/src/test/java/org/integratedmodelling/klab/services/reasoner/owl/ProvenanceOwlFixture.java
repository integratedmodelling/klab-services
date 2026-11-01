package org.integratedmodelling.klab.services.reasoner.owl;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.integratedmodelling.klab.api.knowledge.Concept;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.IRI;

/** Mock dispatch with real OWL storage for authored provenance axioms. */
public final class ProvenanceOwlFixture {
  private ProvenanceOwlFixture() {}

  public static OWL create() {
    try {
      var owl = mock(OWL.class);
      owl.manager = OWLManager.createOWLOntologyManager();
      var factory = owl.manager.getOWLDataFactory();
      var ontology = mock(Ontology.class);
      when(ontology.getOWLOntology()).thenReturn(owl.manager.createOntology());
      when(owl.getOntology(nullable(String.class))).thenReturn(ontology);
      when(owl.getOWLClass(any(Concept.class)))
          .thenAnswer(call -> {
            Concept concept = call.getArgument(0);
            return factory.getOWLClass(IRI.create("urn:test:" + UUID.nameUUIDFromBytes(
                concept.getUrn().getBytes(StandardCharsets.UTF_8))));
          });
      var property = mock(Property.class);
      when(property.getOWLEntity()).thenReturn(factory.getOWLObjectProperty(IRI.create("urn:test:inherent")));
      when(owl.getProperty(anyString())).thenReturn(property);
      return owl;
    } catch (org.semanticweb.owlapi.model.OWLOntologyCreationException e) {
      throw new AssertionError("Cannot initialize provenance fixture", e);
    }
  }
}
