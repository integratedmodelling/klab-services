package org.integratedmodelling.klab.components;

import java.security.MessageDigest;
import java.util.*;
import org.integratedmodelling.common.data.jackson.JacksonConfiguration;
import org.integratedmodelling.common.services.client.BaseServiceClient;
import org.integratedmodelling.klab.api.data.Version;
import org.integratedmodelling.klab.api.identities.UserIdentity;
import org.integratedmodelling.klab.api.knowledge.KlabAsset.KnowledgeClass;
import org.integratedmodelling.klab.api.knowledge.Worldview;
import org.integratedmodelling.klab.api.scope.Scope;
import org.integratedmodelling.klab.api.scope.UserScope;
import org.integratedmodelling.klab.api.services.ResourcesService;
import org.integratedmodelling.klab.api.services.resources.ResourceSet;
import org.integratedmodelling.klab.api.services.runtime.Notification;
import org.integratedmodelling.klab.api.services.runtime.extension.Extensions;

/** Descriptor admission over the existing COMPONENT resolver. Never installs/configures a provider. */
public final class AuthorityComponentResolver {
  public static final String REVISION_METADATA = "authority.catalog.revision";
  public static final String SELECTION_METADATA = "authority.selection.revision";
  private AuthorityComponentResolver() {}
  @com.fasterxml.jackson.annotation.JsonIgnoreProperties("updateable")
  private abstract static class ComponentWire {}
  private static final com.fasterxml.jackson.databind.ObjectMapper JSON =
      JacksonConfiguration.newObjectMapper().addMixIn(Extensions.ComponentDescriptor.class, ComponentWire.class)
          .enable(com.fasterxml.jackson.databind.SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);

  public record Resolution(List<Extensions.ComponentDescriptor> components, ResourceSet resources,
                           String revision, List<Notification> diagnostics) {
    public Resolution { components = List.copyOf(components); diagnostics = List.copyOf(diagnostics); }
  }

  /** Preserve installed precedence; resolve only authored authority requirements missing locally. */
  public static Resolution forWorldview(Worldview worldview,
      Collection<Extensions.ComponentDescriptor> installed, Scope scope) {
    return forWorldview(worldview, installed, scope, scope);
  }

  /** Discover through the host's service network while preserving request authorization. */
  public static Resolution forWorldview(Worldview worldview,
      Collection<Extensions.ComponentDescriptor> installed, Scope scope, Scope discoveryScope) {
    var local = installed.stream().map(AuthorityComponentResolver::copy).toList();
    var selected = new LinkedHashMap<String, Extensions.ComponentDescriptor>();
    local.forEach(component -> selected.put(identity(component), component));
    var requested = new TreeSet<String>();
    for (var declaration : worldview.allConceptStatements()) {
      if (declaration.getAuthorityRequired() == null || declaration.getAuthorityParameters() == null) continue;
      var urn = declaration.getAuthorityParameters().get("urn");
      if (urn instanceof String value && !value.isBlank()) requested.add(value);
    }
    var resources = new ResourceSet();
    var diagnostics = new ArrayList<Notification>();
    var revisions = new TreeMap<String, String>();
    for (var urn : requested) {
      // Leave local ambiguity to WorldviewAuthorityValidator; never override it with a remote hit.
      if (local.stream().anyMatch(component -> matches(component, urn, false))) continue;
      var resolved = resolve(urn, scope, discoveryScope);
      resolved.components().forEach(component -> selected.putIfAbsent(identity(component), component));
      resources.getResults().addAll(resolved.resources().getResults());
      resources.getServices().putAll(resolved.resources().getServices());
      diagnostics.addAll(resolved.diagnostics());
      revisions.put(urn, resolved.revision());
    }
    resources.setEmpty(resources.getResults().isEmpty());
    var components = selected.entrySet().stream().sorted(Map.Entry.comparingByKey()).map(Map.Entry::getValue).toList();
    return new Resolution(components, resources,
        digest(List.of("authority-admission-v1", components.stream().filter(component -> requested.stream().anyMatch(urn -> matches(component, urn, false)))
            .map(AuthorityComponentResolver::identity).toList(), revisions)), diagnostics);
  }

