package org.integratedmodelling.klab.services.resources.storage;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import org.integratedmodelling.klab.api.knowledge.SemanticType;
import org.integratedmodelling.klab.api.knowledge.impl.WorldviewImpl;
import org.integratedmodelling.klab.api.lang.kim.KimConceptStatement;
import org.integratedmodelling.klab.api.lang.kim.KimOntology;
import org.integratedmodelling.klab.services.reasoner.internal.CandidatePublicationCoordinator;
import org.integratedmodelling.klab.services.reasoner.internal.CoreOntologyCatalog;
import org.integratedmodelling.klab.services.reasoner.internal.SemanticEnvironmentFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;

/** Production parser/compiler/matcher: distance ranks compatible types, eligibility stays separate. */
class BearerSemanticDistanceSourceTest {
  @TempDir Path directory;

  private CandidatePublicationCoordinator.Result<org.integratedmodelling.klab.services.reasoner.internal.SemanticEnvironment>
      compile(Map<String, String> sources, Set<String> requested, String name) throws Exception {
    var manager = WorkspaceDomainIngestionTest.manager(Files.createDirectories(directory.resolve(name + "-sources")), sources);
    var ontologies = new ArrayList<>(manager.getOntologies(false));
    assertTrue(ontologies.stream().flatMap(o -> o.getNotifications().stream()).noneMatch(n -> n.getLevel().severity >= 3));
    if (requested != null) selectClosure(ontologies, requested);
    var world = new WorldviewImpl(); world.setUrn("imod"); world.setWorldviewId("imod"); world.setEmpty(false);
    world.setOntologies(ontologies);
    var core = CoreOntologyCatalog.bundledCore();
    var configured = System.getProperty("klab.test.core.directory");
    if (configured != null) for (var file : new ArrayList<>(core.keySet()))
      core.put(file, Files.readAllBytes(Path.of(configured, file)));
    var factory = new SemanticEnvironmentFactory(directory.resolve(name + "-actors"), core);
    var input = factory.capture(world);
    assertEquals(CandidatePublicationCoordinator.BeanStatus.VALID, input.beanStatus(), input.diagnostics().toString());
    var result = factory.build(input, () -> false);
    assertEquals(CandidatePublicationCoordinator.LogicalStatus.VALID, result.logicalStatus(), result.diagnostics().toString());
    return result;
  }

