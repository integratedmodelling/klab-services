package org.integratedmodelling.klab.services.resources.storage;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.LinkedHashSet;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EStructuralFeature;
import org.eclipse.emf.ecore.resource.impl.ResourceImpl;
import org.eclipse.xtext.parser.IParser;
import org.integratedmodelling.klab.api.knowledge.SemanticType;
import org.integratedmodelling.klab.api.knowledge.impl.WorldviewImpl;
import org.integratedmodelling.klab.api.knowledge.observation.impl.ObservationImpl;
import org.integratedmodelling.klab.api.lang.kim.KimConceptStatement;
import org.integratedmodelling.klab.api.lang.kim.KimObservationStrategyDocument;
import org.integratedmodelling.klab.api.lang.kim.KimOntology;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.services.runtime.Notification;
import org.integratedmodelling.klab.runtime.language.LanguageAdapter;
import org.integratedmodelling.klab.runtime.language.WorldviewValidationScope;
import org.integratedmodelling.klab.services.reasoner.internal.CandidatePublicationCoordinator;
import org.integratedmodelling.klab.services.reasoner.internal.SemanticEnvironmentFactory;
import org.integratedmodelling.languages.ObservationStandaloneSetup;
import org.integratedmodelling.languages.ObservationSyntaxAdapter;
import org.integratedmodelling.languages.api.ObservationSyntax;
import org.integratedmodelling.languages.api.ParsedObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;

/**
 * Opt-in acceptance against an exact local source checkout. This never talks to a live service,
 * looks up an authority, or changes the checkout. Full acceptance requires owned authority snapshots;
 * diagnostics are retained rather than marking an uncompiled worldview as accepted.
 */
@EnabledIfSystemProperty(named = "klab.test.strawman.directory", matches = ".+")
class StrawmanBearerSourceAcceptanceTest {
  @TempDir Path directory;

  private record Loaded(Path source, List<KimOntology> ontologies, List<String> evidence, WorkspaceManager manager) {}

