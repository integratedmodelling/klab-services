package org.integratedmodelling.klab.services.resources.lang;

import java.util.*;
import org.integratedmodelling.klab.api.data.Version;
import org.integratedmodelling.klab.api.knowledge.*;
import org.integratedmodelling.klab.api.knowledge.impl.WorldviewImpl;
import org.integratedmodelling.klab.api.services.Authority;
import org.integratedmodelling.klab.api.services.runtime.Notification;
import org.integratedmodelling.klab.api.services.runtime.extension.Extensions;

/** Builds an entire replacement snapshot without invoking provider code or exposing parameters. */
public final class WorldviewAuthorityValidator {
  private WorldviewAuthorityValidator() {}
  private record Candidate(Extensions.ComponentDescriptor component,
                           Extensions.AuthorityDescriptor descriptor) {}

  public static void validate(WorldviewImpl worldview,
      Collection<Extensions.ComponentDescriptor> components) {
    var bindings = new LinkedHashMap<String, Worldview.AuthorityBinding>();
    var seen = new HashSet<String>();
    var conflicts = new HashSet<String>();
    var namespaces = new HashSet<String>();
    worldview.getOntologies().forEach(o -> namespaces.add(o.getUrn()));
    for (var statement : worldview.allConceptStatements()) {
      String name = statement.getAuthorityRequired();
      if (name == null) continue;
      String root = statement.getNamespace() + ":" + statement.getUrn();
      try {
        if (!seen.add(name)) {
          conflicts.add(name);
          throw new IllegalArgumentException("Conflicting local authority name " + name);
        }
        if (!statement.getType().contains(SemanticType.IDENTITY)
            || statement.getType().contains(SemanticType.NOTHING)
            || org.integratedmodelling.klab.api.utils.Utils.Notifications.hasErrors(statement.getNotifications()))
          throw new IllegalArgumentException("Authority root must be a valid identity concept");
        var request = new Authority.ConfigurationRequest(worldview.getUrn(), name, root,
            statement.getAuthorityParameters());
        if (request.parameters().containsKey("codelists")) {
          if (!(request.parameters().get("codelists") instanceof Map<?, ?> lists))
            throw new IllegalArgumentException("codelists must map provider IDs to local namespaces");
          for (var entry : lists.entrySet()) {
            if (!(entry.getKey() instanceof String id) || id.isBlank()
                || !(entry.getValue() instanceof String namespace)
                || !namespace.matches("[a-z][a-z0-9_]*(\\.[a-z][a-z0-9_]*)*"))
              throw new IllegalArgumentException("Invalid codelist namespace binding");
            if (!namespaces.add(namespace)) throw new IllegalArgumentException("Conflicting codelist namespace " + namespace);
          }
        }
        String urn = (String) request.parameters().get("urn");
        org.integratedmodelling.klab.api.collections.Pair<String, Version> coordinates;
        try { coordinates = Version.splitVersion(urn); }
        catch (RuntimeException e) { throw new IllegalArgumentException("Invalid authority provider version"); }
        var candidates = new ArrayList<Candidate>();
        for (var component : components) {
          for (var descriptor : component.authorities()) {
            if (coordinates.getFirst().equals(descriptor.urn())
                && (!urn.contains("@") || coordinates.getSecond().equals(descriptor.version())))
              candidates.add(new Candidate(component, descriptor));
          }
        }
        if (candidates.size() != 1)
          throw new IllegalArgumentException(candidates.isEmpty()
              ? "Missing authority provider descriptor" : "Ambiguous authority provider descriptor");
        var selected = candidates.getFirst();
        bindings.put(name, new Worldview.AuthorityBinding(name, root, statement.getNamespace(),
            statement.getOffsetInDocument(), statement.getLength(),
            worldview.getOntologies().stream().filter(o -> o.getUrn().equals(statement.getNamespace()))
                .map(o -> org.integratedmodelling.klab.api.services.reasoner.objects.SemanticValidationRequest
                    .sourceHash(o.getSourceCode())).filter(Objects::nonNull).findFirst().orElse(null),
            selected.descriptor(),
            selected.component().id(), selected.component().version(), null));
      } catch (IllegalArgumentException e) {
        worldview.getNotifications().add(Notification.error(
            "Authority " + name + " at " + root + ": " + e.getMessage()));
        worldview.setEmpty(true);
      }
    }
    conflicts.forEach(bindings::remove);
    worldview.setAuthorityBindings(new ArrayList<>(bindings.values()));
  }
}
