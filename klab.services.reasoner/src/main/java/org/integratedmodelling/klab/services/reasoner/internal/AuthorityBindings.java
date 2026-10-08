package org.integratedmodelling.klab.services.reasoner.internal;

import java.util.LinkedHashMap;
import java.util.Map;
import org.integratedmodelling.klab.api.exceptions.KlabValidationException;
import org.integratedmodelling.klab.api.services.Authority;
import org.integratedmodelling.klab.api.knowledge.Worldview;

/** Configured bridges owned by one Reasoner's loaded worldview. Provider state stays in the provider. */
public final class AuthorityBindings {
  public record Binding(Authority.ConfigurationRequest request, Authority provider, String id) {}

  private final Map<String, Binding> bindings = new LinkedHashMap<>();
  private final java.nio.file.Path cacheRoot;
  private final Map<String, AuthorityCodelists> codelists = new LinkedHashMap<>();

  /** Without a cache root, use providers directly (e.g. isolated unit tests). */
  public AuthorityBindings() { this.cacheRoot = null; }

  /** Production bridges persist successful provider results beneath the Reasoner's data directory. */
  public AuthorityBindings(java.nio.file.Path cacheRoot) {
    this.cacheRoot = java.util.Objects.requireNonNull(cacheRoot);
  }

  /** Accept the loaded instance ID for compatibility, but persist the stable worldview name. */
  public static Authority.ConfigurationRequest forWorldview(
      Authority.ConfigurationRequest request, Worldview worldview) {
    if (worldview == null || worldview.getUrn() == null || worldview.getUrn().isBlank()
        || (!request.worldview().equals(worldview.getUrn())
            && !request.worldview().equals(worldview.getWorldviewId()))) {
      throw new KlabValidationException("Authority bridge must use the loaded worldview");
    }
    return new Authority.ConfigurationRequest(worldview.getUrn(), request.name(),
        request.rootIdentity(), request.parameters());
  }

  public synchronized String configure(Authority.ConfigurationRequest request, Authority provider) {
    var previous = bindings.get(request.name());
    if (previous != null) {
      if ((previous.provider() == provider || (previous.provider() instanceof CachedAuthority cached && cached.wraps(provider)))
          && previous.request().equals(request)) return previous.id();
      throw new KlabValidationException("Duplicate local authority name: " + request.name());
    }
    var capabilities = provider.getCapabilities();
    if (capabilities != null && capabilities.getWorldview() != null
        && !request.worldview().equals(capabilities.getWorldview()))
      throw new KlabValidationException("Authority is incompatible with worldview " + request.worldview());
    Authority hosted = cacheRoot == null ? provider : new CachedAuthority(provider, cacheRoot);
    String id = hosted.configure(cacheRoot == null ? providerRequest(request) : request);
    if (id == null || id.isBlank())
      throw new KlabValidationException("Authority returned an empty configuration ID: " + request.name());
    var binding = new Binding(request, hosted, id);
    try {
      var lists = new AuthorityCodelists(binding, cacheRoot == null ? null : cacheRoot.resolveSibling("authority-codelists"));
      for (var existing : codelists.values())
        for (var namespace : lists.namespaces())
          if (existing.namespaces().contains(namespace)) throw new KlabValidationException("Duplicate codelist namespace " + namespace);
      codelists.put(request.name(), lists);
      bindings.put(request.name(), binding);
    } catch (RuntimeException failure) { hosted.releaseConfiguration(id); throw failure; }
    return id;
  }

  /** Namespace bindings belong to the Reasoner, not to the provider's parameter schema. */
  public static Authority.ConfigurationRequest providerRequest(Authority.ConfigurationRequest request) {
    var parameters = new LinkedHashMap<>(request.parameters());
    parameters.remove("codelists");
    return new Authority.ConfigurationRequest(request.worldview(), request.name(),
        request.rootIdentity(), parameters);
  }

