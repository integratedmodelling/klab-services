package org.integratedmodelling.common.distribution;

import java.io.*;
import java.net.URL;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.stream.Collectors;

import org.apache.commons.io.FileUtils;
import org.integratedmodelling.common.logging.Logging;
import org.integratedmodelling.common.utils.Utils;
import org.integratedmodelling.klab.api.configuration.Configuration;
import org.integratedmodelling.klab.api.configuration.Setting;
import org.integratedmodelling.klab.api.configuration.Settings;
import org.integratedmodelling.klab.api.data.Version;
import org.integratedmodelling.klab.api.engine.distribution.*;
import org.integratedmodelling.klab.api.engine.distribution.Stack;

public class DistributionImpl extends Utils.Properties.Container implements Distribution {

  static Map<Stack.Tag, DistributionImpl> distributions(
      String distributionName, Settings settings) {

    var ret = new LinkedHashMap<Stack.Tag, DistributionImpl>();

    if (settings.get(Setting.USE_DEVELOPMENT_DISTRIBUTION_IF_AVAILABLE, Boolean.class)) {
      var developmentDistribution = developmentDistribution(distributionName);
      if (developmentDistribution != null) {
        for (var tag : developmentDistribution.getTags()) {
          var devTag = Stack.Tag.of(Version.HEAD, tag.release(), tag.build(), true, false);
          ret.put(devTag, developmentDistribution);
        }
      }
    }

    var remoteDistributions =
        distributions(
            distributionName,
            Utils.URLs.newURL(settings.get(Setting.DISTRIBUTION_SOURCE_URL, String.class)));

    for (var remoteDistribution : remoteDistributions) {
      for (var tag : remoteDistribution.getTags()) {
        ret.put(tag, remoteDistribution);
      }
    }

    var localDistributions =
        distributions(
            distributionName,
            Utils.URLs.newURL(settings.get(Setting.DISTRIBUTION_DIRECTORY, File.class)));

    /* Merge local and remote representations by physical identity. Availability and orphan state
     * are observations about a tag, not part of its identity. */
    for (var localDistribution : localDistributions) {
      localDistribution
          .getTags()
          .forEach(
              tag -> {
                var remoteTag =
                    ret.keySet().stream().filter(candidate -> sameTag(candidate, tag)).findFirst();
                remoteTag.ifPresent(ret::remove);
                var actualTag =
                    Stack.Tag.of(
                        tag.version(), tag.release(), tag.build(), true, remoteTag.isEmpty());
                ret.put(actualTag, localDistribution);
              });
    }

    return ret;
  }

  static boolean sameTag(Stack.Tag first, Stack.Tag second) {
    return first != null
        && second != null
        && Objects.equals(first.version(), second.version())
        && Objects.equals(first.release(), second.release())
        && Objects.equals(first.build(), second.build());
  }

  static DistributionImpl developmentDistribution(String distributionName) {

    File distributionDirectory =
        new File(
            Configuration.INSTANCE.getProperty(
                Configuration.KLAB_DEVELOPMENT_SOURCE_REPOSITORY,
                System.getProperty("user.home")
                    + File.separator
                    + "git"
                    + File.separator
                    + "klab"
                    + "-services"));
    if (distributionDirectory.isDirectory()) {
      File distributionFolder =
          new File(
              distributionDirectory
                  + File.separator
                  + "klab.distribution"
                  + File.separator
                  + "target"
                  + File.separator
                  + "distribution"
                  + File.separator
                  + distributionName);
      var distributionProperties = new File(distributionFolder, DISTRIBUTION_PROPERTIES_FILE);
      if (distributionProperties.isFile()) {
        var properties = Utils.Properties.create(distributionProperties);
        for (var version : commaSeparated(properties.getProperty(DISTRIBUTION_VERSIONS_PROPERTY))) {
          if (Version.CURRENT_VERSION.compatible(Version.create(version))) {
            return new DistributionImpl(
                distributionName,
                Version.create(version),
                Utils.URLs.newURL(distributionFolder),
                Utils.URLs.newURL(new File(distributionFolder, version)));
          }
        }
      }
    }

    return null;
  }

  static List<DistributionImpl> distributions(String distributionName, URL url) {
    return distributions(distributionName, url, true);
  }

