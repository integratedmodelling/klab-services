package org.integratedmodelling.klab.runtime.libraries;

import java.util.*;
import org.integratedmodelling.klab.api.data.*;
import org.integratedmodelling.klab.api.digitaltwin.DigitalTwin;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.knowledge.SemanticType;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.provenance.Activity;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.services.runtime.*;
import org.jgrapht.Graph;

/** Read-only snapshot checks. Unknown evidence must never become a successful assertion. */
final class InspectorSupport {
  private InspectorSupport() {}

  static List<String> problems(Object[] arguments) {
    var options = Metadata.create();
    var assets = new ArrayList<Object>();
    if (arguments != null) {
      for (Object argument : arguments) {
        if (argument instanceof Metadata metadata) options.putAll(metadata);
        else assets.add(argument);
      }
    }
    for (String key : options.keySet()) {
      if (!Set.of("nodata", "data", "resolved", "mincoverage").contains(key)) {
        throw new IllegalArgumentException("Unknown inspector option: " + key);
      }
    }
    for (String key : List.of("nodata", "data", "resolved")) {
      if (options.containsKey(key) && !(options.get(key) instanceof Boolean)) {
        throw new IllegalArgumentException(key + " must be boolean");
      }
    }
    if (options.containsKey("mincoverage")) threshold(options.get("mincoverage"));
    var ret = new ArrayList<String>();
    if (assets.isEmpty()) ret.add("No assets supplied");
    for (int i = 0; i < assets.size(); i++) {
      check(assets.get(i), options, "asset[" + i + "]", ret,
          Collections.newSetFromMap(new IdentityHashMap<>()));
    }
    return List.copyOf(ret);
  }

  private static void check(Object asset, Metadata options, String path, List<String> errors,
      Set<Object> visiting) {
    if (asset == null) { errors.add(path + ": null asset"); return; }
    if (!visiting.add(asset)) { errors.add(path + ": cyclic asset structure"); return; }
    try {
      if (asset instanceof Observation observation) {
        if (observation.isEmpty()) errors.add(path + ": empty observation");
        if (!geometry(observation.getGeometry())) errors.add(path + ": missing or empty geometry");
        if (observation.getObservable() == null) errors.add(path + ": missing observable");
        else {
          boolean dependent = observation.getObservable().is(SemanticType.QUALITY)
              || observation.getObservable().is(SemanticType.PROCESS)
              || observation.getObservable().is(SemanticType.RELATIONSHIP);
          // Historical !resolved explicitly requires resolution even for substantials.
          boolean required = dependent || Boolean.FALSE.equals(options.get("resolved"))
              || options.containsKey("mincoverage");
          if (required && !resolved(observation, options.containsKey("mincoverage")
              ? threshold(options.get("mincoverage")) : 0)) {
            errors.add(path + ": insufficient resolved coverage: " + observation.getResolvedCoverage());
          }
          if (observation.getObservable().is(SemanticType.QUALITY)
              && !dataPolicy(observation, options)) {
            errors.add(path + ": data absent, unreliable, or violates nodata policy");
          }
        }
        if (!noErrors(observation.getNotifications())) errors.add(path + ": error notifications");
      } else if (asset instanceof Activity activity) {
        if (activity.getOutcome() != Activity.Outcome.SUCCESS) errors.add(path + ": activity not successful");
      } else if (asset instanceof Dataflow dataflow) {
        if (dataflow.isEmpty() || dataflow.getComputation() == null || dataflow.getComputation().isEmpty())
          errors.add(path + ": empty dataflow");
        if (!noErrors(dataflow.getNotifications())) errors.add(path + ": error notifications");
        if (dataflow.getComputation() != null) for (var actuator : dataflow.getComputation())
          check(actuator, options, path + ".actuator", errors, visiting);
      } else if (asset instanceof Actuator actuator) {
        if (actuator.getActuatorType() == null) errors.add(path + ": missing actuator type");
        if (actuator.getObservation() == null) errors.add(path + ": missing actuator observation");
        if (actuator.getChildren() != null) for (var child : actuator.getChildren())
          check(child, options, path + ".child", errors, visiting);
      } else if (asset instanceof ContextScope context) {
        check(context.getDigitalTwin(), options, path + ".twin", errors, visiting);
      } else if (asset instanceof DigitalTwin twin) {
        check(twin.getKnowledgeGraph(), options, path + ".graph", errors, visiting);
      } else if (asset instanceof KnowledgeGraph graph) {
        if (!graph.isOnline()) errors.add(path + ": knowledge graph offline");
      } else if (asset instanceof Storage || asset instanceof Storage.Shard || asset instanceof Histogram) {
        if ((options.containsKey("nodata") || options.containsKey("data"))
            ? !dataPolicy(asset, options) : !hasData(asset, false))
          errors.add(path + ": data absent, unreliable, or violates nodata policy");
      } else if (asset instanceof Geometry geometry) {
        if (!geometry(geometry)) errors.add(path + ": empty geometry");
      } else if (asset instanceof Graph<?, ?> graph) {
        if (graph.vertexSet().isEmpty()) errors.add(path + ": empty graph");
      } else if (asset instanceof Collection<?> collection) {
        if (collection.isEmpty()) errors.add(path + ": empty collection");
        int i = 0;
        for (Object item : collection) check(item, options, path + "[" + i++ + "]", errors, visiting);
      } else {
        errors.add(path + ": unsupported asset type " + asset.getClass().getName());
      }
    } finally { visiting.remove(asset); }
  }

