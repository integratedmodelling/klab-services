package org.integratedmodelling.klab.services.reasoner.internal;
import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
class NullPinTest {
  @TempDir Path directory;
  @Test void explicitNullPinMustFailClosedInsteadOfDisablingThePin() throws Exception {
    var file=directory.resolve("null-pin.json"); Files.writeString(file,"null");
    var old=System.getProperty(BootstrapSourcePin.PROPERTY);
    try {
      System.setProperty(BootstrapSourcePin.PROPERTY,file.toAbsolutePath().toString());
      var failure=assertThrows(IllegalArgumentException.class,BootstrapSourcePin::fromSystemProperty,
          "an explicitly supplied null document must not select the unpinned resolver");
      assertEquals("Invalid explicit bootstrap pin file",failure.getMessage());
      assertNull(failure.getCause(),"startup rejection must not echo pin contents through a parse cause");
      assertThrows(IllegalArgumentException.class,()->BootstrapSourcePin.read(file));
    } finally {
      if(old==null)System.clearProperty(BootstrapSourcePin.PROPERTY);
      else System.setProperty(BootstrapSourcePin.PROPERTY,old);
    }
  }
  @Test void absentPropertyRemainsTheOnlyUnpinnedConfiguration() {
    var old=System.getProperty(BootstrapSourcePin.PROPERTY);
    try {System.clearProperty(BootstrapSourcePin.PROPERTY);assertNull(BootstrapSourcePin.fromSystemProperty());}
    finally {if(old==null)System.clearProperty(BootstrapSourcePin.PROPERTY);else System.setProperty(BootstrapSourcePin.PROPERTY,old);}
  }
}