  static List<DistributionImpl> distributions(
      String distributionName, URL url, boolean reportIncompleteCatalog) {

    /*
     * Remote first. This may fail
     */
    var ret = new ArrayList<DistributionImpl>();
    var distributionUrl = Utils.URLs.newURL(url + "/" + distributionName);
    var properties =
        Utils.Properties.create(Utils.URLs.newURL(distributionUrl + "/distribution.properties"));
    if (!properties.isEmpty()) {
      for (var version : commaSeparated(properties.getProperty(DISTRIBUTION_VERSIONS_PROPERTY))) {
        try {
          var distribution =
              new DistributionImpl(
                  distributionName,
                  Version.create(version),
                  distributionUrl,
                  Utils.URLs.newURL(url + "/" + distributionName + "/" + version));
          if (!distribution.isEmpty()) {
            ret.add(distribution);
          } else {
            var invalidProducts =
                distribution.releases.stream()
                    .flatMap(release -> release.getBuilds().stream())
                    .flatMap(build -> build.getInvalidProductReferences().stream())
                    .distinct()
                    .toList();
            if (reportIncompleteCatalog) {
              Logging.INSTANCE.warn(
                  "Ignoring incomplete online distribution version "
                      + version
                      + " at "
                      + distributionUrl
                      + (invalidProducts.isEmpty()
                          ? ""
                          : "; unreadable products: " + String.join(", ", invalidProducts)));
            }
          }
        } catch (RuntimeException e) {
          if (reportIncompleteCatalog) {
            Logging.INSTANCE.warn(
                "Ignoring unreadable distribution version " + version + " at " + distributionUrl,
                e);
          }
        }
      }
    }

    return ret;
  }

  private String name;
  private Version version;
  private List<Release> releases = new ArrayList<>();

  private enum StorageAction {
    NONE,
    DOWNLOAD,
    COPY
  }

  private enum ProductAction {
    NONE,
    LINK
  }

  private record StoredFile(
      FileData file, URL sourceUrl, File destination, StorageAction action, File reusableSource) {}

  private record ProductFile(
      Product product,
      FileData file,
      File destination,
      StoredFile storedFile,
      boolean common,
      ProductAction action) {}

  private record SynchronizationPlan(
      Map<FileData, FileTarget> fullList,
      Map<FileData, FileTarget> downloadList,
      Map<Product, List<ProductFile>> productFiles) {}

  public DistributionImpl(
      String distributionName, Version version, URL distributionUrl, URL versionUrl) {
    super(Utils.URLs.newURL(distributionUrl + "/" + DISTRIBUTION_PROPERTIES_FILE));
    this.name = distributionName;
    var versionProperties =
        Utils.Properties.create(Utils.URLs.newURL(versionUrl + "/version.properties"));
    this.version = version;
    if (versionProperties.isEmpty()) {
      setEmpty(true);
      return;
    }
    var releaseNames = commaSeparated(versionProperties.getProperty(VERSION_RELEASES_PROPERTY));
    if (releaseNames.isEmpty()) {
      setEmpty(true);
      return;
    }
    for (var release : releaseNames) {
      var releaseData =
          new Distribution.Release(
              Utils.URLs.newURL(versionUrl + "/" + release + "/" + RELEASE_PROPERTIES_FILE));
      if (releaseData.isEmpty()) {
        setEmpty(true);
      }
      this.releases.add(releaseData);
    }
  }

  private Map<Distribution.FileData, Integer> getFileCounts() {
    var counter = new HashMap<Distribution.FileData, Integer>();
    for (var release : releases) {
      for (var build : release.getBuilds()) {
        for (var product : build.getProducts()) {
          for (var file : product.getFiles()) {
            counter.put(file, counter.containsKey(file) ? counter.get(file) + 1 : 1);
          }
        }
      }
    }
    return counter;
  }

  private static List<String> commaSeparated(String value) {
    if (value == null || value.isBlank()) {
      return List.of();
    }
    return Arrays.stream(value.split(",")).map(String::trim).filter(s -> !s.isBlank()).toList();
  }

  public boolean needsSync(
      FileData file, FileTarget target, Map<FileData, FileTarget> previouslyAvailable) {
    return !fileMatches(target.destinationFile(), file) && !previouslyAvailable.containsKey(file);
  }

