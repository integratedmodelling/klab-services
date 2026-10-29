package org.integratedmodelling.klab.services.reasoner.owl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import org.integratedmodelling.klab.api.data.Metadata;
import org.integratedmodelling.klab.api.knowledge.*;
import org.integratedmodelling.klab.api.knowledge.impl.WorldviewImpl;
import org.integratedmodelling.klab.api.lang.kim.*;
import org.integratedmodelling.klab.api.lang.kim.impl.*;
import org.integratedmodelling.klab.api.scope.UserScope;
import org.integratedmodelling.klab.api.services.ResourcesService;
import org.integratedmodelling.klab.api.services.reasoner.objects.*;
import org.integratedmodelling.klab.api.services.resources.ResourceSet;
import org.integratedmodelling.klab.api.services.runtime.Notification;
import org.integratedmodelling.klab.services.reasoner.ReasonerService;
import org.integratedmodelling.klab.services.reasoner.internal.*;
import org.integratedmodelling.klab.services.scopes.ScopeManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.model.*;

class CoreAliasDocumentationTest {
  @TempDir Path directory;
  private static final String BACKGROUND = "Agent is a Substantial with intentionality.";

  private Map<String,byte[]> core() throws Exception {
    var out=new HashMap<String,byte[]>();
    for(var name:List.of("odo.owl","klab.owl","dc.owl"))
      try(var stream=getClass().getClassLoader().getResourceAsStream("knowledge/"+name)) {
        assertNotNull(stream,name);out.put(name,stream.readAllBytes());
      }
    return out;
  }
  private KimConceptStatementImpl declaration(String name,SemanticType type) {
    var d=new KimConceptStatementImpl();d.setUrn(name);d.setNamespace("w");d.setProjectName("fixture");
    d.setOffsetInDocument(10);d.setLength(20);d.setType(EnumSet.of(type,SemanticType.OBSERVABLE));
    return d;
  }
  private KimConceptStatementImpl alias(String name,String text) {
    var d=declaration(name,SemanticType.AGENT);d.setAbstract(true);d.getType().add(SemanticType.ABSTRACT);
    d.setUpperConceptDefined("odo:Agent");d.setDocstring(text);return d;
  }
  private KimConceptImpl ref(String name) {var r=new KimConceptImpl();r.setName(name);r.setUrn(name);return r;}
  private WorldviewImpl world(String version,String text,boolean reverse,boolean conflict,boolean remove) {
    var root=new KimOntologyImpl();root.setUrn("w");root.setProjectName("fixture");root.setSourceCode(" ".repeat(1100)+version+text);
    var thing=declaration("Thing",SemanticType.SUBJECT);thing.setUpperConceptDefined("odo:Subject");
    var quality=declaration("Value",SemanticType.QUALITY);quality.setUpperConceptDefined("odo:Quality");
    root.getStatements().addAll(List.of(thing,quality));
    if(!remove)root.getStatements().add(alias("Agent",text));
    if(reverse) {root.getStatements().add(0,alias("ZAgent","Second description"));Collections.reverse(root.getStatements());}
    var group=declaration("Group",SemanticType.SUBJECT);group.setDeclaredParent(ref("w:Thing"));group.setChildrenDisjoint(true);
    group.getChildren().addAll(List.of(declaration("A",SemanticType.SUBJECT),declaration("B",SemanticType.SUBJECT)));
    var first=declaration("Q1",SemanticType.QUALITY);first.setDeclaredParent(ref("w:Value"));first.setDeclaredInherent(ref("w:A"));
    root.getStatements().addAll(List.of(group,first));
    if(conflict){var bad=declaration("Q2",SemanticType.QUALITY);bad.setDeclaredParent(ref("w:Q1"));bad.setDeclaredInherent(ref("w:B"));root.getStatements().add(bad);}
    var w=new WorldviewImpl();w.setUrn("w");w.setWorldviewId("w");w.setEmpty(false);w.setOntologies(new ArrayList<>(List.of(root)));return w;
  }
  private SemanticEnvironmentFactory factory() throws Exception {return new SemanticEnvironmentFactory(directory.resolve(UUID.randomUUID().toString()),core());}
  private CandidatePublicationCoordinator.Result<SemanticEnvironment> build(SemanticEnvironmentFactory factory,Worldview world)throws Exception {
    var captured=factory.capture(world);return factory.build(captured,()->false);
  }
  private static void description(Concept concept,String expected) {
    assertEquals(expected,((org.integratedmodelling.klab.api.collections.impl.MetadataImpl)concept.getMetadata()).getDescription());
    assertEquals(expected,StyledKimToken.create(concept).getDescription());
    assertEquals("odo:Agent",concept.getUrn());assertTrue(concept.isAbstract());
  }
  private ReasonerService facade(SemanticEnvironmentFactory factory)throws Exception {
    var service=mock(ReasonerService.class,invocation->{
      if(invocation.getMethod().getName().equals("getUrl"))return java.net.URI.create("http://127.0.0.1").toURL();
      if(invocation.getMethod().getName().equals("setOperational"))return null;
      if(invocation.getMethod().getName().equals("serviceId"))return "deployed-test-reasoner";
      return invocation.callRealMethod();
    });service.enableCandidatePublication(factory);return service;
  }

