package org.integratedmodelling.common.services.client.engine;

import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import com.electronwill.nightconfig.core.file.FileConfig;
import org.integratedmodelling.common.logging.Logging;
import org.integratedmodelling.common.utils.Utils;
import org.integratedmodelling.klab.api.configuration.Configuration;
import org.integratedmodelling.klab.api.configuration.Setting;
import org.integratedmodelling.klab.api.configuration.Settings;
import org.integratedmodelling.klab.api.engine.distribution.Release;
import org.integratedmodelling.klab.api.services.KlabService;

import java.io.File;

public class SettingsImpl implements Settings {

  private final CommentedFileConfig config;

  public static Settings forProduct(Release release) {
    // TODO
    return new SettingsImpl(release.getProduct().getProductType().getName());
  }

  public static Settings forService(KlabService.Type type) {
    // TODO
    return new SettingsImpl(type.name().toLowerCase());
  }

  public static Settings forEngine() {
    // TODO
    return new SettingsImpl("engine");
  }

  public static Settings forClient() {
    // TODO
    return new SettingsImpl("client");
  }

  // the file name determines which kind of setting we are using. They are all in the same directory
  // with different names.
  private String settingsFileName;
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

  private SettingsImpl(String settingsFileName) {

    this.settingsFileName = settingsFileName;
    this.settingsFile = Configuration.INSTANCE.getFile(settingsFileName + ".toml");
    // Advanced builder, default resource, autosave and much more (-> cf the wiki)
    this.config = CommentedFileConfig.builder(settingsFile).autosave().build();
    config.load(); // This actually reads the config
  }

  @Override
  public <T> T get(Setting setting, Class<T> valueType) {
    var property = setting2Property(setting);
    if (config.contains(property)) {
      return Utils.Data.asType(config.get(property), setting.valueClass);
    }
    return (T) setting.defaultValue;
  }

  @Override
  public void set(Setting setting, Object value) {
    Logging.INSTANCE.info(
        "DIO CASTORO SETTING: "
            + setting.name()
            + " = "
            + value
            + " ("
            + value.getClass()
            + ") "
            + (value instanceof String ? "\"" + value + "\"" : ""));
    config.set(setting2Property(setting), value);
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
