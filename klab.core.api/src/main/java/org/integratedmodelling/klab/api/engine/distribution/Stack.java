package org.integratedmodelling.klab.api.engine.distribution;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

import org.integratedmodelling.klab.api.Klab;
import org.integratedmodelling.klab.api.configuration.Settings;
import org.integratedmodelling.klab.api.data.Version;
import org.integratedmodelling.klab.api.exceptions.KlabIllegalStateException;

/**
 * Software stacks coordinate distributions for a specific software stack, corresponding to a
 * distribution name and a version number. May contain one or more builds and be or not be
 * synchronized to disk. This is lower-level to {@link Stack}, which should be used to resolve tags
 * and launch products.
 *
 * <p>TAG should go here
 */
public interface Stack {

  /**
   * The tag is the current choice of distribution, incorporating the overall version, the release
   * and the build. Initial status synchronization should return all tags that are available both
   * locally and remotely.
   *
   * @param version the overall version of the distribution. If Version.ANY_VERSION is passed, the
   *     latest available version is located.
   * @param release null means "official", normally master or main.
   * @param build apart from the physical build name, the "latest" build is also admitted.
   * @param availableLocally true if the distribution is available locally
   */
  record Tag(Version version, String release, String build, boolean availableLocally)
      implements Comparable<Tag> {
    public static Tag of(Version version, String release, String build, boolean availableLocally) {
      return new Tag(version, release, build, availableLocally);
    }

    public static Tag LATEST_STABLE = Tag.of(Version.ANY_VERSION, null, "latest", true);
    public static Tag LATEST_DEVELOP = Tag.of(Version.ANY_VERSION, "develop", "latest", true);

    @Override
    public int compareTo(Tag other) {
      return Comparator.comparing(Tag::version)
          .thenComparing(Tag::release)
          .thenComparing(Tag::build)
          .compare(this, other);
    }
  }

  /**
   * Status of a locally available tag w.r.t. the remote distribution.
   *
   * @param totalContentSize
   * @param downloadSize
   * @param fullContentList
   * @param downloadList
   */
  record Status(
      long totalContentSize,
      long downloadSize,
      Map<Distribution.FileData, Distribution.FileTarget> fullContentList,
      Map<Distribution.FileData, Distribution.FileTarget> downloadList) {

    public static final Status ABSENT = new Status(0, 0, Map.of(), Map.of());
  }

  /**
   * Get an instance of the passed product, which will refer to a specific locally available tag.
   * The instance may be already running or requiring startup. If already running, it may not
   * correspond to the tag requested, so the tag should be verified before using if that is
   * important.
   *
   * @param productType
   * @param chosenRelease
   * @return the instance, or null if the product is not available locally.
   */
  LocalInstance instance(Distribution.Product.Type productType, Tag chosenRelease);

  /**
   * Return true if the passed tag is available locally and verified w.r.t. the remote distribution.
   *
   * @param distributionTag
   * @return true if the tag is available locally and verified
   */
  boolean verify(Tag distributionTag);

  /**
   * Return all the known tags for this stack, latest first. Based on settings
   *
   * @return
   */
  List<Tag> tags();

  /**
   * Retrieve the required product for the specified tag.
   *
   * @param productType
   * @param chosenRelease
   * @return
   */
  Distribution.Product product(Distribution.Product.Type productType, Stack.Tag chosenRelease);

  /**
   * Retrieve the synchronization status of the passed tag. If the tag is not available locally or
   * does not exist, return {@link Status#ABSENT}.
   *
   * @param tag
   * @return
   */
  Status getStatus(Tag tag);

  /**
   * Synchronize the passed tag to disk w.r.t. the remote distribution. Use the passed
   * synchronization monitor/actuator to perform operations.
   *
   * @param tag
   * @param sync
   * @return
   */
  boolean synchronize(Tag tag, Distribution.Synchronization sync);

  /**
   * Retrieve the available stack with the passed name, or null. The stack's tags will reveal what
   * is available locally.
   *
   * @param name
   * @return
   */
  static Stack of(String name, Settings settings) {
    Klab.Configuration configuration = Klab.INSTANCE.getConfiguration();
    if (configuration == null) {
      throw new KlabIllegalStateException(
          "k.LAB environment not configured to promote a geometry to a scale");
    }
    return configuration.createSoftwareStack(name, settings);
  }
}