  @Test void storesSourceOwnedInterpretationOnOriginalIriAndBothDescriptionConsumers()throws Exception {
    var world=world("A","Worldview meaning",false,false,false);var built=build(factory(),world);
    assertEquals(CandidatePublicationCoordinator.LogicalStatus.VALID,built.logicalStatus(),built.diagnostics().toString());
    try(var state=built.state()) {
      var actor=state.actor();var alias=actor.resolveConcept("w:Agent");var original=actor.resolveConcept("odo:Agent");
      description(alias,"Worldview meaning");description(original,"Worldview meaning");
      assertTrue(actor.satisfiable(alias));assertTrue(actor.is(alias,original));
      var owner=actor.owl().getOntology("w").getOWLOntology();var core=actor.owl().getOntology("odo").getOWLOntology();
      var iri=IRI.create("http://integratedmodelling.org/odo#Agent");
      var annotations=owner.getAnnotationAssertionAxioms(iri);assertEquals(1,annotations.size());
      var axiom=annotations.iterator().next();assertEquals("Worldview meaning",((OWLLiteral)axiom.getValue()).getLiteral());
      var provenance=axiom.getAnnotations().toString();assertTrue(provenance.contains("w:Agent"));assertTrue(provenance.contains("interpretationWorldview"));
      assertTrue(provenance.contains(SemanticValidationRequest.sourceHash(world.getOntologies().getFirst().getSourceCode())));
      assertTrue(provenance.contains("interpretationOffset"));assertTrue(provenance.contains("interpretationLength"));
      assertTrue(core.getAnnotationAssertionAxioms(iri).stream().anyMatch(a->a.getValue().toString().contains(BACKGROUND)));
      assertFalse(core.getAnnotationAssertionAxioms(iri).stream().anyMatch(a->a.getValue().toString().contains("Worldview meaning")));
      var originalManager=OWLManager.createOWLOntologyManager();
      var originalCore=originalManager.loadOntologyFromOntologyDocument(new java.io.ByteArrayInputStream(core().get("odo.owl")));
      assertEquals(originalCore.getLogicalAxioms(),core.getLogicalAxioms());
      assertTrue(core.getAxioms().containsAll(originalCore.getAxioms()));
      assertFalse(owner.getClassesInSignature().stream().anyMatch(c->c.getIRI().toString().endsWith("/w#Agent")));
      var docs=actor.info("odo:Agent",KlabAsset.KnowledgeClass.CONCEPT,String.class,null);
      assertTrue(docs.contains("Hosted worldview interpretation"));assertTrue(docs.indexOf("Worldview meaning")<docs.indexOf(BACKGROUND));
    }
  }

  @Test void warmedCanonicalAndDelegateViewsRefreshOnReplacementBlankAndOwnerRelease()throws Exception {
    var world=world("A","A meaning",false,false,false);var built=build(factory(),world);
    try(var state=built.state()) {
      var actor=state.actor();var alias=actor.resolveConcept("w:Agent");var original=actor.resolveConcept("odo:Agent");
      original.getMetadata().put("unrelated","preserved");
      var d=alias("Agent","B meaning");actor.build(d,actor.owl().getOntology("w"),null,actor.serviceScope());
      description(alias,"B meaning");description(original,"B meaning");
      description(actor.resolveConcept("w:Agent"),"B meaning");assertEquals("preserved",original.getMetadata().get("unrelated"));
      d.setDocstring(null);actor.build(d,actor.owl().getOntology("w"),null,actor.serviceScope());
      description(alias,BACKGROUND);description(original,BACKGROUND);
      d.setDocstring("Temporary meaning");actor.build(d,actor.owl().getOntology("w"),null,actor.serviceScope());
      actor.owl().releaseOntology(actor.owl().getOntology("w"));
      description(original,BACKGROUND);assertTrue(actor.owl().coreAliasDocumentation().interpretations(original).isEmpty());
    }
  }

