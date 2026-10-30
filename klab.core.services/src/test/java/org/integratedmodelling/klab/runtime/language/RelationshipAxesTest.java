package org.integratedmodelling.klab.runtime.language;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.integratedmodelling.common.data.jackson.JacksonConfiguration;
import org.integratedmodelling.klab.api.knowledge.*;
import org.integratedmodelling.klab.api.lang.kim.*;
import org.integratedmodelling.klab.api.lang.kim.impl.*;

class RelationshipAxesTest {
  private KimConceptStatementImpl declaration(String name,boolean abs,RelationshipAxes.Nature nature,RelationshipAxes.Orientation orientation) {
    var s=new KimConceptStatementImpl();s.setUrn(name);s.setNamespace("axes");
    s.setType(EnumSet.of(SemanticType.RELATIONSHIP,SemanticType.OBSERVABLE,SemanticType.COUNTABLE));
    s.setAbstract(abs);if(abs)s.getType().add(SemanticType.ABSTRACT);
    s.setDeclaredRelationshipNature(nature);s.setDeclaredRelationshipOrientation(orientation);
    new RelationshipAxes(nature,orientation).apply(s.getType());return s;
  }
  private KimConceptImpl ref(String name) {
    var p=new KimConceptImpl();p.setName("axes:"+name);p.setUrn("axes:"+name);
    p.setType(EnumSet.of(SemanticType.RELATIONSHIP,SemanticType.OBSERVABLE,SemanticType.COUNTABLE));return p;
  }
  private KimConceptImpl expression(KimConcept.Expression kind,KimConcept... operands) {
    var p=new KimConceptImpl();p.setType(EnumSet.of(SemanticType.RELATIONSHIP));
    p.setExpressionType(kind);p.setOperands(List.of(operands));return p;
  }
  private WorldviewDeclarationRegistry registry(KimConceptStatement... declarations) {
    var o=new KimOntologyImpl();o.setUrn("axes");o.setStatements(new ArrayList<>(List.of(declarations)));
    return new WorldviewDeclarationRegistry(List.of(o));
  }
  private KimConceptStatementImpl neutral(String n,boolean abs) { return declaration(n,abs,RelationshipAxes.Nature.UNSPECIFIED,RelationshipAxes.Orientation.UNSPECIFIED); }
  @Test void abstractAxesAreIndependentlyOptionalButConcreteAxesAreRequired() {
    for(var nature:RelationshipAxes.Nature.values())for(var orientation:RelationshipAxes.Orientation.values()) {
      assertTrue(registry(declaration("Root",true,nature,orientation)).errors().isEmpty());
      boolean complete=nature!=RelationshipAxes.Nature.UNSPECIFIED&&orientation!=RelationshipAxes.Orientation.UNSPECIFIED;
      assertEquals(complete,registry(declaration("Concrete",false,nature,orientation)).errors().isEmpty());
    }
  }
  @Test void forwardReferencesAndRepeatedStagingPreserveAuthoredOmission() {
    var root=declaration("Root",true,RelationshipAxes.Nature.FUNCTIONAL,RelationshipAxes.Orientation.BOND);
    var child=neutral("Child",false);child.setDeclaredParent(ref("Root"));
    assertTrue(registry(child,root).errors().isEmpty());assertTrue(child.getType().contains(SemanticType.BIDIRECTIONAL));assertTrue(child.getType().contains(SemanticType.FUNCTIONAL));
    assertEquals(RelationshipAxes.Orientation.UNSPECIFIED,child.getDeclaredRelationshipOrientation());
    assertTrue(registry(child,root).errors().isEmpty());
  }
  @Test void eachOppositeInheritedAxisFailsIncludingAbstractChildren() {
    for(boolean abs:List.of(false,true)) {
      var root=declaration("Root",true,RelationshipAxes.Nature.STRUCTURAL,RelationshipAxes.Orientation.DIRECTED);
      var nature=declaration("Nature",abs,RelationshipAxes.Nature.FUNCTIONAL,RelationshipAxes.Orientation.UNSPECIFIED);nature.setDeclaredParent(ref("Root"));
      assertTrue(registry(root,nature).errors().stream().anyMatch(e->e.getMessage().contains("AXIS_CONFLICT")));
      var orientation=declaration("Orientation",abs,RelationshipAxes.Nature.UNSPECIFIED,RelationshipAxes.Orientation.BOND);orientation.setDeclaredParent(ref("Root"));
      assertTrue(registry(root,orientation).errors().stream().anyMatch(e->e.getMessage().contains("AXIS_CONFLICT")));
    }
  }
  @Test void diamondsConjoinAndUnionRetainsOnlyCommonChoices() {
    var left=declaration("Left",true,RelationshipAxes.Nature.STRUCTURAL,RelationshipAxes.Orientation.DIRECTED);
    var right=declaration("Right",true,RelationshipAxes.Nature.STRUCTURAL,RelationshipAxes.Orientation.BOND);
    var both=neutral("Both",true);both.setDeclaredParent(expression(KimConcept.Expression.INTERSECTION,ref("Left"),ref("Right")));
    assertTrue(registry(left,right,both).errors().stream().anyMatch(e->e.getMessage().contains("AXIS_CONFLICT")));
    var union=neutral("Union",true);union.setDeclaredParent(expression(KimConcept.Expression.UNION,ref("Left"),ref("Right")));
    assertTrue(registry(left,right,union).errors().isEmpty());assertTrue(union.getType().contains(SemanticType.STRUCTURAL));assertFalse(union.getType().contains(SemanticType.BIDIRECTIONAL));assertFalse(union.getType().contains(SemanticType.UNIDIRECTIONAL));
    var concrete=neutral("Concrete",false);concrete.setDeclaredParent(ref("Union"));assertFalse(registry(left,right,union,concrete).errors().isEmpty());
    concrete.setDeclaredRelationshipOrientation(RelationshipAxes.Orientation.DIRECTED);assertTrue(registry(left,right,union,concrete).errors().isEmpty());
  }
  @Test void aliasesCannotRefineOrMutateAnUnspecifiedCanonicalTarget() {
    var root=neutral("Root",true);var alias=declaration("Alias",false,RelationshipAxes.Nature.STRUCTURAL,RelationshipAxes.Orientation.DIRECTED);
    alias.setAlias(true);alias.setDeclaredParent(ref("Root"));
    assertTrue(registry(root,alias).errors().stream().anyMatch(e->e.getMessage().contains("ALIAS_AXIS_CONFLICT")));
    assertEquals(RelationshipAxes.UNSPECIFIED,RelationshipAxes.of(root.getType()));
  }
  @Test void interfaceTransportPreservesAuthoredChoicesSeparateFromEffectiveFlags() throws Exception {
    var mapper=JacksonConfiguration.newObjectMapper();var root=declaration("Root",true,RelationshipAxes.Nature.FUNCTIONAL,RelationshipAxes.Orientation.BOND);
    var child=neutral("Child",false);child.setDeclaredParent(ref("Root"));assertTrue(registry(root,child).errors().isEmpty());
    var copy=mapper.readValue(mapper.writeValueAsBytes(child),KimConceptStatement.class);
    assertEquals(RelationshipAxes.Nature.UNSPECIFIED,copy.getDeclaredRelationshipNature());assertEquals(RelationshipAxes.Orientation.UNSPECIFIED,copy.getDeclaredRelationshipOrientation());
    assertEquals(new RelationshipAxes(RelationshipAxes.Nature.FUNCTIONAL,RelationshipAxes.Orientation.BOND),RelationshipAxes.of(copy.getType()));
  }
  @Test void forgedConflictingFlagsAreRejectedAndNonConflictingFlagsCannotSupplyMissingChoices() {
    var s=neutral("Forged",false);s.getType().addAll(EnumSet.of(SemanticType.STRUCTURAL,SemanticType.FUNCTIONAL));assertFalse(registry(s).errors().isEmpty());
    s.getType().remove(SemanticType.FUNCTIONAL);s.getType().add(SemanticType.UNIDIRECTIONAL);assertFalse(registry(s).errors().isEmpty());
  }
  @Test void actualDiamondPreservesSharedAncestorAndNestedChildrenInherit() {
    var root=declaration("Root",true,RelationshipAxes.Nature.FUNCTIONAL,RelationshipAxes.Orientation.BOND);
    var left=neutral("Left",true);left.setDeclaredParent(ref("Root"));
    var right=neutral("Right",true);right.setDeclaredParent(ref("Root"));
    var leaf=neutral("Leaf",false);leaf.setDeclaredParent(expression(KimConcept.Expression.INTERSECTION,ref("Left"),ref("Right")));
    var nested=neutral("Nested",false);root.getChildren().add(nested);
    assertTrue(registry(leaf,right,left,root).errors().isEmpty());
    assertEquals(RelationshipAxes.of(root.getType()),RelationshipAxes.of(leaf.getType()));
    assertEquals(RelationshipAxes.of(root.getType()),RelationshipAxes.of(nested.getType()));
    assertEquals(RelationshipAxes.Orientation.UNSPECIFIED,nested.getDeclaredRelationshipOrientation());
  }
  @Test void aliasesMayRepeatButCannotContradictCanonicalAxes() {
    var root=declaration("Root",true,RelationshipAxes.Nature.FUNCTIONAL,RelationshipAxes.Orientation.BOND);
    var alias=neutral("Alias",false);alias.setAlias(true);alias.setDeclaredParent(ref("Root"));
    assertTrue(registry(alias,root).errors().isEmpty());
    assertEquals(RelationshipAxes.of(root.getType()),RelationshipAxes.of(alias.getType()));
    alias.setDeclaredRelationshipNature(RelationshipAxes.Nature.STRUCTURAL);
    assertFalse(registry(alias,root).errors().isEmpty());
    assertEquals(new RelationshipAxes(RelationshipAxes.Nature.FUNCTIONAL,RelationshipAxes.Orientation.BOND),RelationshipAxes.of(root.getType()));
  }
  @Test void nestedAliasDelegatesRatherThanInheritingEnclosingAxes() {
    var target=declaration("Target",true,RelationshipAxes.Nature.STRUCTURAL,RelationshipAxes.Orientation.BOND);
    var enclosing=declaration("Enclosing",true,RelationshipAxes.Nature.FUNCTIONAL,RelationshipAxes.Orientation.DIRECTED);
    var alias=neutral("Alias",false);alias.setAlias(true);alias.setDeclaredParent(ref("Target"));enclosing.getChildren().add(alias);
    assertTrue(registry(enclosing,target).errors().isEmpty());
    assertEquals(RelationshipAxes.of(target.getType()),RelationshipAxes.of(alias.getType()));
    assertEquals(new RelationshipAxes(RelationshipAxes.Nature.STRUCTURAL,RelationshipAxes.Orientation.BOND),RelationshipAxes.of(target.getType()));
  }
  @Test void unionDoesNotPermitCrossedChoicesAbsentFromEveryBranch() {
    var left=declaration("Left",true,RelationshipAxes.Nature.STRUCTURAL,RelationshipAxes.Orientation.DIRECTED);
    var right=declaration("Right",true,RelationshipAxes.Nature.FUNCTIONAL,RelationshipAxes.Orientation.BOND);
    var parent=neutral("Alternatives",true);parent.setDeclaredParent(expression(KimConcept.Expression.UNION,ref("Left"),ref("Right")));
    var crossed=declaration("Crossed",false,RelationshipAxes.Nature.STRUCTURAL,RelationshipAxes.Orientation.BOND);crossed.setDeclaredParent(ref("Alternatives"));
    assertFalse(registry(left,right,parent,crossed).errors().isEmpty());
    var valid=declaration("Valid",false,RelationshipAxes.Nature.STRUCTURAL,RelationshipAxes.Orientation.DIRECTED);valid.setDeclaredParent(ref("Alternatives"));
    assertTrue(registry(left,right,parent,valid).errors().isEmpty());
  }
}
