package org.integratedmodelling.klab.services.reasoner.internal;

import java.util.LinkedHashMap;
import java.util.Map;
import org.integratedmodelling.klab.api.exceptions.KlabValidationException;
import org.integratedmodelling.klab.api.services.Authority;

/** Configured bridges owned by one Reasoner's loaded worldview. Provider state stays in the provider. */
public final class AuthorityBindings {
  public record Binding(Authority.ConfigurationRequest request, Authority provider, String id) {}

  private final Map<String, Binding> bindings = new LinkedHashMap<>();

  public synchronized String configure(Authority.ConfigurationRequest request, Authority provider) {
    var previous = bindings.get(request.name());
    if (previous != null) {
      if (previous.provider() == provider && previous.request().equals(request)) return previous.id();
      throw new KlabValidationException("Duplicate local authority name: " + request.name());
    }
    var capabilities = provider.getCapabilities();
    if (capabilities != null && capabilities.getWorldview() != null
        && !request.worldview().equals(capabilities.getWorldview()))
      throw new KlabValidationException("Authority is incompatible with worldview " + request.worldview());
    String id = provider.configure(request);
    if (id == null || id.isBlank())
      throw new KlabValidationException("Authority returned an empty configuration ID: " + request.name());
    bindings.put(request.name(), new Binding(request, provider, id));
    return id;
  }

  public synchronized Binding get(String name) { return bindings.get(name); }

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
        iterator.remove();
        binding.provider().releaseConfiguration(binding.id());
      }
    }
  }

  public synchronized void clear() {
    var previous = new java.util.ArrayList<>(bindings.values());
    bindings.clear();
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
