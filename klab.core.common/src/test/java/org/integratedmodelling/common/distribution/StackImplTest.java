package org.integratedmodelling.common.distribution;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Properties;
import com.sun.net.httpserver.HttpServer;
import org.apache.commons.exec.CommandLine;
import org.integratedmodelling.common.utils.Utils;
import org.integratedmodelling.klab.api.configuration.Setting;
import org.integratedmodelling.klab.api.configuration.Settings;
import org.integratedmodelling.klab.api.data.Version;
import org.integratedmodelling.klab.api.engine.distribution.Distribution;
import org.integratedmodelling.klab.api.engine.distribution.LocalInstance;
import org.integratedmodelling.klab.api.engine.distribution.Stack;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;

class StackImplTest {

  private static final String VERSION = "1.0.0";
  private static final String RELEASE = "master";
  private static final String BUILD = "202608051200";

  @TempDir Path temporaryDirectory;
  private HttpServer server;
  private Path remoteDirectory;
  private String remoteUrl;

  @BeforeEach
  void startDistributionServer() throws IOException {
    remoteDirectory = temporaryDirectory.resolve("remote");
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(
        "/",
        exchange -> {
          var requested =
              remoteDirectory.resolve(exchange.getRequestURI().getPath().substring(1)).normalize();
          if (!requested.startsWith(remoteDirectory) || !Files.isRegularFile(requested)) {
            exchange.sendResponseHeaders(404, -1);
            exchange.close();
            return;
          }
          var content = Files.readAllBytes(requested);
          exchange.sendResponseHeaders(200, content.length);
          try (var response = exchange.getResponseBody()) {
            response.write(content);
          }
        });
    server.start();
    remoteUrl = "http://127.0.0.1:" + server.getAddress().getPort();
  }

  @AfterEach
  void stopDistributionServer() {
    server.stop(0);
  }

  @Test
  void coalescesLocalAndRemoteCopiesIntoOneAvailableTag() throws Exception {
    var local = temporaryDirectory.resolve("local");
    createDistribution(remoteDirectory, true);
    createDistribution(local, true);

    var catalog = DistributionImpl.distributions("klab", settings(local));

    assertEquals(1, catalog.size());
    var tag = catalog.keySet().iterator().next();
    assertTrue(tag.availableLocally());
    assertFalse(tag.orphan());
    assertEquals(List.of("null"), catalog.get(tag).findBuild(tag).getInvalidProductReferences());
  }

  @Test
  void reportsVerificationProgressAroundEachHashCheck() throws Exception {
    var local = temporaryDirectory.resolve("local");
    createDistribution(local, true);
    var distributionRoot = local.resolve("klab");
    var distribution =
        new DistributionImpl(
            "klab",
            Version.create(VERSION),
            Utils.URLs.newURL(distributionRoot.toFile()),
            Utils.URLs.newURL(distributionRoot.resolve(VERSION).toFile()));
    var tag = distribution.getTags().getFirst();
    var events = new ArrayList<String>();

    assertTrue(
        distribution.verify(
            tag,
            new Distribution.Verification() {
              @Override
              public void notifyVerification(int totalFiles) {
                events.add("total:" + totalFiles);
              }

              @Override
              public void notifyFileVerifying(
                  File file, Distribution.FileData fileData, int index) {
                events.add("before:" + index + ":" + fileData.name());
              }

              @Override
              public void notifyFileVerified(
                  File file, Distribution.FileData fileData, int index, boolean valid) {
                events.add("after:" + index + ":" + valid);
              }
            }));
    assertEquals(List.of("total:1", "before:1:payload.jar", "after:1:true"), events);
  }

  @Test
  void deletesOnlyTheSelectedBuildAndRefreshesItsAvailability() throws Exception {
    var local = temporaryDirectory.resolve("local");
    createDistribution(remoteDirectory, true);
    createDistribution(local, true);
    var stack = new StackImpl("klab", settings(local));
    var installed = stack.tags().getFirst();

    assertTrue(installed.availableLocally());
    assertTrue(stack.delete(installed));
    assertFalse(Files.exists(buildRoot(local)));

    var remaining = stack.resolve(installed);
    assertFalse(remaining.availableLocally());
    assertFalse(stack.delete(remaining));
  }

