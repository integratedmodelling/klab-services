package org.integratedmodelling.common.distribution;

import org.apache.commons.io.FileUtils;
import org.integratedmodelling.common.authentication.AnonymousUser;
import org.integratedmodelling.common.authentication.scope.AbstractDelegatingScope;
import org.integratedmodelling.common.authentication.scope.ChannelImpl;
import org.integratedmodelling.common.services.ServiceStartupOptions;
import org.integratedmodelling.common.utils.Utils;
import org.integratedmodelling.klab.api.configuration.Configuration;
import org.integratedmodelling.klab.api.engine.StartupOptions;
import org.integratedmodelling.klab.api.engine.distribution.Build;
import org.integratedmodelling.klab.api.engine.distribution.Distribution;
import org.integratedmodelling.klab.api.engine.distribution.Product;
import org.integratedmodelling.klab.api.engine.distribution.RunningInstance;
import org.integratedmodelling.klab.api.engine.distribution.impl.AbstractDistributionImpl;
import org.integratedmodelling.klab.api.engine.distribution.impl.LocalProductImpl;
import org.integratedmodelling.klab.api.engine.distribution.impl.ProductImpl;
import org.integratedmodelling.klab.api.exceptions.KlabException;
import org.integratedmodelling.klab.api.exceptions.KlabIOException;
import org.integratedmodelling.klab.api.scope.Scope;
import org.integratedmodelling.klab.api.services.KlabService;

