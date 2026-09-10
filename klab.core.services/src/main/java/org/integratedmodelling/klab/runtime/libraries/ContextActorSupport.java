package org.integratedmodelling.klab.runtime.libraries;

import java.util.*;
import org.integratedmodelling.klab.api.collections.Constant;
import org.integratedmodelling.klab.api.data.*;
import org.integratedmodelling.klab.api.digitaltwin.GraphModel;
import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.api.knowledge.Urn;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.lang.kim.KimConcept;
import org.integratedmodelling.klab.api.lang.kim.KimObservable;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.services.Reasoner;
import org.integratedmodelling.klab.runtime.kactors.JavaArgumentConversions;

/** Strict argument handling shared by the Context actor's read and submission paths. */
final class ContextActorSupport {
  private ContextActorSupport() {}

  static Metadata metadata(Object... arguments) {
    var ret = Metadata.create();
    if (arguments != null) for (Object argument : arguments)
      if (argument instanceof Metadata metadata) ret.putAll(metadata);
    return ret;
  }

  static ContextScope focus(ContextScope context, Metadata options) {
    boolean within = options.containsKey("within");
    boolean source = options.containsKey("source");
    boolean target = options.containsKey("target");
    if (within && (source || target))
      throw new IllegalArgumentException("within cannot be combined with source/target");
    if (within) return context.within(observation(options.get("within"), "within"));
    if (source != target) throw new IllegalArgumentException("Focus requires both source and target");
    return source ? context.between(observation(options.get("source"), "source"),
        observation(options.get("target"), "target")) : context;
  }

  private static Observation observation(Object value, String option) {
    if (value instanceof Observation observation) return observation;
    throw new IllegalArgumentException(option + " requires an Observation");
  }

