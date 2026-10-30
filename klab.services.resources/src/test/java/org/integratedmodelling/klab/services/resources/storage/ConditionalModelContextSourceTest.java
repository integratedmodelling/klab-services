package org.integratedmodelling.klab.services.resources.storage;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.util.*;
import org.integratedmodelling.klab.api.knowledge.impl.WorldviewImpl;
import org.integratedmodelling.klab.api.lang.kim.*;
import org.integratedmodelling.klab.api.services.runtime.Notification;
import org.integratedmodelling.klab.services.reasoner.internal.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Actual worldview and k.IM parsers plus the semantic source/executable validation path. */
class ConditionalModelContextSourceTest {
  @TempDir Path directory;
  private record Fixture(WorkspaceManager manager, SemanticEnvironment environment) implements AutoCloseable {
    public void close() throws Exception { environment.close(); }
  }
  private Fixture fixture(boolean containment, boolean unit) throws Exception {
    var source="""
        ontology imod in domain root version 1.0;
        abstract thing Subject is core odo:Subject;
        abstract quantity Ratio is core odo:Ratio;
        abstract length Length is core odo:Length;
        abstract angle Angle is core odo:Angle;
        abstract configuration Configuration is core odo:Configuration;
        abstract realm Realm is core odo:Realm;
        abstract realm Environment is imod:Realm applies to imod:Subject has disjoint children Terrestrial, Aquatic;
        thing Region;
        thing TerrestrialRegion is imod:Region;
        thing Other;
        thing Marked;
        thing Stream%s;
        agent Person;
        quantity Gradient is imod:Ratio of imod:Stream%s;
        quantity RegionalRatio is imod:Ratio of imod:Region metadata { "odo:unit": "1" };
        length Elevation is imod:Length of imod:Region;
        length TerrestrialElevation is imod:Length of imod:TerrestrialRegion;
        length OtherValue is imod:Length of imod:Other;
        configuration TerrestrialArrangement applies to (imod:Terrestrial imod:Region);
        length CompoundElevation is imod:Length of imod:Region describes imod:TerrestrialArrangement;
        length OrElevation is imod:Length;
        angle AngularGradient is imod:Angle of imod:Stream metadata { "odo:unit": "1" };
        length LengthGradient is imod:Length of imod:Stream metadata { "odo:unit": "1" };
        abstract length AbstractValue is imod:Length of imod:Region;
        """.formatted(containment?" contained into imod:Region, imod:Subject":"",unit?" metadata { \"odo:unit\": \"1\" }":"");
    var manager=WorkspaceDomainIngestionTest.manager(Files.createDirectories(directory.resolve("source")),Map.of("imod",source));
    var ontologies=new ArrayList<>(manager.getOntologies(false));

    var world=new WorldviewImpl();world.setUrn("imod");world.setWorldviewId("imod");world.setEmpty(false);world.setOntologies(ontologies);
    var factory=new SemanticEnvironmentFactory(directory.resolve("actors"),CoreOntologyCatalog.bundledCore());
    var captured=factory.capture(world);
    assertEquals(CandidatePublicationCoordinator.BeanStatus.VALID,captured.beanStatus(),captured.diagnostics().toString());
    var compiled=factory.build(captured,()->false);
    assertEquals(CandidatePublicationCoordinator.LogicalStatus.VALID,compiled.logicalStatus(),compiled.diagnostics().toString());
    var actor=compiled.state().actor();var graph=actor.owl().getOntology("imod").getOWLOntology();
    graph.getOWLOntologyManager().addAxiom(graph,graph.getOWLOntologyManager().getOWLDataFactory().getOWLDisjointClassesAxiom(
        graph.classesInSignature().filter(c -> c.getIRI().getShortForm().equals("Region")).findFirst().orElseThrow(),graph.classesInSignature().filter(c -> c.getIRI().getShortForm().equals("Other")).findFirst().orElseThrow()));
    actor.owl().flushReasoner();
    return new Fixture(manager,compiled.state());
  }
  private KimNamespace parse(Fixture f,String model) throws Exception {
    WorkspaceDomainIngestionTest.set(f.manager(),"namespaceParser",new WorkspaceManager.KimParser());
    var method=WorkspaceManager.class.getDeclaredMethod("readNamespace",String.class,long.class,String.class,String.class);
    method.setAccessible(true);
    var document=(KimNamespace)method.invoke(f.manager(),"namespace models.validation version 1.0;\n"+model,1L,"fixture","models.validation");
    assertFalse(errors(document.getNotifications()),document.getNotifications().toString());return document;
  }
  private static boolean errors(Collection<Notification> diagnostics) {
    return diagnostics.stream().anyMatch(n -> n.getLevel()==Notification.Level.Error);
  }
  @Test void acceptedGenerationPreservesNativeCollectionObligationAndExactPreparableProof() throws Exception {
    try(var f=fixture(false,true)) {
      var actor=f.environment().actor();
      var source=parse(f,"model imod:RegionalRatio observing imod:Gradient of imod:Stream named gradients;");
      var factory=org.mockito.Mockito.mock(SemanticEnvironmentFactory.class);
      // Real accepted-generation lease/validator; only environment construction is controlled.
      var state=org.mockito.Mockito.mock(SemanticEnvironment.class);
      org.mockito.Mockito.when(state.actor()).thenReturn(actor);
      org.mockito.Mockito.when(factory.dependencyRevision()).thenReturn("collection-source-fixture");
      org.mockito.Mockito.when(factory.capture(org.mockito.ArgumentMatchers.any())).thenAnswer(invocation->{
        org.integratedmodelling.klab.api.knowledge.Worldview world=invocation.getArgument(0);
        return new CandidatePublicationCoordinator.Input<>(SemanticEnvironmentFactory.sources(world),
            SemanticEnvironmentFactory.sourceOrder(world),"collection-source-fixture",SemanticEnvironmentFactory.CONFIGURATION_REVISION,
            world,CandidatePublicationCoordinator.BeanStatus.VALID,List.of());
      });
      org.mockito.Mockito.when(factory.build(org.mockito.ArgumentMatchers.any(),org.mockito.ArgumentMatchers.any())).thenReturn(
          new CandidatePublicationCoordinator.Result<>(state,CandidatePublicationCoordinator.LogicalStatus.VALID,true,List.of()));
      var world=new WorldviewImpl();world.setUrn("imod");world.setWorldviewId("imod");world.setEmpty(false);
      world.setOntologies(new ArrayList<>(f.manager().getOntologies(false)));
      try(var accepted=new AcceptedSemanticRuntime(factory)) {
        accepted.submit(world);assertTrue(accepted.awaitIdle(java.time.Duration.ofSeconds(5)));
        long revision=accepted.status().acceptedGeneration();assertTrue(revision>0);
        var request=org.integratedmodelling.klab.api.services.reasoner.objects.SemanticValidationRequest.of(source,"saved-source");
        request.setKnowledgeRevision(revision);
        var response=accepted.validate(request,org.integratedmodelling.klab.api.services.reasoner.objects.SemanticValidationResponse.forRequest(request));
        assertTrue(response.valid(),response.getNotifications().toString());
        assertEquals(1,response.getPendingMemberQualityDependencies().size());
        var mapper=org.integratedmodelling.common.data.jackson.JacksonConfiguration.newObjectMapper();
        var transported=mapper.readValue(mapper.writeValueAsBytes(response),
            org.integratedmodelling.klab.api.services.reasoner.objects.SemanticValidationResponse.class);
        assertEquals(response.getPendingMemberQualityDependencies(),transported.getPendingMemberQualityDependencies());
        request.setExecutableModels(true);request.setExecutionContext("imod:Region");
        var strict=accepted.validate(request,org.integratedmodelling.klab.api.services.reasoner.objects.SemanticValidationResponse.forRequest(request));
        assertFalse(strict.valid(),"Native bearer cannot be waived by accepted-generation routing");
        request.setClassifierPreparableDependencies(true);
        request.setPreparableMemberQualityDependencies(transported.getPendingMemberQualityDependencies());
        var prepared=accepted.validate(request,org.integratedmodelling.klab.api.services.reasoner.objects.SemanticValidationResponse.forRequest(request));
        assertTrue(prepared.valid(),prepared.getNotifications().toString());
        assertEquals(transported.getPendingMemberQualityDependencies(),prepared.getPendingMemberQualityDependencies());
        assertTrue(prepared.matches(request,revision));
        request.setKnowledgeRevision(revision+1);
        var stale=accepted.validate(request,org.integratedmodelling.klab.api.services.reasoner.objects.SemanticValidationResponse.forRequest(request));
        assertEquals(org.integratedmodelling.klab.api.services.reasoner.objects.SemanticValidationResponse.Status.STALE_KNOWLEDGE,stale.getStatus());
      }
    }
  }
  @Test void explicitlyNamedNativeQualityIsOnlyAPreparableCollectionObligation() throws Exception {
    try(var f=fixture(false,true)) {
      var actor=f.environment().actor();
      var source=parse(f,"model imod:RegionalRatio observing imod:Gradient of imod:Stream named gradients;");
      var validator=new DocumentSemanticValidator(actor);
      var diagnostics=validator.validate(source);assertFalse(errors(diagnostics),diagnostics.toString());
      var pending=validator.getPendingMemberQualityDependencies();assertEquals(1,pending.size());
      assertEquals("gradients",pending.getFirst().port());assertEquals(0,pending.getFirst().dependencyIndex());
      assertEquals("imod:Stream",pending.getFirst().nativeBearer());
      assertEquals(((KimModel)source.getStatements().getFirst()).getDependencies().getFirst().getUrn(),pending.getFirst().sourceObservable());
      assertTrue(errors(new DocumentSemanticValidator(actor,true,false,"imod:Region").validate(source)),"Ordinary executable validation cannot waive native bearer");
      var preparable=new DocumentSemanticValidator(actor,true,false,"imod:Region",pending);
      var prepared=preparable.validate(source);assertFalse(errors(prepared),prepared.toString());
      assertEquals(pending,preparable.getPendingMemberQualityDependencies());
      for(var dependency:List.of("imod:Gradient named gradients","imod:Gradient of imod:Stream",
          "imod:Gradient of imod:Region named gradients","imod:Gradient of imod:Stream in m named gradients",
          "imod:Gradient of imod:Stream observed as imod:Person named gradients",
          "imod:Gradient of imod:Stream named gradients, imod:RegionalRatio named gradients")) {
        var invalid=new DocumentSemanticValidator(actor);var result=invalid.validate(parse(f,"model imod:RegionalRatio observing "+dependency+";"));
        assertTrue(errors(result),dependency+result);assertTrue(invalid.getPendingMemberQualityDependencies().isEmpty(),dependency);
      }
    }
  }

