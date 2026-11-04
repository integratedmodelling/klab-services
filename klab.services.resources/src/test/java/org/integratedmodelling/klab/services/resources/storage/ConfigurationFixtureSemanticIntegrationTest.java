package org.integratedmodelling.klab.services.resources.storage;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.io.*;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import org.eclipse.emf.ecore.*;
import org.eclipse.xtext.parser.IParser;
import org.integratedmodelling.languages.*;
import org.integratedmodelling.languages.api.ParsedObject;
import org.integratedmodelling.klab.api.digitaltwin.ProcessPlan;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.knowledge.*;
import org.integratedmodelling.klab.api.knowledge.impl.WorldviewImpl;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.knowledge.observation.impl.ObservationImpl;
import org.integratedmodelling.klab.api.lang.kim.*;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.services.Reasoner;
import org.integratedmodelling.klab.api.services.reasoner.objects.*;
import org.integratedmodelling.klab.api.services.runtime.Notification;
import org.integratedmodelling.klab.runtime.language.*;
import org.integratedmodelling.klab.services.reasoner.internal.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Actual source parsing, accepted semantic generation and executable model admission.
 * No service Runtime, native configuration publication or recomputation is claimed here. */
class ConfigurationFixtureSemanticIntegrationTest {
  @TempDir Path directory;
  private Path fixture() {
    for(var root:List.of(Path.of("").toAbsolutePath(),Path.of("").toAbsolutePath().getParent())) {
      var candidate=root.resolve("klab.services.runtime/src/test/resources/consequences/configurations-dependent");
      if(Files.isDirectory(candidate))return candidate;
    }
    throw new AssertionError("Exact configurations-dependent fixture is unavailable");
  }
  @Test void acceptedPairRecipeDetectionRouteAndResizeAssignmentAreExecutableSource()throws Exception {
    org.integratedmodelling.klab.configuration.ServiceConfiguration.injectInstantiators();
    var root=fixture();
    var manager=WorkspaceDomainIngestionTest.manager(Files.createDirectory(directory.resolve("source")),
        Map.of("imod",Files.readString(root.resolve("src/imod.kwv"))));
    var ontologies=new ArrayList<>(manager.getOntologies(false));assertEquals(1,ontologies.size());
    assertTrue(ontologies.getFirst().getNotifications().stream().noneMatch(n->n.getLevel().severity>=Notification.Level.Error.severity),ontologies.getFirst().getNotifications().toString());
    var errors=new ArrayList<String>();
    var parser=new KimStandaloneSetup().createInjectorAndDoEMFRegistration().getInstance(IParser.class);
    var parsed=parser.parse(new StringReader(Files.readString(root.resolve("src/consequence/configurations/network.kim"))));
    assertFalse(parsed.hasSyntaxErrors());
    var syntax=new NamespaceSyntaxImpl((org.integratedmodelling.languages.kim.Model)parsed.getRootASTElement(),WorkspaceDomainIngestionTest.languageScope(manager)){
      protected void logWarning(ParsedObject p,EObject o,EStructuralFeature f,String m){}
      protected void logError(ParsedObject p,EObject o,EStructuralFeature f,String m){errors.add(m);}
    };
    var namespace=LanguageAdapter.INSTANCE.adaptNamespace(syntax,"configuration-fixture",List.of(),1L);
    assertTrue(errors.isEmpty(),errors.toString());assertEquals(5,namespace.getStatements().size());
    var updateParsed=parser.parse(new StringReader(Files.readString(root.resolve("src/consequence/configurations/update.kim"))));
    assertFalse(updateParsed.hasSyntaxErrors());
    var updateSyntax=new NamespaceSyntaxImpl((org.integratedmodelling.languages.kim.Model)updateParsed.getRootASTElement(),WorkspaceDomainIngestionTest.languageScope(manager)){
      protected void logWarning(ParsedObject p,EObject o,EStructuralFeature f,String m){}
      protected void logError(ParsedObject p,EObject o,EStructuralFeature f,String m){errors.add(m);}
    };
    var updateNamespace=LanguageAdapter.INSTANCE.adaptNamespace(updateSyntax,"configuration-fixture",List.of(),1L);
    assertTrue(errors.isEmpty(),errors.toString());assertEquals(1,updateNamespace.getStatements().size());
    assertTrue(namespace.getStatements().stream().map(KimModel.class::cast).allMatch(m->m.getScope()==KlabStatement.Scope.PUBLIC));
    assertEquals(KlabStatement.Scope.PRIVATE,((KimModel)updateNamespace.getStatements().getFirst()).getScope());
    WorkspaceDomainIngestionTest.set(manager,"behaviorParser",manager.new BehaviorParser());
    var actor=manager.parseAsset(Files.readString(root.resolve("testcases/consequence/configurations/testsuite.kactors")),org.integratedmodelling.klab.api.lang.kactors.KActorsBehavior.class);
    assertNotNull(actor);assertTrue(actor.getNotifications().stream().noneMatch(n->n.getLevel().severity>=Notification.Level.Error.severity),actor.getNotifications().toString());
    assertEquals(List.of("init","dependent_configuration_birth","dependent_configuration_recompute"),actor.getStatements().stream().map(s->s.getUrn()).toList());
    WorkspaceDomainIngestionTest.set(manager,"strategyParser",manager.new StrategyParser());
    var strategies=manager.parseAsset(Files.readString(root.resolve("strategies/observations.obs")),KimObservationStrategyDocument.class);
    assertNotNull(strategies);assertTrue(strategies.getNotifications().stream().noneMatch(n->n.getLevel().severity>=Notification.Level.Error.severity),strategies.getNotifications().toString());
    assertEquals(List.of("substantial.direct","characterization.direct","configuration.direct","dependent.direct"),strategies.getStatements().stream().map(s->s.getUrn()).toList());
    var worldview=new WorldviewImpl();worldview.setUrn("imod");worldview.setWorldviewId("imod");worldview.setEmpty(false);
    worldview.setOntologies(ontologies);worldview.setObservationStrategies(List.of(strategies));
    var core=new LinkedHashMap<String,byte[]>();
    for(var schema:List.of("odo.owl","klab.owl"))try(var input=getClass().getResourceAsStream("/knowledge/"+schema)){assertNotNull(input);core.put(schema,input.readAllBytes());}
    try(var runtime=new AcceptedSemanticRuntime(new SemanticEnvironmentFactory(directory.resolve("accepted"),core),"configuration-fixture")) {
      runtime.submit(worldview);assertTrue(runtime.awaitIdle(Duration.ofSeconds(30)));
      assertEquals(CandidatePublicationCoordinator.BeanStatus.VALID,runtime.status().beanStatus(),runtime.status().diagnostics().toString());
      assertEquals(CandidatePublicationCoordinator.LogicalStatus.VALID,runtime.status().logicalStatus(),runtime.status().diagnostics().toString());
      assertEquals(1,runtime.status().acceptedGeneration());
      var reasoner=(Reasoner)java.lang.reflect.Proxy.newProxyInstance(Reasoner.class.getClassLoader(),new Class[]{Reasoner.class},
          (proxy,method,args)->runtime.invoke(method.getName(),method.getParameterTypes(),args));
      var context=mock(ContextScope.class);when(context.getService(Reasoner.class)).thenReturn(reasoner);
      var bearer=new ObservationImpl();bearer.setId(50);bearer.setObservable(reasoner.resolveObservable("imod:Bearer"));bearer.setGeometry(Geometry.UNIVERSAL);
      when(context.getContextObservation()).thenReturn(bearer);
      var pair=new ObservationImpl();pair.setId(-7);pair.setObservable(reasoner.resolveObservable("imod:Pair"));pair.setGeometry(Geometry.UNIVERSAL);
      assertEquals(Contextualization.DETECTION,pair.getObservable().getContextualization());
      var routes=reasoner.computeObservationStrategies(pair,context);
      assertEquals(1,routes.size());assertEquals("configuration.direct",routes.getFirst().getUrn());
      assertEquals(ObservationStrategy.Operation.Type.OBSERVE,routes.getFirst().getOperations().getFirst().getType());
      when(context.getContextObservation()).thenReturn(null);
      assertTrue(reasoner.computeObservationStrategies(pair,context).isEmpty(),"Dependent configuration explanation requires its real bearer context");
      when(context.getContextObservation()).thenReturn(bearer);
      var recipe=reasoner.consequenceRules(new ConsequenceRuleQuery(List.of(reasoner.resolveConcept("imod:Pair")),
          List.of(reasoner.resolveConcept("imod:Height"),reasoner.resolveConcept("imod:Width")),Set.of(KimConsequenceRule.Kind.EMERGES_FROM),null));
      assertEquals(ConsequenceRuleResponse.Status.COMPLETE,recipe.status(),recipe.reason());assertEquals(1,recipe.rules().size());
      assertEquals("imod:Pair",recipe.rules().getFirst().owner().getUrn());
      assertEquals("imod:Bearer",recipe.rules().getFirst().rule().within().getUrn());assertNotNull(recipe.witness());
      var optionalChange=reasoner.resolveObservable("change in imod:Height in m optional");
      assertNotNull(optionalChange);assertNotNull(optionalChange.asConcept());
      var comparisonTerms=new TreeSet<String>();comparisonTerms.add(optionalChange.asConcept().getUrn());
      for(var leaf:org.integratedmodelling.klab.runtime.consequence.ConsequencePatterns.positiveLeaves(recipe.rules().getFirst().rule().target())) {
        var ingredient=reasoner.resolveConcept(leaf.getUrn());assertNotNull(ingredient);comparisonTerms.add(ingredient.getUrn());
        assertFalse(reasoner.is(optionalChange.asConcept(),ingredient),"Unresolved change process is not the authored quality ingredient "+leaf.getUrn());
        assertTrue(reasoner.is(ingredient,ingredient));
      }
      var comparisonEpoch=recipe.epoch();
      var comparisonRequest=new SemanticDependencyRequest(comparisonEpoch.serviceId(),comparisonEpoch.knowledgeRevision(),comparisonEpoch.semanticFingerprint(),List.copyOf(comparisonTerms));
      var comparisonWitness=reasoner.semanticDependencies(comparisonRequest,context);
      assertEquals(SemanticDependencyWitness.Status.COMPLETE,comparisonWitness.status(),comparisonWitness.reason());
      assertEquals(comparisonRequest.inputHash(),comparisonWitness.inputHash());
      System.out.println("CONFIGURATION_RELEVANCE_REAL_SEMANTICS "+optionalChange.asConcept().getUrn()+" NO Height/Width; exact retained dependency witness COMPLETE");
      var nativePair=reasoner.resolveObservable("imod:Pair");
      assertEquals(Contextualization.DETECTION,nativePair.getContextualization());
      assertEquals(recipe.rules().getFirst().owner().getUrn(),nativePair.asConcept().getUrn(),"Canonical target must retain the accepted recipe owner");
      assertEquals(nativePair.getUrn()+" optional",nativePair.builder(context).optional(true).buildObservable().getUrn());
      assertEquals("imod:Pair",nativePair.getUrn(),"Request optionality must not rewrite the native target");
      var dimensional=reasoner.resolveObservable("imod:Height in m");
      var meanings=List.of(dimensional.getUrn(),dimensional.asConcept().getUrn(),reasoner.resolveConcept(recipe.rules().getFirst().rule().target().getUrn()).getUrn());
      for(var meaning:meanings) {
        var epoch=recipe.epoch();var request=new SemanticDependencyRequest(epoch.serviceId(),epoch.knowledgeRevision(),epoch.semanticFingerprint(),List.of(meaning));
        var proof=reasoner.semanticDependencies(request,context);
        assertEquals(meaning.equals(dimensional.getUrn())?SemanticDependencyWitness.Status.UNKNOWN:SemanticDependencyWitness.Status.COMPLETE,proof.status(),meaning+": "+proof.reason());
      }
      var admission=SemanticValidationRequest.of(namespace,"configuration-fixture");admission.setExecutableModels(true);admission.setExecutionContext("imod:Bearer");
      SemanticValidationResponse response=runtime.invoke("validateDocument",new Class<?>[]{SemanticValidationRequest.class,org.integratedmodelling.klab.api.scope.Scope.class},new Object[]{admission,context});
      assertEquals(SemanticValidationResponse.Status.COMPLETE,response.getStatus(),response.getReason());
      assertTrue(response.getNotifications().stream().noneMatch(n->n.getLevel().severity>=Notification.Level.Error.severity),response.getNotifications().toString());
      var updateAdmission=SemanticValidationRequest.of(updateNamespace,"configuration-fixture");updateAdmission.setExecutableModels(true);updateAdmission.setExecutionContext("imod:Bearer");
      SemanticValidationResponse updateResponse=runtime.invoke("validateDocument",new Class<?>[]{SemanticValidationRequest.class,org.integratedmodelling.klab.api.scope.Scope.class},new Object[]{updateAdmission,context});
      assertEquals(SemanticValidationResponse.Status.COMPLETE,updateResponse.getStatus(),updateResponse.getReason());
      assertTrue(updateResponse.getNotifications().stream().noneMatch(n->n.getLevel().severity>=Notification.Level.Error.severity),updateResponse.getNotifications().toString());
      var resize=updateNamespace.getStatements().stream().filter(KimModel.class::isInstance).map(KimModel.class::cast)
          .filter(model->!model.getObservables().isEmpty()
              &&"imod:Resize".equals(model.getObservables().getFirst().getSemantics().getUrn()))
          .findFirst().orElseThrow();
      var executable=SourceModelMapper.load(resize,updateNamespace,context);
      assertEquals(1,executable.getComputation().size());assertNotNull(executable.getComputation().getFirst().getExpression());
      var analyzer=Class.forName("org.integratedmodelling.klab.services.resolver.ProcessModelBindings").getDeclaredMethod("analyze",Model.class,ContextScope.class);analyzer.setAccessible(true);
      var plan=(ProcessPlan)analyzer.invoke(null,executable,context);
      assertEquals(1,plan.bindings().size());assertEquals("height",plan.bindings().getFirst().name());
      assertTrue(plan.bindings().getFirst().assigned());assertEquals(ProcessPlan.Effect.AFFECTED,plan.bindings().getFirst().effect());
    }
  }
}
