package org.integratedmodelling.klab.api.engine.distribution;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.integratedmodelling.klab.api.data.Version;
import org.integratedmodelling.klab.api.exceptions.KlabIllegalArgumentException;
import org.integratedmodelling.klab.api.services.KlabService;
import org.integratedmodelling.klab.api.utils.Utils;

/**
 * {@link Distribution}s compose a k.LAB software stack, corresponding to a software distribution in
 * a given version. The distribution contains a number of releases, each of which can contain builds
 * which contain products.
 *
 * <p>A Distribution is lower-level compared to {@link Stack} and should not be used directly at
 * client side. It is the first-class object for server-side operations when distributions are built
 * and synchronized.
 */
public interface Distribution {

  /**
   * Represents an entry in the filelist that accompanies each build in the distribution. Used as a
   * key for the files to inspect and/or download, represented by {@link FileTarget}. tags
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

    private String name;
    private List<Product> products = new ArrayList<>();

    protected Build(String name, URL url) {
      super(url);
      this.name = name;
      for (var key : this.properties.getProperty(BUILD_PRODUCTS_PROPERTY).split(",")) {
        var productUrl = url.toString().substring(0, url.toString().lastIndexOf("/")) + "/" + key;
        var product =
            new Product(key, Utils.URLs.newURL(productUrl + "/" + PRODUCT_PROPERTIES_FILE));
        if (product.isEmpty()) {
          setEmpty(true);
        }
        this.products.add(product);
      }
    }

    public String getName() {
      return name;
    }

    public List<Product> getProducts() {
      return products;
    }

    public boolean isAvailableLocally() {
      return products.stream().allMatch(p -> p.localPath != null && p.localPath.exists());
    }
  }

  /**
   * A Product is an executable or library that can be installed and run. It is part of a Build,
   * contained in a Release of a Distribution.
   */
  class Product extends Utils.Properties.Container {

    private String name;
    private Type type;
    private Platform platform;
    private URL url;
    private List<FileData> files = new ArrayList<>();
    private File localPath;
    private String executable;

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

