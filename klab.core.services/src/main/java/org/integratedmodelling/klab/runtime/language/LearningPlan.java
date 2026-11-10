package org.integratedmodelling.klab.runtime.language;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import org.integratedmodelling.klab.api.knowledge.Concept;
import org.integratedmodelling.klab.api.knowledge.Model;
import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.api.knowledge.SemanticType;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.lang.Annotation;
import org.integratedmodelling.klab.api.lang.kim.KimConcept;
import org.integratedmodelling.klab.api.services.runtime.extension.Learning;
import org.integratedmodelling.klab.api.services.runtime.extension.LearningContextualizer;

/**
 * Deterministic acquisition of a learning invocation's ports. Automatic acquisition is delegated
 * to the caller's ordinary resolver in its authenticated context. This class does not run model
 * actions: preparedTargets must contain their completed results, before binding starts.
 */
public record LearningPlan(
    List<LearningContextualizer.Input> predictors,
    List<LearningContextualizer.Input> targets,
    List<LearningContextualizer.Input> dependencies) {

  public LearningPlan {
    predictors = List.copyOf(predictors);
    targets = List.copyOf(targets);
    dependencies = List.copyOf(dependencies);
  }

  public static LearningPlan bind(
      Model model,
      Map<String, Observation> explicit,
      Map<String, Observation> preparedTargets,
      Function<Observable, List<Observation>> automatic,
      boolean requiresTargets) {
    if (!model.isLearningModel()) throw new IllegalArgumentException("Not a learning model");
    Objects.requireNonNull(automatic, "automatic acquisition");
    var names = new HashSet<String>();
    for (var observable : model.getDependencies()) unique(names, Learning.name(observable));
    for (var observable : model.getObservables()) unique(names, Learning.name(observable));
    if (model.getObservables().isEmpty())
      throw new IllegalArgumentException("A learning model requires declared outputs");
    if (!names.containsAll(explicit.keySet()))
      throw new IllegalArgumentException("Unknown explicit learning input names");
    var outputNames = model.getObservables().stream().map(Learning::name).toList();
    if (!outputNames.containsAll(preparedTargets.keySet()))
      throw new IllegalArgumentException("Prepared target is not a declared output");

    var predictors = new ArrayList<LearningContextualizer.Input>();
    var dependencies = new ArrayList<LearningContextualizer.Input>();
    var targets = new ArrayList<LearningContextualizer.Input>();
    for (var dependency : model.getDependencies()) {
      var predictor = annotation(dependency.getAnnotations(), LearningContextualizer.PREDICTOR_ANNOTATION);
      var input = acquire(dependency, explicit, Map.of(), automatic,
          !dependency.isOptional(), predictor == null ? Map.of() : predictor);
      if (input != null) {
        dependencies.add(input);
        if (predictor != null) predictors.add(input);
      }
    }
    if (requiresTargets)
      for (var output : model.getObservables())
        targets.add(acquire(output, explicit, preparedTargets, automatic, true, Map.of()));
    return new LearningPlan(predictors, targets, dependencies);
  }

  /** Validate the configured semantic perspective; actual Agent creation belongs to the runtime. */
  public static Concept observer(
      Model model, Concept worldviewDefault, Function<String, Concept> resolve) {
    var annotation = annotation(model.getAnnotations(), Learning.ANNOTATION);
    Concept observer = worldviewDefault;
    if (annotation != null && annotation.containsKey(Learning.OBSERVER)) {
      var value = annotation.get(Learning.OBSERVER);
      observer = switch (value) {
        case Concept concept -> resolve.apply(concept.getUrn());
        case KimConcept concept -> resolve.apply(concept.getUrn());
        case String urn -> resolve.apply(urn);
        case null, default -> throw new IllegalArgumentException("Invalid learning observer argument");
      };
    }
    // Defaults also go through the current semantic owner rather than trusting transported flags.
    else if (observer != null) observer = resolve.apply(observer.getUrn());
    if (observer == null || !observer.is(SemanticType.AGENT))
      throw new IllegalArgumentException("Training requires a configured learning-observer Agent class");
    return observer;
  }

  private static void unique(HashSet<String> names, String name) {
    if (!names.add(name)) throw new IllegalArgumentException("Duplicate learning input name: " + name);
  }

  private static Annotation annotation(List<Annotation> annotations, String name) {
    var matches = annotations.stream().filter(a -> name.equals(a.getName())).toList();
    if (matches.size() > 1) throw new IllegalArgumentException("Repeated @" + name + " annotation");
    return matches.isEmpty() ? null : matches.getFirst();
  }

  private static LearningContextualizer.Input acquire(
      Observable declared, Map<String, Observation> explicit,
      Map<String, Observation> prepared, Function<Observable, List<Observation>> automatic,
      boolean required, Map<String, Object> parameters) {
    String name = Learning.name(declared);
    Observation observation;
    if (explicit.containsKey(name)) observation = explicit.get(name);
    else if (prepared.containsKey(name)) {
      observation = prepared.get(name);
      if (Learning.isPreview(observation))
        throw new IllegalArgumentException("Prepared target is a learning preview: " + name);
    } else {
      var candidates = Objects.requireNonNull(automatic.apply(declared), "acquisition result").stream()
          .filter(Objects::nonNull).filter(o -> !o.isEmpty()).filter(o -> !Learning.isPreview(o)).toList();
      if (candidates.size() > 1)
        throw new IllegalArgumentException("Ambiguous learning input: " + name);
      observation = candidates.isEmpty() ? null : candidates.getFirst();
      if (observation == null && !required) return null;
    }
    // An invalid explicit/prepared value always fails, including for optional dependencies.
    if (observation == null || observation.isEmpty() || observation.getId() <= 0
        || observation.getEventTimestamps() == null || observation.getEventTimestamps().isEmpty())
      throw new IllegalArgumentException("Unresolved learning input: " + name);
    if (observation.getObservable() == null
        || declared.getArtifactType() != observation.getObservable().getArtifactType())
      throw new IllegalArgumentException("Incompatible learning input type: " + name);
    return new LearningContextualizer.Input(name, observation, parameters);
  }
}
