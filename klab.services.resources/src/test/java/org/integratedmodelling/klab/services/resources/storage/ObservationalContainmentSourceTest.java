package org.integratedmodelling.klab.services.resources.storage;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.util.*;
import org.integratedmodelling.klab.api.lang.kim.KimOntology;
import org.integratedmodelling.klab.api.knowledge.impl.WorldviewImpl;
import org.integratedmodelling.klab.api.services.runtime.Notification;
import org.integratedmodelling.klab.services.reasoner.internal.*;
import org.integratedmodelling.klab.services.reasoner.owl.ObservationalContainmentSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Real grammar, syntax adapter, service bean transport and worldview compiler. */
class ObservationalContainmentSourceTest {
  @TempDir Path directory;
  private static final String PREFIX="""
      ontology imod in domain root version 1.0;
      abstract thing Subject is core odo:Subject;
      abstract event Event is core odo:Event;
      abstract process Process is core odo:Process;
      """;
  private KimOntology parse(String key,String source) throws Exception {
    var manager=WorkspaceDomainIngestionTest.manager(Files.createDirectories(directory.resolve(key)),
        Map.of("imod",PREFIX+source));
    return manager.getOntologies(false).iterator().next();
  }
  private void valid(KimOntology ontology) {
    assertTrue(ontology.getNotifications().stream().noneMatch(n ->
        n.getLevel().severity>=Notification.Level.Error.severity),ontology.getNotifications().toString());
  }
  private List<String> pairs(KimOntology ontology) {
    return ontology.getStatements().stream().flatMap(this::statements)
        .flatMap(s -> s.getObservationalContainments().stream())
        .map(pair -> pair.container().getUrn()+" -> "+pair.contained().getUrn()).sorted().toList();
  }
  private java.util.stream.Stream<org.integratedmodelling.klab.api.lang.kim.KimConceptStatement> statements(
      org.integratedmodelling.klab.api.lang.kim.KimConceptStatement statement) {
    return java.util.stream.Stream.concat(java.util.stream.Stream.of(statement),
        statement.getChildren().stream().flatMap(this::statements));
  }

  @Test void bothDirectionsNormalizeBeforeAndAfterJsonTransport() throws Exception {
    var forward=parse("forward","thing Stream; thing RiverBasin; thing Region contains imod:Stream, imod:RiverBasin;");
    var reverse=parse("reverse","thing Region; thing Stream contained into imod:Region; thing RiverBasin contained into imod:Region;");
    valid(forward);valid(reverse);
    assertEquals(List.of("imod:Region -> imod:RiverBasin","imod:Region -> imod:Stream"),pairs(forward));
    assertEquals(pairs(forward),pairs(reverse));
    var mapper=org.integratedmodelling.common.data.jackson.JacksonConfiguration.newObjectMapper();
    var copy=mapper.readValue(mapper.writeValueAsBytes(reverse),KimOntology.class);
    assertEquals(pairs(forward),pairs(copy));
    for(var statement:copy.getStatements()) assertNull(statement.getDeclaredInherent());
  }

