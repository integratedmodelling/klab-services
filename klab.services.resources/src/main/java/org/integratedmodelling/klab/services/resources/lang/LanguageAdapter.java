package org.integratedmodelling.klab.services.resources.lang;

import java.lang.reflect.Field;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.eclipse.emf.ecore.EObject;
import org.eclipse.emf.ecore.EStructuralFeature;
import org.integratedmodelling.common.lang.ContextualizableImpl;
import org.integratedmodelling.common.lang.QuantityImpl;
import org.integratedmodelling.common.lang.ServiceCallImpl;
import org.integratedmodelling.common.lang.TernaryImpl;
import org.integratedmodelling.klab.api.collections.Constant;
import org.integratedmodelling.klab.api.collections.Pair;
import org.integratedmodelling.klab.api.collections.Parameters;
import org.integratedmodelling.klab.api.collections.Triple;
import org.integratedmodelling.klab.api.collections.impl.ConstantImpl;
import org.integratedmodelling.klab.api.collections.impl.IdentifierImpl;
import org.integratedmodelling.klab.api.data.Metadata;
import org.integratedmodelling.klab.api.data.ValueType;
import org.integratedmodelling.klab.api.data.Version;
import org.integratedmodelling.klab.api.data.mediation.NumericRange;
import org.integratedmodelling.klab.api.data.mediation.impl.NumericRangeImpl;
import org.integratedmodelling.klab.api.exceptions.KlabIllegalArgumentException;
import org.integratedmodelling.klab.api.exceptions.KlabUnimplementedException;
import org.integratedmodelling.klab.api.knowledge.KlabAsset;
import org.integratedmodelling.klab.api.knowledge.SemanticType;
import org.integratedmodelling.klab.api.knowledge.Urn;
import org.integratedmodelling.klab.api.lang.*;
import org.integratedmodelling.klab.api.lang.AnnotationImpl;
import org.integratedmodelling.klab.api.lang.kactors.KActorsAction;
import org.integratedmodelling.klab.api.lang.kactors.KActorsBehavior;
import org.integratedmodelling.klab.api.lang.kactors.KActorsStatement;
import org.integratedmodelling.klab.api.lang.kactors.KActorsValue;
import org.integratedmodelling.klab.api.lang.kactors.impl.KActorsActionImpl;
import org.integratedmodelling.klab.api.lang.kactors.impl.KActorsBehaviorImpl;
import org.integratedmodelling.klab.api.lang.kactors.impl.KActorsStatementImpl;
import org.integratedmodelling.klab.api.lang.kactors.impl.KActorsValueImpl;
import org.integratedmodelling.klab.api.lang.kim.*;
import org.integratedmodelling.klab.api.lang.kim.impl.*;
import org.integratedmodelling.klab.api.services.runtime.Notification;
import org.integratedmodelling.klab.api.services.runtime.extension.Instance;
import org.integratedmodelling.klab.api.services.runtime.impl.ExpressionCodeImpl;
import org.integratedmodelling.klab.api.utils.Utils;
import org.integratedmodelling.languages.ActionSyntaxImpl;
import org.integratedmodelling.languages.BehaviorSyntaxImpl;
import org.integratedmodelling.languages.QuantityLiteral;
import org.integratedmodelling.languages.RangeLiteral;
import org.integratedmodelling.languages.SwitchImpl;
import org.integratedmodelling.languages.api.*;
import org.integratedmodelling.languages.kActors.Statement;
import org.integratedmodelling.languages.kActors.SwitchStatement;
import org.integratedmodelling.languages.validation.BasicObservableValidationScope;

/** Adapter to substitute the current ones, based on older k.IM grammars. */
public enum LanguageAdapter {
  INSTANCE;

  private static final Pattern STATIC_ACTION_PATTERN =
      Pattern.compile("^\\s*static\\s+action\\b", Pattern.CASE_INSENSITIVE | Pattern.MULTILINE);
  private static final Pattern SINGLE_CONSTANT_ANNOTATION_PATTERN =
      Pattern.compile("^\\s*@?[A-Za-z][A-Za-z0-9_]*\\s*\\(\\s*([A-Z][A-Z0-9_]*)\\s*\\)\\s*$");

  Map<String, Instance> instanceAnnotations = new HashMap<>();
  Map<String, Class<?>> instanceImplementations = new HashMap<>();

  public boolean registerInstanceClass(Instance annotation, Class<?> annotated) {

    if (instanceAnnotations.containsKey(annotation.value())) {
      return false;
    }

    instanceAnnotations.put(annotation.value(), annotation);
    instanceImplementations.put(annotation.value(), annotated);

    return true;
  }

  public KimObservable adaptObservable(
      ObservableSyntax observableSyntax,
      String namespace,
      String projectName,
      KlabAsset.KnowledgeClass documentClass) {

    var ret = new KimObservableImpl();

    ret.setLength(observableSyntax.getCodeLength());
    ret.setOffsetInDocument(observableSyntax.getCodeOffset());
    ret.setUrn(observableSyntax.encode());
    ret.setNamespace(namespace);
    for (var annotation : observableSyntax.getAnnotations()) {
      ret.getAnnotations().add(adaptAnnotation(annotation, namespace, projectName, documentClass));
    }
    if (observableSyntax.getSemantics().isPattern()) {
      ret.setPattern(observableSyntax.getSemantics().encode());
      ret.getPatternVariables().addAll(observableSyntax.getSemantics().getPatternVariables());
    } else {
      ret.setSemantics(
          adaptSemantics(observableSyntax.getSemantics(), namespace, projectName, documentClass));
      ret.setCodeName(
          ret.getSemantics().getType().contains(SemanticType.NOTHING)
              ? "invalid_observable"
              : observableSyntax.codeName());
      ret.setReferenceName(observableSyntax.referenceName());
      ret.setFormalName(observableSyntax.getStatedName());
    }

    ret.setProjectName(projectName);
    ret.setDocumentClass(documentClass);

    // TODO value ops

    return ret;
  }

  private Annotation adaptAnnotation(
      FunctionCallSyntax annotation,
      String namespace,
      String projectName,
      KlabAsset.KnowledgeClass documentClass) {
    AnnotationImpl ret = new AnnotationImpl();
    var annotationName = annotation.getName();
    ret.setName(
        annotationName != null && annotationName.startsWith("@")
            ? annotationName.substring(1)
            : annotationName);
    for (var argument : annotation.getArguments().entrySet()) {
      var value = adaptValue(argument.getValue(), namespace, projectName, documentClass);
      if (FunctionCallSyntax.DEFAULT_ARGUMENT_NAME.equals(argument.getKey())
          || annotation.unnamedParameterIndex(argument.getKey()) >= 0) {
        ret.putUnnamed(value);
      } else {
        ret.put(argument.getKey(), value);
      }
    }
    /*
     * FunctionCallSyntax versions predating the single-constant fix omit an annotation argument
     * such as SAYHELLO from getArguments(), although the parser retains it in encode(). Recover
     * that unambiguous grammar case here so services remain compatible with those syntax beans.
     */
    if (ret.getUnnamedArguments().isEmpty() && annotation.getArguments().isEmpty()) {
      Matcher matcher = SINGLE_CONSTANT_ANNOTATION_PATTERN.matcher(annotation.encode());
      if (matcher.matches()) {
        ret.putUnnamed(Constant.create(matcher.group(1)));
      }
    }
    return ret;
  }

  private List<KimConceptImpl> asTokens(
      SemanticSyntax semanticSyntax,
      String namespace,
      String projectName,
      KlabAsset.KnowledgeClass documentClass) {
    List<KimConceptImpl> tokens = new ArrayList<>();
    for (var token : semanticSyntax) {
      tokens.add(adaptSemanticToken(token, namespace, projectName, documentClass));
    }
    return tokens;
  }

  private List<KimConceptImpl> asTokens(
      List<SemanticSyntax> second,
      String namespace,
      String projectName,
      KlabAsset.KnowledgeClass documentClass) {
    var ret = new ArrayList<KimConceptImpl>();
    for (var token : second) {
      ret.addAll(asTokens(token, namespace, projectName, documentClass));
    }
    return ret;
  }

  public KimConceptImpl adaptSemantics(
      SemanticSyntax semantics,
      String namespace,
      String projectName,
      KlabAsset.KnowledgeClass documentClass) {
    return adaptSemanticSequence(asTokens(semantics, namespace, projectName, documentClass));
  }

  /**
   * FIXME this will not work right: e.g. data:Normalized change rate of geography:Elevation will be
   * interpreted as change rate of data:Normalized geography:Elevation. Needs to tokenize
   * intelligently from the last, apply traits where they belong and bring the first "each" or
   * distribution operator to the final concept
   */
  private KimConceptImpl adaptSemanticSequence(List<KimConceptImpl> tokens) {

    // TODO first thing check if there are AND or OR restrictions and behave accordingly

    KimConceptImpl ret = null;
    Set<SemanticType> type = null;
    List<KimConcept> roles = new ArrayList<>();
    List<KimConcept> traits = new ArrayList<>();

    var isNothing = false;
    var observableIsCollective = false;
    for (var token : tokens) {
      if (token.getType().contains(SemanticType.OBSERVABLE)) {
        ret = token;
        observableIsCollective = token.isCollective();
        if (observableIsCollective) {
          token.setCollective(false);
          token.resetDefinition();
        }
      } else if (token.getType().contains(SemanticType.ROLE)) {
        roles.add(token);
      } else if (token.getType().contains(SemanticType.PREDICATE)) {
        traits.add(token);
      } else if (token.getType().contains(SemanticType.NOTHING)) {
        isNothing = true;
      }
    }

    if (ret == null) {
      // no observable
      ret = tokens.getLast();
      traits.remove(ret);
      roles.remove(ret);
    }

    ret.addTraits(traits, null);
    ret.addRoles(roles, null);

    if (observableIsCollective) {
      ret.setCollective(true);
      ret.resetDefinition();
    }

    if (isNothing) {
      ret.setType(EnumSet.of(SemanticType.NOTHING));
    }

    return ret;
  }

