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
  @Test void missingParentTypeProducesAnInvalidDescriptorInsteadOfCrashingRegistration() {
    var parent = org.mockito.Mockito.mock(org.integratedmodelling.languages.api.SemanticSyntax.class);
    org.mockito.Mockito.when(parent.iterator()).thenAnswer(call -> List.of(parent).iterator());
    var declaration = org.mockito.Mockito.mock(org.integratedmodelling.languages.api.ConceptDeclarationSyntax.class);
    org.mockito.Mockito.when(declaration.getNamespace()).thenReturn("test");
    org.mockito.Mockito.when(declaration.getName()).thenReturn("Incomplete");
    org.mockito.Mockito.when(declaration.isGenericQuality()).thenReturn(true);
    org.mockito.Mockito.when(declaration.getDeclaredParent()).thenReturn(parent);
    for (var scope : List.of(new BasicObservableValidationScope(), new WorldviewValidationScope())) {
      assertEquals(org.integratedmodelling.languages.api.SemanticSyntax.Type.NOTHING,
          scope.createConceptDescriptor(declaration).mainType());
    }
  }

  @Test void coreGenericQualityUsesItsDeclaredTypeDuringStartup() {
    for (var scope : List.of(new BasicObservableValidationScope(), new WorldviewValidationScope())) {
      var ontology = adapt("ontology test in domain root version 1.0.0; "
          + "abstract quality Quality is core odo:Quality; quality Derived is test:Quality;", scope);
      assertEquals(org.integratedmodelling.languages.api.SemanticSyntax.Type.GENERIC_QUALITY,
          scope.getConceptDescriptor("test:Quality").mainType());
      assertEquals(org.integratedmodelling.languages.api.SemanticSyntax.Type.GENERIC_QUALITY,
          scope.getConceptDescriptor("odo:Quality").mainType());
      assertTrue(ontology.getStatements().getFirst().getType()
          .contains(org.integratedmodelling.klab.api.knowledge.SemanticType.QUALITY));
      assertEquals("odo:Quality", ontology.getStatements().getFirst().getUpperConceptDefined());
    }
  }

  @Test void countTypesPropagateThroughDeclarationsReferencesAndReloads() {
    var scope = new WorldviewValidationScope();
    var ontology = adapt("ontology test in domain root version 1.0.0; event Event; "
        + "abstract quality Frequency is count of test:Event; "
        + "quality Derived is test:Frequency; length Wavelength decreases with test:Frequency;", scope);
    for (var statement : ontology.getStatements().subList(1, 3)) {
      assertTrue(statement.isGenericQuality());
      assertTrue(statement.getType().containsAll(java.util.EnumSet.of(
          org.integratedmodelling.klab.api.knowledge.SemanticType.NUMEROSITY,
          org.integratedmodelling.klab.api.knowledge.SemanticType.QUALITY,
          org.integratedmodelling.klab.api.knowledge.SemanticType.OBSERVABLE,
          org.integratedmodelling.klab.api.knowledge.SemanticType.QUANTIFIABLE)));
      assertFalse(statement.getType().contains(org.integratedmodelling.klab.api.knowledge.SemanticType.EVENT));
      assertEquals(org.integratedmodelling.languages.api.SemanticSyntax.Type.NUMEROSITY,
          scope.getConceptDescriptor("test:" + statement.getUrn()).mainType());
    }
    var reloaded = new WorldviewValidationScope();
    reloaded.addNamespace(ontology);
    assertEquals(scope.getConceptDescriptor("test:Frequency").mainType(),
        reloaded.getConceptDescriptor("test:Frequency").mainType());
  }

  @Test void qualityDeclarationsCanInhereToProcessesAndEvents() throws Exception {
    assertTrue(diagnostics("process Flow; event Flood; length Length; "
        + "length FlowLength is test:Length of test:Flow; "
        + "length FloodLength is test:Length of test:Flood;").isEmpty());
  }

  @Test void editingCannotResolveForwardOrDeletedDeclarationsFromThePreviousSnapshot() throws Exception {
    var prefix = "ontology test in domain root version 1.0.0; ";
    var warm = new WorldviewValidationScope();
    warm.addNamespace(adapt(prefix + "thing Later; thing Earlier is test:Later;"));
    var imported = adapt("ontology external in domain root version 1.0.0; thing Imported;");
    warm.addNamespace(imported);
    var text = prefix + "thing Earlier is test:Later; thing Later;";
    // Even a bean parsed against stale descriptors must fail the independent order check.
    var stale = adapt(text, warm);
    validate(stale);
    var issue = stale.getNotifications().stream().filter(n -> n.getMessage().contains("before its declaration"))
        .findFirst().orElseThrow();
    assertEquals(text.indexOf("test:Later"), issue.getLexicalContext().getOffsetInDocument());
    for (int i = 0; i < 3; i++) {
      var isolated = warm.withoutNamespace("test");
      assertEquals(org.integratedmodelling.languages.api.SemanticSyntax.Type.VOID,
          isolated.getConceptDescriptor("test:Later").mainType());
      assertEquals(org.integratedmodelling.languages.api.SemanticSyntax.Type.SUBJECT,
          isolated.getConceptDescriptor("external:Imported").mainType());
      var backward = adapt(prefix + "thing Later; thing Earlier is test:Later;", isolated);
      validate(backward);
      assertTrue(backward.getNotifications().isEmpty());
      var deleted = adapt(prefix + "thing Earlier is test:Later;", warm.withoutNamespace("test"));
      validate(deleted);
      assertTrue(deleted.getNotifications().stream().anyMatch(n -> n.getMessage().contains("Undefined concept: test:Later")));
    }
    assertEquals(org.integratedmodelling.languages.api.SemanticSyntax.Type.SUBJECT,
        warm.getConceptDescriptor("test:Later").mainType());
  }

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
    return adapt(text, new BasicObservableValidationScope());
  }

  private org.integratedmodelling.klab.api.lang.kim.KimOntology adapt(String text, BasicObservableValidationScope scope) {
    var parser = new WorldviewStandaloneSetup().createInjectorAndDoEMFRegistration().getInstance(IParser.class);
    var parsed = parser.parse(new StringReader(text));
    assertFalse(parsed.hasSyntaxErrors());
    var bean = new OntologySyntaxImpl((Ontology) parsed.getRootASTElement(), scope) {
      @Override protected void logWarning(ParsedObject t, EObject o, EStructuralFeature f, String m) {}
      @Override protected void logError(ParsedObject t, EObject o, EStructuralFeature f, String m) { fail(m); }
    };
    return LanguageAdapter.INSTANCE.adaptOntology(bean, "project", List.of(), 0L);
  }
}
