package org.integratedmodelling.klab.services.resources.storage;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.util.*;
import org.integratedmodelling.klab.api.knowledge.impl.WorldviewImpl;
import org.integratedmodelling.klab.api.lang.kim.KimNamespace;
import org.integratedmodelling.klab.api.services.runtime.Notification;
import org.integratedmodelling.klab.services.reasoner.internal.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Real k.IM source occurrences, completed OWL proofs, and source-bound diagnostics. */
class ModelDependencyValidationSourceTest {
  @TempDir Path directory;
  private static final String SOURCE = """
      ontology imod in domain root version 1.0;
      abstract identity Identity is core odo:Identity;
      abstract thing Subject is core odo:Subject;
      abstract length Length is core odo:Length;
      abstract process Process is core odo:Process;
      abstract identity Areal;
      abstract identity Volumetric;
      thing Location is imod:Subject;
      thing Region is imod:Areal imod:Location;
      thing Unknown is imod:Subject;
      thing Elsewhere is imod:Subject;
      abstract length Height is imod:Length requires identity (imod:Areal or imod:Volumetric);
      length Value is imod:Height of imod:Location;
      length RegionalValue is imod:Height of imod:Region;
      process Acting is imod:Process of imod:Elsewhere affects imod:RegionalValue;
      process Unrelated is imod:Process of imod:Elsewhere;
      process ActingUnqualified is imod:Process of imod:Elsewhere affects imod:Value;
      """;

  private record Fixture(WorkspaceManager manager, SemanticEnvironment environment) implements AutoCloseable {
    @Override public void close() throws Exception { environment.close(); }
  }

  private Fixture fixture() throws Exception {
    var manager = WorkspaceDomainIngestionTest.manager(Files.createDirectories(directory.resolve("source")), Map.of("imod", SOURCE));
    var world = new WorldviewImpl(); world.setUrn("imod"); world.setWorldviewId("imod"); world.setEmpty(false);
    world.setOntologies(new ArrayList<>(manager.getOntologies(false)));
    var factory = new SemanticEnvironmentFactory(directory.resolve("actors"), CoreOntologyCatalog.bundledCore());
    var captured = factory.capture(world);
    assertEquals(CandidatePublicationCoordinator.BeanStatus.VALID, captured.beanStatus(), captured.diagnostics().toString());
    var compiled = factory.build(captured, () -> false);
    assertEquals(CandidatePublicationCoordinator.LogicalStatus.VALID, compiled.logicalStatus(), compiled.diagnostics().toString());
    return new Fixture(manager, compiled.state());
  }

  private KimNamespace parse(Fixture fixture, String model) throws Exception {
    WorkspaceDomainIngestionTest.set(fixture.manager(), "namespaceParser", new WorkspaceManager.KimParser());
    var read = WorkspaceManager.class.getDeclaredMethod("readNamespace", String.class, long.class, String.class, String.class);
    read.setAccessible(true);
    var source = "namespace models.validation version 1.0;\n" + model;
    var namespace = (KimNamespace) read.invoke(fixture.manager(), source, 1L, "fixture", "models.validation");
    assertTrue(namespace.getNotifications().stream().noneMatch(n -> n.getLevel() == Notification.Level.Error),
        namespace.getNotifications().toString());
    return namespace;
  }

  private List<Notification> validate(Fixture fixture, String source) throws Exception {
    return new DocumentSemanticValidator(fixture.environment().actor()).validate(parse(fixture, source));
  }

  @Test void modeledRegionSuppliesInheritedBearerIdentityWithoutMutatingDependency() throws Exception {
    try (var f = fixture()) {
      var actor = f.environment().actor();
      assertTrue(actor.resolveConcept("imod:Value").isAbstract(), "Unbound Location supplies no spatial identity");
      var graph = actor.owl().getOntology("imod").getOWLOntology();
      int before = graph.getAxiomCount();
      var diagnostics = validate(f, "model imod:Region observing imod:Value in m;");
      assertTrue(diagnostics.stream().noneMatch(n -> n.getLevel() == Notification.Level.Error), diagnostics.toString());
      assertTrue(actor.resolveConcept("imod:Value").isAbstract(), "Model context must not reassign the declaration's bearer");
      assertEquals(before, graph.getAxiomCount());
      var secondaryDocument = parse(f, "model imod:Region observing imod:Value in m;");
      var secondaryModel = (org.integratedmodelling.klab.api.lang.kim.KimModel)
          secondaryDocument.getStatements().getFirst();
      secondaryModel.getObservables().add(secondaryModel.getDependencies().removeFirst());
      var secondary = new DocumentSemanticValidator(actor, true).validate(secondaryDocument);
      assertTrue(secondary.stream().noneMatch(n -> n.getLevel() == Notification.Level.Error), secondary.toString());
      assertTrue(actor.resolveConcept("imod:Value").isAbstract());
    }
  }