  public KimConceptImpl adaptSemanticToken(
      SemanticSyntax semantics,
      String namespace,
      String projectName,
      KlabAsset.KnowledgeClass documentClass) {

    var ret = new KimConceptImpl();

    ret.setLength(semantics.getCodeLength());
    ret.setOffsetInDocument(semantics.getCodeOffset());
    ret.setType(adaptSemanticType(semantics.getType()));
    ret.setNegated(semantics.isNegated());
    ret.setCollective(semantics.isCollective());
    ret.setDeprecation(semantics.getDeprecation());
    ret.setDeprecated(semantics.getDeprecation() != null);
    ret.setNamespace(namespace);
    ret.setProjectName(projectName);
    ret.setDocumentClass(documentClass);
    ret.setPattern(semantics.isPattern());
    ret.getPatternVariables().addAll(semantics.getPatternVariables());

    if (semantics.isLeafDeclaration()) {
      ret.setName(semantics.getObservable().toString());
    } else {
      if (semantics.getType().is(SemanticSyntax.TypeCategory.VALID)) {
        ret.setObservable(adaptSemantics(semantics.getObservable(), documentClass));
      } else {
        ret.setObservable(KimConceptImpl.nothing());
        //        ret.setCodeName("invalid_concept");
      }
      for (var cr : semantics.getConceptReferences()) {
        var trait = adaptSemantics(cr, documentClass);
        if (trait.is(SemanticType.ROLE)) {
          ret.getRoles().add(trait);
        } else if (trait.is(SemanticType.TRAIT)) {
          ret.getTraits().add(trait);
        }
      }
    }

    if (semantics.getUnaryOperator() != null && semantics.getUnaryOperator().getFirst() != null) {
      ret.setSemanticModifier(
          UnarySemanticOperator.valueOf(semantics.getUnaryOperator().getFirst().name()));
      if (semantics.getUnaryOperator().getSecond() != null
          && !semantics.getUnaryOperator().getSecond().isEmpty()) {

        ret.setComparisonConcept(
            adaptSemanticSequence(
                asTokens(
                    semantics.getUnaryOperator().getSecond().getFirst(),
                    namespace,
                    projectName,
                    documentClass)));
      }
    }

    List<KimConceptImpl> logicalOperands = new ArrayList<>();
    SemanticSyntax.BinaryOperator logicalOperator = null;

    for (var restriction : semantics.getRestrictions()) {

      boolean collective = restriction.getThird();
      var operand =
          adaptSemanticSequence(
              asTokens(restriction.getSecond(), namespace, projectName, documentClass));
      if (operand != null && collective) {
        operand.setCollective(true);
        operand.resetDefinition();
      }

      if (operand != null) {
        switch (restriction.getFirst()) {
          case OF -> ret.setInherent(operand);
          case FOR -> ret.setGoal(operand);
          case WITH -> ret.setCompresent(operand);
          case ADJACENT -> ret.setAdjacent(operand);
          case OR, AND -> {
            logicalOperator = restriction.getFirst();
            logicalOperands.add(operand);
          }
          case CAUSING -> ret.setCaused(operand);
          case CAUSED_BY -> ret.setCausant(operand);
          case LINKING -> {
            ret.setRelationshipSource(operand);
            var target =
                adaptSemantics(
                    restriction.getSecond().get(0), namespace, projectName, documentClass);
            if (target != null && collective) {
              target.setCollective(true);
              target.resetDefinition();
            }
            ret.setRelationshipTarget(target);
          }
          case CONTAINING -> {
            // TODO
            throw new IllegalStateException("no syntax for containment");
          }
          case CONTAINED_IN -> {
            // TODO
            throw new IllegalStateException("no syntax for containment");
          }
          case DURING -> ret.setCooccurrent(operand);
        }
      }
    }

    if (logicalOperator != null) {
      ret.getType()
          .add(
              logicalOperator == SemanticSyntax.BinaryOperator.OR
                  ? SemanticType.UNION
                  : SemanticType.INTERSECTION);
      ret.getOperands().addAll(logicalOperands);
    }

    // TODO establish abstract and generic nature
    ret.finalizeDefinition();

    return ret;
  }

  public KimNamespace adaptNamespace(
      NamespaceSyntax namespace,
      String projectName,
      Collection<Notification> notifications,
      long timestamp) {

    var ret = new KimNamespaceImpl();
    ret.setUrn(namespace.getUrn());
    ret.setScenario(namespace.isScenario());
    ret.setSourceCode(namespace.getSourceCode());
    ret.setProjectName(projectName);
    ret.getNotifications().addAll(notifications);
    ret.setLastUpdateTimestamp(timestamp);
    ret.setScope(
        namespace.getScope() == null
            ? KlabStatement.Scope.PUBLIC
            : KlabStatement.Scope.valueOf(namespace.getScope().name()));

    // TODO       ret.setImports(); and the rest
    for (var statement : namespace.getStatements()) {
      ret.getStatements().add(adaptStatement(statement, ret));
    }

    return ret;
  }

  private KlabStatement adaptStatement(NamespaceStatementSyntax statement, KimNamespace namespace) {
    return switch (statement) {
      //            case InstanceSyntax instance -> adaptInstance(instance, namespace);
      case ModelSyntax model -> adaptModel(model, namespace);
      case DefineSyntax define -> adaptDefine(define, namespace);
      default -> null;
    };
  }

  private KlabStatement adaptDefine(DefineSyntax define, KimNamespace namespace) {
    KimSymbolDefinitionImpl ret = new KimSymbolDefinitionImpl();
    ret.setDeprecated(define.getDeprecation() != null);
    ret.setDefineClass(define.getInstanceClass());
    ret.setUrn(namespace.getUrn() + "." + define.getName());
    ret.setOffsetInDocument(define.getCodeOffset());
    ret.setName(define.getName());
    ret.setLength(define.getCodeLength());
    ret.setNamespace(namespace.getUrn());
    ret.setProjectName(namespace.getProjectName());
    ret.setDefaulted(define.isDefaulted());
    ret.setDocumentClass(KlabAsset.KnowledgeClass.NAMESPACE);
    ret.setValue(
        adaptValue(
            define.getValue(),
            namespace.getUrn(),
            namespace.getProjectName(),
            KlabAsset.KnowledgeClass.NAMESPACE));
    return ret;
  }

  private KActorsValue adaptKActorsValue(
      ValueSyntax valueSyntax,
      String namespace,
      String projectName,
      List<Notification> notifications) {
    var ret = new KActorsValueImpl();
    setParsingData(valueSyntax, ret, namespace, projectName);
    ret.setType(
        switch (valueSyntax.getType()) {
          case NUMBER -> {
            ret.setStatedValue(valueSyntax.getPod(Number.class));
            yield ValueType.NUMBER;
          }
          case STRING -> {
            ret.setStatedValue(valueSyntax.getPod(String.class));
            yield ValueType.STRING;
          }
          case RANGE -> {
            ret.setStatedValue(adaptNumericRange(valueSyntax.getPod(RangeLiteral.class)));
            yield ValueType.RANGE;
          }
          case OBSERVABLE -> {
            ret.setStatedValue(
                adaptValue(
                    valueSyntax.getPod(Object.class),
                    namespace,
                    projectName,
                    KlabAsset.KnowledgeClass.BEHAVIOR));
            yield ValueType.OBSERVABLE;
          }
          case QUANTITY -> {
            QuantityImpl quantity = new QuantityImpl();
            quantity.setUnit(valueSyntax.getPod(QuantityLiteral.class).getUnit());
            quantity.setCurrency(valueSyntax.getPod(QuantityLiteral.class).getCurrency());
            quantity.setValue(valueSyntax.getPod(QuantityLiteral.class).getValue());
            ret.setStatedValue(quantity);
            yield ValueType.QUANTITY;
          }
          case CONSTANT -> {
            ret.setStatedValue(valueSyntax.getPod(Object.class));
            yield ValueType.CONSTANT;
          }
          case IDENTIFIER -> {
            ret.setStatedValue(valueSyntax.getPod(Object.class));
            yield ValueType.IDENTIFIER;
          }
          case BOOLEAN -> {
            ret.setStatedValue(valueSyntax.getPod(Boolean.class));
            yield ValueType.BOOLEAN;
          }
          case LIST -> {
            ret.setStatedValue(
                adaptValue(
                    valueSyntax.getPod(Object.class),
                    namespace,
                    projectName,
                    KlabAsset.KnowledgeClass.BEHAVIOR));
            yield ValueType.LIST;
          }
          case MAP -> {
            ret.setStatedValue(
                adaptValue(
                    valueSyntax.getPod(Object.class),
                    namespace,
                    projectName,
                    KlabAsset.KnowledgeClass.BEHAVIOR));
            yield ValueType.MAP;
          }
          case LOCALIZED_STRING_REFERENCE -> {
            ret.setStatedValue(valueSyntax.getPod(String.class));
            yield ValueType.LOCALIZED_KEY;
          }
          case ARGUMENT_REFERENCE -> {
            ret.setStatedValue(valueSyntax.getPod(Integer.class));
            yield ValueType.NUMBERED_PATTERN;
          }
          case TERNARY_EXPRESSION -> {
            var syntax = valueSyntax.getPod(TernaryExpressionSyntax.class);
            if (syntax == null
                || syntax.getCondition() == null
                || syntax.getTrueCase() == null
                || syntax.getFalseCase() == null) {
              notifications.add(
                  Notification.error(
                      valueSyntax, "The parser returned an incomplete ternary expression"));
            } else {
              var ternary = new TernaryImpl();
              ternary.setCondition(
                  adaptKActorsValue(syntax.getCondition(), namespace, projectName, notifications));
              ternary.setTrueCase(
                  adaptKActorsValue(syntax.getTrueCase(), namespace, projectName, notifications));
              ternary.setFalseCase(
                  adaptKActorsValue(syntax.getFalseCase(), namespace, projectName, notifications));
              ret.setStatedValue(ternary);
            }
            yield ValueType.TERNARY_EXPRESSION;
          }
          case EXPRESSION -> {
            ret.setStatedValue(valueSyntax.getPod(String.class));
            yield ValueType.EXPRESSION;
          }
          case REGULAR_EXPRESSION -> {
            ret.setStatedValue(Pattern.compile(valueSyntax.getPod(String.class)));
            yield ValueType.REGEXP;
          }
          case NODATA -> {
            yield ValueType.NODATA;
          }
          case ANYTHING -> ValueType.ANYTHING;
          case EVERYTHING -> ValueType.ANYVALUE;
          case EMPTY -> ValueType.EMPTY;
          case ERROR -> ValueType.ERROR;
          case ANNOTATION -> {
            ret.setStatedValue(valueSyntax.getPod(String.class));
            yield ValueType.ANNOTATION;
          }
        });
    ret.setDeferred(valueSyntax.isQuoted());
    return ret;
  }

