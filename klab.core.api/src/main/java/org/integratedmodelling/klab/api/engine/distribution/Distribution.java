package org.integratedmodelling.klab.api.engine.distribution;

import org.integratedmodelling.klab.api.data.Version;
import org.integratedmodelling.klab.api.engine.Engine;
import org.integratedmodelling.klab.api.scope.Scope;
import org.integratedmodelling.klab.api.services.KlabService;

import java.io.File;
import java.util.Collection;

/**
 * A {@link Distribution} is the top-level object in a k.LAB software stack. It contains one or more
 * {@link Product}s. It can be built from a local or remote distribution file or URL; if remote, the
 * distribution will be able of synchronizing its contents with the network.
 */
public interface Distribution {

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

  /**
   * Use one of these to implement progress monitoring for downloads.
   *
   * @author Ferd
   */
  interface SynchronizationMonitor {

    /**
     * @param file
     */
    void beforeDownload(String file);

    /**
     * This is only called when preparing an incremental update from a previous distribution, which
     * can run relatively long.
     */
    void notifyDownloadPreparationStart();

    /**
     * This is only called when preparing an incremental update from a previous distribution, which
     * can run relatively long.
     */
    void notifyDownloadPreparationEnd();

    void notifyFileProgress(String file, long bytesSoFar, long totalBytes);

    /**
     * @param localFile
     */
    void beforeDelete(File localFile);

    /**
     * @param downloadFilecount
     * @param deleteFileCount
     */
    void notifyDownloadCount(int downloadFilecount, int deleteFileCount);

    /**
     * Notify an error
     *
     * @param e an exception
     */
    void notifyError(Exception e);

    /** */
    void transferFinished(Exception e);
  }

  String DISTRIBUTION_PROPERTIES_FILE = "distribution.properties";
  String DISTRIBUTION_NAME_PROPERTY = "klab.distribution.name";
  String DISTRIBUTION_DATE_PROPERTY = "klab.distribution.date";
  String DISTRIBUTION_PRODUCTS_PROPERTY = "klab.distribution.products";
  String DISTRIBUTION_URL_PROPERTY = "klab.distribution.url";

  /**
   * Synchronize with the remote peer, if there is one. In order to know if there is any
   * synchronization to be done, scan the products and compare versions. Synchronization will use
   * the scope using send() to monitor all events implied in the operations and handle
   * interruptions. The scope may also determine which products can be accessed and how.
   *
   * <p>TODO we should add optional parameters for sync of the latest version only, or whichever
   * release is defined in the product as the currently chosen one.
   *
   * @param scope
   */
  void synchronize(Scope scope, SynchronizationMonitor listener);

  /**
   * If true, synchronize() may be called to update the distribution. This will return false without
   * exceptions also in case of connection errors or other failures.
   *
   * @param scope
   * @return true if synchronization is needed
   */
  boolean needsSynchronization(Scope scope);

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
