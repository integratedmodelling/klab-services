package org.integratedmodelling.klab.services.resolver;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.*;
import org.integratedmodelling.klab.api.knowledge.Model;
import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.services.Reasoner;
import org.integratedmodelling.klab.api.services.resolver.Prioritizer.Criterion;
import org.integratedmodelling.klab.api.services.resolver.ResolutionConstraint;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.common.knowledge.GeometryRepository;
import org.integratedmodelling.klab.configuration.ServiceConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;

class PrioritizerContractTest {
  @BeforeAll static void configure() { ServiceConfiguration.injectInstantiators(); }
  private final ContextScope scope = mock(ContextScope.class);
  private final Observable request = mock(Observable.class);
  private final Reasoner reasoner = mock(Reasoner.class);
  private PrioritizerImpl rank(Map<String,Integer> criteria) {
    when(scope.getService(Reasoner.class)).thenReturn(reasoner);
    return new PrioritizerImpl(scope,null,criteria,request,null);
  }
  private Model model(String urn, Map<String,Integer> overrides) {
    var model = mock(Model.class); var info = mock(Model.ResolutionInfo.class);
    when(model.getUrn()).thenReturn(urn); when(model.getResolutionInfo()).thenReturn(info);
    when(info.getResolutionCriteria()).thenReturn(overrides);
    when(model.getObservables()).thenReturn(List.of(mock(Observable.class)));
    return model;
  }
  private org.integratedmodelling.klab.api.lang.kim.impl.KimNamespaceImpl namespacePolicy(
      String name, Map<String,Integer> overrides) {
    var resources = mock(org.integratedmodelling.klab.api.services.ResourcesService.class);
    when(scope.getService(org.integratedmodelling.klab.api.services.ResourcesService.class)).thenReturn(resources);
    when(scope.getResolutionConstraints()).thenReturn(List.of(
        ResolutionConstraint.of(ResolutionConstraint.Type.ResolutionNamespace, name)));
    var namespace = new org.integratedmodelling.klab.api.lang.kim.impl.KimNamespaceImpl();
    namespace.setResolutionCriteria(overrides);
    when(resources.retrieve(name, org.integratedmodelling.klab.api.lang.kim.KimNamespace.class, scope))
        .thenReturn(namespace);
    return namespace;
  }
  @Test void scopePolicyIsImmutableAndIgnoresConflictingCandidatePolicies() {
    var namespace = namespacePolicy("caller", Map.of("im:semantic-concordance",1));
    var criteria = new HashMap<>(Map.of("im:semantic-concordance",2));
    var rank = rank(criteria); criteria.clear(); namespace.getResolutionCriteria().clear();
    var a=model("a",Map.of()); var b=model("b",Map.of("im:semantic-concordance",0));
    when(a.getNamespace()).thenReturn("external-a");
    when(b.getNamespace()).thenReturn("external-b");
    when(reasoner.semanticDistance(b.getObservables().getFirst(),request,null)).thenReturn(10);
    rank.prepare(List.of(a,b));
    assertTrue(rank.compare(a,b)<0); assertTrue(rank.compare(b,a)>0);
    assertEquals(List.of("im:semantic-concordance"),rank.listCriteria());
    assertThrows(UnsupportedOperationException.class,()->rank.getRanking(a).put(Criterion.LEXICAL_SCOPE,1.0));
    verify(reasoner,times(1)).semanticDistance(a.getObservables().getFirst(),request,null);
  }
  @Test void namespaceChangesBetweenScopesSelectDifferentPolicies() {
    namespacePolicy("outer",Map.of("im:semantic-concordance",0));
    var outer=rank(Map.of("im:semantic-concordance",1));
    namespacePolicy("inner",Map.of("im:semantic-concordance",1));
    var inner=rank(Map.of("im:semantic-concordance",1));
    var a=model("a",Map.of()); var b=model("b",Map.of());
    when(reasoner.semanticDistance(a.getObservables().getFirst(),request,null)).thenReturn(20);
    when(reasoner.semanticDistance(b.getObservables().getFirst(),request,null)).thenReturn(0);
    assertTrue(outer.compare(a,b)<0); assertTrue(inner.compare(a,b)>0);
  }
  @Test void namespacePolicyPreservesJacksonInterfaceTransport() throws Exception {
    var namespace = namespacePolicy("caller",Map.of("im:semantic-concordance",0));
    var mapper=org.integratedmodelling.common.data.jackson.JacksonConfiguration.newObjectMapper();
    var type=org.integratedmodelling.klab.api.lang.kim.KimNamespace.class;
    var restored=mapper.readValue(mapper.writerFor(type).writeValueAsString(namespace),type);
    assertEquals(namespace.getResolutionCriteria(),restored.getResolutionCriteria());
  }
  @Test void emptyPolicyAndTiesUseStableIdentityAndEqualPrioritiesAreDeterministic() {
    var rank=rank(Map.of()); var a=model("a",Map.of()); var b=model("b",Map.of());
    assertTrue(rank.compare(a,b)<0); assertTrue(rank.compare(b,a)>0); assertEquals(0,rank.compare(a,a));
    var tied=rank(Map.of("im:space-coverage",1,"im:lexical-scope",1));
    assertEquals(List.of("im:lexical-scope","im:space-coverage"),tied.listCriteria());
    assertThrows(org.integratedmodelling.klab.api.exceptions.KlabIllegalArgumentException.class,()->rank(Map.of("typo",1)));
  }
  @Test void sharedOverrideAndUnsupportedCriteriaAreVisible() {
    namespacePolicy("caller",Map.of("im:semantic-concordance",0));
    var rank=rank(Map.of("im:semantic-concordance",1,"im:reliability",2));
    var a=model("a",Map.of("im:semantic-concordance",0));
    var b=model("b",Map.of("im:semantic-concordance",0));
    rank.prepare(List.of(a,b));
    assertEquals(List.of("im:reliability"),rank.listCriteria());
    assertEquals(Set.of(Criterion.RELIABILITY),rank.unsupportedCriteria());
    assertEquals(-1.0,rank.getRanking(a).get(Criterion.RELIABILITY));
  }
  @Test void lexicalScopeHandlesMissingProjectsAndPreservesScenarioPrecedence() {
    when(scope.getResolutionConstraints()).thenReturn(List.of(
        ResolutionConstraint.of(ResolutionConstraint.Type.ResolutionNamespace,"local"),
        ResolutionConstraint.of(ResolutionConstraint.Type.ResolutionProject,"project"),
        ResolutionConstraint.of(ResolutionConstraint.Type.Scenarios,"scenario")));
    var rank=rank(Map.of("im:lexical-scope",1));var model=model("m",Map.of());
    when(model.getNamespace()).thenReturn("other"); assertEquals(0,rank.computeLexicalScope(model));
    when(model.getProjectName()).thenReturn("project"); assertEquals(50,rank.computeLexicalScope(model));
    when(model.getNamespace()).thenReturn("local"); assertEquals(75,rank.computeLexicalScope(model));
    when(model.getNamespace()).thenReturn("scenario"); assertEquals(100,rank.computeLexicalScope(model));
  }
  @Test void temporalScoresAreOverlapRatiosAndHandleMissingAndOpenBounds() {
    var time=GeometryRepository.INSTANCE.scale(Geometry.create("T0(1){tend=2000,tstart=1000,ttype=PHYSICAL}")).getTime();
    assertArrayEquals(new double[]{100,50,-1},PrioritizerImpl.computeTemporalCriteria(500,2500,time));
    assertArrayEquals(new double[]{50,100,-1},PrioritizerImpl.computeTemporalCriteria(1000,1500,time));
    assertArrayEquals(new double[]{0,0,-1},PrioritizerImpl.computeTemporalCriteria(3000,4000,time));
    assertArrayEquals(new double[]{100,0,-1},PrioritizerImpl.computeTemporalCriteria(-1,-1,time));
    assertArrayEquals(new double[]{-1,-1,-1},PrioritizerImpl.computeTemporalCriteria(0,1000,null));
    assertThrows(IllegalArgumentException.class,()->PrioritizerImpl.computeTemporalCriteria(2000,1000,time));
  }
  @Test void spatialScoresDecodePortableGeometryAndRemainFinite() {
    var requested=spatial(0,10);
    var rank=new PrioritizerImpl(scope,GeometryRepository.INSTANCE.scale(requested),
        Map.of("im:space-coverage",1,"im:space-specificity",2),request,null);
    var model=model("m",Map.of()); when(model.getCoverage()).thenReturn(spatial(0,20));
    assertEquals(100,rank.computeStandardCriterion(Criterion.SPACE_COVERAGE,model),0.1);
    assertEquals(50,rank.computeStandardCriterion(Criterion.SPACE_SPECIFICITY,model),0.1);
    var disjoint=model("outside",Map.of());when(disjoint.getCoverage()).thenReturn(spatial(30,40));
    assertEquals(0,rank.computeStandardCriterion(Criterion.SPACE_COVERAGE,disjoint));
    var unknown=model("unknown",Map.of());
    assertEquals(-1,rank.computeStandardCriterion(Criterion.SPACE_COVERAGE,unknown));
    var universal=model("universal",Map.of());when(universal.getCoverage()).thenReturn(Geometry.UNIVERSAL);
    assertEquals(100,rank.computeStandardCriterion(Criterion.SPACE_COVERAGE,universal));
    assertEquals(0,rank.computeStandardCriterion(Criterion.SPACE_SPECIFICITY,universal));
    var temporalOnly=model("time-only",Map.of());
    when(temporalOnly.getCoverage()).thenReturn(Geometry.create("T0(1){tstart=0,tend=1000,ttype=PHYSICAL}"));
    assertEquals(-1,rank.computeStandardCriterion(Criterion.SPACE_COVERAGE,temporalOnly));
  }
  private Geometry spatial(int west,int east) {
    return Geometry.create(org.integratedmodelling.klab.runtime.scale.space.ShapeImpl.create(
        "EPSG:3857 POLYGON (("+west+" 0,"+east+" 0,"+east+" 10,"+west+" 10,"+west+" 0))").encode());
  }

  @Test void generalSemanticDistanceIsAscendingUncappedAndComparatorIsTransitive() {
    var rank=rank(Map.of("im:semantic-concordance",1));
    var a=model("a",Map.of());var b=model("b",Map.of());var c=model("c",Map.of());
    when(reasoner.semanticDistance(a.getObservables().getFirst(),request,null)).thenReturn(0);
    when(reasoner.semanticDistance(b.getObservables().getFirst(),request,null)).thenReturn(150);
    when(reasoner.semanticDistance(c.getObservables().getFirst(),request,null)).thenReturn(300);
    rank.prepare(List.of(a,b,c));
    assertTrue(rank.compare(a,b)<0);assertTrue(rank.compare(b,c)<0);assertTrue(rank.compare(a,c)<0);
    assertEquals(300.0,rank.getRanking(c).get(Criterion.SEMANTIC_DISTANCE));
  }
}
