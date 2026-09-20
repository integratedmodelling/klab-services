package org.integratedmodelling.klab.services.runtime;

import org.integratedmodelling.common.knowledge.GeometryRepository;
import org.integratedmodelling.klab.api.digitaltwin.Scheduler;
import org.integratedmodelling.klab.api.geometry.Geometry;

/**
 * Preserve spatial support while supplying the actual transaction's temporal extent.
 *
 * <p>FIXME use Geometry mutations instead of creating new geometries from encoded strings.
 */
public final class TemporalGeometry {
  private TemporalGeometry() {}

  public static Geometry localize(Geometry geometry, Scheduler.Event event) {
    if (event == null || event.getType() == Scheduler.Event.Type.INITIALIZATION) return geometry;
    var time = event.getTime();
    var extent =
        GeometryRepository.INSTANCE
            .scale(
                Geometry.create(
                    "T0(1){ttype=PHYSICAL,tstart="
                        + time.getStart().getMilliseconds()
                        + ",tend="
                        + time.getEnd().getMilliseconds()
                        + "}"))
            .getTime();
    return GeometryRepository.INSTANCE.scale(geometry).with(extent);
  }
}
