package org.integratedmodelling.common.distribution;

import org.apache.commons.io.FileUtils;
import org.integratedmodelling.common.services.ServiceStartupOptions;
import org.integratedmodelling.common.services.client.engine.SettingsImpl;
import org.integratedmodelling.common.services.client.scope.ClientUserScope;
import org.integratedmodelling.common.utils.Utils;
import org.integratedmodelling.klab.api.configuration.Configuration;
import org.integratedmodelling.klab.api.configuration.Setting;
import org.integratedmodelling.klab.api.engine.StartupOptions;
import org.integratedmodelling.klab.api.engine.distribution.Build;
import org.integratedmodelling.klab.api.engine.distribution.Distribution;
import org.integratedmodelling.klab.api.engine.distribution.Product;
import org.integratedmodelling.klab.api.engine.distribution.RunningInstance;
import org.integratedmodelling.klab.api.engine.distribution.impl.AbstractDistributionImpl;
import org.integratedmodelling.klab.api.engine.distribution.impl.LocalProductImpl;
import org.integratedmodelling.klab.api.engine.distribution.impl.ProductImpl;
import org.integratedmodelling.klab.api.exceptions.KlabIOException;
import org.integratedmodelling.klab.api.identities.Federation;
import org.integratedmodelling.klab.api.scope.Scope;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;

/**
 * The main {@link Distribution} implementation looks up a synchronized, remote distribution in the
 * k.LAB configuration directory, and if not found, tries to synchronize the remote repository from
 * any configured URL or from the official public URL in the k.LAB official site.
 *
 * <p>Failing that, it will look up a git repository with Maven artifacts (configured in or using
 * defaults) and builds a distribution out of all the products found in target. This can be used for
 * testing when the code artifacts are there.
 */
public class DistributionImpl extends AbstractDistributionImpl {

  private URL distributionUrl;
  private final File workspace;

  /**
   * Check if there is any trace of a remote distribution on the filesystem (which may be completely
   * unusable).
   *
   * @return
   */
  public static boolean isRemoteDistributionAvailable() {
    File distributionDirectory =
        new File(Configuration.INSTANCE.getDataPath() + File.separator + "distribution");
    if (distributionDirectory.isDirectory()) {
      File propertiesFile =
          new File(distributionDirectory + File.separator + DISTRIBUTION_PROPERTIES_FILE);
      return propertiesFile.isFile();
    }
    return false;
  }

