package org.integratedmodelling.klab.services.reasoner.owl;

import static org.integratedmodelling.klab.runtime.language.SemanticDocumentation.code;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Map;
import org.integratedmodelling.klab.api.data.Metadata;
import org.integratedmodelling.klab.api.knowledge.KlabAsset;
import org.integratedmodelling.klab.api.knowledge.Worldview;
import org.integratedmodelling.klab.api.lang.kim.KimConceptStatement;
import org.integratedmodelling.klab.api.services.runtime.Notification;

/** Documents one loaded generation while its owning Reasoner holds the mutation monitor. */
public final class WorldviewDocumentation {
  private WorldviewDocumentation() {}

  public static String render(Worldview worldview, long revision, boolean consistent, OWL owl,
      Map<String, String> sourceHashes, Collection<Notification> diagnostics) {
    var out = new StringBuilder("# Worldview ").append(code(worldview.getUrn()))
        .append("\n\nWorldview ID ").append(code(worldview.getWorldviewId()))
        .append("\n\nKnowledge revision ").append(revision)
        .append("\n\nConsistent ").append(consistent).append("\n\n");
    out.append("Ontologies appear in load order. Enclosing declarations and authored clauses ")
        .append("describe source structure. OWL axioms describe translated logic. ")
        .append("Direct inferred superclasses are listed when classification is available.\n");
    var owners = worldview.getMetadata() == null ? null : worldview.getMetadata().get(Worldview.PROJECT_SOURCES);
    if (owners instanceof Map<?, ?> map) {
      out.append("\n## Selected project providers\n\n");
      map.forEach((project, provider) -> bullet(out, project + " from " + provider));
    }
    annotations(out, worldview);
    for (var ontology : worldview.getOntologies()) {
      var urn = ontology.getUrn();
      out.append("\n## Ontology ").append(code(urn))
          .append("\n\nProject ").append(code(ontology.getProjectName()))
          .append(", version ").append(code(ontology.getVersion()))
          .append(", source SHA-256 ").append(code(sourceHashes.getOrDefault(urn, "unavailable")))
          .append("\n\nDomain ").append(ontology.getDomain() == null ? "root" : code(ontology.getDomain().getUrn()))
          .append("\n\nImports ").append(code(ontology.getImportedOntologies()))
          .append("\n\nVocabulary imports ").append(code(ontology.getVocabularyImports()))
          .append("\n\nExternal OWL imports ").append(code(ontology.getOwlImports())).append('\n');
      annotations(out, ontology);
      notes(out, ontology.getNotifications());
      for (var statement : ontology.getStatements()) declaration(out, owl, urn, null, statement);
      out.append("\n### Asserted OWL logic\n\n");
      var translated = owl.getOntology(urn);
      if (translated == null) out.append("Ontology unavailable.\n");
      else translated.getOWLOntology().getLogicalAxioms().stream()
          .map(Object::toString).sorted().forEach(a -> bullet(out, a));
    }
    out.append("\n## Observation strategies\n");
    for (var document : worldview.getObservationStrategies()) {
      out.append("\n### ").append(code(document.getUrn())).append('\n');
      annotations(out, document);
      notes(out, document.getNotifications());
      if (document.getSourceCode() != null && !document.getSourceCode().isBlank()) {
        String fence = "```";
        while (document.getSourceCode().contains(fence)) fence += "`";
        out.append("\n").append(fence).append("\n").append(document.getSourceCode())
            .append("\n").append(fence).append("\n");
      }
      for (var strategy : document.getStatements()) {
        bullet(out, strategy.getUrn() + " (" + strategy.getType() + ", rank " + strategy.getRank() + ")");
        annotations(out, strategy);
        notes(out, strategy.getNotifications());
        if (strategy.getDescription() != null) out.append('\n').append(strategy.getDescription()).append('\n');
      }
    }
    out.append("\n## Authorities\n\n");
    for (var binding : worldview.getAuthorityBindings())
      bullet(out, binding.localId() + " rooted at " + binding.rootIdentity() + " in " + binding.sourceOntology());
    out.append("\n## Worldview diagnostics\n\n");
    var notifications = new LinkedHashSet<>(worldview.getNotifications());
    notifications.addAll(diagnostics);
    if (notifications.isEmpty()) out.append("None reported.\n");
    notes(out, notifications);
    return out.toString();
  }

  private static void declaration(StringBuilder out, OWL owl, String namespace, String enclosing,
      KimConceptStatement statement) {
    var name = statement.getUrn();
    if (name != null && !name.isBlank()) {
      var urn = name.contains(":") ? name : namespace + ":" + name;
      out.append("\n### ").append(code(urn)).append("\n\nTypes ").append(code(statement.getType())).append('\n');
      if (statement.isAbstract()) bullet(out, "abstract");
      if (statement.isSubjective()) bullet(out, "subjective");
      if (statement.isSealed()) bullet(out, "sealed");
      if (statement.isAlias()) bullet(out, "alias");
      if (statement.isChildrenDisjoint()) bullet(out, "disjoint children");
      if (statement.getUpperConceptDefined() != null)
        bullet(out, "Upper concept " + statement.getUpperConceptDefined());
      if (statement.getDeclaredInherent() != null)
        bullet(out, "Declared inherency " + statement.getDeclaredInherent().getUrn());
      if (enclosing != null) out.append("\nEnclosing declaration ").append(code(enclosing)).append('\n');
      if (statement.getDeclaredParent() != null)
        out.append("\nDeclared parent ").append(code(statement.getDeclaredParent().getUrn())).append('\n');
      if (statement.getDocstring() != null) out.append('\n').append(statement.getDocstring()).append('\n');
      annotations(out, statement);
      notes(out, statement.getNotifications());
      var clauses = new LinkedHashSet<String>();
      for (var clause : statement.getDeclarationClauses())
        if (!clause.kind().toLowerCase(java.util.Locale.ROOT).contains("authority")) clauses.add(clause.source());
      for (var rule : statement.getConsequenceRules()) clauses.add(rule.source().source());
      if (!clauses.isEmpty()) out.append("\nAuthored clauses\n\n");
      clauses.forEach(c -> bullet(out, c));
      out.append("\nDirect inferred superclasses\n\n");
      try {
        var concept = owl.getConcept(urn);
        var target = concept == null ? null : owl.getOWLClass(concept);
        if (target == null || !owl.isOn()) out.append("Unavailable.\n");
        else owl.getSuperClasses(target, true).getFlattened().stream()
            .map(c -> c.getIRI().toString()).sorted().forEach(c -> bullet(out, c));
      } catch (RuntimeException e) {
        out.append("Unavailable (").append(e.getClass().getSimpleName()).append(").\n");
      }
      enclosing = urn;
    }
    for (var child : statement.getChildren()) declaration(out, owl, namespace, enclosing, child);
  }

  private static void annotations(StringBuilder out, KlabAsset asset) {
    var metadata = asset.getMetadata();
    if (metadata == null) return;
    for (var key : java.util.List.of(Metadata.IM_NOTES, Metadata.DC_DESCRIPTION,
        Metadata.DC_COMMENT, Metadata.RDFS_COMMENT)) {
      var value = metadata.get(key);
      if (value != null) out.append("\n").append(code(key)).append("\n\n").append(value).append('\n');
    }
  }

  private static void notes(StringBuilder out, Collection<Notification> notifications) {
    for (var note : notifications) bullet(out, note.getLevel() + " " + note.getMessage());
  }

  private static void bullet(StringBuilder out, Object value) {
    out.append("- ").append(code(value)).append('\n');
  }
}
