package org.integratedmodelling.klab.services.reasoner.owl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.nio.file.*;
import java.util.*;
import org.integratedmodelling.common.lang.Axiom;
import org.integratedmodelling.klab.api.knowledge.*;
import org.integratedmodelling.klab.api.scope.Scope;
import org.integratedmodelling.klab.api.services.Authority;
import org.integratedmodelling.klab.authority.land.LandAuthority;
import org.integratedmodelling.klab.authority.land.PatternCatalog;
import org.integratedmodelling.klab.services.reasoner.*;
import org.integratedmodelling.klab.services.reasoner.internal.*;
import org.junit.jupiter.api.Test;
import org.semanticweb.owlapi.apibinding.OWLManager;
import org.semanticweb.owlapi.reasoner.structural.StructuralReasonerFactory;

/** Real provider, binding, resolver, Reasoner dispatch and OWL class hierarchy; no live services. */
class LandEntryHierarchyDelegationTest {
  static class TestOWL extends OWL {
    TestOWL() { super(mock(Scope.class)); manager=OWLManager.createOWLOntologyManager(); }
    @Override public synchronized void registerWithReasoner(Ontology ontology) {}
    @Override public synchronized void flushReasoner() {}
    @Override public boolean isOn() { return true; }
    @Override public boolean is(Semantics candidate,Semantics target) {
      try {
        var merged=manager.createOntology(manager.ontologies().flatMap(o->o.axioms()).toList());
        var reasoner=new StructuralReasonerFactory().createReasoner(merged);
        try { return reasoner.getSuperClasses(getOWLClass(candidate.asConcept()),false)
            .containsEntity(getOWLClass(target.asConcept())); }
        finally { reasoner.dispose();manager.removeOntology(merged); }
      } catch(Exception failure) { throw new IllegalStateException(failure); }
    }
  }
  static void field(Object target,String name,Object value)throws Exception {
    var f=ReasonerService.class.getDeclaredField(name);f.setAccessible(true);f.set(target,value);
  }
  static class Fixture implements AutoCloseable {
    final LandAuthority provider=spy(new LandAuthority());
    final AuthorityBindings bindings=new AuthorityBindings();
    final TestOWL owl=new TestOWL();
    final AuthorityIdentityResolver resolver=new AuthorityIdentityResolver(owl,bindings);
    final ReasonerService host=mock(ReasonerService.class,CALLS_REAL_METHODS);
    final String bridge;
    Fixture()throws Exception { this(false); }
    Fixture(boolean synthetic)throws Exception {
      if(synthetic) {
        var entries=new ArrayList<PatternCatalog.Entry>();
        for(var item:Map.of("Vegetation",List.<String>of(),"Forest",List.of("Vegetation"),
            "Seasonal",List.<String>of(),"DeciduousForest",List.of("Forest","Seasonal"),"Water",List.<String>of()).entrySet())
          entries.add(new PatternCatalog.Entry(item.getKey(),item.getKey(),null,item.getKey(),java.net.URI.create("https://example.org/synthetic"),
              List.of("Synthetic conformance fixture"),null,PatternCatalog.EntryVerification.UNVERIFIED,
              PatternCatalog.DefinitionReview.ABSENT,List.of(),item.getValue()));
        provider.registerCatalog(new PatternCatalog("synthetic",PatternCatalog.Domain.LULC,"r1",java.net.URI.create("https://example.org/synthetic"),entries));
      }
      var catalog=synthetic?"synthetic":"corine_entry_hierarchy_example";
      owl.requireOntology("land").define(List.of(Axiom.ClassAssertion("LandCover",EnumSet.of(SemanticType.IDENTITY))));
      var request=new Authority.ConfigurationRequest("example","LAND","land:LandCover",Map.of(
          "urn",LandAuthority.URN,"mode","ENTRY_ONLY","domain","LULC","catalogs",List.of(catalog),
          "codelists",Map.of(catalog,"land.corine")));
      bridge=bindings.configure(request,provider);
      field(host,"owl",owl);field(host,"authorityIdentityResolver",resolver);
      field(host,"subsumption",com.github.benmanes.caffeine.cache.Caffeine.newBuilder().build());
      doReturn(0L).when(host).knowledgeRevision();
    }
    Concept term(String name) { return resolver.resolve("land.corine",name); }
    String code(String name) { return bindings.alias("land.corine",name)[1]; }
    @Override public void close() { provider.close(); }
  }
  @Test void providerOwnsDirectionalDistanceAndTransitiveHierarchyWithoutPatternDefinitions()throws Exception {
    try(var f=new Fixture()) {
      var leaf=f.term("BroadLeavedForest");var parent=f.term("Forest");var grandparent=f.term("ForestAndSeminaturalAreas");
      assertTrue(f.owl.is(leaf,parent));assertTrue(f.owl.is(leaf,grandparent));
      assertTrue(f.host.is(leaf,parent));assertTrue(f.host.is(leaf,grandparent));
      assertFalse(f.host.is(parent,leaf));assertFalse(f.host.is(f.term("ContinuousUrbanFabric"),parent));
      var distance=f.host.authoritySemanticDistance(parent,leaf);
      assertEquals(Authority.DistanceStatus.COMPATIBLE,distance.status());assertTrue(distance.distance()>0);
      assertEquals(1,new SemanticMatcher(f.host).assertedDistance(leaf,parent));
      assertEquals(0,new SemanticMatcher(f.host).assertedDistance(leaf,leaf));
      assertEquals(-1,new SemanticMatcher(f.host).assertedDistance(parent,leaf));
      verify(f.provider,atLeastOnce()).semanticDistance(f.bridge,f.code("Forest"),f.code("BroadLeavedForest"));
    }
  }
  @Test void deciduousForestUsesPositiveAuthorityDistanceNotEqualityAndRetainsMultipleParents()throws Exception {
    try(var f=new Fixture(true)) {
      var leaf=f.term("DeciduousForest");var forest=f.term("Forest");var vegetation=f.term("Vegetation");
      assertTrue(f.host.is(leaf,forest));assertTrue(f.host.is(leaf,vegetation));assertTrue(f.host.is(leaf,f.term("Seasonal")));
      assertFalse(f.host.is(forest,leaf));assertFalse(f.host.is(leaf,f.term("Water")));
      assertEquals(1,f.host.authoritySemanticDistance(forest,leaf).distance());
      assertTrue(new SemanticMatcher(f.host).assertedDistance(leaf,forest)>0);
      assertEquals(-1,new SemanticMatcher(f.host).assertedDistance(forest,leaf));
      assertEquals(0,new SemanticMatcher(f.host).assertedDistance(leaf,leaf));
    }
  }
  @Test void authorityUnknownAndIncompatibleCannotFallBackToExportedOwlAncestry()throws Exception {
    try(var f=new Fixture()) {
      var leaf=f.term("BroadLeavedForest");var parent=f.term("Forest");assertTrue(f.owl.is(leaf,parent));
      doReturn(Authority.SemanticDistance.unknown("No admitted relation")).when(f.provider)
          .semanticDistance(f.bridge,f.code("Forest"),f.code("BroadLeavedForest"));
      assertEquals(Authority.DistanceStatus.UNKNOWN,f.host.authoritySemanticDistance(parent,leaf).status());
      doReturn(Authority.Subsumption.UNKNOWN).when(f.provider).subsumption(f.bridge,f.code("Forest"),f.code("BroadLeavedForest"));
      assertFalse(f.host.is(leaf,parent),"Authority UNKNOWN must not be overridden by exported OWL parents");
      assertEquals(-1,new SemanticMatcher(f.host).assertedDistance(leaf,parent));
      doReturn(new Authority.SemanticDistance(Authority.DistanceStatus.INCOMPATIBLE,-1,List.of("Explicit fixture denial")))
          .when(f.provider).semanticDistance(f.bridge,f.code("Forest"),f.code("BroadLeavedForest"));
      assertEquals(Authority.DistanceStatus.INCOMPATIBLE,f.host.authoritySemanticDistance(parent,leaf).status());
      doReturn(Authority.Subsumption.INCOMPATIBLE).when(f.provider).subsumption(f.bridge,f.code("Forest"),f.code("BroadLeavedForest"));
      assertFalse(f.host.is(leaf,parent));
    }
  }
  @Test void collectiveViewsDelegateWithoutAdmittingMixedModesOrApproximateMatches() throws Exception {
    try (var f = new Fixture()) {
      var leaf=f.term("BroadLeavedForest"); var parent=f.term("Forest");
      assertTrue(f.host.is(leaf.collective(), parent.collective()));
      assertFalse(f.host.is(leaf.collective(), parent));
      assertFalse(f.host.is(leaf, parent.collective()));
      doReturn(Authority.Subsumption.UNKNOWN).when(f.provider)
          .subsumption(f.bridge,f.code("Forest"),f.code("BroadLeavedForest"));
      // Deliberately compatible policy admission cannot become a strict subtype proof.
      doReturn(new Authority.SemanticDistance(Authority.DistanceStatus.COMPATIBLE,25,List.of("Approximate admission")))
          .when(f.provider).semanticDistance(f.bridge,f.code("Forest"),f.code("BroadLeavedForest"));
      assertFalse(f.host.is(leaf,parent));
      assertFalse(f.host.is(leaf.collective(),parent.collective()));
      assertEquals(25,f.host.authoritySemanticDistance(parent.collective(),leaf.collective()).distance());
      assertEquals(Authority.DistanceStatus.UNKNOWN,f.host.authoritySemanticDistance(parent,leaf.collective()).status());
    }
  }

  @Test void separateBindingsRemainUnknownAndUnreadyRequestsDoNotReachProviderDistance() throws Exception {
    try (var f = new Fixture()) {
      var leaf=f.term("BroadLeavedForest");
      f.bindings.configure(new Authority.ConfigurationRequest("example","OTHER","land:LandCover",Map.of(
          "urn",LandAuthority.URN,"mode","ENTRY_ONLY","domain","LULC",
          "catalogs",List.of("corine_entry_hierarchy_example"),
          "codelists",Map.of("corine_entry_hierarchy_example","other.corine"))),f.provider);
      var other=f.resolver.resolve("other.corine","Forest");
      clearInvocations(f.provider);
      assertEquals(Authority.Subsumption.UNKNOWN,f.resolver.subsumption(other,leaf));
      assertEquals(Authority.DistanceStatus.UNKNOWN,f.host.authoritySemanticDistance(other,leaf).status());
      assertFalse(f.host.is(leaf,other));
      assertEquals(-10000,f.host.semanticDistance(other,leaf,null));
      verify(f.provider,never()).semanticDistance(anyString(),anyString(),anyString());
    }
  }

}