  @Test void actualWorldviewCompilesSubjectAndOccurrentFixturesWithoutChangingBearers() throws Exception {
    var source=parse("compile","""
        thing Stream; thing RiverBasin; thing Region contains imod:Stream, imod:RiverBasin;
        event Member; event Whole contains imod:Member;
        process Subprocess; process Compound contains imod:Subprocess;
        process LocalFlow is imod:Process of imod:Stream;
        abstract length Length is core odo:Length;
        length StreamHeight is imod:Length of imod:Stream;
        length MemberHeight is imod:Length of each imod:Stream;
        abstract identity Identity is core odo:Identity;
        abstract identity Missing;
        abstract thing Unidentified requires identity imod:Missing;
        abstract configuration Configuration is core odo:Configuration;
        configuration Arrangement is imod:Configuration applies to imod:Unidentified;
        abstract domain Domain is core odo:Domain;
        domain Perspective is imod:Domain of imod:Unidentified;
        """);
    valid(source);
    var world=new WorldviewImpl();world.setUrn("imod");world.setWorldviewId("imod");
    world.setEmpty(false);world.setOntologies(List.of(source));
    byte[] odo;
    var core=System.getProperty("klab.test.core.directory");
    if(core!=null) odo=Files.readAllBytes(Path.of(core,"odo.owl"));
    else try(var input=getClass().getResourceAsStream("/knowledge/odo.owl")){odo=input.readAllBytes();}
    var factory=new SemanticEnvironmentFactory(directory.resolve("actors"),Map.of("odo.owl",odo));
    var captured=factory.capture(world);
    assertEquals(CandidatePublicationCoordinator.BeanStatus.VALID,captured.beanStatus(),captured.diagnostics().toString());
    var compiled=factory.build(captured,()->false);
    assertEquals(CandidatePublicationCoordinator.LogicalStatus.VALID,compiled.logicalStatus(),compiled.diagnostics().toString());
    try(var state=compiled.state()) {
      var owl=state.actor().owl();var graph=owl.getOntology("imod").getOWLOntology();
      assertEquals(4,ObservationalContainmentSupport.declarations(owl,graph).size());
      assertTrue(owl.isConsistent());
      assertNull(state.actor().inherent(state.actor().resolveConcept("imod:Stream")));
      var height=state.actor().resolveConcept("imod:StreamHeight");
      assertEquals("imod:Stream",state.actor().inherent(height).getUrn(),
          "of names the native bearer, not its observational container");
      assertEquals("imod:Stream",state.actor().directInherent(height).getUrn());
      assertEquals("imod:Stream",state.actor().inherent(state.actor().resolveConcept("imod:LocalFlow")).getUrn());
      var distributed=source.getStatements().stream().filter(s->s.getUrn().equals("MemberHeight")).findFirst().orElseThrow();
      assertTrue(distributed.getDeclaredParent().getInherent().isCollective());
      assertFalse(state.actor().resolveConcept("imod:Region").isAbstract());
      assertFalse(state.actor().resolveConcept("imod:Stream").isAbstract());
      assertTrue(state.actor().resolveConcept("imod:Unidentified").isAbstract());
      for(var name:List.of("Arrangement","Perspective")) {
        var applicable=state.actor().resolveConcept("imod:"+name);
        assertFalse(applicable.isAbstract(),"Applicability target abstraction/identity is not native head dependence: "+name);
        if (name.equals("Arrangement")) assertNull(state.actor().inherent(applicable),
            "Configuration applicability is not native inherency");
        else assertEquals("imod:Unidentified",state.actor().inherent(applicable).getUrn(),
            "Predicate shared inherency OWL encoding remains available to contextualization mechanics");
      }
    }
  }

  @Test void missingIntoIsNotAcceptedAsTheNewDeclarationClause() throws Exception {
    var source=parse("malformed","thing Region; thing Stream contained imod:Region;");
    assertTrue(source.getNotifications().stream().anyMatch(n ->
        n.getLevel().severity>=Notification.Level.Error.severity));
  }

  @Test void nestedDeclarationsRetainCanonicalPairs() throws Exception {
    var source=parse("nested","thing Stream; thing Parent has children (Region contains imod:Stream);");
    valid(source);
    assertEquals(List.of("imod:Region -> imod:Stream"),pairs(source));
  }

  @Test void spanFreeTransportedAliasesCannotSilentlyDropContracts() throws Exception {
    for(boolean core:List.of(false,true)) {
      var ontology=parse("alias"+core,"thing Stream; thing Region contains imod:Stream;");
      valid(ontology);
      var region=(org.integratedmodelling.klab.api.lang.kim.impl.KimConceptStatementImpl)
          ontology.getStatements().stream().filter(s -> s.getUrn().equals("Region")).findFirst().orElseThrow();
      region.getDeclarationClauses().clear();
      if(core)region.setUpperConceptDefined("odo:Subject");else region.setAlias(true);
      var mapper=org.integratedmodelling.common.data.jackson.JacksonConfiguration.newObjectMapper();
      ontology=mapper.readValue(mapper.writeValueAsBytes(ontology),KimOntology.class);
      var visitor=new org.integratedmodelling.klab.runtime.language.KimOntologyVisitor();
      visitor.visit(ontology);
      assertTrue(visitor.getNotifications().stream().anyMatch(n ->
          n.getMessage().contains("alias cannot have additional semantic clauses")));
    }
  }

