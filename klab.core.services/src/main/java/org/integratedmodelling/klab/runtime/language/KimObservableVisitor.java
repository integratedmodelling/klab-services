package org.integratedmodelling.klab.runtime.language;

import java.util.*;
import org.integratedmodelling.klab.api.collections.Pair;
import org.integratedmodelling.klab.api.collections.Triple;
import org.integratedmodelling.klab.api.knowledge.KlabAsset;
import org.integratedmodelling.klab.api.knowledge.Urn;
import org.integratedmodelling.klab.api.lang.Annotation;
import org.integratedmodelling.klab.api.lang.Contextualizable;
import org.integratedmodelling.klab.api.lang.ServiceCall;
import org.integratedmodelling.klab.api.lang.kim.*;
import org.integratedmodelling.klab.api.services.runtime.Notification;

/**
 * External, configurable traversal for the semantic structures shared by all k.IM documents.
 * Semantic beans remain passive transport objects: recursion, reference resolution and validation
 * live here and in the document visitors derived from this class.
 */
public class KimObservableVisitor {

  /** A reference found while traversing a semantic bean. */
  public record Reference(
      String urn, KlabAsset.KnowledgeClass knowledgeClass, Object source, Object resolved) {}

  /** Resolves URNs without imposing a service or repository dependency on the visitor. */
  @FunctionalInterface
  public interface Resolver {
    Object resolve(String urn, KlabAsset.KnowledgeClass knowledgeClass, Context context);
  }

  /** Validation hooks. Returning an empty list leaves that part of the model unchecked. */
  public interface Validator {
    default List<Notification> validateStatement(KlabStatement statement, Context context) {
      return List.of();
    }

    default List<Notification> validateObservable(KimObservable observable, Context context) {
      return List.of();
    }

    default List<Notification> validateConcept(KimConcept concept, Context context) {
      return List.of();
    }

    default List<Notification> validateServiceCall(ServiceCall call, Context context) {
      return List.of();
    }

    default List<Notification> validateReference(Reference reference, Context context) {
      return List.of();
    }
  }

  /** Syntax-only default. Runtime-aware callers can replace either extension point. */
  public static class LenientValidator implements Validator {}

  /** Lexical path and document ownership at one point in the traversal. */
  public static final class Context {
    private final Context parent;
    private final KlabDocument<?> document;
    private final Object node;

    private Context(Context parent, KlabDocument<?> document, Object node) {
      this.parent = parent;
      this.document = document;
      this.node = node;
    }

    public Context getParent() {
      return parent;
    }

    public KlabDocument<?> getDocument() {
      return document;
    }

    public Object getNode() {
      return node;
    }

    public KlabStatement getStatement() {
      for (Context context = this; context != null; context = context.parent) {
        if (context.node instanceof KlabStatement statement) {
          return statement;
        }
      }
      return null;
    }

    public List<Object> getPath() {
      var path = new ArrayList<Object>();
      for (Context context = this; context != null; context = context.parent) {
        if (context.node != null) {
          path.add(context.node);
        }
      }
      Collections.reverse(path);
      return List.copyOf(path);
    }
  }

  private static final Resolver NO_RESOLVER = (urn, knowledgeClass, context) -> null;

  protected final Validator validator;
  protected final Resolver resolver;
  private final List<Notification> notifications = new ArrayList<>();
  private final List<KlabStatement> statements = new ArrayList<>();
  private final List<KimObservable> observables = new ArrayList<>();
  private final List<KimConcept> concepts = new ArrayList<>();
  private final List<ServiceCall> serviceCalls = new ArrayList<>();
  private final List<Reference> references = new ArrayList<>();
  private final Set<Object> visited = Collections.newSetFromMap(new IdentityHashMap<>());

  public KimObservableVisitor() {
    this(new LenientValidator(), NO_RESOLVER);
  }

  public KimObservableVisitor(Validator validator, Resolver resolver) {
    this.validator = Objects.requireNonNullElseGet(validator, LenientValidator::new);
    this.resolver = Objects.requireNonNullElse(resolver, NO_RESOLVER);
  }

  public void visit(KimObservable observable) {
    reset();
    visitObservable(observable, new Context(null, null, observable));
  }

  public void visit(KimConcept concept) {
    reset();
    visitConcept(concept, new Context(null, null, concept));
  }

  protected final Context beginDocument(KlabDocument<?> document) {
    reset();
    return new Context(null, Objects.requireNonNull(document, "document"), document);
  }

