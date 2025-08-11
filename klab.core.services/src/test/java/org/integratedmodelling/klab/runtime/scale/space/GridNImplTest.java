package org.integratedmodelling.klab.runtime.scale.space;

import org.integratedmodelling.klab.api.collections.Pair;
import org.integratedmodelling.klab.api.knowledge.observation.scale.space.GridN;
import org.integratedmodelling.klab.api.knowledge.observation.scale.space.Projection;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class GridNImplTest {

    @Test
    void twoD_basicCountsSizeResolutionAndFlags() {
        double[] cell = new double[]{2.0, 3.0};
        double[][] bounds = new double[][]{{0.0, 6.0}, {0.0, 9.0}}; // spans 6 and 9
        GridN g = new GridNImpl(2, cell, bounds, null, true, null);

        // cells = ceil(span/size) => [3,3]; size=9
        assertArrayEquals(new long[]{3L, 3L}, g.getCells());
        assertEquals(9L, g.size());

        // resolution = sqrt(x*y) in 2D
        double expectedRes = Math.sqrt(2.0 * 3.0);
        assertEquals(expectedRes, g.resolution(), 1e-12);

        // hypercubic flag passthrough
        assertTrue(g.isHypercubicCells());

        // cell sizes unchanged and addressable per dimension
        assertArrayEquals(cell, g.getCellSizes(), 0.0);
        assertEquals(2.0, g.getCellSize(0), 0.0);
        assertEquals(3.0, g.getCellSize(1), 0.0);
    }

    @Test
    void noBounds_meansZeroSizeAndZeroCells() {
        double[] cell = new double[]{1.0, 1.0};
        GridN g = new GridNImpl(2, cell, null, null, false, null);
        assertEquals(0L, g.size());
        assertArrayEquals(new long[]{0L, 0L}, g.getCells());
    }

    @Test
    void locate_changesBoundsAndRecomputesCells_originalUnchanged() {
        double[] cell = new double[]{1.0, 2.0};
        double[][] b1 = new double[][]{{0, 4}, {0, 4}}; // spans 4 and 4 -> cells [4, 2]
        GridN g1 = new GridNImpl(2, cell, b1, null, false, null);

        assertArrayEquals(new long[]{4L, 2L}, g1.getCells());
        assertEquals(8L, g1.size());

        double[][] b2 = new double[][]{{0, 2}, {0, 3}}; // spans 2 and 3 -> cells [2, 2]
        GridN g2 = g1.locate(b2);

        // New grid has new cells
        assertArrayEquals(new long[]{2L, 2L}, g2.getCells());
        assertEquals(4L, g2.size());

        // Original unchanged
        assertArrayEquals(new long[]{4L, 2L}, g1.getCells());
        assertEquals(8L, g1.size());

        // Bounds are copied and not the same reference
        assertNotSame(b2, g2.getBounds());
        assertArrayEquals(b2[0], g2.getBounds()[0], 0.0);
        assertArrayEquals(b2[1], g2.getBounds()[1], 0.0);
    }

    @Test
    void align_intersectsBoundsWhenBothPresent() {
        double[] cell = new double[]{1.0, 1.0};
        GridN a = new GridNImpl(2, cell, new double[][]{{0, 10}, {0, 10}}, null, false, null);
        GridN b = new GridNImpl(2, cell, new double[][]{{5, 12}, {3, 8}}, null, false, null);

        GridN aligned = a.align(b);
        double[][] expected = new double[][]{{5, 10}, {3, 8}};
        assertArrayEquals(expected[0], aligned.getBounds()[0], 0.0);
        assertArrayEquals(expected[1], aligned.getBounds()[1], 0.0);

        // Cells should match ceil on new spans
        assertArrayEquals(new long[]{5L, 5L}, aligned.getCells());
        assertEquals(25L, aligned.size());

        // Non-overlapping on any dimension should return original (per minimal placeholder behavior)
        GridN c = new GridNImpl(2, cell, new double[][]{{-5, -1}, {0, 2}}, null, false, null);
        GridN aligned2 = a.align(c);
        // Expect unchanged 'a' behavior: bounds remain original
        assertArrayEquals(new double[]{0, 10}, aligned2.getBounds()[0], 0.0);
        assertArrayEquals(new double[]{0, 10}, aligned2.getBounds()[1], 0.0);
    }

    @Test
    void threeD_geometricMeanResolution_andSizeProduct() {
        double[] cell = new double[]{2.0, 8.0, 18.0};
        double[][] bounds = new double[][]{{0, 4}, {0, 8}, {0, 36}}; // spans 4,8,36 -> cells 2,1,2
        GridN g = new GridNImpl(3, cell, bounds, null, false, null);

        long[] expectedCells = new long[]{2L, 1L, 2L};
        assertArrayEquals(expectedCells, g.getCells());
        assertEquals(4L, g.size());

        double expectedRes = Math.cbrt(2.0 * 8.0 * 18.0);
        assertEquals(expectedRes, g.resolution(), 1e-12);
    }

    @Test
    void anchors_validationAndImmutability() {
        double[] cell = new double[]{1.0, 1.0, 1.0};
        double[][] bounds = new double[][]{{0, 1}, {0, 1}, {0, 1}};
        List<double[]> anchors = new ArrayList<>();
        anchors.add(new double[]{0.0, 0.0, 0.0});
        anchors.add(new double[]{1.0, 1.0, 1.0});

        GridN g = new GridNImpl(3, cell, bounds, null, true, anchors);
        List<double[]> out = g.getAnchorPoints();
        assertEquals(2, out.size());
        assertArrayEquals(new double[]{0.0, 0.0, 0.0}, out.get(0), 0.0);

        // The returned list must be unmodifiable (constructor wraps as unmodifiableList)
        assertThrows(UnsupportedOperationException.class, () -> out.add(new double[]{0, 0, 0}));

        // Constructor should reject anchors of wrong dimensionality
        List<double[]> bad = List.of(new double[]{0.0, 0.0});
        assertThrows(IllegalArgumentException.class,
                () -> new GridNImpl(3, cell, bounds, null, false, bad));
    }

    @Test
    void dimensionsMismatchOrNoBounds_alignReturnsThis() {
        double[] cell = new double[]{1.0, 1.0};
        GridN a = new GridNImpl(2, cell, new double[][]{{0, 1}, {0, 1}}, null, false, null);
        GridN bNoBounds = new GridNImpl(2, cell, null, null, false, null);
        GridN c3D = new GridNImpl(3, new double[]{1, 1, 1}, new double[][]{{0,1},{0,1},{0,1}}, null, false, null);

        GridN aligned1 = a.align(bNoBounds);
        assertSame(a, aligned1, "align with no-bounds other should return this instance per implementation");

        GridN aligned2 = a.align(c3D);
        assertSame(a, aligned2, "align with different dimensionality should return this instance");
    }

    @Test
    @Disabled
    void projection_canBeNullOrProvided() {
        // Null projection acceptable for non-spatial contexts
        GridN g1 = new GridNImpl(1, new double[]{1.0}, new double[][]{{0, 10}}, null, false, null);
        assertNull(g1.getProjection());

        // Provided projection is returned
        Projection prj = Projection.of(Projection.DEFAULT_PROJECTION_CODE);
        GridN g2 = new GridNImpl(1, new double[]{2.0}, new double[][]{{0, 8}}, prj, false, null);
        assertEquals(prj.getCode(), g2.getProjection().getCode());
    }

    @Test
    void split_even2D_producesAdjacentTilesCoveringAll() {
        double[] cell = new double[]{1.0, 1.0};
        double[][] bounds = new double[][]{{0, 8}, {0, 6}};
        GridN g = new GridNImpl(2, cell, bounds, null, false, null);

        List<GridN> tiles = g.split(4);
        assertEquals(4, tiles.size(), "Expected 4 tiles for 8x6 grid with suggested 4");

        // Sum of tile cells equals original
        long totalCells = g.size();
        long sum = 0;
        for (GridN t : tiles) {
            assertArrayEquals(cell, t.getCellSizes(), 0.0);
            sum += t.size();
            // Each tile bounds must be within original bounds
            double[][] tb = t.getBounds();
            assertTrue(tb[0][0] >= bounds[0][0] - 1e-12 && tb[0][1] <= bounds[0][1] + 1e-12);
            assertTrue(tb[1][0] >= bounds[1][0] - 1e-12 && tb[1][1] <= bounds[1][1] + 1e-12);
        }
        assertEquals(totalCells, sum);

        // Union of mins/maxs equals original
        double minX = Double.POSITIVE_INFINITY, minY = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY, maxY = Double.NEGATIVE_INFINITY;
        for (GridN t : tiles) {
            double[][] tb = t.getBounds();
            minX = Math.min(minX, tb[0][0]);
            minY = Math.min(minY, tb[1][0]);
            maxX = Math.max(maxX, tb[0][1]);
            maxY = Math.max(maxY, tb[1][1]);
        }
        assertEquals(bounds[0][0], minX, 1e-9);
        assertEquals(bounds[1][0], minY, 1e-9);
        assertEquals(bounds[0][1], maxX, 1e-9);
        assertEquals(bounds[1][1], maxY, 1e-9);
    }

    @Test
    void split_edgeCases_andNonEven() {
        // No bounds -> singleton
        GridN noBounds = new GridNImpl(2, new double[]{1.0, 1.0}, null, null, false, null);
        List<GridN> s1 = noBounds.split(10);
        assertEquals(1, s1.size());
        assertSame(noBounds, s1.get(0));

        // suggested <= 1 -> singleton
        double[][] b = new double[][]{{0, 3}, {0, 2}};
        GridN g = new GridNImpl(2, new double[]{1.0, 1.0}, b, null, false, null);
        List<GridN> s2 = g.split(1);
        assertEquals(1, s2.size());
        assertSame(g, s2.get(0));

        // suggested >> total cells caps to total cells
        List<GridN> s3 = g.split(1000);
        assertEquals(3 * 2, s3.size());
        for (GridN t : s3) {
            assertEquals(1L, t.size(), "When capped to total cells, each tile should be a single cell");
        }

        // Non-even case: 7x5 with suggested 5 -> expect close to 5 (4 or 5 or 6), and full coverage
        double[][] b2 = new double[][]{{0, 7}, {0, 5}};
        GridN g2 = new GridNImpl(2, new double[]{1.0, 1.0}, b2, null, false, null);
        List<GridN> s4 = g2.split(5);
        assertTrue(Math.abs(s4.size() - 5) <= 1, "Tile count should be as close as possible to 5");
        long sumCells = 0;
        for (GridN t : s4) sumCells += t.size();
        assertEquals(g2.size(), sumCells, "Sum of tile cells must equal original");
    }

    @Test
    void merge_afterSplit_preservesCoverage_and_mappingWorks() {
        double[] cell = new double[]{1.0, 1.0};
        double[][] bounds = new double[][]{{0, 8}, {0, 6}};
        GridN g = new GridNImpl(2, cell, bounds, null, false, null);
        List<GridN> tiles = g.split(4);

        GridN merged = g.merge(tiles);
        assertArrayEquals(bounds[0], merged.getBounds()[0], 0.0);
        assertArrayEquals(bounds[1], merged.getBounds()[1], 0.0);
        assertEquals(g.size(), merged.size());
        assertArrayEquals(g.getCells(), merged.getCells());

        // Sample a few global offsets and check mapping
        long[][] samples = new long[][]{
                {0, 0}, {7, 0}, {0, 5}, {7, 5}, {3, 2}
        };
        for (long[] off : samples) {
            Pair<GridN, long[]> map = merged.mapToSubgrid(off);
            GridN sub = map.getFirst();
            long[] loc = map.getSecond();
            assertTrue(tiles.contains(sub), "Returned subgrid must be one of the tiles");
            // Validate local offsets coherence: global = start + local
            double[][] sb = sub.getBounds();
            for (int d = 0; d < 2; d++) {
                long start = Math.round((sb[d][0] - bounds[d][0]) / cell[d]);
                assertEquals(off[d] - start, loc[d]);
                assertTrue(loc[d] >= 0 && loc[d] < sub.getCells(d));
            }
        }
    }

    @Test
    void mapToSubgrid_identityOnNonMerged() {
        GridN g = new GridNImpl(2, new double[]{1.0, 1.0}, new double[][]{{0, 4}, {0, 3}}, null, false, null);
        long[] off = new long[]{2, 1};
        Pair<GridN, long[]> map = g.mapToSubgrid(off);
        assertSame(g, map.getFirst());
        assertArrayEquals(off, map.getSecond());
    }

    @Test
    void merge_incompatibleInputs_throw() {
        GridN a = new GridNImpl(2, new double[]{1.0, 1.0}, new double[][]{{0, 2}, {0, 2}}, null, false, null);
        GridN b = new GridNImpl(2, new double[]{2.0, 1.0}, new double[][]{{2, 4}, {0, 2}}, null, false, null);
        assertThrows(IllegalArgumentException.class, () -> a.merge(List.of(a, b)));

        GridN c3 = new GridNImpl(3, new double[]{1,1,1}, new double[][]{{0,1},{0,1},{0,1}}, null, false, null);
        assertThrows(IllegalArgumentException.class, () -> a.merge(List.of(a, c3)));

        assertThrows(IllegalArgumentException.class, () -> a.merge(new ArrayList<>())) ;
    }
}