  @Test void sourceIsConditionalAndActualContextMustProveEveryDependency() throws Exception {
    try(var f=fixture(true,true)) {
      var actor=f.environment().actor();
      var document=parse(f,"model imod:Gradient observing imod:Elevation in m;");
      var sourceValidator=new DocumentSemanticValidator(actor);
      var source=sourceValidator.validate(document);
      assertFalse(errors(source),source.toString());
      assertEquals(List.of("imod:Region"),sourceValidator.getConditionalModelContexts().getFirst().requiredContextAlternatives());
      for(var actual:List.of("imod:Region","imod:TerrestrialRegion")) {
        var diagnostics=new DocumentSemanticValidator(actor,true,false,actual).validate(document);
        assertFalse(errors(diagnostics),diagnostics.toString());
      }
      for(String actual:new String[]{null,"imod:Other","imod:Stream","imod:Missing"}) {
        var diagnostics=new DocumentSemanticValidator(actor,true,false,actual).validate(document);
        assertTrue(errors(diagnostics),"Unproved actual context must fail: "+actual);
      }
      assertEquals("imod:Stream",actor.inherent(actor.resolveConcept("imod:Gradient")).getUrn());
      assertEquals("imod:Region",actor.inherent(actor.resolveConcept("imod:Elevation")).getUrn());
    }
  }
  @Test void qualifyingSubtypeBecomesRequiredContextNotUniversalRegionPermission() throws Exception {
    try(var f=fixture(true,true)) {
      var document=parse(f,"model imod:Gradient observing imod:TerrestrialElevation in m;");
      var validator=new DocumentSemanticValidator(f.environment().actor());
      var source=validator.validate(document);assertFalse(errors(source),source.toString());
      assertEquals(List.of("imod:TerrestrialRegion"),validator.getConditionalModelContexts().getFirst().requiredContextAlternatives());
      assertTrue(errors(new DocumentSemanticValidator(f.environment().actor(),true,false,"imod:Region").validate(document)));
      assertFalse(errors(new DocumentSemanticValidator(f.environment().actor(),true,false,"imod:TerrestrialRegion").validate(document)));
      var joint=parse(f,"model imod:Gradient observing imod:Elevation in m, imod:OtherValue in m;");
      assertTrue(errors(new DocumentSemanticValidator(f.environment().actor()).validate(joint)));
    }
  }
  @Test void permissionUnitProofAndExplicitAbstractnessRemainObligations() throws Exception {
    try(var f=fixture(false,true)) {
      assertTrue(errors(new DocumentSemanticValidator(f.environment().actor()).validate(parse(f,"model imod:Gradient observing imod:Elevation in m;"))));
    }
    try(var f=fixture(true,false)) {
      assertTrue(errors(new DocumentSemanticValidator(f.environment().actor()).validate(parse(f,"model imod:Gradient observing imod:Elevation in m;"))));
    }
    try(var f=fixture(true,true)) {
      for(var unit:List.of("degree_angle","rad","m")) {
        var model=parse(f,"model imod:Gradient in "+unit+" observing imod:Elevation in m;");
        assertTrue(errors(new DocumentSemanticValidator(f.environment().actor(),true,false,"imod:Region").validate(model)),unit);
      }
      assertTrue(errors(new DocumentSemanticValidator(f.environment().actor(),true,false,"imod:Region")
          .validate(parse(f,"model imod:Gradient observing imod:AbstractValue in m;"))));
      for(var head:List.of("AngularGradient","LengthGradient")) {
        var claimedUnity=parse(f,"model imod:"+head+" observing imod:Elevation in m;");
        assertTrue(errors(new DocumentSemanticValidator(f.environment().actor()).validate(claimedUnity)),
            "Canonical unity metadata cannot make "+head+" a semantic Ratio");
      }
    }
  }

