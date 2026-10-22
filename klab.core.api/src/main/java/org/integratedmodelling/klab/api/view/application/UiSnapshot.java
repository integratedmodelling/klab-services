package org.integratedmodelling.klab.api.view.application;

import java.util.Objects;

/** Full tree replacement in protocol version 1; revisions increase within one UI instance. */
public record UiSnapshot(int protocolVersion, long revision, UiNode root) {
  public static final int PROTOCOL_VERSION = 1;

  public UiSnapshot {
    if (protocolVersion != PROTOCOL_VERSION || revision < 1) {
      throw new IllegalArgumentException("Unsupported UI protocol or revision");
    }
    Objects.requireNonNull(root);
  }
}
