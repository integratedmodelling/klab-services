package org.integratedmodelling.klab.services.runtime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.*;
import java.util.concurrent.*;
import org.integratedmodelling.common.knowledge.*;
import org.integratedmodelling.common.lang.ServiceCallImpl;
import org.integratedmodelling.common.runtime.ActuatorImpl;
import org.integratedmodelling.klab.api.data.*;
import org.integratedmodelling.klab.api.digitaltwin.Scheduler;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.knowledge.*;
import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.knowledge.observation.impl.ObservationImpl;
import org.integratedmodelling.klab.api.services.Reasoner;
import org.integratedmodelling.klab.api.services.runtime.impl.ExpressionCodeImpl;
import org.integratedmodelling.klab.api.services.runtime.objects.*;
import org.integratedmodelling.klab.configuration.ServiceConfiguration;
import org.integratedmodelling.klab.runtime.computation.ScalarComputationGroovy;
import org.integratedmodelling.klab.runtime.storage.StorageReads;
import org.integratedmodelling.klab.services.scopes.ServiceContextScope;
import org.junit.jupiter.api.*;

/** Real scalar/storage continuation, with discovery, semantic admission and graph/service I/O controlled.
 * This is not a full resolver/catalog/live-stack test, regional redistribution or E/S sampling.
 */
class MemberQualityScalarPipelineTest {
  @BeforeAll static void configure() { ServiceConfiguration.injectInstantiators(); }

