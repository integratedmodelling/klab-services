package org.integratedmodelling.klab.resources;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Predicate;
import org.integratedmodelling.common.utils.Utils;
import org.integratedmodelling.klab.api.data.Version;
import org.integratedmodelling.klab.api.knowledge.Resource;
import org.integratedmodelling.klab.api.services.resources.impl.ResourceImpl;
import org.integratedmodelling.klab.api.services.runtime.extension.LearnedResourceSubmission;
import org.integratedmodelling.klab.api.services.runtime.extension.Learning;

/**
 * Serialized, idempotent commit for one resolver-owned store. Instantiate once per store, not per
 * request. The writer must store the whole resource/history in its persistence domain before
 * returning true. Both
 * transient and durable stores use detached snapshots. Payload transfer is deliberately rejected
 * until a resolver-owned payload staging protocol is available.
 */
public final class LearningResourceCommitter {
  private final Function<String, Resource> read;
  private final Predicate<Resource> write;

  public LearningResourceCommitter(Function<String, Resource> read, Predicate<Resource> write) {
    this.read = Objects.requireNonNull(read);
    this.write = Objects.requireNonNull(write);
  }

  /** Recover a receipt using saved ownership even if source or adapter availability has changed. */
  public synchronized Resource findCommitted(
      String serviceName, String contextId, String actor, LearnedResourceSubmission submission) {
    if (contextId == null || contextId.isBlank() || actor == null || actor.isBlank())
      throw new IllegalArgumentException("Learning recovery requires an authenticated context and actor");
    var previous = read.apply(Learning.resourceUrn(serviceName, submission.namespace(), submission.resourceId()));
    if (previous == null) return null;
    requireOwner(previous, actor, submission.modelUrn());
    var versions = new ArrayList<Resource>();
    versions.add(previous);
    versions.addAll(previous.getHistory());
    for (var version : versions)
      if (Objects.equals(submission.attemptId(), version.getMetadata().get(Learning.ATTEMPT))) {
        if (!Objects.equals(contextId, version.getMetadata().get(Learning.CONTEXT)))
          throw new IllegalArgumentException("Learning attempt belongs to another context");
        return copy(version);
      }
    return null;
  }

  public synchronized Resource commit(
      String serviceName, String serviceId, String contextId, String actor,
      LearnedResourceSubmission submission) {
    if (contextId == null || contextId.isBlank() || actor == null || actor.isBlank())
      throw new IllegalArgumentException("Learning commit requires an authenticated context and actor");
    String urn = Learning.resourceUrn(serviceName, submission.namespace(), submission.resourceId());
    var replay = findCommitted(serviceName, contextId, actor, submission);
    if (replay != null) return replay;
    Resource previous = read.apply(urn);

    var resource = copy(submission.resource());
    if (!Objects.equals(urn, resource.getUrn()))
      throw new IllegalArgumentException("Learning contextualizer changed the resource identity");
    if (!resource.getLocalFiles().isEmpty())
      throw new IllegalArgumentException("Learning payload files require resolver-owned staging; inline resources only");
    if (resource.getAdapterType() == null || resource.getAdapterType().isBlank()
        || resource.getType() == null
        || org.integratedmodelling.klab.api.utils.Utils.Notifications.hasErrors(resource.getNotifications()))
      throw new IllegalArgumentException("Invalid learned resource adapter, type or diagnostics");
    schema(resource.getInputs());
    schema(resource.getOutputs());
    schema(resource.getAttributes());
    // Initial version follows existing temporary resources; subsequent fits increment the build.
    var version = previous == null ? Version.create("0.0.1")
        : Version.create(previous.getVersion().toString());
    if (previous != null) version.setBuild(Math.addExact(version.getBuild(), 1));
    resource.setVersion(version);
    resource.setTimestamp(System.currentTimeMillis());
    resource.setServiceId(serviceId);
    resource.getMetadata().put(Learning.ATTEMPT, submission.attemptId());
    resource.getMetadata().put(Learning.MODEL, submission.modelUrn());
    resource.getMetadata().put(Learning.CONTEXT, contextId);
    resource.getMetadata().put(Learning.ACTOR, actor);
    var history = new ArrayList<Resource>();
    if (previous != null) {
      var old = copy(previous);
      old.setHistory(List.of());
      history.add(old);
      history.addAll(previous.getHistory());
    }
    resource.setHistory(history);
    // A writer cannot mutate the returned receipt, and caller mutations cannot mutate stored data.
    if (!write.test(copy(resource))) throw new IllegalStateException("Learning resource commit failed");
    return copy(resource);
  }

  private static void requireOwner(Resource resource, String actor, String model) {
    if (!Objects.equals(actor, resource.getMetadata().get(Learning.ACTOR))
        || !Objects.equals(model, resource.getMetadata().get(Learning.MODEL)))
      throw new IllegalArgumentException("Local resource belongs to another actor or model");
  }

  private static void schema(java.util.Collection<Resource.Attribute> attributes) {
    var names = new HashSet<String>();
    for (var attribute : attributes)
      if (attribute == null || attribute.getName() == null || attribute.getName().isBlank()
          || attribute.getType() == null || !names.add(attribute.getName()))
        throw new IllegalArgumentException("Invalid or duplicate learned resource schema name");
  }

  private static ResourceImpl copy(Resource resource) {
    return Utils.Json.parseObject(Utils.Json.asString(resource), ResourceImpl.class);
  }
}
