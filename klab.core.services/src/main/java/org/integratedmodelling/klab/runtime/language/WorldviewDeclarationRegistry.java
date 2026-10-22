package org.integratedmodelling.klab.runtime.language;

import java.util.*;
import org.integratedmodelling.klab.api.knowledge.SemanticType;
import org.integratedmodelling.klab.api.lang.kim.*;
import org.integratedmodelling.klab.api.services.runtime.Notification;

/** Source-owned typed declaration index. Only structural ancestry/alias edges constrain order. */
public final class WorldviewDeclarationRegistry {
  public record Declaration(String urn, KimOntology ontology, KimConceptStatement statement,
      KimConceptStatement enclosing) {}
  private final Map<String,Declaration> declarations = new LinkedHashMap<>();
  private final List<Notification> errors = new ArrayList<>();
  private final List<Declaration> ordered = new ArrayList<>();
  private final Map<String,Set<SemanticType>> coreTypes = new HashMap<>();
  private final Map<String,String> denialOwners = new LinkedHashMap<>();

  public WorldviewDeclarationRegistry(Collection<KimOntology> ontologies) {
    for (var ontology : ontologies)
      for (var statement : ontology.getStatements()) index(ontology,statement,null);
    for (var entry : declarations.values()) {
      try {
        var alias = entry.statement().deniedAlias();
        if (!entry.statement().deniable()) continue;
        if (!entry.statement().getType().contains(SemanticType.ATTRIBUTE))
          error("DENIAL_SOURCE_INVALID: deniable requires an attribute", entry);
        var names = new LinkedHashSet<String>();
        names.add(urn(entry.ontology().getUrn(), "Not" + entry.statement().getUrn().replaceFirst("^.*:", "")));
        if (alias != null) names.add(urn(entry.ontology().getUrn(), alias));
        for (var name : names) {
          if (declarations.containsKey(name) || denialOwners.putIfAbsent(name, entry.urn()) != null)
            error("DENIAL_NAME_COLLISION: " + name, entry);
        }
      } catch (IllegalArgumentException error) { error(error.getMessage(), entry); }
    }
    var dependencies = new LinkedHashMap<String,Set<String>>();
    var dependents = new HashMap<String,List<String>>();
    for (var entry : declarations.values()) {
      var needed = new LinkedHashSet<String>();
      if (entry.enclosing()!=null && !entry.statement().isAlias()
          && entry.statement().getUpperConceptDefined()==null)
        needed.add(urn(entry.ontology().getUrn(),entry.enclosing().getUrn()));
      structuralHeads(entry.statement().getDeclaredParent(),entry.ontology().getUrn(),needed);
      var resolved = new LinkedHashSet<String>();
      for (var name : needed) resolved.add(denialOwners.getOrDefault(name, name));
      needed = resolved;
      needed.removeIf(name->!declarations.containsKey(name));
      dependencies.put(entry.urn(),needed);
      needed.forEach(parent->dependents.computeIfAbsent(parent,k->new ArrayList<>()).add(entry.urn()));
      validateParentType(entry);
      validateOperands(entry);
    }
    // Preserve authored order whenever ancestry permits it. A FIFO would move
    // every initially independent later declaration ahead of earlier dependents.
    var positions = new HashMap<String,Integer>();int position=0;
    for(var name:declarations.keySet())positions.put(name,position++);
    var ready = new PriorityQueue<String>(Comparator.comparingInt(positions::get));
    dependencies.forEach((name,parents)->{if(parents.isEmpty())ready.add(name);});
    while(!ready.isEmpty()) {
      var name=ready.remove();ordered.add(declarations.get(name));
      for(var dependent:dependents.getOrDefault(name,List.of()))
        if(dependencies.get(dependent).remove(name)&&dependencies.get(dependent).isEmpty())ready.add(dependent);
    }
    if(ordered.size()!=declarations.size())
      dependencies.forEach((name,parents)->{if(!parents.isEmpty())
        error("DECLARATION_ANCESTRY_CYCLE: unresolved parent/alias ancestry at "+name+" -> "+parents,declarations.get(name));});
    resolveRelationshipAxes();
  }

