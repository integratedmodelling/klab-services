package org.integratedmodelling.klab.api.services.resources.workflow.impl;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.integratedmodelling.klab.api.data.Metadata;
import org.integratedmodelling.klab.api.knowledge.KlabAsset;
import org.integratedmodelling.klab.api.lang.Annotation;
import org.integratedmodelling.klab.api.scope.UserScope;
import org.integratedmodelling.klab.api.services.resources.workflow.Flow;
import org.integratedmodelling.klab.api.services.resources.workflow.Workflow;
import org.integratedmodelling.klab.api.services.resources.workflow.WorkflowParticipant;
import org.integratedmodelling.klab.api.services.resources.workflow.WorkflowRole;
import org.integratedmodelling.klab.api.services.resources.workflow.WorkflowUrns;

/**
 * Versioned configuration schema for one kind of workflow.
 *
 * <p>The object contains no service state and is deliberately executable on clients. Call {@link
 * #validate()} after deserializing configuration and {@link #admittedTransitions(Flow.State,
 * WorkflowParticipant)} to drive a client UI without a server round trip.
 */
public class WorkflowImpl implements Workflow {

  @SuppressWarnings("unchecked")
  public static Metadata metadata(Map<String, Object> value) {
    if (value instanceof Metadata metadata) return metadata;
    if (value != null) {
      Object delegate = value.get("delegate");
      return Metadata.create(
          (Map<String, Object>) (delegate instanceof Map<?, ?> nested ? nested : value));
    }
    return Metadata.create();
  }

  public static class AttachmentRuleImpl implements Workflow.AttachmentRule {
    private String type;
    private String mediaType;
    private KlabAsset.KnowledgeClass assetType;
    private int arity = -1;
    private boolean required;

    public String getType() {
      return type;
    }

    public void setType(String type) {
      this.type = type;
    }

    public String getMediaType() {
      return mediaType;
    }

    public void setMediaType(String mediaType) {
      this.mediaType = mediaType;
    }

    public KlabAsset.KnowledgeClass getAssetType() {
      return assetType;
    }

    public void setAssetType(KlabAsset.KnowledgeClass assetType) {
      this.assetType = assetType;
    }

    public int getArity() {
      return arity;
    }

    public void setArity(int arity) {
      this.arity = arity;
    }

    public boolean isRequired() {
      return required;
    }

    public void setRequired(boolean required) {
      this.required = required;
    }
  }

  public static class StateSchemaImpl implements Workflow.StateSchema {
    private String id;
    private String workflowId;
    private String workflowVersion;
    private String description;
    private String completionCriteria;
    private String instructions;
    private Set<WorkflowRole> managerRoles = new LinkedHashSet<>();
    private Set<WorkflowRole> contributorRoles = new LinkedHashSet<>();
    private Set<String> admittedGroups = new LinkedHashSet<>();
    private List<Workflow.AttachmentRule> attachments = new ArrayList<>();
    private Set<KlabAsset.KnowledgeClass> assetTypes = new LinkedHashSet<>();
    private boolean open = true;
    private Metadata metadata = Metadata.create();
    private String serviceId;

    @Override
    public String getUrn() {
      return WorkflowUrns.workflowState(workflowId, workflowVersion, id);
    }

    @Override
    public Collection<Annotation> getAnnotations() {
      return List.of();
    }

    public String getId() {
      return id;
    }

    public void setId(String id) {
      this.id = id;
    }

    public String getDescription() {
      return description;
    }

    public void setDescription(String description) {
      this.description = description;
    }

    public String getCompletionCriteria() {
      return completionCriteria;
    }

    public void setCompletionCriteria(String completionCriteria) {
      this.completionCriteria = completionCriteria;
    }

    @Override
    public String getServiceId() {
      return serviceId;
    }

    public void setServiceId(String serviceId) {
      this.serviceId = serviceId;
    }

    public String getInstructions() {
      return instructions;
    }

    public void setInstructions(String instructions) {
      this.instructions = instructions;
    }

    public Set<WorkflowRole> getManagerRoles() {
      return managerRoles;
    }

    public void setManagerRoles(Set<WorkflowRole> v) {
      managerRoles = v == null ? new LinkedHashSet<>() : v;
    }

