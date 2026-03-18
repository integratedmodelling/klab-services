//package org.integratedmodelling.common.distribution;
//
//import org.integratedmodelling.klab.api.configuration.Setting;
//import org.integratedmodelling.klab.api.configuration.Settings;
//import org.integratedmodelling.klab.api.data.Version;
//import org.integratedmodelling.klab.api.engine.distribution.Build;
//import org.integratedmodelling.klab.api.engine.distribution.DistributionObsolete;
//import org.integratedmodelling.klab.api.engine.distribution.Product;
//import org.integratedmodelling.klab.api.engine.distribution.Release;
//import org.integratedmodelling.klab.api.utils.Utils;
//
//import java.io.*;
//import java.net.URL;
//import java.time.Instant;
//import java.util.*;
//import java.util.stream.Collectors;
//
//public class DistributionModel extends Utils.Properties.Container {
//
//  private String name;
//  private Instant releaseDate;
//  private List<ProductModel> products = new ArrayList<>();
//  private Version version;
//
//  public static class ProductModel extends Utils.Properties.Container {
//
//    private String name;
//    private String description;
//    private Product.ProductType productType;
//    private Product.Type type;
//    private List<ReleaseModel> releases = new ArrayList<>();
//
//    protected ProductModel(String name, URL url) {
//      super(url);
//      this.name = name;
//      this.productType =
//          Product.ProductType.valueOf(this.properties.getProperty(Product.PRODUCT_CLASS_PROPERTY));
//      this.type =
//          Product.Type.forOption(this.properties.getProperty(Product.PRODUCT_TYPE_PROPERTY));
//      this.description = this.properties.getProperty(Product.PRODUCT_DESCRIPTION_PROPERTY);
//      for (var key : this.properties.getProperty(Product.RELEASE_NAMES_PROPERTY).split(",")) {
//        var releaseUrl =
//            url.toString().substring(0, url.toString().indexOf(Product.PRODUCT_PROPERTIES_FILE))
//                + key
//                + "/release.properties";
//        var release = new ReleaseModel(Utils.URLs.newURL(releaseUrl));
//        if (release.isEmpty()) {
//          setEmpty(true);
//        }
//        this.releases.add(release);
//      }
//    }
//
//    public String getDescription() {
//      return description;
//    }
//
//    public void setDescription(String description) {
//      this.description = description;
//    }
//
//    public Product.ProductType getProductType() {
//      return productType;
//    }
//
//    public void setProductType(Product.ProductType productType) {
//      this.productType = productType;
//    }
//
//    public String getName() {
//      return name;
//    }
//
//    public void setName(String name) {
//      this.name = name;
//    }
//
//    public Product.Type getType() {
//      return type;
//    }
//
//    public void setType(Product.Type type) {
//      this.type = type;
//    }
//
//    public List<ReleaseModel> getReleases() {
//      return releases;
//    }
//
//    public void setReleases(List<ReleaseModel> releases) {
//      this.releases = releases;
//    }
//  }
//
//  public static class ReleaseModel extends Utils.Properties.Container {
//
//    private String name;
//    private List<BuildModel> builds = new ArrayList<>();
//
//    protected ReleaseModel(URL url) {
//      super(url);
//      this.name = this.properties.getProperty(Release.RELEASE_NAME_PROPERTY);
//      for (var key : this.properties.getProperty(Release.BUILD_VERSIONS_PROPERTY).split(",")) {
//        var buildUrl =
//            url.toString().substring(0, url.toString().indexOf(Release.RELEASE_PROPERTIES_FILE))
//                + key
//                + "/build.properties";
//        var build = new BuildModel(key, Utils.URLs.newURL(buildUrl));
//        if (build.isEmpty()) {
//          setEmpty(true);
//        }
//        this.builds.add(build);
//      }
//    }
//
//    public String getName() {
//      return name;
//    }
//
//    public void setName(String name) {
//      this.name = name;
//    }
//
//    public List<BuildModel> getBuilds() {
//      return builds;
//    }
//
//    public void setBuilds(List<BuildModel> builds) {
//      this.builds = builds;
//    }
//  }
//
//  public static class BuildModel extends Utils.Properties.Container {
//
//    private String url;
//    private String name;
//    private String description;
//    private Product.ProductType productType;
//    private Product.Type type;
//    private List<ReleaseModel> releases = new ArrayList<>();
//    private String mainClass;
//    private boolean osSpecific;
//    private Version version;
//    private long timestamp;
//    private List<DistributionObsolete.FileData> files = new ArrayList<>();
//
//    protected BuildModel(String name, URL url) {
//      super(url);
//      this.name = name;
//      this.productType =
//          Product.ProductType.valueOf(this.properties.getProperty(Product.PRODUCT_CLASS_PROPERTY));
//      this.type =
//          Product.Type.forOption(this.properties.getProperty(Product.PRODUCT_TYPE_PROPERTY));
//      this.description = this.properties.getProperty(Product.PRODUCT_DESCRIPTION_PROPERTY);
//      this.mainClass = this.properties.getProperty(Build.BUILD_MAINCLASS_PROPERTY);
//      this.timestamp = Long.parseLong(this.properties.getProperty(Build.BUILD_TIME_PROPERTY));
//      this.version = Version.create(this.properties.getProperty(Build.BUILD_VERSION_PROPERTY));
//      this.osSpecific =
//          Boolean.parseBoolean(this.properties.getProperty(Build.PRODUCT_OSSPECIFIC_PROPERTY));
//      this.url = url.toString().substring(0, url.toString().indexOf(Build.BUILD_PROPERTIES_FILE));
//
//      var filelistUrl = this.url + "filelist.txt";
//
//      try (BufferedReader reader =
//          new BufferedReader(new InputStreamReader(Utils.URLs.newURL(filelistUrl).openStream()))) {
//        String line;
//        while ((line = reader.readLine()) != null) {
//          if (line.trim().isEmpty()) continue;
//          files.add(DistributionObsolete.FileData.of(line));
//        }
//      } catch (IOException e) {
//        setEmpty(true);
//      }
//    }
//
//    public String getName() {
//      return name;
//    }
//
//    public void setName(String name) {
//      this.name = name;
//    }
//
//    public String getUrl() {
//      return url;
//    }
//
//    public void setUrl(String url) {
//      this.url = url;
//    }
//
//    public List<DistributionObsolete.FileData> getFiles() {
//      return files;
//    }
//
//    public void setFiles(List<DistributionObsolete.FileData> files) {
//      this.files = files;
//    }
//
//    public String getDescription() {
//      return description;
//    }
//
//    public void setDescription(String description) {
//      this.description = description;
//    }
//
//    public Product.ProductType getProductType() {
//      return productType;
//    }
//
//    public void setProductType(Product.ProductType productType) {
//      this.productType = productType;
//    }
//
//    public Product.Type getType() {
//      return type;
//    }
//
//    public void setType(Product.Type type) {
//      this.type = type;
//    }
//
//    public List<ReleaseModel> getReleases() {
//      return releases;
//    }
//
//    public void setReleases(List<ReleaseModel> releases) {
//      this.releases = releases;
//    }
//
//    public String getMainClass() {
//      return mainClass;
//    }
//
//    public void setMainClass(String mainClass) {
//      this.mainClass = mainClass;
//    }
//
//    public boolean isOsSpecific() {
//      return osSpecific;
//    }
//
//    public void setOsSpecific(boolean osSpecific) {
//      this.osSpecific = osSpecific;
//    }
//
//    public Version getVersion() {
//      return version;
//    }
//
//    public void setVersion(Version version) {
//      this.version = version;
//    }
//
//    public long getTimestamp() {
//      return timestamp;
//    }
//
//    public void setTimestamp(long timestamp) {
//      this.timestamp = timestamp;
//    }
//  }
//
//  public DistributionModel(Settings settings) {
//    this(Utils.URLs.newURL(settings.get(Setting.DISTRIBUTION_SOURCE_URL, String.class)));
//  }
//
//  private Map<DistributionObsolete.FileData, Integer> getFileCounts() {
//    var counter = new HashMap<DistributionObsolete.FileData, Integer>();
//    for (var product : products) {
//      for (var release : product.getReleases()) {
//        for (var build : release.getBuilds()) {
//          for (var file : build.getFiles()) {
//            counter.put(file, counter.containsKey(file) ? counter.get(file) + 1 : 1);
//          }
//        }
//      }
//    }
//    return counter;
//  }
//
//  public boolean needsSync(DistributionObsolete.FileData file, DistributionObsolete.FileTarget target) {
//    var exists = target.destinationFile().exists();
//    // FIXME should check only jars like this; others should be hash-checked always
//    if (exists && file.name().contains("SNAPSHOT")) {
//      if (target.destinationFile().length() != file.size()) {
//        return true;
//      }
//      // TODO COMPARE HASH IF JAR WITH SAME SIZE OR NON-JAR
//    }
//    return !exists;
//  }
//
//  /**
//   * FIXME THIS SHOULD COMPARE TO THE LATEST BUILD - A NEW BUILD WILL CONTAIN MANY EQUAL FILES IN
//   * DIFFERENT DIRS
//   *
//   * @param rootDirectory
//   * @param monitor
//   * @return
//   */
//  public boolean synchronize(File rootDirectory, DistributionObsolete.Synchronization monitor) {
//
//    var counter = getFileCounts();
//    var commonFiles =
//        counter.entrySet().stream()
//            .filter(e -> e.getValue() > 1)
//            .map(Map.Entry::getKey)
//            .collect(Collectors.toSet());
//
//    Set<DistributionObsolete.FileData> commonFilesChecked = new HashSet<>();
//    Map<DistributionObsolete.FileData, DistributionObsolete.FileTarget> targets = new HashMap<>();
//    Map<DistributionObsolete.FileData, DistributionObsolete.FileTarget> common = new HashMap<>();
//
//    for (var product : products) {
//      for (var release : product.getReleases()) {
//        for (var build : release.getBuilds()) {
//          for (var file : build.getFiles()) {
//            var target =
//                new DistributionObsolete.FileTarget(
//                    Utils.URLs.newURL(build.getUrl() + file.name()),
//                    new File(
//                        rootDirectory
//                            + File.separator
//                            + this.name
//                            + File.separator
//                            + product.name
//                            + File.separator
//                            + release.name
//                            + File.separator
//                            + build.name
//                            + File.separator
//                            + file.name()));
//            targets.put(file, target);
//            if (commonFiles.contains(file) && !common.containsKey(file)) {
//              common.put(
//                  file,
//                  new DistributionObsolete.FileTarget(
//                      Utils.URLs.newURL(build.getUrl() + file.name()),
//                      new File(
//                          rootDirectory
//                              + File.separator
//                              + this.name
//                              + File.separator
//                              + "common"
//                              + File.separator
//                              + file.name())));
//            }
//          }
//        }
//      }
//    }
//
//    // tally the files and build the final full list
//    Map<DistributionObsolete.FileData, DistributionObsolete.FileTarget> fullList = new HashMap<>();
//    Map<DistributionObsolete.FileData, DistributionObsolete.FileTarget> downloadList = new HashMap<>();
//    for (var product : targets.keySet()) {
//      var choice = common.containsKey(product) ? common.get(product) : targets.get(product);
//      fullList.put(product, choice);
//      if (needsSync(product, choice)) {
//        downloadList.put(product, choice);
//      }
//    }
//
//    var totalSize = fullList.keySet().stream().mapToLong(DistributionObsolete.FileData::size).sum();
//    var downloadSize = downloadList.keySet().stream().mapToLong(DistributionObsolete.FileData::size).sum();
//
//    if (!monitor.notifyDownload(totalSize, downloadSize, fullList, downloadList)) {
//      return true;
//    }
//
//    // we have the whole set, operational loop. This tracks the downloads notified to the monitor
//    var downloaded = new HashSet<DistributionObsolete.FileData>();
//
//    if (monitor.isSynchronizing()) {
//      // create distribution directory
//      var distributionDirectory = new File(rootDirectory + File.separator + this.name);
//      if (!distributionDirectory.exists()) {
//        distributionDirectory.mkdirs();
//      }
//      Utils.Properties.save(
//          new File(distributionDirectory, DistributionObsolete.DISTRIBUTION_PROPERTIES_FILE),
//          this.getProperties());
//
//      // create common directory
//      var commonDirectory =
//          new File(rootDirectory + File.separator + this.name + File.separator + "common");
//      commonDirectory.mkdirs();
//    }
//    for (var product : products) {
//      if (monitor.isSynchronizing()) {
//        // create product directory
//        var productDirectory =
//            new File(
//                rootDirectory + File.separator + this.name + File.separator + product.getName());
//        if (!productDirectory.exists()) {
//          productDirectory.mkdirs();
//        }
//        Utils.Properties.save(
//            new File(productDirectory, Product.PRODUCT_PROPERTIES_FILE), product.getProperties());
//      }
//      for (var release : product.getReleases()) {
//        if (monitor.isSynchronizing()) {
//          // create release directory
//          var releaseDirectory =
//              new File(
//                  rootDirectory
//                      + File.separator
//                      + this.name
//                      + File.separator
//                      + product.getName()
//                      + File.separator
//                      + release.name);
//
//          releaseDirectory.mkdirs();
//          Utils.Properties.save(
//              new File(releaseDirectory, Release.RELEASE_PROPERTIES_FILE), release.getProperties());
//        }
//
//        for (var build : release.getBuilds()) {
//          var buildDirectory =
//              new File(
//                  rootDirectory
//                      + File.separator
//                      + this.name
//                      + File.separator
//                      + product.getName()
//                      + File.separator
//                      + release.name
//                      + File.separator
//                      + build.name);
//
//          if (monitor.isSynchronizing()) {
//            // create build directory
//            buildDirectory.mkdirs();
//            Utils.Properties.save(
//                new File(buildDirectory, Build.BUILD_PROPERTIES_FILE), build.getProperties());
//          }
//          Set<File> accepted = new HashSet<>();
//          for (var file : build.getFiles()) {
//            // TODO must download and/or link, then delete spurious
//            if (downloadList.containsKey(file)) {
//
//              if (!downloaded.contains(file)) {
//                if (!monitor.download(
//                    downloadList.get(file).sourceUrl(),
//                    downloadList.get(file).destinationFile(),
//                    file)) {
//                  return false;
//                }
//                downloaded.add(file);
//              }
//
//              var destination =
//                  new File(buildDirectory.getAbsolutePath() + File.separator + file.name());
//
//              accepted.add(destination);
//
//              if (common.containsKey(file)) {
//                if (!monitor.link(downloadList.get(file).destinationFile(), destination)) {
//                  return false;
//                }
//              }
//            }
//          }
//
//          /*
//           * Last sync step: remove any files that don't belong and link any common files whose link
//           * was lost.
//           */
//          if (buildDirectory.isDirectory()) {
//            var existingFileNames =
//                Arrays.stream(buildDirectory.listFiles())
//                    .map(File::getName)
//                    .collect(Collectors.toSet());
//            var requiredFileNames =
//                build.getFiles().stream()
//                    .map(DistributionObsolete.FileData::name)
//                    .collect(Collectors.toSet());
//            existingFileNames.removeAll(requiredFileNames);
//            for (var fileName : existingFileNames) {
//              if (fileName.endsWith(".properties")) {
//                continue;
//              }
//              var file = new File(buildDirectory.getAbsolutePath() + File.separator + fileName);
//              monitor.delete(file);
//            }
//            for (var file : build.getFiles()) {
//              var expected =
//                  new File(buildDirectory.getAbsolutePath() + File.separator + file.name());
//              if (!expected.exists()) {
//                if (common.containsKey(file)) {
//                  if (!monitor.link(common.get(file).destinationFile(), expected)) {
//                    return false;
//                  }
//                } else {
//                  // shouldn't happen
//                  System.out.println("DIO BULLO È SUCCESSO");
//                  return false;
//                }
//              }
//            }
//          }
//
//          // recreate the filelist so that a newer build can reuse the files
//          Utils.Files.writeStringsToFile(
//              build.getFiles().stream()
//                  .map(e -> e.hash() + " " + e.name() + " " + e.size())
//                  .collect(Collectors.toList()),
//              new File(buildDirectory, "filelist.txt"));
//
//          //          monitor.buildDone(build);
//        }
//      }
//    }
//
//    return false;
//  }
//
//  /**
//   * Total size of distribution in bytes.
//   *
//   * @return
//   */
//  public long getTotalSize() {
//    return getFileCounts().keySet().stream().mapToLong(DistributionObsolete.FileData::size).sum();
//  }
//
//  public Set<DistributionObsolete.FileData> getCommonFiles() {
//    return getFileCounts().entrySet().stream()
//        .filter(e -> e.getValue() > 1)
//        .map(Map.Entry::getKey)
//        .collect(Collectors.toSet());
//  }
//
//  public DistributionModel(URL propertiesUrl) {
//    super(propertiesUrl);
//    if (!isEmpty()) {
//      this.name = this.properties.getProperty(DistributionObsolete.DISTRIBUTION_NAME_PROPERTY);
//      this.version =
//          Version.create(this.properties.getProperty(DistributionObsolete.DISTRIBUTION_VERSION_PROPERTY));
//      this.releaseDate =
//          Instant.parse(this.properties.getProperty(DistributionObsolete.DISTRIBUTION_DATE_PROPERTY));
//      for (var key :
//          this.properties.getProperty(DistributionObsolete.DISTRIBUTION_PRODUCTS_PROPERTY).split(",")) {
//        var productUrl =
//            propertiesUrl
//                    .toString()
//                    .substring(
//                        0,
//                        propertiesUrl.toString().indexOf(DistributionObsolete.DISTRIBUTION_PROPERTIES_FILE))
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
//
//  public String getName() {
//    return name;
//  }
//
//  public void setName(String name) {
//    this.name = name;
//  }
//
//  public Instant getReleaseDate() {
//    return releaseDate;
//  }
//
//  public void setReleaseDate(Instant releaseDate) {
//    this.releaseDate = releaseDate;
//  }
//
//  public List<ProductModel> getProducts() {
//    return products;
//  }
//
//  public void setProducts(List<ProductModel> products) {
//    this.products = products;
//  }
//}
