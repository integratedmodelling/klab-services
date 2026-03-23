package org.integratedmodelling.klab.api.engine.distribution;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

import org.integratedmodelling.klab.api.Klab;
import org.integratedmodelling.klab.api.configuration.Settings;
import org.integratedmodelling.klab.api.data.Version;
import org.integratedmodelling.klab.api.exceptions.KlabIllegalStateException;

/**
 * Software stacks coordinate availability and upgrades of a specific software stack identified by a
 * stack name. The stack gives access to all its available versions, releases and builds in the form
 * of {@link Tag}s, that are presented to the user in descending order of currency.
 *
 * <p>If a compiled source stack is available and the settings allow it, the correspondent tag will
 * be added first, with version {@link Version#HEAD}. Each tag may or may not be available locally.
 * The {@link #synchronize(Tag, Distribution.Synchronization)} operation will synchronize the entire
 * distribution to which the tag belongs, making all its builds available locally. The tags will be
 * updated after it has succeeded. Similarly, the #status(Tag) operation will return the status of
 * the entire distribution that the tag belongs to, indicating the true number of bytes that the
 * synchronization operation needs to move to bring all the distribution's tags to the cache.
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
  record Tag(
      Version version, String release, String build, boolean availableLocally, boolean orphan)
      implements Comparable<Tag> {
    public static Tag of(
        Version version, String release, String build, boolean availableLocally, boolean orphan) {
      return new Tag(version, release, build, availableLocally, orphan);
    }

    public static Tag LATEST_STABLE = Tag.of(Version.ANY_VERSION, null, "stable", true, false);
    public static Tag LATEST_DEVELOP = Tag.of(Version.ANY_VERSION, null, "develop", true, false);

    @Override
    public int compareTo(Tag other) {
      // TODO this is a hack to be revised
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
   * Retrieve the synchronization status for the distribution that the passed tag belongs to. If the
   * tag is not available locally or does not exist, return {@link Status#ABSENT}.
   *
   * <p>If this returns an updatable status, the distribution should be synchronized. The updates in
   * a normal situation will refer to new builds available remotely. No file that is available
   * locally (either in the common area or in a previous build) will be counted as a necessary
   * download.
   *
   * @param tag
   * @return
   */
  Status status(Tag tag);

  /**
   * Synchronize the passed tag to disk w.r.t. the remote distribution. Use the passed
   * synchronization monitor/actuator to perform operations.
   *
   * <p>The entire distribution is synchronized, i.e. all new builds are added to the local file
   * cache. Any missing file in previous builds will be restored. If the build is no longer
   * available remotely, its tag will be tagged as orphan.
   *
   * <p>On a source code development stack, the synchronization will return true without any action.
   *
   * <p>NOTE the {@link #tags()} will be different after this call has returned true, unless the
   * synchronizer does nothing.
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
          "k.LAB environment not configured to provide a software stack");
    }
    return configuration.createSoftwareStack(name, settings);
  }
}
