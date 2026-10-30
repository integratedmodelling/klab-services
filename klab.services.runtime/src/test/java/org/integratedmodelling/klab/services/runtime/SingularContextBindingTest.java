package org.integratedmodelling.klab.services.runtime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.*;
import org.integratedmodelling.common.knowledge.ConceptImpl;
import org.integratedmodelling.common.knowledge.ObservableImpl;
import org.integratedmodelling.common.runtime.ActuatorImpl;
import org.integratedmodelling.common.data.jackson.JacksonConfiguration;
import org.integratedmodelling.klab.api.geometry.Geometry;
import org.integratedmodelling.klab.api.knowledge.*;
import org.integratedmodelling.klab.api.knowledge.Observable;
import org.integratedmodelling.klab.api.knowledge.observation.Observation;
import org.integratedmodelling.klab.api.knowledge.observation.impl.ObservationImpl;
import org.integratedmodelling.klab.api.scope.ContextScope;
import org.integratedmodelling.klab.api.services.Reasoner;
import org.integratedmodelling.klab.services.runtime.RuntimeService;
import org.integratedmodelling.klab.api.services.runtime.Actuator;
import org.integratedmodelling.klab.api.services.resolver.objects.ObservationStrategyImpl;
import org.integratedmodelling.klab.api.digitaltwin.DigitalTwin;
import org.junit.jupiter.api.Test;

class SingularContextBindingTest {
  private final org.integratedmodelling.klab.services.scopes.ServiceContextScope scope = mock(org.integratedmodelling.klab.services.scopes.ServiceContextScope.class);
  private final Reasoner reasoner = mock(Reasoner.class);
  private final Reasoner.Capabilities capabilities = mock(Reasoner.Capabilities.class);
  private final Observable quality = observable("test:Temperature", SemanticType.QUALITY);
  private final ObservationImpl context = context(21);
  private final SingularContextBinding binding;

