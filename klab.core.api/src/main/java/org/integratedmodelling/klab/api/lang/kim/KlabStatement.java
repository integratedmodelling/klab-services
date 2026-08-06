package org.integratedmodelling.klab.api.lang.kim;

import org.integratedmodelling.klab.api.data.Metadata;
import org.integratedmodelling.klab.api.knowledge.KlabAsset;
import org.integratedmodelling.klab.api.lang.Statement;

/**
 * @author Ferd
 */
public interface KlabStatement extends Statement, KlabAsset {

  enum LexicalRole {
    KEYWORD,
    OPERATOR,
    CONCEPT,
    UNIT,
    CURRENCY,
    STRING,
    NUMBER,
    PREFIX_SEMANTIC_OPERATOR,
    INFIX_SEMANTIC_OPERATOR,
    EXPRESSION_CODE,
    OPEN_PARENTHESIS,
    CLOSED_PARENTHESIS,
    OPEN_BRACE,
    CLOSED_BRACE,
    /**
     * Separators have semantic value: comma, semicolon, etc. Whitespace is not a separator and
     * should never be added.
     */
    SEPARATOR,
    COMMENT,
  }

  /**
   * Appender object used to produce code fragments. In the simplest case, it's a string builder
   * that ignores the role of each token and just outputs them as a string separated by spaces. Can
   * be used to produce formatted, indented and styled code.
   *
   * @param <T> the output type - String, formatted string, etc.
   */
  interface CodeAppender<T> {

    /**
     * Append a token to the output. This will be called along with the lexical role of the token.
     * Whitespace is not appended and is at the discretion of the implementation, although it is
     * necessary after non-separator tokens to ensure correct output.
     *
     * @param token
     * @param role
     * @param parameters optional parameters - usually related to what the token represents so that
     *     the formatter can be more precise.
     */
    void append(String token, LexicalRole role, Object... parameters);

    /**
     * Produce the final output as the return object.
     *
     * @return
     */
    T output();
  }

  /**
   * Scope is relevant to models and namespaces, where it affects resolution of models.
   *
   * @author Ferd
   */
  enum Scope {
    PUBLIC,
    PRIVATE,
    PROJECT_PRIVATE;

    public Scope narrowest(Scope... scopes) {
      Scope ret = scopes == null || scopes.length == 0 ? null : scopes[0];
      if (ret != null) {
        for (int i = 1; i < scopes.length; i++) {
          if (scopes[i].ordinal() < ret.ordinal()) {
            ret = scopes[i];
          }
        }
      }
      return ret;
    }
  }

  /**
   * The namespace ID for this object. For a KimNamespace it's also the official name (there is no
   * getName()).
   *
   * @return the namespace or null
   */
  String getNamespace();

  /**
   * Statement are usually defined within a project, unless they're "unhinged" observables and
   * concepts.
   *
   * @return the project or null
   */
  String getProjectName();

  /**
   * The knowledge class of the containing document, if any (or if this is a document). Used for
   * reporting and to compile portable parsing results.
   *
   * @return the class of the (containing) document, or null if the statement was defined outside of
   *     one.
   */
  KnowledgeClass getDocumentClass();

  /**
   * Scope can be declared for namespaces and models. Default is public or whatever the containing
   * namespace scope is. Concepts unfortunately cannot be scoped with current infrastructure.
   *
   * @return
   */
  Scope getScope();

  /**
   * Use the specified appender to obtain a formatted fragment of code specifying this statement.
   *
   * @param appender
   * @return
   * @param <T>
   */
  <T> T format(CodeAppender<T> appender);

}
