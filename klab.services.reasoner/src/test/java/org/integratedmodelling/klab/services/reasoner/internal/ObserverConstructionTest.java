package org.integratedmodelling.klab.services.reasoner.internal;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.integratedmodelling.common.knowledge.ObservableImpl;
import org.integratedmodelling.common.data.jackson.JacksonConfiguration;
import org.integratedmodelling.klab.api.knowledge.*;
import org.integratedmodelling.klab.api.lang.kim.impl.*;

class ObserverConstructionTest {
  @TempDir Path directory;
  private SemanticEnvironment environment()throws Exception {
    var world=StagedDeclarationContractTest.world("observer construction",false,false,false);
    var agent=StagedDeclarationContractTest.declaration("w","AgentRoot",SemanticType.AGENT);agent.setAbstract(true);agent.setUpperConceptDefined("odo:Agent");world.getOntologies().getFirst().getStatements().add(agent);
    var general=StagedDeclarationContractTest.declaration("k","GeneralObserver",SemanticType.AGENT);general.setAbstract(true);general.setDeclaredParent(StagedDeclarationContractTest.reference("w:AgentRoot"));
    var local=StagedDeclarationContractTest.declaration("k","LocalObserver",SemanticType.AGENT);local.setDeclaredParent(StagedDeclarationContractTest.reference("k:GeneralObserver"));
    var other=StagedDeclarationContractTest.declaration("k","OtherObserver",SemanticType.AGENT);other.setDeclaredParent(StagedDeclarationContractTest.reference("w:AgentRoot"));
    world.getOntologies().get(1).getStatements().addAll(List.of(general,local,other));
    // Parsed references carry effective types. This hand-built fixture must retain those too;
    // a type-less reference cached by the captured resolver is not a real parsed source tree.
    var types=new HashMap<String,Set<SemanticType>>();
    for(var ontology:world.getOntologies()) {
      var pending=new ArrayDeque<org.integratedmodelling.klab.api.lang.kim.KimConceptStatement>(ontology.getStatements());
      while(!pending.isEmpty()) {var declaration=pending.removeFirst();types.put(ontology.getUrn()+":"+declaration.getUrn(),declaration.getType());pending.addAll(declaration.getChildren());}
    }
    for(var ontology:world.getOntologies())new org.integratedmodelling.klab.runtime.language.KimOntologyVisitor(
        new org.integratedmodelling.klab.runtime.language.KimOntologyVisitor.LenientValidator() {
          @Override public List<org.integratedmodelling.klab.api.services.runtime.Notification> validateConcept(
              org.integratedmodelling.klab.api.lang.kim.KimConcept concept,org.integratedmodelling.klab.runtime.language.KimObservableVisitor.Context context) {
            var known=types.get(concept.getUrn());if(known!=null)((KimConceptImpl)concept).setType(EnumSet.copyOf(known));return List.of();
          }
        },null).visit(ontology);

    var core=new LinkedHashMap<String,byte[]>();try(var files=Files.list(Path.of(System.getProperty("klab.test.core.directory")))) {
      for(var f:files.filter(p->p.toString().endsWith(".owl")).toList())core.put(f.getFileName().toString(),Files.readAllBytes(f));
    }
    var factory=new SemanticEnvironmentFactory(directory.resolve("assets"),core);var input=factory.capture(world);assertEquals(CandidatePublicationCoordinator.BeanStatus.VALID,input.beanStatus(),input.diagnostics().toString());
    var built=factory.build(input,()->false);assertEquals(CandidatePublicationCoordinator.LogicalStatus.VALID,built.logicalStatus(),built.diagnostics().toString());return built.state();
  }
  @Test void sourceCopiesAndTransportPreserveExplicitIdentityWithoutChangingBaseConcept()throws Exception {
    try(var state=environment()) {
      var actor=state.actor();var absolute=actor.resolveObservable("k:Q");
      var x=actor.resolveObservable("k:Q observed as k:GeneralObserver in m");var y=actor.resolveObservable("k:Q observed as k:OtherObserver in m");
      assertEquals("k:GeneralObserver",x.getObserverSemantics().getUrn());assertTrue(x.getUrn().contains(" observed as k:GeneralObserver"));assertNotEquals(x.getUrn(),y.getUrn());assertEquals(absolute.asConcept(),x.asConcept());assertFalse(x.isAbstract());
      var copy=SemanticsBuilder.create(x,actor,actor.serviceScope()).buildObservable();
      assertEquals(x.getUrn(),copy.getUrn());assertEquals(1,copy.getUrn().split(" observed as ",-1).length-1);assertEquals(x.getObserverSemantics(),copy.getObserverSemantics());
      var mapper=JacksonConfiguration.newObjectMapper();var wire=mapper.readValue(mapper.writeValueAsBytes(copy),org.integratedmodelling.klab.api.knowledge.Observable.class);assertEquals(copy,wire);assertEquals(copy.getObserverSemantics(),wire.getObserverSemantics());
      assertNull(absolute.getObserverSemantics());assertEquals("k:Q",absolute.getUrn());
    }
  }
  @Test void explicitCompatibilityPrecedesDistanceAndUsesNormalClauseSpecialization()throws Exception {
    try(var state=environment()) {
      var actor=state.actor();var absolute=actor.resolveObservable("k:Q");var general=actor.resolveObservable("k:Q observed as k:GeneralObserver");var local=actor.resolveObservable("k:Q observed as k:LocalObserver");var other=actor.resolveObservable("k:Q observed as k:OtherObserver");
      assertTrue(actor.semanticDistance(general,local,null)>=0);assertTrue(actor.semanticDistance(local,general,null)<0);
      assertTrue(actor.semanticDistance(absolute,local,null)<0);assertTrue(actor.semanticDistance(other,local,null)<0);
      assertFalse(actor.compatible(absolute,local));assertFalse(actor.match(absolute,local));assertFalse(actor.match(other,local));
      assertTrue(actor.semanticDistance(general,absolute,null)>=0);assertTrue(actor.semanticDistance(absolute,absolute,null)>=0);
      assertTrue(actor.semanticDistance(general,local,null)>=0,"an earlier negative score must not contaminate another perspective");
    }
  }
  @Test void privateModelDeclarationOwnsMetadataWithoutChangingBaseConceptOrSourceScope()throws Exception {
    try(var state=environment()) {
      var actor=state.actor();var source=new KimObservableImpl();
      var main=StagedDeclarationContractTest.reference("k:Q");main.setType(EnumSet.of(SemanticType.QUALITY,SemanticType.OBSERVABLE));source.setSemantics(main);
      var observer=StagedDeclarationContractTest.reference("k:GeneralObserver");observer.setType(EnumSet.of(SemanticType.AGENT,SemanticType.OBSERVABLE,SemanticType.COUNTABLE));source.setObserverSemantics(observer);
      source.setUrn("k:Q");source.setNamespace("private.models");source.setProjectName("private-project");
      var model=new KimModelImpl();model.setNamespace("private.models");model.setUrn("private.models.test");model.setScope(org.integratedmodelling.klab.api.lang.kim.KlabStatement.Scope.PRIVATE);model.getObservables().add(source);
      var namespace=new KimNamespaceImpl();namespace.setUrn("private.models");namespace.getStatements().add(model);
      var validator=new DocumentSemanticValidator(actor);var diagnostics=validator.validate(namespace);
      assertTrue(diagnostics.stream().noneMatch(n->n.getLevel().severity>=3),diagnostics.toString());
      assertNull(actor.owl().getOntology("private.models"),"validation alone must not publish records");
      validator.exportObserverDeclarations();
      var records=org.integratedmodelling.klab.services.reasoner.owl.ObserverOntologySupport.records(List.of(actor.owl().getOntology("private.models").getOWLOntology()));
      assertEquals(1,records.size());assertEquals(org.integratedmodelling.klab.api.lang.kim.KlabStatement.Scope.PRIVATE,records.getFirst().sourceScope());
      assertEquals(org.integratedmodelling.klab.api.lang.kim.KlabStatement.Scope.PUBLIC,source.getScope(),"caller source is not mutated");
      assertTrue(org.integratedmodelling.klab.services.reasoner.owl.ObserverOntologySupport.records(List.of(actor.owl().getOntology("k").getOWLOntology())).isEmpty());
      var restored=actor.resolveObservable(records.getFirst().observableUrn());assertEquals("k:GeneralObserver",restored.getObserverSemantics().getUrn());
      source.setNamespace("rejected.models");namespace.setUrn("rejected.models");model.setNamespace("rejected.models");
      var missing=new KimObservableImpl();missing.setSemantics(StagedDeclarationContractTest.reference("w:Missing"));model.getDependencies().add(missing);
      var rejected=new DocumentSemanticValidator(actor);var errors=rejected.validate(namespace);
      assertTrue(errors.stream().anyMatch(n->n.getLevel().severity>=3),errors.toString());
      assertThrows(IllegalStateException.class,rejected::exportObserverDeclarations);
      assertNull(actor.owl().getOntology("rejected.models"),"failed model validation cannot leave export records");
    }
  }