  public static boolean isDevelopmentDistributionAvailable() {
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
      File distributionProperties =
          new File(
              distributionDirectory
                  + File.separator
                  + "klab"
                  + ".distribution"
                  + File.separator
                  + "target"
                  + File.separator
                  + "distribution"
                  + File.separator
                  + Distribution.DISTRIBUTION_PROPERTIES_FILE);
      return distributionProperties.isFile();
    }
    return false;
  }

  public DistributionImpl() {
    this.workspace =
        new File(Configuration.INSTANCE.getDataPath() + File.separator + "distribution");
    this.workspace.mkdirs();
    if (this.workspace.isDirectory()) {
      if (isRemoteDistributionAvailable()) {
        initialize(
            new File(
                Configuration.INSTANCE.getDataPath()
                    + File.separator
                    + "distribution"
                    + File.separator
                    + DISTRIBUTION_PROPERTIES_FILE));
      }
    }
    if (isDevelopmentDistributionAvailable()) {
      status.setDevelopmentStatus(Product.Status.UP_TO_DATE);
    }
  }

  @Override
  protected void initialize(File propertiesFile) {
    super.initialize(propertiesFile);
    File distributionPath = propertiesFile.getParentFile();
    var distributionURL = getProperty(DISTRIBUTION_URL_PROPERTY);
    if (distributionURL != null) {
      try {
        this.distributionUrl = new URL(distributionURL);
      } catch (MalformedURLException e) {
        status.setDownloadedStatus(Product.Status.UNAVAILABLE);
      }
    } else {
      status.setDownloadedStatus(Product.Status.UNAVAILABLE);
    }
    var available = true;
    var obsolete = false;
    for (String productName : getProperty(DISTRIBUTION_PRODUCTS_PROPERTY, "").split(",")) {
      var product =
          new LocalProductImpl(
              new File(
                  distributionPath
                      + File.separator
                      + productName
                      + File.separator
                      + ProductImpl.PRODUCT_PROPERTIES_FILE),
              this);
      this.getProducts().add(product);
      switch (product.getStatus()) {
        case UP_TO_DATE:
          break;
        case UNAVAILABLE:
          available = false;
          break;
        case OBSOLETE:
          obsolete = true;
      }
    }
    status.setDownloadedStatus(
        available
            ? (obsolete ? Product.Status.OBSOLETE : Product.Status.UP_TO_DATE)
            : Product.Status.UNAVAILABLE);
  }

  @Override
  public boolean isUsable() {
    return getProducts().size() >= 4;
  }

  @Override
  public RunningInstance runBuild(Build build, Scope scope) {
    if (build.getLocalWorkspace() != null) {
      var ret = new RunningInstanceImpl(build, scope, makeOptions(build, scope));
      if (ret.start()) {
        return ret;
      }
    }
    return super.runBuild(build, scope);
  }

  public RunningInstance getInstance(Build build, Scope scope) {
    if (build.getLocalWorkspace() != null) {
      return new RunningInstanceImpl(build, scope, makeOptions(build, scope));
    }
    return super.getInstance(build, scope);
  }

  /**
   * Startup options for the specific instance
   *
   * @param build
   * @param scope
   * @return
   */
  private StartupOptions makeOptions(Build build, Scope scope) {

    var ret = new ServiceStartupOptions();
    if (scope instanceof ClientUserScope clientUserScope) {
      if (clientUserScope.getEngine().getFederation() != null) {
        if (build.getProduct().getProductType() == Product.ProductType.RUNTIME_SERVICE
            && Federation.LOCAL_FEDERATION_ID.equals(
                clientUserScope.getEngine().getFederation().getId())) {
          if (clientUserScope
              .getEngine()
              .getSettings()
              .get(Setting.USE_LOCAL_MESSAGE_BROKER, Boolean.class)) {
            ret.setStartLocalBroker(true);
          }
        }
      }
    }

    // TODO remaining options from settings

    return ret;
  }

  private void readFilelist(File f, Map<String, String> map) {

    map.clear();

    /*
     * type 0 = "hash filename" (built by md5sum); type 1 = "file,hash" (built by
     * Maven process). Checked on the first valid line only.
     */
    int type = -1;

    if (f.isFile()) {
      try (var lines = Files.lines(f.toPath(), StandardCharsets.UTF_8)) {
        for (String s : lines.toList()) {

          s = s.trim();

          if (s.isEmpty() || s.startsWith("#")) {
            continue;
          }

          if (type < 0) {
            type = s.contains(",") ? 1 : 0;
          }

          String[] ss = type == 0 ? s.split("\\s+") : s.split(",");
          String checksum = type == 0 ? ss[0] : ss[1];
          String file = type == 0 ? ss[1] : ss[0];

          if (file.startsWith(".")) {
            file = file.substring(1);
          }
          if (file.startsWith("/")) {
            file = file.substring(1);
          }

          if (file.isEmpty()) continue;

          map.put(file, checksum);
        }
      } catch (Exception e) {
        throw new KlabIOException(e);
      }
    }
  }

  /**
   * Load the remote file list in the passed map. Map will be empty if list is not found.
   *
   * @param files
   */
  public void getRemoteFilelist(Map<String, String> files) {

    File f = null;
    try {
      f = File.createTempFile("fls", "txt");
      FileUtils.copyURLToFile(new URI(distributionUrl + "/filelist.txt").toURL(), f);
    } catch (Exception e) {
      throw new KlabIOException(e);
    }
    readFilelist(f, files);
  }

  /**
   * Load the local file list in the passed map. Map will be empty if list is not found.
   *
   * @param localFiles
   */
  public void getLocalFilelist(File workspace, Map<String, String> localFiles) {
    readFilelist(new File(workspace + File.separator + "filelist.txt"), localFiles);
  }

  /**
   * Weak check for an existing distribution. Should actually check for all files in the list, but
   * who wants to do that. When fixed, the filelist should be the last file downloaded, so that's a
   * relatively meaningful check.
   *
   * @return true if the last file in the list has been downloaded successfully.
   */
  public boolean isComplete(File workspace) {
    return workspace != null
        && workspace.exists()
        && new File(workspace + File.separator + "filelist.txt").exists();
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
            Map<Distribution.FileData, Distribution.FileTarget> fullList,
            Map<Distribution.FileData, Distribution.FileTarget> downloadList) {

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
      };

  public static void main(String[] args) {

    var distribution = new DistributionModel(SettingsImpl.forEngine());
    Utils.CLI
        .create()
        .with(
            "status",
            ar -> {
              distribution.synchronize(
                  Configuration.INSTANCE.getDataPath("distribution"), loggingSynchronizer);
            })
        .with(
            "sync",
            ar -> {
              distribution.synchronize(
                  Configuration.INSTANCE.getDataPath("distribution"), actingSynchronizer);
            })
        .run();
  }
}
