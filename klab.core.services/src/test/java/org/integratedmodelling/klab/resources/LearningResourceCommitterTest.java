package org.integratedmodelling.klab.resources;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.stream.IntStream;
import org.integratedmodelling.klab.api.knowledge.Artifact;
import org.integratedmodelling.klab.api.knowledge.Resource;
import org.integratedmodelling.klab.api.provenance.Activity;
import org.integratedmodelling.klab.api.services.runtime.extension.LearnedResourceSubmission;
import org.integratedmodelling.klab.api.services.runtime.extension.Learning;
import org.integratedmodelling.klab.api.services.runtime.extension.LearningCommitActivity;
import org.junit.jupiter.api.Test;

class LearningResourceCommitterTest {
  static final String URN = "local:resolver:training:fitted.runoff";
  final Map<String, Resource> store = new ConcurrentHashMap<>();
  final LearningResourceCommitter committer = new LearningResourceCommitter(store::get,
      resource -> { store.put(resource.getUrn(), resource); return true; });

  static Resource resource() {
    return Resource.builder(URN).withType(Artifact.Type.NUMBER).withAdapterType("test.inference")
        .withInput("rain", Artifact.Type.NUMBER, false, false)
        .withOutput("runoff", Artifact.Type.NUMBER).build();
  }
  static LearnedResourceSubmission submission(String attempt, Resource resource) {
    return new LearnedResourceSubmission(attempt, "training", "training.model", "fitted.runoff", resource);
  }
  Resource commit(String attempt) {
    return committer.commit("resolver", "service-id", "context", "actor", submission(attempt, resource()));
  }

  @Test void versionsAndOldAttemptReplaysAreDetachedAndStable() {
    var first = commit("first");
    var second = commit("second");
    assertEquals("0.0.1", first.getVersion().toString());
    assertEquals("0.0.2", second.getVersion().toString());
    assertEquals(1, second.getHistory().size());
    var replay = commit("first");
    assertEquals(first.getVersion(), replay.getVersion());
    replay.getMetadata().put("mutated", true);
    first.getMetadata().put("mutated", true);
    assertFalse(store.get(URN).getMetadata().containsKey("mutated"));
    assertFalse(commit("first").getMetadata().containsKey("mutated"));
    assertEquals("0.0.2", store.get(URN).getVersion().toString());
    var recovered = committer.findCommitted("resolver", "context", "actor", submission("first", resource()));
    assertEquals("0.0.1", recovered.getVersion().toString());
  }

  @Test void parallelCommitsAllocateUniqueVersionsAndFlattenHistory() throws Exception {
    try (var pool = Executors.newVirtualThreadPerTaskExecutor()) {
      var futures = IntStream.range(0, 16).mapToObj(i -> pool.submit(() -> commit("attempt-" + i))).toList();
      var versions = new java.util.HashSet<String>();
      for (var future : futures) assertTrue(versions.add(future.get().getVersion().toString()));
    }
    assertEquals(15, store.get(URN).getHistory().size());
    assertTrue(store.get(URN).getHistory().stream().allMatch(r -> r.getHistory().isEmpty()));
    assertEquals("0.0.16", store.get(URN).getVersion().toString());
  }

  @Test void failedWritesAndInvalidResourcesPreservePreviousVersion() {
    commit("first");
    var failed = new LearningResourceCommitter(store::get, r -> false);
    assertThrows(IllegalStateException.class, () -> failed.commit("resolver", "id", "context", "actor",
        submission("second", resource())));
    var invalid = Resource.builder("local:other:training:fitted.runoff").build();
    assertThrows(IllegalArgumentException.class, () -> committer.commit("resolver", "id", "context", "actor",
        submission("second", invalid)));
    assertEquals("0.0.1", store.get(URN).getVersion().toString());
  }

  @Test void ownershipAndAttemptContextCannotBeSpoofed() {
    commit("first");
    assertThrows(IllegalArgumentException.class, () -> committer.commit("resolver", "id", "context", "other",
        submission("second", resource())));
    assertThrows(IllegalArgumentException.class, () -> committer.commit("resolver", "id", "other-context", "actor",
        submission("first", resource())));
  }

  @Test void filePayloadsAndDuplicateSchemaNamesRequireValidationBeforeCommit() {
    var files = Resource.builder(URN).withType(Artifact.Type.NUMBER).withAdapterType("test")
        .addLocalResourcePath("not-a-transferred-payload.bin").build();
    assertThrows(IllegalArgumentException.class, () -> committer.commit("resolver", "id", "context", "actor",
        submission("files", files)));
    var duplicate = Resource.builder(URN).withType(Artifact.Type.NUMBER).withAdapterType("test")
        .withOutput("runoff", Artifact.Type.NUMBER).withOutput("runoff", Artifact.Type.NUMBER).build();
    assertThrows(IllegalArgumentException.class, () -> committer.commit("resolver", "id", "context", "actor",
        submission("duplicates", duplicate)));
    assertTrue(store.isEmpty());
  }

  @Test void resourceCommitActivityCarriesExactVersionWithoutInventingGraphSuccess() {
    var activity = LearningCommitActivity.of(commit("first"), null);
    assertEquals(Activity.Type.RESOURCE_COMMIT, activity.getType());
    assertFalse(activity.getType().isContextualization());
    assertEquals(URN + "@0.0.1", activity.getMetadata().get(Learning.RESOURCE));
    assertEquals("first", activity.getMetadata().get(Learning.ATTEMPT));
    assertNull(activity.getOutcome());
    assertTrue(activity.getId() <= 0);
    assertThrows(IllegalArgumentException.class, () -> LearningCommitActivity.of(resource(), null));
  }

  @Test void submissionSchemaAndCommitReceiptSurviveJsonTransport() {
    var submission = submission("first", resource());
    var encoded = org.integratedmodelling.common.utils.Utils.Json.asString(submission);
    var decoded = org.integratedmodelling.common.utils.Utils.Json.parseObject(encoded, LearnedResourceSubmission.class);
    assertEquals(1, decoded.resource().getInputs().size());
    assertEquals(1, decoded.resource().getOutputs().size());
    var committed = committer.commit("resolver", "id", "context", "actor", decoded);
    var receipt = org.integratedmodelling.common.utils.Utils.Json.parseObject(
        org.integratedmodelling.common.utils.Utils.Json.asString(committed), Resource.class);
    assertEquals("0.0.1", receipt.getVersion().toString());
    assertEquals(Artifact.Type.NUMBER, receipt.getInputs().iterator().next().getType());
  }
}
