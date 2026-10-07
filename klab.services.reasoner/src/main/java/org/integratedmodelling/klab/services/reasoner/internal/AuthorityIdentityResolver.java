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

  public AuthorityIdentityResolver(OWL owl, AuthorityBindings bindings) {
    this.owl = owl;
    this.bindings = bindings;
  }

  public synchronized Concept resolve(String name, String id) {
    var alias = bindings.alias(name, id);
    if (alias != null) return resolve(alias[0], alias[1]);
    var binding = bindings.find(name);
    if (binding == null) return null;
    // Search-only subdivisions share the base binding's cache, ontology and canonical locator.
    name = binding.request().name();
    resolved.keySet().removeIf(previous -> bindings.get(previous.request().name()) != previous);
    var known = resolved.computeIfAbsent(binding, key -> new HashMap<>());
    if (known.containsKey(id)) return known.get(id);
    var graph = new LinkedHashMap<String, Authority.Identity>();
    collect(binding, id, graph, known);
    if (known.containsKey(id)) return known.get(id);
    var ontology = owl.requireOntology(AuthorityBindings.ontologyId(binding), OWL.INTERNAL_ONTOLOGY_PREFIX);
    ontology.setInternal(true);
    var declarations = new ArrayList<Axiom>();
    var axioms = new ArrayList<Axiom>();
    for (var identity : graph.values()) {
      String concept = identity.getConceptName();
      declarations.add(Axiom.ClassAssertion(concept, EnumSet.of(SemanticType.IDENTITY,
          SemanticType.PREDICATE, SemanticType.AUTHORITY_IDENTITY)));
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
    graph.forEach((externalId, identity) -> known.put(externalId, ontology.getConcept(identity.getConceptName())));
    return known.get(id);
  }

  private void collect(AuthorityBindings.Binding binding, String id,
      Map<String, Authority.Identity> graph, Map<String, Concept> known) {
    if (id == null || id.isBlank()) throw new KlabValidationException("Empty authority identity ID");
    if (known.containsKey(id) || graph.containsKey(id)) return;
    if (id.equals(binding.request().rootIdentity())) {
      var root = owl.getConcept(id);
      if (root == null) throw new KlabValidationException("Authority worldview root is unavailable: " + id);
      known.put(id, root);
      return;
    }
    var identity = binding.provider().resolveIdentity(binding.id(), id);
    if (identity == null || (identity.getNotifications() != null && identity.getNotifications().stream()
        .anyMatch(n -> n.getLevel() == Notification.Level.Error)))
      throw new KlabValidationException("Authority could not resolve identity " + id);
    if (identity.getConceptName() == null || !identity.getConceptName().matches("[A-Za-z_][A-Za-z0-9_]*"))
      throw new KlabValidationException("Invalid authority concept name for " + id);
    var ontology = owl.getOntology(AuthorityBindings.ontologyId(binding));
    var existingConcept = ontology == null ? null : ontology.getConcept(identity.getConceptName());
    if (existingConcept != null) {
      known.put(id, existingConcept);
      return;
    }
    if (identity.getParentRelationship() != null && !identity.getParentRelationship().isEmpty())
      throw new KlabValidationException("Authority parent relationship properties are not yet supported");
    for (var existing : graph.values())
      if (existing.getConceptName().equals(identity.getConceptName())
          && !Objects.equals(existing.getId(), identity.getId()))
        throw new KlabValidationException("Conflicting authority concept name " + identity.getConceptName());
    graph.put(id, identity);
    for (var parent : parents(identity)) collect(binding, parent, graph, known);
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
