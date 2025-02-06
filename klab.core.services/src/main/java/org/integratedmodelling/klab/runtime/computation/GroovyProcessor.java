package org.integratedmodelling.klab.runtime.computation;

import groovyjarjarantlr.Token;
import groovyjarjarantlr.TokenStreamException;
import java.io.Reader;
import java.io.StringReader;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.codehaus.groovy.antlr.parser.GroovyLexer;
import org.integratedmodelling.klab.api.exceptions.KlabInternalErrorException;
import org.integratedmodelling.klab.api.knowledge.Expression;
import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.api.lang.ExpressionCode;
import org.integratedmodelling.klab.api.scope.Scope;
import org.integratedmodelling.klab.api.services.Language;
import org.integratedmodelling.klab.api.services.runtime.Notification;

public class GroovyProcessor implements Language.LanguageProcessor {

  public static final String LOCATOR_REGEXP = "@[A-Z]\\(.*?\\)";
  public static final String CONCEPT_REGEXP = "[a-z|\\.]+:[A-Za-z][A-Za-z0-9]*";
  public static final String OBSERVABLE_REGEXP = "\\{\\{.*\\}\\}";
  public static final String ENCODED_LOCATOR_REGEXP = "IL";
  public static final String ENCODED_METHOD_CALL_REGEXP = "I\\.[L|U](\\(.*?\\))?";

  public static final Pattern LOCATOR_PATTERN = Pattern.compile(LOCATOR_REGEXP);
  public static final Pattern CONCEPT_PATTERN = Pattern.compile(CONCEPT_REGEXP);
  public static final Pattern OBSERVABLE_PATTERN = Pattern.compile(OBSERVABLE_REGEXP);
  public static final Pattern ENCODED_LOCATOR_PATTERN = Pattern.compile(ENCODED_LOCATOR_REGEXP);
  public static final Pattern ENCODED_METHOD_CALL_PATTERN =
      Pattern.compile(ENCODED_METHOD_CALL_REGEXP);

  @Override
  public Expression.Descriptor analyze(
      ExpressionCode expression,
      Scope scope,
      List<Observable> outputs,
      List<Observable> inputs,
      Expression.CompilerOption... options) {
    return new GroovyDescriptor(expression, scope, outputs, inputs, options);
  }

  @Override
  public Expression compile(
      Expression.Descriptor descriptor, Expression.CompilerOption... options) {
    return descriptor.compile();
  }

  static class GroovyDescriptor implements Expression.Descriptor {

    private String processedCode;
    private Collection<String> identifiers = new LinkedHashSet<>();
    private Set<String> scalarIds = new LinkedHashSet<>();
    private Set<String> objectIds = new LinkedHashSet<>();
    private Set<String> contextualizers = new LinkedHashSet<>();
    private List<Notification> notifications = new ArrayList<>();
    //    private Map<String, Set<String>> mapIdentifiers;
    private Set<Expression.CompilerOption> options;
    //    private Parameters<String> variables;
    private final boolean forceScalar;
    private final Map<String, Observable> knownObservables = new HashMap<>();

    GroovyDescriptor(
        ExpressionCode expression,
        Scope scope,
        List<Observable> knownOutputs,
        List<Observable> knownInputs,
        Expression.CompilerOption... options) {

      this.options =
          options == null ? EnumSet.noneOf(Expression.CompilerOption.class) : Set.of(options);
      this.forceScalar = expression.isForcedScalar();

      for (int i = 0; i < knownOutputs.size(); i++) {
        var output = knownOutputs.get(i);
        var identifier =
            output.getStatedName() == null
                ? output.getSemantics().codeName()
                : output.getStatedName();
        knownObservables.put(identifier, output);
        if (i == 0) {
          knownObservables.put("self", output);
        }
      }

      for (int i = 0; i < knownInputs.size(); i++) {
        var input = knownInputs.get(i);
        var identifier =
            input.getStatedName() == null ? input.getSemantics().codeName() : input.getStatedName();
        knownObservables.put(identifier, input);
      }

      this.processedCode = preprocess(expression.getCode());
    }

    record TokenInfo(String code, String encoding, String translation, Observable observable) {}

