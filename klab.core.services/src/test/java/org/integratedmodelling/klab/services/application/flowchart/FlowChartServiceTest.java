package org.integratedmodelling.klab.services.application.flowchart;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import javax.imageio.ImageIO;
import org.integratedmodelling.klab.api.documentation.FlowChart;
import org.integratedmodelling.klab.api.services.resources.workflow.impl.WorkflowImpl;
import org.junit.jupiter.api.Test;

class FlowChartServiceTest {
  private final FlowChartService service = new FlowChartService();

  static WorkflowImpl workflow() {
    var workflow = new WorkflowImpl(); workflow.setId("review"); workflow.setName("Resource review");
    workflow.setDescription("Review, revise and publish a resource.");
    for (String id : List.of("Draft", "Review", "Published", "Archived")) {
      var state = new WorkflowImpl.StateSchemaImpl(); state.setId(id);
      state.setDescription("Work on " + id); workflow.getStates().put(id, state);
    }
    for (var spec : List.of(List.of("Create", "INIT", "Draft"), List.of("Submit", "Draft", "Review"),
        List.of("Revise", "Review", "Draft"), List.of("Publish", "Review", "Published"))) {
      var transition = new WorkflowImpl.TransitionSchemaImpl(); transition.setId(spec.get(0));
      transition.setSourceStates(Set.of(spec.get(1))); transition.setTargetState(spec.get(2));
      workflow.getTransitions().put(spec.get(0), transition);
    }
    return workflow;
  }

  @Test
  void layoutsWorkflowWithoutMutationAndProducesDecodablePng() throws Exception {
    var original = service.adapt(workflow());
    var mapper = new ObjectMapper();
    original.getRoot().getMetadata().put("nullable", null);
    var before = mapper.writeValueAsString(original);
    var chart = service.layout(original);
    assertEquals(before, mapper.writeValueAsString(original));
    assertTrue(chart.getRoot().getWidth() > 100);
    assertEquals(5, chart.getRoot().getChildren().size());
    assertTrue(chart.getRoot().getEdges().stream().allMatch(edge -> !edge.getSections().isEmpty()));
    assertEquals("review", chart.getRoot().getMetadata().get("workflowId"));
    assertTrue(chart.getRoot().getMetadata().containsKey("nullable"));
    byte[] png = service.png(original);
    var image = ImageIO.read(new ByteArrayInputStream(png));
    assertNotNull(image); assertTrue(image.getWidth() > 100); assertTrue(image.getHeight() > 60);
    assertTrue(image.getWidth() <= 4096 && image.getHeight() <= 4096);
    int nonWhite = 0;
    for (int y = 0; y < image.getHeight(); y++)
      for (int x = 0; x < image.getWidth(); x++) if (image.getRGB(x, y) != -1) nonWhite++;
    assertTrue(nonWhite > 1000);
    var output = Path.of("target", "flowchart-test"); Files.createDirectories(output);
    Files.write(output.resolve("workflow.png"), png);
    mapper.writeValue(output.resolve("workflow.json").toFile(), chart);
    mapper.writeValue(output.resolve("workflow-raw.json").toFile(), original);
  }

  @Test
  void laysOutRootPortsNestedAndDisconnectedNodes() {
    var source = FlowChart.builder("root").root(root -> root.label("Isolated component")
        .port("in", FlowChart.Role.INPUT).port("out", FlowChart.Role.OUTPUT)
        .node("nested", n -> n.label("Nested").port("nested-in", FlowChart.Role.INPUT)
            .port("nested-out", FlowChart.Role.OUTPUT))
        .link("input", "in", "nested-in").link("output", "nested-out", "out")).build();
    var chart = service.layout(source);
    chart.validate(); assertEquals(2, chart.getRoot().getPorts().size());
    assertNotNull(service.image(source));
    assertNotNull(service.image(FlowChart.builder("alone").root(root -> root
        .port("alone-in", FlowChart.Role.INPUT).port("alone-out", FlowChart.Role.OUTPUT)).build()));
  }

  @Test
  void supportsRegisteredAdaptersAndRejectsInvalidReferences() {
    service.register(String.class, id -> FlowChart.builder(id).build());
    assertEquals("custom", service.layout("custom").getRoot().getId());
    assertThrows(IllegalArgumentException.class, () -> service.layout(new Object()));
    var bad = FlowChart.builder("root").build();
    var edge = new FlowChart.Link(); edge.setId("bad"); edge.getSources().add("missing");
    edge.getTargets().add("root"); bad.getRoot().getEdges().add(edge);
    assertThrows(IllegalArgumentException.class, () -> service.layout(bad));
  }
}
