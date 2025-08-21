package org.integratedmodelling.common.services.client.engine;

import java.io.File;
import java.util.Properties;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.integratedmodelling.common.logging.Logging;
import org.integratedmodelling.common.utils.Utils;
import org.integratedmodelling.klab.api.configuration.Configuration;
import org.integratedmodelling.klab.api.configuration.Setting;
import org.integratedmodelling.klab.api.configuration.Settings;
import org.integratedmodelling.klab.api.services.KlabService;

public class SettingsImpl implements Settings {

  // back to stupid properties because none of the TOML implementations work
  private final Properties properties = new Properties();
  private final Executor executor = Executors.newSingleThreadExecutor();

  public static Settings forService(KlabService.Type type) {
    // TODO
    return new SettingsImpl(type);
  }

  public static Settings forEngine() {
    // TODO
    return new SettingsImpl("engine");
  }

  // the file name determines which kind of setting we are using. They are all in the same directory
  // with different names.
  private File settingsFile;

  /**
   * Pass the service-side settings for a service, return the settings that must be communicated to
   * the clients used by the service to communicate with other services.
   *
   * @param serviceType
   * @param settings
   * @return
   */
  public static Settings forSlaveServices(KlabService.Type serviceType, Settings settings) {
    // TODO
    return settings;
  }

  private SettingsImpl(KlabService.Type serviceType) {
    this.settingsFile =
        Configuration.INSTANCE.getFileWithTemplate(
            "services/" + serviceType.name().toLowerCase() + "/settings.properties", "");
    if (!Utils.Properties.load(this.settingsFile, properties)) {
      Logging.INSTANCE.error("Error reading settings file: " + this.settingsFile);
    }
  }

  private SettingsImpl(String settingsFileName) {

    this.settingsFile =
        Configuration.INSTANCE.getFileWithTemplate(settingsFileName + ".properties", "");
    if (!Utils.Properties.load(this.settingsFile, properties)) {
      Logging.INSTANCE.error("Error loading settings file: " + this.settingsFile);
    }
  }

  @Override
  public <T> T get(Setting setting, Class<T> valueType) {
    var property = setting2Property(setting);
    if (properties.containsKey(property)) {
      return Utils.Data.asType(properties.getProperty(property), setting.valueClass);
    }
    return (T) setting.defaultValue;
  }

  @Override
  public <T> Future<T> set(Setting setting, T value) {
    executor.execute(
        () -> {
          try {
            var property = setting2Property(setting);
            if (value == null) {
              properties.remove(property);
            } else {
              properties.setProperty(property, Utils.Data.asString(value));
            }
            if (!Utils.Properties.save(settingsFile, properties)) {
              Logging.INSTANCE.error("Error writing settings file " + settingsFile);
            }
          } catch (Exception e) {
            Logging.INSTANCE.error("Error setting property: " + e.getMessage(), e);
          }
        });
    return null;
  }

  private String setting2Property(Setting setting) {
    return setting.page.name().toLowerCase() + "." + setting.name().toLowerCase();
  }

  private Setting property2Setting(String setting) {
    return Setting.valueOf(setting.substring(setting.lastIndexOf(".") + 1).toUpperCase());
  }

  @Override
  public void setIfUnset(Setting setting, Object value) {}

  @Override
  public boolean isSet(Setting setting) {
    return false;
  }
}
