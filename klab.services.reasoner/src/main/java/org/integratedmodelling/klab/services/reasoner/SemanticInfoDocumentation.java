package org.integratedmodelling.klab.services.reasoner;

import java.util.*;
import java.util.function.Function;
import org.integratedmodelling.klab.api.knowledge.Semantics;
import org.integratedmodelling.klab.api.services.Reasoner;
import org.integratedmodelling.klab.runtime.language.SemanticDocumentation;

/** Human-facing names for the Reasoner's clause projections; OWL provenance is reported separately. */
final class SemanticInfoDocumentation {
  private SemanticInfoDocumentation() {}

  static String clauses(Reasoner reasoner, Semantics asset) {
    Map<String, Function<Semantics, ?>> direct = new LinkedHashMap<>();
    direct.put("Traits", reasoner::directTraits);
    direct.put("Roles", reasoner::directRoles);
    direct.put("Inherent (of)", reasoner::directInherent);
    direct.put("Goal (for)", reasoner::directGoal);
    direct.put("Co-occurrent (during)", reasoner::directCooccurrent);
    direct.put("Causant", reasoner::directCausant);
    direct.put("Caused", reasoner::directCaused);
    direct.put("Adjacent", reasoner::directAdjacent);
    direct.put("Compresent (with)", reasoner::directCompresent);
    direct.put("Comparison", reasoner::directRelativeTo);
    Map<String, Function<Semantics, ?>> effective = new LinkedHashMap<>();
    effective.put("Traits", reasoner::traits);
    effective.put("Roles", reasoner::roles);
    effective.put("Inherent (of)", reasoner::inherent);
    effective.put("Goal (for)", reasoner::goal);
    effective.put("Co-occurrent (during)", reasoner::cooccurrent);
    effective.put("Causant", reasoner::causant);
    effective.put("Caused", reasoner::caused);
    effective.put("Adjacent", reasoner::adjacent);
    effective.put("Compresent (with)", reasoner::compresent);
    effective.put("Comparison", reasoner::relativeTo);
    effective.put("Relationship sources", reasoner::relationshipSources);
    effective.put("Relationship targets", reasoner::relationshipTargets);
    effective.put("Affected", reasoner::affected);
    effective.put("Created", reasoner::created);
    effective.put("Parents", reasoner::parents);
    effective.put("Children", reasoner::children);
    effective.put("Logical operands", reasoner::operands);
    effective.put("Described type", reasoner::describedType);
    var out = new StringBuilder();
    append(out, "Direct clause projections", direct, asset);
    out.append("Effective projections include inherited restrictions. These are the values exposed "
        + "by the current Reasoner API; inspect the OWL sections for complete fillers, quantifiers, "
        + "Boolean structure and asserting classes. Missing source clauses cannot be reconstructed "
        + "from OWL; use the syntactic report to inspect them.\n\n");
    append(out, "Effective clause projections and hierarchy", effective, asset);
    return out.toString();
  }

  private static void append(StringBuilder out, String title,
      Map<String, Function<Semantics, ?>> queries, Semantics asset) {
    out.append("## ").append(title).append("\n\n");
    queries.forEach((label, query) -> {
      out.append("- ").append(label).append(": ");
      try { out.append(format(query.apply(asset))); }
      catch (RuntimeException e) { out.append("Unavailable (").append(e.getClass().getSimpleName()).append(')'); }
      out.append('\n');
    });
    out.append('\n');
  }

  private static String format(Object value) {
    if (value == null) return "None reported";
    if (value instanceof Semantics semantics) return SemanticDocumentation.code(semantics.getUrn());
    if (value instanceof Collection<?> collection) return collection.isEmpty() ? "None reported"
        : String.join(", ", collection.stream().map(SemanticInfoDocumentation::format).sorted().toList());
    return SemanticDocumentation.code(value);
  }
}
