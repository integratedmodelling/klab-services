package org.integratedmodelling.common.utils;

/** Small syntax-preserving normalization shared by semantic clients and resource parsing. */
public final class SemanticSyntax {
  private SemanticSyntax() {}

  /** Remove only balanced parentheses enclosing the entire expression, respecting quoted values. */
  public static String stripOuterParentheses(String definition) {
    String result = definition.strip();
    while (enclosed(result)) result = result.substring(1, result.length() - 1).strip();
    return result;
  }

  private static boolean enclosed(String text) {
    if (text.length() < 2 || text.charAt(0) != '(' || text.charAt(text.length() - 1) != ')') return false;
    int depth = 0;
    char quote = 0;
    boolean escaped = false;
    for (int i = 0; i < text.length(); i++) {
      char c = text.charAt(i);
      if (quote != 0) {
        if (escaped) escaped = false;
        else if (c == 92) escaped = true;
        else if (c == quote) quote = 0;
      } else if (c == 34 || c == 39) quote = c;
      else if (c == '(') depth++;
      else if (c == ')') {
        if (--depth == 0 && i != text.length() - 1) return false;
        if (depth < 0) return false;
      }
    }
    return depth == 0 && quote == 0;
  }
}
