package org.integratedmodelling.klab.services.runtime.digitaltwin;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
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
import org.integratedmodelling.klab.api.provenance.impl.ActivityImpl;
import org.junit.jupiter.api.Test;

class DigitalTwinCommitTest {

  @Test
  void executionActivityLinksToAffectedObservationUsingItsOwnType() {
    var twin = org.mockito.Mockito.mock(DigitalTwinImpl.class);
    var scope = org.mockito.Mockito.mock(org.integratedmodelling.klab.services.scopes.ServiceContextScope.class);
    var observation = new ObservationImpl(); observation.setId(42);
    var parentActivity = org.integratedmodelling.klab.api.provenance.Activity.of(
        org.integratedmodelling.klab.api.provenance.Activity.Type.SUBMISSION);
    parentActivity.setId(100);
    var parent = twin.new TransactionImpl(parentActivity, scope, RuntimeAsset.PROVENANCE_ASSET, observation);
    long activityId = 101;
    for (var contextualization : org.integratedmodelling.klab.api.knowledge.Contextualization.values()) {
      if (contextualization == org.integratedmodelling.klab.api.knowledge.Contextualization.VOID) continue;
      var activity = org.integratedmodelling.klab.api.provenance.Activity.of(
          org.integratedmodelling.klab.api.provenance.Activity.Type.forContextualization(contextualization));
      activity.setId(activityId++);
      var child = parent.getChild(activity, scope, observation);
      var effect = child.outgoing(activity).iterator().next();
      assertEquals(GraphModel.Relationship.forContextualization(contextualization), effect.type());
      assertEquals(activity, effect.source());
      assertEquals(observation, effect.target());
      assertTrue(child.incoming(activity).stream().anyMatch(link ->
          link.type() == GraphModel.Relationship.TRIGGERED && link.source() == parentActivity));
      assertFalse(child.outgoing(activity).stream().anyMatch(link -> link.type() == GraphModel.Relationship.CREATED));
    }
  }

  @Test
  void intermediateCommitKeepsItsTransactionLocalTarget() {
    var observation = new ObservationImpl();
    observation.setId(-1);

    assertDoesNotThrow(
        () ->
            DigitalTwinImpl.TransactionImpl.finalizeCommittedTarget(
                false, observation, new ActivityImpl()));
    assertEquals(-1, observation.getId());
  }

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
