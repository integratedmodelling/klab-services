package org.integratedmodelling.klab.api.services;

/** Optional capture capability for providers whose configured state is independently replayable.
 * Export must be deterministic. Restore must validate the exact request and format, create an
 * independent bridge, and perform no network access or mutable external discovery. Restored
 * lookups must use that frozen state, including full hierarchy, search and reconciliation.
 * Normal configure and persistent CachePolicy semantics remain unchanged. */
public interface SnapshottingAuthority extends Authority, AutoCloseable {
  ConfiguredAuthoritySnapshot captureConfiguration(String configurationId);
  String restoreConfiguration(ConfigurationRequest request, ConfiguredAuthoritySnapshot snapshot);
  /** Release all independently owned bridge state and clients. */
  @Override void close();
}
