/*******************************************************************************
 * Copyright (C) 2007, 2015:
 *
 * - Ferdinando Villa <ferdinando.villa@bc3research.org> - integratedmodelling.org - any other
 * authors listed in @author annotations
 *
 * All rights reserved. This file is part of the k.LAB software suite, meant to enable modular,
 * collaborative, integrated development of interoperable data and model components. For details,
 * see http://integratedmodelling.org.
 *
 * This program is free software; you can redistribute it and/or modify it under the terms of the
 * Affero General Public License Version 3 or any later version.
 *
 * This program is distributed in the hope that it will be useful, but without any warranty; without
 * even the implied warranty of merchantability or fitness for a particular purpose. See the Affero
 * General Public License for more details.
 *
 * You should have received a copy of the Affero General Public License along with this program; if
 * not, write to the Free Software Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA
 * 02111-1307, USA. The license is also available at: https://www.gnu.org/licenses/agpl.html
 *******************************************************************************/
package org.integratedmodelling.klab.runtime.computation;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import groovyjarjarantlr.Token;
import groovyjarjarantlr.TokenStreamException;
import java.io.Reader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.codehaus.groovy.antlr.parser.GroovyLexer;
import org.integratedmodelling.klab.api.exceptions.KlabInternalErrorException;
import org.integratedmodelling.klab.api.knowledge.Concept;
import org.integratedmodelling.klab.api.knowledge.Expression;
import org.integratedmodelling.klab.api.knowledge.Knowledge;
import org.integratedmodelling.klab.api.knowledge.SemanticType;
import org.integratedmodelling.klab.api.lang.kim.KimNamespace;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.scope.Scope;
import org.integratedmodelling.klab.api.services.Reasoner;
import org.integratedmodelling.klab.api.services.runtime.Notification;
import org.springframework.util.StringUtils;

/**
 * Smarter preprocessor to produce proper Groovy from k.LAB expressions.
 *
 * <p>Disables Groovy's slashy strings using a trick to prevent the infinitely more important
 * division operator from causing lexer errors.
 *
 * <p>Usage: create, call process(string), use errors and statistics as necessary. All statistics
 * are reset by another call to process().
 *
 * <p>Pass the catalog from the scope, the scale and options to specify the contextualization scope.
 *
 * <p>All states are referenced directly; if in scalar scope, the scale variable must be defined
 * locally and the reference to the state is processed into state.get(scale). Otherwise it's left as
 * is. Overall scale is taken from the context (context.scale).
 *
 * <p>Observables are parsed from #(...) forms, concepts are automatically recognized using pattern
 * matching. Unknown concepts are substituted with owl:Nothing and an error is logged.
 *
 * @author Ferd
 */
public class GroovyExpressionPreprocessor {

  public static final String LOCATOR_REGEXP = "@[A-Z]\\(.*?\\)";
  public static final String CONCEPT_REGEXP = "[a-z|\\.]+:[A-Za-z][A-Za-z0-9]*";
  public static final String OBSERVABLE_REGEXP = "\\{\\{.*\\}\\}";

  // private IGeometry domains;
  private final Scope scope;
  private KimNamespace namespace;
  // private Set<String> knownIdentifiers;
  // private Geometry inferredGeometry;
  Pattern extKnowledge = Pattern.compile("[a-z|\\.]+:[A-Za-z][A-Za-z0-9]*");

  private static final String opchars = "{}[]()+-*/%^&|<>~?:='\"";

  static class Lexer extends GroovyLexer {

    int previous = -1;

    public Lexer(Reader in) {
      super(in);
    }

    @Override
    public Token nextToken() throws TokenStreamException {
      Token t = super.nextToken();
      /*
       * cheat Groovy into thinking that it just saw an integer, so that it won't try to
       * interpret slashes as string separators.
       */
      lastSigTokenType = GroovyLexer.NUM_INT;
      return t;
    }
  }

  /*
   * what separates Groovy words and not necessarily k.LAB's.
   */
  static Set<String> delimiters;
  static Set<String> locators;

  static {
    delimiters = new HashSet<>();
    delimiters.add(":");
    delimiters.add(".");
    delimiters.add("-");

    locators = new HashSet<>();
    locators.add("n");
    locators.add("ne");
    locators.add("nw");
    locators.add("s");
    locators.add("se");
    locators.add("sw");
    locators.add("e");
    locators.add("w");
    locators.add("previous");
    locators.add("next");
  }

