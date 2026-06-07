package org.integratedmodelling.klab.api.knowledge;

import org.integratedmodelling.klab.api.data.Metadata;
import org.integratedmodelling.klab.api.data.RuntimeAsset;
import org.integratedmodelling.klab.api.geometry.Geometry;

/**
 * Cohorts are containers used in the knowledge graph to group observations of substantials of the
 * same substantial type. The member observations are linked to the cohort asset by the HAS_MEMBER
 * relationship.
 */
public interface Cohort extends RuntimeAsset {

  Metadata getMetadata();

  String getUrn();

  Observable getObservable();

  /**
   * The geometry of the cohort is the union of the geometries of its members and gets updated at
   * each root DT transaction that modifies the cohort.
   *
   * @return
   */
  Geometry getGeometry();
}
