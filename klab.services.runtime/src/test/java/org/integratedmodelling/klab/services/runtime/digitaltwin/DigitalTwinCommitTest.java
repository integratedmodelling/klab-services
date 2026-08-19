package org.integratedmodelling.klab.services.runtime.digitaltwin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import org.integratedmodelling.common.knowledge.CohortImpl;
import org.integratedmodelling.klab.api.collections.Triple;
import org.integratedmodelling.klab.api.data.Metadata;
import org.integratedmodelling.klab.api.data.RuntimeAsset;
import org.integratedmodelling.klab.api.digitaltwin.GraphModel;
import org.integratedmodelling.klab.api.knowledge.observation.impl.ObservationImpl;
import org.junit.jupiter.api.Test;

class DigitalTwinCommitTest {

  @Test
  void secondaryObservationAndExistingCohortRemainVisibleInRootCommit() {
    var observation = new ObservationImpl();
    observation.setId(101);
    var cohort = new CohortImpl();
    cohort.setId(10);
    var memberLink =
        Triple.of(10L, 101L, GraphModel.Relationship.HAS_MEMBER.name());
    var ownershipLink =
        Triple.of(
            RuntimeAsset.CONTEXT_ASSET_ID,
            10L,
            GraphModel.Relationship.HAS_CHILD.name());

    var commit =
        DigitalTwinImpl.TransactionImpl.createCommit(
            900, "tester", List.of(observation), Set.of(cohort), List.of(memberLink));

    assertTrue(commit.getAddedAssets().contains(101L));
    assertTrue(commit.getAddedObservations().contains(101L));
    assertFalse(commit.getAddedCohorts().contains(10L));
    assertTrue(commit.getModifiedAssets().contains(10L));
    assertTrue(commit.getAddedLinks().contains(memberLink));
    assertTrue(commit.getAddedLinks().contains(ownershipLink));
    assertEquals(900L, observation.getMetadata().get(Metadata.IM_COMMIT_ID, Number.class).longValue());
  }
}
