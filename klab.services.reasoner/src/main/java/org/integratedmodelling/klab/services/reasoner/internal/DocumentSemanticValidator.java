package org.integratedmodelling.klab.services.reasoner.internal;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.integratedmodelling.klab.api.knowledge.*;
import org.integratedmodelling.klab.api.lang.Statement;
import org.integratedmodelling.klab.api.lang.kim.*;
import org.integratedmodelling.klab.api.services.runtime.Notification;
import org.integratedmodelling.klab.runtime.language.*;
import org.integratedmodelling.klab.services.reasoner.ReasonerService;
import org.integratedmodelling.klab.services.reasoner.owl.OWLSemanticClauseSupport;

/**
 * Validation of source occurrences against a loaded knowledge snapshot; does not define ontologies.
 */
public class DocumentSemanticValidator
    implements KimNamespaceVisitor.Validator, KimOntologyVisitor.Validator,
        KimObservationStrategyDocumentVisitor.Validator {
  private final ReasonerService reasoner;
  private final boolean executableModels;
  private final boolean classifierPreparableDependencies;
  private final java.util.List<org.integratedmodelling.klab.api.services.reasoner.objects.SemanticValidationResponse.PendingClassifierDependency> pendingClassifierDependencies=new java.util.ArrayList<>();
  public java.util.List<org.integratedmodelling.klab.api.services.reasoner.objects.SemanticValidationResponse.PendingClassifierDependency> getPendingClassifierDependencies() { return java.util.List.copyOf(pendingClassifierDependencies); }

  private final OWLSemanticClauseSupport clauses;
  private final KimWorldviewValidator sourceValidator = new KimWorldviewValidator();

  public DocumentSemanticValidator(ReasonerService reasoner) {
    this(reasoner, false);
  }

  public DocumentSemanticValidator(ReasonerService reasoner, boolean executableModels) {
    this(reasoner, executableModels, false);
  }

  public DocumentSemanticValidator(ReasonerService reasoner, boolean executableModels, boolean classifierPreparableDependencies) {
    this.classifierPreparableDependencies = classifierPreparableDependencies;
    this.executableModels = executableModels;
    this.reasoner = reasoner;
    this.clauses = new OWLSemanticClauseSupport(reasoner.owl());
  }

  public List<Notification> validate(KlabDocument<?> document) {
    if (document instanceof KimObservationStrategyDocument strategies) {
      var visitor = new KimObservationStrategyDocumentVisitor(this, null);
      visitor.visit(strategies);
      return visitor.getNotifications();
    }
    if (document instanceof KimNamespace namespace) {
      var visitor = new KimNamespaceVisitor(this, null);
      visitor.visit(namespace);
      return visitor.getNotifications();
    }
    var visitor = new KimOntologyVisitor(this, null);
    visitor.visit((KimOntology) document);
    return visitor.getNotifications();
  }

  @Override
  public List<Notification> validateModel(
      KimModel model, KimObservableVisitor.Context context) {
    var result = validateDependencies(model, context);
    for (var output : model.getObservables()) {
      var semantics = output.getSemantics();
      if (semantics == null || !semantics.isCollective()) continue;
      try {
        // Recheck effective aliases/derivations against the loaded worldview, not caller flags.
        var effective = reasoner.declareConcept(semantics);
        if (effective != null && effective.is(SemanticType.CONFIGURATION))
          result.add(error("CONFIGURATION_INSTANTIATION: configurations emerge through DETECTION; "
              + "model each Configuration is illegal; model Configuration resolves it after emergence",
              output, context));
      } catch (RuntimeException unavailable) {
        result.add(error(unavailable, output, context));
      }
    }
    if (model.getObservables().isEmpty()
        || model.getObservables().getFirst().getSemantics() == null
        || !model.getObservables().getFirst().getSemantics().is(SemanticType.PROCESS)) {
      return result;
    }
    for (var contextualizable : model.getContextualization()) {
      var targetId = contextualizable.getTargetId();
      if (targetId == null || targetId.isBlank()) continue;
      var output = findTarget(model.getObservables(), targetId);
      var dependency = output == null ? findTarget(model.getDependencies(), targetId) : null;
      var target = output == null ? dependency : output;
      if (target == null) {
        result.add(error("Unknown contextualization target " + targetId, contextualizable, context));
        continue;
      }
      try {
        var resolvedTarget = resolveSemantics(target);
        // Unresolved observable diagnostics are produced by validateConcept(). Do not duplicate
        // those here with a misleading target-kind error.
        if (resolvedTarget == null) continue;
        if (!resolvedTarget.is(SemanticType.QUALITY)) {
          result.add(
              error(
                  "Process contextualization target " + targetId + " must be a quality",
                  contextualizable,
                  context));
          continue;
        }
        if (dependency != null) {
          var process = resolveSemantics(model.getObservables().getFirst());
          if (process != null && !reasoner.affectedBy(resolvedTarget, process)) {
            result.add(
                error(
                    "Process contextualization target "
                        + targetId
                        + " must be an output or a quality dependency affected by the process",
                    contextualizable,
                    context));
          }
        }
      } catch (RuntimeException e) {
        result.add(error(e, contextualizable, context));
      }
    }
    return result;
  }

  /** Dependency admission is a type proof, independent of distance/ranking. */
  private ArrayList<Notification> validateDependencies(
      KimModel model, KimObservableVisitor.Context context) {
    var result = new ArrayList<Notification>();
    if (model.getObservables().isEmpty()) return result;
    var primary = model.getObservables().getFirst();
    if (executableModels) {
      for (var output : model.getObservables().subList(1, model.getObservables().size())) {
        try {
          var semantics = output.getSemantics();
          var resolved = semantics == null ? null : reasoner.declareConcept(semantics);
          if (unboundSyntax(output)
              || semantics != null && (semantics.isPattern() || resolved == null
                  || resolved.is(SemanticType.NOTHING) || !reasoner.satisfiable(resolved)
                  || resolved.isGeneric()))
            result.add(error("MODEL_OUTPUT_UNRESOLVED: " + output.getUrn()
                + " has unfulfilled identity, abstractness or generic obligations", output, context));
        } catch (RuntimeException unavailable) {
          result.add(error("MODEL_OUTPUT_UNRESOLVED: " + unavailable.getMessage(), output, context));
        }
      }
    }
    if (primary.getSemantics() == null) {
      if (executableModels && unboundSyntax(primary))
        result.add(error("MODEL_OUTPUT_UNRESOLVED: unbound generic or pattern primary", primary, context));
      return result; // Nonsemantic learners retain their contract.
    }
    try {
      var modeled = reasoner.declareConcept(primary.getSemantics());
      if (modeled == null || modeled.is(SemanticType.NOTHING)) return result;
      var template = !executableModels && (authoredTemplate(primary.getSemantics(), modeled)
          || java.util.stream.Stream.concat(model.getObservables().stream(), model.getDependencies().stream())
              .anyMatch(this::unboundSyntax));
      if (!template && ((!modeled.is(SemanticType.PREDICATE) && modeled.isAbstract())
          || modeled.isGeneric() || unboundSyntax(primary)))
        result.add(error("MODEL_OUTPUT_UNRESOLVED: " + primary.getUrn()
            + " has unfulfilled identity or generic obligations", primary, context));
      Concept subject = SemanticType.isEnumerableSubstantial(modeled.getType())
          ? modeled.singular() : modelBearer(primary.getSemantics(), modeled);
      var requiredOccurrences = new ArrayList<>(model.getDependencies());
      // Secondary outputs are dereified relative to the modeled observation too.
      // Reuse its proof/projection rather than rejecting a context-bound quality
      // merely because its detached declaration is incomplete.
      if (executableModels)
        requiredOccurrences.addAll(model.getObservables().subList(1, model.getObservables().size()));
      int occurrenceIndex=-1;
      for (var originalOccurrence : requiredOccurrences) {
        occurrenceIndex++;
        var occurrence = originalOccurrence;
        // Outputs are always strict. Pending classifier evidence applies only to authored inputs.
        if ((!executableModels || classifierPreparableDependencies) && occurrenceIndex<model.getDependencies().size()) {
          var residual = classifierResidual(originalOccurrence);
          if (residual != null) {
            var prepared=DependencyExpansion.prepare((org.integratedmodelling.klab.api.lang.kim.impl.KimObservableImpl)originalOccurrence,
                reasoner.serviceScope().getService(org.integratedmodelling.klab.api.services.ResourcesService.class)::declareConcept);
            pendingClassifierDependencies.add(new org.integratedmodelling.klab.api.services.reasoner.objects.SemanticValidationResponse.PendingClassifierDependency(
                model.getUrn(),occurrenceIndex,originalOccurrence.getUrn(),reasoner.declareObservable(residual).getUrn(),
                prepared.requests().stream().map(request->request.identity().path()).toList()));
            occurrence = residual;
          }
        }
        if (occurrence.getSemantics() == null) {
          if (unboundSyntax(occurrence))
            dependencyUnresolved(result, occurrence, context, template,
                "generic or pattern dependency remains unbound");
          continue;
        }
        var dependency = reasoner.declareConcept(occurrence.getSemantics());
        if (dependency == null || dependency.is(SemanticType.NOTHING)) continue;
        if (!reasoner.satisfiable(dependency)) {
          result.add(error("MODEL_DEPENDENCY_INCOMPATIBLE: " + occurrence.getUrn()
              + " is unsatisfiable", occurrence, context));
          continue;
        }
        boolean needsBearer = SemanticType.isDependent(dependency.getType())
            || dependency.is(SemanticType.PREDICATE);
        var effective = dependency;
        // An asserted/inherited effect relation explicitly permits a process/event
        // to use an affected quality in its own bearer. It does not transfer
        // the process's context or native identity to that quality.
        boolean affectedQuality = dependency.is(SemanticType.QUALITY)
            && (modeled.is(SemanticType.PROCESS) || modeled.is(SemanticType.EVENT))
            && reasoner.affectedBy(dependency, modeled);
        if (needsBearer && subject != null && !affectedQuality) {
          var compatibility = clauses.clauseCompatibility(dependency, SemanticRole.INHERENT, subject);
          if (compatibility != OWLSemanticClauseSupport.Compatibility.COMPATIBLE) {
            if (compatibility == OWLSemanticClauseSupport.Compatibility.IMPOSSIBLE)
              result.add(error("MODEL_DEPENDENCY_INCOMPATIBLE: " + occurrence.getUrn()
                  + " has a provably incompatible bearer for modeled subject " + subject.getUrn(), occurrence, context));
            else dependencyUnresolved(result, occurrence, context, template,
                "modeled subject " + subject.getUrn() + " does not prove every required bearer bound");
            continue;
          }
          // Detached expression-status projection: no identity or bearer assertion is added
          // to the dependent, its native process identity, or the shared ontology.
          var wrapper = new org.integratedmodelling.klab.api.lang.kim.impl.KimConceptImpl();
          wrapper.setObservable(occurrence.getSemantics());
          wrapper.setType(new java.util.HashSet<>(occurrence.getSemantics().getType()));
          wrapper.setInherent(reasoner.serviceScope().getService(
              org.integratedmodelling.klab.api.services.ResourcesService.class).declareConcept(subject.getUrn()));
          wrapper.finalizeDefinition();
          if (dependency instanceof org.integratedmodelling.common.knowledge.ConceptImpl implementation)
            effective = new org.integratedmodelling.klab.services.reasoner.owl.IdentityRequirementSupport(
                reasoner.owl(), reasoner::identityEligible).project(implementation, wrapper);
        } else if (needsBearer && !affectedQuality
            && !clauses.bearerConstraints(dependency).isEmpty() && subject == null) {
          dependencyUnresolved(result, occurrence, context, template, "the modeled subject has no established bearer type");
          continue;
        }
        // A classifier's abstract predicate is a category to characterize, not an
        // instruction to instantiate an abstract quality/subject.
        if ((!effective.is(SemanticType.PREDICATE) && effective.isAbstract()) || effective.isGeneric()
            || unboundSyntax(occurrence))
          dependencyUnresolved(result, occurrence, context, template,
              "identity, authored abstractness, or generic obligations remain unbound");
      }
    } catch (RuntimeException unavailable) {
      result.add(error("MODEL_DEPENDENCY_UNRESOLVED: " + unavailable.getMessage(), model, context));
    }
    return result;
  }

  /** Detached proof skeleton; the original source still receives applicability/disjointness checks. */
  private KimObservable classifierResidual(KimObservable source) {
    if (!(source instanceof org.integratedmodelling.klab.api.lang.kim.impl.KimObservableImpl syntax)
        || unboundSyntax(source)) return null;
    try {
      var explicitBearer=source.getSemantics().getInherent();
      if(explicitBearer==null || explicitBearer.getName()==null || explicitBearer.getObservable()!=null
          || explicitBearer.isNegated() || explicitBearer.isCollective() || explicitBearer.getSemanticModifier()!=null)return null;
      var resources=reasoner.serviceScope().getService(org.integratedmodelling.klab.api.services.ResourcesService.class);
      var prepared=DependencyExpansion.prepare(syntax,resources::declareConcept);
      if(prepared.requests().isEmpty())return null;
      var removals=new java.util.HashMap<java.util.List<String>,java.util.Set<Integer>>();
      for(var request:prepared.requests()) {
        var path=request.identity().path();var declaration=request.declaration();var predicate=request.predicate();
        if(!declaration.is(SemanticType.ATTRIBUTE) || !declaration.is(SemanticType.ABSTRACT)
            || !plainPredicateSlot(predicate))return null;
        boolean direct=path.size()==3 && path.get(0).equals("semantics") && path.get(1).equals("traits");
        boolean bearer=path.size()==4 && path.get(0).equals("semantics") && path.get(1).equals("inherent") && path.get(2).equals("traits");
        if(!direct && !bearer)return null;
        if(direct) { var canonical=reasoner.declareConcept(source.getSemantics());if(canonical==null || !canonical.is(SemanticType.QUALITY))return null; }
        var owner=direct?source.getSemantics():source.getSemantics().getInherent();
        if(owner==null || owner.isNegated() || owner.getSemanticModifier()!=null || owner.getObservable()!=null
            || owner.getRelationshipSource()!=null || owner.getRelationshipTarget()!=null)return null;
        removals.computeIfAbsent(path.subList(0,path.size()-2),ignored->new java.util.HashSet<>()).add(Integer.parseInt(path.getLast()));
      }
      var residual=new org.integratedmodelling.klab.api.lang.kim.impl.KimObservableImpl(syntax,(path,node)->{
        var indexes=removals.get(path);if(indexes==null)return;
        for(int i=node.getTraits().size()-1;i>=0;i--)if(indexes.contains(i))node.getTraits().remove(i);
      });
      return residual;
    } catch(RuntimeException unavailable) { return null; }
  }

  private static boolean plainPredicateSlot(KimConcept c) {
    return c!=null && c.getName()!=null && c.getObservable()==null && c.getAbout().isEmpty()
        && c.getInherent()==null && c.getGoal()==null && c.getCausant()==null && c.getCaused()==null
        && c.getCompresent()==null && c.getComparisonConcept()==null && c.getAuthorityTerm()==null
        && c.getAuthority()==null && c.getSemanticModifier()==null && c.getRelationshipSource()==null
        && c.getRelationshipTarget()==null && !c.isNegated() && !c.isCollective()
        && c.getOperands().isEmpty() && c.getModifiers().isEmpty() && c.getCooccurrent()==null
        && c.getAdjacent()==null && c.getParent()==null && c.getValueOperators().isEmpty()
        && !c.isPattern() && c.getPatternVariables().isEmpty() && c.getTraits().isEmpty() && c.getRoles().isEmpty();
  }

  private boolean unboundSyntax(KimObservable observable) {
    return ModelValidationGate.hasUnboundSyntax(observable);
  }

  private boolean authoredTemplate(KimConcept syntax, Concept modeled) {
    if (syntax.isPattern() || modeled.isGeneric()) return true;
    if (modeled.is(SemanticType.PREDICATE)) return false;
    var head = syntax;
    while (true) {
      if (head.getSemanticModifier() != null) return false;
      if (head.getObservable() == null) break;
      head = head.getObservable();
    }
    var canonical = head.getName() == null ? null : reasoner.owl().getConcept(head.getName());
    return canonical != null && canonical.isAbstract();
  }

  private Concept modelBearer(KimConcept syntax, Concept modeled) {
    for (var current = syntax; current != null; current = current.getObservable()) {
      if (current.getInherent() != null) return reasoner.declareConcept(current.getInherent()).singular();
      if (current.getSemanticModifier() != null) break;
    }
    return modeled.is(SemanticType.PREDICATE) ? null : reasoner.inherent(modeled);
  }

  private void dependencyUnresolved(List<Notification> result, KimObservable occurrence,
      KimObservableVisitor.Context context, boolean template, String reason) {
    var message = (template ? "MODEL_DEPENDENCY_TEMPLATE: " : "MODEL_DEPENDENCY_UNRESOLVED: ")
        + occurrence.getUrn() + ": " + reason;
    result.add(template
        ? Notification.warning(message, Notification.LexicalContext.of(occurrence, context.getDocument()))
        : error(message, occurrence, context));
  }

  /** Resolve the OWL node used by worldview relationships, without observable-level decoration. */
  private Concept resolveSemantics(KimObservable observable) {
    var semantics = observable == null ? null : observable.getSemantics();
    if (semantics == null) return null;
    return semantics.getName() == null
        ? reasoner.declareConcept(semantics)
        : reasoner.resolveConcept(semantics.getName());
  }

  private KimObservable findTarget(List<KimObservable> observables, String targetId) {
    return observables.stream()
        .filter(
            observable ->
                Objects.equals(targetId, observable.getFormalName())
                    || Objects.equals(targetId, observable.getCodeName()))
        .findFirst()
        .orElse(null);
  }

  @Override
  public List<Notification> validateReference(
      KimObservableVisitor.Reference reference, KimObservableVisitor.Context context) {
    if (context.getDocument() instanceof KimOntology ontology
        && reference.urn().startsWith(ontology.getUrn() + ":")) {
      // The loaded OWL may contain a later (or deleted) declaration from the previous save.
      return sourceValidator.validateReference(reference, context);
    }
    return List.of();
  }

  @Override
  public List<Notification> validateConcept(
      KimConcept syntax, KimObservableVisitor.Context context) {
    // Validate each complete occurrence once, including its operands. The same URN elsewhere
    // is a different occurrence and must receive its own diagnostic.
    if (context.getParent() != null && context.getParent().getNode() instanceof KimConcept)
      return List.of();
    if (syntax.isPattern()) return List.of();
    var result = new ArrayList<Notification>();
    try {
      var head =
          syntax.getName() == null
              ? reasoner.declareConcept(syntax.getObservable())
              : reasoner.resolveConcept(syntax.getName());
      if (head == null || head.is(SemanticType.NOTHING)) {
        result.add(error("Unresolved concept: " + syntax.getUrn(), syntax, context));
        return result;
      }
      if (syntax.getRelationshipSource() != null || syntax.getRelationshipTarget() != null) {
        if (!head.is(SemanticType.RELATIONSHIP)
            || syntax.getRelationshipSource() == null
            || syntax.getRelationshipTarget() == null) {
          result.add(
              error(
                  "linking ... to ... requires a relationship and both endpoints",
                  syntax,
                  context));
        } else {
          endpoint(
              head,
              syntax.getRelationshipSource(),
              SemanticRole.RELATIONSHIP_SOURCE,
              context,
              result);
          endpoint(
              head,
              syntax.getRelationshipTarget(),
              SemanticRole.RELATIONSHIP_TARGET,
              context,
              result);
        }
      }
      for (var modifier : syntax.getModifiers()) {
        if (modifier.getFirst() == SemanticRole.RELATIONSHIP_SOURCE
            || modifier.getFirst() == SemanticRole.RELATIONSHIP_TARGET) continue;
        var operand = reasoner.declareConcept(modifier.getSecond());
        if (operand == null || operand.is(SemanticType.NOTHING))
          result.add(
              error(
                  "Unresolved clause operand: " + modifier.getSecond().getUrn(),
                  modifier.getSecond(),
                  context));
        else if (syntax.getSemanticModifier() == null) {
          // Configuration 'of' is emergence, not native/applicability bearer matching.
          if (modifier.getFirst() == SemanticRole.INHERENT && head.is(SemanticType.CONFIGURATION)) continue;
          var compatibility = clauses.clauseCompatibility(head, modifier.getFirst(), operand);
          if (compatibility != OWLSemanticClauseSupport.Compatibility.COMPATIBLE)
            result.add(error("Clause " + modifier.getFirst() + " operand " + operand.getUrn()
                + (compatibility == OWLSemanticClauseSupport.Compatibility.IMPOSSIBLE
                    ? " is provably incompatible with the inherited restrictions or applies to domain of "
                    : " has unresolved membership in the inherited restrictions or applies to domain of ")
                + head.getUrn(), modifier.getSecond(), context));
        }
      }
      var predicates = new ArrayList<Concept>();
      predicates.addAll(reasoner.traits(head));
      predicates.addAll(reasoner.roles(head));
      var predicateSyntax = new ArrayList<KimConcept>(syntax.getTraits());
      predicateSyntax.addAll(syntax.getRoles());
      for (var predicate : predicateSyntax) {
        var resolved = reasoner.declareConcept(predicate);
        if (resolved == null || resolved.is(SemanticType.NOTHING))
          result.add(error("Unresolved predicate: " + predicate.getUrn(), predicate, context));
        else {
          predicates.add(resolved);
          if (!clauses.predicatesCompatible(predicates))
            result.add(
                error(
                    "Predicate "
                        + predicate.getUrn()
                        + " is disjoint with another applied predicate",
                    predicate,
                    context));
        }
      }
      if (!result.isEmpty()) return result;
      var concept = reasoner.declareConcept(syntax);
      if (concept != null && !concept.is(SemanticType.NOTHING)) {
        for (var predicate : predicateSyntax) {
          var resolved = reasoner.declareConcept(predicate);
          if (!clauses.applicableTo(resolved, concept))
            result.add(
                error(
                    "Predicate " + predicate.getUrn() + " cannot apply to " + syntax.getUrn(),
                    predicate,
                    context));
        }
      }
      collect(concept, syntax, context, result);
    } catch (RuntimeException e) {
      result.add(error(e, syntax, context));
    }
    return result;
  }

  private void endpoint(
      Concept owner,
      KimConcept syntax,
      SemanticRole role,
      KimObservableVisitor.Context context,
      List<Notification> result) {
    var endpoint = reasoner.declareConcept(syntax);
    String label = role == SemanticRole.RELATIONSHIP_SOURCE ? "Source" : "Target";
    if (endpoint == null
        || endpoint.is(SemanticType.NOTHING)
        || !endpoint.is(SemanticType.COUNTABLE)) {
      result.add(
          error(
              label + " endpoint must resolve to a substantial: " + syntax.getUrn(),
              syntax,
              context));
    } else if (!clauses.accepts(owner, role, endpoint)) {
      String bounds =
          clauses.clauses(owner).stream()
              .filter(c -> c.getRole() == role)
              .map(
                  c ->
                      c.getCode().stream()
                          .map(t -> t.getValue())
                          .collect(java.util.stream.Collectors.joining(" ")))
              .collect(java.util.stream.Collectors.joining("; "));
      result.add(
          error(
              label
                  + " endpoint "
                  + syntax.getUrn()
                  + " must specialize the declared links endpoint of "
                  + owner.getUrn()
                  + ": "
                  + bounds,
              syntax,
              context));
    }
  }

  @Override
  public List<Notification> validateConceptStatement(
      KimConceptStatement statement, KimObservableVisitor.Context context) {
    var result = new ArrayList<Notification>();
    String urn =
        statement.getUrn().contains(":")
            ? statement.getUrn()
            : context.getDocument().getUrn() + ":" + statement.getUrn();
    var concept = reasoner.resolveConcept(urn);
    collect(concept, statement, context, result);
    for (var message : org.integratedmodelling.klab.services.reasoner.owl.WorldviewDeclarationSupport
        .unresolvedDescriptionBearers(reasoner.owl(), concept))
      result.add(Notification.warning(message, Notification.LexicalContext.of(statement, context.getDocument())));
    return result;
  }

  private void collect(
      Concept concept,
      Statement source,
      KimObservableVisitor.Context context,
      List<Notification> result) {
    if (concept != null) {
      for (var notification : concept.getNotifications()) {
        // Copy: never attach occurrence locations to cached semantic objects.
        var copy = Notification.create(
                notification.getLevel(),
                notification.getMessage(),
                Notification.LexicalContext.of(source, context.getDocument()));
        copy.setStackTrace(notification.getStackTrace());
        result.add(copy);
      }
    }
    if (concept == null || concept.is(SemanticType.NOTHING) || !reasoner.satisfiable(concept)) {
      if (result.stream().noneMatch(n -> n.getLevel() == Notification.Level.Error))
        result.add(
            error(
                "Unresolved or inconsistent semantics: "
                    + (source instanceof KlabStatement statement
                        ? statement.getUrn()
                        : "declaration"),
                source,
                context));
    }
  }

  private Notification error(
      Object message, Statement source, KimObservableVisitor.Context context) {
    // ConceptData references may lack a token range. Use their enclosing source occurrence
    // rather than emitting a diagnostic that the editor cannot place.
    for (var enclosing = context; source.getLength() <= 0 && enclosing != null;
        enclosing = enclosing.getParent()) {
      if (enclosing.getNode() instanceof Statement statement && statement.getLength() > 0) {
        source = statement;
      }
    }
    return Notification.error(
        message, Notification.LexicalContext.of(source, context.getDocument()));
  }
}
