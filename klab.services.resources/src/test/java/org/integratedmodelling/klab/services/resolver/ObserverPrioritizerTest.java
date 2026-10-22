package org.integratedmodelling.klab.services.resolver;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import java.util.*;
import org.integratedmodelling.common.knowledge.ConceptImpl;
import org.integratedmodelling.common.knowledge.ObservableImpl;
import org.integratedmodelling.klab.api.knowledge.*;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.services.*;
import org.junit.jupiter.api.Test;

class ObserverPrioritizerTest {
  @org.junit.jupiter.api.BeforeAll static void configure() { org.integratedmodelling.klab.configuration.ServiceConfiguration.injectInstantiators(); }
  private ConceptImpl agent(String urn,boolean abs) {
    var c=new ConceptImpl();c.setUrn(urn);c.setType(EnumSet.of(SemanticType.AGENT,SemanticType.OBSERVABLE,SemanticType.COUNTABLE));c.setAbstract(abs);return c;
  }
  private ObservableImpl observable(String urn,Concept observer) {
    var c=new ConceptImpl();c.setUrn("test:Importance");c.setType(EnumSet.of(SemanticType.QUALITY));
    var o=new ObservableImpl();o.setSemantics(c);o.setUrn(urn);o.setObserverSemantics(observer);return o;
  }
  private Model model(String urn,String namespace,org.integratedmodelling.klab.api.knowledge.Observable output) {
    var m=mock(Model.class);when(m.getUrn()).thenReturn(urn);when(m.getNamespace()).thenReturn(namespace);when(m.getObservables()).thenReturn(List.of(output));return m;
  }
  private PrioritizerImpl rank(ContextScope scope,Reasoner reasoner,org.integratedmodelling.klab.api.knowledge.Observable query,Concept selected,Map<String,Integer> criteria) {
    when(scope.getService(Reasoner.class)).thenReturn(reasoner);ModelAdmissionFixtures.prepare(scope,reasoner);
    if(selected!=null) {var observer=mock(Observation.class);when(observer.getId()).thenReturn(12L);var meaning=new ObservableImpl();meaning.setSemantics(selected);when(observer.getObservable()).thenReturn(meaning);when(scope.getObserver()).thenReturn(observer);}
    return new PrioritizerImpl(scope,null,criteria,query,null);
  }
  @Test void compatibleSpecificObserverPrecedesAbsoluteRegardlessOfLexicalOrNumericDistance() {
    var scope=mock(ContextScope.class);var reasoner=mock(Reasoner.class);var selected=agent("test:Resident",false);var category=agent("test:Observer",true);
    var query=observable("test:Importance",null);var specificOutput=observable("test:Importance observed as test:Observer",category);var absoluteOutput=observable("test:Importance",null);
    var rank=rank(scope,reasoner,query,selected,Map.of("im:lexical-scope",1,"im:semantic-concordance",2));
    var specific=model("specific","remote",specificOutput);var absolute=model("absolute",null,absoluteOutput);
    ModelAdmissionFixtures.saved(scope.getService(ResourcesService.class),specific,scope);
    when(reasoner.semanticDistance(specificOutput,query,null,7)).thenReturn(500);when(reasoner.semanticDistance(category,selected,null,7)).thenReturn(1);
    assertEquals(500,rank.semanticDistance(specific));assertEquals(0,rank.semanticDistance(absolute));
    assertTrue(rank.compare(specific,absolute)<0);assertTrue(rank.compare(absolute,specific)>0);
    assertEquals(0,rank.observerPreference(specific));assertEquals(1,rank.observerPreference(absolute));
    assertNull(query.getObserverSemantics());verify(reasoner).semanticDistance(category,selected,null,7);
  }
  @Test void incompatibleSpecificObserverIsExcludedAndAbsoluteRemainsEligible() {
    var scope=mock(ContextScope.class);var reasoner=mock(Reasoner.class);var selected=agent("test:Observer",false);var narrower=agent("test:Resident",false);
    var query=observable("test:Importance",null);var specificOutput=observable("test:Importance observed as test:Resident",narrower);
    var rank=rank(scope,reasoner,query,selected,Map.of());var specific=model("specific",null,specificOutput);var absolute=model("absolute",null,observable("test:Importance",null));
    when(reasoner.semanticDistance(narrower,selected,null,7)).thenReturn(-1);
    assertEquals(Integer.MAX_VALUE,rank.semanticDistance(specific));assertEquals(0,rank.semanticDistance(absolute));assertTrue(rank.compare(absolute,specific)<0);
  }
  @Test void explicitQueryDoesNotAcquireAnExtraConstraintFromDifferentSelectedAgent() {
    var scope=mock(ContextScope.class);var reasoner=mock(Reasoner.class);var requested=agent("test:Resident",true);var selected=agent("test:Assessor",false);
    var query=observable("test:Importance observed as test:Resident",requested);var output=observable(query.getUrn(),requested);var rank=rank(scope,reasoner,query,selected,Map.of());
    assertEquals(0,rank.semanticDistance(model("specific",null,output)));
    verify(reasoner,never()).semanticDistance(requested,selected,null,7);
  }
  @Test void nullEffectiveObserverLeavesExistingOrderingAndTraitMeaningsAreDelegated() {
    var scope=mock(ContextScope.class);var reasoner=mock(Reasoner.class);var query=observable("test:Importance",null);var category=agent("test:Concerned test:Observer",true);
    var rank=rank(scope,reasoner,query,null,Map.of());var specific=model("a",null,observable("test:Importance observed as (test:Concerned test:Observer)",category));var absolute=model("b",null,observable("test:Importance",null));
    assertEquals(0,rank.observerPreference(specific));assertTrue(rank.compare(specific,absolute)<0);
    var selected=agent("test:Concerned test:Resident",false);var otherScope=mock(ContextScope.class);var otherReasoner=mock(Reasoner.class);var scoped=rank(otherScope,otherReasoner,query,selected,Map.of());
    when(otherReasoner.semanticDistance(category,selected,null,7)).thenReturn(2);
    assertEquals(0,scoped.semanticDistance(specific));verify(otherReasoner).semanticDistance(category,selected,null,7);
  }
}