  /**
   * Adapt any value that can be part of a literal, recursively unparsing its contents. We only keep
   * the syntactic info for the top-level object.
   *
   * @param value
   * @return
   */
  private Object adaptValue(
      Object value, String namespace, String projectName, KlabAsset.KnowledgeClass documentClass) {

    if (value == null) {
      return null;
    }

    Object object = value;
    if (object instanceof ParsedLiteral parsedLiteral) {
      return adaptLiteral(parsedLiteral, namespace, projectName, documentClass);
    } else if (object instanceof ObservableSyntax observableSyntax) {
      object = adaptObservable(observableSyntax, namespace, projectName, documentClass);
    } else if (object instanceof SemanticSyntax semanticSyntax) {
      object = adaptSemantics(semanticSyntax, namespace, projectName, documentClass);
    }

    return switch (object) {
      case Map<?, ?> map -> {
        var ret = new LinkedHashMap<Object, Object>();
        for (Object key : map.keySet()) {
          ret.put(key, adaptValue(map.get(key), namespace, projectName, documentClass));
        }
        yield ret;
      }
      case Collection<?> collection -> {
        var ret = new ArrayList<>();
        for (Object item : collection) {
          ret.add(adaptValue(item, namespace, projectName, documentClass));
        }
        yield ret;
      }
      case ObservableSyntax observableSyntax ->
          adaptObservable(observableSyntax, namespace, projectName, documentClass);
      case RangeLiteral rangeLiteral -> {
        var range = new NumericRangeImpl();
        range.setLowerBound(rangeLiteral.getFrom().doubleValue());
        range.setUpperBound(rangeLiteral.getTo().doubleValue());
        range.setLowerExclusive(!rangeLiteral.isLeftInclusive());
        range.setUpperOpen(!rangeLiteral.isRightInclusive());
        yield range;
      }
      default -> object;
    };
  }

  private Object adaptLiteral(
      ParsedLiteral literal,
      String namespace,
      String projectName,
      KlabAsset.KnowledgeClass documentClass) {
    if (literal.getCurrency() != null) {
      var quantity = new QuantityImpl();
      quantity.setValue((Number) literal.getPod());
      quantity.setCurrency(literal.getCurrency());
      return quantity;
    } else if (literal.getUnit() != null) {
      var quantity = new QuantityImpl();
      quantity.setValue((Number) literal.getPod());
      quantity.setUnit(literal.getUnit());
      return quantity;
    } else if (literal.isIdentifier()) {
      if (Utils.Strings.isLowercase(literal.getPod().toString())) {
        var identifier = new IdentifierImpl();
        identifier.setValue(literal.getPod().toString());
        return identifier;
      } else {
        var constant = new ConstantImpl();
        constant.setValue(literal.getPod().toString());
        return constant;
      }
    }

    return adaptValue(literal.getPod(), namespace, projectName, documentClass);
  }

  private KlabStatement adaptModel(ModelSyntax model, KimNamespace namespace) {

    KimModelImpl ret = new KimModelImpl();

    ret.setScope(namespace.getScope());
    ret.setScope(
        model.getScope() == null
            ? namespace.getScope()
            : KlabStatement.Scope.valueOf(model.getScope().name()));
    ret.setNamespace(namespace.getUrn());
    ret.setDeprecated(model.getDeprecation() != null);
    ret.setDeprecation(model.getDeprecation());
    ret.setOffsetInDocument(model.getCodeOffset());
    ret.setLength(model.getCodeLength());
    ret.setProjectName(namespace.getProjectName());
    ret.setDocumentClass(KlabAsset.KnowledgeClass.NAMESPACE);
    ret.getResourceUrns().addAll(model.getResourceUrns().stream().map(u -> adaptUrn(u)).toList());
    for (var annotation : model.getAnnotations()) {
      ret.getAnnotations()
          .add(
              adaptAnnotation(
                  annotation,
                  namespace.getUrn(),
                  namespace.getProjectName(),
                  KlabAsset.KnowledgeClass.NAMESPACE));
    }

    // TODO docstring set through next-gen literate programming features

    boolean inactive = false;

    if (model.getDataType() != null) {
      switch (model.getDataType()) {
        case NUMBER -> {
          // MIERDA TODO needs a syntactic counterpart, too
          //              ret.getObservables().add(Observable.number(model.getName()));
        }
        case TEXT -> {}
        case BOOLEAN -> {}
        case SUBJECTS -> {}
        case EVENTS -> {}
        case RELATIONSHIPS -> {}
      }
      throw new KlabUnimplementedException("non-semantic model support");
    }

    KimObservable mainObservable = null;
    for (var observable : model.getObservables()) {
      var obs =
          adaptObservable(
              observable,
              namespace.getUrn(),
              namespace.getProjectName(),
              KlabAsset.KnowledgeClass.NAMESPACE);
      if (mainObservable == null) {
        mainObservable = obs;
      }
      ret.getObservables().add(obs);
      if (obs.getSemantics().is(SemanticType.NOTHING)) {
        inactive = true;
      }
    }
    for (var dependency : model.getDependencies()) {
      var obs =
          adaptObservable(
              dependency,
              namespace.getUrn(),
              namespace.getProjectName(),
              KlabAsset.KnowledgeClass.NAMESPACE);
      ret.getDependencies().add(obs);
      if (obs.getSemantics().is(SemanticType.NOTHING)) {
        inactive = true;
      }
    }

    var modelDescriptionType =
        mainObservable == null || mainObservable.getSemantics().is(SemanticType.NOTHING)
            ? "inactive"
            : mainObservable.getSemantics().getDescriptionType().getVerbalForm();
    ret.setUrn(namespace.getUrn() + "." + model.getName() + "-" + modelDescriptionType);
    ret.setInactive(inactive);

    for (var contextualizable : model.getContextualizations()) {
      ret.getContextualization().add(adaptContextualizable(contextualizable, namespace));
    }

    return ret;
  }

  private Urn adaptUrn(org.eclipse.xtext.util.Pair<String, Map<Object, Object>> u) {
    var ret = Urn.of(u.getFirst());
    for (var entry : u.getSecond().entrySet()) {
      ret.getParameters().put(entry.getKey().toString(), entry.getValue().toString());
    }
    return ret;
  }

  private Contextualizable adaptContextualizable(
      ModelSyntax.Contextualization contextualizable, KimNamespace namespace) {

    var ret = new ContextualizableImpl();

    ret.setOffsetInDocument(contextualizable.getCodeOffset());
    ret.setLength(contextualizable.getCodeLength());
    ret.setNamespace(namespace.getUrn());

    if (contextualizable.getContextualizable() instanceof FunctionCallSyntax functionCallSyntax) {
      ret.setServiceCall(
          adaptServiceCall(
              functionCallSyntax,
              namespace.getUrn(),
              namespace.getProjectName(),
              KlabAsset.KnowledgeClass.MODEL));
    } else if (contextualizable.getContextualizable()
        instanceof ExpressionSyntax expressionSyntax) {
      ret.setTargetId(contextualizable.getTarget());
      ret.setExpression(adaptExpression(expressionSyntax, namespace));
      if (contextualizable.isIntegration()) {
        ret.setAction(Contextualizable.Action.INTEGRATE);
      } // TODO the rest - set to, do. May be unnecessary if validated properly
    } else {
      // TODO all others
      throw new KlabUnimplementedException("contextualizable " + contextualizable);
    }

    return ret;
  }

