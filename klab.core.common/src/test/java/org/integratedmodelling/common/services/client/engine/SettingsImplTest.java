package org.integratedmodelling.common.services.client.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
