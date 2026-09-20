package org.integratedmodelling.klab.api.data;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

/** Stage-0 characterization: these are current merge rules, not the proposed mediation policy. */
class ShardingStrategyBaselineTest {
  @Test
  void laterConcreteValuesOverrideWhileNeutralValuesDoNotEraseEarlierOnes() {
    var declared = new Data.ShardingStrategy(Data.FillCurve.D2_YX, 3, 10, 100, Storage.Type.INTEGER);
    var runtime = new Data.ShardingStrategy(Data.FillCurve.D2_XY, 8, 0, 0, Storage.Type.DOUBLE);
    var merged = declared.mergeUndefined(Data.ShardingStrategy.neutral(), runtime);
    assertEquals(Data.FillCurve.D2_XY, merged.getCurve());
    assertEquals(8, merged.getSuggestedSplits());
    assertEquals(Storage.Type.DOUBLE, merged.getDataType());
    assertEquals(10, merged.getMinSplitSize());
    assertEquals(100, merged.getMaxBufferSize());
    assertEquals(3, declared.getSuggestedSplits(), "Merging must not mutate the declaration");
    assertThrows(IllegalArgumentException.class,
        () -> declared.mergeUndefined(Data.ShardingStrategy.trivial(Storage.Type.KEYED)));
  }

  @Test
  void strategyEqualityIsCurrentlyAnOverloadNotValueEqualityForCacheKeys() {
    var first = Data.ShardingStrategy.trivial(Storage.Type.DOUBLE);
    var second = Data.ShardingStrategy.trivial(Storage.Type.DOUBLE);
    assertTrue(first.equals(second));
    assertFalse(((Object) first).equals(second), "Stage 1 must not use these beans as value keys");
  }

  @Test
  void rectangularCurvesPreserveEveryCellAcrossTraversalChanges() {
    var curves = new Data.FillCurve[] {
        Data.FillCurve.D2_XY, Data.FillCurve.D2_YX, Data.FillCurve.D2_XInvY};
    for (int width = 1; width <= 7; width++) {
      for (int height = 1; height <= 5; height++) {
        long[] shape = {width, height};
        for (var from : curves) for (var to : curves) {
          var visited = new boolean[width * height];
          for (int step = 0; step < visited.length; step++) {
            long mapped = from.map(step, shape, to);
            assertFalse(visited[(int) mapped]);
            visited[(int) mapped] = true;
            assertEquals(from.offset(step, shape), to.offset(mapped, shape));
            assertEquals(step, to.map((int) mapped, shape, from));
          }
        }
      }
    }
  }

  @Test
  void threeDimensionalZyxMappingCurrentlyFallsBackToXyzOnNonCubicGrid() {
    long[] shape = {2, 3, 5};
    for (int step = 0; step < 30; step++) {
      long mapped = Data.FillCurve.D3_XYZ.map(step, shape, Data.FillCurve.D3_ZYX);
      assertEquals(step, mapped, "Known stage-2 gap: ZYX is currently a row-major alias");
      assertEquals(step, Data.FillCurve.D3_ZYX.offset(mapped, shape));
      assertEquals(step, Data.FillCurve.D3_ZYX.map((int) mapped, shape, Data.FillCurve.D3_XYZ));
    }
  }
}
