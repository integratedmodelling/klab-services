package org.integratedmodelling.klab.components;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import org.integratedmodelling.common.utils.Utils;
import org.junit.jupiter.api.Test;

class OwnedBootstrapHttpStatusTest {
  @Test void retainsOnlyNumericStatusThroughCauseChain() {
    var http=new Utils.Http.Client.RequestFailure(500,"Authorization Bearer secret response body",null);
    var outer=new IllegalStateException("secret",http);
    assertEquals(500,OwnedBootstrapDiagnostics.httpStatus(outer));
    var safe=OwnedBootstrapDiagnostics.snapshot(outer);
    assertFalse(safe.toString().contains("secret"));
    assertFalse(safe.getCause().toString().contains("Bearer"));
    assertNull(OwnedBootstrapDiagnostics.httpStatus(new Utils.Http.Client.RequestFailure(0,"secret",null)));
  }
}
