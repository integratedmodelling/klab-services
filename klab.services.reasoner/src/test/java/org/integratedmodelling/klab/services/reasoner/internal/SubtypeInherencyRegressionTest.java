package org.integratedmodelling.klab.services.reasoner.internal;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import org.integratedmodelling.klab.api.knowledge.SemanticType;
import org.integratedmodelling.klab.api.knowledge.Worldview;
import org.integratedmodelling.klab.api.knowledge.impl.WorldviewImpl;
import org.integratedmodelling.klab.api.lang.kim.impl.ApplicableConceptImpl;
import org.integratedmodelling.klab.api.lang.kim.impl.KimConceptImpl;
import org.integratedmodelling.klab.api.lang.kim.impl.KimConceptStatementImpl;
import org.integratedmodelling.klab.api.lang.kim.impl.KimOntologyImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Production compiler/composer regression for the Location -> Region bearer refinement. */
class SubtypeInherencyRegressionTest {
  @TempDir Path directory;

  private KimConceptImpl reference(String name) {
    var ret = new KimConceptImpl(); ret.setName("w:" + name); ret.setUrn("w:" + name);
    // Normalized source references retain their declaration types. The captured
    // resolver also caches these occurrences for later atomic composition.
    ret.setType(switch (name) {
      case "RealmRoot", "PhysicalEnvironment", "RegionalEnvironment" ->
          EnumSet.of(SemanticType.REALM, SemanticType.PREDICATE);
      case "QualityRoot", "Quality" -> EnumSet.of(SemanticType.QUALITY, SemanticType.OBSERVABLE);
      case "Type", "CoverType" -> EnumSet.of(SemanticType.QUALITY, SemanticType.CLASS, SemanticType.OBSERVABLE);
      case "EarthCover" -> EnumSet.of(SemanticType.CONFIGURATION, SemanticType.OBSERVABLE);
      default -> EnumSet.of(SemanticType.SUBJECT, SemanticType.COUNTABLE, SemanticType.OBSERVABLE);
    });
    ret.setOffsetInDocument(999); return ret;
  }

  private KimConceptStatementImpl statement(String name, SemanticType type, int offset) {
    var ret = new KimConceptStatementImpl(); ret.setUrn(name); ret.setNamespace("w");
    ret.setOffsetInDocument(offset);
    ret.setType(type == SemanticType.REALM
        ? EnumSet.of(SemanticType.REALM, SemanticType.PREDICATE)
        : EnumSet.of(type, SemanticType.OBSERVABLE));
    if (type == SemanticType.SUBJECT) ret.getType().add(SemanticType.COUNTABLE);
    return ret;
  }

  // As in SemanticEnvironmentFactoryTest, these are normalized compiler beans,
  // not source files for syntax/lexical preflight (the source is position padding).
  private CandidatePublicationCoordinator.Input<Worldview> input(
      SemanticEnvironmentFactory factory, Worldview worldview) {
    return new CandidatePublicationCoordinator.Input<>(SemanticEnvironmentFactory.sources(worldview),
        SemanticEnvironmentFactory.sourceOrder(worldview), factory.dependencyRevision(),
        SemanticEnvironmentFactory.CONFIGURATION_REVISION, worldview,
        CandidatePublicationCoordinator.BeanStatus.VALID, List.of());
  }

  private List<org.integratedmodelling.klab.api.services.runtime.Notification> validate(
      org.integratedmodelling.klab.services.reasoner.ReasonerService actor, String expression) {
    var sourceResolver = actor.serviceScope().getService(
        org.integratedmodelling.klab.api.services.ResourcesService.class);
    var model = new org.integratedmodelling.klab.api.lang.kim.impl.KimModelImpl();
    model.setUrn("classification"); model.setNamespace("classification");
    model.getObservables().add(sourceResolver.declareObservable(expression));
    var document = new org.integratedmodelling.klab.api.lang.kim.impl.KimNamespaceImpl();
    document.setUrn("classification"); document.setSourceCode(" ".repeat(1100));
    document.getStatements().add(model);
    return new DocumentSemanticValidator(actor).validate(document);
  }

