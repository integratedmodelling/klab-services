package org.integratedmodelling.common.distribution;

import org.integratedmodelling.common.configuration.CommonConfiguration;
import org.integratedmodelling.common.logging.Logging;
import org.integratedmodelling.common.services.client.engine.SettingsImpl;
import org.integratedmodelling.common.utils.Utils;
import org.integratedmodelling.klab.api.Klab;
import org.integratedmodelling.klab.api.configuration.Setting;
import org.integratedmodelling.klab.api.configuration.Settings;
import org.integratedmodelling.klab.api.data.Version;
import org.integratedmodelling.klab.api.engine.distribution.Distribution;
import org.integratedmodelling.klab.api.engine.distribution.LocalInstance;
import org.integratedmodelling.klab.api.engine.distribution.Stack;
import org.integratedmodelling.klab.api.services.KlabService;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

public class StackImpl implements Stack {

  private final String name;
  private final Settings settings;
  private final Map<Tag, DistributionImpl> tags = new TreeMap<>();
  private final Map<Tag, DistributionImpl> synchronizationSources = new HashMap<>();

  /**
   * Pass settings to determine whether the stack accepts development distributions, where the
   * distribution cache should be, the level of "experimental" tags enabled, etc.
   *
   * @param settings
   */
  public StackImpl(String name, Settings settings) {
    this.name = name;
    this.settings = settings;
    refreshTags();
  }

  private synchronized void refreshTags() {
    tags.clear();
    synchronizationSources.clear();
    tags.putAll(DistributionImpl.distributions(name, settings));

    var remoteDistributions =
        DistributionImpl.distributions(
            name,
            Utils.URLs.newURL(settings.get(Setting.DISTRIBUTION_SOURCE_URL, String.class)));
    for (var distribution : remoteDistributions) {
      for (var remoteTag : distribution.getTags()) {
        tags.keySet().stream()
            .filter(tag -> DistributionImpl.sameTag(tag, remoteTag))
            .findFirst()
            .ifPresent(tag -> synchronizationSources.put(tag, distribution));
      }
    }
  }

  @Override
  public synchronized List<Tag> tags() {
    return tags.keySet().stream().toList().reversed();
  }

  @Override
  public void refresh() {
    refreshTags();
  }

  @Override
  public synchronized Distribution.Product product(
      Distribution.Product.Type productType, Tag chosenRelease) {
    var tag = disambiguateTag(chosenRelease);
    if (tag == null) {
      return null;
    }
    var distribution = tags.get(tag);
    if (distribution != null) {
      return distribution.findProduct(productType, tag);
    }
    return null;
  }

  @Override
  public synchronized Distribution.Build build(Tag chosenRelease) {
    var tag = disambiguateTag(chosenRelease);
    if (tag == null) {
      return null;
    }
    var distribution = tags.get(tag);
    if (distribution != null) {
      return distribution.findBuild(tag);
    }
    return null;
  }

  @Override
  public synchronized Status status(Tag tag) {
    tag = disambiguateTag(tag);
    if (tag == null) {
      return Status.ABSENT;
    }
    var distribution = synchronizationSources.getOrDefault(tag, tags.get(tag));
    if (distribution == null) {
      return Status.ABSENT;
    }
    AtomicReference<Status> ret = new AtomicReference<>(null);
    if (distribution.synchronize(
        settings.get(Setting.DISTRIBUTION_DIRECTORY, File.class),
        tag,
        new Distribution.Synchronization() {

          @Override
          public boolean isSynchronizing() {
            return false;
          }

          @Override
          public boolean notifyDownload(
              long totalSize,
              long downloadSize,
              Map<Distribution.FileData, Distribution.FileTarget> fullList,
              Map<Distribution.FileData, Distribution.FileTarget> downloadList) {
            ret.set(new Status(totalSize, downloadSize, fullList, downloadList));
            return false;
          }

          @Override
          public boolean download(URL url, File file, Distribution.FileData fileData) {
            return true;
          }

          @Override
          public boolean link(File file, File destination) {
            return true;
          }

          @Override
          public void delete(File file) {}

          @Override
          public boolean copy(File source, File destination) {
            return true;
          }

          @Override
          public void notifyProductSynchronizing(Distribution.Product product) {}

          @Override
          public void notifyProductSynchronized(Distribution.Product product) {}
        })) {
      return ret.get() == null ? Status.ABSENT : ret.get();
    }
    return Status.ABSENT;
  }

