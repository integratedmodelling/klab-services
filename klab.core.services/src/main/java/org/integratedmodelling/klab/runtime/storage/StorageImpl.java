package org.integratedmodelling.klab.runtime.storage;

import java.util.*;
import org.integratedmodelling.common.knowledge.GeometryRepository;
import org.integratedmodelling.klab.api.Klab;
import org.integratedmodelling.klab.api.data.Data;
import org.integratedmodelling.klab.api.data.Histogram;
import org.integratedmodelling.klab.api.data.Storage;
import org.integratedmodelling.klab.api.digitaltwin.Scheduler;
import org.integratedmodelling.klab.api.exceptions.KlabIllegalStateException;
import org.integratedmodelling.klab.api.exceptions.KlabUnimplementedException;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.knowledge.observation.scale.time.Time;
import org.integratedmodelling.klab.api.lang.Annotation;
import org.integratedmodelling.klab.api.scope.Persistence;
import org.integratedmodelling.klab.services.scopes.ServiceContextScope;
import org.integratedmodelling.klab.utilities.Utils;

/**
 * Abstract storage class providing geometry and buffer indexing, histograms, merging and splitting.
 */
public class StorageImpl implements Storage {

  protected final Type type;
  protected final StorageManagerImpl stateStorage;
  protected final Observation observation;
  protected final Geometry geometry;
  protected final ServiceContextScope contextScope;
  protected Persistence persistence;
  protected Data.SpaceFillingCurve spaceFillingCurve;
  protected int splits;
  private long transientId = Klab.getNextId();

  /*
   * Buffer storage along slowest-varying dimensions. All dimensions except the
   * last (space) have linear indexing along a "start" number. The final version of this
   * should have a Pair<NavigableMap, List<AbstractBuffer>> argument, which makes it compatible
   * with multiple non-spatial dimensions. But that's unlikely to be useful soon and makes the code
   * very complex, so we just assume start time as the first index and let the implementation provide
   * as many buffers as needed for the second, which is assumed to be space.
   *
   */
  private NavigableMap<Long, List<BufferImpl>> buffers = new TreeMap<>();

  protected StorageImpl(
      Observation observation,
      Type type,
      Data.SpaceFillingCurve fillingCurve,
      int splits,
      StorageManagerImpl stateStorage,
      ServiceContextScope contextScope) {
    this.type = type;
    this.stateStorage = stateStorage;
    this.observation = observation;
    this.geometry = observation.getGeometry();
    this.contextScope = contextScope;
    this.spaceFillingCurve = fillingCurve;
    this.splits = contextScope.getSplits(splits);
  }

  /**
   * Used after the buffers have been created.
   *
   * @param geometry
   * @return
   */
  public List<Buffer> buffers(Geometry geometry, Time eventTime) {
    return buffersCovering(geometry, eventTime, this.spaceFillingCurve, this.type);
  }

  @Override
  public List<? extends Buffer> buffers(
      Geometry geometry, Time eventTime, Annotation storageAnnotation) {
    return List.of();
  }

  @Override
  public <T extends Buffer> List<T> buffers(
      Geometry geometry, Time eventTime, Class<T> bufferClass) {

    var nVaryingDimensions = geometry.getDimensions().stream().filter(d -> d.size() > 1).count();
    if (nVaryingDimensions > 1) {
      throw new KlabIllegalStateException(
          "Cannot create or retrieve buffers for more than one varying geometry extent at a time");
    }

    return (List<T>) buffersCovering(geometry, eventTime, this.spaceFillingCurve, this.type);
  }

  /*
  The storage doesn't have a fill curve until the first buffer request.
   */
  @Override
  public Data.SpaceFillingCurve spaceFillCurve() {
    return spaceFillingCurve;
  }

  /**
   * Retrieve the merged histogram. TODO we should cache if the owning state is finalized.
   *
   * @return
   */
  public com.dynatrace.dynahist.Histogram histogram() {

    var allBuffers = allBuffers();
    if (allBuffers.size() == 1) {
      return ((BufferImpl) allBuffers.getFirst()).histogram;
    } else if (allBuffers.size() > 1) {
      com.dynatrace.dynahist.Histogram ret = null;
      var first = ((BufferImpl) allBuffers.getFirst()).histogram;
      if (first != null) {
        ret = com.dynatrace.dynahist.Histogram.createDynamic(first.getLayout());
        for (var buffer : allBuffers) {
          if (((BufferImpl) buffer).histogram != null) {
            ret.addHistogram(((BufferImpl) buffer).histogram);
          }
        }
      }
    }
    return null;
  }

  @Override
  public Persistence persistence() {
    return persistence;
  }

  protected List<Buffer> buffersCovering(
      Geometry geometry, Time eventTime, Data.SpaceFillingCurve fillingCurve, Type dataType) {

    var scale = GeometryRepository.INSTANCE.scale(geometry);
    var time = eventTime == null ? scale.getTime() : eventTime;
    if (time.size() != 1) {
      throw new KlabUnimplementedException(
          "Multiple time steps for a buffer request during contextualization");
    }

    long timeStart = time.is(Time.Type.INITIALIZATION) ? 0 : time.getStart().getMilliseconds();
    return buffers
        .computeIfAbsent(
            timeStart, k -> new ArrayList<>(createBuffers(geometry, observation, timeStart)))
        .stream()
        .map(b -> adaptBuffer(b, fillingCurve))
        .toList();
  }

  private List<BufferImpl> createBuffers(
      Geometry geometry, Observation observation, long timestamp) {

    var ret = new ArrayList<BufferImpl>();
    long[] splitSizes = new long[splits];
    long size = geometry.size() / splits;
    long remd = geometry.size() % splits;
    Arrays.fill(splitSizes, size);
    splitSizes[splits - 1] += remd;

    long offset = 0L;
    for (long bs : splitSizes) {
      ret.add(
          switch (type) {
            case BOXING -> null;
            case DOUBLE ->
                new DoubleBufferImpl(
                    geometry, observation, this, bs, spaceFillingCurve, offset, timestamp);
            case FLOAT -> null;
            case INTEGER -> null;
            case LONG -> null;
            case KEYED -> null;
            case BOOLEAN -> null;
          });
      offset += bs;
    }

    return ret;
  }

  @Override
  public long getTransientId() {
    return transientId;
  }

  /** DO NOT CALL - reserved for serialization purposes */
  public void setTransientId(long transientId) {
    this.transientId = transientId;
  }

  private Buffer adaptBuffer(BufferImpl b, Data.SpaceFillingCurve fillingCurve) {
    // TODO !
    if (b.getFillingCurve() != fillingCurve) {
      // TODO
    }
    return b;
  }

  @Override
  public List<Buffer> allBuffers() {
    var ret = new ArrayList<Buffer>();
    buffers.values().forEach(ret::addAll);
    return ret;
  }

  @Override
  public Type getType() {
    return this.type;
  }

  @Override
  public Geometry getGeometry() {
    return this.geometry;
  }

  @Override
  public Histogram getHistogram() {
    return Utils.Data.adaptHistogram(histogram());
  }

  @Override
  public long getId() {
    return 0;
  }

  public Data.SpaceFillingCurve getFillCurve() {
    return spaceFillingCurve;
  }
}