  /** Search only exact bindings. Dotted filter aliases must be supplied as explicit filters. */
  public synchronized org.integratedmodelling.klab.api.services.reasoner.objects.AuthoritySearchResponse search(
      org.integratedmodelling.klab.api.services.reasoner.objects.AuthoritySearchRequest request) {
    var binding = get(request.authority());
    var status = org.integratedmodelling.klab.api.services.reasoner.objects.AuthoritySearchResponse.Status.UNAVAILABLE;
    if (binding == null) return org.integratedmodelling.klab.api.services.reasoner.objects.AuthoritySearchResponse
        .failure(status, "Authority is not configured");
    try {
      var capabilities = binding.provider().getCapabilities();
      var lists = codelists.get(request.authority());
      boolean searchable = capabilities != null && capabilities.isSearchable();
      if (!searchable && (lists == null || lists.namespaces().isEmpty()))
        return org.integratedmodelling.klab.api.services.reasoner.objects.AuthoritySearchResponse.failure(
            org.integratedmodelling.klab.api.services.reasoner.objects.AuthoritySearchResponse.Status.UNSUPPORTED,
            "This authority does not support search");
      boolean listFilter = lists != null && request.filter() != null && lists.namespaces().contains(request.filter());
      if (request.filter() != null && !listFilter && (capabilities == null || !capabilities.areSubAuthoritiesSearchFilters()
          || capabilities.getSubAuthorities() == null || capabilities.getSubAuthorities().stream()
              .noneMatch(pair -> request.filter().equals(pair.getFirst()))))
        return org.integratedmodelling.klab.api.services.reasoner.objects.AuthoritySearchResponse.failure(
            org.integratedmodelling.klab.api.services.reasoner.objects.AuthoritySearchResponse.Status.UNSUPPORTED,
            "The authority does not advertise this search filter");
      var found = (listFilter || !searchable) ? new java.util.ArrayList<Authority.Identity>()
          : binding.provider().search(request.query(), request.filter(), binding.id());
      if (found == null) throw new IllegalStateException("Provider returned no search response");
      var unique = new java.util.LinkedHashMap<String, Authority.Identity>();
      for (var identity : found) {
        if (identity == null || identity.getId() == null || identity.getId().isBlank())
          throw new IllegalStateException("Provider returned an invalid candidate");
        unique.putIfAbsent(identity.getId(), identity);
      }
      if (request.filter() == null) {
        try {
          var exact = binding.provider().resolveIdentity(binding.id(), request.query());
          if (exact != null && exact.getId() != null
              && !org.integratedmodelling.klab.api.utils.Utils.Notifications.hasErrors(exact.getNotifications()))
            unique.putIfAbsent(exact.getId(), exact);
        } catch (RuntimeException ignored) { /* A free-text query need not be a valid authority code. */ }
      }
      var aliases = new java.util.LinkedHashMap<String, java.util.List<String>>();
      if (lists != null) {
        String query = request.query().toLowerCase(java.util.Locale.ROOT);
        for (var entry : lists.snapshot().codelists().entrySet()) {
          for (var alias : entry.getValue().codes()) {
            String code = (String) entry.getValue().value(alias);
            aliases.computeIfAbsent(code, key -> new java.util.ArrayList<>()).add(entry.getKey() + ":" + alias);
            if (listFilter && !entry.getKey().equals(request.filter())) continue;
            if (request.filter() != null && !listFilter) continue;
            var identity = binding.provider().resolveIdentity(binding.id(), code);
            if (identity == null || org.integratedmodelling.klab.api.utils.Utils.Notifications.hasErrors(identity.getNotifications())) continue;
            if ((entry.getKey() + ":" + alias).toLowerCase(java.util.Locale.ROOT).contains(query)
                || code.toLowerCase(java.util.Locale.ROOT).contains(query)
                || (identity.getLabel() != null && identity.getLabel().toLowerCase(java.util.Locale.ROOT).contains(query)))
              unique.putIfAbsent(identity.getId(), identity);
          }
        }
      }
      var candidates = new java.util.ArrayList<>(unique.values());
      int from = Math.min(request.offset(), candidates.size());
      int end = Math.min(from + request.limit(), candidates.size());
      var matches = new java.util.ArrayList<org.integratedmodelling.klab.api.services.resources.objects.AuthorityIdentity>();
      for (var identity : candidates.subList(from, end)) {
        var copy = new org.integratedmodelling.klab.api.services.resources.objects.AuthorityIdentity();
        copy.setAliases(aliases.getOrDefault(identity.getId(), java.util.List.of()));
        copy.setId(identity.getId()); copy.setAuthorityName(binding.request().name());
        copy.setLocator(org.integratedmodelling.klab.api.services.reasoner.objects.AuthorityIdentitySyntax
            .encode(binding.request().name(), identity.getId()));
        copy.setLabel(identity.getLabel()); copy.setDescription(identity.getDescription());
        copy.setScore(identity.getScore()); copy.setConceptName(identity.getConceptName());
        copy.setNotifications(identity.getNotifications() == null ? java.util.List.of()
            : new java.util.ArrayList<>(identity.getNotifications()));
        // Provider-local files and configuration details do not belong in discovery results.
        matches.add(copy);
      }
      return new org.integratedmodelling.klab.api.services.reasoner.objects.AuthoritySearchResponse(
          org.integratedmodelling.klab.api.services.reasoner.objects.AuthoritySearchResponse.Status.OK,
          matches, candidates.size(), end < candidates.size() && end <= 10000 ? end : -1, java.util.List.of());
    } catch (UnsupportedOperationException e) {
      return org.integratedmodelling.klab.api.services.reasoner.objects.AuthoritySearchResponse.failure(
          org.integratedmodelling.klab.api.services.reasoner.objects.AuthoritySearchResponse.Status.UNSUPPORTED,
          "This authority does not support search");
    } catch (RuntimeException e) {
      return org.integratedmodelling.klab.api.services.reasoner.objects.AuthoritySearchResponse.failure(
          org.integratedmodelling.klab.api.services.reasoner.objects.AuthoritySearchResponse.Status.FAILED,
          "Authority provider search failed");
    }
  }

