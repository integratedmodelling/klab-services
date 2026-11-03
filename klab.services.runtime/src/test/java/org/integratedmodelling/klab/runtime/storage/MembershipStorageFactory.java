package org.integratedmodelling.klab.runtime.storage;
import java.nio.file.Path;
import org.integratedmodelling.klab.services.scopes.ServiceContextScope;
/** Test package bridge to the real embedded-storage constructor. No authority construction. */
public final class MembershipStorageFactory {
 public static StorageManagerImpl open(ServiceContextScope scope,Path directory) {
  return new StorageManagerImpl(scope,directory.resolve("scratch").toFile(),directory.resolve("persistent").toFile());
 }
}
