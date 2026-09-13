package org.integratedmodelling.klab.runtime.language;

import java.util.ArrayList;
import java.util.List;
import org.integratedmodelling.klab.api.knowledge.KlabAsset;
import org.integratedmodelling.klab.api.lang.kim.*;
import org.integratedmodelling.klab.api.lang.kim.impl.KimConceptImpl;
import org.integratedmodelling.klab.api.services.runtime.Notification;

/** Worldview rules over service semantic beans; no parser or language-project dependencies. */
public class KimWorldviewValidator extends KimValidator implements KimOntologyVisitor.Validator {
  @Override
  public List<Notification> validateConceptStatement(KimConceptStatement statement,
      KimObservableVisitor.Context context) {
    var result = new ArrayList<Notification>();
    if (!statement.isAlias() && statement.getUpperConceptDefined() == null) return result;
    // Programmatically constructed/older beans may not carry source clause spans.
    if (statement.getDeclarationClauses().isEmpty()) {
      if (statement.getDeclaredInherent() != null || !statement.getChildren().isEmpty()
          || !statement.getTraitsInherited().isEmpty() || !statement.getAppliesTo().isEmpty()
          || !statement.getQualitiesAffected().isEmpty() || !statement.getObservablesCreated().isEmpty()
          || !statement.getSubjectsLinked().isEmpty() || !statement.getEmergenceTriggers().isEmpty()
          || !statement.getObservablesDescribed().isEmpty() || !statement.getRequiredIdentities().isEmpty()
          || !statement.getRequiredRealms().isEmpty() || !statement.getRequiredAttributes().isEmpty()
          || !statement.getRequiredExtents().isEmpty() || statement.getAuthorityRequired() != null) {
        result.add(Notification.error("An alias cannot have additional semantic clauses",
            Notification.LexicalContext.of(statement, context.getDocument())));
      }
      if (statement.getUpperConceptDefined() != null
          && !atomicCoreTarget(statement.getUpperConceptDefined())) {
        result.add(Notification.error("A core alias must name one qualified core concept without modifiers",
            Notification.LexicalContext.of(statement, context.getDocument())));
      }
    }
    for (var clause : statement.getDeclarationClauses()) {
      String message = null;
      if (clause.kind().equals("coreTarget")) {
        if (!atomicCoreTarget(statement.getUpperConceptDefined()))
          message = "A core alias must name one qualified core concept without modifiers";
      } else if (clause.kind().equals("within")) {
        message = "An alias cannot be specialized with within";
      } else if (!clause.kind().equals("isClause")) {
        message = "An alias cannot have additional semantic clauses";
      }
      if (message != null) {
        var source = new KimConceptImpl();
        source.setOffsetInDocument(clause.offset());
        source.setLength(clause.length());
        result.add(Notification.error(message, Notification.LexicalContext.of(source, context.getDocument())));
      }
    }
    return result;
  }

  private boolean atomicCoreTarget(String target) {
    return target != null && target.matches("[a-z][a-zA-Z0-9_.]*:[A-Z][a-zA-Z0-9_]*");
  }

  @Override
  public List<Notification> validateReference(KimObservableVisitor.Reference reference,
      KimObservableVisitor.Context context) {
    if (reference.knowledgeClass() != KlabAsset.KnowledgeClass.CONCEPT
        || !(reference.source() instanceof KimConcept source)
        || !(context.getDocument() instanceof KimOntology ontology)) return List.of();
    // Core declarations bootstrap the core descriptor. The loaded OWL checks its existence.
    for (var node : context.getPath()) {
      if (node instanceof KimConceptStatement statement && statement.getUpperConceptDefined() != null) {
        for (var clause : statement.getDeclarationClauses()) {
          if (clause.kind().equals("coreTarget") && source.getOffsetInDocument() >= clause.offset()
              && source.getOffsetInDocument() < clause.offset() + clause.length()) return List.of();
        }
      }
    }
    boolean local = reference.urn().startsWith(ontology.getUrn() + ":");
    boolean known = local ? contains(ontology.getStatements(), ontology.getUrn(), reference.urn())
        : reference.resolved() != null;
    // Preserve authority identity handling when no workspace declaration is expected.
    if (!local && !reference.urn().isEmpty() && Character.isUpperCase(reference.urn().charAt(0))) known = true;
    if (!known) return List.of(Notification.error("Undefined concept: " + reference.urn(),
        Notification.LexicalContext.of(source, ontology)));
    return List.of();
  }

  private boolean contains(List<KimConceptStatement> statements, String namespace, String urn) {
    for (var statement : statements) {
      if ((namespace + ":" + statement.getUrn()).equals(urn)) return true;
      if (contains(statement.getChildren(), namespace, urn)) return true;
    }
    return false;
  }
}