  private void resolveRelationshipAxes() {
    for(var entry:ordered) {
      var s=entry.statement();
      if(!s.getType().contains(SemanticType.RELATIONSHIP))continue;
      try {
        // Effective flags on transported parents are never the authored choices of this source.
        org.integratedmodelling.klab.api.knowledge.RelationshipAxes.of(s.getType());
        var parent=RelationshipDeclarationSupport.inherited(s.getDeclaredParent(),x->namedType(x,entry.ontology().getUrn()));
        if(entry.enclosing()!=null && !s.isAlias() && s.getUpperConceptDefined()==null)parent=parent.merge(org.integratedmodelling.klab.api.knowledge.RelationshipAxes.of(entry.enclosing().getType()));
        var names=new LinkedHashSet<String>();structuralHeads(s.getDeclaredParent(),entry.ontology().getUrn(),names);
        // Partial namespace validation defers external ancestry to the complete worldview registry.
        boolean complete=names.stream().allMatch(name->declarations.containsKey(name)||coreTypes.containsKey(name));
        var axes=RelationshipDeclarationSupport.resolve(s,parent,complete);
        if(!relationshipAxesAdmit(entry,axes))
          throw new IllegalArgumentException("RELATIONSHIP_AXIS_CONFLICT: no compatible inherited boolean branch");
        axes.apply(s.getType());
      } catch(IllegalArgumentException failure) { error(failure.getMessage()+" at "+entry.urn()+" parent="+(s.getDeclaredParent()==null?"<none>":s.getDeclaredParent().getUrn()),entry); }
    }
  }

  private boolean relationshipAxesAdmit(Declaration entry,org.integratedmodelling.klab.api.knowledge.RelationshipAxes requested) {
    for(var nature:List.of(org.integratedmodelling.klab.api.knowledge.RelationshipAxes.Nature.STRUCTURAL,org.integratedmodelling.klab.api.knowledge.RelationshipAxes.Nature.FUNCTIONAL))
      for(var orientation:List.of(org.integratedmodelling.klab.api.knowledge.RelationshipAxes.Orientation.DIRECTED,org.integratedmodelling.klab.api.knowledge.RelationshipAxes.Orientation.BOND)) {
        var candidate=new org.integratedmodelling.klab.api.knowledge.RelationshipAxes(nature,orientation);
        try { requested.merge(candidate); if(axisDeclarationAdmits(entry,candidate,new HashSet<>()))return true; }
        catch(IllegalArgumentException conflict) { /* Try the other finite axis combination. */ }
      }
    return false;
  }
  private boolean axisDeclarationAdmits(Declaration entry,org.integratedmodelling.klab.api.knowledge.RelationshipAxes candidate,Set<String> visiting) {
    if(!visiting.add(entry.urn()))return false;
    try {
      RelationshipDeclarationSupport.authored(entry.statement()).merge(candidate);
      if(!axisExpressionAdmits(entry.statement().getDeclaredParent(),entry.ontology().getUrn(),candidate,visiting))return false;
      if(entry.enclosing()!=null&&!entry.statement().isAlias()&&entry.statement().getUpperConceptDefined()==null) {
        var parent=get(urn(entry.ontology().getUrn(),entry.enclosing().getUrn()));
        if(parent!=null&&!axisDeclarationAdmits(parent,candidate,visiting))return false;
      }
      return true;
    } catch(IllegalArgumentException conflict) { return false; }
    finally { visiting.remove(entry.urn()); }
  }
  private boolean axisExpressionAdmits(KimConcept expression,String namespace,org.integratedmodelling.klab.api.knowledge.RelationshipAxes candidate,Set<String> visiting) {
    if(expression==null)return true;
    var terms=new ArrayList<Boolean>();
    if(expression.getName()!=null) {
      var target=get(urn(namespace,expression.getName()));
      terms.add(target==null?axisFlagsAdmit(namedType(expression,namespace),candidate):axisDeclarationAdmits(target,candidate,visiting));
    } else if(expression.getObservable()!=null)terms.add(axisExpressionAdmits(expression.getObservable(),namespace,candidate,visiting));
    for(var operand:expression.getOperands())terms.add(axisExpressionAdmits(operand,namespace,candidate,visiting));
    return terms.isEmpty()|| (expression.getExpressionType()==KimConcept.Expression.UNION?terms.stream().anyMatch(Boolean::booleanValue):terms.stream().allMatch(Boolean::booleanValue));
  }
  private boolean axisFlagsAdmit(Set<SemanticType> flags,org.integratedmodelling.klab.api.knowledge.RelationshipAxes candidate) {
    try {org.integratedmodelling.klab.api.knowledge.RelationshipAxes.of(flags).merge(candidate);return true;}
    catch(IllegalArgumentException conflict){return false;}
  }

  public List<Notification> errors(){return List.copyOf(errors);}
  public List<Declaration> ordered(){return List.copyOf(ordered);}
  public Collection<Declaration> declarations(){return Collections.unmodifiableCollection(declarations.values());}
  public Declaration get(String urn){return declarations.get(denialOwners.getOrDefault(urn, urn));}
  public static String urn(String namespace,String name){return name!=null&&name.contains(":")?name:namespace+":"+name;}

