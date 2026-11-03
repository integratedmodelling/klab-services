package org.integratedmodelling.klab.runtime.language;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import org.integratedmodelling.common.data.jackson.JacksonConfiguration;
import org.integratedmodelling.common.lang.ContextualizableImpl;
import org.integratedmodelling.klab.api.knowledge.SemanticType;
import org.integratedmodelling.klab.api.lang.Contextualizable;
import org.integratedmodelling.klab.api.lang.kim.impl.*;
import org.junit.jupiter.api.Test;

class ConfigurationTerminationValidationTest {
  static KimObservableImpl observable(SemanticType type) {
    var concept=new KimConceptImpl();concept.setName("test:Target");concept.setUrn("test:Target");concept.setType(EnumSet.of(type));
    var observable=new KimObservableImpl();observable.setSemantics(concept);observable.setUrn("test:Target");return observable;
  }
  @Test void authoredPhasesRoundTripWithoutSelectingAnyExecutionTrigger() throws Exception {
    var contextualizable=new ContextualizableImpl();
    ConfigurationTerminationValidation.authoredPhases(contextualizable,List.of("TERMINATION"));
    var mapper=JacksonConfiguration.newObjectMapper();
    var transported=mapper.readValue(mapper.writeValueAsBytes(contextualizable),Contextualizable.class);
    assertNull(contextualizable.getTrigger());assertNull(transported.getTrigger());
    assertTrue(ConfigurationTerminationValidation.termination(transported));
    assertEquals(List.of("TERMINATION"),transported.getMetadata().get(ConfigurationTerminationValidation.AUTHORED_PHASES));
  }
  @Test void modelerRejectsConfigurationTerminationForAuthoredProvenanceAndExplicitApiTriggerOnly() {
    for(boolean apiTrigger:List.of(false,true))for(var type:List.of(SemanticType.CONFIGURATION,SemanticType.QUALITY,SemanticType.PROCESS)) {
      var target=observable(type);var contextualizable=new ContextualizableImpl();contextualizable.setTarget(target);
      contextualizable.setOffsetInDocument(40);contextualizable.setLength(20);
      if(apiTrigger)contextualizable.setTrigger(Contextualizable.Trigger.TERMINATION);
      else ConfigurationTerminationValidation.authoredPhases(contextualizable,List.of("TERMINATION"));
      var model=new KimModelImpl();model.getObservables().add(target);model.getContextualization().add(contextualizable);
      var namespace=new KimNamespaceImpl();namespace.setUrn("test");namespace.getStatements().add(model);
      var visitor=new KimNamespaceVisitor();visitor.visit(namespace);
      var errors=visitor.getNotifications().stream().filter(n->n.getMessage().startsWith("CONFIGURATION_TERMINATION_UNSUPPORTED")).toList();
      assertEquals(type==SemanticType.CONFIGURATION?1:0,errors.size());
      if(!errors.isEmpty())assertEquals(40,errors.getFirst().getLexicalContext().getOffsetInDocument());
      assertEquals(apiTrigger?Contextualizable.Trigger.TERMINATION:null,contextualizable.getTrigger());
    }
  }
}
