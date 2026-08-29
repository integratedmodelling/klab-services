package org.integratedmodelling.resources.server.controllers;

import io.swagger.v3.oas.annotations.tags.Tag;
import java.security.Principal;
import java.util.Base64;
import java.util.List;
import org.integratedmodelling.klab.api.ServicesAPI;
import org.integratedmodelling.klab.api.exceptions.KlabAuthorizationException;
import org.integratedmodelling.klab.api.scope.UserScope;
import org.integratedmodelling.klab.api.services.resources.workflow.Flow;
import org.integratedmodelling.klab.api.services.resources.workflow.Workflow;
import org.integratedmodelling.klab.services.application.security.EngineAuthorization;
import org.integratedmodelling.klab.services.application.security.Role;
import org.integratedmodelling.resources.server.ResourcesServer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.annotation.Secured;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

/** Minimal authenticated REST surface for workflow schemas, flow tasks, transitions and blobs. */
@RestController
@Tag(name = "Resource workflows", description = "Persistent workflow and task management")
@Secured(Role.USER)
public class WorkflowController {

  @Autowired private ResourcesServer resourcesServer;

  @GetMapping(ServicesAPI.RESOURCES.WORKFLOW)
  public Workflow getWorkflow(@PathVariable String workflowId, Principal principal) {
    return resourcesServer.klabService().getWorkflow(workflowId, userScope(principal));
  }

  @PostMapping(ServicesAPI.RESOURCES.FLOWS)
  public Flow createFlow(
      @RequestParam String workflowId, @RequestBody Flow.State initialState, Principal principal) {
    return resourcesServer.klabService().createFlow(workflowId, initialState, userScope(principal));
  }

  @GetMapping(ServicesAPI.RESOURCES.FLOWS)
  public List<Flow> listFlows(
      @RequestParam(defaultValue = "false") boolean includeClosed, Principal principal) {
    return resourcesServer.klabService().getFlows(includeClosed, userScope(principal));
  }

  @GetMapping(ServicesAPI.RESOURCES.FLOW)
  public Flow getFlow(@PathVariable String flowId, Principal principal) {
    return resourcesServer.klabService().getFlow(flowId, userScope(principal));
  }

  @PostMapping(ServicesAPI.RESOURCES.FLOW_STATES)
  public Flow.State createState(
      @PathVariable String flowId, @RequestBody Flow.State state, Principal principal) {
    return resourcesServer.klabService().createFlowState(flowId, state, userScope(principal));
  }

  @RequestMapping(
      path = ServicesAPI.RESOURCES.FLOW_STATE,
      method = {RequestMethod.POST, RequestMethod.PUT})
  public Flow.State updateState(
      @PathVariable String flowId,
      @PathVariable String stateId,
      @RequestBody Flow.State state,
      Principal principal) {
    return resourcesServer.klabService().updateFlowState(flowId, stateId, state, userScope(principal));
  }

  @DeleteMapping(ServicesAPI.RESOURCES.FLOW_STATE)
  public boolean deleteState(
      @PathVariable String flowId, @PathVariable String stateId, Principal principal) {
    return resourcesServer.klabService().deleteFlowState(flowId, stateId, userScope(principal));
  }

  @PostMapping(ServicesAPI.RESOURCES.FLOW_TRANSITIONS)
  public Flow transition(
      @PathVariable String flowId,
      @RequestBody Flow.TransitionRequest request,
      Principal principal) {
    return resourcesServer.klabService().transitionFlow(flowId, request, userScope(principal));
  }

  @PostMapping(ServicesAPI.RESOURCES.FLOW_ATTACHMENTS)
  public Flow.Attachment addAttachment(
      @PathVariable String flowId,
      @PathVariable String stateId,
      @RequestBody Flow.AttachmentUpload upload,
      Principal principal) {
    return resourcesServer
        .klabService()
        .addFlowAttachment(flowId, stateId, upload, userScope(principal));
  }

  /** JSON-safe Base64 avoids exposing the storage layout and works with every client transport. */
  @GetMapping(ServicesAPI.RESOURCES.FLOW_ATTACHMENT)
  public String getAttachment(
      @PathVariable String flowId, @PathVariable String attachmentId, Principal principal) {
    return Base64.getEncoder()
        .encodeToString(
            resourcesServer
                .klabService()
                .getFlowAttachment(flowId, attachmentId, userScope(principal)));
  }

  private UserScope userScope(Principal principal) {
    if (principal instanceof EngineAuthorization authorization
        && authorization.getScope() instanceof UserScope scope) return scope;
    throw new KlabAuthorizationException("A valid user scope is required for workflow operations");
  }
}
