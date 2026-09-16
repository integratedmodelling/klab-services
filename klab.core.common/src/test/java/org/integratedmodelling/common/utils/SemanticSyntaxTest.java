package org.integratedmodelling.common.utils;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class SemanticSyntaxTest {
  @Test void stripsOnlyWholeExpressionGroups() {
    assertEquals("test:A", SemanticSyntax.stripOuterParentheses(" (( test:A )) "));
    assertEquals("(test:A) or (test:B)", SemanticSyntax.stripOuterParentheses("(test:A) or (test:B)"));
    assertEquals("(test:A) or (test:B)", SemanticSyntax.stripOuterParentheses("((test:A) or (test:B))"));
  }
  @Test void respectsQuotedParenthesesAndLeavesMalformedInputForTheParser() {
    assertEquals("test:A = '\u0029'", SemanticSyntax.stripOuterParentheses("(test:A = '\u0029')"));
    assertEquals("((test:A)", SemanticSyntax.stripOuterParentheses("((test:A)"));
    assertEquals("(test:A))", SemanticSyntax.stripOuterParentheses("(test:A))"));
  }
}
