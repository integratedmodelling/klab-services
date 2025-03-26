package org.integratedmodelling.klab.runtime.storage;

import java.util.*;

import org.integratedmodelling.common.knowledge.GeometryRepository;
import org.integratedmodelling.klab.api.data.Data;
import org.integratedmodelling.klab.api.data.Histogram;
import org.integratedmodelling.klab.api.data.Storage;
import org.integratedmodelling.klab.api.exceptions.KlabIllegalStateException;
import org.integratedmodelling.klab.api.exceptions.KlabUnimplementedException;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.knowledge.observation.scale.Scale;
import org.integratedmodelling.klab.api.knowledge.observation.scale.time.Time;
import org.integratedmodelling.klab.api.lang.Annotation;
import org.integratedmodelling.klab.api.scope.Persistence;
import org.integratedmodelling.klab.data.histogram.SPDTHistogram;
import org.integratedmodelling.klab.services.scopes.ServiceContextScope;

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
  public List<Buffer> buffers(Geometry geometry) {
    return buffersCovering(geometry, this.spaceFillingCurve, this.type);
  }

  @Override
  public List<? extends Buffer> buffers(Geometry geometry, Annotation storageAnnotation) {
    return List.of();
  }

  @Override
  public <T extends Buffer> List<T> buffers(Geometry geometry, Class<T> bufferClass) {

    var nVaryingDimensions = geometry.getDimensions().stream().filter(d -> d.size() > 1).count();
    if (nVaryingDimensions > 1) {
      throw new KlabIllegalStateException(
          "Cannot create or retrieve buffers for more than one varying geometry extent at a time");
    }

    return (List<T>) buffersCovering(geometry, this.spaceFillingCurve, this.type);
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
  public SPDTHistogram<?> histogram() {

    var allBuffers = allBuffers();
    if (allBuffers.size() == 1) {
      return ((BufferImpl) allBuffers.getFirst()).histogram;
    } else if (allBuffers.size() > 1) {
      SPDTHistogram ret = new SPDTHistogram<>(20);
      for (var buffer : allBuffers) {
        if (((BufferImpl) buffer).histogram != null) {
          ret.merge(((BufferImpl) buffer).histogram);
        }
      }
      // TODO cache if storage is finalized
      return ret;
    }
    return new SPDTHistogram<>(20);
  }

  @Override
  public Persistence persistence() {
    return persistence;
  }

  protected List<Buffer> buffersCovering(
      Geometry geometry, Data.SpaceFillingCurve fillingCurve, Type dataType) {

    var scale = GeometryRepository.INSTANCE.scale(geometry);
    var time = scale.getTime();
    if (time.size() != 1) {
      throw new KlabUnimplementedException(
          "Multiple time steps for a buffer request during contextualization");
    }

    long timeStart = time.is(Time.Type.INITIALIZATION) ? 0 : time.getStart().getMilliseconds();
    return buffers
        .computeIfAbsent(timeStart, k -> new ArrayList<>(createBuffers(geometry)))
        .stream()
        .map(b -> adaptBuffer(b, fillingCurve))
        .toList();
  }

  private List<BufferImpl> createBuffers(Geometry geometry) {

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
            case DOUBLE -> new DoubleBufferImpl(geometry, this, bs, spaceFillingCurve, offset);
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
    return histogram().asHistogram();
  }

  @Override
  public long getId() {
    return 0;
  }

  public Data.SpaceFillingCurve getFillCurve() {
    return spaceFillingCurve;
  }
}
