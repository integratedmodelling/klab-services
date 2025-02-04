package org.integratedmodelling.klab.services.resources.lang;

import java.util.*;
import org.integratedmodelling.common.lang.ContextualizableImpl;
import org.integratedmodelling.klab.api.services.runtime.impl.ExpressionCodeImpl;
import org.integratedmodelling.common.lang.QuantityImpl;
import org.integratedmodelling.common.lang.ServiceCallImpl;
import org.integratedmodelling.common.lang.kim.*;
import org.integratedmodelling.klab.api.collections.Identifier;
import org.integratedmodelling.klab.api.collections.Pair;
import org.integratedmodelling.klab.api.data.Metadata;
import org.integratedmodelling.klab.api.data.Version;
import org.integratedmodelling.klab.api.data.mediation.impl.NumericRangeImpl;
import org.integratedmodelling.klab.api.exceptions.KlabUnimplementedException;
import org.integratedmodelling.klab.api.knowledge.KlabAsset;
import org.integratedmodelling.klab.api.knowledge.SemanticType;
import org.integratedmodelling.klab.api.lang.*;
import org.integratedmodelling.klab.api.lang.kim.*;
import org.integratedmodelling.klab.api.services.runtime.Notification;
import org.integratedmodelling.klab.api.services.runtime.extension.Instance;
import org.integratedmodelling.klab.api.utils.Utils;
import org.integratedmodelling.languages.RangeLiteral;
import org.integratedmodelling.languages.api.*;

/** Adapter to substitute the current ones, based on older k.IM grammars. */
public enum LanguageAdapter {
  INSTANCE;

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

    KimObservableImpl ret = new KimObservableImpl();

    ret.setLength(observableSyntax.getCodeLength());
    ret.setOffsetInDocument(observableSyntax.getCodeOffset());
    ret.setUrn(observableSyntax.encode());
    ret.setNamespace(namespace);
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

    KimConceptImpl ret = new KimConceptImpl();

    ret.setLength(semantics.getCodeLength());
    ret.setOffsetInDocument(semantics.getCodeOffset());
    ret.setType(adaptSemanticType(semantics.getType()));
    ret.setNegated(semantics.isNegated());
    ret.setCollective(semantics.isCollective());
    //    ret.setCodeName(semantics.codeName());
    ret.setDeprecation(semantics.getDeprecation());
    ret.setDeprecated(semantics.getDeprecation() != null);
    ret.setNamespace(namespace);
    ret.setProjectName(projectName);
    ret.setDocumentClass(documentClass);
    ret.setPattern(semantics.isPattern());
    ret.getPatternVariables().addAll(semantics.getPatternVariables());

    if (semantics.isLeafDeclaration()) {
      ret.setName(semantics.encode());
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
      ret.getType().add(logicalOperator == SemanticSyntax.BinaryOperator.OR ? SemanticType.UNION : SemanticType.INTERSECTION);
      ret.getOperands().addAll(logicalOperands);
    }

    // TODO establish abstract and generic nature

    ret.finalizeDefinition();

    return ret;
  }

  public KimNamespace adaptNamespace(
      NamespaceSyntax namespace, String projectName, Collection<Notification> notifications) {

    var ret = new KimNamespaceImpl();
    ret.setUrn(namespace.getUrn());
    ret.setScenario(namespace.isScenario());
    ret.setSourceCode(namespace.getSourceCode());
    ret.setProjectName(projectName);

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
      if (parsedLiteral.isIdentifier()) {
        return Identifier.create(parsedLiteral.getPod().toString());
      }
      if (parsedLiteral.getCurrency() != null || parsedLiteral.getUnit() != null) {
        QuantityImpl ret = new QuantityImpl();
        ret.setCurrency(parsedLiteral.getCurrency());
        ret.setUnit(parsedLiteral.getUnit());
        ret.setValue(parsedLiteral.getPod() instanceof Number number ? number : 0);
        return ret;
      }
      object = adaptValue(parsedLiteral.getPod(), namespace, projectName, documentClass);
      if (object == null) {
        return null;
      }
    } /*else if (object instanceof Literal literal) {
          object = literal.get(Object.class);
      } */ else if (object instanceof ObservableSyntax observableSyntax) {
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

  private Notification.LexicalContext asLexicalContext(ParsedObject object) {
    // TODO
    return null;
  }

  private KlabStatement adaptModel(ModelSyntax model, KimNamespace namespace) {

    KimModelImpl ret = new KimModelImpl();

    ret.setNamespace(namespace.getUrn());
    ret.setDeprecated(model.getDeprecation() != null);
    ret.setDeprecation(model.getDeprecation());
    ret.setUrn(namespace.getUrn() + "." + model.getName());
    //        ret.setName(model.getName());
    ret.setOffsetInDocument(model.getCodeOffset());
    ret.setLength(model.getCodeLength());
    ret.setProjectName(namespace.getProjectName());
    ret.setDocumentClass(KlabAsset.KnowledgeClass.NAMESPACE);
    ret.getResourceUrns().addAll(model.getResourceUrns());

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

    for (var observable : model.getObservables()) {
      var obs =
          adaptObservable(
              observable,
              namespace.getUrn(),
              namespace.getProjectName(),
              KlabAsset.KnowledgeClass.NAMESPACE);
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

    ret.setInactive(inactive);

    for (var contextualizable : model.getContextualizations()) {
      ret.getContextualization().add(adaptContextualizable(contextualizable, namespace));
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
      System.out.println("PORCODIO UN'ESPRESSIONE DEL PENE");
      ret.setExpression(adaptExpression(expressionSyntax, namespace));
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
      Collection<Notification> notifications) {

    KimObservationStrategiesImpl ret = new KimObservationStrategiesImpl();
    ret.setUrn(definition.getUrn());
    ret.getNotifications().addAll(notifications);
    ret.setSourceCode(definition.getSourceCode());
    ret.setProjectName(projectName);

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
      if (!operation.getDeferredStrategies().isEmpty()) {
        o.getDeferredStrategies()
            .addAll(
                operation.getDeferredStrategies().stream()
                    .map(s -> adaptStrategy(s, namespace, projectName))
                    .toList());
      }
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
      OntologySyntax ontology, String projectName, Collection<Notification> notifications) {

    KimOntologyImpl ret = new KimOntologyImpl();

    ret.setUrn(ontology.getName());
    ret.getImportedOntologies().addAll(ontology.getImportedOntologies());
    ret.setSourceCode(ontology.getSourceCode());
    ret.getMetadata().put(Metadata.DC_COMMENT, ontology.getDescription());
    ret.setVersion(Version.create(ontology.getVersion()));
    ret.setProjectName(projectName);

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
}
