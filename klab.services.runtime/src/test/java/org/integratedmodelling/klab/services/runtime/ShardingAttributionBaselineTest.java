package org.integratedmodelling.klab.services.runtime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.integratedmodelling.common.runtime.ActuatorImpl;
import org.integratedmodelling.klab.api.configuration.Setting;
import org.integratedmodelling.klab.api.data.Data;
import org.integratedmodelling.klab.api.data.Storage;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.api.knowledge.SemanticType;
import org.integratedmodelling.klab.api.knowledge.observation.impl.ObservationImpl;
import org.integratedmodelling.klab.configuration.ServiceConfiguration;
import org.integratedmodelling.klab.services.scopes.ServiceContextScope;
import org.junit.jupiter.api.Test;

class ShardingAttributionBaselineTest {
  private ObservationImpl quality(long id) {
    var observable = mock(Observable.class);
    when(observable.is(SemanticType.QUALITY)).thenReturn(true);
    var observation = new ObservationImpl();
    observation.setId(id);
    observation.setObservable(observable);
    observation.setGeometry(Geometry.create("1"));
    var cd = new ObservationImpl.ContextualizationDataImpl();
    cd.setNativeShardingStrategy(Data.ShardingStrategy.trivial(Storage.Type.INTEGER));
    observation.setContextualizationData(cd);
    return observation;
  }

  private Data.ShardingStrategy harmonize(CompiledDataflow compiler, ActuatorImpl actuator) throws Exception {
    var method = CompiledDataflow.class.getDeclaredMethod("harmonizeShardingInternal",
        org.integratedmodelling.klab.api.services.runtime.Actuator.class);
    method.setAccessible(true);
    return (Data.ShardingStrategy) method.invoke(compiler, actuator);
  }

  @Test
  void positiveIdentityPreservesRecordedStrategyBeforeConsultingRuntimeDefaults() throws Exception {
    var observation = quality(42);
    var scope = mock(ServiceContextScope.class);
    var runtime = mock(RuntimeService.class);
    var compiler = new CompiledDataflow(runtime, observation, scope);
    var actuator = new ActuatorImpl(); actuator.setObservation(observation);
    actuator.setShardingStrategy(Data.ShardingStrategy.trivial(Storage.Type.DOUBLE));
    assertSame(observation.getContextualizationData().getNativeShardingStrategy(), harmonize(compiler, actuator));
    verify(scope, never()).getService(org.integratedmodelling.klab.api.services.RuntimeService.class);
  }

  @Test
  void runtimeConcreteDefaultsOverrideModelAndScalarGeometryClearsSizeHints() throws Exception {
    ServiceConfiguration.injectInstantiators();
    var observation = quality(-1);
    var scope = mock(ServiceContextScope.class);
    var runtime = mock(RuntimeService.class, RETURNS_DEEP_STUBS);
    when(runtime.settings().get(Setting.USE_SHORT_FLOAT_REPRESENTATION, Boolean.class)).thenReturn(false);
    when(runtime.settings().get(Setting.PARALLELIZE_OBSERVATIONS, Boolean.class)).thenReturn(true);
    when(scope.getService(RuntimeService.class)).thenReturn(runtime);
    when(scope.getService(org.integratedmodelling.klab.api.services.RuntimeService.class)).thenReturn(runtime);
    when(runtime.getDefaultShardingStrategy(observation, scope)).thenReturn(
        new Data.ShardingStrategy(Data.FillCurve.D1_LINEAR, 8, 0, 0, Storage.Type.DOUBLE));
    var actuator = new ActuatorImpl(); actuator.setObservation(observation);
    actuator.setShardingStrategy(new Data.ShardingStrategy(Data.FillCurve.D2_YX, 4, 32, 128, Storage.Type.FLOAT));
    var merged = harmonize(new CompiledDataflow(runtime, observation, scope), actuator);
    assertEquals(Data.FillCurve.D1_LINEAR, merged.getCurve());
    assertEquals(Storage.Type.DOUBLE, merged.getDataType());
    assertEquals(1, merged.getSuggestedSplits());
    assertEquals(0, merged.getMinSplitSize());
    assertEquals(0, merged.getMaxBufferSize());
    assertSame(merged, observation.getContextualizationData().getNativeShardingStrategy());
  }

  @Test
  void disablingParallelismOverridesDistributedModelAndChildSplitsAndSizeHints() throws Exception {
    ServiceConfiguration.injectInstantiators();
    var observation = quality(-1);
    observation.setGeometry(Geometry.create("S2(3,1){proj=EPSG:4326,shape=EPSG:4326 POLYGON ((0 0&comma;0 1&comma;3 1&comma;3 0&comma;0 0))}"));
    var scope = mock(ServiceContextScope.class);
    var runtime = mock(RuntimeService.class, RETURNS_DEEP_STUBS);
    when(runtime.settings().get(Setting.USE_SHORT_FLOAT_REPRESENTATION, Boolean.class)).thenReturn(false);
    when(runtime.settings().get(Setting.PARALLELIZE_OBSERVATIONS, Boolean.class)).thenReturn(false);
    when(scope.getService(RuntimeService.class)).thenReturn(runtime);
    when(scope.getService(org.integratedmodelling.klab.api.services.RuntimeService.class)).thenReturn(runtime);
    when(runtime.getDefaultShardingStrategy(observation, scope)).thenReturn(
        new Data.ShardingStrategy(Data.FillCurve.D2_XY, 8, 0, 0, Storage.Type.DOUBLE));
    var actuator = new ActuatorImpl(); actuator.setObservation(observation);
    actuator.setShardingStrategy(new Data.ShardingStrategy(Data.FillCurve.D2_YX, 4, 1, 2, Storage.Type.DOUBLE));
    var child = new ActuatorImpl(); var existing = quality(42);
    ((ObservationImpl.ContextualizationDataImpl) existing.getContextualizationData()).setNativeShardingStrategy(
        new Data.ShardingStrategy(Data.FillCurve.D2_YX, 12, 1, 2, Storage.Type.DOUBLE));
    child.setObservation(existing); actuator.getChildren().add(child);
    var merged = harmonize(new CompiledDataflow(runtime, observation, scope), actuator);
    assertEquals(1, merged.getSuggestedSplits());
    assertEquals(0, merged.getMinSplitSize());
    assertEquals(0, merged.getMaxBufferSize());
    assertEquals(12, existing.getContextualizationData().getNativeShardingStrategy().getSuggestedSplits(),
        "Existing bytes retain their native contract; the override applies to new output attribution");
  }
}
