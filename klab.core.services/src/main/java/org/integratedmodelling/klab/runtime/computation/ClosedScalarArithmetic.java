package org.integratedmodelling.klab.runtime.computation;

import java.util.Map;
import java.util.function.Predicate;
import org.integratedmodelling.klab.api.knowledge.Artifact;
import org.integratedmodelling.klab.api.knowledge.SemanticType;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;

/** A deliberately small grammar over actual Groovy-processed code and concrete engine bindings.
 * This is footprint admission, not expression execution or a general Groovy safety claim. */
final class ClosedScalarArithmetic {
  private ClosedScalarArithmetic() {}

  static boolean accepts(String code, Map<String, Observation> bindings,
      Predicate<Observation> trustedObservation) {
    if (code == null || code.length() > 4096) return false;
    if (code.trim().equals("true") || code.trim().equals("false")) return true;
    return new Parser(code, bindings, trustedObservation).parse();
  }

  private static final class Parser {
    private final String code;
    private final Map<String, Observation> bindings;
    private final Predicate<Observation> trusted;
    private int position, depth;
    Parser(String code, Map<String, Observation> bindings, Predicate<Observation> trusted) {
      this.code=code; this.bindings=bindings; this.trusted=trusted;
    }
    boolean parse() {
      try { sum(); space(); return position==code.length(); }
      catch (IllegalArgumentException invalid) { return false; }
    }
    void space() { while(position<code.length() && Character.isWhitespace(code.charAt(position)))position++; }
    boolean token(char value) { space(); if(position<code.length() && code.charAt(position)==value){position++;return true;}return false; }
    void sum() { product(); while(true){if(token('+')||token('-'))product();else return;} }
    void product() { atom(); while(true){if(token('*')||token('/')||token('%'))atom();else return;} }
    void atom() {
      if(++depth>128)throw new IllegalArgumentException();
      try {
        space(); if(token('+')||token('-')){atom();return;}
        if(token('(')){sum();if(!token(')'))throw new IllegalArgumentException();return;}
        var matcher=java.util.regex.Pattern.compile(
            "(?:[0-9]+(?:\\.[0-9]*)?|\\.[0-9]+)(?:[eE][+-]?[0-9]+)?[lLfFdDgG]?|[A-Za-z_][A-Za-z0-9_]*(?:\\.childrenCount)?").matcher(code);
        matcher.region(position,code.length()); if(!matcher.lookingAt())throw new IllegalArgumentException();
        String value=matcher.group();position=matcher.end();
        if(Character.isDigit(value.charAt(0))||value.charAt(0)=='.')return;
        boolean count=value.endsWith(".childrenCount");
        String name=count?value.substring(0,value.indexOf('.')):value;
        var observation=bindings.get(name);
        // Check the actual engine/Runtime-owned receiver before invoking any of its getters.
        if(observation==null||!trusted.test(observation))throw new IllegalArgumentException();
        var observable=observation.getObservable();
        if(observable==null||observable.getSemantics()==null)
          throw new IllegalArgumentException();
        if(count){if(!observable.is(SemanticType.COUNTABLE))throw new IllegalArgumentException();}
        else if(observable.getArtifactType()!=Artifact.Type.NUMBER)throw new IllegalArgumentException();
      } finally { depth--; }
    }
  }
}
