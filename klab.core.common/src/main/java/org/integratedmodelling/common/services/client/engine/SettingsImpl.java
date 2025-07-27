package org.integratedmodelling.common.services.client.engine;

import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import com.electronwill.nightconfig.core.file.FileConfig;
import com.electronwill.nightconfig.core.io.WritingMode;
import org.integratedmodelling.common.logging.Logging;
import org.integratedmodelling.common.utils.Utils;
import org.integratedmodelling.klab.api.configuration.Configuration;
import org.integratedmodelling.klab.api.configuration.Setting;
import org.integratedmodelling.klab.api.configuration.Settings;
import org.integratedmodelling.klab.api.engine.distribution.Release;
import org.integratedmodelling.klab.api.services.KlabService;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class SettingsImpl implements Settings {

  private final CommentedFileConfig config;
  private final Executor executor = Executors.newSingleThreadExecutor();

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
    this.config =
        CommentedFileConfig.builder(settingsFile).writingMode(WritingMode.REPLACE_ATOMIC).build();
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
  public <T> Future<T> set(Setting setting, T value) {
    Logging.INSTANCE.info(
        "DIO CASTORO SETTING: "
            + setting.name()
            + " = "
            + value
            + " ("
            + value.getClass()
            + ") "
            + (value instanceof String ? "\"" + value + "\"" : ""));

    // TODO implement the Future<> with a completable future and a callback to call on setting,
    //  including for Actions (whose class is Map)

    executor.execute(
        () -> {
          // CHECK the REPLACE_ATOMIC should be doing this, or maybe not - this seems to work so don't touch
          // anything. Problem is duplicate entries (and refuse to read) when settings are changed quickly.
          config.set(setting2Property(setting), value);
          config.save();
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
