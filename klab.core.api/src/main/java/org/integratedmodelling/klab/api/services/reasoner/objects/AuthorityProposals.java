package org.integratedmodelling.klab.api.services.reasoner.objects;

import java.util.*;
import org.integratedmodelling.klab.api.lang.kim.*;
import org.integratedmodelling.klab.api.services.runtime.Notification;

/** Extract only parsed annotation/statement pairs. Never infer identities from source text. */
public final class AuthorityProposals {
  private AuthorityProposals() {}
  public record Candidate(String namespace, String alias, String authority, String identity,
      AuthorityCodelistRequest.Source source, KlabStatement statement) {}
  public record Extraction(List<Candidate> candidates, List<Notification> notifications) {}
  public static Extraction extract(KlabDocument<?> document) {
    var candidates = new ArrayList<Candidate>();
    var notifications = new ArrayList<Notification>();
    for (var statement : document.getStatements())
      if (statement instanceof KlabStatement klab) extract(klab, document, candidates, notifications);
    return new Extraction(List.copyOf(candidates), List.copyOf(notifications));
  }
  private static void extract(KlabStatement statement, KlabDocument<?> document,
      List<Candidate> candidates, List<Notification> notifications) {
    for (var annotation : statement.getAnnotations()) {
      if (!"proposal".equals(annotation.getName()) || !annotation.containsKey("authoritycode")) continue;
      try {
        Object requested = annotation.get("authoritycode");
        if (!(requested instanceof String name) || !name.matches("[a-z][a-z0-9_]*(\\.[a-z][a-z0-9_]*)*:[A-Z][A-Za-z0-9_]*"))
          throw new IllegalArgumentException("authoritycode requires a configured namespace and concept name");
        var identities = new LinkedHashMap<String, String[]>();
        var seen = Collections.newSetFromMap(new IdentityHashMap<KimConcept, Boolean>());
        if (statement instanceof KimModel model) {
          for (var observable : model.getObservables()) collect(observable.getSemantics(), identities, seen);
          for (var observable : model.getDependencies()) collect(observable.getSemantics(), identities, seen);
        } else if (statement instanceof KimConceptStatement concept) {
          collect(concept.getDeclaredParent(), identities, seen);
          for (var reference : concept.getDeclaredReferences()) collect(reference, identities, seen);
        }
        String[] target;
        Object explicit = annotation.get("target");
        if (explicit instanceof String locator) {
          target = identities.get(locator);
          if (target == null) throw new IllegalArgumentException("Explicit proposal target must occur in the annotated statement");
        } else {
          if (identities.size() != 1) throw new IllegalArgumentException("Proposal requires exactly one authority identity, or an explicit target");
          target = identities.values().iterator().next();
        }
        int colon = name.indexOf(':');
        candidates.add(new Candidate(name.substring(0, colon), name.substring(colon + 1), target[0], target[1],
            new AuthorityCodelistRequest.Source(document.getUrn(), SemanticValidationRequest.sourceHash(document.getSourceCode()),
                statement.getOffsetInDocument(), statement.getLength()), statement));
      } catch (IllegalArgumentException e) {
        notifications.add(Notification.warning(e.getMessage(), Notification.LexicalContext.of(statement, document)));
      }
    }
    if (statement instanceof KimConceptStatement concept)
      for (var child : concept.getChildren()) extract(child, document, candidates, notifications);
  }
  private static void collect(KimConcept concept, Map<String, String[]> identities, Set<KimConcept> seen) {
    if (concept == null || !seen.add(concept)) return;
    // Current adapters represent authority terminals as named AST leaves. The explicit fields
    // below are also accepted for older KimConcept producers.
    String name = concept.getName();
    if (name != null && name.matches("[A-Z][A-Z0-9_]*(\\.[A-Z0-9_]+)*:.*")) {
      String[] parts = name.split(":", 2);
      if (!parts[1].startsWith("[") && !parts[1].matches("[A-Za-z0-9_]+"))
        throw new IllegalArgumentException("Invalid authority terminal in proposal statement");
      String code = AuthorityIdentitySyntax.decode(parts[1]);
      identities.put(AuthorityIdentitySyntax.encode(parts[0], code), new String[] {parts[0], code});
    }
    if (concept.getAuthority() != null && concept.getAuthorityTerm() != null) {
      String code = AuthorityIdentitySyntax.decode(concept.getAuthorityTerm());
      identities.put(AuthorityIdentitySyntax.encode(concept.getAuthority(), code), new String[] {concept.getAuthority(), code});
    }
    for (var child : Arrays.asList(concept.getObservable(), concept.getInherent(), concept.getGoal(),
        concept.getCausant(), concept.getCaused(), concept.getCompresent(), concept.getComparisonConcept(),
        concept.getRelationshipSource(), concept.getRelationshipTarget(), concept.getCooccurrent(), concept.getAdjacent()))
      collect(child, identities, seen);
    for (var child : concept.getTraits()) collect(child, identities, seen);
    for (var child : concept.getRoles()) collect(child, identities, seen);
    for (var child : concept.getOperands()) collect(child, identities, seen);
    for (var modifier : concept.getModifiers()) collect(modifier.getSecond(), identities, seen);
  }
}
