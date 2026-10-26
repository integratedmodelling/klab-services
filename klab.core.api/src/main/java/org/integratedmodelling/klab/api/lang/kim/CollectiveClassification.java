package org.integratedmodelling.klab.api.lang.kim;

import java.util.*;
import java.util.function.Function;
import org.integratedmodelling.klab.api.knowledge.SemanticType;
import org.integratedmodelling.klab.api.lang.kim.impl.KimConceptImpl;
import org.integratedmodelling.klab.api.lang.kim.impl.KimObservableImpl;

/** Same-member classification of an authored collective input; never a dependency product. */
public final class CollectiveClassification {
  private CollectiveClassification() {}

  public record Plan(KimObservableImpl members, List<KimObservableImpl> directives) {
    public Plan { directives = List.copyOf(directives); }
  }

  /** Bounded native subject/agent form. Unsupported compound/occurrence forms remain strict. */
  public static Plan prepare(KimObservable source, Function<String, KimConcept> declarations) {
    if (source == null || source.isGeneric() || source.getPattern() != null || source.getObserverSemantics() != null
        || source.getUnit() != null || source.getCurrency() != null || source.getRange() != null
        || source.getValue() != null || source.getDefaultValue() != null || !source.getValueOperators().isEmpty()) return null;
    if (source instanceof KimObservableImpl direct && atomicHead(source.getSemantics()) instanceof KimConceptImpl predicate
        && predicate.getName() != null && atomicHead(predicate.getInherent()) instanceof KimConceptImpl nativeMembers
        && nativeMembers.isCollective() && nativeMembers.getName() != null && bareMembers(nativeMembers)) {
      var declaration = declarations.apply(predicate.getName());
      var memberDeclaration = declarations.apply(nativeMembers.getName());
      if (memberDeclaration == null || memberDeclaration.is(SemanticType.ABSTRACT)
          || !(memberDeclaration.is(SemanticType.SUBJECT) || memberDeclaration.is(SemanticType.AGENT))) return null;
      var bare = predicate.mapSyntax(n -> {}); bare.setInherent(null);
      if (declaration != null && declaration.is(SemanticType.PREDICATE)
          && (declaration.is(SemanticType.ABSTRACT) || predicate.is(SemanticType.ANY)) && plain(bare)) {
        var members = new KimObservableImpl(); members.setSemantics(nativeMembers.mapSyntax(n -> {})); members.setOptional(source.isOptional());
        members.setUrn(((KimConceptImpl)members.getSemantics()).finalizeDefinition());
        if (declaration instanceof KimConceptImpl declared) {
          var normalized = declared.mapSyntax(n -> {});
          normalized.setInherent(((KimConceptImpl)members.getSemantics()).mapSyntax(n -> {}));
          normalized.setUrn(null); normalized.finalizeDefinition();
          var directive = new KimObservableImpl(direct); directive.setSemantics(normalized);
          directive.setUrn(normalized.getUrn());
          return new Plan(members, List.of(directive));
        }
      }
    }
    if (!(source instanceof KimObservableImpl original) || source.isGeneric() || source.getPattern() != null
        || !(atomicHead(source.getSemantics()) instanceof KimConceptImpl head) || !head.isCollective()
        || head.getName() == null || head.getObservable() != null || head.isNegated()
        || head.getSemanticModifier() != null || head.getInherent() != null
        || head.is(SemanticType.ANY) || head.is(SemanticType.ALL) || head.is(SemanticType.NONE)
        || head.isPattern() || !head.getAbout().isEmpty() || head.getGoal() != null
        || head.getCausant() != null || head.getCaused() != null || head.getCompresent() != null
        || head.getComparisonConcept() != null || head.getCooccurrent() != null || head.getAdjacent() != null
        || !head.getOperands().isEmpty() || !head.getModifiers().isEmpty()
        || !head.getValueOperators().isEmpty() || head.getRelationshipSource() != null
        || head.getRelationshipTarget() != null) return null;
    var nativeHead = declarations.apply(head.getName());
    if (nativeHead == null || !(nativeHead.is(SemanticType.SUBJECT) || nativeHead.is(SemanticType.AGENT))
        || nativeHead.is(SemanticType.ABSTRACT)) return null;
    var selected = new ArrayList<KimConcept>();
    var traits = new HashSet<Integer>(); var roles = new HashSet<Integer>();
    for (var slots : List.of(head.getTraits(), head.getRoles())) {
      for (int i = 0; i < slots.size(); i++) {
        var slot = slots.get(i); var declaration = slot.getName() == null ? null : declarations.apply(slot.getName());
        if (declaration == null || !declaration.is(SemanticType.PREDICATE)
            || !(declaration.is(SemanticType.ABSTRACT) || slot.is(SemanticType.ANY))) continue;
        if (!plain(slot)) return null;
        selected.add(declaration);
        (slots == head.getTraits() ? traits : roles).add(i);
      }
    }
    if (selected.isEmpty()) return null;
    if (head.getTraits().size() + head.getRoles().size() != selected.size()) return null;
    if (selected.size() != 1)
      throw new IllegalArgumentException("Collective classification currently requires one classifier predicate");
    var normalized = new KimObservableImpl(original); normalized.setSemantics(head);
    var members = new KimObservableImpl(normalized, (path, node) -> {
      if (!path.equals(List.of("semantics"))) return;
      for (int i = node.getTraits().size()-1; i >= 0; i--) if (traits.contains(i)) node.getTraits().remove(i);
      for (int i = node.getRoles().size()-1; i >= 0; i--) if (roles.contains(i)) node.getRoles().remove(i);
      node.setType(new HashSet<>(nativeHead.getType()));
    });
    members.setUrn(((KimConceptImpl)members.getSemantics()).finalizeDefinition());
    var directives = new ArrayList<KimObservableImpl>();
    for (var declaration : selected) {
      if (!(declaration instanceof KimConceptImpl predicate)) return null;
      var directive = predicate.mapSyntax(node -> {});
      directive.setInherent(((KimConceptImpl)members.getSemantics()).mapSyntax(node -> {}));
      directive.setUrn(null); directive.finalizeDefinition();
      var observable = new KimObservableImpl(); observable.setSemantics(directive); observable.setUrn(directive.getUrn());
      observable.setOptional(source.isOptional()); directives.add(observable);
    }
    return new Plan(members, directives);
  }

