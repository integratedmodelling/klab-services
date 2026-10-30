package org.integratedmodelling.klab.services.resolver;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.*;
import org.integratedmodelling.common.knowledge.*;
import org.integratedmodelling.common.lang.*;
import org.integratedmodelling.klab.api.knowledge.*;
import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.api.lang.kim.*;
import org.integratedmodelling.klab.api.lang.kim.impl.*;
import org.integratedmodelling.klab.api.services.*;
import org.integratedmodelling.klab.api.services.reasoner.objects.*;
import org.integratedmodelling.klab.api.services.runtime.*;
import org.integratedmodelling.klab.runtime.language.ModelValidationGate;
import org.junit.jupiter.api.*;

/** Real admission/resolver/compiler transport, controlled semantic authority and catalog boundaries. */
class MemberQualityDependencyPlanningTest {
 @BeforeAll static void configure(){CollectiveReductionPlanningTest.configure();}
 final CollectiveReductionPlanningTest f=new CollectiveReductionPlanningTest();
 final Observable quality=CollectiveReductionPlanningTest.observable("test:Gradient of test:Tree",SemanticType.QUALITY);
 final ResourcesService resources=mock(ResourcesService.class);
 final KimNamespaceImpl namespace=new KimNamespaceImpl();
 final KimObservableImpl sourceInput=new KimObservableImpl();
 final org.integratedmodelling.klab.api.services.RuntimeService runtime;
 final SemanticValidationResponse.PendingMemberQualityDependency pending;
 MemberQualityDependencyPlanningTest() {
  ((ObservableImpl)quality).setStatedName("gradients");
  when(f.model.getObservables()).thenReturn(List.of(f.target));
  when(f.model.getDependencies()).thenReturn(List.of(quality));
  var code=new ContextualizableImpl();code.setServiceCall(new ServiceCallImpl("fixture.collection.consumer"));when(f.model.getComputation()).thenReturn(List.of(code));
  doReturn(List.of(CollectiveReductionPlanningTest.flat(f.target))).when(f.reasoner).computeObservationStrategies(f.request,f.scope);
  doReturn(List.of(f.model)).when(f.compiler).queryModels(eq(f.target),any(),eq(f.scope),any());
  doReturn(List.of()).when(f.compiler).queryModels(eq(quality),any(),eq(f.scope),any());
  when(f.reasoner.computeObservationStrategies(argThat(o->o!=null && o.getObservable()==quality),eq(f.scope))).thenReturn(List.of());
  doReturn(new ResolutionCompiler.QueryMatch(null,null,f.scale,null,org.integratedmodelling.klab.api.services.resolver.Coverage.create(f.scale,0)))
      .when(f.compiler).query(any(),any(),eq(f.scope));
  var builder=mock(org.integratedmodelling.klab.api.knowledge.observation.Observation.Builder.class,RETURNS_SELF);
  when(f.scope.observation(quality)).thenReturn(builder);when(builder.register()).thenReturn(f.observation(-52,"quality",quality));
  when(f.scope.getService(ResourcesService.class)).thenReturn(resources);
  namespace.setUrn("test");namespace.setSourceCode("model test:Count observing test:Gradient of test:Tree named gradients using fixture.collection.consumer;");
  var sourceModel=new KimModelImpl();sourceModel.setUrn(f.model.getUrn());sourceModel.setNamespace("test");namespace.getStatements().add(sourceModel);
  var syntax=new KimConceptImpl();syntax.setName("test:Gradient");var bearer=new KimConceptImpl();bearer.setName("test:Tree");syntax.setInherent(bearer);
  sourceInput.setSemantics(syntax);sourceInput.setFormalName("gradients");sourceInput.setUrn("authored Gradient of Tree");sourceModel.getDependencies().add(sourceInput);
  when(resources.retrieve(eq("test"),eq(KimNamespace.class),any())).thenReturn(namespace);
  when(resources.declareObservable(anyString())).thenReturn(new KimObservableImpl());
  pending=new SemanticValidationResponse.PendingMemberQualityDependency(f.model.getUrn(),0,sourceInput.getUrn(),"gradients",f.tree.getUrn(),quality.getUrn());
  when(f.reasoner.validateDocument(any(),eq(f.scope))).thenAnswer(call->{var request=call.getArgument(0,SemanticValidationRequest.class);
   var response=SemanticValidationResponse.forRequest(request);response.setStatus(SemanticValidationResponse.Status.COMPLETE);response.setKnowledgeRevision(3);
   response.getPendingMemberQualityDependencies().add(pending);return response;});
  when(f.reasoner.resolveConcept(f.tree.getUrn())).thenReturn(f.tree);
  when(f.reasoner.resolveObservable(f.tree.collective().getUrn())).thenReturn(f.trees);
  runtime=f.scope.getService(org.integratedmodelling.klab.api.services.RuntimeService.class);
  when(runtime.supportsMemberQualityInput(any(),eq("gradients"),eq(f.scope))).thenReturn(true);
  when(runtime.inspectMemberQualityInputs(any(),any())).thenReturn(new org.integratedmodelling.klab.api.services.runtime.objects.MemberQualityConsumerInspection(true,Set.of("gradients")));
  var receipt=ModelValidationGate.validatedPreparableAdmission(f.model,f.scope,f.target);assertNotNull(receipt);
  doReturn(receipt).when(f.compiler).admissionReceipt(f.model);
 }
 @Test void savedExplicitDependencyBindsNativeCollectionPortAndSurvivesTransport() {
  var graph=f.compiler.resolve(f.request,f.scope);assertFalse(graph.isEmpty(),()->mockingDetails(f.scope).getInvocations().toString());
  var actuator=new DataflowCompiler(f.request,graph,f.scope).compile().getComputation().getFirst();
  assertEquals(Set.of("gradients"),actuator.getMemberQualityInputs().keySet());
  var port=actuator.getMemberQualityInputs().get("gradients");assertEquals(quality.getUrn(),port.request().quality());
  assertEquals(f.trees.getUrn(),port.request().members());assertEquals(f.context.getId(),port.request().container().contextId());
  assertTrue(actuator.getChildren().isEmpty(),"Collection is not a fake observation child");
  verify(f.compiler,never()).queryModels(eq(quality),any(),any(),any());verify(f.scope,never()).observation(quality);
  var transported=org.integratedmodelling.klab.utilities.Utils.Json.parseObject(org.integratedmodelling.klab.utilities.Utils.Json.asString(actuator),Actuator.class);
  assertEquals(port,transported.getMemberQualityInputs().get("gradients"));
 }
 @Test void missingTypedConsumerCannotBindCollection() {
  when(runtime.supportsMemberQualityInput(any(),anyString(),any())).thenReturn(false);
  assertTrue(f.compiler.resolve(f.request,f.scope).isEmpty());
 }
 @Test void changedSavedSourceOrLostExplicitClauseFailsClosed() {
  namespace.setSourceCode("changed same-revision source");assertTrue(f.compiler.resolve(f.request,f.scope).isEmpty());
 }
 @Test void implicitSourceCannotBecomeExplicitByNormalizedObservable() {
  ((KimConceptImpl)sourceInput.getSemantics()).setInherent(null);assertTrue(f.compiler.resolve(f.request,f.scope).isEmpty());
 }
}