  /** Query supported COMPONENT resolution, not the unimplemented COMPONENT query branch. */
  public static Resolution resolve(String authorityUrn, Scope scope) {
    return resolve(authorityUrn, scope, scope);
  }

  private static Resolution resolve(String authorityUrn, Scope scope, Scope discoveryScope) {
    var owner = owner(scope);
    if (owner == null && !publicServiceScope(scope)) return new Resolution(List.of(), ResourceSet.empty(), digest("no-authorized-owner"),
        List.of(Notification.warning("AUTHORITY_METADATA_UNAVAILABLE: authenticated owner required")));
    var candidates = Collections.synchronizedMap(new TreeMap<String, Extensions.ComponentDescriptor>());
    var serviceDiagnostics = new java.util.concurrent.ConcurrentHashMap<String, List<Notification>>();
    var localServices = java.util.concurrent.ConcurrentHashMap.<String>newKeySet();
    var resources = org.integratedmodelling.klab.api.utils.Utils.Resources.queryResources(
        discoveryScope, ResourcesService.class, service -> {
          var diagnostics = new ArrayList<Notification>();
          serviceDiagnostics.put(service.serviceId(), diagnostics);
          if (org.integratedmodelling.klab.api.utils.Utils.URLs.isLocalHost(service.getUrl()))
            localServices.add(service.serviceId());
          try {
            var status = service.status();
            if (status == null || !status.isOperational()) return ResourceSet.empty();
            // This endpoint interprets a suffix as a COMPONENT version, not an authority version.
            // Discover by authority name, then enforce the requested authority version below.
            var response = service.resolve(Version.splitVersion(authorityUrn).getFirst(), KnowledgeClass.COMPONENT,
                scope instanceof UserScope user ? user : null);
            if (response == null || response.isEmpty()) return ResourceSet.empty();
            // A development SNAPSHOT can change bytes without changing its coordinates.
            // Refresh local descriptors on metadata polls, without loading provider code.
            if (service instanceof BaseServiceClient client && localServices.contains(service.serviceId()))
              client.invalidateCapabilities();
            var capabilities = service.capabilities(scope);
            var metadata = capabilities == null ? List.<Extensions.ComponentDescriptor>of() : capabilities.getComponents();
            // A successful resolution newer than cached capabilities warrants one metadata refresh.
            if (service instanceof BaseServiceClient client
                && matching(response, metadata, authorityUrn, service, owner).isEmpty()) {
              client.invalidateCapabilities();
              capabilities = service.capabilities(scope);
              metadata = capabilities == null ? List.of() : capabilities.getComponents();
            }
            if (ambiguous(metadata, authorityUrn, owner)) {
              diagnostics.add(Notification.error("Ambiguous authority provider descriptor at service " + service.serviceId()));
              return ResourceSet.empty();
            }
            var admitted = matching(response, metadata, authorityUrn, service, owner);
            var filtered = new ResourceSet();
            for (var component : admitted) {
              candidates.put(service.serviceId() + "|" + component.id() + "@" + component.version(), copy(component));
              // Construct owned provenance from the selected client, not arbitrary returned URLs.
              var resource = new ResourceSet.Resource(service.serviceId(), component.id() + "@" + component.version(), null,
                  component.version(), KnowledgeClass.COMPONENT, component.timestamp(), false);
              resource.setLocal(org.integratedmodelling.klab.api.utils.Utils.URLs.isLocalHost(service.getUrl()));
              // Installation consumes the URN suffix and these coordinates, not just resourceVersion.
              // Copy only descriptor-vetted coordinates; do not forward arbitrary response metadata.
              if (component.mavenCoordinates() != null)
                resource.getMetadata().put(Extensions.COMPONENT_MAVEN_COORDINATES_METADATA_KEY, component.mavenCoordinates());
              filtered.getResults().add(resource);
              filtered.getServices().put(service.serviceId(), service.getUrl());
            }
            filtered.setEmpty(filtered.getResults().isEmpty());
            if (filtered.isEmpty()) diagnostics.add(Notification.warning(
                "AUTHORITY_METADATA_UNAVAILABLE: resolver could not honor the exact authorized authority/component selection at service " + service.serviceId()));
            return filtered;
          } catch (RuntimeException unavailable) {
            // Exceptions can contain credentials or private provider parameters; do not echo them.
            diagnostics.add(Notification.warning("AUTHORITY_METADATA_UNAVAILABLE: component metadata lookup failed"));
            return ResourceSet.empty();
          }
        });
    // ResourceSet has already chosen provenance for identical component URNs. An eligible
    // local implementation also outranks remote implementations with other component URNs.
    if (resources.getResults().stream().anyMatch(ResourceSet.Resource::isLocal))
      resources.getResults().removeIf(resource -> !resource.isLocal());
    var selected = new TreeMap<String, Extensions.ComponentDescriptor>();
    var origins = new TreeSet<String>();
    var selectedServices = new HashSet<String>();
    for (var resource : resources.getResults()) {
      var component = candidates.get(resource.getServiceId() + "|" + resource.getResourceUrn());
      if (component != null) {
        selected.put(identity(component), component);
        selectedServices.add(resource.getServiceId());
        origins.add(resource.getServiceId() + "|" + resources.getServices().get(resource.getServiceId())
            + "|" + resource.getResourceUrn() + "|" + identity(component));
      }
    }
    var diagnostics = serviceDiagnostics.entrySet().stream()
        .filter(entry -> selectedServices.isEmpty() || selectedServices.contains(entry.getKey())
            || (localServices.contains(entry.getKey())
                && entry.getValue().stream().anyMatch(note -> note.getLevel() == Notification.Level.Error)))
        .sorted(Map.Entry.comparingByKey()).flatMap(entry -> entry.getValue().stream()).toList();
    var components = List.copyOf(selected.values());
    if (diagnostics.stream().anyMatch(note -> note.getLevel() == Notification.Level.Error)) {
      components = List.of(); resources = ResourceSet.empty();
    }
    return new Resolution(components, resources,
        digest(List.of("authority-resolution-v1", authorityUrn, ownerRevision(scope), List.copyOf(origins),
            components.stream().map(AuthorityComponentResolver::identity).toList())), diagnostics);
  }

