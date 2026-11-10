package org.integratedmodelling.klab.api.services.runtime.extension;

import java.util.Objects;
import org.integratedmodelling.klab.api.knowledge.Resource;
import org.integratedmodelling.klab.api.provenance.Activity;
import org.integratedmodelling.klab.api.provenance.impl.ActivityImpl;

/** Construct graph-ready provenance from an authoritative commit receipt, never a staged builder. */
public final class LearningCommitActivity {
  private LearningCommitActivity() {}

  /**
   * The caller must authenticate this receipt and persist this Activity through a graph transaction.
   * No success outcome or graph ID is synthesized here. Initiating activity and evidence may be
   * supplied to the normal Activity/transaction factories when the record is persisted.
   */
  public static ActivityImpl of(Resource committed, Activity initiatingActivity) {
    Objects.requireNonNull(committed, "committed resource");
    if (committed.getUrn() == null || committed.getVersion() == null)
      throw new IllegalArgumentException("Resource commit has no exact version identity");
    var activity = Activity.of(Activity.Type.RESOURCE_COMMIT, "Commit learned resource");
    for (String key : new String[] {Learning.ATTEMPT, Learning.MODEL, Learning.CONTEXT, Learning.ACTOR}) {
      var value = committed.getMetadata().get(key);
      if (!(value instanceof String text) || text.isBlank())
        throw new IllegalArgumentException("Resource has no learning commit receipt: " + key);
      activity.getMetadata().put(key, value);
    }
    activity.getMetadata().put(Learning.RESOURCE,
        committed.getUrn() + "@" + committed.getVersion());
    if (initiatingActivity != null) {
      activity.setTriggeringActivityUrn(initiatingActivity.getUrn());
      activity.setParentTransientId(initiatingActivity.getTransientId());
    }
    return activity;
  }
}
