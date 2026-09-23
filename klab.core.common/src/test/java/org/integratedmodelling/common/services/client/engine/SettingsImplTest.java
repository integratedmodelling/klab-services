package org.integratedmodelling.common.services.client.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import org.integratedmodelling.klab.api.configuration.Setting;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SettingsImplTest {

  @TempDir Path temporaryDirectory;

  @Test
  void persistsTypedValuesAndCompletesTheReturnedFuture() throws Exception {
    var file = Files.createFile(temporaryDirectory.resolve("settings.properties")).toFile();
    var settings = new SettingsImpl(file);

    assertEquals(
        Boolean.FALSE,
        settings.set(Setting.POLLING, false).get(2, TimeUnit.SECONDS));
    assertTrue(settings.isSet(Setting.POLLING));
    assertEquals(Boolean.FALSE, settings.get(Setting.POLLING, Boolean.class));
  }

  @Test
  void reloadsFileSettingsAndListsThemAsFiles() throws Exception {
    var storage = Files.createFile(temporaryDirectory.resolve("paths.properties")).toFile();
    var settings = new SettingsImpl(storage);
    // No existence check: settings may refer to directories or files created later.
    var path = temporaryDirectory.resolve("not yet created").resolve("distribution").toFile();
    for (var setting : Setting.values()) {
      if (setting.valueClass == File.class) {
        settings.set(setting, path).get(2, TimeUnit.SECONDS);
      }
    }

    var reloaded = new SettingsImpl(storage);
    var values = reloaded.asMap();
    for (var setting : Setting.values()) {
      if (setting.valueClass == File.class) {
        assertEquals(path, reloaded.get(setting, File.class), setting.name());
        assertEquals(path, values.get(setting.name()), setting.name());
        assertTrue(reloaded.isSet(setting));
      }
    }
    assertTrue(!path.exists());
  }

  @Test
  void reloadsOtherScalarPropertiesAndStillRejectsWrongRequestedTypes() throws Exception {
    var storage = Files.createFile(temporaryDirectory.resolve("scalars.properties")).toFile();
    var settings = new SettingsImpl(storage);
    settings.set(Setting.POLLING, false).get(2, TimeUnit.SECONDS);
    settings.set(Setting.POLLING_INTERVAL_LOCAL, 37).get(2, TimeUnit.SECONDS);
    settings.set(Setting.DISTRIBUTION_SOURCE_URL, "https://example.org/distribution")
        .get(2, TimeUnit.SECONDS);

    var reloaded = new SettingsImpl(storage);
    assertEquals(false, reloaded.get(Setting.POLLING, Boolean.class));
    assertEquals(37, reloaded.get(Setting.POLLING_INTERVAL_LOCAL, Integer.class));
    assertEquals("https://example.org/distribution",
        reloaded.get(Setting.DISTRIBUTION_SOURCE_URL, String.class));
    assertThrows(org.integratedmodelling.klab.api.exceptions.KlabIllegalArgumentException.class,
        () -> reloaded.get(Setting.DISTRIBUTION_SOURCE_LOCATION, String.class));
  }
  @Test
  void operationResultCallbackReceivesSettingRequestAndResult() throws Exception {
    var file = Files.createFile(temporaryDirectory.resolve("operations.properties")).toFile();
    var settings = new SettingsImpl(file);
    var callback = new AtomicReference<Map<String, Object>>();
    var callbackReceived = new CountDownLatch(1);
    settings.setExecutionHandler(
        Setting.USE_LOCAL_FEDERATION, request -> Map.of("result", true));
    settings.addResultListener(
        (setting, request, result) ->
            {
              callback.set(
                  Map.of("setting", setting, "request", request, "result", result));
              callbackReceived.countDown();
            });

    var request = Map.<String, Object>of("enabled", true);
    assertEquals(
        Map.of("result", true),
        settings.set(Setting.USE_LOCAL_FEDERATION, request).get(2, TimeUnit.SECONDS));
    assertTrue(callbackReceived.await(2, TimeUnit.SECONDS));
    assertEquals(Setting.USE_LOCAL_FEDERATION, callback.get().get("setting"));
    assertEquals(request, callback.get().get("request"));
    assertEquals(Map.of("result", true), callback.get().get("result"));
  }
}