    public String preprocess(String code) {

      /*
       * pre-substitute any \] with ] so that we can use the Groovy lexer without error. Don't
       * laugh at the pattern.
       */
      code = code.replaceAll("\\\\\\]", "]");

      Map<String, String> substitutions = new HashMap<>();
      code = performSubstitutions(code, substitutions);
      List<TokenInfo> tokens = new ArrayList<>();

      try {
        // first pass recognizing k.LAB-unique patterns
        var lexer = new Lexer(new StringReader(" " + code));
        lexer.setWhitespaceIncluded(false);
        lexer.consume();

        /*
         * Encode into a one-character-per-token to recognize the remaining patterns:
         *
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
         * the pattern in the list using same strategy as the substitutions
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

          var classified = classify(token, substitutions);
          compiled.append(classified.encoding);
          tokens.add(classified);
        }

        var encoded = compiled.toString();
        var templateFields = new ArrayList<String>();
        var templateLocalVariables = new ArrayList<String>();

        encoded =
            performSubstitution(
                ENCODED_LOCATOR_PATTERN,
                encoded,
                (p, n) -> {
                  // TODO redefine tokens: remove from starting point in number corresponding to
                  // token.length,
                  //  process, add single processed token with X type
                  return "X";
                });
        encoded =
            performSubstitution(
                ENCODED_METHOD_CALL_PATTERN,
                encoded,
                (p, n) -> {
                  var newToken =
                      swapTokens(
                          tokens,
                          p,
                          n,
                          tks -> {
                            /*
                            encoding should use identifierObs. reconstructed method call
                             */

                            return new TokenInfo("p", "Y", "TODO", null);
                          });