  private ExpressionCode adaptExpression(
      ExpressionSyntax expressionSyntax, KimNamespace namespace) {
    var ret = new ExpressionCodeImpl();
    ret.setCode(expressionSyntax.getCode());
    ret.setForcedScalar(expressionSyntax.isScalar());
    ret.setLanguage(expressionSyntax.getLanguage());
    return ret;
  }

  //
  //    private KlabStatement adaptInstance(InstanceSyntax instance, KimNamespace namespace) {
  //        return null;
  //    }

  private KimConcept adaptSemantics(
      SemanticSyntax.ConceptData observable, KlabAsset.KnowledgeClass documentClass) {
    KimConceptImpl ret = new KimConceptImpl();
    ret.setUrn(observable.concept().namespace() + ":" + observable.concept().conceptName());
    ret.setName(ret.getUrn());
    ret.setType(adaptSemanticType(observable.concept().mainType()));
    ret.setDocumentClass(documentClass);
    //    ret.computeUrn();
    return ret;
  }

  private Set<SemanticType> adaptSemanticType(SemanticSyntax.Type type) {
    var ret =
        switch (type) {
          case VOID, NOTHING -> EnumSet.of(SemanticType.NOTHING);
          case ACCELERATION ->
              EnumSet.of(
                  SemanticType.OBSERVABLE,
                  SemanticType.QUANTIFIABLE,
                  SemanticType.QUALITY,
                  SemanticType.ACCELERATION);
          case AMOUNT ->
              EnumSet.of(
                  SemanticType.OBSERVABLE,
                  SemanticType.QUANTIFIABLE,
                  SemanticType.QUALITY,
                  SemanticType.AMOUNT);
          case ANGLE ->
              EnumSet.of(
                  SemanticType.OBSERVABLE,
                  SemanticType.QUANTIFIABLE,
                  SemanticType.QUALITY,
                  SemanticType.ANGLE);
          case AREA ->
              EnumSet.of(
                  SemanticType.OBSERVABLE,
                  SemanticType.QUANTIFIABLE,
                  SemanticType.QUALITY,
                  SemanticType.AREA);
          case ATTRIBUTE ->
              EnumSet.of(SemanticType.PREDICATE, SemanticType.ATTRIBUTE, SemanticType.TRAIT);
          case BOND ->
              EnumSet.of(
                  SemanticType.OBSERVABLE,
                  SemanticType.COUNTABLE,
                  SemanticType.DIRECT_OBSERVABLE,
                  SemanticType.RELATIONSHIP,
                  SemanticType.BIDIRECTIONAL);
          case CHARGE ->
              EnumSet.of(
                  SemanticType.OBSERVABLE,
                  SemanticType.QUANTIFIABLE,
                  SemanticType.QUALITY,
                  SemanticType.CHARGE);
          case CLASS ->
              EnumSet.of(SemanticType.OBSERVABLE, SemanticType.QUALITY, SemanticType.CLASS);
          case CONFIGURATION ->
              EnumSet.of(SemanticType.DIRECT_OBSERVABLE, SemanticType.CONFIGURATION);
          case DOMAIN -> EnumSet.of(SemanticType.PREDICATE, SemanticType.DOMAIN);
          case DURATION ->
              EnumSet.of(
                  SemanticType.OBSERVABLE,
                  SemanticType.QUANTIFIABLE,
                  SemanticType.QUALITY,
                  SemanticType.DURATION);
          case ELECTRIC_POTENTIAL ->
              EnumSet.of(
                  SemanticType.OBSERVABLE,
                  SemanticType.QUANTIFIABLE,
                  SemanticType.QUALITY,
                  SemanticType.ELECTRIC_POTENTIAL);
          case ENERGY ->
              EnumSet.of(
                  SemanticType.OBSERVABLE,
                  SemanticType.QUANTIFIABLE,
                  SemanticType.QUALITY,
                  SemanticType.ENERGY);
          case ENTROPY ->
              EnumSet.of(
                  SemanticType.OBSERVABLE,
                  SemanticType.QUANTIFIABLE,
                  SemanticType.QUALITY,
                  SemanticType.ENTROPY);
          case EVENT ->
              EnumSet.of(SemanticType.OBSERVABLE, SemanticType.COUNTABLE, SemanticType.EVENT);
          case EXTENT -> EnumSet.of(SemanticType.EXTENT, SemanticType.QUALITY);
          case FUNCTIONAL_RELATIONSHIP ->
              EnumSet.of(
                  SemanticType.OBSERVABLE,
                  SemanticType.COUNTABLE,
                  SemanticType.DIRECT_OBSERVABLE,
                  SemanticType.RELATIONSHIP,
                  SemanticType.FUNCTIONAL);
          case GENERIC_QUALITY ->
              // this only happens with core im:Quality. It's deprecated and should not get here.
              EnumSet.of(SemanticType.OBSERVABLE, SemanticType.QUALITY);
          case IDENTITY ->
              EnumSet.of(SemanticType.PREDICATE, SemanticType.IDENTITY, SemanticType.TRAIT);
          case INDIVIDUAL_IDENTITY ->
              EnumSet.of(
                  SemanticType.PREDICATE,
                  SemanticType.IDENTITY,
                  SemanticType.INDIVIDUAL,
                  SemanticType.TRAIT);
          case LENGTH ->
              EnumSet.of(
                  SemanticType.OBSERVABLE,
                  SemanticType.QUANTIFIABLE,
                  SemanticType.QUALITY,
                  SemanticType.LENGTH);
          case MASS ->
              EnumSet.of(
                  SemanticType.OBSERVABLE,
                  SemanticType.QUANTIFIABLE,
                  SemanticType.QUALITY,
                  SemanticType.MASS);
          case MONEY ->
              EnumSet.of(
                  SemanticType.OBSERVABLE,
                  SemanticType.QUANTIFIABLE,
                  SemanticType.QUALITY,
                  SemanticType.MONEY);
          case ORDERING ->
              EnumSet.of(SemanticType.PREDICATE, SemanticType.ORDERING, SemanticType.TRAIT); // TODO
          // attribute?
          case PRESSURE ->
              EnumSet.of(
                  SemanticType.OBSERVABLE,
                  SemanticType.QUANTIFIABLE,
                  SemanticType.QUALITY,
                  SemanticType.PRESSURE);
          case PRIORITY ->
              EnumSet.of(
                  SemanticType.OBSERVABLE,
                  SemanticType.QUANTIFIABLE,
                  SemanticType.QUALITY,
                  SemanticType.PRIORITY);
          case PROCESS -> EnumSet.of(SemanticType.OBSERVABLE, SemanticType.PROCESS);
          case QUANTITY ->
              EnumSet.of(
                  SemanticType.OBSERVABLE,
                  SemanticType.QUANTIFIABLE,
                  SemanticType.QUALITY,
                  SemanticType.QUANTITY);
          case AGENT ->
              EnumSet.of(
                  SemanticType.OBSERVABLE,
                  SemanticType.COUNTABLE,
                  SemanticType.DIRECT_OBSERVABLE,
                  SemanticType.AGENT);
          case REALM ->
              EnumSet.of(SemanticType.PREDICATE, SemanticType.ATTRIBUTE, SemanticType.TRAIT);
          case RESISTANCE ->
              EnumSet.of(
                  SemanticType.OBSERVABLE,
                  SemanticType.QUANTIFIABLE,
                  SemanticType.QUALITY,
                  SemanticType.RESISTANCE);
          case RESISTIVITY ->
              EnumSet.of(
                  SemanticType.OBSERVABLE,
                  SemanticType.QUANTIFIABLE,
                  SemanticType.QUALITY,
                  SemanticType.RESISTIVITY);
          case ROLE -> EnumSet.of(SemanticType.PREDICATE, SemanticType.ROLE);
          case STRUCTURAL_RELATIONSHIP ->
              EnumSet.of(
                  SemanticType.OBSERVABLE,
                  SemanticType.COUNTABLE,
                  SemanticType.DIRECT_OBSERVABLE,
                  SemanticType.RELATIONSHIP,
                  SemanticType.STRUCTURAL);
          case SUBJECT ->
              EnumSet.of(
                  SemanticType.OBSERVABLE,
                  SemanticType.COUNTABLE,
                  SemanticType.DIRECT_OBSERVABLE,
                  SemanticType.SUBJECT);
          case TEMPERATURE ->
              EnumSet.of(
                  SemanticType.OBSERVABLE,
                  SemanticType.QUANTIFIABLE,
                  SemanticType.QUALITY,
                  SemanticType.TEMPERATURE);
          case VELOCITY ->
              EnumSet.of(
                  SemanticType.OBSERVABLE,
                  SemanticType.QUANTIFIABLE,
                  SemanticType.QUALITY,
                  SemanticType.VELOCITY);
          case VISCOSITY ->
              EnumSet.of(
                  SemanticType.OBSERVABLE,
                  SemanticType.QUANTIFIABLE,
                  SemanticType.QUALITY,
                  SemanticType.VISCOSITY);
          case MONETARY_VALUE -> null;
          case VOLUME ->
              EnumSet.of(
                  SemanticType.OBSERVABLE,
                  SemanticType.QUANTIFIABLE,
                  SemanticType.QUALITY,
                  SemanticType.VOLUME);
          case WEIGHT ->
              EnumSet.of(
                  SemanticType.OBSERVABLE,
                  SemanticType.QUANTIFIABLE,
                  SemanticType.QUALITY,
                  SemanticType.WEIGHT);
          case PROBABILITY ->
              EnumSet.of(
                  SemanticType.OBSERVABLE,
                  SemanticType.QUANTIFIABLE,
                  SemanticType.QUALITY,
                  SemanticType.PROBABILITY);
          case OCCURRENCE ->
              EnumSet.of(
                  SemanticType.OBSERVABLE,
                  SemanticType.QUANTIFIABLE,
                  SemanticType.QUALITY,
                  SemanticType.OCCURRENCE);
          case PERCENTAGE ->
              EnumSet.of(
                  SemanticType.OBSERVABLE,
                  SemanticType.QUANTIFIABLE,
                  SemanticType.QUALITY,
                  SemanticType.PERCENTAGE);
          case RATIO ->
              EnumSet.of(
                  SemanticType.OBSERVABLE,
                  SemanticType.QUANTIFIABLE,
                  SemanticType.QUALITY,
                  SemanticType.RATIO);
          case UNCERTAINTY ->
              EnumSet.of(
                  SemanticType.OBSERVABLE,
                  SemanticType.QUANTIFIABLE,
                  SemanticType.QUALITY,
                  SemanticType.UNCERTAINTY);
          case VALUE ->
              EnumSet.of(
                  SemanticType.OBSERVABLE,
                  SemanticType.QUANTIFIABLE,
                  SemanticType.QUALITY,
                  SemanticType.VALUE);
          case PROPORTION ->
              EnumSet.of(
                  SemanticType.OBSERVABLE,
                  SemanticType.QUANTIFIABLE,
                  SemanticType.QUALITY,
                  SemanticType.PROPORTION);
          case RATE ->
              EnumSet.of(
                  SemanticType.OBSERVABLE,
                  SemanticType.QUANTIFIABLE,
                  SemanticType.QUALITY,
                  SemanticType.RATE);
          case PRESENCE ->
              EnumSet.of(SemanticType.OBSERVABLE, SemanticType.QUALITY, SemanticType.PRESENCE);
          case MAGNITUDE ->
              EnumSet.of(
                  SemanticType.OBSERVABLE,
                  SemanticType.QUANTIFIABLE,
                  SemanticType.QUALITY,
                  SemanticType.MAGNITUDE);
          case NUMEROSITY ->
              EnumSet.of(
                  SemanticType.OBSERVABLE,
                  SemanticType.QUANTIFIABLE,
                  SemanticType.QUALITY,
                  SemanticType.NUMEROSITY);
        };

    // single source of truth for intensive/extensive nature
    if (type.is(SemanticSyntax.TypeCategory.INTENSIVE)) {
      ret.add(SemanticType.INTENSIVE);
    } else if (type.is(SemanticSyntax.TypeCategory.EXTENSIVE)) {
      ret.add(SemanticType.EXTENSIVE);
    }
    return ret;
  }

