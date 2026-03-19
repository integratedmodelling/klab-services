package org.integratedmodelling.common.distribution;

import org.integratedmodelling.common.configuration.CommonConfiguration;
import org.integratedmodelling.common.services.client.engine.SettingsImpl;
import org.integratedmodelling.common.utils.Utils;
import org.integratedmodelling.klab.api.Klab;
import org.integratedmodelling.klab.api.configuration.Setting;
import org.integratedmodelling.klab.api.configuration.Settings;
import org.integratedmodelling.klab.api.engine.distribution.Distribution;
import org.integratedmodelling.klab.api.engine.distribution.LocalInstance;
import org.integratedmodelling.klab.api.engine.distribution.Stack;

import java.io.File;
import java.net.URL;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

public class StackImpl implements Stack {

  private static final Map<Tag, DistributionImpl> TAGS = new TreeMap<>();

  private final String name;
  private final Settings settings;

  /**
   * Pass settings to determine whether the stack accepts development distributions, where the
   * distribution cache should be, the level of "experimental" tags enabled, etc.
   *
   * @param settings
   */
  public StackImpl(String name, Settings settings) {
    this.name = name;
    this.settings = settings;
    TAGS.putAll(DistributionImpl.distributions(name, settings));
  }

  @Override
  public List<Tag> tags() {
    return TAGS.keySet().stream().toList().reversed();
  }

  @Override
  public Distribution.Product product(Distribution.Product.Type productType, Tag chosenRelease) {
    var distribution = TAGS.get(chosenRelease);
    if (distribution != null) {
      return distribution.findProduct(productType, chosenRelease);
    }
    return null;
  }

  @Override
  public Status getStatus(Tag tag) {
    var distribution = TAGS.get(tag);
    if (distribution == null) {
      return Status.ABSENT;
    }
    AtomicReference<Status> ret = new AtomicReference<>(null);
    if (distribution.synchronize(
        settings.get(Setting.DISTRIBUTION_DIRECTORY, File.class),
        new Distribution.Synchronization() {

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
            ret.set(new Status(totalSize, downloadSize, fullList, downloadList));
            return false;
          }

          @Override
          public boolean download(URL url, File file, Distribution.FileData fileData) {
            return false;
          }

          @Override
          public boolean link(File file, File destination) {
            return false;
          }

          @Override
          public void delete(File file) {}

          @Override
          public void notifyProductSynchronized(Distribution.Product product) {}
        })) {
      return ret.get();
    }
    return Status.ABSENT;
  }

  @Override
  public boolean synchronize(Tag tag, Distribution.Synchronization sync) {
    return false;
  }

  @Override
  public LocalInstance instance(Distribution.Product.Type productType, Tag chosenRelease) {
    var product = product(productType, chosenRelease);
    if (product != null && product.getLocalPath() != null) {
      return switch (product.getPlatform()) {
        case JAR -> new JavaLocalInstance(product, settings, chosenRelease);
        // TODO exe
        default -> null;
      };
    }
    return null;
  }

  @Override
  public boolean verify(Tag distributionTag) {
    var tag = disambiguateTag(distributionTag);
    if (tag != null) {
      var distribution = TAGS.get(tag);
      if (distribution != null) {
        return distribution.verify(tag);
      }
    }
    return false;
  }

  /**
   * Return one of the physical tags after substituting for supported mnemonics like HEAD, "latest",
   * etc.
   *
   * @param tag
   * @return
   */
  private Tag disambiguateTag(Tag tag) {
    return tag;
  }

  public static void main(String[] args) {

    Klab.INSTANCE.setConfiguration(new CommonConfiguration());

    var klab = Stack.of("klab", SettingsImpl.forEngine());
    var tag = new AtomicReference<>(klab.tags().isEmpty() ? null : klab.tags().getFirst());

    System.out.println("Using tag " + tag.get());

    Utils.CLI
        .create()
        .with(
            "status",
            ar -> {
              //              ((DistributionImpl) distribution)
              //                  .synchronize(
              //                      Configuration.INSTANCE.getDataPath("distribution"),
              // loggingSynchronizer);
            })
        .with(
            "tags",
            ar -> {
              int n = 1;
              for (var t : klab.tags()) {
                System.out.println(n + ". " + t);
              }
            })
        .with(
            "instance",
            ar -> {
              var instance =
                  klab.instance(Distribution.Product.Type.valueOf(ar[0].toUpperCase()), tag.get());
              if (instance == null) {
                System.out.println("Product not found: " + ar[0]);
                return;
              }
              System.out.println(
                  "Product found: "
                      + instance.getProduct().getName()
                      + ": status = "
                      + instance.getStatus());
            })
        .with(
            "start",
            ar -> {
              var instance =
                  klab.instance(Distribution.Product.Type.valueOf(ar[0].toUpperCase()), tag.get());
              if (instance == null) {
                System.out.println("Product not found: " + ar[0]);
                return;
              }
              System.out.println(
                  "Starting "
                      + instance.getProduct().getName()
                      + ": status = "
                      + instance.getStatus());
              instance.start();
            })
        .with(
            "stop",
            ar -> {
              var instance =
                  klab.instance(Distribution.Product.Type.valueOf(ar[0].toUpperCase()), tag.get());
              if (instance == null) {
                System.out.println("Product not found: " + ar[0]);
                return;
              }
              System.out.println(
                  "Stopping "
                      + instance.getProduct().getName()
                      + ": status = "
                      + instance.getStatus());
              instance.stop();
            })
        .with(
            "tag",
            ar -> {
              int n = Integer.parseInt(ar[0]);
              tag.set(klab.tags().get(n - 1));
              System.out.println("Set tag to " + tag.get());
            })
        .with(
            "sync",
            ar -> {
              //              ((DistributionImpl) distribution)
              //                  .synchronize(
              //                      Configuration.INSTANCE.getDataPath("distribution"),
              // actingSynchronizer);
            })
        .run();
  }
}
