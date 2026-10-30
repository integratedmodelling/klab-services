package org.integratedmodelling.klab.services.reasoner;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.*;
import org.integratedmodelling.common.knowledge.ConceptImpl;
import org.integratedmodelling.klab.api.knowledge.*;
import org.integratedmodelling.klab.api.services.Reasoner;
import org.junit.jupiter.api.Test;
class OperandGuardsTest {
  private Concept concept(String urn, SemanticType... types) {
    var value = new ConceptImpl(); value.setUrn(urn); value.setType(EnumSet.copyOf(List.of(types))); return value;
  }
  @Test void sameOriginalFamilyHandlesSubjectInheritanceAndRejectsMixedAmbiguousKinds() {
    var guards = new OperandGuards(mock(Reasoner.class));
    var first = concept("w:a", SemanticType.AGENT, SemanticType.SUBJECT);
    assertTrue(guards.sameCountableFamily(first, concept("w:b", SemanticType.AGENT)));
    assertFalse(guards.sameCountableFamily(first, concept("w:b", SemanticType.SUBJECT)));
    assertFalse(guards.sameCountableFamily(first, concept("w:b", SemanticType.AGENT, SemanticType.EVENT)));
    assertFalse(guards.sameCountableFamily(first, concept("w:q", SemanticType.QUALITY)));
  }
  @Test void everyNestedLogicalLeafIsCheckedAndCyclesFailClearly() {
    var reasoner = mock(Reasoner.class); var guards = new OperandGuards(reasoner);
    var a = concept("w:a", SemanticType.SUBJECT); var b = concept("w:b", SemanticType.SUBJECT);
    var c = concept("w:c", SemanticType.EVENT); var nested = concept("w:nested", SemanticType.UNION);
    when(reasoner.operands(nested)).thenReturn(List.of(a,b)); assertTrue(guards.sameCountableFamily(a,nested));
    when(reasoner.operands(nested)).thenReturn(List.of(b,c)); assertFalse(guards.sameCountableFamily(a,nested));
    when(reasoner.operands(nested)).thenReturn(List.of(nested)); assertThrows(IllegalArgumentException.class, () -> guards.sameCountableFamily(a,nested));
  }
  @Test void presenceSignatureRequiresSatisfiableResultCompatibleSupportAndSameBearer() {
    var reasoner = mock(Reasoner.class); var guards = new OperandGuards(reasoner);
    var a = concept("w:a", SemanticType.PRESENCE); var b = concept("w:b", SemanticType.PRESENCE);
    var result = concept("w:result", SemanticType.PRESENCE); var bearer = concept("w:support", SemanticType.SUBJECT);
    when(reasoner.satisfiable(any())).thenReturn(true); when(reasoner.compatible(any(),any())).thenReturn(true);
    when(reasoner.inherent(any())).thenReturn(bearer); assertTrue(guards.enumerableCompatible(a,b,result));
    when(reasoner.inherent(b)).thenReturn(concept("w:other", SemanticType.SUBJECT)); assertFalse(guards.enumerableCompatible(a,b,result));
    when(reasoner.inherent(b)).thenReturn(bearer);
    when(reasoner.inherent(result)).thenReturn(concept("w:otherResultSupport", SemanticType.SUBJECT));
    assertFalse(guards.enumerableCompatible(a,b,result));
    when(reasoner.inherent(result)).thenReturn(bearer); when(reasoner.inherent(a)).thenReturn(null);
    when(reasoner.inherent(b)).thenReturn(null); assertFalse(guards.enumerableCompatible(a,b,result));
    when(reasoner.inherent(a)).thenReturn(bearer); when(reasoner.inherent(b)).thenReturn(bearer);
    when(reasoner.satisfiable(result)).thenReturn(false);
    assertFalse(guards.enumerableCompatible(a,b,result));
  }
  @Test void categoriesRemainExplicitlyUnreviewedAndNumericSignaturesCannotPass() {
    var guards = new OperandGuards(mock(Reasoner.class));
    var category = concept("w:category", SemanticType.CLASS); var numeric = concept("w:numeric", SemanticType.QUALITY);
    assertThrows(UnsupportedOperationException.class, () -> guards.enumerableCompatible(category,category,category));
    assertFalse(guards.enumerableCompatible(numeric,numeric,numeric));
  }
}