  /**
   * FIXME THIS SHOULD COMPARE TO THE LATEST BUILD - A NEW BUILD WILL CONTAIN MANY EQUAL FILES IN
   * DIFFERENT DIRS
   *
   * <p>FIXME BASED ON SETTINGS, PREVIOUS BUILDS MAY OR MAY NOT BE REMOVED AFTER SYNC OF A NEW ONE
   *
   * @param rootDirectory
   * @param beingSynced the tag is used to establish which files may come from OTHER builds and may
   *     be copied instead of downloaded or linked.
   * @param monitor
   * @return
   */
  public boolean synchronize(
      File rootDirectory, Stack.Tag beingSynced, Distribution.Synchronization monitor) {

    var plan = createSynchronizationPlan(rootDirectory, beingSynced);

    var totalSize = plan.fullList().keySet().stream().mapToLong(Distribution.FileData::size).sum();
    var downloadSize =
        plan.downloadList().keySet().stream().mapToLong(Distribution.FileData::size).sum();
    if (!monitor.notifyDownload(totalSize, downloadSize, plan.fullList(), plan.downloadList())) {
      return true;
    }

    if (!monitor.isSynchronizing()) {
      return true;
    }

    var distributionDirectory = new File(rootDirectory, this.name);
    distributionDirectory.mkdirs();
    synchronizeProperties(
        this.getProperties(),
        new File(distributionDirectory, Distribution.DISTRIBUTION_PROPERTIES_FILE),
        DISTRIBUTION_VERSIONS_PROPERTY,
        this.version.toString());

    var commonDirectory = new File(distributionDirectory, "common");
    commonDirectory.mkdirs();

    // create version directory if not there, then release, then build and products. They may
    // all be there
    var versionDirectory = new File(distributionDirectory, this.version.toString());
    versionDirectory.mkdirs();

    var versionProperties =
        Utils.Properties.create(
            Distribution.VERSION_NAME_PROPERTY,
            this.version.toString(),
            Distribution.VERSION_RELEASES_PROPERTY,
            "");

    var synchronizedStorage = new HashSet<Distribution.FileData>();
    for (var release : releases) {

      // sync any contents of version.properties to contain the current release
      synchronizeProperties(
          versionProperties.getProperties(),
          new File(versionDirectory, Distribution.VERSION_PROPERTIES_FILE),
          VERSION_RELEASES_PROPERTY,
          release.getName());

      var releaseDirectory =
          new File(versionDirectory.getAbsolutePath() + File.separator + release.getName());
      releaseDirectory.mkdirs();

      var releaseProperties =
          Utils.Properties.create(
              Distribution.RELEASE_NAME_PROPERTY,
              release.getName(),
              Distribution.RELEASE_BUILDS_PROPERTY,
              "");

      for (var build : release.getBuilds()) {

        // sync any contents of release.properties to contain the current build
        synchronizeProperties(
            releaseProperties.getProperties(),
            new File(releaseDirectory, Distribution.RELEASE_PROPERTIES_FILE),
            RELEASE_BUILDS_PROPERTY,
            build.getName());

        var buildDirectory =
            new File(releaseDirectory.getAbsolutePath() + File.separator + build.getName());
        buildDirectory.mkdirs();

        for (var product : build.getProducts()) {

          var productDirectory =
              new File(buildDirectory.getAbsolutePath() + File.separator + product.getName());
          productDirectory.mkdirs();

          monitor.notifyProductSynchronizing(product);

          for (var productFile : plan.productFiles().getOrDefault(product, List.of())) {
            if (synchronizedStorage.add(productFile.file())) {
              if (!synchronizeStoredFile(productFile.storedFile(), monitor)) {
                return false;
              }
            }
            if (!synchronizeProductFile(productFile, monitor)) {
              return false;
            }
          }

          deleteSpuriousFiles(productDirectory, product, monitor);
          // recreate the filelist so that a newer build can reuse the files
          Utils.Files.writeStringsToFile(
              product.getFiles().stream()
                  .map(e -> e.hash() + " " + e.name() + " " + e.size())
                  .collect(Collectors.toList()),
              new File(productDirectory, BUILD_DIGEST_FILE));

          product.save(new File(productDirectory, PRODUCT_PROPERTIES_FILE));

          monitor.notifyProductSynchronized(product);
        }
        build.save(new File(buildDirectory, BUILD_PROPERTIES_FILE));
      }
    }

    return true;
  }

