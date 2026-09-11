package org.integratedmodelling.klab.services.runtime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import org.integratedmodelling.common.data.jackson.JacksonConfiguration;
import org.integratedmodelling.common.knowledge.ObservableImpl;
import org.integratedmodelling.common.runtime.ActuatorImpl;
import org.integratedmodelling.klab.api.data.Metadata;
import org.integratedmodelling.klab.api.digitaltwin.DigitalTwin;
import org.integratedmodelling.klab.api.documentation.*;
import org.integratedmodelling.klab.api.knowledge.Contextualization;
import org.integratedmodelling.klab.api.knowledge.observation.impl.ObservationImpl;
import org.integratedmodelling.klab.api.provenance.Activity;
import org.integratedmodelling.klab.api.services.runtime.*;
import org.integratedmodelling.klab.services.scopes.ServiceContextScope;
import org.junit.jupiter.api.Test;

class ContextualizationDiagnosticsTest {
  @Test void planIsPresentAtCreationAndSurvivesSuccessAndFailureMessages() throws Exception {
    for (boolean success : new boolean[]{true, false}) {
      var observable = new ObservableImpl(); observable.setDescriptionType(Contextualization.MEASURE);
      var observation = new ObservationImpl(); observation.setObservable(observable); observation.setId(42);
      var actuator = new ActuatorImpl(); actuator.setName("measure"); actuator.setObservation(observation); actuator.setId(42); actuator.setActuatorType(Actuator.Type.OBSERVE);
      var plan = new DataflowFlowChartAdapter(actuator).adapt(actuator);
      var activity = CompiledDataflow.createContextualizationActivity(plan, observation, Activity.of(Activity.Type.RESOLUTION));
      assertSame(plan, activity.getMetadata().get(Metadata.IM_DATAFLOW_GRAPH));
      var mapper = JacksonConfiguration.newObjectMapper();
      var started = Message.create("client", Message.MessageClass.DigitalTwin, Message.MessageType.ActivityStarted, activity);
      var before = mapper.readValue(mapper.writerFor(Message.class).writeValueAsString(started), Message.class).getPayload(Activity.class);
      assertInstanceOf(FlowChart.class, before.getMetadata().get(Metadata.IM_DATAFLOW_GRAPH)).validate();
      var scope = mock(ServiceContextScope.class); when(scope.getActivity()).thenReturn(activity);
      var transaction = mock(DigitalTwin.Transaction.class); when(transaction.commit()).thenReturn(1L);
      var field = ServiceContextScope.class.getDeclaredField("currentTransaction"); field.setAccessible(true); field.set(scope, transaction);
      if (success) { when(scope.commit()).thenCallRealMethod(); scope.commit(); }
      else { doCallRealMethod().when(scope).fail(any(Throwable.class)); scope.fail(new IllegalStateException("failed")); }
      verify(scope).send(Message.MessageClass.DigitalTwin, Message.MessageType.ActivityFinished, activity);
      var finished = Message.create("client", Message.MessageClass.DigitalTwin, Message.MessageType.ActivityFinished, activity);
      var after = mapper.readValue(mapper.writerFor(Message.class).writeValueAsString(finished), Message.class).getPayload(Activity.class);
      assertEquals(success ? Activity.Outcome.SUCCESS : Activity.Outcome.INTERNAL_FAILURE, after.getOutcome());
      assertEquals(mapper.valueToTree(before.getMetadata().get(Metadata.IM_DATAFLOW_GRAPH)), mapper.valueToTree(after.getMetadata().get(Metadata.IM_DATAFLOW_GRAPH)));
    }
  }
}
