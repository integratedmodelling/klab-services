package org.integratedmodelling.klab.runtime.language;

import java.util.HashSet;
import java.util.LinkedHashMap;
import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.utilities.Utils;

/** Exact selected dependency contract, independently of spelling and model-local aliases. */
public final class DependencyContract {
  private DependencyContract() {}

  public static boolean matches(Observable expected, Observable selected) {
    if (expected == null || selected == null || expected.getSemantics() == null
        || selected.getSemantics() == null
        || !expected.getSemantics().equals(selected.getSemantics())
        || !java.util.Objects.equals(expected.getObserverSemantics(), selected.getObserverSemantics())
        || !new HashSet<>(expected.getGenericComponents()).equals(new HashSet<>(selected.getGenericComponents()))
        || !expected.getSpecializedComponents().equals(selected.getSpecializedComponents())
        || !new HashSet<>(expected.getResolutionDirectives()).equals(new HashSet<>(selected.getResolutionDirectives()))) return false;
    return requirements(expected).equals(requirements(selected));
  }

  private static com.fasterxml.jackson.databind.JsonNode requirements(Observable observable) {
    var fields = new LinkedHashMap<String, Object>();
    fields.put("unit", observable.getUnit());
    fields.put("currency", observable.getCurrency());
    fields.put("range", observable.getRange());
    fields.put("contextualization", observable.getContextualization());
    fields.put("optional", observable.isOptional());
    fields.put("generic", observable.isGeneric());
    fields.put("defaultValue", observable.getDefaultValue());
    fields.put("value", observable.getValue());
    fields.put("annotations", observable.getAnnotations());
    // Do not compare Observable URNs, names or reference names. Concepts carry meaning;
    // these fields carry the separate requirements of observation production.
    return Utils.Json.parseObject(Utils.Json.asString(fields), com.fasterxml.jackson.databind.JsonNode.class);
  }
}
