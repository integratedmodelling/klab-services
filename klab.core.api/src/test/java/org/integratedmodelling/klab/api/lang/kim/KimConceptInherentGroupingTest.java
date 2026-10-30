package org.integratedmodelling.klab.api.lang.kim;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import org.integratedmodelling.klab.api.knowledge.SemanticType;
import org.integratedmodelling.klab.api.lang.kim.impl.KimConceptImpl;
import org.junit.jupiter.api.Test;

class KimConceptInherentGroupingTest {
  private KimConceptImpl leaf(String name, SemanticType kind) {
    var value=new KimConceptImpl();value.setName(name);
    value.setType(EnumSet.of(kind,kind==SemanticType.SUBJECT?SemanticType.OBSERVABLE:SemanticType.PREDICATE));
    if(kind==SemanticType.SUBJECT)value.getType().add(SemanticType.COUNTABLE);
    value.finalizeDefinition();return value;
  }
  @Test void inherentGroupingDoesNotChangePlainOrSimpleDecoratedSpelling() {
    var member=leaf("test:Region",SemanticType.SUBJECT);
    var role=leaf("test:Reference",SemanticType.ROLE);role.setInherent(member);role.resetDefinition();
    assertEquals("test:Reference of test:Region",role.finalizeDefinition());
    member.getTraits().add(leaf("test:Terrestrial",SemanticType.REALM));member.resetDefinition();role.resetDefinition();
    assertEquals("test:Terrestrial test:Region",member.finalizeDefinition());
    assertEquals("test:Reference of test:Terrestrial test:Region",role.finalizeDefinition());
  }
  @Test void qualifiedRoleAndAttributeStayInsideTheCompleteInherentOperand() {
    for(var kind:List.of(SemanticType.ROLE,SemanticType.ATTRIBUTE)) {
      var qualified=leaf("test:Qualified",kind);qualified.setInherent(leaf("test:Other",SemanticType.SUBJECT));qualified.resetDefinition();qualified.finalizeDefinition();
      var member=leaf("test:Region",SemanticType.SUBJECT);
      (kind==SemanticType.ROLE?member.getRoles():member.getTraits()).add(qualified);member.resetDefinition();
      var top="(test:Qualified of test:Other) test:Region";
      assertEquals(top,member.finalizeDefinition(),"Top-level expression spelling stays unchanged");
      var role=leaf("test:Reference",SemanticType.ROLE);role.setInherent(member);role.resetDefinition();
      assertEquals("test:Reference of ("+top+")",role.finalizeDefinition());
      member.setCollective(true);member.resetDefinition();role.resetDefinition();
      assertEquals("test:Reference of (each "+top+")",role.finalizeDefinition());
      var wrapper=new KimConceptImpl();wrapper.setObservable(member);wrapper.setType(new HashSet<>(member.getType()));wrapper.setCollective(true);wrapper.finalizeDefinition();
      role.setInherent(wrapper);role.resetDefinition();
      assertEquals("test:Reference of (each "+top+")",role.finalizeDefinition(),"Transparent wrappers must not accumulate grouping");
    }
  }
  @Test void transparentPredicateWrappersDoNotHideTheirInherentClause() {
    for(var kind:List.of(SemanticType.ROLE,SemanticType.ATTRIBUTE)) {
      var qualified=leaf("test:Qualified",kind);qualified.setInherent(leaf("test:Other",SemanticType.SUBJECT));qualified.resetDefinition();
      var predicateWrapper=new KimConceptImpl();predicateWrapper.setObservable(qualified);predicateWrapper.setType(new HashSet<>(qualified.getType()));
      var member=leaf("test:Region",SemanticType.SUBJECT);
      (kind==SemanticType.ROLE?member.getRoles():member.getTraits()).add(predicateWrapper);member.resetDefinition();
      var top="(test:Qualified of test:Other) test:Region";
      assertEquals(top,member.finalizeDefinition());
      var role=leaf("test:Reference",SemanticType.ROLE);role.setInherent(member);role.resetDefinition();
      assertEquals("test:Reference of ("+top+")",role.finalizeDefinition());
      member.setCollective(true);member.resetDefinition();role.resetDefinition();
      assertEquals("test:Reference of (each "+top+")",role.finalizeDefinition());
    }
  }}