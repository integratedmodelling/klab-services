package org.integratedmodelling.klab.api.data;

import org.integratedmodelling.klab.api.geometry.Geometry;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SpaceFillingCurveTest {

  @Test
  void offset_rowMajor_linear_and_XY_and_XYZ() {
    // D1_LINEAR behaves row-major: offset == step mod total
    long[] size1 = new long[] {7};
    for (int s = -10; s <= 20; s++) {
      long total = 7;
      long expected = ((s % total) + total) % total;
      assertEquals(expected, Data.SpaceFillingCurve.D1_LINEAR.offset(s, size1));
    }

    // D2_XY behaves as row-major for 2D
    long[] size2 = new long[] {3, 2};
    for (int s = -5; s <= 10; s++) {
      long total = 3L * 2L;
      long expected = ((s % total) + total) % total;
      assertEquals(expected, Data.SpaceFillingCurve.D2_XY.offset(s, size2));
    }

    // D3_XYZ behaves as row-major in 3D
    long[] size3 = new long[] {2, 2, 3};
    long total = 2L * 2L * 3L;
    int limit = (int) (total * 2);
    for (int s = -limit; s <= limit; s++) {
      long expected = ((s % total) + total) % total;
      assertEquals(expected, Data.SpaceFillingCurve.D3_XYZ.offset(s, size3));
    }
  }

  @Test
  void offset_YX_and_XInvY_examples() {
    long[] sizeXY = new long[] {3, 2};
    long[] expectedYX = new long[] {0, 2, 4, 1, 3, 5};
    for (int s = 0; s < expectedYX.length; s++) {
      assertEquals(expectedYX[s], Data.SpaceFillingCurve.D2_YX.offset(s, sizeXY), "D2_YX step " + s);
    }

    long[] sizeXInvY = new long[] {2, 3};
    long[] expectedXInvY = new long[] {2, 1, 0, 5, 4, 3};
    for (int s = 0; s < expectedXInvY.length; s++) {
      assertEquals(expectedXInvY[s], Data.SpaceFillingCurve.D2_XInvY.offset(s, sizeXInvY), "D2_XInvY step " + s);
    }
  }

//  @Test
//  void offset_ZYX_flattens_to_rowMajor_like_XYZ() {
//    long[] size3 = new long[] {2, 2, 2};
//    for (int s = 0; s < 8; s++) {
//      long o1 = Data.SpaceFillingCurve.D3_XYZ.offset(s, size3);
//      long o2 = Data.SpaceFillingCurve.D3_ZYX.offset(s, size3);
//      assertEquals(o1, o2, "D3_ZYX must flatten to same row-major offset as D3_XYZ");
//    }
//  }

  @Test
  void map_preserves_rowMajor_offsets_between_curves() {
    long[] sizes = new long[] {3, 2};
    for (int s = 0; s < 6; s++) {
      long destStep = Data.SpaceFillingCurve.D2_XY.map(s, sizes, Data.SpaceFillingCurve.D2_YX);
      long srcOffset = Data.SpaceFillingCurve.D2_XY.offset(s, sizes);
      long destOffset = Data.SpaceFillingCurve.D2_YX.offset((int) destStep, sizes);
      assertEquals(srcOffset, destOffset, "XY->YX mapping must preserve row-major offset");
    }

    for (int s = 0; s < 6; s++) {
      long destStep = Data.SpaceFillingCurve.D2_YX.map(s, sizes, Data.SpaceFillingCurve.D2_XInvY);
      long srcOffset = Data.SpaceFillingCurve.D2_YX.offset(s, sizes);
      long destOffset = Data.SpaceFillingCurve.D2_XInvY.offset((int) destStep, sizes);
      assertEquals(srcOffset, destOffset, "YX->XInvY mapping must preserve row-major offset");
    }
  }

  @Test
  void defaultCurve_matches_spatial_dimensionality() {
    Geometry g1 = Geometry.create("S1(10)");
    Geometry g2 = Geometry.create("S2(10,20)");
    Geometry g3 = Geometry.create("S3(3,4,5)");
    Geometry gNoSpace = Geometry.create("T1(5)");

    assertEquals(Data.SpaceFillingCurve.D1_LINEAR, Data.SpaceFillingCurve.defaultCurve(g1));
    assertEquals(Data.SpaceFillingCurve.D2_XY, Data.SpaceFillingCurve.defaultCurve(g2));
    assertEquals(Data.SpaceFillingCurve.D3_XYZ, Data.SpaceFillingCurve.defaultCurve(g3));
    assertEquals(Data.SpaceFillingCurve.D1_LINEAR, Data.SpaceFillingCurve.defaultCurve(gNoSpace));
  }

  @Test
  void hilbert_variants_throw_in_offset_and_map() {
    long[] size2 = new long[] {4, 4};
    long[] size3 = new long[] {2, 2, 2};

    assertThrows(UnsupportedOperationException.class, () -> Data.SpaceFillingCurve.D2_HILBERT.offset(0, size2));
    assertThrows(UnsupportedOperationException.class, () -> Data.SpaceFillingCurve.D3_HILBERT.offset(0, size3));

    assertThrows(UnsupportedOperationException.class, () -> Data.SpaceFillingCurve.D2_HILBERT.map(0, size2, Data.SpaceFillingCurve.D2_XY));
    assertThrows(UnsupportedOperationException.class, () -> Data.SpaceFillingCurve.D3_HILBERT.map(0, size3, Data.SpaceFillingCurve.D3_XYZ));
  }

  @Test
  void invalid_sizes_rejected() {
    // Null or empty sizes
    assertThrows(IllegalArgumentException.class, () -> Data.SpaceFillingCurve.D1_LINEAR.offset(0, null));
    assertThrows(IllegalArgumentException.class, () -> Data.SpaceFillingCurve.D1_LINEAR.offset(0, new long[] {}));

    // Zero or negative size
    assertThrows(IllegalArgumentException.class, () -> Data.SpaceFillingCurve.D2_XY.offset(0, new long[] {3, 0}));
    assertThrows(IllegalArgumentException.class, () -> Data.SpaceFillingCurve.D2_XY.offset(0, new long[] {3, -2}));

    // Dimensionality mismatch
    assertThrows(IllegalArgumentException.class, () -> Data.SpaceFillingCurve.D2_XY.offset(0, new long[] {3}));
    assertThrows(IllegalArgumentException.class, () -> Data.SpaceFillingCurve.D3_XYZ.offset(0, new long[] {2, 2}));

    // Also check map validations
    assertThrows(IllegalArgumentException.class, () -> Data.SpaceFillingCurve.D2_XY.map(0, null, Data.SpaceFillingCurve.D2_YX));
    assertThrows(IllegalArgumentException.class, () -> Data.SpaceFillingCurve.D2_XY.map(0, new long[] {}, Data.SpaceFillingCurve.D2_YX));
    assertThrows(IllegalArgumentException.class, () -> Data.SpaceFillingCurve.D2_XY.map(0, new long[] {3, 0}, Data.SpaceFillingCurve.D2_YX));
    assertThrows(IllegalArgumentException.class, () -> Data.SpaceFillingCurve.D2_XY.map(0, new long[] {3}, Data.SpaceFillingCurve.D2_YX));
    assertThrows(IllegalArgumentException.class, () -> Data.SpaceFillingCurve.D2_XY.map(0, new long[] {3, 2}, null));
    assertThrows(IllegalArgumentException.class, () -> Data.SpaceFillingCurve.D2_XY.map(0, new long[] {3, 2}, Data.SpaceFillingCurve.D3_XYZ));
  }

  // -------------------- Tests for offsets() --------------------

  private static long flattenRowMajor(long[] coords, long[] sizes) {
    long offset = 0L;
    long stride = 1L;
    for (int i = sizes.length - 1; i >= 0; i--) {
      offset += coords[i] * stride;
      stride *= sizes[i];
    }
    return offset;
  }

  @Test
  void offsets_rowMajor_linear_and_XY_and_XYZ() {
    // 1D linear
    long[] size1 = new long[] {5};
    long total1 = 5;
    for (int s = -7; s < 12; s++) {
      long ns = ((s % total1) + total1) % total1;
      long[] c = Data.SpaceFillingCurve.D1_LINEAR.offsets(s, size1);
      assertArrayEquals(new long[] {ns}, c);
    }

    // 2D XY row-major: flatten(coords) == step normalized
    long[] size2 = new long[] {3, 4};
    long total2 = 12;
    for (int s = -3; s < 20; s++) {
      long[] coords = Data.SpaceFillingCurve.D2_XY.offsets(s, size2);
      long off = flattenRowMajor(coords, size2);
      long ns = ((s % total2) + total2) % total2;
      assertEquals(ns, off);
    }

    // 3D XYZ row-major: same property
    long[] size3 = new long[] {2, 2, 3};
    long total3 = 12;
    for (int s = -13; s < 25; s++) {
      long[] coords = Data.SpaceFillingCurve.D3_XYZ.offsets(s, size3);
      long off = flattenRowMajor(coords, size3);
      long ns = ((s % total3) + total3) % total3;
      assertEquals(ns, off);
    }
  }

  @Test
  void offsets_consistency_with_offset_for_variants() {
    // For non-row-major variants ensure flatten(offsets(step)) == offset(step)
    long[] sizeXY = new long[] {3, 2};
    long total = 6;
    for (int s = 0; s < total; s++) {
      long[] cYX = Data.SpaceFillingCurve.D2_YX.offsets(s, sizeXY);
      assertEquals(
          Data.SpaceFillingCurve.D2_YX.offset(s, sizeXY),
          flattenRowMajor(cYX, sizeXY),
          "D2_YX offsets must match offset after flatten");
    }

    long[] sizeXInvY = new long[] {2, 3};
    long total2 = 6;
    for (int s = 0; s < total2; s++) {
      long[] c = Data.SpaceFillingCurve.D2_XInvY.offsets(s, sizeXInvY);
      assertEquals(
          Data.SpaceFillingCurve.D2_XInvY.offset(s, sizeXInvY),
          flattenRowMajor(c, sizeXInvY),
          "D2_XInvY offsets must match offset after flatten");
    }

    long[] size3 = new long[] {2, 2, 2};
    for (int s = 0; s < 8; s++) {
      long[] c = Data.SpaceFillingCurve.D3_ZYX.offsets(s, size3);
      assertEquals(
          Data.SpaceFillingCurve.D3_ZYX.offset(s, size3),
          flattenRowMajor(c, size3),
          "D3_ZYX offsets must match offset after flatten");
    }
  }

  @Test
  void offsets_specific_examples_XInvY() {
    long[] size = new long[] {2, 3};
    long[][] expected = new long[][] {
        {0, 2}, {0, 1}, {0, 0}, // steps 0..2
        {1, 2}, {1, 1}, {1, 0}  // steps 3..5
    };
    for (int s = 0; s < expected.length; s++) {
      long[] c = Data.SpaceFillingCurve.D2_XInvY.offsets(s, size);
      assertArrayEquals(expected[s], c, "D2_XInvY coords at step " + s);
    }
  }

  @Test
  void offsets_hilbert_and_invalid_sizes() {
    long[] size2 = new long[] {4, 4};
    long[] size3 = new long[] {2, 2, 2};

    assertThrows(
        UnsupportedOperationException.class,
        () -> Data.SpaceFillingCurve.D2_HILBERT.offsets(0, size2));
    assertThrows(
        UnsupportedOperationException.class,
        () -> Data.SpaceFillingCurve.D3_HILBERT.offsets(0, size3));

    // invalid sizes
    assertThrows(IllegalArgumentException.class, () -> Data.SpaceFillingCurve.D1_LINEAR.offsets(0, null));
    assertThrows(IllegalArgumentException.class, () -> Data.SpaceFillingCurve.D1_LINEAR.offsets(0, new long[] {}));
    assertThrows(IllegalArgumentException.class, () -> Data.SpaceFillingCurve.D2_XY.offsets(0, new long[] {3, 0}));
    assertThrows(IllegalArgumentException.class, () -> Data.SpaceFillingCurve.D2_XY.offsets(0, new long[] {3, -1}));
    assertThrows(IllegalArgumentException.class, () -> Data.SpaceFillingCurve.D2_XY.offsets(0, new long[] {3}));
    assertThrows(IllegalArgumentException.class, () -> Data.SpaceFillingCurve.D3_XYZ.offsets(0, new long[] {2, 2}));
  }
}
