package org.integratedmodelling.common.distribution;

import org.integratedmodelling.common.configuration.CommonConfiguration;
import org.integratedmodelling.common.services.client.engine.SettingsImpl;
import org.integratedmodelling.common.utils.Utils;
import org.integratedmodelling.klab.api.Klab;
import org.integratedmodelling.klab.api.configuration.Setting;
import org.integratedmodelling.klab.api.configuration.Settings;
import org.integratedmodelling.klab.api.data.Version;
import org.integratedmodelling.klab.api.engine.distribution.Distribution;
import org.integratedmodelling.klab.api.engine.distribution.LocalInstance;
import org.integratedmodelling.klab.api.engine.distribution.Stack;
import org.integratedmodelling.klab.api.services.KlabService;

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
    // still needs to use a scan to establish update status for the locals distributions
  }

  private void refreshTags(DistributionImpl... distributions) {

    if (distributions != null) {
      for (var distribution : distributions) {
        var dFile =
            new File(
                settings.get(Setting.DISTRIBUTION_DIRECTORY, File.class), distribution.getName());
        if (dFile.exists() && dFile.isDirectory()) {
          var updated =
              new DistributionImpl(
                  distribution.getName(),
                  distribution.getVersion(),
                  Utils.URLs.newURL(dFile),
                  Utils.URLs.newURL(new File(dFile, distribution.getVersion().toString())));
          List<Tag> tagsToRemove = new ArrayList<>();

          // this pain is needed for now - comparison succeeds but tag isn't swapped
          for (var tag : TAGS.keySet()) {
            if (TAGS.get(tag) == distribution) {
              tagsToRemove.add(tag);
            }
          }
          tagsToRemove.forEach(TAGS::remove);
          for (var tag : updated.getTags()) {
            TAGS.put(tag, updated);
          }
        }
      }
    }
  }

  @Override
  public List<Tag> tags() {
    return TAGS.keySet().stream().toList().reversed();
  }

  @Override
  public Distribution.Product product(Distribution.Product.Type productType, Tag chosenRelease) {
    var tag = disambiguateTag(chosenRelease);
    var distribution = TAGS.get(tag);
    if (distribution != null) {
      return distribution.findProduct(productType, tag);
    }
    return null;
  }

  @Override
  public Distribution.Build build(Tag chosenRelease) {
    var tag = disambiguateTag(chosenRelease);
    var distribution = TAGS.get(tag);
    if (distribution != null) {
      return distribution.findBuild(tag);
    }
    return null;
  }

  @Override
  public Status status(Tag tag) {
    tag = disambiguateTag(tag);
    var distribution = TAGS.get(tag);
    if (distribution == null) {
      return Status.ABSENT;
    }
    AtomicReference<Status> ret = new AtomicReference<>(null);
    if (distribution.synchronize(
        settings.get(Setting.DISTRIBUTION_DIRECTORY, File.class),
        tag,
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
            return true;
          }

          @Override
          public boolean link(File file, File destination) {
            return true;
          }

          @Override
          public void delete(File file) {}

          @Override
          public boolean copy(File source, File destination) {
            return true;
          }

          @Override
          public void notifyProductSynchronizing(Distribution.Product product) {}

          @Override
          public void notifyProductSynchronized(Distribution.Product product) {}
        })) {
      return ret.get();
    }
    return Status.ABSENT;
  }

  @Override
  public boolean synchronize(Tag tag, Distribution.Synchronization sync) {

    if (tag.version() == Version.HEAD) {
      return true;
    }

    tag = disambiguateTag(tag);
    var distribution = TAGS.get(tag);
    if (distribution != null
        && distribution.synchronize(
            settings.get(Setting.DISTRIBUTION_DIRECTORY, File.class), tag, sync)) {
      refreshTags(distribution);
      return true;
    }
    return false;
  }

  @Override
  public LocalInstance instance(Distribution.Product.Type productType, Tag chosenRelease) {
    var tag = disambiguateTag(chosenRelease);
    var product = product(productType, tag);
    if (product != null && product.getLocalPath() != null) {
      return switch (product.getPlatform()) {
        case JAR -> new JavaLocalInstance(product, settings, tag);
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
    if (tag == Tag.LATEST_STABLE) {
      return tags().stream().filter(t -> t.release().equals("master")).findFirst().orElse(null);
    } else if (tag == Tag.LATEST_DEVELOP) {
      return tags().stream().findFirst().orElse(null);
    }
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
              var distributionTag = klab.tags().get(Integer.parseInt(ar[0]) - 1);
              klab.synchronize(distributionTag, DistributionImpl.loggingSynchronizer);
            })
        .with(
            "tags",
            ar -> {
              int n = 1;
              for (var t : klab.tags()) {
                System.out.println((n++) + ". " + t + " " + (t == tag.get() ? "(current)" : ""));
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
              System.out.println("Set current tag to " + tag.get());
            })
        .with(
            "sync",
            ar -> {
              var distributionTag = klab.tags().get(Integer.parseInt(ar[0]) - 1);
              klab.synchronize(distributionTag, DistributionImpl.actingSynchronizer);
            })
        .run();
  }
}
