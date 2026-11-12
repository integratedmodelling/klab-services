package org.integratedmodelling.klab.api.services.reasoner.objects;

import java.util.List;
import java.util.Set;
import org.integratedmodelling.klab.api.knowledge.SemanticType;
import org.integratedmodelling.klab.api.knowledge.Worldview;
import org.integratedmodelling.klab.api.lang.kim.KimConceptStatement;

/** Read-only declaration hierarchy. Deliberately excludes source text and authority parameters. */
public record WorldviewExplorerCatalog(
    String urn, List<Ontology> ontologies, List<Authority> authorities, List<DeclaredLink> declaredLinks) {
  public WorldviewExplorerCatalog(String urn, List<Ontology> ontologies, List<Authority> authorities) {
    this(urn, ontologies, authorities, List.of());
  }
  public record DeclaredLink(String relation, String source, String target, String label) {}
  public record Ontology(String urn, List<Declaration> children) {}
  public record Declaration(
      String urn, String label, Set<SemanticType> type, List<Declaration> children) {}
  public record Authority(String localId, Provider provider) {}
  public record Provider(boolean searchable) {}

  public static WorldviewExplorerCatalog capture(Worldview worldview) {
    if (worldview == null) return new WorldviewExplorerCatalog("", List.of(), List.of());
    var links = new java.util.LinkedHashSet<DeclaredLink>();
    for (var ontology : worldview.getOntologies())
      for (var statement : ontology.getStatements()) collectLinks(statement, ontology.getUrn(), links);
    return new WorldviewExplorerCatalog(
        worldview.getUrn(),
        worldview.getOntologies().stream()
            .map(o -> new Ontology(o.getUrn(), o.getStatements().stream()
                .filter(s -> s.getUrn() != null && !s.getUrn().isBlank())
                .map(s -> declaration(s, o.getUrn())).toList())).toList(),
        worldview.getAuthorityBindings().stream()
            .map(a -> new Authority(a.localId(),
                new Provider(a.provider() != null && a.provider().searchable()))).toList(), List.copyOf(links));
  }

  private static void collectLinks(KimConceptStatement statement, String ontology, Set<DeclaredLink> links) {
    if (statement.getUrn() != null && !statement.getUrn().isBlank()) {
      var source = semanticUrn(statement, ontology);
      for (var containment : statement.getObservationalContainments())
        addLink(links, "CONTAINS", containment.container(), containment.contained(), "contains (declared)");
      for (var ingredient : statement.getEmergenceTriggers())
        addLink(links, "EMERGENCE", source, ingredient, "emerges from (declared)");
      for (var identity : statement.getRequiredIdentities())
        addLink(links, "REQUIRED_IDENTITIES", source, identity, "requires identity (declared)");
      for (var realm : statement.getRequiredRealms())
        addLink(links, "REQUIRED_REALMS", source, realm, "requires realm (declared)");
    }
    for (var child : statement.getChildren()) collectLinks(child, ontology, links);
  }

  private static void addLink(Set<DeclaredLink> links, String relation, Object source, Object target, String label) {
    String from = source instanceof org.integratedmodelling.klab.api.lang.kim.KimConcept c ? c.getUrn() : (String) source;
    String to = target instanceof org.integratedmodelling.klab.api.lang.kim.KimConcept c ? c.getUrn() : (String) target;
    // Preserve Boolean expressions as single endpoints, exactly as the IDE does.
    if (from != null && !from.isBlank() && to != null && !to.isBlank())
      links.add(new DeclaredLink(relation, from, to, label));
  }

  private static String semanticUrn(KimConceptStatement statement, String ontology) {
    var local = statement.getUrn();
    var namespace = statement.getNamespace();
    if (namespace == null || namespace.isBlank()) namespace = ontology;
    return local.contains(":") ? local : namespace + ":" + local;
  }

  private static Declaration declaration(KimConceptStatement statement, String ontology) {
    var local = statement.getUrn();
    var urn = semanticUrn(statement, ontology);
    return new Declaration(urn, local.substring(local.lastIndexOf(':') + 1),
        Set.copyOf(statement.getType()), statement.getChildren().stream()
            .filter(s -> s.getUrn() != null && !s.getUrn().isBlank())
            .map(child -> declaration(child, ontology)).toList());
  }
}
