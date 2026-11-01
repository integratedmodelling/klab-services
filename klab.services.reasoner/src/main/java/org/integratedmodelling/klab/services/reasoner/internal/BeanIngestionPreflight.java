package org.integratedmodelling.klab.services.reasoner.internal;

import java.util.*;
import org.integratedmodelling.common.data.jackson.JacksonConfiguration;
import org.integratedmodelling.klab.api.knowledge.Worldview;
import org.integratedmodelling.klab.api.knowledge.SemanticType;
import org.integratedmodelling.klab.api.data.Version;
import org.integratedmodelling.klab.api.lang.kim.*;
import org.integratedmodelling.klab.api.lang.kim.impl.*;
import org.integratedmodelling.klab.api.services.runtime.Notification;
import org.integratedmodelling.klab.api.lang.Statement;
import org.integratedmodelling.klab.api.services.reasoner.objects.SemanticValidationRequest;
import org.integratedmodelling.klab.runtime.language.*;

/** Uses the Resources bean validators and authoritative source beans, never loaded OWL. */
public final class BeanIngestionPreflight {
  private BeanIngestionPreflight() {}

  public static Worldview snapshot(Worldview worldview) {
    return FrozenWorldviewSnapshot.copyOf(worldview);
  }

  public static KimOntology snapshot(KimOntology ontology) {
    return JacksonConfiguration.newObjectMapper().convertValue(ontology, KimOntology.class);
  }

  public static KimObservationStrategyDocument snapshot(KimObservationStrategyDocument document) {
    return JacksonConfiguration.newObjectMapper().convertValue(document, KimObservationStrategyDocument.class);
  }

  public static List<Notification> errors(Worldview worldview) {
    var documents = new ArrayList<KlabDocument<?>>();
    documents.addAll(worldview.getOntologies());
    documents.addAll(worldview.getObservationStrategies());
    var errors = new ArrayList<>(errors(worldview.getNotifications()));
    errors.addAll(errors(documents, List.of(worldview)));
    return errors;
  }