  private static List<Extensions.ComponentDescriptor> matching(ResourceSet response,
      Collection<Extensions.ComponentDescriptor> descriptors, String urn, ResourcesService service,
      UserIdentity owner) {
    if (descriptors == null) return List.of();
    var declaredSource = response.getServices().get(service.serviceId());
    if (declaredSource == null || !sameUrl(declaredSource, service.getUrl())) return List.of();
    var result = new ArrayList<Extensions.ComponentDescriptor>();
    for (var resource : response.getResults()) {
      if (resource.getKnowledgeClass() != KnowledgeClass.COMPONENT
          || !Objects.equals(resource.getServiceId(), service.serviceId()) || resource.getResourceVersion() == null) continue;
      var coordinates = Version.splitVersion(resource.getResourceUrn());
      if (!Version.ANY_VERSION.equals(coordinates.getSecond())
          && !coordinates.getSecond().equals(resource.getResourceVersion())) continue;
      for (var advertised : descriptors) {
        if (!Objects.equals(advertised.id(), coordinates.getFirst())) continue;
        var descriptor = copy(advertised);
        if (!Objects.equals(descriptor.id(), coordinates.getFirst())
            || !Objects.equals(descriptor.version(), resource.getResourceVersion())
            || descriptor.fileHash() == null || descriptor.fileHash().isBlank()
            || !matches(descriptor, urn, true) || descriptor.usageRights() == null) continue;
        // ServiceScope is trusted for local access. It must not bypass a remote owner's exclusions.
        if (authorized(descriptor, owner)) result.add(descriptor);
      }
    }
    return result;
  }

  private static boolean ambiguous(Collection<Extensions.ComponentDescriptor> metadata, String urn, UserIdentity owner) {
    if (metadata == null) return false;
    return metadata.stream().map(AuthorityComponentResolver::copy)
        .filter(component -> matches(component, urn, true) && component.fileHash() != null && !component.fileHash().isBlank()
            && authorized(component, owner))
        .map(AuthorityComponentResolver::identity).distinct().limit(2).count() > 1;
  }