  @Test
  void retainsConfiguredPreviousBuildsWithoutDeletingCurrentSelection() throws Exception {
    var local = temporaryDirectory.resolve("local");
    var oldest = "202608031200";
    var middle = "202608041200";
    createDistribution(remoteDirectory, true, oldest, middle, BUILD);
    var current = Stack.Tag.of(Version.create(VERSION), RELEASE, oldest, false, false);
    var stack =
        new StackImpl(
            "klab", settings(local, 1, DistributionTagCodec.encode(current)));

    assertTrue(
        stack.synchronize(stack.tags().getFirst(), DistributionImpl.actingSynchronizer));

    var installedBuilds =
        stack.tags().stream().filter(Stack.Tag::availableLocally).map(Stack.Tag::build).toList();
    assertEquals(List.of(BUILD, oldest), installedBuilds);
    assertFalse(Files.exists(buildRoot(local, middle)));
    var remoteOnly =
        stack.resolve(Stack.Tag.of(Version.create(VERSION), RELEASE, middle, false, false));
    assertEquals(middle, remoteOnly.build());
    assertFalse(remoteOnly.availableLocally());
  }

  @Test
  void reportsProcessLivenessInsteadOfRelyingOnLifecycleStatus() {
    var product = mock(Distribution.Product.class);
    when(product.getLocalPath()).thenReturn(temporaryDirectory.toFile());
    when(product.getType()).thenReturn(Distribution.Product.Type.CLI);
    var settings = mock(Settings.class);
    when(settings.get(Setting.RUN_DIRECTORY, File.class))
        .thenReturn(temporaryDirectory.resolve("run").toFile());
    var instance =
        new TestLocalInstance(
            product, settings, Stack.Tag.of(Version.create(VERSION), RELEASE, BUILD, true, false));
    var process = mock(Process.class);

    instance.setProcess(process);
    instance.setStatus(LocalInstance.Status.ERROR);
    when(process.isAlive()).thenReturn(true, false);

    assertTrue(instance.isAlive());
    assertFalse(instance.isAlive());
  }

  @Test
  void rejectsReusedPidWhenProcessStartInstantDoesNotMatch() throws IOException {
    var product = mock(Distribution.Product.class);
    when(product.getLocalPath()).thenReturn(temporaryDirectory.toFile());
    when(product.getType()).thenReturn(Distribution.Product.Type.CLI);
    var settings = mock(Settings.class);
    var runDirectory = temporaryDirectory.resolve("run");
    when(settings.get(Setting.RUN_DIRECTORY, File.class)).thenReturn(runDirectory.toFile());
    Files.createDirectories(runDirectory);
    var pidFile = runDirectory.resolve(Distribution.Product.Type.CLI.getId() + ".pid");
    Files.writeString(
        pidFile,
        ProcessHandle.current().pid()
            + ":"
            + Distribution.Product.Type.CLI.getId()
            + ":0");

    var instance =
        new TestLocalInstance(
            product, settings, Stack.Tag.of(Version.create(VERSION), RELEASE, BUILD, true, false));

    assertEquals(LocalInstance.Status.STOPPED, instance.getStatus());
    assertFalse(instance.isAlive());
    assertFalse(Files.exists(pidFile));
  }

  private Settings settings(Path local) {
    return settings(local, 1, "");
  }

  private Settings settings(Path local, int distributionsToKeep, String currentDistribution) {
    var settings = mock(Settings.class);
    when(settings.get(Setting.DISTRIBUTION_SOURCE_URL, String.class))
        .thenReturn(remoteUrl);
    when(settings.get(Setting.DISTRIBUTION_DIRECTORY, File.class)).thenReturn(local.toFile());
    when(settings.get(Setting.USE_DEVELOPMENT_DISTRIBUTION_IF_AVAILABLE, Boolean.class))
        .thenReturn(false);
    when(settings.get(Setting.NUMBER_OF_DISTRIBUTION_TO_KEEP, Integer.class))
        .thenReturn(distributionsToKeep);
    when(settings.get(Setting.CURRENT_DISTRIBUTION_TAG, String.class))
        .thenReturn(currentDistribution);
    return settings;
  }

