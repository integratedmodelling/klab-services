package org.integratedmodelling.klab.runtime.storage;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import org.integratedmodelling.klab.api.data.Storage;
import org.ojalgo.structure.Access1D;
import org.ojalgo.structure.Mutate1D;

/** Bounded primitive payload transfer. Header validation, file ownership and durability belong to the caller. */
final class ShardBufferIO {
  static final int BLOCK_BYTES = 64 * 1024;

  private ShardBufferIO() {}

  static void write(Access1D<?> source, OutputStream output, Storage.Type type) throws IOException {
    var block = buffer(source.count(), type, ByteOrder.BIG_ENDIAN);
    for (long offset = 0; offset < source.count();) {
      int values = (int) Math.min(block.capacity() / type.size(), source.count() - offset);
      block.clear();
      switch (type) {
        // DataOutputStream's version-1 encoding canonicalizes NaNs; putFloat/putDouble alone do not.
        case DOUBLE -> {
          for (int i = 0; i < values; i++) block.putLong(Double.doubleToLongBits(source.doubleValue(offset + i)));
        }
        case FLOAT -> {
          for (int i = 0; i < values; i++) block.putInt(Float.floatToIntBits(source.floatValue(offset + i)));
        }
        case LONG -> {
          for (int i = 0; i < values; i++) block.putLong(source.longValue(offset + i));
        }
        case INTEGER, KEYED -> {
          for (int i = 0; i < values; i++) block.putInt(source.intValue(offset + i));
        }
        case BOOLEAN -> {
          for (int i = 0; i < values; i++) block.put(source.byteValue(offset + i));
        }
      }
      output.write(block.array(), 0, block.position());
      offset += values;
    }
  }

  static void read(Mutate1D target, DataInputStream input, Storage.Type type, ByteOrder order)
      throws IOException {
    var block = buffer(target.count(), type, order);
    for (long offset = 0; offset < target.count();) {
      int values = (int) Math.min(block.capacity() / type.size(), target.count() - offset);
      // Fill the complete block (or throw EOFException) before touching its destination values.
      input.readFully(block.array(), 0, values * type.size());
      block.clear();
      switch (type) {
        case DOUBLE -> {
          for (int i = 0; i < values; i++) target.set(offset + i, block.getDouble());
        }
        case FLOAT -> {
          for (int i = 0; i < values; i++) target.set(offset + i, block.getFloat());
        }
        case LONG -> {
          for (int i = 0; i < values; i++) target.set(offset + i, block.getLong());
        }
        case INTEGER, KEYED -> {
          for (int i = 0; i < values; i++) target.set(offset + i, block.getInt());
        }
        case BOOLEAN -> {
          for (int i = 0; i < values; i++) target.set(offset + i, block.get());
        }
      }
      offset += values;
    }
  }

  private static ByteBuffer buffer(long count, Storage.Type type, ByteOrder order) {
    int bytes = (int) Math.min(BLOCK_BYTES, Math.multiplyExact(count, type.size()));
    return ByteBuffer.allocate(bytes).order(order);
  }
}