  static boolean geometry(Geometry geometry) {
    return geometry != null && !geometry.isEmpty() && geometry.size() > 0;
  }

  static double threshold(Object value) {
    if (!(value instanceof Number number) || !Double.isFinite(number.doubleValue())
        || number.doubleValue() < 0 || number.doubleValue() > 1)
      throw new IllegalArgumentException("mincoverage must be a finite number between 0 and 1");
    return number.doubleValue();
  }

  static boolean resolved(Observation observation, double minimum) {
    threshold(minimum);
    if (observation == null || observation.isEmpty()) return false;
    double coverage = observation.getResolvedCoverage();
    return Double.isFinite(coverage) && coverage > 0 && coverage <= 1 && coverage >= minimum;
  }

  static boolean noErrors(List<Notification> notifications) {
    return notifications == null || notifications.stream().noneMatch(n -> n != null
        && n.getLevel() != null && n.getLevel().severity >= Notification.Level.Error.severity);
  }

  static List<Histogram> histograms(Object asset) {
    if (asset instanceof Histogram histogram) return List.of(histogram);
    if (asset instanceof Storage.Shard shard)
      return shard.getHistogram() == null ? List.of() : List.of(shard.getHistogram());
    if (asset instanceof Storage storage)
      return storage.getHistogram() == null ? List.of() : List.of(storage.getHistogram());
    if (asset instanceof Observation observation && observation.getHistograms() != null)
      return new ArrayList<>(observation.getHistograms().values());
    return List.of();
  }

  static boolean hasData(Object asset, boolean complete) {
    if (asset instanceof Observation observation && observation.getGeometry() != null
        && observation.getGeometry().isScalar() && observation.getValue() != null) {
      Object value = observation.getValue();
      return !(value instanceof Number number) || !Double.isNaN(number.doubleValue());
    }
    var histograms = histograms(asset);
    if (histograms.isEmpty()) return false;
    boolean valid = false;
    for (Histogram histogram : histograms) {
      boolean available = validCount(histogram) > 0;
      if (complete && (!available || histogram.getMissingCount() != 0)) return false;
      valid |= available;
    }
    return valid;
  }

  private static double validCount(Histogram histogram) {
    if (histogram == null || histogram.isEmpty() || histogram.getBins() == null) return 0;
    return histogram.getBins().stream().filter(Objects::nonNull).mapToDouble(Histogram.Bin::getCount).sum();
  }

  static boolean allNoData(Object asset) {
    if (asset instanceof Observation observation && observation.getGeometry() != null
        && observation.getGeometry().isScalar() && observation.getValue() instanceof Number number)
      return Double.isNaN(number.doubleValue());
    var histograms = histograms(asset);
    return !histograms.isEmpty() && histograms.stream().allMatch(h -> h != null
        && Double.isFinite(h.getMissingCount()) && h.getMissingCount() > 0
        && (h.getBins() == null || h.getBins().isEmpty()));
  }

  private static boolean dataPolicy(Object asset, Metadata options) {
    if (options.containsKey("nodata") && !(Boolean.TRUE.equals(options.get("nodata"))
        ? allNoData(asset) : hasData(asset, true))) return false;
    if (options.containsKey("data") && !(Boolean.TRUE.equals(options.get("data"))
        ? hasData(asset, false) : allNoData(asset))) return false;
    return true;
  }

  static boolean inRange(Object asset, double minimum, double maximum) {
    if (!Double.isFinite(minimum) || !Double.isFinite(maximum) || minimum > maximum)
      throw new IllegalArgumentException("Range bounds must be finite and ordered");
    if (asset instanceof Observation observation && observation.getGeometry() != null
        && observation.getGeometry().isScalar() && observation.getValue() instanceof Number number) {
      double value = number.doubleValue();
      return Double.isFinite(value) && value >= minimum && value <= maximum;
    }
    var histograms = histograms(asset);
    return !histograms.isEmpty() && histograms.stream().allMatch(h -> h != null && !h.isEmpty()
        && Double.isFinite(h.getMin()) && Double.isFinite(h.getMax())
        && h.getMin() <= h.getMax() && h.getMin() >= minimum && h.getMax() <= maximum);
  }
}
