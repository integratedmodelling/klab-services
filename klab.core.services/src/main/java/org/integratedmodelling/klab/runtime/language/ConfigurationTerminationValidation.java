package org.integratedmodelling.klab.runtime.language;

import java.util.*;
import org.integratedmodelling.klab.api.knowledge.SemanticType;
import org.integratedmodelling.klab.api.lang.Contextualizable;
import org.integratedmodelling.klab.api.lang.kim.*;

/** Authored phase provenance used for validation; it never selects a Runtime execution trigger. */
public final class ConfigurationTerminationValidation {
  private ConfigurationTerminationValidation() {}
  public static final String AUTHORED_PHASES="im:authored-contextualization-phases";
  public static final String UNSUPPORTED="CONFIGURATION_TERMINATION_UNSUPPORTED: configuration termination models are not specified; configurations persist even when empty";

  public static void authoredPhases(Contextualizable target,Collection<String> phases) {
    if(phases!=null&&!phases.isEmpty())target.getMetadata().put(AUTHORED_PHASES,List.copyOf(phases));
  }
  public static boolean termination(Contextualizable contextualizable) {
    if(contextualizable.getTrigger()==Contextualizable.Trigger.TERMINATION)return true;
    var value=contextualizable.getMetadata().get(AUTHORED_PHASES);
    return value instanceof List<?> phases&&phases.contains("TERMINATION");
  }
  public static boolean configuration(KimObservable observable) {
    return observable!=null&&Heads.configuration(observable.getSemantics());
  }
  private static final class Heads extends ObservableValidator {
    static boolean configuration(KimConcept concept) {
      return effectiveHeadTypes(concept,Collections.newSetFromMap(new IdentityHashMap<>())).contains(SemanticType.CONFIGURATION);
    }
  }
}
