package org.integratedmodelling.klab.runtime.kactors.view;

import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import org.integratedmodelling.klab.api.view.application.UiToolkitDescriptor;

/**
 * Pins a component contribution until the owning application is stopped, including while detached.
 */
public final class UiToolkitLease implements AutoCloseable {
  public static final UiToolkitDescriptor QUASAR =
      new UiToolkitDescriptor(
          "quasar", 1, Set.of("web"), ApplicationUiModel.TYPES, Set.of("row", "column"), null);
  private final UiToolkitDescriptor descriptor;
  private final String moduleUrl;
  private final Runnable release;
  private final AtomicBoolean closed = new AtomicBoolean();

  public UiToolkitLease(UiToolkitDescriptor descriptor, String moduleUrl, Runnable release) {
    this.descriptor = java.util.Objects.requireNonNull(descriptor);
    this.moduleUrl = moduleUrl;
    this.release = java.util.Objects.requireNonNull(release);
  }

  public static UiToolkitLease quasar() {
    return new UiToolkitLease(QUASAR, null, () -> {});
  }

  public UiToolkitDescriptor descriptor() {
    return descriptor;
  }

  public String moduleUrl() {
    return moduleUrl;
  }

  public void close() {
    if (closed.compareAndSet(false, true)) release.run();
  }
}