  @Test void inheritedLocationBearerAcceptsRegionOnBothCompositionPaths() throws Exception {
    var ontology = new KimOntologyImpl(); ontology.setUrn("w"); ontology.setProjectName("fixture");
    ontology.setSourceCode(" ".repeat(1100));
    var location = statement("Location", SemanticType.SUBJECT, 1);
    location.setUpperConceptDefined("odo:Subject");
    var region = statement("Region", SemanticType.SUBJECT, 2);
    region.setDeclaredParent(reference("Location"));
    var qualityRoot = statement("QualityRoot", SemanticType.QUALITY, 3);
    qualityRoot.setUpperConceptDefined("odo:Quality");
    var quality = statement("Quality", SemanticType.QUALITY, 4);
    quality.setDeclaredParent(reference("QualityRoot")); quality.setDeclaredInherent(reference("Location"));
    var realmRoot = statement("RealmRoot", SemanticType.REALM, 5);
    realmRoot.setUpperConceptDefined("odo:Realm");
    var environment = statement("PhysicalEnvironment", SemanticType.REALM, 4);
    environment.setDeclaredParent(reference("RealmRoot")); environment.setAbstract(true);
    environment.getAppliesTo().add(new ApplicableConceptImpl(null, reference("Location")));
    ontology.getStatements().addAll(List.of(location, region, qualityRoot, quality, realmRoot, environment));
    var worldview = new WorldviewImpl(); worldview.setUrn("w"); worldview.setWorldviewId("w");
    worldview.setEmpty(false); worldview.setOntologies(new ArrayList<>(List.of(ontology)));
    var core = Map.of("odo.owl", Files.readAllBytes(
        Path.of(System.getProperty("klab.test.core.directory"), "odo.owl")));
    var factory = new SemanticEnvironmentFactory(directory.resolve("actors"), core);
    var result = factory.build(input(factory, worldview), () -> false);
    assertEquals(CandidatePublicationCoordinator.LogicalStatus.VALID, result.logicalStatus(), result.diagnostics().toString());
    try (var state = result.state()) {
      var actor = state.actor();
      assertTrue(actor.is(actor.resolveConcept("w:Region"), actor.resolveConcept("w:Location")));
      for (var expression : List.of("w:Quality of w:Region", "w:Quality of each w:Region",
          "w:PhysicalEnvironment of each w:Region")) {
        var ordinary = actor.resolveConcept(expression);
        assertNotNull(ordinary, expression);
        assertFalse(ordinary.is(SemanticType.NOTHING), expression + ": " + ordinary.getNotifications());
        var assisted = actor.resolveConceptWithBuilder(expression, actor.serviceScope());
        assertNotNull(assisted, expression);
        assertFalse(assisted.is(SemanticType.NOTHING), expression + ": " + assisted.getNotifications());
      }
    }
  }

