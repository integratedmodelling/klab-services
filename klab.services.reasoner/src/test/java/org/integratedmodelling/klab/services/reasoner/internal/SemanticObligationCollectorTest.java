package org.integratedmodelling.klab.services.reasoner.internal;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import org.integratedmodelling.klab.api.knowledge.*;
import org.integratedmodelling.klab.api.lang.ExpressionCode;
import org.integratedmodelling.klab.api.lang.kim.impl.*;
import org.junit.jupiter.api.Test;

class SemanticObligationCollectorTest {
  private KimModelImpl model() {
    var model=new KimModelImpl();
    var numeric=new KimObservableImpl();numeric.setFormalName("elevation");numeric.setNonSemanticType(Artifact.Type.NUMBER);
    var selected=new KimObservableImpl();selected.setFormalName("selected");
    var meaning=new KimConceptImpl();meaning.setName("w:Tree");meaning.setType(EnumSet.of(SemanticType.SUBJECT,SemanticType.COUNTABLE));selected.setSemantics(meaning);
    model.getObservables().add(numeric);model.getDependencies().add(selected);return model;
  }
  @Test void explicitlyBoundScalarArithmeticAndChildCountHaveClosedSemanticFootprints() {
    for(var code:List.of("elevation - 10","selected.childrenCount","(elevation + 2) / 3","1e2 + .5","[selected.childrenCount]"))
      assertTrue(SemanticObligationCollector.closedScalar(ExpressionCode.of(code,null),model()),code);
  }
  @Test void OpaqueCallsUnboundNamesDynamicSemanticsAndForeignLanguagesAreNotIndependent() {
    for(var code:List.of("selected.getChildren().size()","Class.forName('x')","unknown + 1","elevation.toString()",
        "TAXA:'code'","resolveConcept('w:Tree')","1; System.exit(0)","selected.childrenCount + hidden"))
      assertFalse(SemanticObligationCollector.closedScalar(ExpressionCode.of(code,null),model()),code);
    assertFalse(SemanticObligationCollector.closedScalar(ExpressionCode.of("1+2","python"),model()));
    assertFalse(SemanticObligationCollector.closedScalar(ExpressionCode.of("(".repeat(200)+"1"+")".repeat(200),null),model()));
  }
}
