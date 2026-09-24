package org.integratedmodelling.klab.runtime.kactors;

import static org.junit.jupiter.api.Assertions.*;

import org.integratedmodelling.klab.api.collections.Constant;
import org.junit.jupiter.api.Test;

class JavaArgumentConversionsTest {
  enum Mixed {
    MixedCase;
    @Override public String toString() { return "display label"; }
  }
  enum Ambiguous { VALUE, value }

  @Test
  void floatingPointWideningAcceptsIntegersButNotNarrowingOrText() {
    for (Class<?> target : java.util.List.of(float.class, Float.class, double.class, Double.class)) {
      for (Object value : java.util.List.of((byte) 1, (short) 1, 1000, 1000L, 0L,
          -1L, Long.MIN_VALUE, Long.MAX_VALUE)) {
        assertTrue(JavaArgumentConversions.widensToFloatingPoint(value, target));
      }
      assertFalse(JavaArgumentConversions.widensToFloatingPoint("1000", target));
      assertFalse(JavaArgumentConversions.widensToFloatingPoint(true, target));
      assertFalse(JavaArgumentConversions.widensToFloatingPoint(null, target));
    }
    assertTrue(JavaArgumentConversions.widensToFloatingPoint(1.5f, Double.class));
    assertTrue(JavaArgumentConversions.widensToFloatingPoint(1.5f, double.class));
    assertFalse(JavaArgumentConversions.widensToFloatingPoint(1.5, Float.class));
    assertFalse(JavaArgumentConversions.widensToFloatingPoint(1.5, float.class));
    assertFalse(JavaArgumentConversions.widensToFloatingPoint(1L, Integer.class));
  }

  @Test
  void matchesDeclaredNamesWithoutUsingDisplayLabels() {
    assertSame(Mixed.MixedCase, JavaArgumentConversions.enumValue(Constant.create("MIXEDCASE"), Mixed.class));
    assertSame(Mixed.MixedCase, JavaArgumentConversions.enumValue("mixedcase", Mixed.class));
    assertSame(Mixed.MixedCase, JavaArgumentConversions.enumValue(Mixed.MixedCase, Mixed.class));
    assertThrows(IllegalArgumentException.class,
        () -> JavaArgumentConversions.enumValue("display label", Mixed.class));
    assertThrows(IllegalArgumentException.class,
        () -> JavaArgumentConversions.enumValue(Constant.create("Mixed.MixedCase"), Mixed.class));
  }

  @Test
  void rejectsUnknownAndAmbiguousNamesWithoutSelectingAnArbitraryValue() {
    assertThrows(IllegalArgumentException.class,
        () -> JavaArgumentConversions.enumValue(Constant.create("unknown"), Mixed.class));
    assertThrows(IllegalArgumentException.class,
        () -> JavaArgumentConversions.enumValue(Constant.create("VALUE"), Ambiguous.class));
    assertSame(Ambiguous.VALUE, JavaArgumentConversions.enumValue(Ambiguous.VALUE, Ambiguous.class));
  }
}