  private void createDistribution(Path root, boolean validPayload) throws Exception {
    createDistribution(root, validPayload, BUILD);
  }

  private void createDistribution(Path root, boolean validPayload, String... builds)
      throws Exception {
    var stackRoot = root.resolve("klab");
    var versionRoot = stackRoot.resolve(VERSION);
    var releaseRoot = versionRoot.resolve(RELEASE);

    writeProperties(
        stackRoot.resolve(Distribution.DISTRIBUTION_PROPERTIES_FILE),
        Distribution.DISTRIBUTION_NAME_PROPERTY,
        "klab",
        Distribution.DISTRIBUTION_VERSIONS_PROPERTY,
        VERSION);
    writeProperties(
        versionRoot.resolve(Distribution.VERSION_PROPERTIES_FILE),
        Distribution.VERSION_NAME_PROPERTY,
        VERSION,
        Distribution.VERSION_RELEASES_PROPERTY,
        RELEASE);
    writeProperties(
        releaseRoot.resolve(Distribution.RELEASE_PROPERTIES_FILE),
        Distribution.RELEASE_NAME_PROPERTY,
        RELEASE,
        Distribution.RELEASE_BUILDS_PROPERTY,
        String.join(",", builds));
    for (var build : builds) {
      var buildRoot = releaseRoot.resolve(build);
      var productRoot = buildRoot.resolve("engine");
      Files.createDirectories(productRoot);
      writeProperties(
          buildRoot.resolve(Distribution.BUILD_PROPERTIES_FILE),
          Distribution.BUILD_NAME_PROPERTY,
          build,
          Distribution.BUILD_PRODUCTS_PROPERTY,
          "null,engine");
      writeProperties(
          productRoot.resolve(Distribution.PRODUCT_PROPERTIES_FILE),
          Distribution.PRODUCT_NAME_PROPERTY,
          "engine",
          Distribution.PRODUCT_TYPE_PROPERTY,
          Distribution.Product.Type.CLI.name(),
          Distribution.PRODUCT_PLATFORM_PROPERTY,
          Distribution.Product.Platform.JAR.name());

      var payload = validPayload ? ("test-payload-" + build).getBytes() : "broken".getBytes();
      Files.write(productRoot.resolve("payload.jar"), payload);
      var hash = HexFormat.of().formatHex(MessageDigest.getInstance("MD5").digest(payload));
      Files.writeString(
          productRoot.resolve(Distribution.BUILD_DIGEST_FILE),
          hash + " payload.jar " + payload.length + System.lineSeparator());
    }
  }

  private Path buildRoot(Path root) {
    return buildRoot(root, BUILD);
  }

  private Path buildRoot(Path root, String build) {
    return root.resolve("klab").resolve(VERSION).resolve(RELEASE).resolve(build);
  }

  private void writeProperties(Path path, String... entries) throws IOException {
    var properties = new Properties();
    for (int i = 0; i < entries.length; i += 2) {
      properties.setProperty(entries[i], entries[i + 1]);
    }
    Files.createDirectories(path.getParent());
    try (var output = Files.newOutputStream(path)) {
      properties.store(output, null);
    }
  }

  private static final class TestLocalInstance extends LocalInstanceImpl {

    private TestLocalInstance(
        Distribution.Product product, Settings settings, Stack.Tag tag) {
      super(product, settings, tag);
    }

    @Override
    protected CommandLine getCommandLine(Distribution.Product product, Settings settings) {
      return null;
    }

    @Override
    public Path getConfigurationPath() {
      return getProduct().getLocalPath().toPath();
    }

    private void setProcess(Process process) {
      this.process = process;
    }

    private void setStatus(LocalInstance.Status status) {
      this.status.set(status);
    }
  }
}
