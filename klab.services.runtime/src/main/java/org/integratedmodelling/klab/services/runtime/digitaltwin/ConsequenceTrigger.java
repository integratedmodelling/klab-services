package org.integratedmodelling.klab.services.runtime.digitaltwin;

import java.util.*;
import org.integratedmodelling.klab.api.data.KnowledgeGraph;
import org.integratedmodelling.klab.api.digitaltwin.Consequence;
import org.integratedmodelling.klab.api.digitaltwin.GraphModel;
import org.integratedmodelling.klab.api.knowledge.SemanticType;
import org.integratedmodelling.klab.services.scopes.ServiceContextScope;

/** Native trigger classification only: never a semantic or execution admission grant. */
record ConsequenceTrigger(boolean known, boolean quality, boolean newSubstantialsOnly) {
  private static final ConsequenceTrigger UNKNOWN = new ConsequenceTrigger(false, false, false);

  boolean relevant(Consequence.Kind kind) {
    if (!known) return true;
    return switch (kind) {
      case UNCERTAINTY -> quality;
      case CHANGE_PROCESS -> !newSubstantialsOnly;
      default -> true;
    };
  }

  static ConsequenceTrigger capture(KnowledgeGraph.Commit commit, long activityId,
      ServiceContextScope scope) {
    try {
      var ids = new TreeSet<>(ConfigurationModeledChanges.candidates(commit));
      ids.add(activityId);
      var graph = scope.getDigitalTwin().getKnowledgeGraph();
      var initial = graph.inspectAssetHeaders(ids, scope);
      if (initial == null) return UNKNOWN;
      var activity = initial.stream().filter(a -> a.id() == activityId
          && a.labels().contains(GraphModel.Labels.ACTIVITY)).findFirst().orElseThrow();
      var journal = ConfigurationModeledChanges.journalChanges(activity, commit, scope.getId());
      if (!ids.containsAll(journal)) {
        ids.addAll(journal);
        initial = graph.inspectAssetHeaders(ids, scope);
      }
      var found = initial.stream().map(KnowledgeGraph.ConsequenceAssetSnapshot::id).toList();
      if (found.size() != new HashSet<>(found).size() || !new HashSet<>(found).containsAll(ids)) return UNKNOWN;
      boolean quality = false;
      boolean unattributedStorage = false;
      boolean newSubstantialsOnly = true;
      boolean observed = false;
      for (var asset : initial) {
        if (asset.labels().contains(GraphModel.Labels.DATA)
            && commit.getModifiedAssets().contains(asset.id())) unattributedStorage = true;
        if (asset.labels().contains(GraphModel.Labels.COHORT)) return UNKNOWN;
        if (!asset.labels().contains(GraphModel.Labels.OBSERVATION)) continue;
        Object encoded = asset.nativeProperties().get(GraphModel.Fields.SEMANTICTYPE);
        if (!(encoded instanceof String text)) return UNKNOWN;
        var type = SemanticType.valueOf(text);
        if (type == SemanticType.NOTHING) return UNKNOWN;
        observed = true;
        quality |= type.isQuality();
        newSubstantialsOnly &= commit.getAddedObservations().contains(asset.id())
            && SemanticType.isSubstantial(Set.of(type))
            && type != SemanticType.PROCESS && type != SemanticType.EVENT;
      }
      if (unattributedStorage && !quality) return UNKNOWN;
      return new ConsequenceTrigger(true, quality, observed && newSubstantialsOnly);
    } catch (Exception unavailable) {
      // Old/missing headers, deleted inputs or unavailable native storage must not hide work.
      return UNKNOWN;
    }
  }
}