  @Test @Timeout(45) void distinctNativeSupportsRetainTwoStoredStreamQualitiesInTheTypedCollection() throws Exception {
    try(var first=new StorageConsumerExecutionTest.Fixture();var second=new StorageConsumerExecutionTest.Fixture()) {
      var root=mock(ServiceContextScope.class);var reasoner=mock(Reasoner.class);
      var caps=mock(Reasoner.Capabilities.class);
      var runtime=mock(org.integratedmodelling.klab.api.services.RuntimeService.class);
      var region=observation(10,"test:Region",SemanticType.SUBJECT,"T0(1){ttype=PHYSICAL,tstart=1000,tend=10000}");
      var one=observation(11,"test:Stream",SemanticType.SUBJECT,"T0(1){ttype=PHYSICAL,tstart=1000,tend=4000}");
      var two=observation(12,"test:Stream",SemanticType.SUBJECT,"T0(1){ttype=PHYSICAL,tstart=4000,tend=8000}");
      var query=observation(30,"each test:Stream",SemanticType.SUBJECT,region.getGeometry().encode());
      ((ConceptImpl)query.getObservable().getSemantics()).setCollective(true);
      var members=query.getObservable();
      var quality=observable("test:StreamGradient",SemanticType.QUALITY);
      ((ConceptImpl)quality.getSemantics()).getType().add(SemanticType.RATIO);
      ((ConceptImpl)quality.getSemantics()).setDescriptionType(Contextualization.QUANTIFICATION);
      quality.setDescriptionType(Contextualization.QUANTIFICATION);
      assertNull(quality.getUnit());
      when(root.getContextObservation()).thenReturn(region);when(root.getService(Reasoner.class)).thenReturn(reasoner);
      when(reasoner.capabilities(root)).thenReturn(caps);when(caps.getServiceId()).thenReturn("fixture-reasoner");when(caps.getKnowledgeRevision()).thenReturn(7L);
      for(var member:List.of(region,one,two))when(root.getObservation(member.getId())).thenReturn(member);
      when(reasoner.resolveObservable(members.getUrn())).thenReturn(members);
      when(reasoner.resolveObservable(quality.getUrn())).thenReturn(quality);
      when(reasoner.inherent(quality)).thenReturn(one.getObservable().getSemantics());
      // Fixture semantic catalogue recognizes only these known Stream memberships/bearer checks.
      when(reasoner.is(any(),any())).thenReturn(true);
      when(runtime.getMembers(query,root)).thenReturn(List.of(one,two));
      var fixtures=List.of(first,second);var streams=List.of(one,two);var values=List.of(.2,.4);
      var outputs=new HashMap<Long,ObservationImpl>();
      for(int i=0;i<2;i++) {
        var fixture=fixtures.get(i);var member=streams.get(i);var value=values.get(i);
        assertNotEquals(Geometry.forTransport(region.getGeometry()).encode(),Geometry.forTransport(member.getGeometry()).encode());
        // No mocked intersection and no cloned regional geometry to bypass native-support checks.
        var intersection=RuntimeService.intersection(member.getGeometry(),region.getGeometry());
        assertNotNull(intersection,"A wholly contained native support must have an intersection");
        assertTrue(MemberQualityResolution.sameSupport(member.getGeometry(),intersection),"Production intersection must preserve this native temporal subset after canonical extent promotion");
        when(root.within(member)).thenReturn(fixture.scope);when(fixture.scope.getContextObservation()).thenReturn(member);
        when(runtime.submit(any(),eq(fixture.scope))).thenAnswer(call->{
          Observation submitted=call.getArgument(0);
          assertEquals(quality.getUrn(),submitted.getObservable().getUrn());
          assertEquals(Geometry.forTransport(member.getGeometry()).encode(),Geometry.forTransport(submitted.getGeometry()).encode());
          var output=fixture.quality(member.getId()+100,1,Data.FillCurve.D1_LINEAR,Storage.Type.DOUBLE,member.getGeometry().encode());
          output.setObservable(submitted.getObservable());output.setParentId(member.getId());output.setResolvedCoverage(1);output.setEventTimestamps(List.of(1000L));
          var actuator=new ActuatorImpl();actuator.setObservation(output);actuator.setOperationObservable(output.getObservable());actuator.setActuatorType(org.integratedmodelling.klab.api.services.runtime.Actuator.Type.OBSERVE);
          var expression=new ExpressionCodeImpl();expression.setCode(Double.toString(value));
          var callBean=ServiceCallImpl.create(org.integratedmodelling.klab.api.services.RuntimeService.CoreFunctor.EXPRESSION_RESOLVER.getServiceCallName(),"expression",expression);
          var builder=ScalarComputationGroovy.builder(output,fixture.scope,actuator,Map.of());assertTrue(builder.add(callBean));
          var executor=new ScalarOperationExecutor(builder,output,Map.of(),fixture.scope,fixture.execution);
          assertTrue(executor.validate());assertTrue(executor.execute(Scheduler.Event.initialization(),fixture.scope,null));
          try(var read=StorageReads.open(output,fixture.scope,null,Data.FillCurve.D1_LINEAR,Storage.DoubleScanner.class)) {
            var scanner=read.scanners().getFirst();assertEquals(1,scanner.size());assertTrue(scanner.isValid());assertEquals(value,scanner.get(),1e-12);
          }
          outputs.put(member.getId(),output);
          return CompletableFuture.completedFuture(output);
        });
      }
      var request=new MemberQualityRequest(new SingularContextBinding(region.getId(),region.getUrn(),region.getObservable().getUrn(),Geometry.forTransport(region.getGeometry()).encode(),quality.getUrn(),"fixture-reasoner",7),members.getUrn(),quality.getUrn());
      var collection=MemberQualityResolution.resolve(runtime,request,root,(discovered,view)->{assertSame(members,discovered);return query;}).get(30,TimeUnit.SECONDS);
      assertEquals(2,collection.getEntries().size());assertEquals(2,outputs.size());
      for(int i=0;i<2;i++) {
        var entry=collection.getEntries().get(i);var member=streams.get(i);var stored=outputs.get(member.getId());
        assertEquals(member.getId(),entry.getMember().getId());assertEquals(member.getUrn(),entry.getMember().getUrn());
        assertEquals(member.getId(),entry.getQuality().getParentId());assertEquals(stored.getId(),entry.getQuality().getId());
        assertEquals(quality.getUrn(),entry.getQuality().getObservable().getUrn());assertNull(entry.getQuality().getObservable().getUnit());
        assertEquals(Geometry.forTransport(member.getGeometry()).encode(),Geometry.forTransport(entry.getQuality().getGeometry()).encode());
        try(var read=StorageReads.open(stored,fixtures.get(i).scope,null,Data.FillCurve.D1_LINEAR,Storage.DoubleScanner.class)) {
          var scanner=read.scanners().getFirst();assertTrue(scanner.isValid());assertEquals(values.get(i),scanner.get(),1e-12);
        }
        verify(root).within(member);verify(runtime).submit(any(),eq(fixtures.get(i).scope));
      }
      assertTrue(collection.getEntries().stream().noneMatch(e->e.getQuality().getParentId()==region.getId()));
      verify(runtime).getMembers(query,root);verifyNoMoreInteractions(runtime);
    }
  }

  static ObservableImpl observable(String urn,SemanticType type) {
    var concept=new ConceptImpl();concept.setUrn(urn);concept.setName(urn.substring(urn.indexOf(':')+1));concept.setReferenceName(urn.replace(':','_').replace(' ','_'));concept.setType(EnumSet.of(type));
    var result=new ObservableImpl();result.setUrn(urn);result.setSemantics(concept);return result;
  }
  static ObservationImpl observation(long id,String urn,SemanticType type,String support) {
    var result=new ObservationImpl();result.setId(id);result.setUrn("fixture:observation:"+id);result.setObservable(observable(urn,type));result.setGeometry(Geometry.create(support));result.setEventTimestamps(List.of(1000L));result.setResolvedCoverage(1);return result;
  }
}
