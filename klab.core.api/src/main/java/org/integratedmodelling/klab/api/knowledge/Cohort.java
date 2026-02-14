package org.integratedmodelling.klab.api.knowledge;

import org.integratedmodelling.klab.api.data.Metadata;
import org.integratedmodelling.klab.api.data.RuntimeAsset;

/**
 * Cohorts are containers used in the knowledge graph to group observations of substantials of the
 * same substantial type. The member observations are linked to the cohort asset by the HAS_MEMBER
 * relationship.
 */
public interface Cohort extends RuntimeAsset {

  Metadata getMetadata();

  String getUrn();

  Observable getObservable();
}
