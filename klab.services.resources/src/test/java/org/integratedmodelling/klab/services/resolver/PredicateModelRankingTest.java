package org.integratedmodelling.klab.services.resolver;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.ArrayList;
import java.util.List;
import org.integratedmodelling.klab.api.knowledge.*;
import org.integratedmodelling.klab.api.services.Reasoner;
import org.junit.jupiter.api.Test;

class PredicateModelRankingTest {
  @Test void exactThenNearestSubsumingModelBeforeOtherRankingCriteria() {
    var reasoner = mock(Reasoner.class);
    var request = mock(Observable.class);
    var scope = mock(org.integratedmodelling.klab.api.scope.ContextScope.class);
    when(scope.getService(Reasoner.class)).thenReturn(reasoner);
    var prioritizer = new PrioritizerImpl(scope, null,
        java.util.Map.of("im:semantic-concordance", 1), request, null);
    var exact = model(); var parent = model(); var ancestor = model(); var unrelated = model();
    when(reasoner.semanticDistance(exact.getObservables().getFirst(), request, null)).thenReturn(0);
    when(reasoner.semanticDistance(parent.getObservables().getFirst(), request, null)).thenReturn(50);
    when(reasoner.semanticDistance(ancestor.getObservables().getFirst(), request, null)).thenReturn(100);
    when(reasoner.semanticDistance(unrelated.getObservables().getFirst(), request, null)).thenReturn(-50);
    var models = new ArrayList<>(List.of(ancestor, unrelated, parent, exact));
    models.removeIf(model -> prioritizer.semanticDistance(model) == Integer.MAX_VALUE);
    models.sort(prioritizer);
    assertEquals(List.of(exact, parent, ancestor), models);
    models.remove(exact);
    models.removeIf(model -> prioritizer.semanticDistance(model) == Integer.MAX_VALUE);
    models.sort(prioritizer);
    assertEquals(parent, models.getFirst());
  }
  @Test void resolutionCapabilityUsesCandidateToRequestDistance() {
    var reasoner = mock(org.integratedmodelling.klab.services.reasoner.ReasonerService.class);
    var requested = mock(Observable.class); var broader = mock(Observable.class);
    when(reasoner.resolves(requested, broader, null)).thenCallRealMethod();
    when(reasoner.semanticDistance(broader, requested, null)).thenReturn(50);
    when(reasoner.semanticDistance(requested, broader, null)).thenReturn(-50);
    assertTrue(reasoner.resolves(requested, broader, null));
    verify(reasoner).semanticDistance(broader, requested, null);
  }

  private Model model() {
    var model = mock(Model.class);
    when(model.getResolutionInfo()).thenReturn(mock(Model.ResolutionInfo.class));
    when(model.getObservables()).thenReturn(List.of(mock(Observable.class)));
    return model;
  }
}
