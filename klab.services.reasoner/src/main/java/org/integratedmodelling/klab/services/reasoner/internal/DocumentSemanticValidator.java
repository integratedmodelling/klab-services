package org.integratedmodelling.klab.services.reasoner.internal;

import java.util.ArrayList;
import java.util.List;
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
    implements KimNamespaceVisitor.Validator, KimOntologyVisitor.Validator {
  private final ReasonerService reasoner;
  private final OWLSemanticClauseSupport clauses;

  public DocumentSemanticValidator(ReasonerService reasoner) {
    this.reasoner = reasoner;
    this.clauses = new OWLSemanticClauseSupport(reasoner.owl());
  }

  public List<Notification> validate(KlabDocument<?> document) {
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
  public List<Notification> validateReference(
      KimObservableVisitor.Reference reference, KimObservableVisitor.Context context) {
    if (context.getDocument() instanceof KimOntology ontology
        && reference.urn().startsWith(ontology.getUrn() + ":")) {
      // The loaded OWL may contain a later (or deleted) declaration from the previous save.
      return new KimWorldviewValidator().validateReference(reference, context);
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
        else if (syntax.getSemanticModifier() == null
            && !clauses.accepts(head, modifier.getFirst(), operand))
          result.add(
              error(
                  "Clause "
                      + modifier.getFirst()
                      + " operand "
                      + operand.getUrn()
                      + " is outside the inherited restrictions or applies to domain of "
                      + head.getUrn(),
                  modifier.getSecond(),
                  context));
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
    } catch (org.integratedmodelling.klab.api.exceptions.KlabValidationException e) {
      result.add(error(e.getMessage(), syntax, context));
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
    collect(reasoner.resolveConcept(urn), statement, context, result);
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
        result.add(
            Notification.create(
                notification.getLevel(),
                notification.getMessage(),
                Notification.LexicalContext.of(source, context.getDocument())));
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
      String message, Statement source, KimObservableVisitor.Context context) {
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