  /** Certificate-backed service ingestion selects public providers without impersonating a user. */
  public static boolean publicServiceScope(Scope scope) {
    return scope instanceof org.integratedmodelling.klab.api.scope.ServiceScope
        && !(scope instanceof UserScope)
        && scope.getIdentity() != null
        && !(scope.getIdentity() instanceof UserIdentity);
  }

  private static boolean authorized(Extensions.ComponentDescriptor component, UserIdentity owner) {
    var rights = component.usageRights();
    return rights != null && (owner == null ? rights.isPublic()
        : rights.checkAuthorization(owner.getUsername(), owner.getGroups() == null ? List.of() : owner.getGroups()));
  }

  private static boolean matches(Extensions.ComponentDescriptor component, String urn, boolean embeddable) {
    try {
      var coordinates = Version.splitVersion(urn);
      return component.authorities().stream().anyMatch(authority ->
          coordinates.getFirst().equals(authority.urn()) && (!embeddable || authority.embeddable())
              && (!urn.contains("@") || coordinates.getSecond().equals(authority.version())));
    } catch (RuntimeException malformed) { return false; }
  }

  private static boolean sameUrl(java.net.URL first, java.net.URL second) {
    return second != null && first.toExternalForm().replaceFirst("/+$", "")
        .equals(second.toExternalForm().replaceFirst("/+$", ""));
  }

  private static UserIdentity owner(Scope scope) {
    if (scope == null) return null;
    var user = scope instanceof UserScope userScope ? userScope.getUser()
        : scope.getIdentity() instanceof UserIdentity identity ? identity : null;
    return user == null || user.isAnonymous() || user.getUsername() == null || user.getUsername().isBlank()
        || user.getId() == null || user.getId().isBlank() ? null : user;
  }

  public static String ownerRevision(Scope scope) {
    var user = owner(scope);
    if (user != null) return ownerRevision(user);
    return publicServiceScope(scope)
        ? digest(Arrays.asList("public-service-ingestion", scope.getIdentity().getId()))
        : digest("no-authorized-owner");
  }

  private static String ownerRevision(UserIdentity user) {
    var groups = user.getGroups() == null ? List.<String>of()
        : user.getGroups().stream().map(org.integratedmodelling.klab.api.identities.Group::getName).sorted().toList();
    return digest(Arrays.asList(user.getId(), user.getUsername(), groups));
  }

  private static String identity(Extensions.ComponentDescriptor component) {
    // Exclude paths, timestamps and update-check status; mirrors with the same artifact are one candidate.
    var rights = component.usageRights();
    Object permissions = rights == null ? null : List.of(rights.isPublic(),
        new TreeSet<>(rights.getAllowedUsers()), new TreeSet<>(rights.getExcludedUsers()),
        new TreeSet<>(rights.getAllowedGroups()), new TreeSet<>(rights.getExcludedGroups()));
    return digest(Arrays.asList(component.id(), component.version(), component.fileHash(), component.authorities(), permissions));
  }

  private static Extensions.ComponentDescriptor copy(Extensions.ComponentDescriptor descriptor) {
    // Authority admission needs owned descriptor/provenance data, not runtime extension payloads.
    // Adapter and function metadata can contain polymorphic KlabAsset objects whose wire-only
    // @CLASS fields cannot be roundtripped as their concrete types by convertValue.lss
    var authorityMetadata = new Extensions.ComponentDescriptor(
        descriptor.id(), descriptor.version(), descriptor.description(), descriptor.sourceArchive(),
        descriptor.fileHash(), descriptor.mavenCoordinates(), descriptor.usageRights(),
        List.of(), List.of(), descriptor.authorities(),
        Map.of(), Map.of(), Map.of(), Map.of(), Map.of(),
        descriptor.sourceServiceId(), descriptor.timestamp(), descriptor.importType(),
        descriptor.updateStatus(), descriptor.latestVersionTimestamp());
    return JSON.convertValue(authorityMetadata, Extensions.ComponentDescriptor.class);
  }

  private static String digest(Object value) {
    try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(JSON.writeValueAsBytes(value))); }
    catch (Exception failure) { throw new IllegalArgumentException("Cannot fingerprint authority metadata", failure); }
  }
}