  /*
   * referenced IDs and knowledge resulting from preprocessing.
   */
  Set<String> identifiers = new HashSet<>();
  List<Knowledge> knowledge = new ArrayList<>();
  List<Notification> errors = new ArrayList<>();
  private Set<String> objectIds = new HashSet<>();
  private Set<String> scalarIds = new HashSet<>();
  private Set<String> contextualizers = new HashSet<>();
  Expression.Scope expressionScope;
  private boolean expectDirectData;
  private boolean recontextualizeAsMap;
  /*
   * if recontextualizeAsMap is passed, identifiers like id@ctx are translated as id["ctx"] and
   * the ID plus any key encountered are placed here.
   */
  private Map<String, Set<String>> mapIdentifiers = new HashMap<>();
  private boolean ignoreContext;
  private boolean doNotProcess;

  enum TokenType {
    KNOWLEDGE,
    DEFINE,
    KNOWN_ID,
    UNKNOWN_ID,
    URN,
    LITERAL_NULL,
    RECONTEXTUALIZATION,
    KNOWN_MODEL_OBJECT,
    NEWLINE,
    INFERENCE,
    WHITESPACE
  }

  private static final String CONCEPT_PATTERN = "[a-z][a-z,\\.]+:([A-Z][a-z0-9]+)+";

  private BiMap<String, Object> inherentVariables = HashBiMap.create();
  private Pattern conceptPattern = Pattern.compile(CONCEPT_PATTERN);
  private boolean ignoreRecontextualization = false;

  public GroovyExpressionPreprocessor(
      Scope scope,
      //      KimNamespace currentNamespace,
      //      Expression.Scope expressionScope,
      Set<Expression.CompilerOption> options) {
    //    this.namespace = currentNamespace;
    //    this.expressionScope = expressionScope;
    this.scope = scope;
    this.recontextualizeAsMap = options.contains(Expression.CompilerOption.RecontextualizeAsMap);
    this.ignoreContext = options.contains(Expression.CompilerOption.IgnoreContext);
    this.doNotProcess = options.contains(Expression.CompilerOption.DoNotPreprocess);
    this.ignoreRecontextualization =
        options.contains(Expression.CompilerOption.IgnoreRecontextualization);
    this.expectDirectData = options.contains(Expression.CompilerOption.DirectQualityAccess);
  }

  /**
   * Return the known identifiers that were used in a scalar context
   *
   * @return all scalar identifiers
   */
  public Set<String> getScalarIdentifiers() {
    return scalarIds;
  }

  /**
   * Return the known identifiers that were used as objects
   *
   * @return all object identifiers
   */
  public Set<String> getObjectIdentifiers() {
    return objectIds;
  }

  class TokenDescriptor {

    TokenType type;
    String token;
    int tokenIndex;
    List<TokenDescriptor> tokens;

    // method in the next token - set by analyze(), used to check usage before
    // translation
    String method;
    boolean methodCall = false;

    public TokenDescriptor(TokenType type, String token) {
      this.type = type;
      this.token = token;
      if ("isa".equals(token) || "is".equals(token)) {
        this.type = TokenType.INFERENCE;
      }
    }

    public String toString() {
      return this.type + ": " + this.token + (this.methodCall ? " (method)" : "");
    }

