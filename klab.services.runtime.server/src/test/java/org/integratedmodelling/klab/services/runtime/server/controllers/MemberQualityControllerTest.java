package org.integratedmodelling.klab.services.runtime.server.controllers;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.integratedmodelling.common.data.jackson.JacksonConfiguration;
import org.integratedmodelling.klab.api.knowledge.SingularContextBinding;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.services.resolver.ResolutionConstraint;
import org.integratedmodelling.klab.api.services.runtime.objects.*;
import org.integratedmodelling.klab.services.JobManager;
import org.integratedmodelling.klab.services.application.security.EngineAuthorization;
import org.integratedmodelling.klab.services.runtime.RuntimeService;
import org.integratedmodelling.klab.services.runtime.server.RuntimeServer;
import org.integratedmodelling.klab.services.scopes.ServiceContextScope;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;
import org.springframework.web.server.ResponseStatusException;

class MemberQualityControllerTest {
  RuntimeServerController controller; RuntimeService runtime;
  ServiceContextScope scope,selected; EngineAuthorization authorization; JobManager jobs;
  CompletableFuture<MemberQualityCollection> future; MemberQualityRequest request;
  @BeforeEach void setup() throws Exception {
    runtime=mock(RuntimeService.class);var server=mock(RuntimeServer.class);when(server.klabService()).thenReturn(runtime);
    controller=new RuntimeServerController();var field=RuntimeServerController.class.getDeclaredField("runtimeService");
    field.setAccessible(true);field.set(controller,server);
    scope=mock(ServiceContextScope.class,RETURNS_DEEP_STUBS);selected=mock(ServiceContextScope.class);
    jobs=new JobManager();when(scope.getJobManager()).thenReturn(jobs);
    when(scope.withResolutionConstraints(any(ResolutionConstraint[].class))).thenReturn(selected);
    when(selected.withResolutionConstraints(any(ResolutionConstraint[].class))).thenReturn(selected);
    authorization=mock(EngineAuthorization.class);when(authorization.getScope(ContextScope.class)).thenReturn(scope);
    future=new CompletableFuture<>();
    when(runtime.resolveMemberQualities(any(),eq(selected))).thenReturn(future);
    request=new MemberQualityRequest(new SingularContextBinding(10,"region:10","test:Region","t0(1)",
        "test:Gradient","reasoner",7),"each test:Stream","test:Gradient","tester",
        List.of(ResolutionConstraint.of(ResolutionConstraint.Type.ObserverUrn,"observer:42")));
  }
  @Test void anonymousPrincipalCannotSubmit() {
    var failure=assertThrows(ResponseStatusException.class,()->controller.memberQualities(request,()->"anonymous"));
    assertEquals(401,failure.getStatusCode().value());verifyNoInteractions(runtime);
  }
  @Test void contextAuthorizationIsRequired() {
    when(authorization.getScope(ContextScope.class)).thenReturn(null);
    assertThrows(IllegalArgumentException.class,()->controller.memberQualities(request,authorization));
    verifyNoInteractions(runtime);
  }
  @Test void requestConstraintsAndProvenanceStayOnDerivedSubmissionScope() {
    var id=controller.memberQualities(request,authorization);assertTrue(id>0);
    var constraints=ArgumentCaptor.forClass(ResolutionConstraint[].class);
    verify(scope).withResolutionConstraints(constraints.capture());
    assertEquals(ResolutionConstraint.Type.ObserverUrn,constraints.getValue()[0].getType());
    assertEquals(List.of("observer:42"),constraints.getValue()[0].payload(String.class));
    verify(scope.getDigitalTwin().getKnowledgeGraph()).requireAgent("tester");
    verify(selected).withResolutionConstraints(any(ResolutionConstraint[].class));
    verify(runtime).resolveMemberQualities(request,selected);
    verify(scope,never()).withObserver(any());
  }
  @Test void jobCancellationInvokesTheRuntimeTaskCancellation() {
    var id=controller.memberQualities(request,authorization);assertTrue(jobs.cancel(id));assertTrue(future.isCancelled());
    assertEquals(org.integratedmodelling.klab.api.scope.Scope.Status.INTERRUPTED,jobs.status(id).getStatus());
  }
  @Test void jobResultPreservesInterfaceCollectionTransport() throws Throwable {
    var id=controller.memberQualities(request,authorization);var result=new MemberQualityCollectionImpl();result.setRequest(request);
    future.complete(result);
    var copy=JacksonConfiguration.newObjectMapper().readValue(jobs.getResult(id),MemberQualityCollection.class);
    assertEquals(request.quality(),copy.getRequest().quality());assertTrue(copy.getEntries().isEmpty());
  }
  @Test void installedInputInspectionUsesAuthorizedUserScopeWithoutKnowledgeGraphReads() {
    var user=mock(org.integratedmodelling.klab.api.scope.UserScope.class);
    when(authorization.getScope(org.integratedmodelling.klab.api.scope.Scope.class)).thenReturn(user);
    var call=new org.integratedmodelling.common.lang.ServiceCallImpl("test.mapper");
    var inspection=MemberQualityConsumerInspection.known(java.util.Set.of("gradients"));
    when(runtime.inspectMemberQualityInputs(call,user)).thenReturn(inspection);
    assertEquals(inspection,controller.memberQualityInputs(call,authorization));
    verify(runtime).inspectMemberQualityInputs(call,user);verifyNoInteractions(user);
    assertThrows(ResponseStatusException.class,()->controller.memberQualityInputs(call,()->"anonymous"));
    when(authorization.getScope(org.integratedmodelling.klab.api.scope.Scope.class)).thenReturn(null);
    assertThrows(IllegalArgumentException.class,()->controller.memberQualityInputs(call,authorization));
  }
}
