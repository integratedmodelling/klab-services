package org.integratedmodelling.klab.services.application.flowchart;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.JsonParser;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Path2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import javax.imageio.ImageIO;
import org.eclipse.elk.core.RecursiveGraphLayoutEngine;
import org.eclipse.elk.core.util.BasicProgressMonitor;
import org.eclipse.elk.core.util.Maybe;
import org.eclipse.elk.graph.json.ElkGraphJson;
import org.eclipse.elk.graph.json.JsonImporter;
import org.integratedmodelling.klab.api.documentation.FlowChart;
import org.integratedmodelling.klab.api.documentation.WorkflowFlowChartAdapter;
import org.integratedmodelling.klab.api.services.resources.workflow.Workflow;

/**
 * Service-local adapter registry, ELK layout and headless PNG rendering. Does not mutate inputs.
 * The Java layout result is also suitable for the embeddable Sprotty viewer.
 */
public class FlowChartService {
  private static final Font FONT = new Font(Font.SANS_SERIF, Font.PLAIN, 14);
  private static final Color INK = new Color(29, 63, 73);
  private final ObjectMapper mapper = new ObjectMapper().setDefaultPropertyInclusion(
      JsonInclude.Value.construct(JsonInclude.Include.NON_NULL, JsonInclude.Include.ALWAYS));
  private final List<Registration<?>> adapters = new CopyOnWriteArrayList<>();

  private record Registration<T>(Class<T> type, FlowChart.Adapter<? super T> adapter) {
    FlowChart adapt(Object source) { return adapter.adapt(type.cast(source)); }
  }

  public FlowChartService() {
    register(Workflow.class, new WorkflowFlowChartAdapter());
    register(FlowChart.class, chart -> chart);
  }

  /** Later registrations take precedence. Register during service initialization. */
  public <T> void register(Class<T> type, FlowChart.Adapter<? super T> adapter) {
    adapters.addFirst(new Registration<>(Objects.requireNonNull(type), Objects.requireNonNull(adapter)));
  }

  public FlowChart adapt(Object source) {
    Objects.requireNonNull(source, "source");
    for (var registration : adapters) {
      if (registration.type().isInstance(source)) return registration.adapt(source);
    }
    throw new IllegalArgumentException("No FlowChart adapter for " + source.getClass().getName());
  }

  /** Return a detached chart with measured labels, node/port positions and routed edge sections. */
  public FlowChart layout(Object source) {
    try {
      FlowChart chart = mapper.readValue(mapper.writeValueAsBytes(adapt(source)), FlowChart.class);
      chart.validate();
      var image = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
      var graphics = image.createGraphics();
      try {
        graphics.setFont(FONT);
        prepare(chart.getRoot(), graphics);
      } finally { graphics.dispose(); }
      var root = chart.getRoot();
      root.getLayoutOptions().putIfAbsent("elk.algorithm", "layered");
      root.getLayoutOptions().putIfAbsent("elk.direction", "RIGHT");
      root.getLayoutOptions().putIfAbsent("elk.hierarchyHandling", "INCLUDE_CHILDREN");
      var json = JsonParser.parseString(mapper.writeValueAsString(root)).getAsJsonObject();
      var importer = new Maybe<JsonImporter>();
      // ELK treats ports on a parentless root as external ports and requires a parent.
      var envelope = new com.google.gson.JsonObject();
      envelope.addProperty("id", "layout-" + java.util.UUID.randomUUID());
      envelope.add("layoutOptions", json.get("layoutOptions").deepCopy());
      var children = new com.google.gson.JsonArray(); children.add(json); envelope.add("children", children);
      var graph = ElkGraphJson.forGraph(envelope).rememberImporter(importer).toElk();
      new RecursiveGraphLayoutEngine().layout(graph, new BasicProgressMonitor());
      importer.get().transferLayout(graph);
      chart.setRoot(mapper.readValue(json.toString(), FlowChart.Node.class));
      chart.getRoot().setX(0); chart.getRoot().setY(0);
      chart.validate();
      return chart;
    } catch (IOException e) {
      throw new IllegalArgumentException("Cannot serialize FlowChart", e);
    }
  }

  private static void prepare(FlowChart.Node node, Graphics2D graphics) {
    measure(node.getLabels(), graphics);
    double labelWidth = node.getLabels().stream().mapToDouble(FlowChart.Label::getWidth).max().orElse(0);
    node.setWidth(Math.max(node.getWidth(), Math.max(140, labelWidth + 32)));
    node.setHeight(Math.max(node.getHeight(), 60));
    node.getLayoutOptions().putIfAbsent("elk.padding", "[top=36,left=24,bottom=24,right=24]");
    node.getLayoutOptions().putIfAbsent("elk.nodeLabels.placement", "[H_CENTER,V_TOP,INSIDE]");
    node.getLayoutOptions().putIfAbsent("elk.portConstraints", "FIXED_SIDE");
    for (var port : node.getPorts()) {
      port.setWidth(Math.max(8, port.getWidth()));
      port.setHeight(Math.max(8, port.getHeight()));
      port.getLayoutOptions().putIfAbsent("elk.port.side", port.getRole() == FlowChart.Role.INPUT ? "WEST" : "EAST");
      measure(port.getLabels(), graphics);
    }
    for (var edge : node.getEdges()) {
      edge.getSections().clear();
      edge.getJunctionPoints().clear();
      measure(edge.getLabels(), graphics);
    }
    for (var child : node.getChildren()) prepare(child, graphics);
  }

