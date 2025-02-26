package org.integratedmodelling.klab.runtime.storage;

import java.util.*;

import jnr.ffi.annotations.In;
import org.integratedmodelling.common.knowledge.GeometryRepository;
import org.integratedmodelling.common.utils.Utils;
import org.integratedmodelling.klab.api.data.Data;
import org.integratedmodelling.klab.api.data.Histogram;
import org.integratedmodelling.klab.api.data.Storage;
import org.integratedmodelling.klab.api.exceptions.KlabIllegalStateException;
import org.integratedmodelling.klab.api.exceptions.KlabUnimplementedException;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.knowledge.SemanticType;
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
public abstract class AbstractStorage<B extends AbstractBuffer> implements Storage<B> {

  protected final Type type;
  protected final StateStorageImpl stateStorage;
  protected final Observation observation;
  protected final Geometry geometry;
  protected final ServiceContextScope contextScope;
  protected Persistence persistence;
  //  private List<AbstractBuffer> buffers = new ArrayList<>();
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
  private NavigableMap<Long, List<AbstractBuffer>> buffers = new TreeMap<>();

  protected AbstractStorage(
      Type type,
      Observation observation,
      StateStorageImpl stateStorage,
      ServiceContextScope contextScope) {
    this.type = type;
    this.stateStorage = stateStorage;
    this.observation = observation;
    this.geometry = observation.getGeometry();
    this.contextScope = contextScope;
    // defaults
    this.splits = 1;
  }

  protected void setOptions(Collection<Annotation> annotations) {

    var split = annotations.stream().filter(a -> "split".equals(a.getName())).findFirst();
    var fillcurve = annotations.stream().filter(a -> "fillcurve".equals(a.getName())).findFirst();

    // TODO cannot be null
    if (split.isPresent()) {
      this.splits = split.get().get("value", Integer.class);
    }
    if (fillcurve.isPresent()) {
      this.spaceFillingCurve =
          Data.SpaceFillingCurve.valueOf(fillcurve.get().get("value").toString());
    } else {
      var space =
          geometry.getDimensions().stream()
              .filter(d -> d.getType() == Geometry.Dimension.Type.SPACE)
              .findFirst();
      if (space.isEmpty() || space.get().size() <= 1) {
        this.spaceFillingCurve = Data.SpaceFillingCurve.D1_LINEAR;
      } else if (space.get().size() == 2) {
        this.spaceFillingCurve = Data.SpaceFillingCurve.D2_XY;
      } else if (space.get().size() == 3) {
        this.spaceFillingCurve = Data.SpaceFillingCurve.D3_XYZ;
      }
    }
  }

  @Override
  public <T extends Buffer> List<T> buffers(
      Geometry geometry, Class<T> bufferClass, Collection<Annotation> annotations) {

    if (this.spaceFillingCurve == null) {
      setOptions(annotations);
    }
    var nVaryingDimensions = geometry.getDimensions().stream().filter(d -> d.size() > 1).count();

    if (nVaryingDimensions > 1) {
      throw new KlabIllegalStateException(
          "Cannot create or retrieve buffers for more than one varying geometry extent at a time");
    }

    return (List<T>) buffersCovering(geometry, this.spaceFillingCurve, this.splits);
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
    //    if (buffers.size() == 1) {
    //      return buffers.getFirst().histogram;
    //    } else if (buffers.size() > 1) {
    //      SPDTHistogram ret = new SPDTHistogram<>(20);
    //      for (var buffer : buffers) {
    //        if (buffer.histogram != null) {
    //          ret.merge(buffer.histogram);
    //        }
    //      }
    //      // TODO cache if storage is finalized
    //      return ret;
    //    }
    return new SPDTHistogram<>(20);
  }

  //
  //  protected void registerBuffer(AbstractBuffer buffer) {
  //    // TODO index geometries, validate
  //    System.out.println("HOSTIA UN BÜFFER");
  //    //    buffers.add(buffer);
  //  }

  @Override
  public Persistence persistence() {
    return persistence;
  }

  protected List<? extends Buffer> buffersCovering(
      Geometry geometry, Data.SpaceFillingCurve fillingCurve, int splits) {

    if (this.spaceFillingCurve == null) {
      this.spaceFillingCurve = fillingCurve;
      this.splits = splits;
    }

    var scale = GeometryRepository.INSTANCE.get(geometry.toString(), Scale.class);
    var time = scale.getTime();
    if (time.size() != 1) {
      throw new KlabUnimplementedException(
          "Multiple time steps for a buffer request during contextualization");
    }

    long timeStart = time.is(Time.Type.INITIALIZATION) ? 0 : time.getStart().getMilliseconds();
    List<AbstractBuffer> bufs = buffers.computeIfAbsent(timeStart, k -> new ArrayList<>());

    /*
     * Based on observation scale and passed geometry, determine how many indices of this.buffers we
     * cover
     */
    if (!bufs.isEmpty()) {
      /*
      TODO adapt the curve; ignore the splits
       */
      return bufs.stream().map(b -> adaptBuffer(b, fillingCurve)).toList();
    }

    /* Build enough buffers to cover it honoring any splits and fill curve required. */
    var ret = createBuffers(geometry, geometry.size(), fillingCurve, splits);

    bufs.addAll(ret);

    return ret;
  }

  /**
   * The function in charge of creating the specific buffers wanted.
   *
   * @param size
   * @param fillingCurve
   * @param splits
   * @return
   */
  protected abstract List<AbstractBuffer> createBuffers(
      Geometry geometry, long size, Data.SpaceFillingCurve fillingCurve, int splits);

  private AbstractBuffer adaptBuffer(AbstractBuffer b, Data.SpaceFillingCurve fillingCurve) {
    // TODO !
    if (b.getFillingCurve() != fillingCurve) {
      // TODO
    }
    return b;
  }

  @Override
  public List<Storage.Buffer> buffers() {
    var ret = new ArrayList<Storage.Buffer>();
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