    public String translate(int index, List<TokenDescriptor> tokens) {

      String ret = token;

      // next significant token
      int next = index + 1;
      TokenDescriptor nextToken = null;
      while (next < tokens.size()) {
        if (tokens.get(next).type != TokenType.WHITESPACE
            && tokens.get(next).type != TokenType.NEWLINE) {
          nextToken = tokens.get(next);
          break;
        }
        next++;
      }

      switch (type) {
        case RECONTEXTUALIZATION:
          if (nextToken != null && locators.contains(nextToken.token)) {
            nextToken.token = "\"" + nextToken.token + "\"";
          }
          ret = "<<";
          break;
        case INFERENCE:
          ret = "is".equals(ret) ? "<<" : ">>";
          break;
        case KNOWLEDGE:
          ret = translateKnowledge(ret);
          break;
        case DEFINE:
          ret = translateDefine(ret);
          break;
        case KNOWN_ID:
          var type = getIdentifierType(token);
          if (expressionScope.getCompilerScope() == Expression.CompilerScope.Scalar
              && type == SemanticType.QUALITY
              && !methodCall) {
            if (nextToken != null && nextToken.type == TokenType.RECONTEXTUALIZATION) {
              ret = token + ".getProxy(scale)";
            } else {
              ret = expectDirectData ? ("_" + token) : (token + ".get(scale)");
            }
          } else if (type == SemanticType.QUALITY && methodCall) {
            ret = token + ".getStateProxy(scale)";
          } else {
            // just the variable
            ret = token;
          }
          break;
        case URN:
          ret = translateUrn(ret);
          break;
        case KNOWN_MODEL_OBJECT:
          ret = translateModelObject(ret);
          break;
        case LITERAL_NULL:
          ret = "null";
          break;
        default:
          // just add the literal content to the expression
          break;
      }
      return ret;
    }
  }

  private List<TokenDescriptor> tokenize(String code) {

    List<TokenDescriptor> ret = new ArrayList<>();

    List<List<Token>> groups = new ArrayList<>();
    Lexer lexer = new Lexer(new StringReader(code));
    lexer.setWhitespaceIncluded(true);
    // String ret = "";
    // String remainder = "";

    try {
      lexer.consume();
      List<Token> acc = new ArrayList<>();
      boolean isSpecial = false;
      boolean wasSpecial = false;
      while (true) {

        Token token = lexer.nextToken();
        isSpecial =
            token != null
                && (token.getType() == GroovyLexer.IDENT || delimiters.contains(token.getText()));
        boolean isEof = token == null || token.getType() == Token.EOF_TYPE;
        if (!acc.isEmpty()
            && (!isSpecial || isEof || (isSpecial && !wasSpecial) || isRecognized(acc))) {
          /*
           * if last parsed token is a namespace and this is a colon, keep the same group
           * TODO all this must be brought back to some level of sanity with a true state
           * processor.
           */
          Token last = acc.get(acc.size() - 1);
          //          boolean conceptPrefix =
          //              ":".equals(token.getText())
          //                  && Namespaces.INSTANCE.getNamespace(last.getText()) != null;
          //          if (!conceptPrefix) {
          //            groups.add(acc);
          //            acc = new ArrayList<>();
          //          }
        }
        if (isEof) {
          break;
        }
        wasSpecial = isSpecial;
        acc.add(token);
      }

    } catch (Exception e) {
      throw new KlabInternalErrorException(e);
    }

    int line = -1;
    for (List<Token> group : groups) {

      String tk = join(group);
      int tline = lastLine(group);

      if (line < 0) {
        line = tline;
      } else if (tline > line) {
        line = tline;
        ret.add(new TokenDescriptor(TokenType.NEWLINE, "\n"));
      }

      ret.add(classify(tk));
    }

    return ret;
  }

  /**
   * Initial substitutions
   *
   * @param code
   * @return
   */
  String preprocess(String code) {

    identifiers.clear();
    knowledge.clear();

    /*
     * mysterious, but if I don't do this, the lexer will cut off the first character.
     */
    code = " " + code + " ";

    /*
     * pre-substitute any \] with ] so that we can use the Groovy lexer without error. Don't
     * laugh at the pattern.
     */
    code = code.replaceAll("\\\\\\]", "]");

    // substitute all #(...) declarations with variables
    code = preprocessDeclarations(code);
    // ...then the remaining concepts outside of observable declarations
    code = preprocessConcepts(code);
    // code = preprocessContextualizations(code);

    return code;
  }

  public String process(String code) {

    String ret = preprocess(code);

    if (this.doNotProcess) {
      return ret;
    }

    List<TokenDescriptor> tokens = tokenize(ret);
    analyze(tokens);
    ret = reconstruct(tokens);

    return ret;
  }