  private void index(KimOntology ontology,KimConceptStatement statement,KimConceptStatement parent) {
    var name=statement.getUrn();var entry=new Declaration(urn(ontology.getUrn(),name),ontology,statement,parent);
    if(name==null||name.isBlank()||!entry.urn().matches("[a-z][a-zA-Z0-9_.]*:[A-Z][a-zA-Z0-9_]*")) {
      error("DECLARATION_NAME_INVALID: "+entry.urn(),entry);return;
    }
    if(!entry.urn().startsWith(ontology.getUrn()+":")
        || (statement.getNamespace()!=null&&!statement.getNamespace().isBlank()
            &&!statement.getNamespace().equals(ontology.getUrn())))
      error("DECLARATION_OWNER_INVALID: "+entry.urn()+" is not owned by "+ontology.getUrn(),entry);
    if(declarations.putIfAbsent(entry.urn(),entry)!=null)
      error("DECLARATION_DUPLICATE: "+entry.urn(),entry);
    if(statement.getUpperConceptDefined()!=null)
      coreTypes.putIfAbsent(statement.getUpperConceptDefined(),statement.getType());
    var type=statement.getType();
    if(type==null||type.isEmpty()||type.contains(SemanticType.NOTHING)
        || (Collections.disjoint(type,SemanticType.DECLARABLE_TYPES)&&!type.contains(SemanticType.QUALITY)))
      error("DECLARATION_TYPE_UNRESOLVED: "+entry.urn(),entry);
    for(var child:statement.getChildren())index(ontology,child,statement);
  }

  private void validateParentType(Declaration entry) {
    var parent=entry.statement().getDeclaredParent();
    if(parent==null||entry.statement().getUpperConceptDefined()!=null)return;
    var own=family(entry.statement().getType());
    // Transported expression flags cannot relabel a source-owned named parent.
    // Unary semantic operators retain their result kind instead of the operand's.
    var inherited=family(typeOf(parent,entry.ontology().getUrn()));
    if(own!=null&&inherited!=null&&!own.equals(inherited))
      error("DECLARATION_PARENT_TYPE_MISMATCH: "+entry.urn()+" ("+own+") inherits "+inherited,entry);
  }

  private static String family(Set<SemanticType> type) {
    if(type==null)return null;
    for(var kind:List.of(SemanticType.QUALITY,SemanticType.CONFIGURATION,SemanticType.PROCESS,
        SemanticType.SUBJECT,SemanticType.AGENT,SemanticType.EVENT,SemanticType.RELATIONSHIP,
        SemanticType.DOMAIN,SemanticType.ROLE,SemanticType.IDENTITY,SemanticType.REALM,SemanticType.ATTRIBUTE))
      if(type.contains(kind))return kind.name();
    return null;
  }

