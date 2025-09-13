package org.integratedmodelling.klab.data;

import org.integratedmodelling.klab.api.data.Storage;
import org.integratedmodelling.klab.api.exceptions.KlabUnimplementedException;

public class ScannerAdapter {

  public <T extends Storage.Scanner> T adapt(Storage.Scanner scanner, Class<T> scannerClass) {
    if (scanner == null) {
      return null;
    }
    if (scannerClass.isAssignableFrom(scanner.getClass())) {
      return (T) scanner;
    }
    // TODO
    throw new KlabUnimplementedException("Scanner adaptation is unimplemented");
  }
}