  @Test void spanFreeTransportedEndpointsAreVisitedForUnknownReferences() throws Exception {
    var ontology=parse("visitor","thing Stream; thing Region contains imod:Stream;");
    var region=ontology.getStatements().stream().filter(s -> s.getUrn().equals("Region")).findFirst().orElseThrow();
    var pair=region.getObservationalContainments().getFirst();
    ((org.integratedmodelling.klab.api.lang.kim.impl.KimConceptImpl)pair.contained()).setName("imod:Missing");
    var visitor=new org.integratedmodelling.klab.runtime.language.KimOntologyVisitor(null,(urn,type,context)->null);
    visitor.visit(ontology);
    assertTrue(visitor.getNotifications().stream().anyMatch(n -> n.getMessage().contains("imod:Missing")));
  }

  @Test void withinAndNestedWithinNormalizeLikeExplicitContainerSpecialization() throws Exception {
    var prefix="thing Region; thing Basin is imod:Region; ";
    var within=parse("within",prefix+"thing Stream contained into imod:Region; "
        +"thing Reach is imod:Stream within imod:Basin;");
    var explicit=parse("explicit",prefix+"thing Stream contained into imod:Region; "
        +"thing Reach is imod:Stream contained into imod:Basin;");
    var nested=parse("nestedWithin",prefix+"thing Stream contained into imod:Region "
        +"has children Reach within imod:Basin;");
    for(var ontology:List.of(within,explicit,nested)) {
      valid(ontology);
      assertEquals(List.of("imod:Basin -> imod:Reach","imod:Region -> imod:Stream"),pairs(ontology));
      assertTrue(new org.integratedmodelling.klab.runtime.language.WorldviewDeclarationRegistry(List.of(ontology))
          .errors().isEmpty());
      for(var root:ontology.getStatements()) for(var statement:statements(root).toList())
        assertNull(statement.getDeclaredInherent(),"within must not set inherency");
    }
    var reach=within.getStatements().stream().filter(s->s.getUrn().equals("Reach")).findFirst().orElseThrow();
    assertEquals("imod:Stream",reach.getDeclaredParent().getUrn());
    var mapper=org.integratedmodelling.common.data.jackson.JacksonConfiguration.newObjectMapper();
    assertEquals(pairs(explicit),pairs(mapper.readValue(mapper.writeValueAsBytes(nested),KimOntology.class)));
  }

  @Test void dependentWithinDoesNotSilentlyBecomeInherencyOrAValidContainmentPair() throws Exception {
    var source=parse("dependentWithin","abstract length Length is core odo:Length; thing Region; "
        +"length Height is imod:Length within imod:Region;");
    var registry=new org.integratedmodelling.klab.runtime.language.WorldviewDeclarationRegistry(List.of(source));
    assertTrue(registry.errors().stream().anyMatch(n->n.getMessage().contains("observational containment")));
    var height=source.getStatements().stream().filter(s->s.getUrn().equals("Height")).findFirst().orElseThrow();
    assertNull(height.getDeclaredInherent());
    assertFalse(height.getObservationalContainments().isEmpty());
  }

  @Test void olderParsedWithinBeanCannotSilentlyReintroduceLegacyInherency() throws Exception {
    var source=parse("legacyWithin","thing Region; thing Stream; thing Reach is imod:Stream within imod:Region;");
    valid(source);
    var reach=(org.integratedmodelling.klab.api.lang.kim.impl.KimConceptStatementImpl)
        source.getStatements().stream().filter(s->s.getUrn().equals("Reach")).findFirst().orElseThrow();
    reach.setDeclaredInherent(reach.getObservationalContainments().getFirst().container());
    reach.getObservationalContainments().clear();
    var mapper=org.integratedmodelling.common.data.jackson.JacksonConfiguration.newObjectMapper();
    source=mapper.readValue(mapper.writeValueAsBytes(source),KimOntology.class);
    var visitor=new org.integratedmodelling.klab.runtime.language.KimOntologyVisitor();visitor.visit(source);
    assertTrue(visitor.getNotifications().stream().anyMatch(n->n.getMessage().contains("LEGACY_WITHIN_INHERENCY")));
  }