  @Test void predicateClassificationUsesApplicableSubjectRatherThanDescribedConfiguration() throws Exception {
    var ontology = new KimOntologyImpl(); ontology.setUrn("w"); ontology.setProjectName("fixture");
    ontology.setSourceCode(" ".repeat(1100));
    var thing = statement("Thing", SemanticType.SUBJECT, 1); thing.setUpperConceptDefined("odo:Subject");
    var place = statement("Place", SemanticType.SUBJECT, 2);
    place.setDeclaredParent(reference("Thing")); place.setChildrenDisjoint(true);
    var location = statement("Location", SemanticType.SUBJECT, 2);
    location.setChildrenDisjoint(true);
    var region = statement("Region", SemanticType.SUBJECT, 2);
    location.getChildren().add(region);
    var elsewhere = statement("Elsewhere", SemanticType.SUBJECT, 3);
    place.getChildren().addAll(List.of(location, elsewhere));
    var unknown = statement("UnknownPlace", SemanticType.SUBJECT, 3);
    unknown.setDeclaredParent(reference("Thing"));
    var cover = statement("EarthCover", SemanticType.CONFIGURATION, 4);
    cover.setUpperConceptDefined("odo:Configuration");
    var type = statement("Type", SemanticType.QUALITY, 5);
    type.getType().add(SemanticType.CLASS); type.setAbstract(true);
    type.setUpperConceptDefined("odo:EnumerableQuality");
    var coverType = statement("CoverType", SemanticType.QUALITY, 6);
    coverType.getType().add(SemanticType.CLASS);
    coverType.setDeclaredParent(reference("Type")); coverType.setDeclaredInherent(reference("EarthCover"));
    var realmRoot = statement("RealmRoot", SemanticType.REALM, 7);
    realmRoot.setUpperConceptDefined("odo:Realm");
    var environment = statement("PhysicalEnvironment", SemanticType.REALM, 8);
    environment.setDeclaredParent(reference("RealmRoot")); environment.setAbstract(true);
    environment.getAppliesTo().add(new ApplicableConceptImpl(null, reference("Location")));
    environment.getObservablesDescribed().add(new org.integratedmodelling.klab.api.collections.impl.PairImpl<>(
        reference("CoverType"), org.integratedmodelling.klab.api.lang.kim.KimConceptStatement.DescriptionType.CLASSIFIES));
    var regionalEnvironment = statement("RegionalEnvironment", SemanticType.REALM, 9);
    regionalEnvironment.setDeclaredParent(reference("PhysicalEnvironment"));
    regionalEnvironment.setDeclaredInherent(reference("Region"));
    ontology.getStatements().addAll(List.of(thing, place, unknown, cover, type, coverType, realmRoot,
        environment, regionalEnvironment));
    var worldview = new WorldviewImpl(); worldview.setUrn("w"); worldview.setWorldviewId("w");
    worldview.setEmpty(false); worldview.setOntologies(new ArrayList<>(List.of(ontology)));
    var core = Map.of("odo.owl", Files.readAllBytes(
        Path.of(System.getProperty("klab.test.core.directory"), "odo.owl")));
    var factory = new SemanticEnvironmentFactory(directory.resolve("description-actors"), core);
    var result = factory.build(input(factory, worldview), () -> false);
    assertEquals(CandidatePublicationCoordinator.LogicalStatus.VALID, result.logicalStatus(), result.diagnostics().toString());
    try (var state = result.state()) {
      var actor = state.actor();
      var predicate = actor.resolveConcept("w:PhysicalEnvironment");
      assertEquals(actor.resolveConcept("w:EarthCover").getUrn(), actor.inherent(predicate).getUrn(),
          "The descriptor retains its distinct configuration bearer");
      var support = new org.integratedmodelling.klab.services.reasoner.owl.OWLSemanticClauseSupport(actor.owl());
      assertTrue(support.applicableTo(predicate, actor.resolveConcept("w:Region")));
      assertFalse(support.applicableTo(predicate, actor.resolveConcept("w:Elsewhere")));
      var expression = "w:PhysicalEnvironment of each w:Region";
      var diagnostics = validate(actor, expression);
      assertTrue(diagnostics.stream().noneMatch(n -> n.getLevel().severity >= 3),
          "The source model must accept specialized applicable Region: " + diagnostics);
      var ordinary = actor.resolveConcept(expression);
      assertNotNull(ordinary);
      assertFalse(ordinary.is(SemanticType.NOTHING), expression + ": " + ordinary.getNotifications());
      assertTrue(ordinary.getNotifications().stream().noneMatch(n -> n.getLevel().severity >= 3),
          ordinary.getNotifications().toString());
      actor.owl().flushReasoner();
      assertTrue(actor.owl().isSatisfiable(ordinary), "Ordinary predicate request must be satisfiable");
      var assisted = actor.resolveConceptWithBuilder(expression, actor.serviceScope());
      assertNotNull(assisted);
      assertFalse(assisted.is(SemanticType.NOTHING), expression + ": " + assisted.getNotifications());
      assertTrue(assisted.getNotifications().stream().noneMatch(n -> n.getLevel().severity >= 3),
          assisted.getNotifications().toString());
      actor.owl().flushReasoner();
      assertTrue(actor.owl().isSatisfiable(assisted), "Assisted predicate request must be satisfiable");
      var incompatible = validate(actor, "w:PhysicalEnvironment of each w:Elsewhere");
      assertTrue(incompatible.stream().anyMatch(n -> n.getLevel().severity >= 3
          && n.getMessage().contains("incompatible")), incompatible.toString());
      var unresolved = validate(actor, "w:PhysicalEnvironment of each w:UnknownPlace");
      assertTrue(unresolved.stream().anyMatch(n -> n.getLevel().severity >= 3), unresolved.toString());
      assertTrue(unresolved.stream().noneMatch(n -> n.getMessage().contains("incompatible")),
          "Missing membership proof must not be called incompatibility: " + unresolved);
      var authored = validate(actor, "w:RegionalEnvironment of each w:Location");
      assertTrue(authored.stream().anyMatch(n -> n.getLevel().severity >= 3),
          "An independently authored Region bearer remains binding: " + authored);
    }
  }
}
