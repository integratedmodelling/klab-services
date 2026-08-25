package org.integratedmodelling.klab.api.configuration;

import java.util.Map;
import java.util.concurrent.Future;

/**
 * Typed settings manager shared by engines, modelers, service implementations and service clients.
 * Existing values are loaded on construction, only explicit scalar values are persisted, and every
 * setting change returns a future for its effective value or operation result.
 */
public interface Settings {

  @FunctionalInterface
  interface ResultListener {
    /** Called when an operation setting completes and returns a result map. */
    void onResult(Setting setting, Map<String, Object> request, Map<String, Object> result);
  }

  /**
   * Retrieve a setting or its non-null default value.
   *
   * @param setting setting to retrieve
   * @param valueType the exact value type declared by the setting
   * @throws org.integratedmodelling.klab.api.exceptions.KlabIllegalArgumentException if the type
   *     or setting category is invalid
   */
  <T> T get(Setting setting, Class<T> valueType);

  /**
   * Validate and change a setting. Scalar changes are persisted; Map settings execute their
   * configured operation and complete with its result Map.
   */
  <T> Future<T> set(Setting setting, T value);

  /** Change and persist a setting only when it has never been explicitly stored. */
  void setIfUnset(Setting setting, Object value);

  /** Return all settings accepted by this manager, including defaults for unset values. */
  Map<String, Object> asMap();

  /** Return whether the setting has been explicitly stored instead of using its default. */
  boolean isSet(Setting setting);

  /** Register a callback for completed Map operation settings. */
  void addResultListener(ResultListener listener);
}
