package org.integratedmodelling.klab.services.runtime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import org.integratedmodelling.common.data.jackson.JacksonConfiguration;
import org.integratedmodelling.common.runtime.DataflowImpl;
import org.integratedmodelling.klab.api.data.Metadata;
import org.integratedmodelling.klab.api.documentation.FlowChart;
import org.integratedmodelling.klab.api.provenance.Activity;
import org.integratedmodelling.klab.api.services.runtime.Dataflow;
import org.integratedmodelling.klab.api.services.runtime.Message;
import org.integratedmodelling.klab.api.digitaltwin.DigitalTwin;
import org.integratedmodelling.klab.services.scopes.ServiceContextScope;
import org.junit.jupiter.api.Test;

class ResolutionDiagnosticsTest {
  @Test void flowChartSurvivesDataflowCommitAndActivityMessageTransport() throws Exception {
    var chart = FlowChart.builder("resolution").metadata("schema", "klab.resolution-graph.v1")
        .root(root -> root.node("a", n -> n.label("requested"))
            .node("b", n -> n.label("model")).link("e", "a", "b")).build();
    Dataflow dataflow = new DataflowImpl();
    dataflow.getMetadata().put(Metadata.IM_RESOLUTION_GRAPH, chart);
    var mapper = JacksonConfiguration.newObjectMapper();
    dataflow = mapper.readValue(mapper.writerFor(Dataflow.class).writeValueAsString(dataflow), Dataflow.class);
    var restored = assertInstanceOf(FlowChart.class, dataflow.getMetadata().get(Metadata.IM_RESOLUTION_GRAPH));
    restored.validate();
    assertEquals("requested", restored.getRoot().getChildren().getFirst().getLabels().getFirst().getText());
    var activity = Activity.of(Activity.Type.RESOLUTION);
    RuntimeService.attachResolutionDiagnostics(dataflow, activity);
    var scope = mock(ServiceContextScope.class);
    when(scope.getActivity()).thenReturn(activity);
    var transaction = mock(DigitalTwin.Transaction.class);
    when(transaction.commit()).thenReturn(123L);
    var field = ServiceContextScope.class.getDeclaredField("currentTransaction"); field.setAccessible(true); field.set(scope, transaction);
    when(scope.commit()).thenCallRealMethod();
    assertEquals(123L, scope.commit());
    assertEquals(Activity.Outcome.SUCCESS, activity.getOutcome());
    verify(scope).send(Message.MessageClass.DigitalTwin, Message.MessageType.ActivityFinished, activity);
    verify(transaction, never()).getGraph();
    var message = Message.create("client", Message.MessageClass.DigitalTwin, Message.MessageType.ActivityFinished, activity);
    var copy = mapper.readValue(mapper.writerFor(Message.class).writeValueAsString(message), Message.class);
    var received = copy.getPayload(Activity.class);
    var receivedChart = assertInstanceOf(FlowChart.class, received.getMetadata().get(Metadata.IM_RESOLUTION_GRAPH));
    receivedChart.validate();
    assertEquals("b", receivedChart.getRoot().getEdges().getFirst().getTargets().getFirst());
  }
  @Test void absentDiagnosticsAndOtherMetadataDoNotChangeActivity() {
    var dataflow = Dataflow.empty(); dataflow.getMetadata().put("other", "not forwarded");
    var activity = Activity.of(Activity.Type.RESOLUTION);
    RuntimeService.attachResolutionDiagnostics(dataflow, activity);
    assertTrue(activity.getMetadata().isEmpty());
  }
}
