package org.integratedmodelling.klab.api.services.reasoner.objects;

/** Canonical authority terminals for the observable grammar (AUTHORITY_CONCEPT and EXPR). */
public final class AuthorityIdentitySyntax {
  /** Bounded complex definitions (including typed pattern envelopes), before bracket escaping. */
  public static final int MAX_CODE_LENGTH = 131072;
  private AuthorityIdentitySyntax() {}
  public static void validateAuthority(String authority) {
    if (authority == null || !authority.matches("[A-Z][A-Z0-9_]+(\\.[A-Z][A-Z0-9_]+)*"))
      throw new IllegalArgumentException("A valid worldview-local authority ID is required");
  }
  public static String encode(String authority, String code) {
    validateAuthority(authority);
    if (code == null || code.isBlank() || code.length() > MAX_CODE_LENGTH
        || code.chars().anyMatch(Character::isISOControl))
      throw new IllegalArgumentException("A nonblank authority code of at most " + MAX_CODE_LENGTH + " characters is required");
    if (code.matches("[a-z][a-z0-9_]*|[A-Z][A-Z0-9_]+|[0-9]+")) return authority + ":" + code;
    return authority + ":[" + code.replace("\\", "\\\\").replace("]", "\\]") + "]";
  }
  /** Decode the bracketed code part without permitting injected tokens or malformed escapes. */
  public static String decode(String token) {
    if (token == null || token.isEmpty()) throw new IllegalArgumentException("Empty authority code");
    if (!token.startsWith("[")) return token;
    if (!token.endsWith("]")) throw new IllegalArgumentException("Unclosed authority code");
    var result = new StringBuilder();
    for (int i = 1; i < token.length() - 1; i++) {
      char c = token.charAt(i);
      if (c == ']') throw new IllegalArgumentException("Unescaped authority code delimiter");
      if (c == '\\') {
        if (++i >= token.length() - 1) throw new IllegalArgumentException("Incomplete authority code escape");
        c = token.charAt(i);
        if (c != '\\' && c != ']') throw new IllegalArgumentException("Unsupported authority code escape");
      }
      result.append(c);
    }
    return result.toString();
  }
}
