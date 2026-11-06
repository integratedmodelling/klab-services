package org.integratedmodelling.klab.services.resources.storage;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Path;
import java.util.*;
import org.integratedmodelling.klab.api.knowledge.SemanticType;
import org.integratedmodelling.klab.api.knowledge.impl.WorldviewImpl;
import org.integratedmodelling.klab.api.services.runtime.Notification;
import org.integratedmodelling.klab.configuration.ServiceConfiguration;
import org.integratedmodelling.klab.data.mediation.UnitServiceImpl;
import org.integratedmodelling.klab.services.reasoner.internal.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Real source adaptation, private semantic environment and dimensional checks. */
class CycleQuantityIntegrationTest {
  @TempDir Path directory;
  private static org.semanticweb.owlapi.model.OWLClass owlClass(
      org.integratedmodelling.klab.services.reasoner.owl.OWL owl,
      org.integratedmodelling.klab.api.knowledge.Concept concept) throws Exception {
    var method=owl.getClass().getDeclaredMethod("getOWLClass",org.integratedmodelling.klab.api.knowledge.Concept.class);
    method.setAccessible(true);return (org.semanticweb.owlapi.model.OWLClass)method.invoke(owl,concept);
  }
  @Test void cycleQualitiesShareBearerAndRetainDistinctDimensions() throws Exception {
    ServiceConfiguration.injectInstantiators();
    var manager=WorkspaceDomainIngestionTest.manager(directory,Map.of("imod","""
        ontology imod in domain root version 1.0;
        abstract process Process is core odo:Process;
        abstract event Event is core odo:Event;
        abstract thing Subject is core odo:Subject;
        abstract identity Identity is core odo:Identity;
        abstract quantity CycleFrequency is core odo:Frequency;
        abstract duration Duration is core odo:Duration;
        abstract process CyclicProcess is imod:Process;
        abstract quantity Frequency is imod:CycleFrequency of imod:CyclicProcess;
        abstract duration Period is imod:Duration of imod:CyclicProcess decreases with imod:Frequency;
        process Oscillation is imod:CyclicProcess;
        quantity OscillationFrequency is imod:Frequency;
        duration OscillationPeriod is imod:Period;
        event IrregularEvent is imod:Event;
        duration EventDuration is imod:Duration of imod:IrregularEvent;
        quality GenericDuration is imod:Duration;
        identity Oxygen; identity Air;
        thing MaterialBody is imod:Subject;
        """));
    var ontology=manager.getOntologies(false).iterator().next();
    assertTrue(ontology.getNotifications().stream().noneMatch(n->n.getLevel().severity>=Notification.Level.Error.severity),ontology.getNotifications().toString());
    var world=new WorldviewImpl();world.setUrn("imod");world.setWorldviewId("imod");world.setEmpty(false);world.setOntologies(List.of(ontology));
    var core=new TreeMap<String,byte[]>();
    for(var name:List.of("odo.owl","klab.owl","dc.owl"))
      try(var input=getClass().getResourceAsStream("/knowledge/"+name)){assertNotNull(input);core.put(name,input.readAllBytes());}
    var factory=new SemanticEnvironmentFactory(directory.resolve("actor"),core);
    var compiled=factory.build(factory.capture(world),()->false);
    assertEquals(CandidatePublicationCoordinator.LogicalStatus.VALID,compiled.logicalStatus(),compiled.diagnostics().toString());
    assertTrue(compiled.diagnostics().stream().noneMatch(d->d.contains("DESCRIPTION_BEARER")),compiled.diagnostics().toString());
    try(var state=compiled.state()) {
      var actor=state.actor();
      var cyclic=actor.resolveConcept("imod:CyclicProcess");
      var frequency=actor.resolveConcept("imod:Frequency");
      var period=actor.resolveConcept("imod:Period");
      assertTrue(frequency.is(SemanticType.QUALITY));assertTrue(frequency.is(SemanticType.QUANTIFIABLE));
      assertFalse(frequency.is(SemanticType.NUMEROSITY));assertFalse(frequency.is(SemanticType.EXTENSIVE));
      assertTrue(period.is(SemanticType.DURATION));
      var dataFactory=actor.owl().getOntology("odo").getOWLOntology().getOWLOntologyManager().getOWLDataFactory();
      var intensive=dataFactory.getOWLClass(org.semanticweb.owlapi.model.IRI.create("http://integratedmodelling.org/odo#IntensivePhysicalProperty"));
      var extensive=dataFactory.getOWLClass(org.semanticweb.owlapi.model.IRI.create("http://integratedmodelling.org/odo#ExtensivePhysicalProperty"));
      for(var name:List.of("Duration","Period","OscillationPeriod","EventDuration","GenericDuration")) {
        var temporal=actor.resolveConcept("imod:"+name);
        assertTrue(temporal.is(SemanticType.DURATION),name);
        assertTrue(temporal.is(SemanticType.INTENSIVE),name);
        assertFalse(temporal.is(SemanticType.EXTENSIVE),name);
        assertTrue(actor.owl().isEntailed(dataFactory.getOWLSubClassOfAxiom(owlClass(actor.owl(),temporal),intensive)),name);
        assertFalse(actor.owl().isEntailed(dataFactory.getOWLSubClassOfAxiom(owlClass(actor.owl(),temporal),extensive)),name);
        var temporalSyntax=new org.integratedmodelling.klab.api.lang.kim.impl.KimConceptImpl();
        temporalSyntax.setType(EnumSet.copyOf(temporal.getType()));
        assertEquals(org.integratedmodelling.klab.api.knowledge.Contextualization.MEASURE,
            org.integratedmodelling.klab.api.knowledge.Contextualization.forSemantics(temporalSyntax));
        for(int repeat=0;repeat<2;repeat++)
          assertThrows(org.integratedmodelling.klab.api.exceptions.KlabValidationException.class,
              ()->actor.resolveObservable("imod:Oxygen within imod:Air imod:"+name+" in s"),name);
      }
      var eventDuration=actor.resolveObservable("imod:Duration of imod:IrregularEvent in s");
      assertNotNull(eventDuration);assertTrue(eventDuration.is(SemanticType.INTENSIVE));
      assertFalse(eventDuration.is(SemanticType.EXTENSIVE));
      assertEquals("imod:IrregularEvent",actor.inherent(eventDuration.getSemantics()).getUrn());
      assertTrue(actor.satisfiable(eventDuration.getSemantics()));
      // Even stale or forged consumer flags cannot override the ODO ancestry gate.
      var duration=actor.resolveConcept("imod:Duration");
      var originalDurationFlags=EnumSet.copyOf(duration.getType());
      try {
        duration.getType().remove(SemanticType.INTENSIVE);
        duration.getType().add(SemanticType.EXTENSIVE);
        assertTrue(duration.is(SemanticType.EXTENSIVE));
        assertThrows(org.integratedmodelling.klab.api.exceptions.KlabValidationException.class,
            ()->actor.resolveObservable("imod:Oxygen within imod:Air imod:Duration"));
      } finally {
        duration.getType().clear();duration.getType().addAll(originalDurationFlags);
      }
      assertEquals(cyclic.getUrn(),actor.inherent(frequency).getUrn());
      assertEquals(cyclic.getUrn(),actor.inherent(period).getUrn());
      assertEquals(cyclic.getUrn(),actor.inherent(actor.resolveConcept("imod:OscillationFrequency")).getUrn());
      assertEquals(cyclic.getUrn(),actor.inherent(actor.resolveConcept("imod:OscillationPeriod")).getUrn());
      assertFalse(actor.is(actor.resolveConcept("imod:IrregularEvent"),cyclic));
      for(var quality:List.of("Frequency","Period")) {
        var specialized=actor.resolveConcept("imod:"+quality+" of imod:Oscillation");
        assertNotNull(specialized);assertFalse(specialized.is(SemanticType.NOTHING));
        assertEquals("imod:Oscillation",actor.inherent(specialized).getUrn());
        for(var invalid:List.of("IrregularEvent","MaterialBody","Process")) {
          var rejected=actor.resolveConcept("imod:"+quality+" of imod:"+invalid);
          assertTrue(rejected==null || rejected.is(SemanticType.NOTHING),quality+" accepted "+invalid);
        }
      }
      var units=new UnitServiceImpl();
      assertTrue(units.isCompatible(eventDuration.getUnit(),units.getUnit("s")));
      assertEquals(60,units.getUnit("s").convert(1,units.getUnit("min")).doubleValue(),1e-12);
      var hz=actor.resolveObservable("imod:Frequency in Hz");
      var seconds=actor.resolveObservable("imod:Period in s");
      assertNotNull(hz);assertNotNull(seconds);
      assertTrue(units.isCompatible(hz.getUnit(),units.getUnit("s^-1")));
      assertTrue(units.isCompatible(seconds.getUnit(),units.getUnit("s")));
      assertFalse(units.isCompatible(hz.getUnit(),seconds.getUnit()));
      assertFalse(units.isCompatible(hz.getUnit(),units.getUnit("m")));
      var inheritedHz=actor.resolveObservable("imod:OscillationFrequency in Hz");
      var inheritedSeconds=actor.resolveObservable("imod:OscillationPeriod in s");
      assertTrue(units.isCompatible(inheritedHz.getUnit(),units.getUnit("s^-1")));
      assertTrue(units.isCompatible(inheritedSeconds.getUnit(),units.getUnit("s")));
      assertTrue(actor.satisfiable(frequency));assertTrue(actor.satisfiable(period));
    }
  }
}
