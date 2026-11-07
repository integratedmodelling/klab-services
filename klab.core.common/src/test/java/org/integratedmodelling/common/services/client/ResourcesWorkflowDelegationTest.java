package org.integratedmodelling.common.services.client;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Map;
import org.integratedmodelling.common.utils.Utils;
import org.integratedmodelling.klab.api.scope.Scope;
import org.integratedmodelling.klab.api.scope.UserScope;
import org.integratedmodelling.klab.api.services.ResourcesService;
import org.integratedmodelling.klab.api.services.resources.workflow.Flow;
import org.integratedmodelling.klab.api.services.resources.workflow.ReviewCycles;
import org.integratedmodelling.klab.api.services.resources.workflow.WorkflowBehavior;
import org.junit.jupiter.api.Test;

class ResourcesWorkflowDelegationTest {
  @Test void allDefaultPilotMethodsAreImplementedByBothProductionClientPaths() throws Exception {
    for (var method : ResourcesService.class.getDeclaredMethods()) {
      if (method.isDefault() && (method.getName().contains("Flow")
          || method.getName().contains("Review") || method.getName().equals("reviewCycle"))) {
        for (Class<?> implementation : List.of(ResourcesClient.class, ResourcesMerger.class)) {
          assertEquals(implementation,
              implementation.getMethod(method.getName(), method.getParameterTypes()).getDeclaringClass(),
              implementation.getSimpleName() + " must not inherit unsupported " + method.getName());
        }
      }
    }
  }

  @Test void pilotOperationsPreservePrimarySelectionArgumentsScopeAndResults() {
    var primary = mock(ResourcesService.class);
    var secondary = mock(ResourcesService.class);
    var owner = mock(Scope.class);
    var caller = mock(UserScope.class);
    doReturn(List.of(primary, secondary)).when(owner).getServices(ResourcesService.class);
    ResourcesService resources = new ResourcesMerger(owner);
    var flow = Flow.create();
    var actions = List.of(new WorkflowBehavior.AvailableAction("prepare", "Prepare review draft", "prepare", List.of(), 7));
    var request = new WorkflowBehavior.ActionRequest(7, Map.of("name", "draft"));
    var command = new ReviewCycles.Command(7, ReviewCycles.Operation.OPEN_CYCLE,
        "stage", null, null, null, null, null, false);
    var view = new ReviewCycles.View(8, List.of());
    byte[] artifact = {1, 2, 3};
    when(primary.getFlowActions("flow", "stage", caller)).thenReturn(actions);
    when(primary.executeFlowAction("flow", "stage", "prepare", request, caller)).thenReturn(flow);
    when(primary.getReviewCycles("flow", caller)).thenReturn(view);
    when(primary.reviewCycle("flow", command, caller)).thenReturn(view);
    when(primary.getReviewArtifact("flow", "cycle", "attachment", caller)).thenReturn(artifact);

    assertSame(actions, resources.getFlowActions("flow", "stage", caller));
    assertSame(flow, resources.executeFlowAction("flow", "stage", "prepare", request, caller));
    assertSame(view, resources.getReviewCycles("flow", caller));
    assertSame(view, resources.reviewCycle("flow", command, caller));
    assertSame(artifact, resources.getReviewArtifact("flow", "cycle", "attachment", caller));
    verify(secondary, never()).getFlowActions(any(), any(), any());
    verify(secondary, never()).executeFlowAction(any(), any(), any(), any(), any());
    verify(secondary, never()).getReviewCycles(any(), any());
    verify(secondary, never()).reviewCycle(any(), any(), any());
    verify(secondary, never()).getReviewArtifact(any(), any(), any(), any());
  }

  @Test void failedMutationsAreNotRepeatedOnAnotherResourcesService() {
    var primary = mock(ResourcesService.class);
    var secondary = mock(ResourcesService.class);
    var owner = mock(Scope.class);
    var caller = mock(UserScope.class);
    doReturn(List.of(primary, secondary)).when(owner).getServices(ResourcesService.class);
    ResourcesService resources = new ResourcesMerger(owner);
    var action = new WorkflowBehavior.ActionRequest(7, Map.of());
    var command = new ReviewCycles.Command(7, ReviewCycles.Operation.OPEN_CYCLE,
        "stage", null, null, null, null, null, false);
    var denied = new Utils.Http.Client.RequestFailure(403, "Access denied", null);
    var conflict = new Utils.Http.Client.RequestFailure(409, "Revision conflict", null);
    when(primary.executeFlowAction("flow", "stage", "prepare", action, caller)).thenThrow(denied);
    when(primary.reviewCycle("flow", command, caller)).thenThrow(conflict);
    assertSame(denied, assertThrows(Utils.Http.Client.RequestFailure.class,
        () -> resources.executeFlowAction("flow", "stage", "prepare", action, caller)));
    assertSame(conflict, assertThrows(Utils.Http.Client.RequestFailure.class,
        () -> resources.reviewCycle("flow", command, caller)));
    verify(primary).executeFlowAction("flow", "stage", "prepare", action, caller);
    verify(primary).reviewCycle("flow", command, caller);
    verify(secondary, never()).executeFlowAction(any(), any(), any(), any(), any());
    verify(secondary, never()).reviewCycle(any(), any(), any());
  }
}
