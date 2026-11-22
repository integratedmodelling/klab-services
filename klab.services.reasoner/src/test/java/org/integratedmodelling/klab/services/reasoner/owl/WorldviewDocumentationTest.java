package org.integratedmodelling.klab.services.reasoner.owl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Map;
import org.integratedmodelling.klab.api.data.Metadata;
import org.integratedmodelling.klab.api.knowledge.Worldview;
import org.integratedmodelling.klab.api.lang.kim.KimConceptStatement;
import org.integratedmodelling.klab.api.lang.kim.KimOntology;
import org.junit.jupiter.api.Test;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.IRI;

class WorldviewDocumentationTest {
  @Test
  void rendersNestedDeclarationsNotesAndTranslatedLogicWhenClassificationIsUnavailable() throws Exception {
    var worldview = mock(Worldview.class);
    var ontology = mock(KimOntology.class);
    var parent = mock(KimConceptStatement.class);
    var child = mock(KimConceptStatement.class);
    when(worldview.getOntologies()).thenReturn(List.of(ontology));
    when(ontology.getUrn()).thenReturn("land");
    when(ontology.getStatements()).thenReturn(List.of(parent));
    when(parent.getUrn()).thenReturn("Cover");
    when(parent.getChildren()).thenReturn(List.of(child));
    when(child.getUrn()).thenReturn("Forest");
    when(child.getDocstring()).thenReturn("**Field definition**");
    var metadata = mock(Metadata.class);
    when(child.getMetadata()).thenReturn(metadata);
    when(metadata.get(Metadata.IM_NOTES)).thenReturn("Canopy threshold under review.");
    when(child.getDeclarationClauses()).thenReturn(List.of(
        new KimConceptStatement.DeclarationClause("implies", "implies land:Habitat", 0, 20)));
    var owl = mock(OWL.class);
    var domain = mock(Ontology.class);
    when(owl.getOntology("land")).thenReturn(domain);
    var manager = OWLManager.createOWLOntologyManager();
    var translated = manager.createOntology();
    var factory = manager.getOWLDataFactory();
    manager.addAxiom(translated, factory.getOWLSubClassOfAxiom(
        factory.getOWLClass(IRI.create("urn:Forest")), factory.getOWLClass(IRI.create("urn:Cover"))));
    when(domain.getOWLOntology()).thenReturn(translated);
    var report = WorldviewDocumentation.render(worldview, 7, true, owl, Map.of("land", "hash"), List.of());
    for (var value : List.of("land:Forest", "Enclosing declaration", "land:Cover", "**Field definition**",
        "Canopy threshold under review.", "implies land:Habitat", "SubClassOf", "urn:Forest",
        "Knowledge revision 7", "Unavailable.", "hash")) assertTrue(report.contains(value), value);
  }
}
