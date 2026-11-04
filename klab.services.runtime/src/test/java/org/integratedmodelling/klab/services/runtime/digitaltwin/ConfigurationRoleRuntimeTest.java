package org.integratedmodelling.klab.services.runtime.digitaltwin;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.*;
import org.integratedmodelling.common.knowledge.*;
import org.integratedmodelling.klab.api.data.*;
import org.integratedmodelling.klab.api.digitaltwin.*;
import org.integratedmodelling.klab.api.knowledge.*;
import org.integratedmodelling.klab.api.knowledge.observation.impl.ObservationImpl;
import org.integratedmodelling.klab.api.lang.kim.KimConsequenceRule;
import org.junit.jupiter.api.*;

class ConfigurationRoleRuntimeTest {
  @AfterEach void cleanup(){DefaultRoleConsequenceRuntimeTest.cleanup();}
  @BeforeAll static void configure(){org.integratedmodelling.klab.configuration.ServiceConfiguration.injectInstantiators();}
  static DefaultRoleConsequenceRuntimeTest.Fixture configuration(SemanticType ingredientType)throws Exception {
    var f=new DefaultRoleConsequenceRuntimeTest.Fixture();
    var owner=new ConceptImpl();owner.setUrn("test:Configuration");owner.setType(EnumSet.of(SemanticType.CONFIGURATION,SemanticType.OBSERVABLE));owner.setDescriptionType(Contextualization.DETECTION);
    var observable=ObservableImpl.promote(owner,f.role.f.scope);observable.setDescriptionType(Contextualization.DETECTION);((ObservationImpl)f.role.source).setObservable(observable);
    when(f.role.f.reasoner.is(any(),eq(owner))).thenReturn(true);
    var ingredient=new ObservationImpl();ingredient.setId(71);ingredient.setUrn("context.71");ingredient.setGeometry(f.role.source.getGeometry());ingredient.setParentId(41);
    var meaning=new ConceptImpl();meaning.setUrn("test:Ingredient");meaning.setType(EnumSet.of(ingredientType,SemanticType.OBSERVABLE));
    var value=ObservableImpl.promote(meaning,f.role.f.scope);value.setDescriptionType(ingredientType==SemanticType.QUALITY?Contextualization.QUANTIFICATION:Contextualization.ACKNOWLEDGEMENT);ingredient.setObservable(value);f.assets.put(71L,ingredient);
    f.extraEdges.add(new KnowledgeGraph.ConsequenceEdgeSnapshot("ingredient",f.ref(70),f.ref(71),GraphModel.Relationship.EMERGED_FROM,Map.of()));
    f.extraEdges.add(new KnowledgeGraph.ConsequenceEdgeSnapshot("ingredient-bearer",f.ref(41),f.ref(71),GraphModel.Relationship.HAS_CHILD,Map.of()));
    f.authored(KimConsequenceRule.Kind.IMPLIES,f.role.role.getUrn(),owner,null);
    return f;
  }
  @Test void dependentConfigurationAttributesRoleToActualCommonSubstantialBearerWithoutCharacterization()throws Exception {
    var f=configuration(SemanticType.QUALITY);
    var effect=f.runtime.configurationImplications(f.role.f.scope.getConsequenceCause(),70,f.role.f.scope);
    assertEquals(Consequence.Outcome.APPLIED,effect.outcome(),effect.reason());assertEquals(List.of(41L),effect.observations());
    verify(f.localRuntime,never()).submit(any(),any());verify(f.role.f.reasoner,atLeastOnce()).consequenceRules(argThat(query->query.kinds().equals(Set.of(KimConsequenceRule.Kind.IMPLIES))));
  }
  @Test void substantialConfigurationDoesNotInventBearerFromItsFocusParent()throws Exception {
    var f=configuration(SemanticType.SUBJECT);
    var effect=f.runtime.configurationImplications(f.role.f.scope.getConsequenceCause(),70,f.role.f.scope);
    assertEquals(Consequence.Outcome.FAILED,effect.outcome());verify(f.role.f.twin,never()).transaction(any(),any(),any());
    assertSame(f.role.before,f.role.recipient.getObservable());
  }
  @Test void dependentIngredientsWithDifferentObservedParentsCannotSupplyDefaultRoleBearer()throws Exception {
    var f=configuration(SemanticType.QUALITY);
    f.extraEdges.removeIf(edge->edge.elementId().equals("ingredient-bearer"));
    var other=DefaultRoleConsequenceRuntimeTest.actualObservation(42,SemanticType.SUBJECT);f.assets.put(42L,other);
    f.extraEdges.add(new KnowledgeGraph.ConsequenceEdgeSnapshot("other-bearer",f.ref(42),f.ref(71),GraphModel.Relationship.HAS_CHILD,Map.of()));
    var effect=f.runtime.configurationImplications(f.role.f.scope.getConsequenceCause(),70,f.role.f.scope);
    assertEquals(Consequence.Outcome.FAILED,effect.outcome());verify(f.role.f.storage,never()).store(any());
  }
  @Test void explicitParticipantDomainIncludesOnlyObservedSubstantialsAndRelationshipEndpoints()throws Exception {
    var f=configuration(SemanticType.RELATIONSHIP);
    f.extraEdges.add(new KnowledgeGraph.ConsequenceEdgeSnapshot("endpoint",f.ref(71),f.ref(41),GraphModel.Relationship.HAS_RELATIONSHIP_SOURCE,Map.of()));
    var base=ConsequenceNativeInputs.capture(70,List.of(),f.role.f.scope);
    var inputs=ConfigurationRoleInputs.capture(70,base,f.role.f.scope);
    assertEquals(Set.of(41L,71L),ConfigurationRoleInputs.participants(70,inputs));
    assertTrue(ConfigurationRoleInputs.bearers(70,inputs).isEmpty());assertFalse(ConfigurationRoleInputs.participants(70,inputs).contains(70L));
  }
}

