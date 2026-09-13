package org.integratedmodelling.klab.services.resources.lang;

import static org.junit.jupiter.api.Assertions.*;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EStructuralFeature;
import org.eclipse.xtext.parser.IParser;
import org.integratedmodelling.languages.OntologySyntaxImpl;
import org.integratedmodelling.languages.WorldviewStandaloneSetup;
import org.integratedmodelling.languages.api.ParsedObject;
import org.integratedmodelling.languages.validation.BasicObservableValidationScope;


import org.integratedmodelling.languages.worldview.Ontology;
import org.junit.jupiter.api.Test;

class WorldviewValidationTest {
  @Test void nestedAndTopLevelAliasesBothInstallDelegatesWithoutSubclassAxioms() throws Exception {
    var reasoner = org.mockito.Mockito.mock(
        org.integratedmodelling.klab.services.reasoner.ReasonerService.class,
        org.mockito.Mockito.CALLS_REAL_METHODS);
    var ontology = org.mockito.Mockito.mock(org.integratedmodelling.klab.services.reasoner.owl.Ontology.class);
    org.mockito.Mockito.when(ontology.getName()).thenReturn("test");
    var scope = org.mockito.Mockito.mock(org.integratedmodelling.klab.api.scope.Scope.class);
    var target = new org.integratedmodelling.klab.api.lang.kim.impl.KimConceptImpl();
    target.setName("test:Entity");
    var canonical = new org.integratedmodelling.common.knowledge.ConceptImpl();
    canonical.setUrn("test:Entity");
    org.mockito.Mockito.doReturn(canonical).when(reasoner).declareConcept(target);
    var alias = new org.integratedmodelling.klab.api.lang.kim.impl.KimConceptStatementImpl();
    alias.setUrn("Alias");
    alias.setNamespace("test");
    alias.setAlias(true);
    alias.setDeclaredParent(target);
    reasoner.build(alias, ontology, null, scope);
    var buildInternal = org.integratedmodelling.klab.services.reasoner.ReasonerService.class
        .getDeclaredMethod("buildInternal", org.integratedmodelling.klab.api.lang.kim.KimConceptStatement.class,
            org.integratedmodelling.klab.services.reasoner.owl.Ontology.class,
            org.integratedmodelling.klab.api.lang.kim.KimConceptStatement.class,
            org.integratedmodelling.klab.api.scope.Scope.class);
    buildInternal.setAccessible(true);
    buildInternal.invoke(reasoner, alias, ontology, alias, scope);
    org.mockito.Mockito.verify(ontology, org.mockito.Mockito.times(2)).addDelegateConcept(
        org.mockito.Mockito.eq("Alias"), org.mockito.Mockito.eq("test"), org.mockito.Mockito.any());
    org.mockito.Mockito.verify(ontology, org.mockito.Mockito.never()).add(org.mockito.Mockito.any());
  }

  @Test void rulesWorkOnSemanticBeansWithoutAParser() {
    var ontology = new org.integratedmodelling.klab.api.lang.kim.impl.KimOntologyImpl();
    ontology.setUrn("test");
    ontology.setProjectName("project");
    var alias = new org.integratedmodelling.klab.api.lang.kim.impl.KimConceptStatementImpl();
    alias.setUrn("Alias");
    alias.setAlias(true);
    alias.getDeclarationClauses().add(new org.integratedmodelling.klab.api.lang.kim.KimConceptStatement.DeclarationClause(
        "childrenClause", "has children Child", 30, 18));
    ontology.getStatements().add(alias);
    var visitor = new org.integratedmodelling.klab.runtime.language.KimOntologyVisitor();
    visitor.visit(ontology);
    var issue = visitor.getNotifications().stream()
        .filter(n -> n.getMessage().contains("additional semantic clauses")).findFirst().orElseThrow();
    assertEquals(30, issue.getLexicalContext().getOffsetInDocument());
    assertEquals(18, issue.getLexicalContext().getLength());
    assertEquals("test", issue.getLexicalContext().getDocumentUrn());
    assertEquals("project", issue.getLexicalContext().getProjectUrn());
  }

  @Test void undefinedReferencesAreReportedAfterAdaptationWithFocusedContext() throws Exception {
    var text = "ontology test in domain root version 1.0.0; thing Derived is absent:Missing;";
    var ontology = adapt(text);
    assertTrue(ontology.getNotifications().isEmpty());
    validate(ontology);
    var issue = ontology.getNotifications().stream()
        .filter(n -> n.getMessage().equals("Undefined concept: absent:Missing")).findFirst().orElseThrow();
    assertEquals(text.indexOf("absent:Missing"), issue.getLexicalContext().getOffsetInDocument());
    assertEquals("absent:Missing".length(), issue.getLexicalContext().getLength());
    assertEquals("test", issue.getLexicalContext().getDocumentUrn());
    assertEquals("project", issue.getLexicalContext().getProjectUrn());
  }

