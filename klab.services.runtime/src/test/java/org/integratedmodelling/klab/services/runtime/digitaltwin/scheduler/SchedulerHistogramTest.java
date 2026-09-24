package org.integratedmodelling.klab.services.runtime.digitaltwin.scheduler;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Map;
import org.integratedmodelling.common.knowledge.ConceptImpl;
import org.integratedmodelling.common.knowledge.ObservableImpl;
import org.integratedmodelling.klab.api.data.RuntimeAsset;
import org.integratedmodelling.klab.api.data.Storage;
import org.integratedmodelling.klab.api.data.impl.HistogramImpl;
import org.integratedmodelling.klab.api.digitaltwin.Scheduler;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.knowledge.SemanticType;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.knowledge.observation.impl.ObservationImpl;
import org.integratedmodelling.klab.api.lang.TriFunction;
import org.integratedmodelling.klab.api.provenance.Activity;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.configuration.ServiceConfiguration;
import org.integratedmodelling.klab.services.runtime.digitaltwin.DigitalTwinImpl;
import org.integratedmodelling.klab.services.scopes.ServiceContextScope;
import org.integratedmodelling.klab.utilities.Utils;
import org.junit.jupiter.api.Test;

class SchedulerHistogramTest {
  @Test
  void detachedDependencyPublishesHistogramOnTransactionObservationAndRollsBack() throws Exception {
    ServiceConfiguration.injectInstantiators();
    var concept = new ConceptImpl();
    concept.setUrn("test:Elevation");
    concept.setName("Elevation");
    concept.getType().add(SemanticType.QUALITY);
    var canonical = new ObservationImpl();
    canonical.setId(42);
    canonical.setObservable(ObservableImpl.promote(concept, null));
    canonical.setGeometry(Geometry.create("T0(1){ttype=PHYSICAL,tstart=1000,tend=10000}"));
    canonical.setSubstantialQuality(true);
    var detached = (ObservationImpl) Observation.forTransport(canonical);
    var scope = mock(ServiceContextScope.class, RETURNS_DEEP_STUBS);
    var twin = mock(DigitalTwinImpl.class);
    var transaction = twin.new TransactionImpl(Activity.of(Activity.Type.SUBMISSION), scope,
        RuntimeAsset.PROVENANCE_ASSET, canonical);
    when(scope.getCurrentTransaction()).thenReturn(transaction);
    var storage = mock(Storage.class);
    when(scope.getDigitalTwin().getStorageManager().getStorage(any())).thenReturn(storage);
    var histogram = new HistogramImpl();
    histogram.setEmpty(false);
    histogram.setMin(12);
    histogram.setMax(12);
    var bin = new HistogramImpl.BinImpl();
    bin.setMin(12); bin.setMax(12); bin.setCount(20);
    histogram.setBins(List.of(bin));
    when(storage.getHistograms()).thenReturn(Map.of(0L, histogram));
    var scheduler = mock(SchedulerImpl.class, CALLS_REAL_METHODS);
    var execute = SchedulerImpl.class.getDeclaredMethod("execute", TriFunction.class,
        Observation.class, Geometry.class, Scheduler.Event.class, ServiceContextScope.class);
    execute.setAccessible(true);
    TriFunction<Geometry, Scheduler.Event, ContextScope, Boolean> computation = (g, e, s) -> true;

    assertEquals(true, execute.invoke(scheduler, computation, detached, canonical.getGeometry(),
        Scheduler.Event.initialization(), scope));

    var published = Utils.Json.parseObject(
        Utils.Json.asString(Observation.forTransport(canonical)), ObservationImpl.class);
    assertFalse(published.getHistograms().get(0L).isEmpty());
    assertEquals(20, published.getHistograms().get(0L).getBins().getFirst().getCount());
    assertFalse(canonical.getEventTimestamps().isEmpty());
    verify(storage).flush();
    transaction.fail(new IllegalStateException("rollback"));
    assertTrue(canonical.getHistograms().isEmpty());
    assertTrue(canonical.getEventTimestamps().isEmpty());
  }
}