  private String preprocessDeclarations(String code) {

    StringBuffer ret = new StringBuffer(code.length());
    int level = -1;
    for (int i = 0; i < code.length(); i++) {
      char c = code.charAt(i);
      StringBuffer declaration = new StringBuffer(code.length());
      if (c == '#' && i < code.length() - 2 && code.charAt(i + 1) == '(') {
        i += 2;
        for (; i < code.length(); i++) {
          if (code.charAt(i) == '(') {
            level++;
            declaration.append(code.charAt(i));
          } else if (code.charAt(i) == ')') {
            if (level == -1) {
              break;
            } else {
              level--;
              declaration.append(code.charAt(i));
            }
          } else {
            declaration.append(code.charAt(i));
          }
        }

        if (level >= 0) {
          errors.add(
              Notification.error(
                  "concept declaration: unexpected end of input: " + declaration.toString()));
        } else {
          //          ret.append(encodeValue(Observables.INSTANCE.declare(declaration.toString())));
        }

      } else {
        ret.append(c);
      }
    }
    return ret.toString();
  }

  private String encodeValue(Object value) {
    if (this.inherentVariables.containsValue(value)) {
      return this.inherentVariables.inverse().get(value);
    }
    String code = "__V" + (this.inherentVariables.size() + 1);
    this.inherentVariables.put(code, value);
    return code;
  }

  private String preprocessConcepts(String code) {

    Matcher matcher = conceptPattern.matcher(code);
    return matcher.replaceAll(
        (result) -> {
          String val = result.group();
          Concept c = scope.getService(Reasoner.class).resolveConcept(val);
          if (c == null) {
            c = Concept.nothing();
            errors.add(Notification.error("Concept " + val + " is not recognized"));
          }

          return encodeValue(c);
        });
  }

  public SemanticType getIdentifierType(String identifier) {

    if (expressionScope == null) {
      return SemanticType.PRIORITY;
    }

    if (identifier.equals("self")) {
      return expressionScope.getReturnType();
    }
    var ret = expressionScope.getIdentifierType(identifier);
    if (ret != null) {
      return ret;
    }
    return SemanticType.OBSERVABLE;
  }

  private String reconstruct(List<TokenDescriptor> tokens) {

    String ret = "";

    /*
     * if we have a known quality id followed by @, translate it to state.getProxy(locator)
     * which can be further located reacting to the << operator, then translate the @ operator
     * to <<. After @ turn any naked identifier recognizable as a locator into a string, or
     * leave as is. If the @ does not follow a quality var, raise an error.
     */

    int i = 0;
    for (TokenDescriptor t : tokens) {
      ret += t.type == TokenType.NEWLINE ? "\n" : t.translate(i, tokens);
      i++;
    }

    return ret;
  }

  /** Recognize calls to a known identifier as non-scalar usage */
  private void analyze(List<TokenDescriptor> tokens) {

    TokenDescriptor current = null;
    for (TokenDescriptor t : tokens) {
      if (t.type == TokenType.KNOWN_ID) {
        current = t;
      } else if (t.type == TokenType.UNKNOWN_ID) {
        if (current != null && t.token.trim().startsWith(".")) {
          current.methodCall = true;
          current.method = t.token.trim().substring(1);
        }
        current = null;
      }
    }

    for (TokenDescriptor t : tokens) {
      if (t.type == TokenType.KNOWN_ID
          || (t.type == TokenType.UNKNOWN_ID
              && expressionScope.getIdentifiers().contains(t.token.trim()))) {
        identifiers.add(t.token.trim());
        if (t.methodCall) {
          this.objectIds.add(t.token.trim());
        } else {
          this.scalarIds.add(t.token.trim());
        }
      }
    }
  }

  private int lastLine(List<Token> group) {
    int line = -1;
    for (Token t : group) {
      line = t.getLine();
    }
    return line;
  }

  private boolean isRecognized(List<Token> acc) {
    return classify(join(acc)).type != TokenType.UNKNOWN_ID;
  }

  private String join(List<Token> group) {
    String ret = "";
    for (Token t : group) {
      ret += getText(t);
    }
    return ret;
  }

  private String getText(Token t) {

    switch (t.getType()) {
      case GroovyLexer.STRING_LITERAL:
        String delimiter = getDelimiter(t);
        return delimiter + t.getText() + delimiter;
    }
    return t.getText();
  }

  private String getDelimiter(Token t) {
    return t.getText().contains("\n") ? "\"\"\"" : "\"";
  }