  public KimObservationStrategyDocument adaptStrategies(
      ObservationStrategiesSyntax definition,
      String projectName,
      Collection<Notification> notifications,
      long timestamp) {

    KimObservationStrategiesImpl ret = new KimObservationStrategiesImpl();
    ret.setUrn(definition.getUrn());
    ret.getNotifications().addAll(notifications);
    ret.setSourceCode(definition.getSourceCode());
    ret.setProjectName(projectName);
    ret.setLastUpdateTimestamp(timestamp);

    // we don't add source code here as each strategy has its own
    for (var strategy : definition.getStrategies()) {
      ret.getStatements().add(adaptStrategy(strategy, definition.getUrn(), projectName));
    }
    return ret;
  }

  private ServiceCall adaptServiceCall(
      FunctionCallSyntax functionCallSyntax,
      String namespace,
      String projectName,
      KlabAsset.KnowledgeClass documentClass) {

    ServiceCallImpl ret = new ServiceCallImpl();
    ret.setLength(functionCallSyntax.getCodeLength());
    ret.setOffsetInDocument(functionCallSyntax.getCodeOffset());
    ret.setNamespace(namespace);
    ret.setProjectName(projectName);
    ret.setUrn(functionCallSyntax.getName());
    ret.setSourceCode(functionCallSyntax.encode());

    for (String key : functionCallSyntax.getArguments().keySet()) {
      ret.getParameters()
          .put(
              key,
              adaptValue(
                  functionCallSyntax.getArguments().get(key),
                  namespace,
                  projectName,
                  documentClass));
    }

    // TODO unnamed parameters, annotations and all that

    return ret;
  }

  private KimObservationStrategy adaptStrategy(
      ObservationStrategySyntax strategy, String namespace, String projectName) {

    var ret = new KimObservationStrategyImpl();

    ret.setRank(strategy.getRank());
    ret.setType(KimObservationStrategy.Type.valueOf(strategy.getType().name()));
    ret.setNamespace(namespace);
    ret.setUrn(strategy.getName());
    ret.setDescription(strategy.getDescription());
    ret.setOffsetInDocument(strategy.getCodeOffset());
    ret.setLength(strategy.getCodeLength());
    ret.setDeprecation(strategy.getDeprecation());
    ret.setDeprecated(strategy.getDeprecation() != null);
    ret.setProjectName(projectName);
    ret.setDocumentClass(KlabAsset.KnowledgeClass.OBSERVATION_STRATEGY_DOCUMENT);

    // these are multiple 'for' statements
    for (var filter : strategy.getFilters()) {

      List<KimObservationStrategy.Filter> filters = new ArrayList<>();

      // and these are comma-separated filters in a 'for'
      for (var match : filter.getMatch()) {

        var f = new KimObservationStrategyImpl.FilterImpl();
        f.setNegated(match.isNegated());
        if (match.getObservable() != null /* which it should */) {
          f.setMatch(
              adaptSemantics(
                  match.getObservable(),
                  namespace,
                  projectName,
                  KlabAsset.KnowledgeClass.OBSERVATION_STRATEGY));
        }

        if (match.getTypePattern() != null) {
          match
              .getTypePattern()
              .forEach(
                  type ->
                      f.getTypePattern()
                          .add(KimObservationStrategy.Filter.SemanticPattern.valueOf(type.name())));
        }

        for (var condition : match.getConditions()) {
          f.getFunctions()
              .add(
                  adaptServiceCall(
                      condition,
                      namespace,
                      projectName,
                      KlabAsset.KnowledgeClass.OBSERVATION_STRATEGY_DOCUMENT));
        }

        f.setConnectorToPrevious(
            match.getConnectorToPrevious() == SemanticSyntax.Quantifier.ALL
                ? LogicalConnector.INTERSECTION
                : LogicalConnector.UNION);

        filters.add(f);
      }

      ret.getFilters().add(filters);
    }
    for (var operation : strategy.getOperations()) {
      var o = new KimObservationStrategyImpl.OperationImpl();
      if (operation.getType() != null) {
        o.setType(KimObservationStrategy.Operation.Type.valueOf(operation.getType().name()));
      }

      o.setLocalId(operation.getId());
      o.setTransformationTarget(operation.getTransformationTarget());

      if (operation.getObservable() != null) {
        o.setObservable(
            adaptObservable(
                operation.getObservable(),
                strategy.getName(),
                projectName,
                KlabAsset.KnowledgeClass.OBSERVATION_STRATEGY_DOCUMENT));
      }
      if (!operation.getFunctions().isEmpty()) {
        o.getFunctions()
            .addAll(
                operation.getFunctions().stream()
                    .map(
                        f ->
                            adaptServiceCall(
                                f,
                                namespace,
                                projectName,
                                KlabAsset.KnowledgeClass.OBSERVATION_STRATEGY))
                    .toList());
      }
      //      if (!operation.getDeferredStrategies().isEmpty()) {
      //        o.getDeferredStrategies()
      //            .addAll(
      //                operation.getDeferredStrategies().stream()
      //                    .map(s -> adaptStrategy(s, namespace, projectName))
      //                    .toList());
      //      }
      ret.getOperations().add(o);
    }

    for (var let : strategy.getMacroVariables().keySet()) {
      var f = new KimObservationStrategyImpl.FilterImpl();
      String key = null;
      if (let.isIdentifier()) {
        key = let.toString();
      } else if (let.getPod() instanceof List<?> list) {
        key = Utils.Strings.join(list, ",");
      }
      if (key == null) {
        ret.getNotifications()
            .add(Notification.error("unrecognized argument for let statement", let));
        continue;
      }

      var filter = strategy.getMacroVariables().get(let);
      if (filter.getObservable() != null) {
        f.setMatch(
            adaptSemantics(
                filter.getObservable(),
                namespace,
                projectName,
                KlabAsset.KnowledgeClass.OBSERVATION_STRATEGY));
      }
      for (var condition : filter.getConditions()) {
        f.getFunctions()
            .add(
                adaptServiceCall(
                    condition,
                    namespace,
                    projectName,
                    KlabAsset.KnowledgeClass.OBSERVATION_STRATEGY));
      }
      ret.getMacroVariables().put(key, f);
    }
    return ret;
  }