  /** Changed beans overlay the source snapshot, so saved replacements resolve each other. */
  public static List<Notification> errors(
      Collection<? extends KlabDocument<?>> documents, Collection<? extends Worldview> snapshots) {
    var ontologies = new LinkedHashMap<String, KimOntology>();
    var strategyDocuments = new LinkedHashMap<String, KimObservationStrategyDocument>();
    for (var snapshot : snapshots) {
      for (var ontology : snapshot.getOntologies()) ontologies.put(ontology.getUrn(), ontology);
      for (var strategy : snapshot.getObservationStrategies()) strategyDocuments.put(strategy.getUrn(), strategy);
    }
    for (var document : documents) {
      if (document instanceof KimOntology ontology) ontologies.put(ontology.getUrn(), ontology);
      else if (document instanceof KimObservationStrategyDocument strategy) strategyDocuments.put(strategy.getUrn(), strategy);
    }
    var registry = new WorldviewDeclarationRegistry(ontologies.values());
    var concepts = new HashMap<String, KimConceptStatement>();
    var owners = new IdentityHashMap<KimConceptStatement, String>();
    for (var ontology : ontologies.values())
      for (var statement : ontology.getStatements()) index(concepts, owners, ontology.getUrn(), statement);
    KimObservableVisitor.Resolver resolver = (urn, type, context) -> switch (type) {
      case ONTOLOGY -> ontologies.get(urn);
      case CONCEPT, CONCEPT_STATEMENT -> {
        var resolved = concepts.get(urn);
        if (resolved == null && context.getDocument() instanceof KimOntology current)
          resolved = concepts.get(current.getUrn() + ":" + urn);
        yield resolved;
      }
      default -> null;
    };
    var validating = new ArrayList<KlabDocument<?>>(documents);
    var included = new HashSet<String>();
    var includedStrategies = new HashSet<String>();
    for (var document : documents)
      if (document instanceof KimOntology) included.add(document.getUrn());
      else if (document instanceof KimObservationStrategyDocument) includedStrategies.add(document.getUrn());
    var result = new ArrayList<Notification>(registry.errors());
    // Follow the requested imports and semantic references. Unknown dependencies never become
    // valid merely because a strategy's default visitor does not check reference existence.
    for (int i = 0; i < validating.size(); i++) {
      var document = validating.get(i);
      result.addAll(errors(document.getNotifications()));
      KimObservableVisitor visitor;
      if (document instanceof KimOntology ontology) {
        for (var imported : ontology.getImportedOntologies()) {
          if (!ontologies.containsKey(imported))
            result.add(Notification.error("Authoritative imported ontology is unavailable: " + imported,
                Notification.LexicalContext.of(new KimConceptImpl(), ontology)));
          else include(imported, ontologies, validating, included);
        }
        var ontologyVisitor = new KimOntologyVisitor(new KimWorldviewValidator(registry), resolver);
        ontologyVisitor.visit(ontology);
        visitor = ontologyVisitor;
      } else if (document instanceof KimObservationStrategyDocument strategies) {
        for (var imported : strategies.getImports()) {
          if (!strategyDocuments.containsKey(imported)) result.add(Notification.error(
              "Authoritative strategy import is unavailable: " + imported));
          else if (includedStrategies.add(imported)) validating.add(strategyDocuments.get(imported));
        }
        var strategyVisitor = new KimObservationStrategyDocumentVisitor(null, resolver);
        strategyVisitor.visit(strategies);
        visitor = strategyVisitor;
        if (strategies.getSource() != null) result.addAll(errors(strategies.getSource().getNotifications()));
        for (var strategy : strategies.getStatements()) {
          if (strategy.getSource() != null) result.addAll(errors(strategy.getSource().getNotifications()));
          var visited = Collections.newSetFromMap(new IdentityHashMap<KimObservationPlan.Node, Boolean>());
          planErrors(strategy.getSelection(), visited, result);
          strategy.getSetup().forEach(node -> planErrors(node, visited, result));
          planErrors(strategy.getPlan(), visited, result);
        }
      } else continue;
      result.addAll(errors(visitor.getNotifications()));
      for (var statement : visitor.getStatements()) {
        result.addAll(errors(statement.getNotifications()));
        if (document instanceof KimOntology ontology && statement instanceof KimConceptStatement declaration
            && declaration.getAuthorityRequired() != null) {
          result.addAll(authorityErrors(ontology, declaration, snapshots, concepts, owners, ontologies));
          var boundaries = declaration.getAuthorityParameters().get("semanticBoundaries");
          if (boundaries instanceof Map<?, ?> map)
            for (var target : map.values()) {
              var boundary = concepts.get(target);
              if (boundary != null) include(owners.get(boundary), ontologies, validating, included);
              if (boundary == null || !boundary.getType().contains(SemanticType.IDENTITY)
                  || boundary.getType().contains(SemanticType.NOTHING))
                result.add(Notification.error("Semantic boundary must name a valid worldview identity: " + target,
                    Notification.LexicalContext.of(declaration, ontology)));
            }
        }
      }
      for (var reference : visitor.getReferences()) {
        switch (reference.knowledgeClass()) {
          case CONCEPT, CONCEPT_STATEMENT -> {
            if (reference.resolved() instanceof KimConceptStatement declaration)
              include(owners.get(declaration), ontologies, validating, included);
            else if (document instanceof KimObservationStrategyDocument) {
              var roots = concepts.values().stream().filter(d -> authorityReference(d, reference.urn())).toList();
              if (!roots.isEmpty())
                roots.forEach(root -> include(owners.get(root), ontologies, validating, included));
              else result.add(Notification.error("Undefined strategy concept: " + reference.urn(),
                  Notification.LexicalContext.of(reference.source() instanceof Statement source
                      ? source : new KimConceptImpl(), document)));
            }
          }
          default -> { /* Keep the default validator contracts for non-worldview references. */ }
        }
      }
    }
    return result;
  }

  private static void include(String namespace, Map<String, KimOntology> ontologies,
      List<KlabDocument<?>> validating, Set<String> included) {
    var dependency = ontologies.get(namespace);
    if (dependency != null && included.add(namespace)) validating.add(dependency);
  }