  @Override
  public synchronized boolean synchronize(Tag tag, Distribution.Synchronization sync) {

    tag = disambiguateTag(tag);
    if (tag == null) {
      return false;
    }

    if (tag.version() == Version.HEAD) {
      return true;
    }

    var distribution = synchronizationSources.getOrDefault(tag, tags.get(tag));
    if (distribution != null
        && distribution.synchronize(
            settings.get(Setting.DISTRIBUTION_DIRECTORY, File.class), tag, sync)) {
      refreshTags();
      if (sync.isSynchronizing()) {
        removeExpiredLocalDistributions();
      }
      return true;
    }
    return false;
  }

  /** Retain the newest binary distribution and the configured number of previous builds. */
  private void removeExpiredLocalDistributions() {
    var newest =
        tags().stream()
            .filter(candidate -> candidate.version() != Version.HEAD && candidate.availableLocally())
            .findFirst()
            .orElse(null);
    if (newest == null) {
      return;
    }

    var configured = settings.get(Setting.NUMBER_OF_DISTRIBUTION_TO_KEEP, Integer.class);
    var previousToKeep = Math.max(0, configured == null ? 1 : configured);
    var previous =
        tags().stream()
            .filter(candidate -> candidate.version() != Version.HEAD)
            .filter(Tag::availableLocally)
            .filter(candidate -> candidate.compareTo(newest) < 0)
            .toList();
    var current = persistedCurrentTag();
    var currentIsPrevious = current != null && previous.stream().anyMatch(current::equals);
    var remainingSlots = Math.max(0, previousToKeep - (currentIsPrevious ? 1 : 0));

    for (var candidate : previous) {
      if (candidate.equals(current)) {
        continue;
      }
      if (remainingSlots > 0) {
        remainingSlots--;
      } else if (!delete(candidate)) {
        Logging.INSTANCE.warn("Could not remove expired software distribution " + candidate);
      }
    }
  }

  private Tag persistedCurrentTag() {
    try {
      return disambiguateTag(
          DistributionTagCodec.decode(
              settings.get(Setting.CURRENT_DISTRIBUTION_TAG, String.class)));
    } catch (RuntimeException invalidTag) {
      return null;
    }
  }

  @Override
  public LocalInstance instance(Distribution.Product.Type productType, Tag chosenRelease) {
    var tag = disambiguateTag(chosenRelease);
    if (tag == null) {
      return null;
    }
    var product = product(productType, tag);
    if (product != null && product.getLocalPath() != null) {
      return switch (product.getPlatform()) {
        case JAR -> new JavaLocalInstance(product, settings, tag);
        // TODO exe
        default -> null;
      };
    }
    return null;
  }

  @Override
  public synchronized boolean verify(Tag distributionTag) {
    return verify(distributionTag, null);
  }

  @Override
  public synchronized boolean verify(
      Tag distributionTag, Distribution.Verification monitor) {
    var tag = disambiguateTag(distributionTag);
    if (tag != null) {
      var distribution = tags.get(tag);
      if (distribution != null) {
        return distribution.verify(tag, monitor);
      }
    }
    return false;
  }

  /**
   * Return one of the physical tags after substituting for supported mnemonics like HEAD, "latest",
   * etc.
   *
   * @param tag
   * @return
   */
  private Tag disambiguateTag(Tag tag) {
    if (tag == null) {
      return null;
    }
    if (tag == Tag.LATEST_STABLE) {
      return tags().stream().filter(t -> "master".equals(t.release())).findFirst().orElse(null);
    } else if (tag == Tag.LATEST_DEVELOP) {
      return tags().stream().findFirst().orElse(null);
    }
    return tags.keySet().stream()
        .filter(candidate -> DistributionImpl.sameTag(candidate, tag))
        .findFirst()
        .orElse(null);
  }

  @Override
  public synchronized Tag resolve(Tag tag) {
    return disambiguateTag(tag);
  }

