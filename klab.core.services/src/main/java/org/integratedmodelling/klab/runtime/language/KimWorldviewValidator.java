package org.integratedmodelling.klab.runtime.language;

import java.util.ArrayList;
import java.util.List;
import org.integratedmodelling.klab.api.knowledge.KlabAsset;
import org.integratedmodelling.klab.api.knowledge.SemanticType;
import org.integratedmodelling.klab.api.lang.kim.*;
import org.integratedmodelling.klab.api.lang.kim.impl.KimConceptImpl;
import org.integratedmodelling.klab.api.services.runtime.Notification;

/** Worldview rules over service semantic beans; no parser or language-project dependencies. */
public class KimWorldviewValidator extends KimValidator implements KimOntologyVisitor.Validator {
  private final java.util.Map<KimOntology,WorldviewDeclarationRegistry> declarationRegistries = new java.util.IdentityHashMap<>();
  private final WorldviewDeclarationRegistry completeRegistry;
  private final java.util.function.Predicate<String> acceptedAuthorityReference;
  public KimWorldviewValidator() { this(null); }
  public KimWorldviewValidator(WorldviewDeclarationRegistry registry) { this(registry, null); }
  /** Authoritative ingestion supplies its source-current validated binding predicate. */
  public KimWorldviewValidator(WorldviewDeclarationRegistry registry,
      java.util.function.Predicate<String> acceptedAuthorityReference) {
    this.completeRegistry=registry; this.acceptedAuthorityReference=acceptedAuthorityReference;
  }
  private WorldviewDeclarationRegistry registry(KimOntology ontology) {
    if(completeRegistry!=null)return completeRegistry;
    return declarationRegistries.computeIfAbsent(ontology,o->new WorldviewDeclarationRegistry(List.of(o)));
  }
  @Override public List<Notification> validateOntology(KimOntology ontology,KimObservableVisitor.Context context) {
    return registry(ontology).errors();
  }

  private boolean authorityReference(String urn, KimObservableVisitor.Context context) {
    if (urn == null || !(context.getDocument() instanceof KimOntology ontology)) return false;
    if (acceptedAuthorityReference != null) return acceptedAuthorityReference.test(urn);
    var parts = urn.split(":", -1);
    if (parts.length != 2 || !parts[1].matches("[A-Z][a-zA-Z0-9_]*")) return false;
    var declarations = registry(ontology).declarations();
    if (java.util.Objects.equals(ontology.getUrn(), parts[0])
        || declarations.stream().anyMatch(d -> java.util.Objects.equals(d.ontology().getUrn(), parts[0]))) return false;
    int owners = 0;
    for (var entry : declarations) {
      var root = entry.statement();
      if (root.getAuthorityRequired() == null || !authorityErrors(root).isEmpty()
          || root.getType().contains(SemanticType.NOTHING)) continue;
      if (root.getAuthorityParameters().get("codelists") instanceof java.util.Map<?, ?> names)
        for (var binding : names.entrySet())
          if (binding.getKey() instanceof String id && !id.isBlank()
              && binding.getValue() instanceof String namespace
              && namespace.matches("[a-z][a-z0-9_]*(\\.[a-z][a-z0-9_]*)*")
              && namespace.equals(parts[0])) owners++;
    }
    return owners == 1; // Source shape only; provider/code approval belongs to the Reasoner.
  }

