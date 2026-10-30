package org.integratedmodelling.klab.services.scopes;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.integratedmodelling.klab.api.data.Metadata;
import org.integratedmodelling.klab.api.knowledge.CollectiveSelectionView;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.knowledge.observation.impl.ObservationImpl;
import org.integratedmodelling.klab.api.services.RuntimeService;
import org.junit.jupiter.api.Test;

class CollectiveSelectionScopeLookupTest {
  @Test void discoveryWitnessDoesNotReplaceTheFilteredSelection() {
    var scope = mock(ServiceContextScope.class);
    var runtime = mock(RuntimeService.class);
    var view = new ObservationImpl(); view.setId(Observation.QUERY_ID); view.setChildrenCount(1);
    view.getMetadata().put(CollectiveSelectionView.DATA_KEY, "checked-by-runtime");
    view.getMetadata().put(Metadata.IM_QUERY_SOURCE_IDS, List.of(42L));
    var checked = new ObservationImpl(); checked.setId(Observation.QUERY_ID); checked.setChildrenCount(1);
    when(scope.getService(RuntimeService.class)).thenReturn(runtime);
    when(runtime.submit(view, scope)).thenReturn(CompletableFuture.completedFuture(checked));
    when(scope.getObservation(view)).thenCallRealMethod();
    assertSame(checked, scope.getObservation(view));
    verify(runtime).submit(view, scope);
    verify(scope, never()).getDigitalTwin();
  }

  @Test void unavailableSelectionCannotFallBackToTheUnfilteredProducer() {
    var scope = mock(ServiceContextScope.class); var runtime = mock(RuntimeService.class);
    var view = new ObservationImpl(); view.setId(Observation.QUERY_ID);
    view.getMetadata().put(CollectiveSelectionView.DATA_KEY, "checked-by-runtime");
    view.getMetadata().put(Metadata.IM_QUERY_SOURCE_IDS, List.of(42L));
    when(scope.getService(RuntimeService.class)).thenReturn(runtime);
    when(runtime.submit(view, scope)).thenReturn(CompletableFuture.completedFuture(Observation.empty()));
    when(scope.getObservation(view)).thenCallRealMethod();
    assertNull(scope.getObservation(view));
    verify(scope, never()).getDigitalTwin();
  }
}