  static Object query(ContextScope base, Object... arguments) {
    var options = metadata(arguments);
    Set<String> allowed = Set.of("type", "id", "urn", "semantics", "within", "source", "target",
        "along", "all", "limit", "offset", "depth");
    for (String key : options.keySet()) if (!allowed.contains(key))
      throw new IllegalArgumentException("Unknown context query option: " + key);
    if (arguments != null) for (Object argument : arguments) {
      if (argument instanceof Metadata) continue;
      String key;
      if (argument instanceof Constant constant) {
        if ("ANY".equalsIgnoreCase(constant.getValue())) key = "type";
        else {
          try { JavaArgumentConversions.enumValue(constant, RuntimeAsset.Type.class); key = "type"; }
          catch (IllegalArgumentException notAssetType) {
            JavaArgumentConversions.enumValue(constant, GraphModel.Relationship.class);
            key = "along";
          }
        }
      }
      else if (argument instanceof RuntimeAsset.Type || argument instanceof Class<?>) key = "type";
      else if (argument instanceof GraphModel.Relationship) key = "along";
      else if (argument instanceof Number) key = "id";
      else if (argument instanceof String || argument instanceof Urn) key = "urn";
      else if (argument instanceof Observable || argument instanceof KimObservable || argument instanceof KimConcept) key = "semantics";
      else throw new IllegalArgumentException("Unsupported query selector: " + argument);
      if (options.containsKey(key)) throw new IllegalArgumentException("Duplicate query selector: " + key);
      options.put(key, argument);
    }
    if (options.containsKey("id") && options.containsKey("urn"))
      throw new IllegalArgumentException("Choose id or urn, not both");
    boolean all = flag(options, "all", false);
    long limit = integer(options, "limit", all ? -1 : 1, -1, Long.MAX_VALUE);
    long offset = integer(options, "offset", 0, 0, Long.MAX_VALUE);
    int depth = (int) integer(options, "depth", 1, 1, 64);
    Object source = options.get("source");
    Object target = options.get("target");
    if (!options.containsKey("source") && !options.containsKey("target") && !options.containsKey("within")) {
      source = base.getSourceObservation();
      target = base.getTargetObservation();
    }
    if (options.containsKey("source") && !(source instanceof RuntimeAsset)
        || options.containsKey("target") && !(target instanceof RuntimeAsset))
      throw new IllegalArgumentException("source and target require RuntimeAssets");
    if (options.containsKey("within") && (source != null || target != null))
      throw new IllegalArgumentException("within cannot be combined with source/target");
    ContextScope selected = options.containsKey("within") ? focus(base, options) : base;
    if (source instanceof Observation from && target instanceof Observation to) selected = base.between(from, to);
    boolean endpoints = source != null && target != null;
    Class<? extends RuntimeAsset> type = options.containsKey("type") ? assetClass(options.get("type"))
        : endpoints ? KnowledgeGraph.Link.class : Observation.class;
    if (options.containsKey("semantics") && type != Observation.class)
      throw new IllegalArgumentException("Semantic selection requires OBSERVATION results");
    if (endpoints && type != KnowledgeGraph.Link.class)
      throw new IllegalArgumentException("Paired endpoints currently select LINK assets; semantic relationship observations require a capture contract");
    if (endpoints && depth != 1) throw new IllegalArgumentException("Endpoint links require depth 1");
    var relationship = options.containsKey("along")
        ? (GraphModel.Relationship) JavaArgumentConversions.enumValue(options.get("along"), GraphModel.Relationship.class)
        : GraphModel.Relationship.HAS_CHILD;
    var graph = Objects.requireNonNull(selected.getDigitalTwin(), "Context has no digital twin").getKnowledgeGraph();
    var query = graph.query(type, selected);
    if (endpoints) query.between(source, target, relationship);
    else if (source != null) query.source(source).along(relationship).depth(depth);
    else if (target != null) query.target(target).along(relationship).depth(depth);
    else if (selected.getContextObservation() != null) query.source(selected.getContextObservation()).along(relationship).depth(depth);
    else if (options.containsKey("along") || options.containsKey("depth"))
      throw new IllegalArgumentException("along/depth require an anchor or a focused context");
    if (options.containsKey("id")) {
      long id = integer(options, "id", 0, 1, Long.MAX_VALUE);
      // Query.id() deliberately ignores other conditions; a predicate preserves focus and type.
      query.where(GraphModel.Fields.ID, KnowledgeGraph.Query.Operator.EQUALS, id);
    }
    if (options.containsKey("urn")) {
      Object urn = options.get("urn");
      String value = urn instanceof Urn u ? u.getUrn() : urn instanceof String s ? s : null;
      if (value == null || value.isBlank()) throw new IllegalArgumentException("urn must be a nonblank URN or string");
      query.where(GraphModel.Fields.URN, KnowledgeGraph.Query.Operator.EQUALS, value);
    }
    if (options.containsKey("semantics")) {
      Object semantics = options.get("semantics");
      Observable observable;
      if (semantics instanceof Observable o) observable = o;
      else {
        String definition = semantics instanceof KimObservable o ? o.getUrn()
            : semantics instanceof KimConcept c ? c.getUrn() : null;
        if (definition == null) throw new IllegalArgumentException("semantics requires an observable or semantic literal");
        observable = selected.getService(Reasoner.class).resolveObservable(definition);
      }
      if (observable == null || observable.getUrn() == null)
        throw new IllegalArgumentException("Cannot resolve query semantics");
      query.where(GraphModel.Fields.OBSERVABLE, KnowledgeGraph.Query.Operator.EQUALS, observable.getUrn());
    }
    query.order(GraphModel.Fields.ID).offset(offset).limit(all ? limit : limit == 0 ? 0 : 1);
    var result = query.run(selected);
    return all ? List.copyOf(result) : result.isEmpty() ? null : result.getFirst();
  }

  @SuppressWarnings("unchecked")
  private static Class<? extends RuntimeAsset> assetClass(Object value) {
    if (value instanceof Constant constant && "ANY".equalsIgnoreCase(constant.getValue())
        || value instanceof String text && "ANY".equalsIgnoreCase(text)) return RuntimeAsset.class;
    if (value instanceof Class<?> cls && RuntimeAsset.class.isAssignableFrom(cls))
      return (Class<? extends RuntimeAsset>) cls;
    return ((RuntimeAsset.Type) JavaArgumentConversions.enumValue(value, RuntimeAsset.Type.class)).assetClass;
  }

  private static boolean flag(Metadata options, String key, boolean fallback) {
    if (!options.containsKey(key)) return fallback;
    if (options.get(key) instanceof Boolean b) return b;
    throw new IllegalArgumentException(key + " must be boolean");
  }

  private static long integer(Metadata options, String key, long fallback, long minimum, long maximum) {
    if (!options.containsKey(key)) return fallback;
    Object value = options.get(key);
    if (!(value instanceof Number number)) throw new IllegalArgumentException(key + " must be an integer");
    long result;
    try { result = new java.math.BigDecimal(number.toString()).longValueExact(); }
    catch (NumberFormatException | ArithmeticException error) { throw new IllegalArgumentException(key + " must be an integer", error); }
    if (result < minimum || result > maximum) throw new IllegalArgumentException(key + " out of range");
    return result;
  }
}