        @Override
        public boolean isService() {
          return false;
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

        @Override
        public boolean isService() {
          return true;
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

        @Override
        public boolean isService() {
          return true;
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

        @Override
        public boolean isService() {
          return true;
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

        @Override
        public boolean isService() {
          return true;
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

        @Override
        public boolean isService() {
          return true;
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

        @Override
        public boolean isService() {
          return true;
        }
      },

      AMQP_BROKER {
        @Override
        public String getRemoteUrl(String baseUrl) {
          return baseUrl + "/" + getId() + "/" + Utils.OS.get().toString().toLowerCase();
        }

        @Override
        public String getId() {
          return "klab.amqp.broker";
        }

        @Override
        public String getName() {
          return "k.LAB local AMQP broker";
        }

        @Override
        public int getDebugPort() {
          return 5011;
        }

        @Override
        public int defaultMaxMemoryLimitMB() {
          return 1024;
        }

        @Override
        public boolean isService() {
          return true;
        }
      };

      public static Type forService(KlabService.Type serviceType) {
        return switch (serviceType) {
          case REASONER -> REASONER_SERVICE;
          case RESOURCES -> RESOURCES_SERVICE;
          case RESOLVER -> RESOLVER_SERVICE;
          case RUNTIME -> RUNTIME_SERVICE;
          case DATABASE -> DATABASE_SERVER;
          default ->
              throw new KlabIllegalArgumentException(
                  "wrong service type for product: " + serviceType);
        };
      }

      public static Set<Type> PRIMARY_SERVICES =
          Set.of(REASONER_SERVICE, RESOURCES_SERVICE, RESOLVER_SERVICE, RUNTIME_SERVICE);

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

      public abstract int getDebugPort();

      public abstract boolean isService();

      public abstract int defaultMaxMemoryLimitMB();
    }

    public enum Platform {

      /**
       * Jar packaging with bin/, lib/ and a main jar file with a main class in properties, OS
       * independent distribution with potential OS-specific subcomponents to merge in from subdirs.
       */
      JAR("jar"),

      /**
       * Direct executable packaging. The main class executor named in the product must be the full
       * name of the executable file, without path.
       */
      EXE("exe");

      // user-defined name of the product in build.properties options, set in Maven
      // configuration of klab.product plugin
      public final String userOption;

      Platform(String userOption) {
        this.userOption = userOption;
      }
    }

    protected Product(String name, URL url) {

      super(url);
      this.name = name;
      this.type = Type.valueOf(getProperty(PRODUCT_TYPE_PROPERTY));
      this.platform = Platform.valueOf(getProperty(PRODUCT_PLATFORM_PROPERTY));
      this.executable = getProperty(PRODUCT_MAINCLASS_PROPERTY);
      this.url = Utils.URLs.newURL(url.toString().substring(0, url.toString().lastIndexOf("/")));
      if (this.url.getProtocol().equals("file")) {
        this.localPath = new File(this.url.getFile());
      }

      var fileListUrl = this.url + "/filelist.txt";
      try (BufferedReader reader =
          new BufferedReader(new InputStreamReader(Utils.URLs.newURL(fileListUrl).openStream()))) {
        String line;
        while ((line = reader.readLine()) != null) {
          if (line.trim().isEmpty()) continue;
          files.add(FileData.of(line));
        }
      } catch (IOException e) {
        setEmpty(true);
      }
    }

    public Type getType() {
      return type;
    }

    public URL getUrl() {
      return url;
    }

    public String getName() {
      return name;
    }

    public List<FileData> getFiles() {
      return files;
    }

    public Platform getPlatform() {
      return platform;
    }

    public String getExecutable() {
      return executable;
    }

    /**
     * This will only be null if the product is not available locally. If not null, it comes from a
     * verified distribution.
     *
     * @return
     */
    public File getLocalPath() {
      return localPath;
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

  /**
   * A release corresponds to a branch in a version of a distribution. It may be identified by its
   * own native tag or referenced as "latest", "stable" etc.
   */
  class Release extends Utils.Properties.Container {

    static final String RELEASE_PROPERTIES_FILE = "release.properties";
    static final String BUILD_VERSIONS_PROPERTY = "klab.build.versions";
    static final String RELEASE_NAME_PROPERTY = "klab.release.name";

    private String name;
    private List<Build> builds = new ArrayList<>();

    public Release(URL url) {
      super(url);
      this.name = this.properties.getProperty(RELEASE_NAME_PROPERTY);
      for (var key : this.properties.getProperty(RELEASE_BUILDS_PROPERTY).split(",")) {

        if (key.isEmpty()) {
          // DIOCAN why?
          continue;
        }

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

    public List<Build> getBuilds() {
      return builds;
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

    /**
     * Copy a file from source to destination. Used for copying files from previous builds that may
     * get lost. In some OSs, using hard links is OK here.
     *
     * @param source
     * @param destination
     * @return
     */
    boolean copy(File source, File destination);

    /**
     * Heads-up that the product is about to be synchronized.
     *
     * @param product
     */
    void notifyProductSynchronizing(Product product);

    /**
     * Heads-up that the product has been synchronized.
     *
     * @param product
     */
    void notifyProductSynchronized(Product product);
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
  String PRODUCT_PLATFORM_PROPERTY = "klab.product.platform";
  String PRODUCT_JAVA_OPTIONS_PROPERTY = "klab.product.options.java";

  String DEVELOP_RELEASE = "develop";
  String LATEST_RELEASE = "latest";

  String BUILD_PROPERTIES_FILE = "build.properties";
  String BUILD_DIGEST_FILE = "filelist.txt";
  String PRODUCT_OSSPECIFIC_PROPERTY = "klab.product.osspecific";
  String BUILD_VERSION_PROPERTY = "klab.build.version";
  String PRODUCT_MAINCLASS_PROPERTY = "klab.build.main";
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
  List<Stack.Tag> getTags();

  /**
   * @param rootFolder
   * @param sync
   * @return
   */
  boolean synchronize(File rootFolder, Stack.Tag beingSynchronized, Synchronization sync);

  /**
   * Verify the consistency of a locally available tag by comparing all file hashes in the
   * referenced build. If the tag is not available locally, return false without error.
   *
   * @param tag
   * @return
   */
  boolean verify(Stack.Tag tag);
}
