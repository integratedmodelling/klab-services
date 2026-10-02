package org.integratedmodelling.klab.runtime.storage;

import java.io.*;
import org.integratedmodelling.klab.api.data.Storage;
import org.ojalgo.array.BufferArray;

/**
 * Independent version-1 scalar reference, retained from StorageManagerImpl at 721f37365.
 * Used for byte-compatibility checks and same-JVM benchmarks, never by production code.
 */
final class ScalarShardIOReference {
  static final int MAGIC = 0x4B4C4142;
  static final int HEADER_BYTES = 24;

  private ScalarShardIOReference() {}

  static void write(BufferArray array, File file, Storage.Type type) throws IOException {
    try (var stream = new FileOutputStream(file);
         var buffered = new BufferedOutputStream(stream);
         var output = new DataOutputStream(buffered)) {
      Math.multiplyExact(array.count(), type.size());
      output.writeInt(MAGIC);
      output.writeInt(1);
      output.writeInt(type.ordinal());
      output.writeInt(type.size());
      output.writeLong(array.count());
      for (long i = 0; i < array.count(); i++) {
        switch (type) {
          case FLOAT -> output.writeFloat(array.floatValue(i));
          case DOUBLE -> output.writeDouble(array.doubleValue(i));
          case LONG -> output.writeLong(array.longValue(i));
          case INTEGER, KEYED -> output.writeInt(array.intValue(i));
          case BOOLEAN -> output.writeByte(array.byteValue(i));
        }
      }
      output.flush();
      stream.getFD().sync();
    }
  }

  static void read(BufferArray array, File file, Storage.Type type) throws IOException {
    try (var stream = new FileInputStream(file);
         var buffered = new BufferedInputStream(stream);
         var input = new DataInputStream(buffered)) {
      if (input.readInt() != MAGIC || input.readInt() != 1
          || input.readInt() != type.ordinal() || input.readInt() != type.size()
          || input.readLong() != array.count()
          || file.length() != HEADER_BYTES + Math.multiplyExact(array.count(), type.size()))
        throw new IOException("Invalid reference shard");
      for (long i = 0; i < array.count(); i++) {
        switch (type) {
          case FLOAT -> array.set(i, input.readFloat());
          case DOUBLE -> array.set(i, input.readDouble());
          case LONG -> array.set(i, input.readLong());
          case INTEGER, KEYED -> array.set(i, input.readInt());
          case BOOLEAN -> array.set(i, input.readByte());
        }
      }
    }
  }
}