  private static void planErrors(KimObservationPlan.Node node, Set<KimObservationPlan.Node> visited,
      List<Notification> errors) {
    if (node == null || !visited.add(node)) return;
    if (node.getSource() != null) errors.addAll(errors(node.getSource().getNotifications()));
    for (var child : node.children()) planErrors(child, visited, errors);
  }

  private static List<Notification> authorityErrors(KimOntology ontology,
      KimConceptStatement declaration, Collection<? extends Worldview> snapshots,
      Map<String, KimConceptStatement> concepts, Map<KimConceptStatement, String> owners,
      Map<String, KimOntology> ontologies) {
    var name = declaration.getAuthorityRequired();
    var root = declaration.getUrn().contains(":") ? declaration.getUrn()
        : ontology.getUrn() + ":" + declaration.getUrn();
    var hash = SemanticValidationRequest.sourceHash(ontology.getSourceCode());
    var requested = declaration.getAuthorityParameters().get("urn");
    var coordinates = providerCoordinates(requested);
    boolean validated = hash != null && snapshots.stream().flatMap(s -> s.getAuthorityBindings().stream())
        .anyMatch(b -> name.equals(b.localId()) && root.equals(b.rootIdentity())
            && ontology.getUrn().equals(b.sourceOntology()) && hash.equals(b.sourceHash())
            && b.provider() != null && coordinates != null
            && coordinates.getFirst().equals(b.provider().urn())
            && (!((String) requested).contains("@") || coordinates.getSecond().equals(b.provider().version())));
    if (validated) return List.of(); // A current accepted binding supersedes old aggregate messages.
    var current = snapshots.stream()
        .filter(s -> s.getOntologies().stream().anyMatch(o -> ontology.getUrn().equals(o.getUrn())
            && Objects.equals(hash, SemanticValidationRequest.sourceHash(o.getSourceCode()))))
        .flatMap(s -> errors(s.getNotifications()).stream().filter(n -> concepts.entrySet().stream()
            .anyMatch(e -> name.equals(e.getValue().getAuthorityRequired())
                && n.getMessage().startsWith("Authority " + name + " at " + e.getKey() + ": ")
                && s.getOntologies().stream().anyMatch(o -> o.getUrn().equals(owners.get(e.getValue()))
                    && Objects.equals(SemanticValidationRequest.sourceHash(o.getSourceCode()),
                        SemanticValidationRequest.sourceHash(ontologies.get(o.getUrn()).getSourceCode()))))))
        .toList();
    if (!current.isEmpty()) return current;
    return List.of(Notification.error("Authoritative authority validation is unavailable for " + name
        + " at " + root, Notification.LexicalContext.of(declaration, ontology)));
  }

  private static boolean authorityReference(KimConceptStatement declaration, String urn) {
    if (declaration.getAuthorityRequired() == null) return false;
    if (urn.startsWith(declaration.getAuthorityRequired() + ":")) return true;
    var codelists = declaration.getAuthorityParameters().get("codelists");
    return codelists instanceof Map<?, ?> map && map.values().stream()
        .anyMatch(namespace -> namespace instanceof String value && urn.startsWith(value + ":"));
  }

  private static org.integratedmodelling.klab.api.collections.Pair<String, Version> providerCoordinates(Object urn) {
    try { return urn instanceof String value ? Version.splitVersion(value) : null; }
    catch (RuntimeException invalid) { return null; }
  }

  private static void index(Map<String, KimConceptStatement> concepts,
      Map<KimConceptStatement, String> owners, String namespace,
      KimConceptStatement statement) {
    var urn = statement.getUrn();
    if (urn != null && !urn.isBlank()) concepts.putIfAbsent(urn.contains(":") ? urn : namespace + ":" + urn, statement);
    owners.put(statement, namespace);
    for (var child : statement.getChildren()) index(concepts, owners, namespace, child);
  }

  public static List<Notification> errors(Collection<Notification> notifications) {
    return notifications == null ? List.of() : notifications.stream()
        .filter(Objects::nonNull)
        .filter(n -> n.getLevel() == Notification.Level.Error || n.getLevel() == Notification.Level.SystemError)
        .toList();
  }
}
