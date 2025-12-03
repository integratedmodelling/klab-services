package org.integratedmodelling.klab.runtime.storage;

import org.integratedmodelling.klab.api.collections.Triple;
import org.integratedmodelling.klab.api.configuration.Configuration;
import org.integratedmodelling.klab.api.data.Data;
import org.integratedmodelling.klab.api.data.Storage;
import org.integratedmodelling.klab.api.digitaltwin.DigitalTwin;
import org.integratedmodelling.klab.api.digitaltwin.StorageManager;
import org.integratedmodelling.klab.api.exceptions.KlabIOException;
import org.integratedmodelling.klab.api.exceptions.KlabIllegalStateException;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.lang.Annotation;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.scope.ServiceScope;
import org.integratedmodelling.klab.api.services.KlabService;
import org.integratedmodelling.klab.api.services.RuntimeService;
import org.integratedmodelling.klab.api.services.runtime.objects.ContextInfo;
import org.integratedmodelling.klab.services.base.BaseService;
import org.integratedmodelling.klab.services.scopes.ServiceContextScope;
import org.integratedmodelling.klab.utilities.Utils;
import org.integratedmodelling.klab.configuration.ServiceConfiguration;
import org.ojalgo.array.BufferArray;
import org.ojalgo.concurrent.Parallelism;
import picocli.CommandLine;

import java.io.*;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

/**
 * There is one separate <code>StorageScope</code> in each {@link ContextScope}. It's built on
 * demand based on the configuration available from the context data, including whatever user-level
 * configuration was passed, and stored in the context data at the runtime side. The StorageScope is
 * managed by the StorageManager, which is a singleton used by the DigitalTwin.
 */
public class StorageManagerImpl implements StorageManager {

  private static final String NEXT_ID_PROPERTY = "storage.mmap.nextid";

  private final ServiceContextScope contextScope;
  private final File propertyFile;
  private final RuntimeService service;
  private final File workspace;
  private final File floatBackupFile;
  private final File doubleBackupFile;
  private final File intBackupFile;
  private final File longBackupFile;
  private final File booleanBackupFile;
  private final int histogramBinSize = 20;
  private final Map<Observation, Storage> storage = new ConcurrentHashMap<>();
  private final AtomicLong nextId = new AtomicLong(0);
  private final Executor shardMaintenance = Executors.newSingleThreadExecutor();
  private final File persistentSpace;
  private boolean existingData = false;

  public boolean isRecordHistogram() {
    return recordHistogram;
  }

  private boolean recordHistogram = true;

  // Called to remove any orphan files from disk.
  public static void removeStorage(ContextInfo scope, RuntimeService service) {
    final var path =
        BaseService.getConfigurationSubdirectory(
            ((BaseService) service).startupOptions(), "storage");
    final var storagePath = new File(path + File.separator + scope.getConfiguration().getId());
    if (storagePath.isDirectory()) {
      Utils.Files.deleteQuietly(storagePath);
    }
  }

  public StorageManagerImpl(RuntimeService service, ServiceContextScope scope) {
    // choose the mm files, parallelism level and the floating point representation
    this.service = service;
    this.workspace = ServiceConfiguration.INSTANCE.getScratchDataDirectory(scope.getId());
    this.persistentSpace =
        BaseService.getConfigurationSubdirectory(
            ((BaseService) service).startupOptions(), "storage");
    if (persistentSpace.isDirectory()) {
      existingData = true;
    }
    this.floatBackupFile = new File(this.workspace + File.separator + "fstorage.bin");
    this.doubleBackupFile = new File(this.workspace + File.separator + "dstorage.bin");
    this.longBackupFile = new File(this.workspace + File.separator + "lstorage.bin");
    this.intBackupFile = new File(this.workspace + File.separator + "istorage.bin");
    this.booleanBackupFile = new File(this.workspace + File.separator + "bstorage.bin");
    this.contextScope = scope;
    this.propertyFile =
        ServiceConfiguration.INSTANCE.getFileWithTemplate(
            "storage.properties", NEXT_ID_PROPERTY + "=0");
    // TODO should have a cache of existing storages and create the storage lazy proxies for the
    //  existing ones.
    readConfiguration();
  }

  // FIXME this is for floats; there should be factories created on demand for each type and size.
  // Put
  //  this in the
  //  constructor
  BufferArray.MappedFileFactory floatMappedArrayFactory = null;
  BufferArray.MappedFileFactory doubleMappedArrayFactory = null;
  BufferArray.MappedFileFactory intMappedArrayFactory = null;
  BufferArray.MappedFileFactory longMappedArrayFactory = null;
  BufferArray.MappedFileFactory booleanMappedArrayFactory = null;