  public KimOntology adaptOntology(
      OntologySyntax ontology,
      String projectName,
      Collection<Notification> notifications,
      long timestamp) {

    KimOntologyImpl ret = new KimOntologyImpl();

    ret.setUrn(ontology.getName());
    ret.getImportedOntologies().addAll(ontology.getImportedOntologies());
    ret.setSourceCode(ontology.getSourceCode());
    ret.getMetadata().put(Metadata.DC_COMMENT, ontology.getDescription());
    ret.setVersion(Version.create(ontology.getVersion()));
    ret.setProjectName(projectName);
    ret.setLastUpdateTimestamp(timestamp);

    if (ontology.getDomain() == OntologySyntax.rootDomain) {
      ret.setDomain(KimOntology.rootDomain);
      for (var owlImport : ontology.getImportedCoreOntologies().keySet()) {
        ret.getOwlImports()
            .add(Pair.of(owlImport, ontology.getImportedCoreOntologies().get(owlImport)));
      }
    } else {
      ret.setDomain(
          adaptSemantics(
              ontology.getDomain(),
              ontology.getName(),
              projectName,
              KlabAsset.KnowledgeClass.ONTOLOGY));
    }

    for (var definition : ontology.getConceptDeclarations()) {
      ret.getStatements().add(adaptConceptDefinition(definition, ontology.getName(), projectName));
    }

    ret.getNotifications().addAll(notifications);

    return ret;
  }

  private NumericRange adaptNumericRange(RangeLiteral range) {
    // TODO open/close; should distinguish integers for iterations etc.
    var ret = new NumericRangeImpl();
    ret.setLowerBound(range.getFrom().doubleValue());
    ret.setUpperBound(range.getTo().doubleValue());
    return ret;
  }

  private KimConceptStatement adaptConceptDefinition(
      ConceptDeclarationSyntax definition, String namespace, String projectName) {

    KimConceptStatementImpl ret = new KimConceptStatementImpl();

    ret.setUrn(definition.getName());
    ret.setNamespace(namespace);
    ret.setAbstract(definition.isAbstract());
    ret.setSealed(definition.isSealed());
    ret.setSubjective(definition.isSubjective());
    ret.setDocstring(definition.getDescription());
    ret.setAlias(definition.isAlias());
    ret.setOffsetInDocument(definition.getCodeOffset());
    ret.setLength(definition.getCodeLength());
    ret.setDeprecation(definition.getDeprecation());
    ret.setDeprecated(definition.getDeprecation() != null);
    ret.setProjectName(projectName);
    ret.setType(adaptSemanticType(definition.getDeclaredType()));
    ret.setDocumentClass(KlabAsset.KnowledgeClass.ONTOLOGY);

    if (definition.isDeniable()) {
      ret.getType().add(SemanticType.DENIABLE);
    }
    if (definition.isAbstract()) {
      ret.getType().add(SemanticType.ABSTRACT);
    }
    if (definition.isSealed()) {
      ret.getType().add(SemanticType.SEALED);
    }
    if (definition.isSubjective()) {
      ret.getType().add(SemanticType.SUBJECTIVE);
    }

    if (definition.isCoreDeclaration()) {
      ret.setUpperConceptDefined(definition.getDeclaredParent().encode());
    } else {
      ret.setDeclaredParent(
          definition.getDeclaredParent() == null
              ? null
              : adaptSemantics(
                  definition.getDeclaredParent(),
                  namespace,
                  projectName,
                  KlabAsset.KnowledgeClass.ONTOLOGY));
      if (ret.getDeclaredParent() != null && definition.isGenericQuality()) {
        ret.getType().clear();
        ret.getType().addAll(ret.getDeclaredParent().getType());
      }
    }
    for (var child : definition.getChildren()) {
      ret.getChildren().add(adaptConceptDefinition(child, namespace, projectName));
    }
    return ret;
  }

  public KActorsBehavior adaptBehavior(
      BehaviorSyntaxImpl syntax,
      String name,
      String projectName,
      List<Notification> notifications,
      long timestamp) {

    var ret = new KActorsBehaviorImpl();

    ret.setUrn(name);
    ret.setLastUpdateTimestamp(timestamp);
    ret.setProjectName(projectName);
    ret.setSourceCode(syntax.encode());
    ret.setVersion(
        syntax.getVersion() == null
            ? Version.CURRENT_VERSION
            : Version.create(syntax.getVersion()));
    ret.setPlatform(KActorsBehavior.Platform.ANY);
    ret.setDescription(syntax.getDocstring());
    ret.setBehaviorType(
        switch (syntax.getType()) {
          case TESTCASE -> KActorsBehavior.Type.UNITTEST;
          case SCRIPT -> KActorsBehavior.Type.SCRIPT;
          case COMPONENT -> KActorsBehavior.Type.COMPONENT;
          case LIBRARY -> KActorsBehavior.Type.LIBRARY;
          case APPLICATION -> KActorsBehavior.Type.APP;
          case BEHAVIOR -> KActorsBehavior.Type.BEHAVIOR;
          case USER -> KActorsBehavior.Type.USER;
          case TASK -> KActorsBehavior.Type.TASK;
          case TRAIT -> KActorsBehavior.Type.TRAIT;
        });

    ret.setAnnotations(
        syntax.getAnnotations().stream()
            .map(
                annotation ->
                    adaptAnnotation(
                        annotation, name, projectName, KlabAsset.KnowledgeClass.BEHAVIOR))
            .toList());

    for (var imp : syntax.getImports()) {
      var imported = new KActorsBehaviorImpl.ImportImpl();
      imported.setOffsetInDocument(imp.offsetInCode());
      imported.setLength(imp.lenght());
      imported.setImportedBehavior(imp.source());
      imported.setImportedAlias(imp.alias());
      imported.getImportedComponents().addAll(imp.imports());
      ret.getImports().add(imported);
    }

    for (var inh : syntax.getInherited()) {
      var imported = new KActorsBehaviorImpl.ImportImpl();
      imported.setOffsetInDocument(inh.offsetInCode());
      imported.setLength(inh.lenght());
      imported.setImportedBehavior(inh.source());
      imported.setImportedAlias(inh.alias());
      imported.getImportedComponents().addAll(inh.imports());
      ret.getInheritedBehaviors().add(imported);
    }

    for (var action : syntax.getActions()) {
      ret.getStatements().add(adaptAction(action, ret, name, projectName, notifications));
    }
    ret.setNotifications(new ArrayList<>(notifications));

    return ret;
  }

  private KActorsAction adaptAction(
      ActionSyntax action,
      KActorsBehavior behavior,
      String namespace,
      String projectName,
      List<Notification> notifications) {

    var ret = new KActorsActionImpl();
    setParsingData(action, ret, namespace, projectName);

    ret.setUrn(action.getName());
    ret.setArguments(
        action.getArgumentNames().stream()
            .map(
                argument ->
                    new KActorsAction.Argument(
                        argument.getFirst(),
                        argument.getSecond() == null
                            ? null
                            : adaptAnnotation(
                                argument.getSecond(),
                                namespace,
                                projectName,
                                KlabAsset.KnowledgeClass.BEHAVIOR)))
            .toList());
    // ActionSyntax currently exposes the action source but not the grammar's `static` attribute.
    // Preserve the semantic contract until that syntax-bean accessor is available.
    ret.setStatic(STATIC_ACTION_PATTERN.matcher(action.encode()).find());
    for (var statement : action.getStatements()) {
      ret.getCode().add(adaptActionStatement(statement, behavior, ret, notifications));
    }
    return ret;
  }

  /**
   * Add lexical context to a language asset
   *
   * @param syntax
   * @param ret
   */
  private void setParsingData(
      ParsedObject syntax, KimAssetImpl ret, String namespace, String projectName) {
    ret.setOffsetInDocument(syntax.getCodeOffset());
    ret.setLength(syntax.getCodeLength());
    ret.setProjectName(projectName);
    ret.setDeprecation(syntax.getDeprecation());
    ret.setDeprecated(syntax.getDeprecation() != null);
    ret.setAnnotations(
        syntax.getAnnotations().stream()
            .map(
                annotation ->
                    adaptAnnotation(
                        annotation, namespace, projectName, KlabAsset.KnowledgeClass.BEHAVIOR))
            .toList());
    if (ret instanceof KimStatementImpl statement) {
      statement.setNamespace(namespace);
      statement.setDocumentClass(KlabAsset.KnowledgeClass.BEHAVIOR);
    }
  }

  private KActorsStatement adaptActionStatement(
      ActionStatementSyntax statement,
      KActorsBehavior behavior,
      KActorsAction action,
      List<Notification> notifications) {
    // TODO
    var ret =
        switch (statement) {
          case ActionStatementSyntax.Assert assertion ->
              adaptAssert(assertion, behavior, action, notifications);
          case ActionStatementSyntax.Assignment assign ->
              adaptAssign(assign, behavior, action, notifications);
          case ActionStatementSyntax.Verb verbStatement ->
              adaptVerb(verbStatement, behavior, action, notifications);
          case ActionStatementSyntax.Do doStatement ->
              adaptDo(doStatement, behavior, action, notifications);
          case ActionStatementSyntax.For forStatement ->
              adaptFor(forStatement, behavior, action, notifications);
          case ActionStatementSyntax.If ifStatement ->
              adaptIf(ifStatement, behavior, action, notifications);
          case ActionStatementSyntax.Return returnStatement ->
              adaptReturn(returnStatement, behavior, action, notifications);
          case ActionStatementSyntax.While whileStatement ->
              adaptWhile(whileStatement, behavior, action, notifications);
          case ActionStatementSyntax.Text textStatement ->
              adaptText(textStatement, behavior, action, notifications);
          case ActionStatementSyntax.Fire fireStatement ->
              adaptFire(fireStatement, behavior, action, notifications);
          case ActionStatementSyntax.Fail failStatement ->
              adaptFail(failStatement, behavior, action, notifications);
          case ActionStatementSyntax.Break breakStatement ->
              adaptBreak(breakStatement, behavior, action, notifications);
          case ActionStatementSyntax.Group groupStatement ->
              adaptGroup(groupStatement, behavior, action, notifications);
          case ActionStatementSyntax.Switch switchStatement ->
              adaptSwitch(switchStatement, behavior, action, notifications);
          case ActionStatementSyntax.Yield yieldStatement ->
              adaptYield(yieldStatement, behavior, action, notifications);
          default ->
              throw new KlabIllegalArgumentException("unknown action statement type: " + statement);
        };

    setParsingData(statement, (KimAssetImpl) ret, behavior.getUrn(), behavior.getProjectName());
    var implementation = (KActorsStatementImpl) ret;
    implementation.setSequential(statement.isSequential());
    implementation.setTag(statement.getTag());
    var metadata = Metadata.create();
    statement
        .getTrailingMetadata()
        .forEach(
            (key, value) ->
                metadata.put(
                    key,
                    value instanceof ValueSyntax syntax
                        ? adaptKActorsValue(
                            syntax, behavior.getUrn(), behavior.getProjectName(), notifications)
                        : value));
    implementation.setMetadata(metadata);

    return ret;
  }