  @Test void bearerSpecializationRanksDirectionallyWithoutConvertingDistanceIntoEligibility() throws Exception {
    var source = """
        ontology imod in domain root version 1.0;
        abstract identity Identity is core odo:Identity;
        abstract thing Subject is core odo:Subject;
        abstract length Length is core odo:Length;
        abstract identity Areal;
        abstract identity Volumetric;
        thing Location is imod:Subject;
        thing Region is imod:Areal imod:Location;
        thing Unknown is imod:Subject;
        abstract length Height is imod:Length requires identity (imod:Areal or imod:Volumetric);
        length Value is imod:Height;
        """;
    var compiled = compile(Map.of("imod", source), null, "direction");
    try (var state = compiled.state()) {
      var actor = state.actor();
      var broad = actor.resolveObservable("imod:Value of imod:Location in m");
      var narrow = actor.resolveObservable("imod:Value of imod:Region in m");
      var unknown = actor.resolveObservable("imod:Value of imod:Unknown in m");
      assertTrue(actor.resolveConcept("imod:Areal").isAbstract());
      assertFalse(narrow.isAbstract(), "Abstract identity categories can constrain a qualifying bearer");
      assertTrue(broad.isAbstract(), "Location lacks identity evidence; a Region subtype does not qualify all Location");
      assertTrue(unknown.isAbstract());
      assertEquals(0, new org.integratedmodelling.klab.services.reasoner.SemanticMatcher(actor).semanticDistance(broad, broad),
          "Pure metric reflexivity does not discharge obligations");
      assertTrue(actor.semanticDistance(broad, broad) < 0,
          "The executable request boundary must reject an unresolved request before metric reflexivity");
      var forgedPredicate = new org.integratedmodelling.common.knowledge.ConceptImpl();
      forgedPredicate.setUrn(broad.asConcept().getUrn());
      forgedPredicate.setNamespace("imod"); forgedPredicate.setName("Value");
      forgedPredicate.setType(EnumSet.of(SemanticType.PREDICATE, SemanticType.REALM));
      forgedPredicate.setAbstract(false);
      assertTrue(actor.semanticDistance(broad, forgedPredicate) < 0,
          "Caller predicate flags cannot bypass the canonical request's missing identity evidence");
      assertEquals(0, actor.semanticDistance(narrow, narrow));
      int specialization = actor.semanticDistance(broad, narrow);
      int generalization = actor.semanticDistance(narrow, broad);
      int unrelated = actor.semanticDistance(narrow, unknown);
      var report = Path.of("target/bearer-distance-source.txt"); Files.createDirectories(report.getParent());
      Files.write(report, List.of("broadAbstract=" + broad.isAbstract(), "narrowAbstract=" + narrow.isAbstract(),
          "broadModelToNarrowRequest=" + specialization, "narrowModelToBroadRequest=" + generalization,
          "unrelatedUnknownScore=" + unrelated));
      assertTrue(specialization > 0, "A model with broader bearer type may rank behind exact for a narrower request");
      assertTrue(generalization < 0, "A model limited to Region must not supply all Location");
      assertTrue(unrelated < 0, "Missing hierarchy proof cannot be accepted merely because overlap is possible");
      var graph = actor.owl().getOntology("imod").getOWLOntology();
      var f = graph.getOWLOntologyManager().getOWLDataFactory();
      var regionClass = graph.classesInSignature().filter(c -> c.getIRI().getRemainder().orElse("").equals("Region"))
          .findFirst().orElseThrow();
      var unknownClass = graph.classesInSignature().filter(c -> c.getIRI().getRemainder().orElse("").equals("Unknown"))
          .findFirst().orElseThrow();
      assertFalse(actor.owl().isEntailed(f.getOWLDisjointClassesAxiom(regionClass, unknownClass)),
          "Negative admission score for unknown membership is not a proof of incompatibility");
      graph.getOWLOntologyManager().addAxiom(graph, f.getOWLDisjointClassesAxiom(regionClass, unknownClass));
      actor.owl().flushReasoner();
      assertTrue(actor.owl().isEntailed(f.getOWLDisjointClassesAxiom(regionClass, unknownClass)));
      assertTrue(actor.semanticDistance(narrow, unknown) < 0,
          "A provably incompatible bearer also stays outside admission; the integer score carries no diagnostic status");
      assertTrue(actor.resolveObservable("imod:Height of imod:Region in m").isAbstract(), "Authored abstractness stays binding");
    }
  }

