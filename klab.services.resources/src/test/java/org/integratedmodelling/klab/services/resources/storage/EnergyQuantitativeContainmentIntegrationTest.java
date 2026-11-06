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
import org.integratedmodelling.klab.services.reasoner.owl.QuantitativeContainmentSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Private coordinated-artifact experiment: semantic construction and units, no computed observation. */
class EnergyQuantitativeContainmentIntegrationTest {
  @TempDir Path directory;
  @Test void correctedOdoAndBuiltLanguageApiConstructEnergyPortionInJoules() throws Exception {
    ServiceConfiguration.injectInstantiators();
    var manager=WorkspaceDomainIngestionTest.manager(directory,Map.of("imod","""
        ontology imod in domain root version 1.0;
        abstract identity Identity is core odo:Identity;
        abstract energy Energy is core odo:Energy;
        abstract area Area is core odo:Area;
        abstract temperature Temperature is core odo:Temperature;
        abstract money Money is core odo:MonetaryValue;
        area ExposedArea is imod:Area;
        quality GenericArea is imod:ExposedArea;
        identity Oxygen; identity Air;
        area OxygenArea is imod:Oxygen within imod:Air imod:ExposedArea;
        energy OxygenEnergy is imod:Oxygen within imod:Air imod:Energy;
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
    try(var state=compiled.state()) {
      var quantity=state.actor().resolveObservable("imod:Oxygen within imod:Air imod:Energy in J");
      assertNotNull(quantity);assertTrue(quantity.is(SemanticType.ENERGY));assertTrue(quantity.is(SemanticType.EXTENSIVE));
      assertFalse(quantity.is(SemanticType.INTENSIVE));assertFalse(quantity.is(SemanticType.RATIO));
      assertEquals("imod:Oxygen",quantity.getSemantics().getMetadata().get(QuantitativeContainmentSupport.PORTION));
      assertEquals("imod:Air",quantity.getSemantics().getMetadata().get(QuantitativeContainmentSupport.WHOLE));
      assertNotNull(quantity.getUnit());
      var units=new UnitServiceImpl();assertTrue(units.isCompatible(quantity.getUnit(),units.getUnit("J")));
      var area=state.actor().resolveObservable("imod:Oxygen within imod:Air imod:ExposedArea in m2");
      assertNotNull(area);assertTrue(area.is(SemanticType.AREA));assertTrue(area.is(SemanticType.EXTENSIVE));
      assertTrue(units.isCompatible(area.getUnit(),units.getUnit("m2")));
      assertTrue(QuantitativeContainmentSupport.sameOperands(area.getSemantics(),state.actor().resolveConcept("imod:OxygenArea")));
      var generic=state.actor().resolveObservable("imod:Oxygen within imod:Air imod:GenericArea in m2");
      assertNotNull(generic);assertTrue(generic.is(SemanticType.AREA));
      assertTrue(units.isCompatible(generic.getUnit(),units.getUnit("m2")));
      for(var invalid:List.of("imod:Temperature","imod:Money")) {
        assertThrows(org.integratedmodelling.klab.api.exceptions.KlabValidationException.class,
            ()->state.actor().resolveObservable("imod:Oxygen within imod:Air "+invalid));
      }
      var alias=state.actor().resolveConcept("imod:OxygenEnergy");
      assertTrue(QuantitativeContainmentSupport.sameOperands(alias,quantity.getSemantics()));
    }
  }
}