  private TokenDescriptor classify(String currentToken) {

    /*
     * known ones. Also ensure that "space" and "time" go through unmodified unless the domains
     * do not know them.
     */
    if (currentToken.equals("unknown")) {
      return new TokenDescriptor(TokenType.LITERAL_NULL, currentToken);
    }

    if ("@".equals(currentToken) && !ignoreRecontextualization) {
      return new TokenDescriptor(TokenType.RECONTEXTUALIZATION, currentToken);
    }

    if (expressionScope.getIdentifiers().contains(currentToken)) {
      return new TokenDescriptor(TokenType.KNOWN_ID, currentToken);
    }

    Knowledge k = null;
    //    IKimObject o = null;

    if (currentToken.contains(":")) {
      if (StringUtils.countOccurrencesOf(currentToken, ":") == 3
          && !currentToken.endsWith(":")
          && !StringUtils.containsWhitespace(currentToken)) {
        return new TokenDescriptor(TokenType.URN, currentToken);
      } else if ((k = scope.getService(Reasoner.class).resolveConcept(currentToken)) != null) {
        return new TokenDescriptor(TokenType.KNOWLEDGE, k.toString());
      }
    }

    if (namespace != null) {
      //      if (namespace.getSymbolTable().get(currentToken) != null) {
      //        return new TokenDescriptor(TokenType.DEFINE, currentToken);
      //      }
      //      if ((k = namespace.getOntology().getConcept(currentToken)) != null) {
      //        return new TokenDescriptor(TokenType.KNOWLEDGE, k.toString());
      //      }
      //      if (!namespace.isProjectKnowledge()
      //          && namespace.getProject() != null
      //          && namespace.getProject().getUserKnowledge() != null
      //          &&
      // namespace.getProject().getUserKnowledge().getOntology().getConcept(currentToken)
      //              != null) {
      //        return new TokenDescriptor(
      //            TokenType.KNOWLEDGE,
      //            namespace
      //                .getProject()
      //                .getUserKnowledge()
      //                .getOntology()
      //                .getConcept(currentToken)
      //                .toString());
      //      }
      //      if ((k = namespace.getOntology().getProperty(currentToken)) != null) {
      //        return new TokenDescriptor(TokenType.KNOWLEDGE, k.toString());
      //      }
      //      if ((o = namespace.getObject(currentToken)) != null) {
      //        return new TokenDescriptor(TokenType.KNOWN_MODEL_OBJECT, o.getName());
      //      }
    }

    if (currentToken.contains(".")) {
      //      if ((o = Resources.INSTANCE.getModelObject(currentToken)) != null) {
      //        return new TokenDescriptor(TokenType.KNOWN_MODEL_OBJECT, o.getName());
      //      }
    }

    if (expressionScope.getCompilerScope() != Expression.CompilerScope.Contextual
        && isValidIdentifier(currentToken)) {
      return new TokenDescriptor(TokenType.KNOWN_ID, currentToken);
    }

    if (currentToken.trim().isEmpty()) {
      return new TokenDescriptor(TokenType.WHITESPACE, currentToken);
    }

    return new TokenDescriptor(TokenType.UNKNOWN_ID, currentToken);
  }

  /*
   * Used only when context is null to discriminate identifiers without a list of known ones. The
   * definition is quite restrictive.
   */
  private boolean isValidIdentifier(String token) {
    for (int i = 0; i < token.length(); i++) {
      char c = token.charAt(i);
      if ((c < 'a' || c > 'z') && !(c == '_')) {
        return false;
      }
    }
    return true;
  }

  private String translateModelObject(String o) {
    return "_mmanager.findModelObject(\"" + o + "\")";
  }

  private String translateDefine(String currentToken) {
    return "_ns.getSymbolTable().get(\"" + currentToken + "\")";
  }

  private String translateKnowledge(String k) {
    return encodeValue(scope.getService(Reasoner.class).resolveConcept(k));
  }

  private String translateUrn(String k) {
    return "_getUrn(\"" + k + "\")";
  }

  public List<Notification> getErrors() {
    return errors;
  }

  /**
   * All the identifiers encountered. TODO return a structure to show how they have been used - if
   * they are followed by assignment operators, other ops or method calls.
   *
   * @return identifiers encountered during preprocessing.
   */
  public Collection<String> getIdentifiers() {
    return identifiers;
  }