  public synchronized AuthorityCodelists codelists(String authority) {
    var ret = codelists.get(authority);
    if (ret == null) throw new java.util.NoSuchElementException("Authority is not configured");
    return ret;
  }

  public synchronized Map<String, Long> codelistRevisions() {
    var ret = new LinkedHashMap<String, Long>();
    codelists.forEach((name, lists) -> ret.put(name, lists.revision()));
    return Map.copyOf(ret);
  }

  public synchronized String[] alias(String namespace, String code) {
    for (var entry : codelists.entrySet()) {
      if (entry.getValue().namespaces().contains(namespace)) {
        String canonical = entry.getValue().resolve(namespace, code);
        if (canonical == null) throw new KlabValidationException("Unknown or unapproved codelist alias " + namespace + ":" + code);
        return new String[] {entry.getKey(), canonical};
      }
    }
    return null;
  }

  public synchronized Binding get(String name) { return bindings.get(name); }

  /** Exact dotted bindings take precedence over advertised search-only rank aliases. */
  public synchronized Binding find(String name) {
    if (name == null) return null;
    var exact = bindings.get(name);
    if (exact != null) return exact;
    int separator = name.lastIndexOf('.');
    if (separator <= 0) return null;
    var base = bindings.get(name.substring(0, separator));
    if (base == null) return null;
    var capabilities = base.provider().getCapabilities();
    String suffix = name.substring(separator + 1);
    if (capabilities != null && capabilities.areSubAuthoritiesSearchFilters()
        && capabilities.getSubAuthorities() != null
        && capabilities.getSubAuthorities().stream().anyMatch(pair -> suffix.equals(pair.getFirst()))) {
      return base;
    }
    return null;
  }

  /** Resolve provider data without creating semantic concepts or exposing configuration IDs. */
  public synchronized Map<String, java.net.URL> documentation(String name, String id) {
    if (name == null || name.isBlank() || id == null || id.isBlank())
      throw new IllegalArgumentException("Authority name and identity ID are required");
    var alias = alias(name, id);
    if (alias != null) { name = alias[0]; id = alias[1]; }
    var binding = find(name);
    if (binding == null) throw new java.util.NoSuchElementException("Authority is not configured");
    var identity = binding.provider().resolveIdentity(binding.id(), id);
    if (identity == null || (identity.getNotifications() != null && identity.getNotifications().stream()
        .anyMatch(n -> n.getLevel() == org.integratedmodelling.klab.api.services.runtime.Notification.Level.Error))) {
      throw new java.util.NoSuchElementException("Authority identity is unavailable");
    }
    return identity.getDocumentation() == null ? Map.of() : Map.copyOf(identity.getDocumentation());
  }

  public synchronized java.util.List<Binding> snapshot() { return java.util.List.copyOf(bindings.values()); }

  /** Internal ontology names isolate worldview-local namespaces and avoid punctuation collisions. */
  public static String ontologyId(Binding binding) {
    return "auth_" + java.util.UUID.nameUUIDFromBytes(
        (binding.request().worldview() + "\u0000" + binding.request().name())
            .getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString().replace("-", "");
  }

  public synchronized void releaseNamespace(String namespace) {
    var iterator = bindings.values().iterator();
    while (iterator.hasNext()) {
      var binding = iterator.next();
      if (binding.request().rootIdentity().startsWith(namespace + ":")) {
        codelists.remove(binding.request().name());
        iterator.remove();
        binding.provider().releaseConfiguration(binding.id());
      }
    }
  }

  public synchronized void clear() {
    var previous = new java.util.ArrayList<>(bindings.values());
    bindings.clear();
    codelists.clear();
    RuntimeException failure = null;
    for (var binding : previous) {
      try { binding.provider().releaseConfiguration(binding.id()); }
      catch (RuntimeException e) {
        if (failure == null) failure = e; else failure.addSuppressed(e);
      }
    }
    if (failure != null) throw failure;
  }
}
