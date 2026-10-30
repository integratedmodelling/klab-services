package org.integratedmodelling.klab.services.reasoner.owl;

import static org.junit.jupiter.api.Assertions.*;

import java.io.StringReader;
import java.nio.file.*;
import java.util.*;
import org.eclipse.xtext.parser.IParser;
import org.integratedmodelling.languages.WorldviewStandaloneSetup;
import org.integratedmodelling.languages.OntologySyntaxImpl;
import org.integratedmodelling.klab.api.knowledge.*;
import org.integratedmodelling.klab.api.knowledge.impl.WorldviewImpl;
import org.integratedmodelling.klab.api.lang.kim.KimOntology;
import org.integratedmodelling.klab.runtime.language.*;
import org.integratedmodelling.klab.services.reasoner.internal.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Exercises the authored hierarchy through the real parser, adapter and staged compiler. */
class LexicalRootAuthoringTest {
  @TempDir Path directory;

  static KimOntology parse(String source, WorldviewValidationScope scope) {
    var parser = new WorldviewStandaloneSetup().createInjectorAndDoEMFRegistration().getInstance(IParser.class);
    var parsed = parser.parse(new StringReader(source));
    assertFalse(parsed.hasSyntaxErrors(), parsed.getSyntaxErrors().toString());
    var errors = new ArrayList<String>();
    var syntax = new OntologySyntaxImpl((org.integratedmodelling.languages.worldview.Ontology) parsed.getRootASTElement(), scope) {
      @Override protected void logError(org.integratedmodelling.languages.api.ParsedObject target,
          org.eclipse.emf.ecore.EObject object, org.eclipse.emf.ecore.EStructuralFeature feature, String message) {
        errors.add(message);
      }
      @Override protected void logWarning(org.integratedmodelling.languages.api.ParsedObject target,
          org.eclipse.emf.ecore.EObject object, org.eclipse.emf.ecore.EStructuralFeature feature, String message) {}
    };
    var ontology = new LanguageAdapter().adaptOntology(syntax, "lexical-root-authoring", List.of(), 0L);
    assertTrue(errors.isEmpty(), errors.toString());
    return ontology;
  }

  @Test void parsedTopLevelAndNestedDeclarationsRetainBoundariesAcrossOntologyParents() throws Exception {
    var scope = new WorldviewValidationScope();
    var root = parse("""
        ontology imod in domain root version 1.0.0;
        abstract attribute AttributeFamily has children AttributeLeaf;
        abstract realm RealmFamily has children RealmLeaf;
        abstract identity IdentityFamily has children IdentityLeaf;
        abstract role RoleFamily has children RoleLeaf;
        """, scope);
    var extension = parse("""
        ontology extension using imod in domain root version 1.0.0;
        abstract attribute AttributeBoundary is imod:AttributeFamily has children AttributeNested;
        abstract realm RealmBoundary is imod:RealmFamily has children RealmNested;
        abstract identity IdentityBoundary is imod:IdentityFamily has children IdentityNested;
        abstract role RoleBoundary is imod:RoleFamily has children RoleNested;
        """, scope);
    var world = new WorldviewImpl(); world.setUrn("imod"); world.setWorldviewId("imod"); world.setEmpty(false);
    world.setOntologies(new ArrayList<>(List.of(root, extension)));
    var core = new TreeMap<String, byte[]>();
    for (var name : List.of("odo.owl", "klab.owl", "dc.owl"))
      try (var input = getClass().getResourceAsStream("/knowledge/" + name)) {
        assertNotNull(input, name); core.put(name, input.readAllBytes());
      }
    var factory = new SemanticEnvironmentFactory(directory.resolve("compiled"), core);
    var captured = factory.capture(world);
    assertEquals(CandidatePublicationCoordinator.BeanStatus.VALID, captured.beanStatus(), captured.diagnostics().toString());
    var built = factory.build(captured, () -> false);
    assertNotNull(built.state(), built.diagnostics().toString());
    assertEquals(CandidatePublicationCoordinator.LogicalStatus.VALID, built.logicalStatus(), built.diagnostics().toString());
    try (var state = built.state()) {
      var reasoner = state.actor();
      for (var name : List.of("Attribute", "Realm", "Identity", "Role")) {
        var family = reasoner.owl().getConcept("imod:" + name + "Family");
        var leaf = reasoner.owl().getConcept("imod:" + name + "Leaf");
        var boundary = reasoner.owl().getConcept("extension:" + name + "Boundary");
        var nested = reasoner.owl().getConcept("extension:" + name + "Nested");
        assertNotNull(family); assertNotNull(leaf); assertNotNull(boundary); assertNotNull(nested);
        assertSame(family, reasoner.lexicalRoot(family));
        assertSame(family, reasoner.lexicalRoot(leaf));
        assertSame(boundary, reasoner.lexicalRoot(boundary));
        assertSame(boundary, reasoner.lexicalRoot(nested));
        assertTrue(reasoner.is(boundary, family), "Semantic inheritance does not remove a lexical boundary");
        assertNotEquals(reasoner.lexicalRoot(family), reasoner.lexicalRoot(boundary));
      }
    }
  }

  @Test void parsedNestedAuthorityDeclarationRetainsItsExplicitWorldviewBinding() {
    var document = parse("""
        ontology biology in domain root version 1.0.0;
        abstract identity Taxon has children
          (Species requires authority TAXA {
            urn: "test.authority",
            codelists: { species: "taxonomy.species" }
          });
        """, new WorldviewValidationScope());
    var declaration = document.getStatements().getFirst().getChildren().getFirst();
    assertEquals("Species", declaration.getUrn());
    assertEquals("biology", declaration.getNamespace());
    assertEquals("TAXA", declaration.getAuthorityRequired());
    assertEquals(Map.of("species", "taxonomy.species"), declaration.getAuthorityParameters().get("codelists"));
    assertTrue(declaration.getType().contains(SemanticType.IDENTITY));
    assertTrue(KimWorldviewValidator.authorityErrors(declaration).isEmpty());
  }
}
