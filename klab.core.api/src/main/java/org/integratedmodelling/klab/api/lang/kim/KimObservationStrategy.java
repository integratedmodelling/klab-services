package org.integratedmodelling.klab.api.lang.kim;

import java.util.List;

/**
 * A portable, unevaluated observation strategy hosted by Resources and consumed by the Reasoner.
 * Selection alternatives are OR; each alternative's guards are AND. Setup and plan steps retain
 * declaration order. Source, namespace and project are retained when a strategy travels alone.
 * This replaces the legacy filters/macros/implicit operations model; no automatic lowering exists.
 */
public interface KimObservationStrategy extends KlabStatement {
  enum Type { OBSERVATION, IDENTIFICATION }
  String getDescription();
  int getRank();
  Type getType();
  /** Transport model generation, distinct from the authored document version. */
  int getModelVersion();
  KimObservationPlan.Source getSource();
  KimObservationPlan.StrategySelection getSelection();
  List<KimObservationPlan.StrategySetup> getSetup();
  KimObservationPlan.PlanBody getPlan();
}