  SingularContextBindingTest() {
    when(scope.getContextObservation()).thenReturn(context);
    when(scope.getObservation(21)).thenReturn(context);
    when(scope.getService(Reasoner.class)).thenReturn(reasoner);
    when(reasoner.capabilities(scope)).thenReturn(capabilities);
    when(capabilities.getServiceId()).thenReturn("reasoner");
    when(capabilities.getKnowledgeRevision()).thenReturn(3L);
    binding = new SingularContextBinding(21, context.getUrn(), context.getObservable().getUrn(),
        Geometry.forTransport(context.getGeometry()).encode(), quality.getUrn(), "reasoner", 3);
  }
  private static Observable observable(String urn, SemanticType type) {
    var c = new ConceptImpl(); c.setUrn(urn); c.setType(EnumSet.of(type));
    var o = new ObservableImpl(); o.setSemantics(c); o.setUrn(urn);
    o.setDescriptionType(type == SemanticType.QUALITY ? Contextualization.QUANTIFICATION : Contextualization.ACKNOWLEDGEMENT);
    return o;
  }
  private static ObservationImpl context(long id) {
    var o = new ObservationImpl(); o.setId(id); o.setUrn("context:"+id);
    o.setObservable(observable("test:System",SemanticType.SUBJECT));
    o.setGeometry(Geometry.create("T0(1){tend=10,tstart=0,ttype=PHYSICAL}"));
    o.setEventTimestamps(new ArrayList<>(List.of(0L))); return o;
  }
  @Test void usesActualAccessibleContextWithoutChangingScope() {
    var actual = context(21); when(scope.getObservation(21)).thenReturn(actual);
    assertSame(actual, binding.require(scope,quality));
    verify(scope,never()).within(any()); verify(scope,never()).observation(any());
  }
  @Test void twoContextsCannotShareAChild() {
    when(scope.getContextObservation()).thenReturn(context(22));
    assertThrows(IllegalStateException.class,()->binding.require(scope,quality));
    verify(scope,never()).getObservation(anyLong());
  }
  @Test void inaccessibleContextFails() { when(scope.getObservation(21)).thenReturn(null);
    assertThrows(IllegalStateException.class,()->binding.require(scope,quality)); }
  @Test void identityChangesFail() { var different=context(21); different.setUrn("other");
    when(scope.getObservation(21)).thenReturn(different);
    assertThrows(IllegalStateException.class,()->binding.require(scope,quality)); }
  @Test void supportChangesFail() { context.setGeometry(Geometry.UNIVERSAL);
    assertThrows(IllegalStateException.class,()->binding.require(scope,quality)); }
  @Test void unresolvedOrEmptyContextFails() { context.setEventTimestamps(List.of());
    assertThrows(IllegalStateException.class,()->binding.require(scope,quality));
    context.setEventTimestamps(List.of(0L)); context.setEmpty(true);
    assertThrows(IllegalStateException.class,()->binding.require(scope,quality)); }
  @Test void collectiveAndNonSubstantialContextsFail() {
    ((ConceptImpl)context.getObservable().asConcept()).setCollective(true);
    assertThrows(IllegalStateException.class,()->binding.require(scope,quality));
    context.setObservable(observable("test:Event",SemanticType.EVENT));
    assertThrows(IllegalStateException.class,()->binding.require(scope,quality)); }
  @Test void revisionOrServiceChangeFails() { when(capabilities.getKnowledgeRevision()).thenReturn(4L);
    assertThrows(IllegalStateException.class,()->binding.require(scope,quality));
    when(capabilities.getKnowledgeRevision()).thenReturn(3L); when(capabilities.getServiceId()).thenReturn("other");
    assertThrows(IllegalStateException.class,()->binding.require(scope,quality)); }
  @Test void targetRetypingFails() {
    assertThrows(IllegalStateException.class,()->binding.require(scope,observable("test:Other",SemanticType.QUALITY))); }
  @Test void cancellationAndFailedAttemptFailBeforeLookup() {
    when(scope.isInterrupted()).thenReturn(true);
    assertThrows(IllegalStateException.class,()->binding.require(scope,quality));
    when(scope.isInterrupted()).thenReturn(false);
    var transaction=mock(DigitalTwin.Transaction.class); when(scope.getCurrentTransaction()).thenReturn(transaction);
    when(transaction.isFailed()).thenReturn(true);
    assertThrows(IllegalStateException.class,()->binding.require(scope,quality));
    verify(scope,never()).getObservation(anyLong());
  }
  @Test void interfaceRootJsonPreservesBindingOnStrategyAndActuator() throws Exception {
    var mapper=JacksonConfiguration.newObjectMapper();
    var strategy=new ObservationStrategyImpl(); strategy.setContextBinding(binding);
    var restored=mapper.readValue(mapper.writerFor(ObservationStrategy.class).writeValueAsString(strategy),ObservationStrategy.class);
    assertEquals(binding,restored.getContextBinding());
    var actuator=new ActuatorImpl(); actuator.setContextBinding(restored.getContextBinding());
    var copy=mapper.readValue(mapper.writerFor(Actuator.class).writeValueAsString(actuator),Actuator.class);
    assertEquals(binding,copy.getContextBinding()); assertSame(context,copy.getContextBinding().require(scope,quality));
  }
  private ActuatorImpl boundActuator() {
    var target=new ObservationImpl(); target.setId(-1); target.setUrn("request");target.setObservable(quality);
    target.setGeometry(context.getGeometry());target.setParentId(21);
    var actuator=new ActuatorImpl();actuator.setId(-1);actuator.setObservation(target);
    actuator.setOperationObservable(quality);actuator.setActuatorType(Actuator.Type.OBSERVE);
    actuator.setRequestedSupport(context.getGeometry());actuator.setContextBinding(binding);return actuator;
  }
  @Test void runtimeValidatesBeforeAllocatingObservations() {
    var actuator=boundActuator();var runtime=mock(RuntimeService.class);
    var compiled=new CompiledDataflow(runtime,actuator.getObservation(),scope);
    when(scope.getObservation(21)).thenReturn(null);
    assertThrows(IllegalStateException.class,()->compiled.compile(actuator));
    verify(scope,never()).observation(any());
  }
  @Test void runtimeStoreRejectsStaleBindingBeforeStagingLinks() throws Exception {
    var actuator=boundActuator();var runtime=mock(RuntimeService.class);
    var compiled=new CompiledDataflow(runtime,actuator.getObservation(),scope);
    var field=CompiledDataflow.class.getDeclaredField("operations");field.setAccessible(true);
    @SuppressWarnings("unchecked") var operations=(Map<Actuator,CompiledDataflow.ExecutorImpl>)field.get(compiled);
    operations.put(actuator,null);
    var transaction=mock(org.integratedmodelling.klab.services.runtime.digitaltwin.DigitalTwinImpl.TransactionImpl.class);
    when(capabilities.getKnowledgeRevision()).thenReturn(4L);
    assertThrows(IllegalStateException.class,()->compiled.store(transaction));verifyNoInteractions(transaction);
  }
  @Test void runtimeExecutionRejectsDifferentActualContextAndWrongParent() {
    var actuator=boundActuator();var runtime=mock(RuntimeService.class);
    var compiled=new CompiledDataflow(runtime,actuator.getObservation(),scope);
    var executor=compiled.new ExecutorImpl(actuator);
    var another=mock(org.integratedmodelling.klab.services.scopes.ServiceContextScope.class);
    when(another.getContextObservation()).thenReturn(context(22));
    assertFalse(executor.run(context.getGeometry(),null,another));
    ((ObservationImpl)actuator.getObservation()).setParentId(22);
    assertFalse(executor.run(context.getGeometry(),initialization(),scope));
    verify(scope,never()).executing(any(org.integratedmodelling.klab.api.provenance.Activity.class),any());
  }

