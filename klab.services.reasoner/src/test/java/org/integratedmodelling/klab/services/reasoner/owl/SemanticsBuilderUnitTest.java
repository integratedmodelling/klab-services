package org.integratedmodelling.klab.services.reasoner.owl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.util.EnumSet;
import org.integratedmodelling.common.knowledge.ConceptImpl;
import org.integratedmodelling.common.utils.Utils;
import org.integratedmodelling.klab.api.data.mediation.impl.UnitImpl;
import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.api.knowledge.SemanticType;
import org.integratedmodelling.klab.api.lang.kim.impl.KimConceptImpl;
import org.integratedmodelling.klab.api.scope.Scope;
import org.integratedmodelling.klab.api.scope.ServiceScope;
import org.integratedmodelling.klab.services.reasoner.ReasonerService;
import org.integratedmodelling.klab.services.reasoner.internal.SemanticsBuilder;
import org.junit.jupiter.api.Test;

class SemanticsBuilderUnitTest {
  private SemanticsBuilder builder() {
    var syntax = new KimConceptImpl();
    syntax.setName("test:Length");
    syntax.setUrn("test:Length");
    var types = EnumSet.of(SemanticType.OBSERVABLE, SemanticType.QUALITY,
        SemanticType.LENGTH, SemanticType.QUANTIFIABLE, SemanticType.EXTENSIVE);
    syntax.setType(types);
    var concept = new ConceptImpl();
    concept.setUrn("test:Length");
    concept.getType().addAll(types);
    var reasoner = mock(ReasonerService.class);
    when(reasoner.owl()).thenReturn(mock(OWL.class));
    when(reasoner.resolveConcept("test:Length")).thenReturn(concept);
    when(reasoner.serviceScope()).thenReturn(mock(ServiceScope.class));
    return SemanticsBuilder.create(syntax, reasoner, mock(Scope.class));
  }

  @Test void stringUnitsSurviveObservableBuildAndWireRoundTrip() {
    for (String definition : new String[] {"m", "mm"}) {
      var observable = builder().withUnit(definition).buildObservable();
      assertEquals("test:Length in " + definition, observable.getUrn());
      assertEquals(definition, ((UnitImpl) observable.getUnit()).getDefinition());
      var restored = Utils.Json.parseObject(Utils.Json.asString(observable), Observable.class);
      assertEquals(observable.getUrn(), restored.getUrn());
      assertEquals(definition, ((UnitImpl) restored.getUnit()).getDefinition());
    }
  }

  @Test void invalidUnitFailsRatherThanSilentlyDroppingTheDeclaration() {
    assertThrows(org.integratedmodelling.klab.api.exceptions.KlabValidationException.class,
        () -> builder().withUnit("not_a_real_unit_xyz"));
    assertThrows(org.integratedmodelling.klab.api.exceptions.KlabValidationException.class,
        () -> builder().withUnit(""));
  }
}