  @Test void booleanObserverAlternativesSurviveCanonicalConstruction()throws Exception {
    try(var state=environment()) {
      var actor=state.actor();
      for(var operator:List.of(" or "," and ")) {
        var expression="k:LocalObserver"+operator+"k:OtherObserver";
        var canonical=actor.resolveConcept(expression);
        var observed=actor.resolveObservable("k:Q observed as ("+expression+")");
        assertEquals(canonical,observed.getObserverSemantics());
        assertEquals(2,actor.owl().getOperands(observed.getObserverSemantics()).size());
        var copy=SemanticsBuilder.create(observed,actor,actor.serviceScope()).buildObservable();
        assertEquals(canonical,copy.getObserverSemantics());assertEquals(observed.getUrn(),copy.getUrn());
      }
    }
  }

  @Test void structuredObserverControlsIdentityAndCannotForgeAgentKind()throws Exception {
    try(var state=environment()) {
      var actor=state.actor();var source=new KimObservableImpl();source.setSemantics(StagedDeclarationContractTest.reference("k:Q"));source.setUrn("k:Q");source.setObserverSemantics(StagedDeclarationContractTest.reference("k:LocalObserver"));
      source.setNamespace("spoofed.owner");
      var built=actor.declareObservable(source);assertTrue(built.getUrn().contains(" observed as k:LocalObserver"));assertEquals("k:Q",source.getUrn(),"caller bean remains unchanged");
      assertNull(actor.owl().getOntology("spoofed.owner"),"public declaration cannot claim lexical ownership or publish metadata");
      var forged=StagedDeclarationContractTest.reference("w:Tree");forged.setType(EnumSet.of(SemanticType.AGENT));source.setObserverSemantics(forged);
      assertThrows(org.integratedmodelling.klab.api.exceptions.KlabValidationException.class,()->actor.declareObservable(source));
    }
  }
  @Test void perspectiveNormalizationClearsOnlyObserverThroughCanonicalBuilder() throws Exception {
    try (var state=environment()) {
      var actor=state.actor();
      // Source-string units are a separate known baseline gap. Use the supported typed path.
      var metres=new org.integratedmodelling.klab.api.data.mediation.impl.UnitImpl("m");
      var feet=new org.integratedmodelling.klab.api.data.mediation.impl.UnitImpl("ft");
      var observed=actor.resolveObservable("k:Q observed as k:GeneralObserver")
          .builder(actor.serviceScope()).withUnit(metres).buildObservable();
      assertNotNull(observed.getUnit());
      var normalized=observed.builder(actor.serviceScope()).withObserverSemantics(null).buildObservable();
      var expected=actor.resolveObservable("k:Q").builder(actor.serviceScope()).withUnit(metres).buildObservable();
      assertNull(normalized.getObserverSemantics());
      assertEquals(expected.getUrn(),normalized.getUrn());
      assertEquals(observed.getSemantics(),normalized.getSemantics());
      assertEquals(observed.getUnit(),normalized.getUnit());
      assertEquals("k:GeneralObserver",observed.getObserverSemantics().getUrn());
      assertNotEquals(actor.resolveObservable("k:Q").builder(actor.serviceScope()).withUnit(feet)
          .buildObservable().getUrn(),normalized.getUrn());
    }
  }