  @Test void qualifiedReferencesAndPlainAliasesAreAllowed() throws Exception {
    assertTrue(diagnostics("thing Entity; thing Derived is test:Entity; "
        + "thing Alias equals test:Entity; thing Core is core odo:Subject;").isEmpty());
  }

  @Test void referencesInUncompiledClausesUseTheWorkspaceResolver() {
    var ontology = adapt("ontology test in domain root version 1.0.0; "
        + "thing Entity inherits external:Trait;");
    var unresolved = new org.integratedmodelling.klab.runtime.language.KimOntologyVisitor(null,
        (urn, type, context) -> null);
    unresolved.visit(ontology);
    assertTrue(unresolved.getNotifications().stream()
        .anyMatch(n -> n.getMessage().equals("Undefined concept: external:Trait")));
    var resolved = new org.integratedmodelling.klab.runtime.language.KimOntologyVisitor(null,
        (urn, type, context) -> urn.equals("external:Trait") ? new Object() : null);
    resolved.visit(ontology);
    assertFalse(resolved.getNotifications().stream()
        .anyMatch(n -> n.getMessage().equals("Undefined concept: external:Trait")));
  }

  @Test void aliasesCannotHaveExtraClausesOrModifiedCoreTargets() throws Exception {
    assertTrue(diagnostics("thing Core is core odo:Subject has children Child;").stream()
        .anyMatch(n -> n.getMessage().contains("additional semantic clauses")));
    assertTrue(diagnostics("thing Core is core presence of odo:Subject;").stream()
        .anyMatch(n -> n.getMessage().contains("one qualified core concept")));
    assertTrue(diagnostics("thing Entity; thing Alias equals test:Entity within test:Entity;").stream()
        .anyMatch(n -> n.getMessage().contains("specialized with within")));
    assertTrue(diagnostics("thing Entity; thing Parent has children "
        + "(Alias equals test:Entity inherits test:Entity);").stream()
        .anyMatch(n -> n.getMessage().contains("additional semantic clauses")));
  }

  @Test void unqualifiedReferencesAreSyntaxErrors() {
    var parser = new WorldviewStandaloneSetup().createInjectorAndDoEMFRegistration().getInstance(IParser.class);
    assertTrue(parser.parse(new StringReader("ontology test in domain root version 1.0.0; "
        + "thing Entity; thing Derived is Entity;")).hasSyntaxErrors());
  }

  private List<org.integratedmodelling.klab.api.services.runtime.Notification> diagnostics(String declarations) throws Exception {
    var ontology = adapt("ontology test in domain root version 1.0.0; " + declarations);
    validate(ontology);
    return List.copyOf(ontology.getNotifications());
  }

  private void validate(org.integratedmodelling.klab.api.lang.kim.KimOntology ontology) throws Exception {
    var method = org.integratedmodelling.klab.services.resources.storage.WorkspaceManager.class
        .getDeclaredMethod("validateSemanticAsset", org.integratedmodelling.klab.api.lang.kim.KlabDocument.class,
            org.integratedmodelling.klab.runtime.language.KimObservableVisitor.Resolver.class);
    method.setAccessible(true);
    org.integratedmodelling.klab.runtime.language.KimObservableVisitor.Resolver resolver = (urn, type, context) -> null;
    method.invoke(null, ontology, resolver);
  }

  private org.integratedmodelling.klab.api.lang.kim.KimOntology adapt(String text) {
    var parser = new WorldviewStandaloneSetup().createInjectorAndDoEMFRegistration().getInstance(IParser.class);
    var parsed = parser.parse(new StringReader(text));
    assertFalse(parsed.hasSyntaxErrors());
    var bean = new OntologySyntaxImpl((Ontology) parsed.getRootASTElement(), new BasicObservableValidationScope()) {
      @Override protected void logWarning(ParsedObject t, EObject o, EStructuralFeature f, String m) {}
      @Override protected void logError(ParsedObject t, EObject o, EStructuralFeature f, String m) { fail(m); }
    };
    return LanguageAdapter.INSTANCE.adaptOntology(bean, "project", List.of(), 0L);
  }
}
