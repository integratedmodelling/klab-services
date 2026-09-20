package org.integratedmodelling.klab.services.resolver;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.HashSet;
import java.util.List;
import org.integratedmodelling.klab.api.digitaltwin.ProcessPlan;
import org.integratedmodelling.klab.api.exceptions.KlabValidationException;
import org.integratedmodelling.klab.api.knowledge.Model;
import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.api.knowledge.SemanticType;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.services.Reasoner;

/** Shared pre-resolution and compilation validation for a process model. */
final class ProcessModelBindings {
  static String name(Observable observable) {
    return observable.getStatedName() == null ? observable.getName() : observable.getStatedName();
  }

  static ProcessPlan analyze(Model model, ContextScope scope) {
    if (model.getObservables().isEmpty()
        || !model.getObservables().getFirst().is(SemanticType.PROCESS)) return null;
    var process = model.getObservables().getFirst();
    var bearer = scope.getContextObservation();
    if (bearer == null)
      throw new KlabValidationException("Process requires a substantial bearer: " + model.getUrn());
    var semantics = bearer.getObservable();
    if (!(semantics.is(SemanticType.SUBJECT)
            || semantics.is(SemanticType.EVENT)
            || (semantics.is(SemanticType.RELATIONSHIP) && semantics.is(SemanticType.FUNCTIONAL)))
        || semantics.is(SemanticType.STRUCTURAL))
      throw new KlabValidationException("Process cannot be hosted by " + semantics.getUrn());
    var reasoner = scope.getService(Reasoner.class);
    if (reasoner == null)
      throw new KlabValidationException("Process binding requires Reasoner evidence");
    compatible(process, semantics, reasoner);
    var declared = new LinkedHashMap<String, Observable>();
    var dependencies = new HashSet<String>();
    for (var output : model.getObservables().subList(1, model.getObservables().size()))
      add(declared, output);
    for (var input : model.getDependencies()) {
      add(declared, input);
      dependencies.add(name(input));
    }
    var assigned = new HashSet<String>();
    for (var computation : model.getComputation()) {
      if (computation.getExpression() == null && computation.getLiteral() == null) continue;
      var target = computation.getTargetId();
      if (target == null || target.isBlank() || target.equals(name(process)))
        throw new KlabValidationException(
            "A process cannot be set to a value; use set <quality> to [...] in " + model.getUrn());
      var observable = declared.get(target);
      if (observable == null || !observable.is(SemanticType.QUALITY))
        throw new KlabValidationException(
            "Unknown or non-quality process assignment target: " + target);
      assigned.add(target);
    }
    var bindings = new ArrayList<ProcessPlan.Binding>();
    var evidence = reasoner.influences(process).stream().filter(i -> !i.kind().descriptive()).toList();
    for (var entry : declared.entrySet()) {
      var observable = entry.getValue();
      if (!observable.is(SemanticType.QUALITY)) continue;
      compatible(observable, semantics, reasoner);
      var effect =
          reasoner.createdBy(observable, process)
              ? ProcessPlan.Effect.CREATED
              : reasoner.affectedBy(observable, process) || assigned.contains(entry.getKey())
                  ? ProcessPlan.Effect.AFFECTED
                  : ProcessPlan.Effect.INPUT;
      var relations = new java.util.TreeSet<String>();
      for (var influence : evidence) {
        if (observable.getSemantics().equals(influence.target())
            || reasoner.is(observable, influence.target())) relations.add(influence.kind().name());
      }
      if (effect == ProcessPlan.Effect.CREATED) relations.add("CREATES");
      if (effect == ProcessPlan.Effect.AFFECTED && reasoner.affectedBy(observable, process))
        relations.add("AFFECTS");
      if (assigned.contains(entry.getKey())) relations.add("ASSIGNMENT");
      bindings.add(
          new ProcessPlan.Binding(
              entry.getKey(),
              observable,
              effect,
              dependencies.contains(entry.getKey()),
              assigned.contains(entry.getKey()),
              List.copyOf(relations)));
    }
    var obligations = new ArrayList<ProcessPlan.Obligation>();
    for (var influence : evidence) {
      obligations.add(
          new ProcessPlan.Obligation(
              influence.target().getUrn(),
              influence.kind()
                      == org.integratedmodelling.klab.api.knowledge.SemanticInfluence.Kind.CREATES
                  ? ProcessPlan.Effect.CREATED
                  : ProcessPlan.Effect.AFFECTED,
              influence.kind().name()));
    }
    // Preserve the semantic closure even where endpoints are not yet resolved observations.
    // It does not create inputs, assign effects to qualities, or allocate observations.
    var descriptive = new java.util.LinkedHashSet<org.integratedmodelling.klab.api.knowledge.SemanticInfluence>();
    var queue = new java.util.ArrayDeque<org.integratedmodelling.klab.api.knowledge.Concept>();
    bindings.forEach(b -> queue.add(b.observable().getSemantics()));
    evidence.forEach(i -> queue.add(i.target()));
    var visited = new HashSet<String>();
    while (!queue.isEmpty()) {
      var quality = queue.removeFirst();
      if (!visited.add(quality.getUrn())) continue;
      for (var link : reasoner.influences(quality)) {
        if (!link.kind().descriptive()) continue;
        if (link.source() == null || link.provenance() == null)
          throw new KlabValidationException("Descriptive evidence requires an S3.2 Reasoner");
        descriptive.add(link);
        queue.add(link.source());
        queue.add(link.target());
      }
    }
    return new ProcessPlan(2, bearer.getId(), model.getUrn(), bindings, obligations, List.copyOf(descriptive));
  }

  private static void add(LinkedHashMap<String, Observable> declared, Observable observable) {
    var name = name(observable);
    if (name == null || name.isBlank() || declared.putIfAbsent(name, observable) != null)
      throw new KlabValidationException("Ambiguous process model binding: " + name);
  }

  private static void compatible(Observable observable, Observable bearer, Reasoner reasoner) {
    var inherent = reasoner.inherent(observable);
    if (inherent != null && !reasoner.is(bearer, inherent))
      throw new KlabValidationException(
          "Incompatible process bearer for "
              + observable.getUrn()
              + ": expected "
              + inherent.getUrn()
              + ", got "
              + bearer.getUrn());
  }
}
