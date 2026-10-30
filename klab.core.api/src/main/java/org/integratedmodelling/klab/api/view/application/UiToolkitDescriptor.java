package org.integratedmodelling.klab.api.view.application;

import java.util.Set;

/** Immutable live-renderer capabilities. Module IDs refer to component-declared webUiModules. */
public record UiToolkitDescriptor(
    String id,
    int protocolVersion,
    Set<String> platforms,
    Set<String> types,
    Set<String> layouts,
    String rendererModule) {
  public UiToolkitDescriptor {
    if (id == null || !id.matches("[a-z][a-z0-9-]*"))
      throw new IllegalArgumentException("Invalid toolkit ID");
    if (protocolVersion != UiSnapshot.PROTOCOL_VERSION)
      throw new IllegalArgumentException("Unsupported toolkit protocol");
    platforms = Set.copyOf(platforms);
    types = Set.copyOf(types);
    layouts = Set.copyOf(layouts);
    if (platforms.isEmpty() || types.isEmpty() || layouts.isEmpty())
      throw new IllegalArgumentException("Empty toolkit capabilities");
    if (rendererModule != null && rendererModule.isBlank())
      throw new IllegalArgumentException("Empty renderer module ID");
  }

  public void validate(UiNode node) {
    if (!types.contains(node.type()))
      throw new IllegalArgumentException("Toolkit " + id + " does not support " + node.type());
    Object layout = node.properties().get("layout");
    if (layout != null && !layouts.contains(layout))
      throw new IllegalArgumentException("Toolkit " + id + " does not support layout " + layout);
    node.children().forEach(this::validate);
  }
}
