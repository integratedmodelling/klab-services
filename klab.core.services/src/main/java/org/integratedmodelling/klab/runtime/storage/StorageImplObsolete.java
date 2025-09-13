//package org.integratedmodelling.klab.runtime.storage;
//
//import java.util.*;
//import org.integratedmodelling.common.knowledge.GeometryRepository;
//import org.integratedmodelling.klab.api.Klab;
//import org.integratedmodelling.klab.api.data.Data;
//import org.integratedmodelling.klab.api.data.Histogram;
//import org.integratedmodelling.klab.api.data.RuntimeAsset;
//import org.integratedmodelling.klab.api.data.Storage;
//import org.integratedmodelling.klab.api.digitaltwin.Scheduler;
//import org.integratedmodelling.klab.api.exceptions.KlabIllegalStateException;
//import org.integratedmodelling.klab.api.exceptions.KlabUnimplementedException;
//import org.integratedmodelling.klab.api.geometry.Geometry;
//import org.integratedmodelling.klab.api.knowledge.observation.Observation;
//import org.integratedmodelling.klab.api.knowledge.observation.scale.time.Time;
//import org.integratedmodelling.klab.api.scope.Persistence;
//import org.integratedmodelling.klab.services.scopes.ServiceContextScope;
//import org.integratedmodelling.klab.utilities.Utils;
//
///**
// * Abstract storage class providing geometry and buffer indexing, histograms, merging and splitting.
// * TODO remove after finishing the other implementation.
// */
//public class StorageImplObsolete implements Storage {
//
//  @Deprecated protected final Geometry geometry;
//  @Deprecated protected Data.FillCurve fillCurve;
//  @Deprecated protected int splits;
//
//  protected final Type type;
//  protected final StorageManagerImpl stateStorage;
//  protected final Observation observation;
//  protected final ServiceContextScope contextScope;
//  protected Persistence persistence;
//  private long transientId = Klab.getNextId();
//
//  /*
//   * Buffer storage along slowest-varying dimensions. All dimensions except the
//   * last (space) have linear indexing along a "start" number. The final version of this
//   * should have a Pair<NavigableMap, List<AbstractBuffer>> argument, which makes it compatible
//   * with multiple non-spatial dimensions. But that's unlikely to be useful soon and makes the code
//   * very complex, so we just assume start time as the first index and let the implementation provide
//   * as many buffers as needed for the second, which is assumed to be space.
//   *
//   */
//  private NavigableMap<Long, List<ShardImpl>> buffers = new TreeMap<>();
//
//  protected StorageImplObsolete(
//      Observation observation,
//      Type type,
//      Data.FillCurve fillingCurve,
//      int splits,
//      StorageManagerImpl stateStorage,
//      ServiceContextScope contextScope) {
//    this.type = type;
//    this.stateStorage = stateStorage;
//    this.observation = observation;
//    this.geometry = observation.getGeometry();
//    this.contextScope = contextScope;
//    this.fillCurve = fillingCurve;
//    this.splits = contextScope.getSplits(splits);
//  }
//
//  /**
//   * Used after the buffers have been created.
//   *
//   * @param geometry
//   * @return
//   */
//  public List<Shard> buffers(Geometry geometry, Time eventTime) {
//    return buffersCovering(geometry, eventTime, this.fillCurve, this.type);
//  }
//
//  //  @Override
//  //  public List<? extends Buffer> buffers(
//  //      Geometry geometry, Time eventTime, Annotation storageAnnotation) {
//  //    return List.of();
//  //  }
//
//  //  @Override
//  public <T extends Shard> List<T> buffers(
//      Geometry geometry, Time eventTime, Class<T> bufferClass) {
//
//    var nVaryingDimensions = geometry.getDimensions().stream().filter(d -> d.size() > 1).count();
//    if (nVaryingDimensions > 1) {
//      throw new KlabIllegalStateException(
//          "Cannot create or retrieve buffers for more than one varying geometry extent at a time");
//    }
//
//    return (List<T>) buffersCovering(geometry, eventTime, this.fillCurve, this.type);
//  }
//
//  /*
//  The storage doesn't have a fill curve until the first buffer request.
//   */
//  //  @Override
//  public Data.FillCurve spaceFillCurve() {
//    return fillCurve;
//  }
//
//  /**
//   * Retrieve the merged histogram. TODO we should cache if the owning state is finalized.
//   *
//   * @return
//   */
//  public com.dynatrace.dynahist.Histogram histogram() {
//
//    var allBuffers = allBuffers();
//    if (allBuffers.size() == 1) {
//      return ((ShardImpl) allBuffers.getFirst()).histogram;
//    } else if (allBuffers.size() > 1) {
//      com.dynatrace.dynahist.Histogram ret = null;
//      var first = ((ShardImpl) allBuffers.getFirst()).histogram;
//      if (first != null) {
//        ret = com.dynatrace.dynahist.Histogram.createDynamic(first.getLayout());
//        for (var buffer : allBuffers) {
//          if (((ShardImpl) buffer).histogram != null) {
//            ret.addHistogram(((ShardImpl) buffer).histogram);
//          }
//        }
//      }
//    }
//    return null;
//  }
//
//  //  @Override
//  public Persistence persistence() {
//    return persistence;
//  }
//
//  protected List<Shard> buffersCovering(
//      Geometry geometry, Time eventTime, Data.FillCurve fillingCurve, Type dataType) {
//
//    var scale = GeometryRepository.INSTANCE.scale(geometry);
//    var time = eventTime == null ? scale.getTime() : eventTime;
//    if (time.size() != 1) {
//      throw new KlabUnimplementedException(
//          "Multiple time steps for a buffer request during contextualization");
//    }
//
//    long timeStart = time.is(Time.Type.INITIALIZATION) ? 0 : time.getStart().getMilliseconds();
//    return buffers
//        .computeIfAbsent(
//            timeStart, k -> new ArrayList<>(createBuffers(geometry, observation, timeStart)))
//        .stream()
//        .map(b -> adaptBuffer(b, fillingCurve))
//        .toList();
//  }
//
//  private List<ShardImpl> createBuffers(
//      Geometry geometry, Observation observation, long timestamp) {
//
//    var ret = new ArrayList<ShardImpl>();
//    long[] splitSizes = new long[splits];
//    long size = geometry.size() / splits;
//    long remd = geometry.size() % splits;
//    Arrays.fill(splitSizes, size);
//    splitSizes[splits - 1] += remd;
//
//    long offset = 0L;
//    for (long bs : splitSizes) {
//      ret.add(
//          switch (type) {
//            //            case BOXING -> null;
//            case DOUBLE -> null;
//            //                new DoubleBufferImpl(geometry, observation, this, bs, fillCurve,
//            // offset, timestamp);
//            case FLOAT -> null;
//            case INTEGER -> null;
//            case LONG -> null;
//            case KEYED -> null;
//            case BOOLEAN -> null;
//          });
//      offset += bs;
//    }
//
//    return ret;
//  }
//
//  //  @Override
//  public long getTransientId() {
//    return transientId;
//  }
//
//  //    @Override
//  public RuntimeAsset.Type classify() {
//    return null;
//  }
//
//  /** DO NOT CALL - reserved for serialization purposes */
//  public void setTransientId(long transientId) {
//    this.transientId = transientId;
//  }
//
//  private Shard adaptBuffer(ShardImpl b, Data.FillCurve fillingCurve) {
//    // TODO !
////    if (b.getFillingCurve() != fillingCurve) {
////      // TODO
////    }
//    return b;
//  }
//
//  //  @Override
//  public List<Shard> allBuffers() {
//    var ret = new ArrayList<Shard>();
//    buffers.values().forEach(ret::addAll);
//    return ret;
//  }
//
//  //  @Override
//  public Type getNativeType() {
//    return this.type;
//  }
//
//  //  @Override
//  public Geometry getGeometry() {
//    return this.geometry;
//  }
//
//  @Override
//  public List<Shard> getNativeShards(Scheduler.Event event) {
//    return List.of();
//  }
//
//  @Override
//  public <T extends Scanner> List<T> scan(
//      Scheduler.Event locator,
//      Data.ShardingStrategy request,
//      Class<T> scannerClass,
//      boolean readOnly) {
//    return List.of();
//  }
//
//  @Override
//  public Histogram getHistogram() {
//    return Utils.Data.adaptHistogram(histogram());
//  }
//
//  //  @Override
//  public long getId() {
//    return 0;
//  }
//
//  public Data.FillCurve getFillCurve() {
//    return fillCurve;
//  }
//}