  private SynchronizationPlan createSynchronizationPlan(File rootDirectory, Stack.Tag beingSynced) {

    var commonFiles =
        getFileCounts().entrySet().stream()
            .filter(e -> e.getValue() > 1)
            .map(Map.Entry::getKey)
            .collect(Collectors.toSet());

    var previouslyAvailable = listExistingFilesNotInCommon(beingSynced, rootDirectory);
    Map<Distribution.FileData, Distribution.FileTarget> fullList = new LinkedHashMap<>();
    Map<Distribution.FileData, Distribution.FileTarget> downloadList = new LinkedHashMap<>();
    Map<Distribution.FileData, StoredFile> storedFiles = new LinkedHashMap<>();
    Map<Product, List<ProductFile>> productFiles = new LinkedHashMap<>();

    for (var release : releases) {
      for (var build : release.getBuilds()) {
        for (var product : build.getProducts()) {
          var productDirectory = productDirectory(rootDirectory, release, build, product);
          var productFileList = new ArrayList<ProductFile>();
          productFiles.put(product, productFileList);
          for (var file : product.getFiles()) {
            var sourceUrl = Utils.URLs.newURL(product.getUrl() + "/" + file.name());
            var productDestination = new File(productDirectory, file.name());
            var common = commonFiles.contains(file);
            var storageDestination =
                common ? commonStorageFile(rootDirectory, file) : productDestination;
            var storedFile = storedFiles.get(file);
            if (storedFile == null) {
              var reusableSource =
                  reusableSource(file, storageDestination, productDestination, previouslyAvailable);
              var action =
                  fileMatches(storageDestination, file)
                      ? StorageAction.NONE
                      : reusableSource == null ? StorageAction.DOWNLOAD : StorageAction.COPY;
              storedFile =
                  new StoredFile(file, sourceUrl, storageDestination, action, reusableSource);
              storedFiles.put(file, storedFile);
              fullList.put(file, new FileTarget(sourceUrl, storageDestination));
              if (action == StorageAction.DOWNLOAD) {
                downloadList.put(file, new FileTarget(sourceUrl, storageDestination));
              }
            }
            productFileList.add(
                new ProductFile(
                    product,
                    file,
                    productDestination,
                    storedFile,
                    common,
                    common && !productFileLinksStorage(productDestination, storageDestination, file)
                        ? ProductAction.LINK
                        : ProductAction.NONE));
          }
        }
      }
    }

    return new SynchronizationPlan(fullList, downloadList, productFiles);
  }

  private boolean synchronizeStoredFile(StoredFile storedFile, Synchronization monitor) {
    if (storedFile.action() == StorageAction.NONE) {
      return true;
    }
    ensureParentDirectory(storedFile.destination());
    if (storedFile.destination().exists()) {
      monitor.delete(storedFile.destination());
    }
    var ok =
        switch (storedFile.action()) {
          case COPY -> monitor.copy(storedFile.reusableSource(), storedFile.destination());
          case DOWNLOAD ->
              monitor.download(storedFile.sourceUrl(), storedFile.destination(), storedFile.file());
          case NONE -> true;
        };
    return ok && fileMatches(storedFile.destination(), storedFile.file());
  }

  private boolean synchronizeProductFile(ProductFile productFile, Synchronization monitor) {
    if (productFile.action() == ProductAction.NONE) {
      return true;
    }
    ensureParentDirectory(productFile.destination());
    if (productFile.destination().exists()) {
      if (sameFile(productFile.destination(), productFile.storedFile().destination())) {
        return true;
      }
      monitor.delete(productFile.destination());
    }
    if (!monitor.link(productFile.storedFile().destination(), productFile.destination())) {
      return false;
    }
    return fileMatches(productFile.destination(), productFile.file())
        && sameFile(productFile.destination(), productFile.storedFile().destination());
  }

  private void deleteSpuriousFiles(
      File productDirectory, Product product, Distribution.Synchronization monitor) {
    if (!productDirectory.isDirectory()) {
      return;
    }
    var existingFiles = productDirectory.listFiles();
    if (existingFiles == null) {
      return;
    }
    var requiredFileNames =
        product.getFiles().stream().map(Distribution.FileData::name).collect(Collectors.toSet());
    for (var file : existingFiles) {
      if (requiredFileNames.contains(file.getName()) || isProductMetadataFile(file)) {
        continue;
      }
      monitor.delete(file);
    }
  }

