package org.integratedmodelling.klab.services.resolver;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.*;
import org.integratedmodelling.common.knowledge.*;
import org.integratedmodelling.klab.api.knowledge.*;
import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.services.reasoner.ObservationReasoner;
import org.junit.jupiter.api.Test;

class CollectiveSelectionBindingTest {
  @org.junit.jupiter.api.BeforeAll static void configure() { CollectiveReductionPlanningTest.configure(); }
  final CollectiveReductionPlanningTest f=new CollectiveReductionPlanningTest();
  final Observable requested=CollectiveReductionPlanningTest.observable("each test:Beech test:Tree",SemanticType.SUBJECT,SemanticType.COUNTABLE);
  final ConceptImpl predicate=concept("test:Beech",SemanticType.IDENTITY);
  final ConceptImpl family=concept("test:Species",SemanticType.IDENTITY,SemanticType.ABSTRACT);
  final Observable directive=CollectiveSelectionPlanningTest.directive("test:Species");
  final org.integratedmodelling.klab.api.knowledge.observation.Observation request;
  CollectiveSelectionBindingTest() {
    when(f.scope.getId()).thenReturn("selection-test");
    ((ConceptImpl)requested.asConcept()).setCollective(true);((ObservableImpl)requested).setDescriptionType(Contextualization.INSTANTIATION);
    request=f.observation(0,"request",requested);
    when(f.reasoner.directTraits(requested)).thenReturn(List.of(predicate));
    when(f.reasoner.lexicalRoot(predicate)).thenReturn(family);
    when(f.reasoner.buildObservable(any(),eq(f.scope))).thenAnswer(call->{
      var b=call.getArgument(0,ObservableBuildStrategy.class);
      if(b.getBaseObservable()==requested) {
        assertEquals(ObservableBuildStrategy.OperationType.WITHOUT,b.getOperations().getFirst().getType());
        assertEquals(List.of(predicate),b.getOperations().getFirst().getConcepts());return f.trees;
      }
      assertEquals(family,b.getBaseConcept());return directive;
    });
  }
  static ConceptImpl concept(String urn,SemanticType...types) {
    var c=new ConceptImpl();c.setUrn(urn);var t=EnumSet.of(SemanticType.PREDICATE);t.addAll(List.of(types));c.setType(t);c.setAbstract(t.contains(SemanticType.ABSTRACT));return c;
  }
  @Test void allFourPredicateKindsUseSameExplicitFamilyContract() {
    for(var kind:List.of(SemanticType.IDENTITY,SemanticType.ATTRIBUTE,SemanticType.REALM,SemanticType.ROLE)) {
      predicate.setType(EnumSet.of(SemanticType.PREDICATE,kind));family.setType(EnumSet.of(SemanticType.PREDICATE,kind,SemanticType.ABSTRACT));
      when(f.reasoner.directTraits(requested)).thenReturn(kind==SemanticType.ROLE?List.of():List.of(predicate));
      when(f.reasoner.directRoles(requested)).thenReturn(kind==SemanticType.ROLE?List.of(predicate):List.of());
      var binding=CollectiveSelectionBinding.capture(request,f.scope,f.reasoner);assertNotNull(binding);
      assertEquals(List.of("test:Beech"),binding.predicates());assertEquals(List.of("test:Species"),binding.families());
      assertEquals(f.trees.getUrn(),binding.nativeMembers());
    }
  }
  @Test void unorderedPredicateEnumerationCapturesOneStableFamilyPlan() {
    var healthy=concept("test:Healthy",SemanticType.ATTRIBUTE);
    var condition=concept("test:Condition",SemanticType.ATTRIBUTE,SemanticType.ABSTRACT);
    var conditionDirective=CollectiveSelectionPlanningTest.directive("test:Condition");
    when(f.reasoner.lexicalRoot(healthy)).thenReturn(condition);
    when(f.reasoner.directTraits(requested)).thenReturn(List.of(healthy,predicate),List.of(predicate,healthy));
    when(f.reasoner.buildObservable(any(),eq(f.scope))).thenAnswer(call->{
      var b=call.getArgument(0,ObservableBuildStrategy.class);
      if(b.getBaseObservable()==requested)return f.trees;
      return b.getBaseConcept().equals(family)?directive:conditionDirective;
    });
    var first=CollectiveSelectionBinding.capture(request,f.scope,f.reasoner);
    var second=CollectiveSelectionBinding.capture(request,f.scope,f.reasoner);
    assertNotNull(first);assertEquals(first,second);
    assertEquals(List.of("test:Beech","test:Healthy"),first.predicates());
    assertEquals(List.of("test:Species","test:Condition"),first.families());
  }
  @Test void compoundAndQuantifiedAndOccurrentTargetsStayOutsideTheBoundedRoute() {
    var concept=(ConceptImpl)requested.asConcept();
    for(var unsupported:List.of(SemanticType.UNION,SemanticType.INTERSECTION,SemanticType.OCCURRENCE,
        SemanticType.ANY,SemanticType.ALL,SemanticType.NONE)) {
      concept.getType().add(unsupported);
      assertNull(CollectiveSelectionBinding.capture(request,f.scope,f.reasoner),unsupported.name());
      concept.getType().remove(unsupported);
    }
    concept.getType().remove(SemanticType.SUBJECT);concept.getType().add(SemanticType.EVENT);
    assertNull(CollectiveSelectionBinding.capture(request,f.scope,f.reasoner));
    concept.getType().remove(SemanticType.EVENT);concept.getType().add(SemanticType.RELATIONSHIP);
    assertNull(CollectiveSelectionBinding.capture(request,f.scope,f.reasoner));
  }
  @Test void unknownOrCompetingFamilyIsNotSilentlySelected() {
    when(f.reasoner.lexicalRoot(predicate)).thenReturn(null);assertNull(CollectiveSelectionBinding.capture(request,f.scope,f.reasoner));
    when(f.reasoner.lexicalRoot(predicate)).thenReturn(family);
    var oak=concept("test:Oak",SemanticType.IDENTITY);when(f.reasoner.lexicalRoot(oak)).thenReturn(family);
    when(f.reasoner.directTraits(requested)).thenReturn(List.of(predicate,oak));
    assertNull(CollectiveSelectionBinding.capture(request,f.scope,f.reasoner));
  }
  @Test void concreteLexicalRootUsesNormalAnySelfClassification() {
    family.setType(EnumSet.of(SemanticType.PREDICATE,SemanticType.IDENTITY));family.setAbstract(false);
    when(f.reasoner.resolveObservable("any test:Species of "+f.trees.getSemantics().getUrn())).thenReturn(directive);
    assertNotNull(CollectiveSelectionBinding.capture(request,f.scope,f.reasoner));
    verify(f.reasoner).resolveObservable("any test:Species of "+f.trees.getSemantics().getUrn());
  }
  @Test void scopeAndNativeFocusArePartOfThePrecommitAdmission() {
    var binding=CollectiveSelectionBinding.capture(request,f.scope,f.reasoner);assertNotNull(binding);
    assertEquals("selection-test",binding.scopeId());binding.require(f.scope,requested,request.getGeometry());
    when(f.scope.getId()).thenReturn("another-twin");
    assertThrows(IllegalStateException.class,()->binding.require(f.scope,requested,request.getGeometry()));
    when(f.scope.getId()).thenReturn("selection-test");
    when(f.scope.getContextObservation()).thenReturn(null);
    assertThrows(IllegalStateException.class,()->binding.require(f.scope,requested,request.getGeometry()));
  }
  @Test void sameObserverIdAndUrnCannotAuthorizeAChangedAgentClass() {
    var observer=f.observation(81,"observer:81",CollectiveReductionPlanningTest.observable("test:ObserverA",SemanticType.AGENT));
    when(f.scope.getObserver()).thenReturn(observer);
    var binding=CollectiveSelectionBinding.capture(request,f.scope,f.reasoner);assertNotNull(binding);
    assertEquals("test:ObserverA",binding.observerClass());binding.require(f.scope,requested,request.getGeometry());
    observer.setObservable(CollectiveReductionPlanningTest.observable("test:ObserverB",SemanticType.AGENT));
    assertThrows(IllegalStateException.class,()->binding.require(f.scope,requested,request.getGeometry()));
    observer.setObservable(CollectiveReductionPlanningTest.observable("test:Region",SemanticType.SUBJECT));
    assertNull(CollectiveSelectionBinding.capture(request,f.scope,f.reasoner));
  }
  @Test void ordinaryBareAndAbstractRequestsDoNotOfferSelection() {
    when(f.reasoner.directTraits(requested)).thenReturn(List.of());
    assertNull(CollectiveSelectionBinding.capture(request,f.scope,f.reasoner));
    when(f.reasoner.directTraits(requested)).thenReturn(List.of(predicate));predicate.setType(EnumSet.of(SemanticType.PREDICATE,SemanticType.IDENTITY,SemanticType.ABSTRACT));
    assertNull(CollectiveSelectionBinding.capture(request,f.scope,f.reasoner));
  }
  @Test void syntheticStrategyRetainsPortableBindingAndRankOne() throws Exception {
    var strategies=new ObservationReasoner(f.reasoner,ignored->null).computeMatchingStrategies(request,f.scope,true);
    assertEquals(1,strategies.size());assertEquals(1,strategies.getFirst().getRank());
    var mapper=org.integratedmodelling.common.data.jackson.JacksonConfiguration.newObjectMapper();
    var copy=mapper.readValue(mapper.writeValueAsBytes(strategies.getFirst()),ObservationStrategy.class);
    assertEquals(strategies.getFirst().getCollectiveSelection(),copy.getCollectiveSelection());
  }
}
