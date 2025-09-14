package org.integratedmodelling.klab.runtime.storage;

import org.ehcache.Cache;
import org.ehcache.PersistentCacheManager;
import org.ehcache.config.EvictionAdvisor;
import org.ehcache.config.builders.CacheConfigurationBuilder;
import org.ehcache.config.builders.CacheManagerBuilder;
import org.ehcache.config.builders.ResourcePoolsBuilder;
import org.ehcache.config.units.MemoryUnit;
import org.integratedmodelling.common.logging.Logging;
import org.integratedmodelling.klab.api.digitaltwin.Scheduler;
import org.integratedmodelling.klab.api.scope.UserScope;

import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.util.function.Supplier;

/**
 * Persistent, configurable file cache for export files, which are created in a given directory on
 * the filesystem and not managed except for insertion and deletion. The key contains the URN of the
 * observation (or other object), a Scheduler.Event time or URN, and the media type.
 */
public class ExportFileCache implements Closeable {

  private final int maxOccupancy;
  private boolean offline;
  private PersistentCacheManager persistentCacheManager;
  private Cache<Key, File> cache;

  @Override
  public void close() throws IOException {
    if (!offline) {
      persistentCacheManager.close();
    }
  }

  public static class Key implements Serializable {
    private String urn;
    private String locator; // stringified long or event URN
    private String mediaType;

    public String getUrn() {
      return urn;
    }

    public void setUrn(String urn) {
      this.urn = urn;
    }

    public String getLocator() {
      return locator;
    }

    public void setLocator(String locator) {
      this.locator = locator;
    }

    public String getMediaType() {
      return mediaType;
    }

    public void setMediaType(String mediaType) {
      this.mediaType = mediaType;
    }
  }

  public ExportFileCache(File directory, String name, int maxMbOccupancy) {

    this.maxOccupancy = maxMbOccupancy;

    // configure eviction policy
    var configuration =
        CacheConfigurationBuilder.newCacheConfigurationBuilder(
                Key.class,
                File.class,
                // occupancy here refers to the File objects, not their content. 10 MB is pretty
                // much infinite
                ResourcePoolsBuilder.newResourcePoolsBuilder().disk(10, MemoryUnit.MB, true))
            .withEvictionAdvisor(
                (key, file) -> {
                  // TODO if the new file pushes the dir content beyond max, evict oldest
                  return false;
                });
    try {
      this.persistentCacheManager =
          CacheManagerBuilder.newCacheManagerBuilder()
              .with(CacheManagerBuilder.persistence(directory))
              .withCache(name, configuration)
              .build(true);

      this.cache = persistentCacheManager.getCache(name, Key.class, File.class);
    } catch (Throwable e) {
      this.offline = true;
      Logging.INSTANCE.error("Failed to initialize export file cache: caching is disabled", e);
    }
  }

  public File get(
      String urn, Scheduler.Event event, String mediaType, Supplier<File> computeIfAbsent) {
    if (offline) {
      return computeIfAbsent.get();
    }
    // create key
    // retrieve or add
    return null;
  }

  public static ExportFileCache temporary() {
    return null;
  }

  public static ExportFileCache user(UserScope scope) {
    return null;
  }

  public static ExportFileCache persistent(File directory) {
    return null;
  }
}
