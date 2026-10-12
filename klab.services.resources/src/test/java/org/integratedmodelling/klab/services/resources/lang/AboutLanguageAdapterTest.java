package org.integratedmodelling.klab.services.resources.lang;

import static org.junit.jupiter.api.Assertions.*;
import java.io.StringReader;
import java.util.List;
import org.eclipse.emf.ecore.*;
import org.eclipse.xtext.parser.IParser;
import org.integratedmodelling.languages.*;
import org.integratedmodelling.languages.api.*;
import org.integratedmodelling.languages.validation.*;
import org.integratedmodelling.languages.worldview.Ontology;
import org.integratedmodelling.klab.api.knowledge.SemanticType;
import org.junit.jupiter.api.Test;

class AboutLanguageAdapterTest {
  private String structure(org.integratedmodelling.klab.api.lang.kim.KimConcept c) {
    if (c == null) return "-";
    var head = c.getName() == null ? structure(c.getObservable()) : c.getName();
    var tree = new StringBuilder(head);
    if (c.isNegated()) tree.insert(0, "not:");
    for (var target : c.getAbout()) tree.append("[about:").append(structure(target)).append(']');
    if (c.getCompresent() != null) tree.append("[with:").append(structure(c.getCompresent())).append(']');
    if (c.getInherent() != null) tree.append("[of:").append(structure(c.getInherent())).append(']');
    for (var operand : c.getOperands()) tree.insert(0, '(').append(' ').append(c.getExpressionType())
        .append(' ').append(structure(operand)).append(')');
    return tree.toString();
  }

  @Test void retainedHeadsAndFillersRoundTripWithoutLosingNestedModifiers() {
    var expressions = new java.util.ArrayList<String>();
    for (var modifier : List.of("with", "of")) {
      expressions.add("(core:Knowledge " + modifier + " (core:Other about core:Earth)) " + modifier + " core:Third");
      expressions.add("core:Knowledge " + modifier + " ((core:Other about core:Earth) " + modifier + " core:Third)");
      expressions.add("(core:Knowledge about (core:Earth " + modifier + " core:Ocean)) " + modifier + " core:Third");
      expressions.add("((core:Knowledge about core:Earth) " + modifier + " core:Other) " + modifier + " core:Third");
      expressions.add("(core:Knowledge " + modifier + " (core:Other about core:Earth)) about core:Ocean");
    }
    for (var expression : expressions) {
      var original = domain(expression);
      var restored = domain(original.getUrn());
      assertEquals(structure(original), structure(restored), expression);
      assertEquals(original.getUrn(), restored.getUrn(), expression);
      var formatted = original.format(new org.integratedmodelling.klab.api.lang.kim.style.KimStyle.PlainCodeAppender());
      assertEquals(structure(original), structure(domain(formatted)), expression + " formatted: " + formatted);
    }
    var reproduction = domain(expressions.getFirst());
    assertEquals(1, reproduction.getObservable().getCompresent().getAbout().size());
    assertNotNull(reproduction.getCompresent());
    assertNotEquals(domain("core:Knowledge with core:Third").getUrn(), reproduction.getUrn());
    var diagnostic = domain("(core:Knowledge with (core:Other about core:Quality)) with core:Third", true);
    var diagnosticRestored = domain(diagnostic.getUrn(), true);
    assertEquals(structure(diagnostic), structure(diagnosticRestored));
    assertEquals(diagnostic.getUrn(), diagnosticRestored.getUrn());
  }

  private org.integratedmodelling.klab.api.lang.kim.KimConcept domain(String expression) {
    return domain(expression, false);
  }

  private org.integratedmodelling.klab.api.lang.kim.KimConcept domain(String expression, boolean expectDiagnostic) {
    var parser = new WorldviewStandaloneSetup().createInjectorAndDoEMFRegistration().getInstance(IParser.class);
    var parsed = parser.parse(new StringReader("ontology example in domain " + expression + " version 1.0;"));
    assertFalse(parsed.hasSyntaxErrors(), expression);
    var scope = new BasicObservableValidationScope() {
      @Override public ConceptDescriptor getConceptDescriptor(String name) {
        var parts = name.split(":");
        return new ConceptDescriptor(parts[0], parts[1], name.endsWith("Quality")
            ? SemanticSyntax.Type.GENERIC_QUALITY : SemanticSyntax.Type.DOMAIN, "", "", false, false);
      }
    };
    var diagnostics = new java.util.ArrayList<String>();
    var syntax = new OntologySyntaxImpl((Ontology) parsed.getRootASTElement(), scope) {
      @Override protected void logWarning(ParsedObject p, EObject o, EStructuralFeature f, String m) {}
      @Override protected void logError(ParsedObject p, EObject o, EStructuralFeature f, String m) {
        diagnostics.add(m);
        if (!expectDiagnostic) fail(expression + ": " + m);
      }
    };
    if (expectDiagnostic) assertTrue(diagnostics.stream().anyMatch(m -> m.contains("INTERPRETATION_TARGET_INVALID")), diagnostics.toString());
    return LanguageAdapter.INSTANCE.adaptOntology(syntax, "test", List.of(), 1L).getDomain();
  }