  @Test void compoundObligationIsTypedPendingAndNeverAnExecutableContextProof() throws Exception {
    try(var f=fixture(true,true)) {
      var actor=f.environment().actor();var source=parse(f,"model imod:Gradient observing imod:CompoundElevation in m;");
      var validator=new DocumentSemanticValidator(actor);
      var diagnostics=validator.validate(source);assertFalse(errors(diagnostics),diagnostics.toString());
      assertTrue(validator.getConditionalModelContexts().isEmpty(),"No fabricated named context");
      var pending=validator.getPendingModelContexts();assertFalse(pending.isEmpty());
      assertTrue(pending.stream().anyMatch(p -> p.containerUrn().equals("imod:Region")
          && p.dependentSemanticUrns().equals(List.of("imod:CompoundElevation"))));
      assertTrue(errors(new DocumentSemanticValidator(actor,true,false,"imod:Region").validate(source)));
      assertTrue(errors(new DocumentSemanticValidator(actor,true,false,"imod:Other").validate(source)));
      assertTrue(errors(new DocumentSemanticValidator(actor,true).validate(source)));
      var qualified=actor.resolveConcept("imod:Terrestrial imod:Region");
      assertFalse(errors(new DocumentSemanticValidator(actor,true,false,qualified.getUrn()).validate(source)));
      var graph=actor.owl().getOntology("imod").getOWLOntology();var factory=graph.getOWLOntologyManager().getOWLDataFactory();
      var named=graph.classesInSignature().collect(java.util.stream.Collectors.toMap(c -> c.getIRI().getShortForm(),c -> c));
      var property=factory.getOWLObjectProperty(org.semanticweb.owlapi.model.IRI.create("http://integratedmodelling.org/odo#isInherentTo"));
      graph.getOWLOntologyManager().addAxiom(graph,factory.getOWLSubClassOfAxiom(named.get("OrElevation"),
          factory.getOWLObjectSomeValuesFrom(property,factory.getOWLObjectUnionOf(named.get("Region"),named.get("Other")))));
      actor.owl().flushReasoner();
      var overlapping=actor.resolveConcept("imod:Region or imod:Marked");
      assertEquals(org.integratedmodelling.klab.services.reasoner.owl.OWLSemanticClauseSupport.Compatibility.UNRESOLVED,
          new org.integratedmodelling.klab.services.reasoner.owl.OWLSemanticClauseSupport(actor.owl()).clauseCompatibility(
              actor.resolveConcept("imod:OrElevation"),org.integratedmodelling.klab.api.knowledge.SemanticRole.INHERENT,overlapping),
          "Actual A-or-C cannot entail required A-or-B from overlap");
      assertFalse(org.integratedmodelling.klab.services.reasoner.owl.ConditionalModelContextSupport.admits(
          actor.owl(),actor.resolveConcept("imod:Gradient"),List.of(actor.resolveConcept("imod:OrElevation")),overlapping),
          "Overlap of alternatives is not whole-clause entailment");
      var impossible=parse(f,"model imod:Gradient observing imod:Elevation in m, imod:OtherValue in m;");
      var impossibleValidator=new DocumentSemanticValidator(actor);
      assertTrue(errors(impossibleValidator.validate(impossible)));
      assertTrue(impossibleValidator.getPendingModelContexts().isEmpty(),"Unsatisfiable conjunction is not a pending route");
    }
  }
}
