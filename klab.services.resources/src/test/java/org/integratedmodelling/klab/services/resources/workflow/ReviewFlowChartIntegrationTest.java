package org.integratedmodelling.klab.services.resources.workflow;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import javax.imageio.ImageIO;
import org.integratedmodelling.klab.api.documentation.FlowChart;
import org.integratedmodelling.klab.api.exceptions.KlabResourceAccessException;
import org.integratedmodelling.klab.api.knowledge.KlabAsset.KnowledgeClass;
import org.integratedmodelling.klab.api.scope.UserScope;
import org.integratedmodelling.klab.api.services.resources.workflow.*;
import org.integratedmodelling.klab.api.services.resources.workflow.ReviewCycles.*;
import org.integratedmodelling.klab.services.application.flowchart.FlowChartService;
import org.integratedmodelling.klab.services.base.BaseService;
import org.integratedmodelling.klab.services.resources.ResourcesProvider;
import org.junit.jupiter.api.Test;

/** Actual service info routing, authorization, persisted lifecycle and ELK/PNG, without a UI mock. */
class ReviewFlowChartIntegrationTest {
  private static final ObjectMapper JSON = new ObjectMapper();

  private ResourcesProvider provider(WorkflowManager manager) throws Exception {
    var provider = mock(ResourcesProvider.class, CALLS_REAL_METHODS);
    var managerField = ResourcesProvider.class.getDeclaredField("workflowManager");
    managerField.setAccessible(true); managerField.set(provider, manager);
    var graphField = BaseService.class.getDeclaredField("flowCharts");
    graphField.setAccessible(true); graphField.set(provider, new FlowChartService());
    return provider;
  }

  private View command(OntologyReviewBootstrapActionTest.Fixture f, Operation operation,
      String source, String cycle, Audience audience, String proposal) {
    return f.manager.reviewCycle(f.flow.getId(), new Command(
        f.manager.getReviewCycles(f.flow.getId(), f.editor).revision(), operation, source,
        cycle, null, audience, null, null, false, proposal, null), f.editor);
  }

  @Test void pngAndChartDenyPublicBootstrapEvenToOtherEditorsAndAdministrators() throws Exception {
    var f = new OntologyReviewBootstrapActionTest().new Fixture(null);
    // The public flag is not an escape hatch around the creator-exclusive bootstrap policy.
    f.store.getFlow(f.flow.getId()).setPublicRead(true);
    var provider = provider(f.manager);
    var urn = f.flow.getUrn();
    var people = new ProposalReviewProtocolTest();
    var citizen = new ReviewCycleProtocolTest().community("citizen", true, false);
    var anonymous = new ReviewCycleProtocolTest().community("anonymous", false, false);
    for (var actor : List.of(people.scope("other-editor", "EDITOR"), people.scope("admin", "ADMIN"), citizen, anonymous)) {
      assertThrows(KlabResourceAccessException.class,
          () -> provider.info(urn, KnowledgeClass.FLOW, FlowChart.class, actor));
      assertThrows(KlabResourceAccessException.class,
          () -> provider.info(urn, KnowledgeClass.FLOW, BufferedImage.class, actor));
    }
    var graph = provider.info(urn, KnowledgeClass.FLOW, FlowChart.class, f.editor);
    assertEquals(1, graph.getRoot().getChildren().size());
    assertEquals("bootstrap", graph.getRoot().getChildren().getFirst().getMetadata().get("schemaId"));
    assertNotNull(provider.info(urn, KnowledgeClass.FLOW, BufferedImage.class, f.editor));
  }

