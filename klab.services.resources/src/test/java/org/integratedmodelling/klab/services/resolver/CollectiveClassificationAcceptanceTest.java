package org.integratedmodelling.klab.services.resolver;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.integratedmodelling.klab.api.knowledge.*;
import org.integratedmodelling.klab.api.knowledge.impl.WorldviewImpl;
import org.integratedmodelling.klab.api.lang.kim.*;
import org.integratedmodelling.klab.api.lang.kim.impl.*;
import org.integratedmodelling.klab.api.services.ResourcesService;
import org.integratedmodelling.klab.api.services.runtime.Notification;
import org.integratedmodelling.klab.configuration.ServiceConfiguration;
import org.integratedmodelling.klab.services.reasoner.internal.*;
import org.integratedmodelling.klab.services.resources.storage.WorkspaceDomainIngestionTest;

/** Real ontology grammar, observable parser, semantic actor and model validation; no live stack. */
class CollectiveClassificationAcceptanceTest {
 @TempDir Path directory;
 @BeforeAll static void configure(){ServiceConfiguration.injectInstantiators();}
 @Test void bothAuthoredDependencyFormsHaveOneNativeMemberDomain() throws Exception {
  var manager=WorkspaceDomainIngestionTest.manager(Files.createDirectories(directory.resolve("source")),Map.of("imod","""
    ontology imod in domain root version 1.0;
    abstract thing Subject is core odo:Subject;
    abstract realm Realm is core odo:Realm;
    abstract realm PhysicalEnvironment is imod:Realm has disjoint children Terrestrial, Aquatic;
    realm Meadow is imod:Terrestrial;
    realm Leaf is imod:Realm;
    thing Region;
    thing Container;
    """));
  var ontology=manager.getOntologies(false).iterator().next();
  assertTrue(ontology.getNotifications().stream().noneMatch(n->n.getLevel()==Notification.Level.Error),ontology.getNotifications().toString());
  var world=new WorldviewImpl();world.setUrn("imod");world.setWorldviewId("imod");world.setEmpty(false);
  world.setOntologies(List.of(ontology));world.setObservationStrategies(List.of(ObservationPipelineTest.document()));
  byte[] core;
  try(var input=getClass().getResourceAsStream("/knowledge/odo.owl")){core=Objects.requireNonNull(input).readAllBytes();}
  var factory=new SemanticEnvironmentFactory(directory.resolve("actors"),Map.of("odo.owl",core));
  var captured=factory.capture(world);assertEquals(CandidatePublicationCoordinator.BeanStatus.VALID,captured.beanStatus(),captured.diagnostics().toString());
  var compiled=factory.build(captured,()->false);assertEquals(CandidatePublicationCoordinator.LogicalStatus.VALID,compiled.logicalStatus(),compiled.diagnostics().toString());
  try(var state=compiled.state()) {
   var actor=state.actor();var resources=actor.serviceScope().getService(ResourcesService.class);
   assertNull(CollectiveClassification.prepare(resources.declareObservable("each imod:Terrestrial imod:Region"),resources::declareConcept));
   for(var predicate:List.of("imod:PhysicalEnvironment","any imod:Terrestrial")) {
    for (boolean qualified : List.of(false,true)) {
    var source=resources.declareObservable(qualified ? "each "+predicate+" imod:Region" : predicate+" of each imod:Region");assertNotNull(source);
    var plan=CollectiveClassification.prepare(source,resources::declareConcept);assertNotNull(plan,source.getUrn());
    var members=actor.declareObservable(plan.members());assertFalse(members.isAbstract());
    assertEquals("each imod:Region",members.getUrn());assertEquals(1,plan.directives().size());
    var directive=actor.declareObservable(plan.directives().getFirst());assertNotNull(directive);
    assertEquals(Contextualization.CLASSIFICATION,directive.getContextualization());
    assertEquals(members.asConcept(),actor.directInherent(directive));
    var ordinary=actor.resolveObservable(predicate.startsWith("any ")?"imod:Terrestrial of each imod:Region":"imod:PhysicalEnvironment of each imod:Region");
    assertEquals(ordinary.getUrn(),directive.getUrn());
    var model=new KimModelImpl();model.setUrn("fixture.classified");
    model.getObservables().add(resources.declareObservable("imod:Container"));model.getDependencies().add(source);
    var lexical=mock(org.integratedmodelling.klab.runtime.language.KimObservableVisitor.Context.class);
    doReturn(ontology).when(lexical).getDocument();
    var validator=new DocumentSemanticValidator(actor,true,true);
    var issues=validator.validateModel(model,lexical);
    assertTrue(issues.stream().noneMatch(n->n.getLevel()==Notification.Level.Error),issues.toString());
    assertEquals(1,validator.getPendingClassifierDependencies().size());
    var executable=new DocumentSemanticValidator(actor,true,false);
    var executableIssues=executable.validateModel(model,lexical);
    assertTrue(executableIssues.stream().noneMatch(n->n.getLevel()==Notification.Level.Error),executableIssues.toString());
    assertEquals(1,executable.getPendingClassifierDependencies().size(),"Strict admission must retain the collective classification obligation");
    // Named models must compare canonical semantics without discarding saved ANY syntax.
    var saved=new KimNamespaceImpl();saved.setUrn("fixture");saved.getStatements().add(model);
    var selected=mock(Model.class);when(selected.getNamespace()).thenReturn("fixture");when(selected.getUrn()).thenReturn(model.getUrn());
    var scope=mock(org.integratedmodelling.klab.api.scope.ContextScope.class);
    var sourceResources=mock(ResourcesService.class);
    when(sourceResources.retrieve("fixture",KimNamespace.class,scope)).thenReturn(saved);
    when(sourceResources.declareConcept(anyString())).thenAnswer(i->resources.declareConcept(i.getArgument(0,String.class)));
    var canonical=actor.declareObservable(source);
    var sourceUrn=source.getUrn();
    assertNotNull(ResolutionCompiler.prepareCollectiveDependencySource(selected,canonical,0,sourceResources,actor,scope));
    assertEquals(sourceUrn,source.getUrn(),"Saved selectors must stay authoritative and unchanged");
    assertThrows(org.integratedmodelling.klab.api.exceptions.KlabValidationException.class,
        ()->ResolutionCompiler.prepareCollectiveDependencySource(selected,actor.resolveObservable("imod:Aquatic of each imod:Region"),0,sourceResources,actor,scope));
    assertThrows(org.integratedmodelling.klab.api.exceptions.KlabValidationException.class,
        ()->ResolutionCompiler.prepareCollectiveDependencySource(selected,actor.resolveObservable("imod:PhysicalEnvironment of each imod:Container"),0,sourceResources,actor,scope));
    var optional=new org.integratedmodelling.common.knowledge.ObservableImpl((org.integratedmodelling.common.knowledge.ObservableImpl)canonical);
    optional.setOptional(!canonical.isOptional());
    assertThrows(org.integratedmodelling.klab.api.exceptions.KlabValidationException.class,
        ()->ResolutionCompiler.prepareCollectiveDependencySource(selected,optional,0,sourceResources,actor,scope));
    var unavailable=mock(org.integratedmodelling.klab.api.services.Reasoner.class);
    assertThrows(org.integratedmodelling.klab.api.exceptions.KlabValidationException.class,
        ()->ResolutionCompiler.prepareCollectiveDependencySource(selected,canonical,0,sourceResources,unavailable,scope));
    when(unavailable.declareObservable(source)).thenReturn(org.integratedmodelling.klab.api.knowledge.Observable.nothing("unavailable"));
    assertThrows(org.integratedmodelling.klab.api.exceptions.KlabValidationException.class,
        ()->ResolutionCompiler.prepareCollectiveDependencySource(selected,canonical,0,sourceResources,unavailable,scope));
    model.getDependencies().set(0,resources.declareObservable("each imod:Terrestrial imod:Region"));
    assertNull(ResolutionCompiler.prepareCollectiveDependencySource(selected,canonical,0,sourceResources,actor,scope),"Ordinary concrete request stays on its existing path");
    model.getDependencies().set(0,source);
    }
   }
   var restricted=actor.resolveConcept("imod:PhysicalEnvironment of each imod:Region");
   assertTrue(restricted.isAbstract());
   var closure=actor.closure(actor.resolveConcept("imod:PhysicalEnvironment"));
   var abstractSelf=actor.resolveConcept("imod:PhysicalEnvironment");
   assertTrue(closure.contains(abstractSelf),"Abstract self belongs to semantic closure");
   assertFalse(closure.stream().filter(c->!c.isAbstract()).toList().contains(abstractSelf),"Only concrete filtering removes abstract self");
   var leaf=actor.resolveConcept("imod:Leaf");
   assertEquals(Set.of(leaf),new HashSet<>(actor.closure(leaf)),"Concrete leaf closure contains itself");
   var parent=actor.resolveConcept("imod:Terrestrial");
   assertTrue(actor.closure(parent).containsAll(List.of(parent,actor.resolveConcept("imod:Meadow"))),"Concrete parent closure contains self and descendants");
   assertTrue(closure.stream().anyMatch(c->c.getUrn().equals(restricted.getUrn())),"Materialized predicate must be in the closure fixture");
   for(var child:closure)assertEquals(actor.resolveConcept(child.getUrn()).isAbstract(),child.isAbstract(),
       "Public closure must preserve canonical predicate abstractness: "+child.getUrn());
   var raw=new org.integratedmodelling.common.knowledge.ConceptImpl(
       (org.integratedmodelling.common.knowledge.ConceptImpl)restricted);
   raw.setAbstract(false); // Reproduce the derived OWL DTO before expression projection.
   var guarded=spy(actor);
   var unavailableScope=mock(org.integratedmodelling.klab.api.scope.ServiceScope.class);
   var unavailableResources=mock(ResourcesService.class);
   doReturn(unavailableScope).when(guarded).serviceScope();
   when(unavailableScope.getService(ResourcesService.class)).thenReturn(unavailableResources);
   var project=actor.getClass().getDeclaredMethod("effectiveConcept",Concept.class);
   project.setAccessible(true);
   assertTrue(((Concept)project.invoke(guarded,raw)).isAbstract(),"Unavailable syntax must fail closed");
   when(unavailableResources.declareConcept(raw.getUrn())).thenThrow(new IllegalStateException("unavailable"));
   assertTrue(((Concept)project.invoke(guarded,raw)).isAbstract(),"Failed syntax lookup must fail closed");
   assertFalse(raw.isAbstract(),"Fallback must not mutate the canonical/raw concept");
  }
 }
}
