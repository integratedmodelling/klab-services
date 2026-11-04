package org.integratedmodelling.klab.services.runtime.digitaltwin;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.*;
import org.integratedmodelling.common.knowledge.*;
import org.integratedmodelling.klab.api.data.KnowledgeGraph;
import org.integratedmodelling.klab.api.data.KnowledgeGraph.*;
import org.integratedmodelling.klab.api.digitaltwin.*;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.knowledge.*;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.knowledge.observation.impl.ObservationImpl;
import org.integratedmodelling.klab.runtime.consequence.ConsequencePatternMatcher;
import org.integratedmodelling.klab.services.scopes.ServiceContextScope;
import org.junit.jupiter.api.*;
class ConfigurationNativeInputsTest {
 ServiceContextScope scope;KnowledgeGraph graph;KnowledgeGraph.Transaction transaction;
 Map<Long,ConsequenceAssetSnapshot> assets;Map<Long,Observation> observations;
 List<ConsequenceEdgeSnapshot> edges;
 static ConsequenceAssetRef ref(long id){return new ConsequenceAssetRef("e"+id,id,List.of(id==10?"Cohort":"Observation"));}
 static ConsequenceEdgeSnapshot edge(long from,long to,GraphModel.Relationship type){return new ConsequenceEdgeSnapshot(from+":"+type+":"+to,ref(from),ref(to),type,Map.of());}
 static ObservationImpl observation(long id,boolean relationship) {
  var concept=new ConceptImpl();concept.setUrn("test:Meaning"+id);concept.setType(relationship?EnumSet.of(SemanticType.RELATIONSHIP,SemanticType.UNIDIRECTIONAL,SemanticType.STRUCTURAL,SemanticType.OBSERVABLE,SemanticType.COUNTABLE):EnumSet.of(SemanticType.SUBJECT,SemanticType.OBSERVABLE,SemanticType.COUNTABLE));
  concept.setDescriptionType(relationship?Contextualization.CONNECTION:Contextualization.ACKNOWLEDGEMENT);var observable=new ObservableImpl();observable.setUrn(concept.getUrn());observable.setSemantics(concept);observable.setDescriptionType(concept.getDescriptionType());observable.setArtifactType(Artifact.Type.OBJECT);
  var observation=new ObservationImpl();observation.setId(id);observation.setObservable(observable);observation.setGeometry(Geometry.create("1"));observation.setResolvedCoverage(1);return observation;
 }
 @BeforeEach void setup()throws Exception {
  scope=mock(ServiceContextScope.class);graph=mock(KnowledgeGraph.class);transaction=mock(KnowledgeGraph.Transaction.class);var twin=mock(DigitalTwin.class);
  when(scope.getId()).thenReturn("context");when(scope.getDigitalTwin()).thenReturn(twin);when(twin.getKnowledgeGraph()).thenReturn(graph);when(graph.createTransaction(scope)).thenReturn(transaction);
  assets=new TreeMap<>();observations=new TreeMap<>();
  for(long id:List.of(1L,10L,11L,12L)) {assets.put(id,new ConsequenceAssetSnapshot(id,"e"+id,List.of(id==10?"Cohort":"Observation"),Map.of("observable","test:Meaning"+id),"1",null));if(id!=10)observations.put(id,observation(id,id==1));}
  edges=List.of(edge(10,1,GraphModel.Relationship.HAS_MEMBER),edge(1,11,GraphModel.Relationship.HAS_RELATIONSHIP_SOURCE),edge(1,12,GraphModel.Relationship.HAS_RELATIONSHIP_TARGET));
  when(transaction.captureConsequenceInputs(any(),any())).thenAnswer(call->{Collection<Long> ids=call.getArgument(0);Collection<ConsequenceEdgeSelection> selections=call.getArgument(1);
   var selected=edges.stream().filter(edge->selections.stream().anyMatch(selection->selection.relationship()==edge.relationship()&&selection.direction()==GraphModel.Relationship.Direction.OUTGOING&&Objects.equals(edge.source().id(),selection.assetId()))).toList();
   return new ConsequenceReadSet("context",ids.stream().sorted().map(assets::get).toList(),List.copyOf(selections),selected);
  });
  when(graph.materializeConsequenceObservation(any(),eq(scope))).thenAnswer(call->observations.get(call.getArgument(0,ConsequenceAssetSnapshot.class).id()));
 }
 ConfigurationNativeInputs.Capture capture(){return ConfigurationNativeInputs.capture(new ConsequencePatternMatcher.Placement(ConsequencePatternMatcher.PlacementKind.ROOT_COHORT,10),0,scope);}
 @Test void projectionUsesCanonicalCapturedPayloadAndCompleteLocalEndpointDomain() {
  var capture=capture();assertEquals(List.of(1L,10L,11L,12L),capture.reads().assetIds());assertEquals(List.of(1L),capture.facts().stream().map(ConsequencePatternMatcher.Fact::id).toList());
  assertEquals("test:Meaning1",capture.facts().getFirst().semantics());assertEquals("1",capture.facts().getFirst().resolvedSupport());assertEquals(ConfigurationComponents.EndpointKind.DIRECTED,capture.relationships().get(1L));
  verify(graph).materializeConsequenceObservation(assets.get(1L),scope);verify(transaction,times(2)).validateConsequenceInputs(any());verify(graph,never()).query(any(),any());
 }
 @Test void changedOrMissingMaterializedNativeIdentityCannotBecomeAMatcherFact() {
  when(graph.materializeConsequenceObservation(eq(assets.get(1L)),eq(scope))).thenReturn(observation(999,true));
  assertThrows(IllegalStateException.class,this::capture);
 }
 @Test void unresolvedSupportOrUnspecifiedRelationshipOrientationDefersInsteadOfInventingFacts()throws Exception {
  observations.get(1L).getObservable().getSemantics().getType().remove(SemanticType.UNIDIRECTIONAL);
  assertThrows(ConfigurationComponents.UnsupportedGrouping.class,this::capture);
  setup();((ObservationImpl)observations.get(1L)).setResolvedCoverage(0);
  assertNull(capture().facts().getFirst().resolvedSupport(),"Unresolved support is retained as unknown until recipe relevance is established");
 }
 @Test void partialCoverageCannotPromoteFullRequestedGeometryToActualDetectionSupport() {
  var observation=(ObservationImpl)observations.get(1L);var requested=Geometry.create("T0(1){ttype=PHYSICAL,tstart=0,tend=1000}");
  observation.setGeometry(requested);observation.setResolvedCoverage(0.5);
  var captured=capture();assertNull(captured.facts().getFirst().resolvedSupport());
  assertTrue(captured.reads().assetIds().contains(1L));assertSame(requested,observation.getGeometry());assertEquals(0.5,observation.getResolvedCoverage());
 }


