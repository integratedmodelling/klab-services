package org.integratedmodelling.klab.runtime.computation;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.integratedmodelling.common.knowledge.*;
import org.integratedmodelling.common.lang.ServiceCallImpl;
import org.integratedmodelling.common.runtime.ActuatorImpl;
import org.integratedmodelling.klab.api.data.*;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.knowledge.*;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.knowledge.observation.impl.ObservationImpl;
import org.integratedmodelling.klab.api.lang.ExpressionCode;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.services.RuntimeService;
import org.integratedmodelling.klab.api.services.runtime.ScalarComputation;
import org.integratedmodelling.klab.configuration.ServiceConfiguration;
import org.junit.jupiter.api.*;

class ScalarSemanticFootprintTest {
  @BeforeAll static void configure(){ServiceConfiguration.injectInstantiators();}
  private final ContextScope scope=mock(ContextScope.class);
  private final RuntimeService runtime=mock(RuntimeService.class);
  private final ObservationImpl target=observation("result",false);

  ScalarSemanticFootprintTest(){
    when(scope.getService(RuntimeService.class)).thenReturn(runtime);
    when(runtime.getDefaultShardingStrategy(any(),same(scope))).thenReturn(
        new Data.ShardingStrategy(Data.FillCurve.D1_LINEAR,1,0,1024,Storage.Type.DOUBLE));
  }
  private static ObservationImpl observation(String name,boolean countable){
    var meaning=new ConceptImpl();meaning.setUrn("test:"+name);meaning.setName(name);meaning.setReferenceName(name);
    meaning.setType(countable?EnumSet.of(SemanticType.SUBJECT,SemanticType.COUNTABLE,SemanticType.OBSERVABLE):EnumSet.of(SemanticType.QUALITY,SemanticType.QUANTIFIABLE,SemanticType.OBSERVABLE));
    meaning.setCollective(countable);meaning.setDescriptionType(countable?Contextualization.INSTANTIATION:Contextualization.QUANTIFICATION);
    var observable=new ObservableImpl();observable.setSemantics(meaning);observable.setUrn(meaning.getUrn());observable.setStatedName(name);
    observable.setDescriptionType(meaning.getDescriptionType());observable.setArtifactType(countable?Artifact.Type.OBJECT:Artifact.Type.NUMBER);
    var result=new ObservationImpl();result.setId(countable?Observation.QUERY_ID:1);result.setUrn("obs:"+name);
    result.setObservable(observable);result.setGeometry(Geometry.create("t0(1){tstart=0,tend=10,ttype=PHYSICAL}"));result.setChildrenCount(3);return result;
  }
  private ScalarComputation.Builder builder(Map<String,Observation> inputs){
    var actuator=new ActuatorImpl();actuator.setObservation(target);actuator.setOperationObservable(target.getObservable());
    return ScalarComputationGroovy.builder(target,scope,actuator,inputs);
  }
  private void expression(ScalarComputation.Builder builder,String code){
    assertTrue(builder.add(ServiceCallImpl.create(RuntimeService.CoreFunctor.EXPRESSION_RESOLVER.getServiceCallName(),
        "expression",ExpressionCode.of(code,"groovy"))));
  }

