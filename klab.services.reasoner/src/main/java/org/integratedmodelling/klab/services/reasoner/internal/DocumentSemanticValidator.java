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
  private record ObserverDeclaration(Observable observable,org.integratedmodelling.klab.api.lang.kim.impl.KimObservableImpl source) {}
  private final List<ObserverDeclaration> observerDeclarations=new ArrayList<>();
  private boolean sourceValid;
  private final ReasonerService reasoner;
  private final SemanticObligationCollector semanticObligations;
  public List<String> getSemanticObligations() { return semanticObligations.terms(); }
  public boolean isSemanticObligationsComplete() { return sourceValid && semanticObligations.complete(); }
  public boolean isSourceSemanticsComplete() { return sourceValid && semanticObligations.sourceComplete(); }
  private final boolean executableModels;
  private final String executionContext;
  private final List<org.integratedmodelling.klab.api.services.reasoner.objects.SemanticValidationResponse.PendingModelContext> pendingModelContexts=new ArrayList<>();
  public List<org.integratedmodelling.klab.api.services.reasoner.objects.SemanticValidationResponse.PendingModelContext> getPendingModelContexts() { return List.copyOf(pendingModelContexts); }
  private final java.util.List<org.integratedmodelling.klab.api.services.reasoner.objects.SemanticValidationResponse.ConditionalModelContext> conditionalModelContexts=new ArrayList<>();
  public java.util.List<org.integratedmodelling.klab.api.services.reasoner.objects.SemanticValidationResponse.ConditionalModelContext> getConditionalModelContexts() { return List.copyOf(conditionalModelContexts); }

  private final boolean classifierPreparableDependencies;
  private final java.util.List<org.integratedmodelling.klab.api.services.reasoner.objects.SemanticValidationResponse.PendingClassifierDependency> pendingClassifierDependencies=new java.util.ArrayList<>();
  public java.util.List<org.integratedmodelling.klab.api.services.reasoner.objects.SemanticValidationResponse.PendingClassifierDependency> getPendingClassifierDependencies() { return java.util.List.copyOf(pendingClassifierDependencies); }

  private final java.util.List<org.integratedmodelling.klab.api.services.reasoner.objects.SemanticValidationResponse.PendingMemberQualityDependency> preparableMemberQualityDependencies;
  private final java.util.List<org.integratedmodelling.klab.api.services.reasoner.objects.SemanticValidationResponse.PendingMemberQualityDependency> pendingMemberQualityDependencies=new ArrayList<>();
  public java.util.List<org.integratedmodelling.klab.api.services.reasoner.objects.SemanticValidationResponse.PendingMemberQualityDependency> getPendingMemberQualityDependencies() {return List.copyOf(pendingMemberQualityDependencies);}

  private final OWLSemanticClauseSupport clauses;
  private final KimWorldviewValidator sourceValidator = new KimWorldviewValidator();

  public DocumentSemanticValidator(ReasonerService reasoner) {
    this(reasoner, false);
  }

  public DocumentSemanticValidator(ReasonerService reasoner, boolean executableModels) {
    this(reasoner, executableModels, false);
  }

  public DocumentSemanticValidator(ReasonerService reasoner, boolean executableModels, boolean classifierPreparableDependencies) {
    this(reasoner, executableModels, classifierPreparableDependencies, null);
  }

  public DocumentSemanticValidator(ReasonerService reasoner, boolean executableModels,
      boolean classifierPreparableDependencies, String executionContext) {
    this(reasoner,executableModels,classifierPreparableDependencies,executionContext,List.of());
  }
  public DocumentSemanticValidator(ReasonerService reasoner,boolean executableModels,
      boolean classifierPreparableDependencies,String executionContext,
      List<org.integratedmodelling.klab.api.services.reasoner.objects.SemanticValidationResponse.PendingMemberQualityDependency> memberDependencies) {
    this.preparableMemberQualityDependencies=List.copyOf(memberDependencies);
    this.executionContext = executionContext;
    this.classifierPreparableDependencies = classifierPreparableDependencies;
    this.executableModels = executableModels;
    this.reasoner = reasoner;
    this.semanticObligations = new SemanticObligationCollector(reasoner);
    this.clauses = new OWLSemanticClauseSupport(reasoner.owl());
  }

  public List<Notification> validate(KlabDocument<?> document) {
    observerDeclarations.clear();conditionalModelContexts.clear();pendingModelContexts.clear();sourceValid=false;
    semanticObligations.reset();
    if(executionContext!=null)try { semanticObligations.add(reasoner.resolveConcept(executionContext)); }
      catch(RuntimeException unavailable) {semanticObligations.incomplete();}
    var diagnostics=validateSource(document);
    sourceValid=!org.integratedmodelling.common.utils.Utils.Notifications.hasErrors(diagnostics);
    return diagnostics;
  }

  /** Explicit export step for trusted source ingestion only, after the complete source is valid.
   * Generic construction and editor validation never publish source records into accepted OWL. */
  public void exportObserverDeclarations() {
    if(!sourceValid)throw new IllegalStateException("Observer metadata requires a completely validated source");
    synchronized(reasoner.owl()) {
      for(var declaration:observerDeclarations)org.integratedmodelling.klab.services.reasoner.owl.ObserverOntologySupport.compile(
          reasoner.owl(),reasoner.owl().requireOntology(declaration.source().getNamespace()),
          declaration.observable(),declaration.source());
    }
  }

  private List<Notification> validateSource(KlabDocument<?> document) {
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
  public List<Notification> validateStatement(KlabStatement statement,KimObservableVisitor.Context context) {
    try {semanticObligations.statement(statement,context);}
    catch(RuntimeException unsupported) {semanticObligations.incomplete();}
    return List.of();
  }
  @Override
  public List<Notification> validateServiceCall(org.integratedmodelling.klab.api.lang.ServiceCall call,
      KimObservableVisitor.Context context) {
    try {semanticObligations.call(call,context);}
    catch(RuntimeException unsupported) {semanticObligations.incomplete();}
    return List.of();
  }

  @Override
  public List<Notification> validateObservable(KimObservable observable,KimObservableVisitor.Context context) {
    if(observable.getObserverSemantics()==null)return List.of();
    try {
      var source=new org.integratedmodelling.klab.api.lang.kim.impl.KimObservableImpl(
          (org.integratedmodelling.klab.api.lang.kim.impl.KimObservableImpl)observable);
      // The observable bean's default PUBLIC is not the effective lexical model scope.
      for(var node:context.getPath())if(node instanceof KimModel model)source.setScope(model.getScope());
      var resolved=reasoner.declareObservable(source);
      if(context.getDocument()!=null && source.getNamespace()!=null && !source.getNamespace().isBlank()) {
        if(!source.getNamespace().equals(context.getDocument().getUrn()))
          throw new IllegalArgumentException("OBSERVER_SOURCE_OWNER: observable namespace differs from its lexical document");
        source.setProjectName(context.getDocument().getProjectName());
        observerDeclarations.add(new ObserverDeclaration(resolved,source));
      }
      return List.of();
    } catch(RuntimeException invalid) {return List.of(error(invalid,observable,context));}
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
      // A source can describe a context-parametric model. Its context must jointly prove every
      // occurrence, and executable admission binds the obligation to the actual observed context.
      boolean pendingSourceContext=false;
      var nativeSubject=subject;
      var ordinaryContextOccurrences=new ArrayList<KimObservable>();
      for(int i=0;i<requiredOccurrences.size();i++) {
        var candidate=requiredOccurrences.get(i);
        var declared=candidate.getSemantics()==null?null:reasoner.declareConcept(candidate.getSemantics());
        if(i<model.getDependencies().size() && declared!=null && pendingMemberQuality(model,candidate,declared,i)!=null)continue;
        ordinaryContextOccurrences.add(candidate);
      }
      if (nativeSubject != null && ordinaryContextOccurrences.stream().anyMatch(o -> o.getSemantics()!=null
          && clauses.clauseCompatibility(reasoner.declareConcept(o.getSemantics()), SemanticRole.INHERENT, nativeSubject)
              != OWLSemanticClauseSupport.Compatibility.COMPATIBLE)) {
        int priorPending=pendingModelContexts.size();
        var contextual = conditionalSubject(model, modeled, ordinaryContextOccurrences);
        if (contextual != null) subject=contextual;
        pendingSourceContext=!executableModels && pendingModelContexts.size()>priorPending;
      }
      int occurrenceIndex=-1;
      for (var originalOccurrence : requiredOccurrences) {
        occurrenceIndex++;
        final int sourceOccurrenceIndex=occurrenceIndex;
        var occurrence = originalOccurrence;
        // Outputs are always strict. Pending classifier evidence applies only to authored inputs.
        var declarations=reasoner.serviceScope().getService(org.integratedmodelling.klab.api.services.ResourcesService.class);
        var collective=occurrenceIndex<model.getDependencies().size() && declarations!=null
            ? CollectiveClassification.prepare(originalOccurrence,declarations::declareConcept) : null;
        if (occurrenceIndex<model.getDependencies().size()
            && (collective!=null || !executableModels || classifierPreparableDependencies)) {
          // Selected collective predicates are executable classification operations,
          // including concrete ANY. Quality-family expansion keeps its prior gate.
          var residual = collective!=null ? collective.members() : classifierResidual(originalOccurrence);
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
        if (needsBearer && subject != null && !affectedQuality && !pendingSourceContext) {
          var compatibility = clauses.clauseCompatibility(dependency, SemanticRole.INHERENT, subject);
          if (compatibility != OWLSemanticClauseSupport.Compatibility.COMPATIBLE) {
            var memberPending=occurrenceIndex<model.getDependencies().size()
                ? pendingMemberQuality(model,originalOccurrence,dependency,occurrenceIndex) : null;
            if(memberPending!=null) {
              pendingMemberQualityDependencies.add(memberPending);
              // Validate the native quality under its native bearer, never under the regional consumer.
              var wrapper=new org.integratedmodelling.klab.api.lang.kim.impl.KimConceptImpl();
              wrapper.setObservable(occurrence.getSemantics());wrapper.setType(new java.util.HashSet<>(occurrence.getSemantics().getType()));
              wrapper.setInherent(reasoner.serviceScope().getService(org.integratedmodelling.klab.api.services.ResourcesService.class).declareConcept(memberPending.nativeBearer()));
              wrapper.finalizeDefinition();
              if(dependency instanceof org.integratedmodelling.common.knowledge.ConceptImpl implementation)
                effective=new org.integratedmodelling.klab.services.reasoner.owl.IdentityRequirementSupport(reasoner.owl(),reasoner::identityEligible).project(implementation,wrapper);
            } else {
            if (compatibility == OWLSemanticClauseSupport.Compatibility.IMPOSSIBLE)
              result.add(error("MODEL_DEPENDENCY_INCOMPATIBLE: " + occurrence.getUrn()
                  + " has a provably incompatible bearer for modeled subject " + subject.getUrn(), occurrence, context));
            else dependencyUnresolved(result, occurrence, context, template,
                "modeled subject " + subject.getUrn() + " does not prove every required bearer bound");
            continue;
            }
          }
          // Detached expression-status projection: no identity or bearer assertion is added
          // to the dependent, its native process identity, or the shared ontology.
          var wrapper = new org.integratedmodelling.klab.api.lang.kim.impl.KimConceptImpl();
          wrapper.setObservable(occurrence.getSemantics());
          wrapper.setType(new java.util.HashSet<>(occurrence.getSemantics().getType()));
          wrapper.setInherent(reasoner.serviceScope().getService(
              org.integratedmodelling.klab.api.services.ResourcesService.class).declareConcept(subject.getUrn()));
          wrapper.finalizeDefinition();
          if (!pendingMemberQualityDependencies.stream().anyMatch(p->p.modelUrn().equals(model.getUrn()) && p.dependencyIndex()==sourceOccurrenceIndex)
              && dependency instanceof org.integratedmodelling.common.knowledge.ConceptImpl implementation)
            effective = new org.integratedmodelling.klab.services.reasoner.owl.IdentityRequirementSupport(
                reasoner.owl(), reasoner::identityEligible).project(implementation, wrapper);
        } else if (needsBearer && !affectedQuality && !pendingSourceContext
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

  private org.integratedmodelling.klab.api.services.reasoner.objects.SemanticValidationResponse.PendingMemberQualityDependency
      pendingMemberQuality(KimModel model,KimObservable source,Concept quality,int index) {
    if(!quality.is(SemanticType.QUALITY) || quality.isAbstract() || quality.isGeneric() || unboundSyntax(source)
        || source.getUnit()!=null && !source.getUnit().isBlank() || source.getCurrency()!=null || source.getRange()!=null
        || source.getFormalName()==null || source.getFormalName().isBlank()
        || model.getDependencies().stream().filter(o->source.getFormalName().equals(o.getFormalName())).count()!=1) return null;
    var ratio=reasoner.owl().getConcept(org.integratedmodelling.klab.services.reasoner.internal.CoreOntology.NS.CORE_RATIO);
    if(ratio==null || !reasoner.is(quality,ratio)
        || !org.integratedmodelling.klab.services.reasoner.owl.ConditionalModelContextSupport.explicitlyUnitless(reasoner.owl(),quality))return null;
    var complete=reasoner.declareObservable(source);
    if(complete==null || complete.getUrn()==null || complete.getObserverSemantics()!=null
        || complete.getUnit()!=null || complete.getCurrency()!=null || complete.getRange()!=null
        || complete.getContextualization()!=org.integratedmodelling.klab.api.knowledge.Contextualization.QUANTIFICATION)return null;
    String canonicalQuality=complete.getUrn();
    if(executableModels) {
      return preparableMemberQualityDependencies.stream().filter(p->p.modelUrn().equals(model.getUrn())
          && p.dependencyIndex()==index && p.port().equals(source.getFormalName()) && p.quality().equals(canonicalQuality))
          .filter(p->{var bearer=reasoner.resolveConcept(p.nativeBearer());return bearer!=null
              && clauses.clauseCompatibility(quality,SemanticRole.INHERENT,bearer)==OWLSemanticClauseSupport.Compatibility.COMPATIBLE;})
          .findFirst().orElse(null);
    }
    var explicit=ModelValidationGate.explicitAtomicInherent(source);
    if(explicit==null)return null;
    var bearer=reasoner.declareConcept(explicit);
    if(bearer==null || bearer.isAbstract() || bearer.isGeneric()
        || !(bearer.is(SemanticType.SUBJECT)||bearer.is(SemanticType.AGENT))
        || clauses.clauseCompatibility(quality,SemanticRole.INHERENT,bearer)!=OWLSemanticClauseSupport.Compatibility.COMPATIBLE)return null;
    return new org.integratedmodelling.klab.api.services.reasoner.objects.SemanticValidationResponse.PendingMemberQualityDependency(
        model.getUrn(),index,source.getUrn(),source.getFormalName(),bearer.getUrn(),canonicalQuality);
  }

  private Concept conditionalSubject(KimModel model, Concept output, List<KimObservable> occurrences) {
    // First bounded route: semantic Ratio specialization, canonical unity and no output unit. Missing
    // metadata never means unitless; unitful conditional routes await semantic unit validation.
    var ratio=reasoner.owl().getConcept(org.integratedmodelling.klab.services.reasoner.internal.CoreOntology.NS.CORE_RATIO);
    if (ratio==null || !reasoner.is(output,ratio) || model.getObservables().size()!=1
        || model.getObservables().stream().anyMatch(o -> o.getUnit()!=null && !o.getUnit().isBlank())
        || !org.integratedmodelling.klab.services.reasoner.owl.ConditionalModelContextSupport.explicitlyUnitless(reasoner.owl(),output))
      return null;
    var inputs=new ArrayList<Concept>();
    for(var occurrence:occurrences) {
      if(occurrence.getSemantics()==null)continue;
      var input=reasoner.declareConcept(occurrence.getSemantics());
      if(input==null || input.is(SemanticType.NOTHING) || !reasoner.satisfiable(input))return null;
      inputs.add(input);
    }
    if(executableModels) {
      if(executionContext==null || executionContext.isBlank())return null;
      var actual=reasoner.resolveConcept(executionContext);
      return actual!=null && reasoner.satisfiable(actual)
          && org.integratedmodelling.klab.services.reasoner.owl.ConditionalModelContextSupport.admits(reasoner.owl(),output,inputs,actual)
          ? actual : null;
    }
    var contexts=org.integratedmodelling.klab.services.reasoner.owl.ConditionalModelContextSupport.contexts(reasoner.owl(),output,inputs);
    if(contexts.isEmpty()) {
      for(var container:org.integratedmodelling.klab.services.reasoner.owl.ConditionalModelContextSupport.possibleContainers(reasoner.owl(),output,inputs))
        pendingModelContexts.add(new org.integratedmodelling.klab.api.services.reasoner.objects.SemanticValidationResponse.PendingModelContext(
            model.getUrn(),container.getUrn(),inputs.stream().map(Concept::getUrn).toList()));
      return null; // No invented projection: explicit abstract/generic/identity errors still apply.
    }
    conditionalModelContexts.add(new org.integratedmodelling.klab.api.services.reasoner.objects.SemanticValidationResponse.ConditionalModelContext(
        model.getUrn(),contexts.stream().map(Concept::getUrn).toList()));
    // Every listed alternative proves the complete conjunction. Choosing one for detached
    // expression-status projection asserts nothing about the output/native bearer ontology.
    return contexts.getFirst();
  }

  /** Detached proof skeleton; the original source still receives applicability/disjointness checks. */
  private KimObservable classifierResidual(KimObservable source) {
    if (!(source instanceof org.integratedmodelling.klab.api.lang.kim.impl.KimObservableImpl syntax)) return null;
    try {
      var declarations = reasoner.serviceScope().getService(org.integratedmodelling.klab.api.services.ResourcesService.class);
      if (declarations != null) {
        var collective = CollectiveClassification.prepare(source, declarations::declareConcept);
        if (collective != null && !unboundSyntax(collective.members())) return collective.members();
      }
      if (unboundSyntax(source)) return null;
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
    if (syntax.isPattern()) {semanticObligations.incomplete(); return List.of();}
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
      semanticObligations.add(concept);
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
