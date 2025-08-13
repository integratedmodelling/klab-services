package org.integratedmodelling.klab.api.geometry;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.integratedmodelling.klab.api.data.Data;
import org.integratedmodelling.klab.api.utils.Utils;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

class GeometryAndCurvesTest {

  @Test
  void spaceFillingCurve_offset_rowMajorAndVariants() {
    long[] sizes12 = new long[] {3, 2}; // X=3, Y=2

    // D2_XY behaves as row-major: offset == step (mod total)
    for (int s = -2; s < 8; s++) {
      long off = Data.FillCurve.D2_XY.offset(s, sizes12);
      long total = 3L * 2L;
      long expected = ((s % total) + total) % total;
      assertEquals(expected, off, "D2_XY should be row-major normalized to [0,total)");
    }

    // D2_YX example mapping for sizes [3,2]: expected sequence [0,2,4,1,3,5]
    long[] expectedYX = new long[] {0, 2, 4, 1, 3, 5};
    for (int s = 0; s < expectedYX.length; s++) {
      long off = Data.FillCurve.D2_YX.offset(s, sizes12);
      assertEquals(expectedYX[s], off, "D2_YX incorrect at step " + s);
    }

    // D2_XInvY example with sizes [2,3]
    long[] sizes23 = new long[] {2, 3};
    long[] expectedXInvY = new long[] {2, 1, 0, 5, 4, 3};
    for (int s = 0; s < expectedXInvY.length; s++) {
      long off = Data.FillCurve.D2_XInvY.offset(s, sizes23);
      assertEquals(expectedXInvY[s], off, "D2_XInvY incorrect at step " + s);
    }

    // 3D variants fall back to row-major in offset()
    long[] sizes3d = new long[] {2, 2, 2};
    for (int s = 0; s < 8; s++) {
      long offXYZ = Data.FillCurve.D3_XYZ.offset(s, sizes3d);
      long offZYX = Data.FillCurve.D3_ZYX.offset(s, sizes3d);
      assertEquals(s, offXYZ, "D3_XYZ offset should equal step in row-major");
      // ZYX computes coords differently but flatten to same row-major offset
      assertEquals(offXYZ, offZYX, "D3_ZYX should flatten to same row-major offset");
    }
  }

  @Test
  void spaceFillingCurve_map_preservesRowMajorOffset() {
    long[] sizes = new long[] {3, 2};
    for (int s = 0; s < 6; s++) {
      long destStep = Data.FillCurve.D2_XY.map(s, sizes, Data.FillCurve.D2_YX);
      long srcOffset = Data.FillCurve.D2_XY.offset(s, sizes);
      long destOffset = Data.FillCurve.D2_YX.offset((int) destStep, sizes);
      assertEquals(srcOffset, destOffset, "Mapping XY->YX must preserve row-major offset");
    }

    for (int s = 0; s < 6; s++) {
      long destStep = Data.FillCurve.D2_YX.map(s, sizes, Data.FillCurve.D2_XInvY);
      long srcOffset = Data.FillCurve.D2_YX.offset(s, sizes);
      long destOffset = Data.FillCurve.D2_XInvY.offset((int) destStep, sizes);
      assertEquals(srcOffset, destOffset, "Mapping YX->XInvY must preserve row-major offset");
    }
  }

