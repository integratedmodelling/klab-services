package org.integratedmodelling.klab.common.data;

import org.ehcache.Cache;
import org.ehcache.PersistentCacheManager;
import org.ehcache.config.builders.CacheConfigurationBuilder;
import org.ehcache.config.builders.CacheManagerBuilder;
import org.ehcache.config.builders.ResourcePoolsBuilder;
import org.ehcache.config.units.MemoryUnit;
import org.integratedmodelling.common.logging.Logging;
import org.integratedmodelling.klab.api.digitaltwin.Scheduler;
import org.integratedmodelling.klab.api.scope.UserScope;
import org.integratedmodelling.klab.api.utils.Utils;

import java.io.*;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Supplier;

/**
 * Persistent, configurable file cache for exported files, which are created in a given directory on
 * the filesystem and not managed except for insertion and deletion. The key contains the URN of the
 * observation (or other object), a Scheduler.Event time or URN, and the media type.
 *
 * <p>Can be used at client side or server side as needed.
 */
public class ExportFileCache {

  private static final int DEFAULT_MAX_OCCUPANCY_MB = 1024;
  private final int maxOccupancy;
  private boolean offline;
  private PersistentCacheManager persistentCacheManager;
  private Cache<Key, File> cache;
  private String fileExtension = "dat";

  private static ExportFileCache _temporary;

  private ExportFileCache(ExportFileCache exportFileCache) {
    offline = exportFileCache.offline;
    maxOccupancy = exportFileCache.maxOccupancy;
    persistentCacheManager = exportFileCache.persistentCacheManager;
    cache = exportFileCache.cache;
    fileExtension = exportFileCache.fileExtension;
  Runtime.getRuntime().addShutdownHook(new Thread(() -> {
      try {
          close();
      } catch (IOException e) {
          Logging.INSTANCE.error("Error closing export file cache", e);
      }
  }));
  }

//  @Override
  public void close() throws IOException {
    if (!offline) {
      persistentCacheManager.close();
    }
  }

  public void setFileExtension(String fileExtension) {
    this.fileExtension = fileExtension;
  }

  /**
   * Create a temporary version that forces the file extension to the passed one.
   *
   * @param fileExtension
   * @return
   */
  public ExportFileCache withExtension(String fileExtension) {
    var ret = new ExportFileCache(this);
    ret.fileExtension = fileExtension;
    return ret;
  }

  private File getTemporaryFile() {
    // TODO
    return null;
  }

  public static class Key implements Serializable {
    private String urn;
    private String locator; // stringified long (possibly array) or event URN
    private String mediaType;
    private String hash; // if used, hash of the file content to verify integrity and currency

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

    public String getHash() {
      return hash;
    }

    public void setHash(String hash) {
      this.hash = hash;
    }
  }

  public ExportFileCache(File directory, String name, int maxMbOccupancy) {

    this.maxOccupancy = maxMbOccupancy;
    Runtime.getRuntime().addShutdownHook(new Thread(() -> {
        try {
            close();
        } catch (IOException e) {
            Logging.INSTANCE.error("Error closing export file cache", e);
        }
    }));

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
      String urn, Scheduler.Event event, String mediaType, Supplier<?> computeIfAbsent) {
    if (offline) {
      return toFile(computeIfAbsent);
    }
    // create key
    // return null if content is null
    // retrieve or add

    return null;
  }

  public File toFile(Supplier<?> computeIfAbsent) {

    var input = computeIfAbsent.get();
    if (input instanceof File) {
      return (File) input;
    } else if (input instanceof InputStream inputStream) {
    } else if (input instanceof URL url) {
    } else if (input instanceof String string) {
      return Utils.Files.writeStringToFile(string, getTemporaryFile());
    }

    /* TODO inputstream, String, etc. */
    throw new IllegalArgumentException("Input cannot be converted to a file");
  }

  public static ExportFileCache temporary() {
    if (_temporary == null) {
      Path tdir = null;
      try {
        tdir = Files.createTempDirectory("kexport");
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
      _temporary = new ExportFileCache(tdir.toFile(), "temporary", DEFAULT_MAX_OCCUPANCY_MB);
    }
    return _temporary;
  }

  public static ExportFileCache user(UserScope scope) {
    return null;
  }

  public static ExportFileCache persistent(File directory) {
    return null;
  }
}
