package org.integratedmodelling.klab.services.resolver;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import java.util.*;
import org.integratedmodelling.common.knowledge.GeometryRepository;
import org.integratedmodelling.common.knowledge.ConceptImpl;
import org.integratedmodelling.common.knowledge.ObservableImpl;
import org.integratedmodelling.klab.api.collections.Parameters;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.knowledge.*;
import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.api.knowledge.observation.impl.ObservationImpl;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.services.*;
import org.integratedmodelling.klab.api.services.resolver.*;
import org.integratedmodelling.klab.api.services.resolver.objects.*;
import org.integratedmodelling.klab.api.services.resources.ResourceSet;
import org.integratedmodelling.klab.configuration.ServiceConfiguration;
import org.junit.jupiter.api.*;

class SingularBindingResolutionTest {
  @BeforeAll static void configure() { ServiceConfiguration.injectInstantiators(); }
  private final ContextScope scope=mock(ContextScope.class);
  private final Reasoner reasoner=mock(Reasoner.class);
  private final Reasoner.Capabilities capabilities=mock(Reasoner.Capabilities.class);
  private final org.integratedmodelling.klab.api.knowledge.observation.scale.Scale scale=GeometryRepository.INSTANCE.scale(
      Geometry.create("T0(1){tend=10,tstart=0,ttype=PHYSICAL}"));
  private final Observable target=observable("test:Temperature",SemanticType.QUALITY);
  private final ObservationImpl context=new ObservationImpl();
  private final ObservationImpl request=new ObservationImpl();
  private final ObservationStrategyImpl strategy=new ObservationStrategyImpl();
  private final ObservationStrategyImpl.OperationImpl operation=new ObservationStrategyImpl.OperationImpl();
  private final Model model=mock(Model.class);
  private final ResolutionCompiler compiler=spy(new ResolutionCompiler(mock(ResolverService.class)));
  SingularBindingResolutionTest() {
    context.setId(21);context.setUrn("context:21");context.setGeometry(scale);
    context.setObservable(observable("test:System",SemanticType.SUBJECT));context.setEventTimestamps(List.of(0L));
    request.setId(-1);request.setUrn("request");request.setObservable(target);request.setGeometry(scale);
    when(scope.getContextObservation()).thenReturn(context);when(scope.getObservation(21)).thenReturn(context);
    when(scope.withResolutionConstraints(any(ResolutionConstraint[].class))).thenReturn(scope);
    when(scope.getService(Reasoner.class)).thenReturn(reasoner); when(reasoner.capabilities(scope)).thenReturn(capabilities);
    when(capabilities.getServiceId()).thenReturn("reasoner"); when(capabilities.getKnowledgeRevision()).thenReturn(3L);
    var state=Parameters.<String>create();state.put("__RESOLUTION_GRAPH__",ResolutionGraph.create(scope));when(scope.getData()).thenReturn(state);
    strategy.setUrn("test:singular");strategy.setRank(1);
    strategy.setContextBinding(new SingularContextBinding(21,context.getUrn(),context.getObservable().getUrn(),
        Geometry.forTransport(scale).encode(),target.getUrn(),"reasoner",3));
    operation.setId("bound");operation.setType(ObservationStrategy.Operation.Type.OBSERVE);operation.setObservable(target);
    strategy.getOperations().add(operation);
    when(reasoner.computeObservationStrategies(request,scope)).thenReturn(List.of(strategy));
    doReturn(new ResolutionCompiler.QueryMatch(null,null,scale,null,Coverage.create(scale,0))).when(compiler).query(target,scale,scope);
    when(model.getUrn()).thenReturn("test:precomputed");when(model.getNamespace()).thenReturn("test");
    when(model.getCoverage()).thenReturn(scale);when(model.getObservables()).thenReturn(List.of(target));
    var computation=mock(org.integratedmodelling.klab.api.lang.Contextualizable.class);when(model.getComputation()).thenReturn(List.of(computation));
    doReturn(List.of(model)).when(compiler).queryModels(target,context.getObservable().asConcept(),scope,scale);
    var runtime=mock(RuntimeService.class);when(scope.getService(RuntimeService.class)).thenReturn(runtime);
    when(runtime.resolveContextualizables(model.getComputation(),scope)).thenReturn(new ResourceSet());
  }
  private static Observable observable(String urn,SemanticType kind) {
    var c=new ConceptImpl();c.setUrn(urn);c.setType(EnumSet.of(kind));c.setName(urn);c.setReferenceName(urn);
    var o=new ObservableImpl();o.setSemantics(c);o.setUrn(urn);o.setName(urn);
    o.setDescriptionType(kind==SemanticType.QUALITY?Contextualization.QUANTIFICATION:Contextualization.ACKNOWLEDGEMENT);return o;
  }
  @Test void ordinaryModelResolutionAndDataflowKeepNativeContextIdentity() {
    var graph=compiler.resolve(request,scope);assertFalse(graph.isEmpty());assertTrue(graph.getCoverage().isComplete());
    var flow=new DataflowCompiler(request,graph,scope).compile();
    assertEquals(1,flow.getComputation().size());
    assertEquals(strategy.getContextBinding(),flow.getComputation().getFirst().getContextBinding());
    assertEquals(target.getUrn(),flow.getComputation().getFirst().getOperationObservable().getUrn());
    verify(compiler,times(1)).queryModels(target,context.getObservable().asConcept(),scope,scale);
    verify(scope,never()).within(any());verify(scope,never()).observation(any());
  }
  @Test void incompatibleIdentityFailsBeforeModelLookup() {
    when(scope.getObservation(21)).thenReturn(null);assertTrue(compiler.resolve(request,scope).isEmpty());
    verify(compiler,never()).queryModels(any(),any(),any(),any());
  }
  @Test void failedChildHasNoPublishedCoverage() {
    doReturn(List.of()).when(compiler).queryModels(target,context.getObservable().asConcept(),scope,scale);
    assertTrue(compiler.resolve(request,scope).isEmpty());
  }
  @Test void cancellationBeforeBindingHasNoModelOrIdentityLookup() {
    when(scope.isInterrupted()).thenReturn(true);assertTrue(compiler.resolve(request,scope).isEmpty());
    verify(scope,never()).getObservation(anyLong());verify(compiler,never()).queryModels(any(),any(),any(),any());
  }
  @Test void cancellationDuringChildDiscardsCoverageAndGuardIsCleaned() {
    doAnswer(call->{when(scope.isInterrupted()).thenReturn(true);return List.of(model);})
        .when(compiler).queryModels(target,context.getObservable().asConcept(),scope,scale);
    assertTrue(compiler.resolve(request,scope).isEmpty());
    when(scope.isInterrupted()).thenReturn(false);
    doReturn(List.of(model)).when(compiler).queryModels(target,context.getObservable().asConcept(),scope,scale);
    assertFalse(compiler.resolve(request,scope).isEmpty());
  }
  @Test void sameObligationResolveIsRejectedWithoutRegistrationOrCycle() {
    operation.setType(ObservationStrategy.Operation.Type.RESOLVE);assertTrue(compiler.resolve(request,scope).isEmpty());
    verify(scope,never()).observation(any());verify(compiler,never()).queryModels(any(),any(),any(),any());
  }
}