  private boolean isProductMetadataFile(File file) {
    return file.getName().endsWith(".properties") || BUILD_DIGEST_FILE.equals(file.getName());
  }

  private File reusableSource(
      FileData file,
      File storageDestination,
      File productDestination,
      Map<FileData, FileTarget> previouslyAvailable) {
    if (!samePath(storageDestination, productDestination) && fileMatches(productDestination, file)) {
      return productDestination;
    }
    var previous = previouslyAvailable.get(file);
    if (previous != null && fileMatches(previous.destinationFile(), file)) {
      return previous.destinationFile();
    }
    var legacyCommon = legacyCommonStorageFile(storageDestination, file);
    if (!samePath(storageDestination, legacyCommon) && fileMatches(legacyCommon, file)) {
      return legacyCommon;
    }
    return null;
  }

  private File productDirectory(
      File rootDirectory, Distribution.Release release, Distribution.Build build, Product product) {
    return new File(
        new File(
            new File(new File(rootDirectory, this.name), this.version.toString()),
            release.getName()),
        build.getName() + File.separator + product.getName());
  }

  private File commonStorageFile(File rootDirectory, FileData file) {
    return new File(
        new File(new File(rootDirectory, this.name), "common"),
        file.hash() + File.separator + file.name());
  }

  private File legacyCommonStorageFile(File storageDestination, FileData file) {
    var hashDirectory = storageDestination.getParentFile();
    if (hashDirectory == null) {
      return storageDestination;
    }
    var commonDirectory = hashDirectory.getParentFile();
    return commonDirectory == null ? storageDestination : new File(commonDirectory, file.name());
  }

  private boolean productFileLinksStorage(
      File productDestination, File storageDestination, FileData file) {
    return fileMatches(productDestination, file) && sameFile(productDestination, storageDestination);
  }

  private boolean fileMatches(File file, FileData fileData) {
    if (file == null || !file.isFile() || file.length() != fileData.size()) {
      return false;
    }
    var expectedHash = fileData.hash();
    if (expectedHash == null || expectedHash.isBlank()) {
      return true;
    }
    var actualHash = md5(file);
    return actualHash != null && expectedHash.equalsIgnoreCase(actualHash);
  }

  private String md5(File file) {
    try (var input = new BufferedInputStream(new FileInputStream(file))) {
      var digest = MessageDigest.getInstance("MD5");
      var buffer = new byte[8192];
      int read;
      while ((read = input.read(buffer)) >= 0) {
        digest.update(buffer, 0, read);
      }
      var ret = new StringBuilder();
      for (var b : digest.digest()) {
        ret.append(String.format("%02x", b));
      }
      return ret.toString();
    } catch (IOException | NoSuchAlgorithmException e) {
      return null;
    }
  }

  private boolean sameFile(File first, File second) {
    if (first == null || second == null || !first.exists() || !second.exists()) {
      return false;
    }
    try {
      return Files.isSameFile(first.toPath(), second.toPath());
    } catch (IOException e) {
      return false;
    }
  }

  private boolean samePath(File first, File second) {
    if (first == null || second == null) {
      return false;
    }
    return first
        .toPath()
        .toAbsolutePath()
        .normalize()
        .equals(second.toPath().toAbsolutePath().normalize());
  }

  private void ensureParentDirectory(File file) {
    var parent = file == null ? null : file.getParentFile();
    if (parent != null) {
      parent.mkdirs();
    }
  }

  /**
   * Return a map of all files that are not in the common distro but are part of builds that are not
   * the one being synchronized. Used to check when a required file is already present in a previous
   * build.
   *
   * @param beingSynced
   * @return
   */
  private Map<FileData, FileTarget> listExistingFilesNotInCommon(
      Stack.Tag beingSynced, File syncDirectory) {

    var ret = new HashMap<FileData, FileTarget>();
    Map<Stack.Tag, DistributionImpl> localTags = new HashMap<>();
    distributions(this.name, Utils.URLs.newURL(syncDirectory))
        .forEach(d -> d.getTags().forEach(t -> localTags.put(t, d)));

    for (var tag : localTags.keySet()) {

      if (tag.equals(beingSynced) || !tag.availableLocally()) {
        continue;
      }

      var build = localTags.get(tag).findBuild(tag);
      for (var product : build.getProducts()) {
        for (var file : product.getFiles()) {
          if (ret.containsKey(file)) {
            continue;
          }
          var fileInCommon = commonStorageFile(syncDirectory, file);
          var legacyFileInCommon = legacyCommonStorageFile(fileInCommon, file);
          if (fileMatches(fileInCommon, file) || fileMatches(legacyFileInCommon, file)) {
            continue;
          }
          var existing = new File(product.getLocalPath(), file.name());
          if (existing.isFile()) {
            ret.put(file, new FileTarget(Utils.URLs.newURL(existing), existing));
          }
        }
      }
    }
    return ret;
  }

