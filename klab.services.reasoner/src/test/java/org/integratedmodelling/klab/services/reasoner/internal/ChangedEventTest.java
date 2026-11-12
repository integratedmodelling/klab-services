package org.integratedmodelling.klab.services.reasoner.internal;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.util.*;
import org.integratedmodelling.klab.api.exceptions.KlabValidationException;
import org.integratedmodelling.klab.api.knowledge.*;
import org.integratedmodelling.klab.api.knowledge.impl.WorldviewImpl;
import org.integratedmodelling.klab.api.lang.UnarySemanticOperator;
import org.integratedmodelling.klab.api.lang.kim.impl.*;
import org.integratedmodelling.klab.runtime.language.*;
import org.integratedmodelling.languages.api.SemanticSyntax;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.semanticweb.owlapi.model.*;

class ChangedEventTest {
  @TempDir Path directory;
  private WorldviewImpl world() throws Exception {
    var ontology = new KimOntologyImpl();
    ontology.setUrn("k"); ontology.setProjectName("changed-test"); ontology.setSourceCode(" ".repeat(2048));
    for (var entry : List.of(Map.entry("Cover", SemanticSyntax.Type.IDENTITY),
        Map.entry("Forest", SemanticSyntax.Type.IDENTITY), Map.entry("Urban", SemanticSyntax.Type.IDENTITY),
        Map.entry("Other", SemanticSyntax.Type.IDENTITY), Map.entry("Temperature", SemanticSyntax.Type.TEMPERATURE),
        Map.entry("Body", SemanticSyntax.Type.SUBJECT))) {
      var value = new KimConceptStatementImpl();
      value.setUrn(entry.getKey()); value.setNamespace("k"); value.setOffsetInDocument(1);
      var method = LanguageAdapter.class.getDeclaredMethod("adaptSemanticType", SemanticSyntax.Type.class);
      method.setAccessible(true);
      @SuppressWarnings("unchecked") var types = (Set<SemanticType>) method.invoke(LanguageAdapter.INSTANCE, entry.getValue());
      value.setType(EnumSet.copyOf(types)); ontology.getStatements().add(value);
    }
    var world = new WorldviewImpl(); world.setUrn("k"); world.setWorldviewId("k"); world.setEmpty(false);
    world.setOntologies(new ArrayList<>(List.of(ontology)));
    var parser = new CapturedSemanticExpressionParser(world);
    for (var declaration : ontology.getStatements())
      if (Set.of("Forest", "Urban").contains(declaration.getUrn()))
        ((KimConceptStatementImpl) declaration).setDeclaredParent(parser.concept("k:Cover"));
    return world;
  }
  private SemanticEnvironment environment() throws Exception {
    var core = new TreeMap<String, byte[]>();
    for (var name : List.of("odo.owl", "klab.owl", "dc.owl"))
      try (var in = getClass().getResourceAsStream("/knowledge/" + name)) {
        assertNotNull(in); core.put(name, in.readAllBytes());
      }
    var factory = new SemanticEnvironmentFactory(directory, core);
    var input = factory.capture(world());
    assertEquals(CandidatePublicationCoordinator.BeanStatus.VALID, input.beanStatus(), input.diagnostics().toString());
    var result = factory.build(input, () -> false);
    assertEquals(CandidatePublicationCoordinator.LogicalStatus.VALID, result.logicalStatus(), result.diagnostics().toString());
    return result.state();
  }
  private OWLClass owlClass(SemanticEnvironment state, Concept concept) {
    return state.actor().owl().getOntology(concept.getNamespace()).getOWLOntology().classesInSignature()
        .filter(c -> c.getIRI().getRemainder().orElse("").equals(concept.getName())).findFirst().orElseThrow();
  }
  @Test void grammarAdapterAndSyntaxCopiesPreserveBothEndpoints() throws Exception {
    var parser = new CapturedSemanticExpressionParser(world());
    var syntax = (KimConceptImpl) parser.concept("changed (type of k:Cover) from k:Forest to k:Urban");
    assertEquals(UnarySemanticOperator.CHANGED, syntax.getSemanticModifier());
    assertTrue(syntax.is(SemanticType.EVENT));
    assertEquals("k:Forest", syntax.getChangedFrom().getUrn());
    assertEquals("k:Urban", syntax.getChangedTo().getUrn());
    for (var copy : List.of(syntax, (KimConceptImpl) KimConceptImpl.copyValue(syntax), syntax.mapSyntax(n -> {}))) {
      copy.resetDefinition();
      assertEquals(syntax.getUrn(), parser.concept(copy.getUrn()).getUrn());
      assertNotNull(copy.getChangedTo());
    }
    var mapper = org.integratedmodelling.common.data.jackson.JacksonConfiguration.newObjectMapper();
    var wire = mapper.readValue(mapper.writeValueAsBytes(syntax), org.integratedmodelling.klab.api.lang.kim.KimConcept.class);
    assertEquals(syntax.getUrn(), wire.getUrn());
    assertEquals("k:Forest", wire.getChangedFrom().getUrn());
    assertEquals("k:Urban", wire.getChangedTo().getUrn());
    var mapped = syntax.mapSyntax(n -> {
      if ("k:Urban".equals(n.getName())) n.setName("k:Other");
    });
    mapped.resetDefinition();
    assertEquals("k:Other", mapped.getChangedTo().getUrn());
    assertEquals("k:Urban", syntax.getChangedTo().getUrn());
    var validator = new org.integratedmodelling.klab.api.lang.kim.ObservableValidator();
    assertTrue(validator.validateConcept(syntax).isEmpty());
    var invalid = parser.concept("changed k:Temperature from k:Forest to k:Urban");
    assertTrue(validator.validateConcept(invalid).stream().anyMatch(n -> n.getMessage().contains("categorical")));
    assertThrows(IllegalArgumentException.class, () -> parser.concept("changed (type of k:Cover) from k:Forest"));
    assertThrows(IllegalArgumentException.class, () -> parser.concept("changed (type of k:Cover) to k:Urban"));
  }
  @Test void specializedFirstCreatesGenericEventAndRetainsDirectionAcrossBothBuilders() throws Exception {
    try (var state = environment()) {
      var actor = state.actor(); var owl = actor.owl();
      var transition = actor.resolveConcept("changed (type of k:Cover) from k:Forest to k:Urban");
      assertNotNull(transition); assertTrue(transition.is(SemanticType.EVENT)); assertTrue(transition.is(SemanticType.CHANGED));
      var generic = actor.resolveConcept("changed (type of k:Cover)");
      assertNotNull(generic); assertTrue(generic.is(SemanticType.EVENT));
      assertEquals(transition.getName(), actor.resolveConceptWithBuilder(transition.getUrn(), actor.serviceScope()).getName());
      assertEquals(transition.getName(), actor.resolveConcept(transition.getUrn()).getName());
      var reverse = actor.resolveConcept("changed (type of k:Cover) from k:Urban to k:Forest");
      assertNotEquals(transition.getName(), reverse.getName());
      var numerical = actor.resolveConcept("changed k:Temperature");
      assertTrue(numerical.is(SemanticType.EVENT)); assertFalse(numerical.is(SemanticType.PROCESS));
      owl.flushReasoner();
      var graph = owl.getOntology(transition.getNamespace()).getOWLOntology();
      var f = graph.getOWLOntologyManager().getOWLDataFactory();
      assertTrue(owl.isEntailed(f.getOWLSubClassOfAxiom(owlClass(state, transition), owlClass(state, generic))));
      for (var entry : List.of(Map.entry("changedFrom", "Forest"), Map.entry("changedTo", "Urban"))) {
        var property = f.getOWLObjectProperty(IRI.create("http://integratedmodelling.org/odo#" + entry.getKey()));
        assertTrue(owl.isEntailed(f.getOWLSubClassOfAxiom(owlClass(state, transition),
            f.getOWLObjectSomeValuesFrom(property, owlClass(state, owl.getConcept("k:" + entry.getValue()))))));
      }
      assertTrue(actor.satisfiable(transition)); assertTrue(owl.isConsistent());
    }
  }
  @Test void concurrentFirstRequestsReuseGenericAndSpecializedEvents() throws Exception {
    try (var state = environment(); var executor = java.util.concurrent.Executors.newFixedThreadPool(4)) {
      var owl = state.actor().owl();
      var categorical = owl.makeType(owl.getConcept("k:Cover"));
      var forest = owl.getConcept("k:Forest"); var urban = owl.getConcept("k:Urban");
      var tasks = new ArrayList<java.util.concurrent.Future<Concept>>();
      for (int i = 0; i < 8; i++)
        tasks.add(executor.submit(() -> owl.makeChanged(categorical, forest, urban)));
      var expected = tasks.getFirst().get();
      for (var task : tasks) assertEquals(expected.getName(), task.get().getName());
      var generic = owl.makeChanged(categorical);
      assertTrue(owl.getParents(expected).stream().anyMatch(p -> p.getName().equals(generic.getName())));
    }
  }

  @Test void invalidDirectCallsLeaveNoPartialEvent() throws Exception {
    try (var state = environment()) {
      var owl = state.actor().owl(); var graph = owl.getOntology("k").getOWLOntology();
      var categorical = owl.makeType(owl.getConcept("k:Cover"));
      var before = Set.copyOf(graph.getAxioms());
      var forest = owl.getConcept("k:Forest"); var urban = owl.getConcept("k:Urban");
      assertThrows(KlabValidationException.class, () -> owl.makeChanged(owl.getConcept("k:Body")));
      assertThrows(KlabValidationException.class, () -> owl.makeChanged(owl.getConcept("k:Temperature"), forest, urban));
      assertThrows(KlabValidationException.class, () -> owl.makeChanged(categorical, forest, null));
      assertThrows(KlabValidationException.class, () -> owl.makeChanged(categorical, forest, owl.getConcept("k:Other")));
      assertEquals(before, graph.getAxioms());
    }
  }
}

