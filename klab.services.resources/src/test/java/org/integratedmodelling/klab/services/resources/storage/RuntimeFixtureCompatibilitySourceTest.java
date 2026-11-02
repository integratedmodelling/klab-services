package org.integratedmodelling.klab.services.resources.storage;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.integratedmodelling.klab.api.knowledge.RelationshipAxes;
import org.integratedmodelling.klab.api.lang.kim.KimConceptStatement;
import org.integratedmodelling.klab.api.lang.kim.KimOntology;
import org.integratedmodelling.klab.api.services.runtime.Notification;
import org.integratedmodelling.klab.runtime.language.WorldviewDeclarationRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Synthetic fixture compatibility only; never a migration of the shared worldview. */
class RuntimeFixtureCompatibilitySourceTest {
  @TempDir Path directory;
  private static final String ROOT = """
      ontology imod in domain root version 1.0;
      abstract thing Subject is core odo:Subject;
      abstract agent Agent is core odo:Agent;
      abstract process Process is core odo:Process;
      abstract relationship Relationship is core odo:Relationship;
      thing Region;
      process Activity;
      configuration Network;
      """;

  private KimOntology parse(String key, String declarations) throws Exception {
    var manager = WorkspaceDomainIngestionTest.manager(Files.createDirectories(directory.resolve(key)),
        Map.of("imod", ROOT + declarations));
    return manager.getOntologies(false).iterator().next();
  }
  private List<String> errors(KimOntology source) {
    var errors = new java.util.ArrayList<String>();
    for (var note : source.getNotifications())
      if (note.getLevel().severity >= Notification.Level.Error.severity) errors.add(note.getMessage());
    errors.addAll(new WorldviewDeclarationRegistry(List.of(source)).errors().stream().map(Notification::getMessage).toList());
    return errors;
  }
  private KimConceptStatement named(KimOntology source, String name) {
    return source.getStatements().stream().filter(s -> s.getUrn().equals(name)).findFirst().orElseThrow();
  }

  @Test void crossFamilyContainmentRemainsRejectedAndExplicitProcessDependencyIsValid() throws Exception {
    var original = parse("decision-original", "abstract process Decision is imod:Activity within imod:Agent;");
    assertTrue(errors(original).stream().anyMatch(e -> e.contains("DECLARATION_CLAUSE_TYPE_INVALID")), errors(original).toString());
    var variant = parse("decision-variant", "abstract process Decision is imod:Activity of imod:Agent;");
    assertTrue(errors(variant).isEmpty(), errors(variant).toString());
    assertTrue(named(variant, "Decision").getObservationalContainments().isEmpty());
  }

  @Test void concreteSocialRelationshipNeedsExplicitOrientationNotAnImplicitDefault() throws Exception {
    var original = parse("social-original", "structural relationship SocialConnection;");
    assertTrue(errors(original).stream().anyMatch(e -> e.contains("RELATIONSHIP_AXES_UNRESOLVED")), errors(original).toString());
    var variant = parse("social-variant", "structural bond SocialConnection;");
    assertTrue(errors(variant).isEmpty(), errors(variant).toString());
    assertEquals(RelationshipAxes.Nature.STRUCTURAL, named(variant, "SocialConnection").getDeclaredRelationshipNature());
    assertEquals(RelationshipAxes.Orientation.BOND, named(variant, "SocialConnection").getDeclaredRelationshipOrientation());
  }

  @Test void streamFixtureExplicitlySelectsFunctionalDirectedAxes() throws Exception {
    var original = parse("stream-original", "functional relationship StreamConnection links imod:Region to imod:Region;");
    assertTrue(errors(original).stream().anyMatch(e -> e.contains("RELATIONSHIP_AXES_UNRESOLVED")), errors(original).toString());
    var variant = parse("stream-variant", "functional directed relationship StreamConnection links imod:Region to imod:Region;");
    assertTrue(errors(variant).isEmpty(), errors(variant).toString());
    assertEquals(RelationshipAxes.Nature.FUNCTIONAL, named(variant, "StreamConnection").getDeclaredRelationshipNature());
    assertEquals(RelationshipAxes.Orientation.DIRECTED, named(variant, "StreamConnection").getDeclaredRelationshipOrientation());
  }

  @Test void configurationClauseOrderCannotHideIndependentHeadInherency() throws Exception {
    var relation = "functional directed relationship StreamConnection links imod:Region to imod:Region; ";
    var original = parse("network-original", relation + "configuration StreamNetwork applies to imod:Region is imod:Network of imod:StreamConnection;");
    assertTrue(errors(original).stream().anyMatch(e -> e.contains("INHERENCY_HEAD_INDEPENDENT")), errors(original).toString());
    var variant = parse("network-variant", relation + "configuration StreamNetwork is imod:Network of imod:StreamConnection applies to imod:Region;");
    assertTrue(errors(variant).isEmpty(), errors(variant).toString());
    var network = named(variant, "StreamNetwork");
    assertNull(network.getDeclaredParent().getInherent());
    assertEquals("imod:StreamConnection", network.getEmergenceTriggers().getFirst().getUrn());
    assertNull(network.getAppliesTo().getFirst().getTarget().getInherent());
  }

  @Test void fixtureCoreImportNamesTheActualCapturedVersionWithoutReplacingOwlBytes() throws Exception {
    var core = org.integratedmodelling.klab.services.reasoner.internal.CoreOntologyCatalog.bundledOdo();
    var digest = java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(core));
    assertEquals("496bc6634392d7e2eb2a6749bca6a1570333ea607b9b8e52ffaf5b619bcf2a57", digest);
    var manager = org.semanticweb.owlapi.apibinding.OWLManager.createOWLOntologyManager();
    var captured = manager.loadOntologyFromOntologyDocument(new java.io.ByteArrayInputStream(core));
    var capturedVersion = captured.getOntologyID().getVersionIRI().orElseThrow().toString();
    assertEquals("http://integratedmodelling.org/odo/0.2.0", capturedVersion);
    var legacy = "https://raw.githubusercontent.com/integratedmodelling/odo-im/master/releases/0.1.1/odo.owl";
    assertNotEquals(legacy, capturedVersion);
    for (var locator : List.of(legacy, capturedVersion)) {
      var source = "ontology imod version 1.0 in domain root with core \"" + locator + "\" as odo;\n"
          + ROOT.substring(ROOT.indexOf('\n') + 1);
      var workspace = WorkspaceDomainIngestionTest.manager(Files.createDirectories(directory.resolve(locator.equals(legacy) ? "core-original" : "core-variant")), Map.of("imod", source));
      var parsed = workspace.getOntologies(false).iterator().next();
      assertEquals("odo", parsed.getOwlImports().getFirst().getFirst());
      assertEquals(locator, parsed.getOwlImports().getFirst().getSecond());
    }
    assertArrayEquals(core, org.integratedmodelling.klab.services.reasoner.internal.CoreOntologyCatalog.bundledOdo());
  }
}
