package org.integratedmodelling.klab.services.runtime.digitaltwin;
import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import org.integratedmodelling.klab.api.data.KnowledgeGraph.*;
import org.integratedmodelling.klab.api.digitaltwin.GraphModel;
import org.integratedmodelling.klab.api.services.reasoner.objects.ConsequenceRuleResponse;
import org.integratedmodelling.klab.runtime.consequence.ConsequencePatternMatcher;
import org.junit.jupiter.api.Test;
class ConfigurationComponentsTest {
  static final ConsequenceRuleResponse.Epoch EPOCH=new ConsequenceRuleResponse.Epoch("r",1,"semantic-content-v2:"+"a".repeat(64),"s");
  static ConsequencePatternMatcher.Snapshot snapshot(long[][] relationships) {
    var assets=new TreeMap<Long,ConsequenceAssetSnapshot>();var facts=new ArrayList<ConsequencePatternMatcher.Fact>();
    var edges=new ArrayList<ConsequenceEdgeSnapshot>();var selections=new ArrayList<ConsequenceEdgeSelection>();
    for(var relation:relationships) {
      for(long id:relation)assets.put(id,new ConsequenceAssetSnapshot(id,"e"+id,List.of("Observation"),Map.of("observable","test:Meaning"),"1",null));
      facts.add(new ConsequencePatternMatcher.Fact(relation[0],"test:Relationship","1",false,0,100));
      for(var type:List.of(GraphModel.Relationship.HAS_RELATIONSHIP_SOURCE,GraphModel.Relationship.HAS_RELATIONSHIP_TARGET,GraphModel.Relationship.HAS_RELATIONSHIP_PARTICIPANT))
        selections.add(new ConsequenceEdgeSelection(relation[0],type,GraphModel.Relationship.Direction.OUTGOING));
      for(int n=1;n<3;n++)edges.add(new ConsequenceEdgeSnapshot("r"+relation[0]+"-"+n,
          new ConsequenceAssetRef("e"+relation[0],relation[0],List.of("Observation")),new ConsequenceAssetRef("e"+relation[n],relation[n],List.of("Observation")),
          n==1?GraphModel.Relationship.HAS_RELATIONSHIP_SOURCE:GraphModel.Relationship.HAS_RELATIONSHIP_TARGET,Map.of()));
    }
    var read=new ConsequenceReadSet("context",List.copyOf(assets.values()),selections,edges);
    return new ConsequencePatternMatcher.Snapshot("context",0,EPOCH,facts,Map.of(),true,read);
  }
  static Map<Long,ConfigurationComponents.EndpointKind> kinds(Set<Long> ids) {
    var result=new HashMap<Long,ConfigurationComponents.EndpointKind>();ids.forEach(id->result.put(id,ConfigurationComponents.EndpointKind.DIRECTED));return result;
  }
  @Test void disconnectedNetworksInsideOneCohortRemainTwoInstances() {
    var input=snapshot(new long[][]{{1,10,11},{2,11,12},{3,20,21},{4,21,22}});
    var components=ConfigurationComponents.partition(input,kinds(Set.of(1L,2L,3L,4L)));
    assertEquals(List.of(List.of(1L,2L),List.of(3L,4L)),components.stream().map(c->c.facts().stream().map(ConsequencePatternMatcher.Fact::id).toList()).toList());
    components.forEach(component->assertSame(input.reads(),component.reads()));
  }
  @Test void connectivityUsesNativeEndpointsAndHandlesReflexiveRelationships() {
    var components=ConfigurationComponents.partition(snapshot(new long[][]{{1,10,10},{2,10,11}}),kinds(Set.of(1L,2L)));
    assertEquals(1,components.size());assertEquals(2,components.getFirst().facts().size());
  }
  @Test void incompleteEndpointSelectionCannotProveADisconnectedPartition() {
    var input=snapshot(new long[][]{{1,10,11}});var read=input.reads();
    var incomplete=new ConsequenceReadSet(read.contextId(),read.assets(),read.selections().subList(0,2),read.edges());
    var invalid=new ConsequencePatternMatcher.Snapshot(input.contextId(),0,EPOCH,input.facts(),Map.of(),true,incomplete);
    assertThrows(ConfigurationComponents.UnsupportedGrouping.class,()->ConfigurationComponents.partition(invalid,kinds(Set.of(1L))));
  }
  @Test void endpointReplacementWithSamePublicIdRequiresItsExactNativeSnapshot() {
    var input=snapshot(new long[][]{{1,10,11}});var read=input.reads();var assets=new ArrayList<>(read.assets());
    assets.removeIf(a->a.id()==10);assets.add(new ConsequenceAssetSnapshot(10,"replaced",List.of("Observation"),Map.of(),"1",null));
    var invalid=new ConsequencePatternMatcher.Snapshot(input.contextId(),0,EPOCH,input.facts(),Map.of(),true,
        new ConsequenceReadSet(read.contextId(),assets,read.selections(),read.edges()));
    assertThrows(ConfigurationComponents.UnsupportedGrouping.class,()->ConfigurationComponents.partition(invalid,kinds(Set.of(1L))));
  }
  @Test void stableContinuityAndExplicitNewComponentsAreDistinct() {
    var identity=ConfigurationComponents.identify(List.of(Set.of(1L,2L,5L),Set.of(6L,7L)),Map.of(90L,Set.of(1L,2L)),Set.of(1));
    assertEquals(90L,identity.get(0).observationId());assertEquals(ConfigurationComponents.IdentityKind.NEW,identity.get(1).kind());
  }
  @Test void splitMergeAndEmptyNetworksRetainDeterministicPersistentIdentities() {
    var split=ConfigurationComponents.assign(List.of(Set.of(2L),Set.of(1L)),Map.of(90L,Set.of(1L,2L)),Set.of());
    assertEquals(90L,split.components().get(1).observationId());assertEquals(ConfigurationComponents.IdentityKind.NEW,split.components().get(0).kind());
    var merge=ConfigurationComponents.assign(List.of(Set.of(1L,2L)),Map.of(91L,Set.of(1L),90L,Set.of(2L)),Set.of());
    assertEquals(90L,merge.components().get(0).observationId());assertEquals(Set.of(91L),merge.emptyIdentities());
    var empty=ConfigurationComponents.assign(List.of(),Map.of(90L,Set.of(1L,2L)),Set.of());assertEquals(Set.of(90L),empty.emptyIdentities());
    assertEquals(ConfigurationComponents.IdentityKind.DEFERRED,ConfigurationComponents.identify(List.of(Set.of(3L)),Map.of(90L,Set.of(1L)),Set.of()).get(0).kind());
  }
  @Test void identityRequiresAWholeDisjointDurablePartition() {
    assertThrows(IllegalArgumentException.class,()->ConfigurationComponents.identify(List.of(Set.of(1L),Set.of(1L)),Map.of(),Set.of(0,1)));
    assertThrows(IllegalArgumentException.class,()->ConfigurationComponents.identify(List.of(Set.of(1L)),Map.of(-1L,Set.of(1L)),Set.of()));
    assertThrows(IllegalArgumentException.class,()->ConfigurationComponents.identify(List.of(Set.of(1L)),Map.of(),Set.of(1)));
  }