 @Test void provisionalRoundsCloseBeforeFinalSortedUnionLocksAndValidates()throws Exception {
  var rounds=new ArrayList<KnowledgeGraph.Transaction>();var closed=new HashSet<KnowledgeGraph.Transaction>();
  when(graph.createTransaction(scope)).thenAnswer(call->{
   assertTrue(closed.containsAll(rounds),"Earlier provisional transaction must close before new locks");
   var next=mock(KnowledgeGraph.Transaction.class);rounds.add(next);
   when(next.captureConsequenceInputs(any(),any())).thenAnswer(c->{
    Collection<Long> ids=c.getArgument(0);Collection<ConsequenceEdgeSelection> selections=c.getArgument(1);
    assertEquals(new ArrayList<>(new TreeSet<>(ids)),new ArrayList<>(ids),"Complete lock union must be sorted");
    var selected=edges.stream().filter(edge->selections.stream().anyMatch(selection->selection.relationship()==edge.relationship()&&selection.direction()==GraphModel.Relationship.Direction.OUTGOING&&Objects.equals(edge.source().id(),selection.assetId()))).toList();
    return new ConsequenceReadSet("context",ids.stream().sorted().map(assets::get).toList(),List.copyOf(selections),selected);
   });
   doAnswer(c->{closed.add(next);return null;}).when(next).close();return next;
  });
  var captured=capture();assertEquals(3,rounds.size());assertEquals(3,closed.size());
  verify(rounds.get(0),never()).validateConsequenceInputs(any());verify(rounds.get(1),never()).validateConsequenceInputs(any());
  verify(rounds.get(2),times(2)).validateConsequenceInputs(any());assertEquals(List.of(1L,10L,11L,12L),captured.reads().assetIds());
 }

