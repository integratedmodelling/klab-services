package org.integratedmodelling.klab.runtime.storage;

import java.io.*;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import org.integratedmodelling.klab.api.data.Data;
import org.integratedmodelling.klab.api.data.Storage;
import org.integratedmodelling.klab.api.digitaltwin.DigitalTwin;
import org.integratedmodelling.klab.api.digitaltwin.StorageManager;
import org.integratedmodelling.klab.api.exceptions.KlabIOException;
import org.integratedmodelling.klab.api.exceptions.KlabIllegalStateException;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.scope.ServiceScope;
import org.integratedmodelling.klab.api.services.RuntimeService;
import org.integratedmodelling.klab.api.services.runtime.objects.ContextInfo;
import org.integratedmodelling.klab.configuration.ServiceConfiguration;
import org.integratedmodelling.klab.services.base.BaseService;
import org.integratedmodelling.klab.services.scopes.ServiceContextScope;
import org.integratedmodelling.klab.utilities.Utils;
import org.ojalgo.array.BufferArray;

/**
 * There is one separate <code>StorageScope</code> in each {@link ContextScope}. It's built on
 * demand based on the configuration available from the context data, including whatever user-level
 * configuration was passed, and stored in the context data at the runtime side. The StorageScope is
 * managed by the StorageManager, which is a singleton used by the DigitalTwin.
 */
public class StorageManagerImpl implements StorageManager {

  private static final String NEXT_ID_PROPERTY = "storage.mmap.nextid";
  private static final int STORAGE_MAGIC = 0x4B4C4142; // "KLAB"
  private static final int STORAGE_VERSION = 1;
  private static final int STORAGE_HEADER_SIZE = Integer.BYTES * 4 + Long.BYTES;

