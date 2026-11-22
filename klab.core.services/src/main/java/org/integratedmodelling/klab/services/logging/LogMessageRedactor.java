package org.integratedmodelling.klab.services.logging;

import java.util.regex.Pattern;

/** Redaction for complete message headers, before their text is retained in a log cursor. */
final class LogMessageRedactor {
  private static final String MASK = "[redacted]";
  private static final String WITHHELD = "[message withheld by redaction policy]";
  private static final Pattern UNSAFE = Pattern.compile(
      "[\\p{Cf}\\p{Zl}\\p{Zp}\\uFFFD]|[\\p{Cc}&&[^\\t]]");
  private static final Pattern PRIVATE_BLOCK = Pattern.compile(
      "(?i)-----BEGIN [A-Z0-9 ]*(?:PRIVATE KEY|SECRET KEY|PGP MESSAGE)-----");
  private static final Pattern SENSITIVE_FIELD = Pattern.compile(
      "(?i)(?<![a-z0-9])(?:password|passwd|passphrase|passcode|pwd|credentials?|secrets?"
          + "|(?:smtp|db|auth|user|database)[._-]?password|(?:client|shared)[._-]?secret"
          + "|(?:private|access|api|signing|encryption)[._-]?key|access[._-]?key[._-]?id|secret[._-]?access[._-]?key"
          + "|(?:access|refresh|id|auth|session|bearer|csrf)[._-]?token|token|session[._-]?id"
          + "|(?:proxy[._-]?)?authorization|(?:set[._-]?)?cookie|(?:request|response)[. _-]?body|body|payload)"
          + "[\"']?\\s*(?:[:=]|\\bis\\b)\\s*");
  private static final Pattern AUTH_VALUE = Pattern.compile(
      "(?i)\\b(Bearer|Basic)\\s+[A-Za-z0-9._~+/-]+=*(?=\\W|$)");
  private static final Pattern XML_SECRET = Pattern.compile(
      "(?i)<(password|passwd|passphrase|token|secret|api-key)\\s*>(?:.*?</\\1\\s*>|.*$)");
  private static final Pattern TOKEN = Pattern.compile(
      "\\b(?:eyJ[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]*"
          + "|(?:webui_|gh[pousr]_|github_pat_|xox[baprs]-|sk_(?:live|test)_|sk-)[A-Za-z0-9_-]+"
          + "|(?:AKIA|ASIA)[A-Z0-9]{16})(?![A-Za-z0-9_-])");
  private static final Pattern URL = Pattern.compile(
      "(?i)\\b[a-z][a-z0-9+.-]{0,31}://[^\\s\"'<>]+");
  private static final Pattern EMAIL = Pattern.compile(
      "(?i)(?<![A-Z0-9.!#$%&'*+/=?^_`{|}~-])[A-Z0-9.!#$%&'*+/=?^_`{|}~-]+"
          + "@[A-Z0-9-]++(?:\\.[A-Z0-9-]++){1,127}");

  private LogMessageRedactor() {}

  static String redact(String text, String knownSecret, boolean truncated) {
    if (truncated || UNSAFE.matcher(text).find() || PRIVATE_BLOCK.matcher(text).find()) {
      return WITHHELD;
    }

    var field = SENSITIVE_FIELD.matcher(text);
    var masked = new StringBuilder(text.length());
    int offset = 0;
    while (field.find(offset)) {
      int valueStart = field.end();
      masked.append(text, offset, valueStart);
      int end = quotedValueEnd(text, valueStart);
      if (end < 0) {
        // An unquoted value may include whitespace, commas or an entire request body.
        return redactIdentifiers(masked.append(MASK).toString(), knownSecret);
      }
      char quote = text.charAt(valueStart);
      masked.append(quote).append(MASK).append(quote);
      offset = end;
    }
    return redactIdentifiers(masked.append(text, offset, text.length()).toString(), knownSecret);
  }

  private static String maskKnownSecret(String text, String knownSecret) {
    if (knownSecret == null || knownSecret.isEmpty()) return text;
    String masked = text.replace(knownSecret, MASK);
    return masked.length() <= DiagnosticLogReader.MAX_EVENT ? masked : WITHHELD;
  }

  private static int quotedValueEnd(String text, int start) {
    if (start == text.length()) return -1;
    char quote = text.charAt(start);
    if (quote != '"' && quote != '\'') return -1;
    for (int i = start + 1; i < text.length(); i++) {
      if (text.charAt(i) == '\\') i++;
      else if (text.charAt(i) == quote) return i + 1;
    }
    return -1;
  }

  static String redactIdentifiers(String text, String knownSecret) {
    text = XML_SECRET.matcher(text).replaceAll("<$1>" + MASK + "</$1>");
    text = URL.matcher(text).replaceAll(match ->
        java.util.regex.Matcher.quoteReplacement(redactUrl(match.group())));
    text = AUTH_VALUE.matcher(text).replaceAll("$1 " + MASK);
    text = TOKEN.matcher(text).replaceAll(MASK);
    return maskKnownSecret(EMAIL.matcher(text).replaceAll(MASK), knownSecret);
  }

  private static String redactUrl(String url) {
    int authority = url.indexOf("://") + 3;
    int suffix = url.length();
    for (char delimiter : new char[] {'?', '#'}) {
      int position = url.indexOf(delimiter, authority);
      if (position >= 0) suffix = Math.min(suffix, position);
    }
    int path = url.indexOf('/', authority);
    int authorityEnd = path < 0 ? suffix : Math.min(path, suffix);
    int userInfo = url.lastIndexOf('@', authorityEnd - 1);
    String safe = userInfo >= authority
        ? url.substring(0, authority) + MASK + "@" + url.substring(userInfo + 1, suffix)
        : url.substring(0, suffix);
    return suffix < url.length() ? safe + url.charAt(suffix) + MASK : safe;
  }
}
