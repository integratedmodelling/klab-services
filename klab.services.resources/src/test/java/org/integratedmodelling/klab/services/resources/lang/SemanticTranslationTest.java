package org.integratedmodelling.klab.services.resources.lang;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.List;
import org.eclipse.xtext.util.Tuples;
import org.integratedmodelling.klab.api.knowledge.KlabAsset;
import org.integratedmodelling.klab.api.lang.kim.KimConcept;
import org.integratedmodelling.languages.api.SemanticSyntax;
import org.integratedmodelling.languages.validation.LanguageValidationScope.ConceptDescriptor;
import org.junit.jupiter.api.Test;

class SemanticTranslationTest {
  @Test
  void observableUrnPreservesCollectiveRestrictionAndSuffix() {
    for (boolean collective : List.of(false, true)) {
      var predicate = leaf("Environment", SemanticSyntax.Type.ATTRIBUTE);
      var member = leaf("Region", SemanticSyntax.Type.SUBJECT);
      when(predicate.getRestrictions()).thenReturn(List.of(Tuples.create(
          SemanticSyntax.BinaryOperator.OF, List.of(member), collective)));
      // The installed language encoder loses the restriction's collective flag.
      String encoded = "test:Environment of test:Region";
      when(predicate.encode()).thenReturn(encoded);
      var observable = mock(org.integratedmodelling.languages.api.ObservableSyntax.class);
      when(observable.getSemantics()).thenReturn(predicate);
      when(observable.encode()).thenReturn(encoded + " named environment");
      when(observable.getStatedName()).thenReturn("environment");
      var result = LanguageAdapter.INSTANCE.adaptObservable(
          observable, "test", "project", KlabAsset.KnowledgeClass.NAMESPACE);
      assertEquals("test:Environment of " + (collective ? "each " : "")
          + "test:Region named environment", result.getUrn());
      assertEquals(collective, result.getSemantics().getInherent().isCollective());
    }
  }

  @Test
  void genericQualityUsesLocalDeclarationFlags() throws Exception {
    var parent = leaf("Quality", SemanticSyntax.Type.LENGTH);
    when(parent.getObservable()).thenReturn(new SemanticSyntax.ConceptData(
        new ConceptDescriptor("test", "Quality", SemanticSyntax.Type.LENGTH,
            "Quality", "", true, false, true, true), null, false));
    var declaration = mock(org.integratedmodelling.languages.api.ConceptDeclarationSyntax.class);
    when(declaration.getName()).thenReturn("Child");
    when(declaration.getDeclaredType()).thenReturn(SemanticSyntax.Type.GENERIC_QUALITY);
    when(declaration.isGenericQuality()).thenReturn(true);
    when(declaration.getDeclaredParent()).thenReturn(parent);
    var method = LanguageAdapter.class.getDeclaredMethod("adaptConceptDefinition",
        org.integratedmodelling.languages.api.ConceptDeclarationSyntax.class, String.class, String.class);
    method.setAccessible(true);
    for (boolean explicitlyAbstract : List.of(false, true)) {
      when(declaration.isAbstract()).thenReturn(explicitlyAbstract);
      when(declaration.isSealed()).thenReturn(explicitlyAbstract);
      when(declaration.isSubjective()).thenReturn(explicitlyAbstract);
      var result = (org.integratedmodelling.klab.api.lang.kim.KimConceptStatement)
          method.invoke(LanguageAdapter.INSTANCE, declaration, "test", "project");
      assertEquals(explicitlyAbstract, result.isAbstract());
      for (var flag : List.of(org.integratedmodelling.klab.api.knowledge.SemanticType.ABSTRACT,
          org.integratedmodelling.klab.api.knowledge.SemanticType.SEALED,
          org.integratedmodelling.klab.api.knowledge.SemanticType.SUBJECTIVE)) {
        assertEquals(explicitlyAbstract, result.getType().contains(flag));
      }
      assertTrue(result.getType().contains(org.integratedmodelling.klab.api.knowledge.SemanticType.LENGTH));
    }
  }