    public Set<WorkflowRole> getContributorRoles() {
      return contributorRoles;
    }

    public void setContributorRoles(Set<WorkflowRole> v) {
      contributorRoles = v == null ? new LinkedHashSet<>() : v;
    }

    public Set<String> getAdmittedGroups() {
      return admittedGroups;
    }

    public void setAdmittedGroups(Set<String> v) {
      admittedGroups = v == null ? new LinkedHashSet<>() : v;
    }

    public List<Workflow.AttachmentRule> getAttachments() {
      return attachments;
    }

    public void setAttachments(List<Workflow.AttachmentRule> v) {
      attachments = v == null ? new ArrayList<>() : v;
    }

    public Set<KlabAsset.KnowledgeClass> getAssetTypes() {
      return assetTypes;
    }

    public void setAssetTypes(Set<KlabAsset.KnowledgeClass> v) {
      assetTypes = v == null ? new LinkedHashSet<>() : v;
    }

    public boolean isOpen() {
      return open;
    }

    public void setOpen(boolean open) {
      this.open = open;
    }

    @Override
    public Metadata getMetadata() {
      return metadata;
    }

    public void setMetadata(Map<String, Object> v) {
      metadata = WorkflowImpl.metadata(v);
    }

    public String getWorkflowId() {
      return workflowId;
    }

    public void setWorkflowId(String workflowId) {
      this.workflowId = workflowId;
    }

    public String getWorkflowVersion() {
      return workflowVersion;
    }

    public void setWorkflowVersion(String workflowVersion) {
      this.workflowVersion = workflowVersion;
    }
  }

  public static class TransitionSchemaImpl implements Workflow.TransitionSchema {
    private String id;
    private String workflowId;
    private String workflowVersion;
    private String description;
    private Set<String> sourceStates = new LinkedHashSet<>();
    private String targetState;
    private Set<WorkflowRole> roles = new LinkedHashSet<>();
    private Set<KlabAsset.KnowledgeClass> sourceAssetTypes = new LinkedHashSet<>();
    private Set<String> sourceMediaTypes = new LinkedHashSet<>();
    private Metadata metadata = Metadata.create();
    private String serviceId;

    @Override
    public String getUrn() {
      return WorkflowUrns.workflowTransition(workflowId, workflowVersion, id);
    }

    @Override
    public Collection<Annotation> getAnnotations() {
      return List.of();
    }

    public String getId() {
      return id;
    }

    public void setId(String id) {
      this.id = id;
    }

    @Override
    public String getServiceId() {
      return serviceId;
    }

    public void setServiceId(String serviceId) {
      this.serviceId = serviceId;
    }

    public String getDescription() {
      return description;
    }

    public void setDescription(String description) {
      this.description = description;
    }

    public Set<String> getSourceStates() {
      return sourceStates;
    }

    public void setSourceStates(Set<String> v) {
      sourceStates = v == null ? new LinkedHashSet<>() : v;
    }

    public String getTargetState() {
      return targetState;
    }

    public void setTargetState(String targetState) {
      this.targetState = targetState;
    }

    public Set<WorkflowRole> getRoles() {
      return roles;
    }

    public void setRoles(Set<WorkflowRole> v) {
      roles = v == null ? new LinkedHashSet<>() : v;
    }

    public Set<KlabAsset.KnowledgeClass> getSourceAssetTypes() {
      return sourceAssetTypes;
    }

    public void setSourceAssetTypes(Set<KlabAsset.KnowledgeClass> v) {
      sourceAssetTypes = v == null ? new LinkedHashSet<>() : v;
    }

    public Set<String> getSourceMediaTypes() {
      return sourceMediaTypes;
    }

    public void setSourceMediaTypes(Set<String> v) {
      sourceMediaTypes = v == null ? new LinkedHashSet<>() : v;
    }

    @Override
    public Metadata getMetadata() {
      return metadata;
    }

    public void setMetadata(Map<String, Object> v) {
      metadata = WorkflowImpl.metadata(v);
    }

    public String getWorkflowId() {
      return workflowId;
    }

    public void setWorkflowId(String workflowId) {
      this.workflowId = workflowId;
    }