  @Test void perspectiveEnumerationRetainsNonUrnConstraintsWithRealConstruction() throws Exception {
    try (var state=environment()) {
      var actor=state.actor();
      var scope=org.mockito.Mockito.mock(org.integratedmodelling.klab.api.scope.ContextScope.class);
      org.mockito.Mockito.when(scope.getService(org.integratedmodelling.klab.api.services.Reasoner.class)).thenReturn(actor);
      org.mockito.Mockito.when(scope.getService(org.integratedmodelling.klab.api.services.ResourcesService.class))
          .thenReturn(actor.serviceScope().getService(org.integratedmodelling.klab.api.services.ResourcesService.class));
      var query=(ObservableImpl)actor.resolveObservable("k:Q observed as k:GeneralObserver in m");
      query.setRange(org.integratedmodelling.klab.api.data.mediation.NumericRange.create(0,10));
      query.setDescriptionType(Contextualization.QUANTIFICATION);
      var retained=new ObservableImpl(query);
      var otherRange=new ObservableImpl(query);
      otherRange.setRange(org.integratedmodelling.klab.api.data.mediation.NumericRange.create(0,20));
      var otherActivity=new ObservableImpl(query);otherActivity.setDescriptionType(Contextualization.VERIFICATION);
      var records=new ArrayList<org.integratedmodelling.klab.api.knowledge.observation.Observation>();
      for(var observable:List.of(retained,otherRange,otherActivity)) {
        var observation=new org.integratedmodelling.klab.api.knowledge.observation.impl.ObservationImpl();
        observation.setId(records.size()+1);observation.setObservable(observable);records.add(observation);
      }
      org.mockito.Mockito.when(scope.getObservations()).thenReturn(records);
      assertEquals(List.of(records.getFirst()),new ArrayList<>(
          org.integratedmodelling.common.runtime.ObservationPerspectives.find(scope,query)));
    }
  }

