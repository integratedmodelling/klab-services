package org.integratedmodelling.common.distribution;

import java.io.*;
import java.net.URL;
import java.util.*;
import java.util.stream.Collectors;

import org.apache.commons.io.FileUtils;
import org.integratedmodelling.common.services.client.engine.SettingsImpl;
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
          var devTag =
              new Stack.Tag(Version.HEAD, tag.release(), tag.build(), tag.availableLocally());
          ret.put(devTag, developmentDistribution);
        }
      }
    }

    var localDistributions =
        distributions(
            distributionName,
            Utils.URLs.newURL(
                new File(
                    Configuration.INSTANCE.getDataPath("distribution")
                        + File.separator
                        + distributionName)));

    // nah needs to sync first
    for (var localDistribution : localDistributions) {
      localDistribution.getTags().forEach(tag -> ret.put(tag, localDistribution));
    }

    //    var remoteDistributions =
    //        distributions(
    //            distributionName,
    //            Utils.URLs.newURL(settings.get(Setting.DISTRIBUTION_SOURCE_URL, String.class)));

    // TODO merge or sync

    return ret;
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
        for (var version : properties.getProperty(DISTRIBUTION_VERSIONS_PROPERTY).split(",")) {
          if (Version.CURRENT_VERSION.compatible(Version.create(version))) {
            return new DistributionImpl(
                distributionName, Utils.URLs.newURL(new File(distributionFolder, version)));
          }
        }
      }
    }

    return null;
  }

  static List<DistributionImpl> distributions(String distributionName, URL url) {

    /*
     * Remote first. This may fail
     */
    var ret = new ArrayList<DistributionImpl>();
    var properties = Utils.Properties.create(Utils.URLs.newURL(url + "/distribution.properties"));
    if (!properties.isEmpty()) {
      for (var version : properties.getProperty(DISTRIBUTION_VERSIONS_PROPERTY).split(",")) {
        ret.add(
            new DistributionImpl(
                distributionName,
                Utils.URLs.newURL(url + "/" + version + "/distribution.properties")));
      }
      return ret;
    }

    /*
     * Now check locally and see if (1) we have OTHER distributions and (2) the ones we have are
     * synchronized
     */

    /*
     * Check if a development distro is available. Use settings to override the dev folder
     */

    /*
     * Build the tags for all existing distributions
     */

    return ret;
  }

  private String name;
  private Version version;
  private List<Release> releases = new ArrayList<>();

  public DistributionImpl(String distributionName, URL versionUrl) {
    super(Utils.URLs.newURL(versionUrl + "/version.properties"));
    this.name = distributionName;
    this.version = Version.create(getProperty(VERSION_NAME_PROPERTY));
    for (var release : getProperty(VERSION_RELEASES_PROPERTY).split(",")) {
      this.releases.add(
          new Distribution.Release(
              Utils.URLs.newURL(versionUrl + "/" + release + "/" + RELEASE_PROPERTIES_FILE)));
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

  public boolean needsSync(Distribution.FileData file, Distribution.FileTarget target) {
    var exists = target.destinationFile().exists();
    // FIXME should check only jars like this; others should be hash-checked always
    if (exists && file.name().contains("SNAPSHOT")) {
      if (target.destinationFile().length() != file.size()) {
        return true;
      }
      // TODO COMPARE HASH IF JAR WITH SAME SIZE OR NON-JAR
    }
    return !exists;
  }

  /**
   * FIXME THIS SHOULD COMPARE TO THE LATEST BUILD - A NEW BUILD WILL CONTAIN MANY EQUAL FILES IN
   * DIFFERENT DIRS
   *
   * @param rootDirectory
   * @param monitor
   * @return
   */
  public boolean synchronize(File rootDirectory, Distribution.Synchronization monitor) {

    var counter = getFileCounts();
    var commonFiles =
        counter.entrySet().stream()
            .filter(e -> e.getValue() > 1)
            .map(Map.Entry::getKey)
            .collect(Collectors.toSet());

    Set<Distribution.FileData> commonFilesChecked = new HashSet<>();
    Map<Distribution.FileData, Distribution.FileTarget> targets = new HashMap<>();
    Map<Distribution.FileData, Distribution.FileTarget> common = new HashMap<>();

    for (var release : releases) {
      for (var build : release.getBuilds()) {
        for (var product : build.getProducts()) {
          for (var file : product.getFiles()) {
            var target =
                new Distribution.FileTarget(
                    Utils.URLs.newURL(product.getUrl() + file.name()),
                    new File(
                        rootDirectory
                            + File.separator
                            + this.name
                            + File.separator
                            + this.version
                            + File.separator
                            + release.getName()
                            + File.separator
                            + build.getName()
                            + File.separator
                            + product.getName()
                            + File.separator
                            + file.name()));
            targets.put(file, target);
            if (commonFiles.contains(file) && !common.containsKey(file)) {
              common.put(
                  file,
                  new Distribution.FileTarget(
                      Utils.URLs.newURL(product.getUrl() + file.name()),
                      new File(
                          rootDirectory
                              + File.separator
                              + this.name
                              + File.separator
                              + "common"
                              + File.separator
                              + file.name())));
            }
          }
        }
      }
    }

    // tally the files and build the final full list
    Map<Distribution.FileData, Distribution.FileTarget> fullList = new HashMap<>();
    Map<Distribution.FileData, Distribution.FileTarget> downloadList = new HashMap<>();
    for (var product : targets.keySet()) {
      var choice = common.containsKey(product) ? common.get(product) : targets.get(product);
      fullList.put(product, choice);
      if (needsSync(product, choice)) {
        downloadList.put(product, choice);
      }
    }

    var totalSize = fullList.keySet().stream().mapToLong(Distribution.FileData::size).sum();
    var downloadSize = downloadList.keySet().stream().mapToLong(Distribution.FileData::size).sum();
    if (!monitor.notifyDownload(totalSize, downloadSize, fullList, downloadList)) {
      return true;
    }

    // we have the whole set, operational loop. This tracks the downloads notified to the monitor
    var downloaded = new HashSet<Distribution.FileData>();

    if (monitor.isSynchronizing()) {
      // create distribution directory
      var distributionDirectory = new File(rootDirectory + File.separator + this.name);
      if (!distributionDirectory.exists()) {
        distributionDirectory.mkdirs();
      }
      Utils.Properties.save(
          new File(distributionDirectory, Distribution.DISTRIBUTION_PROPERTIES_FILE),
          this.getProperties());

      // create common directory
      var commonDirectory =
          new File(rootDirectory + File.separator + this.name + File.separator + "common");
      commonDirectory.mkdirs();
    }

    // create version directory if not there, then release, then build and products. They may
    // all be there
    var versionDirectory =
        new File(rootDirectory + File.separator + this.name + File.separator + this.version);
    versionDirectory.mkdirs();
    // sync any contents of distribution.properties to contain the current version

    for (var release : releases) {
      var releaseDirectory =
          new File(versionDirectory.getAbsolutePath() + File.separator + release);
      releaseDirectory.mkdirs();
      // sync any contents of version.properties to contain the current release

      for (var build : release.getBuilds()) {
        var buildDirectory =
            new File(releaseDirectory.getAbsolutePath() + File.separator + build.getName());
        buildDirectory.mkdirs();
        build.save(new File(buildDirectory, BUILD_PROPERTIES_FILE));

        for (var product : build.getProducts()) {

          var productDirectory =
              new File(buildDirectory.getAbsolutePath() + File.separator + product.getName());
          productDirectory.mkdirs();

          Set<File> accepted = new HashSet<>();
          for (var file : product.getFiles()) {
            // TODO must download and/or link, then delete spurious
            if (downloadList.containsKey(file)) {

              if (!downloaded.contains(file)) {
                if (!monitor.download(
                    downloadList.get(file).sourceUrl(),
                    downloadList.get(file).destinationFile(),
                    file)) {
                  return false;
                }
                downloaded.add(file);
              }

              var destination =
                  new File(productDirectory.getAbsolutePath() + File.separator + file.name());

              accepted.add(destination);

              if (common.containsKey(file)) {
                if (!monitor.link(downloadList.get(file).destinationFile(), destination)) {
                  return false;
                }
              }
            }
          }

          /* Last sync step: remove any files that don't belong and link any common
          files whose link was lost. */
          if (productDirectory.isDirectory()) {
            var existingFileNames =
                Arrays.stream(productDirectory.listFiles())
                    .map(File::getName)
                    .collect(Collectors.toSet());
            var requiredFileNames =
                product.getFiles().stream()
                    .map(Distribution.FileData::name)
                    .collect(Collectors.toSet());
            existingFileNames.removeAll(requiredFileNames);
            for (var fileName : existingFileNames) {
              if (fileName.endsWith(".properties")) {
                continue;
              }
              var file = new File(productDirectory.getAbsolutePath() + File.separator + fileName);
              monitor.delete(file);
            }
            for (var file : product.getFiles()) {
              var expected =
                  new File(productDirectory.getAbsolutePath() + File.separator + file.name());
              if (!expected.exists()) {
                if (common.containsKey(file)) {
                  if (!monitor.link(common.get(file).destinationFile(), expected)) {
                    return false;
                  }
                } else {
                  // shouldn't happen
                  System.out.println("DIO BULLO È SUCCESSO");
                  return false;
                }
              }
            }
          }

          // recreate the filelist so that a newer build can reuse the files
          Utils.Files.writeStringsToFile(
              product.getFiles().stream()
                  .map(e -> e.hash() + " " + e.name() + " " + e.size())
                  .collect(Collectors.toList()),
              new File(buildDirectory, "filelist.txt"));

          monitor.notifyProductSynchronized(product);

          build.save(new File(buildDirectory, BUILD_PROPERTIES_FILE));
        }
      }
    }

    return false;
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

  //  public DistributionImpl(URL propertiesUrl) {
  //    super(propertiesUrl);
  //    if (!isEmpty()) {
  //      this.name = this.properties.getProperty(Distribution.DISTRIBUTION_NAME_PROPERTY);
  //      this.version =
  //
  // Version.create(this.properties.getProperty(Distribution.DISTRIBUTION_VERSION_PROPERTY));
  //      this.releaseDate =
  //          Instant.parse(this.properties.getProperty(Distribution.DISTRIBUTION_DATE_PROPERTY));
  //      for (var key :
  //          this.properties.getProperty(Distribution.DISTRIBUTION_PRODUCTS_PROPERTY).split(",")) {
  //        var productUrl =
  //            propertiesUrl
  //                    .toString()
  //                    .substring(
  //                        0,
  //
  // propertiesUrl.toString().indexOf(Distribution.DISTRIBUTION_PROPERTIES_FILE))
  //                + key
  //                + "/product.properties";
  //        var product = new ProductModel(key, Utils.URLs.newURL(productUrl));
  //        if (product.isEmpty()) {
  //          setEmpty(true);
  //        }
  //        this.products.add(product);
  //      }
  //    }
  //  }

  public String getName() {
    return name;
  }

  @Override
  public Version getVersion() {
    return null;
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
                this.version, release.getName(), build.getName(), build.isAvailableLocally()));
      }
    }
    return ret;
  }

  @Override
  public boolean verify(Stack.Tag tag) {
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
              "Must synchronize "
                  + downloadList.size()
                  + " files: "
                  + FileUtils.byteCountToDisplaySize(downloadSize)
                  + " out of "
                  + FileUtils.byteCountToDisplaySize(totalSize)
                  + " total storage in "
                  + fullList.size()
                  + " files.");

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
            FileUtils.copyURLToFile(url, file);
          } catch (IOException e) {
            return false;
          }
          return true;
        }

        @Override
        public boolean link(File file, File destination) {
          System.out.println("Linking " + file + " -> " + destination);
          return Utils.Files.symlink(file, destination);
        }

        @Override
        public void delete(File file) {
          System.out.println("Deleting " + file);
          FileUtils.deleteQuietly(file);
        }

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
}