import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.attribute.PosixFilePermission;
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

  private String DEFAULT_DISTRIBUTION_DESCRIPTOR =
      "https://resources.integratedmodelling.org/klab/products/klab/distribution.properties";

  private boolean isRemote = false;
  private URL distributionUrl;

  //  /**
  //   * When the URL is known and the distribution may or may not have been synchronized, use this
  // to
  //   * download whatever is necessary and add the URL to the distribution properties. After that,
  // the
  //   * other constructor may be used.
  //   *
  //   * @param url
  //   * @param scope
  //   * @param synchronizeIfIncomplete
  //   * @param synchronizeAnyway
  //   */
  //  public DistributionImpl(
  //      URL url, Scope scope, boolean synchronizeIfIncomplete, boolean synchronizeAnyway) {
  //    // TODO launch synchronization with messages to scope
  //  }

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
    File distributionDirectory =
        new File(Configuration.INSTANCE.getDataPath() + File.separator + "distribution");
    if (distributionDirectory.isDirectory()) {
      isRemote = true;
    }
    if (!distributionDirectory.isDirectory()) {
      distributionDirectory =
          new File(
              Configuration.INSTANCE.getProperty(
                  Configuration.KLAB_DEVELOPMENT_SOURCE_REPOSITORY,
                  System.getProperty("user.home")
                      + File.separator
                      + "git"
                      + File.separator
                      + "klab"
                      + "-services"));
    }
    if (!distributionDirectory.isDirectory()) {
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
      if (distributionProperties.isFile()) {
        initialize(distributionProperties);
      }
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
        isRemote = false;
      }
    } else {
      isRemote = false;
    }
    for (String productName : getProperty(DISTRIBUTION_PRODUCTS_PROPERTY, "").split(",")) {
      this.getProducts()
          .add(
              new LocalProductImpl(
                  new File(
                      distributionPath
                          + File.separator
                          + productName
                          + File.separator
                          + ProductImpl.PRODUCT_PROPERTIES_FILE),
                  this));
    }
  }

  @Override
  public void synchronize(Scope scope, SynchronizationMonitor listener) {
    if (isRemote && distributionUrl != null) {

      var builds = new ArrayList<Build>();

      for (var product : getProducts()) {
        for (var release : product.getReleases()) {
          for (var build : release.getBuilds()) {
            builds.add(build);
          }
        }
      }

      for (var build : builds) {}
    }
  }

  @Override
  public boolean needsSynchronization(Scope scope) {
    // TODO
    return false;
  }

  public boolean isAvailable() {
    return getProducts().size() > 0;
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
    // TODO
    return new ServiceStartupOptions();
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
      FileUtils.copyURLToFile(new URL(distributionUrl + "/filelist.txt"), f);
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

  /**
   * Synchronize the necessary files. Will do nothing (and return true) if we have elected to use a
   * local installation. Will return false if we're not network-enabled or the selected server is
   * offline.
   *
   * @return true if synchronization was successful.
   */
  public boolean sync(File workspace, SynchronizationMonitor monitor) {

    var toDownload = new HashMap<String, String>();
    var toRemove = new ArrayList<File>();

    var remote = new HashMap<String, String>();
    var local = new HashMap<String, String>();

    var exec = new HashSet<PosixFilePermission>();
    exec.add(PosixFilePermission.OWNER_EXECUTE);
    exec.add(PosixFilePermission.OWNER_READ);
    exec.add(PosixFilePermission.OWNER_WRITE);

    getRemoteFilelist(remote);
    getLocalFilelist(workspace, local);

    // process the filelist.txt entry last, so that the distrib only returns
    // isComplete when it
    // got to the end.
    for (String s : remote.keySet()) {
      if (!local.containsKey(s)
          || !local.get(s).equals(remote.get(s))
          || !getDestinationFile(workspace, s).exists()) {
        if (!s.equals("filelist.txt")) toDownload.put(s, remote.get(s));
      }
    }
    toDownload.put("filelist.txt", null);

    /*
     * TODO scan workspace and schedule anything that isn't in the file list for
     * deletion.
     */
    scanForDeletion(workspace, workspace, remote, toRemove);

    if (monitor != null) {
      monitor.notifyDownloadCount(toDownload.size(), toRemove.size());
    }

    workspace.mkdirs();
    Exception downloadError = null;

    for (String f : toDownload.keySet()) {
      if (monitor != null) {
        monitor.beforeDownload(f);
      }
      try {
        new Downloader(
                new URL(distributionUrl + "/" + f),
                getDestinationFile(workspace, f),
                (sofar, total) -> {
                  if (monitor != null) {
                    monitor.notifyFileProgress(f, sofar, total);
                  }
                },
                toDownload.get(f))
            .download();

        if (f.endsWith(".sh")) {
          // bit of a hack, but that should make things work on Linux
          // and MacOS.
          Files.setPosixFilePermissions(getDestinationFile(workspace, f).toPath(), exec);
        }
      } catch (UnsupportedOperationException e) {
        // ignore
      } catch (IOException e) {
        monitor.notifyError(e);
        break;
      } catch (KlabException e) {
        monitor.notifyError(e);
        if (e.getCause() instanceof KlabIOException) {
          downloadError = e;
        }
        break;
      }
    }
    if (downloadError == null) {
      for (var f : toRemove) {
        if (monitor != null) {
          monitor.beforeDelete(f);
        }
        FileUtils.deleteQuietly(f);
      }
    }
    if (monitor != null) {
      monitor.transferFinished(downloadError);
    }

    return true;
  }

  private void scanForDeletion(
      File workspace, File file, HashMap<String, String> remote, ArrayList<File> toRemove) {

    if (file.isDirectory()) {
      for (File f : file.listFiles()) {
        scanForDeletion(workspace, f, remote, toRemove);
      }
    } else {

      String fname =
          ("." + file.toString().substring(workspace.toString().length())).replaceAll("\\\\", "/");

      if (fname.startsWith(".")) fname = fname.substring(1);
      if (fname.startsWith("/")) fname = fname.substring(1);

      if (!fname.isEmpty()
          && !fname.equals("filelist.txt")
          && !fname.endsWith(".log")
          && !remote.containsKey(fname)) {
        toRemove.add(file);
      }
    }
  }

  private File getDestinationFile(File workspace, String f) {

    String[] fpath = f.split("\\/");
    String pref = workspace.toString();
    for (int i = 0; i < fpath.length - 1; i++) {
      pref += File.separator + fpath[i];
    }
    new File(pref).mkdirs();
    return new File(pref + File.separator + fpath[fpath.length - 1]);
  }

  public static void main(String[] args) {

    var distribution = new DistributionImpl();
    var resources = distribution.findProduct(Product.ProductType.RESOURCES_SERVICE);
    var instance =
        resources.launch(
            new AbstractDelegatingScope(new ChannelImpl(new AnonymousUser())) {
              @Override
              public <T extends KlabService> T getService(Class<T> serviceClass) {
                return null;
              }

              @Override
              public <T extends KlabService> Collection<T> getServices(Class<T> serviceClass) {
                return null;
              }

              @Override
              public Type getType() {
                return Type.SERVICE;
              }
            });
    while (instance.getStatus() != RunningInstance.Status.STOPPED) {
      try {
        Thread.sleep(200);
      } catch (InterruptedException e) {
        return;
      }
    }
  }
}
