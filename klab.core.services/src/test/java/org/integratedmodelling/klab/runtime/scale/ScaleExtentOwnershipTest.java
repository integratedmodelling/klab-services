package org.integratedmodelling.klab.runtime.scale;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.integratedmodelling.common.knowledge.GeometryRepository;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.knowledge.observation.scale.Scale;
import org.integratedmodelling.klab.api.lang.LogicalConnector;
import org.integratedmodelling.klab.configuration.ServiceConfiguration;
import org.integratedmodelling.klab.runtime.scale.time.TimeImpl;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class ScaleExtentOwnershipTest {
  @BeforeAll static void configure() { ServiceConfiguration.injectInstantiators(); }

  @Test void coverageCacheCanMergeScalesBuiltFromImmutableExtentLists() {
    var left = new ScaleImpl(List.of(TimeImpl.create(0L, 10L)));
    var right = new ScaleImpl(List.of(TimeImpl.create(0L, 5L)));
    var merged = GeometryRepository.INSTANCE.getMerged(left, right, LogicalConnector.INTERSECTION, Scale.class);
    assertEquals(0, merged.getTime().getStart().getMilliseconds());
    assertEquals(5, merged.getTime().getEnd().getMilliseconds());
  }

  @Test void canonicalOrderingDoesNotReorderCallerOwnedLists() {
    var source = GeometryRepository.INSTANCE.scale(Geometry.create(
        "T0(1){tstart=0,tend=10,ttype=PHYSICAL}"
            + "S2(1,1){proj=EPSG:4326,shape=EPSG:4326 POLYGON ((0 0&comma;1 0&comma;1 1&comma;0 1&comma;0 0))}"));
    var input = new ArrayList<>(source.getExtents());
    Collections.reverse(input);
    var original = List.copyOf(input);
    var scale = new ScaleImpl(input);
    assertEquals(original, input);
    assertEquals(source.getExtents(), scale.getExtents());
  }
}
