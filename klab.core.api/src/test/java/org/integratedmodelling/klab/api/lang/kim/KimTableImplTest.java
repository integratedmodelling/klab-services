package org.integratedmodelling.klab.api.lang.kim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.List;
import org.integratedmodelling.klab.api.lang.kim.impl.KimClassifierImpl;
import org.integratedmodelling.klab.api.lang.kim.impl.KimLookupTableImpl;
import org.integratedmodelling.klab.api.lang.kim.impl.KimTableImpl;
import org.junit.jupiter.api.Test;

class KimTableImplTest {

  @Test
  void storesRowsAndDerivesDimensions() {
    var first = new KimClassifierImpl();
    var second = new KimClassifierImpl();
    var table = new KimTableImpl();

    table.setRows(List.<KimClassifier[]>of(new KimClassifier[] {first, second}));

    assertEquals(1, table.getRowCount());
    assertEquals(2, table.getColumnCount());
    assertSame(first, table.row(0)[0]);
    assertSame(second, table.rows().getFirst()[1]);
  }

  @Test
  void unspecifiedLookupColumnIsNotMistakenForFirstColumn() {
    assertEquals(-1, new KimLookupTableImpl().getLookupColumnIndex());
  }
}
