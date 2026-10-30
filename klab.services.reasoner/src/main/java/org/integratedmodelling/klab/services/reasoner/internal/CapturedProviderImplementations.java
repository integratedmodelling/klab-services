package org.integratedmodelling.klab.services.reasoner.internal;

import io.github.classgraph.ClassGraph;
import java.nio.file.Path;
import java.util.*;
import org.integratedmodelling.klab.api.services.runtime.extension.Extensions.ComponentDescriptor;

/** Discover implementations from the exact verified archive, never the mutable service classpath. */
final class CapturedProviderImplementations {
  private CapturedProviderImplementations() {}
  private static final Map<String, String> LEGACY_CACHE_REPLAY = Map.of(
      "klab.authority.taxa", "org.integratedmodelling.klab.authority.taxa.TaxaAuthority",
      "klab.authority.pubchem", "org.integratedmodelling.klab.authority.pubchem.PubChemAuthority");

  static boolean legacy(ComponentDescriptor component, String implementation) {
    return implementation.equals(LEGACY_CACHE_REPLAY.get(component.id()));
  }

  static List<String> discover(Path archive, ComponentDescriptor component) {
    var result = new TreeMap<String, String>();
    String annotation = org.integratedmodelling.klab.api.services.reasoner.Authority.class.getName();
    try (var scan = new ClassGraph().enableClassInfo().enableAnnotationInfo()
        .overrideClasspath(archive.toFile()).ignoreParentClassLoaders()
        .disableNestedJarScanning().disableModuleScanning()
        .filterClasspathElements(path -> {
          try { return Path.of(path).toAbsolutePath().normalize().equals(archive.toAbsolutePath().normalize()); }
          catch (RuntimeException invalid) { return false; }
        }).scan()) {
      for (var type : scan.getClassesWithAnnotation(annotation)) {
        var parameters = type.getAnnotationInfo(annotation).getParameterValues();
        String urn = (String) parameters.getValue("urn");
        String version = (String) parameters.getValue("version");
        if (component.authorities().stream().noneMatch(a -> a.urn().equals(urn) && a.version().toString().equals(version)))
          throw new IllegalArgumentException("Captured authority class differs from advertised descriptor");
        if (result.putIfAbsent(urn, type.getName()) != null)
          throw new IllegalArgumentException("Captured authority implementation is ambiguous");
      }
    }
    if (result.size() != component.authorities().size())
      throw new IllegalArgumentException("Captured archive has no exact authority implementations");
    return List.copyOf(result.values());
  }
}
