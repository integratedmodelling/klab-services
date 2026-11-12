package org.integratedmodelling.klab.api.services.reasoner.objects;

import static org.junit.jupiter.api.Assertions.*;
import java.util.Set;
import org.integratedmodelling.klab.api.knowledge.SemanticType;
import org.integratedmodelling.klab.api.knowledge.impl.WorldviewImpl;
import org.integratedmodelling.klab.api.lang.kim.impl.KimConceptStatementImpl;
import org.integratedmodelling.klab.api.lang.kim.impl.KimOntologyImpl;
import org.junit.jupiter.api.Test;

class WorldviewExplorerCatalogTest {
  @Test void preservesSourceHierarchyAndQualifiesNestedLocalNames() {
    var worldview = new WorldviewImpl();
    worldview.setUrn("test");
    var ontology = new KimOntologyImpl();
    ontology.setUrn("earth");
    var parent = new KimConceptStatementImpl();
    parent.setUrn("Landscape");
    parent.setType(Set.of(SemanticType.SUBJECT));
    var child = new KimConceptStatementImpl();
    child.setUrn("Forest");
    parent.getChildren().add(child);
    ontology.getStatements().add(parent);
    worldview.getOntologies().add(ontology);
    var catalog = WorldviewExplorerCatalog.capture(worldview);
    var declaration = catalog.ontologies().getFirst().children().getFirst();
    assertEquals("earth:Landscape", declaration.urn());
    assertEquals("earth:Forest", declaration.children().getFirst().urn());
    assertEquals(Set.of(SemanticType.SUBJECT), declaration.type());
    parent.getChildren().clear();
    assertEquals(1, declaration.children().size(), "Catalog is detached from mutable source lists");
    assertThrows(UnsupportedOperationException.class, () -> declaration.children().clear());
  }

  @Test void unavailableWorldviewHasAnEmptyCatalog() {
    var result = WorldviewExplorerCatalog.capture(null);
    assertTrue(result.ontologies().isEmpty());
    assertTrue(result.authorities().isEmpty());
  }

  @Test void exportsTypedDeclaredContractsWithoutSplittingGroupedRequirements() {
    var worldview = new WorldviewImpl();
    var ontology = new KimOntologyImpl(); ontology.setUrn("test");
    var statement = new KimConceptStatementImpl(); statement.setUrn("Container");
    var member = new org.integratedmodelling.klab.api.lang.kim.impl.KimConceptImpl(); member.setUrn("test:Member");
    var container = new org.integratedmodelling.klab.api.lang.kim.impl.KimConceptImpl(); container.setUrn("test:Container");
    var grouped = new org.integratedmodelling.klab.api.lang.kim.impl.KimConceptImpl(); grouped.setUrn("(test:A or test:B)");
    statement.getObservationalContainments().add(new org.integratedmodelling.klab.api.lang.kim.KimConceptStatement.ObservationalContainment(container, member));
    statement.getEmergenceTriggers().add(member);
    statement.getRequiredIdentities().add(grouped);
    statement.getRequiredRealms().add(member);
    ontology.getStatements().add(statement); worldview.getOntologies().add(ontology);
    var links = WorldviewExplorerCatalog.capture(worldview).declaredLinks();
    assertEquals(4, links.size());
    assertTrue(links.contains(new WorldviewExplorerCatalog.DeclaredLink("CONTAINS", "test:Container", "test:Member", "contains (declared)")));
    assertTrue(links.contains(new WorldviewExplorerCatalog.DeclaredLink("REQUIRED_IDENTITIES", "test:Container", "(test:A or test:B)", "requires identity (declared)")));
  }
}
