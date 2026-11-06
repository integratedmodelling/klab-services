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
  @Test void cycleQualitiesShareBearerAndRetainDistinctDimensions() throws Exception {
    ServiceConfiguration.injectInstantiators();
    var manager=WorkspaceDomainIngestionTest.manager(directory,Map.of("imod","""
        ontology imod in domain root version 1.0;
        abstract process Process is core odo:Process;
        abstract event Event is core odo:Event;
        abstract thing Subject is core odo:Subject;
        abstract quantity CycleFrequency is core odo:Frequency;
        abstract duration Duration is core odo:Duration;
        abstract process CyclicProcess is imod:Process;
        abstract quantity Frequency is imod:CycleFrequency of imod:CyclicProcess;
        abstract duration Period is imod:Duration of imod:CyclicProcess decreases with imod:Frequency;
        process Oscillation is imod:CyclicProcess;
        quantity OscillationFrequency is imod:Frequency;
        duration OscillationPeriod is imod:Period;
        event IrregularEvent is imod:Event;
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