  protected final Context child(Context context, Object node) {
    return new Context(context, context == null ? null : context.document, node);
  }

  protected final boolean enter(Object object) {
    return object != null && visited.add(object);
  }

  protected final void visitStatement(KlabStatement statement, Context context) {
    if (statement == null || !enter(statement)) {
      return;
    }
    var statementContext = context.node == statement ? context : child(context, statement);
    statements.add(statement);
    addNotifications(validator.validateStatement(statement, statementContext));
    for (var annotation : safe(statement.getAnnotations())) {
      visitAnnotation(annotation, child(statementContext, annotation));
    }
    switch (statement) {
      case KimObservable observable -> visitObservableEntered(observable, statementContext);
      case KimConcept concept -> visitConceptEntered(concept, statementContext);
      case KimLookupTable table -> visitLookupTableEntered(table, statementContext);
      case KimTable table -> visitTableEntered(table, statementContext);
      case Contextualizable contextualizable ->
          visitContextualizableEntered(contextualizable, statementContext);
      default -> visitUnknownStatement(statement, statementContext);
    }
  }

  /** Hook used by document visitors for their own statement types. */
  protected void visitUnknownStatement(KlabStatement statement, Context context) {}

  protected final void visitObservable(KimObservable observable, Context context) {
    if (observable == null || !enter(observable)) {
      return;
    }
    var observableContext = context.node == observable ? context : child(context, observable);
    statements.add(observable);
    addNotifications(validator.validateStatement(observable, observableContext));
    for (var annotation : safe(observable.getAnnotations())) {
      visitAnnotation(annotation, child(observableContext, annotation));
    }
    visitObservableEntered(observable, observableContext);
  }

  private void visitObservableEntered(KimObservable observable, Context context) {
    observables.add(observable);
    addNotifications(validator.validateObservable(observable, context));
    visitConcept(observable.getSemantics(), child(context, observable.getSemantics()));
    visitValue(observable.getValue(), context);
    visitValue(observable.getDefaultValue(), context);
    for (var operator : safe(observable.getValueOperators())) {
      visitValue(operator == null ? null : operator.getSecond(), context);
    }
    reference(observable.getModelReference(), KlabAsset.KnowledgeClass.MODEL, observable, context);
  }

  protected final void visitConcept(KimConcept concept, Context context) {
    if (concept == null || !enter(concept)) {
      return;
    }
    var conceptContext = context.node == concept ? context : child(context, concept);
    statements.add(concept);
    addNotifications(validator.validateStatement(concept, conceptContext));
    for (var annotation : safe(concept.getAnnotations())) {
      visitAnnotation(annotation, child(conceptContext, annotation));
    }
    visitConceptEntered(concept, conceptContext);
  }

  private void visitConceptEntered(KimConcept concept, Context conceptContext) {
    concepts.add(concept);
    addNotifications(validator.validateConcept(concept, conceptContext));
    reference(concept.getName(), KlabAsset.KnowledgeClass.CONCEPT, concept, conceptContext);
    visitConcept(concept.getObservable(), conceptContext);
    visitConcept(concept.getParent(), conceptContext);
    visitConcept(concept.getInherent(), conceptContext);
    visitConcept(concept.getGoal(), conceptContext);
    visitConcept(concept.getCausant(), conceptContext);
    visitConcept(concept.getCaused(), conceptContext);
    visitConcept(concept.getCompresent(), conceptContext);
    visitConcept(concept.getCooccurrent(), conceptContext);
    visitConcept(concept.getAdjacent(), conceptContext);
    visitConcept(concept.getComparisonConcept(), conceptContext);
    visitConcept(concept.getRelationshipSource(), conceptContext);
    visitConcept(concept.getRelationshipTarget(), conceptContext);
    for (var trait : safe(concept.getTraits())) visitConcept(trait, conceptContext);
    for (var role : safe(concept.getRoles())) visitConcept(role, conceptContext);
    for (var operand : safe(concept.getOperands())) visitConcept(operand, conceptContext);
    for (var modifier : safe(concept.getModifiers())) {
      visitConcept(modifier == null ? null : modifier.getSecond(), conceptContext);
    }
    for (var operator : safe(concept.getValueOperators())) {
      visitValue(operator == null ? null : operator.getSecond(), conceptContext);
    }
    var operation = concept.semanticOperation();
    if (operation != null) {
      visitConcept(operation.getSecond(), conceptContext);
      visitConcept(operation.getThird(), conceptContext);
    }
  }

