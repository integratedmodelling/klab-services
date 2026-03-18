//package org.integratedmodelling.common.distribution;
//
//import org.integratedmodelling.klab.api.configuration.Configuration;
//import org.integratedmodelling.klab.api.data.Version;
//import org.integratedmodelling.klab.api.engine.distribution.DistributionObsolete;
//import org.integratedmodelling.klab.api.engine.distribution.Product;
//import org.integratedmodelling.klab.api.scope.Scope;
//
//import java.io.File;
//
///**
// * A {@link DistributionObsolete} that prioritizes
// * lookup of source code distributions to any synchronizable distro in ~/.klab. The only difference
// * with the regular distribution is that the local installation is given priority and if one is
// * available, {@link #needsSynchronization(Scope)} will return false irrespective of the remote
// * repository.
// */
//public class DevelopmentDistributionObsoleteImpl extends DistributionObsoleteImpl {
//
//  public DevelopmentDistributionObsoleteImpl() {
//    File distributionDirectory =
//        new File(
//            Configuration.INSTANCE.getProperty(
//                Configuration.KLAB_DEVELOPMENT_SOURCE_REPOSITORY,
//                System.getProperty("user.home")
//                    + File.separator
//                    + "git"
//                    + File.separator
//                    + "klab"
//                    + "-services"));
//    if (distributionDirectory.isDirectory()) {
//      File distributionProperties =
//          new File(
//              distributionDirectory
//                  + File.separator
//                  + "klab"
//                  + ".distribution"
//                  + File.separator
//                  + "target"
//                  + File.separator
//                  + "distribution"
//                  + File.separator
//                  + DistributionObsolete.DISTRIBUTION_PROPERTIES_FILE);
//      if (distributionProperties.isFile()) {
//        initialize(distributionProperties);
//      }
//    }
//
//    status.setAvailableDevelopmentVersion(Version.CURRENT_VERSION);
//    status.setDevelopmentStatus(Product.Status.UP_TO_DATE);
//  }
//
//  // TODO handle the needsSynchronization() logics
//}
