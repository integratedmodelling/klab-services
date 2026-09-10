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
