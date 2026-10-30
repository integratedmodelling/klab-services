package org.integratedmodelling.klab.services.reasoner.internal;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import org.integratedmodelling.klab.api.configuration.Setting;
import org.integratedmodelling.klab.api.configuration.Settings;

/** Private defaults only; semantic actors must never open deployment settings files. */
public final class SemanticExecutionSettings implements Settings {
  @Override public <T> T get(Setting setting, Class<T> type) {
    if (type != setting.valueClass) throw new IllegalArgumentException("Incorrect setting type");
    return type.cast(setting.defaultValue);
  }
  @Override public <T> Future<T> set(Setting setting, T value) {
    return CompletableFuture.failedFuture(new UnsupportedOperationException("Semantic settings are read-only"));
  }
  @Override public void setIfUnset(Setting setting, Object value) {
    throw new UnsupportedOperationException("Semantic settings are read-only");
  }
  @Override public Map<String, Object> asMap() {
    var values = new LinkedHashMap<String, Object>();
    for (var setting : Setting.values()) values.put(setting.name(), setting.defaultValue);
    return Collections.unmodifiableMap(values);
  }
  @Override public boolean isSet(Setting setting) { return false; }
  @Override public void addResultListener(ResultListener listener) {
    throw new UnsupportedOperationException("Semantic settings execute no deployment actions");
  }
}
