package org.integratedmodelling.klab.services.resources;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.*;
import org.integratedmodelling.klab.api.scope.UserScope;
import org.integratedmodelling.klab.api.services.resources.workflow.ReviewCycles.*;
import org.integratedmodelling.klab.resources.ReviewArchiveOutbox;
import org.integratedmodelling.klab.services.resources.storage.WorkspaceManager;
import org.integratedmodelling.klab.services.resources.workflow.*;
import org.junit.jupiter.api.Test;

class ReviewArchiveProviderTest {
  static void set(ResourcesProvider service, String name, Object value) throws Exception {
    var field = ResourcesProvider.class.getDeclaredField(name); field.setAccessible(true); field.set(service, value);
  }
  @Test void successfulClosureAttemptsOnlyItsAuthorizedHostAndArchiveFailureDoesNotUndoClosure() throws Exception {
    var service = mock(ResourcesProvider.class, CALLS_REAL_METHODS); var manager = mock(WorkflowManager.class);
    var worker = mock(ReviewArchiveWorker.class); var workspace = mock(WorkspaceManager.class); var user = mock(UserScope.class);
    set(service, "workflowManager", manager); set(service, "reviewArchiveWorker", worker); set(service, "workspaceManager", workspace);
    String flow = UUID.randomUUID().toString(), cycle = UUID.randomUUID().toString();
    var intent = new ReviewArchiveOutbox.Intent(flow, cycle, "ontology", 8);
    var command = new Command(7, Operation.CLOSE_CYCLE, null, cycle, null, null, null, null, false);
    var closed = new View(8, List.of(), true, "editor", List.of());
    when(manager.reviewCycle(flow, command, user)).thenReturn(closed);
    when(manager.requireReviewArchiveIntent(flow, cycle, user)).thenReturn(intent);
    when(worker.attempt(eq(intent), any())).thenAnswer(invocation -> {
      ReviewArchiveOutbox.ProjectAccess access = invocation.getArgument(1);
      access.authorized(intent, (project, root, base) -> fail("Mock workspace must not write a project"));
      return new ReviewArchiveWorker.Status("BLOCKED", false, null, null, null);
    });
    assertSame(closed, service.reviewCycle(flow, command, user));
    verify(workspace).withReviewArchiveProject(eq(intent), same(user), any());
    verify(manager, times(2)).requireReviewArchiveIntent(flow, cycle, user);
    when(worker.attempt(eq(intent), any())).thenThrow(new IllegalStateException("queue unavailable"));
    assertSame(closed, service.reviewCycle(flow, command, user));
    verify(manager, times(2)).reviewCycle(flow, command, user);
  }
}