  private Set<SemanticType> typeOf(KimConcept expression,String namespace) {
    if(expression==null)return Set.of();
    if(expression.getSemanticModifier()!=null) {
      var operand=expression.getObservable()==null?namedType(expression,namespace):typeOf(expression.getObservable(),namespace);
      var result=expression.getSemanticModifier().apply(operand);
      return result==null?operand:result;
    }
    if(expression.getName()!=null)return namedType(expression,namespace);
    if(expression.getObservable()!=null)return typeOf(expression.getObservable(),namespace);
    return expression.getType()==null?Set.of():expression.getType();
  }
  private Set<SemanticType> namedType(KimConcept expression,String namespace) {
    if(expression.getName()!=null) {
      var name = urn(namespace,expression.getName());
      var named=declarations.get(denialOwners.getOrDefault(name, name));
      if(named!=null)return named.statement().getType();
      if(coreTypes.containsKey(expression.getName()))return coreTypes.get(expression.getName());
    }
    return expression.getType()==null?Set.of():expression.getType();
  }
  private static boolean observable(Set<SemanticType> type) {
    return !type.contains(SemanticType.NOTHING)&&(type.contains(SemanticType.OBSERVABLE)
        ||type.contains(SemanticType.CONFIGURATION)||type.contains(SemanticType.EXTENT)
        ||!Collections.disjoint(type,SemanticType.BASE_OBSERVABLE_TYPES));
  }
  private static boolean predicate(Set<SemanticType> type) {
    return type.contains(SemanticType.PREDICATE)||type.contains(SemanticType.DOMAIN)
        ||type.contains(SemanticType.ROLE)||!Collections.disjoint(type,SemanticType.TRAIT_TYPES);
  }
  private void require(Declaration entry,KimConcept expression,String clause,
      java.util.function.Predicate<Set<SemanticType>> valid) {
    if(expression!=null&&!valid.test(typeOf(expression,entry.ontology().getUrn())))
      error("DECLARATION_CLAUSE_TYPE_INVALID: "+entry.urn()+" "+clause+" "+expression.getUrn(),entry);
  }
  private void validateOperands(Declaration entry) {
    var s=entry.statement();var own=s.getType();
    for(var description:s.getObservablesDescribed()) {
      boolean source=switch(description.getSecond()) {
        case DESCRIBES,CLASSIFIES -> own.contains(SemanticType.QUALITY)
            &&description.getSecond()==KimConceptStatement.DescriptionType.DESCRIBES||predicate(own);
        case DISCRETIZES -> own.contains(SemanticType.ORDERING);
        default -> own.contains(SemanticType.QUALITY);
      };
      if(!source)error("DECLARATION_CLAUSE_SOURCE_INVALID: "+entry.urn()+" "+description.getSecond(),entry);
      if(description.getFirst()==null)error("DECLARATION_CLAUSE_TARGET_MISSING: "+entry.urn()+" "+description.getSecond(),entry);
      else require(entry,description.getFirst(),description.getSecond().name(),type->switch(description.getSecond()) {
        case DESCRIBES -> observable(type);
        case MARKS -> type.contains(SemanticType.PRESENCE);
        case DISCRETIZES -> type.contains(SemanticType.QUANTIFIABLE);
        default -> type.contains(SemanticType.QUALITY);
      });
    }
    for(var operand:s.getQualitiesAffected())require(entry,operand,"affects",type->type.contains(SemanticType.QUALITY));
    for(var operand:s.getAffectedBy())require(entry,operand,"affected by",type->type.contains(SemanticType.PROCESS)||type.contains(SemanticType.EVENT));
    for(var operand:s.getObservablesCreated())require(entry,operand,"creates",WorldviewDeclarationRegistry::observable);
    for(var operand:s.getCreatedBy())require(entry,operand,"created by",type->type.contains(SemanticType.PROCESS)||type.contains(SemanticType.EVENT));
    for(var operand:s.getRequiredIdentities())require(entry,operand,"requires identity",type->type.contains(SemanticType.IDENTITY));
    for(var operand:s.getRequiredRealms())require(entry,operand,"requires realm",type->type.contains(SemanticType.REALM));
    for(var operand:s.getRequiredAttributes())require(entry,operand,"requires attribute",type->type.contains(SemanticType.ATTRIBUTE));
    for(var operand:s.getRequiredExtents())require(entry,operand,"requires extent",type->type.contains(SemanticType.EXTENT));
    for(var operand:s.getEmergenceTriggers())require(entry,operand,"emerges from",WorldviewDeclarationRegistry::observable);
    for(var operand:s.getImpliedObservables())require(entry,operand,"implies",WorldviewDeclarationRegistry::observable);
    require(entry,s.getDeclaredInherent(),"inherent",WorldviewDeclarationRegistry::observable);
    for (var declaration : s.getObservationalContainments()) {
      if (declaration == null || declaration.container() == null || declaration.contained() == null) {
        error("DECLARATION_CLAUSE_TARGET_MISSING: " + entry.urn() + " observational containment", entry);
        continue;
      }
      var container = typeOf(declaration.container(), entry.ontology().getUrn());
      var contained = typeOf(declaration.contained(), entry.ontology().getUrn());
      int family = containmentFamily(container);
      if (family == 0 || family != containmentFamily(contained))
        error("DECLARATION_CLAUSE_TYPE_INVALID: " + entry.urn()
            + " observational containment requires subject/agent or event/process pairs", entry);
    }
    for(var applicable:s.getAppliesTo())require(entry,applicable.getTarget(),"applies to",WorldviewDeclarationRegistry::observable);
  }

  private static int containmentFamily(Set<SemanticType> types) {
    if (types.contains(SemanticType.EVENT) || types.contains(SemanticType.PROCESS)) return 2;
    return types.contains(SemanticType.SUBJECT) || types.contains(SemanticType.AGENT) ? 1 : 0;
  }

  /** Parent heads only: containment endpoints and clause fillers are not ancestry. */
  private static void structuralHeads(KimConcept expression,String namespace,Set<String> result) {
    if(expression==null)return;
    if(expression.getName()!=null)result.add(urn(namespace,expression.getName()));
    else structuralHeads(expression.getObservable(),namespace,result);
    for(var operand:expression.getOperands())structuralHeads(operand,namespace,result);
  }
  private void error(String message,Declaration entry) {
    errors.add(Notification.error(message,Notification.LexicalContext.of(entry.statement(),entry.ontology())));
  }
}