  @Test void compositeTargetRetainsGroupingAndCacheIdentity() {
    var target = domain("core:Knowledge about (core:Earth and core:Ocean)");
    var branch = domain("(core:Knowledge about core:Earth) and core:Ocean");
    assertNotEquals(target.getUrn(), branch.getUrn());
    var restored = domain(target.getUrn());
    assertTrue(restored.getOperands().isEmpty());
    assertEquals(1, restored.getAbout().size());
    assertEquals(1, restored.getAbout().getFirst().getOperands().size());
    assertEquals(org.integratedmodelling.klab.api.lang.kim.KimConcept.Expression.INTERSECTION,
        restored.getAbout().getFirst().getExpressionType());
    assertEquals(target.getUrn(), restored.getUrn());
  }

  @Test void branchAndWholeScopeStayDistinctThroughUrnAndTransport() throws Exception {
    var mapper = org.integratedmodelling.common.data.jackson.JacksonConfiguration.newObjectMapper();
    for (var logical : List.of("or", "and")) {
      var branch = domain("(core:Knowledge about core:Earth) " + logical + " core:Other");
      var whole = domain("(core:Knowledge " + logical + " core:Other) about core:Earth");
      assertNotEquals(branch.getUrn(), whole.getUrn());
      for (var original : List.of(branch, whole)) {
        var wire = mapper.readValue(mapper.writeValueAsString(original), org.integratedmodelling.klab.api.lang.kim.KimConcept.class);
        assertEquals(original.getUrn(), wire.getUrn());
        assertEquals(original.getUrn(), domain(wire.getUrn()).getUrn());
      }
      var reparsedBranch = domain(branch.getUrn());
      assertTrue(reparsedBranch.getAbout().isEmpty());
      assertEquals(1, reparsedBranch.getObservable().getAbout().size());
      assertEquals(1, reparsedBranch.getOperands().size());
      assertTrue(reparsedBranch.getOperands().getFirst().getAbout().isEmpty());
      var reparsedWhole = domain(whole.getUrn());
      assertEquals(1, reparsedWhole.getAbout().size());
      assertTrue(reparsedWhole.getOperands().isEmpty());
      assertEquals(1, reparsedWhole.getObservable().getOperands().size());
    }
    var negated = domain("core:Knowledge about (not core:Earth or core:Ocean)");
    var restored = domain(negated.getUrn());
    assertTrue(restored.getAbout().getFirst().isNegated()
        || restored.getAbout().getFirst().getObservable().isNegated());
    assertEquals(1, restored.getAbout().getFirst().getOperands().size());
    assertEquals(negated.getUrn(), restored.getUrn());
  }

  @Test void ontologyDomainAndSpecializationTransportIndependentAboutTargets() {
    var parser = new WorldviewStandaloneSetup().createInjectorAndDoEMFRegistration().getInstance(IParser.class);
    var parsed = parser.parse(new StringReader("ontology example in domain core:Knowledge about core:Earth about core:Ocean version 1.0; domain Specialized is core:Knowledge about core:Earth;"));
    assertFalse(parsed.hasSyntaxErrors());
    var scope = new BasicObservableValidationScope() {
      @Override public ConceptDescriptor getConceptDescriptor(String name) {
        var parts = name.split(":");
        return new ConceptDescriptor(parts[0], parts[1], SemanticSyntax.Type.DOMAIN, "", "", false, false);
      }
    };
    var syntax = new OntologySyntaxImpl((Ontology) parsed.getRootASTElement(), scope) {
      @Override protected void logWarning(ParsedObject p, EObject o, EStructuralFeature f, String m) {}
      @Override protected void logError(ParsedObject p, EObject o, EStructuralFeature f, String m) { fail(m); }
    };
    var ontology = LanguageAdapter.INSTANCE.adaptOntology(syntax, "test", List.of(), 1L);
    assertEquals(2, ontology.getDomain().getAbout().size());
    assertNull(ontology.getDomain().getInherent());
    assertTrue(ontology.getDomain().is(SemanticType.DOMAIN));
    assertFalse(ontology.getDomain().is(SemanticType.OBSERVABLE));
    assertEquals(1, ontology.getStatements().getFirst().getDeclaredParent().getAbout().size());
  }
}