    public String getWorkflowVersion() {
      return workflowVersion;
    }

    public void setWorkflowVersion(String workflowVersion) {
      this.workflowVersion = workflowVersion;
    }
  }

  private String id;
  private String version;
  private String name;
  private String description;
  private Set<KlabAsset.KnowledgeClass> assetTypes = new LinkedHashSet<>();
  private Map<String, Workflow.StateSchema> states = new LinkedHashMap<>();
  private Map<String, Workflow.TransitionSchema> transitions = new LinkedHashMap<>();
  private Metadata metadata = Metadata.create();
  private String serviceId;

  /** Return every structural error. An empty result means the schema can be executed. */
  public List<String> validate() {
    var errors = new ArrayList<String>();
    if (id == null || id.isBlank()) errors.add("Workflow id is required");
    if (version == null || version.isBlank()) errors.add("Workflow version is required");
    if (states.isEmpty()) errors.add("At least one state is required");
    for (var entry : states.entrySet()) {
      if (entry.getValue() == null) errors.add("State " + entry.getKey() + " has no schema");
      else {
        var state = entry.getValue();
        state.setWorkflowId(id);
        state.setWorkflowVersion(version);
        if (state.getId() == null) state.setId(entry.getKey());
        else if (!entry.getKey().equals(state.getId()))
          errors.add("State key/id mismatch: " + entry.getKey());
        if (state.getManagerRoles().isEmpty() && state.getContributorRoles().isEmpty())
          errors.add("State " + entry.getKey() + " admits no roles");
        var attachmentTypes = new LinkedHashSet<String>();
        for (var attachment : state.getAttachments()) {
          if (attachment.getType() == null || attachment.getType().isBlank())
            errors.add("Unnamed attachment rule in " + entry.getKey());
          else if (!attachmentTypes.add(attachment.getType()))
            errors.add(
                "Duplicate attachment type " + attachment.getType() + " in " + entry.getKey());
          if (attachment.getArity() < -1)
            errors.add("Invalid attachment arity in " + entry.getKey());
          if (attachment.isRequired() && attachment.getArity() == 0)
            errors.add("Required attachment cannot have zero arity in " + entry.getKey());
        }
      }
    }
    for (var entry : transitions.entrySet()) {
      var transition = entry.getValue();
      if (transition == null) {
        errors.add("Transition " + entry.getKey() + " has no schema");
        continue;
      }
      if (transition.getId() == null) transition.setId(entry.getKey());
      else if (!entry.getKey().equals(transition.getId()))
        errors.add("Transition key/id mismatch: " + entry.getKey());
      transition.setWorkflowId(id);
      transition.setWorkflowVersion(version);
      if (transition.getSourceStates().isEmpty())
        errors.add("Transition " + entry.getKey() + " has no source states");
      if (transition.getRoles().isEmpty())
        errors.add("Transition " + entry.getKey() + " admits no roles");
      if (!states.containsKey(transition.getTargetState()))
        errors.add("Unknown target state in " + entry.getKey());
      for (String source : transition.getSourceStates()) {
        if (!Workflow.INIT.equals(source) && !states.containsKey(source))
          errors.add("Unknown source state " + source + " in " + entry.getKey());
      }
    }
    if (transitions.values().stream()
        .noneMatch(t -> t != null && t.getSourceStates().contains(Workflow.INIT)))
      errors.add("At least one INIT transition is required");
    return errors;
  }

  public List<Workflow.TransitionSchema> admittedTransitions(
      Flow.State state, WorkflowParticipant participant) {
    var ret = new ArrayList<Workflow.TransitionSchema>();
    if (state == null || participant == null) return ret;
    if (!canAccess(states.get(state.getSchemaId()), participant)) return ret;
    for (var transition : transitions.values()) {
      if (transition.getSourceStates().contains(state.getSchemaId())
          && participant.hasAnyRole(transition.getRoles())
          && participant.canRespondTo(state)
          && !participant.getDisallowedTransitions().contains(transition.getId()))
        ret.add(transition);
    }
    return ret;
  }