  @Test
  @EnabledIfSystemProperty(named = "klab.test.strawman.directory", matches = ".+")
  void actualClassificationDistanceUsesApplicableBearerSpecializationDespiteDescriptorConfiguration() throws Exception {
    var sources = new LinkedHashMap<String, String>();
    try (var files = Files.list(Path.of(System.getProperty("klab.test.strawman.directory"), "src"))) {
      for (var file : files.filter(p -> p.toString().endsWith(".kwv")).sorted().toList())
        sources.put(file.getFileName().toString().replaceFirst("\\.kwv$", ""), Files.readString(file));
    }
    var compiled = compile(sources, new LinkedHashSet<>(List.of("earth:PhysicalEnvironment", "earth:Location", "earth:Region")), "actual");
    try (var state = compiled.state()) {
      var actor = state.actor();
      var descriptor = actor.inherent(actor.resolveConcept("earth:PhysicalEnvironment"));
      var broad = actor.resolveObservable("earth:PhysicalEnvironment of each earth:Location");
      var narrow = actor.resolveObservable("earth:PhysicalEnvironment of each earth:Region");
      int forward = actor.semanticDistance(broad, narrow);
      int backward = actor.semanticDistance(narrow, broad);
      var broadBearer = actor.directInherent(broad);
      var narrowBearer = actor.directInherent(narrow);
      var matcher = new org.integratedmodelling.klab.services.reasoner.SemanticMatcher(actor);
      int collectiveHierarchy = matcher.assertedDistance(narrowBearer, broadBearer);
      int singularHierarchy = matcher.assertedDistance(narrowBearer.singular(), broadBearer.singular());
      int mixedHierarchy = matcher.assertedDistance(narrowBearer, broadBearer.singular());
      var report = Path.of("target/actual-classification-distance.txt"); Files.createDirectories(report.getParent());
      Files.write(report, List.of("descriptorInherent=" + descriptor, "broad=" + broad + " abstract=" + broad.isAbstract(),
          "narrow=" + narrow + " abstract=" + narrow.isAbstract(), "locationModelToRegionRequest=" + forward,
          "regionModelToLocationRequest=" + backward,
          "broadDirectInherent=" + actor.directInherent(broad), "narrowDirectInherent=" + actor.directInherent(narrow),
          "broadEffectiveInherent=" + actor.inherent(broad), "narrowEffectiveInherent=" + actor.inherent(narrow),
          "broadTraits=" + actor.traits(broad), "narrowTraits=" + actor.traits(narrow),
          "collectiveHierarchyDistance=" + collectiveHierarchy, "singularHierarchyDistance=" + singularHierarchy,
          "mixedCollectivityHierarchyDistance=" + mixedHierarchy,
          "collectiveRegionParents=" + actor.parents(narrowBearer)));
      assertFalse(broad.is(SemanticType.NOTHING)); assertFalse(narrow.is(SemanticType.NOTHING));
      assertTrue(actor.satisfiable(broad)); assertTrue(actor.satisfiable(narrow));
      assertEquals(0, actor.semanticDistance(narrow, narrow));
      assertTrue(singularHierarchy > 0, "The actual Region→Location asserted hierarchy must be present");
      assertEquals(singularHierarchy, collectiveHierarchy,
          "Collective wrappers must preserve same-kind hierarchy distance through parent traversal");
      assertTrue(mixedHierarchy < 0, "Collective and singular subjects remain distinct");
      assertTrue(forward > 0, "A real Location classifier must admit a Region request after descriptor/application separation");
      assertTrue(backward < 0, "A Region classifier must not claim all Location");
    }
  }

  private void index(KimConceptStatement statement, KimConceptStatement root, Map<String, KimConceptStatement> roots) {
    roots.put(statement.getNamespace() + ":" + statement.getUrn(), root);
    statement.getChildren().forEach(child -> index(child, root, roots));
  }

  private void references(KimConceptStatement statement, Set<String> wanted) {
    assertNull(statement.getAuthorityRequired(), "Selected authority obligations cannot be waived");
    for (var reference : statement.getDeclaredReferences()) {
      String text = reference.getName() == null ? reference.getUrn() : reference.getName();
      if (text == null) continue;
      var matcher = java.util.regex.Pattern.compile("([a-z][a-z0-9_.]*):([A-Z][A-Za-z0-9_]*)").matcher(text);
      boolean qualified = false;
      while (matcher.find()) { wanted.add(matcher.group()); qualified = true; }
      if (!qualified && text.matches("[A-Z][A-Za-z0-9_]*")) wanted.add(statement.getNamespace() + ":" + text);
    }
    statement.getChildren().forEach(child -> references(child, wanted));
  }

  private void selectClosure(List<KimOntology> ontologies, Set<String> wanted) {
    var roots = new LinkedHashMap<String, KimConceptStatement>();
    ontologies.forEach(o -> o.getStatements().forEach(s -> index(s, s, roots)));
    var selected = Collections.newSetFromMap(new IdentityHashMap<KimConceptStatement, Boolean>());
    ontologies.forEach(o -> o.getStatements().stream()
        .filter(s -> s.getUpperConceptDefined() != null || s.getType().contains(SemanticType.DOMAIN)).forEach(selected::add));
    boolean changed;
    do {
      int count = wanted.size() + selected.size();
      for (var name : new ArrayList<>(wanted)) if (roots.get(name) != null) selected.add(roots.get(name));
      selected.forEach(s -> references(s, wanted));
      changed = count != wanted.size() + selected.size();
    } while (changed);
    ontologies.forEach(o -> ((org.integratedmodelling.klab.api.lang.kim.impl.KimOntologyImpl) o)
        .setStatements(o.getStatements().stream().filter(selected::contains).toList()));
  }
}