 @Test void rootWithinUsesTheActualCapturedFocusAndNeverARepresentativeIngredient() {
  var focus=observation(20,false);assets.put(20L,new ConsequenceAssetSnapshot(20,"e20",List.of("Observation"),Map.of("observable",focus.getObservable().getUrn()),"1",null));observations.put(20L,focus);
  when(scope.getContextObservation()).thenReturn(focus);
  var captured=capture();var placement=new ConsequencePatternMatcher.Placement(ConsequencePatternMatcher.PlacementKind.ROOT_COHORT,10);
  assertEquals(20,captured.contexts().get(placement).id());assertEquals(focus.getObservable().getUrn(),captured.contexts().get(placement).semantics());
  assertTrue(captured.reads().assetIds().contains(20L));assertNotEquals(1,captured.contexts().get(placement).id());
 }
 @Test void absentFocusRemainsAbsentAndChangingFocusCannotCertifyWithin() {
  assertTrue(capture().contexts().isEmpty());var first=observation(11,false);var other=observation(12,false);
  when(scope.getContextObservation()).thenReturn(first,other);
  assertThrows(ConfigurationComponents.UnsupportedGrouping.class,this::capture);
 }
 @Test void rootCaptureQueriesOnlyRelevantCohortMembershipAndPreservesCompleteCatalogue() {
  var reasoner=mock(org.integratedmodelling.klab.api.services.Reasoner.class);when(scope.getService(org.integratedmodelling.klab.api.services.Reasoner.class)).thenReturn(reasoner);
  var meaning=observations.get(1L).getObservable().asConcept();
  when(reasoner.resolveConcept("test:Meaning1")).thenReturn(meaning);when(reasoner.baseSubstantialType(any(),eq(scope))).thenReturn(meaning);
  var irrelevant=observation(999,false).getObservable().asConcept();when(reasoner.resolveConcept("test:Meaning999")).thenReturn(irrelevant);
  when(reasoner.is(any(),any())).thenAnswer(call->call.getArgument(0,Concept.class).getUrn().equals(call.getArgument(1,Concept.class).getUrn()));
  var headers=List.of(new ConsequenceAssetSnapshot(10,"e10",List.of("Cohort"),Map.of("observable","test:Meaning1"),null,null),new ConsequenceAssetSnapshot(20,"e20",List.of("Cohort"),Map.of("observable","test:Meaning999"),null,null));
  assets.put(20L,headers.get(1));
  var catalogue=new ContextCohortCatalogue("context","ctx",2,headers,headers.stream().map(h->new ConsequenceEdgeSnapshot("catalogue"+h.id(),new ConsequenceAssetRef("ctx","context",List.of("Context")),new ConsequenceAssetRef(h.elementId(),h.id(),h.labels()),GraphModel.Relationship.HAS_CHILD,Map.of())).toList());
  when(transaction.captureContextCohortCatalogue()).thenReturn(catalogue);
  var capture=ConfigurationNativeInputs.captureRoot(List.of(DefaultConfigurationBackendTest.recipe("test:Meaning1")),0,scope);
  assertSame(catalogue,capture.catalogue());assertEquals(List.of(1L),capture.facts().stream().map(ConsequencePatternMatcher.Fact::id).toList());assertEquals(10,capture.facts().getFirst().cohortId());
  assertTrue(capture.domainTerms().contains("test:Meaning999"),"Rejected cohort comparison is part of fresh semantic authority");
  assertTrue(capture.reads().assetIds().contains(20L),"Every header participates in final sorted union even when membership is not queried");
  assertTrue(capture.reads().selections().stream().noneMatch(selection->selection.assetId()==20&&selection.relationship()==GraphModel.Relationship.HAS_MEMBER));
  verify(transaction).validateContextCohortCatalogue(catalogue);verify(graph,never()).query(any(),any());
 }