  /**
   * Total size of distribution in bytes.
   *
   * @return
   */
  public long getTotalSize() {
    return getFileCounts().keySet().stream().mapToLong(Distribution.FileData::size).sum();
  }

  public Set<Distribution.FileData> getCommonFiles() {
    return getFileCounts().entrySet().stream()
        .filter(e -> e.getValue() > 1)
        .map(Map.Entry::getKey)
        .collect(Collectors.toSet());
  }

  public String getName() {
    return name;
  }

  @Override
  public Version getVersion() {
    return this.version;
  }

  @Override
  public boolean isOnline() {
    return false;
  }

  @Override
  public List<Stack.Tag> getTags() {
    var ret = new ArrayList<Stack.Tag>();
    for (var release : releases) {
      for (var build : release.getBuilds()) {
        ret.add(
            Stack.Tag.of(
                this.version,
                release.getName(),
                build.getName(),
                build.isAvailableLocally(),
                false));
      }
    }
    return ret;
  }

  @Override
  public boolean verify(Stack.Tag tag) {
    return verify(tag, null);
  }

  @Override
  public boolean verify(Stack.Tag tag, Verification monitor) {
    var build = findBuild(tag);
    if (build != null) {
      var totalFiles =
          build.getProducts().stream().mapToInt(product -> product.getFiles().size()).sum();
      if (monitor != null) {
        monitor.notifyVerification(totalFiles);
      }
      int fileIndex = 0;
      for (var product : build.getProducts()) {
        if (product.getLocalPath() == null || !product.getLocalPath().exists()) {
          return false;
        }
        for (var file : product.getFiles()) {
          var localFile = new File(product.getLocalPath(), file.name());
          var index = ++fileIndex;
          if (monitor != null) {
            monitor.notifyFileVerifying(localFile, file, index);
          }
          var valid = fileMatches(localFile, file);
          if (monitor != null) {
            monitor.notifyFileVerified(localFile, file, index, valid);
          }
          if (!valid) {
            return false;
          }
        }
      }
      return true;
    }
    return false;
  }

  public void setName(String name) {
    this.name = name;
  }

  // Synchronizer for testing, outputting on console only
  static Synchronization loggingSynchronizer =
      new Synchronization() {
        @Override
        public boolean isSynchronizing() {
          return false;
        }

        @Override
        public boolean notifyDownload(
            long totalSize,
            long downloadSize,
            Map<FileData, FileTarget> fullList,
            Map<FileData, FileTarget> downloadList) {

          System.out.println(
              "Download size is "
                  + FileUtils.byteCountToDisplaySize(downloadSize)
                  + "("
                  + downloadList.size()
                  + " files) out of "
                  + FileUtils.byteCountToDisplaySize(totalSize)
                  + " of total storage ("
                  + fullList.size()
                  + " files).");
          if (downloadList.size() <= 15) {
            System.out.println("Files to download:");
            for (var file : downloadList.keySet()) {
              System.out.println("   " + file);
            }
          }

          return false;
        }

        @Override
        public boolean download(URL url, File file, FileData fileData) {
          System.out.println("DOWNLOAD " + url + " -> " + file);
          return true;
        }

        @Override
        public boolean link(File file, File destination) {
          System.out.println("LINK " + file + " -> " + destination);
          return true;
        }

        @Override
        public void delete(File file) {
          System.out.println("DELETE " + file);
        }

        @Override
        public boolean copy(File source, File destination) {
          System.out.println("COPY " + source + " TO " + destination);
          return true;
        }

        @Override
        public void notifyProductSynchronizing(Product product) {}

        @Override
        public void notifyProductSynchronized(Product product) {}
      };