  private KActorsStatement.Fail adaptFail(
      ActionStatementSyntax.Fail failStatement,
      KActorsBehavior behavior,
      KActorsAction action,
      List<Notification> notifications) {
    var ret = new KActorsStatementImpl.FailImpl();
    ret.setMessage(failStatement.getMessage());
    return ret;
  }

  private KActorsStatement.Assignment adaptAssign(
      ActionStatementSyntax.Assignment assign,
      KActorsBehavior behavior,
      KActorsAction action,
      List<Notification> notifications) {
    // TODO
    var ret = new KActorsStatementImpl.AssignmentImpl();
    ret.setVariable(assign.getVariable());
    ret.setAssignmentScope(
        assign.getScope() == ActionStatementSyntax.Assignment.Scope.BLOCK
            ? KActorsStatement.Assignment.Scope.FRAME
            : KActorsStatement.Assignment.Scope.ACTOR);
    ret.setAdaptedBehaviorUrn(assign.getCastToBehavior());
    if (assign.getValue() != null) {
      ret.setValue(
          adaptKActorsValue(
              assign.getValue(), behavior.getUrn(), behavior.getProjectName(), notifications));
    } else if (assign.getFunction() != null) {
      ret.setFunction(adaptVerb(assign.getFunction(), behavior, action, notifications));
    } else {
      var switchStatement = reflectedSwitch(assign);
      if (switchStatement != null) {
        ret.setSwitch(adaptSwitch(switchStatement, behavior, action, notifications));
      }
    }
    return ret;
  }

  private KActorsStatement.Do adaptDo(
      ActionStatementSyntax.Do doStatement,
      KActorsBehavior behavior,
      KActorsAction action,
      List<Notification> notifications) {
    var ret = new KActorsStatementImpl.DoImpl();
    ret.setCondition(
        doStatement.getCondition() == null
            ? null
            : adaptKActorsValue(
                doStatement.getCondition(),
                behavior.getUrn(),
                behavior.getProjectName(),
                notifications));
    ret.setFunction(
        doStatement.getFunction() == null
            ? null
            : adaptVerb(doStatement.getFunction(), behavior, action, notifications));
    ret.setAdaptedBehaviorUrn(doStatement.getCastToBehavior());
    ret.setBody(adaptActionStatement(doStatement.getStatement(), behavior, action, notifications));
    return ret;
  }

  private KActorsStatement.For adaptFor(
      ActionStatementSyntax.For forStatement,
      KActorsBehavior behavior,
      KActorsAction action,
      List<Notification> notifications) {
    var ret = new KActorsStatementImpl.ForImpl();
    ret.setVariable(forStatement.getVariable());
    ret.setIterable(
        forStatement.getCondition() == null
            ? null
            : adaptKActorsValue(
                forStatement.getCondition(),
                behavior.getUrn(),
                behavior.getProjectName(),
                notifications));
    ret.setFunction(
        forStatement.getFunction() == null
            ? null
            : adaptVerb(forStatement.getFunction(), behavior, action, notifications));
    ret.setAdaptedBehaviorUrn(forStatement.getCastToBehavior());
    ret.setBody(adaptActionStatement(forStatement.getStatement(), behavior, action, notifications));
    return ret;
  }

  private KActorsStatement.If adaptIf(
      ActionStatementSyntax.If ifStatement,
      KActorsBehavior behavior,
      KActorsAction action,
      List<Notification> notifications) {
    var ret = new KActorsStatementImpl.IfImpl();
    var thenBranch = ifStatement.getThenBranch();
    ret.setCondition(
        thenBranch.getCondition() == null
            ? null
            : adaptKActorsValue(
                thenBranch.getCondition(),
                behavior.getUrn(),
                behavior.getProjectName(),
                notifications));
    ret.setFunction(
        thenBranch.getFunction() == null
            ? null
            : adaptVerb(thenBranch.getFunction(), behavior, action, notifications));
    ret.setAdaptedBehaviorUrn(ifStatement.getCastToBehavior());
    ret.setThenBody(
        adaptActionStatement(thenBranch.getStatement(), behavior, action, notifications));
    ret.setElseIfs(
        ifStatement.getElseIfBranches().stream()
            .map(
                branch ->
                    Pair.of(
                        Triple.of(
                            (branch.getCondition() == null
                                ? null
                                : adaptKActorsValue(
                                    branch.getCondition(),
                                    behavior.getUrn(),
                                    behavior.getProjectName(),
                                    notifications)),
                            (branch.getFunction() == null
                                ? null
                                : adaptVerb(branch.getFunction(), behavior, action, notifications)),
                            branch.getCastToBehavior()),
                        adaptActionStatement(
                            branch.getStatement(), behavior, action, notifications)))
            .toList());
    ret.setElseBody(
        ifStatement.getElseStatement() == null
            ? null
            : adaptActionStatement(
                ifStatement.getElseStatement(), behavior, action, notifications));
    return ret;
  }

  private KActorsStatement.Return adaptReturn(
      ActionStatementSyntax.Return returnStatement,
      KActorsBehavior behavior,
      KActorsAction action,
      List<Notification> notifications) {
    var ret = new KActorsStatementImpl.ReturnImpl();
    ret.setAdaptedBehaviorUrn(returnStatement.getCastToBehavior());
    if (returnStatement.getReturnValue() != null) {
      ret.setValue(
          adaptKActorsValue(
              returnStatement.getReturnValue(),
              behavior.getUrn(),
              behavior.getProjectName(),
              notifications));
    } else if (returnStatement.getFunction() != null) {
      ret.setFunction(adaptVerb(returnStatement.getFunction(), behavior, action, notifications));
    } else {
      var switchStatement = reflectedSwitch(returnStatement);
      if (switchStatement != null) {
        ret.setSwitch(adaptSwitch(switchStatement, behavior, action, notifications));
      }
    }
    return ret;
  }

  private KActorsStatement.Yield adaptYield(
      ActionStatementSyntax.Yield yieldStatement,
      KActorsBehavior behavior,
      KActorsAction action,
      List<Notification> notifications) {
    var ret = new KActorsStatementImpl.YieldImpl();
    ret.setAdaptedBehaviorUrn(yieldStatement.getCastToBehavior());
    if (yieldStatement.getReturnValue() != null) {
      ret.setValue(
          adaptKActorsValue(
              yieldStatement.getReturnValue(),
              behavior.getUrn(),
              behavior.getProjectName(),
              notifications));
    } else if (yieldStatement.getFunction() != null) {
      ret.setFunction(adaptVerb(yieldStatement.getFunction(), behavior, action, notifications));
    } else {
      var switchStatement = reflectedSwitch(yieldStatement);
      if (switchStatement != null) {
        ret.setSwitch(adaptSwitch(switchStatement, behavior, action, notifications));
      }
    }
    return ret;
  }

  private KActorsStatement.While adaptWhile(
      ActionStatementSyntax.While whileStatement,
      KActorsBehavior behavior,
      KActorsAction action,
      List<Notification> notifications) {
    var ret = new KActorsStatementImpl.WhileImpl();
    ret.setCondition(
        whileStatement.getCondition() == null
            ? null
            : adaptKActorsValue(
                whileStatement.getCondition(),
                behavior.getUrn(),
                behavior.getProjectName(),
                notifications));
    ret.setFunction(
        whileStatement.getFunction() == null
            ? null
            : adaptVerb(whileStatement.getFunction(), behavior, action, notifications));
    ret.setAdaptedBehaviorUrn(reflectedCastToBehavior(whileStatement));
    ret.setBody(
        adaptActionStatement(whileStatement.getStatement(), behavior, action, notifications));
    return ret;
  }

  private KActorsStatement.Text adaptText(
      ActionStatementSyntax.Text textStatement,
      KActorsBehavior behavior,
      KActorsAction action,
      List<Notification> notifications) {
    var ret = new KActorsStatementImpl.TextImpl();
    ret.setText(textStatement.getText());
    return ret;
  }

  private KActorsStatement.Fire adaptFire(
      ActionStatementSyntax.Fire fireStatement,
      KActorsBehavior behavior,
      KActorsAction action,
      List<Notification> notifications) {
    var ret = new KActorsStatementImpl.FireImpl();
    ret.setAdaptedBehaviorUrn(fireStatement.getCastToBehavior());
    if (fireStatement.getFiredValue() != null) {
      ret.setValue(
          adaptKActorsValue(
              fireStatement.getFiredValue(),
              behavior.getUrn(),
              behavior.getProjectName(),
              notifications));
    } else if (fireStatement.getFunction() != null) {
      ret.setFunction(adaptVerb(fireStatement.getFunction(), behavior, action, notifications));
    } else {
      var switchStatement = reflectedSwitch(fireStatement);
      if (switchStatement != null) {
        ret.setSwitch(adaptSwitch(switchStatement, behavior, action, notifications));
      }
    }
    return ret;
  }