  protected final void visitContextualizable(Contextualizable contextualizable, Context context) {
    if (contextualizable == null || !enter(contextualizable)) {
      return;
    }
    var contextualizableContext =
        context.node == contextualizable ? context : child(context, contextualizable);
    statements.add(contextualizable);
    addNotifications(validator.validateStatement(contextualizable, contextualizableContext));
    for (var annotation : safe(contextualizable.getAnnotations())) {
      visitAnnotation(annotation, child(contextualizableContext, annotation));
    }
    visitContextualizableEntered(contextualizable, contextualizableContext);
  }

  private void visitContextualizableEntered(Contextualizable contextualizable, Context context) {
    visitObservable(contextualizable.getTarget(), context);
    visitServiceCall(contextualizable.getServiceCall(), context);
    visitClassification(contextualizable.getClassification(), context);
    visitLookupTable(contextualizable.getLookupTable(), context);
    visitContextualizable(contextualizable.getCondition(), context);
    visitValue(contextualizable.getLiteral(), context);
    for (Urn urn : safe(contextualizable.getResourceUrns())) {
      reference(
          urn == null ? null : urn.toString(),
          KlabAsset.KnowledgeClass.RESOURCE,
          contextualizable,
          context);
    }
  }

  protected final void visitServiceCall(ServiceCall call, Context context) {
    if (call == null || !enter(call)) {
      return;
    }
    var callContext = child(context, call);
    serviceCalls.add(call);
    addNotifications(validator.validateServiceCall(call, callContext));
    reference(call.getUrn(), KlabAsset.KnowledgeClass.SERVICE_IMPLEMENTATION, call, callContext);
    if (call.getParameters() != null) {
      call.getParameters().values().forEach(value -> visitValue(value, callContext));
    }
  }

  protected final void visitClassification(KimClassification classification, Context context) {
    if (classification == null || !enter(classification)) return;
    for (var entry : safe(classification.getClassifiers())) {
      if (entry != null) {
        visitConcept(entry.getFirst(), context);
        visitClassifier(entry.getSecond(), context);
      }
    }
  }

  protected final void visitClassifier(KimClassifier classifier, Context context) {
    if (classifier == null || !enter(classifier)) return;
    visitConcept(classifier.getConceptMatch(), context);
    for (var concept : safe(classifier.getConceptMatches())) visitConcept(concept, context);
    for (var nested : safe(classifier.getClassifierMatches())) visitClassifier(nested, context);
  }

  protected final void visitLookupTable(KimLookupTable table, Context context) {
    if (table == null || !enter(table)) return;
    var tableContext = context.node == table ? context : child(context, table);
    statements.add(table);
    addNotifications(validator.validateStatement(table, tableContext));
    for (var annotation : safe(table.getAnnotations())) {
      visitAnnotation(annotation, child(tableContext, annotation));
    }
    visitLookupTableEntered(table, tableContext);
  }

  private void visitLookupTableEntered(KimLookupTable table, Context context) {
    for (var argument : safe(table.getArguments())) {
      if (argument != null) visitConcept(argument.concept, context);
    }
    visitTable(table.getTable(), context);
    for (var classifier : safe(table.getRowClassifiers())) visitClassifier(classifier, context);
    for (var classifier : safe(table.getColumnClassifiers())) visitClassifier(classifier, context);
  }

  protected final void visitTable(KimTable table, Context context) {
    if (table == null || !enter(table)) return;
    var tableContext = context.node == table ? context : child(context, table);
    statements.add(table);
    addNotifications(validator.validateStatement(table, tableContext));
    for (var annotation : safe(table.getAnnotations())) {
      visitAnnotation(annotation, child(tableContext, annotation));
    }
    visitTableEntered(table, tableContext);
  }

  private void visitTableEntered(KimTable table, Context context) {
    for (var classifier : safe(table.getRowClassifiers())) visitClassifier(classifier, context);
    for (var classifier : safe(table.getColumnClassifiers())) visitClassifier(classifier, context);
    for (var row : safe(table.rows())) {
      if (row != null) for (var classifier : row) visitClassifier(classifier, context);
    }
  }

