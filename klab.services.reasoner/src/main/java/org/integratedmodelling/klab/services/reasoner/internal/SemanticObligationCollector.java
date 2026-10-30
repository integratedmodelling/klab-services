package org.integratedmodelling.klab.services.reasoner.internal;

import java.util.*;
import org.integratedmodelling.klab.api.knowledge.*;
import org.integratedmodelling.klab.api.lang.*;
import org.integratedmodelling.klab.api.lang.kim.*;
import org.integratedmodelling.klab.runtime.language.KimObservableVisitor;
import org.integratedmodelling.klab.services.reasoner.ReasonerService;

/** Source-structure accounting, not certification of a selected executor's implementation.
 * Runtime must separately verify resource/function footprints and all interpreted parameters. */
final class SemanticObligationCollector {
  static final String CONTRACT_REVISION="source-semantic-obligations-v1";
  private final ReasonerService reasoner;
  private final TreeSet<String> terms=new TreeSet<>();
  private boolean complete=true;
  private boolean sourceComplete=true;
  SemanticObligationCollector(ReasonerService reasoner) { this.reasoner=reasoner; }
  void reset() { terms.clear(); complete=true; sourceComplete=true; }
  List<String> terms() { return List.copyOf(terms); }
  boolean complete() { return complete; }
  boolean sourceComplete() { return sourceComplete; }
  void add(Concept concept) {
    if(concept==null||concept.getUrn()==null||concept.is(SemanticType.NOTHING))incomplete();
    else terms.add(concept.getUrn());
  }
  void incomplete() { complete=false; sourceComplete=false; }
  void statement(KlabStatement statement,KimObservableVisitor.Context context) {
    KimModel model=null;
    for(var item:context.getPath())if(item instanceof KimModel m)model=m;
    var seen=Collections.newSetFromMap(new IdentityHashMap<Object,Boolean>());
    for(var annotation:statement.getAnnotations())value(annotation,model,seen);
    if(statement instanceof Contextualizable c)value(c,model,seen);
    else if(statement instanceof KimObservable o) {
      value(o.getValue(),model,seen); value(o.getDefaultValue(),model,seen);
      for(var operator:o.getValueOperators())if(operator!=null)value(operator.getSecond(),model,seen);
    } else if(statement instanceof KimSymbolDefinition symbol)value(symbol.getValue(),model,seen);
    else if(statement instanceof KimTable table)value(table,model,seen);
    else if(statement instanceof KimLookupTable table)value(table,model,seen);
  }
  void call(ServiceCall call,KimObservableVisitor.Context context) {
    KimModel model=null; for(var item:context.getPath())if(item instanceof KimModel m)model=m;
    value(call,model,Collections.newSetFromMap(new IdentityHashMap<>()));
  }
  private void value(Object value,KimModel model,Set<Object> seen) {
    if(value==null||value instanceof String||value instanceof Number||value instanceof Boolean||value instanceof Enum<?>)return;
    if(!seen.add(value))return;
    if(value instanceof Concept concept) { add(concept); return; }
    if(value instanceof org.integratedmodelling.klab.api.knowledge.Observable observable) {add(observable.getSemantics());return;}
    if(value instanceof KimConcept syntax) {
      try { if(syntax.isPattern())incomplete(); else add(reasoner.declareConcept(syntax)); }
      catch(RuntimeException unavailable) {incomplete();} return;
    }
    if(value instanceof KimObservable observable) {
      value(observable.getSemantics(),model,seen);value(observable.getObserverSemantics(),model,seen);
      value(observable.getValue(),model,seen);value(observable.getDefaultValue(),model,seen);
      for(var operator:observable.getValueOperators())if(operator!=null)value(operator.getSecond(),model,seen);return;
    }
    if(value instanceof ExpressionCode expression) { if(!closedScalar(expression,model))complete=false; return; }
    if(value instanceof ServiceCall call) { if(call.getParameters()!=null)value(call.getParameters(),model,seen);return; }
    if(value instanceof Contextualizable c) {
      value(c.getTarget(),model,seen);value(c.getLiteral(),model,seen);value(c.getExpression(),model,seen);
      value(c.getServiceCall(),model,seen);value(c.getClassification(),model,seen);value(c.getLookupTable(),model,seen);
      value(c.getCondition(),model,seen);return;
    }
    if(value instanceof Annotation annotation) {value(annotation.values(),model,seen);return;}
    if(value instanceof KimClassification classification) {value(classification.getClassifiers(),model,seen);return;}
    if(value instanceof KimClassifier classifier) {
      value(classifier.getConceptMatch(),model,seen);value(classifier.getConceptMatches(),model,seen);
      value(classifier.getClassifierMatches(),model,seen);value(classifier.getExpressionMatch(),model,seen);return;
    }
    if(value instanceof KimLookupTable table) {
      for(var argument:table.getArguments())if(argument!=null)value(argument.concept,model,seen);
      value(table.getTable(),model,seen);value(table.getRowClassifiers(),model,seen);value(table.getColumnClassifiers(),model,seen);return;
    }
    if(value instanceof KimTable table) {
      value(table.getRowClassifiers(),model,seen);value(table.getColumnClassifiers(),model,seen);value(table.rows(),model,seen);return;
    }
    if(value instanceof org.integratedmodelling.klab.api.collections.Pair<?,?> pair) {
      value(pair.getFirst(),model,seen);value(pair.getSecond(),model,seen);return;
    }
    if(value instanceof Map<?,?> map) {map.forEach((key,item)->{value(key,model,seen);value(item,model,seen);});return;}
    if(value instanceof Iterable<?> values) {for(var item:values)value(item,model,seen);return;}
    if(value instanceof Object[] values) {for(var item:values)value(item,model,seen);return;}
    // Typed quantities contain only a number and unit/currency strings, with no semantic
    // operands used by the source validator. Retain the legacy conservative runtime flag.
    if(value instanceof org.integratedmodelling.klab.api.lang.Quantity) { complete=false; return; }
    // Declarative scalar syntax has no hidden evaluator or semantic operands.
    if(value instanceof KimQuantity||value instanceof KimDate||value instanceof org.integratedmodelling.klab.api.data.mediation.impl.NumericRangeImpl
        ||value instanceof org.integratedmodelling.klab.api.geometry.Geometry||value instanceof Urn)return;
    incomplete();
  }
  static boolean closedScalar(ExpressionCode expression,KimModel model) {
    if(expression==null)return true;
    if(expression.getLanguage()!=null&&!Set.of("groovy","k.LAB","klab").contains(expression.getLanguage()))return false;
    String code=expression.getCode();if(code==null||code.length()>4096)return false;
    code=code.trim();if(code.startsWith("[")&&code.endsWith("]"))code=code.substring(1,code.length()-1);
    var bindings=new HashMap<String,KimObservable>();
    if(model!=null) {
      var all=new ArrayList<KimObservable>(model.getObservables());all.addAll(model.getDependencies());
      for(var observable:all) {
        if(observable.getFormalName()!=null)bindings.put(observable.getFormalName(),observable);
        if(observable.getCodeName()!=null)bindings.put(observable.getCodeName(),observable);
      }
      if(!model.getObservables().isEmpty())bindings.put("self",model.getObservables().getFirst());
    }
    return new ScalarParser(code,bindings).parse();
  }
  private static final class ScalarParser {
    private final String code;private final Map<String,KimObservable> bindings;private int position;private int depth;
    ScalarParser(String code,Map<String,KimObservable> bindings){this.code=code;this.bindings=bindings;}
    boolean parse(){try{sum();space();return position==code.length();}catch(IllegalArgumentException invalid){return false;}}
    void space(){while(position<code.length()&&Character.isWhitespace(code.charAt(position)))position++;}
    boolean token(char value){space();if(position<code.length()&&code.charAt(position)==value){position++;return true;}return false;}
    void sum(){product();while(true){if(token('+')||token('-'))product();else return;}}
    void product(){atom();while(true){if(token('*')||token('/')||token('%'))atom();else return;}}
    void atom(){if(++depth>128)throw new IllegalArgumentException();try{space();if(token('+')||token('-')){atom();return;}if(token('(')){sum();if(!token(')'))throw new IllegalArgumentException();return;}
      var matcher=java.util.regex.Pattern.compile("(?:[0-9]+(?:\\.[0-9]*)?|\\.[0-9]+)(?:[eE][+-]?[0-9]+)?|[A-Za-z_][A-Za-z0-9_]*(?:\\.childrenCount)?").matcher(code);
      matcher.region(position,code.length());if(!matcher.lookingAt())throw new IllegalArgumentException();
      String value=matcher.group();position=matcher.end();
      if(Character.isDigit(value.charAt(0))||value.charAt(0)=='.')return;
      boolean count=value.endsWith(".childrenCount");String name=count?value.substring(0,value.indexOf('.')):value;
      var observable=bindings.get(name);if(observable==null)throw new IllegalArgumentException();
      var semantics=observable.getSemantics();
      if(count){if(semantics==null||!semantics.getType().contains(SemanticType.COUNTABLE))throw new IllegalArgumentException();}
      else if(observable.getNonSemanticType()!=Artifact.Type.NUMBER
          &&(semantics==null||Artifact.Type.forSemantics(semantics.getType())!=Artifact.Type.NUMBER))throw new IllegalArgumentException();
      }finally{depth--;}
    }
  }
}
