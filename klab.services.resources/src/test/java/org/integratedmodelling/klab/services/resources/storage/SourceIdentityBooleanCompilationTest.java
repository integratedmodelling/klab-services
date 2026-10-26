package org.integratedmodelling.klab.services.resources.storage;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.integratedmodelling.klab.api.knowledge.impl.WorldviewImpl;
import org.integratedmodelling.klab.api.services.runtime.Notification;
import org.integratedmodelling.klab.services.reasoner.internal.CandidatePublicationCoordinator;
import org.integratedmodelling.klab.services.reasoner.internal.SemanticEnvironmentFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.semanticweb.owlapi.model.*;

/** Actual source parser + production compiler: source booleans are not narrower generated classes. */
class SourceIdentityBooleanCompilationTest {
  @TempDir Path directory;

  private static final String SOURCE = """
      ontology imod in domain root version 1.0;
      abstract identity Identity is core odo:Identity;
      abstract thing Subject is core odo:Subject;
      abstract length Length is core odo:Length;
      abstract identity A;
      abstract identity B;
      abstract identity C;
      identity Both is (imod:A and imod:B);
      identity ImportedNarrow;
      thing ASubject is imod:Subject inherits imod:A;
      thing BothSubject is imod:Subject inherits imod:Both;
      thing SeparateSubject is imod:ASubject inherits imod:B;
      thing ImportedSubject is imod:Subject inherits imod:ImportedNarrow;
      length OrA is imod:Length of imod:ASubject requires identity (imod:A or imod:B);
      length AndOne is imod:Length of imod:BothSubject requires identity (imod:A and imod:B);
      length AndSeparate is imod:Length of imod:SeparateSubject requires identity (imod:A and imod:B);
      abstract length RequiredParent is imod:Length requires identity (imod:A or imod:B);
      length InheritedPass is imod:RequiredParent of imod:BothSubject requires identity (imod:B or imod:C);
      length InheritedMissing is imod:RequiredParent of imod:ASubject requires identity (imod:B or imod:C);
      length NestedPass is imod:Length of imod:BothSubject requires identity ((imod:A or imod:C) and imod:B);
      length NestedSeparate is imod:Length of imod:SeparateSubject requires identity ((imod:A or imod:C) and imod:B);
      length NestedMissing is imod:Length of imod:ASubject requires identity ((imod:A or imod:C) and imod:B);
      length RightGroupedPass is imod:Length of imod:ASubject requires identity (imod:A or (imod:C and imod:B));
      length NamedMissing is imod:Length of imod:ASubject requires identity imod:ImportedNarrow;
      length NamedPass is imod:Length of imod:ImportedSubject requires identity imod:ImportedNarrow;
      """;

  private OWLClass named(OWLOntology graph, String name) {
    return graph.classesInSignature().filter(c -> c.getIRI().getRemainder().orElse("").equals(name))
        .findFirst().orElseThrow();
  }

  private List<OWLClassExpression> requirements(OWLOntology graph, String name) {
    var owner = named(graph, name);
    return graph.getOWLOntologyManager().ontologies()
        .flatMap(o -> o.subClassAxiomsForSubClass(owner))
        .flatMap(a -> a.getNestedClassExpressions().stream())
        .filter(e -> e instanceof OWLObjectSomeValuesFrom some && !some.getProperty().isAnonymous()
            && some.getProperty().asOWLObjectProperty().getIRI().toString().equals("http://integratedmodelling.org/odo#requiresIdentity"))
        .map(e -> ((OWLObjectSomeValuesFrom) e).getFiller()).distinct().toList();
  }

  private String shape(org.integratedmodelling.klab.api.lang.kim.KimConcept concept) {
    if (concept == null) return "null";
    return "{name=" + concept.getName() + ", urn=" + concept.getUrn() + ", type=" + concept.getType()
        + ", expression=" + concept.getExpressionType() + ", modifier=" + concept.getSemanticModifier()
        + ", observable=" + shape(concept.getObservable())
        + ", operands=" + concept.getOperands().stream().map(this::shape).toList() + "}";
  }

