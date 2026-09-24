package org.integratedmodelling.klab.runtime.storage;

import java.lang.reflect.Proxy;
import java.util.*;
import org.integratedmodelling.klab.api.data.*;
import org.integratedmodelling.klab.api.geometry.Geometry;

/** Transactional event-grid writes, projected back by nearest cell center without modifying outsiders. */
final class EventWriteSession<T extends Storage.Scanner> implements StorageScan.Session<T> {
  interface Sink { void put(int partition, long index, Object value); }
  private final StorageScan.Session<T> baseline;
  private final List<T> scanners = new ArrayList<>();
  private final List<Map<Long, Object>> changes = new ArrayList<>();
  private final ScanMapping mapping;
  private final Sink sink;
  private final ConceptDataKey key;
  private boolean closed;

  EventWriteSession(StorageScan.Session<T> baseline, StorageScan.Request<T> request, Storage.Type type,
      Data.ShardingStrategy nativeLayout, Geometry nativeGeometry, List<StorageScan.Partition> nativePartitions,
      Sink sink, ConceptDataKey key, boolean acceptsLossy) {
    this.baseline = baseline; this.sink = sink; this.key = key;
    var descriptor = baseline.description();
    var sources = new ArrayList<StorageScan.SourceShard>();
    for (int p = 0; p < descriptor.partitions().size(); p++) {
      var partition = descriptor.partitions().get(p);
      sources.add(new StorageScan.SourceShard("event-view:" + p, partition.geometry(), partition.size(), p,
          request.slice().end(), new StorageScan.Layout(request.layout().curve(), 1, 0, 0, type)));
    }
    var scatter = new StorageScan.Request<>(request.slice(), StorageScan.Layout.of(nativeLayout),
        nativeGeometry.encode(), nativePartitions, null, Storage.Scanner.class,
        StorageScan.Access.READ_ONLY, StorageScan.Precision.LOSSLESS, StorageScan.Coverage.MISSING_OUTSIDE,
        StorageScan.Sampling.NEAREST, request.budget());
    String eventGeometry = request.geometry() == null ? nativeGeometry.encode() : request.geometry();
    boolean aligned = request.layout().curve() == nativeLayout.getCurve()
        && descriptor.partitions().size() == nativePartitions.size();
    for (int p = 0; aligned && p < nativePartitions.size(); p++)
      aligned = descriptor.partitions().get(p).geometry().equals(nativePartitions.get(p).geometry());
    mapping = aligned ? null : SpatialScan.plan(sources, scatter, eventGeometry, acceptsLossy);
    for (int p = 0; p < baseline.scanners().size(); p++) {
      var reader = baseline.scanners().get(p);
      var pending = new HashMap<Long, Object>(); changes.add(pending);
      Class<?> api = switch (type) {
        case DOUBLE -> Storage.DoubleScanner.class; case FLOAT -> Storage.FloatScanner.class;
        case INTEGER -> Storage.IntScanner.class; case LONG -> Storage.LongScanner.class;
        case BOOLEAN -> Storage.BooleanScanner.class; case KEYED -> Storage.KeyScanner.class;
      };
      long[] position = {0};
      @SuppressWarnings("unchecked") T writer = (T) Proxy.newProxyInstance(Storage.class.getClassLoader(),
          new Class<?>[] {api}, (proxy, method, args) -> {
            if (closed) throw new IllegalStateException("Event scanner is closed");
            switch (method.getName()) {
              case "add" -> {
                if (position[0] >= reader.size()) throw new NoSuchElementException();
                pending.put(position[0]++, key == null ? args[0] : key.code(args[0]));
                reader.nextLong(); return null;
              }
              case "seek" -> { position[0] = ((Number)args[0]).longValue(); }
              case "get", "next", "nextLong" -> position[0]++;
            }
            try { return method.invoke(reader, args); }
            catch (java.lang.reflect.InvocationTargetException e) { throw e.getCause(); }
          });
      scanners.add(writer);
    }
  }
  public StorageScan.Description description() { return baseline.description(); }
  public boolean isClosed() { return closed; }
  public void cancel() { closed = true; baseline.close(); changes.forEach(Map::clear); }
  public List<T> scanners() { return List.copyOf(scanners); }
  public void close() {
    if (closed) return;
    closed = true;
    try {
      if (mapping == null) {
        for (int p = 0; p < changes.size(); p++)
          for (var entry : changes.get(p).entrySet()) sink.put(p, entry.getKey(), entry.getValue());
      } else if (mapping instanceof ConformantScan exact) {
        long[] point = new long[exact.sources[0].shape.length];
        for (int p = 0; p < exact.targets.length; p++) for (long i = 0; i < exact.targets[p].size; i++) {
          exact.targets[p].decode(i, exact.targetCurve, point);
          int source = exact.directory.find(point);
          transfer(p, i, source, source < 0 ? -1 : exact.sources[source].encode(point, exact.sourceCurve));
        }
      } else if (mapping instanceof SpatialScan spatial) {
        long[] target = new long[2], point = new long[2]; double[] world = new double[2];
        for (int p = 0; p < spatial.target.targets.length; p++) {
          var box = spatial.target.targets[p];
          for (long i = 0; i < box.size; i++) {
            box.decode(i, spatial.target.targetCurve, target);
            spatial.world(target[0] + 0.5, target[1] + 0.5, world);
            point[0] = SpatialScan.floor(spatial.coordinate(world[0], 0));
            point[1] = SpatialScan.floor(spatial.coordinate(world[1], 1));
            int source = spatial.source.directory.find(point);
            transfer(p, i, source, source < 0 ? -1 : spatial.source.sources[source].encode(point, spatial.source.sourceCurve));
          }
        }
      }
    } finally { baseline.close(); }
  }
  private void transfer(int partition, long index, int source, long offset) {
    if (source >= 0 && changes.get(source).containsKey(offset))
      sink.put(partition, index, changes.get(source).get(offset));
  }
}
