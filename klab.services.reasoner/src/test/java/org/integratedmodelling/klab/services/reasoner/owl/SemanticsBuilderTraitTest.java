package org.integratedmodelling.klab.services.reasoner.owl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.*;
import org.integratedmodelling.common.knowledge.ConceptImpl;
import org.integratedmodelling.klab.api.knowledge.*;
import org.integratedmodelling.klab.api.lang.kim.impl.KimConceptImpl;
import org.integratedmodelling.klab.api.scope.Scope;
import org.integratedmodelling.klab.api.services.ResourcesService;
import org.integratedmodelling.klab.services.reasoner.ReasonerService;
import org.integratedmodelling.klab.services.reasoner.internal.CoreOntology;
import org.integratedmodelling.klab.services.reasoner.internal.SemanticsBuilder;
import org.junit.jupiter.api.Test;

class SemanticsBuilderTraitTest {
  private ConceptImpl concept(String name) {
    var ret = new ConceptImpl(); ret.setUrn("test:" + name); ret.setNamespace("test");
    ret.getType().add(SemanticType.ATTRIBUTE); return ret;
  }
  private KimConceptImpl syntax(String name) {
    var ret = new KimConceptImpl(); ret.setName("test:" + name); ret.setUrn("test:" + name);
    ret.setType(EnumSet.of(SemanticType.ATTRIBUTE, SemanticType.PREDICATE)); return ret;
  }

  @Test void missingRootsDoNotCrashOrSilentlyRemoveOtherTraits() throws Exception {
    var reasoner = mock(ReasonerService.class); var resources = mock(ResourcesService.class);
    var scope = mock(Scope.class); when(scope.getService(ResourcesService.class)).thenReturn(resources);
    var original = syntax("Original"); var added = syntax("Added");
    var head = syntax("Entity"); head.setType(EnumSet.of(SemanticType.SUBJECT, SemanticType.COUNTABLE, SemanticType.OBSERVABLE)); head.getTraits().add(original);
    var originalConcept = concept("Original"); var addedConcept = concept("Added");
    when(resources.declareConcept("test:Added")).thenReturn(added);
    when(reasoner.resolveConcept("test:Original")).thenReturn(originalConcept);
    when(reasoner.resolveConcept("test:Added")).thenReturn(addedConcept);
    var builder = SemanticsBuilder.create(head, reasoner, scope);
    assertDoesNotThrow(() -> builder.withTrait(addedConcept));
    var field = SemanticsBuilder.class.getDeclaredField("syntax"); field.setAccessible(true);
    var composed = (KimConceptImpl) field.get(builder);
    assertEquals(List.of("test:Added", "test:Original"), composed.getTraits().stream().map(t -> t.getUrn()).toList());
    assertEquals(1, head.getTraits().size(), "The cached source must remain unchanged");

    // Once both roots are known, replacement within a trait family still works.
    var root = concept("Root");
    when(reasoner.lexicalRoot(originalConcept)).thenReturn(root);
    when(reasoner.lexicalRoot(addedConcept)).thenReturn(root);
    var replacement = SemanticsBuilder.create(head, reasoner, scope);
    replacement.withTrait(addedConcept);
    assertEquals(List.of("test:Added"), ((KimConceptImpl) field.get(replacement)).getTraits().stream().map(t -> t.getUrn()).toList());
  }

  @Test void missingRootWarnsAndStillAppliesThePredicateRestriction() {
    var scope = mock(Scope.class);
    var owl = new OWL(scope);
    owl.manager = org.semanticweb.owlapi.apibinding.OWLManager.createOWLOntologyManager();
    var ontology = owl.requireOntology("test");
    ontology.define(List.of(
        org.integratedmodelling.common.lang.Axiom.ClassAssertion("Entity", EnumSet.of(SemanticType.SUBJECT, SemanticType.COUNTABLE, SemanticType.OBSERVABLE)),
        org.integratedmodelling.common.lang.Axiom.ClassAssertion("Trait", EnumSet.of(SemanticType.ATTRIBUTE, SemanticType.PREDICATE))));
    owl.requireOntology("odo").define(List.of(org.integratedmodelling.common.lang.Axiom.ObjectPropertyAssertion("hasAttribute")));
    var reasoner = mock(ReasonerService.class); when(reasoner.owl()).thenReturn(owl);
    when(scope.getService(org.integratedmodelling.klab.api.services.Reasoner.class)).thenReturn(reasoner);
    when(reasoner.resolveConcept(anyString())).thenAnswer(call -> owl.getConcept(call.getArgument(0)));
    var head = syntax("Entity"); head.setType(EnumSet.of(SemanticType.SUBJECT, SemanticType.COUNTABLE, SemanticType.OBSERVABLE));
    head.getTraits().add(syntax("Trait"));
    var builder = SemanticsBuilder.create(head, reasoner, scope);
    var built = builder.buildConcept();
    assertFalse(built.is(SemanticType.NOTHING));
    assertTrue(built.getNotifications().stream().anyMatch(n ->
        n.getLevel() == org.integratedmodelling.klab.api.services.runtime.Notification.Level.Warning
            && n.getMessage().contains("test:Trait") && n.getMessage().contains("lexical root")));
    assertTrue(owl.getRestrictedClasses(built, owl.getProperty(CoreOntology.NS.HAS_ATTRIBUTE_PROPERTY))
        .contains(ontology.getConcept("Trait")));
    assertTrue(built.getNotifications().stream().noneMatch(n ->
        n.getLevel() == org.integratedmodelling.klab.api.services.runtime.Notification.Level.Error));
  }

