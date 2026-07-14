package org.integratedmodelling.klab.runtime.computation;

import groovy.lang.GroovyShell;
import groovyjarjarantlr.Token;
import groovyjarjarantlr.TokenStreamException;
import java.io.Reader;
import java.io.Serializable;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.codehaus.groovy.antlr.parser.GroovyLexer;
import org.integratedmodelling.common.utils.Utils;
import org.integratedmodelling.klab.api.exceptions.KlabInternalErrorException;
import org.integratedmodelling.klab.api.knowledge.Expression;
import org.integratedmodelling.klab.api.knowledge.SemanticType;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.lang.ExpressionCode;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.scope.Scope;
import org.integratedmodelling.klab.api.services.Language;
import org.integratedmodelling.klab.api.services.runtime.Dataflow;
import org.integratedmodelling.klab.api.services.runtime.Notification;

/**
 * FIXME this uses Groovy 3.0 lexer - and the implementation uses 4.0. No conflict but it's not
 * nice.
 */
public class GroovyProcessor implements Language.LanguageProcessor {

  public static final String LOCATOR_REGEXP = "@[A-Z]\\(.*?\\)";
  public static final String CONCEPT_REGEXP =
      "(?<![A-Za-z0-9_.-])[a-z][a-z0-9_.-]*:[A-Za-z][A-Za-z0-9_]*(?![A-Za-z0-9_])";
  public static final String OBSERVABLE_REGEXP = "\\{\\{.*?\\}\\}";
  public static final String ENCODED_LOCATOR_REGEXP = "IW*L";
  public static final String ENCODED_METHOD_CALL_REGEXP = "IW*(?:Q|T?\\.)W*[IU]";

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
      List<Observation> outputs,
      List<Observation> inputs,
      Expression.CompilerOption... options) {
    return new GroovyDescriptor(expression, scope, outputs, inputs, options);
  }

  @Override
  public Expression compile(
      Expression.Descriptor descriptor, Expression.CompilerOption... options) {
    return descriptor.compile();
  }

  static class IdImpl implements Expression.Descriptor.Identifier {

    private String name;
    private Observation observation;
    private Class<?> runtimeClass;
    private int scalarReferenceCount;
    private int nonScalarReferenceCount;
    private final List<String> methodsCalled = new ArrayList<>();
    private boolean predefined;

    public IdImpl(String name) {
      this.name = name;
    }

    @Override
    public String name() {
      return this.name;
    }

    @Override
    public Observation observation() {
      return this.observation;
    }

    @Override
    public Class<?> runtimeClass() {
      return this.runtimeClass;
    }

    @Override
    public int scalarReferenceCount() {
      return this.scalarReferenceCount;
    }

    @Override
    public int nonScalarReferenceCount() {
      return this.nonScalarReferenceCount;
    }

    @Override
    public List<String> methodsCalled() {
      return Collections.unmodifiableList(this.methodsCalled);
    }

    public String getName() {
      return name;
    }

    public void setName(String name) {
      this.name = name;
    }

    public Observation getObservation() {
      return observation;
    }

    public void setObservation(Observation observation) {
      this.observation = observation;
    }

    public Class<?> getRuntimeClass() {
      return runtimeClass;
    }

    public void setRuntimeClass(Class<?> runtimeClass) {
      this.runtimeClass = runtimeClass;
    }

    public int getScalarReferenceCount() {
      return scalarReferenceCount;
    }

    public void setScalarReferenceCount(int scalarReferenceCount) {
      this.scalarReferenceCount = scalarReferenceCount;
    }

    public int getNonScalarReferenceCount() {
      return nonScalarReferenceCount;
    }

    public void setNonScalarReferenceCount(int nonScalarReferenceCount) {
      this.nonScalarReferenceCount = nonScalarReferenceCount;
    }

    public List<String> getMethodsCalled() {
      return methodsCalled;
    }

    public void setMethodsCalled(List<String> methodsCalled) {
      this.methodsCalled.clear();
      if (methodsCalled != null) {
        this.methodsCalled.addAll(methodsCalled);
      }
    }

    public boolean isPredefined() {
      return predefined;
    }

    public void setPredefined(boolean predefined) {
      this.predefined = predefined;
    }
  }

  static class GroovyDescriptor implements Expression.Descriptor {

    // TODO use https://github.com/casid/jte and a template (differentiating templates for
    //  contextualizers or not)?
    private String processedCode;
    private List<Notification> notifications = new ArrayList<>();
    private Set<Expression.CompilerOption> options;
    private final boolean forceScalar;
    private Map<String, Expression.Descriptor.Identifier> identifiers = new LinkedHashMap<>();
    private final Map<String, Observation> knownObservations = new HashMap<>();
    private final List<String> templateFields = new ArrayList<>();
    private final Map<String, SemanticLiteral> semanticLiterals = new LinkedHashMap<>();

    enum SemanticLiteralType {
      CONCEPT,
      OBSERVABLE
    }

    record SemanticLiteral(SemanticLiteralType type, String definition) implements Serializable {}

    GroovyDescriptor(
        ExpressionCode expression,
        Scope scope,
        List<Observation> knownOutputs,
        List<Observation> knownInputs,
        Expression.CompilerOption... options) {

      if (expression == null || expression.getCode() == null) {
        throw new IllegalArgumentException("The expression and its code must not be null");
      }
      this.options = EnumSet.noneOf(Expression.CompilerOption.class);
      if (options != null) {
        Collections.addAll(this.options, options);
      }
      this.forceScalar = expression.isForcedScalar();

      if (!this.options.contains(Expression.CompilerOption.IgnoreContext)) {
        var outputs = knownOutputs == null ? List.<Observation>of() : knownOutputs;
        var inputs = knownInputs == null ? List.<Observation>of() : knownInputs;

        for (int i = 0; i < outputs.size(); i++) {
          var output = outputs.get(i);
          registerObservation(output, false);
          if (i == 0 && output != null) {
            knownObservations.put(Dataflow.SELF_ID, output);
          }
        }

        for (Observation input : inputs) {
          registerObservation(input, true);
        }

        if (this.options.contains(Expression.CompilerOption.ScanContext)
            && scope instanceof ContextScope contextScope) {
          for (Observation observation : contextScope.getObservations()) {
            registerObservation(observation, true);
          }
          if (!knownObservations.containsKey(Dataflow.SELF_ID)
              && contextScope.getContextObservation() != null) {
            knownObservations.put(Dataflow.SELF_ID, contextScope.getContextObservation());
          }
        }
      }
      this.processedCode =
          this.options.contains(Expression.CompilerOption.DoNotPreprocess)
              ? expression.getCode()
              : preprocess(expression.getCode());
      validateSyntax();
    }

    private void validateSyntax() {
      if (notifications.stream()
          .anyMatch(
              notification ->
                  notification.getLevel().severity >= Notification.Level.Error.severity)) {
        return;
      }
      try {
        new GroovyShell(GroovyProcessor.class.getClassLoader())
            .getClassLoader()
            .parseClass(processedCode);
      } catch (RuntimeException e) {
        notifications.add(
            Notification.error(e, "Invalid Groovy expression: " + String.valueOf(e.getMessage())));
      }
    }

    private void registerObservation(Observation observation, boolean onlyIfAbsent) {
      if (observation == null || observation.getObservable() == null) {
        return;
      }
      var observable = observation.getObservable();
      var identifier =
          observable.getStatedName() == null
              ? observable.getSemantics().codeName()
              : observable.getStatedName();
      if (identifier != null) {
        if (onlyIfAbsent) {
          knownObservations.putIfAbsent(identifier, observation);
        } else {
          knownObservations.put(identifier, observation);
        }
      }
    }

    static class TokenInfo {

      String code;
      String encoding;
      String translation;
      Observation observation;
      String member;

      TokenInfo(String code, String encoding, String translation, Observation observable) {
        this.code = code;
        this.encoding = encoding;
        this.translation = translation;
        this.observation = observable;
      }
    }

    public String getProcessedCode() {
      return processedCode;
    }

    public Map<String, Observation> getKnownObservables() {
      return knownObservations;
    }

    public List<String> getTemplateFields() {
      return templateFields;
    }

    Map<String, SemanticLiteral> getSemanticLiterals() {
      return Collections.unmodifiableMap(semanticLiterals);
    }

    Set<Expression.CompilerOption> getOptions() {
      return Collections.unmodifiableSet(options);
    }

    public boolean isForceScalar() {
      return forceScalar;
    }

    public String preprocess(String code) {

      int varCounter = 1;
      String literalPrefix = Integer.toUnsignedString(code.hashCode(), 36);
      String originalCode = code;

      Map<String, String> substitutions = new HashMap<>();
      code = performSubstitutions(code, substitutions);
      if (substitutions.isEmpty() && knownObservations.isEmpty()) {
        return originalCode;
      }
      // k.IM escapes closing brackets in some embedded expressions; Groovy expects the bracket.
      code = code.replace("\\]", "]");
      code = protectGroovyText(code, substitutions);
      List<TokenInfo> tokens = new ArrayList<>();

      try {
        // first pass recognizing k.LAB-unique patterns
        var lexer = new Lexer(new StringReader(" " + code));
        lexer.setWhitespaceIncluded(true);
        lexer.consume();

        /*
         * Encode into a one-character-per-token string to recognize the remaining patterns:
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
         * The recognize regexp patterns in it:
         *    IL -> LOCATED_IDENTIFIER
         *    I.U and I.I -> IDENTIFIER_METHOD_CALL
         *
         * Substitute these patterns as X and Y using <I>Obs for the method call; change the corresponding list
         * elements with the compound values of the pattern in the list using same strategy as the substitutions;
         * define all needed variables corresponding to I as we go, to later insert in the class template. Also
         * recognize known variables going through U and add the needed fields to the class template.
         *
         * Reassemble the expression and create the final code for the run() function, separating out all
         * scalar code into loops honoring any @fillcurve setting or using the native fill curve of the
         * buffers.
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

        if (ENCODED_LOCATOR_PATTERN.matcher(encoded).find()) {
          notifications.add(
              Notification.error(
                  "Located observation syntax is recognized but not yet supported in Groovy expressions"));
        }

        Matcher methodCall = ENCODED_METHOD_CALL_PATTERN.matcher(encoded);
        while (methodCall.find()) {
          String pattern = methodCall.group();
          int position = methodCall.start();
          swapTokens(
              tokens,
              pattern,
              position,
              removed -> {
                var first = removed.getFirst();
                var last = removed.getLast();
                var buffer = new StringBuilder(first.code).append("Obs");
                for (int i = 1; i < removed.size(); i++) {
                  buffer.append(removed.get(i).translation);
                }
                var token = new TokenInfo(first.code, "Y", buffer.toString(), first.observation);
                token.member = last.code;
                return token;
              });
          encoded = encoded.substring(0, position) + "Y" + encoded.substring(methodCall.end());
          methodCall = ENCODED_METHOD_CALL_PATTERN.matcher(encoded);
        }

        // reconstruct the finalized expression while building template variables
        for (int i = 0; i < encoded.length(); i++) {
          var tokenInfo = tokens.get(i);
          switch (encoded.charAt(i)) {
            case 'I' -> {
              var identifier =
                  (IdImpl) identifiers.computeIfAbsent(tokenInfo.translation, IdImpl::new);

              var observation = knownObservations.get(tokenInfo.code);
              identifier.setObservation(observation);
              if (observation == null
                  || observation.getObservable().getSemantics().is(SemanticType.QUALITY)) {
                identifier.scalarReferenceCount++;
              } else {
                identifier.nonScalarReferenceCount++;
                tokenInfo.translation = tokenInfo.code + "Obs";
              }
            }
            case 'U' -> {
              // Handle scale, scope, time, space, unknown etc. Also we probably need a
              // klab object with the service handles. This works nicely because they get
              // overridden if the
              // inputs have the same name.
              switch (tokenInfo.code) {
                case "space",
                    "time",
                    "scope",
                    "scale",
                    "observer",
                    "context",
                    "source",
                    "target" -> {
                  var identifier =
                      (IdImpl) identifiers.computeIfAbsent(tokenInfo.translation, IdImpl::new);
                  identifier.setPredefined(true);
                  identifier.nonScalarReferenceCount++;
                }
              }
              tokenInfo.translation = "unknown".equals(tokenInfo.code) ? "null" : tokenInfo.code;
            }
            case 'L' -> {
              // Kept verbatim so that diagnostics retain the user's source. Compilation is blocked
              // by the notification added above.
            }
            case 'C' -> {
              var vName = "_concept_" + literalPrefix + "_" + (varCounter++);
              var definition = tokenInfo.translation;
              semanticLiterals.put(
                  vName, new SemanticLiteral(SemanticLiteralType.CONCEPT, definition));
              templateFields.add(
                  "@Lazy "
                      + vName
                      + " = { reasoner.resolveConcept("
                      + quoteGroovy(definition)
                      + ") }()");
              tokenInfo.translation = vName;
            }
            case 'O' -> {
              var vName = "_observable_" + literalPrefix + "_" + (varCounter++);
              var definition = tokenInfo.translation;
              semanticLiterals.put(
                  vName, new SemanticLiteral(SemanticLiteralType.OBSERVABLE, definition));
              templateFields.add(
                  "@Lazy "
                      + vName
                      + " = { reasoner.resolveObservable("
                      + quoteGroovy(definition)
                      + ") }()");
              tokenInfo.translation = vName;
            }
            case 'Y' -> {
              var identifier = (IdImpl) identifiers.computeIfAbsent(tokenInfo.code, IdImpl::new);
              identifier.setObservation(knownObservations.get(tokenInfo.code));
              identifier.nonScalarReferenceCount++;
              if (tokenInfo.member != null && !identifier.methodsCalled.contains(tokenInfo.member)) {
                identifier.methodsCalled.add(tokenInfo.member);
              }
            }
            case 'T', 'W', 'P', 'Q', '(', ')', '.' -> {
              /* OK as is */
            }
            default ->
                throw new KlabInternalErrorException(
                    "wrong pattern encoding in expression preprocessor: " + code);
          }
        }

        return Utils.Strings.join(tokens.stream().map(t -> t.translation).toList(), "");

      } catch (Exception e) {
        notifications.add(
            Notification.error(
                e, "Cannot preprocess Groovy expression: " + String.valueOf(e.getMessage())));
      }

      return code;
    }

    private static String quoteGroovy(String value) {
      return "'" + value.replace("\\", "\\\\").replace("'", "\\'") + "'";
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
      tokens.add(n, ret);
      return ret;
    }

    private TokenInfo classify(Token token, Map<String, String> substitutions) {

      String code = token.getText();
      String encoding = null;
      String translation = null;
      Observation observation = null;

      if (code.isBlank()) {
        encoding = "W";
        translation = code;
      } else if (code.startsWith("__P__")) {
        encoding = "P";
        translation = substitutions.get(code);
      } else if (code.startsWith("__L__")) {
        encoding = "L";
        translation = substitutions.get(code);
      } else if (code.startsWith("__O__")) {
        encoding = "O";
        translation = substitutions.get(code);
      } else if (code.startsWith("__C__")) {
        encoding = "C";
        translation = substitutions.get(code);
      } else if (token.getType() == GroovyLexer.IDENT) {
        if (knownObservations.containsKey(code)) {
          observation = knownObservations.get(code);
          encoding = "I";
        } else {
          encoding = "U";
        }
        translation = code;
      } else if ("?.".equals(code) || "*.".equals(code)) {
        encoding = "Q";
        translation = code;
      } else if ("(".equals(code) || ")".equals(code) || ".".equals(code)) {
        encoding = code;
        translation = code;
      } else {
        encoding = "T";
        translation = code;
      }

      return new TokenInfo(code, encoding, translation, observation);
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
                var placeholder = "__L__" + locatorId.getAndIncrement();
                substitutions.put(placeholder, x);
                return " " + placeholder + " ";
              });
      code =
          performSubstitution(
              OBSERVABLE_PATTERN,
              code,
              (x, n) -> {
                var placeholder = "__O__" + observableId.getAndIncrement();
                substitutions.put(placeholder, x.substring(2, x.length() - 2));
                return " " + placeholder + " ";
              });
      return performSubstitution(
          CONCEPT_PATTERN,
          code,
          (x, n) -> {
            var placeholder = "__C__" + conceptId.getAndIncrement();
            substitutions.put(placeholder, x);
            return " " + placeholder + " ";
          });
    }

    private static String protectGroovyText(
        String code, Map<String, String> substitutions) {
      boolean[] protectedCharacters = protectedGroovyCharacters(code);
      StringBuilder ret = new StringBuilder();
      int textId = 0;
      for (int i = 0; i < code.length(); ) {
        if (!protectedCharacters[i]) {
          ret.append(code.charAt(i++));
          continue;
        }
        int end = i + 1;
        while (end < code.length() && protectedCharacters[end]) {
          end++;
        }
        String placeholder = "__P__" + textId++;
        substitutions.put(placeholder, code.substring(i, end));
        ret.append(' ').append(placeholder).append(' ');
        i = end;
      }
      return ret.toString();
    }

    private static String performSubstitution(
        Pattern pattern, String code, BiFunction<String, Integer, String> translator) {
      StringBuilder output = new StringBuilder();
      Matcher matcher = pattern.matcher(code);
      boolean[] protectedCharacters = protectedGroovyCharacters(code);
      int lastIndex = 0;
      while (matcher.find()) {
        boolean protectedMatch = false;
        for (int i = matcher.start(); i < matcher.end(); i++) {
          if (protectedCharacters[i]) {
            protectedMatch = true;
            break;
          }
        }
        if (protectedMatch) {
          continue;
        }
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

    /** Mark quoted strings and comments so k.LAB syntax inside them remains ordinary Groovy text. */
    private static boolean[] protectedGroovyCharacters(String code) {
      boolean[] ret = new boolean[code.length()];
      int i = 0;
      while (i < code.length()) {
        int delimiterLength = 0;
        char delimiter = 0;
        if (code.startsWith("//", i)) {
          int end = code.indexOf('\n', i + 2);
          end = end < 0 ? code.length() : end;
          for (int n = i; n < end; n++) {
            ret[n] = true;
          }
          i = end;
          continue;
        }
        if (code.startsWith("/*", i)) {
          int end = code.indexOf("*/", i + 2);
          end = end < 0 ? code.length() : end + 2;
          for (int n = i; n < end; n++) {
            ret[n] = true;
          }
          i = end;
          continue;
        }
        if (code.startsWith("'''", i) || code.startsWith("\"\"\"", i)) {
          delimiterLength = 3;
          delimiter = code.charAt(i);
        } else if (code.charAt(i) == '\'' || code.charAt(i) == '"') {
          delimiterLength = 1;
          delimiter = code.charAt(i);
        }
        if (delimiterLength == 0) {
          i++;
          continue;
        }

        int start = i;
        i += delimiterLength;
        while (i < code.length()) {
          if (code.charAt(i) == '\\') {
            i = Math.min(code.length(), i + 2);
          } else if (delimiterLength == 3
              && i + 2 < code.length()
              && code.charAt(i) == delimiter
              && code.charAt(i + 1) == delimiter
              && code.charAt(i + 2) == delimiter) {
            i += 3;
            break;
          } else if (delimiterLength == 1 && code.charAt(i) == delimiter) {
            i++;
            break;
          } else {
            i++;
          }
        }
        for (int n = start; n < i; n++) {
          ret[n] = true;
        }
      }
      return ret;
    }

    @Override
    public Map<String, Identifier> getIdentifiers() {
      return identifiers;
    }

    @Override
    public List<Notification> getNotifications() {
      return notifications;
    }

    @Override
    public Expression compile() {
      var errors =
          notifications.stream()
              .filter(
                  notification ->
                      notification.getLevel().severity >= Notification.Level.Error.severity)
              .map(Notification::getMessage)
              .toList();
      if (!errors.isEmpty()) {
        throw new IllegalArgumentException(
            "Cannot compile invalid Groovy expression: " + String.join("; ", errors));
      }
      return new GroovyExpression(processedCode, this);
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
