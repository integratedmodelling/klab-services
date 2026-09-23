package org.integratedmodelling.klab.api.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.io.File;
import org.junit.jupiter.api.Test;

class DataFileConversionTest {

  @Test
  void convertsPathStringsWithoutChangingTheirMeaning() {
    for (var path : new String[] {"relative/path with spaces", "C:\\data\\distribution", "./café"}) {
      var expected = new File(path);
      assertEquals(expected, Utils.Data.asType(path, File.class));
      assertEquals(expected, Utils.Data.parseAsType(path, File.class));
    }
  }

  @Test
  void retainsAlreadyTypedFiles() {
    var file = new File("relative/path");
    assertSame(file, Utils.Data.asType(file, File.class));
  }
}
