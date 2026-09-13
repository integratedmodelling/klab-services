package org.integratedmodelling.klab.runtime.language;

import java.util.*;
import java.util.function.Function;
import org.integratedmodelling.klab.api.lang.kim.KimConceptStatement;

/** Documentation of an expression and the declarations available to its Resources service. */
public final class SyntacticDocumentation {
  private SyntacticDocumentation() {}

  public static String describe(String urn, Object expression,
      Function<String, KimConceptStatement> declarations) {
    return describeWithPaths(urn, expression, name -> {
      var declaration = declarations.apply(name);
      return declaration == null ? List.of() : List.of(declaration);
    });
  }

  public static String describeWithPaths(String urn, Object expression,
      Function<String, List<KimConceptStatement>> declarations) {
    var out = new StringBuilder("# Syntactic documentation\n\n")
        .append(SemanticDocumentation.code(urn)).append("\n\n")
        .append("Bean properties below describe the adapted expression. Declaration clauses are "
            + "asserted source syntax, not evidence that an OWL restriction has been compiled. "
            + "Parent declarations provide inheritance context; effective restrictions are reported by the Reasoner.\n\n")
        .append(SemanticDocumentation.describe("Expression", expression));
    var pending = new ArrayDeque<>(SemanticDocumentation.references(expression));
    var visited = new HashSet<String>();
    while (!pending.isEmpty()) {
      var name = pending.removeFirst();
      if (!visited.add(name)) continue;
      var path = declarations.apply(name);
      out.append("## Declaration ").append(SemanticDocumentation.code(name)).append("\n\n");
      if (path == null || path.isEmpty()) {
        out.append("No source declaration available in this Resources service.\n\n");
        continue;
      }
      var declaration = path.getLast();
      for (int i = 0; i < path.size() - 1; i++) {
        var enclosing = path.get(i);
        out.append("Enclosing parent declaration: ")
            .append(SemanticDocumentation.code(enclosing.getNamespace() + ":" + enclosing.getUrn()))
            .append(". Its clauses are inheritance context, not assertions on this child.\n\n")
            .append(SemanticDocumentation.describe("Enclosing declaration", enclosing));
        pending.addAll(SemanticDocumentation.references(enclosing));
      }
      if (declaration.getDocstring() != null && !declaration.getDocstring().isBlank()) {
        out.append("### Description\n\n").append(declaration.getDocstring()).append("\n\n");
      }
      out.append(SemanticDocumentation.describe("Asserted declaration and metadata", declaration));
      var parent = declaration.getDeclaredParent();
      if (parent != null) {
        out.append("Inheritance/alias target: ").append(SemanticDocumentation.code(parent.getUrn()))
            .append(". Its declaration is documented separately.\n\n");
      }
      pending.addAll(SemanticDocumentation.references(declaration));
    }
    return out.append("Core ontology concepts are automatically abstract. Declared abstract status "
        + "does not carry to subclasses. Abstract and subjective attributes in the direct expression "
        + "contribute status; clause fillers and unary-operator targets do not.\n").toString();
  }
}
