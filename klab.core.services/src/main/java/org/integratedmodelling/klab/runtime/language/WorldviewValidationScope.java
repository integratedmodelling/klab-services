package org.integratedmodelling.klab.runtime.language;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.integratedmodelling.klab.api.data.Metadata;
import org.integratedmodelling.klab.api.knowledge.SemanticType;
import org.integratedmodelling.klab.api.knowledge.Worldview;
import org.integratedmodelling.klab.api.lang.kim.KimConceptStatement;
import org.integratedmodelling.klab.api.lang.kim.KimConcept;
import org.integratedmodelling.klab.api.lang.kim.KimOntology;
import org.integratedmodelling.languages.api.ConceptDeclarationSyntax;
import org.integratedmodelling.languages.api.ObservableSyntax;
import org.integratedmodelling.languages.api.SemanticSyntax;
import org.integratedmodelling.languages.validation.BasicObservableValidationScope;
import org.integratedmodelling.languages.validation.ReasoningValidationScope;

/** Worldview-aware semantic validation scope */
public class WorldviewValidationScope extends BasicObservableValidationScope
    implements ReasoningValidationScope {

  private final Set<String> authorityCodelistNamespaces = new HashSet<>();
  private final java.util.Map<String, Set<String>> codelistOwners = new java.util.HashMap<>();

  public WorldviewValidationScope() {}

  @Override
  public ConceptDescriptor getConceptDescriptor(String name) {
    // The language's fallback splits on every colon, truncating bracketed authority codes.
    if (name != null && name.matches("[A-Z][A-Z0-9_]*(\\.[A-Z0-9_]+)*:.*")) {
      var parts = name.split(":", 2);
      return new ConceptDescriptor(parts[0], parts[1], SemanticSyntax.Type.IDENTITY,
          "Authority identity", "", false, false);
    }
    if (name != null && name.contains(":")) {
      var parts = name.split(":", 2);
      if (authorityCodelistNamespaces.contains(parts[0]))
        return new ConceptDescriptor(parts[0], parts[1], SemanticSyntax.Type.IDENTITY,
            "Authority codelist identity; approval is checked by the Reasoner", "", false, false);
    }
    return super.getConceptDescriptor(name);
  }

  public WorldviewValidationScope(Worldview worldview) {
    this();
    for (var ontology : worldview.getOntologies()) {
      for (var statement : ontology.getStatements()) {
        loadConcepts(statement, ontology.getUrn());
      }
    }
    refreshGenericDescriptors(worldview.getOntologies().stream().flatMap(o->o.getStatements().stream()).toList());
  }

  /** Isolated editing scope: old declarations from the edited ontology cannot resolve references. */
  public WorldviewValidationScope withoutNamespace(String namespace) {
    var isolated = new WorldviewValidationScope();
    synchronized (conceptTypes) {
      isolated.conceptTypes.putAll(conceptTypes);
    }
    isolated.authorityCodelistNamespaces.addAll(authorityCodelistNamespaces);
    codelistOwners.forEach((owner, names) -> isolated.codelistOwners.put(owner, new HashSet<>(names)));
    isolated.clearNamespace(namespace);
    return isolated;
  }

  public void clearNamespace(String namespace) {
    codelistOwners.remove(namespace);
    authorityCodelistNamespaces.clear();
    codelistOwners.values().forEach(authorityCodelistNamespaces::addAll);
    Set<String> keys = new HashSet<>();
    String ns = namespace + ":";
    for (var concept : conceptTypes.keySet()) {
      if (concept.startsWith(ns)) {
        keys.add(concept);
      }
    }
    synchronized (conceptTypes) {
      keys.forEach(key -> conceptTypes.remove(key));
    }
  }

  public void addNamespace(KimOntology ontology) {
    for (var statement : ontology.getStatements()) {
      loadConcepts(statement, ontology.getUrn());
    }
    refreshGenericDescriptors(ontology.getStatements());
  }

  @Override
  public ConceptDescriptor createConceptDescriptor(ConceptDeclarationSyntax declaration) {
    // trust the "is core" to define the type for all core ontology concepts

    if (declaration.isCoreDeclaration()) {
      SemanticSyntax coreConcept = declaration.getDeclaredParent();
      var coreId = coreConcept.encode();
      var cname = coreConcept.encode().split(":");
      var declaredType=CoreAliasTypeSupport.syntaxType(coreId,declaration.getDeclaredType());

      this.conceptTypes.put(
          coreConcept.encode(),
          new ConceptDescriptor(
              cname[0],
              cname[1],
              declaredType,
              coreConcept.encode(),
              "Core concept " + coreConcept.encode() + " for type " + declaredType,
              true,
              false));
      // Do not delegate core registration to language versions that infer generic types
      // from the intentionally untyped core parent.
      var descriptor = new ConceptDescriptor(declaration.getNamespace(), declaration.getName(),
          declaredType, declaration.getDescription(), declaration.getName(),
          declaration.isAbstract(), false, declaration.isSubjective(), declaration.isSealed());
      conceptTypes.put(declaration.getNamespace() + ":" + declaration.getName(), descriptor);
      return descriptor;
    }
    if (declaration.isGenericQuality() && declaration.getDeclaredParent() != null) {
      // Descriptor registration must not compile syntax. Core parents in particular are
      // intentionally untyped: the declaration supplies their type, not vice versa.
      var type = inheritedType(declaration.getDeclaredParent());
      var namedParent=conceptTypes.get(declaration.getDeclaredParent().encode());
      if(namedParent!=null)type=namedParent.mainType();
      var descriptor = new ConceptDescriptor(declaration.getNamespace(), declaration.getName(),
          type, declaration.getDescription(), declaration.getName(),
          declaration.isAbstract(), false, declaration.isSubjective(), declaration.isSealed());
      conceptTypes.put(declaration.getNamespace() + ":" + declaration.getName(), descriptor);
      return descriptor;
    }
    return super.createConceptDescriptor(declaration);
  }

  private static SemanticSyntax.Type inheritedType(SemanticSyntax parent) {
    var result = parent.getType();
    for (var token : parent) {
      var type = token.getType();
      // Accommodate older language beans that still report the unary operand's type.
      var unary = token.getUnaryOperator();
      if (unary != null && unary.getFirst() != null && token.getObservable() != null) {
        var operand = token.getObservable().concept().mainType();
        type = operand == null ? SemanticSyntax.Type.NOTHING
            : unary.getFirst().getType(operand, unary.getSecond());
      }
      if (type == null || !type.is(SemanticSyntax.TypeCategory.VALID)) {
        return SemanticSyntax.Type.NOTHING;
      }
      if (type.is(SemanticSyntax.TypeCategory.OBSERVABLE)) result = type;
    }
    return result == null ? SemanticSyntax.Type.NOTHING : result;
  }

  /** Older transported beans can still carry the coarse kind on named generic children. */
  private void refreshGenericDescriptors(List<KimConceptStatement> roots) {
    var declarations=new java.util.ArrayList<KimConceptStatement>();
    java.util.function.Consumer<KimConceptStatement> collect=new java.util.function.Consumer<>() {
      @Override public void accept(KimConceptStatement statement) {
        declarations.add(statement);statement.getChildren().forEach(this);
      }
    };
    roots.forEach(collect);
    for(int pass=0;pass<declarations.size();pass++) {
      boolean changed=false;
      for(var statement:declarations) {
        if(!statement.isGenericQuality() || statement.getUpperConceptDefined()!=null
            || statement.getDeclaredParent()==null)continue;
        var key=statement.getNamespace()+":"+statement.getUrn();
        var previous=conceptTypes.get(key);
        var type=capturedParentType(statement.getDeclaredParent(),statement.getNamespace());
        if(previous==null || type==SemanticSyntax.Type.NOTHING || previous.mainType()==type)continue;
        conceptTypes.put(key,new ConceptDescriptor(statement.getNamespace(),statement.getUrn(),type,
            previous.label(),previous.description(),statement.isAbstract(),false,
            statement.isSubjective(),statement.isSealed()));
        changed=true;
      }
      if(!changed)break;
    }
  }

  private SemanticSyntax.Type capturedParentType(KimConcept parent,String namespace) {
    if(parent.getSemanticModifier()==null && parent.getName()!=null) {
      var name=parent.getName();if(!name.contains(":"))name=namespace+":"+name;
      var descriptor=conceptTypes.get(name);
      if(descriptor!=null)return descriptor.mainType();
    }
    if(parent.getSemanticModifier()==null && parent.getObservable()!=null)
      return capturedParentType(parent.getObservable(),namespace);
    return parent.getType()==null || parent.getType().isEmpty()
        ? SemanticSyntax.Type.NOTHING:getMainType(parent.getType());
  }

  private void loadConcepts(KimConceptStatement statement, String namespace) {
    if (statement.getAuthorityRequired() != null && statement.getAuthorityParameters() != null
        && statement.getAuthorityParameters().get("codelists") instanceof java.util.Map<?, ?> names)
      for (var value : names.values()) if (value instanceof String name) {
        authorityCodelistNamespaces.add(name);
        codelistOwners.computeIfAbsent(namespace, key -> new HashSet<>()).add(name);
      }
    var effectiveTypes=CoreAliasTypeSupport.types(statement.getUpperConceptDefined(),statement.getType());
    String defaultLabel = namespace + ":" + statement.getUrn();
    ConceptDescriptor descriptor =
        new ConceptDescriptor(
            namespace,
            statement.getUrn(),
            getMainType(effectiveTypes),
            statement.getMetadata().get(Metadata.DC_LABEL, defaultLabel),
            statement.getMetadata().get(Metadata.DC_COMMENT, String.class),
            statement.isAbstract(),
            false, statement.isSubjective(), statement.isSealed());
    this.conceptTypes.put(defaultLabel, descriptor);
    if (statement.deniable() && statement.getType().contains(SemanticType.ATTRIBUTE)) {
      var names = new java.util.LinkedHashSet<String>();
      names.add("Not" + statement.getUrn());
      if (statement.deniedAlias() != null) names.add(statement.deniedAlias());
      for (var name : names) conceptTypes.put(namespace + ":" + name,
          new ConceptDescriptor(namespace, name, SemanticSyntax.Type.ATTRIBUTE,
              "Not " + descriptor.label(), descriptor.description(), false, false,
              statement.isSubjective(), statement.isSealed()));
    }
    // A captured worldview must register the same core target descriptors that the normal
    // parser registers through createConceptDescriptor(). The core RDF is deliberately
    // untyped; omitting these source-defined aliases turns expressions such as odo:Speed
    // into Nothing even though the captured declarations resolve them.
    if (statement.getUpperConceptDefined() != null) {
      var coreId = statement.getUpperConceptDefined();
      var parts = coreId.split(":", 2);
      if (parts.length == 2) conceptTypes.put(coreId, new ConceptDescriptor(parts[0], parts[1],
          getMainType(effectiveTypes), coreId,
          "Core concept " + coreId + " for type " + getMainType(effectiveTypes), true, false));
    }
    for (var child : statement.getChildren()) {
      loadConcepts(child, namespace);
    }
  }

  /**
   * Turn the K.IM main semantic typeset into its syntactic equivalent. No VOID is allowed if a
   * worldview is loaded.
   *
   * @param type
   * @return
   */
  public static SemanticSyntax.Type getMainType(Set<SemanticType> type) {
    Set<SemanticType> strippedType = EnumSet.copyOf(type);
    strippedType.retainAll(SemanticType.DECLARABLE_TYPES);
    if (strippedType.isEmpty() && type.contains(SemanticType.QUALITY)) {
      // shouldn't happen in KimDeclarations
      return SemanticSyntax.Type.GENERIC_QUALITY;
    } else if (strippedType.size() == 1) {
      var mainType = strippedType.iterator().next();
      // Preserve the source relationship family only after validating one declarable type.
      // Conflicting families are malformed source evidence, not an arbitrary choice.
      if (mainType == SemanticType.RELATIONSHIP) {
        if (type.contains(SemanticType.STRUCTURAL) && type.contains(SemanticType.FUNCTIONAL))
          return SemanticSyntax.Type.NOTHING;
        if (type.contains(SemanticType.STRUCTURAL)) return SemanticSyntax.Type.STRUCTURAL_RELATIONSHIP;
        if (type.contains(SemanticType.FUNCTIONAL)) return SemanticSyntax.Type.FUNCTIONAL_RELATIONSHIP;
      }
      return getMainType(mainType);
    }
    // No VOID admitted if we have a worldview
    return SemanticSyntax.Type.NOTHING;
  }

  public static SemanticSyntax.Type getMainType(SemanticType type) {
    return switch (type) {
      case PROPORTION -> SemanticSyntax.Type.PROPORTION;
      case PROBABILITY -> SemanticSyntax.Type.PROBABILITY;
      case DISTANCE, LENGTH -> SemanticSyntax.Type.LENGTH;
      case VALUE -> SemanticSyntax.Type.VALUE;
      case OCCURRENCE -> SemanticSyntax.Type.OCCURRENCE;
      case PRESENCE -> SemanticSyntax.Type.PRESENCE;
      case UNCERTAINTY -> SemanticSyntax.Type.UNCERTAINTY;
      case NUMEROSITY -> SemanticSyntax.Type.NUMEROSITY;
      case RATE -> SemanticSyntax.Type.RATE;
      case CLASS -> SemanticSyntax.Type.CLASS;
      case QUANTITY -> SemanticSyntax.Type.QUANTITY;
      case ENERGY -> SemanticSyntax.Type.ENERGY;
      case ENTROPY -> SemanticSyntax.Type.ENTROPY;
      case ROLE -> SemanticSyntax.Type.ROLE;
      case EXTENT -> SemanticSyntax.Type.EXTENT;
      case MONETARY_VALUE -> SemanticSyntax.Type.MONETARY_VALUE;
      case DOMAIN -> SemanticSyntax.Type.DOMAIN;
      case MASS -> SemanticSyntax.Type.MASS;
      case VOLUME -> SemanticSyntax.Type.VOLUME;
      case WEIGHT -> SemanticSyntax.Type.WEIGHT;
      case MONEY -> SemanticSyntax.Type.MONEY;
      case DURATION -> SemanticSyntax.Type.DURATION;
      case AREA -> SemanticSyntax.Type.AREA;
      case ACCELERATION -> SemanticSyntax.Type.ACCELERATION;
      case PRIORITY -> SemanticSyntax.Type.PRIORITY;
      case ELECTRIC_POTENTIAL -> SemanticSyntax.Type.ELECTRIC_POTENTIAL;
      case CHARGE -> SemanticSyntax.Type.CHARGE;
      case RESISTANCE -> SemanticSyntax.Type.RESISTANCE;
      case RESISTIVITY -> SemanticSyntax.Type.RESISTIVITY;
      case PRESSURE -> SemanticSyntax.Type.PRESSURE;
      case ANGLE -> SemanticSyntax.Type.ANGLE;
      case VELOCITY -> SemanticSyntax.Type.VELOCITY;
      case TEMPERATURE -> SemanticSyntax.Type.TEMPERATURE;
      case VISCOSITY -> SemanticSyntax.Type.VISCOSITY;
      case RATIO -> SemanticSyntax.Type.RATIO;
      case AMOUNT -> SemanticSyntax.Type.AMOUNT;
      case SUBJECT -> SemanticSyntax.Type.SUBJECT;
      case AGENT -> SemanticSyntax.Type.AGENT;
      case EVENT -> SemanticSyntax.Type.EVENT;
      case RELATIONSHIP -> SemanticSyntax.Type.FUNCTIONAL_RELATIONSHIP;
      case PROCESS -> SemanticSyntax.Type.PROCESS;
      case CONFIGURATION -> SemanticSyntax.Type.CONFIGURATION;
      case ATTRIBUTE -> SemanticSyntax.Type.ATTRIBUTE;
      case REALM -> SemanticSyntax.Type.REALM;
      case IDENTITY -> SemanticSyntax.Type.IDENTITY;
      case ORDERING -> SemanticSyntax.Type.ORDERING;
      default -> SemanticSyntax.Type.NOTHING;
    };
  }

  @Override
  public SemanticSyntax.Type validate(SemanticSyntax concept, List<ValidationMessage> messages) {
    return concept.getType();
  }

  @Override
  public List<ValidationMessage> validateObservable(ObservableSyntax observable) {
    return List.of();
  }
}
