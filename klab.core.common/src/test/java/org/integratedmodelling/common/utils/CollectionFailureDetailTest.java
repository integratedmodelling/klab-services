package org.integratedmodelling.common.utils;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class CollectionFailureDetailTest {
  @Test void preservesProblemDetailWithoutDumpingBodiesOrStackTraces() {
    assertEquals("Collection request failed: Graph unavailable", detail("{\"detail\":\"Graph unavailable\\nstack trace\"}"));
    assertEquals("Collection request failed: Graph unavailable", detail("{\"body\":{\"detail\":\"Graph unavailable\"}}"));
    assertEquals("Collection request failed",detail("<html>server error</html>"));
    assertEquals("Collection request failed",detail(null));
    assertEquals("Collection request failed",detail("{\"secret\":\"unrelated\"}"));
    assertEquals(539,detail("{\"detail\":\""+"x".repeat(600)+"\"}").length());
  }
  private String detail(String body) { return Utils.Http.Client.RequestFailure.collectionFailureDetail(body); }
}
