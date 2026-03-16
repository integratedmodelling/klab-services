package org.integratedmodelling.klab.api.engine.distribution;

import org.integratedmodelling.klab.api.data.Version;

import java.io.File;
import java.net.URL;
import java.util.Collection;
import java.util.Map;

/**
 * A {@link Distribution} is the top-level object in a k.LAB software stack. It contains one or more
 * {@link Product}s. It can be built from a local or remote distribution file or URL; if remote, the
 * distribution will be able of synchronizing its contents with the network.
 *
 * TODO the hierarchy should be distribution/version/release/build/product, not distribution/release/product/build
 *
 * FIXME should keep the name but switch to the new logic in DistributionImpl/ DistributionModel
 */
public interface Distribution {

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

  /**
   * Represents the URL and destination file for a file to be downloaded after the distribution has
   * been synchronized to a folder.
   *
   * @param sourceUrl
   * @param destinationFile
   */
  record FileTarget(URL sourceUrl, java.io.File destinationFile) {}

  /**
   * This gets sent to the engine messaging system to inform of the status of the possible
   * distributions, including both any compiled source distribution and the downloaded one.
   */
  interface Status {

    Product.Status getDevelopmentStatus();

    Product.Status getDownloadedStatus();

    Version getInstalledDownloadedVersion();

    Version getAvailableDownloadedVersion();
  }

  interface Synchronization {

    /**
     * Return false here to skip synchronization and only perform statistics without creating any
     * directory
     */
    boolean isSynchronizing();

    /**
     * Notify what needs to be downloaded and the respective sizes. If #isSynchronizing() returns
     * false, no other method is called.
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
  String DISTRIBUTION_PRODUCTS_PROPERTY = "klab.distribution.products";
  String DISTRIBUTION_VERSION_PROPERTY = "klab.distribution.version";
  String DISTRIBUTION_URL_PROPERTY = "klab.distribution.url";

  boolean isUsable();

  Status getStatus();

  /**
   * A distribution is a list of products. Each may be individually versioned.
   *
   * @return
   */
  Collection<Product> getProducts();

  /**
   * Find the product we have available of the passed type, if any.
   *
   * @param productType
   * @return a product or null.
   */
  Product findProduct(Product.ProductType productType);
}