  /** The parser represents `each` as a transparent wrapper around the named head. */
  private static KimConcept atomicHead(KimConcept syntax) {
    if (!(syntax instanceof KimConceptImpl node)) return null;
    var copy=node.mapSyntax(n -> {});
    var seen=Collections.newSetFromMap(new IdentityHashMap<KimConcept,Boolean>());
    while(copy.getName()==null && copy.getObservable()!=null) {
      if(!seen.add(copy) || copy.isNegated() || copy.isPattern() || copy.getSemanticModifier()!=null
          || copy.getInherent()!=null
          || !copy.getOperands().isEmpty() || !copy.getModifiers().isEmpty() || !copy.getAbout().isEmpty()
          || !copy.getValueOperators().isEmpty() || copy.getGoal()!=null || copy.getCausant()!=null
          || copy.getCaused()!=null || copy.getCompresent()!=null || copy.getComparisonConcept()!=null
          || copy.getCooccurrent()!=null || copy.getAdjacent()!=null || copy.getRelationshipSource()!=null
          || copy.getRelationshipTarget()!=null || copy.is(SemanticType.ANY)
          || copy.is(SemanticType.ALL) || copy.is(SemanticType.NONE)) return null;
      boolean collective=copy.isCollective();
      var traits=List.copyOf(copy.getTraits());var roles=List.copyOf(copy.getRoles());
      if(!(copy.getObservable() instanceof KimConceptImpl inner)) return null;
      copy=inner.mapSyntax(n -> {});copy.setCollective(collective || copy.isCollective());
      copy.getTraits().addAll(traits);copy.getRoles().addAll(roles);
    }
    return copy;
  }

  private static boolean bareMembers(KimConcept c) {
    return c.getObservable() == null && !c.isNegated() && !c.isPattern() && c.getSemanticModifier() == null
        && !c.is(SemanticType.ANY) && !c.is(SemanticType.ALL) && !c.is(SemanticType.NONE)
        && c.getInherent() == null && c.getTraits().isEmpty() && c.getRoles().isEmpty()
        && c.getOperands().isEmpty() && c.getModifiers().isEmpty() && c.getAbout().isEmpty()
        && c.getValueOperators().isEmpty() && c.getRelationshipSource() == null && c.getRelationshipTarget() == null
        && c.getGoal() == null && c.getCausant() == null && c.getCaused() == null && c.getCompresent() == null
        && c.getComparisonConcept() == null && c.getCooccurrent() == null && c.getAdjacent() == null;
  }

  private static boolean plain(KimConcept c) {
    return c.getObservable() == null && !c.isCollective() && !c.isNegated() && !c.isPattern()
        && !c.is(SemanticType.ALL) && !c.is(SemanticType.NONE) && c.getSemanticModifier() == null
        && c.getInherent() == null && c.getGoal() == null && c.getCausant() == null && c.getCaused() == null
        && c.getCompresent() == null && c.getComparisonConcept() == null && c.getCooccurrent() == null
        && c.getAdjacent() == null && c.getRelationshipSource() == null && c.getRelationshipTarget() == null
        && c.getTraits().isEmpty() && c.getRoles().isEmpty() && c.getOperands().isEmpty()
        && c.getModifiers().isEmpty() && c.getAbout().isEmpty() && c.getValueOperators().isEmpty();
  }
}
