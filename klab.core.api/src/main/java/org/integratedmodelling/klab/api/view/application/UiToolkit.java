package org.integratedmodelling.klab.api.view.application;

import java.util.Set;
import java.util.function.Consumer;

/**
 * Renderer SPI, independent of Spring, Vue and JavaFX. Component discovery and configuration will
 * supply toolkit instances; this interface does not perform classpath discovery or authorization.
 */
public interface UiToolkit {
  /** Stable configuration identifier, e.g. quasar or javafx-test. */
  String id();

  /** Canonical node types supported by this adapter; unsupported types fail before presentation. */
  Set<String> supportedTypes();

  /**
   * Open a renderer for one application instance. The callback must only be invoked for user input,
   * never synchronously from open, render or close. Those methods must not wait for event dispatch.
   * Adapters marshal rendering to their UI thread as necessary.
   */
  View open(Consumer<UiEvent> input);

  interface View extends AutoCloseable {
    /** Ordered full replacement. An exception detaches the renderer; server state survives. */
    void render(UiSnapshot snapshot);

    /** Release all renderer resources. */
    @Override
    void close();
  }
}
