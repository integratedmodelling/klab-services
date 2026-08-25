package org.integratedmodelling.common.services.client.engine;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Function;
import org.integratedmodelling.common.logging.Logging;
import org.integratedmodelling.common.services.client.BaseServiceClient;
import org.integratedmodelling.common.utils.Utils;
import org.integratedmodelling.klab.api.configuration.Configuration;
import org.integratedmodelling.klab.api.configuration.Setting;
import org.integratedmodelling.klab.api.configuration.Settings;
import org.integratedmodelling.klab.api.exceptions.KlabIllegalArgumentException;
import org.integratedmodelling.klab.api.services.KlabService;

/** The persistent or REST-backed implementation of the k.LAB settings contract. */
public class SettingsImpl implements Settings {

  private final Properties properties = new Properties();
  private final ExecutorService executor =
      Executors.newSingleThreadExecutor(
          task -> {
            var thread = new Thread(task, "klab-settings");
            thread.setDaemon(true);
            return thread;
          });
  private final Map<Setting, Function<Object, Object>> executionHandlers = new LinkedHashMap<>();
  private final List<ResultListener> resultListeners = new ArrayList<>();
  private final KlabService service;
  private final KlabService.Type serviceType;
  private final File settingsFile;
  private final boolean remote;
  private boolean remoteLoaded;

  public static Settings forService(KlabService service, KlabService.Type type) {
    if (service == null || type == null) {
      throw new KlabIllegalArgumentException("A service and its type are required for settings");
    }
    return new SettingsImpl(service, type);
  }

  /** Client/runtime settings used while a service owner is authenticating or locating peers. */
  public static Settings forServiceOwner(KlabService.Type type) {
    return new SettingsImpl(
        Configuration.INSTANCE.getFileWithTemplate(
            "services/" + type.name().toLowerCase() + "/client.properties", ""));
  }

  public static Settings forEngine() {
    return new SettingsImpl("engine");
  }

  public static Settings forSlaveServices(KlabService.Type serviceType, Settings settings) {
    return forServiceOwner(serviceType);
  }

  private SettingsImpl(KlabService service, KlabService.Type serviceType) {
    this.service = service;
    this.serviceType = serviceType;
    this.remote = service instanceof BaseServiceClient;
    this.settingsFile =
        remote
            ? null
            : Configuration.INSTANCE.getFileWithTemplate(
                "services/" + serviceType.name().toLowerCase() + "/settings.properties", "");
    loadLocal();
  }

  private SettingsImpl(String settingsFileName) {
    this.service = null;
    this.serviceType = null;
    this.remote = false;
    this.settingsFile =
        Configuration.INSTANCE.getFileWithTemplate(settingsFileName + ".properties", "");
    loadLocal();
  }

  SettingsImpl(File settingsFile) {
    this.service = null;
    this.serviceType = null;
    this.remote = false;
    this.settingsFile = settingsFile;
    loadLocal();
  }

  private void loadLocal() {
    if (settingsFile != null && !Utils.Properties.load(settingsFile, properties)) {
      Logging.INSTANCE.error("Error loading settings file: " + settingsFile);
    }
  }

  private boolean accepts(Setting setting) {
    return serviceType == null || setting.appliesTo(serviceType);
  }

  private void requireAccepted(Setting setting) {
    if (setting == null || !accepts(setting)) {
      throw new KlabIllegalArgumentException(
          "Setting " + setting + " does not belong to " + serviceType + " settings");
    }
  }

  @Override
  public synchronized <T> T get(Setting setting, Class<T> valueType) {
    requireAccepted(setting);
    if (valueType == null || !valueType.equals(setting.valueClass)) {
      throw new KlabIllegalArgumentException(
          "Setting " + setting + " handles " + setting.valueClass.getSimpleName());
    }
    if (remote) refreshRemote(false);
    var property = setting2Property(setting);
    Object value =
        properties.containsKey(property)
            ? Utils.Data.asType(properties.getProperty(property), setting.valueClass)
            : setting.defaultValue;
    return (T) value;
  }