  @Test void actualOverlappingRepeatedCyclesLayoutAndExportWithoutLeakingPrivateMaterials() throws Exception {
    var f = new OntologyReviewBootstrapActionTest().new Fixture("PRIVATE-BOOTSTRAP-MATERIAL");
    var prepared = f.prepare();
    var proposal = prepared.getStates().get(f.stage).getAttachments().stream()
        .filter(a -> "bootstrap-proposal".equals(a.getType())).findFirst().orElseThrow();
    var view = command(f, Operation.OPEN_CYCLE, f.stage, null, Audience.REVIEWER, proposal.getId());
    var cycle = view.cycles().getFirst();
    command(f, Operation.OPEN_ROUND, null, cycle.id(), Audience.REVIEWER, null);
    command(f, Operation.OPEN_ROUND, null, cycle.id(), Audience.COMMUNITY, null);
    var reviewer = new ProposalReviewProtocolTest().scope("private-reviewer", "REVIEWER");
    f.manager.reviewCycle(f.flow.getId(), new Command(f.manager.getReviewCycles(f.flow.getId(), reviewer).revision(),
        Operation.SAVE_CONTRIBUTION, null, cycle.id(), cycle.rounds().getFirst().id(), null, null,
        "PRIVATE-OTHER-REVIEWER-DRAFT", false), reviewer);
    var provider = provider(f.manager);
    var citizen = new ReviewCycleProtocolTest().community("citizen", true, false);
    var publicGraph = provider.info(f.flow.getUrn(), KnowledgeClass.FLOW, FlowChart.class, citizen);
    assertEquals(2, publicGraph.getRoot().getChildren().size(), "One admitted community round and its cycle only");
    String publicJson = JSON.writeValueAsString(publicGraph);
    assertFalse(publicJson.contains(f.stage));
    assertFalse(publicJson.contains("peer-review"));
    assertFalse(publicJson.contains("PRIVATE"));
    assertFalse(publicJson.contains("private-reviewer"));
    assertFalse(publicJson.contains(proposal.getId()));
    assertNotNull(provider.info(f.flow.getUrn(), KnowledgeClass.FLOW, BufferedImage.class, citizen));

    var firstGraph = provider.info(f.flow.getUrn(), KnowledgeClass.FLOW, FlowChart.class, f.editor);
    assertEquals(5, firstGraph.getRoot().getChildren().size());
    assertEquals(3, firstGraph.getRoot().getChildren().stream()
        .filter(n -> Boolean.TRUE.equals(n.getMetadata().get("current"))).count());
    assertEquals(3, firstGraph.getRoot().getEdges().stream()
        .filter(e -> "cycleRound".equals(e.getMetadata().get("kind"))).count());

    view = command(f, Operation.ENTER_COALESCING, null, cycle.id(), null, null);
    var coalescing = view.editableStageId();
    var next = JSON.readTree(f.store.attachments.get(proposal.getId()));
    ((ObjectNode) next.path("proposal")).put("revision_id", "test-r2").put("supersedes_revision", "test-r1");
    var upload = Flow.AttachmentUpload.create(); upload.setType("bootstrap-proposal");
    upload.setFileName("next-proposal.json"); upload.setMediaType("application/vnd.klab.proposal+yaml");
    upload.setContent(JSON.writeValueAsBytes(next));
    var nextProposal = f.manager.addAttachment(f.flow.getId(), coalescing, upload, f.editor);
    view = command(f, Operation.OPEN_CYCLE, coalescing, null, Audience.COMMUNITY, nextProposal.getId());
    var secondCycle = view.cycles().getLast();
    command(f, Operation.ENTER_COALESCING, null, secondCycle.id(), null, null);
    command(f, Operation.TERMINATE, null, null, null, null);
    var saved = f.manager.getFlow(f.flow.getId(), f.editor);
    var graph = provider.info(saved.getUrn(), KnowledgeClass.FLOW, FlowChart.class, f.editor);
    assertEquals(saved.getStates().size() + 2, graph.getRoot().getChildren().size());
    assertEquals(2, graph.getRoot().getChildren().stream()
        .filter(n -> "coalescing".equals(n.getMetadata().get("schemaId"))).count());
    assertEquals(2, graph.getRoot().getEdges().stream()
        .filter(e -> "cycleCoalescing".equals(e.getMetadata().get("kind"))).count());
    assertEquals(saved.getHistory().size(), graph.getRoot().getEdges().stream()
        .filter(e -> "persistedTransaction".equals(e.getMetadata().get("kind"))).count());
    assertEquals("CLOSED", graph.getRoot().getMetadata().get("status"));
    assertTrue(graph.getRoot().getEdges().stream().allMatch(e -> !e.getSections().isEmpty()));
    String json = JSON.writeValueAsString(graph);
    assertFalse(json.contains("PRIVATE")); assertFalse(json.contains("private-reviewer"));
    assertFalse(json.contains(proposal.getId())); assertFalse(json.contains("candidate-ontology"));
    var image = provider.info(saved.getUrn(), KnowledgeClass.FLOW, BufferedImage.class, f.editor);
    byte[] png = FlowChartService.encodePng(image);
    assertNotNull(ImageIO.read(new ByteArrayInputStream(png)));
    assertTrue(image.getWidth() > 100 && image.getHeight() > 60);
    assertTrue(image.getWidth() <= 4096 && image.getHeight() <= 4096);
    var output = Path.of("target", "flowchart-test"); Files.createDirectories(output);
    Files.write(output.resolve("review-flow.png"), png);
    Files.writeString(output.resolve("review-flow.json"), json);
  }

  @Test void bundledSchemaShowsPlannedCycleCommandsRatherThanDisconnectedTerminalPhases() throws Exception {
    var f = new OntologyReviewBootstrapActionTest().new Fixture(null);
    var provider = provider(f.manager);
    var workflow = f.manager.getWorkflow("ontology-review-pilot", f.editor);
    var graph = provider.info(workflow.getUrn(), KnowledgeClass.WORKFLOW, FlowChart.class, f.editor);
    assertEquals("planned-review-lifecycle", graph.getRoot().getMetadata().get("projection"));
    assertEquals(6, graph.getRoot().getChildren().size());
    assertEquals(1, graph.getRoot().getPorts().stream().filter(p -> p.getRole() == FlowChart.Role.OUTPUT).count());
    assertEquals(8, graph.getRoot().getEdges().stream()
        .filter(e -> "plannedLifecycleCommand".equals(e.getMetadata().get("kind"))).count());
    assertTrue(graph.getRoot().getEdges().stream().allMatch(e -> !e.getSections().isEmpty()));
    assertEquals(1, workflow.getTransitions().size(), "The projection must not alter the executable schema");
    var output = Path.of("target", "flowchart-test"); Files.createDirectories(output);
    Files.write(output.resolve("review-schema.png"), FlowChartService.encodePng(
        provider.info(workflow.getUrn(), KnowledgeClass.WORKFLOW, BufferedImage.class, f.editor)));
  }
}
