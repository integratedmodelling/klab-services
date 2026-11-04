package org.integratedmodelling.klab.services.runtime.neo4j;

import java.util.*;
import org.integratedmodelling.klab.api.data.KnowledgeGraph.*;

/** Value-free local mismatch facts. No semantic or publication authority. */
final class ConsequenceReadSetDiagnostics {
  private ConsequenceReadSetDiagnostics() {}

  static List<String> differences(ConsequenceReadSet before, ConsequenceReadSet after) {
    var result = new ArrayList<String>();
    if (!Objects.equals(before.contextId(), after.contextId())) result.add("context");
    if (!before.selections().equals(after.selections())) result.add("selections");
    var assets = new TreeMap<Long, ConsequenceAssetSnapshot>();
    after.assets().forEach(a -> assets.put(a.id(), a));
    for (var a : before.assets()) {
      var b = assets.remove(a.id());
      String prefix = "asset:" + a.id() + ":";
      if (b == null) {
        result.add(prefix + "removed");
        continue;
      }
      if (!a.elementId().equals(b.elementId())) result.add(prefix + "identity");
      if (!a.labels().equals(b.labels())) result.add(prefix + "labels");
      if (!Objects.equals(a.geometry(), b.geometry())) result.add(prefix + "geometry");
      if (!Objects.equals(a.perceivedGeometry(), b.perceivedGeometry()))
        result.add(prefix + "perceivedGeometry");
      keys(result, prefix, a.properties(), b.properties());
    }
    assets.keySet().forEach(id -> result.add("asset:" + id + ":added"));
    var edges = new LinkedHashMap<String, ConsequenceEdgeSnapshot>();
    after.edges().forEach(e -> edges.put(e.elementId(), e));
    for (var a : before.edges()) {
      var b = edges.remove(a.elementId());
      String prefix = "edge:" + a.relationship().name() + ":" + safe(a.elementId()) + ":";
      if (b == null) {
        result.add(prefix + "removed");
        continue;
      }
      if (!a.source().equals(b.source()) || !a.target().equals(b.target()))
        result.add(prefix + "endpoints");
      if (a.relationship() != b.relationship()) result.add(prefix + "relationship");
      keys(result, prefix, a.properties(), b.properties());
    }
    edges
        .values()
        .forEach(
            e ->
                result.add(
                    "edge:" + e.relationship().name() + ":" + safe(e.elementId()) + ":added"));
    if (result.isEmpty() && !before.equals(after)) result.add("ordering");
    return List.copyOf(result.subList(0, Math.min(256, result.size())));
  }

  private static void keys(
      List<String> out, String prefix, Map<String, Object> a, Map<String, Object> b) {
    var keys = new TreeSet<>(a.keySet());
    keys.addAll(b.keySet());
    for (var key : keys)
      if (a.containsKey(key) != b.containsKey(key) || !Objects.equals(a.get(key), b.get(key)))
        out.add(prefix + "property:" + safe(key));
  }

  private static String safe(String value) {
    return value != null && value.length() <= 120 && value.matches("[A-Za-z0-9_.:-]+")
        ? value
        : "[redacted-key]";
  }
}
