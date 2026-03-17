package org.integratedmodelling.klab.api.engine.distribution;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.integratedmodelling.klab.api.data.Version;
import org.integratedmodelling.klab.api.exceptions.KlabIllegalArgumentException;
import org.integratedmodelling.klab.api.services.KlabService;
import org.integratedmodelling.klab.api.utils.Utils;

/**
 * A {@link DistributionGiusta} is the top-level object in a k.LAB software stack. It contains one
 * or more {@link org.integratedmodelling.klab.api.engine.distribution.Product}s. It can be built
 * from a local or remote distribution file or URL; if remote, the distribution will be able of
 * synchronizing its contents with the network.
 *
 * <p>TODO the hierarchy should be distribution-version/release/build/product
 *
 * <p>FIXME should keep the name but switch to the new logic in DistributionImpl/ DistributionModel
 */
public interface DistributionGiusta {

  /**
   * The tag is the current choice of distribution, incorporating the overall version, the release
   * and the build. Initial status synchronization should return all tags that are available both
   * locally and remotely.
   *
   * @param version the overall version of the distribution. If Version.ANY_VERSION is passed, the
   *     latest available version is located.
   * @param release null means "official", normally master or main.
   * @param build apart from the physical build name, the "latest" build is also admitted.
   * @param availableLocally true if the distribution is available locally
   */
  record Tag(Version version, String release, String build, boolean availableLocally) {
    static Tag of(Version version, String release, String build, boolean availableLocally) {
      return new Tag(version, release, build, availableLocally);
    }

    public static Tag LATEST_STABLE = Tag.of(Version.ANY_VERSION, null, "latest", true);
    public static Tag LATEST_DEVELOP = Tag.of(Version.ANY_VERSION, "develop", "latest", true);
  }

  /**
   * Represents an entry in the filelist that accompanies each build in the distribution. Used as a
   * key for the files to inspect and/or download, represented by {@link FileTarget}.
   *
   * @param hash
   * @param name
   * @param size
   */
  record FileData(String hash, String name, long size) {
    public static FileData of(String string) {
      var parts = string.split("\\s+");
      return new FileData(
          parts[0],
          parts[1].startsWith("./") ? parts[1].substring(2) : parts[1],
          Long.parseLong(parts[2]));
    }
  }

  class Build extends Utils.Properties.Container {

    private String url;
    private String name;
    private String description;
    private List<Product> products = new ArrayList<>();
    private String mainClass;
    private boolean osSpecific;
    private Version version;
    private long timestamp;
    private List<FileData> files = new ArrayList<>();

    protected Build(String name, URL url) {
      super(url);
      this.name = name;
      this.mainClass = this.properties.getProperty(BUILD_MAINCLASS_PROPERTY);
      this.timestamp = Long.parseLong(this.properties.getProperty(BUILD_TIME_PROPERTY));
      this.version = Version.create(this.properties.getProperty(BUILD_VERSION_PROPERTY));
      this.osSpecific =
          Boolean.parseBoolean(this.properties.getProperty(PRODUCT_OSSPECIFIC_PROPERTY));
      this.url = url.toString().substring(0, url.toString().indexOf(BUILD_PROPERTIES_FILE));

      var filelistUrl = this.url + "filelist.txt";

      try (BufferedReader reader =
          new BufferedReader(new InputStreamReader(Utils.URLs.newURL(filelistUrl).openStream()))) {
        String line;
        while ((line = reader.readLine()) != null) {
          if (line.trim().isEmpty()) continue;
          files.add(FileData.of(line));
        }
      } catch (IOException e) {
        setEmpty(true);
      }
    }

    public String getName() {
      return name;
    }

    public void setName(String name) {
      this.name = name;
    }

    public String getUrl() {
      return url;
    }

    public void setUrl(String url) {
      this.url = url;
    }

    public List<FileData> getFiles() {
      return files;
    }

    public void setFiles(List<FileData> files) {
      this.files = files;
    }

    public String getDescription() {
      return description;
    }

    public void setDescription(String description) {
      this.description = description;
    }

    public List<Product> getProducts() {
      return products;
    }

    public void setProducts(List<Product> products) {
      this.products = products;
    }

    public String getMainClass() {
      return mainClass;
    }

    public void setMainClass(String mainClass) {
      this.mainClass = mainClass;
    }

    public boolean isOsSpecific() {
      return osSpecific;
    }

    public void setOsSpecific(boolean osSpecific) {
      this.osSpecific = osSpecific;
    }

    public Version getVersion() {
      return version;
    }

