package org.integratedmodelling.klab.services.resources.persistence;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.*;
import org.integratedmodelling.klab.api.knowledge.Concept;
import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.api.knowledge.Contextualization;
import org.integratedmodelling.klab.api.scope.Scope;
import org.integratedmodelling.klab.api.services.Reasoner;
import org.junit.jupiter.api.Test;

class PredicateModelDiscoveryTest {
  @Test void modelIndexIncludesExactAndSubsumingHeadsThenChecksFullSemantics() throws Exception {
    var box = mock(ModelKbox.class, CALLS_REAL_METHODS);
    var scope = mock(Scope.class); var reasoner = mock(Reasoner.class);
    when(scope.getService(Reasoner.class)).thenReturn(reasoner);
    var request = mock(Observable.class);
    when(request.getContextualization()).thenReturn(Contextualization.CHARACTERIZATION);
    var child = concept("P2"); var parent = concept("P1");
    var exact = concept("P2 of S"); var broad = concept("P1 of S"); var wrongBearer = concept("P1 of T");
    when(reasoner.coreObservable(request)).thenReturn(child);
    when(reasoner.resolving(child)).thenReturn(List.of(child,parent));
    when(reasoner.semanticDistance(exact,request,null)).thenReturn(0);
    when(reasoner.semanticDistance(broad,request,null)).thenReturn(50);
    when(reasoner.semanticDistance(wrongBearer,request,null)).thenReturn(-50);
    field(box,"scope",scope);
    field(box,"coreTypeHash",Map.of("P2",Set.of("P2 of S"),"P1",Set.of("P1 of S","P1 of T")));
    field(box,"conceptHash",Map.of("P2 of S",exact,"P1 of S",broad,"P1 of T",wrongBearer));
    field(box,"definitionHash",Map.of("P2 of S",1L,"P1 of S",2L,"P1 of T",3L));
    assertEquals(Set.of(1L,2L),box.getCompatibleTypeIds(request,null));
  }
  private Concept concept(String urn) { var c=mock(Concept.class); when(c.getUrn()).thenReturn(urn); return c; }
  private void field(Object target,String name,Object value) throws Exception {
    var field=ObservableKbox.class.getDeclaredField(name);field.setAccessible(true);field.set(target,value);
  }
}
