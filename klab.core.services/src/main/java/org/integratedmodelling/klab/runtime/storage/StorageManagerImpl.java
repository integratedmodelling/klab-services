package org.integratedmodelling.klab.runtime.storage;

import org.integratedmodelling.klab.api.collections.Triple;
import org.integratedmodelling.klab.api.data.Data;
import org.integratedmodelling.klab.api.data.Storage;
import org.integratedmodelling.klab.api.digitaltwin.StorageManager;
import org.integratedmodelling.klab.api.exceptions.KlabIOException;
import org.integratedmodelling.klab.api.exceptions.KlabIllegalArgumentException;
import org.integratedmodelling.klab.api.geometry.Geometry;
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
  private File workspace;
  private File floatBackupFile;
  private File doubleBackupFile;
  private File intBackupFile;
  private File longBackupFile;
  private File booleanBackupFile;
  private int histogramBinSize = 20;
  private final Map<String, Storage> storage = new HashMap<>();
  private AtomicLong nextId = new AtomicLong(0);

  public boolean isRecordHistogram() {
    return recordHistogram;
  }

  private boolean recordHistogram = true;

  private Parallelism parallelism = Parallelism.ONE;

  public StorageManagerImpl(KlabService service, ServiceContextScope scope) {
    // choose the mm files, parallelism level and the floating point representation
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

  private BufferArray.MappedFileFactory getFloatFactory() {
    if (this.floatMappedArrayFactory == null) {
      this.floatMappedArrayFactory = BufferArray.R032.newMapped(this.floatBackupFile);
    }
    return this.floatMappedArrayFactory;
  }

  private BufferArray.MappedFileFactory getLongFactory() {
    if (this.longMappedArrayFactory == null) {
      this.longMappedArrayFactory = BufferArray.Z032.newMapped(this.longBackupFile);
    }
    return this.longMappedArrayFactory;
  }

  private BufferArray.MappedFileFactory getDoubleFactory() {
    if (this.doubleMappedArrayFactory == null) {
      this.doubleMappedArrayFactory = BufferArray.R064.newMapped(this.doubleBackupFile);
    }
    return this.doubleMappedArrayFactory;
  }

  /*
  SHORT int. For now we use floats to encode longs
   */
  private BufferArray.MappedFileFactory getIntFactory() {
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
  private BufferArray.MappedFileFactory getBooleanFactory() {
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

  /**
   * Find out the representative interface of the passed buffer.
   *
   * @param buffer
   * @return
   */
  public static Class<? extends Storage.Buffer> bufferClass(Storage.Buffer buffer) {
    return switch (buffer) {
      case DoubleBufferImpl ignored -> Storage.DoubleBuffer.class;
      // TODO the rest!
      default -> throw new KlabIllegalArgumentException("not a recognized buffer type");
    };
  }

  public BufferArray getIntBuffer(long sliceSize) {
    return getIntFactory().make(sliceSize);
  }

  public BufferArray getLongBuffer(long sliceSize) {
    return getLongFactory().make(sliceSize);
  }

  public BufferArray getFloatBuffer(long sliceSize) {
    return getFloatFactory().make(sliceSize);
  }

  public BufferArray getBooleanBuffer(long sliceSize) {
    return getBooleanFactory().make(sliceSize);
  }

  public BufferArray getDoubleBuffer(long sliceSize) {
    return getDoubleFactory().make(sliceSize);
  }

  public int getHistogramBinSize() {
    return histogramBinSize;
  }

  public Storage getStorage(Observation observation) {
    return getStorage(
        observation, Utils.Annotations.findAnnotation("storage", observation.getAnnotations()));
  }

  public static Triple<Integer, Data.SpaceFillingCurve, Storage.Type> getOptions(
      ServiceContextScope contextScope, Annotation storageAnnotation, Observation observation) {
    // defaults
    var splits = contextScope.getParallelism().getAsInt();
    var fillingCurve = Data.SpaceFillingCurve.defaultCurve(observation.getGeometry());
    var storageType =
        contextScope
            .getService(RuntimeService.class)
            .capabilities(contextScope)
            .getDefaultStorageType();

    /*
     * Find specs if any. May not be honored.
     */
    if (storageAnnotation != null) {
      if (storageAnnotation.containsKey("fillcurve")) {
        try {
          fillingCurve =
              Data.SpaceFillingCurve.valueOf(
                  storageAnnotation.get("fillcurve").toString().toUpperCase());
        } catch (Throwable t) {
          contextScope.error(
              "Wrong fill curve specification: "
                  + storageAnnotation.get("fillcurve")
                  + " in "
                  + observation);
        }
      }

      if (storageAnnotation.containsKey("type")) {
        try {
          storageType =
              Storage.Type.valueOf(storageAnnotation.get("type").toString().toUpperCase());
        } catch (Throwable t) {
          contextScope.error(
              "Wrong storage type specification: "
                  + storageAnnotation.get("type")
                  + " in "
                  + observation);
        }
      }

      if (storageAnnotation.containsKey("splits")) {
        splits = contextScope.getSplits(storageAnnotation.get("splits", Integer.class));
      }
    }

    return Triple.of(splits, fillingCurve, storageType);
  }

  @Override
  public Storage getStorage(Observation observation, Annotation storageAnnotation) {
    final var options = getOptions(contextScope, storageAnnotation, observation);
    return this.storage.computeIfAbsent(
        observation.getUrn(),
        urn ->
            new StorageImpl(
                observation,
                options.getThird(),
                options.getSecond(),
                options.getFirst(),
                this,
                contextScope));
  }

  //  @SuppressWarnings("unchecked")
  //  @Override
  //  private Storage getOrCreateStorage(Observation observation) {
  //
  //    Class<?> sClass = storageClass;
  //    if (storageClass == Storage.class) {
  //      sClass =
  //          switch (observation.getObservable().getArtifactType()) {
  //            //            case BOOLEAN -> BooleanStorage.class;
  //            case NUMBER -> /* TODO use config to choose between double and float */
  //                DoubleStorage.class;
  //            //            case TEXT, CONCEPT -> KeyedStorage.class;
  //            default ->
  //                throw new KlabIllegalStateException(
  //                    "scalar mapping to type "
  //                        + observation.getObservable().getArtifactType()
  //                        + " not supported");
  //          };
  //    }
  //
  //    T ret = getExistingStorage(observation, (Class<T>) sClass);
  //
  //    if (ret == null) {
  //      if (DoubleStorage.class.isAssignableFrom(sClass)) {
  //        ret = (T) new DoubleStorage(observation, this, contextScope);
  //      } /*else if (LongStorage.class.isAssignableFrom(sClass)) {
  //          ret = (T) new LongStorage(observation, this, contextScope);
  //        } else if (FloatStorage.class.isAssignableFrom(sClass)) {
  //          ret = (T) new FloatStorage(observation, this, contextScope);
  //        } else if (IntStorage.class.isAssignableFrom(sClass)) {
  //          ret = (T) new IntStorage(observation, this, contextScope);
  //        } else if (BooleanStorage.class.isAssignableFrom(sClass)) {
  //          ret = (T) new BooleanStorage(observation, this, contextScope);
  //        } else if (KeyedStorage.class.isAssignableFrom(sClass)) {
  //          ret = (T) new KeyedStorage(observation, this, contextScope);
  //        }*/
  //    }
  //
  //    if (ret != null) {
  //      // TODO load any pre-existing state
  //      storage.put(observation.getUrn(), ret);
  //      return ret;
  //    }
  //
  //    throw new KlabUnimplementedException(
  //        "cannot create storage of class " + sClass.getCanonicalName());
  //  }

  //  @Override
  public <T extends Storage> T promoteStorage(
      Observation observation, Storage existingStorage, Class<T> storageClass) {

    if (existingStorage == null) {
      //      return getOrCreateStorage(observation, storageClass);
    } else if (storageClass.isAssignableFrom(existingStorage.getClass())) {
      return (T) existingStorage;
    }

    // TODO create a casting delegate or throw an exception

    return null;
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