  @Override
  public synchronized boolean delete(Tag requestedTag) {
    var tag = disambiguateTag(requestedTag);
    if (tag == null || tag.version() == Version.HEAD || !tag.availableLocally()) {
      return false;
    }

    var distributionRoot =
        settings
            .get(Setting.DISTRIBUTION_DIRECTORY, File.class)
            .toPath()
            .toAbsolutePath()
            .normalize();
    var stackRoot = distributionRoot.resolve(name).normalize();
    var versionRoot = stackRoot.resolve(tag.version().toString()).normalize();
    var releaseRoot = versionRoot.resolve(tag.release()).normalize();
    var buildRoot = releaseRoot.resolve(tag.build()).normalize();
    if (!buildRoot.startsWith(stackRoot) || !Files.isDirectory(buildRoot)) {
      return false;
    }

    try {
      org.apache.commons.io.FileUtils.deleteDirectory(buildRoot.toFile());
      removeMetadataEntry(
          releaseRoot.resolve(Distribution.RELEASE_PROPERTIES_FILE).toFile(),
          Distribution.RELEASE_BUILDS_PROPERTY,
          tag.build());
      if (listBuildDirectories(releaseRoot.toFile()).isEmpty()) {
        org.apache.commons.io.FileUtils.deleteDirectory(releaseRoot.toFile());
        removeMetadataEntry(
            versionRoot.resolve(Distribution.VERSION_PROPERTIES_FILE).toFile(),
            Distribution.VERSION_RELEASES_PROPERTY,
            tag.release());
      }
      if (listReleaseDirectories(versionRoot.toFile()).isEmpty()) {
        org.apache.commons.io.FileUtils.deleteDirectory(versionRoot.toFile());
        removeMetadataEntry(
            stackRoot.resolve(Distribution.DISTRIBUTION_PROPERTIES_FILE).toFile(),
            Distribution.DISTRIBUTION_VERSIONS_PROPERTY,
            tag.version().toString());
      }
      refreshTags();
      return true;
    } catch (IOException e) {
      return false;
    }
  }

  private void removeMetadataEntry(File file, String property, String value) throws IOException {
    if (!file.isFile()) {
      return;
    }
    var properties = new Properties();
    try (var input = Files.newInputStream(file.toPath())) {
      properties.load(input);
    }
    var values =
        Arrays.stream(properties.getProperty(property, "").split(","))
            .map(String::trim)
            .filter(entry -> !entry.isBlank() && !entry.equals(value))
            .toList();
    properties.setProperty(property, String.join(",", values));
    try (var output = Files.newOutputStream(file.toPath())) {
      properties.store(output, null);
    }
  }

  private List<File> listBuildDirectories(File releaseDirectory) {
    return listDirectoriesExcluding(releaseDirectory, Set.of());
  }

  private List<File> listReleaseDirectories(File versionDirectory) {
    return listDirectoriesExcluding(versionDirectory, Set.of());
  }

  private List<File> listDirectoriesExcluding(File directory, Set<String> excluded) {
    var children =
        directory.listFiles(
            file -> file.isDirectory() && !excluded.contains(file.getName()));
    return children == null ? List.of() : Arrays.asList(children);
  }