  @Override
  public <T> CompletableFuture<T> set(Setting setting, T value) {
    requireAccepted(setting);
    if (!setting.validate(value)) {
      throw new KlabIllegalArgumentException(
          "Invalid " + setting.valueClass.getSimpleName() + " value for " + setting);
    }

    CompletableFuture<T> future =
        remote
            ? ((BaseServiceClient) service)
                .postSetting(setting, value, (Class<T>) setting.valueClass)
            : CompletableFuture.supplyAsync(() -> applyLocal(setting, value), executor);
    if (remote && !(value instanceof Map)) {
      future.thenAccept(
          result -> {
            synchronized (this) {
              properties.setProperty(setting2Property(setting), Utils.Data.asString(result));
            }
          });
    }
    notifyOperationResult(setting, value, future);
    return future;
  }

  private <T> T applyLocal(Setting setting, T value) {
    Object result = value;
    if (value instanceof Map && executionHandlers.containsKey(setting)) {
      result = executionHandlers.get(setting).apply(value);
    } else if (!(value instanceof Map)) {
      synchronized (this) {
        properties.setProperty(setting2Property(setting), Utils.Data.asString(value));
        if (!Utils.Properties.save(settingsFile, properties)) {
          throw new IllegalStateException("Cannot write settings file " + settingsFile);
        }
      }
    }
    return (T) result;
  }

  private <T> void notifyOperationResult(Setting setting, T request, CompletableFuture<T> future) {
    if (!(request instanceof Map<?, ?> requestMap)) return;
    future.thenAccept(
        result -> {
          if (result instanceof Map<?, ?> resultMap) {
            var typedRequest = stringMap(requestMap);
            var typedResult = stringMap(resultMap);
            List<ResultListener> listeners;
            synchronized (this) {
              listeners = List.copyOf(resultListeners);
            }
            listeners.forEach(listener -> listener.onResult(setting, typedRequest, typedResult));
          }
        });
  }

  private static Map<String, Object> stringMap(Map<?, ?> map) {
    Map<String, Object> ret = new LinkedHashMap<>();
    map.forEach((key, value) -> ret.put(String.valueOf(key), value));
    return ret;
  }

  @Override
  public void setIfUnset(Setting setting, Object value) {
    requireAccepted(setting);
    if (!isSet(setting)) set(setting, value).join();
  }

  @Override
  public synchronized Map<String, Object> asMap() {
    if (remote) refreshRemote(true);
    Map<String, Object> ret = new LinkedHashMap<>();
    for (var setting : Setting.values()) {
      if (accepts(setting)) {
        var property = setting2Property(setting);
        ret.put(
            setting.name(),
            properties.containsKey(property)
                ? Utils.Data.asType(properties.getProperty(property), setting.valueClass)
                : setting.defaultValue);
      }
    }
    return ret;
  }

  private synchronized void refreshRemote(boolean force) {
    if (remoteLoaded && !force) return;
    var values = ((BaseServiceClient) service).readSettings();
    if (values == null) return;
    properties.clear();
    values.forEach(
        (name, value) -> {
          try {
            var setting = Setting.valueOf(name);
            if (accepts(setting) && !(value instanceof Map)) {
              properties.setProperty(setting2Property(setting), Utils.Data.asString(value));
            }
          } catch (IllegalArgumentException ignored) {
            Logging.INSTANCE.warn("Ignoring unknown remote setting " + name);
          }
        });
    remoteLoaded = true;
  }

  @Override
  public synchronized boolean isSet(Setting setting) {
    requireAccepted(setting);
    if (remote) refreshRemote(false);
    return properties.containsKey(setting2Property(setting));
  }

  @Override
  public synchronized void addResultListener(ResultListener listener) {
    if (listener != null) resultListeners.add(listener);
  }

  public String setting2Property(Setting setting) {
    return setting.page.name().toLowerCase() + "." + setting.name().toLowerCase();
  }

  public Setting property2Setting(String property) {
    if (property == null || property.isBlank()) return null;
    try {
      return Setting.valueOf(property.substring(property.lastIndexOf('.') + 1).toUpperCase());
    } catch (IllegalArgumentException e) {
      return null;
    }
  }

  public synchronized void setExecutionHandler(Setting setting, Function<Object, Object> handler) {
    requireAccepted(setting);
    if (!Map.class.equals(setting.valueClass)) {
      throw new KlabIllegalArgumentException("Execution handlers require a Map setting");
    }
    executionHandlers.put(setting, handler);
  }
}