  private static void measure(List<FlowChart.Label> labels, Graphics2D graphics) {
    for (var label : labels) {
      label.setWidth(Math.max(label.getWidth(), graphics.getFontMetrics().stringWidth(Objects.toString(label.getText(), ""))));
      label.setHeight(Math.max(label.getHeight(), 18));
    }
  }

  /** Render at natural size, downscaling large diagrams to at most 4096 pixels on either axis. */
  public BufferedImage image(Object source) {
    var root = layout(source).getRoot();
    double width = root.getWidth() + 40;
    double height = root.getHeight() + 40;
    if (!Double.isFinite(width) || !Double.isFinite(height) || width <= 0 || height <= 0)
      throw new IllegalArgumentException("Invalid diagram bounds");
    double scale = Math.min(1, Math.min(4096 / width, 4096 / height));
    var image = new BufferedImage(Math.max(1, (int) Math.ceil(width * scale)),
        Math.max(1, (int) Math.ceil(height * scale)), BufferedImage.TYPE_INT_ARGB);
    var graphics = image.createGraphics();
    try {
      graphics.setColor(Color.WHITE); graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
      graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
      graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
      graphics.scale(scale, scale); graphics.translate(20, 20); graphics.setFont(FONT);
      var origins = new java.util.HashMap<String, java.awt.geom.Point2D.Double>();
      origins(root, 0, 0, origins);
      drawNode(graphics, root, true, origins);
    } finally { graphics.dispose(); }
    return image;
  }

  public byte[] png(Object source) { return encodePng(image(source)); }

  public static byte[] encodePng(BufferedImage image) {
    try {
      var output = new ByteArrayOutputStream();
      if (!ImageIO.write(image, "png", output)) throw new IllegalStateException("PNG writer unavailable");
      return output.toByteArray();
    } catch (IOException e) { throw new IllegalStateException("Cannot encode diagram PNG", e); }
  }

  private static void origins(FlowChart.Node node, double x, double y,
      java.util.Map<String, java.awt.geom.Point2D.Double> result) {
    result.put(node.getId(), new java.awt.geom.Point2D.Double(x, y));
    for (var child : node.getChildren()) origins(child, x + child.getX(), y + child.getY(), result);
  }

  private static void drawNode(Graphics2D graphics, FlowChart.Node node, boolean root,
      java.util.Map<String, java.awt.geom.Point2D.Double> origins) {
    var g = (Graphics2D) graphics.create();
    try {
      if (!root) g.translate(node.getX(), node.getY());
      g.setColor(node.getChildren().isEmpty() ? new Color(234, 245, 243) : new Color(247, 250, 251));
      var box = new RoundRectangle2D.Double(0, 0, node.getWidth(), node.getHeight(), 12, 12);
      g.fill(box); g.setColor(new Color(112, 153, 157)); g.setStroke(new BasicStroke(1.4f)); g.draw(box);
      for (var child : node.getChildren()) drawNode(g, child, false, origins);
      for (var link : node.getEdges()) {
        var owner = origins.get(node.getId());
        var container = origins.getOrDefault(link.getContainer(), owner);
        g.translate(container.x - owner.x, container.y - owner.y);
        g.setColor(INK); g.setStroke(new BasicStroke(1.5f));
        for (var section : link.getSections()) {
          var points = new ArrayList<FlowChart.Point>();
          points.add(section.getStartPoint()); points.addAll(section.getBendPoints()); points.add(section.getEndPoint());
          var path = new Path2D.Double(); path.moveTo(points.getFirst().getX(), points.getFirst().getY());
          for (var point : points.subList(1, points.size())) path.lineTo(point.getX(), point.getY());
          g.draw(path);
          if (section.getOutgoingSections().isEmpty()) arrow(g, points.get(points.size() - 2), points.getLast());
        }
        drawLabels(g, link.getLabels());
        g.translate(owner.x - container.x, owner.y - container.y);
      }
      drawLabels(g, node.getLabels());
      for (var port : node.getPorts()) {
        g.setColor(port.getRole() == FlowChart.Role.INPUT ? new Color(32, 128, 133) : new Color(190, 116, 36));
        g.fill(new java.awt.geom.Rectangle2D.Double(port.getX(), port.getY(), port.getWidth(), port.getHeight()));
        var pg = (Graphics2D) g.create();
        try { pg.translate(port.getX(), port.getY()); drawLabels(pg, port.getLabels()); }
        finally { pg.dispose(); }
      }
    } finally { g.dispose(); }
  }

  private static void drawLabels(Graphics2D g, List<FlowChart.Label> labels) {
    g.setColor(INK);
    for (var label : labels) g.drawString(Objects.toString(label.getText(), ""), (float) label.getX(),
        (float) (label.getY() + g.getFontMetrics().getAscent()));
  }

  private static void arrow(Graphics2D g, FlowChart.Point from, FlowChart.Point to) {
    double angle = Math.atan2(to.getY() - from.getY(), to.getX() - from.getX());
    var path = new Path2D.Double(); path.moveTo(to.getX(), to.getY());
    path.lineTo(to.getX() - 9 * Math.cos(angle - .45), to.getY() - 9 * Math.sin(angle - .45));
    path.lineTo(to.getX() - 9 * Math.cos(angle + .45), to.getY() - 9 * Math.sin(angle + .45));
    path.closePath(); g.fill(path);
  }
}
