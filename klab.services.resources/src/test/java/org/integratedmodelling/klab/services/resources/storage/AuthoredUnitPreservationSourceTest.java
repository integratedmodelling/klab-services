package org.integratedmodelling.klab.services.resources.storage;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.*;
import java.util.*;
import org.integratedmodelling.klab.api.data.mediation.impl.UnitImpl;
import org.integratedmodelling.klab.api.exceptions.KlabValidationException;
import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.api.knowledge.impl.WorldviewImpl;
import org.integratedmodelling.klab.api.lang.kim.*;
import org.integratedmodelling.klab.api.services.UnitService;
import org.integratedmodelling.klab.api.services.runtime.Notification;
import org.integratedmodelling.klab.configuration.ServiceConfiguration;
import org.integratedmodelling.klab.services.reasoner.internal.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

/** Actual worldview/k.IM parsing and declaration; no model execution or unit-kind policy bypass. */
class AuthoredUnitPreservationSourceTest {
  @TempDir static Path directory;
  static WorkspaceManager manager;
  static SemanticEnvironment environment;

  @BeforeAll static void load() throws Exception {
    manager=WorkspaceDomainIngestionTest.manager(Files.createDirectories(directory.resolve("source")),Map.of("imod","""
        ontology imod in domain root version 1.0;
        abstract thing Subject is core odo:Subject;
        abstract agent Agent is core odo:Agent;
        abstract mass Mass is core odo:Mass;
        thing Sample;
        agent Surveyor;
        mass SampleMass is imod:Mass of imod:Sample;
        """));
    var worldview=new WorldviewImpl();worldview.setUrn("imod");worldview.setWorldviewId("imod");worldview.setEmpty(false);
    worldview.setOntologies(new ArrayList<>(manager.getOntologies(false)));
    var factory=new SemanticEnvironmentFactory(directory.resolve("actors"),CoreOntologyCatalog.bundledCore());
    var captured=factory.capture(worldview);
    assertEquals(CandidatePublicationCoordinator.BeanStatus.VALID,captured.beanStatus(),captured.diagnostics().toString());
    var compiled=factory.build(captured,()->false);
    assertEquals(CandidatePublicationCoordinator.LogicalStatus.VALID,compiled.logicalStatus(),compiled.diagnostics().toString());
    environment=compiled.state();
    WorkspaceDomainIngestionTest.set(manager,"namespaceParser",new WorkspaceManager.KimParser());
  }
  @AfterAll static void close() throws Exception { if(environment!=null)environment.close(); }

  static KimObservable parse(String expression) throws Exception {
    var method=WorkspaceManager.class.getDeclaredMethod("readNamespace",String.class,long.class,String.class,String.class);
    method.setAccessible(true);
    var namespace=(KimNamespace)method.invoke(manager,"namespace units.validation version 1.0;\nmodel "+expression+" set to [20];",1L,"fixture","units.validation");
    assertTrue(namespace.getNotifications().stream().noneMatch(n->n.getLevel()==Notification.Level.Error),namespace.getNotifications().toString());
    return ((KimModel)namespace.getStatements().getFirst()).getObservables().getFirst();
  }
  static UnitService units() { return ServiceConfiguration.INSTANCE.getService(UnitService.class); }

  @Test void authoredGramsSurviveDeclarationRebuildTransportAndRealConversion() throws Exception {
    var syntax=parse("imod:SampleMass in g");assertEquals("g",syntax.getUnit());
    var actor=environment.actor();var declared=actor.declareObservable(syntax);
    assertNotNull(declared.getUnit());assertEquals("g",((UnitImpl)declared.getUnit()).getDefinition());
    assertEquals("imod:SampleMass in g",declared.getUrn());
    assertEquals(0.02,units().convert(20,units().getUnit("kg"),declared.getUnit()).doubleValue(),1e-12);
    var stringBuilt=SemanticsBuilder.create(actor.resolveConcept("imod:SampleMass"),actor,actor.serviceScope()).withUnit("g").buildObservable();
    var unitBuilt=SemanticsBuilder.create(actor.resolveConcept("imod:SampleMass"),actor,actor.serviceScope()).withUnit(units().getUnit("g")).buildObservable();
    assertEquals(declared.getUrn(),stringBuilt.getUrn());assertEquals(declared.getUrn(),unitBuilt.getUrn());
    var mapper=org.integratedmodelling.common.data.jackson.JacksonConfiguration.newObjectMapper();
    var restored=mapper.readValue(mapper.writerFor(Observable.class).writeValueAsBytes(declared),Observable.class);
    assertEquals("g",restored.getUnit().toString());assertEquals(declared.getUrn(),restored.getUrn());
    assertEquals(declared.getUrn(),SemanticsBuilder.create(restored,actor,actor.serviceScope()).buildObservable().getUrn());
    assertEquals(0.02,units().convert(20,units().getUnit("kg"),restored.getUnit()).doubleValue(),1e-12);
  }

  @Test void invalidAuthoredUnitsFailAndNullClearsLikeTheUnitOverload() throws Exception {
    var actor=environment.actor();
    assertThrows(KlabValidationException.class,()->actor.declareObservable(parse("imod:SampleMass in not_a_unit")));
    var builder=SemanticsBuilder.create(actor.resolveConcept("imod:SampleMass"),actor,actor.serviceScope());
    assertThrows(KlabValidationException.class,()->builder.withUnit(" "));
    assertNull(builder.withUnit("g").withUnit((String)null).buildObservable().getUnit());
    var other=SemanticsBuilder.create(actor.resolveConcept("imod:SampleMass"),actor,actor.serviceScope());
    assertNull(other.withUnit(units().getUnit("g")).withUnit((org.integratedmodelling.klab.api.data.mediation.Unit)null).buildObservable().getUnit());
  }

  @Test void observerAndUnitHaveStableCanonicalIdentityWithoutLosingNativeSemantics() throws Exception {
    var actor=environment.actor();var absolute=actor.declareObservable(parse("imod:SampleMass in g"));
    var observer=actor.resolveConcept("imod:Surveyor");
    var explicit=SemanticsBuilder.create(absolute,actor,actor.serviceScope()).withObserverSemantics(observer).buildObservable();
    var refined=org.integratedmodelling.common.knowledge.ObservableImpl.withObserverSemantics(absolute,observer);
    assertEquals("imod:SampleMass observed as imod:Surveyor in g",explicit.getUrn());
    assertEquals(explicit.getUrn(),refined.getUrn());
    assertEquals(absolute.asConcept().getUrn(),explicit.asConcept().getUrn());
    assertEquals("imod:Sample",actor.inherent(explicit).getUrn());
    var mapper=org.integratedmodelling.common.data.jackson.JacksonConfiguration.newObjectMapper();
    var transported=mapper.readValue(mapper.writerFor(Observable.class).writeValueAsBytes(explicit),Observable.class);
    assertEquals(explicit.getUrn(),SemanticsBuilder.create(transported,actor,actor.serviceScope()).buildObservable().getUrn());
    assertEquals("g",transported.getUnit().toString());
  }
}