  public static void main(String[] args) {

    Klab.INSTANCE.setConfiguration(new CommonConfiguration());

    var settings = SettingsImpl.forEngine();
    var klab = Stack.of("klab", settings);
    var tag = new AtomicReference<>(klab.tags().isEmpty() ? null : klab.tags().getFirst());

    System.out.println("k.LAB distribution test console");
    System.out.println(
        "Distribution directory: " + settings.get(Setting.DISTRIBUTION_DIRECTORY, File.class));
    System.out.println("Current tag: " + tag.get());

    Utils.CLI
        .create()
        .with(
            "help",
            ar -> printDistributionCliHelp())
        .with(
            "status",
            ar -> {
              var distributionTag = resolveTag(klab, tag.get(), ar);
              if (distributionTag == null) {
                return;
              }
              printStatus(klab.status(distributionTag));
            })
        .with(
            "plan",
            ar -> {
              var distributionTag = resolveTag(klab, tag.get(), ar);
              if (distributionTag != null) {
                klab.synchronize(distributionTag, DistributionImpl.loggingSynchronizer);
              }
            })
        .with(
            "tags",
            ar -> {
              printTags(klab, tag.get());
            })
        .with(
            "current",
            ar -> {
              System.out.println("Current tag: " + tag.get());
            })
        .with(
            "tag",
            ar -> {
              var resolved = resolveTag(klab, tag.get(), ar);
              if (resolved != null) {
                tag.set(resolved);
                System.out.println("Set current tag to " + tag.get());
              }
            })
        .with(
            "build",
            ar -> {
              var distributionTag = resolveTag(klab, tag.get(), ar);
              if (distributionTag == null) {
                return;
              }
              printBuild(klab.build(distributionTag));
            })
        .with(
            "products",
            ar -> {
              var distributionTag = resolveTag(klab, tag.get(), ar);
              if (distributionTag == null) {
                return;
              }
              var build = klab.build(distributionTag);
              if (build != null) {
                build.getProducts().forEach(StackImpl::printProduct);
              }
            })
        .with(
            "sync",
            ar -> {
              var distributionTag = resolveTag(klab, tag.get(), ar);
              if (distributionTag != null) {
                System.out.println(
                    "Synchronized: "
                        + klab.synchronize(
                            distributionTag,
                            ar.length > 1 && "quiet".equalsIgnoreCase(ar[1])
                                ? quietSynchronizer()
                                : DistributionImpl.actingSynchronizer));
              }
            })
        .with(
            "verify",
            ar -> {
              var distributionTag = resolveTag(klab, tag.get(), ar);
              if (distributionTag != null) {
                System.out.println("Verified: " + klab.verify(distributionTag));
              }
            })
        .with(
            "product",
            ar -> {
              if (ar.length == 0) {
                System.out.println("Usage: product <type> [tag]");
                return;
              }
              var productType = resolveProductType(ar[0]);
              var distributionTag =
                  ar.length > 1
                      ? resolveTag(klab, tag.get(), Arrays.copyOfRange(ar, 1, ar.length))
                      : tag.get();
              var product = productType == null ? null : klab.product(productType, distributionTag);
              if (product == null) {
                System.out.println("Product not found: " + ar[0]);
                return;
              }
              printProduct(product);
            })
        .with(
            "instance",
            ar -> {
              var instance = resolveInstance(klab, tag.get(), ar);
              if (instance != null) {
                System.out.println(
                    "Instance "
                        + instance.getProduct().getName()
                        + ": status = "
                        + instance.getStatus());
              }
            })
        .with(
            "start",
            ar -> {
              var instance = resolveInstance(klab, tag.get(), ar);
              if (instance != null) {
                System.out.println(
                    "Starting "
                        + instance.getProduct().getName()
                        + ": status = "
                        + instance.getStatus());
                instance.start();
              }
            })
        .with(
            "stop",
            ar -> {
              var instance = resolveInstance(klab, tag.get(), ar);
              if (instance != null) {
                System.out.println(
                    "Stopping "
                        + instance.getProduct().getName()
                        + ": status = "
                        + instance.getStatus());
                instance.stop();
              }
            })
        .run();
  }

  private static void printDistributionCliHelp() {
    System.out.println("Commands:");
    System.out.println("  tags                         list known tags");
    System.out.println("  tag <n|current|develop|stable> select current tag");
    System.out.println("  current                      show selected tag");
    System.out.println("  status [tag]                 compute dry-run status");
    System.out.println("  plan [tag]                   print download plan using logging synchronizer");
    System.out.println("  sync [tag] [quiet]           synchronize selected or indexed tag");
    System.out.println("  verify [tag]                 verify local files by size/hash");
    System.out.println("  build [tag]                  show build products");
    System.out.println("  products [tag]               list product details");
    System.out.println("  product <type> [tag]         show one product");
    System.out.println("  instance <type>              show local instance status");
    System.out.println("  start <type> / stop <type>   start or stop a local instance");
    System.out.println("  exit                         leave the console");
  }

  private static void printTags(Stack stack, Tag current) {
    int n = 1;
    for (var t : stack.tags()) {
      System.out.println((n++) + ". " + t + " " + (t.equals(current) ? "(current)" : ""));
    }
    if (n == 1) {
      System.out.println("No distribution tags found.");
    }
  }

  private static Tag resolveTag(Stack stack, Tag current, String[] args) {
    if (args.length == 0 || args[0] == null || args[0].isBlank() || "current".equals(args[0])) {
      if (current == null) {
        System.out.println("No current tag is selected.");
      }
      return current;
    }
    if ("develop".equalsIgnoreCase(args[0])) {
      var latest = stack.tags().stream().findFirst().orElse(null);
      if (latest == null) {
        System.out.println("No develop/latest tag is available.");
      }
      return latest;
    }
    if ("stable".equalsIgnoreCase(args[0])) {
      var stable =
          stack.tags().stream().filter(t -> "master".equals(t.release())).findFirst().orElse(null);
      if (stable == null) {
        System.out.println("No stable/master tag is available.");
      }
      return stable;
    }
    try {
      var index = Integer.parseInt(args[0]);
      if (index < 1 || index > stack.tags().size()) {
        System.out.println("Tag index out of range: " + index);
        return null;
      }
      return stack.tags().get(index - 1);
    } catch (NumberFormatException e) {
      System.out.println("Tag must be an index, current, develop or stable: " + args[0]);
      return null;
    }
  }