  @Test void allAliasesContributeInStableOrderAndRemovingOneRetainsTheOther()throws Exception {
    var first=world("two","First description",true,false,false);
    var second=world("two","First description",true,false,false);Collections.reverse(second.getOntologies().getFirst().getStatements());
    var left=build(factory(),first);var right=build(factory(),second);
    try(var a=left.state();var b=right.state()) {
      var expected="w:Agent: First description\n\nw:ZAgent: Second description";
      description(a.actor().resolveConcept("odo:Agent"),expected);description(b.actor().resolveConcept("odo:Agent"),expected);
      description(a.actor().resolveConcept("w:Agent"),expected);description(a.actor().resolveConcept("w:ZAgent"),expected);
      var d=alias("Agent",null);a.actor().build(d,a.actor().owl().getOntology("w"),null,a.actor().serviceScope());
      description(a.actor().resolveConcept("odo:Agent"),"Second description");
      assertEquals(List.of("w:ZAgent"),a.actor().owl().coreAliasDocumentation().interpretations(a.actor().resolveConcept("odo:Agent")).stream().map(CoreAliasDocumentation.Interpretation::declaration).toList());
      var removed=world("removed alias","First description",true,false,false);
      removed.getOntologies().getFirst().getStatements().removeIf(statement -> statement.getUrn().equals("Agent"));
      var rebuilt=build(factory(),removed);
      try(var c=rebuilt.state()) {
        description(c.actor().resolveConcept("odo:Agent"),"Second description");
        description(c.actor().resolveConcept("w:ZAgent"),"Second description");
        assertTrue(c.actor().resolveConcept("w:Agent").is(SemanticType.NOTHING));
      }
    }
  }

  @Test void crossNamespaceAliasContributionSurvivesOtherNamespaceReleaseAndReload()throws Exception {
    var world=world("root","Root meaning",false,false,false);
    var other=new KimOntologyImpl();other.setUrn("z");other.setProjectName("fixture");
    other.setSourceCode(" ".repeat(1100)+"Other meaning");other.getImportedOntologies().add("w");
    var declaration=alias("OtherAgent","Other meaning");declaration.setNamespace("z");
    declaration.setUpperConceptDefined(null);declaration.setAlias(true);declaration.setDeclaredParent(ref("w:Agent"));
    other.getStatements().add(declaration);world.getOntologies().add(other);
    var built=build(factory(),world);
    assertEquals(CandidatePublicationCoordinator.LogicalStatus.VALID,built.logicalStatus(),built.diagnostics().toString());
    try(var state=built.state()) {
      var actor=state.actor();var canonical=actor.resolveConcept("odo:Agent");
      var rootAlias=actor.resolveConcept("w:Agent");var otherAlias=actor.resolveConcept("z:OtherAgent");
      String both="w:Agent: Root meaning\n\nz:OtherAgent: Other meaning";
      description(canonical,both);description(rootAlias,both);description(otherAlias,both);
      var rootAxioms=Set.copyOf(actor.owl().getOntology("w").getOWLOntology().getAxioms());
      actor.owl().releaseOntology(actor.owl().getOntology("z"));
      description(canonical,"Root meaning");description(rootAlias,"Root meaning");
      assertEquals(rootAxioms,actor.owl().getOntology("w").getOWLOntology().getAxioms());
      assertEquals(List.of("w:Agent"),actor.owl().coreAliasDocumentation().interpretations(canonical).stream().map(CoreAliasDocumentation.Interpretation::declaration).toList());
      // Supply the replacement source context, as updateKnowledge does before compiling it.
      other.setSourceCode(" ".repeat(1100)+"Reloaded meaning");declaration.setDocstring("Reloaded meaning");
      var field=ReasonerService.class.getDeclaredField("worldview");field.setAccessible(true);field.set(actor,world);
      actor.build(declaration,actor.owl().requireOntology("z"),null,actor.serviceScope());
      String reloaded="w:Agent: Root meaning\n\nz:OtherAgent: Reloaded meaning";
      description(canonical,reloaded);description(rootAlias,reloaded);description(actor.owl().getConcept("z:OtherAgent"),reloaded);
      var contributions=actor.owl().coreAliasDocumentation().interpretations(canonical);
      assertEquals(2,contributions.size());assertEquals(SemanticValidationRequest.sourceHash(other.getSourceCode()),contributions.getLast().sourceHash());
      assertEquals(rootAxioms,actor.owl().getOntology("w").getOWLOntology().getAxioms());
    }
  }

