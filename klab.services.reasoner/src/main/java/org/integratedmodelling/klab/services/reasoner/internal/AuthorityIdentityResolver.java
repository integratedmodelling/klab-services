package org.integratedmodelling.klab.services.reasoner.internal;

import java.util.*;
import org.integratedmodelling.common.lang.Axiom;
import org.integratedmodelling.klab.api.data.Metadata;
import org.integratedmodelling.klab.api.exceptions.KlabValidationException;
import org.integratedmodelling.klab.api.knowledge.Concept;
import org.integratedmodelling.klab.api.knowledge.SemanticType;
import org.integratedmodelling.klab.api.services.Authority;
import org.integratedmodelling.klab.api.services.runtime.Notification;
import org.integratedmodelling.klab.services.reasoner.owl.OWL;

/** Lazy, rooted materialization. Validate the complete provider graph before adding axioms. */
public final class AuthorityIdentityResolver {
  private final OWL owl;
  private final AuthorityBindings bindings;
  private final Map<AuthorityBindings.Binding, Map<String, Concept>> resolved = new IdentityHashMap<>();
  private final Map<AuthorityBindings.Binding, Set<String>> deferred = new IdentityHashMap<>();
  private final Map<String, String[]> references = new HashMap<>();
  private final Runnable enriched;

  public AuthorityIdentityResolver(OWL owl, AuthorityBindings bindings) {
    this(owl, bindings, () -> {});
  }

  public AuthorityIdentityResolver(OWL owl, AuthorityBindings bindings, Runnable enriched) {
    this.owl = owl;
    this.bindings = bindings;
    this.enriched = enriched;
  }

  public synchronized Concept resolve(String name, String id) {
    return resolve(name, id, false);
  }

  /** Expand the same concept in place; failed provider validation leaves accepted axioms intact. */
  public synchronized Concept resolve(String name, String id, boolean fullHierarchy) {
    var alias = bindings.alias(name, id);
    if (alias != null) return resolve(alias[0], alias[1], fullHierarchy);
    var binding = bindings.find(name);
    if (binding == null) return null;
    // Search-only subdivisions share the base binding's cache, ontology and canonical locator.
    name = binding.request().name();
    resolved.keySet().removeIf(previous -> bindings.get(previous.request().name()) != previous);
    deferred.keySet().removeIf(previous -> bindings.get(previous.request().name()) != previous);
    var known = resolved.computeIfAbsent(binding, key -> new HashMap<>());
    var incomplete = deferred.computeIfAbsent(binding, key -> new HashSet<>());
    if (known.containsKey(id) && (!fullHierarchy || !incomplete.contains(id))) return known.get(id);
    var graph = new LinkedHashMap<String, Authority.Identity>();
    collect(binding, id, graph, known, incomplete, fullHierarchy);
    if (graph.isEmpty()) return known.get(id);
    validateGraph(id, graph, new HashSet<>(), new HashSet<>());
    var roots = new HashMap<String, List<String>>();
    for (var entry : graph.entrySet()) roots.put(entry.getKey(), boundaryRoots(binding, entry.getValue()));
    var ontology = owl.requireOntology(AuthorityBindings.ontologyId(binding), OWL.INTERNAL_ONTOLOGY_PREFIX);
    ontology.setInternal(true);
    var declarations = new ArrayList<Axiom>();
    var axioms = new ArrayList<Axiom>();
    for (var entry : graph.entrySet()) {
      var identity = entry.getValue();
      String concept = identity.getConceptName();
      if (!known.containsKey(entry.getKey())) declarations.add(Axiom.ClassAssertion(concept,
          EnumSet.of(SemanticType.IDENTITY, SemanticType.PREDICATE, SemanticType.AUTHORITY_IDENTITY)));
      for (var root : roots.get(entry.getKey())) axioms.add(Axiom.SubClass(root, concept));
      // The bridge is attached at the provider's top-level identity. Other identities inherit
      // through the provider's base/parent graph, whose consistency belongs to the authority.
      if (parents(identity).isEmpty())
        axioms.add(Axiom.SubClass(binding.request().rootIdentity(), concept));
      for (var parent : parents(identity))
        axioms.add(Axiom.SubClass(known.containsKey(parent) ? known.get(parent).getUrn()
            : graph.get(parent).getConceptName(), concept));
      annotate(axioms, concept, Metadata.DC_LABEL, identity.getLabel());
      annotate(axioms, concept, CoreOntology.NS.DISPLAY_LABEL_PROPERTY, identity.getLabel());
      annotate(axioms, concept, Metadata.DC_COMMENT, identity.getDescription());
      annotate(axioms, concept, CoreOntology.NS.AUTHORITY_ID_PROPERTY, name);
      annotate(axioms, concept, CoreOntology.NS.CONCEPT_DEFINITION_PROPERTY,
          org.integratedmodelling.klab.api.services.reasoner.objects.AuthorityIdentitySyntax.encode(name, identity.getId()));
    }
    // Declare the entire graph first, before edges refer to parents. Keeping locator annotations
    // out of this pass also preserves each node's internal concept URN.
    ontology.define(declarations);
    ontology.define(axioms);
    owl.registerWithReasoner(ontology);
    owl.flushReasoner();
    boolean expanded = graph.keySet().stream().anyMatch(incomplete::contains);
    String authorityName = name;
    graph.forEach((externalId, identity) -> {
      known.putIfAbsent(externalId, ontology.getConcept(identity.getConceptName()));
      var concept = known.get(externalId);
      known.putIfAbsent(identity.getId(), concept);
      boolean pending = identity.getHierarchyStatus() == Authority.HierarchyStatus.DEFERRED;
      if (pending) {
        incomplete.add(externalId);
        incomplete.add(identity.getId());
      } else {
        incomplete.removeIf(aliasId -> concept.equals(known.get(aliasId)));
      }
      concept.getMetadata().put(Authority.HIERARCHY_STATUS,
          (pending ? Authority.HierarchyStatus.DEFERRED : Authority.HierarchyStatus.COMPLETE).name());
      references.put(concept.getUrn(), new String[] {authorityName, externalId});
    });
    if (expanded) enriched.run();
    return known.get(id);
  }

