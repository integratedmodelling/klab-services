package org.integratedmodelling.klab.services.resources.storage;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.List;
import org.integratedmodelling.klab.api.lang.kactors.KActorsBehavior;
import org.integratedmodelling.klab.services.resources.ResourcesProvider;
import org.integratedmodelling.klab.services.resources.workflow.WorkflowBehaviorBridge;
import org.junit.jupiter.api.Test;

/** Uses the production parser and semantic validation, not the workflow fixture's parser. */
class BundledOntologyReviewBehaviorTest {
  @Test void productionWorkspaceParserExposesThePreparationContract() throws Exception {
    var workspace = mock(WorkspaceManager.class, CALLS_REAL_METHODS);
    var service = mock(ResourcesProvider.class);
    when(service.serviceId()).thenReturn("resources");
    field(workspace, "service", service);
    field(workspace, "behaviorParser", workspace.new BehaviorParser());
    var behavior = WorkflowBehaviorBridge.bundledOntologyReview(
        source -> workspace.parseAsset(source, KActorsBehavior.class));
    assertEquals(WorkflowBehaviorBridge.ONTOLOGY_REVIEW_BEHAVIOR, behavior.getUrn());
    var prepare = behavior.getStatements().stream().filter(a -> "prepare".equals(a.getUrn())).findFirst().orElseThrow();
    assertEquals(List.of("content", "ontology", "project", "proposal_id", "revision_id", "domain", "purpose", "requested_tier", "target_community"),
        prepare.getArguments().stream().map(a -> a.getName()).toList());
    assertFalse(org.integratedmodelling.common.utils.Utils.Notifications.hasErrors(behavior.getNotifications()));
    assertThrows(IllegalStateException.class, () -> WorkflowBehaviorBridge.bundledOntologyReview(source -> null));
  }

  private void field(Object target, String name, Object value) throws Exception {
    var field = WorkspaceManager.class.getDeclaredField(name); field.setAccessible(true); field.set(target, value);
  }
}
