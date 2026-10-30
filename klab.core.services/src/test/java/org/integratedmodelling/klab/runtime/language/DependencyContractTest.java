package org.integratedmodelling.klab.runtime.language;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import org.integratedmodelling.common.knowledge.ConceptImpl;
import org.integratedmodelling.common.knowledge.ObservableImpl;
import org.integratedmodelling.klab.api.data.mediation.impl.UnitImpl;
import org.integratedmodelling.klab.api.knowledge.SemanticType;
import org.junit.jupiter.api.Test;

class DependencyContractTest {
  private ObservableImpl elevation() {
    var concept = new ConceptImpl(); concept.setUrn("geography:Elevation");
    concept.getType().addAll(List.of(SemanticType.QUALITY, SemanticType.LENGTH));
    var observable = ObservableImpl.promote(concept, null);
    observable.setUrn("geography:Elevation in m"); observable.setUnit(new UnitImpl("m"));
    return observable;
  }
  @Test void modelLocalNamesAndWrapperSpellingsAreNotCompatibilityRequirements() {
    var source = elevation(); var selected = elevation();
    source.setStatedName("elevation"); selected.setStatedName("height");
    source.setUrn("geography:Elevation in m named elevation");
    selected.setUrn("geography:Elevation in m named height");
    source.setName("localA"); selected.setName("localB");
    source.setReferenceName("referenceA"); selected.setReferenceName("referenceB");
    assertTrue(DependencyContract.matches(source, selected));
  }
  @Test void equalConceptsDoNotEraseProductionRequirements() {
    var source = elevation(); var selected = elevation();
    selected.setUnit(new UnitImpl("mm")); assertFalse(DependencyContract.matches(source, selected));
    selected.setUnit(new UnitImpl("m")); selected.setOptional(true);
    assertFalse(DependencyContract.matches(source, selected));
    selected.setOptional(false); selected.setGeneric(true);
    assertFalse(DependencyContract.matches(source, selected));
    selected.setGeneric(false); selected.setDefaultValue(3);
    assertFalse(DependencyContract.matches(source, selected));
    selected.setDefaultValue(null); selected.setValue(4);
    assertFalse(DependencyContract.matches(source, selected));
    selected.setValue(null); assertTrue(DependencyContract.matches(source, selected));
  }
  @Test void equalWrapperUrnsCannotHideDifferentConcepts() {
    var source = elevation(); var selected = elevation();
    var different = new ConceptImpl(); different.setUrn("geography:Slope");
    selected.setSemantics(different);
    assertFalse(DependencyContract.matches(source, selected));
  }
  @Test void observerRequirementsCompareConceptsWithoutIncidentalTransportState() {
    var source = elevation(); var selected = elevation();
    var first = new ConceptImpl(); first.setUrn("test:Observer"); first.setName("cached label");
    var second = new ConceptImpl(); second.setUrn("test:Observer"); second.setName("another label");
    source.setObserverSemantics(first); selected.setObserverSemantics(second);
    assertEquals(first, second);
    assertTrue(DependencyContract.matches(source, selected));
    second.setUrn("test:OtherObserver");
    assertFalse(DependencyContract.matches(source, selected));
    selected.setObserverSemantics(null);
    assertFalse(DependencyContract.matches(source, selected));
  }
}
