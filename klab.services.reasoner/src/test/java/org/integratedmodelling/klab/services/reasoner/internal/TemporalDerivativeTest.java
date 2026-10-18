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
}
