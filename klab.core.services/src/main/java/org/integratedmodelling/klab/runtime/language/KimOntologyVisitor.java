package org.integratedmodelling.klab.runtime.language;

import java.util.List;
import org.integratedmodelling.klab.api.knowledge.KlabAsset;
import org.integratedmodelling.klab.api.lang.kim.*;
import org.integratedmodelling.klab.api.services.runtime.Notification;

/** Traverses and validates a complete {@link KimOntology}. */
public class KimOntologyVisitor extends KimObservableVisitor {

  public interface Validator extends KimObservableVisitor.Validator {
    default List<Notification> validateOntology(KimOntology ontology, Context context) {
      return List.of();
    }

    default List<Notification> validateConceptStatement(
        KimConceptStatement statement, Context context) {
      return List.of();
    }
  }

  public static class LenientValidator extends KimObservableVisitor.LenientValidator
      implements Validator {}

  public static class DefaultValidator extends KimValidator implements Validator {}

  private final Validator ontologyValidator;

  public KimOntologyVisitor() {
    this(new DefaultValidator(), null);
  }

  public KimOntologyVisitor(Validator validator, Resolver resolver) {
    super(validator, resolver);
    this.ontologyValidator = validator == null ? new DefaultValidator() : validator;
  }

  public void visit(KimOntology ontology) {
    var context = beginDocument(ontology);
    addNotifications(ontologyValidator.validateOntology(ontology, context));
    for (var urn : safe(ontology.getImportedOntologies())) {
      reference(urn, KlabAsset.KnowledgeClass.ONTOLOGY, ontology, context);
    }
    for (var vocabularyImport : safe(ontology.getVocabularyImports())) {
      if (vocabularyImport != null) {
        reference(
            vocabularyImport.getFirst(), KlabAsset.KnowledgeClass.RESOURCE, ontology, context);
      }
    }
    visitConcept(ontology.getDomain(), context);
    for (var statement : safe(ontology.getStatements())) visitStatement(statement, context);
  }

  @Override
  protected void visitUnknownStatement(KlabStatement statement, Context context) {
    if (statement instanceof KimConceptStatement conceptStatement) {
      visitConceptStatement(conceptStatement, context);
    }
  }

  private void visitConceptStatement(KimConceptStatement statement, Context context) {
    addNotifications(ontologyValidator.validateConceptStatement(statement, context));
    visitConceptStatementContents(statement, context);
  }
}