  private synchronized BufferArray.MappedFileFactory getFloatFactory() {
    if (this.floatMappedArrayFactory == null) {
      this.floatMappedArrayFactory = BufferArray.R032.newMapped(this.floatBackupFile);
    }
    return this.floatMappedArrayFactory;
  }

  private synchronized BufferArray.MappedFileFactory getLongFactory() {
    if (this.longMappedArrayFactory == null) {
      this.longMappedArrayFactory = BufferArray.Z032.newMapped(this.longBackupFile);
    }
    return this.longMappedArrayFactory;
  }

  private synchronized BufferArray.MappedFileFactory getDoubleFactory() {
    if (this.doubleMappedArrayFactory == null) {
      this.doubleMappedArrayFactory = BufferArray.R064.newMapped(this.doubleBackupFile);
    }
    return this.doubleMappedArrayFactory;
  }

  /*
  SHORT int. For now we use floats to encode longs
   */
  private synchronized BufferArray.MappedFileFactory getIntFactory() {
    if (this.intMappedArrayFactory == null) {
      this.intMappedArrayFactory = BufferArray.Z016.newMapped(this.intBackupFile);
    }
    return this.intMappedArrayFactory;
  }

  /**
   * Bytes. Not sure we should have the overhead of packing bytes but maybe we should as bitmaps
   * make wonderful cheap masks and they may intersect more cheaply than 2D geometries.
   *
   * @return
   */
  private synchronized BufferArray.MappedFileFactory getBooleanFactory() {
    if (this.booleanMappedArrayFactory == null) {
      this.booleanMappedArrayFactory = BufferArray.Z008.newMapped(this.booleanBackupFile);
    }
    return this.booleanMappedArrayFactory;
  }

  public void close() {

    storage.clear();

    // TODO this is crucial for everything, ensure it's complete
    if (doubleMappedArrayFactory != null) {
      doubleMappedArrayFactory = null;
      Utils.Files.deleteQuietly(doubleBackupFile);
    }
    if (floatMappedArrayFactory != null) {
      floatMappedArrayFactory = null;
      Utils.Files.deleteQuietly(floatBackupFile);
    }
    if (intMappedArrayFactory != null) {
      intMappedArrayFactory = null;
      Utils.Files.deleteQuietly(intBackupFile);
    }
    if (booleanMappedArrayFactory != null) {
      booleanMappedArrayFactory = null;
      Utils.Files.deleteQuietly(booleanBackupFile);
    }
    if (longMappedArrayFactory != null) {
      longMappedArrayFactory = null;
      Utils.Files.deleteQuietly(longBackupFile);
    }
  }

  public synchronized BufferArray getIntBuffer(long sliceSize) {
    return getIntFactory().make(sliceSize);
  }

  public synchronized BufferArray getLongBuffer(long sliceSize) {
    return getLongFactory().make(sliceSize);
  }

  public synchronized BufferArray getFloatBuffer(long sliceSize) {
    return getFloatFactory().make(sliceSize);
  }

  public synchronized BufferArray getBooleanBuffer(long sliceSize) {
    return getBooleanFactory().make(sliceSize);
  }

  public synchronized BufferArray getDoubleBuffer(long sliceSize) {
    return getDoubleFactory().make(sliceSize);
  }

  public int getHistogramBinSize() {
    return histogramBinSize;
  }

  public Storage getStorage(Observation observation) {

    var ret = this.storage.get(observation);
    if (ret == null && this.existingData) {
      ret = reconstructStorage(observation);
    }

    if (ret == null) {
      throw new KlabIllegalStateException(
          "cannot create storage: no storage found for "
              + observation
              + " or insufficient information to reconstruct storage");
    }
    return ret;
  }

  private Storage reconstructStorage(Observation observation) {
    var contextualizationData = observation.getContextualizationData();
    if (contextualizationData == null) return null;
    var shardingStrategy = contextualizationData.getNativeShardingStrategy();
    if (shardingStrategy == null) return null;
    return new StorageImpl(observation, shardingStrategy, contextScope, this);
  }

  @Override
  public void deleteStorage(DigitalTwin.Configuration digitalTwin, ServiceScope serviceScope) {
    // remove any persisted data; clear caches related to a DT
    storage.values().forEach(s -> s.close(serviceScope));
    storage.clear();
  }

  @Override
  public Storage createStorage(Observation observation, Data.ShardingStrategy shardingStrategy) {
    return this.storage.computeIfAbsent(
        observation, urn -> createShard(observation, shardingStrategy, contextScope));
  }

