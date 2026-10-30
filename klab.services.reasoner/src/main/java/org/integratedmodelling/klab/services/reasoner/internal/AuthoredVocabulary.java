package org.integratedmodelling.klab.services.reasoner.internal;

import java.util.*;
import java.util.function.Function;
import org.integratedmodelling.klab.api.knowledge.Concept;
import org.integratedmodelling.klab.api.knowledge.Worldview;
import org.integratedmodelling.klab.runtime.language.WorldviewDeclarationRegistry;

/** Canonical meanings authorized by the private actor's captured source declarations. */
public final class AuthoredVocabulary {
  private final Worldview source;
  private final long revision;
  private final Set<String> meanings;

  private AuthoredVocabulary(Worldview source, long revision, Set<String> meanings) {
    this.source = source; this.revision = revision; this.meanings = Set.copyOf(meanings);
  }

  public boolean belongsTo(Worldview source, long revision) {
    return this.source == source && this.revision == revision;
  }

  public boolean contains(Concept concept) { return meanings.contains(concept.getUrn()); }

  public static AuthoredVocabulary capture(Worldview source, long revision,
      Function<String, Concept> resolveDeclaration) {
    if (source == null) throw new IllegalStateException("Accepted authored vocabulary is unavailable");
    var registry = new WorldviewDeclarationRegistry(source.getOntologies());
    if (!registry.errors().isEmpty())
      throw new IllegalStateException("Accepted authored vocabulary has invalid declarations");
    var names = new LinkedHashSet<String>();
    for (var declaration : registry.declarations()) {
      names.add(declaration.urn());
      var statement = declaration.statement();
      // These are the declarations for which compileStagedOntologies compiles authored
      // clauses. An alias contributes its canonical target, not new synthetic names.
      if (statement.deniable() && !statement.isAlias()
          && statement.getUpperConceptDefined() == null) {
        var local = statement.getUrn().replaceFirst("^.*:", "");
        names.add(WorldviewDeclarationRegistry.urn(declaration.ontology().getUrn(), "Not" + local));
        if (statement.deniedAlias() != null)
          names.add(WorldviewDeclarationRegistry.urn(declaration.ontology().getUrn(), statement.deniedAlias()));
      }
    }
    var meanings = new LinkedHashSet<String>();
    for (var name : names) {
      var concept = resolveDeclaration.apply(name);
      if (concept == null)
        throw new IllegalStateException("Accepted authored declaration is unavailable: " + name);
      meanings.add(concept.getUrn());
    }
    return new AuthoredVocabulary(source, revision, meanings);
  }
}