  static Synchronization actingSynchronizer =
      new Synchronization() {
        @Override
        public boolean isSynchronizing() {
          return true;
        }

        @Override
        public boolean notifyDownload(
            long totalSize,
            long downloadSize,
            Map<FileData, FileTarget> fullList,
            Map<FileData, FileTarget> downloadList) {

          System.out.println(
              "Synchronizing "
                  + downloadList.size()
                  + " files: "
                  + FileUtils.byteCountToDisplaySize(downloadSize)
                  + " out of "
                  + FileUtils.byteCountToDisplaySize(totalSize)
                  + " total storage in "
                  + fullList.size()
                  + " files.");
          return true;
        }

        @Override
        public boolean download(URL url, File file, FileData fileData) {
          System.out.println("Downloading " + url + " -> " + file);
          try {
            var parent = file.getParentFile();
            if (parent != null) {
              parent.mkdirs();
            }
            FileUtils.copyURLToFile(url, file);
          } catch (IOException e) {
            return false;
          }
          return true;
        }

        @Override
        public boolean link(File file, File destination) {
          System.out.println("Linking " + file + " -> " + destination);
          var parent = destination.getParentFile();
          if (parent != null) {
            parent.mkdirs();
          }
          return Utils.Files.symlink(file, destination);
        }

        @Override
        public void delete(File file) {
          System.out.println("Deleting " + file);
          FileUtils.deleteQuietly(file);
        }

        @Override
        public boolean copy(File source, File destination) {
          try {
            var parent = destination.getParentFile();
            if (parent != null) {
              parent.mkdirs();
            }
            Files.copy(
                source.toPath(),
                destination.toPath(),
                java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            return true;
          } catch (IOException e) {
            return false;
          }
        }

        @Override
        public void notifyProductSynchronizing(Product product) {}

        @Override
        public void notifyProductSynchronized(Product product) {}
      };

  public Product findProduct(Product.Type productType, Stack.Tag chosenRelease) {
    var build = findBuild(chosenRelease);
    return build == null
        ? null
        : build.getProducts().stream()
            .filter(p -> p.getType() == productType)
            .findFirst()
            .orElse(null);
  }

  public Release findRelease(Stack.Tag chosenRelease) {
    return releases.stream()
        .filter(r -> r.getName().equals(chosenRelease.release()))
        .findFirst()
        .orElse(null);
  }

  public Build findBuild(Stack.Tag chosenRelease) {
    var release = findRelease(chosenRelease);
    return release == null
        ? null
        : release.getBuilds().stream()
            .filter(b -> b.getName().equals(chosenRelease.build()))
            .findFirst()
            .orElse(null);
  }

  /**
   * Pass a properties object, the file to save it on, and a field whose value must be merged with
   * the passed value. If the properties don't exist, create as is, otherwise read them, add the
   * missing values to the property in the originalProperties if not there already, and save the
   * result.
   *
   * @param propertiesToSave
   * @param possiblyExistingPopertiesFile
   * @param buildPropertyToMergeWith
   * @param thisBuildId
   */
  private void synchronizeProperties(
      Properties propertiesToSave,
      File possiblyExistingPopertiesFile,
      String buildPropertyToMergeWith,
      String thisBuildId) {
    var properties = new Properties();
    properties.putAll(propertiesToSave);
    if (possiblyExistingPopertiesFile.exists()) {
      var existingProperties = new Properties();
      if (Utils.Properties.load(possiblyExistingPopertiesFile, existingProperties)) {
        existingProperties.forEach(properties::putIfAbsent);
        var existingBuildIds = existingProperties.getProperty(buildPropertyToMergeWith);
        if (existingBuildIds != null) {
          var existingBuildIdsList =
              new ArrayList<>(
                  Arrays.stream(existingBuildIds.split(",")).filter(s -> !s.isBlank()).toList());
          if (!existingBuildIdsList.contains(thisBuildId)) {
            existingBuildIdsList.addFirst(thisBuildId);
          }
          properties.setProperty(buildPropertyToMergeWith, String.join(",", existingBuildIdsList));
        }
      }
    } else {
      properties.setProperty(buildPropertyToMergeWith, thisBuildId);
    }
    Utils.Properties.save(possiblyExistingPopertiesFile, properties);
  }
}