  @Test void missingIdentityAndUnknownBearerAreSourceBoundErrors() throws Exception {
    try (var f = fixture()) {
      for (var subject : List.of("Location", "Unknown")) {
        var source = "model imod:" + subject + " observing imod:Value in m;";
        var document = parse(f, source);
        var diagnostics = new DocumentSemanticValidator(f.environment().actor()).validate(document);
        var error = diagnostics.stream().filter(n -> n.getLevel() == Notification.Level.Error
            && n.getMessage().contains("MODEL_DEPENDENCY_UNRESOLVED")).findFirst().orElseThrow(() -> new AssertionError(diagnostics));
        assertFalse(error.getMessage().contains("provably incompatible"));
        assertEquals("models.validation", error.getLexicalContext().getDocumentUrn());
        assertTrue(error.getLexicalContext().getOffsetInDocument() >= document.getSourceCode().indexOf("observing"));
        assertTrue(error.getLexicalContext().getLength() > 0);
      }
    }
  }

  @Test void provedDisjointBearerIsRejectedSeparatelyFromUnknown() throws Exception {
    try (var f = fixture()) {
      var graph = f.environment().actor().owl().getOntology("imod").getOWLOntology();
      var factory = graph.getOWLOntologyManager().getOWLDataFactory();
      var classes = graph.classesInSignature().toList();
      var location = classes.stream().filter(c -> c.getIRI().getRemainder().orElse("").equals("Location")).findFirst().orElseThrow();
      var elsewhere = classes.stream().filter(c -> c.getIRI().getRemainder().orElse("").equals("Elsewhere")).findFirst().orElseThrow();
      graph.getOWLOntologyManager().addAxiom(graph, factory.getOWLDisjointClassesAxiom(location, elsewhere));
      f.environment().actor().owl().flushReasoner();
      var diagnostics = validate(f, "model imod:Elsewhere observing imod:Value in m;");
      assertTrue(diagnostics.stream().anyMatch(n -> n.getLevel() == Notification.Level.Error
          && n.getMessage().contains("MODEL_DEPENDENCY_INCOMPATIBLE")), diagnostics.toString());
    }
  }

  @Test void authoredTemplatesRemainDeclarationsButConcreteModelCannotDemandAbstractDependency() throws Exception {
    try (var f = fixture()) {
      var concrete = validate(f, "model imod:Region observing imod:Height in m;");
      assertTrue(concrete.stream().anyMatch(n -> n.getLevel() == Notification.Level.Error
          && n.getMessage().contains("MODEL_DEPENDENCY_UNRESOLVED")), concrete.toString());
      var template = validate(f, "model imod:Height of imod:Region in m observing imod:Height in m;");
      assertTrue(template.stream().noneMatch(n -> n.getLevel() == Notification.Level.Error), template.toString());
      assertTrue(template.stream().anyMatch(n -> n.getLevel() == Notification.Level.Warning
          && n.getMessage().contains("MODEL_DEPENDENCY_TEMPLATE")), template.toString());
    }
  }
  @Test void provedEffectRelationPreservesIndependentQualityBearerWithoutWaivingItsIdentity() throws Exception {
    try (var f = fixture()) {
      var graph = f.environment().actor().owl().getOntology("imod").getOWLOntology();
      var factory = graph.getOWLOntologyManager().getOWLDataFactory();
      var classes = graph.classesInSignature().toList();
      var region = classes.stream().filter(c -> c.getIRI().getRemainder().orElse("").equals("Region")).findFirst().orElseThrow();
      var elsewhere = classes.stream().filter(c -> c.getIRI().getRemainder().orElse("").equals("Elsewhere")).findFirst().orElseThrow();
      graph.getOWLOntologyManager().addAxiom(graph, factory.getOWLDisjointClassesAxiom(region, elsewhere));
      f.environment().actor().owl().flushReasoner();
      var actor = f.environment().actor();
      assertTrue(actor.affectedBy(actor.resolveConcept("imod:RegionalValue"), actor.resolveConcept("imod:Acting")));
      assertFalse(actor.affectedBy(actor.resolveConcept("imod:RegionalValue"), actor.resolveConcept("imod:Unrelated")));
      assertTrue(actor.affectedBy(actor.resolveConcept("imod:Value"), actor.resolveConcept("imod:ActingUnqualified")));
      assertTrue(actor.resolveConcept("imod:Value").isAbstract());
      var permitted = validate(f, "model imod:Acting observing imod:RegionalValue in m;");
      assertTrue(permitted.stream().noneMatch(n -> n.getLevel() == Notification.Level.Error), permitted.toString());
      var absentRelation = validate(f, "model imod:Unrelated observing imod:RegionalValue in m;");
      assertTrue(absentRelation.stream().anyMatch(n -> n.getLevel() == Notification.Level.Error
          && n.getMessage().contains("MODEL_DEPENDENCY_INCOMPATIBLE")), absentRelation.toString());
      var missingEvidence = validate(f, "model imod:ActingUnqualified observing imod:Value in m;");
      assertTrue(missingEvidence.stream().anyMatch(n -> n.getLevel() == Notification.Level.Error), missingEvidence.toString());
    }
  }

