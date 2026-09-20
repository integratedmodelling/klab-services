package org.integratedmodelling.klab.components;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.lang.reflect.Method;
import org.integratedmodelling.common.lang.ServiceInfoImpl;
import org.integratedmodelling.klab.api.data.Data;
import org.integratedmodelling.klab.api.data.Storage;
import org.integratedmodelling.klab.api.knowledge.Artifact;
import org.integratedmodelling.klab.api.knowledge.KlabAsset;
import org.integratedmodelling.klab.api.services.resources.adapters.Exporter;
import org.integratedmodelling.klab.api.services.resources.adapters.ResourceAdapter;
import org.integratedmodelling.klab.api.services.runtime.extension.AdapterDescriptor;
import org.integratedmodelling.klab.api.services.runtime.extension.KlabFunction;
import org.junit.jupiter.api.Test;

class ComponentRegistryShardingTest {
  @KlabFunction(name = "grid", description = "test", type = Artifact.Type.NUMBER,
      split = 3, fillCurve = Data.FillCurve.D2_YX, minSizeForSplitting = 10, maxSize = 100)
  public static class GridFunction {
    @KlabFunction(name = "method", description = "test")
    public static void method() {}
  }

  @ResourceAdapter(name = "test", version = "1.0.0")
  public static class DefaultAdapter {}

  @ResourceAdapter(name = "limited", version = "1.0.0", splits = 3,
      fillCurve = Data.FillCurve.D2_XInvY, minSizeForSplitting = 16, maxSize = 512)
  public static class LimitedAdapter {}

  @Test
  void resourceAdapterLimitsUseTheSameValidatedStateCountContract() {
    var strategy = AdapterDescriptor.shardingStrategy(LimitedAdapter.class.getAnnotation(ResourceAdapter.class));
    assertEquals(3, strategy.getSuggestedSplits());
    assertEquals(16, strategy.getMinSplitSize());
    assertEquals(512, strategy.getMaxBufferSize());
    assertEquals(Data.FillCurve.D2_XInvY, strategy.getCurve());
    assertNull(strategy.getDataType());
  }

  @Exporter(schema = "grid", knowledgeClass = KlabAsset.KnowledgeClass.OBSERVATION,
      description = "test", mediaType = "application/octet-stream", fillCurve = Data.FillCurve.D2_YX)
  public static void export() {}

  private ServiceInfoImpl prototype(Method method) throws Exception {
    var factory = ComponentRegistry.class.getDeclaredMethod("createContextualizerPrototype",
        String.class, KlabFunction.class, Method.class, Class.class);
    factory.setAccessible(true);
    return (ServiceInfoImpl) factory.invoke(mock(ComponentRegistry.class, CALLS_REAL_METHODS),
        "test.", method == null ? GridFunction.class.getAnnotation(KlabFunction.class)
            : method.getAnnotation(KlabFunction.class), method, GridFunction.class);
  }

  @Test
  void functionDeclarationReachesPrototypeButMethodDefaultsDoNotInheritClassSharding() throws Exception {
    var strategy = prototype(null).getShardingStrategy();
    assertEquals(3, strategy.getSuggestedSplits());
    assertEquals(10, strategy.getMinSplitSize());
    assertEquals(100, strategy.getMaxBufferSize());
    assertEquals(Data.FillCurve.D2_YX, strategy.getCurve());
    assertEquals(Storage.Type.DOUBLE, strategy.getDataType());
    var method = prototype(GridFunction.class.getMethod("method")).getShardingStrategy();
    assertTrue(Data.ShardingStrategy.neutral().equals(method));
  }

  @Test
  void adapterHasDifferentSplitDefaultAndDoesNotAttributeAPrimitiveType() {
    var annotation = DefaultAdapter.class.getAnnotation(ResourceAdapter.class);
    assertEquals(1, annotation.splits());
    var descriptor = new AdapterDescriptor();
    descriptor.setFillCurve(annotation.fillCurve());
    descriptor.setSplits(annotation.splits());
    descriptor.setMinSplitSize(annotation.minSizeForSplitting());
    descriptor.setMaxSize(annotation.maxSize());
    var strategy = descriptor.shardingStrategy();
    assertEquals(1, strategy.getSuggestedSplits());
    assertEquals(Data.FillCurve.UNSPECIFIED, strategy.getCurve());
    assertNull(strategy.getDataType());
  }

  @Test
  void exporterPrototypeCarriesOnlyTraversalPreference() throws Exception {
    var factory = ComponentRegistry.class.getDeclaredMethod("createPrototype", String.class, Exporter.class);
    factory.setAccessible(true);
    var prototype = (ServiceInfoImpl) factory.invoke(mock(ComponentRegistry.class, CALLS_REAL_METHODS),
        "test.", getClass().getMethod("export").getAnnotation(Exporter.class));
    var strategy = prototype.getShardingStrategy();
    assertEquals(Data.FillCurve.D2_YX, strategy.getCurve());
    assertEquals(-1, strategy.getSuggestedSplits());
    assertNull(strategy.getDataType());
  }
}