  @Test void canonicalUnorientedBondsUseOnlyTheirTwoCapturedParticipants() {
    var input=snapshot(new long[][]{{1,10,11},{2,11,12},{3,20,21}});var read=input.reads();
    var bonds=read.edges().stream().map(e->new ConsequenceEdgeSnapshot(e.elementId(),e.source(),e.target(),GraphModel.Relationship.HAS_RELATIONSHIP_PARTICIPANT,e.properties())).toList();
    var bondSnapshot=new ConsequencePatternMatcher.Snapshot(input.contextId(),0,EPOCH,input.facts(),Map.of(),true,
        new ConsequenceReadSet(read.contextId(),read.assets(),read.selections(),bonds));
    var kinds=Map.of(1L,ConfigurationComponents.EndpointKind.BOND,2L,ConfigurationComponents.EndpointKind.BOND,3L,ConfigurationComponents.EndpointKind.BOND);
    var components=ConfigurationComponents.partition(bondSnapshot,kinds);
    assertEquals(List.of(List.of(1L,2L),List.of(3L)),components.stream().map(c->c.facts().stream().map(ConsequencePatternMatcher.Fact::id).toList()).toList());
    assertThrows(ConfigurationComponents.UnsupportedGrouping.class,()->ConfigurationComponents.partition(input,kinds));
  }

