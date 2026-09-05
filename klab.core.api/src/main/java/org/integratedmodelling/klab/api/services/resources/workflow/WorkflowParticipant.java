package org.integratedmodelling.klab.api.services.resources.workflow;

import java.io.Serializable;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashSet;
import java.util.Collection;
import java.util.Set;
import org.integratedmodelling.klab.api.authentication.CustomProperty;
import org.integratedmodelling.klab.api.identities.Group;
import org.integratedmodelling.klab.api.scope.UserScope;

/**
 * Stable, serializable authorization and provenance projection of a {@link UserScope}.
 *
 * <p>Group custom properties use the {@value #ROLES_PROPERTY}, {@value
 * #PERMITTED_WORKFLOWS_PROPERTY}, {@value #DISALLOWED_TRANSITIONS_PROPERTY}, and {@value
 * #MAX_RESPONSE_HOURS_PROPERTY} keys. Values that contain several items are comma separated. Public
 * review additionally requires the identity data flag {@value #KNOWN_REAL_PERSON_PROPERTY}.
 */
public class WorkflowParticipant implements Serializable {

  public static final String PUBLIC_GROUP = "PUBLIC";
  public static final String KNOWN_REAL_PERSON_PROPERTY = "workflow.knownRealPerson";
  public static final String ROLES_PROPERTY = "workflow.roles";
  public static final String PERMITTED_WORKFLOWS_PROPERTY = "workflow.permitted";
  public static final String DISALLOWED_TRANSITIONS_PROPERTY = "workflow.disallowedTransitions";
  public static final String MAX_RESPONSE_HOURS_PROPERTY = "workflow.maxResponseHours";

  private String identity;
  private String email;
  private Set<String> groups = new LinkedHashSet<>();
  private Set<WorkflowRole> roles = new LinkedHashSet<>();
  private Set<String> permittedWorkflows = new LinkedHashSet<>();
  private Set<String> disallowedTransitions = new LinkedHashSet<>();
  private Long maxResponseHours;
  private boolean knownRealPerson;

  public static WorkflowParticipant from(UserScope scope) {
    if (scope == null || scope.getUser() == null) {
      throw new IllegalArgumentException("A workflow operation requires an identified user scope");
    }
    var user = scope.getUser();
    var ret = new WorkflowParticipant();
    ret.identity = user.getUsername();
    ret.email = user.getEmailAddress();
    ret.knownRealPerson =
        user.isAuthenticated()
            && !user.isAnonymous()
            && ret.email != null
            && !ret.email.isBlank()
            && Boolean.parseBoolean(String.valueOf(user.getData().get(KNOWN_REAL_PERSON_PROPERTY)));

    Collection<Group> userGroups =
        user.getGroups() == null ? java.util.List.of() : user.getGroups();
    for (Group group : userGroups) {
      ret.groups.add(group.getId());
      var properties =
          group.getCustomProperties() == null
              ? java.util.List.<CustomProperty>of()
              : group.getCustomProperties();
      for (CustomProperty property : properties) {
        if (property == null || property.getKey() == null || property.getValue() == null) {
          continue;
        }
        if (ROLES_PROPERTY.equals(property.getKey())) {
          for (String role : split(property.getValue())) {
            ret.roles.add(WorkflowRole.valueOf(role.toUpperCase()));
          }
        } else if (PERMITTED_WORKFLOWS_PROPERTY.equals(property.getKey())) {
          ret.permittedWorkflows.addAll(split(property.getValue()));
        } else if (DISALLOWED_TRANSITIONS_PROPERTY.equals(property.getKey())) {
          ret.disallowedTransitions.addAll(split(property.getValue()));
        } else if (MAX_RESPONSE_HOURS_PROPERTY.equals(property.getKey())) {
          long value = Long.parseLong(property.getValue().trim());
          ret.maxResponseHours =
              ret.maxResponseHours == null ? value : Math.min(ret.maxResponseHours, value);
        }
      }
    }
    if (ret.roles.isEmpty() && ret.knownRealPerson) {
      ret.roles.add(WorkflowRole.REVIEWER);
    }
    return ret;
  }

  private static Set<String> split(String value) {
    var ret = new LinkedHashSet<String>();
    for (String item : value.split(",")) {
      if (!item.isBlank()) ret.add(item.trim());
    }
    return ret;
  }

  public boolean hasAnyRole(Set<WorkflowRole> admitted) {
    return roles.contains(WorkflowRole.ADMIN)
        || (admitted != null && admitted.stream().anyMatch(roles::contains));
  }

  /**
   * Whether group policy admits this workflow. Administrators and the {@code *} wildcard bypass the
   * explicit allow-list. Entries may use the stable workflow ID, {@code id@version}, complete
   * workflow URN, or human-readable workflow name.
   */
  public boolean isWorkflowPermitted(Workflow workflow) {
    if (workflow == null) return false;
    return roles.contains(WorkflowRole.ADMIN)
        || permittedWorkflows.contains("*")
        || permittedWorkflows.contains(workflow.getId())
        || permittedWorkflows.contains(workflow.getId() + "@" + workflow.getVersion())
        || permittedWorkflows.contains(workflow.getUrn())
        || (workflow.getName() != null && permittedWorkflows.contains(workflow.getName()));
  }

  /** ID-only permission check for clients that have not retrieved the workflow schema yet. */
  public boolean isWorkflowPermitted(String workflowId) {
    if (workflowId == null) return false;
    return roles.contains(WorkflowRole.ADMIN)
        || permittedWorkflows.contains("*")
        || permittedWorkflows.contains(workflowId)
        || permittedWorkflows.stream().anyMatch(value -> value.startsWith(workflowId + "@"));
  }

  /** Whether this participant's most restrictive group response deadline still admits action. */
  public boolean canRespondTo(Flow.State state) {
    return maxResponseHours == null
        || state == null
        || state.getCreatedAt() == null
        || Instant.now().isBefore(state.getCreatedAt().plus(maxResponseHours, ChronoUnit.HOURS));
  }

  public String getIdentity() {
    return identity;
  }

  public void setIdentity(String identity) {
    this.identity = identity;
  }

  public String getEmail() {
    return email;
  }

  public void setEmail(String email) {
    this.email = email;
  }

  public Set<String> getGroups() {
    return groups;
  }

  public void setGroups(Set<String> groups) {
    this.groups = groups == null ? new LinkedHashSet<>() : groups;
  }

  public Set<WorkflowRole> getRoles() {
    return roles;
  }

  public void setRoles(Set<WorkflowRole> roles) {
    this.roles = roles == null ? new LinkedHashSet<>() : roles;
  }

  public Set<String> getPermittedWorkflows() {
    return permittedWorkflows;
  }

  public void setPermittedWorkflows(Set<String> value) {
    this.permittedWorkflows = value == null ? new LinkedHashSet<>() : value;
  }

  public Set<String> getDisallowedTransitions() {
    return disallowedTransitions;
  }

  public void setDisallowedTransitions(Set<String> value) {
    this.disallowedTransitions = value == null ? new LinkedHashSet<>() : value;
  }

  public Long getMaxResponseHours() {
    return maxResponseHours;
  }

  public void setMaxResponseHours(Long maxResponseHours) {
    this.maxResponseHours = maxResponseHours;
  }

  public boolean isKnownRealPerson() {
    return knownRealPerson;
  }

  public void setKnownRealPerson(boolean knownRealPerson) {
    this.knownRealPerson = knownRealPerson;
  }
}
