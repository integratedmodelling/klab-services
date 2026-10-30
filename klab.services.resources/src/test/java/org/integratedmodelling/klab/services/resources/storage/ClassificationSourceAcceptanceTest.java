package org.integratedmodelling.klab.services.resources.storage;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.integratedmodelling.klab.api.knowledge.*;
import org.integratedmodelling.klab.api.knowledge.impl.WorldviewImpl;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.lang.kim.*;
import org.integratedmodelling.klab.api.services.Reasoner;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.services.runtime.Notification;
import org.integratedmodelling.klab.configuration.ServiceConfiguration;
import org.integratedmodelling.klab.services.reasoner.internal.*;
import org.integratedmodelling.klab.runtime.language.SourceModelMapper;

/** Actual staging sources through parsing, OWL validation and source projection; no live services. */
class ClassificationSourceAcceptanceTest {
 @TempDir Path directory;
 @BeforeAll static void configure(){ServiceConfiguration.injectInstantiators();}
 @Test void stagingClassificationAndTentativeQueriesParseAndProject() throws Exception {
  var staging=Path.of(System.getProperty("klab.test.staging.directory"));
  var manager=WorkspaceDomainIngestionTest.manager(Files.createDirectories(directory.resolve("source")),Map.of(
   "earth", """
    ontology earth in domain root version 1.0;
    abstract thing Subject is core odo:Subject;
    abstract realm Realm is core odo:Realm;
    abstract realm PhysicalEnvironment is earth:Realm has disjoint children Terrestrial, Aquatic;
    thing Region;
    """,
   "geography", """
    ontology geography in domain root version 1.0;
    abstract length Length is core odo:Length;
    length Elevation is geography:Length;
    """));
  var world=new WorldviewImpl();world.setUrn("earth");world.setWorldviewId("earth");world.setEmpty(false);
  world.setOntologies(new ArrayList<>(manager.getOntologies(false)));
  var factory=new SemanticEnvironmentFactory(directory.resolve("actors"),CoreOntologyCatalog.bundledCore());
  var captured=factory.capture(world);assertEquals(CandidatePublicationCoordinator.BeanStatus.VALID,captured.beanStatus(),captured.diagnostics().toString());
  var compiled=factory.build(captured,()->false);assertEquals(CandidatePublicationCoordinator.LogicalStatus.VALID,compiled.logicalStatus(),compiled.diagnostics().toString());
  WorkspaceDomainIngestionTest.set(manager,"namespaceParser",new WorkspaceManager.KimParser());
  var read=WorkspaceManager.class.getDeclaredMethod("readNamespace",String.class,long.class,String.class,String.class);read.setAccessible(true);
  try(var state=compiled.state()) {
   var actor=state.actor();var reasoner=mock(Reasoner.class);var scope=mock(ContextScope.class);
   when(scope.getService(Reasoner.class)).thenReturn(reasoner);
   when(reasoner.resolveObservable(anyString())).thenAnswer(i->actor.resolveObservable(i.getArgument(0)));
   when(reasoner.resolveConcept(anyString())).thenAnswer(i->actor.resolveConcept(i.getArgument(0)));
   for(var name:List.of("classification","queries")) {
    var source=Files.readString(staging.resolve("src/staging/vxii/test/"+name+".kim"));
    var namespace=(KimNamespace)read.invoke(manager,source,1L,"klab.staging.vxii","staging.vxii.test."+name);
    assertTrue(namespace.getNotifications().stream().noneMatch(n->n.getLevel()==Notification.Level.Error),namespace.getNotifications().toString());
    var diagnostics=new DocumentSemanticValidator(actor).validate(namespace);
    assertTrue(diagnostics.stream().noneMatch(n->n.getLevel()==Notification.Level.Error),diagnostics.toString());
    if(name.equals("classification")) {
     var models=namespace.getStatements().stream().filter(KimModel.class::isInstance).map(KimModel.class::cast).toList();assertEquals(3,models.size());
     var projected=models.stream().map(m->SourceModelMapper.load(m,namespace,scope)).toList();
     assertEquals("earth:Terrestrial earth:Region",projected.getFirst().getObservables().getFirst().getUrn());
     assertEquals(Contextualization.CLASSIFICATION,projected.getFirst().getDependencies().getFirst().getContextualization());
     assertEquals(Contextualization.CLASSIFICATION,projected.get(1).getObservables().getFirst().getContextualization());
     assertEquals(1,projected.get(1).getComputation().size());
     assertTrue(projected.get(2).getObservables().getFirst().getSemantics().isCollective());
    } else {
     var definitions=namespace.getStatements().stream().filter(KimSymbolDefinition.class::isInstance).map(KimSymbolDefinition.class::cast).toList();assertEquals(4,definitions.size());
     for(var definition:definitions) {
      var observation=new Observation.NaiveBuilder(definition,scope).build();
      assertFalse(observation.isEmpty(),observation.getNotifications().toString());
      assertEquals("query".equals(definition.getDefineClass())?Observation.QUERY_ID:Observation.UNASSIGNED_ID,observation.getId());
      assertNotNull(observation.getGeometry());
     }
    }
   }
  }
 }
}