  @Test void unrelatedExplicitPairCannotMaskLegacyWithinInherency() throws Exception {
    var source=parse("mixedLegacyWithin","thing Region; thing Stream; thing Tributary; "
        +"thing Reach is imod:Stream within imod:Region contains imod:Tributary;");
    valid(source);
    var reach=(org.integratedmodelling.klab.api.lang.kim.impl.KimConceptStatementImpl)
        source.getStatements().stream().filter(s->s.getUrn().equals("Reach")).findFirst().orElseThrow();
    var legacy=reach.getObservationalContainments().stream()
        .filter(pair->pair.contained().getUrn().equals("imod:Reach")).findFirst().orElseThrow();
    reach.setDeclaredInherent(legacy.container());
    reach.getObservationalContainments().remove(legacy);
    assertFalse(reach.getObservationalContainments().isEmpty());
    var mapper=org.integratedmodelling.common.data.jackson.JacksonConfiguration.newObjectMapper();
    source=mapper.readValue(mapper.writeValueAsBytes(source),KimOntology.class);
    var visitor=new org.integratedmodelling.klab.runtime.language.KimOntologyVisitor();visitor.visit(source);
    assertTrue(visitor.getNotifications().stream().anyMatch(n->n.getMessage().contains("LEGACY_WITHIN_INHERENCY")));
  }

  @Test void sourceOfRejectsIndependentAndAliasedHeadsAndAcceptsTransformedQuality() throws Exception {
    for(var head:List.of("imod:Stream","(imod:Stream or imod:Tree)","imod:Alias")) {
      var source=parse("head"+head.hashCode(),"thing Region; thing Stream; thing Tree; "
          +"thing Alias equals imod:Stream; thing Invalid is "+head+" of imod:Region;");
      assertTrue(source.getNotifications().stream().anyMatch(n->
          n.getLevel().severity>=Notification.Level.Error.severity),head);
    }
    var transformed=parse("transformed","thing Region; thing Stream; "
        +"quality Counted is count of imod:Stream; "
        +"quality Present is (presence of imod:Stream) of imod:Region;");
    valid(transformed);
  }

  @Test void applicabilityClauseOrderCannotHideAnIndependentHeadInherency() throws Exception {
    var declarations="thing Region; relationship Connection; configuration Network; ";
    var ambiguous=parse("ambiguous",declarations+"configuration Structure "
        +"applies to imod:Region is imod:Network of imod:Connection;");
    assertTrue(ambiguous.getNotifications().stream().anyMatch(n->n.getMessage().contains("INHERENCY_HEAD_INDEPENDENT")));
    var explicit=parse("ordered",declarations+"configuration Structure "
        +"is imod:Network of imod:Connection applies to imod:Region;");
    valid(explicit);
    var structure=explicit.getStatements().stream().filter(s->s.getUrn().equals("Structure")).findFirst().orElseThrow();
    assertNotNull(structure.getDeclaredParent());
    assertNull(structure.getDeclaredParent().getInherent());
    assertEquals("imod:Connection",structure.getEmergenceTriggers().getFirst().getUrn());
    assertNull(structure.getAppliesTo().getFirst().getTarget().getInherent());
  }

  @Test void modelEachConfigurationCannotInstantiateButNativeOfEachRemainsLegal() throws Exception {
    var manager=WorkspaceDomainIngestionTest.manager(Files.createDirectories(directory.resolve("models")),
        Map.of("imod",PREFIX+"abstract configuration Configuration is core odo:Configuration; "
            +"configuration Pattern; configuration Alias equals imod:Pattern; "
            +"thing Region; abstract length Length is core odo:Length;"));
    manager.getOntologies(false).forEach(this::valid);
    WorkspaceDomainIngestionTest.set(manager,"namespaceParser",new WorkspaceManager.KimParser());
    var read=WorkspaceManager.class.getDeclaredMethod("readNamespace",String.class,long.class,String.class,String.class);
    read.setAccessible(true);
    for(var head:List.of("imod:Pattern","imod:Alias","(imod:Pattern or imod:Pattern)")) {
      var namespace=(org.integratedmodelling.klab.api.lang.kim.KimNamespace)read.invoke(manager,
          "namespace models.validation version 1.0; model each "+head+";",1L,"fixture","models.validation");
      assertTrue(namespace.getNotifications().stream().anyMatch(n->
          n.getMessage().contains("CONFIGURATION_INSTANTIATION")),head+": "+namespace.getNotifications());
    }
    for(var request:List.of("imod:Pattern","imod:Length of each imod:Region","presence of imod:Region")) {
      var namespace=(org.integratedmodelling.klab.api.lang.kim.KimNamespace)read.invoke(manager,
          "namespace models.validation version 1.0; model "+request+";",1L,"fixture","models.validation");
      assertTrue(namespace.getNotifications().stream().noneMatch(n->
          n.getLevel().severity>=Notification.Level.Error.severity),namespace.getNotifications().toString());
    }
  }
}
