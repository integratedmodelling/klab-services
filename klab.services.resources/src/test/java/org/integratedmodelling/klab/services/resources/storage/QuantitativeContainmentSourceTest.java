package org.integratedmodelling.klab.services.resources.storage;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Path;
import java.util.Map;
import java.util.List;
import java.util.TreeMap;
import org.integratedmodelling.klab.api.knowledge.SemanticType;
import org.integratedmodelling.klab.api.services.runtime.Notification;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class QuantitativeContainmentSourceTest {
  @TempDir Path directory;
  @Test void worldviewDeclarationUsesQuantitativeWithinWhileSubstantialContainmentRemainsSeparate() throws Exception {
    var manager=WorkspaceDomainIngestionTest.manager(directory,Map.of("imod","""
        ontology imod in domain root version 1.0;
        abstract identity Identity is core odo:Identity;
        abstract volume Volume is core odo:Volume;
        abstract thing Subject is core odo:Subject;
        identity Oxygen; identity Air;
        volume OxygenVolume is imod:Oxygen within imod:Air imod:Volume;
        thing Region; thing Stream;
        thing LocalStream is imod:Stream within imod:Region;
        volume NativePartial is imod:Oxygen within imod:Air imod:Volume of imod:Stream;
        volume LocalPartial is imod:Oxygen within imod:Air imod:Volume of imod:LocalStream;
        """));
    var ontology=manager.getOntologies(false).iterator().next();
    assertTrue(ontology.getNotifications().stream().noneMatch(n->n.getLevel().severity>=Notification.Level.Error.severity),
        ontology.getNotifications().toString());
    var quantity=ontology.getStatements().stream().filter(s->s.getUrn().equals("OxygenVolume")).findFirst().orElseThrow();
    var parent=quantity.getDeclaredParent();
    assertTrue(parent.is(SemanticType.VOLUME));assertTrue(parent.is(SemanticType.EXTENSIVE));
    assertEquals("imod:Oxygen",parent.getPortionIdentity().getUrn());
    assertEquals("imod:Air",parent.getWholeIdentity().getUrn());
    assertNull(parent.getInherent());
    assertTrue(quantity.getObservationalContainments().isEmpty());
    var substantial=ontology.getStatements().stream().filter(s->s.getUrn().equals("LocalStream")).findFirst().orElseThrow();
    assertFalse(substantial.getObservationalContainments().isEmpty());
    assertNull(substantial.getDeclaredParent().getPortionIdentity());

    var world=new org.integratedmodelling.klab.api.knowledge.impl.WorldviewImpl();
    world.setUrn("imod");world.setWorldviewId("imod");world.setEmpty(false);world.setOntologies(List.of(ontology));
    var core=new TreeMap<String,byte[]>();
    for(var name:List.of("odo.owl","klab.owl","dc.owl")) {
      try(var input=getClass().getResourceAsStream("/knowledge/"+name)) {
        assertNotNull(input);core.put(name,input.readAllBytes());
      }
    }
    var factory=new org.integratedmodelling.klab.services.reasoner.internal.SemanticEnvironmentFactory(directory.resolve("compiled"),core);
    var compiled=factory.build(factory.capture(world),()->false);
    assertEquals(org.integratedmodelling.klab.services.reasoner.internal.CandidatePublicationCoordinator.LogicalStatus.VALID,
        compiled.logicalStatus(),compiled.diagnostics().toString());
    try(var state=compiled.state()) {
      var actor=state.actor();var alias=actor.resolveConcept("imod:OxygenVolume");
      var explicit=actor.resolveConcept(parent.getUrn());
      assertEquals("imod:Oxygen",alias.getMetadata().get(org.integratedmodelling.klab.services.reasoner.owl.QuantitativeContainmentSupport.PORTION));
      assertTrue(org.integratedmodelling.klab.services.reasoner.owl.QuantitativeContainmentSupport.sameOperands(alias,explicit));
      assertFalse(org.integratedmodelling.klab.services.reasoner.owl.QuantitativeContainmentSupport.sameOperands(alias,actor.resolveConcept("imod:Volume")));
      var aliasOntology=actor.owl().getOntology(alias.getNamespace()).getOWLOntology();
      var aliasClass=aliasOntology.getClassesInSignature().stream()
          .filter(c->c.getIRI().getShortForm().equals("OxygenVolume")).findFirst().orElseThrow();
      var restored=new org.integratedmodelling.klab.services.reasoner.owl.OWLMetadata(aliasClass,aliasOntology);
      assertEquals("imod:Air",restored.get(org.integratedmodelling.klab.services.reasoner.owl.QuantitativeContainmentSupport.WHOLE));
      long declarations=actor.owl().getOntologies(true).stream().flatMap(o->
          org.integratedmodelling.klab.services.reasoner.owl.ObservationalContainmentSupport.declarations(actor.owl(),o.getOWLOntology()).stream()).count();
      assertEquals(1,declarations,"Only explicit substantial within declares observational containment");
      assertFalse(org.integratedmodelling.klab.services.reasoner.owl.ContextualRequestAdmission.admits(
          actor.owl(),actor.resolveConcept("imod:NativePartial"),actor.resolveConcept("imod:Region")),
          "Quantitative identities cannot authorize a native Stream request in Region");
      assertTrue(org.integratedmodelling.klab.services.reasoner.owl.ContextualRequestAdmission.admits(
          actor.owl(),actor.resolveConcept("imod:LocalPartial"),actor.resolveConcept("imod:Region")),
          "The separate explicit LocalStream containment still authorizes request admission");
    }
  }
}
