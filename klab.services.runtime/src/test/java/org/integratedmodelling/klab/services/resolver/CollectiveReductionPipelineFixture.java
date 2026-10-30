package org.integratedmodelling.klab.services.resolver;

import static org.mockito.Mockito.*;
import java.util.*;
import org.integratedmodelling.common.lang.*;
import org.integratedmodelling.common.knowledge.*;
import org.integratedmodelling.klab.api.collections.Parameters;
import org.integratedmodelling.klab.api.knowledge.*;
import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.api.knowledge.observation.*;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.services.*;
import org.integratedmodelling.klab.api.services.resources.ResourceSet;
import org.integratedmodelling.klab.api.services.resolver.*;
import org.integratedmodelling.klab.api.services.runtime.*;
import org.integratedmodelling.klab.runtime.language.ModelValidationGate;
import org.integratedmodelling.klab.services.reasoner.ObservationReasoner;

/** Controlled catalog boundary only: production strategy, resolver, admission and dataflow compiler. */
public class CollectiveReductionPipelineFixture {
 public record Plan(Actuator actuator,Model model,org.integratedmodelling.klab.api.lang.kim.KimNamespace namespace) {}
 public static Plan plan(Observation target,Observation discovery,ContextScope scope,Reasoner reasoner) {
  var trees=discovery.getObservable();var scale=GeometryRepository.INSTANCE.scale(target.getGeometry());
  var compiler=spy(new ResolutionCompiler(mock(ResolverService.class)));
  when(scope.withResolutionConstraints(any(ResolutionConstraint[].class))).thenReturn(scope);
  when(scope.within(nullable(Observation.class))).thenReturn(scope);
  var data=Parameters.<String>create();data.put("__RESOLUTION_GRAPH__",ResolutionGraph.create(scope));when(scope.getData()).thenReturn(data);
  var builder=mock(Observation.Builder.class,RETURNS_SELF);when(scope.observation(trees)).thenReturn(builder);when(builder.register()).thenReturn(discovery);doAnswer(call->{((org.integratedmodelling.klab.api.knowledge.observation.impl.ObservationImpl)discovery).setGeometry(call.getArgument(0));return builder;}).when(builder).geometry(any(org.integratedmodelling.klab.api.geometry.Geometry.class));
  var strategies=new ObservationReasoner(reasoner,ignored->null);
  doAnswer(call->{Observation o=call.getArgument(0);return o.getObservable().getUrn().equals(trees.getUrn())
      ? List.of(CollectiveReductionPlanningTest.flat(trees)):strategies.computeMatchingStrategies(o,scope,true);})
      .when(reasoner).computeObservationStrategies(any(),eq(scope));
  doReturn(new ResolutionCompiler.QueryMatch(null,null,scale,null,Coverage.create(scale,0)))
      .when(compiler).query(any(),any(),eq(scope));
  var model=new ModelImpl();model.setUrn("test:syntheticTrees");model.setNamespace("test");model.setCoverage(scale);
  model.setObservables(List.of(trees));var code=new ContextualizableImpl();code.setServiceCall(new ServiceCallImpl("test.synthetic.trees"));model.setComputation(List.of(code));
  doReturn(List.of(model)).when(compiler).queryModels(eq(trees),any(),eq(scope),any());
  var resources=mock(ResourcesService.class);when(scope.getService(ResourcesService.class)).thenReturn(resources);
  var namespace=new org.integratedmodelling.klab.api.lang.kim.impl.KimNamespaceImpl();namespace.setUrn("test");namespace.setSourceCode("controlled synthetic tree instantiator fixture");
  var declaration=new org.integratedmodelling.klab.api.lang.kim.impl.KimModelImpl();declaration.setUrn(model.getUrn());namespace.getStatements().add(declaration);
  when(resources.retrieve("test",org.integratedmodelling.klab.api.lang.kim.KimNamespace.class,scope)).thenReturn(namespace);
  when(resources.declareObservable(anyString())).thenReturn(new org.integratedmodelling.klab.api.lang.kim.impl.KimObservableImpl());
  when(reasoner.validateDocument(any(),eq(scope))).thenAnswer(call->{var response=org.integratedmodelling.klab.api.services.reasoner.objects.SemanticValidationResponse.forRequest(call.getArgument(0));
    response.setStatus(org.integratedmodelling.klab.api.services.reasoner.objects.SemanticValidationResponse.Status.COMPLETE);response.setKnowledgeRevision(3);return response;});
  var receipt=ModelValidationGate.validatedPreparableAdmission(model,scope,trees);
  if(receipt==null)throw new AssertionError("Synthetic catalog model was not admitted");
  doReturn(receipt).when(compiler).admissionReceipt(model);
  var runtime=scope.getService(org.integratedmodelling.klab.api.services.RuntimeService.class);
  doReturn(new ResourceSet()).when(runtime).resolveContextualizables(anyList(),eq(scope));
  var graph=compiler.resolve(target,scope);if(graph.isEmpty())throw new AssertionError("No Tier-1 pipeline plan: "+compiler.getNotifications()+" calls "+mockingDetails(compiler).getInvocations()+" scope "+mockingDetails(scope).getInvocations());
  return new Plan(new DataflowCompiler(target,graph,scope).compile().getComputation().getFirst(),model,namespace);
 }
}