  @Test void collectivePredicateApplicationsKeepTheirOwlIdentityAcrossRepeatedValidation() {
    var scope = mock(Scope.class);
    var owl = new OWL(scope);
    owl.manager = org.semanticweb.owlapi.apibinding.OWLManager.createOWLOntologyManager();
    var ontology = owl.requireOntology("test");
    ontology.define(List.of(
        org.integratedmodelling.common.lang.Axiom.ClassAssertion("Region", EnumSet.of(SemanticType.SUBJECT, SemanticType.COUNTABLE, SemanticType.OBSERVABLE)),
        org.integratedmodelling.common.lang.Axiom.ClassAssertion("Terrestrial", EnumSet.of(SemanticType.ATTRIBUTE, SemanticType.PREDICATE)),
        org.integratedmodelling.common.lang.Axiom.ClassAssertion("Freshwater", EnumSet.of(SemanticType.ATTRIBUTE, SemanticType.PREDICATE))));
    owl.requireOntology("odo").define(List.of(org.integratedmodelling.common.lang.Axiom.ObjectPropertyAssertion("hasAttribute")));
    var reasoner = mock(ReasonerService.class); when(reasoner.owl()).thenReturn(owl);
    when(scope.getService(org.integratedmodelling.klab.api.services.Reasoner.class)).thenReturn(reasoner);
    when(reasoner.resolveConcept(anyString())).thenAnswer(call -> owl.getConcept(call.getArgument(0)));
    for (int attempt = 0; attempt < 6; attempt++) {
      for (String name : List.of("Freshwater", "Terrestrial")) {
        var head = syntax("Region");
        head.setType(EnumSet.of(SemanticType.SUBJECT, SemanticType.COUNTABLE, SemanticType.OBSERVABLE));
        head.getTraits().add(syntax(name)); head.setCollective(true); head.resetDefinition();
        var built = SemanticsBuilder.create(head, reasoner, scope).buildConcept();
        assertNotNull(owl.getOWLClass(built));
        assertEquals(owl.getOWLClass(built), owl.getOWLClass(built.singular()));
        assertTrue(new OWLSemanticClauseSupport(owl).applicableTo(ontology.getConcept(name), built));
        assertTrue(new OWLSemanticClauseSupport(owl).applicableTo(ontology.getConcept(name), built.singular()));
        assertEquals(1, head.getTraits().size());
      }
    }
    assertEquals("test:Region", ontology.getConcept("Region").getUrn());
  }

  @Test void lexicalRootHandlesMissingConceptsAndCyclesWithoutHidingReachableRoots() {
    var reasoner = mock(ReasonerService.class);
    when(reasoner.lexicalRoot(any())).thenCallRealMethod();
    assertNull(reasoner.lexicalRoot(null));
    var first = concept("First"); var second = concept("Second"); var root = concept("Root");
    root.getMetadata().put(CoreOntology.NS.BASE_DECLARATION, "true");
    when(reasoner.parents(first)).thenReturn(List.of(second));
    when(reasoner.parents(second)).thenReturn(List.of(first));
    assertNull(reasoner.lexicalRoot(first));
    when(reasoner.parents(second)).thenReturn(List.of(first, root));
    assertSame(root, reasoner.lexicalRoot(first));
    first.getType().add(SemanticType.NOTHING);
    assertNull(reasoner.lexicalRoot(first));
  }
}
