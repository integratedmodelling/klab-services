package org.integratedmodelling.klab.runtime.storage;

import java.util.*;

import org.integratedmodelling.common.knowledge.GeometryRepository;
import org.integratedmodelling.common.utils.Utils;
import org.integratedmodelling.klab.api.data.Data;
import org.integratedmodelling.klab.api.data.Histogram;
import org.integratedmodelling.klab.api.data.Storage;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.knowledge.observation.scale.Scale;
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
  }

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

  protected void registerBuffer(AbstractBuffer buffer) {
    // TODO index geometries, validate
    System.out.println("HOSTIA UN BUFFER");
//    buffers.add(buffer);
  }

  @Override
  public Persistence persistence() {
    return persistence;
  }

  protected Collection<AbstractBuffer> buffersCovering(Geometry geometry) {
//    var scale = GeometryRepository.INSTANCE.get(geometry.key(), Scale.class);

    /**
     * Based on observation scale and passed geometry, determine how many indices of this.buffers we cover
     */

    /*
    For each index, lookup or build the correspondent collections
     */

    /*
    If the collection exists, it must cover each possible state of the observation geometry. If it doesn't,
    build enough buffers to cover it honoring any splits and fill curve required. Otherwise determine which
    buffers are covered and possibly adapt them to the fill curve required.
     */

    /*
    We should never be in a situation when the splits requested are not in phase with existing buffers. In
    that case we are free to throw an internal error exception so that the implementation can prevent this.
     */

    return List.of();
  }

  @Override
  public List<Storage.Buffer> buffers() {
    // hope this gets optimized
    return Utils.Collections.join(buffers.values()).stream().map(b -> (Buffer) b).toList();
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