  public static void main(String[] dio) throws Exception {

    String code =
        " (elevation - slope@S(nw)/elevation.max) is {{uffa:Eccheccazzo bestia qua ci va anche la merda 123 }} with canna:Sperma, vecchio.peto:DiCane";

    Map<String, String> substitutions = new HashMap<>();
    code = performSubstitutions(code, substitutions);

    System.out.println("CAZZONE SOSTITUITO: " + code);

    //

    /**
     * 1. Find all concepts, {{....}} and localizers @S() @T() using regexp matching and substitute
     * with identifiers, add to known identifiers to add to the constants.
     *
     * <p>2. Process each token and substitute each with a single character, token itself for
     * operators, I for known identifier, U for unknown, N for numbers and the like. Save start and
     * end offsets in code for each of them along with the processed pattern match.
     *
     * <p>Use regexp patterns over the string to find out the patterns; group tokens into marked
     * patterns
     *
     * <p>Go over the pattern list and compute the translation as required per group of tokens
     *
     * <p>Rebuild string by reassembling the recomputed identifiers
     */
    Lexer lexer = new Lexer(new StringReader(code));
    lexer.setWhitespaceIncluded(false);
    lexer.consume();
    List<Token> acc = new ArrayList<>();

    /**
     * Compiled string contains a character per token identified with the following:
     * ., ( and ) are literal;
     * I = known identifier
     * U = unknown identifier
     * L = previously parsed locator
     * C = previously parsed concept literal
     * O = previously parsed observable literal
     * T = anything else
     *
     * Keep a parallel list with the actual tokens along with their category matched by character index
     *
     * Recognize:
     *    IL -> LOCATED_IDENTIFIER
     *    I.U and I.I optionally followed by (L*) -> IDENTIFIER_METHOD_CALL
     *
     * Substitute these patterns as X and Y; change the corresponding list elements with the compound values of
     * the pattern in the list
     *
     * Reassemble the expression using the token list with all the substitutions and related actions (add fields, call
     * functions etc.)
     */
    StringBuilder compiled = new StringBuilder();
    while (true) {
      Token token = lexer.nextToken();
      if (token == null || token.getType() == Token.EOF_TYPE) {
        break;
      }
//      compiled.append(classify(token, n, klabTokens)  -- ADD each token's interpretation to a list with index = the char
    }
  }

  public static String performSubstitutions(String code, Map<String, String> substitutions) {

    final AtomicInteger locatorId = new AtomicInteger(0);
    final AtomicInteger observableId = new AtomicInteger(0);
    final AtomicInteger conceptId = new AtomicInteger(0);

    // TODO the map should be a bimap and reuse tokens if the value is already present

    code =
        performSubstitution(
            Pattern.compile(LOCATOR_REGEXP),
            code,
            x -> {
              var ret = " __L__" + locatorId.getAndIncrement() + " ";
              substitutions.put(ret, x);
              return ret;
            });
    code =
        performSubstitution(
            Pattern.compile(OBSERVABLE_REGEXP),
            code,
            x -> {
              var ret = " __O__" + observableId.getAndIncrement() + " ";
              substitutions.put(ret, x);
              return ret;
            });
    return performSubstitution(
        Pattern.compile(CONCEPT_REGEXP),
        code,
        x -> {
          var ret = " __C__" + conceptId.getAndIncrement() + " ";
          substitutions.put(ret, x);
          return ret;
        });
  }

  private static String performSubstitution(
      Pattern pattern, String code, Function<String, String> translator) {
    StringBuilder output = new StringBuilder();
    Matcher matcher = pattern.matcher(code);
    int lastIndex = 0;
    while (matcher.find()) {
      output
          .append(code, lastIndex, matcher.start())
          .append(translator.apply(code.substring(matcher.start(), matcher.end())));

      lastIndex = matcher.end();
    }
    if (lastIndex < code.length()) {
      output.append(code, lastIndex, code.length());
    }
    return output.toString();
  }

  /**
   * All contextualizers encountered, divided up and without distance locators.
   *
   * @return
   */
  public Set<String> getContextualizers() {
    return contextualizers;
  }

  public Map<String, Set<String>> getMapIdentifiers() {
    return mapIdentifiers;
  }

  public Map<String, Object> getVariables() {
    return inherentVariables;
  }
}