  static ConsequencePatternMatcher.Snapshot contextDomain(ConsequencePatternMatcher.Snapshot input,Map<Long,Long> origins,Set<Long> subjects) {
    var facts=new ArrayList<ConsequencePatternMatcher.Fact>();
    input.facts().forEach(f->facts.add(new ConsequencePatternMatcher.Fact(f.id(),f.semantics(),f.resolvedSupport(),false,0,origins.get(f.id()))));
    subjects.forEach(id->facts.add(new ConsequencePatternMatcher.Fact(id,"test:Subject","1",false,0,origins.get(id))));
    var ids=new TreeSet<>(origins.values());var headers=new ArrayList<ConsequenceAssetSnapshot>();var edges=new ArrayList<ConsequenceEdgeSnapshot>();
    for(var id:ids){headers.add(new ConsequenceAssetSnapshot(id,"c"+id,List.of("Cohort"),Map.of("observable","test:Cohort"),null,null));
      edges.add(new ConsequenceEdgeSnapshot("catalogue"+id,new ConsequenceAssetRef("ctx",input.contextId(),List.of("Context")),new ConsequenceAssetRef("c"+id,id,List.of("Cohort")),GraphModel.Relationship.HAS_CHILD,Map.of()));}
    var catalogue=new ContextCohortCatalogue(input.contextId(),"ctx",0,headers,edges);
    return new ConsequencePatternMatcher.Snapshot(input.contextId(),0,EPOCH,facts,Map.of(),true,input.reads(),catalogue);
  }
  @Test void connectedRelationshipsAcrossSourceCohortsFormOneContextComponent() {
    var input=contextDomain(snapshot(new long[][]{{1,10,11},{2,11,12},{3,20,21}}),Map.of(1L,100L,2L,200L,3L,200L),Set.of());
    var components=ConfigurationComponents.partition(input,kinds(Set.of(1L,2L,3L)));
    assertEquals(List.of(List.of(1L,2L),List.of(3L)),components.stream().map(c->c.facts().stream().map(ConsequencePatternMatcher.Fact::id).toList()).toList());
    assertEquals(Set.of(100L,200L),components.getFirst().facts().stream().map(ConsequencePatternMatcher.Fact::cohortId).collect(java.util.stream.Collectors.toSet()));
    components.forEach(c->{assertSame(input.reads(),c.reads());assertSame(input.catalogue(),c.catalogue());});
  }
  @Test void authoredSubjectsJoinThroughStructuringRelationshipsNotBareCooccurrence() {
    var input=contextDomain(snapshot(new long[][]{{1,10,11},{2,11,12},{3,20,21}}),Map.of(1L,100L,2L,200L,3L,200L,10L,300L,12L,301L,21L,301L),Set.of(10L,12L,21L));
    var components=ConfigurationComponents.partition(input,kinds(Set.of(1L,2L,3L)));
    assertEquals(List.of(List.of(1L,2L,10L,12L),List.of(3L,21L)),components.stream().map(c->c.facts().stream().map(ConsequencePatternMatcher.Fact::id).toList()).toList());
    assertTrue(ConfigurationComponents.partition(input,Map.of()).isEmpty(),"Bare authored subjects cannot manufacture a network");
  }
  @Test void contextPlacementIsTypedStringNotFabricatedPositiveAsset() {
    var p=ConsequencePatternMatcher.Placement.context("context");assertEquals(0,p.id());assertEquals("context",p.contextId());
    assertThrows(IllegalArgumentException.class,()->new ConsequencePatternMatcher.Placement(ConsequencePatternMatcher.PlacementKind.ROOT_CONTEXT,100,"context"));
    assertThrows(IllegalArgumentException.class,()->new ConsequencePatternMatcher.Placement(ConsequencePatternMatcher.PlacementKind.ROOT_COHORT,0));
  }

  @Test void pureMatcherUsesCapturedContextDomainWithoutDiscardingOriginCohorts() {
    var input=contextDomain(snapshot(new long[][]{{1,10,11},{2,11,12}}),Map.of(1L,100L,2L,200L),Set.of());
    var semantics=new ConsequencePatternMatcher.Semantics(){
      public ConsequenceRuleResponse.Epoch epoch(){return EPOCH;}
      public ConsequencePatternMatcher.Decision matches(String actual,org.integratedmodelling.klab.api.lang.kim.KimConcept expected){return ConsequencePatternMatcher.Decision.YES;}
      public ConsequencePatternMatcher.Decision applicable(ConsequenceRuleResponse.Entry r,ConsequencePatternMatcher.Placement p,ConsequencePatternMatcher.Fact c){return ConsequencePatternMatcher.Decision.YES;}
    };
    var result=new ConsequencePatternMatcher().evaluate(DefaultConfigurationBackendTest.recipe("test:Relationship"),input,semantics,(e,c,p)->"1");
    assertEquals(ConsequencePatternMatcher.Outcome.MATCH,result.outcome());assertEquals(1,result.matches().size());
    assertEquals(List.of(1L,2L),result.matches().getFirst().ingredientIds());
    assertEquals(ConsequencePatternMatcher.Placement.context("context"),result.matches().getFirst().placement());
    assertSame(input.reads(),result.matches().getFirst().reads());
  }

}
