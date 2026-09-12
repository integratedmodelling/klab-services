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
  void linkingKeepsDistinctOrderedEndpoints() {
    var relationship = leaf("Connection", SemanticSyntax.Type.FUNCTIONAL_RELATIONSHIP);
    when(relationship.getRestrictions()).thenReturn(List.of(Tuples.create(
        SemanticSyntax.BinaryOperator.LINKING,
        List.of(leaf("Source", SemanticSyntax.Type.SUBJECT),
            leaf("Target", SemanticSyntax.Type.SUBJECT)), false)));
    var result = adapt(relationship);
    assertEquals("test:Source", result.getRelationshipSource().getUrn());
    assertEquals("test:Target", result.getRelationshipTarget().getUrn());
    assertEquals("test:Connection linking test:Source to test:Target", result.getUrn());
  }

  @Test
  void intersectionKeepsItsConnectorWhenDefinitionIsRegenerated() {
    var left = leaf("Left", SemanticSyntax.Type.SUBJECT);
    when(left.getRestrictions()).thenReturn(List.of(Tuples.create(
        SemanticSyntax.BinaryOperator.AND,
        List.of(leaf("Right", SemanticSyntax.Type.SUBJECT)), false)));
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
