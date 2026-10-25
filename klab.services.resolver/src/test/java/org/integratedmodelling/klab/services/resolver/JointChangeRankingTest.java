package org.integratedmodelling.klab.services.resolver;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.List;
import java.util.Map;
import org.integratedmodelling.klab.api.knowledge.Concept;
import org.integratedmodelling.klab.api.knowledge.Model;
import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.api.knowledge.SemanticType;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.services.Reasoner;
import org.junit.jupiter.api.Test;

class JointChangeRankingTest {
  static Observable observable(String urn, SemanticType type) {
    var observable = mock(Observable.class); when(observable.getUrn()).thenReturn(urn);
    when(observable.is(type)).thenReturn(true); return observable;
  }

  @Test void onlyDistinctAffectedQualitiesOnTheSpecificBearerEarnJointPreference() {
    var reasoner = mock(Reasoner.class);
    when(reasoner.semanticDistance(any(), any(), any(), anyLong())).thenReturn(-1);
    var bearer = mock(Concept.class); var generic = mock(Concept.class);
    var process = observable("process", SemanticType.PROCESS);
    var first = observable("quality1", SemanticType.QUALITY);
    var second = observable("quality2", SemanticType.QUALITY);
    var unrelated = observable("input", SemanticType.QUALITY);
    var model = mock(Model.class);
    when(model.getObservables()).thenReturn(List.of(process, first, second));
    when(model.getDependencies()).thenReturn(List.of(first, unrelated));
    when(reasoner.inherent(process)).thenReturn(bearer);
    when(reasoner.semanticDistance(bearer, bearer, null, 7)).thenReturn(0);
    when(reasoner.semanticDistance(first, first, bearer, 7)).thenReturn(0);
    when(reasoner.semanticDistance(second, second, bearer, 7)).thenReturn(0);
    when(reasoner.semanticDistance(unrelated, unrelated, bearer, 7)).thenReturn(0);
    when(reasoner.affectedBy(first, process)).thenReturn(true);
    when(reasoner.createdBy(second, process)).thenReturn(true);
    assertEquals(2, PrioritizerImpl.jointChangeCoverage(model, List.of(first, second, unrelated), bearer, reasoner, 7));
    assertEquals(0, PrioritizerImpl.jointChangeCoverage(model, List.of(first, first), bearer, reasoner, 7));
    when(reasoner.inherent(process)).thenReturn(generic);
    when(reasoner.semanticDistance(generic, bearer, null, 7)).thenReturn(1);
    assertEquals(0, PrioritizerImpl.jointChangeCoverage(model, List.of(first, second), bearer, reasoner, 7));
  }

  @Test void jointPreferenceIsAfterObserverCompatibilityAndBeforeOrdinaryModelRanking() {
    var scope = mock(ContextScope.class);
    var request = observable("change", SemanticType.CHANGE);
    var ranking = spy(new PrioritizerImpl(scope, null, Map.of(), request, mock(Concept.class)));
    var single = mock(Model.class); when(single.getUrn()).thenReturn("a.single");
    var joint = mock(Model.class); when(joint.getUrn()).thenReturn("z.joint");
    doReturn(0).when(ranking).observerPreference(any());
    doReturn(0).when(ranking).jointChangePreference(single);
    doReturn(2).when(ranking).jointChangePreference(joint);
    assertTrue(ranking.compare(joint, single) < 0);
    doReturn(1).when(ranking).observerPreference(joint);
    assertTrue(ranking.compare(joint, single) > 0);
  }
}
