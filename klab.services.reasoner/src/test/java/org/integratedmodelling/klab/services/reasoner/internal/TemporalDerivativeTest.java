package org.integratedmodelling.klab.services.reasoner.internal;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.util.*;
import org.integratedmodelling.klab.api.knowledge.*;
import org.integratedmodelling.klab.api.knowledge.impl.WorldviewImpl;
import org.integratedmodelling.klab.api.lang.kim.impl.*;
import org.integratedmodelling.klab.api.services.UnitService;
import org.integratedmodelling.klab.api.knowledge.observation.scale.*;
import org.integratedmodelling.klab.configuration.ServiceConfiguration;
import org.integratedmodelling.klab.runtime.language.*;
import org.integratedmodelling.languages.api.SemanticSyntax;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.semanticweb.owlapi.model.*;

class TemporalDerivativeTest {
  @TempDir Path directory;
  @SuppressWarnings("unchecked") private Set<SemanticType> types(SemanticSyntax.Type type) throws Exception {
    var m=LanguageAdapter.class.getDeclaredMethod("adaptSemanticType",SemanticSyntax.Type.class);m.setAccessible(true);
    return EnumSet.copyOf((Set<SemanticType>)m.invoke(LanguageAdapter.INSTANCE,type));
  }
  private SemanticEnvironment environment() throws Exception {
    return environment(true);
  }
  private SemanticEnvironment environment(boolean includeDerivativeFoundation) throws Exception {
    var ontology=new KimOntologyImpl();ontology.setUrn("imod");ontology.setProjectName("derivative-test");ontology.setSourceCode(" ".repeat(2048));
    for(var entry:List.of(Map.entry("Mass",SemanticSyntax.Type.MASS),Map.entry("Velocity",SemanticSyntax.Type.VELOCITY),Map.entry("Temperature",SemanticSyntax.Type.TEMPERATURE))) {
      var value=new KimConceptStatementImpl();value.setUrn(entry.getKey());value.setNamespace("imod");value.setAbstract(true);value.setType(types(entry.getValue()));value.getType().add(SemanticType.ABSTRACT);value.setOffsetInDocument(1);
      value.setUpperConceptDefined("odo:"+(entry.getKey().equals("Velocity")?"Speed":entry.getKey()));ontology.getStatements().add(value);
    }
    var body=new KimConceptStatementImpl();body.setUrn("Body");body.setNamespace("imod");body.setType(types(SemanticSyntax.Type.SUBJECT));body.setOffsetInDocument(1);ontology.getStatements().add(body);
    var stock=new KimConceptStatementImpl();stock.setUrn("Stock");stock.setNamespace("imod");stock.setType(types(SemanticSyntax.Type.MASS));stock.setOffsetInDocument(1);
    ontology.getStatements().add(stock);
    var unknown=new KimConceptStatementImpl();unknown.setUrn("UnknownUnit");unknown.setNamespace("imod");unknown.setType(types(SemanticSyntax.Type.QUANTITY));unknown.setOffsetInDocument(1);ontology.getStatements().add(unknown);
    var world=new WorldviewImpl();world.setUrn("imod");world.setWorldviewId("imod");world.setEmpty(false);world.setOntologies(new ArrayList<>(List.of(ontology)));
    stock.setDeclaredParent(new CapturedSemanticExpressionParser(world).concept("imod:Mass of imod:Body"));
    var core=new TreeMap<String,byte[]>();for(var name:List.of("odo.owl","klab.owl","dc.owl")) {
      String root=System.getProperty("klab.test.core.directory");
      if(root!=null)core.put(name,Files.readAllBytes(Path.of(root,name)));
      else try(var in=getClass().getResourceAsStream("/knowledge/"+name)){assertNotNull(in);core.put(name,in.readAllBytes());}
    }
    if(!includeDerivativeFoundation) {
      var manager=org.semanticweb.owlapi.apibinding.OWLManager.createOWLOntologyManager();
      var ontologyCore=manager.loadOntologyFromOntologyDocument(new java.io.ByteArrayInputStream(core.get("odo.owl")));
      var derivative=manager.getOWLDataFactory().getOWLClass(IRI.create("http://integratedmodelling.org/odo#ChangeRate"));
      manager.removeAxioms(ontologyCore,ontologyCore.axioms().filter(a->a.containsEntityInSignature(derivative)).collect(java.util.stream.Collectors.toSet()));
      var bytes=new java.io.ByteArrayOutputStream();manager.saveOntology(ontologyCore,new org.semanticweb.owlapi.formats.RDFXMLDocumentFormat(),bytes);core.put("odo.owl",bytes.toByteArray());
    }
    var factory=new SemanticEnvironmentFactory(directory.resolve(UUID.randomUUID().toString()),core);var input=factory.capture(world);
    assertEquals(CandidatePublicationCoordinator.BeanStatus.VALID,input.beanStatus(),input.diagnostics().toString());
    var result=factory.build(input,()->false);assertEquals(CandidatePublicationCoordinator.LogicalStatus.VALID,result.logicalStatus(),result.diagnostics().toString());return result.state();
  }
  private OWLClass owlClass(SemanticEnvironment state,Concept concept) {
    return state.actor().owl().getOntology(concept.getNamespace()).getOWLOntology().classesInSignature()
        .filter(c->c.getIRI().getRemainder().orElse("").equals(concept.getName())).findFirst().orElseThrow();
  }
  private boolean isA(SemanticEnvironment state,Concept child,String parent) {
    var owl=state.actor().owl();var factory=owl.getOntology(child.getNamespace()).getOWLOntology().getOWLOntologyManager().getOWLDataFactory();
    return owl.isEntailed(factory.getOWLSubClassOfAxiom(owlClass(state,child),owlClass(state,owl.getConcept("odo:"+parent))));
  }
  @Test void massAndSpeedKeepTheirSpatialKindsAndHigherDerivativesRemainDistinct() throws Exception {
    try(var state=environment()) {
      var actor=state.actor();var owl=actor.owl();var units=ServiceConfiguration.INSTANCE.getService(UnitService.class);
      var mass=owl.getConcept("imod:Mass");var rate=owl.makeRate(mass);var second=owl.makeRate(rate);var speed=owl.makeRate(owl.getConcept("imod:Velocity"));
      var injector=new org.integratedmodelling.languages.ObservableStandaloneSetup().createInjector();
      var parser=injector.getInstance(org.eclipse.xtext.parser.IParser.class);
      var grammar=injector.getInstance(org.integratedmodelling.languages.services.ObservableGrammarAccess.class);
      assertFalse(parser.parse(grammar.getConceptExpressionRule(),new java.io.StringReader(second.getUrn())).hasSyntaxErrors(),second.getUrn());
      // These constructions occur after the fixture's completed compiler batch. Commit that
      // batch to HermiT before testing entailments, as production staged compilation does.
      owl.flushReasoner();
      assertTrue(rate.is(SemanticType.RATE));assertTrue(rate.is(SemanticType.EXTENSIVE));assertFalse(rate.is(SemanticType.INTENSIVE));
      assertTrue(speed.is(SemanticType.INTENSIVE));assertFalse(speed.is(SemanticType.EXTENSIVE));
      assertNotEquals(rate.getUrn(),second.getUrn());assertEquals(rate.getUrn(),owl.makeRate(mass).getUrn());
      assertTrue(isA(state,rate,"ExtensivePhysicalProperty"));
      assertFalse(isA(state,rate,"IntensivePhysicalProperty"));
      assertFalse(isA(state,rate,"Change"));
      var unit=units.getDefaultUnitFor(rate);assertNotNull(unit);assertEquals(ExtentDistribution.INTENSIVE,unit.getAggregatedDimensions().get(ExtentDimension.TEMPORAL));
      assertTrue(units.isCompatible(units.getUnit(rate.getMetadata().get("odo:unit").toString()),units.getUnit("kg/s")));
      assertTrue(units.isCompatible(units.getUnit(second.getMetadata().get("odo:unit").toString()),units.getUnit("kg/s^2")));
      assertTrue(units.isCompatible(units.getUnit(speed.getMetadata().get("odo:unit").toString()),units.getUnit("m/s^2")));
      var factory=owl.getOntology(rate.getNamespace()).getOWLOntology().getOWLOntologyManager().getOWLDataFactory();
      var describes=factory.getOWLObjectProperty(IRI.create("http://integratedmodelling.org/odo#describesQuality"));
      assertTrue(owl.isEntailed(factory.getOWLSubClassOfAxiom(owlClass(state,rate),factory.getOWLObjectSomeValuesFrom(describes,owlClass(state,mass)))));
      assertTrue(owl.isEntailed(factory.getOWLSubClassOfAxiom(owlClass(state,second),factory.getOWLObjectSomeValuesFrom(describes,owlClass(state,rate)))));
      assertTrue(owl.isConsistent());assertTrue(actor.satisfiable(rate));assertTrue(actor.satisfiable(second));
    }
  }
  @Test void serializedHigherDerivativesPreserveIdentityUnitsAndOperandRestrictions() throws Exception {
    try (var state = environment()) {
      var actor = state.actor();
      var owl = actor.owl();
      var units = ServiceConfiguration.INSTANCE.getService(UnitService.class);
      var expected = owl.getConcept("odo:Mass");
      for (int order = 1; order <= 3; order++) {
        var operand = expected;
        expected = owl.makeRate(operand);
        var restored = actor.resolveConcept(expected.getUrn());
        assertNotNull(restored, expected.getUrn());
        assertEquals(expected.getUrn(), restored.getUrn());
        assertEquals(expected.getNamespace(), restored.getNamespace());
        assertEquals(expected.getName(), restored.getName());
        assertNotEquals(operand.getUrn(), restored.getUrn());
        String serialized = restored.getUrn();
        var builderRoundtrip = actor.resolveConceptWithBuilder(serialized, actor.serviceScope());
        assertEquals(serialized, builderRoundtrip.getUrn());
        assertEquals(restored.getName(), builderRoundtrip.getName());
        var unit = units.getUnit(restored.getMetadata().get("odo:unit").toString());
        assertTrue(units.isCompatible(unit, units.getUnit("kg/s^" + order)));
        assertEquals(1, units.conversion(units.getUnit("kg/s^" + order), unit).factor());
        owl.flushReasoner();
        var factory = owl.getOntology(restored.getNamespace()).getOWLOntology()
            .getOWLOntologyManager().getOWLDataFactory();
        var describes = factory.getOWLObjectProperty(IRI.create("http://integratedmodelling.org/odo#describesQuality"));
        assertTrue(owl.isEntailed(factory.getOWLSubClassOfAxiom(owlClass(state, restored),
            factory.getOWLObjectSomeValuesFrom(describes, owlClass(state, operand)))));
        assertTrue(actor.satisfiable(restored));
      }
      assertTrue(owl.isConsistent());
    }
  }
  @Test void directConstructionAndEquivalentParenthesesReuseOneOwlClassAtEveryOrder() throws Exception {
    for (int[] resolutionOrder : List.of(new int[] {1, 2, 3}, new int[] {3, 2, 1}, new int[] {2, 1, 3})) {
      try (var state = environment()) {
        var actor = state.actor(); var owl = actor.owl();
        var derivatives = new ArrayList<Concept>();
        var operand = owl.getConcept("odo:Mass");
        // Construct every level before resolving any expression: normalization of
        // a lower derivative must not be a prerequisite for stable OWL identity.
        for (int order = 1; order <= 3; order++) {
          operand = owl.makeRate(operand); derivatives.add(operand);
        }
        var expectedNames = derivatives.stream().map(Concept::getName).toList();
        assertEquals(3, new HashSet<>(expectedNames).size());
        var manager = owl.getOntology("odo").getOWLOntology().getOWLOntologyManager();
        var originalClasses = manager.ontologies().flatMap(o -> o.classesInSignature())
            .map(OWLClass::getIRI).collect(java.util.stream.Collectors.toSet());
        for (int order : resolutionOrder) {
          for (String atomic : List.of("odo:Mass", "(odo:Mass)", "((odo:Mass))",
              "imod:Mass", "(imod:Mass)", "((imod:Mass))")) {
            String expression = atomic;
            for (int level = 0; level < order; level++) expression = "change rate of (" + expression + ")";
            for (var restored : List.of(actor.resolveConcept(expression),
                actor.resolveConcept(expression), actor.resolveConceptWithBuilder(expression, actor.serviceScope()))) {
              assertEquals(expectedNames.get(order - 1), restored.getName(), expression);
              assertEquals(derivatives.get(order - 1).getNamespace(), restored.getNamespace());
              assertEquals(derivatives.get(order - 1).getUrn(), restored.getUrn());
              var units = ServiceConfiguration.INSTANCE.getService(UnitService.class);
              assertTrue(units.isCompatible(units.getUnit(restored.getMetadata().get("odo:unit").toString()),
                  units.getUnit("kg/s^" + order)));
            }
          }
        }
        assertEquals(originalClasses, manager.ontologies().flatMap(o -> o.classesInSignature())
            .map(OWLClass::getIRI).collect(java.util.stream.Collectors.toSet()),
            "Equivalent syntax must not allocate duplicate generated OWL classes");
      }
    }
  }
  @Test void mixedOperatorOrdersAndSpecializationsRemainDistinctThroughResolution() throws Exception {
    try (var state = environment()) {
      var actor = state.actor(); var owl = actor.owl();
      var mass = owl.getConcept("odo:Mass"); var temperature = owl.getConcept("odo:Temperature");
      var rate = owl.makeRate(mass);
      assertNotEquals(rate.getName(), owl.makeRate(temperature).getName());
      assertNotEquals(rate.getName(), owl.makeRate(owl.getConcept("imod:Stock")).getName());
      var rateOfRatio = owl.makeRate(owl.makeRatio(mass, temperature));
      var ratioOfRate = owl.makeRatio(rate, temperature);
      assertNotEquals(rateOfRatio.getName(), ratioOfRate.getName());
      var manager = owl.getOntology("odo").getOWLOntology().getOWLOntologyManager();
      var before = manager.ontologies().flatMap(o -> o.classesInSignature())
          .map(OWLClass::getIRI).collect(java.util.stream.Collectors.toSet());
      for (var expected : List.of(rateOfRatio, ratioOfRate)) {
        for (String expression : List.of(expected.getUrn(), "((" + expected.getUrn() + "))")) {
          for (var actual : List.of(actor.resolveConcept(expression),
              actor.resolveConceptWithBuilder(expression, actor.serviceScope()))) {
            assertEquals(expected.getName(), actual.getName(), expression);
            assertEquals(expected.getNamespace(), actual.getNamespace());
          }
        }
      }
      assertEquals(before, manager.ontologies().flatMap(o -> o.classesInSignature())
          .map(OWLClass::getIRI).collect(java.util.stream.Collectors.toSet()));
    }
  }
  @Test void fullyParenthesizedSecondDerivativeResolvesWithoutAPriorGeneratedDefinition() throws Exception {
    try (var state = environment()) {
      var actor = state.actor();
      var restored = actor.resolveConcept("change rate of (change rate of (odo:Mass))");
      var units = ServiceConfiguration.INSTANCE.getService(UnitService.class);
      var unit = units.getUnit(restored.getMetadata().get("odo:unit").toString());
      assertTrue(units.isCompatible(unit, units.getUnit("kg/s^2")));
      var expected = actor.owl().makeRate(actor.owl().makeRate(actor.owl().getConcept("odo:Mass")));
      assertEquals(expected.getName(), restored.getName());
      assertEquals(expected.getUrn(), restored.getUrn());
    }
  }
  @Test void categoricalAndInvalidUnitRejectionsDoNotWritePartialDefinitions() throws Exception {
    try(var state=environment()) {
      var owl=state.actor().owl();var ontology=owl.getOntology("odo").getOWLOntology();var before=Set.copyOf(ontology.getAxioms());
      assertThrows(org.integratedmodelling.klab.api.exceptions.KlabValidationException.class,()->owl.makeRate(owl.getConcept("odo:EnumerableQuality")));
      assertEquals(before,ontology.getAxioms());
      var mass=owl.getConcept("imod:Mass");Object previous=mass.getMetadata().put("odo:unit","not_a_unit");
      try {assertThrows(RuntimeException.class,()->owl.makeRate(mass));assertEquals(before,ontology.getAxioms());}
      finally {mass.getMetadata().put("odo:unit",previous);}
    }
  }
  @Test void rateAdapterDoesNotImposeASpatialIntensiveFlag() throws Exception {
    var type=types(SemanticSyntax.Type.RATE);assertTrue(type.contains(SemanticType.RATE));assertFalse(type.contains(SemanticType.INTENSIVE));assertFalse(type.contains(SemanticType.EXTENSIVE));
  }
  @Test void inheritedUnitAndBearerAreRetainedWithoutInventingAnUnknownUnit() throws Exception {
    try(var state=environment()) {
      var actor=state.actor();var stock=actor.owl().getConcept("imod:Stock");var rate=actor.owl().makeRate(stock);
      var units=ServiceConfiguration.INSTANCE.getService(UnitService.class);
      assertEquals(actor.inherent(stock),actor.inherent(rate));assertNotNull(actor.inherent(rate));
      assertTrue(units.isCompatible(units.getUnit(rate.getMetadata().get("odo:unit").toString()),units.getUnit("kg/s")));
      var unknown=actor.owl().makeRate(actor.owl().getConcept("imod:UnknownUnit"));
      assertNull(unknown.getMetadata().get("odo:unit"));assertNull(units.getDefaultUnitFor(unknown));
    }
  }
  @Test void legacyFoundationStillRejectsBeforeWritingAnyAxioms() throws Exception {
    try(var state=environment(false)) {
      var owl=state.actor().owl();var manager=owl.getOntology("imod").getOWLOntology().getOWLOntologyManager();
      var before=manager.ontologies().collect(java.util.stream.Collectors.toMap(o->o.getOntologyID().toString(),o->Set.copyOf(o.getAxioms())));
      assertNull(owl.getConcept("odo:ChangeRate"));
      var failure=assertThrows(org.integratedmodelling.klab.api.exceptions.KlabValidationException.class,()->owl.makeRate(owl.getConcept("imod:Mass")));
      assertTrue(failure.getMessage().contains("odo:ChangeRate"));
      assertEquals(before,manager.ontologies().collect(java.util.stream.Collectors.toMap(o->o.getOntologyID().toString(),o->Set.copyOf(o.getAxioms()))));
    }
  }
}
