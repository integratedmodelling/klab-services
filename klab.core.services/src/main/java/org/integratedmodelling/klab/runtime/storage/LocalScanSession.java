package org.integratedmodelling.klab.runtime.storage;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.integratedmodelling.klab.api.data.Storage;
import org.integratedmodelling.klab.api.data.StorageScan;

/** Task-local cursors over leased readers. Closing a session never closes storage-owned buffers. */
final class LocalScanSession<T extends Storage.Scanner> implements StorageScan.Session<T> {
  private final List<IndexedStorageReader> readers;
  private final List<T> scanners;
  private final Runnable release;
  private final ValueMediation.Kernel kernel;
  private final StorageScan.Description description;
  private final AtomicBoolean closed = new AtomicBoolean();

  LocalScanSession(StorageScan.Plan<T> plan, List<Storage.Shard> shards,
      List<IndexedStorageReader> readers, ConformantScan mapping, Runnable release) {
    this.readers = List.copyOf(readers);
    this.release = release;
    var description = plan.description();
    this.description = description;
    this.kernel = ValueMediation.kernel(description.conversion());
    var cursors = new ArrayList<T>();
    for (int i = 0; i < description.partitions().size(); i++) {
      List<StorageScan.SourceShard> physical;
      Storage.Shard shard;
      IndexedStorageReader reader;
      if (mapping == null) {
        physical = List.of(description.sources().get(i)); shard = shards.get(i); reader = readers.get(i);
      } else {
        var links = mapping.dependencies[i];
        var linked = new ArrayList<StorageScan.SourceShard>(links.length);
        for (int source : links) linked.add(description.sources().get(source));
        physical = List.copyOf(linked);
        shard = links.length == 1 ? shards.get(links[0]) : null;
        reader = new ConformantReader(mapping, i, this.readers, description.budget().blockValues());
      }
      var view = new StorageScan.View(description.partitions().get(i), description.requestedLayout().curve(),
          description.valueType(), description.targetSemantics(), description.slice(),
          physical, description.histogram());
      Storage.Scanner scanner = switch (description.valueType()) {
        case DOUBLE -> new DoubleCursor(shard, reader, view);
        case FLOAT -> new FloatCursor(shard, reader, view);
        case INTEGER -> new IntCursor(shard, reader, view);
        case LONG -> new LongCursor(shard, reader, view);
        case BOOLEAN -> new BooleanCursor(shard, reader, view);
        case KEYED -> throw new UnsupportedOperationException("KEYED scanner unavailable");
      };
      cursors.add(plan.scannerClass().cast(scanner));
    }
    scanners = List.copyOf(cursors);
  }

  @Override public List<T> scanners() { checkOpen(); return scanners; }
  @Override public StorageScan.Description description() { return description; }
  @Override public boolean isClosed() { return closed.get(); }
  private void checkOpen() { if (isClosed()) throw new IllegalStateException("Scan session is closed"); }

  @Override public void close() {
    if (!closed.compareAndSet(false, true)) return;
    RuntimeException failure = null;
    try {
      for (var reader : readers) {
        try { reader.close(); }
        catch (RuntimeException e) { if (failure == null) failure = e; else failure.addSuppressed(e); }
      }
    } finally { release.run(); }
    if (failure != null) throw failure;
  }

  private abstract class Cursor implements Storage.Scanner {
    final Storage.Shard shard;
    final IndexedStorageReader reader;
    final StorageScan.View view;
    long index;
    Cursor(Storage.Shard shard, IndexedStorageReader reader, StorageScan.View view) {
      this.shard = shard; this.reader = reader; this.view = view;
    }
    @Override public Storage.Shard shard() { checkOpen();
      if (shard == null) throw new UnsupportedOperationException("View has no single physical shard; use view().sources()");
      return shard; }
    @Override public StorageScan.View view() { checkOpen(); return view; }
    @Override public long size() { checkOpen(); return view.partition().size(); }
    @Override public void seek(long offset) {
      checkOpen();
      if (offset < 0 || offset > view.partition().size()) throw new IndexOutOfBoundsException("Scan offset " + offset);
      index = offset;
    }
    @Override public long position() { checkOpen(); return index; }
    @Override public boolean hasNext() { checkOpen(); return index < view.partition().size(); }
    @Override public long nextLong() { checkValue(); return index++; }
    @Override public boolean isValid() { checkValue(); return reader.isValid(index); }
    void checkValue() { if (!hasNext()) throw new NoSuchElementException("Scan exhausted"); }
    void rejectWrite() { checkOpen(); throw new IllegalStateException("Planned scans are read-only"); }
  }
  private final class DoubleCursor extends Cursor implements Storage.DoubleScanner {
    DoubleCursor(Storage.Shard s, IndexedStorageReader r, StorageScan.View v) { super(s,r,v); }
    public double peek() { checkValue(); return kernel.apply(reader.type() == Storage.Type.FLOAT ? reader.readFloat(index) : reader.readDouble(index)); }
    public double get() { double value = peek(); index++; return value; }
    public void add(double value) { rejectWrite(); }
  }
  private final class FloatCursor extends Cursor implements Storage.FloatScanner {
    FloatCursor(Storage.Shard s, IndexedStorageReader r, StorageScan.View v) { super(s,r,v); }
    public float peek() { checkValue(); return kernel.applyFloat(reader.type() == Storage.Type.DOUBLE ? reader.readDouble(index) : reader.readFloat(index)); }
    public float get() { float value = peek(); index++; return value; }
    public void add(float value) { rejectWrite(); }
  }
  private final class IntCursor extends Cursor implements Storage.IntScanner {
    IntCursor(Storage.Shard s, IndexedStorageReader r, StorageScan.View v) { super(s,r,v); }
    public int peek() { checkValue(); return reader.readInt(index); }
    public int get() { int value = peek(); index++; return value; }
    public void add(int value) { rejectWrite(); }
  }
  private final class LongCursor extends Cursor implements Storage.LongScanner {
    LongCursor(Storage.Shard s, IndexedStorageReader r, StorageScan.View v) { super(s,r,v); }
    public long peek() { checkValue(); return reader.readLong(index); }
    public long get() { long value = peek(); index++; return value; }
    public void add(long value) { rejectWrite(); }
  }
  private final class BooleanCursor extends Cursor implements Storage.BooleanScanner {
    BooleanCursor(Storage.Shard s, IndexedStorageReader r, StorageScan.View v) { super(s,r,v); }
    public boolean peek() { checkValue(); return reader.readBoolean(index); }
    public boolean get() { boolean value = peek(); index++; return value; }
    public void add(boolean value) { rejectWrite(); }
  }
}