  private KActorsStatement.Break adaptBreak(
      ActionStatementSyntax.Break breakStatement,
      KActorsBehavior behavior,
      KActorsAction action,
      List<Notification> notifications) {
    // TODO
    var ret = new KActorsStatementImpl.BreakImpl();
    return ret;
  }

  private KActorsStatement.Group adaptGroup(
      ActionStatementSyntax.Group groupStatement,
      KActorsBehavior behavior,
      KActorsAction action,
      List<Notification> notifications) {
    var ret = new KActorsStatementImpl.GroupImpl();
    ret.setStatements(
        groupStatement.getStatements().stream()
            .map(statement -> adaptActionStatement(statement, behavior, action, notifications))
            .toList());
    return ret;
  }

  private KActorsStatement.Assert adaptAssert(
      ActionStatementSyntax.Assert assertion,
      KActorsBehavior behavior,
      KActorsAction action,
      List<Notification> notifications) {
    var ret = new KActorsStatementImpl.AssertImpl();
    var arguments = Parameters.<String>create();
    arguments.putAll(
        adaptArguments(
            assertion.getArguments(), behavior.getUrn(), behavior.getProjectName(), notifications));
    ret.setArguments(arguments);
    ret.setAssertions(
        assertion.getAssertions().stream()
            .map(
                syntax -> {
                  var adapted = new KActorsStatementImpl.AssertImpl.AssertionImpl();
                  setParsingData(syntax, adapted, behavior.getUrn(), behavior.getProjectName());
                  adapted.setExpression(
                      syntax.getExpression() == null
                          ? null
                          : adaptKActorsValue(
                              syntax.getExpression(),
                              behavior.getUrn(),
                              behavior.getProjectName(),
                              notifications));
                  adapted.setCalls(
                      syntax.getMethodCall() == null
                          ? List.of()
                          : List.of(
                              adaptVerb(syntax.getMethodCall(), behavior, action, notifications)));
                  if (syntax.getExpectedValue() != null) {
                    adapted.setValue(
                        adaptKActorsValue(
                            syntax.getExpectedValue(),
                            behavior.getUrn(),
                            behavior.getProjectName(),
                            notifications));
                  } else if (syntax.isOk()) {
                    var ok = new KActorsValueImpl();
                    ok.setType(ValueType.BOOLEAN);
                    ok.setStatedValue(Boolean.TRUE);
                    adapted.setValue(ok);
                  }
                  return adapted;
                })
            .map(KActorsStatement.Assert.Assertion.class::cast)
            .toList());
    return ret;
  }

  private KActorsStatement.Verb adaptVerb(
      ActionStatementSyntax.Verb verbStatement,
      KActorsBehavior behavior,
      KActorsAction action,
      List<Notification> notifications) {

    var ret = new KActorsStatementImpl.VerbImpl();
    setParsingData(verbStatement, ret, behavior.getUrn(), behavior.getProjectName());
    var declaration = verbStatement.getName().split("\\.");
    var reactorName = declaration.length == 1 ? "self" : declaration[0];
    ret.setRecipient(reactorName);
    ret.setMessage(declaration[declaration.length - 1]);

    // cannot enforce argument mapping at this stage
    ret.getArguments()
        .putAll(
            adaptArguments(
                verbStatement.getArguments(),
                behavior.getUrn(),
                behavior.getProjectName(),
                notifications));

    for (var match : verbStatement.getMatches()) {
      var m = new KActorsStatementImpl.VerbImpl.MatchActionImpl();
      setParsingData(match, m, behavior.getUrn(), behavior.getProjectName());
      m.setMatchCriterion(
          match.getMatchCondition() == null
              ? null
              : adaptKActorsValue(
                  match.getMatchCondition(),
                  behavior.getUrn(),
                  behavior.getProjectName(),
                  notifications));
      m.setActionOnMatch(
          adaptActionStatement(match.getStatement(), behavior, action, notifications));
      m.getVariables().addAll(match.getReactorVariables());
      m.setCaptureAs(match.getCaptureAs());
      ret.getActions().add(m);
    }

    return ret;
  }

  private KActorsStatement.Switch adaptSwitch(
      ActionStatementSyntax.Switch verbStatement,
      KActorsBehavior behavior,
      KActorsAction action,
      List<Notification> notifications) {

    var ret = new KActorsStatementImpl.SwitchImpl();
    setParsingData(verbStatement, ret, behavior.getUrn(), behavior.getProjectName());
    ret.setValue(
        verbStatement.getValue() == null
            ? null
            : adaptKActorsValue(
                verbStatement.getValue(),
                behavior.getUrn(),
                behavior.getProjectName(),
                notifications));
    ret.setFunction(
        verbStatement.getFunction() == null
            ? null
            : adaptVerb(verbStatement.getFunction(), behavior, action, notifications));
    ret.setAdaptedBehaviorUrn(verbStatement.getCastToBehavior());

    for (var match : verbStatement.getMatches()) {
      var m = new KActorsStatementImpl.VerbImpl.MatchActionImpl();
      setParsingData(match, m, behavior.getUrn(), behavior.getProjectName());
      m.setMatchCriterion(
          match.getMatchCondition() == null
              ? null
              : adaptKActorsValue(
                  match.getMatchCondition(),
                  behavior.getUrn(),
                  behavior.getProjectName(),
                  notifications));
      m.setActionOnMatch(
          adaptActionStatement(match.getStatement(), behavior, action, notifications));
      m.getVariables().addAll(match.getReactorVariables());
      m.setCaptureAs(match.getCaptureAs());
      ret.getCases().add(m);
    }

    return ret;
  }

  private String reflectedCastToBehavior(Object statement) {
    if (statement == null) {
      return null;
    }
    try {
      Object value = statement.getClass().getMethod("getCastToBehavior").invoke(statement);
      return value == null ? null : value.toString();
    } catch (ReflectiveOperationException ignored) {
      return null;
    }
  }

  private ActionStatementSyntax.Switch reflectedSwitch(Object statement) {
    if (statement == null) {
      return null;
    }
    try {
      Object value = statement.getClass().getMethod("getSwitchStatement").invoke(statement);
      if (value instanceof ActionStatementSyntax.Switch switchStatement) {
        return switchStatement;
      }
    } catch (ReflectiveOperationException ignored) {
      // Compatibility with syntax-bean versions that parse the embedded switch in the Xtext
      // model but do not expose it through ActionStatementSyntax yet.
    }

    var sourceStatement = capturedValue(statement, Statement.class);
    var parentAction = capturedValue(statement, ActionSyntaxImpl.class);
    if (sourceStatement == null || parentAction == null) {
      return null;
    }

    SwitchStatement switchDefinition = null;
    if (sourceStatement.getAssignment() != null) {
      switchDefinition = sourceStatement.getAssignment().getSwitchStatement();
    } else if (sourceStatement.getReturn() != null) {
      switchDefinition = sourceStatement.getReturn().getSwitchStatement();
    } else if (sourceStatement.getFire() != null) {
      switchDefinition = sourceStatement.getFire().getSwitchStatement();
    } else if (sourceStatement.getYieldSwitch() != null) {
      switchDefinition = sourceStatement.getYieldSwitch().getSwitchStatement();
    }
    if (switchDefinition == null) {
      return null;
    }

    var parsedSwitch = switchDefinition;
    return new SwitchImpl(parsedSwitch, parentAction, new BasicObservableValidationScope()) {
      @Override
      protected void logWarning(
          ParsedObject target, EObject object, EStructuralFeature feature, String message) {
        // The complete behavior syntax has already been validated before adaptation.
      }

      @Override
      protected void logError(
          ParsedObject target, EObject object, EStructuralFeature feature, String message) {
        // The complete behavior syntax has already been validated before adaptation.
      }

      @Override
      public String encode() {
        return sourceCode(parsedSwitch);
      }
    };
  }

  private <T> T capturedValue(Object object, Class<T> type) {
    for (Class<?> current = object.getClass();
        current != null && current != Object.class;
        current = current.getSuperclass()) {
      for (Field field : current.getDeclaredFields()) {
        if (!type.isAssignableFrom(field.getType())) {
          continue;
        }
        try {
          if (!field.canAccess(object)) {
            field.setAccessible(true);
          }
          Object value = field.get(object);
          if (type.isInstance(value)) {
            return type.cast(value);
          }
        } catch (ReflectiveOperationException | RuntimeException ignored) {
          // Try any other compatible captured field before giving up.
        }
      }
    }
    return null;
  }

  private Map<String, KActorsValue> adaptArguments(
      Map<String, org.eclipse.xtext.util.Pair<ValueSyntax, String>> arguments,
      String namespace,
      String projectName,
      List<Notification> notifications) {
    return arguments.entrySet().stream()
        .map(
            e ->
                new AbstractMap.SimpleEntry<>(
                    e.getKey(),
                    // TODO handle cast behavior
                    adaptKActorsValue(
                        e.getValue().getFirst(), namespace, projectName, notifications)))
        .collect(
            Collectors.toMap(
                Map.Entry::getKey,
                Map.Entry::getValue,
                (left, right) -> right,
                LinkedHashMap::new));
  }
}