  @Test
  void unaryOperandPredicatesAndOuterPredicatesHaveDifferentScopes() {
    var tree = leaf("Tree", SemanticSyntax.Type.SUBJECT);
    var attribute = leaf("Managed", SemanticSyntax.Type.ATTRIBUTE);
    when(attribute.getObservable()).thenReturn(new SemanticSyntax.ConceptData(
        new ConceptDescriptor("test", "Managed", SemanticSyntax.Type.ATTRIBUTE,
            "Managed", "", true, false, true, false), null, false));
    var presence = leaf("Tree", SemanticSyntax.Type.PRESENCE);
    when(presence.isLeafDeclaration()).thenReturn(false);
    var treeReference = tree.getObservable();
    var attributeReference = attribute.getObservable();
    when(presence.getObservable()).thenReturn(treeReference);
    when(presence.getUnaryOperator()).thenReturn(Tuples.pair(
        SemanticSyntax.UnaryOperator.PRESENCE, null));
    when(presence.getConceptReferences()).thenReturn(List.of(attributeReference));
    var inside = adapt(presence);
    assertTrue(inside.getTraits().isEmpty());
    assertEquals("test:Managed", inside.getObservable().getTraits().getFirst().getUrn());
    assertEquals("presence of test:Managed test:Tree", inside.getUrn());
    assertFalse(inside.is(org.integratedmodelling.klab.api.knowledge.SemanticType.ABSTRACT));
    assertFalse(inside.is(org.integratedmodelling.klab.api.knowledge.SemanticType.SUBJECTIVE));

    when(presence.getConceptReferences()).thenReturn(List.of());
    when(attribute.iterator()).thenAnswer(invocation -> List.of(attribute, presence).iterator());
    var outside = LanguageAdapter.INSTANCE.adaptSemantics(
        attribute, "test", "project", KlabAsset.KnowledgeClass.ONTOLOGY);
    assertEquals("test:Managed", outside.getTraits().getFirst().getUrn());
    assertTrue(outside.getObservable().getTraits().isEmpty());
    assertEquals("test:Managed presence of test:Tree", outside.getUrn());
    assertTrue(outside.is(org.integratedmodelling.klab.api.knowledge.SemanticType.ABSTRACT));
    assertTrue(outside.is(org.integratedmodelling.klab.api.knowledge.SemanticType.SUBJECTIVE));
  }

  @Test
  void referenceFlagsAndSelectorsSurviveAdaptationAndCopy() {
    for (String selector : List.of("any", "all", "no")) {
      var syntax = leaf("Thing", SemanticSyntax.Type.SUBJECT);
      when(syntax.getObservable()).thenReturn(new SemanticSyntax.ConceptData(
          new ConceptDescriptor("test", "Thing", SemanticSyntax.Type.SUBJECT,
              "Thing", "", true, false, true, true), selector, false));
      var result = adapt(syntax);
      var copy = (org.integratedmodelling.klab.api.lang.kim.impl.KimConceptImpl)
          result.removeComponents();
      assertTrue(copy.is(org.integratedmodelling.klab.api.knowledge.SemanticType.ABSTRACT));
      assertTrue(copy.is(org.integratedmodelling.klab.api.knowledge.SemanticType.SUBJECTIVE));
      assertTrue(copy.is(org.integratedmodelling.klab.api.knowledge.SemanticType.SEALED));
      assertTrue(copy.is(org.integratedmodelling.klab.api.knowledge.SemanticType.valueOf(
          selector.equals("no") ? "NONE" : selector.toUpperCase(java.util.Locale.ROOT))));
      copy.resetDefinition();
      assertEquals(selector + " test:Thing", copy.getUrn());
    }
  }

  @Test
  void emptySequencesAreRejectedAtTheBoundary() {
    var syntax = leaf("Thing", SemanticSyntax.Type.SUBJECT);
    when(syntax.iterator()).thenAnswer(invocation -> List.<SemanticSyntax>of().iterator());
    assertThrows(IllegalArgumentException.class, () -> LanguageAdapter.INSTANCE.adaptSemantics(
        syntax, "test", "project", KlabAsset.KnowledgeClass.ONTOLOGY));
  }

  @Test
  void linkingKeepsDistinctOrderedEndpoints() {
    var relationship = leaf("Connection", SemanticSyntax.Type.FUNCTIONAL_RELATIONSHIP);
    var source = leaf("Source", SemanticSyntax.Type.SUBJECT);
    var target = leaf("Target", SemanticSyntax.Type.SUBJECT);
    when(relationship.getRestrictions()).thenReturn(List.of(Tuples.create(
        SemanticSyntax.BinaryOperator.LINKING,
        List.of(source, target), false)));
    var result = adapt(relationship);
    assertEquals("test:Source", result.getRelationshipSource().getUrn());
    assertEquals("test:Target", result.getRelationshipTarget().getUrn());
    assertEquals("test:Connection linking test:Source to test:Target", result.getUrn());
  }

