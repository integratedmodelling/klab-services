package org.integratedmodelling.klab.services.reasoner;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.EnumSet;
import org.integratedmodelling.common.knowledge.ConceptImpl;
import org.integratedmodelling.common.knowledge.ObservableImpl;
import org.integratedmodelling.klab.api.data.mediation.Unit;
import org.integratedmodelling.klab.api.knowledge.SemanticType;
import org.integratedmodelling.klab.api.knowledge.Semantics;
import org.junit.jupiter.api.Test;

class ObservableMediatorMatchingTest {
  @Test void nativeModelUnitsCanResolveAUnitUnspecifiedRecursiveRequest() throws Exception {
    var reasoner = reasoner();
    var candidate = elevation();
    candidate.setUnit(mock(Unit.class));
    assertEquals(0, reasoner.semanticDistance(candidate, elevation(), null));
  }

  @Test void incompatibleExplicitRequestUnitsStillRejectTheModel() throws Exception {
    var reasoner = reasoner();
    var candidate = elevation();
    var request = elevation();
    candidate.setUnit(mock(Unit.class));
    request.setUnit(mock(Unit.class));
    assertTrue(reasoner.semanticDistance(candidate, request, null) < 0);
  }

  @Test void compatibleExplicitRequestUnitsRemainEligible() throws Exception {
    var reasoner = reasoner();
    var candidate = elevation();
    var request = elevation();
    var metres = mock(Unit.class);
    var feet = mock(Unit.class);
    when(metres.isCompatible(feet)).thenReturn(true);
    candidate.setUnit(metres);
    request.setUnit(feet);
    assertTrue(reasoner.semanticDistance(candidate, request, null) >= 0);
  }

  private static ObservableImpl elevation() {
    var concept = new ConceptImpl();
    concept.setUrn("geography:Elevation");
    concept.setType(EnumSet.of(SemanticType.QUALITY, SemanticType.LENGTH));
    var observable = new ObservableImpl();
    observable.setSemantics(concept);
    return observable;
  }

  private static ReasonerService reasoner() throws Exception {
    var reasoner = mock(ReasonerService.class);
    when(reasoner.resolveConcept("geography:Elevation")).thenReturn(elevation().asConcept());
    when(reasoner.satisfiable(any())).thenReturn(true);
    var matcher = mock(SemanticMatcher.class);
    var field = ReasonerService.class.getDeclaredField("semanticMatcher");
    field.setAccessible(true);
    field.set(reasoner, matcher);
    when(reasoner.semanticDistance(any(Semantics.class), any(Semantics.class), isNull()))
        .thenCallRealMethod();
    return reasoner;
  }
}
