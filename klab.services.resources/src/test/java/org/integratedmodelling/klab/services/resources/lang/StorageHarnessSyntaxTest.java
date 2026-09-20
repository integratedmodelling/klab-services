package org.integratedmodelling.klab.services.resources.lang;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.io.StringReader;
import org.eclipse.xtext.parser.IParser;
import org.integratedmodelling.languages.KActorsStandaloneSetup;
import org.junit.jupiter.api.Test;

class StorageHarnessSyntaxTest {
  @Test void storageHarnessIsARealParsableTestcase() throws Exception {
    var source = Files.readString(Path.of("../docs/testcases/klab/staging/vxii/storage.kactors"));
    var injector = new KActorsStandaloneSetup().createInjectorAndDoEMFRegistration();
    var parsed = injector.getInstance(IParser.class).parse(new StringReader(source));
    var errors = new java.util.ArrayList<String>();
    for (var error : parsed.getSyntaxErrors()) errors.add(error.getSyntaxErrorMessage().getMessage() + " at " + error.getStartLine());
    assertFalse(parsed.hasSyntaxErrors(),errors.toString());
    assertNotNull(parsed.getRootASTElement());
  }
}