  @Test
  void intersectionKeepsItsConnectorWhenDefinitionIsRegenerated() {
    var left = leaf("Left", SemanticSyntax.Type.SUBJECT);
    var right = leaf("Right", SemanticSyntax.Type.SUBJECT);
    when(left.getRestrictions()).thenReturn(List.of(Tuples.create(
        SemanticSyntax.BinaryOperator.AND,
        List.of(right), false)));
    var result = adapt(left);
    assertEquals(KimConcept.Expression.INTERSECTION, result.getExpressionType());
    assertEquals("test:Left and test:Right", result.getUrn());
    result.resetDefinition();
    assertEquals("test:Left and test:Right", result.getUrn());
    var tokens = new java.util.ArrayList<String>();
    result.format(new org.integratedmodelling.klab.api.lang.kim.KlabStatement.CodeAppender<String>() {
      public void append(String token,
          org.integratedmodelling.klab.api.lang.kim.KlabStatement.LexicalRole role,
          Object... parameters) {
        tokens.add(token);
      }
      public String output() { return String.join(" ", tokens); }
    });
    assertTrue(String.join(" ", tokens).contains("and"));
    assertTrue(String.join(" ", tokens).contains("Left"));
    assertTrue(String.join(" ", tokens).contains("Right"));
  }

  @Test
  void domainHeadIsNotReintroducedAsItsOwnPredicate() {
    var domain = leaf("Physical", SemanticSyntax.Type.DOMAIN);
    when(domain.isLeafDeclaration()).thenReturn(false);
    var head = domain.getObservable();
    when(domain.getConceptReferences()).thenReturn(List.of(head));
    for (int attempt = 0; attempt < 6; attempt++) {
      var adapted = adapt(domain);
      assertEquals("test:Physical", adapted.getUrn());
      assertTrue(adapted.getTraits().isEmpty());
    }
  }

  @Test
  void emptyOntologyWithDomainKeepsAnAtomicDomainExpression() {
    var parser = new org.integratedmodelling.languages.WorldviewStandaloneSetup()
        .createInjectorAndDoEMFRegistration().getInstance(org.eclipse.xtext.parser.IParser.class);
    var source = "ontology decision version 1.0 using imod, physical, agency in domain imod:Physical;";
    var scope = new org.integratedmodelling.languages.validation.BasicObservableValidationScope() {
      @Override public ConceptDescriptor getConceptDescriptor(String name) {
        if (name.equals("imod:Physical")) return new ConceptDescriptor("imod", "Physical",
            SemanticSyntax.Type.DOMAIN, "Physical", "", false, false);
        return super.getConceptDescriptor(name);
      }
    };
    for (int attempt = 0; attempt < 6; attempt++) {
      var parsed = parser.parse(new java.io.StringReader(source));
      assertFalse(parsed.hasSyntaxErrors());
      var syntax = new org.integratedmodelling.languages.OntologySyntaxImpl(
          (org.integratedmodelling.languages.worldview.Ontology) parsed.getRootASTElement(), scope) {
        @Override protected void logWarning(org.integratedmodelling.languages.api.ParsedObject t,
            org.eclipse.emf.ecore.EObject o, org.eclipse.emf.ecore.EStructuralFeature f, String message) {}
        @Override protected void logError(org.integratedmodelling.languages.api.ParsedObject t,
            org.eclipse.emf.ecore.EObject o, org.eclipse.emf.ecore.EStructuralFeature f, String message) { fail(message); }
      };
      var ontology = LanguageAdapter.INSTANCE.adaptOntology(syntax, "project", List.of(), 0L);
      assertTrue(ontology.getStatements().isEmpty());
      assertEquals("imod:Physical", ontology.getDomain().getUrn());
      assertTrue(ontology.getDomain().getTraits().isEmpty());
    }
  }

  private org.integratedmodelling.klab.api.lang.kim.impl.KimConceptImpl adapt(SemanticSyntax syntax) {
    return LanguageAdapter.INSTANCE.adaptSemanticToken(
        syntax, "test", "project", KlabAsset.KnowledgeClass.ONTOLOGY);
  }

  private SemanticSyntax leaf(String name, SemanticSyntax.Type type) {
    var syntax = mock(SemanticSyntax.class);
    when(syntax.getType()).thenReturn(type);
    when(syntax.isLeafDeclaration()).thenReturn(true);
    when(syntax.getObservable()).thenReturn(new SemanticSyntax.ConceptData(
        new ConceptDescriptor("test", name, type, name, "", false, false), null, false));
    when(syntax.iterator()).thenAnswer(invocation -> List.of(syntax).iterator());
    return syntax;
  }
}