  private final ServiceContextScope contextScope;
  private final File propertyFile;
  private final File workspace;
  private final List<File> mappedBufferFiles = Collections.synchronizedList(new ArrayList<>());
  private final int histogramBinSize = 20;
  private final Map<Long, Storage> storage = new ConcurrentHashMap<>();
  private final AtomicLong nextId = new AtomicLong(0);
  private final ExecutorService shardMaintenance = Executors.newSingleThreadExecutor();
  private final Map<String, Future<Boolean>> pendingPersistence = new ConcurrentHashMap<>();
  private final File persistentSpace;

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
    this.workspace = ServiceConfiguration.INSTANCE.getScratchDataDirectory(scope.getId());
    this.persistentSpace =
        BaseService.getConfigurationSubdirectory(
            ((BaseService) service).startupOptions(), "storage");
    if (!this.workspace.exists()) {
      this.workspace.mkdirs();
    }
    this.contextScope = scope;
    this.propertyFile =
        ServiceConfiguration.INSTANCE.getFileWithTemplate(
            "storage.properties", NEXT_ID_PROPERTY + "=0");
    // Descriptors are reconstructed on demand; primitive buffers remain unloaded until scanned.
    readConfiguration();
  }

  private synchronized File nextBufferFile(String prefix) {
    var ret = new File(this.workspace, prefix + nextBufferId() + ".bin");
    mappedBufferFiles.add(ret);
    return ret;
  }

  public void close() {
    storage.values().forEach(s -> s.close(null));
    storage.clear();

    flushPendingPersistence();
    shardMaintenance.shutdown();
    try {
      if (!shardMaintenance.awaitTermination(30, TimeUnit.SECONDS)) {
        shardMaintenance.shutdownNow();
      }
    } catch (InterruptedException e) {
      shardMaintenance.shutdownNow();
      Thread.currentThread().interrupt();
    }

    synchronized (mappedBufferFiles) {
      // TODO OJAlgo does not expose deterministic unmapping through BufferArray. On Windows a JVM
      // may therefore keep these scratch files locked until GC; a cache/offload implementation
      // should own the FileChannel/MappedByteBuffer lifecycle and explicitly release mappings.
      for (var file : mappedBufferFiles) {
        Utils.Files.deleteQuietly(file);
      }
      mappedBufferFiles.clear();
    }
  }

  public synchronized BufferArray getIntBuffer(long sliceSize) {
    return createMappedBuffer(nextBufferFile("istorage-"), sliceSize, Storage.Type.INTEGER);
  }

  public synchronized BufferArray getLongBuffer(long sliceSize) {
    return createMappedBuffer(nextBufferFile("lstorage-"), sliceSize, Storage.Type.LONG);
  }

  public synchronized BufferArray getFloatBuffer(long sliceSize) {
    return BufferArray.R032.newMapped(nextBufferFile("fstorage-")).make(sliceSize);
  }

  public synchronized BufferArray getBooleanBuffer(long sliceSize) {
    return createMappedBuffer(nextBufferFile("bstorage-"), sliceSize, Storage.Type.BOOLEAN);
  }

  public synchronized BufferArray getDoubleBuffer(long sliceSize) {
    return BufferArray.R064.newMapped(nextBufferFile("dstorage-")).make(sliceSize);
  }

  static BufferArray createMappedBuffer(File file, long size, Storage.Type type) {
    return bufferFactory(type).newMapped(file).make(size);
  }

  static BufferArray.Factory bufferFactory(Storage.Type type) {
    return switch (type) {
      case DOUBLE -> BufferArray.R064;
      case FLOAT -> BufferArray.R032;
      case INTEGER, KEYED -> BufferArray.Z032;
      case LONG -> BufferArray.Z064;
      case BOOLEAN -> BufferArray.Z008;
    };
  }

  public int getHistogramBinSize() {
    return histogramBinSize;
  }

  public Storage getStorage(Observation observation) {

    var ret = this.storage.get(observation.getId());
    if (ret == null && observation.getId() > 0) {
      ret = this.storage.computeIfAbsent(observation.getId(), id -> reconstructStorage(observation));
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
    var restored = new StorageImpl(observation, shardingStrategy, contextScope, this);
    // The graph, not the presence of an unrelated .dat file, defines existing storage.
    return restored.allShards().isEmpty() ? null : restored;
  }

  @Override
  public void deleteStorage(DigitalTwin.Configuration digitalTwin, ServiceScope serviceScope) {
    close();
    Utils.Files.deleteQuietly(getContextStorageDirectory());
  }

  @Override
  public Storage createStorage(Observation observation) {
    var cd = observation.getContextualizationData();
    if (cd == null || cd.getNativeShardingStrategy() == null) {
      throw new KlabIllegalStateException(
          "Cannot create storage for "
              + observation
              + ": contextualization data or native sharding strategy is not set");
    }
    return this.storage.computeIfAbsent(
        observation.getId(),
        obs -> createShard(observation, cd.getNativeShardingStrategy(), contextScope));
  }

  @Override
  public <T extends Storage.Scanner> T getTemporaryScanner(
      Geometry geometry, Data.FillCurve fillCurve, Class<T> scannerClass) {
    // TODO
    return null;
  }

  /**
   * Finalize a temporary storage by moving it to a permanent ID when the observation is committed
   * to the knowledge graph. MUST be called upon commit. Also sets the observation up for
   * persistence.
   */
  @Override
  public boolean finalizeStorage(long temporaryId, long finalizedId) {
    var storage = this.storage.get(temporaryId);
    if (storage == null) {
      return false;
    }
    this.storage.put(finalizedId, storage);
    this.storage.remove(temporaryId);
    // TODO shard persistence should start here
    return true;
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
    close();
    Utils.Files.deleteQuietly(getContextStorageDirectory());
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
      writeBufferArray(array, file, type);
      return true;
    } catch (Exception e) {
      contextScope.error("Error saving buffer array: " + e.getMessage());
      return false;
    }
  }

  static void writeBufferArray(BufferArray array, File file, Storage.Type type) throws IOException {
    try (var fileOutput = new FileOutputStream(file);
        var bufferedOutput = new BufferedOutputStream(fileOutput);
        var output = new DataOutputStream(bufferedOutput)) {
      int elementSize = type.size();
      Math.multiplyExact(array.count(), elementSize);
      output.writeInt(STORAGE_MAGIC);
      output.writeInt(STORAGE_VERSION);
      output.writeInt(type.ordinal());
      output.writeInt(elementSize);
      output.writeLong(array.count());

      for (long i = 0; i < array.count(); i++) {
        switch (type) {
          case FLOAT:
            output.writeFloat(array.floatValue(i));
            break;
          case DOUBLE:
            output.writeDouble(array.doubleValue(i));
            break;
          case LONG:
            output.writeLong(array.longValue(i));
            break;
          case INTEGER, KEYED:
            output.writeInt(array.intValue(i));
            break;
          case BOOLEAN:
            output.writeByte(array.byteValue(i));
            break;
        }
      }
      output.flush();
      fileOutput.getFD().sync();
    }
  }

  public boolean loadBufferArray(BufferArray array, File file, Storage.Type type) {
    try {
      readBufferArray(array, file, type);
      return true;
    } catch (Exception e) {
      contextScope.error("Error loading buffer array: " + e.getMessage());
      return false;
    }
  }

  static void readBufferArray(BufferArray array, File file, Storage.Type type) throws IOException {
    try (var fileInput = new FileInputStream(file);
        var bufferedInput = new BufferedInputStream(fileInput);
        var input = new DataInputStream(bufferedInput)) {
      long expectedPayloadSize = Math.multiplyExact(array.count(), type.size());
      long fileSize = file.length();
      bufferedInput.mark(STORAGE_HEADER_SIZE);
      boolean legacy = fileSize < STORAGE_HEADER_SIZE || input.readInt() != STORAGE_MAGIC;
      if (!legacy) {
        int version = input.readInt();
        int storedType = input.readInt();
        int storedElementSize = input.readInt();
        long storedCount = input.readLong();
        if (version != STORAGE_VERSION
            || storedType != type.ordinal()
            || storedElementSize != type.size()
            || storedCount != array.count()
            || fileSize != STORAGE_HEADER_SIZE + expectedPayloadSize) {
          throw new IOException("Invalid or incompatible shard storage header in " + file);
        }
      } else {
        // Backward-compatible reader for the original headerless, native-endian format.
        if (fileSize != expectedPayloadSize) {
          throw new IOException("Invalid legacy shard storage length in " + file);
        }
        bufferedInput.reset();
      }

      for (long i = 0; i < array.count(); i++) {
        switch (type) {
          case FLOAT:
            array.set(
                i,
                legacy
                    ? Float.intBitsToFloat(readLegacyInt(input))
                    : input.readFloat());
            break;
          case DOUBLE:
            array.set(
                i,
                legacy
                    ? Double.longBitsToDouble(readLegacyLong(input))
                    : input.readDouble());
            break;
          case LONG:
            array.set(i, legacy ? readLegacyLong(input) : input.readLong());
            break;
          case INTEGER, KEYED:
            array.set(i, legacy ? readLegacyInt(input) : input.readInt());
            break;
          case BOOLEAN:
            array.set(i, input.readByte());
            break;
        }
      }
    }
  }

  private static int readLegacyInt(DataInputStream input) throws IOException {
    int value = input.readInt();
    return ByteOrder.nativeOrder() == ByteOrder.LITTLE_ENDIAN
        ? Integer.reverseBytes(value)
        : value;
  }

  private static long readLegacyLong(DataInputStream input) throws IOException {
    long value = input.readLong();
    return ByteOrder.nativeOrder() == ByteOrder.LITTLE_ENDIAN ? Long.reverseBytes(value) : value;
  }

  // TODO check that this is executed transparently and only after the obs is finalized
  // TODO also clean up the persisted storage for orphan shards on startup and periodically
  public void persistShard(Storage.Scanner scanner) {
    if (scanner instanceof StorageImpl.BaseScanner baseScanner) {
      final var shard = baseScanner.shard();
      final var storagePath = getContextStorageDirectory();
      storagePath.mkdirs();
      final var outFile = new File(storagePath + File.separator + shard.getUrn() + ".dat");
      final var temporaryFile =
          new File(storagePath, shard.getUrn() + ".dat.tmp-" + UUID.randomUUID());
      var future =
          shardMaintenance.submit(
              () -> {
                if (!saveBufferArray(baseScanner.data, temporaryFile, shard.getNativeType())) {
                  Utils.Files.deleteQuietly(temporaryFile);
                  return false;
                }
                try {
                  try {
                    Files.move(
                        temporaryFile.toPath(),
                        outFile.toPath(),
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
                  } catch (java.nio.file.AtomicMoveNotSupportedException e) {
                    Files.move(
                        temporaryFile.toPath(),
                        outFile.toPath(),
                        StandardCopyOption.REPLACE_EXISTING);
                  }
                  return true;
                } catch (IOException e) {
                  contextScope.error("Error committing shard storage: " + e.getMessage());
                  Utils.Files.deleteQuietly(temporaryFile);
                  return false;
                }
              });
      pendingPersistence.put(shard.getUrn(), future);
    }
  }

  public boolean hasExistingData() {
    var directory = getContextStorageDirectory();
    var files = directory.listFiles((dir, name) -> name.endsWith(".dat"));
    return files != null && files.length > 0;
  }

  public void flushPendingPersistence() {
    while (!pendingPersistence.isEmpty()) {
      var pending = new ArrayList<>(pendingPersistence.entrySet());
      for (var entry : pending) {
        try {
          if (!entry.getValue().get()) {
            throw new KlabIOException("Cannot persist shard " + entry.getKey());
          }
          pendingPersistence.remove(entry.getKey(), entry.getValue());
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          throw new KlabIOException(e);
        } catch (ExecutionException e) {
          throw new KlabIOException(e.getCause());
        }
      }
    }
  }

  private File getContextStorageDirectory() {
    return new File(persistentSpace, contextScope.getId());
  }

  public File getStorageFile(Storage.Shard shard) {
    return new File(getContextStorageDirectory(), shard.getUrn() + ".dat");
  }
}