  /** Explicit parent inspection or authority-to-authority subsumption may request enrichment. */
  public synchronized void ensureHierarchy(Concept concept) {
    var reference = references.get(concept.getUrn());
    if (reference != null) resolve(reference[0], reference[1], true);
  }

  private List<String> boundaryRoots(AuthorityBindings.Binding binding, Authority.Identity identity) {
    var roots = new ArrayList<String>();
    for (var boundary : identity.getSemanticBoundaries()) {
      var target = binding.request().semanticBoundaries().get(boundary);
      if (target == null) continue;
      var concept = owl.getConcept(target);
      if (concept == null || !concept.is(SemanticType.IDENTITY) || concept.is(SemanticType.NOTHING))
        throw new KlabValidationException("Semantic boundary must be a loaded identity concept: " + target);
      roots.add(target);
    }
    if (identity.getHierarchyStatus() == Authority.HierarchyStatus.DEFERRED
        && (roots.isEmpty() || !parents(identity).isEmpty()))
      throw new KlabValidationException("Deferred authority identity requires a mapped boundary and no external parent edges");
    return roots;
  }

  private void validateGraph(String id, Map<String, Authority.Identity> graph, Set<String> active, Set<String> done) {
    if (!graph.containsKey(id) || done.contains(id)) return;
    if (!active.add(id)) throw new KlabValidationException("Cycle in authority hierarchy at " + id);
    for (var parent : parents(graph.get(id))) validateGraph(parent, graph, active, done);
    active.remove(id); done.add(id);
  }

  private void collect(AuthorityBindings.Binding binding, String id,
      Map<String, Authority.Identity> graph, Map<String, Concept> known, Set<String> incomplete, boolean fullHierarchy) {
    if (id == null || id.isBlank()) throw new KlabValidationException("Empty authority identity ID");
    if (graph.containsKey(id) || known.containsKey(id) && (!fullHierarchy || !incomplete.contains(id))) return;
    if (graph.size() >= 4096) throw new KlabValidationException("Authority hierarchy exceeds materialization limit");
    if (id.equals(binding.request().rootIdentity())) {
      var root = owl.getConcept(id);
      if (root == null) throw new KlabValidationException("Authority worldview root is unavailable: " + id);
      known.put(id, root);
      return;
    }
    var identity = fullHierarchy ? binding.provider().resolveIdentity(binding.id(), id, Authority.ResolutionMode.FULL_HIERARCHY)
        : binding.provider().resolveIdentity(binding.id(), id);
    if (identity == null || (identity.getNotifications() != null && identity.getNotifications().stream()
        .anyMatch(n -> n.getLevel() == Notification.Level.Error)))
      throw new KlabValidationException("Authority could not resolve identity " + id);
    if (fullHierarchy && identity.getHierarchyStatus() == Authority.HierarchyStatus.DEFERRED)
      throw new KlabValidationException("Authority did not provide complete ancestry");
    if (identity.getConceptName() == null || !identity.getConceptName().matches("[A-Za-z_][A-Za-z0-9_]*"))
      throw new KlabValidationException("Invalid authority concept name for " + id);
    var ontology = owl.getOntology(AuthorityBindings.ontologyId(binding));
    var existingConcept = ontology == null ? null : ontology.getConcept(identity.getConceptName());
    if (known.containsKey(id) && !known.get(id).equals(existingConcept))
      throw new KlabValidationException("Authority changed the concept name during hierarchy enrichment: " + id);
    if (existingConcept != null) {
      known.put(id, existingConcept);
      boolean pending = Authority.HierarchyStatus.DEFERRED.name().equals(
          existingConcept.getMetadata().get(Authority.HIERARCHY_STATUS));
      if (pending) incomplete.add(id);
      if (!fullHierarchy || !pending) return;
    }
    if (identity.getParentRelationship() != null && !identity.getParentRelationship().isEmpty())
      throw new KlabValidationException("Authority parent relationship properties are not yet supported");
    for (var existing : graph.values())
      if (existing.getConceptName().equals(identity.getConceptName())
          && !Objects.equals(existing.getId(), identity.getId()))
        throw new KlabValidationException("Conflicting authority concept name " + identity.getConceptName());
    graph.put(id, identity);
    // An unmapped identity retains the complete legacy graph, even if an ancestor belongs to
    // a mapped boundary. Do not mark the leaf complete above a silently deferred branch.
    boolean fullParents = fullHierarchy || !binding.request().semanticBoundaries().isEmpty();
    for (var parent : parents(identity)) collect(binding, parent, graph, known, incomplete, fullParents);
  }

  private List<String> parents(Authority.Identity identity) {
    var parents = new ArrayList<String>();
    if (identity.getParentIds() != null) parents.addAll(identity.getParentIds());
    if (identity.getBaseIdentity() != null && !identity.getBaseIdentity().equals(identity.getId()))
      parents.add(identity.getBaseIdentity());
    return parents;
  }

  private void annotate(List<Axiom> axioms, String concept, String property, String value) {
    if (value != null) axioms.add(Axiom.AnnotationAssertion(concept, property, value));
  }
}
