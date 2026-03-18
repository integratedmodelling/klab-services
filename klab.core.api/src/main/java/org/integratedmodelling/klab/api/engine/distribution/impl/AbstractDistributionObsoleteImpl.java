//package org.integratedmodelling.klab.api.engine.distribution.impl;
//
//import org.integratedmodelling.klab.api.data.Version;
//import org.integratedmodelling.klab.api.engine.distribution.Build;
//import org.integratedmodelling.klab.api.engine.distribution.DistributionObsolete;
//import org.integratedmodelling.klab.api.engine.distribution.Product;
//import org.integratedmodelling.klab.api.engine.distribution.RunningInstance;
//import org.integratedmodelling.klab.api.scope.Scope;
//import org.integratedmodelling.klab.api.utils.PropertyBean;
//
//import java.io.File;
//import java.util.ArrayList;
//import java.util.Collection;
//
///**
// * {@link DistributionObsolete} bean which implements all the properties and can be initialized from and
// * saved to a {@link java.util.Properties} object. Subclasses will need to define any further
// * properties.
// */
//public abstract class AbstractDistributionObsoleteImpl extends PropertyBean implements DistributionObsolete {
//
//  public static class StatusImpl implements Status {
//
//    private Product.Status developmentStatus = Product.Status.UNAVAILABLE;
//    private Product.Status downloadedStatus = Product.Status.UNAVAILABLE;
//    private Version installedDownloadedVersion = Version.EMPTY_VERSION;
//    private Version availableDevelopmentVersion = Version.CURRENT_VERSION;
//
//    @Override
//    public Product.Status getDevelopmentStatus() {
//      return this.developmentStatus;
//    }
//
//    @Override
//    public Product.Status getDownloadedStatus() {
//      return this.downloadedStatus;
//    }
//
//    @Override
//    public Version getInstalledDownloadedVersion() {
//      return this.installedDownloadedVersion;
//    }
//
//    @Override
//    public Version getAvailableDownloadedVersion() {
//      return this.availableDevelopmentVersion;
//    }
//
//    public void setDevelopmentStatus(Product.Status developmentStatus) {
//      this.developmentStatus = developmentStatus;
//    }
//
//    public void setDownloadedStatus(Product.Status downloadedStatus) {
//      this.downloadedStatus = downloadedStatus;
//    }
//
//    public void setInstalledDownloadedVersion(Version installedDownloadedVersion) {
//      this.installedDownloadedVersion = installedDownloadedVersion;
//    }
//
//    public Version getAvailableDevelopmentVersion() {
//      return availableDevelopmentVersion;
//    }
//
//    public void setAvailableDevelopmentVersion(Version availableDevelopmentVersion) {
//      this.availableDevelopmentVersion = availableDevelopmentVersion;
//    }
//
//    @Override
//    public String toString() {
//      return "StatusImpl{"
//          + "developmentStatus="
//          + developmentStatus
//          + ", downloadedStatus="
//          + downloadedStatus
//          + ", installedDownloadedVersion="
//          + installedDownloadedVersion
//          + ", availableDevelopmentVersion="
//          + availableDevelopmentVersion
//          + '}';
//    }
//  }
//
//  protected StatusImpl status = new StatusImpl();
//
//  private Collection<Product> products = new ArrayList<>();
//
//  @Override
//  public Collection<Product> getProducts() {
//    return products;
//  }
//
//  public AbstractDistributionObsoleteImpl() {
//    super(null);
//  }
//
//  public AbstractDistributionObsoleteImpl(File file) {
//    super(file);
//  }
//
//  @Override
//  public Product findProduct(Product.ProductType productType) {
//    for (var product : products) {
//      if (product.getProductType() == productType) {
//        return product;
//      }
//    }
//    return null;
//  }
//
//  @Override
//  public Status getStatus() {
//    return status;
//  }
//
//  /**
//   * This is used by {@link LocalBuildImpl} so that we can run the build in more capable
//   * implementations by just overriding this.
//   *
//   * @param build
//   * @param scope
//   * @return
//   */
//  public RunningInstance runBuild(Build build, Scope scope) {
//    return null;
//  }
//
//  public RunningInstance getInstance(Build build, Scope scope) {
//    return null;
//  }
//}