  @Test void fullPublicationReloadAndRejectedCandidateKeepAcceptedDescriptionsIsolated()throws Exception {
    var service=facade(factory());var scope=new SemanticEnvironmentScope(new CapturedSemanticSourceResolver(world("scope","A",false,false,false)).service());
    try(var runtime=service.candidateRuntime()) {
      service.loadKnowledge(world("A","A meaning",false,false,false),scope);assertTrue(runtime.awaitIdle(Duration.ofSeconds(30)));
      description(service.resolveConcept("w:Agent"),"A meaning");description(service.resolveConcept("odo:Agent"),"A meaning");
      service.loadKnowledge(world("B","B meaning",false,false,false),scope);assertTrue(runtime.awaitIdle(Duration.ofSeconds(30)));
      description(service.resolveConcept("w:Agent"),"B meaning");description(service.resolveConcept("odo:Agent"),"B meaning");
      long accepted=runtime.status().acceptedGeneration();
      service.loadKnowledge(world("bad","REJECTED meaning",false,true,false),scope);assertTrue(runtime.awaitIdle(Duration.ofSeconds(30)));
      assertEquals(CandidatePublicationCoordinator.LogicalStatus.INVALID,runtime.status().logicalStatus());assertEquals(accepted,runtime.status().acceptedGeneration());
      description(service.resolveConcept("w:Agent"),"B meaning");description(service.resolveConcept("odo:Agent"),"B meaning");
      service.loadKnowledge(world("blank",null,false,false,false),scope);assertTrue(runtime.awaitIdle(Duration.ofSeconds(30)));
      description(service.resolveConcept("w:Agent"),BACKGROUND);description(service.resolveConcept("odo:Agent"),BACKGROUND);
      service.loadKnowledge(world("removed",null,false,false,true),scope);assertTrue(runtime.awaitIdle(Duration.ofSeconds(30)));
      description(service.resolveConcept("odo:Agent"),BACKGROUND);assertTrue(service.resolveConcept("w:Agent").is(SemanticType.NOTHING));
    }finally{scope.close();}
  }

  @Test void supportedIncrementalCandidateUpdateReplacesThenRemovesDocumentation()throws Exception {
    var service=facade(factory());var resources=mock(ResourcesService.class);var user=mock(UserScope.class);
    when(user.getService(ResourcesService.class)).thenReturn(resources);var manager=mock(ScopeManager.class);
    when(manager.collectMessagePayload(eq(user),eq(Notification.class),any())).thenReturn(user);doReturn(manager).when(service).getScopeManager();
    var capabilities=mock(ResourcesService.Capabilities.class);when(capabilities.getServiceId()).thenReturn("resources");when(resources.capabilities(user)).thenReturn(capabilities);
    var serviceScope=mock(org.integratedmodelling.klab.api.scope.ServiceScope.class);
    var status=mock(org.integratedmodelling.klab.api.services.KlabService.ServiceStatus.class);
    when(status.isAvailable()).thenReturn(true);when(resources.status()).thenReturn(status);
    when(resources.getUrl()).thenReturn(java.net.URI.create("http://127.0.0.1:8092").toURL());
    when(capabilities.isWorldviewProvider()).thenReturn(true);when(resources.capabilities(serviceScope)).thenReturn(capabilities);
    when(serviceScope.getServices(ResourcesService.class)).thenReturn(List.of(resources));
    doReturn(serviceScope).when(service).serviceScope();
    var scope=new SemanticEnvironmentScope(new CapturedSemanticSourceResolver(world("scope","A",false,false,false)).service());
    try(var runtime=service.candidateRuntime()) {
      service.loadKnowledge(world("A","A meaning",false,false,false),scope);assertTrue(runtime.awaitIdle(Duration.ofSeconds(30)));
      description(service.resolveConcept("w:Agent"),"A meaning");description(service.resolveConcept("odo:Agent"),"A meaning");
      for(int phase=0;phase<3;phase++) {
        String text=phase==0?"B meaning":null;
        var next=world("updated "+phase,text,false,false,phase==2);
        when(resources.retrieve("w",KimOntology.class,user)).thenReturn(next.getOntologies().getFirst());when(resources.retrieve("w",Worldview.class,user)).thenReturn(next);
        when(resources.list(Worldview.class,null)).thenReturn(List.of(next));
        var changes=new ResourceSet();changes.getOntologies().add(new ResourceSet.Resource("resources","w","fixture",null,KlabAsset.KnowledgeClass.ONTOLOGY,0,false));
        var result=service.updateKnowledge(changes,user);
        assertFalse(org.integratedmodelling.klab.api.utils.Utils.Resources.hasErrors(result),result.getNotifications().toString());
        assertTrue(runtime.awaitIdle(Duration.ofSeconds(30)));
        assertEquals(CandidatePublicationCoordinator.LogicalStatus.VALID,runtime.status().logicalStatus(),runtime.status().diagnostics().toString());
        if(phase==2) assertTrue(service.resolveConcept("w:Agent").is(SemanticType.NOTHING));
        else description(service.resolveConcept("w:Agent"),text==null?BACKGROUND:text);
        description(service.resolveConcept("odo:Agent"),text==null?BACKGROUND:text);
      }
    }finally{scope.close();}
  }
}
