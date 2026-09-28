package org.integratedmodelling.klab.services.reasoner.owl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.EnumSet;
import java.util.List;
import org.integratedmodelling.common.lang.ContextualizableImpl;
import org.integratedmodelling.klab.api.knowledge.Concept;
import org.integratedmodelling.klab.api.knowledge.SemanticType;
import org.integratedmodelling.klab.api.lang.kim.KimConcept;
import org.integratedmodelling.klab.api.lang.kim.impl.KimConceptImpl;
import org.integratedmodelling.klab.api.lang.kim.impl.KimModelImpl;
import org.integratedmodelling.klab.api.lang.kim.impl.KimNamespaceImpl;
import org.integratedmodelling.klab.api.lang.kim.impl.KimObservableImpl;
import org.integratedmodelling.klab.services.reasoner.ReasonerService;
import org.integratedmodelling.klab.services.reasoner.internal.DocumentSemanticValidator;
import org.junit.jupiter.api.Test;

class ProcessContextualizationSemanticValidationTest {

  @Test
  void processAssignmentsUseTheCanonicalOwlAffectsEdge() {
    var reasoner = mock(ReasonerService.class);
    when(reasoner.owl()).thenReturn(mock(OWL.class));
    var resolvedProcess = mock(Concept.class);
    var resolvedElevation = mock(Concept.class);
    when(resolvedProcess.getNotifications()).thenReturn(List.of());
    when(resolvedElevation.getNotifications()).thenReturn(List.of());
    when(reasoner.resolveConcept("earth:Erosion")).thenReturn(resolvedProcess);
    when(reasoner.resolveConcept("geography:Elevation")).thenReturn(resolvedElevation);
    when(reasoner.declareConcept(any()))
        .thenAnswer(
            invocation -> {
              var syntax = (KimConcept) invocation.getArgument(0);
              return syntax.getName().equals("earth:Erosion")
                  ? resolvedProcess
                  : resolvedElevation;
            });
    when(reasoner.traits(any())).thenReturn(List.of());
    when(reasoner.roles(any())).thenReturn(List.of());
    when(reasoner.satisfiable(any())).thenReturn(true);
    when(resolvedElevation.is(SemanticType.QUALITY)).thenReturn(true);

    var process = observable("earth:Erosion", "erosion", SemanticType.PROCESS);
    var elevation = observable("geography:Elevation", "elevation", SemanticType.QUALITY);
    var assignment = new ContextualizableImpl();
    assignment.setTargetId("elevation");
    assignment.setTarget(elevation);
    assignment.setOffsetInDocument(70);
    assignment.setLength(24);
    var model = new KimModelImpl();
    model.getObservables().add(process);
    model.getDependencies().add(elevation);
    model.getContextualization().add(assignment);
    var document = new KimNamespaceImpl();
    document.setUrn("staging.vxii.test.process.basic");
    document.getStatements().add(model);

    when(reasoner.affectedBy(resolvedElevation, resolvedProcess)).thenReturn(true);
    assertTrue(new DocumentSemanticValidator(reasoner).validate(document).isEmpty());

    when(reasoner.affectedBy(resolvedElevation, resolvedProcess)).thenReturn(false);
    var notifications = new DocumentSemanticValidator(reasoner).validate(document);
    assertEquals(1, notifications.size());
    assertTrue(notifications.getFirst().getMessage().contains("affected by the process"));
    assertEquals(70, notifications.getFirst().getLexicalContext().getOffsetInDocument());
  }

  private KimObservableImpl observable(String urn, String name, SemanticType type) {
    var concept = new KimConceptImpl();
    concept.setName(urn);
    concept.setUrn(urn);
    concept.setType(EnumSet.of(type, SemanticType.OBSERVABLE));
    var observable = new KimObservableImpl();
    observable.setSemantics(concept);
    observable.setFormalName(name);
    observable.setCodeName(name);
    return observable;
  }
}