  @Test void parsedSourceRetainsOrAndNestedInheritedClausesAndSingleWitnessScope() throws Exception {
    var manager = WorkspaceDomainIngestionTest.manager(Files.createDirectories(directory.resolve("sources")), Map.of("imod", SOURCE));
    var ontologies = new ArrayList<>(manager.getOntologies(false));
    assertEquals(1, ontologies.size());
    assertTrue(ontologies.getFirst().getNotifications().stream()
        .noneMatch(n -> n.getLevel().severity >= Notification.Level.Error.severity), ontologies.getFirst().getNotifications().toString());
    var world = new WorldviewImpl(); world.setUrn("imod"); world.setWorldviewId("imod"); world.setEmpty(false);
    world.setOntologies(ontologies);
    byte[] odo;
    var coreDirectory = System.getProperty("klab.test.core.directory");
    if (coreDirectory != null) odo = Files.readAllBytes(Path.of(coreDirectory, "odo.owl"));
    else try (var input = getClass().getResourceAsStream("/knowledge/odo.owl")) {
      assertNotNull(input, "The reasoner dependency must supply its bundled core"); odo = input.readAllBytes();
    }
    var factory = new SemanticEnvironmentFactory(directory.resolve("actors"), Map.of("odo.owl", odo));
    var captured = factory.capture(world);
    assertEquals(CandidatePublicationCoordinator.BeanStatus.VALID, captured.beanStatus(), captured.diagnostics().toString());
    var compiled = factory.build(captured, () -> false);
    assertEquals(CandidatePublicationCoordinator.LogicalStatus.VALID, compiled.logicalStatus(), compiled.diagnostics().toString());
    try (var state = compiled.state()) {
      var actor = state.actor();
      var graph = actor.owl().getOntology("imod").getOWLOntology();
      var evidence = new ArrayList<String>();
      for (var statement : ontologies.getFirst().getStatements()) if (!statement.getRequiredIdentities().isEmpty()) {
        evidence.add(statement.getUrn() + " syntax=" + statement.getRequiredIdentities().stream().map(this::shape).toList());
        evidence.add(statement.getUrn() + " compiled=" + requirements(graph, statement.getUrn()));
      }
      var report = Path.of("target/source-identity-boolean-compiler.txt"); Files.createDirectories(report.getParent());
      Files.write(report, evidence);
      assertInstanceOf(OWLObjectUnionOf.class, requirements(graph, "OrA").getFirst());
      assertInstanceOf(OWLObjectIntersectionOf.class, requirements(graph, "AndOne").getFirst());
      var nested = requirements(graph, "NestedPass").getFirst();
      assertInstanceOf(OWLObjectIntersectionOf.class, nested);
      assertTrue(((OWLObjectIntersectionOf) nested).operands().anyMatch(OWLObjectUnionOf.class::isInstance),
          "Nested source boolean lost its union: " + nested + "; syntax=" + evidence);
      var rightGrouped = requirements(graph, "RightGroupedPass").getFirst();
      assertInstanceOf(OWLObjectUnionOf.class, rightGrouped);
      assertTrue(((OWLObjectUnionOf) rightGrouped).operands().anyMatch(OWLObjectIntersectionOf.class::isInstance),
          "Right-grouped source boolean lost its intersection: " + rightGrouped + "; syntax=" + evidence);
      for (var name : List.of("OrA", "AndOne", "InheritedPass", "NestedPass", "RightGroupedPass", "NamedPass"))
        assertFalse(actor.resolveConcept("imod:" + name).isAbstract(), name + " must satisfy the complete source requirement");
      for (var name : List.of("AndSeparate", "InheritedMissing", "NestedSeparate", "NestedMissing", "NamedMissing", "RequiredParent"))
        assertTrue(actor.resolveConcept("imod:" + name).isAbstract(), name + " must retain its unresolved/explicit obligation");
      for(var name:List.of("NamedMissing","NamedPass")) {
        var raw=actor.owl().getOntology("imod").getConcept(name);
        assertFalse(raw.isAbstract(),"The authored declaration is concrete");
        var self=actor.closure(raw).stream().filter(c->c.getUrn().equals(raw.getUrn())).findFirst().orElseThrow();
        assertEquals(actor.resolveConcept("imod:"+name).isAbstract(),self.isAbstract(),"Service closure self must project identity obligations");
        var mapper=org.integratedmodelling.common.data.jackson.JacksonConfiguration.newObjectMapper();
        var transported=mapper.readValue(mapper.writeValueAsString(self),org.integratedmodelling.klab.api.knowledge.Concept.class);
        assertEquals(self.isAbstract(),transported.isAbstract());
        assertEquals(self.getType(),transported.getType());
        assertFalse(raw.isAbstract(),"Closure/transport must not mutate canonical declaration flags");
      }

      // An imported-style necessary union bound is not an equivalence. Only the explicit
      // source boolean syntax is expanded by requirement translation, never this named category.
      var f = graph.getOWLOntologyManager().getOWLDataFactory();
      var imported = named(graph, "ImportedNarrow");
      var union = f.getOWLObjectUnionOf(named(graph, "A"), named(graph, "B"));
      graph.getOWLOntologyManager().addAxiom(graph, f.getOWLSubClassOfAxiom(imported, union));
      actor.owl().flushReasoner();
      assertTrue(actor.owl().isEntailed(f.getOWLSubClassOfAxiom(imported, union)));
      assertFalse(actor.owl().isEntailed(f.getOWLSubClassOfAxiom(named(graph, "A"), imported)));
      assertEquals(List.of(imported), requirements(graph, "NamedMissing"), "A named requirement must stay named");
      assertTrue(actor.resolveConcept("imod:NamedMissing").isAbstract(), "A cannot be promoted into the narrower named category");
      assertFalse(actor.resolveConcept("imod:NamedPass").isAbstract());
    }
  }
}