  private static void printStatus(Stack.Status status) {
    System.out.println(
        "Total content: "
            + org.apache.commons.io.FileUtils.byteCountToDisplaySize(status.totalContentSize()));
    System.out.println(
        "Download: "
            + org.apache.commons.io.FileUtils.byteCountToDisplaySize(status.downloadSize())
            + " in "
            + status.downloadList().size()
            + " files out of "
            + status.fullContentList().size()
            + " unique files.");
    if (!status.downloadList().isEmpty() && status.downloadList().size() <= 20) {
      status.downloadList()
          .forEach((file, target) -> System.out.println("  " + file.name() + " -> " + target.destinationFile()));
    }
  }

  private static void printBuild(Distribution.Build build) {
    if (build == null) {
      System.out.println("Build not found.");
      return;
    }
    System.out.println("Build " + build.getName() + " with " + build.getProducts().size() + " products:");
    build.getProducts().forEach(StackImpl::printProduct);
  }

  private static void printProduct(Distribution.Product product) {
    System.out.println(
        product.getName()
            + " type="
            + product.getType()
            + " platform="
            + product.getPlatform()
            + " files="
            + product.getFiles().size()
            + " local="
            + product.getLocalPath());
  }

  private static Distribution.Product.Type resolveProductType(String value) {
    for (var type : Distribution.Product.Type.values()) {
      if (type.name().equalsIgnoreCase(value)
          || type.getId().equalsIgnoreCase(value)
          || type.getName().equalsIgnoreCase(value)) {
        return type;
      }
    }
    System.out.println("Unknown product type: " + value);
    System.out.println(
        "Known types: "
            + Arrays.stream(Distribution.Product.Type.values())
                .map(t -> t.name() + "/" + t.getId())
                .collect(java.util.stream.Collectors.joining(", ")));
    return null;
  }

  private static LocalInstance resolveInstance(Stack stack, Tag current, String[] args) {
    if (args.length == 0) {
      System.out.println("Usage: instance|start|stop <product-type>");
      return null;
    }
    var type = resolveProductType(args[0]);
    if (type == null) {
      return null;
    }
    var instance = stack.instance(type, current);
    if (instance == null) {
      System.out.println("No local instance for " + args[0] + " in tag " + current);
    }
    return instance;
  }

  private static Distribution.Synchronization quietSynchronizer() {
    return new Distribution.Synchronization() {
      @Override
      public boolean isSynchronizing() {
        return true;
      }

      @Override
      public boolean notifyDownload(
          long totalSize,
          long downloadSize,
          Map<Distribution.FileData, Distribution.FileTarget> fullList,
          Map<Distribution.FileData, Distribution.FileTarget> downloadList) {
        System.out.println(
            "Synchronizing "
                + downloadList.size()
                + " downloads ("
                + org.apache.commons.io.FileUtils.byteCountToDisplaySize(downloadSize)
                + ") over "
                + fullList.size()
                + " unique files.");
        return true;
      }

      @Override
      public boolean download(URL url, File file, Distribution.FileData fileData) {
        try {
          var parent = file.getParentFile();
          if (parent != null) {
            parent.mkdirs();
          }
          org.apache.commons.io.FileUtils.copyURLToFile(url, file);
          return true;
        } catch (IOException e) {
          System.out.println("Download failed: " + e.getMessage());
          return false;
        }
      }

      @Override
      public boolean link(File file, File destination) {
        var parent = destination.getParentFile();
        if (parent != null) {
          parent.mkdirs();
        }
        return Utils.Files.symlink(file, destination);
      }

      @Override
      public void delete(File file) {
        org.apache.commons.io.FileUtils.deleteQuietly(file);
      }

      @Override
      public boolean copy(File source, File destination) {
        try {
          var parent = destination.getParentFile();
          if (parent != null) {
            parent.mkdirs();
          }
          Files.copy(source.toPath(), destination.toPath(), StandardCopyOption.REPLACE_EXISTING);
          return true;
        } catch (IOException e) {
          System.out.println("Copy failed: " + e.getMessage());
          return false;
        }
      }

      @Override
      public void notifyProductSynchronizing(Distribution.Product product) {}

      @Override
      public void notifyProductSynchronized(Distribution.Product product) {}
    };
  }
}
