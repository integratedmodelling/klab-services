package org.integratedmodelling.klab.services.reasoner.internal;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import org.integratedmodelling.klab.api.knowledge.*;
import org.integratedmodelling.klab.api.lang.ExpressionCode;
import org.integratedmodelling.klab.api.lang.kim.impl.*;
import org.junit.jupiter.api.Test;

class SemanticObligationCollectorTest {
  private SemanticObligationCollector collect(Object value) {
    var collector=new SemanticObligationCollector(org.mockito.Mockito.mock(org.integratedmodelling.klab.services.reasoner.ReasonerService.class));
    var symbol=new KimSymbolDefinitionImpl();symbol.setValue(value);
    var namespace=new KimNamespaceImpl();namespace.setUrn("fixture");namespace.getStatements().add(symbol);
    new org.integratedmodelling.klab.runtime.language.KimNamespaceVisitor(new org.integratedmodelling.klab.runtime.language.KimNamespaceVisitor.Validator(){
      @Override public List<org.integratedmodelling.klab.api.services.runtime.Notification> validateStatement(
          org.integratedmodelling.klab.api.lang.kim.KlabStatement statement,org.integratedmodelling.klab.runtime.language.KimObservableVisitor.Context context) {
        collector.statement(statement,context);return List.of();
      }
    },null).visit(namespace);
    return collector;
  }
  @Test void typedQuantitiesAndOpaqueEvaluatorsHaveStaticCoverageWithoutExecutableCompleteness() {
    for(var value:List.of(org.integratedmodelling.common.lang.QuantityImpl.parse("1.km"),ExpressionCode.of("resolveConcept('w:Hidden')",null))) {
      var collector=collect(value);assertTrue(collector.sourceComplete());assertFalse(collector.complete());
    }
  }
  @Test void patternsUnsupportedObjectsAndCollectionFailuresCannotClaimStaticCoverage() {
    var pattern=new KimConceptImpl();pattern.setPattern(true);
    for(var value:List.of(pattern,new Object())) {
      var collector=collect(value);assertFalse(collector.sourceComplete());assertFalse(collector.complete());
    }
    var collector=collect(1);collector.incomplete();assertFalse(collector.sourceComplete());assertFalse(collector.complete());
    collector.reset();assertTrue(collector.sourceComplete());assertTrue(collector.complete());
  }
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