  @Test void genericDependencyMakesConcretePrimaryParametricButMustBeBoundBeforeExecution() throws Exception {
    try (var f = fixture()) {
      var document = parse(f, "model imod:Region observing any imod:RegionalValue in m;");
      var model = (org.integratedmodelling.klab.api.lang.kim.KimModel) document.getStatements().getFirst();
      var selectorEvidence = new ArrayList<String>();
      ((org.integratedmodelling.klab.api.lang.kim.impl.KimConceptImpl) model.getDependencies().getFirst().getSemantics())
          .mapSyntax(java.util.List.of(), (path, node) -> selectorEvidence.add(path + " " + node.getUrn() + " " + node.getType()));
      Files.write(Path.of("target/model-selector-syntax.txt"), selectorEvidence);
      assertFalse(model.getObservables().getFirst().isGeneric());
      assertTrue(model.getDependencies().getFirst().getSemantics().getObservable().is(
          org.integratedmodelling.klab.api.knowledge.SemanticType.ANY), selectorEvidence.toString());
      assertFalse(f.environment().actor().resolveConcept("imod:RegionalValue").isAbstract(),
          "This control has fulfilled identity; only the generic parameter is unbound");
      var source = new DocumentSemanticValidator(f.environment().actor(), false).validate(document);
      assertTrue(source.stream().noneMatch(n -> n.getLevel() == Notification.Level.Error), source.toString());
      assertTrue(source.stream().anyMatch(n -> n.getLevel() == Notification.Level.Warning
          && n.getMessage().contains("MODEL_DEPENDENCY_TEMPLATE")), source.toString());
      var execution = new DocumentSemanticValidator(f.environment().actor(), true).validate(document);
      assertTrue(execution.stream().anyMatch(n -> n.getLevel() == Notification.Level.Error
          && n.getMessage().contains("MODEL_DEPENDENCY_UNRESOLVED")), execution.toString());
      assertTrue(model.getDependencies().getFirst().getSemantics().getObservable().is(
          org.integratedmodelling.klab.api.knowledge.SemanticType.ANY), "Validation must retain the authored selector");

      var genericPrimary = parse(f, "model any imod:Region observing imod:RegionalValue in m;");
      var primaryModel = (org.integratedmodelling.klab.api.lang.kim.KimModel) genericPrimary.getStatements().getFirst();
      var primaryTypes = new HashSet<org.integratedmodelling.klab.api.knowledge.SemanticType>();
      ((org.integratedmodelling.klab.api.lang.kim.impl.KimConceptImpl) primaryModel.getObservables().getFirst().getSemantics())
          .mapSyntax(node -> primaryTypes.addAll(node.getType()));
      assertTrue(primaryTypes.contains(org.integratedmodelling.klab.api.knowledge.SemanticType.ANY));
      var primarySource = new DocumentSemanticValidator(f.environment().actor(), false).validate(genericPrimary);
      assertTrue(primarySource.stream().noneMatch(n -> n.getLevel() == Notification.Level.Error), primarySource.toString());
      var primaryExecution = new DocumentSemanticValidator(f.environment().actor(), true).validate(genericPrimary);
      assertTrue(primaryExecution.stream().anyMatch(n -> n.getLevel() == Notification.Level.Error
          && n.getMessage().contains("MODEL_OUTPUT_UNRESOLVED")), primaryExecution.toString());

      // Reuse the parsed generic occurrence as a secondary output to exercise the
      // complete model AST contract independently of which output discovery selects.
      var secondary = model.getDependencies().removeFirst();
      model.getObservables().add(secondary);
      var secondarySource = new DocumentSemanticValidator(f.environment().actor(), false).validate(document);
      assertTrue(secondarySource.stream().noneMatch(n -> n.getLevel() == Notification.Level.Error), secondarySource.toString());
      var secondaryExecution = new DocumentSemanticValidator(f.environment().actor(), true).validate(document);
      assertTrue(secondaryExecution.stream().anyMatch(n -> n.getLevel() == Notification.Level.Error
          && n.getMessage().contains("MODEL_OUTPUT_UNRESOLVED")
          && n.getMessage().contains(secondary.getUrn())), secondaryExecution.toString());
    }
  }

}