  @Test void actualCompiledCountUsesOriginalBindingsAndRejectsDifferentOrMutatedBuilder(){
    var original=observation("selected",true);var replacement=observation("other",true);
    var bindings=new HashMap<String,Observation>();bindings.put("selected",original);
    var owner=builder(bindings);expression(owner,"selected.childrenCount");var mapper=owner.build();assertNotNull(mapper);
    bindings.put("selected",replacement);
    var inputs=ScalarComputationGroovy.semanticInputs(owner,mapper).orElseThrow();
    assertEquals(1,inputs.size());assertSame(original,inputs.getFirst());
    assertTrue(ScalarComputationGroovy.semanticInputs(builder(bindings),mapper).isEmpty());
    expression(owner,"2");assertTrue(ScalarComputationGroovy.semanticInputs(owner,mapper).isEmpty());
  }
  @Test void scannerAliasCannotBeReboundEvenWhenOldInputSurvivesUnderAnotherName(){
    var original=observation("foo",true);var replacement=observation("other",true);
    var bindings=new HashMap<String,Observation>();bindings.put("foo",original);bindings.put("bar",original);
    var owner=builder(bindings);expression(owner,"foo.childrenCount");
    var mapper=owner.build();assertNotNull(mapper);
    assertTrue(ScalarComputationGroovy.semanticInputs(owner,mapper,o->o==original,bindings,target).isPresent());
    bindings.put("foo",replacement);
    assertTrue(ScalarComputationGroovy.semanticInputs(owner,mapper,o->o==original||o==replacement,bindings,target).isEmpty());
  }
  @Test void distinctQueryIdInputsAreNotCollapsedByValueEquality(){
    var left=observation("left",true);var right=observation("right",true);
    assertEquals(left.getId(),right.getId());
    var owner=builder(Map.of("left",left,"right",right));expression(owner,"left.childrenCount + right.childrenCount");
    var mapper=owner.build();assertNotNull(mapper);
    var inputs=ScalarComputationGroovy.semanticInputs(owner,mapper).orElseThrow();assertEquals(2,inputs.size());
    assertTrue(inputs.stream().anyMatch(o->o==left));assertTrue(inputs.stream().anyMatch(o->o==right));
  }
  @Test void pureProcessedArithmeticAndConstantHaveClosedFootprints(){
    var value=observation("value",false);var owner=builder(Map.of("value",value));expression(owner,"(value - 10) / 2");
    var mapper=owner.build();assertNotNull(mapper);assertSame(value,ScalarComputationGroovy.semanticInputs(owner,mapper).orElseThrow().getFirst());
    var constant=builder(Map.of());assertTrue(constant.add(ServiceCallImpl.create(RuntimeService.CoreFunctor.CONSTANT_RESOLVER.getServiceCallName(),"value",3L)));
    var literal=constant.build();assertNotNull(literal);assertTrue(ScalarComputationGroovy.semanticInputs(constant,literal).orElseThrow().isEmpty());
  }
  @Test void actualCompiledMethodCallsRemainUnknown(){
    var owner=builder(Map.of());expression(owner,"scope.getId()");var mapper=owner.build();assertNotNull(mapper);
    assertTrue(ScalarComputationGroovy.semanticInputs(owner,mapper).isEmpty());
    assertTrue(ScalarComputationGroovy.semanticInputs(mock(ScalarComputation.Builder.class),mock(ScalarComputationGroovy.class)).isEmpty());
  }
  @Test void smallGrammarRejectsSemanticCallsOpaqueSyntaxAndUnboundVariables(){
    var selected=observation("selected",true);var value=observation("value",false);
    var map=Map.<String,Observation>of("selectedObs",selected,"value",value);
    for(var code:List.of("selectedObs.childrenCount","-(value-10)/2","3L","true"))
      assertTrue(ClosedScalarArithmetic.accepts(code,map,o->o==selected||o==value),code);
    for(var code:List.of("scope.getId()","selectedObs.max","selectedObs.getChildrenCount()","new URL('x')","unknown + 1","value = 3","value; 1","{{test:Meaning}}","value?.max","selectedObs + 1"))
      assertFalse(ClosedScalarArithmetic.accepts(code,map,o->o==selected||o==value),code);
  }
  @Test void untrustedReceiverIsRejectedBeforeAnyGetter(){
    var reads=new AtomicInteger();
    var fake=(Observation)java.lang.reflect.Proxy.newProxyInstance(getClass().getClassLoader(),new Class[]{Observation.class},(p,m,a)->{reads.incrementAndGet();throw new AssertionError("Untrusted getter");});
    assertFalse(ClosedScalarArithmetic.accepts("x.childrenCount",Map.of("x",fake),o->false));assertEquals(0,reads.get());
  }
}
