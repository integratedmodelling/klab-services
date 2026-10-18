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
import org.integratedmodelling.klab.api.services.reasoner.objects.AuthorityIdentitySyntax;
import org.integratedmodelling.klab.runtime.language.*;

/** Uses the Resources bean validators and authoritative source beans, never loaded OWL. */
public final class BeanIngestionPreflight {
  private BeanIngestionPreflight() {}

  public static Worldview snapshot(Worldview worldview) {
    var owned = FrozenWorldviewSnapshot.copyOf(worldview);
    var namespaces = authorityNamespaces(owned.getOntologies(), List.of(owned));
    owned.getOntologies().forEach(o -> typeAuthorityReferences(o, namespaces));
    owned.getObservationStrategies().forEach(o -> typeAuthorityReferences(o, namespaces));
    return owned;
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
    record Occurrence(Notification.Level level, String message, String document, String project, int offset, int length) {}
    var unique = new LinkedHashMap<Occurrence, Notification>();
    for (var error : errors) {
      var context = error.getLexicalContext();
      var key = new Occurrence(error.getLevel(), error.getMessage(),
          context == null ? null : context.getDocumentUrn(), context == null ? null : context.getProjectUrn(),
          context == null ? -1 : context.getOffsetInDocument(), context == null ? -1 : context.getLength());
      unique.putIfAbsent(key, error);
    }
    return List.copyOf(unique.values());
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
    // Refine private validation beans only. A declaration grants a provisional identity kind,
    // never existence of a provider code; the configured Reasoner must still resolve that code.
    var namespaces = authorityNamespaces(ontologies.values(), snapshots);
    ontologies.replaceAll((name, ontology) -> snapshot(ontology));
    strategyDocuments.replaceAll((name, strategy) -> snapshot(strategy));
    ontologies.values().forEach(o -> typeAuthorityReferences(o, namespaces));
    strategyDocuments.values().forEach(o -> typeAuthorityReferences(o, namespaces));
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
        if (resolved == null) {
          var generated = registry.get(urn);
          if (generated == null && context.getDocument() instanceof KimOntology current)
            generated = registry.get(current.getUrn() + ":" + urn);
          if (generated != null) resolved = generated.statement();
        }
        yield resolved != null ? resolved : authorityOwner(urn, namespaces);
      }
      default -> null;
    };
    var validating = new ArrayList<KlabDocument<?>>();
    for (var document : documents) validating.add(document instanceof KimOntology
        ? ontologies.get(document.getUrn()) : document instanceof KimObservationStrategyDocument
            ? strategyDocuments.get(document.getUrn()) : document);
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
        var ontologyVisitor = new KimOntologyVisitor(new KimWorldviewValidator(registry,
            urn -> authorityOwner(urn, namespaces) != null), resolver);
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
            else if (reference.resolved() instanceof AuthorityNamespace authority)
              include(authority.ontology(), ontologies, validating, included);
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
    boolean validated = validatedAuthority(ontology, declaration, snapshots);
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

  private static boolean validatedAuthority(KimOntology ontology, KimConceptStatement declaration,
      Collection<? extends Worldview> snapshots) {
    var name = declaration.getAuthorityRequired();
    var root = declaration.getUrn().contains(":") ? declaration.getUrn()
        : ontology.getUrn() + ":" + declaration.getUrn();
    var hash = SemanticValidationRequest.sourceHash(ontology.getSourceCode());
    var requested = declaration.getAuthorityParameters().get("urn");
    var coordinates = providerCoordinates(requested);
    return hash != null && snapshots.stream().flatMap(s -> s.getAuthorityBindings().stream())
        .anyMatch(b -> name.equals(b.localId()) && root.equals(b.rootIdentity())
            && ontology.getUrn().equals(b.sourceOntology()) && hash.equals(b.sourceHash())
            && b.provider() != null && coordinates != null
            && coordinates.getFirst().equals(b.provider().urn())
            && (!((String) requested).contains("@") || coordinates.getSecond().equals(b.provider().version())));
  }

  private record AuthorityNamespace(String ontology) {}

  private static Map<String, AuthorityNamespace> authorityNamespaces(
      Collection<KimOntology> ontologies, Collection<? extends Worldview> snapshots) {
    var result = new HashMap<String, AuthorityNamespace>();
    var occupied = new HashSet<String>();
    ontologies.forEach(o -> occupied.add(o.getUrn()));
    var declarations = new HashMap<String, KimConceptStatement>();
    var owners = new IdentityHashMap<KimConceptStatement, String>();
    var byName = new HashMap<String, KimOntology>();
    for (var ontology : ontologies) {
      byName.put(ontology.getUrn(), ontology);
      ontology.getStatements().forEach(s -> index(declarations, owners, ontology.getUrn(), s));
    }
    for (var declaration : declarations.values()) {
      if (declaration.getAuthorityRequired() == null) continue;
      var ontology = byName.get(owners.get(declaration));
      if (!KimWorldviewValidator.authorityErrors(declaration).isEmpty()
          || declaration.getType().contains(SemanticType.NOTHING)
          || !errors(declaration.getNotifications()).isEmpty()
          || !validatedAuthority(ontology, declaration, snapshots)) continue;
      var authority = declaration.getAuthorityRequired();
      if (occupied.add(authority)) result.put(authority, new AuthorityNamespace(ontology.getUrn()));
      else result.remove(authority);
      if (declaration.getAuthorityParameters().get("codelists") instanceof Map<?, ?> names)
        for (var entry : names.entrySet()) {
          if (!(entry.getKey() instanceof String id) || id.isBlank()
              || !(entry.getValue() instanceof String name)
              || !name.matches("[a-z][a-z0-9_]*(\\.[a-z][a-z0-9_]*)*")) continue;
          if (occupied.add(name)) result.put(name, new AuthorityNamespace(ontology.getUrn()));
          else result.remove(name); // No placeholder may shadow an ontology or another codelist.
        }
    }
    return result;
  }

  private static AuthorityNamespace authorityOwner(String urn, Map<String, AuthorityNamespace> namespaces) {
    if (urn == null) return null;
    var colon = urn.indexOf(':');
    if (colon <= 0) return null;
    var namespace = urn.substring(0, colon);
    var code = urn.substring(colon + 1);
    if (Character.isUpperCase(namespace.charAt(0))) {
      try {
        AuthorityIdentitySyntax.validateAuthority(namespace);
        if (!code.startsWith("[") && !code.matches("[a-z][a-z0-9_]*|[A-Z][A-Z0-9_]+|[0-9]+")) return null;
        var decoded = AuthorityIdentitySyntax.decode(code);
        AuthorityIdentitySyntax.encode(namespace, decoded);
      } catch (IllegalArgumentException invalid) { return null; }
      return namespaces.get(namespace);
    }
    return code.matches("[A-Z][a-zA-Z0-9_]*") ? namespaces.get(namespace) : null;
  }

  private static void typeAuthorityReferences(KlabDocument<?> document,
      Map<String, AuthorityNamespace> namespaces) {
    KimObservableVisitor visitor;
    if (document instanceof KimOntology ontology) {
      var traversal = new KimOntologyVisitor(new KimOntologyVisitor.LenientValidator(), null);
      traversal.visit(ontology); visitor = traversal;
    } else if (document instanceof KimObservationStrategyDocument strategy) {
      var traversal = new KimObservationStrategyDocumentVisitor(null, null);
      traversal.visit(strategy); visitor = traversal;
    } else return;
    for (var concept : visitor.getConcepts())
      if (authorityOwner(concept.getName(), namespaces) != null
          && concept.getSemanticModifier() == null && concept.getObservable() == null
          && concept.getType().contains(SemanticType.NOTHING)) {
        concept.getType().remove(SemanticType.NOTHING);
        concept.getType().addAll(EnumSet.of(SemanticType.IDENTITY, SemanticType.PREDICATE));
      }
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
