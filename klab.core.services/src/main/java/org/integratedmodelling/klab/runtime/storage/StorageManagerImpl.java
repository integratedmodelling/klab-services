package org.integratedmodelling.klab.runtime.storage;

import org.integratedmodelling.klab.api.collections.Triple;
import org.integratedmodelling.klab.api.data.Data;
import org.integratedmodelling.klab.api.data.Storage;
import org.integratedmodelling.klab.api.digitaltwin.StorageManager;
import org.integratedmodelling.klab.api.exceptions.KlabIOException;
import org.integratedmodelling.klab.api.exceptions.KlabIllegalStateException;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.lang.Annotation;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.services.KlabService;
import org.integratedmodelling.klab.api.services.RuntimeService;
import org.integratedmodelling.klab.services.scopes.ServiceContextScope;
import org.integratedmodelling.klab.utilities.Utils;
import org.integratedmodelling.klab.configuration.ServiceConfiguration;
import org.ojalgo.array.BufferArray;
import org.ojalgo.concurrent.Parallelism;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
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

  public boolean isRecordHistogram() {
    return recordHistogram;
  }

  private boolean recordHistogram = true;

  public StorageManagerImpl(RuntimeService service, ServiceContextScope scope) {
    // choose the mm files, parallelism level and the floating point representation
    this.service = service;
    this.workspace = ServiceConfiguration.INSTANCE.getScratchDataDirectory("ktmp");
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
    if (ret == null) {
      throw new KlabIllegalStateException(
          "cannot create storage: no storage found for " + observation);
    }
    return ret;
  }

  @Override
  public Storage createStorage(Observation observation, Data.ShardingStrategy shardingStrategy) {
    return this.storage.computeIfAbsent(
        observation, urn -> createShard(observation, shardingStrategy, contextScope));
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
}