  /** Traverses the semantic content common to concept declarations in ontologies and namespaces. */
  protected final void visitConceptStatementContents(
      KimConceptStatement statement, Context context) {
    reference(
        statement.getUpperConceptDefined(), KlabAsset.KnowledgeClass.CONCEPT, statement, context);
    visitConcept(statement.getDeclaredParent(), context);
    visitConcept(statement.getDeclaredInherent(), context);
    for (var concept : safe(statement.getQualitiesAffected())) visitConcept(concept, context);
    for (var concept : safe(statement.getObservablesCreated())) visitConcept(concept, context);
    for (var concept : safe(statement.getTraitsConferred())) visitConcept(concept, context);
    for (var concept : safe(statement.getTraitsInherited())) visitConcept(concept, context);
    for (var concept : safe(statement.getRequiredExtents())) visitConcept(concept, context);
    for (var concept : safe(statement.getRequiredRealms())) visitConcept(concept, context);
    for (var concept : safe(statement.getRequiredAttributes())) visitConcept(concept, context);
    for (var concept : safe(statement.getRequiredIdentities())) visitConcept(concept, context);
    for (var concept : safe(statement.getEmergenceTriggers())) visitConcept(concept, context);
    for (var description : safe(statement.getObservablesDescribed())) {
      if (description != null) visitConcept(description.getFirst(), context);
    }
    for (var applicable : safe(statement.getSubjectsLinked())) {
      visitApplicableConcept(applicable, context);
    }
    for (var applicable : safe(statement.getAppliesTo())) {
      visitApplicableConcept(applicable, context);
    }
    for (var child : safe(statement.getChildren())) visitStatement(child, context);
  }

  private void visitApplicableConcept(
      KimConceptStatement.ApplicableConcept applicable, Context context) {
    if (applicable == null) return;
    visitConcept(applicable.getOriginalObservable(), context);
    visitConcept(applicable.getSource(), context);
    visitConcept(applicable.getTarget(), context);
  }

  protected final void visitValue(Object value, Context context) {
    if (value == null) return;
    switch (value) {
      case KimObservable observable -> visitObservable(observable, context);
      case KimConcept concept -> visitConcept(concept, context);
      case Contextualizable contextualizable -> visitContextualizable(contextualizable, context);
      case ServiceCall call -> visitServiceCall(call, context);
      case KimClassification classification -> visitClassification(classification, context);
      case KimLookupTable table -> visitLookupTable(table, context);
      case KimTable table -> visitTable(table, context);
      case KimClassifier classifier -> visitClassifier(classifier, context);
      case Pair<?, ?> pair -> {
        visitValue(pair.getFirst(), context);
        visitValue(pair.getSecond(), context);
      }
      case Triple<?, ?, ?> triple -> {
        visitValue(triple.getFirst(), context);
        visitValue(triple.getSecond(), context);
        visitValue(triple.getThird(), context);
      }
      case Map<?, ?> map ->
          map.forEach(
              (key, item) -> {
                visitValue(key, context);
                visitValue(item, context);
              });
      case Iterable<?> iterable -> iterable.forEach(item -> visitValue(item, context));
      case Object[] array -> Arrays.stream(array).forEach(item -> visitValue(item, context));
      default -> {}
    }
  }

  private void visitAnnotation(Annotation annotation, Context context) {
    if (annotation != null && enter(annotation)) {
      annotation.values().forEach(value -> visitValue(value, context));
    }
  }

  protected final void reference(
      String urn, KlabAsset.KnowledgeClass knowledgeClass, Object source, Context context) {
    if (urn == null || urn.isBlank()) return;
    var resolved = resolver.resolve(urn, knowledgeClass, context);
    var reference = new Reference(urn, knowledgeClass, source, resolved);
    references.add(reference);
    addNotifications(validator.validateReference(reference, context));
  }

  protected final void addNotifications(Collection<Notification> diagnostics) {
    if (diagnostics != null)
      diagnostics.stream().filter(Objects::nonNull).forEach(notifications::add);
  }

  protected final void reset() {
    notifications.clear();
    statements.clear();
    observables.clear();
    concepts.clear();
    serviceCalls.clear();
    references.clear();
    visited.clear();
  }

  protected static <T> Collection<T> safe(Collection<T> collection) {
    return collection == null ? List.of() : collection;
  }

  public List<Notification> getNotifications() {
    return List.copyOf(notifications);
  }

  public List<KlabStatement> getStatements() {
    return List.copyOf(statements);
  }

  public List<KimObservable> getObservables() {
    return List.copyOf(observables);
  }

  public List<KimConcept> getConcepts() {
    return List.copyOf(concepts);
  }

  public List<ServiceCall> getServiceCalls() {
    return List.copyOf(serviceCalls);
  }

  public List<Reference> getReferences() {
    return List.copyOf(references);
  }
}
