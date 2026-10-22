package org.integratedmodelling.klab.services.resources.storage;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.integratedmodelling.common.data.jackson.JacksonConfiguration;
import org.integratedmodelling.klab.api.knowledge.RelationshipAxes;
import org.integratedmodelling.klab.api.knowledge.SemanticType;
import org.integratedmodelling.klab.api.knowledge.organization.Workspace;
import org.integratedmodelling.klab.api.knowledge.organization.impl.*;
import org.integratedmodelling.klab.api.lang.kim.*;
import org.integratedmodelling.klab.runtime.language.WorldviewDeclarationRegistry;

class RelationshipAxesSourceTest {
  @TempDir Path directory;
  private static final String ROOT="""
      ontology imod in domain root version 1.0;
      abstract thing Subject is core odo:Subject;
      abstract relationship Relationship is core odo:Relationship;
      thing A;
      thing B;
      """;
  private KimOntology parse(String extra)throws Exception {
    var manager=WorkspaceDomainIngestionTest.manager(directory,Map.of("imod",ROOT+extra));
    return manager.getOntologies(false).iterator().next();
  }
  private KimConceptStatement named(KimOntology ontology,String name) {
    return ontology.getStatements().stream().filter(s->s.getUrn().equals(name)).findFirst().orElseThrow();
  }
  @Test void independentPrefixesSurviveParserAdapterAndWholeWorkspaceInterfaceTransport()throws Exception {
    var source=parse("""
        abstract relationship Neutral;
        structural directed relationship StructuralDirected links imod:A to imod:B;
        functional directed relationship FunctionalDirected links imod:A to imod:B;
        structural bond StructuralBond links imod:A to imod:B;
        functional bond FunctionalBond links imod:A to imod:B;
        """);
    assertTrue(source.getNotifications().stream().noneMatch(n->n.getLevel().severity>=3),source.getNotifications().toString());
    assertEquals(RelationshipAxes.Nature.UNSPECIFIED,named(source,"Neutral").getDeclaredRelationshipNature());
    assertEquals(RelationshipAxes.Orientation.UNSPECIFIED,named(source,"Neutral").getDeclaredRelationshipOrientation());
    var project=new ProjectImpl();project.setUrn("imod");project.setOntologies(List.of(source));
    var workspace=new WorkspaceImpl();workspace.setUrn("axes-workspace");workspace.setProjects(List.of(project));
    var mapper=JacksonConfiguration.newObjectMapper();
    var copy=mapper.readValue(mapper.writeValueAsBytes(workspace),Workspace.class);
    var copied=copy.getProjects().iterator().next().getOntologies().getFirst();
    for(String name:List.of("StructuralDirected","FunctionalDirected","StructuralBond","FunctionalBond")) {
      var original=named(source,name);var roundtrip=named(copied,name);
      assertEquals(original.getDeclaredRelationshipNature(),roundtrip.getDeclaredRelationshipNature());
      assertEquals(original.getDeclaredRelationshipOrientation(),roundtrip.getDeclaredRelationshipOrientation());
      assertEquals(name.startsWith("Structural")?RelationshipAxes.Nature.STRUCTURAL:RelationshipAxes.Nature.FUNCTIONAL,roundtrip.getDeclaredRelationshipNature());
      assertEquals(name.endsWith("Directed")?RelationshipAxes.Orientation.DIRECTED:RelationshipAxes.Orientation.BOND,roundtrip.getDeclaredRelationshipOrientation());
      assertEquals(1,roundtrip.getSubjectsLinked().size());
    }
  }
  @Test void inheritedChoiceIsEffectiveButNotLocallyAuthored()throws Exception {
    var source=parse("""
        abstract structural relationship Continuant;
        directed relationship Concrete is imod:Continuant links imod:A to imod:B;
        relationship Child is imod:Concrete;
        """);
    var registry=new WorldviewDeclarationRegistry(List.of(source));assertTrue(registry.errors().isEmpty(),registry.errors().toString());
    var child=named(source,"Child");assertEquals(RelationshipAxes.Nature.UNSPECIFIED,child.getDeclaredRelationshipNature());
    assertEquals(RelationshipAxes.Orientation.UNSPECIFIED,child.getDeclaredRelationshipOrientation());
    assertTrue(child.getType().containsAll(EnumSet.of(SemanticType.STRUCTURAL,SemanticType.UNIDIRECTIONAL)));
  }
  @Test void bareConcreteAndInheritedOppositionHaveSemanticDiagnostics()throws Exception {
    var source=parse("""
        relationship Missing;
        abstract functional directed relationship Parent;
        structural bond Opposite is imod:Parent;
        """);
    var errors=new WorldviewDeclarationRegistry(List.of(source)).errors().stream().map(n->n.getMessage()).toList();
    assertTrue(errors.stream().anyMatch(s->s.contains("AXES_UNRESOLVED")),errors.toString());
    assertTrue(errors.stream().anyMatch(s->s.contains("AXIS_CONFLICT")),errors.toString());
  }
  @Test void nestedBodyPreservesAuthoredOmissionThroughInterfaceTransport()throws Exception {
    var source=parse("abstract functional directed relationship Parent has children (Child \"A nested child with a body\");");
    var registry=new WorldviewDeclarationRegistry(List.of(source));assertTrue(registry.errors().isEmpty(),registry.errors().toString());
    var child=named(source,"Parent").getChildren().getFirst();
    var mapper=JacksonConfiguration.newObjectMapper();var copy=mapper.readValue(mapper.writeValueAsBytes(child),KimConceptStatement.class);
    assertEquals(RelationshipAxes.Nature.UNSPECIFIED,copy.getDeclaredRelationshipNature());
    assertEquals(RelationshipAxes.Orientation.UNSPECIFIED,copy.getDeclaredRelationshipOrientation());
    assertTrue(copy.getType().containsAll(EnumSet.of(SemanticType.FUNCTIONAL,SemanticType.UNIDIRECTIONAL)));
  }
}