  private CompiledDataflow.ExecutorImpl executionFixture() {
    var actuator=boundActuator();var runtime=mock(RuntimeService.class);
    when(scope.getService(RuntimeService.class)).thenReturn(runtime);
    when(scope.executing(any(org.integratedmodelling.klab.api.provenance.Activity.class),eq(actuator.getObservation())))
        .thenReturn(scope);
    when(scope.commit()).thenReturn(1L);
    return new CompiledDataflow(runtime,actuator.getObservation(),scope).new ExecutorImpl(actuator);
  }
  @Test void runtimeExecutionCompletesInOriginalNativeContext() {
    var executor=executionFixture();assertTrue(executor.run(context.getGeometry(),initialization(),scope));
    verify(scope.getService(RuntimeService.class))
        .submitContextualizationResult(any(),eq(scope),eq(org.integratedmodelling.klab.api.provenance.Activity.Outcome.SUCCESS));
    verify(scope,never()).within(any());verify(scope).commit();
  }
  @Test void runtimeCancellationBeforeCommitPublishesOnlyFailure() {
    var executor=executionFixture();when(scope.isInterrupted()).thenReturn(false,false,true);
    assertFalse(executor.run(context.getGeometry(),initialization(),scope));
    verify(scope.getService(RuntimeService.class),never())
        .submitContextualizationResult(any(),any(),eq(org.integratedmodelling.klab.api.provenance.Activity.Outcome.SUCCESS));
    verify(scope,never()).commit();
  }
  @Test void runtimeChildCommitFailurePropagates() {
    var executor=executionFixture();when(scope.commit()).thenReturn(-1L);
    assertFalse(executor.run(context.getGeometry(),initialization(),scope));verify(scope).fail(any(Throwable.class));
  }

  private static org.integratedmodelling.klab.api.digitaltwin.Scheduler.Event initialization() {
    var event=mock(org.integratedmodelling.klab.api.digitaltwin.Scheduler.Event.class);
    when(event.getType()).thenReturn(org.integratedmodelling.klab.api.digitaltwin.Scheduler.Event.Type.INITIALIZATION);return event;
  }
  @Test void runtimePartialInvocationIsNotFullSupportSuccess() {
    var executor=executionFixture();assertFalse(executor.run(Geometry.UNIVERSAL,initialization(),scope));
    verify(scope,never()).executing(any(org.integratedmodelling.klab.api.provenance.Activity.class),any());
  }

}
