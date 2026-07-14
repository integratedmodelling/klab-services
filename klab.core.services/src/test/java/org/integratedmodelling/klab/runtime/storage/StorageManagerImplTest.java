package org.integratedmodelling.klab.runtime.storage;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.file.Files;
import java.nio.file.Path;
import org.integratedmodelling.klab.api.data.Storage;
import org.ojalgo.array.BufferArray;
import org.ojalgo.type.math.MathType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StorageManagerImplTest {

  @Test
  void integerAndLongBuffersKeepTheirDeclaredWidth() {
    assertEquals(
        MathType.Z032, StorageManagerImpl.bufferFactory(Storage.Type.INTEGER).getMathType());
    assertEquals(MathType.Z032, StorageManagerImpl.bufferFactory(Storage.Type.KEYED).getMathType());
    assertEquals(MathType.Z064, StorageManagerImpl.bufferFactory(Storage.Type.LONG).getMathType());
  }

  @Test
  void booleanBuffersUseOneByteValues() {
    assertEquals(MathType.Z008, StorageManagerImpl.bufferFactory(Storage.Type.BOOLEAN).getMathType());
  }

  @Test
  void durableCopyRoundTripsLongsWithoutLeavingTheFileMapped(@TempDir Path directory)
      throws Exception {
    var source = (BufferArray) BufferArray.Z064.make(3);
    var restored = (BufferArray) BufferArray.Z064.make(3);
    var file = directory.resolve("shard.dat").toFile();
    try {
      source.set(0, Long.MIN_VALUE);
      source.set(1, ((long) Integer.MAX_VALUE) + 42);
      source.set(2, Long.MAX_VALUE);

      StorageManagerImpl.writeBufferArray(source, file, Storage.Type.LONG);
      StorageManagerImpl.readBufferArray(restored, file, Storage.Type.LONG);

      assertEquals(Long.MIN_VALUE, restored.longValue(0));
      assertEquals(((long) Integer.MAX_VALUE) + 42, restored.longValue(1));
      assertEquals(Long.MAX_VALUE, restored.longValue(2));
      Files.delete(file.toPath());
    } finally {
      source.close();
      restored.close();
    }
  }
}