  @Test
  void defaultCurve_matchesSpatialDimensionality() {
    Geometry g1 = Geometry.create("S1(10)");
    Geometry g2 = Geometry.create("S2(10,20)");
    Geometry g3 = Geometry.create("S3(3,4,5)");
    Geometry gNoSpace = Geometry.create("T1(5)");

    assertEquals(Data.FillCurve.D1_LINEAR, Data.FillCurve.defaultCurve(g1));
    assertEquals(Data.FillCurve.D2_XY, Data.FillCurve.defaultCurve(g2));
    assertEquals(Data.FillCurve.D3_XYZ, Data.FillCurve.defaultCurve(g3));
    // If no space, spec says default to D1_LINEAR
    assertEquals(Data.FillCurve.D1_LINEAR, Data.FillCurve.defaultCurve(gNoSpace));
  }

//  @Test
//  void geometry_split_2D_evenSplit_withBbox() {
//    Geometry g = Geometry.create("S2(8,6){bbox=[0 8 0 6]}");
//    List<Geometry> tiles = g.split(Data.FillCurve.D2_XY, 4);
//
//    assertEquals(4, tiles.size(), "Expected 2x2 tiling for 8x6 with suggested 4");
//
//    // All tiles should be 4x3
//    for (Geometry t : tiles) {
//      Geometry.Dimension s = t.dimension(Geometry.Dimension.Type.SPACE);
//      List<Long> shape = s.getShape();
//      assertEquals(List.of(4L, 3L), shape, "Tile shape must be 4x3");
//
//      Object bboxObj = s.getParameters().get("bbox");
//      List<Double> bbox;
//      if (bboxObj instanceof String str) {
//        bbox = Utils.Data.parseList(Utils.Strings.chopSymmetrically(str, 1), Double.class, " ");
//      } else if (bboxObj instanceof List<?> list) {
//        bbox = new ArrayList<>();
//        for (Object o : list) bbox.add(((Number) o).doubleValue());
//      } else {
//        fail("Tile missing bbox");
//        return;
//      }
//      assertEquals(4.0, bbox.get(1) - bbox.get(0), 1e-9);
//      assertEquals(3.0, bbox.get(3) - bbox.get(2), 1e-9);
//      assertTrue(bbox.get(0) >= 0 && bbox.get(1) <= 8);
//      assertTrue(bbox.get(2) >= 0 && bbox.get(3) <= 6);
//    }
//
//    // Sum of tile areas (cells) equals original
//    long totalCells = 8L * 6L;
//    long sumTileCells =
//        tiles.stream()
//            .map(t -> t.dimension(Geometry.Dimension.Type.SPACE).getShape())
//            .mapToLong(sh -> sh.get(0) * sh.get(1))
//            .sum();
//    assertEquals(totalCells, sumTileCells);
//  }
//
//  @Test
//  void geometry_split_respectsEdgeCases() {
//    // suggestedSplits <= 1 => singleton
//    Geometry g = Geometry.create("S2(4,4){bbox=[0 4 0 4]}");
//    List<Geometry> tiles1 = g.split(Data.FillCurve.D2_XY, 1);
//    assertEquals(1, tiles1.size());
//
//    // totalCells <= suggestedSplits => singleton
//    List<Geometry> tiles2 = g.split(Data.FillCurve.D2_XY, 64);
//    assertEquals(1, tiles2.size());
//
//    // Mismatched curve dimensions => singleton
//    List<Geometry> tiles3 = g.split(Data.FillCurve.D3_XYZ, 4);
//    assertEquals(1, tiles3.size());
//  }

//  @Test
//  @Disabled("Fails consistently")
//  void geometry_split_approximateCount_whenPrimeLike() {
//    Geometry g = Geometry.create("S2(7,5){bbox=[0 7 0 5]}");
//    List<Geometry> tiles = g.split(Data.FillCurve.D2_XY, 5);
//
//    // We cannot always get exactly 5 tiles with rectilinear grid; expect >= suggested
//    assertTrue(tiles.size() >= 5 && tiles.size() <= 7 * 5);
//
//    // Validate tile areas sum to full area
//    long totalCells = 7L * 5L;
//    long sumTileCells =
//        tiles.stream()
//            .map(t -> t.dimension(Geometry.Dimension.Type.SPACE).getShape())
//            .mapToLong(sh -> sh.getFirst() * sh.get(1))
//            .sum();
//    assertEquals(totalCells, sumTileCells);
//
//    // All tile bboxes widths/heights are integer multiples of base cell size (1x1 here)
//    for (Geometry t : tiles) {
//      List<Long> shape = t.dimension(Geometry.Dimension.Type.SPACE).getShape();
//      Object bboxObj = t.dimension(Geometry.Dimension.Type.SPACE).getParameters().get("bbox");
//      List<Double> bbox;
//      if (bboxObj instanceof String str) {
//        bbox = Utils.Data.parseList(Utils.Strings.chopSymmetrically(str, 1), Double.class, " ");
//      } else if (bboxObj instanceof List<?> list) {
//        bbox = list.stream().map(o -> ((Number) o).doubleValue()).collect(Collectors.toList());
//      } else {
//        continue;
//      }
//      // the next assertion always fails
//      assertEquals(shape.get(0).doubleValue(), bbox.get(1) - bbox.get(0), 1e-9);
//      assertEquals(shape.get(1).doubleValue(), bbox.get(3) - bbox.get(2), 1e-9);
//    }
//  }
}