  @Override
  public <T extends Storage.Scanner> T getTemporaryScanner(
      Geometry geometry, Data.FillCurve fillCurve, Class<T> scannerClass) {
    // TODO
    return null;
  }

  private Storage createShard(
      Observation observation,
      Data.ShardingStrategy shardingStrategy,
      ServiceContextScope contextScope) {
    // TODO set up a persistable peer object for the shard and expose it into the maintenance thread
    return new StorageImpl(observation, shardingStrategy, contextScope, this);
  }

  @Override
  public void clear() {
    // CHECK the implementation of close() is actually a clear(); close() may save state for later
    // re-opening,
    // depending on context persistence
    close();
  }

  /**
   * Return a new unique ID for a buffer. TODO this must survive reboots.
   *
   * @return
   */
  public long nextBufferId() {
    long ret = nextId.incrementAndGet();
    writeConfiguration();
    return ret;
  }

  private void readConfiguration() {
    Properties properties = new Properties();
    try (InputStream input = new FileInputStream(propertyFile)) {
      properties.load(input);
      this.nextId.set(Long.parseLong(properties.getProperty(NEXT_ID_PROPERTY)));
    } catch (Exception e) {
      throw new KlabIOException("cannot read configuration properties");
    }
  }

  private void writeConfiguration() {
    Properties p = new Properties();
    p.setProperty(NEXT_ID_PROPERTY, nextId.get() + "");
    try {
      p.store(new FileOutputStream(propertyFile), null);
    } catch (Exception e) {
      throw new KlabIOException(e);
    }
  }

  public boolean saveBufferArray(BufferArray array, File file, Storage.Type type) {
    try {
      int elementSize = type.size();
      FileChannel channel = new RandomAccessFile(file, "rw").getChannel();
      MappedByteBuffer buffer =
          channel.map(FileChannel.MapMode.READ_WRITE, 0, array.count() * elementSize);
      buffer.order(ByteOrder.nativeOrder());

      for (long i = 0; i < array.count(); i++) {
        switch (type) {
          case FLOAT:
            buffer.putFloat(array.get(i).floatValue());
            break;
          case DOUBLE:
            buffer.putDouble(array.get(i).doubleValue());
            break;
          case LONG:
            buffer.putLong(array.get(i).longValue());
            break;
          case INTEGER, KEYED:
            buffer.putInt(array.get(i).intValue());
            break;
          case BOOLEAN:
            buffer.put(array.get(i).byteValue());
            break;
        }
      }
      channel.close();
    } catch (IOException e) {
      contextScope.error("Error saving buffer array: " + e.getMessage());
      return false;
    }
    return true;
  }

  // FIXME restore the histogram and return that
  public boolean loadBufferArray(BufferArray array, File file, Storage.Type type) {
    try {
      FileChannel channel = new RandomAccessFile(file, "r").getChannel();
      MappedByteBuffer buffer = channel.map(FileChannel.MapMode.READ_ONLY, 0, channel.size());
      buffer.order(ByteOrder.nativeOrder());

      for (long i = 0; i < array.count(); i++) {
        switch (type) {
          case FLOAT:
            array.set(i, buffer.getFloat());
            break;
          case DOUBLE:
            array.set(i, buffer.getDouble());
            break;
          case LONG:
            array.set(i, buffer.getLong());
            break;
          case INTEGER, KEYED:
            array.set(i, buffer.getInt());
            break;
          case BOOLEAN:
            array.set(i, buffer.get() != 0);
            break;
        }
      }
      channel.close();
    } catch (IOException e) {
      contextScope.error("Error loading buffer array: " + e.getMessage());
      return false;
    }
    return true;
  }

  public void persistShard(Storage.Scanner scanner) {
    if (scanner instanceof StorageImpl.BaseScanner baseScanner) {
      final var shard = baseScanner.shard();
      final var baseService = contextScope.getService(RuntimeService.class);
      final var storagePath = new File(persistentSpace + File.separator + contextScope.getId());
      storagePath.mkdirs();
      final var outFile = new File(storagePath + File.separator + shard.getUrn() + ".dat");
      shardMaintenance.execute(
          () -> saveBufferArray(baseScanner.data, outFile, shard.getNativeType()));
    }
  }

  public boolean hasExistingData() {
    return existingData;
  }

  public File getStorageFile(Storage.Shard shard) {
    return new File(
        persistentSpace
            + File.separator
            + contextScope.getId()
            + File.separator
            + shard.getUrn()
            + ".dat");
  }
}