 @Test void qualifiedFallbackBaseStillIncludesItsUnqualifiedOriginalCohort() {
  var header=observation(11,false).getObservable().asConcept();var qualified=observation(12,false).getObservable().asConcept();
  var reasoner=mock(org.integratedmodelling.klab.api.services.Reasoner.class);when(reasoner.is(qualified,header)).thenReturn(true);
  var terms=new TreeSet<String>();assertTrue(ConfigurationNativeInputs.cohortMayMatch(header,qualified,reasoner,terms));
  assertEquals(Set.of(header.getUrn(),qualified.getUrn()),terms);verify(reasoner,never()).compose(any(),any());
 }
 @Test void ambiguousSiblingCohortsNeedActualIntersectionProofNotNegativeSubtypeChecks() {
  var header=observation(11,false).getObservable().asConcept();var base=observation(12,false).getObservable().asConcept();var intersection=observation(13,false).getObservable().asConcept();
  var reasoner=mock(org.integratedmodelling.klab.api.services.Reasoner.class);
  when(reasoner.compose(eq(List.of(header,base)),eq(org.integratedmodelling.klab.api.lang.LogicalConnector.INTERSECTION))).thenReturn(intersection);
  when(reasoner.resolveConcept(intersection.getUrn())).thenReturn(intersection);when(reasoner.satisfiable(intersection)).thenReturn(true);
  var terms=new TreeSet<String>();assertTrue(ConfigurationNativeInputs.cohortMayMatch(header,base,reasoner,terms));assertTrue(terms.contains(intersection.getUrn()));
  when(reasoner.satisfiable(intersection)).thenReturn(false);assertFalse(ConfigurationNativeInputs.cohortMayMatch(header,base,reasoner,new TreeSet<>()));
 }
 @Test void unavailableIntersectionOrUnknownCategoryCannotCertifyAnEmptyDomain() {
  var header=observation(11,false).getObservable().asConcept();var base=observation(12,false).getObservable().asConcept();var reasoner=mock(org.integratedmodelling.klab.api.services.Reasoner.class);
  assertThrows(ConfigurationComponents.UnsupportedGrouping.class,()->ConfigurationNativeInputs.cohortMayMatch(header,base,reasoner,new TreeSet<>()));
  when(reasoner.compose(any(),any())).thenThrow(new IllegalStateException("retained closure unavailable"));
  assertThrows(ConfigurationComponents.UnsupportedGrouping.class,()->ConfigurationNativeInputs.cohortMayMatch(header,base,reasoner,new TreeSet<>()));
  var unknown=new ConceptImpl();unknown.setUrn("test:Unknown");unknown.setType(EnumSet.of(SemanticType.NOTHING));
  assertThrows(ConfigurationComponents.UnsupportedGrouping.class,()->ConfigurationNativeInputs.cohortMayMatch(unknown,base,reasoner,new TreeSet<>()));
 }
 @Test void knownRelationshipAndNonrelationshipHeadersUseNativeTypedExclusion() {
  var header=observation(11,false).getObservable().asConcept();var relation=observation(1,true).getObservable().asConcept();var reasoner=mock(org.integratedmodelling.klab.api.services.Reasoner.class);
  assertFalse(ConfigurationNativeInputs.cohortMayMatch(header,relation,reasoner,new TreeSet<>()));verify(reasoner,never()).compose(any(),any());
 }

}