    public void setVersion(Version version) {
      this.version = version;
    }

    public long getTimestamp() {
      return timestamp;
    }

    public void setTimestamp(long timestamp) {
      this.timestamp = timestamp;
    }
  }

  class Product extends Utils.Properties.Container {

    private String name;
    private String description;
    private Type type;
    private Platform platform;
    private List<Release> releases = new ArrayList<>();

    public enum Type {
      CLI {
        @Override
        public String getRemoteUrl(String baseUrl) {
          return baseUrl + "/" + getId();
        }

        @Override
        public String getId() {
          return "cli";
        }

        @Override
        public String getName() {
          return "k.LAB Engine";
        }

        @Override
        public int getDebugPort() {
          return 5005;
        }

        @Override
        public int defaultMaxMemoryLimitMB() {
          return 1024;
        }
      },
      RESOURCES_SERVICE {
        @Override
        public String getRemoteUrl(String baseUrl) {
          return baseUrl + "/" + getId();
        }

        @Override
        public String getId() {
          return "resources";
        }

        @Override
        public String getName() {
          return "k.LAB Resources service";
        }

        @Override
        public int getDebugPort() {
          return 5006;
        }

        @Override
        public int defaultMaxMemoryLimitMB() {
          return 4096;
        }
      },
      REASONER_SERVICE {
        @Override
        public String getRemoteUrl(String baseUrl) {
          return baseUrl + "/" + getId();
        }

        @Override
        public String getId() {
          return "reasoner";
        }

        @Override
        public String getName() {
          return "k.LAB Reasoner service";
        }

        @Override
        public int getDebugPort() {
          return 5007;
        }

        @Override
        public int defaultMaxMemoryLimitMB() {
          return 2048;
        }
      },
      RESOLVER_SERVICE {
        @Override
        public String getRemoteUrl(String baseUrl) {
          return baseUrl + "/" + getId();
        }

        @Override
        public String getId() {
          return "resolver";
        }

        @Override
        public String getName() {
          return "k.LAB Resolver service";
        }

        @Override
        public int getDebugPort() {
          return 5008;
        }

        @Override
        public int defaultMaxMemoryLimitMB() {
          return 2048;
        }
      },
      RUNTIME_SERVICE {
        @Override
        public String getRemoteUrl(String baseUrl) {
          return baseUrl + "/" + getId();
        }

        @Override
        public String getId() {
          return "runtime";
        }

        @Override
        public String getName() {
          return "k.LAB Runtime service";
        }

        @Override
        public int getDebugPort() {
          return 5009;
        }

        @Override
        public int defaultMaxMemoryLimitMB() {
          return 4096;
        }
      },
      LANGUAGE_SERVER {
        @Override
        public String getRemoteUrl(String baseUrl) {
          return null;
        }

        @Override
        public String getId() {
          return "lsp";
        }

        @Override
        public String getName() {
          return "k.LAB LSP language server";
        }

        @Override
        public int getDebugPort() {
          return -1;
        }

        @Override
        public int defaultMaxMemoryLimitMB() {
          return 512;
        }
      },
      DATABASE_SERVER {
        @Override
        public String getRemoteUrl(String baseUrl) {
          return baseUrl + "/" + getId();
        }

        @Override
        public String getId() {
          return "graphdb";
        }

        @Override
        public String getName() {
          return "k.LAB Graph database service";
        }

        @Override
        public int getDebugPort() {
          return 5010;
        }

        @Override
        public int defaultMaxMemoryLimitMB() {
          return 4096;
        }
      },
      MODELER {
        @Override
        public String getRemoteUrl(String baseUrl) {
          return baseUrl + "/" + getId() + "/" + Utils.OS.get().toString().toLowerCase();
        }

        @Override
        public String getId() {
          return "kmodeler";
        }

        @Override
        public String getName() {
          return "k.LAB Modeler";
        }

        @Override
        public int getDebugPort() {
          return 5011;
        }

        @Override
        public int defaultMaxMemoryLimitMB() {
          return 2048;
        }
      };

      public static Type forService(KlabService.Type serviceType) {
        return switch (serviceType) {
          case REASONER -> REASONER_SERVICE;
          case RESOURCES -> RESOURCES_SERVICE;
          case RESOLVER -> RESOLVER_SERVICE;
          case RUNTIME -> RUNTIME_SERVICE;
          default -> throw new KlabIllegalArgumentException("wrong service type for product");
        };
      }

      /**
       * The id used in paths
       *
       * @return the id
       */
      public abstract String getId();

