package org.integratedmodelling.klab.services.resolver;

import static org.mockito.Mockito.*;
import java.util.*;
import org.integratedmodelling.common.knowledge.*;
import org.integratedmodelling.common.lang.*;
import org.integratedmodelling.klab.api.collections.Parameters;
import org.integratedmodelling.klab.api.knowledge.*;
import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.lang.kim.*;
import org.integratedmodelling.klab.api.lang.kim.impl.*;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.services.*;
import org.integratedmodelling.klab.api.services.reasoner.objects.*;
import org.integratedmodelling.klab.api.services.resolver.*;
import org.integratedmodelling.klab.api.services.resources.ResourceSet;
import org.integratedmodelling.klab.api.services.runtime.*;
import org.integratedmodelling.klab.runtime.language.ModelValidationGate;

/** Actual Resolver/DataflowCompiler; explicit controlled saved-source/catalog and validation responses. */
public final class MemberQualityParentPlanningFixture {
 public record Plan(Actuator actuator,Model model,KimNamespace namespace) {}
 public static Plan plan(Observation target,Observable members,Observable quality,ContextScope scope,Reasoner reasoner) {
  var scale=GeometryRepository.INSTANCE.scale(target.getGeometry());
  var compiler=spy(new ResolutionCompiler(mock(ResolverService.class)));
  when(scope.withResolutionConstraints(any(ResolutionConstraint[].class))).thenReturn(scope);
  when(scope.within(nullable(Observation.class))).thenReturn(scope);
  var parameters=Parameters.<String>create();parameters.put("__RESOLUTION_GRAPH__",ResolutionGraph.create(scope));when(scope.getData()).thenReturn(parameters);
  doReturn(List.of(CollectiveReductionPlanningTest.flat(target.getObservable()))).when(reasoner).computeObservationStrategies(eq(target),eq(scope));
  doReturn(List.of()).when(reasoner).computeObservationStrategies(argThat(o->o!=null&&o.getObservable()==quality),eq(scope));
  doReturn(new ResolutionCompiler.QueryMatch(null,null,scale,null,Coverage.create(scale,0))).when(compiler).query(any(),any(),eq(scope));
  var model=new ModelImpl();model.setUrn("test:explicitMaximum");model.setNamespace("test");model.setCoverage(scale);model.setObservables(List.of(target.getObservable()));model.setDependencies(List.of(quality));
  var code=new ContextualizableImpl();code.setServiceCall(new ServiceCallImpl("test.explicit.maximum"));model.setComputation(List.of(code));
  doReturn(List.of(model)).when(compiler).queryModels(eq(target.getObservable()),any(),eq(scope),any());
  doReturn(List.of()).when(compiler).queryModels(eq(quality),any(),eq(scope),any());
  var builder=mock(Observation.Builder.class,RETURNS_SELF);when(scope.observation(quality)).thenReturn(builder);
  var child=new org.integratedmodelling.klab.api.knowledge.observation.impl.ObservationImpl();child.setId(-52);child.setUrn("fixture:native-request");child.setObservable(quality);child.setGeometry(target.getGeometry());when(builder.register()).thenReturn(child);
  var resources=scope.getService(ResourcesService.class);
  var namespace=new KimNamespaceImpl();namespace.setUrn("test");namespace.setSourceCode("namespace test version 1.0; model test:MaximumGradient of test:Region observing test:StreamGradient of test:Stream named gradients using test.explicit.maximum();");
  var declaration=new KimModelImpl();declaration.setUrn(model.getUrn());declaration.setNamespace("test");namespace.getStatements().add(declaration);
  var source=new KimObservableImpl();var syntax=new KimConceptImpl();syntax.setName("test:StreamGradient");var nativeBearer=new KimConceptImpl();nativeBearer.setName("test:Stream");syntax.setInherent(nativeBearer);source.setSemantics(syntax);source.setFormalName("gradients");source.setUrn(quality.getUrn());declaration.getDependencies().add(source);
  when(resources.retrieve(eq("test"),eq(KimNamespace.class),any())).thenReturn(namespace);when(resources.declareObservable(anyString())).thenReturn(new KimObservableImpl());
  var pending=new SemanticValidationResponse.PendingMemberQualityDependency(model.getUrn(),0,source.getUrn(),"gradients","test:Stream",quality.getUrn());
  doAnswer(call->{var response=SemanticValidationResponse.forRequest(call.getArgument(0));response.setStatus(SemanticValidationResponse.Status.COMPLETE);response.setKnowledgeRevision(3);response.getPendingMemberQualityDependencies().add(pending);return response;}).when(reasoner).validateDocument(any(),any());
  var runtime=scope.getService(org.integratedmodelling.klab.api.services.RuntimeService.class);
  doReturn(new ResourceSet()).when(runtime).resolveContextualizables(anyList(),eq(scope));
  var receipt=ModelValidationGate.validatedPreparableAdmission(model,scope,target.getObservable());if(receipt==null)throw new AssertionError("No source-backed native input admission");
  doReturn(receipt).when(compiler).admissionReceipt(model);
  var graph=compiler.resolve(target,scope);if(graph.isEmpty())throw new AssertionError("No explicit typed parent plan: "+compiler.getNotifications());
  var plan=new DataflowCompiler(target,graph,scope).compile().getComputation().getFirst();
  if(plan.getMemberQualityInputs().size()!=1)throw new AssertionError("No typed source-bound port");
  return new Plan(plan,model,namespace);
 }
}
