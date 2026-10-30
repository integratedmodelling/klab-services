package org.integratedmodelling.klab.api.services.runtime.objects;

import java.util.List;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;

/** Completed native quality results, never a synthetic container observation or a scalar reduction.
 * An exceptional resolution has no collection; an empty list is successful complete discovery.
 */
public interface MemberQualityCollection extends java.io.Serializable {
  MemberQualityRequest getRequest();
  Long getObserverId();
  String getObserverUrn();
  String getObserverClass();
  List<Entry> getEntries();
  /** Member and result retain their actual graph identities, native parent and individual support. */
  interface Entry extends java.io.Serializable {
    Observation getMember();
    Observation getQuality();
  }
}
