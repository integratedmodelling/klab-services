package org.integratedmodelling.klab.api.lang.kim;

import java.util.List;
import java.util.Map;

/** Strategy document header and ordered strategies; executable dataflows have a separate contract. */
public interface KimObservationStrategyDocument extends KlabDocument<KimObservationStrategy> {
  /** Portable schema generation, independent of getVersion() in the authored source. */
  int getModelVersion();
  KimObservationPlan.Source getSource();
  /** Explicit using declarations, in source order. */
  List<String> getImports();
  /** Unevaluated coverage specification. Values use ordinary k.LAB literal/semantic beans. */
  Map<String, Object> getCoverage();
}
