//package org.integratedmodelling.common.distribution;
//
//import org.integratedmodelling.klab.api.configuration.Setting;
//import org.integratedmodelling.klab.api.configuration.Settings;
//import org.integratedmodelling.klab.api.data.Version;
//import org.integratedmodelling.klab.api.engine.distribution.Distribution;
//import org.integratedmodelling.klab.api.engine.distribution.RunningInstance;
//import org.integratedmodelling.klab.api.utils.Utils;
//
//import java.net.URL;
//import java.util.List;
//
//public class DistributionGiustaModel extends Utils.Properties.Container
//    implements Distribution {
//
//  public static String DISTRIBUTION_NAME = "klab";
//
//  protected DistributionGiustaModel(URL url) {
//    super(url);
//  }
//
//  @Override
//  public String getName() {
//    return "";
//  }
//
//  @Override
//  public Version getVersion() {
//    return null;
//  }
//
//  @Override
//  public boolean isOnline() {
//    return false;
//  }
//
//  @Override
//  public List<Tag> getTags() {
//    return List.of();
//  }
//
//  @Override
//  public boolean synchronize(Tag tag, Synchronization sync) {
//    return false;
//  }
//
//  @Override
//  public boolean verify(Tag tag) {
//    return false;
//  }
//
//  @Override
//  public Product product(Product.Type productType, Tag chosenRelease) {
//    return null;
//  }
//
//  @Override
//  public RunningInstance getInstance(Product product) {
//    return null;
//  }
//
//  /**
//   * Link this to the main Distribution class and make it the primary distribution key. Must return
//   * the various distributions, one per version available, most recent first.
//   *
//   * @return
//   */
//  public static List<Distribution> getDistributions(Settings settings) {
//
//    // 1. Read anything remote (or fail)
//    Utils.URLs.newURL(settings.get(Setting.DISTRIBUTION_SOURCE_URL, String.class));
//    return List.of();
//  }
//}