                  return "Y";
                });

        var preprocessedCode = new StringBuilder();

        // reconstruct the finalized expression while building template variables
        for (int i = 0; i < encoded.length(); i++) {
          var tokenInfo = tokens.get(i);
          preprocessedCode.append(switch (encoded.charAt(i)) { // TODO! Also add identifiers
            case 'I' -> {
              // ensure accessible; set scalar/vector flags
              yield tokenInfo.translation;
            }
            case 'U' -> {
              // TODO handle scale, scope, time, space, unknown etc. Also we probably need a klab object
              //  and the service handles. This works nicely because they get overridden if the inputs
              //  have the same name.
              yield tokenInfo.translation;
            }
            case 'L' -> {
              // LOCATOR call using scope etc, use a closure in fields or prepend local variable
              yield tokenInfo.translation;
            }
            case 'C' -> {
              // set variable field, return variable
              yield tokenInfo.translation;
            }
            case 'O' -> {
              // set variable field, return variable
              yield tokenInfo.translation;
            }
            case 'T', 'X', 'Y', '(', ')', '.' -> tokenInfo.translation;
            default ->
                throw new KlabInternalErrorException(
                    "wrong pattern encoding in expression preprocessor: " + code);
          });
        }

        return preprocessedCode.toString();

      } catch (Exception e) {

      }

      return null;
    }

    /**
     * Swap the tokens correspondent to the length of the string P in position N with a new token
     *
     * @param tokens
     * @param p
     * @param n
     * @param tokenInitializer consumer that takes the removed tokens and the newly created one
     * @return
     */
    private TokenInfo swapTokens(
        List<TokenInfo> tokens,
        String p,
        Integer n,
        Function<List<TokenInfo>, TokenInfo> tokenInitializer) {

      List<TokenInfo> removed = new ArrayList<>();
      for (int i = 0; i < p.length(); i++) {
        removed.add(tokens.remove(n.intValue()));
      }

      var ret = tokenInitializer.apply(removed);
      tokens.add(n,ret);
      return ret;
    }

    private TokenInfo classify(Token token, Map<String, String> substitutions) {

      String code = token.getText();
      String encoding = null;
      String translation = null;
      Observable observable = null;

      if (code.startsWith("__L__")) {
        encoding = "L";
        translation = substitutions.get(code);
      } else if (code.startsWith("__O__")) {
        encoding = "O";
        translation = substitutions.get(code);
      } else if (code.startsWith("__C__")) {
        encoding = "C";
        translation = substitutions.get(code);
      } else if (token.getType() == GroovyLexer.IDENT) {
        if (knownObservables.containsKey(code)) {
          observable = knownObservables.get(code);
          encoding = "I";
        } else {
          encoding = "U";
        }
      } else if ("(".equals(code) || ")".equals(code) || ".".equals(code)) {
        encoding = code;
        translation = code;
      } else {
        encoding = "T";
        translation = code;
      }

      return new TokenInfo(code, encoding, translation, observable);
    }

    private String performSubstitutions(String code, Map<String, String> substitutions) {

      final AtomicInteger locatorId = new AtomicInteger(0);
      final AtomicInteger observableId = new AtomicInteger(0);
      final AtomicInteger conceptId = new AtomicInteger(0);

      // TODO the map should be a bimap and reuse tokens if the value is already present

      code =
          performSubstitution(
              LOCATOR_PATTERN,
              code,
              (x, n) -> {
                var ret = " __L__" + locatorId.getAndIncrement() + " ";
                substitutions.put(ret, x);
                return ret;
              });
      code =
          performSubstitution(
              OBSERVABLE_PATTERN,
              code,
              (x, n) -> {
                var ret = " __O__" + observableId.getAndIncrement() + " ";
                substitutions.put(ret, x.substring(2, x.length() - 2));
                return ret;
              });
      return performSubstitution(
          CONCEPT_PATTERN,
          code,
          (x, n) -> {
            var ret = " __C__" + conceptId.getAndIncrement() + " ";
            substitutions.put(ret, x);
            return ret;
          });
    }

    private static String performSubstitution(
        Pattern pattern, String code, BiFunction<String, Integer, String> translator) {
      StringBuilder output = new StringBuilder();
      Matcher matcher = pattern.matcher(code);
      int lastIndex = 0;
      while (matcher.find()) {
        output
            .append(code, lastIndex, matcher.start())
            .append(
                translator.apply(code.substring(matcher.start(), matcher.end()), matcher.start()));

        lastIndex = matcher.end();
      }
      if (lastIndex < code.length()) {
        output.append(code, lastIndex, code.length());
      }
      return output.toString();
    }

    @Override
    public Collection<String> getIdentifiers() {
      return identifiers;
    }

    //    @Override
    //    public Collection<Expression.CompilerOption> getOptions() {
    //      return options;
    //    }

    @Override
    public boolean isScalar(Collection<String> stateIdentifiers) {

      for (String id : stateIdentifiers) {
        if (this.scalarIds.contains(id)) {
          return true;
        }
      }
      return false;
    }

    @Override
    public List<Notification> getNotifications() {
      return notifications;
    }

    @Override
    public Collection<String> getIdentifiersInScalarScope() {
      return this.scalarIds;
    }

    @Override
    public Collection<String> getIdentifiersInNonscalarScope() {
      return this.objectIds;
    }

    @Override
    public Expression compile() {
      return new GroovyExpression(processedCode, true, this);
    }

    //    @Override
    //    public Expression.Descriptor scalar(Expression.Forcing forcing) {
    //      return null;
    //    }

    @Override
    public boolean isScalar(String identifier) {
      return scalarIds.contains(identifier);
    }

    @Override
    public boolean isNonscalar(String identifier) {
      return objectIds.contains(identifier);
    }

    @Override
    public boolean isNonscalar(Collection<String> stateIdentifiers) {
      for (String id : stateIdentifiers) {
        if (this.objectIds.contains(id)) {
          return true;
        }
      }
      return false;
    }

    @Override
    public Collection<String> getContextualizers() {
      return contextualizers;
    }

    //    @Override
    //    public Map<String, Set<String>> getMapIdentifiers() {
    //      return mapIdentifiers;
    //    }
    //
    //    @Override
    //    public Parameters<String> getVariables() {
    //      return variables;
    //    }

    @Override
    public boolean isScalar() {

      if (forceScalar) {
        return true;
      }

      for (String id : scalarIds) {
        if (isScalar(id)) {
          return true;
        }
      }

      return false;
    }
  }

  static class Lexer extends GroovyLexer {
    //
    //    int previous = -1;

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
}