      /**
       * The name used in product.properties
       *
       * @return the name used in product.properties
       */
      public abstract String getName();

      public abstract String getRemoteUrl(String baseUrl);

      public File getLocalPath(String basePath) {
        return new File(basePath + File.separator + getId());
      }

      public abstract int getDebugPort();

      public abstract int defaultMaxMemoryLimitMB();
    }

    enum Platform {
      UNKNOWN("unknown"),

      /**
       * Jar packaging with bin/, lib/ and a main jar file with a main class in properties, OS
       * independent distribution with potential OS-specific subcomponents to merge in from subdirs.
       */
      JAR("jar"),

      /** Installer executable packaging. */
      INSTALLER_EXECUTABLE("installer"),

      /** Direct executable packaging. */
      DIRECT_EXE("exe"),

      /** Eclipse packaging with a zipped or unzipped distribution per supported OS. */
      ECLIPSE("eclipse");

      // user-defined name of the product in build.properties options, set in Maven
      // configuration of klab.product plugin
      public String userOption;

      public static Platform forOption(String option) {
        return switch (option) {
          case "jar" -> JAR;
          case "installer" -> INSTALLER_EXECUTABLE;
          case "exe" -> DIRECT_EXE;
          case "eclipse" -> ECLIPSE;
          default -> UNKNOWN;
        };
      }

      Platform(String userOption) {
        this.userOption = userOption;
      }
    }

    protected Product(String name, URL url) {
      super(url);
      this.name = name;
      this.type = Type.valueOf(this.properties.getProperty(PRODUCT_CLASS_PROPERTY));
      this.platform = Platform.forOption(PRODUCT_TYPE_PROPERTY);
      this.description = this.properties.getProperty(PRODUCT_DESCRIPTION_PROPERTY);
//      for (var key : this.properties.getProperty(RELEASE_NAMES_PROPERTY).split(",")) {
//        var releaseUrl =
//            url.toString().substring(0, url.toString().indexOf(PRODUCT_PROPERTIES_FILE))
//                + key
//                + "/release.properties";
//        var release = new Release(Utils.URLs.newURL(releaseUrl));
//        if (release.isEmpty()) {
//          setEmpty(true);
//        }
//        this.releases.add(release);
//      }
    }

    public String getDescription() {
      return description;
    }

    public void setDescription(String description) {
      this.description = description;
    }

    public Type getType() {
      return type;
    }

    public String getName() {
      return name;
    }

    public void setName(String name) {
      this.name = name;
    }

    public List<Release> getReleases() {
      return releases;
    }

    public void setReleases(List<Release> releases) {
      this.releases = releases;
    }

    public void setType(Type type) {
      this.type = type;
    }

    public Platform getPlatform() {
      return platform;
    }

    public void setPlatform(Platform platform) {
      this.platform = platform;
    }
  }

  /**
   * Represents the URL and destination file for a file to be downloaded after the distribution has
   * been synchronized to a folder.
   *
   * @param sourceUrl
   * @param destinationFile
   */
  record FileTarget(URL sourceUrl, File destinationFile) {}

  class Release extends Utils.Properties.Container {

    static final String RELEASE_PROPERTIES_FILE = "release.properties";
    static final String BUILD_VERSIONS_PROPERTY = "klab.build.versions";
    static final String RELEASE_NAME_PROPERTY = "klab.release.name";

    private String name;
    private List<Build> builds = new ArrayList<>();

    protected Release(URL url) {
      super(url);
      this.name = this.properties.getProperty(RELEASE_NAME_PROPERTY);
      for (var key : this.properties.getProperty(BUILD_VERSIONS_PROPERTY).split(",")) {
        var buildUrl =
            url.toString().substring(0, url.toString().indexOf(RELEASE_PROPERTIES_FILE))
                + key
                + "/build.properties";
        var build = new Build(key, Utils.URLs.newURL(buildUrl));
        if (build.isEmpty()) {
          setEmpty(true);
        }
        this.builds.add(build);
      }
    }

    public String getName() {
      return name;
    }

    public void setName(String name) {
      this.name = name;
    }

    public List<Build> getBuilds() {
      return builds;
    }

    public void setBuilds(List<Build> builds) {
      this.builds = builds;
    }
  }

  /** Synchronization handler for all sync operations and monitoring. */
  interface Synchronization {

    /**
     * Return false here to skip synchronization and only perform statistics without creating any
     * directory
     */
    boolean isSynchronizing();

