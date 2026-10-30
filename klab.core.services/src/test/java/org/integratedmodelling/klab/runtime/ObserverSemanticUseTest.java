package org.integratedmodelling.klab.runtime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.integratedmodelling.common.knowledge.*;
import org.integratedmodelling.klab.api.digitaltwin.DigitalTwin;
import org.integratedmodelling.klab.api.knowledge.SemanticType;
import org.integratedmodelling.klab.api.knowledge.observation.impl.ObservationImpl;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.services.Reasoner;

class ObserverSemanticUseTest {
  ObservableImpl observable(String name) {
    var concept=new ConceptImpl();concept.setUrn(name);
    concept.setType(EnumSet.of(SemanticType.AGENT,SemanticType.OBSERVABLE,SemanticType.COUNTABLE));
    var result=new ObservableImpl();result.setSemantics(concept);result.setUrn(name);return result;
  }
  ContextScope scope() {
    var scope=mock(ContextScope.class);when(scope.getId()).thenReturn("test");
    var observer=new ObservationImpl();observer.setId(7);observer.setUrn("observer:7");
    observer.setObservable(observable("test:Human"));when(scope.getObserver()).thenReturn(observer);
    return scope;
  }
  @Test void fallbackNeverComparesSelectedObserverMeaning() {
    var scope=scope();var reasoner=mock(Reasoner.class);when(scope.getService(Reasoner.class)).thenReturn(reasoner);
    try(var capture=ObserverSemanticUse.capture(scope)) {
      assertEquals(1,ObserverResolution.preference(observable("test:Region"),observable("test:Region"),scope));
      assertEquals(List.of(),ObserverSemanticUse.requirements(scope).terms());
      assertTrue(ObserverSemanticUse.requirements(scope).valid());verifyNoInteractions(reasoner);
    }
  }
  @Test void rejectedSpecificComparisonIsStillARealDependencyBeforeFallback() {
    var scope=scope();var reasoner=mock(Reasoner.class);var caps=mock(Reasoner.Capabilities.class);
    when(scope.getService(Reasoner.class)).thenReturn(reasoner);when(reasoner.capabilities(scope)).thenReturn(caps);
    when(caps.getKnowledgeRevision()).thenReturn(1L);
    when(reasoner.semanticDistance(any(),any(),isNull(),eq(1L))).thenReturn(-1);
    var specific=observable("test:Region");specific.setObserverSemantics(observable("test:SpecialAgent").getSemantics());
    try(var capture=ObserverSemanticUse.capture(scope)) {
      assertEquals(-1,ObserverResolution.preference(specific,observable("test:Region"),scope));
      assertEquals(1,ObserverResolution.preference(observable("test:Region"),observable("test:Region"),scope));
      assertEquals(List.of("test:Human","test:SpecialAgent"),ObserverSemanticUse.requirements(scope).terms());
    }
  }
  @Test void nativeCommitGuardPinsActualObserverAndMissingTraceIsConservative() {
    var scope=scope();assertFalse(ObserverSemanticUse.requirements(scope).tracked());
    var tx=mock(DigitalTwin.Transaction.class);when(scope.getCurrentTransaction()).thenReturn(tx);
    ObserverSemanticUse.start(scope);
    ObserverSemanticUse.merge(null,scope,null);
    assertEquals(List.of("test:Human"),ObserverSemanticUse.requirements(scope).terms());
    var guard=ArgumentCaptor.forClass(Runnable.class);verify(tx).beforeCommit(guard.capture());
    ((ObservationImpl)scope.getObserver()).setUrn("changed:observer");
    assertFalse(ObserverSemanticUse.requirements(scope).valid());
    assertThrows(IllegalStateException.class,guard.getValue()::run);
  }
  @Test void transportedBranchProofMustMatchObserverScopeAndGeneration() {
    var scope=scope();var owner=new ObserverSemanticUse.Owner("reasoner",1,"content","session-one");
    final String proof;
    try(var capture=ObserverSemanticUse.capture(scope,owner)){proof=capture.summary(scope,owner);}
    var tx=mock(DigitalTwin.Transaction.class);when(scope.getCurrentTransaction()).thenReturn(tx);
    ObserverSemanticUse.start(scope);ObserverSemanticUse.merge(proof,scope,owner);
    assertEquals(List.of(),ObserverSemanticUse.requirements(scope).terms());
    ObserverSemanticUse.merge(proof,scope,new ObserverSemanticUse.Owner("reasoner",1,"content","session-two"));
    assertFalse(ObserverSemanticUse.requirements(scope).valid());
  }
  @Test void unpersistedObserverCannotAcquireFallbackEvidence() {
    var scope=scope();((ObservationImpl)scope.getObserver()).setId(-1);
    try(var capture=ObserverSemanticUse.capture(scope)){assertFalse(ObserverSemanticUse.requirements(scope).valid());}
  }
  @Test void nativeCommitGuardNeverLoadsObserverThroughScopeCallback() {
    var scope=scope();var tx=mock(DigitalTwin.Transaction.class);
    when(scope.getCurrentTransaction()).thenReturn(tx);ObserverSemanticUse.start(scope);
    var guard=ArgumentCaptor.forClass(Runnable.class);verify(tx).beforeCommit(guard.capture());
    when(scope.getObserver()).thenThrow(new IllegalStateException("Remote callback under graph monitor"));
    assertDoesNotThrow(guard.getValue()::run);
  }
}
