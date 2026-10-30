package org.integratedmodelling.klab.services.runtime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import java.util.*;
import org.integratedmodelling.common.knowledge.ConceptImpl;
import org.integratedmodelling.common.knowledge.ObservableImpl;
import org.integratedmodelling.klab.api.knowledge.*;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.knowledge.observation.impl.ObservationImpl;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.services.Reasoner;
import org.junit.jupiter.api.Test;

class ObserverSubmissionIdentityTest {
  private Object key(Observation observation,long observerId)throws Exception {
    return key(observation, observerId, "test:Observer", 0);
  }
  private Object key(Observation observation,long observerId,String observerMeaning,long revision)throws Exception {
    var selected=mock(Observation.class);when(selected.getId()).thenReturn(observerId);
    var observerObservable=mock(org.integratedmodelling.klab.api.knowledge.Observable.class);
    when(observerObservable.getUrn()).thenReturn(observerMeaning);when(selected.getObservable()).thenReturn(observerObservable);
    var scope=mock(ContextScope.class);when(scope.getId()).thenReturn("twin");if(observerId>0)when(scope.getObserver()).thenReturn(selected);
    var twin=mock(org.integratedmodelling.klab.api.digitaltwin.DigitalTwin.class);
    var graph=mock(org.integratedmodelling.klab.api.data.KnowledgeGraph.class);
    when(twin.getKnowledgeGraph()).thenReturn(graph);when(graph.getSemanticRevision()).thenReturn(revision);when(scope.getDigitalTwin()).thenReturn(twin);
    var reasoner=mock(Reasoner.class);when(scope.getService(Reasoner.class)).thenReturn(reasoner);
    var base=mock(Concept.class);when(base.getUrn()).thenReturn("test:Subject");when(reasoner.baseSubstantialType(any(),eq(scope))).thenReturn(base);
    var method=RuntimeService.class.getDeclaredMethod("submissionIdentity",Observation.class,ContextScope.class);method.setAccessible(true);
    return method.invoke(mock(RuntimeService.class),observation,scope);
  }
  private ObservationImpl subject(String semantics) {
    var concept=new ConceptImpl();concept.setUrn("test:Tree");concept.setType(EnumSet.of(SemanticType.SUBJECT,SemanticType.COUNTABLE));
    var observable=new ObservableImpl();observable.setSemantics(concept);observable.setUrn(semantics);
    var observation=new ObservationImpl();observation.setObservable(observable);observation.setUrn("urn:tree:one");return observation;
  }
  @Test void coalescingKeySeparatesActualObserverFromSemanticObserverAndPreservesSameRequestReuse()throws Exception {
    var absolute=subject("test:Tree");assertEquals(key(absolute,11),key(absolute,11));
    assertNotEquals(key(absolute,11),key(absolute,12));assertNotEquals(key(absolute,0),key(absolute,11));
    assertNotEquals(key(subject("test:Tree observed as test:Assessor"),11),key(absolute,11));
    assertNotEquals(key(subject("test:Tree observed as test:Assessor"),11),key(subject("test:Tree observed as test:Resident"),11));
    assertEquals("test:Tree",absolute.getObservable().getUrn());
  }
  @Test void sameSelectedAgentCannotCoalesceAcrossMeaningOrSemanticRevisionChange()throws Exception {
    var requested=subject("test:Tree");
    assertNotEquals(key(requested,11,"test:Observer",1),key(requested,11,"test:Observer",2));
    assertNotEquals(key(requested,11,"test:Observer",2),key(requested,11,"test:SpecializedObserver",2));
    assertEquals(key(requested,11,"test:SpecializedObserver",2),key(requested,11,"test:SpecializedObserver",2));
  }
}
