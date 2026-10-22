package org.integratedmodelling.common.runtime;

import java.util.*;
import org.integratedmodelling.klab.api.data.KnowledgeGraph;
import org.integratedmodelling.klab.api.data.Metadata;
import org.integratedmodelling.klab.api.digitaltwin.GraphModel;
import org.integratedmodelling.klab.api.knowledge.SemanticType;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.provenance.Activity;
import org.integratedmodelling.klab.api.scope.ContextScope;

/** Recovers actual observer observations from authored activity provenance, never from classes. */
public final class ObserverProvenance {
  private ObserverProvenance() {}

  public record Selection(boolean known, Observation observer) {
    public Observation requireKnown() {
      if (!known) throw invalid("Observer selection was not recorded");
      return observer;
    }
  }

  public static void recordSelection(Activity activity, Observation observer) {
    if (observer != null && (observer.getId() <= 0 || observer.getObservable() == null
        || !observer.getObservable().is(SemanticType.AGENT)))
      throw invalid("Selected observer is not a persisted Agent");
    activity.getMetadata().put(Metadata.IM_OBSERVER_SELECTION_ID, observer == null ? 0L : observer.getId());
  }

  public static Selection recover(ContextScope scope, Observation observation) {
    if (scope == null || scope.getDigitalTwin() == null || observation == null) return new Selection(false,null);
    if (observation.getId() <= 0) return new Selection(false,null);
    // Inherited effective selection is captured at creation. An unrecorded child cannot infer
    // whether its old submission intended the parent's observer or deliberately selected null.
    var creators = scope.getDigitalTwin().getKnowledgeGraph().query(Activity.class,scope)
        .target(observation).along(GraphModel.Relationship.CREATED).run(scope);
    var choices = new ArrayList<Selection>();
    for (var creator : creators) choices.add(activitySelection(scope,creator,new HashSet<>()));
    return agree(choices);
  }

  private static Selection activitySelection(ContextScope scope,Activity activity,Set<Long> path) {
    if (activity.getId() <= 0 || !path.add(activity.getId())) return new Selection(false,null);
    if (path.size() > 256) throw invalid("Observer provenance is too deep to recover safely");
    var graph = scope.getDigitalTwin().getKnowledgeGraph();
    try {
        var observers = graph.query(Observation.class,scope).source(activity)
            .along(GraphModel.Relationship.HAS_OBSERVER).run(scope);
        var ids = new LinkedHashMap<Long,Observation>();
        for (var observer : observers) {
          if (observer.getId() <= 0 || observer.getObservable() == null
              || !observer.getObservable().is(SemanticType.AGENT))
            throw invalid("Recorded observer is not an accessible Agent");
          ids.put(observer.getId(),observer);
        }
        var recorded = activity.getMetadata().get(Metadata.IM_OBSERVER_SELECTION_ID);
        if (recorded != null) {
          if (!(recorded instanceof Byte || recorded instanceof Short
              || recorded instanceof Integer || recorded instanceof Long)
              || ((Number)recorded).longValue() < 0)
            throw invalid("Invalid recorded observer selection");
          long expected = ((Number)recorded).longValue();
          if (expected == 0) {
            if (!ids.isEmpty()) throw invalid("Null observer selection contradicts its provenance");
            return new Selection(true,null);
          } else if (ids.size() != 1 || !ids.containsKey(expected)) {
            throw invalid("Recorded observer is missing or inaccessible");
          } else return new Selection(true,ids.values().iterator().next());
        }
        if (ids.size() > 1) throw invalid("Ambiguous recorded observer selection");
        if (!ids.isEmpty()) return new Selection(true,ids.values().iterator().next());
      // A legacy submission may have deliberately selected null: parent provenance cannot decide it.
        if (activity.getType() == Activity.Type.SUBMISSION) return new Selection(false,null);
        var choices = new ArrayList<Selection>();
        for (var parent : graph.query(Activity.class,scope).target(activity)
            .along(GraphModel.Relationship.TRIGGERED).run(scope))
          choices.add(activitySelection(scope,parent,path));
        return agree(choices);
    } finally {
      path.remove(activity.getId());
    }
  }

  /** Every creation/ancestry branch must identify the same actual selection. */
  private static Selection agree(List<Selection> choices) {
    Selection result = null;
    boolean unknown = choices.isEmpty();
    for (var choice : choices) {
      if (!choice.known()) {unknown=true;continue;}
      if (result != null && (result.observer()==null ? 0 : result.observer().getId())
          != (choice.observer()==null ? 0 : choice.observer().getId()))
        throw invalid("Ambiguous recorded observer selection");
      result=choice;
    }
    return unknown || result==null ? new Selection(false,null) : result;
  }

  private static IllegalStateException invalid(String message) {
    return new IllegalStateException(message + "; resubmit the observation with a recorded observer selection");
  }
}