  @Test void perspectivesUsePortableMediatorDefinitionsAcrossIndependentObjectsAndProductionJson() throws Exception {
    try (var state=environment()) {
      var actor=state.actor();
      var scope=org.mockito.Mockito.mock(org.integratedmodelling.klab.api.scope.ContextScope.class);
      org.mockito.Mockito.when(scope.getService(org.integratedmodelling.klab.api.services.Reasoner.class)).thenReturn(actor);
      org.mockito.Mockito.when(scope.getService(org.integratedmodelling.klab.api.services.ResourcesService.class))
          .thenReturn(actor.serviceScope().getService(org.integratedmodelling.klab.api.services.ResourcesService.class));
      var general=actor.resolveObservable("k:Q observed as k:GeneralObserver");
      var other=actor.resolveObservable("k:Q observed as k:OtherObserver");
      var metres=new org.integratedmodelling.klab.api.data.mediation.impl.UnitImpl("m");
      var sameMetres=new org.integratedmodelling.klab.api.data.mediation.impl.UnitImpl("m");
      var feet=new org.integratedmodelling.klab.api.data.mediation.impl.UnitImpl("ft");
      var distributed=new org.integratedmodelling.klab.api.data.mediation.impl.UnitImpl("m");
      distributed.getAggregatedDimensions().put(
          org.integratedmodelling.klab.api.knowledge.observation.scale.ExtentDimension.AREAL,
          org.integratedmodelling.klab.api.knowledge.observation.scale.ExtentDistribution.EXTENSIVE);
      var contextual=new org.integratedmodelling.klab.api.data.mediation.impl.UnitImpl("m");contextual.setContextual(true);
      var query=general.builder(actor.serviceScope()).withUnit(metres).buildObservable();
      var independent=other.builder(actor.serviceScope()).withUnit(sameMetres).buildObservable();
      var mapper=JacksonConfiguration.newObjectMapper();
      var recovered=mapper.readValue(mapper.writeValueAsBytes(independent),org.integratedmodelling.klab.api.knowledge.Observable.class);
      assertNotSame(query.getUnit(),recovered.getUnit());
      assertEquals("m",((org.integratedmodelling.klab.api.data.mediation.impl.UnitImpl)recovered.getUnit()).getDefinition());
      var candidates=List.of(recovered,
          other.builder(actor.serviceScope()).withUnit(feet).buildObservable(),
          other.builder(actor.serviceScope()).withUnit(distributed).buildObservable(),
          other.builder(actor.serviceScope()).withUnit(contextual).buildObservable());
      var records=new ArrayList<org.integratedmodelling.klab.api.knowledge.observation.Observation>();
      for(var candidate:candidates) {
        var record=new org.integratedmodelling.klab.api.knowledge.observation.impl.ObservationImpl();
        record.setId(records.size()+1);record.setObservable(candidate);records.add(record);
      }
      org.mockito.Mockito.when(scope.getObservations()).thenReturn(records);
      assertEquals(List.of(records.getFirst()),new ArrayList<>(
          org.integratedmodelling.common.runtime.ObservationPerspectives.find(scope,query)));
      var dollars=general.builder(actor.serviceScope()).withCurrency(
          new org.integratedmodelling.klab.api.data.mediation.impl.CurrencyImpl("USD")).buildObservable();
      var currencies=List.of(other.builder(actor.serviceScope()).withCurrency(
          new org.integratedmodelling.klab.api.data.mediation.impl.CurrencyImpl("USD")).buildObservable(),
          other.builder(actor.serviceScope()).withCurrency(
          new org.integratedmodelling.klab.api.data.mediation.impl.CurrencyImpl("EUR")).buildObservable());
      var moneyRecords=new ArrayList<org.integratedmodelling.klab.api.knowledge.observation.Observation>();
      for(var candidate:currencies) {
        var record=new org.integratedmodelling.klab.api.knowledge.observation.impl.ObservationImpl();
        record.setId(moneyRecords.size()+10);record.setObservable(candidate);moneyRecords.add(record);
      }
      org.mockito.Mockito.when(scope.getObservations()).thenReturn(moneyRecords);
      assertEquals(List.of(moneyRecords.getFirst()),new ArrayList<>(
          org.integratedmodelling.common.runtime.ObservationPerspectives.find(scope,dollars)));
    }
  }

}