  private Loaded load() throws Exception {
    var source = Path.of(System.getProperty("klab.test.strawman.directory")).toAbsolutePath();
    var texts = new LinkedHashMap<String, String>();
    var evidence = new ArrayList<String>();
    try (var files = Files.list(source.resolve("src"))) {
      for (var file : files.filter(p -> p.toString().endsWith(".kwv")).sorted().toList()) {
        byte[] bytes = Files.readAllBytes(file);
        texts.put(file.getFileName().toString().replaceFirst("\\.kwv$", ""),
            new String(bytes, java.nio.charset.StandardCharsets.UTF_8));
        evidence.add(file.getFileName() + " sha256="
            + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)));
      }
    }
    assertFalse(texts.isEmpty(), "No worldview ontology sources found");
    var manager = WorkspaceDomainIngestionTest.manager(Files.createDirectories(directory.resolve("sources")), texts);
    var ontologies = new ArrayList<>(manager.getOntologies(false));
    assertEquals(texts.size(), ontologies.size(), "Every source must be ingested");
    var errors = ontologies.stream().flatMap(o -> o.getNotifications().stream())
        .filter(n -> n.getLevel().severity >= Notification.Level.Error.severity)
        .map(Notification::getMessage).toList();
    assertTrue(errors.isEmpty(), errors.toString());
    return new Loaded(source, ontologies, evidence, manager);
  }

  private KimConceptStatement declaration(Loaded loaded, String namespace, String name) {
    var ontology = loaded.ontologies().stream().filter(o -> namespace.equals(o.getUrn())).findFirst().orElseThrow();
    return find(ontology.getStatements(), name);
  }

  private KimConceptStatement find(List<? extends org.integratedmodelling.klab.api.lang.kim.KlabStatement> statements, String name) {
    for (var statement : statements) if (statement instanceof KimConceptStatement concept) {
      if (name.equals(concept.getUrn())) return concept;
      var child = find(concept.getChildren(), name);
      if (child != null) return child;
    }
    return null;
  }

  @Test void actualSourcesPreserveSpatialRequirementTargetAndNativeProcessIdentity() throws Exception {
    var loaded = load();
    var height = declaration(loaded, "physical", "Height");
    assertNotNull(height); assertTrue(height.isAbstract());
    assertEquals(1, height.getRequiredIdentities().size());
    var required = height.getRequiredIdentities().getFirst().getUrn();
    assertTrue(required.contains("imod:Areal") && required.contains("imod:Volumetric") && required.contains(" or "), required);
    var elevation = declaration(loaded, "geography", "Elevation");
    assertNotNull(elevation); assertFalse(elevation.isAbstract());
    assertTrue(elevation.getDeclaredParent().getUrn().contains("physical:Height"));
    assertTrue(elevation.getDeclaredParent().getUrn().contains("earth:Location"));
    var location = declaration(loaded, "earth", "Location");
    assertNotNull(location); assertTrue(location.getTraitsInherited().isEmpty());
    assertTrue(location.getDeclaredParent().getUrn().contains("physical:Feature"));
    assertTrue(location.getRequiredIdentities().isEmpty());
    var feature = declaration(loaded, "physical", "Feature");
    assertNotNull(feature); assertTrue(feature.getTraitsInherited().isEmpty());
    assertTrue(feature.getRequiredIdentities().isEmpty());
    var region = declaration(loaded, "earth", "Region");
    assertNotNull(region);
    assertTrue(region.getDeclaredParent().getUrn().contains("imod:Areal"));
    assertTrue(region.getDeclaredParent().getUrn().contains("earth:Location"));
    var transformation = declaration(loaded, "physical", "Transformation");
    assertNotNull(transformation);
    assertTrue(transformation.getTraitsInherited().stream()
        .anyMatch(c -> "physical:IntransitiveProcess".equals(c.getUrn())));
    assertTrue(transformation.getRequiredIdentities().isEmpty(), "Native process identity is not a bearer requirement");
  }

  @Test
  @EnabledIfSystemProperty(named = "klab.test.staging.directory", matches = ".+")
  void actualClassificationModelDoesNotReportIncompatibleRegionInherency() throws Exception {
    var loaded = load();
    var sourceFile = Path.of(System.getProperty("klab.test.staging.directory"))
        .resolve("src/staging/vxii/test/classification.kim");
    var source = Files.readString(sourceFile);
    assertTrue(source.contains("model earth:PhysicalEnvironment of each earth:Region"));
    // Read original bytes only; parser state and document identity remain private to this test.
    WorkspaceDomainIngestionTest.set(loaded.manager(), "namespaceParser", new WorkspaceManager.KimParser());
    var method = WorkspaceManager.class.getDeclaredMethod("readNamespace", String.class, long.class, String.class, String.class);
    method.setAccessible(true);
    var namespace = (org.integratedmodelling.klab.api.lang.kim.KimNamespace) method.invoke(
        loaded.manager(), source, 1L, "klab.staging.vxii", "staging.vxii.test.classification");
    assertNotNull(namespace);
    var messages = namespace.getNotifications().stream().map(Notification::getMessage).toList();
    var report = Path.of("target/strawman-classification-source-acceptance.txt");
    Files.createDirectories(report.getParent());
    var evidence = new ArrayList<>(loaded.evidence());
    evidence.add(sourceFile + " sha256=" + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
        .digest(source.getBytes(java.nio.charset.StandardCharsets.UTF_8))));
    evidence.addAll(messages); Files.write(report, evidence);
    assertTrue(messages.stream().noneMatch(m -> {
      var text = m.toLowerCase(java.util.Locale.ROOT);
      return text.contains("inher") && (text.contains("incompatib") || text.contains("outside"));
    }), "Actual classification source inherency failure: " + messages);
  }

  private KimObservationStrategyDocument strategies(Loaded loaded) throws Exception {
    var file = loaded.source().resolve("strategies/observations.obs");
    var parser = new ObservationStandaloneSetup().createInjectorAndDoEMFRegistration().getInstance(IParser.class);
    var parsed = parser.parse(new StringReader(Files.readString(file)));
    assertFalse(parsed.hasSyntaxErrors());
    var root = (org.integratedmodelling.languages.observation.ObservationDocument) parsed.getRootASTElement();
    new ResourceImpl(URI.createURI("memory:/observations.obs")).getContents().add(root);
    var world = new WorldviewImpl(); world.setOntologies(loaded.ontologies());
    var syntax = (ObservationSyntax.StrategyDocument) new ObservationSyntaxAdapter() {
      @Override protected void logWarning(ParsedObject target, EObject object, EStructuralFeature feature, String message) {}
      @Override protected void logError(ParsedObject target, EObject object, EStructuralFeature feature, String message) { fail(message); }
    }.adapt(root, new WorldviewValidationScope(world));
    return LanguageAdapter.INSTANCE.adaptStrategies(syntax, "imod", List.of(), 1L);
  }

  private void index(KimConceptStatement statement, KimConceptStatement root,
      Map<String, KimConceptStatement> roots) {
    roots.put(statement.getNamespace() + ":" + statement.getUrn(), root);
    statement.getChildren().forEach(child -> index(child, root, roots));
  }

  private void dependencies(KimConceptStatement statement, Set<String> result) {
    for (var reference : statement.getDeclaredReferences()) {
      String text = reference.getName() == null ? reference.getUrn() : reference.getName();
      if (text == null) continue;
      var matcher = java.util.regex.Pattern.compile("([a-z][a-z0-9_.]*):([A-Z][A-Za-z0-9_]*)").matcher(text);
      boolean qualified = false;
      while (matcher.find()) { result.add(matcher.group()); qualified = true; }
      if (!qualified && text.matches("[A-Z][A-Za-z0-9_]*")) result.add(statement.getNamespace() + ":" + text);
    }
    statement.getChildren().forEach(child -> dependencies(child, result));
  }

  private void assertNoAuthorityWaiver(KimConceptStatement statement) {
    assertNull(statement.getAuthorityRequired(), "Selected authority obligation must not be waived: " + statement.getUrn());
    statement.getChildren().forEach(this::assertNoAuthorityWaiver);
  }

  private void identityProofEvidence(org.integratedmodelling.klab.services.reasoner.ReasonerService actor,
      List<String> evidence) {
    var graph = actor.owl().getOntology("physical").getOWLOntology();
    var manager = graph.getOWLOntologyManager(); var f = manager.getOWLDataFactory();
    var height = graph.classesInSignature().filter(c -> c.getIRI().getRemainder().orElse("").equals("Height"))
        .findFirst().orElseThrow();
    var areal = actor.owl().getOntology("imod").getOWLOntology().classesInSignature()
        .filter(c -> c.getIRI().getRemainder().orElse("").equals("Areal")).findFirst().orElseThrow();
    var requirements = manager.ontologies().flatMap(o -> o.subClassAxiomsForSubClass(height))
        .flatMap(a -> a.getNestedClassExpressions().stream())
        .filter(e -> e instanceof org.semanticweb.owlapi.model.OWLObjectSomeValuesFrom some
            && !some.getProperty().isAnonymous()
            && some.getProperty().asOWLObjectProperty().getIRI().toString().equals("http://integratedmodelling.org/odo#requiresIdentity"))
        .map(e -> ((org.semanticweb.owlapi.model.OWLObjectSomeValuesFrom) e).getFiller()).distinct().toList();
    var bounds = new org.integratedmodelling.klab.services.reasoner.owl.OWLSemanticClauseSupport(actor.owl())
        .bearerConstraints(actor.resolveConcept("geography:Elevation")).stream()
        .filter(b -> !b.applicability()).map(b -> b.filler()).collect(java.util.stream.Collectors.toSet());
    var bearer = f.getOWLObjectIntersectionOf(bounds);
    var hasIdentity = f.getOWLObjectProperty(org.semanticweb.owlapi.model.IRI.create("http://integratedmodelling.org/odo#hasIdentity"));
    evidence.add("effectiveElevationBearerSatisfiable=" + actor.owl().isSatisfiable(bearer));
    evidence.add("effectiveElevationBearerHasAreal=" + actor.owl().isEntailed(
        f.getOWLSubClassOfAxiom(bearer, f.getOWLObjectSomeValuesFrom(hasIdentity, areal))));
    for (var required : requirements) {
      evidence.add("heightRequiredIdentityRaw=" + required);
      evidence.add("arealSpecializesRequired=" + actor.owl().isEntailed(f.getOWLSubClassOfAxiom(areal, required)));
      evidence.add("effectiveBearerEntailsRequiredIdentity=" + actor.owl().isEntailed(
          f.getOWLSubClassOfAxiom(bearer, f.getOWLObjectSomeValuesFrom(hasIdentity, required))));
      if (required instanceof org.semanticweb.owlapi.model.OWLClass named)
        for (var ontology : manager.getOntologies()) {
          evidence.addAll(ontology.getSubClassAxiomsForSubClass(named).stream().map(a -> "requiredFillerSuperclassAxiom=" + a).toList());
          evidence.addAll(ontology.getEquivalentClassesAxioms(named).stream().map(a -> "requiredFillerEquivalentAxiom=" + a).toList());
        }
    }
    var requiredConcepts = actor.owl().getRestrictedClasses(actor.resolveConcept("physical:Height"),
        actor.owl().getProperty("odo:requiresIdentity"));
    evidence.add("requiredIdentityConceptFlags=" + requiredConcepts.stream()
        .map(c -> c + " " + c.getNamespace() + ":" + c.getName() + " " + c.getType()).toList());
  }

  private Map<String, byte[]> capturedCore() throws Exception {
    var core = org.integratedmodelling.klab.services.reasoner.internal.CoreOntologyCatalog.bundledCore();
    var configured = System.getProperty("klab.test.core.directory");
    if (configured != null) for (var name : new ArrayList<>(core.keySet()))
      core.put(name, Files.readAllBytes(Path.of(configured, name)));
    return core;
  }

  private void selectSourceClosure(Loaded loaded, Set<String> wanted, List<String> evidence) {
    var roots = new LinkedHashMap<String, KimConceptStatement>();
    loaded.ontologies().forEach(o -> o.getStatements().forEach(s -> index(s, s, roots)));
    var selected = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<KimConceptStatement, Boolean>());
    for (var ontology : loaded.ontologies()) for (var statement : ontology.getStatements())
      if (statement.getUpperConceptDefined() != null || statement.getType().contains(SemanticType.DOMAIN)) selected.add(statement);
    boolean changed;
    do {
      changed = false;
      for (var name : new ArrayList<>(wanted)) {
        var root = roots.get(name);
        if (root != null && selected.add(root)) changed = true;
      }
      var dependencies = new LinkedHashSet<String>();
      selected.forEach(s -> dependencies(s, dependencies));
      if (wanted.addAll(dependencies)) changed = true;
    } while (changed);
    evidence.add("scope=exact parsed declaration dependency subset, not whole worldview acceptance");
    evidence.add("selected=" + selected.stream().map(s -> s.getNamespace() + ":" + s.getUrn()).sorted().toList());
    selected.forEach(this::assertNoAuthorityWaiver);
    for (var ontology : loaded.ontologies())
      ((org.integratedmodelling.klab.api.lang.kim.impl.KimOntologyImpl) ontology)
          .setStatements(ontology.getStatements().stream().filter(selected::contains).toList());
  }

  /** Exact parsed declaration closure, with no selected clause rewritten or authority waived. */
  @Test void actualDeclarationSubsetEligibilityAndStrategies() throws Exception {
    var loaded = load();
    var actualStrategies = strategies(loaded);
    var evidence = new ArrayList<>(loaded.evidence());
    selectSourceClosure(loaded, new LinkedHashSet<>(List.of("physical:Height", "geography:Elevation", "geography:Slope",
        "earth:Location", "earth:Region", "earth:PhysicalEnvironment", "earth:EarthCover", "physical:Transformation",
        "physical:Period", "physical:Frequency")), evidence);
    var world = new WorldviewImpl(); world.setUrn("imod"); world.setWorldviewId("imod"); world.setEmpty(false);
    world.setOntologies(loaded.ontologies()); world.setObservationStrategies(List.of(actualStrategies));
    var core = capturedCore();
    var factory = new SemanticEnvironmentFactory(directory.resolve("subset-actors"), core);
    var captured = factory.capture(world);
    var report = Path.of("target/strawman-bearer-subset-acceptance.txt"); Files.createDirectories(report.getParent());
    evidence.add("beanStatus=" + captured.beanStatus()); evidence.addAll(captured.diagnostics()); Files.write(report, evidence);
    assertEquals(CandidatePublicationCoordinator.BeanStatus.VALID, captured.beanStatus(), captured.diagnostics().toString());
    var compiled = factory.build(captured, () -> false);
    evidence.add("logicalStatus=" + compiled.logicalStatus()); evidence.addAll(compiled.diagnostics()); Files.write(report, evidence);
    assertEquals(CandidatePublicationCoordinator.LogicalStatus.VALID, compiled.logicalStatus(), compiled.diagnostics().toString());
    try (var state = compiled.state()) {
      var actor = state.actor();
      actor.owl().flushReasoner();
      var consistent = actor.owl().isConsistent();
      var namedClasses = actor.owl().getOntologies(true).stream()
          .flatMap(o -> o.getOWLOntology().classesInSignature()).distinct()
          .filter(c -> !c.isOWLNothing()).toList();
      var unsatisfiable = namedClasses.stream().filter(c -> !actor.owl().isSatisfiable(c))
          .map(Object::toString).sorted().toList();
      evidence.add("selectedClosureConsistent=" + consistent);
      evidence.add("selectedClosureNamedClassesChecked=" + namedClasses.size());
      evidence.add("selectedClosureUnsatisfiableNamedClasses=" + unsatisfiable);
      Files.write(report, evidence);
      assertTrue(consistent, "The selected source closure must remain logically consistent");
      assertTrue(unsatisfiable.isEmpty(), "The selected source closure has unsatisfiable named classes: " + unsatisfiable);
      identityProofEvidence(actor, evidence);
      evidence.add("effectiveElevationBearers="
          + new org.integratedmodelling.klab.services.reasoner.owl.OWLSemanticClauseSupport(actor.owl())
              .bearerConstraints(actor.resolveConcept("geography:Elevation")));
      var bare = actor.resolveConcept("geography:Elevation");
      var effectiveInherent = actor.inherent(bare);
      evidence.add("effectiveElevationInherent=" + effectiveInherent
          + " identities=" + (effectiveInherent == null ? List.of() : actor.identities(effectiveInherent)));
      evidence.add("atomicElevationAbstract=" + bare.isAbstract());
      var canonicalElevation = actor.owl().getConcept("geography:Elevation");
      evidence.add("canonicalElevationAbstract=" + canonicalElevation.isAbstract()
          + " flags=" + canonicalElevation.getType());
      if (canonicalElevation instanceof org.integratedmodelling.common.knowledge.ConceptImpl implementation) {
        var fresh = new org.integratedmodelling.klab.services.reasoner.owl.IdentityRequirementSupport(actor.owl())
            .project(implementation);
        evidence.add("freshElevationProjectionAbstract=" + fresh.isAbstract() + " flags=" + fresh.getType());
      }
      evidence.add("cachedAtomicElevationFlags=" + bare.getType());
      Files.write(report, evidence);
      assertFalse(bare.isAbstract(), "Initial atomic resolution must see the complete staged bearer/identity closure");
      var sourceResolver = actor.serviceScope().getService(org.integratedmodelling.klab.api.services.ResourcesService.class);
      var model = new org.integratedmodelling.klab.api.lang.kim.impl.KimModelImpl();
      model.setUrn("classification"); model.setNamespace("staging.vxii.test.classification");
      model.getObservables().add(sourceResolver.declareObservable("earth:PhysicalEnvironment of each earth:Region"));
      var document = new org.integratedmodelling.klab.api.lang.kim.impl.KimNamespaceImpl();
      document.setUrn("staging.vxii.test.classification");
      document.setSourceCode("model earth:PhysicalEnvironment of each earth:Region using klab.generators.random.categories();");
      document.getStatements().add(model);
      var validation = new org.integratedmodelling.klab.services.reasoner.internal.DocumentSemanticValidator(actor).validate(document);
      evidence.add("classificationValidation=" + validation); Files.write(report, evidence);
      assertTrue(validation.isEmpty(), validation.toString());
      var classification = actor.resolveConcept("earth:PhysicalEnvironment of each earth:Region");
      evidence.add("classification=" + classification + " notifications=" + classification.getNotifications());
      Files.write(report, evidence);
      assertFalse(classification.is(SemanticType.NOTHING), classification.getNotifications().toString());
      assertTrue(actor.satisfiable(classification), "Actual predicate classification request must be satisfiable");
      var alternativeBearer = actor.resolveObservable("geography:Elevation of (earth:Region or earth:Location) in m");
      assertTrue(alternativeBearer.isAbstract() || alternativeBearer.is(SemanticType.NOTHING),
          "A qualifying Region alternative does not prove the Location alternative has the required identity");
      var broadOverride = actor.resolveObservable("geography:Elevation of earth:Region in m");
      evidence.add("plainRegionOverrideAbstract=" + broadOverride.isAbstract()
          + " notifications=" + broadOverride.getSemantics().getNotifications()); Files.write(report, evidence);
      var elevation = actor.resolveObservable("geography:Elevation in m");
      assertFalse(elevation.isAbstract(), "The complete actual effective bearer must establish the spatial identity bound");
      assertFalse(elevation.is(SemanticType.NOTHING));
      var context = mock(ContextScope.class);
      when(context.getService(org.integratedmodelling.klab.api.services.Reasoner.class)).thenReturn(actor);
      when(context.getService(org.integratedmodelling.klab.api.services.ResourcesService.class)).thenReturn(sourceResolver);
      var contextObservation = new ObservationImpl(); contextObservation.setObservable(actor.resolveObservable("earth:Region"));
      when(context.getContextObservation()).thenReturn(contextObservation);
      var classificationRequest = new ObservationImpl();
      classificationRequest.setObservable(actor.resolveObservable("earth:PhysicalEnvironment of each earth:Region"));
      var classificationStrategies = actor.computeObservationStrategies(classificationRequest, context);
      evidence.add("classificationStrategies=" + classificationStrategies.stream().map(s -> s.getUrn()).toList());
      Files.write(report, evidence);
      assertTrue(classificationStrategies.stream().anyMatch(s -> s.getUrn().endsWith("classification.members")),
          "The actual worldview must select its classification.members strategy");
      var request = new ObservationImpl(); request.setObservable(elevation);
      var selectedStrategies = actor.computeObservationStrategies(request, context);
      evidence.add("atomicElevationStrategies=" + selectedStrategies.stream().map(s -> s.getUrn()).toList()); Files.write(report, evidence);
      assertFalse(selectedStrategies.isEmpty(), "Actual observations.obs must select an eligible dependent strategy");
      assertEquals(bare.isAbstract(), actor.resolveConcept("geography:Elevation").isAbstract(),
          "Strategy selection must not mutate cached declaration eligibility");
      var abstractRequest = new ObservationImpl(); abstractRequest.setObservable(actor.resolveObservable("physical:Height in m"));
      assertTrue(actor.computeObservationStrategies(abstractRequest, context).isEmpty(),
          "Authored abstract Height stays ineligible even within a qualifying Region context");
    }
  }

  @Test void actualFrequencyAndPeriodClosuresUseCompleteCoreAndReportDiagnostics() throws Exception {
    for (var selectedRoot : List.of("physical:Frequency", "physical:Period")) {
      var loaded = load();
      var evidence = new ArrayList<>(loaded.evidence());
      evidence.add("scope=exact Frequency/Period declaration closure with complete bundled core; not whole worldview acceptance");
      selectSourceClosure(loaded, new LinkedHashSet<>(List.of(selectedRoot)), evidence);
      assertNotNull(declaration(loaded, "physical", "Frequency"), "Period dependency closure must retain Frequency");
      if (selectedRoot.endsWith("Frequency"))
        assertNull(declaration(loaded, "physical", "Period"), "Frequency-only control must exclude the correlation owner");
      var world = new WorldviewImpl(); world.setUrn("imod"); world.setWorldviewId("imod"); world.setEmpty(false);
      world.setOntologies(loaded.ontologies());
      var core = capturedCore();
      var factory = new SemanticEnvironmentFactory(directory.resolve("correlation-" + selectedRoot.replace(':', '-')), core);
      var captured = factory.capture(world);
      evidence.add("beanStatus=" + captured.beanStatus()); evidence.addAll(captured.diagnostics());
      assertEquals(CandidatePublicationCoordinator.BeanStatus.VALID, captured.beanStatus(), captured.diagnostics().toString());
      var compiled = factory.build(captured, () -> false);
      evidence.add("logicalStatus=" + compiled.logicalStatus()); evidence.addAll(compiled.diagnostics());
      evidence.add("coreAliases=" + core.keySet());
      var report = Path.of("target/strawman-" + selectedRoot.substring(selectedRoot.indexOf(':') + 1).toLowerCase(java.util.Locale.ROOT) + "-semantic-diagnostics.txt");
      Files.createDirectories(report.getParent()); Files.write(report, evidence);
      assertEquals(CandidatePublicationCoordinator.LogicalStatus.VALID, compiled.logicalStatus(), compiled.diagnostics().toString());
      try (var state = compiled.state()) {
        state.actor().owl().flushReasoner();
        assertTrue(state.actor().owl().isConsistent());
        var concept = state.actor().resolveConcept(selectedRoot);
        assertTrue(state.actor().satisfiable(concept), selectedRoot + " must remain satisfiable");
        evidence.add("rootSatisfiable=true"); Files.write(report, evidence);
      }
    }
  }

  /** Exact cotechino dependency admission, retaining unresolved evidence rather than waiving it. */
  @Test
  @EnabledIfSystemProperty(named = "klab.test.staging.directory", matches = ".+")
  void actualCotechinoModelRetainsUnprovedCompleteElevationBearerDiagnostic() throws Exception {
    var loaded = load();
    var originalFile = Path.of(System.getProperty("klab.test.staging.directory"))
        .resolve("src/staging/vxii/basic.kim");
    var original = Files.readString(originalFile);
    int start = original.indexOf("private model earth:Terrestrial earth:Region");
    assertTrue(start >= 0, "The pinned cotechino model must exist in basic.kim");
    int end = original.indexOf(';', start) + 1; assertTrue(end > start);
    int headerEnd = original.indexOf(';') + 1;
    var prefix = new StringBuilder(original.substring(0, start).replaceAll("[^\\r\\n]", " "));
    prefix.replace(0, headerEnd, original.substring(0, headerEnd));
    var source = prefix + original.substring(start, end);
    assertTrue(source.contains("each earth:Freshwater earth:Region"));
    assertTrue(source.contains("geography:Elevation in m"));
    assertTrue(source.contains("geography:Slope in degree_angle"));
    WorkspaceDomainIngestionTest.set(loaded.manager(), "namespaceParser", new WorkspaceManager.KimParser());
    var read = WorkspaceManager.class.getDeclaredMethod("readNamespace", String.class, long.class, String.class, String.class);
    read.setAccessible(true);
    var namespace = (org.integratedmodelling.klab.api.lang.kim.KimNamespace) read.invoke(
        loaded.manager(), source, 1L, "klab.staging.vxii", "staging.vxii.basic");
    assertTrue(namespace.getNotifications().stream().noneMatch(n -> n.getLevel().severity >= 3), namespace.getNotifications().toString());
    var evidence = new ArrayList<>(loaded.evidence());
    evidence.add("scope=exact cotechino model excerpt with original character offsets and complete selected declaration closure; not live execution");
    evidence.add("basic.kim sha256=" + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
        .digest(original.getBytes(java.nio.charset.StandardCharsets.UTF_8))));
    evidence.add("modelOriginalOffsets=" + start + ".." + end);
    selectSourceClosure(loaded, new LinkedHashSet<>(List.of("earth:Terrestrial", "earth:Freshwater", "earth:Region",
        "geography:Elevation", "geography:Slope")), evidence);
    var world = new WorldviewImpl(); world.setUrn("imod"); world.setWorldviewId("imod"); world.setEmpty(false);
    world.setOntologies(loaded.ontologies());
    var factory = new SemanticEnvironmentFactory(directory.resolve("cotechino-actors"), capturedCore());
    var captured = factory.capture(world);
    assertEquals(CandidatePublicationCoordinator.BeanStatus.VALID, captured.beanStatus(), captured.diagnostics().toString());
    var compiled = factory.build(captured, () -> false);
    evidence.addAll(compiled.diagnostics()); evidence.add("logicalStatus=" + compiled.logicalStatus());
    assertEquals(CandidatePublicationCoordinator.LogicalStatus.VALID, compiled.logicalStatus(), compiled.diagnostics().toString());
    try (var state = compiled.state()) {
      var actor = state.actor();
      var modeled = actor.resolveConcept("earth:Terrestrial earth:Region");
      var elevation = actor.resolveConcept("geography:Elevation");
      var compatibility = new org.integratedmodelling.klab.services.reasoner.owl.OWLSemanticClauseSupport(actor.owl())
          .clauseCompatibility(elevation, org.integratedmodelling.klab.api.knowledge.SemanticRole.INHERENT, modeled);
      evidence.add("modeled=" + modeled + " abstract=" + modeled.isAbstract());
      evidence.add("effectiveElevationBearer=" + actor.inherent(elevation));
      evidence.add("modeledToElevationBearer=" + compatibility);
      var diagnostics = new org.integratedmodelling.klab.services.reasoner.internal.DocumentSemanticValidator(actor).validate(namespace);
      for (var diagnostic : diagnostics) {
        var lexical = diagnostic.getLexicalContext();
        evidence.add(diagnostic.getLevel() + " " + diagnostic.getMessage()
            + (lexical == null ? "" : " document=" + lexical.getDocumentUrn()
                + " offset=" + lexical.getOffsetInDocument() + " length=" + lexical.getLength()));
      }
      var report = Path.of("target/cotechino-model-bearer-diagnostics.txt"); Files.createDirectories(report.getParent());
      Files.write(report, evidence);
      assertEquals(org.integratedmodelling.klab.services.reasoner.owl.OWLSemanticClauseSupport.Compatibility.UNRESOLVED, compatibility,
          "Terrestrial Region lacks proof of the complete Crust-qualified bearer; do not invent that commitment");
      var diagnostic = diagnostics.stream().filter(n -> n.getLevel().severity >= 3
          && n.getMessage().contains("MODEL_DEPENDENCY_UNRESOLVED") && n.getMessage().contains("geography:Elevation"))
          .findFirst().orElseThrow(() -> new AssertionError(diagnostics));
      assertFalse(diagnostic.getMessage().contains("incompatible"));
      assertNotNull(diagnostic.getLexicalContext());
      assertTrue(diagnostic.getLexicalContext().getOffsetInDocument() >= start);
      assertTrue(diagnostic.getLexicalContext().getOffsetInDocument() < end);
    }
  }

  /** The whole authority-bearing source cannot be accepted by a source-only private harness. */
  @Test void wholeWorldviewWithoutAuthoritySnapshotsReportsBlocker() throws Exception {
    var loaded = load();
    var world = new WorldviewImpl(); world.setUrn("imod"); world.setWorldviewId("imod"); world.setEmpty(false);
    world.setOntologies(loaded.ontologies()); world.setObservationStrategies(List.of(strategies(loaded)));
    var core = capturedCore();
    var factory = new SemanticEnvironmentFactory(directory.resolve("actors"), core);
    var captured = factory.capture(world);
    var evidence = new ArrayList<>(loaded.evidence());
    evidence.add("beanStatus=" + captured.beanStatus()); evidence.addAll(captured.diagnostics());
    var report = Path.of("target/strawman-bearer-acceptance.txt"); Files.createDirectories(report.getParent());
    Files.write(report, evidence);
    assertEquals(CandidatePublicationCoordinator.BeanStatus.INVALID, captured.beanStatus(), captured.diagnostics().toString());
    for (var root : List.of("life:TaxonomicIdentity", "chemistry:ChemicalIdentity", "geography:M49GeographicalIdentity"))
      assertTrue(captured.diagnostics().stream().anyMatch(d -> d.contains("Authoritative authority validation is unavailable") && d.contains(root)),
          "Missing provider evidence must be explicit for " + root + ": " + captured.diagnostics());
  }
}