    /**
     * Notify what needs to be downloaded and the respective sizes prior to any other operation. If
     * #isSynchronizing() returns false, no other method is called.
     *
     * <p>If this returns false,
     *
     * @param totalSize
     * @param downloadSize
     * @param fullList
     * @param downloadList
     */
    boolean notifyDownload(
        long totalSize,
        long downloadSize,
        Map<FileData, FileTarget> fullList,
        Map<FileData, FileTarget> downloadList);

    boolean download(URL url, File file, FileData fileData);

    boolean link(File file, File destination);

    void delete(File file);
  }

  String DISTRIBUTION_PROPERTIES_FILE = "distribution.properties";
  String DISTRIBUTION_NAME_PROPERTY = "klab.distribution.name";
  String DISTRIBUTION_DATE_PROPERTY = "klab.distribution.date";
  String DISTRIBUTION_VERSIONS_PROPERTY = "klab.distribution.versions";

  String VERSION_PROPERTIES_FILE = "version.properties";
  String VERSION_NAME_PROPERTY = "klab.version.name";
  String VERSION_RELEASES_PROPERTY = "klab.version.releases";

  String RELEASE_PROPERTIES_FILE = "release.properties";
  String RELEASE_NAME_PROPERTY = "klab.release.name";
  String RELEASE_BUILDS_PROPERTY = "klab.release.builds";

  String BUILD_PRODUCTS_PROPERTY = "klab.build.products";
  String BUILD_NAME_PROPERTY = "klab.build.name";

  String PRODUCT_PROPERTIES_FILE = "product.properties";
  String PRODUCT_NAME_PROPERTY = "klab.product.name";
  String PRODUCT_DESCRIPTION_PROPERTY = "klab.product.description";
  String PRODUCT_TYPE_PROPERTY = "klab.product.type";
  String PRODUCT_CLASS_PROPERTY = "klab.product.class";

  String DEVELOP_RELEASE = "develop";
  String LATEST_RELEASE = "latest";
  String RELEASE = "release";
  String DEFAULT_RELEASE_URL = "https://products.integratedmodelling.org/klab/";
  String BUILD_PROPERTIES_FILE = "build.properties";
  String BUILD_DIGEST_FILE = "filelist.txt";
  String PRODUCT_OSSPECIFIC_PROPERTY = "klab.build.osspecific";
  String BUILD_VERSION_PROPERTY = "klab.build.version";
  String BUILD_MAINCLASS_PROPERTY = "klab.build.main";
  String BUILD_TIME_PROPERTY = "klab.build.time";
  String BUILD_WORKSPACE_PROPERTY = "klab.build.workspace";

  /**
   * The distribution name, e.g. <code>klab</code>
   *
   * @return
   */
  String getName();

  /**
   * The distribution version, e.g. <code>1.0.0-SNAPSHOT</code>. Each version corresponds to a
   * separate distribution object.
   *
   * @return
   */
  Version getVersion();

  /**
   * If true, this means that the distribution has a findable online counterpart - not that it is
   * available, synchronized or anything else. It will return false for a completely up to date
   * distribution whose online counterpart isn't accessible, or true for a distribution that is
   * online but not available locally.
   *
   * @return
   */
  boolean isOnline();

  /**
   * Return all available tags for this distribution. If there are locally available tags, the
   * distribution is usable without sync.
   *
   * <p>Upon construction or at least at the first request, the distribution should read the online
   * status to return the tags that are available online. Any necessary status info can be retrieved
   * by processing the tag list. Implementations should stay up to date by reading the online status
   * at regular intervals.
   *
   * @return all available tags both locally and online
   */
  List<Tag> getTags();

  /**
   * Ensure that the passed tag is available locally, downloading whatever is necessary. May run
   * long. A tag that available locally may be verified at the discretion of the implementation.
   *
   * <p>The synchronization should refer to a local directory chosen by the implementation.
   *
   * @param tag
   * @param sync
   * @return
   */
  boolean synchronize(Tag tag, Synchronization sync);

  /**
   * Verify the consistency of a locally available tag by comparing all file hashes in the
   * referenced build. If the tag is not available locally, return false without error.
   *
   * @param tag
   * @return
   */
  boolean verify(Tag tag);

  /**
   * Find the product of the specified type in the specified tag, which must be locally available.
   * Null tag means use the latest available.
   *
   * @param productType
   * @return a product or null if the tag is not available.
   */
  Product product(Product.Type productType, Tag chosenRelease);

  /**
   * Get an instance of the passed product, which will refer to a specific locally available tag.
   * The instance may be already running or requiring startup.
   *
   * @param product
   * @return
   */
  RunningInstance getInstance(Product product);
}