  /** Convenience overload for clients that hold the complete flow and a live user scope. */
  public List<Workflow.TransitionSchema> admittedTransitions(Flow flow, String stateId, UserScope scope) {
    return flow == null
        ? List.of()
        : admittedTransitions(flow.getStates().get(stateId), WorkflowParticipant.from(scope));
  }

  /**
   * Validate a transition using only client-side data. The server repeats these checks against its
   * authoritative aggregate before committing it.
   */
  public List<String> validateTransition(
      Flow flow, String stateId, String transitionId, WorkflowParticipant participant) {
    var errors = new ArrayList<String>();
    if (flow == null) {
      errors.add("Flow is required");
      return errors;
    }
    var state = flow.getStates().get(stateId);
    if (state == null) {
      errors.add("Unknown state " + stateId);
      return errors;
    }
    if (!flow.getCurrentStateIds().contains(stateId)) errors.add("State is not current");
    if (participant == null || !canAccess(states.get(state.getSchemaId()), participant))
      errors.add("Participant is not permitted to use this workflow state");
    var transition = transitions.get(transitionId);
    if (transition == null) errors.add("Unknown transition " + transitionId);
    else {
      if (!transition.getSourceStates().contains(state.getSchemaId()))
        errors.add("Transition does not admit the state schema");
      if (participant == null || !participant.hasAnyRole(transition.getRoles()))
        errors.add("Participant role is not admitted");
      if (participant != null && !participant.canRespondTo(state))
        errors.add("The group response deadline has elapsed");
      if (participant != null && participant.getDisallowedTransitions().contains(transitionId))
        errors.add("Transition is disallowed by group policy");
      var schema = states.get(state.getSchemaId());
      if (schema != null)
        for (var rule : schema.getAttachments())
          if (rule.isRequired()
              && state.getAttachments().stream()
                  .noneMatch(attachment -> Objects.equals(rule.getType(), attachment.getType())))
            errors.add("Stage requires an attachment of type '" + rule.getType() + "'");
    }
    return errors;
  }

  public boolean canAccess(Workflow.StateSchema state, WorkflowParticipant participant) {
    if (state == null || participant == null) return false;
    if (participant.getRoles().contains(WorkflowRole.ADMIN)) return true;
    boolean role =
        participant.hasAnyRole(state.getManagerRoles())
            || participant.hasAnyRole(state.getContributorRoles());
    if (!role) return false;
    if (state.getAdmittedGroups().contains(WorkflowParticipant.PUBLIC_GROUP)
        && participant.isKnownRealPerson()
        && participant.getRoles().contains(WorkflowRole.REVIEWER)) return true;
    if (!participant.isWorkflowPermitted(this)) return false;
    if (state.getAdmittedGroups().isEmpty()) return true;
    return state.getAdmittedGroups().stream().anyMatch(participant.getGroups()::contains);
  }

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public String getVersion() {
    return version;
  }

  public void setVersion(String version) {
    this.version = version;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  public Set<KlabAsset.KnowledgeClass> getAssetTypes() {
    return assetTypes;
  }

  public void setAssetTypes(Set<KlabAsset.KnowledgeClass> assetTypes) {
    this.assetTypes = assetTypes == null ? new LinkedHashSet<>() : assetTypes;
  }

  public Map<String, Workflow.StateSchema> getStates() {
    return states;
  }

  public void setStates(Map<String, Workflow.StateSchema> v) {
    states = v == null ? new LinkedHashMap<>() : v;
  }

  public Map<String, Workflow.TransitionSchema> getTransitions() {
    return transitions;
  }

  @Override
  public String getServiceId() {
    return serviceId;
  }

  public void setServiceId(String serviceId) {
    this.serviceId = serviceId;
  }

  public void setTransitions(Map<String, Workflow.TransitionSchema> v) {
    transitions = v == null ? new LinkedHashMap<>() : v;
  }

  @Override
  public String getUrn() {
    return WorkflowUrns.workflow(id, version);
  }

  @Override
  public Metadata getMetadata() {
    return metadata;
  }

  public void setMetadata(Map<String, Object> v) {
    metadata = WorkflowImpl.metadata(v);
  }

  @Override
  public Collection<Annotation> getAnnotations() {
    return List.of();
  }
}
