package org.integratedmodelling.klab.services.resources.storage;
import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;import java.io.*;import java.util.*;import java.time.Duration;
import org.eclipse.emf.ecore.*;import org.eclipse.xtext.parser.IParser;
import org.integratedmodelling.languages.*;import org.integratedmodelling.languages.api.ParsedObject;
import org.integratedmodelling.klab.api.knowledge.*;import org.integratedmodelling.klab.api.knowledge.impl.WorldviewImpl;
import org.integratedmodelling.klab.api.services.reasoner.objects.*;import org.integratedmodelling.klab.api.lang.kim.KimConsequenceRule;
import org.integratedmodelling.klab.api.services.runtime.Notification;
import org.integratedmodelling.klab.runtime.language.LanguageAdapter;
import org.integratedmodelling.klab.services.reasoner.internal.*;
import org.junit.jupiter.api.Test;import org.junit.jupiter.api.io.TempDir;
/** The separate core-authored acceptance world; never replaces or weakens authored imod. */
class MinimalRoleFixtureSemanticIntegrationTest {
 @TempDir Path directory;
 private Path fixture() {
  var here=Path.of("").toAbsolutePath();
  for(var root:List.of(here,here.getParent())) {
   var path=root.resolve("klab.services.runtime/src/test/resources/consequences/roles-minimal");if(Files.isDirectory(path))return path;
  }
  throw new AssertionError("Exact runtime roles-minimal test resources are unavailable");
 }
 @Test void exactMinimalRoleFixtureCompilesAcceptsIndexesAndAdmitsItsExecutableModels()throws Exception {
  org.integratedmodelling.klab.configuration.ServiceConfiguration.injectInstantiators();var root=fixture();var code=Files.readString(root.resolve("src/imod.kwv"));
  var manager=WorkspaceDomainIngestionTest.manager(Files.createDirectory(directory.resolve("source")),Map.of("imod",code));
  var ontologies=new ArrayList<>(manager.getOntologies(false));assertEquals(1,ontologies.size());
  assertTrue(ontologies.getFirst().getNotifications().stream().noneMatch(n->n.getLevel().severity>=Notification.Level.Error.severity),ontologies.getFirst().getNotifications().toString());
  var parser=new KimStandaloneSetup().createInjectorAndDoEMFRegistration().getInstance(IParser.class);
  var parsed=parser.parse(new StringReader(Files.readString(root.resolve("src/consequence/roles/process.kim"))));assertFalse(parsed.hasSyntaxErrors());
  var errors=new ArrayList<String>();var syntax=new NamespaceSyntaxImpl((org.integratedmodelling.languages.kim.Model)parsed.getRootASTElement(),WorkspaceDomainIngestionTest.languageScope(manager)){
   protected void logWarning(ParsedObject p,EObject o,EStructuralFeature f,String m){}
   protected void logError(ParsedObject p,EObject o,EStructuralFeature f,String m){errors.add(m);}};
  var namespace=LanguageAdapter.INSTANCE.adaptNamespace(syntax,"rolefixture-minimal",List.of(),1L);assertEquals(4,namespace.getStatements().size());assertTrue(errors.isEmpty(),errors.toString());
  WorkspaceDomainIngestionTest.set(manager,"strategyParser",manager.new StrategyParser());
  var strategies=manager.parseAsset(Files.readString(root.resolve("strategies/observations.obs")),org.integratedmodelling.klab.api.lang.kim.KimObservationStrategyDocument.class);
  assertNotNull(strategies);assertEquals(List.of("substantial.direct","characterization.direct","dependent.direct"),strategies.getStatements().stream().map(s->s.getUrn()).toList());
  assertTrue(strategies.getNotifications().stream().noneMatch(n->n.getLevel().severity>=Notification.Level.Error.severity),strategies.getNotifications().toString());
  var worldview=new WorldviewImpl();worldview.setUrn("imod");worldview.setWorldviewId("imod");worldview.setEmpty(false);worldview.setOntologies(ontologies);
  byte[] core;try(var input=getClass().getResourceAsStream("/knowledge/odo.owl")){assertNotNull(input);core=input.readAllBytes();}
  try(var runtime=new AcceptedSemanticRuntime(new SemanticEnvironmentFactory(directory.resolve("accepted"),Map.of("odo.owl",core)),"minimal-role-fixture")){
   runtime.submit(worldview);assertTrue(runtime.awaitIdle(Duration.ofSeconds(30)));assertEquals(CandidatePublicationCoordinator.BeanStatus.VALID,runtime.status().beanStatus(),runtime.status().diagnostics().toString());assertEquals(CandidatePublicationCoordinator.LogicalStatus.VALID,runtime.status().logicalStatus(),runtime.status().diagnostics().toString());assertEquals(1,runtime.status().acceptedGeneration());
   var context=org.mockito.Mockito.mock(org.integratedmodelling.klab.api.scope.ContextScope.class);
   var bearer=new org.integratedmodelling.klab.api.knowledge.observation.impl.ObservationImpl();bearer.setId(50);
   bearer.setObservable(runtime.invoke("resolveObservable",new Class<?>[]{String.class},new Object[]{"imod:Bearer"}));
   org.mockito.Mockito.when(context.getContextObservation()).thenReturn(bearer);
   var birth=new org.integratedmodelling.klab.api.knowledge.observation.impl.ObservationImpl();birth.setId(-7);birth.setGeometry(org.integratedmodelling.klab.api.geometry.Geometry.UNIVERSAL);
   birth.setObservable(runtime.invoke("resolveObservable",new Class<?>[]{String.class},new Object[]{"imod:Birth"}));
   List<ObservationStrategy> missing=runtime.invoke("computeObservationStrategies",new Class<?>[]{org.integratedmodelling.klab.api.knowledge.observation.Observation.class,org.integratedmodelling.klab.api.scope.ContextScope.class},new Object[]{birth,context});
   assertTrue(missing.isEmpty(),"Ontology/model declarations do not synthesize an authored process route");
   worldview.setObservationStrategies(List.of(strategies));runtime.submit(worldview);assertTrue(runtime.awaitIdle(Duration.ofSeconds(30)));
   assertEquals(CandidatePublicationCoordinator.LogicalStatus.VALID,runtime.status().logicalStatus(),runtime.status().diagnostics().toString());assertEquals(2,runtime.status().acceptedGeneration());
   List<ObservationStrategy> routes=runtime.invoke("computeObservationStrategies",new Class<?>[]{org.integratedmodelling.klab.api.knowledge.observation.Observation.class,org.integratedmodelling.klab.api.scope.ContextScope.class},new Object[]{birth,context});
   assertEquals(1,routes.size());assertEquals("dependent.direct",routes.getFirst().getUrn());assertEquals(0,routes.getFirst().getRank());
   assertEquals(ObservationStrategy.Operation.Type.OBSERVE,routes.getFirst().getOperations().getFirst().getType());
   org.mockito.Mockito.when(context.getContextObservation()).thenReturn(null);
   List<ObservationStrategy> noContext=runtime.invoke("computeObservationStrategies",new Class<?>[]{org.integratedmodelling.klab.api.knowledge.observation.Observation.class,org.integratedmodelling.klab.api.scope.ContextScope.class},new Object[]{birth,context});
   assertTrue(noContext.isEmpty(),"The canonical dependent route still requires an actual context");
   List<ObservationStrategy> substantial=runtime.invoke("computeObservationStrategies",new Class<?>[]{org.integratedmodelling.klab.api.knowledge.observation.Observation.class,org.integratedmodelling.klab.api.scope.ContextScope.class},new Object[]{bearer,context});
   assertEquals(1,substantial.size());assertEquals("substantial.direct",substantial.getFirst().getUrn());
   org.mockito.Mockito.when(context.getContextObservation()).thenReturn(bearer);
   Concept process=runtime.invoke("resolveConcept",new Class<?>[]{String.class},new Object[]{"imod:Birth"});Concept role=runtime.invoke("resolveConcept",new Class<?>[]{String.class},new Object[]{"imod:Marked"});
   Concept input=runtime.invoke("resolveConcept",new Class<?>[]{String.class},new Object[]{"imod:Input"});
   Concept implied=runtime.invoke("resolveConcept",new Class<?>[]{String.class},new Object[]{"imod:ImpliedValue"});
   for(var quality:List.of(input,implied)) {
    Concept inherent=runtime.invoke("inherent",new Class<?>[]{Semantics.class},new Object[]{quality});
    assertNotNull(inherent);assertEquals("imod:Bearer",inherent.getUrn());
   }
   // A consequence names a semantic target, not the units used by one resolving model.
   // Exact context queries retain that distinction rather than converting units implicitly.
   org.integratedmodelling.klab.api.knowledge.Observable impliedRequest=runtime.invoke("resolveObservable",new Class<?>[]{String.class},new Object[]{"imod:ImpliedValue"});
   org.integratedmodelling.klab.api.knowledge.Observable meteredQuery=runtime.invoke("resolveObservable",new Class<?>[]{String.class},new Object[]{"imod:ImpliedValue in m"});
   assertNotEquals(impliedRequest.getUrn(),meteredQuery.getUrn());
   assertEquals("imod:ImpliedValue",impliedRequest.getUrn());
   assertEquals("imod:ImpliedValue in m",meteredQuery.getUrn());
   assertEquals("imod:Bearer",((Concept)runtime.invoke("inherent",new Class<?>[]{Semantics.class},new Object[]{impliedRequest})).getUrn());
   assertEquals("imod:Bearer",((Concept)runtime.invoke("inherent",new Class<?>[]{Semantics.class},new Object[]{meteredQuery})).getUrn());
   assertEquals(Boolean.TRUE,runtime.invoke("affectedBy",new Class<?>[]{Semantics.class,Semantics.class},new Object[]{input,process}));
   assertEquals(Boolean.FALSE,runtime.invoke("affectedBy",new Class<?>[]{Semantics.class,Semantics.class},new Object[]{implied,process}),"ImpliedValue is role-triggered, not a new process assignment");
   var actualReasoner=(org.integratedmodelling.klab.api.services.Reasoner)java.lang.reflect.Proxy.newProxyInstance(
       org.integratedmodelling.klab.api.services.Reasoner.class.getClassLoader(),new Class[]{org.integratedmodelling.klab.api.services.Reasoner.class},
       (proxy,method,args)->runtime.invoke(method.getName(),method.getParameterTypes(),args));
   org.integratedmodelling.klab.api.knowledge.Observable inputObservable=runtime.invoke("resolveObservable",new Class<?>[]{String.class},new Object[]{"imod:Input in m"});
   org.mockito.Mockito.when(context.getService(org.integratedmodelling.klab.api.services.Reasoner.class)).thenReturn(actualReasoner);
   var optionalTarget=impliedRequest.builder(context).optional(true).buildObservable();
   org.integratedmodelling.klab.api.knowledge.Observable exactOptionalQuery=runtime.invoke("resolveObservable",new Class<?>[]{String.class},new Object[]{"imod:ImpliedValue optional"});
   assertEquals(exactOptionalQuery.getUrn(),optionalTarget.getUrn());
   assertNotEquals(meteredQuery.getUrn(),optionalTarget.getUrn());
   assertEquals("imod:Bearer",actualReasoner.inherent(optionalTarget).getUrn());
   var characterization=actualReasoner.resolveObservable("imod:Marked").builder(context)
       .of(bearer.getObservable().getSemantics()).optional(true).buildObservable();
   assertEquals(Contextualization.CHARACTERIZATION,characterization.getContextualization());
   var directive=new org.integratedmodelling.klab.api.knowledge.observation.impl.ObservationImpl();
   directive.setId(-8);directive.setObservable(characterization);directive.setGeometry(org.integratedmodelling.klab.api.geometry.Geometry.UNIVERSAL);
   var characterizationRoutes=actualReasoner.computeObservationStrategies(directive,context);
   assertEquals(1,characterizationRoutes.size(),"Model absence cannot be established without an authored characterization discovery route");
   assertEquals("characterization.direct",characterizationRoutes.getFirst().getUrn());
   org.mockito.Mockito.when(context.getContextObservation()).thenReturn(null);
   assertTrue(actualReasoner.computeObservationStrategies(directive,context).isEmpty(),"Characterization still requires its accessible target context");
   org.mockito.Mockito.when(context.getContextObservation()).thenReturn(bearer);
   assertEquals(org.integratedmodelling.klab.api.digitaltwin.ProcessPlan.Bearer.CONTEXT,
       org.integratedmodelling.klab.runtime.language.OccurrentSemantics.bearer(inputObservable,birth.getObservable(),bearer.getObservable(),actualReasoner));
   assertThrows(org.integratedmodelling.klab.api.exceptions.KlabValidationException.class,
       ()->org.integratedmodelling.klab.runtime.language.OccurrentSemantics.bearer(inputObservable,birth.getObservable(),null,actualReasoner));
   ConsequenceRuleResponse rules=runtime.invoke("consequenceRules",new Class<?>[]{ConsequenceRuleQuery.class},new Object[]{new ConsequenceRuleQuery(List.of(process,role),List.of(),Set.of(KimConsequenceRule.Kind.CONFERS,KimConsequenceRule.Kind.IMPLIES),null)});
   assertEquals(ConsequenceRuleResponse.Status.COMPLETE,rules.status(),rules.reason());assertEquals(2,rules.rules().size());
   assertTrue(rules.rules().stream().anyMatch(e->e.rule().kind()==KimConsequenceRule.Kind.CONFERS&&e.rule().target().getUrn().equals("imod:Marked")&&e.rule().within().getUrn().equals("imod:Bearer")));
   assertTrue(rules.rules().stream().anyMatch(e->e.rule().kind()==KimConsequenceRule.Kind.IMPLIES&&e.rule().target().getUrn().equals("imod:ImpliedValue")));
   var request=SemanticValidationRequest.of(namespace,"minimal-role-fixture");request.setExecutableModels(true);request.setExecutionContext("imod:Bearer");
   SemanticValidationResponse result=runtime.invoke("validateDocument",new Class<?>[]{SemanticValidationRequest.class,org.integratedmodelling.klab.api.scope.Scope.class},new Object[]{request,org.mockito.Mockito.mock(org.integratedmodelling.klab.api.scope.Scope.class)});
   assertEquals(SemanticValidationResponse.Status.COMPLETE,result.getStatus(),result.getReason());assertTrue(result.getNotifications().stream().noneMatch(n->n.getLevel().severity>=Notification.Level.Error.severity),result.getNotifications().toString());
  }
 }
}