  @Override public List<Notification> validateConcept(KimConcept concept, KimObservableVisitor.Context context) {
    if (concept != null && concept.is(SemanticType.NOTHING)
        && concept.getSemanticModifier() == null && concept.getObservable() == null
        && authorityReference(concept.getName(), context)) {
      var typed = org.integratedmodelling.common.data.jackson.JacksonConfiguration.newObjectMapper()
          .convertValue(concept, KimConcept.class);
      typed.getType().remove(SemanticType.NOTHING);
      typed.getType().addAll(java.util.EnumSet.of(SemanticType.IDENTITY, SemanticType.PREDICATE));
      return super.validateConcept(typed, context);
    }
    return super.validateConcept(concept, context);
  }
  @Override
  public List<Notification> validateConceptStatement(
      KimConceptStatement statement, KimObservableVisitor.Context context) {
    var result = new ArrayList<Notification>();
    if (statement.getDeclarationClauses().stream().anyMatch(clause -> clause.kind().equals("within"))
        && (statement.getDeclaredInherent() != null
            || statement.getObservationalContainments().stream().noneMatch(pair ->
                pair.contained() != null && java.util.Objects.equals(
                    pair.contained().getUrn(), context.getDocument().getUrn() + ":" + statement.getUrn())))) {
      result.add(Notification.error(
          "LEGACY_WITHIN_INHERENCY: reparse the worldview declaration with a compatible parser; "
              + "within requires a canonical observational containment contract, not native inherency",
          Notification.LexicalContext.of(statement, context.getDocument())));
    }
    if (statement.getDescriptionValue() != null)
      result.add(
          Notification.warning(
              "The describes target is compiled; its as value semantics are not implemented",
              Notification.LexicalContext.of(statement, context.getDocument())));
    if (statement.getAuthorityRequired() != null) {
      var source = statement.getDeclarationClauses().stream()
          .filter(clause -> clause.kind().equals("requiresClause"))
          .findFirst().map(this::clauseSource).orElse(null);
      var lexical = Notification.LexicalContext.of(
          source == null ? statement : source, context.getDocument());
      var errors = authorityErrors(statement);
      for (var message : errors) result.add(Notification.error(message, lexical));
    }
    for (var clause : statement.getDeclarationClauses()) {
      if ((clause.kind().equals("impliesClause") || clause.kind().equals("emergesFromClause"))
              && clause.source().matches("(?s).*\\bwithin\\b.*"))
        result.add(
            Notification.warning(
                "This declaration clause is retained but its scoped/denial semantics are not yet compiled: "
                    + clause.kind(),
                Notification.LexicalContext.of(clauseSource(clause), context.getDocument())));
    }
    if (!statement.isAlias() && statement.getUpperConceptDefined() == null) return result;
    // Programmatically constructed/older beans may not carry source clause spans.
    if (statement.getDeclarationClauses().isEmpty()) {
      if (statement.getDeclaredInherent() != null
          || !statement.getChildren().isEmpty()
          || !statement.getTraitsInherited().isEmpty()
          || !statement.getAppliesTo().isEmpty()
          || !statement.getQualitiesAffected().isEmpty()
          || !statement.getObservablesCreated().isEmpty()
          || !statement.getObservationalContainments().isEmpty()
          || !statement.getSubjectsLinked().isEmpty()
          || !statement.getEmergenceTriggers().isEmpty()
          || !statement.getObservablesDescribed().isEmpty()
          || !statement.getRequiredIdentities().isEmpty()
          || !statement.getRequiredRealms().isEmpty()
          || !statement.getRequiredAttributes().isEmpty()
          || !statement.getRequiredExtents().isEmpty()
          || statement.getAuthorityRequired() != null) {
        result.add(
            Notification.error(
                "An alias cannot have additional semantic clauses",
                Notification.LexicalContext.of(statement, context.getDocument())));
      }
      if (statement.getUpperConceptDefined() != null
          && !atomicCoreTarget(statement.getUpperConceptDefined())) {
        result.add(
            Notification.error(
                "A core alias must name one qualified core concept without modifiers",
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
        result.add(
            Notification.error(
                message, Notification.LexicalContext.of(source, context.getDocument())));
      }
    }
    return result;
  }

  /** Checks shared by source validation and worldview ingestion, including transported beans. */
  public static List<String> authorityErrors(KimConceptStatement statement) {
    if (statement.getAuthorityRequired() == null) return List.of();
    var errors = new ArrayList<String>();
    if (!statement.getType().contains(SemanticType.IDENTITY))
      errors.add("Only an identity concept may require an authority");
    if (statement.isAlias() || statement.getUpperConceptDefined() != null)
      errors.add("An alias cannot require an authority");
    if (!statement.getAuthorityRequired().matches("[A-Z][A-Z0-9_]*(\\.[A-Z][A-Z0-9_]*)*"))
      errors.add("An authority requirement needs an uppercase local name");
    var urn = statement.getAuthorityParameters() == null ? null
        : statement.getAuthorityParameters().get("urn");
    if (!(urn instanceof String reference) || reference.isBlank())
      errors.add("An authority requirement needs a nonblank string urn parameter");
    return errors;
  }

  private boolean atomicCoreTarget(String target) {
    return target != null && target.matches("[a-z][a-zA-Z0-9_.]*:[A-Z][a-zA-Z0-9_]*");
  }

  private KimConcept clauseSource(KimConceptStatement.DeclarationClause clause) {
    var source = new KimConceptImpl();
    source.setOffsetInDocument(clause.offset());
    source.setLength(clause.length());
    return source;
  }

  @Override
  public List<Notification> validateReference(
      KimObservableVisitor.Reference reference, KimObservableVisitor.Context context) {
    if (reference.knowledgeClass() != KlabAsset.KnowledgeClass.CONCEPT
        || !(reference.source() instanceof KimConcept source)
        || !(context.getDocument() instanceof KimOntology ontology)) return List.of();
    // Core declarations bootstrap the core descriptor. The loaded OWL checks its existence.
    for (var node : context.getPath()) {
      if (node instanceof KimConceptStatement statement
          && statement.getUpperConceptDefined() != null) {
        for (var clause : statement.getDeclarationClauses()) {
          if (clause.kind().equals("coreTarget")
              && source.getOffsetInDocument() >= clause.offset()
              && source.getOffsetInDocument() < clause.offset() + clause.length()) return List.of();
        }
      }
    }
    boolean local = reference.urn().startsWith(ontology.getUrn() + ":");
    var declaration = registry(ontology).get(reference.urn());
    // Complete, source-owned typed registration makes semantic reference order irrelevant.
    // Structural parent/alias cycles and unresolved kinds are rejected by the registry.
    boolean known = declaration != null || (!local && reference.resolved() != null);
    if (!local && authorityReference(reference.urn(), context)) known = true;
    if (local && declaration != null && source.getLength()>0 && declaration.statement().getLength()>0
        && declaration.statement().getOffsetInDocument()>source.getOffsetInDocument()) {
      KimConceptStatement enclosing=null;
      for(var node:context.getPath())if(node instanceof KimConceptStatement s)enclosing=s;
      // Staging admits semantic clause reciprocity, not a new preamble/parent/alias
      // ordering policy. The language header overlay preserves these same boundaries.
      if(enclosing==null||parentHead(enclosing.getDeclaredParent(),reference.urn(),ontology.getUrn()))
        return List.of(Notification.error("Concept is referenced before its declaration: "+reference.urn(),
            Notification.LexicalContext.of(source,ontology)));
    }
    // Preserve authority identity handling when no workspace declaration is expected.
    if (!local && !reference.urn().isEmpty() && Character.isUpperCase(reference.urn().charAt(0)))
      known = acceptedAuthorityReference == null || acceptedAuthorityReference.test(reference.urn());
    if (!known)
      return List.of(
          Notification.error(
              "Undefined concept: " + reference.urn(),
              Notification.LexicalContext.of(source, ontology)));
    return List.of();
  }

  private KimConceptStatement find(List<KimConceptStatement> statements, String namespace, String urn) {
    for (var statement : statements) {
      String name = statement.getUrn().contains(":") ? statement.getUrn() : namespace + ":" + statement.getUrn();
      if (name.equals(urn)) return statement;
      var child = find(statement.getChildren(), namespace, urn);
      if (child != null) return child;
    }
    return null;
  }
  private boolean parentHead(KimConcept parent,String urn,String namespace) {
    if(parent==null)return false;
    if(parent.getName()!=null&&WorldviewDeclarationRegistry.urn(namespace,parent.getName()).equals(urn))return true;
    if(parentHead(parent.getObservable(),urn,namespace))return true;
    return parent.getOperands().stream().anyMatch(p->parentHead(p,urn,namespace));
  }
}
