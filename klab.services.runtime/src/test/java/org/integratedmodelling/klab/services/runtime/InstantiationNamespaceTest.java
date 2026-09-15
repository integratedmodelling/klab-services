package org.integratedmodelling.klab.services.runtime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.integratedmodelling.common.runtime.ActuatorImpl;
import org.integratedmodelling.common.data.jackson.JacksonConfiguration;
import org.integratedmodelling.klab.api.knowledge.Contextualization;
import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.provenance.Activity;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.services.resolver.ResolutionConstraint;
import org.integratedmodelling.klab.api.services.runtime.Actuator;

class InstantiationNamespaceTest {
  @Test void transportedProducerBindingsReachEachMembersSubmission() throws Exception {
    var plan = new ActuatorImpl();
    plan.getComputationConstraints().put(0, List.of(
        ResolutionConstraint.of(ResolutionConstraint.Type.ResolutionNamespace,"instantiator.a"),
        ResolutionConstraint.of(ResolutionConstraint.Type.ResolutionProject,"project.a")));
    plan.getComputationConstraints().put(1, List.of(
        ResolutionConstraint.of(ResolutionConstraint.Type.ResolutionNamespace,"instantiator.b")));
    var mapper = JacksonConfiguration.newObjectMapper();
    var restored = mapper.readValue(mapper.writerFor(Actuator.class).writeValueAsString(plan),Actuator.class);
    assertEquals("instantiator.a",restored.getComputationConstraints().get(0).getFirst().payload(String.class).getFirst());
    var collective=mock(Observation.class); var observable=mock(Observable.class);
    when(collective.getObservable()).thenReturn(observable);
    when(observable.getContextualization()).thenReturn(Contextualization.INSTANTIATION);
    var a=mock(Observation.class); var b=mock(Observation.class);
    var outcomes=new ContextualizationScopeImpl(collective,null);
    outcomes.getOutcomes().add(a); outcomes.bindOutcomes(0,restored.getComputationConstraints().get(0));
    outcomes.getOutcomes().add(b); outcomes.bindOutcomes(1,restored.getComputationConstraints().get(1));
    var parent=mock(ContextScope.class);var within=mock(ContextScope.class);
    var memberA=mock(ContextScope.class);var memberB=mock(ContextScope.class);
    when(parent.within(collective)).thenReturn(within);
    when(within.withResolutionConstraints(outcomes.getResolutionConstraints(a).toArray(ResolutionConstraint[]::new))).thenReturn(memberA);
    when(within.withResolutionConstraints(outcomes.getResolutionConstraints(b).toArray(ResolutionConstraint[]::new))).thenReturn(memberB);
    var client=mock(org.integratedmodelling.klab.api.services.RuntimeService.class);
    when(memberA.getService(org.integratedmodelling.klab.api.services.RuntimeService.class)).thenReturn(client);
    when(memberB.getService(org.integratedmodelling.klab.api.services.RuntimeService.class)).thenReturn(client);
    when(client.submit(a,memberA)).thenReturn(CompletableFuture.completedFuture(a));
    when(client.submit(b,memberB)).thenReturn(CompletableFuture.completedFuture(b));
    var runtime=mock(RuntimeService.class,CALLS_REAL_METHODS);
    runtime.submitContextualizationResult(outcomes,parent,Activity.Outcome.SUCCESS);
    verify(client).submit(a,memberA);verify(client).submit(b,memberB);
    verify(parent,never()).withResolutionConstraints(any(ResolutionConstraint[].class));
  }
}
